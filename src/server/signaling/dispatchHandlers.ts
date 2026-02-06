/**
 * Dispatch Handlers for priority PTT and emergency broadcast operations
 * Implements Dispatch-specific PTT operations with priority override and multi-channel broadcast
 */

import { SignalingMessage, SignalingType, createMessage } from '../../shared/protocol';
import { UserRole } from '../../shared/types';
import { ChannelStateManager } from '../state/channelState';
import { ProducerConsumerManager } from '../mediasoup/producerConsumerManager';
import { SessionStore } from '../state/sessionStore';
import { AuditLogger, AuditAction } from '../auth/auditLogger';
import { createLogger } from '../logger';
import { config } from '../config';
import { ClientContext } from './websocketServer';
import { SignalingHandlers } from './handlers';

const logger = createLogger('DispatchHandlers');

/**
 * Broadcast function type for channel-wide notifications
 */
type BroadcastFunction = (channelId: string, message: SignalingMessage, excludeUserId?: string) => void;

/**
 * SendToUser function type for user-specific messages
 */
type SendToUserFunction = (userId: string, message: SignalingMessage) => void;

/**
 * Emergency broadcast tracking
 */
interface EmergencyBroadcast {
  dispatchUserId: string;
  channelIds: string[];
  temporaryJoins: Set<string>; // Channels user wasn't in before broadcast
  pausedSpeakers: Map<string, string>; // channelId -> userId of paused speakers
  startTime: number;
}

/**
 * DispatchHandlers class
 * Handles priority PTT and emergency broadcast operations for Dispatch role
 */
export class DispatchHandlers {
  private channelStateManager: ChannelStateManager;
  private producerConsumerManager: ProducerConsumerManager;
  private sessionStore: SessionStore;
  private broadcastToChannel: BroadcastFunction;
  private sendToUser: SendToUserFunction;
  private auditLogger: AuditLogger;
  private signalingHandlers: SignalingHandlers;

  // Track active emergency broadcasts
  private activeEmergencyBroadcasts = new Map<string, EmergencyBroadcast>(); // userId -> broadcast state

  constructor(
    channelStateManager: ChannelStateManager,
    producerConsumerManager: ProducerConsumerManager,
    sessionStore: SessionStore,
    broadcastToChannel: BroadcastFunction,
    sendToUser: SendToUserFunction,
    auditLogger: AuditLogger,
    signalingHandlers: SignalingHandlers
  ) {
    this.channelStateManager = channelStateManager;
    this.producerConsumerManager = producerConsumerManager;
    this.sessionStore = sessionStore;
    this.broadcastToChannel = broadcastToChannel;
    this.sendToUser = sendToUser;
    this.auditLogger = auditLogger;
    this.signalingHandlers = signalingHandlers;
  }

  /**
   * Handle PRIORITY_PTT_START: Dispatch user can interrupt General users
   */
  async handlePriorityPttStart(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { channelId } = message.data as { channelId: string };

      if (!channelId) {
        throw new Error('channelId is required');
      }

      // Validate role: must be DISPATCH (not ADMIN, not GENERAL)
      if (ctx.role !== UserRole.DISPATCH) {
        logger.warn(`Priority PTT denied for ${ctx.userId}: not a Dispatch user (role=${ctx.role})`);

        this.auditLogger.log({
          action: AuditAction.PERMISSION_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          targetId: channelId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            reason: 'not_dispatch_role',
            operation: 'priority_ptt_start',
          },
        });

        this.sendError(ctx, message.id, 'Permission denied: Priority PTT is only available to Dispatch users');
        return;
      }

      // Get current channel state
      const currentState = await this.channelStateManager.getChannelState(channelId);

