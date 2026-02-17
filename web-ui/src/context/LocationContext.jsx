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
 * Derive connection quality from networkType field
 * Heuristic: wifi is better than cellular, power save mode degrades quality
 *
 * @param {object} position - Location position data
 * @returns {string} Connection quality: 'Good', 'Fair', 'Poor', 'Unknown'
 */
export function deriveConnectionQuality(position) {
  if (!position.networkType) return 'Unknown';
  if (position.networkType === 'wifi') {
    return position.powerSaveMode ? 'Fair' : 'Good';
  }
  if (position.networkType === 'cellular') {
    return position.powerSaveMode ? 'Poor' : 'Fair';
  }
  return 'Unknown';
}

/**
 * Format timestamp as relative time (e.g., "2 min ago", "just now")
 *
 * @param {string|number} timestamp - ISO timestamp or epoch milliseconds
 * @returns {string} Human-readable relative time
 */
export function formatRelativeTime(timestamp) {
  if (!timestamp) return 'Unknown';
  const now = Date.now();
  const age = now - new Date(timestamp).getTime();
  if (age < 0) return 'just now';

  const seconds = Math.floor(age / 1000);
  if (seconds < 10) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;

  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m ago`;
}

/**
 * Derive latency proxy from timestamp freshness
 * Shows how recent the position update is
 *
 * @param {object} position - Location position data
 * @returns {string} Latency category: '< 5s', '< 30s', '< 1m', '> 1m', 'N/A'
 */
export function deriveLatency(position) {
  if (!position.timestamp) return 'N/A';
  const ageMs = Date.now() - new Date(position.timestamp).getTime();
  if (ageMs < 5000) return '< 5s';
  if (ageMs < 30000) return '< 30s';
  if (ageMs < 60000) return '< 1m';
  return '> 1m';
}

/**
 * Generate tooltip content for marker hover
 * Shows: Name, Team, Channels (identity glance)
 *
 * @param {object} position - Enriched location position with teamName and channelNames
 * @returns {string} HTML string for Leaflet tooltip
 */
export function generateTooltipContent(position) {
  const channels = position.channelNames?.length > 0
    ? position.channelNames.join(', ')
    : 'None';
  return `<strong>${position.userName || 'Unknown'}</strong><br>` +
    `Team: ${position.teamName || 'Unknown'}<br>` +
    `Channels: ${channels}`;
}

/**
 * Generate popup content for marker click
 * Shows three grouped sections: Identity, Status, Activity
 * Sections conditionally rendered based on settings parameter
 *
 * @param {object} position - Enriched location position with all telemetry fields
 * @param {object} settings - Optional popup field settings (if undefined, all fields shown)
 * @returns {string} HTML string for Leaflet popup
 */
export function generatePopupContent(position, settings) {
  // Default to all fields shown if settings not provided (backward compatible)
  const showChannel = settings?.showChannel !== false;
  const showBattery = settings?.showBattery !== false;
  const showConnection = settings?.showConnection !== false;
  const showMotion = settings?.showMotion !== false;
  const showLocation = settings?.showLocation !== false;
  const showPTTStatus = settings?.showPTTStatus !== false;

  const channels = position.channelNames?.length > 0
    ? position.channelNames.join(', ')
    : 'None';
  const battery = position.batteryPercentage != null
    ? `${Math.round(position.batteryPercentage)}%`
    : 'N/A';
  const connection = deriveConnectionQuality(position);
  const latency = deriveLatency(position);
  const motionState = position.motionState
    ? position.motionState.charAt(0).toUpperCase() + position.motionState.slice(1).toLowerCase()
    : 'Unknown';
  const speed = position.speed != null
    ? `${Math.round(position.speed * 3.6)} km/h`  // m/s to km/h
    : 'N/A';
  const updated = formatRelativeTime(position.timestamp);

  // Build sections conditionally
  let html = '<div class="marker-popup">';

  // Identity section (always shown, but channels conditional)
  html += `<div class="marker-popup__section">
    <div class="marker-popup__section-title">Identity</div>
    <div class="marker-popup__row"><span class="marker-popup__label">Name</span><span>${position.userName || 'Unknown'}</span></div>
    <div class="marker-popup__row"><span class="marker-popup__label">Team</span><span>${position.teamName || 'Unknown'}</span></div>`;

  if (showChannel) {
    html += `<div class="marker-popup__row"><span class="marker-popup__label">Channels</span><span>${channels}</span></div>`;
  }

  html += '</div>';

  // Status section (only if at least one status field enabled)
  const hasStatusFields = showBattery || showConnection;
  if (hasStatusFields) {
    html += '<div class="marker-popup__divider"></div>';
    html += '<div class="marker-popup__section">';
    html += '<div class="marker-popup__section-title">Status</div>';

    if (showBattery) {
      html += `<div class="marker-popup__row"><span class="marker-popup__label">Battery</span><span>${battery}</span></div>`;
    }
    if (showConnection) {
      html += `<div class="marker-popup__row"><span class="marker-popup__label">Connection</span><span>${connection}</span></div>`;
      html += `<div class="marker-popup__row"><span class="marker-popup__label">Latency</span><span>${latency}</span></div>`;
    }

    html += '</div>';
  }

  // Activity section (always shown, but with conditional fields)
  html += '<div class="marker-popup__divider"></div>';
  html += '<div class="marker-popup__section">';
  html += '<div class="marker-popup__section-title">Activity</div>';

  if (showMotion) {
    html += `<div class="marker-popup__row"><span class="marker-popup__label">Motion</span><span>${motionState}</span></div>`;
  }
  if (showLocation) {
    html += `<div class="marker-popup__row"><span class="marker-popup__label">Speed</span><span>${speed}</span></div>`;
  }

  // Updated always shown (dispatchers always need recency info)
  html += `<div class="marker-popup__row"><span class="marker-popup__label">Updated</span><span>${updated}</span></div>`;
  html += '</div>';

  // PTT button (conditional)
  if (showPTTStatus) {
    html += '<div class="marker-popup__divider"></div>';
    html += '<button class="marker-popup__ptt-btn" disabled title="Coming soon">PTT (placeholder)</button>';
  }

  html += '</div>';
  return html;
}

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
