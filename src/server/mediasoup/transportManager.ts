/**
 * mediasoup Transport Manager
 * Creates and manages WebRTC transports for sending and receiving audio
 */

import { types as mediasoupTypes } from 'mediasoup';
import { RouterManager } from './routerManager';
import { TransportOptions } from '../../shared/types';
import { config } from '../config';
import { createLogger } from '../logger';

const logger = createLogger('TransportManager');

/**
 * TransportManager creates and tracks WebRTC transports
 * Each user gets send/recv transports per channel for bidirectional audio
 */
export class TransportManager {
  private transports = new Map<string, mediasoupTypes.WebRtcTransport>();
  private transportIdToKey = new Map<string, string>();
  private routerManager: RouterManager;

  constructor(routerManager: RouterManager) {
    this.routerManager = routerManager;
  }

  /**
   * Create WebRTC transport for sending or receiving audio
   * Optimized for voice: 600kbps outgoing bitrate (sufficient for Opus)
   * Server-side jitter buffering handled by mediasoup pacing mechanism
   */
  async createWebRtcTransport(
    channelId: string,
    userId: string,
    direction: 'send' | 'recv'
  ): Promise<TransportOptions> {
    const router = await this.routerManager.getOrCreateRouter(channelId);

    const transport = await router.createWebRtcTransport({
      listenIps: config.webrtc.listenIps,
      enableUdp: config.webrtc.enableUdp,
      enableTcp: config.webrtc.enableTcp,
      preferUdp: config.webrtc.preferUdp,
      initialAvailableOutgoingBitrate: 600000, // 600kbps for voice-optimized bandwidth
    });

    const key = `${userId}:${channelId}:${direction}`;
    this.transports.set(key, transport);
    this.transportIdToKey.set(transport.id, key);

    // Monitor DTLS state changes
    transport.on('dtlsstatechange', (dtlsState) => {
      logger.info(`Transport ${transport.id} DTLS state changed to ${dtlsState}`);

      if (dtlsState === 'failed') {
        logger.error(`Transport ${transport.id} DTLS failed, closing transport`);
        this.closeTransport(transport.id);
      }
    });

    // Monitor ICE state changes for debugging
    transport.on('icestatechange', (iceState) => {
      logger.info(`Transport ${transport.id} ICE state changed to ${iceState}`);
    });

    logger.info(`Created ${direction} transport ${transport.id} for user ${userId} in channel ${channelId}`);

    return {
      id: transport.id,
      iceParameters: transport.iceParameters,
      iceCandidates: transport.iceCandidates,
      dtlsParameters: transport.dtlsParameters,
    };
  }

  /**
   * Connect transport with DTLS parameters from client
   */
  async connectTransport(transportId: string, dtlsParameters: mediasoupTypes.DtlsParameters): Promise<void> {
    const transport = this.getTransport(transportId);

    if (!transport) {
      throw new Error(`Transport ${transportId} not found`);
    }

    await transport.connect({ dtlsParameters });
    logger.info(`Transport ${transportId} connected`);
  }

  /**
   * Get transport by mediasoup ID
   */
  getTransport(transportId: string): mediasoupTypes.WebRtcTransport | null {
    const key = this.transportIdToKey.get(transportId);

    if (!key) {
      return null;
    }

    return this.transports.get(key) || null;
  }

  /**
   * Get router for a channel
   */
  getRouterForChannel(channelId: string): mediasoupTypes.Router | null {
    return this.routerManager.getRouter(channelId);
  }

  /**
   * Close and remove transport
   */
  async closeTransport(transportId: string): Promise<void> {
    const key = this.transportIdToKey.get(transportId);

    if (!key) {
      logger.warn(`Transport ${transportId} not found for closing`);
      return;
    }

    const transport = this.transports.get(key);

    if (transport) {
      transport.close();
      this.transports.delete(key);
      this.transportIdToKey.delete(transportId);
      logger.info(`Transport ${transportId} closed and removed`);
    }
  }

  /**
   * Configure jitter buffer for a transport
   * Validates buffer size is within configured min/max range
   * Note: mediasoup handles jitter buffering internally via pacing mechanism
   */
  configureJitterBuffer(transportId: string, bufferMs: number): void {
    const { minMs, maxMs } = config.jitterBuffer;

    if (bufferMs < minMs || bufferMs > maxMs) {
      logger.warn(
        `Jitter buffer ${bufferMs}ms out of range [${minMs}, ${maxMs}], clamping to range`
      );
      bufferMs = Math.max(minMs, Math.min(maxMs, bufferMs));
    }

    logger.info(`Jitter buffer configured for transport ${transportId}: ${bufferMs}ms`);
    // mediasoup handles jitter buffering internally via its pacing mechanism
    // This method primarily validates and logs the configuration
  }

  /**
   * Close all transports for a user (cleanup on disconnect)
   */
  async closeUserTransports(userId: string): Promise<void> {
    const keysToClose: string[] = [];

    for (const key of this.transports.keys()) {
      if (key.startsWith(`${userId}:`)) {
        keysToClose.push(key);
      }
    }

    for (const key of keysToClose) {
      const transport = this.transports.get(key);

      if (transport) {
        transport.close();
        this.transportIdToKey.delete(transport.id);
        this.transports.delete(key);
      }
    }

    if (keysToClose.length > 0) {
      logger.info(`Closed ${keysToClose.length} transports for user ${userId}`);
    }
  }
}