      // Case 1: Channel idle - normal PTT start
      if (!currentState.isBusy || !currentState.currentSpeaker) {
        logger.info(`Priority PTT: channel ${channelId} idle, proceeding as normal PTT`);

        // Delegate to normal PTT start
        const result = await this.channelStateManager.startPtt(channelId, ctx.userId, ctx.userName);

        if (result.success) {
          // Resume producer
          const producerId = this.signalingHandlers.getUserProducerId(ctx.userId, channelId);
          if (producerId) {
            await this.producerConsumerManager.resumeProducer(producerId);
          }

          // Audit log
          this.auditLogger.log({
            action: AuditAction.PRIORITY_PTT_START,
            actorId: ctx.userId,
            eventId: ctx.eventId,
            targetId: channelId,
            metadata: {
              userName: ctx.userName,
              role: ctx.role,
              channelWasIdle: true,
            },
          });

          // Send success response
          this.sendResponse(ctx, message.id, { success: true, state: result.state });

          // Broadcast speaker change
          this.broadcastToChannel(
            channelId,
            createMessage(SignalingType.SPEAKER_CHANGED, result.state as any),
            ctx.userId
          );
        }

        return;
      }

      // Case 2: Channel busy with General user - override
      const currentSpeakerCtx = await this.getClientContext(currentState.currentSpeaker);

      if (currentSpeakerCtx && currentSpeakerCtx.role === UserRole.GENERAL) {
        logger.info(`Priority PTT: interrupting General user ${currentState.currentSpeaker} in channel ${channelId}`);

        // Pause current speaker's producer
        const currentProducerId = this.signalingHandlers.getUserProducerId(
          currentState.currentSpeaker,
          channelId
        );

        if (currentProducerId) {
          await this.producerConsumerManager.pauseProducer(currentProducerId);
        }

        // Override lock: stop PTT for current speaker, start PTT for Dispatch
        await this.channelStateManager.stopPtt(channelId, currentState.currentSpeaker);
        const result = await this.channelStateManager.startPtt(channelId, ctx.userId, ctx.userName);

        if (result.success) {
          // Resume Dispatch's producer
          const producerId = this.signalingHandlers.getUserProducerId(ctx.userId, channelId);
          if (producerId) {
            await this.producerConsumerManager.resumeProducer(producerId);
          }

          // Send PTT_INTERRUPTED to interrupted user
          this.sendToUser(
            currentState.currentSpeaker,
            createMessage(SignalingType.PTT_INTERRUPTED, {
              channelId,
              interruptedBy: ctx.userName,
              message: `Dispatch ${ctx.userName} has priority`,
            })
          );

          // Audit log interruption
          this.auditLogger.log({
            action: AuditAction.PRIORITY_PTT_INTERRUPTED,
            actorId: ctx.userId,
            eventId: ctx.eventId,
            targetId: channelId,
            metadata: {
              userName: ctx.userName,
              role: ctx.role,
              interruptedUser: currentState.currentSpeaker,
              interruptedUserName: currentState.speakerName,
              interruptedRole: UserRole.GENERAL,
            },
          });

          // Audit log Priority PTT start
          this.auditLogger.log({
            action: AuditAction.PRIORITY_PTT_START,
            actorId: ctx.userId,
            eventId: ctx.eventId,
            targetId: channelId,
            metadata: {
              userName: ctx.userName,
              role: ctx.role,
              interrupted: true,
              interruptedUser: currentState.currentSpeaker,
            },
          });

          // Send success response
          this.sendResponse(ctx, message.id, { success: true, state: result.state });

          // Broadcast SPEAKER_CHANGED
          this.broadcastToChannel(
            channelId,
            createMessage(SignalingType.SPEAKER_CHANGED, result.state as any),
            ctx.userId
          );

          logger.info(`Priority PTT started for ${ctx.userId} in channel ${channelId} (interrupted ${currentState.currentSpeaker})`);
        }

        return;
      }

      // Case 3: Channel busy with another Dispatch user - deny
      if (currentSpeakerCtx && currentSpeakerCtx.role === UserRole.DISPATCH) {
        logger.info(`Priority PTT denied: another Dispatch user ${currentState.currentSpeaker} is speaking in channel ${channelId}`);

        this.auditLogger.log({
          action: AuditAction.PTT_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          targetId: channelId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            reason: 'another_dispatch_speaking',
            currentSpeaker: currentState.currentSpeaker,
            currentSpeakerName: currentState.speakerName,
          },
        });

