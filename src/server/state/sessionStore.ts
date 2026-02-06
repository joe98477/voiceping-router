/**
 * User session storage in Redis
 * Tracks connected users and their channel memberships
 */

import { getRedisClient } from './redisClient';
import { UserSession } from '../../shared/types';

/**
 * Session store for tracking connected users
 * Uses Redis hash for session data and sets for channel membership
 */
export class SessionStore {
  private static readonly SESSIONS_KEY = 'sessions';
  private static readonly SESSION_TTL_SECONDS = 3600; // 1 hour

  /**
   * Get Redis key for session expiry tracking
   */
  private getSessionExpiryKey(userId: string): string {
    return `session:${userId}`;
  }

  /**
   * Get Redis key for channel users set
   */
  private getChannelUsersKey(channelId: string): string {
    return `channel:${channelId}:users`;
  }

  /**
   * Add or update user session
   *
   * @param userId - User ID
   * @param session - Session data
   */
  async addSession(userId: string, session: UserSession): Promise<void> {
    try {
      const client = getRedisClient();

      // Store session in hash
      await client.hSet(
        SessionStore.SESSIONS_KEY,
        userId,
        JSON.stringify(session)
      );

      // Set expiry tracking key with TTL
      await client.set(
        this.getSessionExpiryKey(userId),
        '1',
        { EX: SessionStore.SESSION_TTL_SECONDS }
      );

      console.info(`Session added for user ${userId}`);
    } catch (err) {
      console.error('Error adding session:', err);
      throw err;
    }
  }

  /**
   * Remove user session
   *
   * @param userId - User ID to remove
   */
  async removeSession(userId: string): Promise<void> {
    try {
      const client = getRedisClient();

      // Get session to find channels user was in
      const session = await this.getSession(userId);

      // Remove from all channels
      if (session) {
        await this.removeUserFromAllChannels(userId, session.channels);
      }

      // Remove from sessions hash
      await client.hDel(SessionStore.SESSIONS_KEY, userId);

      // Remove expiry key
      await client.del(this.getSessionExpiryKey(userId));

      console.info(`Session removed for user ${userId}`);
    } catch (err) {
      console.error('Error removing session:', err);
      throw err;
    }
  }

  /**
   * Get user session
   *
   * @param userId - User ID
   * @returns User session or null if not found
   */
  async getSession(userId: string): Promise<UserSession | null> {
    try {
      const client = getRedisClient();

      const sessionData = await client.hGet(SessionStore.SESSIONS_KEY, userId);

      if (!sessionData) {
        return null;
      }

      return JSON.parse(sessionData) as UserSession;
    } catch (err) {
      console.error('Error getting session:', err);
      return null;
    }
  }

  /**
   * Get all active sessions
   *
   * @returns Array of all active user sessions
   */
  async getActiveSessions(): Promise<UserSession[]> {
    try {
      const client = getRedisClient();

      const allSessions = await client.hGetAll(SessionStore.SESSIONS_KEY);

      const sessions: UserSession[] = [];

      for (const [userId, sessionData] of Object.entries(allSessions)) {
        try {
          sessions.push(JSON.parse(sessionData) as UserSession);
        } catch (parseErr) {
          console.error(`Failed to parse session for user ${userId}:`, parseErr);
        }
      }

      return sessions;
    } catch (err) {
      console.error('Error getting active sessions:', err);
      return [];
    }
  }

  /**
   * Get list of users in a channel
   *
   * @param channelId - Channel ID
   * @returns Array of user IDs in the channel
   */
  async getUsersInChannel(channelId: string): Promise<string[]> {
    try {
      const client = getRedisClient();
      const key = this.getChannelUsersKey(channelId);

      const users = await client.sMembers(key);
      return users;
    } catch (err) {
      console.error('Error getting users in channel:', err);
      return [];
    }
  }

  /**
   * Add user to a channel
   *
   * @param userId - User ID
   * @param channelId - Channel ID
   */
  async addUserToChannel(userId: string, channelId: string): Promise<void> {
    try {
      const client = getRedisClient();
      const key = this.getChannelUsersKey(channelId);

      await client.sAdd(key, userId);

      // Update user's session to include this channel
      const session = await this.getSession(userId);
      if (session && !session.channels.includes(channelId)) {
        session.channels.push(channelId);
        await client.hSet(
          SessionStore.SESSIONS_KEY,
          userId,
          JSON.stringify(session)
        );
      }

      console.info(`User ${userId} added to channel ${channelId}`);
    } catch (err) {
      console.error('Error adding user to channel:', err);
      throw err;
    }
  }

  /**
   * Remove user from a channel
   *
   * @param userId - User ID
   * @param channelId - Channel ID
   */
  async removeUserFromChannel(userId: string, channelId: string): Promise<void> {
    try {
      const client = getRedisClient();
      const key = this.getChannelUsersKey(channelId);

      await client.sRem(key, userId);

      // Update user's session to remove this channel
      const session = await this.getSession(userId);
      if (session) {
        session.channels = session.channels.filter((ch) => ch !== channelId);
        await client.hSet(
          SessionStore.SESSIONS_KEY,
          userId,
          JSON.stringify(session)
        );
      }

      console.info(`User ${userId} removed from channel ${channelId}`);
    } catch (err) {
      console.error('Error removing user from channel:', err);
      throw err;
    }
  }

  /**
   * Remove user from all channels
   * Helper for session cleanup
   *
   * @param userId - User ID
   * @param channels - List of channels user is in (from session)
   */
  private async removeUserFromAllChannels(
    userId: string,
    channels: string[]
  ): Promise<void> {
    try {
      const client = getRedisClient();

      // Remove from each channel's user set
      for (const channelId of channels) {
        const key = this.getChannelUsersKey(channelId);
        await client.sRem(key, userId);
      }

      console.info(`User ${userId} removed from ${channels.length} channels`);
    } catch (err) {
      console.error('Error removing user from all channels:', err);
      // Don't throw - best effort cleanup
    }
  }

  /**
   * Public method to remove user from all channels
   * Used when session data not available
   *
   * @param userId - User ID
   */
  async removeUserFromAllChannelsById(userId: string): Promise<void> {
    const session = await this.getSession(userId);
    if (session) {
      await this.removeUserFromAllChannels(userId, session.channels);
    }
  }

  /**
   * Get count of users in a channel
   *
   * @param channelId - Channel ID
   * @returns Number of users in channel
   */
  async getChannelUserCount(channelId: string): Promise<number> {
    try {
      const client = getRedisClient();
      const key = this.getChannelUsersKey(channelId);

      return await client.sCard(key);
    } catch (err) {
      console.error('Error getting channel user count:', err);
      return 0;
    }
  }
}
