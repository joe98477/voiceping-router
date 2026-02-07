/**
 * DispatchConsole - Full dispatch monitoring page
 * Shows all channels grouped by team with stats bar, admin drawer, and mute persistence
 */

import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { apiGet, apiPost } from '../api.js';
import { useAuth } from '../hooks/useAuth.js';
import { ChannelProvider, useChannels } from '../context/ChannelContext.jsx';
import ChannelGrid from '../components/ChannelGrid.jsx';
import AdminDrawer from '../components/AdminDrawer.jsx';
import { usePermissionUpdates } from '../hooks/usePermissionUpdates.js';

/**
 * Get WebSocket URL for router signaling
 */
const getWsUrl = () => {
  const envUrl = import.meta.env.VITE_ROUTER_WS;
  if (envUrl) {
    return envUrl.endsWith('/ws') ? envUrl : `${envUrl}/ws`;
  }
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocol}://${window.location.host}/ws`;
};

/**
 * Inner component that uses ChannelContext
 * Must be inside ChannelProvider
 */
const DispatchGridWithContext = ({ overview, wsUrl, token, mutedChannels, onToggleMute, onMuteTeam, onUnmuteTeam }) => {
  const { channelStates } = useChannels();

  return (
    <ChannelGrid
      teams={overview.teams || []}
      channels={overview.channels || []}
      wsUrl={wsUrl}
      token={token}
      mutedChannels={mutedChannels}
      onToggleMute={onToggleMute}
      onMuteTeam={onMuteTeam}
      onUnmuteTeam={onUnmuteTeam}
      channelStates={channelStates}
    />
  );
};

/**
 * DispatchConsole page component
 */
