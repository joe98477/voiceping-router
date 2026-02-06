/**
 * Security Events Manager
 * Handles ban/unban operations, security event tracking, and rate limit monitoring
 */

import { RedisClientType } from 'redis';
import { AuditLogger, AuditAction } from './auditLogger';
import { createLogger } from '../logger';

const logger = createLogger('SecurityEventsManager');

/**
 * Redis key patterns for security data
 */
const REDIS_KEYS = {
  bannedUsers: 'security:banned', // sorted set with score=expiresAt
  banDetails: (userId: string) => `security:ban:${userId}`, // hash with ban details
  securityEvents: 'security:events', // sorted set with score=timestamp
} as const;

/**
 * Maximum number of security events to keep in Redis
 */
const MAX_SECURITY_EVENTS = 5000;

/**
 * Ban information stored in Redis
 */
export interface BanInfo {
  userId: string;
  bannedBy: string;
  bannedAt: number;
  expiresAt: number | null; // null = permanent ban
  reason: string;
}

/**
 * Security event types
 */
export enum SecurityEventType {
  BAN = 'ban',
  UNBAN = 'unban',
  FORCE_DISCONNECT = 'force_disconnect',
  RATE_LIMIT = 'rate_limit',
  AUTH_FAILURE = 'auth_failure',
}

/**
 * Security event stored in Redis
 */
export interface SecurityEvent {
  id: string;
  type: SecurityEventType;
  actorId: string;
  targetId: string | null;
  timestamp: number;
  metadata: Record<string, unknown>;
}

/**
 * Options for querying security events
 */
export interface SecurityEventQueryOptions {
  limit?: number;
  type?: SecurityEventType;
  since?: number; // timestamp
}

/**
 * Rate limit status for an IP address
 */
export interface RateLimitStatus {
  ip: string;
  connectionAttempts?: number;
  authAttempts?: number;
  authFailures?: number;
  lastAttempt?: number;
}

/**
 * SecurityEventsManager class
 * Manages user bans, security events, and rate limit data
 */
export class SecurityEventsManager {
  private getRedisClient: () => RedisClientType;
  private auditLogger: AuditLogger;

  constructor(getRedisClient: () => RedisClientType, auditLogger: AuditLogger) {
    this.getRedisClient = getRedisClient;
    this.auditLogger = auditLogger;
  }

