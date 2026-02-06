/**
 * PTT Controller - Orchestrates complete PTT flow
 * Connects button UI, audio feedback, signaling, transport, and microphone
 */

import { SignalingClient } from './signaling/signalingClient';
import { TransportClient } from './mediasoup/transportClient';
import { MicrophoneManager } from './audio/microphone';
import { AudioFeedback } from './audio/feedback';
import { PttButton } from './ui/PttButton';
import { PttMode, PttState } from '../shared/types';
import { SignalingType } from '../shared/protocol';
import type * as mediasoupClient from 'mediasoup-client';

type Consumer = mediasoupClient.types.Consumer;

export interface PttControllerOptions {
  channelId: string;
  pttMode: PttMode;
  buttonContainer: HTMLElement;
}

export interface ErrorCallback {
  (error: Error): void;
}

export class PttController {
  private signalingClient: SignalingClient;
  private transportClient: TransportClient;
  private microphoneManager: MicrophoneManager;
  private audioFeedback: AudioFeedback;
  private pttButton: PttButton;
  private options: PttControllerOptions;

  private audioTrack: MediaStreamTrack | null = null;
  private producerId: string | null = null;
  private activeConsumers = new Map<string, { consumer: Consumer; audioElement: HTMLAudioElement }>();
  private isTransmitting: boolean = false;
  private errorCallback: ErrorCallback | null = null;

  constructor(
    signalingClient: SignalingClient,
    transportClient: TransportClient,
    microphoneManager: MicrophoneManager,
    audioFeedback: AudioFeedback,
    options: PttControllerOptions
  ) {
    this.signalingClient = signalingClient;
    this.transportClient = transportClient;
    this.microphoneManager = microphoneManager;
    this.audioFeedback = audioFeedback;
    this.options = options;

    // Create PTT button with bound callbacks
    this.pttButton = new PttButton(options.buttonContainer, {
      mode: options.pttMode,
      onPttStart: () => this.handlePttStart(),
      onPttStop: () => this.handlePttStop(),
    });
  }

  /**
   * Set error callback for UI notification
   */
  onError(callback: ErrorCallback): void {
    this.errorCallback = callback;
  }

  /**
   * Initialize PTT controller
   */
  async init(): Promise<void> {
    try {
      console.log('Initializing PTT controller...');

      // Step 1: Preload audio feedback tones
      await this.audioFeedback.preload();

      // Step 2: Check microphone permission
      const permission = await this.microphoneManager.checkPermission();
      if (permission === 'denied') {
        throw new Error(
          'Microphone access denied. Please allow microphone access in browser settings.'
        );
      }

      // Step 3: Get audio track from microphone
      this.audioTrack = await this.microphoneManager.getAudioTrack();

      // Step 4: Create send transport
      await this.transportClient.createSendTransport(this.options.channelId);

      // Step 5: Produce audio (starts paused)
      this.producerId = await this.transportClient.produceAudio(
        this.audioTrack,
        this.options.channelId
      );

      // Step 6: Create receive transport
      await this.transportClient.createRecvTransport(this.options.channelId);

      // Step 7: Wire PTT button callbacks
      // The button's onPttStart/onPttStop callbacks are already set in constructor
      // Just set the mode
      this.pttButton.setMode(this.options.pttMode);

      // Step 8: Subscribe to server push events
      this.subscribeToServerEvents();

      // Step 9: Enable PTT button
      this.pttButton.setEnabled(true);

      console.log('PTT controller initialized successfully');
    } catch (error) {
      console.error('Failed to initialize PTT controller:', error);
      this.handleError(error as Error);
      throw error;
    }
  }

  /**
   * Subscribe to server push events
   */
  private subscribeToServerEvents(): void {
    // Handle speaker changed events
    this.signalingClient.on(SignalingType.SPEAKER_CHANGED, (data) => {
      this.handleSpeakerChanged(data);
    });

    // Handle PTT denied events
    this.signalingClient.on(SignalingType.PTT_DENIED, (data) => {
      this.handlePttDenied(data);
    });

    // Handle channel state updates
    this.signalingClient.on(SignalingType.CHANNEL_STATE, (data) => {
      this.handleChannelState(data);
    });
  }

  /**
   * Handle speaker changed event
   */
  private async handleSpeakerChanged(data: Record<string, unknown>): Promise<void> {
    const speakerId = data.speakerId as string | null;
    const speakerName = data.speakerName as string | null;
    const producerId = data.producerId as string | null;

    console.log(`Speaker changed: ${speakerName || 'none'} (${speakerId || 'none'})`);

    if (speakerId && producerId) {
      // Someone started speaking - consume their audio
      try {
        await this.consumeAudio(producerId);
      } catch (error) {
        console.error('Failed to consume audio from new speaker:', error);
      }
    } else {
      // Speaker stopped - optionally cleanup consumer
      // (consumers can be kept alive for quick reconnection)
    }
  }

