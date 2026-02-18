/**
 * DispatchConsole - Full dispatch monitoring page
 * Shows all channels grouped by team with stats bar, admin drawer, and mute persistence
 */

import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { apiFetch, apiGet, apiPost } from '../api.js';
import { useAuth } from '../hooks/useAuth.js';
import { ChannelProvider, useChannels } from '../context/ChannelContext.jsx';
import { LocationProvider } from '../context/LocationContext.jsx';
import ChannelGrid from '../components/ChannelGrid.jsx';
import AdminDrawer from '../components/AdminDrawer.jsx';
import { usePermissionUpdates } from '../hooks/usePermissionUpdates.js';
import MapView from '../components/MapView.jsx';

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
const DispatchGridWithContext = ({ overview, wsUrl, token, mutedChannels, onToggleMute, onMuteTeam, onUnmuteTeam, isCollapsed }) => {
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
      isCollapsed={isCollapsed}
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
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [activeTab, setActiveTab] = useState('channels');
  const locationWsRef = useRef(null);

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

  // Dedicated WebSocket connection for location updates (with reconnection)
  const [locationWs, setLocationWs] = useState(null);
  useEffect(() => {
    if (!token) return;

    let reconnectTimer = null;
    let intentionalClose = false;

    const connect = () => {
      const ws = new WebSocket(wsUrl, ['voiceping', token]);
      locationWsRef.current = ws;

      ws.onopen = () => {
        console.log('[LocationWS] Connected');
        setLocationWs(ws);
      };

      ws.onerror = (e) => {
        console.error('[LocationWS] Error:', e);
      };

      ws.onclose = () => {
        console.log('[LocationWS] Disconnected');
        locationWsRef.current = null;
        setLocationWs(null);
        if (!intentionalClose) {
          reconnectTimer = setTimeout(connect, 3000);
        }
      };
    };

    // Delay initial connection to avoid competing with channel WebSockets
    const initialTimer = setTimeout(connect, 2000);

    return () => {
      intentionalClose = true;
      clearTimeout(initialTimer);
      clearTimeout(reconnectTimer);
      if (locationWsRef.current) {
        locationWsRef.current.close();
        locationWsRef.current = null;
      }
      setLocationWs(null);
    };
  }, [token, wsUrl]);

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
        // Step 1: Fetch overview (requires DISPATCH or ADMIN role)
        // Use 30s timeout — Prisma connection pool can be slow on cold start
        let overviewData;
        try {
          overviewData = await apiFetch(`/api/events/${eventId}/overview`, { method: 'GET', timeout: 30000 });
        } catch (overviewErr) {
          if (overviewErr.status === 403) {
            setError('You need DISPATCH or ADMIN role to access the dispatch console.');
          } else {
            setError(overviewErr.message || 'Failed to load event overview');
          }
          setLoading(false);
          return;
        }

        // Step 2: Fetch router token (requires active event membership)
        let tokenResponse;
        try {
          tokenResponse = await apiPost('/api/router/token', { eventId });
        } catch (tokenErr) {
          if (tokenErr.status === 403) {
            setError('You are not an active member of this event. Use Admin Settings to add yourself to the event first.');
          } else {
            setError(tokenErr.message || 'Failed to get router token');
          }
          setLoading(false);
          return;
        }

        if (!tokenResponse || !tokenResponse.token) {
          throw new Error('Invalid token response');
        }

        // Store token in useAuth hook
        login(tokenResponse.token);
        setToken(tokenResponse.token);
        setOverview(overviewData);
      } catch (err) {
        setError(err.message || 'Failed to load dispatch console');
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

  // Compute isMapVisible based on responsive breakpoint
  // Desktop (>=1200px): map always visible
  // Mobile (<1200px): map visible when activeTab === 'map'
  const [isDesktop, setIsDesktop] = useState(window.matchMedia('(min-width: 1200px)').matches);

  useEffect(() => {
    const mediaQuery = window.matchMedia('(min-width: 1200px)');
    const handleMediaChange = (e) => setIsDesktop(e.matches);
    mediaQuery.addEventListener('change', handleMediaChange);
    return () => mediaQuery.removeEventListener('change', handleMediaChange);
  }, []);

  const isMapVisible = isDesktop || activeTab === 'map';

  if (loading) {
    return <div className="screen screen--center">Loading dispatch console...</div>;
  }

  // Retry handler
  const handleRetry = () => {
    setError('');
    setLoading(true);
    setOverview(null);
    setToken(null);
    // Re-trigger the useEffect by updating a dependency
    window.location.reload();
  };

  if (error) {
    return (
      <div className="screen screen--center">
        <div>
          <div className="alert">
            {error}
            <button className="btn btn--secondary" onClick={handleRetry} style={{ marginLeft: '12px' }}>
              Retry
            </button>
          </div>
        </div>
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

      {/* Main content: split layout with channels panel and map panel */}
      <div className="dispatch-console__main-content">
        <LocationProvider eventId={eventId} overview={overview}>
          {/* Channels panel */}
          <div className={`channels-panel ${isCollapsed ? 'channels-panel--collapsed' : ''} ${activeTab === 'channels' ? 'active' : ''}`}>
            <button
              className="channels-panel__collapse-btn"
              onClick={() => setIsCollapsed(prev => !prev)}
              aria-label={isCollapsed ? 'Expand channels panel' : 'Collapse channels panel'}
            >
              {isCollapsed ? '\u25B6' : '\u25C0'}
            </button>
            <ChannelProvider user={user}>
              <DispatchGridWithContext
                overview={overview}
                wsUrl={wsUrl}
                token={token}
                mutedChannels={mutedChannels}
                onToggleMute={toggleMute}
                onMuteTeam={muteTeam}
                onUnmuteTeam={unmuteTeam}
                isCollapsed={isCollapsed}
              />
            </ChannelProvider>
          </div>

          {/* Map panel */}
          <div className={`map-panel ${activeTab === 'map' ? 'active' : ''}`}>
            <div className="map-container">
              <MapView eventId={eventId} ws={locationWs} isMapVisible={isMapVisible} channels={overview?.channels || []} />
            </div>
          </div>
        </LocationProvider>
      </div>

      {/* Mobile tab bar */}
      <div className="mobile-tab-bar">
        <button
          className={`mobile-tab-bar__tab ${activeTab === 'channels' ? 'mobile-tab-bar__tab--active' : ''}`}
          onClick={() => setActiveTab('channels')}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="2" y="3" width="20" height="18" rx="2" />
            <line x1="8" y1="3" x2="8" y2="21" />
            <line x1="16" y1="3" x2="16" y2="21" />
          </svg>
          <span>Channels</span>
        </button>
        <button
          className={`mobile-tab-bar__tab ${activeTab === 'map' ? 'mobile-tab-bar__tab--active' : ''}`}
          onClick={() => setActiveTab('map')}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polygon points="1,6 1,22 8,18 16,22 23,18 23,2 16,6 8,2" />
          </svg>
          <span>Map</span>
        </button>
      </div>

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