  /**
   * Ban a user temporarily or permanently
   * @param userId - User to ban
   * @param bannedBy - Actor who initiated the ban
   * @param durationMs - Ban duration in milliseconds (undefined = permanent)
   * @param reason - Reason for ban
   */
  async banUser(
    userId: string,
    bannedBy: string,
    durationMs?: number,
    reason?: string
  ): Promise<void> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        throw new Error('Redis client not connected');
      }

      const now = Date.now();
      const expiresAt = durationMs ? now + durationMs : null;
      const score = expiresAt || Number.MAX_SAFE_INTEGER; // Permanent bans use max score

      // Add to sorted set
      await client.zAdd(REDIS_KEYS.bannedUsers, {
        score,
        value: userId,
      });

      // Store ban details in hash
      const banInfo: BanInfo = {
        userId,
        bannedBy,
        bannedAt: now,
        expiresAt,
        reason: reason || 'No reason provided',
      };

      const banKey = REDIS_KEYS.banDetails(userId);
      await client.hSet(banKey, {
        userId,
        bannedBy,
        bannedAt: now.toString(),
        expiresAt: expiresAt?.toString() || 'permanent',
        reason: reason || 'No reason provided',
      });

      // Set expiry on ban details hash if temporary ban
      if (expiresAt) {
        const ttlSeconds = Math.ceil(durationMs! / 1000);
        await client.expire(banKey, ttlSeconds);
      }

      // Record security event
      await this.recordSecurityEvent({
        id: `ban_${userId}_${now}`,
        type: SecurityEventType.BAN,
        actorId: bannedBy,
        targetId: userId,
        timestamp: now,
        metadata: {
          duration: durationMs || 'permanent',
          reason: reason || 'No reason provided',
        },
      });

      logger.info(`User ${userId} banned by ${bannedBy} (duration: ${durationMs || 'permanent'})`);
    } catch (err) {
      logger.error(`Failed to ban user ${userId}: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  /**
   * Unban a user
   * @param userId - User to unban
   * @param unbannedBy - Actor who initiated the unban
   */
  async unbanUser(userId: string, unbannedBy: string): Promise<void> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        throw new Error('Redis client not connected');
      }

      // Remove from sorted set
      await client.zRem(REDIS_KEYS.bannedUsers, userId);

      // Delete ban details hash
      const banKey = REDIS_KEYS.banDetails(userId);
      await client.del(banKey);

      // Record security event
      await this.recordSecurityEvent({
        id: `unban_${userId}_${Date.now()}`,
        type: SecurityEventType.UNBAN,
        actorId: unbannedBy,
        targetId: userId,
        timestamp: Date.now(),
        metadata: {},
      });

      logger.info(`User ${userId} unbanned by ${unbannedBy}`);
    } catch (err) {
      logger.error(`Failed to unban user ${userId}: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  /**
   * Check if a user is currently banned
   * @param userId - User to check
   * @returns true if user is banned, false otherwise
   */
  async isUserBanned(userId: string): Promise<boolean> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        logger.warn('Redis client not connected, assuming user not banned');
        return false;
      }

      const now = Date.now();

      // Get user's score from sorted set
      const score = await client.zScore(REDIS_KEYS.bannedUsers, userId);

      if (score === null) {
        // User not in banned set
        return false;
      }

      // Check if ban has expired (score < now means expired)
      if (score !== Number.MAX_SAFE_INTEGER && score < now) {
        // Ban expired, remove from set
        await client.zRem(REDIS_KEYS.bannedUsers, userId);
        await client.del(REDIS_KEYS.banDetails(userId));
        logger.info(`Expired ban removed for user ${userId}`);
        return false;
      }

      return true;
    } catch (err) {
      logger.error(`Failed to check ban status for user ${userId}: ${err instanceof Error ? err.message : String(err)}`);
      // Fail open: if we can't check ban status, allow connection
      return false;
    }
  }

  /**
   * Get all active bans
   * @returns Array of ban information for all currently banned users
   */
  async getActiveBans(): Promise<BanInfo[]> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        logger.warn('Redis client not connected, cannot fetch active bans');
        return [];
      }

      const now = Date.now();

      // Get all users with score >= now (active bans)
      const bannedUserIds = await client.zRangeByScore(
        REDIS_KEYS.bannedUsers,
        now,
        Number.MAX_SAFE_INTEGER
      );

      // Fetch ban details for each user
      const bans: BanInfo[] = [];
      for (const userId of bannedUserIds) {
        const banKey = REDIS_KEYS.banDetails(userId);
        const details = await client.hGetAll(banKey);

        if (details && Object.keys(details).length > 0) {
          bans.push({
            userId: details.userId,
            bannedBy: details.bannedBy,
            bannedAt: parseInt(details.bannedAt, 10),
            expiresAt: details.expiresAt === 'permanent' ? null : parseInt(details.expiresAt, 10),
            reason: details.reason,
          });
        }
      }

      return bans;
    } catch (err) {
      logger.error(`Failed to get active bans: ${err instanceof Error ? err.message : String(err)}`);
      return [];
    }
  }

  /**
   * Record a security event
   * @param event - Security event to record
   */
  async recordSecurityEvent(event: SecurityEvent): Promise<void> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        logger.warn('Redis client not connected, skipping security event recording');
        return;
      }

      // Add to sorted set with timestamp as score
      await client.zAdd(REDIS_KEYS.securityEvents, {
        score: event.timestamp,
        value: JSON.stringify(event),
      });

      // Trim to max size
      await client.zRemRangeByRank(REDIS_KEYS.securityEvents, 0, -(MAX_SECURITY_EVENTS + 1));

      logger.debug(`Security event recorded: ${event.type} by ${event.actorId}`);
    } catch (err) {
      logger.error(`Failed to record security event: ${err instanceof Error ? err.message : String(err)}`);
      // Non-blocking: don't throw
    }
  }

  /**
   * Get security events with optional filtering
   * @param options - Query options (limit, type, since)
   * @returns Array of security events
   */
  async getSecurityEvents(options?: SecurityEventQueryOptions): Promise<SecurityEvent[]> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        logger.warn('Redis client not connected, cannot fetch security events');
        return [];
      }

      const limit = options?.limit || 100;
      const since = options?.since || 0;

      // Get events from sorted set (newest first)
      const events = await client.zRange(
        REDIS_KEYS.securityEvents,
        since,
        Number.MAX_SAFE_INTEGER,
        {
          BY: 'SCORE',
          REV: true,
          LIMIT: { offset: 0, count: limit },
        }
      );

      // Parse events
      const parsedEvents = events.map((json) => JSON.parse(json) as SecurityEvent);

      // Filter by type if specified
      if (options?.type) {
        return parsedEvents.filter((event) => event.type === options.type);
      }

      return parsedEvents;
    } catch (err) {
      logger.error(`Failed to get security events: ${err instanceof Error ? err.message : String(err)}`);
      return [];
    }
  }

  /**
   * Get rate limit status for an IP address
   * @param ip - IP address to check
   * @returns Rate limit status
   */
  async getRateLimitStatus(ip: string): Promise<RateLimitStatus> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        logger.warn('Redis client not connected, cannot fetch rate limit status');
        return { ip };
      }

      // Read rate limit keys
      const connKey = `rl:conn:${ip}`;
      const authKey = `rl:auth:${ip}`;
      const failKey = `rl:fail:${ip}`;

      const [connCount, authCount, failCount] = await Promise.all([
        client.get(connKey),
        client.get(authKey),
        client.get(failKey),
      ]);

      return {
        ip,
        connectionAttempts: connCount ? parseInt(connCount, 10) : 0,
        authAttempts: authCount ? parseInt(authCount, 10) : 0,
        authFailures: failCount ? parseInt(failCount, 10) : 0,
        lastAttempt: Date.now(), // Approximate
      };
    } catch (err) {
      logger.error(`Failed to get rate limit status for ${ip}: ${err instanceof Error ? err.message : String(err)}`);
      return { ip };
    }
  }

  /**
   * Get rate-limited IP addresses
   * @param limit - Maximum number of IPs to return
   * @returns Array of rate limit statuses for IPs with active rate limits
   */
  async getRateLimitedIPs(limit?: number): Promise<RateLimitStatus[]> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        logger.warn('Redis client not connected, cannot fetch rate-limited IPs');
        return [];
      }

      const maxIPs = limit || 100;
      const rateLimitedIPs: RateLimitStatus[] = [];

      // Scan for rate limit keys (rl:conn:*, rl:auth:*, rl:fail:*)
      const patterns = ['rl:conn:*', 'rl:auth:*', 'rl:fail:*'];
      const ipSet = new Set<string>();

      for (const pattern of patterns) {
        // Use SCAN to find keys matching pattern
        let cursor = 0;
        do {
          const result = await client.scan(cursor, {
            MATCH: pattern,
            COUNT: 100,
          });

          cursor = result.cursor;
          const keys = result.keys;

          // Extract IP addresses from keys
          for (const key of keys) {
            const parts = key.split(':');
            if (parts.length === 3) {
              const ip = parts[2];
              ipSet.add(ip);
            }
          }

          if (ipSet.size >= maxIPs) {
            break;
          }
        } while (cursor !== 0 && ipSet.size < maxIPs);

        if (ipSet.size >= maxIPs) {
          break;
        }
      }

      // Get rate limit status for each IP
      const ips = Array.from(ipSet).slice(0, maxIPs);
      for (const ip of ips) {
        const status = await this.getRateLimitStatus(ip);
        if (status.connectionAttempts || status.authAttempts || status.authFailures) {
          rateLimitedIPs.push(status);
        }
      }

      return rateLimitedIPs;
    } catch (err) {
      logger.error(`Failed to get rate-limited IPs: ${err instanceof Error ? err.message : String(err)}`);
      return [];
    }
  }

  /**
   * Check if user is banned during WebSocket connection
   * Called during WebSocket authentication
   * @param userId - User attempting to connect
   * @returns true if banned, false if allowed
   */
  async checkBanOnConnect(userId: string): Promise<boolean> {
    return this.isUserBanned(userId);
  }
}
