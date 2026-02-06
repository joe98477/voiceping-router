/**
 * Client-side send and receive transport management
 * Manages WebRTC transports for audio production and consumption
 */

import * as mediasoupClient from 'mediasoup-client';

type Transport = mediasoupClient.types.Transport;
type Producer = mediasoupClient.types.Producer;
type Consumer = mediasoupClient.types.Consumer;
import { MediasoupDevice } from './device';
import { ISignalingClient } from '../signaling/signalingClient';

export class TransportClient {
  private device: MediasoupDevice;
  private signalingClient: ISignalingClient;
  private sendTransport: Transport | null = null;
  private recvTransport: Transport | null = null;
  private producer: Producer | null = null;
  private consumers = new Map<string, Consumer>();

  constructor(device: MediasoupDevice, signalingClient: ISignalingClient) {
    this.device = device;
    this.signalingClient = signalingClient;
  }

  /**
   * Create send transport for producing audio
   */
  async createSendTransport(channelId: string): Promise<void> {
    if (this.sendTransport) {
      console.warn('Send transport already exists, reusing');
      return;
    }

    // Request transport creation from server
    const response = await this.signalingClient.createTransport(channelId, 'send');

    if (!response.data) {
      throw new Error('Server did not return transport options');
    }

    const { id, iceParameters, iceCandidates, dtlsParameters } = response.data as any;

    // Create send transport on device
    const device = this.device.getDevice();
    this.sendTransport = device.createSendTransport({
      id,
      iceParameters,
      iceCandidates,
      dtlsParameters,
    });

    // Wire transport events
    this.sendTransport.on('connect', async ({ dtlsParameters }, callback, errback) => {
      try {
        await this.signalingClient.connectTransport(id, dtlsParameters);
        callback();
      } catch (error) {
        errback(error as Error);
      }
    });

    this.sendTransport.on('produce', async ({ kind, rtpParameters }, callback, errback) => {
      try {
        const response = await this.signalingClient.produce(id, kind, rtpParameters, channelId);
        if (!response.data || !response.data.id) {
          throw new Error('Server did not return producer ID');
        }
        callback({ id: response.data.id as string });
      } catch (error) {
        errback(error as Error);
      }
    });

    this.sendTransport.on('connectionstatechange', (state) => {
      console.log(`Send transport connection state: ${state}`);
      if (state === 'failed') {
        console.error('Send transport connection failed');
        // Reconnection will be handled by Plan 06
      }
    });

    console.log('Send transport created');
  }

  /**
   * Create receive transport for consuming audio
   */
  async createRecvTransport(channelId: string): Promise<void> {
    if (this.recvTransport) {
      console.warn('Receive transport already exists, reusing');
      return;
    }

    // Request transport creation from server
    const response = await this.signalingClient.createTransport(channelId, 'recv');

    if (!response.data) {
      throw new Error('Server did not return transport options');
    }

    const { id, iceParameters, iceCandidates, dtlsParameters } = response.data as any;

    // Create receive transport on device
    const device = this.device.getDevice();
    this.recvTransport = device.createRecvTransport({
      id,
      iceParameters,
      iceCandidates,
      dtlsParameters,
    });

    // Wire transport events
    this.recvTransport.on('connect', async ({ dtlsParameters }, callback, errback) => {
      try {
        await this.signalingClient.connectTransport(id, dtlsParameters);
        callback();
      } catch (error) {
        errback(error as Error);
      }
    });

    this.recvTransport.on('connectionstatechange', (state) => {
      console.log(`Receive transport connection state: ${state}`);
      if (state === 'failed') {
        console.error('Receive transport connection failed');
        // Reconnection will be handled by Plan 06
      }
    });

    console.log('Receive transport created');
  }

  /**
   * Produce audio on send transport with PTT-optimized Opus settings
   */
  async produceAudio(track: MediaStreamTrack, channelId: string): Promise<string> {
    if (!this.sendTransport) {
      throw new Error('Send transport not created. Call createSendTransport() first.');
    }

    // Produce audio with PTT-optimized codec options
    this.producer = await this.sendTransport.produce({
      track,
      codecOptions: {
        opusStereo: false, // Mono audio
        opusDtx: false, // CRITICAL: Disable DTX to prevent first-word cutoff
        opusFec: true, // Enable Forward Error Correction for packet loss
        opusMaxPlaybackRate: 48000,
      },
    });

    console.log(`Audio producer created: ${this.producer.id}`);

    // Producer starts paused (server controls via PTT)
    this.producer.pause();

    return this.producer.id;
  }

  /**
   * Consume audio from another user
   */
  async consumeAudio(
    producerId: string,
    channelId: string
  ): Promise<{ consumer: Consumer; track: MediaStreamTrack }> {
    if (!this.recvTransport) {
      throw new Error('Receive transport not created. Call createRecvTransport() first.');
    }

    // Request consumer creation from server
    const response = await this.signalingClient.consume(channelId, producerId);

    if (!response.data) {
      throw new Error('Server did not return consumer parameters');
    }

    const { id, kind, rtpParameters } = response.data as any;

    // Create consumer on receive transport
    const consumer = await this.recvTransport.consume({
      id,
      producerId,
      kind,
      rtpParameters,
    });

    // Resume consumer to start receiving audio
    await consumer.resume();

    // Store consumer reference
    this.consumers.set(consumer.id, consumer);

    console.log(`Audio consumer created: ${consumer.id}`);

    return { consumer, track: consumer.track };
  }

  /**
   * Get producer (if exists)
   */
  getProducer(): Producer | null {
    return this.producer;
  }

  /**
   * Get consumer by ID
   */
  getConsumer(consumerId: string): Consumer | undefined {
    return this.consumers.get(consumerId);
  }

  /**
   * Get all consumers
   */
  getAllConsumers(): Consumer[] {
    const consumers: Consumer[] = [];
    this.consumers.forEach((consumer) => consumers.push(consumer));
    return consumers;
  }

  /**
   * Close send transport and producer
   */
  async closeSendTransport(): Promise<void> {
    if (this.producer) {
      this.producer.close();
      this.producer = null;
    }

    if (this.sendTransport) {
      this.sendTransport.close();
      this.sendTransport = null;
      console.log('Send transport closed');
    }
  }

  /**
   * Close receive transport and consumers
   */
  async closeRecvTransport(): Promise<void> {
    // Close all consumers
    for (const consumer of this.consumers.values()) {
      consumer.close();
    }
    this.consumers.clear();

    if (this.recvTransport) {
      this.recvTransport.close();
      this.recvTransport = null;
      console.log('Receive transport closed');
    }
  }

  /**
   * Close all transports and producers/consumers
   */
  async closeAll(): Promise<void> {
    await this.closeSendTransport();
    await this.closeRecvTransport();
    console.log('All transports closed');
  }
}
