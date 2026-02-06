/**
 * High-level connection lifecycle and session recovery orchestration
 * Manages the entire PTT client system with automatic reconnection
 */

import { ReconnectingSignalingClient } from './signaling/reconnectingClient';
import { MediasoupDevice } from './mediasoup/device';
import { TransportClient } from './mediasoup/transportClient';
import { MicrophoneManager } from './audio/microphone';
import { SignalingType } from '../shared/protocol';
import type { ChannelState } from '../shared/types';

type ConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'error' | 'disconnected';

export interface ConnectionManagerOptions {
  url: string;
  token: string;
  channelId: string;
  onStateChange?: (state: ConnectionState, details?: string) => void;
  onError?: (error: Error) => void;
  onChannelStateUpdate?: (channelState: ChannelState) => void;
  onSpeakerChanged?: (userId: string | null, userName: string | null) => void;
}

/**
 * ConnectionManager orchestrates the full client-side PTT system lifecycle
 * Handles initial connection, automatic reconnection, and session recovery
 */
export class ConnectionManager {
  private options: ConnectionManagerOptions;
  private signalingClient: ReconnectingSignalingClient | null = null;
  private device: MediasoupDevice | null = null;
  private transportClient: TransportClient | null = null;
  private microphoneManager: MicrophoneManager | null = null;

  private connectionState: ConnectionState = 'disconnected';
  private channelId: string;
  private currentChannelState: ChannelState | null = null;
  private isTransmitting: boolean = false;
  private audioTrack: MediaStreamTrack | null = null;
  private producerId: string | null = null;

  constructor(options: ConnectionManagerOptions) {
    this.options = options;
    this.channelId = options.channelId;
  }

  /**
   * Initialize and connect the entire PTT system
   */
  async connect(): Promise<void> {
    try {
      this.setConnectionState('connecting', 'Initializing connection...');

      // Step 1: Create reconnecting signaling client
      this.signalingClient = new ReconnectingSignalingClient(this.options.url, this.options.token);

      // Register reconnection handler BEFORE connecting
      this.signalingClient.on('reconnected', () => {
        this.handleReconnection();
      });

      // Register connection state change handler
      this.signalingClient.on('stateChange', (state) => {
        if (state === 'reconnecting') {
          this.setConnectionState('reconnecting', 'Connection lost, reconnecting...');
        } else if (state === 'connected' && this.connectionState === 'reconnecting') {
          // Reconnection in progress, handleReconnection will be called
          console.log('Signaling reconnected, session recovery starting...');
        }
      });

      // Register channel state update handlers
      this.signalingClient.on(SignalingType.CHANNEL_STATE, (data) => {
        this.handleChannelStateUpdate(data);
      });

      this.signalingClient.on(SignalingType.SPEAKER_CHANGED, (data) => {
        this.handleSpeakerChanged(data);
      });

      // Step 2: Connect to WebSocket server
      this.setConnectionState('connecting', 'Connecting to server...');
      await this.signalingClient.connect();

      // Step 3: Create mediasoup device and load with server capabilities
      this.setConnectionState('connecting', 'Loading device capabilities...');
      this.device = new MediasoupDevice(this.signalingClient);
      await this.device.load(this.channelId);

      // Step 4: Request microphone access
      this.setConnectionState('connecting', 'Requesting microphone access...');
      this.microphoneManager = new MicrophoneManager();
      this.audioTrack = await this.microphoneManager.getAudioTrack();

      // Mute by default (PTT not pressed)
      this.microphoneManager.muteTrack();

      // Step 5: Create transport client
      this.transportClient = new TransportClient(this.device, this.signalingClient);

      // Step 6: Create WebRTC transports
      this.setConnectionState('connecting', 'Creating WebRTC transports...');
      await this.transportClient.createSendTransport(this.channelId);
      await this.transportClient.createRecvTransport(this.channelId);

      // Step 7: Produce audio
      this.setConnectionState('connecting', 'Setting up audio stream...');
      this.producerId = await this.transportClient.produceAudio(this.audioTrack, this.channelId);

      // Step 8: Join channel
      this.setConnectionState('connecting', 'Joining channel...');
      const joinResponse = await this.signalingClient.joinChannel(this.channelId);

      // Extract initial channel state from join response
      if (joinResponse.data && joinResponse.data.channelState) {
        this.currentChannelState = joinResponse.data.channelState as ChannelState;
        if (this.options.onChannelStateUpdate) {
          this.options.onChannelStateUpdate(this.currentChannelState);
        }
      }

      // Step 9: Connection complete
      this.setConnectionState('connected', 'Connected successfully');
      console.log('PTT system fully initialized and ready');
    } catch (error) {
      console.error('Connection failed:', error);
      this.setConnectionState('error', error instanceof Error ? error.message : 'Unknown error');
      if (this.options.onError) {
        this.options.onError(error as Error);
      }
      throw error;
    }
  }

