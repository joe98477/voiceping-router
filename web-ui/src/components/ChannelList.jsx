/**
 * ChannelList - Renders a grid of ChannelCard components for assigned channels
 */

import React from 'react';
import { useChannels } from '../context/ChannelContext';
import ChannelCard from './ChannelCard';

/**
 * ChannelList component
 *
 * @param {object} props
 * @param {string} props.wsUrl - WebSocket URL for connections
 * @param {string} props.token - JWT token for authentication
 */
const ChannelList = ({ wsUrl, token }) => {
  const { channels } = useChannels();

  // Show empty state if no channels assigned
  if (!channels || channels.length === 0) {
    return (
      <div className="channel-list__empty">
        No channels assigned. Contact your dispatch operator to get channel access.
      </div>
    );
  }

  // Render channel cards in a grid
  return (
    <div className="channel-list">
      {channels.map((channel) => (
        <ChannelCard
          key={channel.id}
          channel={channel}
          wsUrl={wsUrl}
          token={token}
        />
      ))}
    </div>
  );
};

export default ChannelList;
