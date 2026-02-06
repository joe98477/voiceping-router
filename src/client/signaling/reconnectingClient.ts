/**
 * WebSocket reconnection wrapper with exponential backoff
 * Wraps SignalingClient with automatic reconnection after network loss
 */

import { SignalingClient } from './signalingClient';
import { SignalingType, SignalingMessage } from '../../shared/protocol';

type ConnectionState = 'connected' | 'connecting' | 'reconnecting' | 'disconnected';

type EventHandler = (data: any) => void;

interface ReconnectionConfig {
  initialDelay: number;
  maxDelay: number;
  maxAttempts: number;
  backoffMultiplier: number;
  jitter: boolean;
}

interface QueuedMessage {
  type: SignalingType;
  data?: Record<string, unknown>;
  timestamp: number;
  resolve: (message: SignalingMessage) => void;
  reject: (error: Error) => void;
}

export class ReconnectingSignalingClient {
  private signalingClient: SignalingClient;
  private url: string;
  private token: string;
  private config: ReconnectionConfig;

  private connectionState: ConnectionState = 'disconnected';
  private reconnectAttempts: number = 0;
  private currentDelay: number;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private intentionalDisconnect: boolean = false;
  private isReconnecting: boolean = false;

  // Message queue for sending during reconnection
  private messageQueue: QueuedMessage[] = [];
  private readonly MAX_QUEUE_SIZE = 100;
  private readonly STALE_MESSAGE_THRESHOLD = 2000; // 2 seconds

  // Event handlers
  private stateChangeHandlers = new Set<(state: ConnectionState) => void>();
  private reconnectedHandlers = new Set<() => void>();
  private reconnectFailedHandlers = new Set<(error: Error) => void>();

  constructor(
    url: string,
    token: string,
    config: Partial<ReconnectionConfig> = {}
  ) {
    this.url = url;
    this.token = token;
    this.config = {
      initialDelay: config.initialDelay ?? 1000,
      maxDelay: config.maxDelay ?? 30000,
      maxAttempts: config.maxAttempts ?? Infinity,
      backoffMultiplier: config.backoffMultiplier ?? 2,
      jitter: config.jitter ?? true,
    };
    this.currentDelay = this.config.initialDelay;

    this.signalingClient = new SignalingClient(url, token);
  }

  /**
   * Connect to the WebSocket server with automatic reconnection
   */
  async connect(): Promise<void> {
    if (this.connectionState === 'connected') {
      return;
    }

    this.connectionState = 'connecting';
    this.intentionalDisconnect = false;
    this.emitStateChange('connecting');

    try {
      await this.signalingClient.connect();
      this.onConnected();
    } catch (error) {
      console.error('Initial connection failed:', error);
      this.scheduleReconnection();
      throw error;
    }
  }

  /**
   * Handle successful connection
   */
  private onConnected(): void {
    this.connectionState = 'connected';
    this.reconnectAttempts = 0;
    this.currentDelay = this.config.initialDelay;
    this.isReconnecting = false;
    this.emitStateChange('connected');

    // Set up close event handler for reconnection
    // Access the underlying WebSocket via a method we'll need
    this.setupCloseHandler();

    // Replay queued messages
    this.replayQueuedMessages();

    console.log('WebSocket connected successfully');
  }

  /**
   * Set up close handler on the underlying WebSocket
   */
  private setupCloseHandler(): void {
    // We need to hook into the SignalingClient's WebSocket close event
    // Since SignalingClient doesn't expose this, we'll handle it via polling connection state
    // or by extending SignalingClient. For now, we'll use a different approach:
    // Monitor isConnected() status periodically
    const checkInterval = setInterval(() => {
      if (!this.signalingClient.isConnected() && !this.intentionalDisconnect && this.connectionState === 'connected') {
        clearInterval(checkInterval);
        this.handleDisconnection(false); // Unclean close
      }
      if (this.connectionState === 'disconnected' || this.intentionalDisconnect) {
        clearInterval(checkInterval);
      }
    }, 1000);
  }

  /**
   * Handle disconnection (clean or unclean)
   */
  private handleDisconnection(wasClean: boolean): void {
    if (this.intentionalDisconnect) {
      // User called disconnect() - don't reconnect
      this.connectionState = 'disconnected';
      this.emitStateChange('disconnected');
      console.log('WebSocket disconnected cleanly (user-initiated)');
      return;
    }

    if (!wasClean && !this.isReconnecting) {
      // Unclean close - network loss or server restart
      console.warn('WebSocket connection lost (unclean close)');
      this.scheduleReconnection();
    }
  }

