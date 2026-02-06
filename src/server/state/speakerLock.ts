/**
 * Distributed speaker lock using Redis atomic operations
 * Implements PTT exclusive access: only one speaker per channel at a time
 */

import { getRedisClient } from './redisClient';
import { SpeakerLockResult } from '../../shared/types';
import { config } from '../config';

/**
 * Lock value structure stored in Redis
 */
interface LockValue {
  userId: string;
  userName: string;
  timestamp: number;
}

/**
 * Generate Redis key for channel speaker lock
 */
function getLockKey(channelId: string): string {
  return `channel:${channelId}:speaker`;
}

/**
 * Acquire exclusive speaker lock for a channel
 * Uses atomic SET NX EX to prevent race conditions
 *
 * @param channelId - Channel to acquire lock for
 * @param userId - User attempting to acquire lock
 * @param userName - User's display name
 * @returns SpeakerLockResult with acquisition status
 */
export async function acquireSpeakerLock(
  channelId: string,
  userId: string,
  userName: string
): Promise<SpeakerLockResult> {
  try {
    const client = getRedisClient();
    const key = getLockKey(channelId);
    const value: LockValue = {
      userId,
      userName,
      timestamp: Date.now(),
    };

    // Atomic SET NX EX: set if not exists with TTL expiration
    // Returns 'OK' if acquired, null if already held by someone else
    const result = await client.set(key, JSON.stringify(value), {
      NX: true, // Only set if key doesn't exist
      EX: config.ptt.lockTtlSeconds, // Expire after TTL seconds
    });

    if (result === 'OK') {
      // Lock acquired successfully
      return { acquired: true };
    }

    // Lock already held - get current holder info
    const currentLockData = await client.get(key);
    if (currentLockData) {
      try {
        const currentLock: LockValue = JSON.parse(currentLockData);
        return {
          acquired: false,
          currentSpeaker: currentLock.userId,
          currentSpeakerName: currentLock.userName,
        };
      } catch (parseErr) {
        console.error('Failed to parse current lock data:', parseErr);
        return { acquired: false };
      }
    }

    // Lock exists but couldn't read it
    return { acquired: false };
  } catch (err) {
    console.error('Error acquiring speaker lock:', err);
    // Fail-safe: deny lock on Redis errors to prevent multiple speakers
    return { acquired: false };
  }
}

/**
 * Release speaker lock for a channel
 * Only the current lock holder can release their lock
 *
 * @param channelId - Channel to release lock for
 * @param userId - User attempting to release lock
 * @returns true if released, false if not the lock holder
 */
export async function releaseSpeakerLock(
  channelId: string,
  userId: string
): Promise<boolean> {
  try {
    const client = getRedisClient();
    const key = getLockKey(channelId);

    // Get current lock to verify ownership
    const currentLockData = await client.get(key);
    if (!currentLockData) {
      // No lock exists - consider it released
      return true;
    }

    // Parse and verify lock holder
    try {
      const currentLock: LockValue = JSON.parse(currentLockData);

      if (currentLock.userId !== userId) {
        // Not the lock holder - cannot release
        console.warn(`User ${userId} attempted to release lock held by ${currentLock.userId}`);
        return false;
      }

      // User owns the lock - delete it
      await client.del(key);
      return true;
    } catch (parseErr) {
      console.error('Failed to parse lock data during release:', parseErr);
      return false;
    }
  } catch (err) {
    console.error('Error releasing speaker lock:', err);
    return false;
  }
}

/**
 * Refresh/extend TTL on existing speaker lock
 * Useful for long PTT sessions to prevent auto-expiry
 *
 * @param channelId - Channel lock to refresh
 * @param userId - User requesting refresh (must be current holder)
 * @returns true if refreshed, false if not holder or lock doesn't exist
 */
export async function refreshSpeakerLock(
  channelId: string,
  userId: string
): Promise<boolean> {
  try {
    const client = getRedisClient();
    const key = getLockKey(channelId);

    // Verify lock exists and user owns it
    const currentLockData = await client.get(key);
    if (!currentLockData) {
      return false;
    }

    try {
      const currentLock: LockValue = JSON.parse(currentLockData);

      if (currentLock.userId !== userId) {
        // Not the lock holder
        return false;
      }

      // Refresh TTL
      const result = await client.expire(key, config.ptt.lockTtlSeconds);
      return result; // Returns true if key exists and TTL was set
    } catch (parseErr) {
      console.error('Failed to parse lock data during refresh:', parseErr);
      return false;
    }
  } catch (err) {
    console.error('Error refreshing speaker lock:', err);
    return false;
  }
}

/**
 * Get current speaker for a channel
 *
 * @param channelId - Channel to query
 * @returns Current speaker info or null if no active speaker
 */
export async function getCurrentSpeaker(
  channelId: string
): Promise<{ userId: string; userName: string } | null> {
  try {
    const client = getRedisClient();
    const key = getLockKey(channelId);

    const lockData = await client.get(key);
    if (!lockData) {
      return null;
    }

    try {
      const lock: LockValue = JSON.parse(lockData);
      return {
        userId: lock.userId,
        userName: lock.userName,
      };
    } catch (parseErr) {
      console.error('Failed to parse lock data:', parseErr);
      return null;
    }
  } catch (err) {
    console.error('Error getting current speaker:', err);
    return null;
  }
}

/**
 * Force release speaker lock regardless of holder
 * Admin function for handling stuck locks
 * USE WITH CAUTION
 *
 * @param channelId - Channel to force release
 */
export async function forceReleaseLock(channelId: string): Promise<void> {
  try {
    const client = getRedisClient();
    const key = getLockKey(channelId);

    await client.del(key);
    console.warn(`Force released speaker lock for channel ${channelId}`);
  } catch (err) {
    console.error('Error force releasing speaker lock:', err);
    throw err;
  }
}
