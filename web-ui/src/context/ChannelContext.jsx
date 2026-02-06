/**
 * Global channel state management via React Context
 * Provides channel list and per-channel state (busy, speaker) to all components
 */

import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';

const ChannelContext = createContext(null);

/**
 * Channel state provider component
 * Manages channel list and per-channel state (busy, speakerId, speakerName)
 *
 * @param {object} props
 * @param {object|null} props.user - Decoded JWT payload with channelIds array
 * @param {React.ReactNode} props.children - Child components
 */
export const ChannelProvider = ({ user, children }) => {
  // Channel list: [{ id: channelId, name: displayName }]
  // Names default to channelId (MVP limitation - no name API for general users)
  const [channels, setChannelsState] = useState([]);

  // Per-channel state: { [channelId]: { isBusy: boolean, speakerId: string|null, speakerName: string|null } }
  const [channelStates, setChannelStates] = useState({});

  // Initialize channels from user.channelIds when user changes
  useEffect(() => {
    if (!user || !user.channelIds || !Array.isArray(user.channelIds)) {
      setChannelsState([]);
      setChannelStates({});
      return;
    }

    // Map channelIds to { id, name } objects
    // MVP: name defaults to id (awaiting future /api/events/:eventId/my-channels endpoint)
    const channelList = user.channelIds.map((channelId) => ({
      id: channelId,
      name: channelId, // TODO: Replace with real channel name when API available
    }));

    setChannelsState(channelList);

    // Initialize channelStates with empty state for each channel
    const initialStates = {};
    user.channelIds.forEach((channelId) => {
      initialStates[channelId] = {
        isBusy: false,
        speakerId: null,
        speakerName: null,
      };
    });
    setChannelStates(initialStates);
  }, [user]);

  /**
   * Update state for a specific channel (partial merge)
   * @param {string} channelId - Channel identifier
   * @param {object} stateUpdate - Partial state update (isBusy, speakerId, speakerName)
   */
  const updateChannelState = useCallback((channelId, stateUpdate) => {
    setChannelStates((prev) => ({
      ...prev,
      [channelId]: {
        ...(prev[channelId] || { isBusy: false, speakerId: null, speakerName: null }),
        ...stateUpdate,
      },
    }));
  }, []);

  /**
   * Replace entire channel list (used when PERMISSION_UPDATE arrives)
   * Supports both direct value and function updater patterns
   * @param {Array<{id: string, name: string}>|Function} channelListOrUpdater - New channel list or updater function
   */
  const setChannels = useCallback((channelListOrUpdater) => {
    setChannelsState((prevChannels) => {
      // Resolve the new channel list (support function updater pattern)
      const channelList = typeof channelListOrUpdater === 'function'
        ? channelListOrUpdater(prevChannels)
        : channelListOrUpdater;

      if (!Array.isArray(channelList)) {
        console.error('setChannels expects an array of { id, name } objects');
        return prevChannels;
      }

      // Update channel states for new list
      setChannelStates((prev) => {
        const newStates = {};
        channelList.forEach((channel) => {
          newStates[channel.id] = prev[channel.id] || {
            isBusy: false,
            speakerId: null,
            speakerName: null,
          };
        });
        return newStates;
      });

      return channelList;
    });
  }, []);

  const value = {
    channels,
    channelStates,
    updateChannelState,
    setChannels,
  };

  return <ChannelContext.Provider value={value}>{children}</ChannelContext.Provider>;
};

/**
 * Hook to access channel context
 * Must be used within a ChannelProvider
 *
 * @returns {object} Channel context value
 * @returns {Array<{id: string, name: string}>} channels - Channel list
 * @returns {object} channelStates - Per-channel state map
 * @returns {function} updateChannelState - Update channel state (channelId, stateUpdate) => void
 * @returns {function} setChannels - Replace channel list (channelList) => void
 */
export const useChannels = () => {
  const context = useContext(ChannelContext);

  if (!context) {
    throw new Error('useChannels must be used within a ChannelProvider');
  }

  return context;
};
