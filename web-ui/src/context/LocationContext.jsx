/**
 * Location state management via React Context
 * Provides Map-based location storage with eager stale cleanup
 * Separate from ChannelContext to prevent high-frequency location updates from re-rendering channel components
 */

import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';

const LocationContext = createContext(null);

/**
 * Location provider component
 * Manages Map<userId, LocationPosition> with automatic stale cleanup
 *
 * @param {object} props
 * @param {string} props.eventId - Current event ID (triggers clear on event switch)
 * @param {React.ReactNode} props.children - Child components
 */
export const LocationProvider = ({ eventId, children }) => {
  // Location state: Map<userId, LocationPosition>
  // LocationPosition = { userId, userName, latitude, longitude, accuracy, speed, heading,
  //                      motionState, timestamp, batteryPercentage, powerSaveMode, networkType,
  //                      lowBattery, isStale, isConnected }
  const [locations, setLocations] = useState(new Map());

  /**
   * Update single location from LOCATION_BROADCAST
   * Performs eager stale cleanup (removes entries older than 1 hour)
   */
  const updateLocation = useCallback((userId, position) => {
    setLocations((prev) => {
      const newMap = new Map(prev);
      newMap.set(userId, position);

      // Eager stale cleanup: remove entries older than 1 hour
      const now = Date.now();
      const oneHourThreshold = 60 * 60 * 1000; // 1 hour

      for (const [entryUserId, entryPosition] of newMap.entries()) {
        const age = now - new Date(entryPosition.timestamp).getTime();
        if (age > oneHourThreshold) {
          newMap.delete(entryUserId);
        }
      }

      return newMap;
    });
  }, []);

  /**
   * Bulk set from LOCATION_QUERY response
   * Replaces entire Map with fresh data
   */
  const setAllLocations = useCallback((positions) => {
    if (!Array.isArray(positions)) {
      console.error('setAllLocations expects an array of position objects');
      return;
    }

    const newMap = new Map();
    for (const position of positions) {
      if (position.userId) {
        newMap.set(position.userId, position);
      }
    }
    setLocations(newMap);
  }, []);

  /**
   * Merge positions into existing Map
   * Used for reconnect - preserves existing markers, merges fresh data
   */
  const mergeLocations = useCallback((positions) => {
    if (!Array.isArray(positions)) {
      console.error('mergeLocations expects an array of position objects');
      return;
    }

    setLocations((prev) => {
      const newMap = new Map(prev);
      for (const position of positions) {
        if (position.userId) {
          newMap.set(position.userId, position);
        }
      }
      return newMap;
    });
  }, []);

  /**
   * Remove single location entry
   */
  const removeLocation = useCallback((userId) => {
    setLocations((prev) => {
      const newMap = new Map(prev);
      newMap.delete(userId);
      return newMap;
    });
  }, []);

  /**
   * Clear all locations (called on event switch)
   */
  const clearLocations = useCallback(() => {
    setLocations(new Map());
  }, []);

  // Clear locations when eventId changes
  useEffect(() => {
    clearLocations();
  }, [eventId, clearLocations]);

  const value = {
    locations,
    updateLocation,
    setAllLocations,
    mergeLocations,
    removeLocation,
    clearLocations,
  };

  return <LocationContext.Provider value={value}>{children}</LocationContext.Provider>;
};

/**
 * Hook to access location context
 * Must be used within a LocationProvider
 *
 * @returns {object} Location context value
 * @returns {Map<string, object>} locations - Map of userId to LocationPosition
 * @returns {function} updateLocation - Update single location (userId, position) => void
 * @returns {function} setAllLocations - Bulk set from array (positions) => void
 * @returns {function} mergeLocations - Merge positions into existing Map (positions) => void
 * @returns {function} removeLocation - Remove single location (userId) => void
 * @returns {function} clearLocations - Clear all locations () => void
 */
export const useLocations = () => {
  const context = useContext(LocationContext);

  if (!context) {
    throw new Error('useLocations must be used within LocationProvider');
  }

  return context;
};