        this.sendResponse(ctx, message.id, {
          success: false,
          denied: true,
          currentSpeaker: currentState.currentSpeaker,
          currentSpeakerName: currentState.speakerName,
          message: `Another Dispatch user (${currentState.speakerName}) is speaking`,
        });

        return;
      }

      // Case 4: Unknown speaker role - deny as fallback
      logger.warn(`Priority PTT: unknown current speaker role for ${currentState.currentSpeaker} in channel ${channelId}`);
      this.sendError(ctx, message.id, 'Cannot determine current speaker role');

    } catch (err) {
      logger.error(`Error handling PRIORITY_PTT_START: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to start priority PTT');
    }
  }

  /**
   * Handle PRIORITY_PTT_STOP: Stop priority PTT transmission
   */
  async handlePriorityPttStop(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { channelId } = message.data as { channelId: string };

      if (!channelId) {
        throw new Error('channelId is required');
      }

      // Validate role
      if (ctx.role !== UserRole.DISPATCH) {
        this.sendError(ctx, message.id, 'Permission denied: Priority PTT is only available to Dispatch users');
        return;
      }

      // Pause producer
      const producerId = this.signalingHandlers.getUserProducerId(ctx.userId, channelId);
      if (producerId) {
        await this.producerConsumerManager.pauseProducer(producerId);
      }

      // Release speaker lock
      const state = await this.channelStateManager.stopPtt(channelId, ctx.userId);

      // Audit log
      this.auditLogger.log({
        action: AuditAction.PTT_STOP,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        targetId: channelId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          wasPriorityPtt: true,
        },
      });

      // Send response
      this.sendResponse(ctx, message.id, { success: true, state });

      // Broadcast speaker change
      this.broadcastToChannel(
        channelId,
        createMessage(SignalingType.SPEAKER_CHANGED, state as any),
        ctx.userId
      );

      logger.info(`Priority PTT stopped for ${ctx.userId} in channel ${channelId}`);
    } catch (err) {
      logger.error(`Error handling PRIORITY_PTT_STOP: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to stop priority PTT');
    }
  }

  /**
   * Handle EMERGENCY_BROADCAST_START: Broadcast to all event channels
   */
  async handleEmergencyBroadcastStart(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { holdDuration } = message.data as { holdDuration?: number };

      // Validate role: must be DISPATCH
      if (ctx.role !== UserRole.DISPATCH) {
        logger.warn(`Emergency broadcast denied for ${ctx.userId}: not a Dispatch user (role=${ctx.role})`);

        this.auditLogger.log({
          action: AuditAction.PERMISSION_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            reason: 'not_dispatch_role',
            operation: 'emergency_broadcast_start',
          },
        });

        this.sendError(ctx, message.id, 'Permission denied: Emergency broadcast is only available to Dispatch users');
        return;
      }

      // Validate hold duration (2-second guard against accidental activation)
      if (!holdDuration || holdDuration < config.dispatch.emergencyBroadcastHoldMs) {
        logger.warn(`Emergency broadcast denied for ${ctx.userId}: insufficient hold duration (${holdDuration}ms < ${config.dispatch.emergencyBroadcastHoldMs}ms)`);

        this.sendError(ctx, message.id, `Hold button for ${config.dispatch.emergencyBroadcastHoldMs}ms to activate emergency broadcast`);
        return;
      }

      // Check if user already has active broadcast
      if (this.activeEmergencyBroadcasts.has(ctx.userId)) {
        logger.warn(`Emergency broadcast denied for ${ctx.userId}: already has active broadcast`);
        this.sendError(ctx, message.id, 'Emergency broadcast already active');
        return;
      }

      // Get all channels for event
      const channelIds = await this.channelStateManager.getChannelsForEvent(ctx.eventId);

      if (channelIds.length === 0) {
        logger.warn(`Emergency broadcast: no channels found for event ${ctx.eventId}`);
        this.sendError(ctx, message.id, 'No channels found for this event');
        return;
      }

      logger.info(`Emergency broadcast starting for ${ctx.userId} on ${channelIds.length} channels in event ${ctx.eventId}`);

      // Pause ALL active speakers across all channels
      const pausedSpeakers = new Map<string, string>();
      for (const channelId of channelIds) {
        const state = await this.channelStateManager.getChannelState(channelId);

        if (state.currentSpeaker && state.currentSpeaker !== ctx.userId) {
          // Pause speaker's producer
          const producerId = this.signalingHandlers.getUserProducerId(state.currentSpeaker, channelId);
          if (producerId) {
            await this.producerConsumerManager.pauseProducer(producerId);
          }

          // Send PTT_INTERRUPTED
          this.sendToUser(
            state.currentSpeaker,
            createMessage(SignalingType.PTT_INTERRUPTED, {
              channelId,
              interruptedBy: ctx.userName,
              message: `Emergency broadcast by Dispatch ${ctx.userName}`,
            })
          );

          pausedSpeakers.set(channelId, state.currentSpeaker);
        }
      }

      // Identify temporary joins (channels user wasn't already in)
      const temporaryJoins = new Set<string>();
      for (const channelId of channelIds) {
        if (!ctx.channels.has(channelId)) {
          temporaryJoins.add(channelId);
        }
      }

      // Temporary channel joins for unjoined channels
      for (const channelId of temporaryJoins) {
        const joinMessage: SignalingMessage = {
          type: SignalingType.JOIN_CHANNEL,
          data: { channelId },
        };

        // Call internal handleJoinChannel
        await this.signalingHandlers.handleJoinChannel(ctx, joinMessage);
      }

      // Acquire speaker lock on ALL channels
      const acquiredChannels: string[] = [];
      for (const channelId of channelIds) {
        const result = await this.channelStateManager.startPtt(channelId, ctx.userId, ctx.userName);
        if (result.success) {
          acquiredChannels.push(channelId);
        }
      }

      // Resume Dispatch's producer (same producer broadcasts to all channels)
      const producerIds: string[] = [];
      for (const channelId of acquiredChannels) {
        const producerId = this.signalingHandlers.getUserProducerId(ctx.userId, channelId);
        if (producerId && !producerIds.includes(producerId)) {
          await this.producerConsumerManager.resumeProducer(producerId);
          producerIds.push(producerId);
        }
      }

      // Track broadcast state
      const broadcast: EmergencyBroadcast = {
        dispatchUserId: ctx.userId,
        channelIds: acquiredChannels,
        temporaryJoins,
        pausedSpeakers,
        startTime: Date.now(),
      };
      this.activeEmergencyBroadcasts.set(ctx.userId, broadcast);

      // Broadcast EMERGENCY_BROADCAST_START to all channels
      for (const channelId of acquiredChannels) {
        this.broadcastToChannel(
          channelId,
          createMessage(SignalingType.EMERGENCY_BROADCAST_START, {
            dispatchUserId: ctx.userId,
            dispatchUserName: ctx.userName,
            channelId,
          })
        );
      }

      // Audit log
      this.auditLogger.log({
        action: AuditAction.EMERGENCY_BROADCAST_START,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          channelCount: acquiredChannels.length,
          channelIds: acquiredChannels,
          temporaryJoinCount: temporaryJoins.size,
          pausedSpeakerCount: pausedSpeakers.size,
        },
      });

      // Send response
      this.sendResponse(ctx, message.id, {
        success: true,
        channelCount: acquiredChannels.length,
        channelIds: acquiredChannels,
      });

      logger.info(`Emergency broadcast started for ${ctx.userId} on ${acquiredChannels.length} channels`);
    } catch (err) {
      logger.error(`Error handling EMERGENCY_BROADCAST_START: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to start emergency broadcast');
    }
  }

  /**
   * Handle EMERGENCY_BROADCAST_STOP: Stop emergency broadcast
   */
  async handleEmergencyBroadcastStop(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      // Validate role
      if (ctx.role !== UserRole.DISPATCH) {
        this.sendError(ctx, message.id, 'Permission denied: Emergency broadcast is only available to Dispatch users');
        return;
      }

      // Check if user has active broadcast
      const broadcast = this.activeEmergencyBroadcasts.get(ctx.userId);
      if (!broadcast) {
        logger.warn(`Emergency broadcast stop: no active broadcast for ${ctx.userId}`);
        this.sendError(ctx, message.id, 'No active emergency broadcast');
        return;
      }

      logger.info(`Emergency broadcast stopping for ${ctx.userId} on ${broadcast.channelIds.length} channels`);

      // Pause Dispatch's producers
      const producerIds: string[] = [];
      for (const channelId of broadcast.channelIds) {
        const producerId = this.signalingHandlers.getUserProducerId(ctx.userId, channelId);
        if (producerId && !producerIds.includes(producerId)) {
          await this.producerConsumerManager.pauseProducer(producerId);
          producerIds.push(producerId);
        }
      }

      // Release speaker locks on ALL channels
      for (const channelId of broadcast.channelIds) {
        await this.channelStateManager.stopPtt(channelId, ctx.userId);
      }

      // Leave ONLY temporary channels (not permanent ones)
      for (const channelId of broadcast.temporaryJoins) {
        const leaveMessage: SignalingMessage = {
          type: SignalingType.LEAVE_CHANNEL,
          data: { channelId },
        };

        await this.signalingHandlers.handleLeaveChannel(ctx, leaveMessage);
      }

      // Broadcast EMERGENCY_BROADCAST_STOP to all channels
      for (const channelId of broadcast.channelIds) {
        this.broadcastToChannel(
          channelId,
          createMessage(SignalingType.EMERGENCY_BROADCAST_STOP, {
            dispatchUserId: ctx.userId,
            dispatchUserName: ctx.userName,
            channelId,
          })
        );
      }

      // Calculate duration
      const duration = Date.now() - broadcast.startTime;

      // Audit log
      this.auditLogger.log({
        action: AuditAction.EMERGENCY_BROADCAST_STOP,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          channelCount: broadcast.channelIds.length,
          channelIds: broadcast.channelIds,
          temporaryJoinCount: broadcast.temporaryJoins.size,
          durationMs: duration,
        },
      });

      // Clean up broadcast state
      this.activeEmergencyBroadcasts.delete(ctx.userId);

      // Send response
      this.sendResponse(ctx, message.id, {
        success: true,
        channelCount: broadcast.channelIds.length,
        durationMs: duration,
      });

      logger.info(`Emergency broadcast stopped for ${ctx.userId} (duration: ${duration}ms)`);
    } catch (err) {
      logger.error(`Error handling EMERGENCY_BROADCAST_STOP: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to stop emergency broadcast');
    }
  }

  /**
   * Get client context from session store
   * Helper method to look up user's role
   */
  private async getClientContext(userId: string): Promise<ClientContext | null> {
    try {
      // This is a simplified lookup - in production, we'd need access to the websocket server's
      // client connections map. For now, we'll return null if not found.
      // The actual implementation will be completed in Plan 07 when wiring to WebSocketServer.

      logger.debug(`Looking up client context for userId ${userId} (not yet implemented)`);
      return null;
    } catch (err) {
      logger.error(`Error getting client context for ${userId}: ${err instanceof Error ? err.message : String(err)}`);
      return null;
    }
  }

  /**
   * Send response message to client
   */
  private sendResponse(ctx: ClientContext, messageId: string | undefined, data: any): void {
    const response: SignalingMessage = {
      type: SignalingType.CHANNEL_STATE,
      data,
    };

    if (messageId) {
      response.id = messageId;
    }

    if (ctx.ws.readyState === 1) { // WebSocket.OPEN
      ctx.ws.send(JSON.stringify(response));
    }
  }

  /**
   * Send error message to client
   */
  private sendError(ctx: ClientContext, messageId: string | undefined, error: string): void {
    const errorMessage: SignalingMessage = {
      type: SignalingType.ERROR,
      error,
    };

    if (messageId) {
      errorMessage.id = messageId;
    }

    if (ctx.ws.readyState === 1) { // WebSocket.OPEN
      ctx.ws.send(JSON.stringify(errorMessage));
    }
  }
}
