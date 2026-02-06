/**
 * Permission Manager for role-based channel access validation
 * Validates user permissions based on JWT claims and Redis state
 */

import { RedisClientType } from 'redis';
import { createLogger } from '../logger';
import {
  UserRole,
  AuthenticatedUser,
  PermissionSet,
} from '../../shared/types';

const logger = createLogger('PermissionManager');

/**
 * Redis key patterns matching control-plane conventions
 */
const REDIS_KEYS = {
  userChannels: (userId: string) => `u.${userId}.g`,
  channelUsers: (channelId: string) => `g.${channelId}.u`,
} as const;

/**
 * Permission Manager class
 * Validates user access to channels based on role and membership
 */
export class PermissionManager {
  private getRedisClient: () => RedisClientType;

  constructor(getRedisClient: () => RedisClientType) {
    this.getRedisClient = getRedisClient;
  }

  /**
   * Parse JWT claims into AuthenticatedUser structure
   * Maps EventRole.DISPATCH to UserRole.DISPATCH, EventRole.USER to UserRole.GENERAL
   * If globalRole is ADMIN, sets role to UserRole.ADMIN
   */
  parseJwtClaims(payload: {
    userId: string;
    userName: string;
    eventId: string;
    role?: string;
    eventRole?: string;
    channelIds: string[];
    globalRole: string;
  }): AuthenticatedUser {
    let role: UserRole;

    // Priority: globalRole ADMIN overrides all
    if (payload.globalRole === 'ADMIN') {
      role = UserRole.ADMIN;
    } else {
      // Map event role to UserRole
      const eventRole = payload.eventRole || payload.role || 'USER';
      if (eventRole === 'DISPATCH') {
        role = UserRole.DISPATCH;
      } else {
        role = UserRole.GENERAL;
      }
    }

    return {
      userId: payload.userId,
      userName: payload.userName,
      eventId: payload.eventId,
      role,
      channelIds: payload.channelIds || [],
      globalRole: payload.globalRole,
    };
  }

  /**
   * Check if user can join a specific channel
   * Admin users (globalRole ADMIN) can join any channel in their event
   * Other users must have channel in their channelIds array
   */
  canJoinChannel(user: AuthenticatedUser, channelId: string): boolean {
    // Admin bypass: can join any channel
    if (user.globalRole === 'ADMIN') {
      logger.debug(`Admin ${user.userId} can join any channel (bypass check)`);
      return true;
    }

    // Check if channel is in user's assigned channels
    const hasAccess = user.channelIds.includes(channelId);
    logger.debug(
      `User ${user.userId} ${hasAccess ? 'can' : 'cannot'} join channel ${channelId}`
    );
    return hasAccess;
  }

  /**
   * Get permission set based on user role
   * Per user decision: Admin does NOT have PTT priority
   */
  getPermissionSet(user: AuthenticatedUser): PermissionSet {
    switch (user.role) {
      case UserRole.ADMIN:
        return {
          canJoinChannel: true,
          canPtt: true,
          canPriorityPtt: false, // Admin does NOT have PTT priority
          canEmergencyBroadcast: false,
          canForceDisconnect: true,
          canManageChannels: true,
        };

      case UserRole.DISPATCH:
        return {
          canJoinChannel: true,
          canPtt: true,
          canPriorityPtt: true,
          canEmergencyBroadcast: true,
          canForceDisconnect: true,
          canManageChannels: true,
        };

      case UserRole.GENERAL:
        return {
          canJoinChannel: true, // Only assigned channels
          canPtt: true, // Only assigned channels
          canPriorityPtt: false,
          canEmergencyBroadcast: false,
          canForceDisconnect: false,
          canManageChannels: false,
        };

      default:
        logger.warn(`Unknown role ${user.role}, returning restrictive permissions`);
        return {
          canJoinChannel: false,
          canPtt: false,
          canPriorityPtt: false,
          canEmergencyBroadcast: false,
          canForceDisconnect: false,
          canManageChannels: false,
        };
    }
  }

  /**
   * Refresh permissions from Redis
   * Queries Redis for user's current channel list
   * Returns the current list of channel IDs the user is authorized for
   * Called during heartbeat permission refresh
   */
  async refreshPermissions(userId: string, eventId: string): Promise<string[]> {
    try {
      const channelIds = await this.getUserChannelsFromRedis(userId);
      logger.debug(
        `Refreshed permissions for user ${userId}: ${channelIds.length} channels`
      );
      return channelIds;
    } catch (err) {
      logger.error(
        `Failed to refresh permissions for user ${userId}: ${
          err instanceof Error ? err.message : String(err)
        }`
      );
      // Return empty array on error (fail-safe: deny access)
      return [];
    }
  }

  /**
   * Get user's channel list from Redis
   * Reads from Redis key pattern: u.{userId}.g
   * Integrates with control-plane's syncUserChannelsToRedis
   */
  async getUserChannelsFromRedis(userId: string): Promise<string[]> {
    try {
      const client = this.getRedisClient();
      const key = REDIS_KEYS.userChannels(userId);

      // Check if client is connected
      if (!client.isOpen) {
        logger.warn(`Redis client not connected, cannot fetch channels for ${userId}`);
        return [];
      }

      // Redis v4 uses sMembers (async)
      const channels = await client.sMembers(key);
      return channels || [];
    } catch (err) {
      logger.error(
        `Failed to get channels from Redis for user ${userId}: ${
          err instanceof Error ? err.message : String(err)
        }`
      );
      return [];
    }
  }
}
