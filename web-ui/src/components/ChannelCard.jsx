/**
 * ChannelCard - Single channel display with PTT button and speaker status
 * Manages ConnectionManager lifecycle and PTT interaction for one channel
 */

import React, { useState, useCallback } from 'react';
import { useChannelConnection } from '../hooks/useChannelConnection';
import { useChannels } from '../context/ChannelContext';

/**
 * ChannelCard component
 *
 * @param {object} props
 * @param {object} props.channel - Channel object { id, name }
 * @param {string} props.wsUrl - WebSocket URL
 * @param {string} props.token - JWT token
 */
const ChannelCard = ({ channel, wsUrl, token }) => {
  // Get connection manager and state from hook
  const { connectionState, error, connectionManager } = useChannelConnection(
    channel.id,
    wsUrl,
    token
  );

  // Get channel state from context
  const { channelStates } = useChannels();
  const channelState = channelStates[channel.id] || {
    isBusy: false,
    speakerId: null,
    speakerName: null,
  };

  // Local PTT button state
  const [isPressed, setIsPressed] = useState(false);
  const [pttError, setPttError] = useState(null);

  // Map connection state to CSS class
  const getStatusClass = () => {
    switch (connectionState) {
      case 'connected':
        return 'ok';
      case 'connecting':
        return 'info';
      case 'reconnecting':
        return 'warn';
      case 'error':
        return 'error';
      case 'disconnected':
      default:
        return 'muted';
    }
  };

  // PTT button press handler (hold to talk)
  const handlePttPress = useCallback(async () => {
    if (!connectionManager || connectionState !== 'connected') {
      console.warn(`[${channel.id}] Cannot start PTT: not connected`);
      return;
    }

    if (isPressed) {
      console.warn(`[${channel.id}] Already transmitting`);
      return;
    }

    try {
      setIsPressed(true);
      setPttError(null);

      await connectionManager.startTransmitting();
      console.log(`[${channel.id}] PTT started`);
    } catch (err) {
      console.error(`[${channel.id}] PTT start failed:`, err);
      setPttError(err.message || 'PTT denied');
      setIsPressed(false);

      // Auto-clear error after 3 seconds
      setTimeout(() => setPttError(null), 3000);
    }
  }, [connectionManager, connectionState, isPressed, channel.id]);

  // PTT button release handler
  const handlePttRelease = useCallback(async () => {
    if (!connectionManager || !isPressed) {
      return;
    }

    try {
      await connectionManager.stopTransmitting();
      console.log(`[${channel.id}] PTT stopped`);
    } catch (err) {
      console.error(`[${channel.id}] PTT stop failed:`, err);
    } finally {
      setIsPressed(false);
    }
  }, [connectionManager, isPressed, channel.id]);

  // Mouse event handlers
  const handleMouseDown = (e) => {
    e.preventDefault();
    handlePttPress();
  };

  const handleMouseUp = (e) => {
    e.preventDefault();
    handlePttRelease();
  };

  const handleMouseLeave = (e) => {
    if (isPressed) {
      handlePttRelease();
    }
  };

  // Touch event handlers
  const handleTouchStart = (e) => {
    e.preventDefault(); // Prevent scrolling
    handlePttPress();
  };

  const handleTouchEnd = (e) => {
    e.preventDefault();
    handlePttRelease();
  };

  // Determine PTT button state
  const getPttButtonClass = () => {
    if (channelState.isBusy && !isPressed) {
      return 'ptt-button ptt-blocked';
    }
    if (isPressed) {
      return 'ptt-button ptt-transmitting';
    }
    return 'ptt-button ptt-idle';
  };

  const getPttButtonText = () => {
    if (channelState.isBusy && !isPressed) {
      return 'Channel Busy';
    }
    if (isPressed) {
      return 'Transmitting...';
    }
    return 'Push to Talk';
  };

  // Disable button when not connected or busy
  const isPttDisabled =
    connectionState !== 'connected' || (channelState.isBusy && !isPressed);

  return (
    <div className={`channel-card ${channelState.isBusy ? 'channel-card--busy' : ''}`}>
      {/* Channel header */}
      <div className="channel-card__header">
        <h3 className="channel-card__name">{channel.name}</h3>
        <span className={`channel-card__status pill pill--${getStatusClass()}`}>
          {connectionState}
        </span>
      </div>

      {/* Speaker indicator (shown when someone is transmitting) */}
      {channelState.isBusy && (
        <div className="channel-card__speaker">
          <span className="channel-card__speaker-icon">🎙 Speaking:</span>
          <span className="channel-card__speaker-name">{channelState.speakerName || 'Unknown'}</span>
        </div>
      )}

      {/* PTT Button */}
      <div className="channel-card__ptt">
        <button
          className={getPttButtonClass()}
          disabled={isPttDisabled}
          onMouseDown={handleMouseDown}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseLeave}
          onTouchStart={handleTouchStart}
          onTouchEnd={handleTouchEnd}
          onTouchCancel={handleTouchEnd}
        >
          {getPttButtonText()}
        </button>
      </div>

      {/* Error display */}
      {(error || pttError) && (
        <div className="channel-card__error">
          {pttError || error}
        </div>
      )}
    </div>
  );
};

export default ChannelCard;