  /**
   * Handle reconnection after network loss
   * Restores full session: rejoins channel, recreates transports, restores audio
   */
  private async handleReconnection(): Promise<void> {
    try {
      console.log('Connection restored, recovering session...');
      this.setConnectionState('connecting', 'Recovering session...');

      if (!this.signalingClient || !this.device || !this.transportClient || !this.audioTrack) {
        throw new Error('Cannot recover session: components not initialized');
      }

      // Step 1: Re-join channel (server may have lost session)
      console.log('Re-joining channel...');
      const joinResponse = await this.signalingClient.joinChannel(this.channelId);

      if (joinResponse.data && joinResponse.data.channelState) {
        this.currentChannelState = joinResponse.data.channelState as ChannelState;
        if (this.options.onChannelStateUpdate) {
          this.options.onChannelStateUpdate(this.currentChannelState);
        }
      }

      // Step 2: Re-load device with router capabilities (router may have changed)
      console.log('Reloading device capabilities...');
      await this.device.load(this.channelId);

      // Step 3: Close old transports (they're invalid after reconnection)
      console.log('Closing old transports...');
      await this.transportClient.closeAll();

      // Step 4: Create new send/recv transports
      console.log('Creating new transports...');
      await this.transportClient.createSendTransport(this.channelId);
      await this.transportClient.createRecvTransport(this.channelId);

      // Step 5: Re-produce audio on new send transport
      console.log('Re-producing audio...');
      this.producerId = await this.transportClient.produceAudio(this.audioTrack, this.channelId);

      // Step 6: If user was transmitting before disconnect, re-acquire PTT lock
      if (this.isTransmitting) {
        console.log('User was transmitting before disconnect, re-acquiring PTT...');
        try {
          await this.signalingClient.pttStart(this.channelId);
          // Resume producer
          const producer = this.transportClient.getProducer();
          if (producer && producer.paused) {
            producer.resume();
          }
          // Unmute microphone
          if (this.microphoneManager) {
            this.microphoneManager.unmuteTrack();
          }
        } catch (error) {
          console.warn('Failed to re-acquire PTT after reconnection:', error);
          this.isTransmitting = false;
        }
      }

      // Step 7: Session recovered
      console.log('Session recovered successfully');
      this.setConnectionState('connected', 'Session recovered');
    } catch (error) {
      console.error('Session recovery failed:', error);
      this.setConnectionState('error', 'Session recovery failed');
      if (this.options.onError) {
        this.options.onError(error as Error);
      }
    }
  }

  /**
   * Handle channel state updates from server
   */
  private handleChannelStateUpdate(data: Record<string, unknown>): void {
    this.currentChannelState = data as unknown as ChannelState;
    console.log('Channel state updated:', this.currentChannelState);
    if (this.options.onChannelStateUpdate) {
      this.options.onChannelStateUpdate(this.currentChannelState);
    }
  }

