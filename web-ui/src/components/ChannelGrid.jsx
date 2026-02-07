/**
 * ChannelGrid - Team-grouped channel grid for dispatch monitoring
 * Groups channels by team with collapsible sections and per-team mute toggles
 */

import React, { useMemo, useState } from 'react';
import DispatchChannelCard from './DispatchChannelCard.jsx';

/**
 * ChannelGrid component
 *
 * @param {object} props
 * @param {Array} props.teams - Array of { id, name } team objects
 * @param {Array} props.channels - Array of { id, name, teamId } channel objects
 * @param {string} props.wsUrl - WebSocket URL
 * @param {string} props.token - JWT token
 * @param {Set} props.mutedChannels - Set of muted channel IDs
 * @param {function} props.onToggleMute - (channelId) => void
 * @param {function} props.onMuteTeam - (teamId, channelIds) => void
 * @param {function} props.onUnmuteTeam - (teamId, channelIds) => void
 * @param {object} props.channelStates - channelStates object from ChannelContext (for stats)
 */
const ChannelGrid = ({
  teams,
  channels,
  wsUrl,
  token,
  mutedChannels,
  onToggleMute,
  onMuteTeam,
  onUnmuteTeam,
  channelStates
}) => {
  // Track expanded/collapsed state for each team (all start expanded)
  const [expandedTeams, setExpandedTeams] = useState(() => {
    const initial = {};
    teams.forEach(team => {
      initial[team.id] = true;
    });
    // Also handle "Event" group (channels with no teamId)
    initial['__event__'] = true;
    return initial;
  });

  // Group channels by team
  const groupedChannels = useMemo(() => {
    const groups = {};

    // Initialize team groups
    teams.forEach(team => {
      groups[team.id] = {
        name: team.name,
        channels: []
      };
    });

    // Add "Event" group for channels without teamId
    groups['__event__'] = {
      name: 'Event',
      channels: []
    };

    // Assign channels to groups
    channels.forEach(channel => {
      const groupId = channel.teamId || '__event__';
      if (groups[groupId]) {
        groups[groupId].channels.push(channel);
      }
    });

    // Sort channels alphabetically within each group
    Object.values(groups).forEach(group => {
      group.channels.sort((a, b) => a.name.localeCompare(b.name));
    });

    // Sort groups alphabetically by name
    const sortedGroupIds = Object.keys(groups).sort((a, b) => {
      return groups[a].name.localeCompare(groups[b].name);
    });

    return { groups, sortedGroupIds };
  }, [teams, channels]);

  const toggleTeamExpanded = (teamId) => {
    setExpandedTeams(prev => ({
      ...prev,
      [teamId]: !prev[teamId]
    }));
  };

  const handleTeamMuteToggle = (teamId, channelIds) => {
    // Check if all channels in team are muted
    const allMuted = channelIds.every(id => mutedChannels.has(id));

    if (allMuted) {
      // Unmute all
      onUnmuteTeam(teamId, channelIds);
    } else {
      // Mute all
      onMuteTeam(teamId, channelIds);
    }
  };

  return (
    <div className="channel-grid">
      {groupedChannels.sortedGroupIds.map(teamId => {
        const group = groupedChannels.groups[teamId];
        if (group.channels.length === 0) {
          // Don't render empty groups
          return null;
        }

        const isExpanded = expandedTeams[teamId];
        const channelIds = group.channels.map(ch => ch.id);
        const allMuted = channelIds.every(id => mutedChannels.has(id));

        return (
          <div key={teamId} className="channel-grid__team-section">
            {/* Team header with expand/collapse and mute toggle */}
            <div className="channel-grid__team-header">
              <div
                style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, cursor: 'pointer' }}
                onClick={() => toggleTeamExpanded(teamId)}
              >
                <span className={`channel-grid__team-chevron ${!isExpanded ? 'channel-grid__team-chevron--collapsed' : ''}`}>
                  ▼
                </span>
                <span className="channel-grid__team-name">{group.name}</span>
              </div>
              <button
                className={`channel-grid__team-mute-btn ${allMuted ? 'channel-grid__team-mute-btn--all-muted' : ''}`}
                onClick={(e) => {
                  e.stopPropagation();
                  handleTeamMuteToggle(teamId, channelIds);
                }}
              >
                {allMuted ? 'Muted' : 'Unmuted'}
              </button>
            </div>

            {/* Channel cards grid */}
            <div className={`channel-grid__cards ${!isExpanded ? 'channel-grid__cards--collapsed' : ''}`}>
              {group.channels.map(channel => (
                <DispatchChannelCard
                  key={channel.id}
                  channel={channel}
                  wsUrl={wsUrl}
                  token={token}
                  isMuted={mutedChannels.has(channel.id)}
                  onToggleMute={() => onToggleMute(channel.id)}
                />
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default ChannelGrid;