const DispatchConsole = ({ user, onLogout }) => {
  const { eventId } = useParams();
  const { login } = useAuth();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [overview, setOverview] = useState(null);
  const [token, setToken] = useState(null);
  const [wsUrl] = useState(getWsUrl());
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [uptime, setUptime] = useState(0);
  const [connectionHealth, setConnectionHealth] = useState('Online');

  // Mute state: load from localStorage
  const [mutedChannels, setMutedChannels] = useState(() => {
    try {
      const stored = localStorage.getItem(`cv.dispatch.muted.${eventId}`);
      return stored ? new Set(JSON.parse(stored)) : new Set();
    } catch {
      return new Set();
    }
  });

  // Save mute state to localStorage whenever it changes
  useEffect(() => {
    localStorage.setItem(`cv.dispatch.muted.${eventId}`, JSON.stringify([...mutedChannels]));
  }, [mutedChannels, eventId]);

  // Uptime counter
  useEffect(() => {
    const startTime = Date.now();
    const interval = setInterval(() => {
      const elapsed = Math.floor((Date.now() - startTime) / 1000);
      setUptime(elapsed);
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  // Connection health monitoring
  useEffect(() => {
    const updateHealth = () => setConnectionHealth(navigator.onLine ? 'Online' : 'Offline');
    updateHealth();
    window.addEventListener('online', updateHealth);
    window.addEventListener('offline', updateHealth);
    return () => {
      window.removeEventListener('online', updateHealth);
      window.removeEventListener('offline', updateHealth);
    };
  }, []);

  // Format uptime as "Xh Ym"
  const uptimeFormatted = useMemo(() => {
    const hours = Math.floor(uptime / 3600);
    const minutes = Math.floor((uptime % 3600) / 60);
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
  }, [uptime]);

  // Fetch overview and token on mount
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError('');

      try {
        // Fetch overview (teams, channels, roster, event info)
        const overviewData = await apiGet(`/api/events/${eventId}/overview`);

        // Fetch router token
        const tokenResponse = await apiPost('/api/router/token', { eventId });
        if (!tokenResponse || !tokenResponse.token) {
          throw new Error('Invalid token response');
        }

        // Store token in useAuth hook
        login(tokenResponse.token);
        setToken(tokenResponse.token);
        setOverview(overviewData);
      } catch (err) {
        if (err.status === 403) {
          setError('You do not have dispatch access to this event.');
        } else {
          setError(err.message || 'Failed to load dispatch console');
        }
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [eventId, login]);

  // Reload overview
  const handleReload = async () => {
    try {
      const overviewData = await apiGet(`/api/events/${eventId}/overview`);
      setOverview(overviewData);
    } catch (err) {
      console.error('Failed to reload overview:', err);
    }
  };

  // Mute toggle handlers
  const toggleMute = useCallback((channelId) => {
    setMutedChannels(prev => {
      const updated = new Set(prev);
      if (updated.has(channelId)) {
        updated.delete(channelId);
      } else {
        updated.add(channelId);
      }
      return updated;
    });
  }, []);

  const muteTeam = useCallback((teamId, channelIds) => {
    setMutedChannels(prev => {
      const updated = new Set(prev);
      channelIds.forEach(id => updated.add(id));
      return updated;
    });
  }, []);

  const unmuteTeam = useCallback((teamId, channelIds) => {
    setMutedChannels(prev => {
      const updated = new Set(prev);
      channelIds.forEach(id => updated.delete(id));
      return updated;
    });
  }, []);

  // Compute stats
  const totalChannels = overview?.channels?.length || 0;
  const mutedCount = mutedChannels.size;

  if (loading) {
    return <div className="screen screen--center">Loading dispatch console...</div>;
  }

  if (error) {
    return (
      <div className="screen screen--center">
        <div className="alert">{error}</div>
      </div>
    );
  }

  if (!overview || !token) {
    return <div className="screen screen--center">No data available</div>;
  }

  return (
    <div className="dispatch-console">
      {/* Header */}
      <header className="dispatch-console__header">
        <div className="dispatch-console__brand">
          <Link to="/events">ConnectVoice</Link>
          <h1>Dispatch Console</h1>
          <span className="dispatch-console__event-name">{overview.event?.name || 'Event'}</span>
        </div>
        <div className="dispatch-console__actions">
          <button
            className="btn btn--secondary"
            onClick={() => setDrawerOpen(true)}
            title="Admin Settings"
          >
            ⚙️
          </button>
          <button className="btn" onClick={onLogout}>
            Log out
          </button>
        </div>
      </header>

      {/* Stats bar */}
      <div className="dispatch-stats">
        <div className="dispatch-stats__item">
          <strong>Event:</strong> {overview.event?.name || 'Unknown'}
        </div>
        <div className="dispatch-stats__divider"></div>
        <div className="dispatch-stats__item">
          <strong>User:</strong> {user.displayName || user.email}
        </div>
        <div className="dispatch-stats__divider"></div>
        <div className="dispatch-stats__item">
          <strong>Channels:</strong> {totalChannels}
        </div>
        <div className="dispatch-stats__divider"></div>
        <div className="dispatch-stats__item">
          <strong>Muted:</strong> {mutedCount}
        </div>
        <div className="dispatch-stats__divider"></div>
        <div className="dispatch-stats__item">
          <strong>Uptime:</strong> {uptimeFormatted}
        </div>
        <div className="dispatch-stats__divider"></div>
        <div className="dispatch-stats__item">
          <strong>Health:</strong> {connectionHealth}
        </div>
      </div>

      {/* Channel grid (wrapped in ChannelProvider) */}
      <ChannelProvider user={user}>
        <DispatchGridWithContext
          overview={overview}
          wsUrl={wsUrl}
          token={token}
          mutedChannels={mutedChannels}
          onToggleMute={toggleMute}
          onMuteTeam={muteTeam}
          onUnmuteTeam={unmuteTeam}
        />
      </ChannelProvider>

      {/* Admin drawer */}
      <AdminDrawer
        isOpen={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        user={user}
        eventId={eventId}
        overview={overview}
        onReload={handleReload}
      />
    </div>
  );
};

export default DispatchConsole;
