/**
 * Authentication hook for router JWT session management
 * Manages router token (not cookie-based control-plane auth)
 * This token contains channelIds and is used for WebSocket authentication
 */

import { useState, useEffect, useCallback } from 'react';
import { getToken, saveToken, removeToken } from '../utils/tokenStorage.js';

/**
 * Decode JWT payload without external dependencies
 * @param {string} token - JWT token string
 * @returns {object|null} Decoded payload or null if invalid
 */
const decodeJwt = (token) => {
  try {
    if (!token || typeof token !== 'string') {
      return null;
    }

    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }

    // Base64url decode: replace URL-safe chars then decode
    let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    // Pad with = if needed
    while (base64.length % 4 !== 0) {
      base64 += '=';
    }

    const payload = JSON.parse(atob(base64));
    return payload;
  } catch (err) {
    console.error('Failed to decode JWT:', err);
    return null;
  }
};

/**
 * Check if JWT token is expired
 * @param {object} payload - Decoded JWT payload
 * @returns {boolean} True if expired or invalid
 */
const isTokenExpired = (payload) => {
  if (!payload || !payload.exp) {
    return true;
  }

  // JWT exp is in seconds, Date.now() is in milliseconds
  const expiryTime = payload.exp * 1000;
  return expiryTime <= Date.now();
};

/**
 * Router JWT authentication hook
 * Manages router token lifecycle (login/logout/session restore)
 *
 * @returns {object} Authentication state and methods
 * @returns {object|null} user - Decoded JWT payload (userId, displayName, globalRole, channelIds, etc.)
 * @returns {string|null} token - Raw JWT token string
 * @returns {function} login - Store token and decode user (token: string) => void
 * @returns {function} logout - Clear token and user state () => void
 * @returns {boolean} isAuthenticated - True if user is authenticated with valid token
 */
export const useAuth = () => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);

  // Initialize from sessionStorage on mount
  useEffect(() => {
    const storedToken = getToken();
    if (storedToken) {
      const payload = decodeJwt(storedToken);
      if (payload && !isTokenExpired(payload)) {
        setUser(payload);
        setToken(storedToken);
      } else {
        // Token expired or invalid, clear it
        removeToken();
      }
    }
  }, []);

  /**
   * Authenticate with router JWT token
   * @param {string} newToken - JWT token from /api/router/token
   */
  const login = useCallback((newToken) => {
    if (!newToken || typeof newToken !== 'string') {
      console.error('Invalid token provided to login');
      return;
    }

    const payload = decodeJwt(newToken);
    if (!payload) {
      console.error('Failed to decode token in login');
      return;
    }

    if (isTokenExpired(payload)) {
      console.error('Token is already expired');
      return;
    }

    saveToken(newToken);
    setToken(newToken);
    setUser(payload);
  }, []);

  /**
   * Clear authentication state
   */
  const logout = useCallback(() => {
    removeToken();
    setToken(null);
    setUser(null);
  }, []);

  return {
    user,
    token,
    login,
    logout,
    isAuthenticated: user !== null && token !== null,
  };
};
