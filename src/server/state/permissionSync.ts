/**
 * Permission synchronization via Redis pub/sub
 * Listens to vp:membership_updates channel for real-time permission changes
 */

import { createClient, RedisClientType } from 'redis';
import { config } from '../config';
import { createLogger } from '../logger';

const logger = createLogger('PermissionSync');

/**
 * Membership update event payload from control-plane
 */
interface MembershipUpdateEvent {
  eventId: string;
  userId: string;
  channelIds: string[];
  action: string;
}

/**
 * Permission change callback type
 */
type PermissionChangeCallback = (
  userId: string,
  eventId: string,
  newChannelIds: string[],
  action: string
) => void;

/**
 * Permission synchronization manager
 * Subscribes to Redis pub/sub for real-time membership updates from control-plane
 */
export class PermissionSyncManager {
  private subClient: RedisClientType | null = null;
  private callback: PermissionChangeCallback;
  private isStarted: boolean = false;
  private reconnectAttempts: number = 0;
  private reconnectTimeoutId: NodeJS.Timeout | null = null;

  // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s (cap)
  private readonly BACKOFF_DELAYS_MS = [1000, 2000, 4000, 8000, 16000, 30000];
  private readonly CHANNEL_NAME = 'vp:membership_updates';

  constructor(onPermissionChange: PermissionChangeCallback) {
    this.callback = onPermissionChange;
  }

  /**
   * Start listening for permission updates
   * Creates dedicated Redis subscriber client and subscribes to membership updates channel
   */
  async start(): Promise<void> {
    if (this.isStarted) {
      logger.warn('PermissionSyncManager already started');
      return;
    }

    try {
      // Create dedicated Redis subscriber client (required by Redis v4)
      this.subClient = createClient({ url: config.redis.url });

      // Setup error handlers
      this.subClient.on('error', (err) => {
        logger.error('Redis subscriber client error:', err);
        this.handleConnectionError();
      });

      this.subClient.on('reconnecting', () => {
        logger.warn('Redis subscriber client reconnecting...');
      });

      this.subClient.on('ready', () => {
        logger.info('Redis subscriber client ready');
        this.reconnectAttempts = 0; // Reset backoff counter on successful connection
      });

      // Connect to Redis
      await this.subClient.connect();
      logger.info('Permission sync manager connected to Redis');

      // Subscribe to membership updates channel
      await this.subClient.subscribe(this.CHANNEL_NAME, (message) => {
        this.handleMessage(message);
      });

      logger.info(`Subscribed to ${this.CHANNEL_NAME} for permission updates`);
      this.isStarted = true;
    } catch (err) {
      logger.error('Failed to start permission sync manager:', err);
      this.handleConnectionError();
      throw err;
    }
  }

  /**
   * Stop listening for permission updates
   * Unsubscribes and disconnects subscriber client
   */
  async stop(): Promise<void> {
    if (!this.isStarted) {
      logger.warn('PermissionSyncManager not started');
      return;
    }

    try {
      // Cancel any pending reconnect
      if (this.reconnectTimeoutId) {
        clearTimeout(this.reconnectTimeoutId);
        this.reconnectTimeoutId = null;
      }

      // Unsubscribe and disconnect
      if (this.subClient) {
        try {
          await this.subClient.unsubscribe(this.CHANNEL_NAME);
          logger.info(`Unsubscribed from ${this.CHANNEL_NAME}`);
        } catch (err) {
          logger.error('Error unsubscribing from channel:', err);
        }

        try {
          await this.subClient.disconnect();
          logger.info('Permission sync manager disconnected from Redis');
        } catch (err) {
          logger.error('Error disconnecting subscriber client:', err);
        }

        this.subClient = null;
      }

      this.isStarted = false;
      this.reconnectAttempts = 0;
    } catch (err) {
      logger.error('Error stopping permission sync manager:', err);
      throw err;
    }
  }

  /**
   * Handle incoming membership update message
   */
  private handleMessage(message: string): void {
    try {
      // Parse JSON payload
      const event: MembershipUpdateEvent = JSON.parse(message);

      // Validate required fields
      if (!event.eventId || !event.userId || !event.channelIds || !event.action) {
        logger.warn('Invalid membership update event (missing fields):', event);
        return;
      }

      // Validate channelIds is array
      if (!Array.isArray(event.channelIds)) {
        logger.warn('Invalid membership update event (channelIds not array):', event);
        return;
      }

      logger.info(`Permission update: user=${event.userId}, event=${event.eventId}, action=${event.action}, channels=${event.channelIds.length}`);

      // Call callback with extracted fields
      this.callback(event.userId, event.eventId, event.channelIds, event.action);
    } catch (err) {
      logger.error('Error handling membership update message:', err);
      // Don't crash - log error and continue listening
    }
  }

  /**
   * Handle connection errors with exponential backoff retry
   */
  private handleConnectionError(): void {
    if (!this.isStarted) {
      // Not started, don't retry
      return;
    }

    // Calculate backoff delay
    const delayIndex = Math.min(this.reconnectAttempts, this.BACKOFF_DELAYS_MS.length - 1);
    const delayMs = this.BACKOFF_DELAYS_MS[delayIndex];

    logger.warn(`Reconnecting permission sync manager in ${delayMs}ms (attempt ${this.reconnectAttempts + 1})`);

    // Cancel any existing reconnect timeout
    if (this.reconnectTimeoutId) {
      clearTimeout(this.reconnectTimeoutId);
    }

    // Schedule reconnect
    this.reconnectTimeoutId = setTimeout(async () => {
      this.reconnectAttempts++;
      this.reconnectTimeoutId = null;

      try {
        // Disconnect old client if exists
        if (this.subClient) {
          try {
            await this.subClient.disconnect();
          } catch (err) {
            logger.error('Error disconnecting old client during retry:', err);
          }
          this.subClient = null;
        }

        // Mark as not started to allow start() to proceed
        this.isStarted = false;

        // Retry start
        await this.start();
      } catch (err) {
        logger.error('Reconnect attempt failed:', err);
        // handleConnectionError will be called again via error handler
      }
    }, delayMs);
  }

  /**
   * Get current connection status
   */
  isConnected(): boolean {
    return this.isStarted && this.subClient !== null && this.subClient.isOpen;
  }
}
