/**
 * mediasoup Worker Pool
 * Manages lifecycle of mediasoup workers (one per CPU core)
 */

import * as mediasoup from 'mediasoup';
import { types as mediasoupTypes } from 'mediasoup';
import { config } from '../config';
import { createLogger } from '../logger';

const logger = createLogger('WorkerPool');

/**
 * WorkerPool manages mediasoup workers across CPU cores
 * Uses round-robin distribution for even load balancing
 */
export class WorkerPool {
  private workers: mediasoupTypes.Worker[] = [];
  private nextWorkerIndex = 0;

  /**
   * Initialize worker pool with one worker per CPU core
   */
  async init(): Promise<void> {
    logger.info(`Initializing ${config.mediasoup.numWorkers} mediasoup workers...`);

    for (let i = 0; i < config.mediasoup.numWorkers; i++) {
      const worker = await mediasoup.createWorker({
        logLevel: config.mediasoup.worker.logLevel,
        rtcMinPort: config.mediasoup.worker.rtcMinPort,
        rtcMaxPort: config.mediasoup.worker.rtcMaxPort,
      });

      // Handle worker death (per research Pattern 1 -- let process manager restart)
      worker.on('died', (error) => {
        logger.error(`mediasoup worker ${worker.pid} died:`, error);
        logger.error('Exiting process to trigger restart by process manager');

        setTimeout(() => {
          process.exit(1);
        }, 2000);
      });

      this.workers.push(worker);
      logger.info(`Worker ${i + 1}/${config.mediasoup.numWorkers} created with PID ${worker.pid}`);
    }

    logger.info(`Worker pool initialized with ${this.workers.length} workers`);
  }

  /**
   * Get next worker using round-robin distribution
   * Round-robin provides more even load distribution than random selection
   */
  getNextWorker(): mediasoupTypes.Worker {
    if (this.workers.length === 0) {
      throw new Error('Worker pool not initialized. Call init() first.');
    }

    const worker = this.workers[this.nextWorkerIndex];
    this.nextWorkerIndex = (this.nextWorkerIndex + 1) % this.workers.length;

    return worker;
  }

  /**
   * Get count of active workers
   */
  getWorkerCount(): number {
    return this.workers.length;
  }

  /**
   * Gracefully close all workers
   */
  async close(): Promise<void> {
    logger.info(`Closing ${this.workers.length} workers...`);

    for (const worker of this.workers) {
      worker.close();
    }

    this.workers = [];
    this.nextWorkerIndex = 0;

    logger.info('All workers closed');
  }
}

// Export singleton instance
export const workerPool = new WorkerPool();
