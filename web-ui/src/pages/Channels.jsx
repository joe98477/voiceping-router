/**
 * Channels page - Main interface for general users
 * Fetches router token, displays user's assigned channels with PTT controls
 */

import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { apiPost } from '../api.js';
import { useAuth } from '../hooks/useAuth.js';
import { ChannelProvider } from '../context/ChannelContext.jsx';
import ChannelList from '../components/ChannelList.jsx';

/**
 * Get WebSocket URL for router signaling (SIG-001)
 * Constructs ws(s)://${host}/ws
 */
const getWsUrl = () => {
  // Check for env override (development)
  const envUrl = import.meta.env.VITE_ROUTER_WS;
  if (envUrl) {
    // envUrl may or may not have /ws, normalize
    return envUrl.endsWith('/ws') ? envUrl : `${envUrl}/ws`;
  }

  // Production: derive from window.location
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocol}://${window.location.host}/ws`;
};

/**
 * Channels page component
 *
 * @param {object} props
 * @param {object} props.user - Control-plane authenticated user (from cookie auth)
 * @param {function} props.onLogout - Logout callback
 */
const Channels = ({ user, onLogout }) => {
  const { eventId } = useParams();
  const { user: authUser, login } = useAuth();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [token, setToken] = useState(null);
  const [wsUrl] = useState(getWsUrl());

  // Fetch router token on mount
  useEffect(() => {
    const fetchToken = async () => {
      setLoading(true);
      setError('');

      try {
        const response = await apiPost('/api/router/token', { eventId });

        if (!response || !response.token) {
          throw new Error('Invalid token response');
        }

        // Store token in useAuth hook (sessionStorage persistence)
        login(response.token);
        setToken(response.token);
      } catch (err) {
        if (err.status === 403) {
          setError('You are not active in this event. Contact an administrator for access.');
        } else {
          setError(err.message || 'Failed to load channels');
        }
      } finally {
        setLoading(false);
      }
    };

    fetchToken();
  }, [eventId, login]);

  // Retry handler
  const handleRetry = () => {
    setError('');
    setLoading(true);
    // Re-run effect by forcing component update
    window.location.reload();
  };

  return (
    <div className="channels-page">
      {/* Top bar */}
      <header className="channels-page__topbar panel">
        <div className="channels-page__brand">
          <span>ConnectVoice</span>
          <h1>My Channels</h1>
        </div>
        <div className="channels-page__actions">
          <button className="btn" onClick={onLogout}>
            Log out
          </button>
        </div>
      </header>

      {/* Error alert */}
      {error && (
        <div className="alert">
          {error}
          <button className="btn btn--secondary" onClick={handleRetry} style={{ marginLeft: '12px' }}>
            Retry
          </button>
        </div>
      )}

      {/* Loading state */}
      {loading && (
        <div className="screen screen--center">Loading channels...</div>
      )}

      {/* Channel list (rendered when token and authUser available) */}
      {!loading && !error && token && authUser && (
        <ChannelProvider user={authUser}>
          <ChannelList wsUrl={wsUrl} token={token} />
        </ChannelProvider>
      )}
    </div>
  );
};

export default Channels;
