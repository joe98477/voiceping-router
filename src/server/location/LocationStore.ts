/**
 * SQLite-based location storage with 24-hour retention
 * Stores location updates from Android clients with lat/lng validation
 */

import Database from 'better-sqlite3';
import * as path from 'path';
import * as fs from 'fs';
import { LocationData } from './types';
import { createLogger } from '../logger';

const logger = createLogger('LocationStore');

/**
 * LocationStore manages SQLite database for location updates
 */
export class LocationStore {
  private db: Database.Database;

  constructor(dbPath: string = './data/locations.db') {
    // Ensure data directory exists
    const dir = path.dirname(dbPath);
    fs.mkdirSync(dir, { recursive: true });

    // Create database with WAL mode for better concurrent reads
    this.db = new Database(dbPath);
    this.db.pragma('journal_mode = WAL');

    // Initialize schema
    this.initSchema();

    logger.info(`Location store initialized at ${dbPath}`);
  }

  /**
   * Create locations table with indexes
   */
  private initSchema(): void {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS locations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id TEXT NOT NULL,
        latitude REAL NOT NULL CHECK(latitude >= -90 AND latitude <= 90),
        longitude REAL NOT NULL CHECK(longitude >= -180 AND longitude <= 180),
        accuracy REAL NOT NULL CHECK(accuracy >= 0),
        speed REAL CHECK(speed IS NULL OR speed >= 0),
        heading REAL CHECK(heading IS NULL OR (heading >= 0 AND heading < 360)),
        motion_state TEXT NOT NULL CHECK(motion_state IN ('still', 'walking', 'driving', 'unknown')),
        timestamp TEXT NOT NULL,
        created_at TEXT NOT NULL DEFAULT (datetime('now'))
      );

      CREATE INDEX IF NOT EXISTS idx_locations_user_time
        ON locations(user_id, timestamp DESC);

      CREATE INDEX IF NOT EXISTS idx_locations_created
        ON locations(created_at);
    `);

    logger.info('Location database schema initialized');
  }

  /**
   * Insert single location update
   */
  insertLocation(data: LocationData): void {
    const stmt = this.db.prepare(`
      INSERT INTO locations (user_id, latitude, longitude, accuracy, speed, heading, motion_state, timestamp)
      VALUES (@userId, @latitude, @longitude, @accuracy, @speed, @heading, @motionState, @timestamp)
    `);

    try {
      stmt.run({
        userId: data.userId,
        latitude: data.latitude,
        longitude: data.longitude,
        accuracy: data.accuracy,
        speed: data.speed,
        heading: data.heading,
        motionState: data.motionState,
        timestamp: data.timestamp,
      });

      logger.debug(`Stored location for user ${data.userId} at ${data.timestamp}`);
    } catch (err) {
      logger.error(`Failed to insert location for ${data.userId}: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  /**
   * Insert batch of location updates in a transaction
   */
  insertBatch(updates: LocationData[]): void {
    if (updates.length === 0) {
      return;
    }

    const stmt = this.db.prepare(`
      INSERT INTO locations (user_id, latitude, longitude, accuracy, speed, heading, motion_state, timestamp)
      VALUES (@userId, @latitude, @longitude, @accuracy, @speed, @heading, @motionState, @timestamp)
    `);

    const insertMany = this.db.transaction((locations: LocationData[]) => {
      for (const data of locations) {
        stmt.run({
          userId: data.userId,
          latitude: data.latitude,
          longitude: data.longitude,
          accuracy: data.accuracy,
          speed: data.speed,
          heading: data.heading,
          motionState: data.motionState,
          timestamp: data.timestamp,
        });
      }
    });

    try {
      insertMany(updates);
      logger.info(`Stored batch of ${updates.length} location updates`);
    } catch (err) {
      logger.error(`Failed to insert location batch: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  /**
   * Get latest location for each user
   * Uses subquery since SQLite doesn't support DISTINCT ON
   */
  getLatestPositions(): LocationData[] {
    const stmt = this.db.prepare(`
      SELECT l.user_id, l.latitude, l.longitude, l.accuracy, l.speed, l.heading, l.motion_state, l.timestamp
      FROM locations l
      INNER JOIN (
        SELECT user_id, MAX(timestamp) as max_ts
        FROM locations
        GROUP BY user_id
      ) latest ON l.user_id = latest.user_id AND l.timestamp = latest.max_ts
    `);

    try {
      const rows = stmt.all() as any[];

      return rows.map((row) => ({
        userId: row.user_id,
        latitude: row.latitude,
        longitude: row.longitude,
        accuracy: row.accuracy,
        speed: row.speed,
        heading: row.heading,
        motionState: row.motion_state,
        timestamp: row.timestamp,
      }));
    } catch (err) {
      logger.error(`Failed to get latest positions: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  /**
   * Clean up location records older than 24 hours
   * Returns number of deleted records
   */
  cleanupOldLocations(): number {
    const stmt = this.db.prepare(`
      DELETE FROM locations
      WHERE created_at < datetime('now', '-24 hours')
    `);

    try {
      const result = stmt.run();
      return result.changes;
    } catch (err) {
      logger.error(`Failed to cleanup old locations: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  /**
   * Close database connection
   */
  close(): void {
    this.db.close();
    logger.info('Location store closed');
  }
}