  /**
   * Handle PTT denied event
   */
  private handlePttDenied(data: Record<string, unknown>): void {
    const speakerName = data.currentSpeakerName as string;

    console.log(`PTT denied - ${speakerName} is speaking`);

    // Revert UI to blocked state
    this.pttButton.setState(PttState.BLOCKED, { name: speakerName });

    // Play busy tone
    this.audioFeedback.play('busy-tone');

    // Ensure microphone is muted
    this.microphoneManager.muteTrack();
    this.isTransmitting = false;

    // Auto-revert handled by PttButton
  }

  /**
   * Handle channel state update
   */
  private handleChannelState(data: Record<string, unknown>): void {
    console.log('Channel state updated:', data);
    // Additional state management can be added here
  }

  /**
   * PTT Start - User activates PTT
   */
  async handlePttStart(): Promise<void> {
    if (this.isTransmitting) {
      console.warn('Already transmitting');
      return;
    }

    try {
      // Step 1: Immediate visual feedback (optimistic UI)
      this.pttButton.setState(PttState.TRANSMITTING);

      // Step 2: Play transmit-start tone
      this.audioFeedback.play('transmit-start');

      // Step 3: Send PTT_START to server
      const response = await this.signalingClient.pttStart(this.options.channelId);

      // Step 4: Check if server granted permission
      if (response.error) {
        // Server denied PTT
        throw new Error(response.error);
      }

      // Step 5: Unmute microphone (audio flows)
      this.microphoneManager.unmuteTrack();
      this.isTransmitting = true;

      console.log('PTT started successfully');
    } catch (error) {
      console.error('PTT start failed:', error);

      // Revert optimistic UI changes
      this.pttButton.setState(PttState.IDLE);
      this.microphoneManager.muteTrack();
      this.isTransmitting = false;

      // Error might be PTT_DENIED, which is handled by server push event
      // Don't show error for denied PTT (user sees busy state)
    }
  }

  /**
   * PTT Stop - User deactivates PTT
   */
  async handlePttStop(): Promise<void> {
    if (!this.isTransmitting) {
      console.warn('Not transmitting');
      return;
    }

    try {
      // Step 1: Mute microphone immediately (stop audio before server confirms)
      this.microphoneManager.muteTrack();
      this.isTransmitting = false;

      // Step 2: Play transmit-stop tone
      this.audioFeedback.play('transmit-stop');

      // Step 3: Send PTT_STOP to server
      await this.signalingClient.pttStop(this.options.channelId);

      // Step 4: Update button visual to IDLE
      this.pttButton.setState(PttState.IDLE);

      console.log('PTT stopped successfully');
    } catch (error) {
      console.error('PTT stop failed:', error);
      // Even if server doesn't confirm, we've stopped transmitting
      this.pttButton.setState(PttState.IDLE);
    }
  }

  /**
   * Consume audio from another speaker
   */
  private async consumeAudio(producerId: string): Promise<void> {
    try {
      // Check if already consuming this producer
      if (this.activeConsumers.has(producerId)) {
        console.log(`Already consuming audio from producer ${producerId}`);
        return;
      }

      // Request consumer from server
      const { consumer, track } = await this.transportClient.consumeAudio(
        producerId,
        this.options.channelId
      );

      // Create HTML Audio element for playback
      const audioElement = document.createElement('audio');
      audioElement.autoplay = true;

      // Attach track to audio element
      const stream = new MediaStream([track]);
      audioElement.srcObject = stream;

      // Store consumer reference
      this.activeConsumers.set(producerId, { consumer, audioElement });

      console.log(`Consuming audio from producer ${producerId}`);
    } catch (error) {
      console.error(`Failed to consume audio from producer ${producerId}:`, error);
      throw error;
    }
  }

  /**
   * Start listening for incoming audio
   */
  async startListening(): Promise<void> {
    console.log('Started listening for incoming audio');
    // Consumption is handled automatically by SPEAKER_CHANGED events
  }

  /**
   * Set PTT mode
   */
  setMode(mode: PttMode): void {
    this.options.pttMode = mode;
    this.pttButton.setMode(mode);
    console.log(`PTT mode changed to: ${mode}`);
  }

  /**
   * Get current PTT mode
   */
  getMode(): PttMode {
    return this.options.pttMode;
  }

  /**
   * Handle errors with callback
   */
  private handleError(error: Error): void {
    if (this.errorCallback) {
      this.errorCallback(error);
    }
  }

  /**
   * Cleanup and destroy controller
   */
  async destroy(): Promise<void> {
    console.log('Destroying PTT controller...');

    try {
      // Stop PTT if active
      if (this.isTransmitting) {
        await this.handlePttStop();
      }

      // Close all consumers and audio elements
      for (const [producerId, { consumer, audioElement }] of this.activeConsumers) {
        consumer.close();
        audioElement.pause();
        audioElement.srcObject = null;
        audioElement.remove();
      }
      this.activeConsumers.clear();

      // Close transports
      await this.transportClient.closeAll();

      // Release microphone
      this.microphoneManager.release();

      // Destroy button
      this.pttButton.destroy();

      // Clear references
      this.audioTrack = null;
      this.producerId = null;
      this.isTransmitting = false;

      console.log('PTT controller destroyed');
    } catch (error) {
      console.error('Error during PTT controller cleanup:', error);
    }
  }
}
