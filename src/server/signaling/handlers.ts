/**
 * Signaling message handlers
 * Implements all signaling operations for WebRTC negotiation and PTT control
 */

import { types as mediasoupTypes } from 'mediasoup';
import { SignalingMessage, SignalingType, createMessage } from '../../shared/protocol';
import { ChannelState, UserRole } from '../../shared/types';
import { RouterManager } from '../mediasoup/routerManager';
import { TransportManager } from '../mediasoup/transportManager';
import { ProducerConsumerManager } from '../mediasoup/producerConsumerManager';
import { ChannelStateManager } from '../state/channelState';
import { SessionStore } from '../state/sessionStore';
import { PermissionManager } from '../auth/permissionManager';
import { AuditLogger, AuditAction } from '../auth/auditLogger';
import { SecurityEventsManager } from '../auth/securityEvents';
import { AdminHandlers } from './adminHandlers';
import { DispatchHandlers } from './dispatchHandlers';
import { createLogger } from '../logger';
import { config } from '../config';
import { ClientContext } from './websocketServer';

const logger = createLogger('SignalingHandlers');

/**
 * Broadcast function type for channel-wide notifications
 */
type BroadcastFunction = (channelId: string, message: SignalingMessage, excludeUserId?: string) => void;

/**
 * SignalingHandlers implements all signaling message handlers
 */
export class SignalingHandlers {
  private routerManager: RouterManager;
  private transportManager: TransportManager;
  private producerConsumerManager: ProducerConsumerManager;
  private channelStateManager: ChannelStateManager;
  private sessionStore: SessionStore;
  private broadcastToChannel: BroadcastFunction;
  private permissionManager: PermissionManager;
  private auditLogger: AuditLogger;
  private adminHandlers?: AdminHandlers;
  private securityEventsManager?: SecurityEventsManager;
  private dispatchHandlers?: DispatchHandlers;

  // Track user's producer IDs for PTT operations
  private userProducers = new Map<string, string>(); // userId:channelId -> producerId

  // Track pending channel removals for users transmitting when permission revoked
  private pendingChannelRemovals = new Map<string, Set<string>>(); // userId -> Set<channelId>

  constructor(
    routerManager: RouterManager,
    transportManager: TransportManager,
    producerConsumerManager: ProducerConsumerManager,
    channelStateManager: ChannelStateManager,
    sessionStore: SessionStore,
    broadcastToChannel: BroadcastFunction,
    permissionManager: PermissionManager,
    auditLogger: AuditLogger
  ) {
    this.routerManager = routerManager;
    this.transportManager = transportManager;
    this.producerConsumerManager = producerConsumerManager;
    this.channelStateManager = channelStateManager;
    this.sessionStore = sessionStore;
    this.broadcastToChannel = broadcastToChannel;
    this.permissionManager = permissionManager;
    this.auditLogger = auditLogger;
  }

  /**
   * Set AdminHandlers instance (called during initialization in Plan 07)
   */
  setAdminHandlers(adminHandlers: AdminHandlers): void {
    this.adminHandlers = adminHandlers;
  }

  /**
   * Set SecurityEventsManager instance (called during initialization in Plan 07)
   */
  setSecurityEventsManager(securityEventsManager: SecurityEventsManager): void {
    this.securityEventsManager = securityEventsManager;
  }

  /**
   * Set DispatchHandlers instance (called during initialization in Plan 07)
   */
  setDispatchHandlers(dispatchHandlers: DispatchHandlers): void {
    this.dispatchHandlers = dispatchHandlers;
  }

  /**
   * Get producer ID for a user in a channel
   * Used by DispatchHandlers to access producer IDs for PTT operations
   *
   * @param userId - User ID
   * @param channelId - Channel ID
   * @returns Producer ID or undefined if not found
   */
  getUserProducerId(userId: string, channelId: string): string | undefined {
    const producerKey = `${userId}:${channelId}`;
    return this.userProducers.get(producerKey);
  }

