/**
 * Audit Logger for security event tracking
 * Records all auth and security events to Redis with optional database export
 */

import { RedisClientType } from 'redis';
import { createLogger } from '../logger';
import { AuditEvent } from '../../shared/types';
import { randomUUID } from 'crypto';

const logger = createLogger('AuditLogger');

/**
 * Audit action enumeration
 * Defines all security and auth events that can be logged
 */
export enum AuditAction {
  // Authentication events
  AUTH_LOGIN = 'auth.login',
  AUTH_LOGOUT = 'auth.logout',
  AUTH_TOKEN_REFRESH = 'auth.token_refresh',

  // Permission events
  PERMISSION_CHECK = 'permission.check',
  PERMISSION_DENIED = 'permission.denied',
  PERMISSION_GRANTED = 'permission.granted',

  // Channel events
  CHANNEL_JOIN = 'channel.join',
  CHANNEL_LEAVE = 'channel.leave',
  CHANNEL_JOIN_DENIED = 'channel.join_denied',

  // PTT events
  PTT_START = 'ptt.start',
  PTT_STOP = 'ptt.stop',
  PTT_DENIED = 'ptt.denied',

  // Priority PTT events
  PRIORITY_PTT_START = 'priority_ptt.start',
  PRIORITY_PTT_INTERRUPTED = 'priority_ptt.interrupted',

  // Emergency broadcast events
  EMERGENCY_BROADCAST_START = 'emergency_broadcast.start',
  EMERGENCY_BROADCAST_STOP = 'emergency_broadcast.stop',

  // Force disconnect events
  FORCE_DISCONNECT = 'force_disconnect',

  // Rate limiting events
  RATE_LIMIT_HIT = 'rate_limit.hit',
  RATE_LIMIT_BLOCK = 'rate_limit.block',

  // Role events
  ROLE_CHANGE = 'role.change',

  // Security events
  SECURITY_BAN = 'security.ban',
  SECURITY_UNBAN = 'security.unban',
}

/**
 * Redis key patterns for audit log storage
 */
const REDIS_KEYS = {
  auditLog: 'audit:log',
  auditActor: (actorId: string) => `audit:actor:${actorId}`,
  auditExportChannel: 'vp:audit_export',
} as const;

/**
 * Maximum number of audit events to keep in Redis
 */
const MAX_AUDIT_LOG_SIZE = 10000;

/**
 * Audit Logger class
 * Non-blocking audit logging with Redis storage
 */
export class AuditLogger {
  private getRedisClient: () => RedisClientType;

  constructor(getRedisClient: () => RedisClientType) {
    this.getRedisClient = getRedisClient;
  }

  /**
   * Log an audit event
   * Non-blocking: failures logged but never throw
   * Auto-generates id (UUID) and timestamp
   */
  log(event: Partial<AuditEvent> & { action: AuditAction }): void {
    // Run async operation without blocking caller
    this.logAsync(event).catch((err) => {
      // Never throw from log() method
      logger.error(
        `Audit logging failed: ${err instanceof Error ? err.message : String(err)}`
      );
    });
  }

  /**
   * Internal async log implementation
   * Wrapped in try/catch by log() method
   */
  private async logAsync(
    event: Partial<AuditEvent> & { action: AuditAction }
  ): Promise<void> {
    try {
      const client = this.getRedisClient();

      // Check if client is connected
      if (!client.isOpen) {
        logger.warn('Redis client not connected, skipping audit log');
        return;
      }

      // Build complete audit event
      const auditEvent: AuditEvent = {
        id: event.id || randomUUID(),
        actorId: event.actorId || 'system',
        eventId: event.eventId || null,
        action: event.action,
        targetId: event.targetId || null,
        metadata: event.metadata || {},
        timestamp: event.timestamp || Date.now(),
      };

      // Serialize event
      const serialized = JSON.stringify(auditEvent);

      // Store in main audit log list
      await client.lPush(REDIS_KEYS.auditLog, serialized);

      // Trim list to max size
      await client.lTrim(REDIS_KEYS.auditLog, 0, MAX_AUDIT_LOG_SIZE - 1);

      // Store in actor-specific sorted set for fast actor queries
      if (auditEvent.actorId) {
        const actorKey = REDIS_KEYS.auditActor(auditEvent.actorId);
        await client.zAdd(actorKey, {
          score: auditEvent.timestamp,
          value: serialized,
        });

        // Expire actor index after 30 days
        await client.expire(actorKey, 30 * 24 * 60 * 60);
      }

      logger.debug(`Audit event logged: ${auditEvent.action} by ${auditEvent.actorId}`);
    } catch (err) {
      // Catch all errors - audit logging must never break core functionality
      logger.error(
        `Failed to write audit event: ${err instanceof Error ? err.message : String(err)}`
      );
    }
  }

  /**
   * Get recent audit events
   * Returns last N events from Redis list
   */
  async getRecentEvents(limit: number = 100): Promise<AuditEvent[]> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        logger.warn('Redis client not connected, cannot fetch audit events');
        return [];
      }

      // Get last N events from list
      const events = await client.lRange(REDIS_KEYS.auditLog, 0, limit - 1);

      // Parse and return
      return events.map((json) => JSON.parse(json) as AuditEvent);
    } catch (err) {
      logger.error(
        `Failed to get recent audit events: ${
          err instanceof Error ? err.message : String(err)
        }`
      );
      return [];
    }
  }

  /**
   * Get audit events by actor
   * Returns events for a specific actor (user)
   */
  async getEventsByActor(actorId: string, limit: number = 100): Promise<AuditEvent[]> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        logger.warn('Redis client not connected, cannot fetch actor events');
        return [];
      }

      const actorKey = REDIS_KEYS.auditActor(actorId);

      // Get events from sorted set (newest first)
      const events = await client.zRange(actorKey, 0, limit - 1, { REV: true });

      // Parse and return
      return events.map((json) => JSON.parse(json) as AuditEvent);
    } catch (err) {
      logger.error(
        `Failed to get audit events for actor ${actorId}: ${
          err instanceof Error ? err.message : String(err)
        }`
      );
      return [];
    }
  }

  /**
   * Export audit events to database
   * Batch export Redis audit events to control-plane via Redis pub/sub
   * The control-plane can subscribe and persist to Prisma AuditLog table
   * This is a periodic operation, not called on every log
   */
  async exportToDatabase(): Promise<void> {
    try {
      const client = this.getRedisClient();

      if (!client.isOpen) {
        logger.warn('Redis client not connected, cannot export audit events');
        return;
      }

      // Get all events from Redis list
      const events = await client.lRange(REDIS_KEYS.auditLog, 0, -1);

      if (events.length === 0) {
        logger.debug('No audit events to export');
        return;
      }

      // Publish export message to control-plane
      const exportMessage = JSON.stringify({
        type: 'audit_export',
        events: events.map((json) => JSON.parse(json)),
        timestamp: Date.now(),
      });

      await client.publish(REDIS_KEYS.auditExportChannel, exportMessage);

      logger.info(`Exported ${events.length} audit events to control-plane`);
    } catch (err) {
      logger.error(
        `Failed to export audit events: ${
          err instanceof Error ? err.message : String(err)
        }`
      );
    }
  }
}
