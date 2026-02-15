/**
 * Location data types for server-side storage and broadcast
 */

export interface LocationData {
  userId: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  speed: number | null;
  heading: number | null;
  motionState: 'still' | 'walking' | 'driving' | 'unknown';
  timestamp: string; // ISO8601 from client
}

/**
 * Location data with stale indicator for dispatch queries
 */
export interface LocationPosition extends LocationData {
  isStale: boolean; // true if timestamp > 5 minutes old
}

/**
 * Validate lat/lng range. Reject invalid coordinates.
 * Returns error string if invalid, null if valid.
 */
export function validateLocation(lat: number, lng: number): string | null {
  if (typeof lat !== 'number' || typeof lng !== 'number') {
    return 'Latitude and longitude must be numbers';
  }
  if (isNaN(lat) || isNaN(lng)) {
    return 'Latitude and longitude must not be NaN';
  }
  if (lat < -90 || lat > 90) {
    return `Invalid latitude: ${lat} (must be -90 to 90)`;
  }
  if (lng < -180 || lng > 180) {
    return `Invalid longitude: ${lng} (must be -180 to 180)`;
  }
  return null;
}

/**
 * Validate motion state value
 */
export function isValidMotionState(state: string): state is LocationData['motionState'] {
  return ['still', 'walking', 'driving', 'unknown'].includes(state);
}
