/**
 * mediasoup-client Device wrapper
 * Manages device initialization with server router capabilities
 */

import * as mediasoupClient from 'mediasoup-client';

type Device = mediasoupClient.types.Device;
type RtpCapabilities = mediasoupClient.types.RtpCapabilities;
import { SignalingClient } from '../signaling/signalingClient';

export class MediasoupDevice {
  private device: Device;
  private signalingClient: SignalingClient;
  private channelId: string | null = null;
  private loadedCapabilities: RtpCapabilities | null = null;

  constructor(signalingClient: SignalingClient) {
    this.device = new mediasoupClient.Device();
    this.signalingClient = signalingClient;
  }

  /**
   * Load device with router capabilities from server
   */
  async load(channelId: string): Promise<RtpCapabilities> {
    try {
      // If already loaded with same capabilities, skip re-loading
      if (this.device.loaded && this.channelId === channelId) {
        return this.device.rtpCapabilities;
      }

      // Request router capabilities from server
      const response = await this.signalingClient.getRouterCapabilities(channelId);

      if (!response.data || !response.data.routerRtpCapabilities) {
        throw new Error('Server did not return router capabilities');
      }

      const routerRtpCapabilities = response.data.routerRtpCapabilities as RtpCapabilities;

      // Load device with server capabilities
      await this.device.load({ routerRtpCapabilities });

      this.channelId = channelId;
      this.loadedCapabilities = routerRtpCapabilities;

      return this.device.rtpCapabilities;
    } catch (error) {
      if (error instanceof Error) {
        if (error.message.includes('UnsupportedError')) {
          throw new Error(
            'Your browser does not support the required WebRTC codecs. Please use a modern browser like Chrome, Firefox, or Safari.'
          );
        }
        throw new Error(`Failed to load mediasoup device: ${error.message}`);
      }
      throw error;
    }
  }

  /**
   * Get the underlying Device instance
   */
  getDevice(): Device {
    if (!this.device.loaded) {
      throw new Error('Device not loaded. Call load() first.');
    }
    return this.device;
  }

  /**
   * Get loaded RTP capabilities
   */
  getRtpCapabilities(): RtpCapabilities {
    if (!this.device.loaded) {
      throw new Error('Device not loaded. Call load() first.');
    }
    return this.device.rtpCapabilities;
  }

  /**
   * Check if device can produce audio
   */
  canProduce(kind: 'audio'): boolean {
    if (!this.device.loaded) {
      return false;
    }
    return this.device.canProduce(kind);
  }

  /**
   * Check if device has been loaded
   */
  isLoaded(): boolean {
    return this.device.loaded;
  }

  /**
   * Get associated channel ID
   */
  getChannelId(): string | null {
    return this.channelId;
  }
}