  /**
   * Handle JOIN_CHANNEL: Add user to channel and subscribe to state updates
   */
  async handleJoinChannel(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { channelId } = message.data as { channelId: string };

      if (!channelId) {
        throw new Error('channelId is required');
      }

      // Permission check: Admin bypasses, others must have channel in authorizedChannels
      const isAdmin = ctx.globalRole === 'ADMIN';
      const hasPermission = isAdmin || ctx.authorizedChannels.has(channelId);

      if (!hasPermission) {
        logger.warn(`User ${ctx.userId} denied access to channel ${channelId} (not authorized)`);

        // Audit log denial
        this.auditLogger.log({
          action: AuditAction.CHANNEL_JOIN_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          targetId: channelId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            reason: 'not_authorized',
          },
        });

        this.sendError(ctx, message.id, 'Permission denied: Not authorized for this channel');
        return;
      }

      // Enforce simultaneous channel limit (role-aware)
      const isDispatchOrAdmin = ctx.role === UserRole.DISPATCH || ctx.role === UserRole.ADMIN;
      const channelLimit = isDispatchOrAdmin
        ? config.channels.dispatchSimultaneousChannelLimit
        : config.channels.defaultSimultaneousChannelLimit;

      if (ctx.channels.size >= channelLimit) {
        logger.warn(`User ${ctx.userId} denied access to channel ${channelId} (simultaneous channel limit)`);

        // Audit log denial
        this.auditLogger.log({
          action: AuditAction.CHANNEL_JOIN_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          targetId: channelId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            reason: 'simultaneous_channel_limit',
            currentChannelCount: ctx.channels.size,
            limit: channelLimit,
          },
        });

