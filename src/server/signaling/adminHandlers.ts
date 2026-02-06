/**
 * Admin Handlers for administrative operations
 * Implements force-disconnect, ban/unban, and other admin actions
 */

import { SignalingMessage } from '../../shared/protocol';
import { UserRole } from '../../shared/types';
import { AuditLogger, AuditAction } from '../auth/auditLogger';
import { SecurityEventsManager } from '../auth/securityEvents';
import { createLogger } from '../logger';
import { ClientContext } from './websocketServer';

const logger = createLogger('AdminHandlers');

/**
 * Callback type for disconnecting a user
 * Returns true if disconnect successful, false otherwise
 */
type DisconnectUserCallback = (userId: string, reason: string) => boolean;

/**
 * AdminHandlers implements administrative operations
 * Requires DISPATCH or ADMIN role for all operations
 */
export class AdminHandlers {
  private auditLogger: AuditLogger;
  private disconnectUser: DisconnectUserCallback;
  private securityEventsManager: SecurityEventsManager;

  constructor(
    auditLogger: AuditLogger,
    disconnectUser: DisconnectUserCallback,
    securityEventsManager: SecurityEventsManager
  ) {
    this.auditLogger = auditLogger;
    this.disconnectUser = disconnectUser;
    this.securityEventsManager = securityEventsManager;
  }

  /**
   * Handle FORCE_DISCONNECT: Admin/dispatch force-disconnects a user
   */
  async handleForceDisconnect(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      // Validate role is DISPATCH or ADMIN
      if (ctx.role !== UserRole.DISPATCH && ctx.role !== UserRole.ADMIN) {
        logger.warn(`User ${ctx.userId} attempted force-disconnect without permission (role: ${ctx.role})`);

        // Audit log permission denial
        this.auditLogger.log({
          action: AuditAction.PERMISSION_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            attemptedAction: 'force_disconnect',
            reason: 'insufficient_privileges',
          },
        });

        throw new Error('Permission denied: Requires DISPATCH or ADMIN role');
      }

      // Extract targetUserId and reason from message
      const { targetUserId, reason } = message.data as { targetUserId: string; reason?: string };

      if (!targetUserId) {
        throw new Error('targetUserId is required');
      }

      // Prevent self-disconnect
      if (targetUserId === ctx.userId) {
        logger.warn(`User ${ctx.userId} attempted to force-disconnect themselves`);
        throw new Error('Cannot force-disconnect yourself');
      }

      // Call disconnectUser callback
      const success = this.disconnectUser(targetUserId, reason || 'Force disconnected by administrator');

      // Audit log force-disconnect
      this.auditLogger.log({
        action: AuditAction.FORCE_DISCONNECT,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        targetId: targetUserId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          reason: reason || 'no_reason_provided',
          success,
        },
      });

      logger.info(`User ${ctx.userId} force-disconnected ${targetUserId} (reason: ${reason || 'none'})`);
    } catch (err) {
      logger.error(`Error handling FORCE_DISCONNECT: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  /**
   * Handle BAN_USER: Admin/dispatch bans a user temporarily or permanently
   */
  async handleBanUser(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      // Validate role is DISPATCH or ADMIN
      if (ctx.role !== UserRole.DISPATCH && ctx.role !== UserRole.ADMIN) {
        logger.warn(`User ${ctx.userId} attempted ban without permission (role: ${ctx.role})`);

        // Audit log permission denial
        this.auditLogger.log({
          action: AuditAction.PERMISSION_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            attemptedAction: 'ban_user',
            reason: 'insufficient_privileges',
          },
        });

        throw new Error('Permission denied: Requires DISPATCH or ADMIN role');
      }

      // Extract targetUserId, duration, reason from message
      const { targetUserId, duration, reason } = message.data as {
        targetUserId: string;
        duration?: number; // milliseconds, undefined = permanent
        reason?: string;
      };

      if (!targetUserId) {
        throw new Error('targetUserId is required');
      }

      // Call securityEventsManager.banUser()
      await this.securityEventsManager.banUser(
        targetUserId,
        ctx.userId, // bannedBy
        duration,
        reason
      );

      // Force-disconnect banned user if currently connected
      this.disconnectUser(targetUserId, `Banned by ${ctx.userName}: ${reason || 'no reason provided'}`);

      // Audit log security ban
      this.auditLogger.log({
        action: AuditAction.SECURITY_BAN,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        targetId: targetUserId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          duration: duration || 'permanent',
          reason: reason || 'no_reason_provided',
        },
      });

      logger.info(`User ${ctx.userId} banned ${targetUserId} (duration: ${duration || 'permanent'}, reason: ${reason || 'none'})`);
    } catch (err) {
      logger.error(`Error handling BAN_USER: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  /**
   * Handle UNBAN_USER: Admin/dispatch unbans a user
   */
  async handleUnbanUser(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      // Validate role is DISPATCH or ADMIN
      if (ctx.role !== UserRole.DISPATCH && ctx.role !== UserRole.ADMIN) {
        logger.warn(`User ${ctx.userId} attempted unban without permission (role: ${ctx.role})`);

        // Audit log permission denial
        this.auditLogger.log({
          action: AuditAction.PERMISSION_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            attemptedAction: 'unban_user',
            reason: 'insufficient_privileges',
          },
        });

        throw new Error('Permission denied: Requires DISPATCH or ADMIN role');
      }

      // Extract targetUserId from message
      const { targetUserId } = message.data as { targetUserId: string };

      if (!targetUserId) {
        throw new Error('targetUserId is required');
      }

      // Call securityEventsManager.unbanUser()
      await this.securityEventsManager.unbanUser(targetUserId, ctx.userId);

      // Audit log security unban
      this.auditLogger.log({
        action: AuditAction.SECURITY_UNBAN,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        targetId: targetUserId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
        },
      });

      logger.info(`User ${ctx.userId} unbanned ${targetUserId}`);
    } catch (err) {
      logger.error(`Error handling UNBAN_USER: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }
}
