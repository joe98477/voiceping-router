/**
 * mediasoup Worker Pool
 * Manages lifecycle of mediasoup workers (one per CPU core)
 */

import * as mediasoup from 'mediasoup';
import { types as mediasoupTypes } from 'mediasoup';
import * as os from 'os';
import { config } from '../config';
import { createLogger } from '../logger';

const logger = createLogger('WorkerPool');

/**
 * WorkerPool manages mediasoup workers across CPU cores
 * Uses load-aware distribution for optimal scalability to 1000+ users
 */
export class WorkerPool {
  private workers: mediasoupTypes.Worker[] = [];
  private nextWorkerIndex = 0;
  private routerCounts = new Map<number, number>(); // Track routers per worker

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
      this.routerCounts.set(worker.pid, 0); // Initialize router count
      logger.info(`Worker ${i + 1}/${config.mediasoup.numWorkers} created with PID ${worker.pid}`);
    }

    logger.info(`Worker pool initialized with ${this.workers.length} workers`);
    this.logPoolStatus();
  }

  /**
   * Get next worker using load-aware selection
   * Selects worker with fewest active routers for optimal load distribution
   * Falls back to round-robin if counts are equal
   */
  getNextWorker(): mediasoupTypes.Worker {
    if (this.workers.length === 0) {
      throw new Error('Worker pool not initialized. Call init() first.');
    }

    // Find worker with fewest routers
    let selectedWorker = this.workers[0];
    let minRouterCount = this.routerCounts.get(selectedWorker.pid) || 0;

    for (const worker of this.workers) {
      const routerCount = this.routerCounts.get(worker.pid) || 0;
      if (routerCount < minRouterCount) {
        selectedWorker = worker;
        minRouterCount = routerCount;
      }
    }

    // Increment router count for selected worker
    this.routerCounts.set(selectedWorker.pid, minRouterCount + 1);

    return selectedWorker;
  }

  /**
   * Get count of active workers
   */
  getWorkerCount(): number {
    return this.workers.length;
  }

  /**
   * Get worker statistics for monitoring and debugging
   * Returns array of worker stats with ID, router count, PID, and CPU usage
   */
  getWorkerStats(): Array<{ workerId: number; routerCount: number; pid: number; cpuUsage: number }> {
    return this.workers.map((worker, index) => ({
      workerId: index,
      routerCount: this.routerCounts.get(worker.pid) || 0,
      pid: worker.pid,
      cpuUsage: 0, // mediasoup doesn't expose CPU usage per worker, would need OS-level monitoring
    }));
  }

  /**
   * Calculate optimal worker count for single-server scalability
   * Formula: Reserve 25% CPU headroom for system overhead
   * Returns: Math.max(1, Math.min(cpuCount, Math.floor(cpuCount * 0.75)))
   */
  getOptimalWorkerCount(): number {
    const cpuCount = os.cpus().length;
    // Reserve 25% CPU headroom
    const optimal = Math.max(1, Math.min(cpuCount, Math.floor(cpuCount * 0.75)));
    return optimal;
  }

  /**
   * Log current worker pool status
   * Useful for debugging and monitoring load distribution
   */
  logPoolStatus(): void {
    const stats = this.getWorkerStats();
    const totalRouters = stats.reduce((sum, s) => sum + s.routerCount, 0);
    const optimalCount = this.getOptimalWorkerCount();

    logger.info('Worker Pool Status:');
    logger.info(`  Active workers: ${this.workers.length} (optimal: ${optimalCount})`);
    logger.info(`  Total routers: ${totalRouters}`);

    stats.forEach((stat) => {
      logger.info(
        `  Worker ${stat.workerId} (PID ${stat.pid}): ${stat.routerCount} routers`
      );
    });
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
    this.routerCounts.clear();

    logger.info('All workers closed');
  }
}

// Export singleton instance
export const workerPool = new WorkerPool();
