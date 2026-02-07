/**
 * Progressive rate limiter with Redis backend
 * Implements lenient rate limiting with progressive slowdown (never hard lockout)
 */

import { RateLimiterRedis } from 'rate-limiter-flexible';
import { getRedisClient } from '../state/redisClient';
import { createLogger } from '../logger';

const logger = createLogger('RateLimiter');

/**
 * RateLimiter class with progressive slowdown for auth failures
 * CRITICAL: NEVER hard-locks accounts - always allows retry after delay
 */
export class RateLimiter {
  private connectionLimiter: RateLimiterRedis | null = null;
  private authLimiter: RateLimiterRedis | null = null;
  private pttLimiter: RateLimiterRedis | null = null;

  /**
   * Initialize rate limiters lazily
   */
  private initLimiters(): void {
    if (this.connectionLimiter) {
      return; // Already initialized
    }

    const redisClient = getRedisClient();

    // Connection limiter: 60 WebSocket connections per IP per minute
    // Multi-channel dispatch creates one WebSocket per channel (can be 20+)
    this.connectionLimiter = new RateLimiterRedis({
      storeClient: redisClient,
      keyPrefix: 'rl:conn',
      points: 60, // 60 connections
      duration: 60, // per minute
    });

    // Auth limiter: 40 auth attempts per IP per 15 minutes
    // Each channel reconnection consumes an auth point
    this.authLimiter = new RateLimiterRedis({
      storeClient: redisClient,
      keyPrefix: 'rl:auth',
      points: 40, // 40 attempts
      duration: 900, // 15 minutes
    });

    // PTT limiter: 60 PTT actions per user per minute
    this.pttLimiter = new RateLimiterRedis({
      storeClient: redisClient,
      keyPrefix: 'rl:ptt',
      points: 60, // 60 PTT actions
      duration: 60, // per minute
    });

    logger.info('Rate limiters initialized with Redis backend');
  }

  /**
   * Consume connection rate limit
   * Returns allowed status and retry time if blocked
   */
  async consumeConnection(ip: string): Promise<{ allowed: boolean; retryAfterMs?: number }> {
    this.initLimiters();

    try {
      await this.connectionLimiter!.consume(ip);
      return { allowed: true };
    } catch (rejRes: any) {
      if (rejRes && rejRes.msBeforeNext !== undefined) {
        logger.warn(`Connection rate limit exceeded for IP ${ip}, retry after ${rejRes.msBeforeNext}ms`);
        return { allowed: false, retryAfterMs: rejRes.msBeforeNext };
      }
      throw rejRes;
    }
  }

  /**
   * Consume auth rate limit
   * Returns allowed status, retry time, and current penalty if blocked
   */
  async consumeAuth(ip: string): Promise<{ allowed: boolean; retryAfterMs?: number; penalty?: number }> {
    this.initLimiters();

    try {
      // Check auth rate limit
      await this.authLimiter!.consume(ip);

      // Check progressive slowdown penalty
      const penaltyMs = await this.getSlowdownMs(ip);
      if (penaltyMs > 0) {
        logger.warn(`Auth slowdown active for IP ${ip}, penalty ${penaltyMs}ms`);
        return { allowed: false, retryAfterMs: penaltyMs, penalty: penaltyMs };
      }

      return { allowed: true };
    } catch (rejRes: any) {
      if (rejRes && rejRes.msBeforeNext !== undefined) {
        logger.warn(`Auth rate limit exceeded for IP ${ip}, retry after ${rejRes.msBeforeNext}ms`);
        return { allowed: false, retryAfterMs: rejRes.msBeforeNext };
      }
      throw rejRes;
    }
  }

  /**
   * Consume PTT rate limit
   * Returns allowed status
   */
  async consumePtt(userId: string): Promise<{ allowed: boolean }> {
    this.initLimiters();

    try {
      await this.pttLimiter!.consume(userId);
      return { allowed: true };
    } catch (rejRes: any) {
      if (rejRes && rejRes.msBeforeNext !== undefined) {
        logger.warn(`PTT rate limit exceeded for user ${userId}`);
        return { allowed: false };
      }
      throw rejRes;
    }
  }

  /**
   * Record auth failure and increment penalty counter
   * Stored in Redis with 15-minute TTL
   */
  async recordAuthFailure(ip: string): Promise<void> {
    try {
      const redisClient = getRedisClient();
      const key = `rl:fail:${ip}`;
      const failureCount = await redisClient.incr(key);

      // Set TTL on first failure
      if (failureCount === 1) {
        await redisClient.expire(key, 900); // 15 minutes TTL
      }

      logger.info(`Auth failure recorded for IP ${ip}, count: ${failureCount}`);
    } catch (err) {
      logger.error(`Failed to record auth failure for IP ${ip}:`, err);
    }
  }

  /**
   * Reset failure counter on successful auth
   */
  async recordAuthSuccess(ip: string): Promise<void> {
    try {
      const redisClient = getRedisClient();
      const key = `rl:fail:${ip}`;
      await redisClient.del(key);
      logger.info(`Auth success recorded for IP ${ip}, failure counter reset`);
    } catch (err) {
      logger.error(`Failed to reset auth failure counter for IP ${ip}:`, err);
    }
  }

  /**
   * Calculate progressive delay based on failure count
   * Formula: min(1000 * 2^(failures-3), 30000) for failures > 3, else 0
   * Progressive delay: 1s, 2s, 4s, 8s, 16s, capped at 30s
   */
  async getSlowdownMs(ip: string): Promise<number> {
    try {
      const redisClient = getRedisClient();
      const key = `rl:fail:${ip}`;
      const failureCountStr = await redisClient.get(key);

      if (!failureCountStr) {
        return 0; // No failures
      }

      const failureCount = parseInt(failureCountStr, 10);

      if (failureCount <= 3) {
        return 0; // No penalty for first 3 failures
      }

      // Progressive delay: 1s, 2s, 4s, 8s, 16s, 30s (cap)
      const delayMs = Math.min(1000 * Math.pow(2, failureCount - 4), 30000);
      return delayMs;
    } catch (err) {
      logger.error(`Failed to get slowdown for IP ${ip}:`, err);
      return 0; // Fail open - don't block legitimate users on Redis errors
    }
  }
}

// Export singleton instance
export const rateLimiter = new RateLimiter();