        this.sendError(ctx, message.id, `Cannot join more than ${channelLimit} channels simultaneously`);
        return;
      }

      // Check channel user limit
      const channelUserCount = await this.sessionStore.getChannelUserCount(channelId);
      if (channelUserCount >= config.channels.defaultMaxUsersPerChannel) {
        logger.warn(`User ${ctx.userId} denied access to channel ${channelId} (channel full)`);

        // Audit log denial
        this.auditLogger.log({
          action: AuditAction.CHANNEL_JOIN_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          targetId: channelId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            reason: 'channel_full',
            currentUserCount: channelUserCount,
            limit: config.channels.defaultMaxUsersPerChannel,
          },
        });

        this.sendError(ctx, message.id, 'Channel is full');
        return;
      }

      // Add user to channel in session store
      await this.sessionStore.addUserToChannel(ctx.userId, channelId);

      // Add to client context
      ctx.channels.add(channelId);

      // Get or create router for channel
      await this.routerManager.getOrCreateRouter(channelId);

      // Subscribe to channel state events
      await this.channelStateManager.subscribeToChannel(channelId, (state: ChannelState) => {
        // Broadcast speaker change to all channel members
        this.broadcastToChannel(
          channelId,
          createMessage(SignalingType.SPEAKER_CHANGED, state as any)
        );
      });

      // Get current channel state
      const currentState = await this.channelStateManager.getChannelState(channelId);

      // Audit log successful join
      this.auditLogger.log({
        action: AuditAction.CHANNEL_JOIN,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        targetId: channelId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
        },
      });

      // Get updated user count after join
      const updatedUserCount = await this.sessionStore.getChannelUserCount(channelId);

      // Send response to requesting client
      this.sendResponse(ctx, message.id, {
        channelId,
        state: currentState,
        userCount: updatedUserCount,
      });

      // Notify other channel members
      this.broadcastToChannel(
        channelId,
        createMessage(SignalingType.CHANNEL_STATE, {
          ...currentState,
          action: 'user-joined',
          userId: ctx.userId,
          userName: ctx.userName,
          userCount: updatedUserCount,
        }),
        ctx.userId
      );

      logger.info(`User ${ctx.userId} joined channel ${channelId}`);
    } catch (err) {
      logger.error(`Error handling JOIN_CHANNEL: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to join channel');
    }
  }

  /**
   * Handle LEAVE_CHANNEL: Remove user from channel and clean up resources
   */
  async handleLeaveChannel(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { channelId } = message.data as { channelId: string };

      if (!channelId) {
        throw new Error('channelId is required');
      }

      // Release speaker lock if user was speaking
      const currentState = await this.channelStateManager.getChannelState(channelId);
      if (currentState.currentSpeaker === ctx.userId) {
        await this.channelStateManager.stopPtt(channelId, ctx.userId);
      }

      // Remove user producer tracking
      const producerKey = `${ctx.userId}:${channelId}`;
      this.userProducers.delete(producerKey);

      // Remove from channel in session store
      await this.sessionStore.removeUserFromChannel(ctx.userId, channelId);

      // Remove from client context
      ctx.channels.delete(channelId);

      // Unsubscribe from channel events
      await this.channelStateManager.unsubscribeFromChannel(channelId);

      // Audit log channel leave
      this.auditLogger.log({
        action: AuditAction.CHANNEL_LEAVE,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        targetId: channelId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
        },
      });

      // Send response
      this.sendResponse(ctx, message.id, { channelId, success: true });

      // Notify other channel members
      const updatedState = await this.channelStateManager.getChannelState(channelId);
      const leaveUserCount = await this.sessionStore.getChannelUserCount(channelId);
      this.broadcastToChannel(
        channelId,
        createMessage(SignalingType.CHANNEL_STATE, {
          ...updatedState,
          action: 'user-left',
          userId: ctx.userId,
          userName: ctx.userName,
          userCount: leaveUserCount,
        }),
        ctx.userId
      );

      logger.info(`User ${ctx.userId} left channel ${channelId}`);
    } catch (err) {
      logger.error(`Error handling LEAVE_CHANNEL: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to leave channel');
    }
  }

  /**
   * Handle GET_ROUTER_CAPABILITIES: Return router RTP capabilities for client Device.load()
   */
  async handleGetRouterCapabilities(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { channelId } = message.data as { channelId: string };

      if (!channelId) {
        throw new Error('channelId is required');
      }

      const rtpCapabilities = this.routerManager.getRtpCapabilities(channelId);

      if (!rtpCapabilities) {
        throw new Error(`Router not found for channel ${channelId}`);
      }

      this.sendResponse(ctx, message.id, { routerRtpCapabilities: rtpCapabilities });

      logger.info(`Sent router capabilities to ${ctx.userId} for channel ${channelId}`);
    } catch (err) {
      logger.error(`Error handling GET_ROUTER_CAPABILITIES: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to get router capabilities');
    }
  }

  /**
   * Handle CREATE_TRANSPORT: Create WebRTC transport for sending or receiving audio
   */
  async handleCreateTransport(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { channelId, direction } = message.data as { channelId: string; direction: 'send' | 'recv' };

      if (!channelId || !direction) {
        throw new Error('channelId and direction are required');
      }

      if (direction !== 'send' && direction !== 'recv') {
        throw new Error('direction must be "send" or "recv"');
      }

      const transportOptions = await this.transportManager.createWebRtcTransport(
        channelId,
        ctx.userId,
        direction
      );

      this.sendResponse(ctx, message.id, transportOptions);

      logger.info(`Created ${direction} transport for ${ctx.userId} in channel ${channelId}`);
    } catch (err) {
      logger.error(`Error handling CREATE_TRANSPORT: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to create transport');
    }
  }

  /**
   * Handle CONNECT_TRANSPORT: Connect transport DTLS layer
   */
  async handleConnectTransport(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { transportId, dtlsParameters } = message.data as {
        transportId: string;
        dtlsParameters: mediasoupTypes.DtlsParameters;
      };

      if (!transportId || !dtlsParameters) {
        throw new Error('transportId and dtlsParameters are required');
      }

      await this.transportManager.connectTransport(transportId, dtlsParameters);

      this.sendResponse(ctx, message.id, { success: true });

      logger.info(`Connected transport ${transportId} for ${ctx.userId}`);
    } catch (err) {
      logger.error(`Error handling CONNECT_TRANSPORT: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to connect transport');
    }
  }

  /**
   * Handle PRODUCE: Create audio producer on transport (starts paused)
   */
  async handleProduce(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { transportId, kind, rtpParameters, channelId } = message.data as {
        transportId: string;
        kind: mediasoupTypes.MediaKind;
        rtpParameters: mediasoupTypes.RtpParameters;
        channelId: string;
      };

      if (!transportId || !kind || !rtpParameters || !channelId) {
        throw new Error('transportId, kind, rtpParameters, and channelId are required');
      }

      const producerId = await this.producerConsumerManager.createProducer(
        transportId,
        kind,
        rtpParameters,
        ctx.userId,
        channelId
      );

      // Track producer for PTT operations
      const producerKey = `${ctx.userId}:${channelId}`;
      this.userProducers.set(producerKey, producerId);

      this.sendResponse(ctx, message.id, { id: producerId });

      logger.info(`Created producer ${producerId} for ${ctx.userId} in channel ${channelId}`);
    } catch (err) {
      logger.error(`Error handling PRODUCE: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to create producer');
    }
  }

  /**
   * Handle CONSUME: Create audio consumer for a producer
   */
  async handleConsume(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { channelId, producerId, rtpCapabilities } = message.data as {
        channelId: string;
        producerId: string;
        rtpCapabilities: mediasoupTypes.RtpCapabilities;
      };

      if (!channelId || !producerId || !rtpCapabilities) {
        throw new Error('channelId, producerId, and rtpCapabilities are required');
      }

      // Get receiver's recv transport
      const transport = this.transportManager.getUserChannelTransport(ctx.userId, channelId, 'recv');
      if (!transport) {
        throw new Error('Receive transport not found for consume operation');
      }

      const consumerInfo = await this.producerConsumerManager.createConsumer(
        transport.id,
        producerId,
        rtpCapabilities,
        ctx.userId,
        channelId
      );

      // Resume server-side consumer so RTP flows from producer to client
      // Client-side consumer.resume() only resumes locally; server must resume independently
      await this.producerConsumerManager.resumeConsumer(consumerInfo.id);

      this.sendResponse(ctx, message.id, consumerInfo);

      logger.info(`Created consumer ${consumerInfo.id} for ${ctx.userId} consuming ${producerId}`);
    } catch (err) {
      logger.error(`Error handling CONSUME: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to create consumer');
    }
  }

  /**
   * Handle PTT_START: Acquire speaker lock and resume producer
   */
  async handlePttStart(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { channelId } = message.data as { channelId: string };

      if (!channelId) {
        throw new Error('channelId is required');
      }

      // Attempt to acquire speaker lock
      const result = await this.channelStateManager.startPtt(channelId, ctx.userId, ctx.userName);

      if (result.success) {
        // Lock acquired - resume producer
        const producerKey = `${ctx.userId}:${channelId}`;
        const producerId = this.userProducers.get(producerKey);

        if (producerId) {
          await this.producerConsumerManager.resumeProducer(producerId);
          logger.info(`PTT started for ${ctx.userId} in channel ${channelId}`);
        } else {
          logger.warn(`No producer found for ${ctx.userId} in channel ${channelId}`);
        }

        // Audit log PTT start
        this.auditLogger.log({
          action: AuditAction.PTT_START,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          targetId: channelId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
          },
        });

        // Send success response
        this.sendResponse(ctx, message.id, { success: true, state: result.state });

        // Broadcast speaker change to channel (include producerId for audio consumption)
        this.broadcastToChannel(
          channelId,
          createMessage(SignalingType.SPEAKER_CHANGED, { ...result.state, producerId } as any),
          ctx.userId
        );
      } else {
        // Lock denied - send PTT_DENIED to requesting client
        const deniedMessage = createMessage(SignalingType.PTT_DENIED, {
          channelId,
          currentSpeaker: result.state.currentSpeaker,
          currentSpeakerName: result.state.speakerName,
          message: `${result.state.speakerName || 'Another user'} is speaking`,
        });

        if (message.id) {
          deniedMessage.id = message.id;
        }

        // Audit log PTT denial
        this.auditLogger.log({
          action: AuditAction.PTT_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          targetId: channelId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            currentSpeaker: result.state.currentSpeaker,
            currentSpeakerName: result.state.speakerName,
          },
        });

        this.sendResponse(ctx, message.id, {
          success: false,
          denied: true,
          currentSpeaker: result.state.currentSpeaker,
          currentSpeakerName: result.state.speakerName,
        });

        logger.info(`PTT denied for ${ctx.userId} in channel ${channelId} (${result.state.speakerName} is speaking)`);
      }
    } catch (err) {
      logger.error(`Error handling PTT_START: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to start PTT');
    }
  }

  /**
   * Handle PTT_STOP: Release speaker lock and pause producer
   */
  async handlePttStop(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      const { channelId } = message.data as { channelId: string };

      if (!channelId) {
        throw new Error('channelId is required');
      }

      // Pause producer
      const producerKey = `${ctx.userId}:${channelId}`;
      const producerId = this.userProducers.get(producerKey);

      if (producerId) {
        await this.producerConsumerManager.pauseProducer(producerId);
      }

      // Release speaker lock
      const state = await this.channelStateManager.stopPtt(channelId, ctx.userId);

      // Audit log PTT stop
      this.auditLogger.log({
        action: AuditAction.PTT_STOP,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        targetId: channelId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
        },
      });

      // Check for pending channel removals after PTT stop
      const pendingRemovals = this.pendingChannelRemovals.get(ctx.userId);
      if (pendingRemovals && pendingRemovals.has(channelId)) {
        logger.info(`Executing deferred channel removal for ${ctx.userId} from channel ${channelId}`);
        pendingRemovals.delete(channelId);
        if (pendingRemovals.size === 0) {
          this.pendingChannelRemovals.delete(ctx.userId);
        }

        // Execute the deferred removal
        await this.handlePermissionRevocation(ctx, channelId, true);
      }

      // Send response
      this.sendResponse(ctx, message.id, { success: true, state });

      // Broadcast speaker change to channel
      this.broadcastToChannel(
        channelId,
        createMessage(SignalingType.SPEAKER_CHANGED, state as any),
        ctx.userId
      );

      logger.info(`PTT stopped for ${ctx.userId} in channel ${channelId}`);
    } catch (err) {
      logger.error(`Error handling PTT_STOP: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to stop PTT');
    }
  }

  /**
   * Handle PRIORITY_PTT_START: Delegate to DispatchHandlers
   * Validates Dispatch role before delegating
   */
  async handlePriorityPttStart(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    // Validate role
    if (ctx.role !== UserRole.DISPATCH) {
      logger.warn(`PRIORITY_PTT_START denied for ${ctx.userId}: not a Dispatch user`);

      this.auditLogger.log({
        action: AuditAction.PERMISSION_DENIED,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          operation: 'priority_ptt_start',
        },
      });

      this.sendError(ctx, message.id, 'Permission denied: Priority PTT is only available to Dispatch users');
      return;
    }

    // Delegate to DispatchHandlers
    if (!this.dispatchHandlers) {
      logger.warn('PRIORITY_PTT_START: DispatchHandlers not yet wired');
      this.sendError(ctx, message.id, 'Priority PTT not yet implemented');
      return;
    }

    await this.dispatchHandlers.handlePriorityPttStart(ctx, message);
  }

  /**
   * Handle PRIORITY_PTT_STOP: Delegate to DispatchHandlers
   * Validates Dispatch role before delegating
   */
  async handlePriorityPttStop(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    // Validate role
    if (ctx.role !== UserRole.DISPATCH) {
      logger.warn(`PRIORITY_PTT_STOP denied for ${ctx.userId}: not a Dispatch user`);

      this.auditLogger.log({
        action: AuditAction.PERMISSION_DENIED,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          operation: 'priority_ptt_stop',
        },
      });

      this.sendError(ctx, message.id, 'Permission denied: Priority PTT is only available to Dispatch users');
      return;
    }

    // Delegate to DispatchHandlers
    if (!this.dispatchHandlers) {
      logger.warn('PRIORITY_PTT_STOP: DispatchHandlers not yet wired');
      this.sendError(ctx, message.id, 'Priority PTT not yet implemented');
      return;
    }

    await this.dispatchHandlers.handlePriorityPttStop(ctx, message);
  }

  /**
   * Handle EMERGENCY_BROADCAST_START: Delegate to DispatchHandlers
   * Validates Dispatch role before delegating
   */
  async handleEmergencyBroadcastStart(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    // Validate role
    if (ctx.role !== UserRole.DISPATCH) {
      logger.warn(`EMERGENCY_BROADCAST_START denied for ${ctx.userId}: not a Dispatch user`);

      this.auditLogger.log({
        action: AuditAction.PERMISSION_DENIED,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          operation: 'emergency_broadcast_start',
        },
      });

      this.sendError(ctx, message.id, 'Permission denied: Emergency broadcast is only available to Dispatch users');
      return;
    }

    // Delegate to DispatchHandlers
    if (!this.dispatchHandlers) {
      logger.warn('EMERGENCY_BROADCAST_START: DispatchHandlers not yet wired');
      this.sendError(ctx, message.id, 'Emergency broadcast not yet implemented');
      return;
    }

    await this.dispatchHandlers.handleEmergencyBroadcastStart(ctx, message);
  }

  /**
   * Handle EMERGENCY_BROADCAST_STOP: Delegate to DispatchHandlers
   * Validates Dispatch role before delegating
   */
  async handleEmergencyBroadcastStop(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    // Validate role
    if (ctx.role !== UserRole.DISPATCH) {
      logger.warn(`EMERGENCY_BROADCAST_STOP denied for ${ctx.userId}: not a Dispatch user`);

      this.auditLogger.log({
        action: AuditAction.PERMISSION_DENIED,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          operation: 'emergency_broadcast_stop',
        },
      });

      this.sendError(ctx, message.id, 'Permission denied: Emergency broadcast is only available to Dispatch users');
      return;
    }

    // Delegate to DispatchHandlers
    if (!this.dispatchHandlers) {
      logger.warn('EMERGENCY_BROADCAST_STOP: DispatchHandlers not yet wired');
      this.sendError(ctx, message.id, 'Emergency broadcast not yet implemented');
      return;
    }

    await this.dispatchHandlers.handleEmergencyBroadcastStop(ctx, message);
  }

  /**
   * Handle FORCE_DISCONNECT: Delegate to AdminHandlers
   * Validates DISPATCH or ADMIN role before delegating
   */
  async handleForceDisconnect(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      // Validate role
      if (ctx.role !== UserRole.DISPATCH && ctx.role !== UserRole.ADMIN) {
        logger.warn(`FORCE_DISCONNECT denied for ${ctx.userId}: not a Dispatch or Admin user`);

        this.auditLogger.log({
          action: AuditAction.PERMISSION_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            operation: 'force_disconnect',
          },
        });

        this.sendError(ctx, message.id, 'Permission denied: Force disconnect is only available to Dispatch or Admin users');
        return;
      }

      // Delegate to AdminHandlers if available
      if (!this.adminHandlers) {
        logger.warn('FORCE_DISCONNECT: AdminHandlers not yet wired (will be completed in Plan 07)');
        this.sendError(ctx, message.id, 'Force disconnect not yet implemented');
        return;
      }

      await this.adminHandlers.handleForceDisconnect(ctx, message);
      this.sendResponse(ctx, message.id, { success: true });
    } catch (err) {
      logger.error(`Error handling FORCE_DISCONNECT: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to force disconnect user');
    }
  }

  /**
   * Handle BAN_USER: Delegate to AdminHandlers
   * Validates DISPATCH or ADMIN role before delegating
   */
  async handleBanUser(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      // Validate role
      if (ctx.role !== UserRole.DISPATCH && ctx.role !== UserRole.ADMIN) {
        logger.warn(`BAN_USER denied for ${ctx.userId}: not a Dispatch or Admin user`);

        this.auditLogger.log({
          action: AuditAction.PERMISSION_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            operation: 'ban_user',
          },
        });

        this.sendError(ctx, message.id, 'Permission denied: Ban user is only available to Dispatch or Admin users');
        return;
      }

      // Delegate to AdminHandlers if available
      if (!this.adminHandlers) {
        logger.warn('BAN_USER: AdminHandlers not yet wired (will be completed in Plan 07)');
        this.sendError(ctx, message.id, 'Ban user not yet implemented');
        return;
      }

      await this.adminHandlers.handleBanUser(ctx, message);
      this.sendResponse(ctx, message.id, { success: true });
    } catch (err) {
      logger.error(`Error handling BAN_USER: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to ban user');
    }
  }

  /**
   * Handle UNBAN_USER: Delegate to AdminHandlers
   * Validates DISPATCH or ADMIN role before delegating
   */
  async handleUnbanUser(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      // Validate role
      if (ctx.role !== UserRole.DISPATCH && ctx.role !== UserRole.ADMIN) {
        logger.warn(`UNBAN_USER denied for ${ctx.userId}: not a Dispatch or Admin user`);

        this.auditLogger.log({
          action: AuditAction.PERMISSION_DENIED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            operation: 'unban_user',
          },
        });

        this.sendError(ctx, message.id, 'Permission denied: Unban user is only available to Dispatch or Admin users');
        return;
      }

      // Delegate to AdminHandlers if available
      if (!this.adminHandlers) {
        logger.warn('UNBAN_USER: AdminHandlers not yet wired (will be completed in Plan 07)');
        this.sendError(ctx, message.id, 'Unban user not yet implemented');
        return;
      }

      await this.adminHandlers.handleUnbanUser(ctx, message);
      this.sendResponse(ctx, message.id, { success: true });
    } catch (err) {
      logger.error(`Error handling UNBAN_USER: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx, message.id, err instanceof Error ? err.message : 'Failed to unban user');
    }
  }

  /**
   * Handle PING: Respond with PONG (application-level heartbeat)
   */
  async handlePing(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    this.sendResponse(ctx, message.id, { type: SignalingType.PONG });
  }

  /**
   * Handle disconnect: Clean up all user resources
   */
  async handleDisconnect(ctx: ClientContext): Promise<void> {
    try {
      logger.info(`Cleaning up resources for disconnected user ${ctx.userId}`);

      // Audit log disconnect (AUTH_LOGOUT)
      this.auditLogger.log({
        action: AuditAction.AUTH_LOGOUT,
        actorId: ctx.userId,
        eventId: ctx.eventId,
        metadata: {
          userName: ctx.userName,
          role: ctx.role,
          connectionId: ctx.connectionId,
          channelCount: ctx.channels.size,
        },
      });

      // Release speaker locks in all channels user was in
      for (const channelId of ctx.channels) {
        const currentState = await this.channelStateManager.getChannelState(channelId);
        if (currentState.currentSpeaker === ctx.userId) {
          await this.channelStateManager.stopPtt(channelId, ctx.userId);

          // Broadcast speaker change
          const updatedState = await this.channelStateManager.getChannelState(channelId);
          this.broadcastToChannel(
            channelId,
            createMessage(SignalingType.SPEAKER_CHANGED, updatedState as any)
          );
        }
      }

      // Close transports, producers, consumers per-channel (not globally for user)
      // Each channel has its own WebSocket connection, so only clean up THIS connection's channels
      for (const channelId of ctx.channels) {
        await this.transportManager.closeUserChannelTransports(ctx.userId, channelId);
        await this.producerConsumerManager.closeUserChannelProducersAndConsumers(ctx.userId, channelId);

        // Clear user producer tracking for this channel
        const producerKey = `${ctx.userId}:${channelId}`;
        this.userProducers.delete(producerKey);

        // Remove from session store
        await this.sessionStore.removeUserFromChannel(ctx.userId, channelId);

        // Notify channel members
        const updatedState = await this.channelStateManager.getChannelState(channelId);
        this.broadcastToChannel(
          channelId,
          createMessage(SignalingType.CHANNEL_STATE, {
            ...updatedState,
            action: 'user-disconnected',
            userId: ctx.userId,
            userName: ctx.userName,
          })
        );
      }

      // Clear pending channel removals
      this.pendingChannelRemovals.delete(ctx.userId);

      logger.info(`Cleanup complete for user ${ctx.userId}`);
    } catch (err) {
      logger.error(`Error during disconnect cleanup for ${ctx.userId}: ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  /**
   * Handle permission revocation for a channel
   * If user is transmitting, defer removal until PTT stop
   * If user is not transmitting (or force=true), remove immediately
   *
   * @param ctx - Client context
   * @param channelId - Channel to revoke access to
   * @param force - Force immediate removal even if transmitting
   */
  async handlePermissionRevocation(ctx: ClientContext, channelId: string, force: boolean): Promise<void> {
    try {
      // Check if user is currently in this channel
      if (!ctx.channels.has(channelId)) {
        logger.debug(`User ${ctx.userId} not in channel ${channelId}, no revocation needed`);
        return;
      }

      // Check if user is currently transmitting in this channel
      const currentState = await this.channelStateManager.getChannelState(channelId);
      const isTransmitting = currentState.currentSpeaker === ctx.userId;

      if (force || !isTransmitting) {
        // Remove immediately
        logger.info(`Immediately removing ${ctx.userId} from channel ${channelId} (force=${force}, transmitting=${isTransmitting})`);

        // Synthesize a LEAVE_CHANNEL message for internal handling
        const leaveMessage: SignalingMessage = {
          type: SignalingType.LEAVE_CHANNEL,
          data: { channelId },
        };

        await this.handleLeaveChannel(ctx, leaveMessage);

        // Audit log permission revocation
        this.auditLogger.log({
          action: AuditAction.PERMISSION_REVOKED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          targetId: channelId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            force,
            wasTransmitting: isTransmitting,
          },
        });
      } else {
        // User is transmitting - defer removal until PTT stop
        logger.info(`Deferring removal of ${ctx.userId} from channel ${channelId} (currently transmitting)`);

        // Add to pending removals
        let pendingRemovals = this.pendingChannelRemovals.get(ctx.userId);
        if (!pendingRemovals) {
          pendingRemovals = new Set();
          this.pendingChannelRemovals.set(ctx.userId, pendingRemovals);
        }
        pendingRemovals.add(channelId);

        // Audit log deferred revocation
        this.auditLogger.log({
          action: AuditAction.PERMISSION_REVOCATION_DEFERRED,
          actorId: ctx.userId,
          eventId: ctx.eventId,
          targetId: channelId,
          metadata: {
            userName: ctx.userName,
            role: ctx.role,
            reason: 'user_transmitting',
          },
        });

        // Send PERMISSION_UPDATE to notify client (UI can show warning)
        const updateMessage = createMessage(SignalingType.PERMISSION_UPDATE, {
          removed: [channelId],
          pendingRemoval: true,
          message: 'Access to this channel will be revoked when you stop transmitting',
        });
        this.sendResponse(ctx, undefined, updateMessage);
      }
    } catch (err) {
      logger.error(`Error handling permission revocation for ${ctx.userId} in channel ${channelId}: ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  /**
   * Send response message to client
   */
  private sendResponse(ctx: ClientContext, messageId: string | undefined, data: any): void {
    const response: SignalingMessage = {
      type: SignalingType.CHANNEL_STATE, // Default type, handlers may override
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
