/**
 * DispatchChannelCard - Compact channel card for dispatch multi-channel monitoring
 * Purpose-built for dispatch monitoring grid with mute toggle, activity indicators, and PTT
 */

import React, { useState, useCallback, useEffect } from 'react';
import { useChannelConnection } from '../hooks/useChannelConnection.js';
import { useChannels } from '../context/ChannelContext.jsx';

/**
 * DispatchChannelCard component
 *
 * @param {object} props
 * @param {object} props.channel - Channel object { id, name }
 * @param {string} props.wsUrl - WebSocket URL
 * @param {string} props.token - JWT token
 * @param {boolean} props.isMuted - Whether this channel's incoming audio is muted
 * @param {function} props.onToggleMute - Called when mute toggle is clicked
 */
const DispatchChannelCard = ({ channel, wsUrl, token, isMuted, onToggleMute }) => {
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

  // Apply muting to consumer tracks when isMuted changes
  useEffect(() => {
    if (!connectionManager) return;

    const transportClient = connectionManager.getTransportClient();
    if (!transportClient) return;

    const consumers = transportClient.getAllConsumers();
    consumers.forEach(consumer => {
      if (consumer.track) {
        consumer.track.enabled = !isMuted;
      }
    });
  }, [connectionManager, isMuted]);

  // Map connection state to CSS class for status pill
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
      return 'dispatch-card__ptt-btn dispatch-card__ptt-btn--busy';
    }
    if (isPressed) {
      return 'dispatch-card__ptt-btn dispatch-card__ptt-btn--transmitting';
    }
    return 'dispatch-card__ptt-btn dispatch-card__ptt-btn--idle';
  };

  const getPttButtonText = () => {
    if (channelState.isBusy && !isPressed) {
      return 'Busy';
    }
    if (isPressed) {
      return 'TX...';
    }
    return 'PTT';
  };

  // Disable button when not connected or busy (but NOT when muted - mute only affects incoming audio)
  const isPttDisabled =
    connectionState !== 'connected' || (channelState.isBusy && !isPressed);

  // Determine card CSS classes
  const cardClasses = [
    'dispatch-card--compact',
    isMuted && 'dispatch-card--muted',
    channelState.isBusy && 'dispatch-card--active',
  ].filter(Boolean).join(' ');

  return (
    <div className={cardClasses}>
      {/* Top row: Channel name, status, mute toggle */}
      <div className="dispatch-card__top-row">
        <div className="dispatch-card__name" title={channel.name}>
          {channel.name}
        </div>
        <span className={`dispatch-card__status pill pill--${getStatusClass()}`}>
          {connectionState}
        </span>
        <button
          className={`dispatch-card__mute-btn ${isMuted ? 'dispatch-card__mute-btn--muted' : 'dispatch-card__mute-btn--unmuted'}`}
          onClick={onToggleMute}
          title={isMuted ? 'Unmute channel' : 'Mute channel'}
        >
          {isMuted ? 'M' : '🔊'}
        </button>
      </div>

      {/* Activity row: Green pulsing dot when active, orange dot when idle */}
      <div className="dispatch-card__activity">
        {connectionState === 'connected' && (
          channelState.isBusy ? (
            <>
              <div className="dispatch-card__pulse" />
              <div className="dispatch-card__speaker-name">
                {channelState.speakerName || 'Unknown'}
              </div>
            </>
          ) : (
            <>
              <div className="dispatch-card__idle-dot" />
              <div className="dispatch-card__idle-label">Idle</div>
            </>
          )
        )}
      </div>

      {/* PTT button */}
      <div className="dispatch-card__ptt">
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
        <div className="dispatch-card__error">
          {pttError || error}
        </div>
      )}
    </div>
  );
};

export default DispatchChannelCard;
