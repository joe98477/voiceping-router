/**
 * React hook for managing ConnectionManager lifecycle per channel
 * One ConnectionManager instance per channel for multi-channel PTT support
 */

import { useRef, useState, useEffect } from 'react';
import { ConnectionManager } from '@client/connectionManager';
import { useChannels } from '../context/ChannelContext';

/**
 * useChannelConnection - Manages ConnectionManager instance for a single channel
 *
 * @param {string} channelId - Channel identifier
 * @param {string} wsUrl - Full WebSocket URL (e.g., wss://host/ws)
 * @param {string} token - JWT token for authentication
 * @returns {object} Connection state and manager instance
 * @returns {string} connectionState - Connection state (disconnected/connecting/connected/reconnecting/error)
 * @returns {string|null} error - Error message if any
 * @returns {ConnectionManager|null} connectionManager - ConnectionManager instance for external control
 */
export const useChannelConnection = (channelId, wsUrl, token) => {
  // Persistent ConnectionManager instance (survives re-renders)
  const managerRef = useRef(null);

  // Connection state tracking
  const [connectionState, setConnectionState] = useState('disconnected');
  const [error, setError] = useState(null);

  // Get updateChannelState from ChannelContext
  const { updateChannelState } = useChannels();

  useEffect(() => {
    // Skip if no token (not authenticated yet)
    if (!token || !channelId || !wsUrl) {
      return;
    }

    // Create ConnectionManager instance
    const manager = new ConnectionManager({
      url: wsUrl,
      token: token,
      channelId: channelId,

      // Connection state change handler
      onStateChange: (state, details) => {
        console.log(`[${channelId}] Connection state: ${state}`, details);
        setConnectionState(state);

        // Clear error when successfully connected
        if (state === 'connected') {
          setError(null);
        }
      },

      // Error handler
      onError: (err) => {
        console.error(`[${channelId}] Connection error:`, err);
        setError(err.message || 'Connection error');
      },

      // Channel state update handler (isBusy, currentSpeaker, speakerName)
      onChannelStateUpdate: (channelState) => {
        console.log(`[${channelId}] Channel state update:`, channelState);

        // Update ChannelContext with new state (server uses currentSpeaker, UI uses speakerId)
        updateChannelState(channelId, {
          isBusy: channelState.isBusy || false,
          speakerId: channelState.currentSpeaker || null,
          speakerName: channelState.speakerName || null,
        });
      },

      // Speaker changed handler
      onSpeakerChanged: (userId, userName) => {
        console.log(`[${channelId}] Speaker changed:`, userId, userName);

        // Update ChannelContext with speaker info
        updateChannelState(channelId, {
          isBusy: !!userId,
          speakerId: userId,
          speakerName: userName,
        });
      },
    });

    // Store in ref for external access
    managerRef.current = manager;

    // Connect to channel
    manager.connect().catch((err) => {
      console.error(`[${channelId}] Failed to connect:`, err);
      setError(err.message || 'Failed to connect');
    });

    // Cleanup function - disconnect when unmounting or deps change
    return () => {
      console.log(`[${channelId}] Cleaning up connection...`);

      if (managerRef.current) {
        managerRef.current.disconnect().catch((err) => {
          console.error(`[${channelId}] Disconnect error:`, err);
        });
        managerRef.current = null;
      }
    };
  }, [channelId, wsUrl, token, updateChannelState]);

  return {
    connectionState,
    error,
    connectionManager: managerRef.current,
  };
};
