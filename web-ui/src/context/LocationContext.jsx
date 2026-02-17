/**
 * Location state management via React Context
 * Provides Map-based location storage with eager stale cleanup
 * Separate from ChannelContext to prevent high-frequency location updates from re-rendering channel components
 */

import React, { createContext, useContext, useState, useCallback, useEffect, useMemo } from 'react';

const LocationContext = createContext(null);

/**
 * Location provider component
 * Manages Map<userId, LocationPosition> with automatic stale cleanup
 *
 * @param {object} props
 * @param {string} props.eventId - Current event ID (triggers clear on event switch)
 * @param {object} props.overview - Event overview data containing teams and channels
 * @param {React.ReactNode} props.children - Child components
 */
export const LocationProvider = ({ eventId, overview, children }) => {
  // Location state: Map<userId, LocationPosition>
  // LocationPosition = { userId, userName, latitude, longitude, accuracy, speed, heading,
  //                      motionState, timestamp, batteryPercentage, powerSaveMode, networkType,
  //                      lowBattery, isStale, isConnected, teamName, channelNames }
  const [locations, setLocations] = useState(new Map());

  // Build user lookup from overview data
  // Maps userId -> { teamName, channelNames[] }
  const userLookup = useMemo(() => {
    if (!overview) return new Map();
    const lookup = new Map();
    const teamMap = new Map();

    // Build team name lookup
    if (overview.teams) {
      for (const team of overview.teams) {
        teamMap.set(team.id, team.name);
      }
    }

    // Build user -> { teamName, channelNames } mapping from channels
    if (overview.channels) {
      for (const channel of overview.channels) {
        const teamName = teamMap.get(channel.teamId) || 'Unknown';
        if (channel.members && Array.isArray(channel.members)) {
          for (const member of channel.members) {
            // member can be userId string or object with .userId or .id
            const userId = typeof member === 'string' ? member : member.userId || member.id;
            if (!userId) continue;

            if (!lookup.has(userId)) {
              lookup.set(userId, { teamName, channelNames: [] });
            }
            const entry = lookup.get(userId);
            // Use team from first channel found (consistent)
            if (!entry.channelNames.includes(channel.name)) {
              entry.channelNames.push(channel.name);
            }
          }
        }
      }
    }
    return lookup;
  }, [overview]);

  /**
   * Update single location from LOCATION_BROADCAST
   * Performs eager stale cleanup (removes entries older than 1 hour)
   */
  const updateLocation = useCallback((userId, position) => {
    setLocations((prev) => {
      const newMap = new Map(prev);

      // Enrich with team/channel data from overview
      const userInfo = userLookup.get(userId);
      const enrichedPosition = {
        ...position,
        teamName: userInfo?.teamName || 'Unknown',
        channelNames: userInfo?.channelNames || [],
      };

      newMap.set(userId, enrichedPosition);

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
  }, [userLookup]);

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
        // Enrich with team/channel data from overview
        const userInfo = userLookup.get(position.userId);
        const enrichedPosition = {
          ...position,
          teamName: userInfo?.teamName || 'Unknown',
          channelNames: userInfo?.channelNames || [],
        };
        newMap.set(position.userId, enrichedPosition);
      }
    }
    setLocations(newMap);
  }, [userLookup]);

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
          // Enrich with team/channel data from overview
          const userInfo = userLookup.get(position.userId);
          const enrichedPosition = {
            ...position,
            teamName: userInfo?.teamName || 'Unknown',
            channelNames: userInfo?.channelNames || [],
          };
          newMap.set(position.userId, enrichedPosition);
        }
      }
      return newMap;
    });
  }, [userLookup]);

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