  /**
   * Schedule reconnection with exponential backoff
   */
  private scheduleReconnection(): void {
    if (this.intentionalDisconnect) {
      return;
    }

    if (this.reconnectAttempts >= this.config.maxAttempts) {
      console.error(`Max reconnection attempts (${this.config.maxAttempts}) reached`);
      this.connectionState = 'disconnected';
      this.emitStateChange('disconnected');
      this.emitReconnectFailed(new Error('Max reconnection attempts reached'));
      return;
    }

    this.connectionState = 'reconnecting';
    this.isReconnecting = true;
    this.emitStateChange('reconnecting');

    // Calculate delay with exponential backoff
    const baseDelay = Math.min(
      this.config.initialDelay * Math.pow(this.config.backoffMultiplier, this.reconnectAttempts),
      this.config.maxDelay
    );

    // Add jitter to prevent thundering herd
    const jitter = this.config.jitter ? Math.random() * 500 : 0;
    this.currentDelay = baseDelay + jitter;

    console.log(
      `Reconnecting in ${Math.round(this.currentDelay)}ms (attempt ${this.reconnectAttempts + 1})`
    );

    this.reconnectTimer = setTimeout(() => {
      this.attemptReconnection();
    }, this.currentDelay);
  }

  /**
   * Attempt to reconnect
   */
  private async attemptReconnection(): Promise<void> {
    if (this.intentionalDisconnect) {
      return;
    }

    this.reconnectAttempts++;

    try {
      console.log(`Attempting reconnection (attempt ${this.reconnectAttempts})...`);

      // Create new SignalingClient for reconnection
      this.signalingClient = new SignalingClient(this.url, this.token);
      await this.signalingClient.connect();

      // Success
      console.log('Reconnection successful');
      this.onConnected();
      this.emitReconnected();
    } catch (error) {
      console.error(`Reconnection attempt ${this.reconnectAttempts} failed:`, error);
      // Schedule another attempt
      this.scheduleReconnection();
    }
  }

  /**
   * Intentional disconnect - prevents reconnection
   */
  disconnect(): void {
    this.intentionalDisconnect = true;

    // Cancel any pending reconnection
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    // Close WebSocket cleanly
    this.signalingClient.close();
    this.connectionState = 'disconnected';
    this.emitStateChange('disconnected');

    console.log('Disconnected intentionally');
  }

  /**
   * Queue message for sending after reconnection
   */
  private queueMessage(
    type: SignalingType,
    data: Record<string, unknown> | undefined,
    resolve: (message: SignalingMessage) => void,
    reject: (error: Error) => void
  ): void {
    // Check queue size limit
    if (this.messageQueue.length >= this.MAX_QUEUE_SIZE) {
      console.warn('Message queue full, dropping oldest message');
      const dropped = this.messageQueue.shift();
      if (dropped) {
        dropped.reject(new Error('Message queue full, message dropped'));
      }
    }

    this.messageQueue.push({
      type,
      data,
      timestamp: Date.now(),
      resolve,
      reject,
    });

    console.log(`Message queued: ${type} (queue size: ${this.messageQueue.length})`);
  }

  /**
   * Replay queued messages after reconnection
   */
  private async replayQueuedMessages(): Promise<void> {
    if (this.messageQueue.length === 0) {
      return;
    }

    console.log(`Replaying ${this.messageQueue.length} queued messages...`);

    // Filter out stale PTT messages
    const now = Date.now();
    const validMessages = this.messageQueue.filter((msg) => {
      if ((msg.type === SignalingType.PTT_START || msg.type === SignalingType.PTT_STOP) &&
          now - msg.timestamp > this.STALE_MESSAGE_THRESHOLD) {
        console.warn(`Dropping stale PTT message: ${msg.type}`);
        msg.reject(new Error('PTT message too old, dropped'));
        return false;
      }
      return true;
    });

    this.messageQueue = [];

    // Replay messages sequentially
    for (const msg of validMessages) {
      try {
        const response = await this.signalingClient.request(msg.type, msg.data);
        msg.resolve(response);
      } catch (error) {
        msg.reject(error as Error);
      }
    }
  }

