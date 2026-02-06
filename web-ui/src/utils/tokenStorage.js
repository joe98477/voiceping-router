/**
 * Token storage utilities for router JWT session management
 * Uses sessionStorage (not localStorage) - persists across page refresh but clears on tab close
 */

export const TOKEN_KEY = 'voiceping_session_token';

/**
 * Save JWT token to sessionStorage
 * @param {string} token - JWT token string
 */
export const saveToken = (token) => {
  if (!token || typeof token !== 'string') {
    throw new Error('Invalid token: must be a non-empty string');
  }
  try {
    sessionStorage.setItem(TOKEN_KEY, token);
  } catch (err) {
    console.error('Failed to save token to sessionStorage:', err);
    throw new Error('Unable to save session token');
  }
};

/**
 * Retrieve JWT token from sessionStorage
 * @returns {string|null} Token string or null if not found
 */
export const getToken = () => {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch (err) {
    console.error('Failed to read token from sessionStorage:', err);
    return null;
  }
};

/**
 * Remove JWT token from sessionStorage
 */
export const removeToken = () => {
  try {
    sessionStorage.removeItem(TOKEN_KEY);
  } catch (err) {
    console.error('Failed to remove token from sessionStorage:', err);
  }
};