  /**
   * Handle speaker changed events
   */
  private handleSpeakerChanged(data: Record<string, unknown>): void {
    const userId = data.userId as string | null;
    const userName = data.userName as string | null;
    console.log('Speaker changed:', userId, userName);
    if (this.options.onSpeakerChanged) {
      this.options.onSpeakerChanged(userId, userName);
    }
  }

  /**
   * Start PTT transmission
   */
  async startTransmitting(): Promise<void> {
    if (!this.signalingClient || this.connectionState !== 'connected') {
      throw new Error('Cannot start transmitting: not connected');
    }

    if (this.isTransmitting) {
      console.warn('Already transmitting');
      return;
    }

    try {
      // Request PTT lock from server
      const response = await this.signalingClient.pttStart(this.channelId);

      if (response.error) {
        // PTT denied (someone else is speaking)
        console.log('PTT denied:', response.error);
        throw new Error(response.error);
      }

      // PTT granted - resume producer and unmute microphone
      const producer = this.transportClient?.getProducer();
      if (producer && producer.paused) {
        producer.resume();
      }

      if (this.microphoneManager) {
        this.microphoneManager.unmuteTrack();
      }

      this.isTransmitting = true;
      console.log('PTT transmission started');
    } catch (error) {
      console.error('Failed to start transmitting:', error);
      throw error;
    }
  }

  /**
   * Stop PTT transmission
   */
  async stopTransmitting(): Promise<void> {
    if (!this.signalingClient) {
      return;
    }

    if (!this.isTransmitting) {
      return;
    }

    try {
      // Release PTT lock
      await this.signalingClient.pttStop(this.channelId);

      // Pause producer and mute microphone
      const producer = this.transportClient?.getProducer();
      if (producer && !producer.paused) {
        producer.pause();
      }

      if (this.microphoneManager) {
        this.microphoneManager.muteTrack();
      }

      this.isTransmitting = false;
      console.log('PTT transmission stopped');
    } catch (error) {
      console.error('Failed to stop transmitting:', error);
      throw error;
    }
  }

  /**
   * Disconnect and clean up all resources
   */
  async disconnect(): Promise<void> {
    try {
      this.setConnectionState('disconnected', 'Disconnecting...');

      // Stop transmission if active
      if (this.isTransmitting) {
        await this.stopTransmitting();
      }

      // Leave channel
      if (this.signalingClient && this.connectionState !== 'error') {
        try {
          await this.signalingClient.leaveChannel(this.channelId);
        } catch (error) {
          console.warn('Failed to leave channel gracefully:', error);
        }
      }

      // Close transports
      if (this.transportClient) {
        await this.transportClient.closeAll();
      }

      // Release microphone
      if (this.microphoneManager) {
        this.microphoneManager.release();
      }

      // Disconnect signaling (clean close, no reconnection)
      if (this.signalingClient) {
        this.signalingClient.disconnect();
      }

      // Clear references
      this.signalingClient = null;
      this.device = null;
      this.transportClient = null;
      this.microphoneManager = null;
      this.audioTrack = null;
      this.producerId = null;
      this.currentChannelState = null;

      console.log('Disconnected and cleaned up all resources');
    } catch (error) {
      console.error('Error during disconnect:', error);
      throw error;
    }
  }

  /**
   * Get current connection state
   */
  getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Get current channel state
   */
  getChannelState(): ChannelState | null {
    return this.currentChannelState;
  }

  /**
   * Check if currently transmitting
   */
  isCurrentlyTransmitting(): boolean {
    return this.isTransmitting;
  }

  /**
   * Get microphone manager for external control
   */
  getMicrophoneManager(): MicrophoneManager | null {
    return this.microphoneManager;
  }

  /**
   * Get transport client for external control
   */
  getTransportClient(): TransportClient | null {
    return this.transportClient;
  }

  /**
   * Set connection state and notify callback
   */
  private setConnectionState(state: ConnectionState, details?: string): void {
    this.connectionState = state;
    console.log(`Connection state: ${state}${details ? ` - ${details}` : ''}`);
    if (this.options.onStateChange) {
      this.options.onStateChange(state, details);
    }
  }
}
