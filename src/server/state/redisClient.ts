/**
 * Redis v4 async client connection management
 * Provides singleton Redis client with reconnection handling
 */

import { createClient, RedisClientType } from 'redis';
import { config } from '../config';

// Singleton Redis client instance
let clientInstance: RedisClientType | null = null;

/**
 * Get or create Redis client instance
 */
export function getRedisClient(): RedisClientType {
  if (!clientInstance) {
    clientInstance = createClient({
      url: config.redis.url,
    });

    // Error event handler - log but don't crash on transient errors
    clientInstance.on('error', (err) => {
      console.error('Redis client error:', err);
    });

    // Reconnection event handler
    clientInstance.on('reconnecting', () => {
      console.warn('Redis client reconnecting...');
    });

    // Ready event handler
    clientInstance.on('ready', () => {
      console.info('Redis client ready');
    });
  }

  return clientInstance;
}

/**
 * Connect to Redis server
 * Must be called before using Redis operations
 */
export async function connectRedis(): Promise<void> {
  try {
    const client = getRedisClient();

    // Check if already connected
    if (client.isOpen) {
      console.info('Redis client already connected');
      return;
    }

    await client.connect();
    console.info('Redis client connected successfully');
  } catch (err) {
    console.error('Failed to connect to Redis:', err);
    throw new Error(`Redis connection failed: ${err instanceof Error ? err.message : String(err)}`);
  }
}

/**
 * Disconnect from Redis server
 * Call during graceful shutdown
 */
export async function disconnectRedis(): Promise<void> {
  try {
    if (clientInstance && clientInstance.isOpen) {
      await clientInstance.disconnect();
      console.info('Redis client disconnected');
      clientInstance = null;
    }
  } catch (err) {
    console.error('Error disconnecting Redis:', err);
    throw err;
  }
}

// Export redisClient as an alias to getRedisClient for backward compatibility
export const redisClient = getRedisClient;