  /**
   * Send request, with queueing during reconnection
   */
  async request(type: SignalingType, data?: Record<string, unknown>): Promise<SignalingMessage> {
    if (this.connectionState === 'reconnecting') {
      // Queue message for replay after reconnection
      return new Promise<SignalingMessage>((resolve, reject) => {
        this.queueMessage(type, data, resolve, reject);
      });
    }

    if (this.connectionState !== 'connected') {
      throw new Error(`Cannot send request: connection state is ${this.connectionState}`);
    }

    return this.signalingClient.request(type, data);
  }

  /**
   * Register event handler
   */
  on(event: 'stateChange', callback: (state: ConnectionState) => void): void;
  on(event: 'reconnected', callback: () => void): void;
  on(event: 'reconnectFailed', callback: (error: Error) => void): void;
  on(event: SignalingType, callback: EventHandler): void;
  on(event: string, callback: EventHandler): void {
    if (event === 'stateChange') {
      this.stateChangeHandlers.add(callback as (state: ConnectionState) => void);
    } else if (event === 'reconnected') {
      this.reconnectedHandlers.add(callback as () => void);
    } else if (event === 'reconnectFailed') {
      this.reconnectFailedHandlers.add(callback as (error: Error) => void);
    } else {
      // Delegate to underlying SignalingClient
      this.signalingClient.on(event as SignalingType, callback);
    }
  }

  /**
   * Remove event handler
   */
  off(event: 'stateChange', callback: (state: ConnectionState) => void): void;
  off(event: 'reconnected', callback: () => void): void;
  off(event: 'reconnectFailed', callback: (error: Error) => void): void;
  off(event: SignalingType, callback: EventHandler): void;
  off(event: string, callback: EventHandler): void {
    if (event === 'stateChange') {
      this.stateChangeHandlers.delete(callback as (state: ConnectionState) => void);
    } else if (event === 'reconnected') {
      this.reconnectedHandlers.delete(callback as () => void);
    } else if (event === 'reconnectFailed') {
      this.reconnectFailedHandlers.delete(callback as (error: Error) => void);
    } else {
      // Delegate to underlying SignalingClient
      this.signalingClient.off(event as SignalingType, callback);
    }
  }

  /**
   * Emit state change event
   */
  private emitStateChange(state: ConnectionState): void {
    this.stateChangeHandlers.forEach((handler) => handler(state));
  }

  /**
   * Emit reconnected event
   */
  private emitReconnected(): void {
    this.reconnectedHandlers.forEach((handler) => handler());
  }

  /**
   * Emit reconnect failed event
   */
  private emitReconnectFailed(error: Error): void {
    this.reconnectFailedHandlers.forEach((handler) => handler(error));
  }

  /**
   * Get current connection state
   */
  getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.connectionState === 'connected';
  }

  // Proxy methods to underlying SignalingClient

  async joinChannel(channelId: string): Promise<SignalingMessage> {
    return this.request(SignalingType.JOIN_CHANNEL, { channelId });
  }

  async leaveChannel(channelId: string): Promise<SignalingMessage> {
    return this.request(SignalingType.LEAVE_CHANNEL, { channelId });
  }

  async getRouterCapabilities(channelId: string): Promise<SignalingMessage> {
    return this.request(SignalingType.GET_ROUTER_CAPABILITIES, { channelId });
  }

  async createTransport(channelId: string, direction: 'send' | 'recv'): Promise<SignalingMessage> {
    return this.request(SignalingType.CREATE_TRANSPORT, { channelId, direction });
  }

  async connectTransport(transportId: string, dtlsParameters: object): Promise<SignalingMessage> {
    return this.request(SignalingType.CONNECT_TRANSPORT, { transportId, dtlsParameters });
  }

  async produce(
    transportId: string,
    kind: string,
    rtpParameters: object,
    channelId: string
  ): Promise<SignalingMessage> {
    return this.request(SignalingType.PRODUCE, { transportId, kind, rtpParameters, channelId });
  }

  async consume(channelId: string, producerId: string): Promise<SignalingMessage> {
    return this.request(SignalingType.CONSUME, { channelId, producerId });
  }

  async pttStart(channelId: string): Promise<SignalingMessage> {
    return this.request(SignalingType.PTT_START, { channelId });
  }

  async pttStop(channelId: string): Promise<SignalingMessage> {
    return this.request(SignalingType.PTT_STOP, { channelId });
  }
}
