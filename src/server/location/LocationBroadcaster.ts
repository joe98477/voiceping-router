/**
 * Location broadcaster for real-time location updates to dispatch users
 * Maintains in-memory cache of latest positions with stale indicator
 */

import { LocationData, LocationPosition } from './types';
import { SignalingType } from '../../shared/protocol';
import { createLogger } from '../logger';

const logger = createLogger('LocationBroadcaster');

/**
 * LocationBroadcaster manages real-time broadcast to dispatch users
 */
export class LocationBroadcaster {
  private latestPositions = new Map<string, LocationData>();
  private sendToDispatchUsers: (message: string) => void;
  private lowBatteryAlertSent = new Map<string, boolean>(); // Track per-user low battery alert state

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

    // Compute lowBattery flag server-side
    const lowBattery =
      location.batteryPercentage !== null && location.batteryPercentage !== undefined
        ? location.batteryPercentage < 20
        : false;

    // Broadcast to dispatch users
    const broadcastMessage = JSON.stringify({
      type: 'location-broadcast',
      data: {
        userId,
        userName: location.userName,
        latitude: location.latitude,
        longitude: location.longitude,
        accuracy: location.accuracy,
        speed: location.speed,
        heading: location.heading,
        motionState: location.motionState,
        timestamp: location.timestamp,
        batteryPercentage: location.batteryPercentage ?? null,
        powerSaveMode: location.powerSaveMode ?? null,
        networkType: location.networkType ?? null,
        lowBattery,
      },
    });

    this.sendToDispatchUsers(broadcastMessage);

    logger.debug(`Broadcast location for ${userId} to dispatch users`);

    // Check for low battery alert
    this.checkLowBatteryAlert(userId, location);
  }

  /**
   * Check and send low battery alert with hysteresis
   * Alert fires once when battery drops below 20%, resets when it goes above 20%
   */
  private checkLowBatteryAlert(userId: string, location: LocationData): void {
    const batteryPercentage = location.batteryPercentage;

    // No battery data available
    if (batteryPercentage === null || batteryPercentage === undefined) {
      return;
    }

    const alreadySent = this.lowBatteryAlertSent.get(userId) === true;

    if (batteryPercentage < 20 && !alreadySent) {
      // Battery dropped below 20% and alert not yet sent
      this.lowBatteryAlertSent.set(userId, true);

      const alertMessage = JSON.stringify({
        type: SignalingType.LOW_BATTERY_ALERT,
        data: {
          userId,
          batteryPercentage,
          userName: location.userName,
        },
      });

      this.sendToDispatchUsers(alertMessage);

      logger.warn(`LOW_BATTERY_ALERT sent for user ${userId} (battery: ${batteryPercentage}%)`);
    } else if (batteryPercentage >= 20 && alreadySent) {
      // Battery recovered above 20% - reset hysteresis flag
      this.lowBatteryAlertSent.set(userId, false);
      logger.info(`Low battery alert reset for user ${userId} (battery recovered to ${batteryPercentage}%)`);
    }
  }

  /**
   * Get all latest positions with stale indicator
   * Position is stale if timestamp > 5 minutes old
   * Only returns positions within the last 1 hour (dispatch map view requirement)
   */
  getAllLatestPositions(): LocationPosition[] {
    const now = Date.now();
    const staleThreshold = 5 * 60 * 1000; // 5 minutes
    const oneHourThreshold = 60 * 60 * 1000; // 1 hour

    const positions: LocationPosition[] = [];

    for (const [_userId, position] of this.latestPositions.entries()) {
      const age = now - new Date(position.timestamp).getTime();

      // Skip positions older than 1 hour
      if (age > oneHourThreshold) {
        continue;
      }

      const isStale = age > staleThreshold;

      // Compute lowBattery flag server-side
      const lowBattery =
        position.batteryPercentage !== null && position.batteryPercentage !== undefined
          ? position.batteryPercentage < 20
          : false;

      positions.push({
        ...position,
        isStale,
        lowBattery,
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
