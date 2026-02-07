/**
 * WebSocket client for typed signaling protocol
 * Handles request-response correlation and server push events
 */

import { SignalingType, SignalingMessage, SignalingRequest } from '../../shared/protocol';

/**
 * Interface for signaling client operations
 * Implemented by both SignalingClient and ReconnectingSignalingClient
 */
export interface ISignalingClient {
  request(type: SignalingType, data?: Record<string, unknown>): Promise<SignalingMessage>;
  on(type: SignalingType, callback: (data: Record<string, unknown>) => void): void;
  off(type: SignalingType, callback: (data: Record<string, unknown>) => void): void;
  isConnected(): boolean;
  joinChannel(channelId: string): Promise<SignalingMessage>;
  leaveChannel(channelId: string): Promise<SignalingMessage>;
  getRouterCapabilities(channelId: string): Promise<SignalingMessage>;
  createTransport(channelId: string, direction: 'send' | 'recv'): Promise<SignalingMessage>;
  connectTransport(transportId: string, dtlsParameters: object): Promise<SignalingMessage>;
  produce(transportId: string, kind: string, rtpParameters: object, channelId: string): Promise<SignalingMessage>;
  consume(channelId: string, producerId: string, rtpCapabilities?: object): Promise<SignalingMessage>;
  pttStart(channelId: string): Promise<SignalingMessage>;
  pttStop(channelId: string): Promise<SignalingMessage>;
}

type PendingRequest = {
  resolve: (message: SignalingMessage) => void;
  reject: (error: Error) => void;
  timeout: NodeJS.Timeout;
};

type EventHandler = (data: Record<string, unknown>) => void;

type ConnectionState = 'disconnected' | 'connecting' | 'connected';

export class SignalingClient implements ISignalingClient {
  private ws: WebSocket | null = null;
  private url: string;
  private token: string;
  private state: ConnectionState = 'disconnected';
  private pendingRequests = new Map<string, PendingRequest>();
  private eventHandlers = new Map<SignalingType, Set<EventHandler>>();
  private messageIdCounter = 0;

  constructor(url: string, token: string) {
    this.url = url;
    this.token = token;
  }

  /**
   * Connect to the WebSocket server
   */
  async connect(): Promise<void> {
    if (this.state === 'connected') {
      return;
    }

    return new Promise((resolve, reject) => {
      this.state = 'connecting';

      try {
        // Pass token as protocol for authentication
        this.ws = new WebSocket(this.url, ['voiceping', this.token]);

        this.ws.onopen = () => {
          this.state = 'connected';
          resolve();
        };

        this.ws.onerror = (event) => {
          this.state = 'disconnected';
          reject(new Error('WebSocket connection failed'));
        };

        this.ws.onclose = () => {
          this.state = 'disconnected';
          this.cleanup();
        };

        this.ws.onmessage = (event) => {
          this.handleMessage(event.data);
        };
      } catch (error) {
        this.state = 'disconnected';
        reject(error);
      }
    });
  }

  /**
   * Handle incoming WebSocket message
   */
  private handleMessage(data: string): void {
    try {
      const message = JSON.parse(data) as SignalingMessage;

      // Check if this is a response to a pending request
      if (message.id && this.pendingRequests.has(message.id)) {
        const pending = this.pendingRequests.get(message.id)!;
        clearTimeout(pending.timeout);
        this.pendingRequests.delete(message.id);

        if (message.error) {
          pending.reject(new Error(message.error));
        } else {
          pending.resolve(message);
        }
      } else {
        // Server push event - emit to registered handlers
        this.emitEvent(message.type, message.data || {});
      }
    } catch (error) {
      console.error('Failed to parse signaling message:', error);
    }
  }

  /**
   * Send a request and wait for response
   */
  async request(type: SignalingType, data?: Record<string, unknown>): Promise<SignalingMessage> {
    if (!this.ws || this.state !== 'connected') {
      throw new Error('WebSocket not connected');
    }

    return new Promise((resolve, reject) => {
      // Generate unique message ID
      const id = `msg-${++this.messageIdCounter}-${Date.now()}`;

      // Create timeout for request
      const timeout = setTimeout(() => {
        this.pendingRequests.delete(id);
        reject(new Error(`Request timeout: ${type} (10s)`));
      }, 10000);

      // Store pending request
      this.pendingRequests.set(id, { resolve, reject, timeout });

      // Send request
      const message: SignalingRequest = {
        type,
        id,
        data,
      };

      try {
        this.ws!.send(JSON.stringify(message));
      } catch (error) {
        clearTimeout(timeout);
        this.pendingRequests.delete(id);
        reject(error);
      }
    });
  }

  /**
   * Register event handler for server push messages
   */
  on(type: SignalingType, callback: EventHandler): void {
    if (!this.eventHandlers.has(type)) {
      this.eventHandlers.set(type, new Set());
    }
    this.eventHandlers.get(type)!.add(callback);
  }

  /**
   * Remove event handler
   */
  off(type: SignalingType, callback: EventHandler): void {
    const handlers = this.eventHandlers.get(type);
    if (handlers) {
      handlers.delete(callback);
    }
  }

  /**
   * Emit event to registered handlers
   */
  private emitEvent(type: SignalingType, data: Record<string, unknown>): void {
    const handlers = this.eventHandlers.get(type);
    if (handlers) {
      handlers.forEach((handler) => handler(data));
    }
  }

  /**
   * Clean up pending requests and handlers
   */
  private cleanup(): void {
    // Reject all pending requests
    this.pendingRequests.forEach((pending) => {
      clearTimeout(pending.timeout);
      pending.reject(new Error('WebSocket connection closed'));
    });
    this.pendingRequests.clear();
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.state === 'connected';
  }

  /**
   * Close WebSocket connection
   */
  close(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.state = 'disconnected';
    this.cleanup();
  }

  // Typed helper methods for signaling operations

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

  async consume(channelId: string, producerId: string, rtpCapabilities?: object): Promise<SignalingMessage> {
    return this.request(SignalingType.CONSUME, { channelId, producerId, rtpCapabilities });
  }

  async pttStart(channelId: string): Promise<SignalingMessage> {
    return this.request(SignalingType.PTT_START, { channelId });
  }

  async pttStop(channelId: string): Promise<SignalingMessage> {
    return this.request(SignalingType.PTT_STOP, { channelId });
  }
}
