/**
 * Location broadcaster for real-time location updates to dispatch users
 * Maintains in-memory cache of latest positions with stale indicator
 */

import { LocationData, LocationPosition } from './types';
import { createLogger } from '../logger';

const logger = createLogger('LocationBroadcaster');

/**
 * LocationBroadcaster manages real-time broadcast to dispatch users
 */
export class LocationBroadcaster {
  private latestPositions = new Map<string, LocationData>();
  private sendToDispatchUsers: (message: string) => void;

  constructor(sendToDispatchUsers: (message: string) => void) {
    this.sendToDispatchUsers = sendToDispatchUsers;
    logger.info('Location broadcaster initialized');
  }

  /**
   * Broadcast location update to all dispatch users
   * Only broadcasts if newer than cached position
   */
  broadcastLocation(userId: string, location: LocationData): void {
    const existing = this.latestPositions.get(userId);

    // Skip if we have a newer or equal timestamp already
    if (existing && location.timestamp <= existing.timestamp) {
      logger.debug(`Skipping broadcast for ${userId}: not newer than cached position`);
      return;
    }

    // Update cache
    this.latestPositions.set(userId, location);

    // Broadcast to dispatch users
    const broadcastMessage = JSON.stringify({
      type: 'location-broadcast',
      data: {
        userId,
        latitude: location.latitude,
        longitude: location.longitude,
        accuracy: location.accuracy,
        speed: location.speed,
        heading: location.heading,
        motionState: location.motionState,
        timestamp: location.timestamp,
      },
    });

    this.sendToDispatchUsers(broadcastMessage);

    logger.debug(`Broadcast location for ${userId} to dispatch users`);
  }

  /**
   * Get all latest positions with stale indicator
   * Position is stale if timestamp > 5 minutes old
   */
  getAllLatestPositions(): LocationPosition[] {
    const now = Date.now();
    const staleThreshold = 5 * 60 * 1000; // 5 minutes

    const positions: LocationPosition[] = [];

    for (const [userId, position] of this.latestPositions.entries()) {
      const age = now - new Date(position.timestamp).getTime();
      const isStale = age > staleThreshold;

      positions.push({
        ...position,
        isStale,
      });
    }

    return positions;
  }

  /**
   * Clear stale entries from cache to prevent unbounded growth
   * Removes entries older than 24 hours
   */
  clearStaleCache(): void {
    const now = Date.now();
    const staleThreshold = 24 * 60 * 60 * 1000; // 24 hours

    let removedCount = 0;

    for (const [userId, position] of this.latestPositions.entries()) {
      const age = now - new Date(position.timestamp).getTime();
      if (age > staleThreshold) {
        this.latestPositions.delete(userId);
        removedCount++;
      }
    }

    if (removedCount > 0) {
      logger.info(`Cleared ${removedCount} stale positions from cache`);
    }
  }
}
