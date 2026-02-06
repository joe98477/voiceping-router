/**
 * Signaling message handlers
 * Implements all signaling operations for WebRTC negotiation and PTT control
 */

import { types as mediasoupTypes } from 'mediasoup';
import { SignalingMessage, SignalingType, createMessage } from '../../shared/protocol';
import { ChannelState } from '../../shared/types';
import { RouterManager } from '../mediasoup/routerManager';
import { TransportManager } from '../mediasoup/transportManager';
import { ProducerConsumerManager } from '../mediasoup/producerConsumerManager';
import { ChannelStateManager } from '../state/channelState';
import { SessionStore } from '../state/sessionStore';
import { createLogger } from '../logger';
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

  // Track user's producer IDs for PTT operations
  private userProducers = new Map<string, string>(); // userId:channelId -> producerId

  constructor(
    routerManager: RouterManager,
    transportManager: TransportManager,
    producerConsumerManager: ProducerConsumerManager,
    channelStateManager: ChannelStateManager,
    sessionStore: SessionStore,
    broadcastToChannel: BroadcastFunction
  ) {
    this.routerManager = routerManager;
    this.transportManager = transportManager;
    this.producerConsumerManager = producerConsumerManager;
    this.channelStateManager = channelStateManager;
    this.sessionStore = sessionStore;
    this.broadcastToChannel = broadcastToChannel;
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

      // Send response to requesting client
      this.sendResponse(ctx, message.id, {
        channelId,
        state: currentState,
      });

      // Notify other channel members
      this.broadcastToChannel(
        channelId,
        createMessage(SignalingType.CHANNEL_STATE, {
          ...currentState,
          action: 'user-joined',
          userId: ctx.userId,
          userName: ctx.userName,
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

      // Send response
      this.sendResponse(ctx, message.id, { channelId, success: true });

      // Notify other channel members
      const updatedState = await this.channelStateManager.getChannelState(channelId);
      this.broadcastToChannel(
        channelId,
        createMessage(SignalingType.CHANNEL_STATE, {
          ...updatedState,
          action: 'user-left',
          userId: ctx.userId,
          userName: ctx.userName,
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

      this.sendResponse(ctx, message.id, { rtpCapabilities });

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

      this.sendResponse(ctx, message.id, { producerId });

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

      // Get user's receive transport
      const transport = this.transportManager.getTransport(producerId);
      if (!transport) {
        throw new Error('Transport not found for consume operation');
      }

      const consumerInfo = await this.producerConsumerManager.createConsumer(
        transport.id,
        producerId,
        rtpCapabilities,
        ctx.userId,
        channelId
      );

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

        // Send success response
        this.sendResponse(ctx, message.id, { success: true, state: result.state });

        // Broadcast speaker change to channel
        this.broadcastToChannel(
          channelId,
          createMessage(SignalingType.SPEAKER_CHANGED, result.state as any),
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

      // Close all transports, producers, consumers
      await this.transportManager.closeUserTransports(ctx.userId);
      await this.producerConsumerManager.closeUserProducersAndConsumers(ctx.userId);

      // Remove from all channels in session store
      for (const channelId of ctx.channels) {
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

      // Clear user producer tracking
      for (const channelId of ctx.channels) {
        const producerKey = `${ctx.userId}:${channelId}`;
        this.userProducers.delete(producerKey);
      }

      logger.info(`Cleanup complete for user ${ctx.userId}`);
    } catch (err) {
      logger.error(`Error during disconnect cleanup for ${ctx.userId}: ${err instanceof Error ? err.message : String(err)}`);
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
