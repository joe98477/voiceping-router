/**
 * Channel state management for PTT operations
 * Manages speaker lock lifecycle and pub/sub notifications
 */

import { createClient, RedisClientType } from 'redis';
import { ChannelState } from '../../shared/types';
import { config } from '../config';
import * as speakerLock from './speakerLock';

/**
 * Channel state manager
 * Provides high-level PTT operations with speaker lock integration
 */
export class ChannelStateManager {
  private pubClient: RedisClientType;
  private subClient: RedisClientType;
  private subscriptions: Map<string, ((state: ChannelState) => void)[]> = new Map();

  constructor() {
    // Create dedicated clients for pub/sub (Redis v4 requirement)
    this.pubClient = createClient({ url: config.redis.url });
    this.subClient = createClient({ url: config.redis.url });

    // Setup error handlers
    this.pubClient.on('error', (err) => console.error('Pub client error:', err));
    this.subClient.on('error', (err) => console.error('Sub client error:', err));
  }

  /**
   * Initialize pub/sub clients
   * Must be called before using channel state manager
   */
  async initialize(): Promise<void> {
    try {
      await Promise.all([
        this.pubClient.connect(),
        this.subClient.connect(),
      ]);
      console.info('Channel state manager initialized');
    } catch (err) {
      console.error('Failed to initialize channel state manager:', err);
      throw err;
    }
  }

  /**
   * Shutdown pub/sub clients
   */
  async shutdown(): Promise<void> {
    try {
      await Promise.all([
        this.pubClient.disconnect(),
        this.subClient.disconnect(),
      ]);
      console.info('Channel state manager shut down');
    } catch (err) {
      console.error('Error shutting down channel state manager:', err);
      throw err;
    }
  }

  /**
   * Get Redis pub/sub event key for channel
   */
  private getEventKey(channelId: string): string {
    return `channel:${channelId}:events`;
  }

  /**
   * Start PTT transmission for a user
   * Attempts to acquire speaker lock and notifies listeners
   *
   * @param channelId - Channel to start PTT in
   * @param userId - User starting PTT
   * @param userName - User's display name
   * @returns Success status and current channel state
   */
  async startPtt(
    channelId: string,
    userId: string,
    userName: string
  ): Promise<{ success: boolean; state: ChannelState }> {
    try {
      // Attempt to acquire speaker lock
      const lockResult = await speakerLock.acquireSpeakerLock(channelId, userId, userName);

      if (lockResult.acquired) {
        // Lock acquired - build success state
        const state: ChannelState = {
          channelId,
          currentSpeaker: userId,
          speakerName: userName,
          isBusy: true,
          lockTimestamp: Date.now(),
        };

        // Publish speaker changed event
        await this.publishSpeakerChanged(channelId, state);

        return { success: true, state };
      } else {
        // Lock denied - build state showing current speaker
        const state: ChannelState = {
          channelId,
          currentSpeaker: lockResult.currentSpeaker || null,
          speakerName: lockResult.currentSpeakerName || null,
          isBusy: true,
          lockTimestamp: Date.now(),
        };

        return { success: false, state };
      }
    } catch (err) {
      console.error('Error starting PTT:', err);

      // Return failure state
      const state: ChannelState = {
        channelId,
        currentSpeaker: null,
        speakerName: null,
        isBusy: false,
        lockTimestamp: null,
      };

      return { success: false, state };
    }
  }

  /**
   * Stop PTT transmission for a user
   * Releases speaker lock and notifies listeners
   *
   * @param channelId - Channel to stop PTT in
   * @param userId - User stopping PTT
   * @returns Updated channel state
   */
  async stopPtt(channelId: string, userId: string): Promise<ChannelState> {
    try {
      // Release speaker lock
      await speakerLock.releaseSpeakerLock(channelId, userId);

      // Build idle state
      const state: ChannelState = {
        channelId,
        currentSpeaker: null,
        speakerName: null,
        isBusy: false,
        lockTimestamp: null,
      };

      // Publish speaker changed event
      await this.publishSpeakerChanged(channelId, state);

      return state;
    } catch (err) {
      console.error('Error stopping PTT:', err);

      // Return current state even on error
      return this.getChannelState(channelId);
    }
  }

  /**
   * Get current channel state
   *
   * @param channelId - Channel to query
   * @returns Current channel state
   */
  async getChannelState(channelId: string): Promise<ChannelState> {
    try {
      const currentSpeaker = await speakerLock.getCurrentSpeaker(channelId);

      if (currentSpeaker) {
        return {
          channelId,
          currentSpeaker: currentSpeaker.userId,
          speakerName: currentSpeaker.userName,
          isBusy: true,
          lockTimestamp: Date.now(), // Approximate - actual timestamp in lock
        };
      }

      return {
        channelId,
        currentSpeaker: null,
        speakerName: null,
        isBusy: false,
        lockTimestamp: null,
      };
    } catch (err) {
      console.error('Error getting channel state:', err);

      return {
        channelId,
        currentSpeaker: null,
        speakerName: null,
        isBusy: false,
        lockTimestamp: null,
      };
    }
  }

  /**
   * Subscribe to channel state changes
   *
   * @param channelId - Channel to subscribe to
   * @param callback - Called when speaker changes
   */
  async subscribeToChannel(
    channelId: string,
    callback: (state: ChannelState) => void
  ): Promise<void> {
    try {
      const eventKey = this.getEventKey(channelId);

      // Add callback to subscriptions map
      if (!this.subscriptions.has(eventKey)) {
        this.subscriptions.set(eventKey, []);

        // Subscribe to Redis pub/sub channel
        await this.subClient.subscribe(eventKey, (message) => {
          try {
            const state: ChannelState = JSON.parse(message);

            // Call all callbacks for this channel
            const callbacks = this.subscriptions.get(eventKey) || [];
            callbacks.forEach((cb) => cb(state));
          } catch (err) {
            console.error('Error parsing channel state message:', err);
          }
        });
      }

      // Add this callback
      this.subscriptions.get(eventKey)!.push(callback);
    } catch (err) {
      console.error('Error subscribing to channel:', err);
      throw err;
    }
  }

  /**
   * Unsubscribe from channel state changes
   *
   * @param channelId - Channel to unsubscribe from
   */
  async unsubscribeFromChannel(channelId: string): Promise<void> {
    try {
      const eventKey = this.getEventKey(channelId);

      // Remove subscription
      this.subscriptions.delete(eventKey);

      // Unsubscribe from Redis pub/sub
      await this.subClient.unsubscribe(eventKey);
    } catch (err) {
      console.error('Error unsubscribing from channel:', err);
      throw err;
    }
  }

  /**
   * Publish speaker changed event
   */
  private async publishSpeakerChanged(
    channelId: string,
    state: ChannelState
  ): Promise<void> {
    try {
      const eventKey = this.getEventKey(channelId);
      await this.pubClient.publish(eventKey, JSON.stringify(state));
    } catch (err) {
      console.error('Error publishing speaker changed event:', err);
      // Don't throw - event publication is non-critical
    }
  }
}
