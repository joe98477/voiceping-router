/**
 * mediasoup Router Manager
 * Creates and manages mediasoup Routers per channel
 */

import { types as mediasoupTypes } from 'mediasoup';
import { WorkerPool } from './workerPool';
import { config } from '../config';
import { createLogger } from '../logger';

const logger = createLogger('RouterManager');

/**
 * RouterManager creates and tracks mediasoup Routers
 * Each channel gets its own Router for audio isolation
 */
export class RouterManager {
  private routers = new Map<string, mediasoupTypes.Router>();
  private workerPool: WorkerPool;

  constructor(workerPool: WorkerPool) {
    this.workerPool = workerPool;
  }

  /**
   * Create a new Router with Opus codec configuration
   */
  async createRouter(): Promise<mediasoupTypes.Router> {
    const worker = this.workerPool.getNextWorker();

    const router = await worker.createRouter({
      mediaCodecs: config.mediasoup.router.mediaCodecs,
    });

    logger.info(`Created router ${router.id} on worker ${worker.pid}`);

    return router;
  }

  /**
   * Get existing router for channel or create new one
   */
  async getOrCreateRouter(channelId: string): Promise<mediasoupTypes.Router> {
    let router = this.routers.get(channelId);

    if (!router) {
      router = await this.createRouter();
      this.routers.set(channelId, router);
      logger.info(`Router created for channel ${channelId}`);
    }

    return router;
  }

  /**
   * Get router for channel
   */
  getRouter(channelId: string): mediasoupTypes.Router | null {
    return this.routers.get(channelId) || null;
  }

  /**
   * Remove router for channel
   */
  async removeRouter(channelId: string): Promise<void> {
    const router = this.routers.get(channelId);

    if (router) {
      router.close();
      this.routers.delete(channelId);
      logger.info(`Router removed for channel ${channelId}`);
    }
  }

  /**
   * Get router RTP capabilities (needed by client Device.load())
   */
  getRtpCapabilities(channelId: string): mediasoupTypes.RtpCapabilities | null {
    const router = this.routers.get(channelId);

    if (!router) {
      logger.warn(`No router found for channel ${channelId}`);
      return null;
    }

    return router.rtpCapabilities;
  }
}
