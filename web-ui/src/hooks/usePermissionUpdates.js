/**
 * Global WebSocket hook for listening to PERMISSION_UPDATE messages
 * Manages a lightweight WebSocket connection separate from per-channel ConnectionManager instances
 * This connection only listens for permission updates - no audio/WebRTC setup
 */

import { useEffect, useRef, useState } from 'react';

/**
 * Hook to manage global permission update WebSocket connection
 *
 * @param {string} wsUrl - Full WebSocket URL (e.g., wss://host/ws)
 * @param {string} token - JWT token for authentication
 * @param {function} onPermissionUpdate - Callback: ({ added: string[], removed: string[] }) => void
 * @returns {{ connected: boolean }} - Connection status
 */
export const usePermissionUpdates = (wsUrl, token, onPermissionUpdate) => {
  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const reconnectDelayRef = useRef(2000); // Start at 2 seconds
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    // Skip if no token
    if (!token || !wsUrl) {
      return;
    }

    let isMounted = true;

    const connect = () => {
      // Clean up existing connection
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }

      try {
        // Create WebSocket with same auth sub-protocol as ConnectionManager (SIG-002)
        const ws = new WebSocket(wsUrl, ['voiceping', token]);
        wsRef.current = ws;

        ws.onopen = () => {
          if (!isMounted) return;
          console.log('[usePermissionUpdates] Connected to permission update WebSocket');
          setConnected(true);
          // Reset backoff on successful connection
          reconnectDelayRef.current = 2000;
        };

        ws.onmessage = (event) => {
          if (!isMounted) return;

          try {
            const message = JSON.parse(event.data);

            // Filter for PERMISSION_UPDATE messages (type: 'permission-update' from SignalingType enum)
            if (message.type === 'permission-update') {
              // Extract added/removed arrays from message.data
              const data = message.data || {};
              const added = data.added || [];
              const removed = data.removed || [];

              console.log('[usePermissionUpdates] PERMISSION_UPDATE received:', { added, removed });

              // Call callback with update
              if (onPermissionUpdate) {
                onPermissionUpdate({ added, removed });
              }
            }
            // Ignore all other message types (heartbeat pings, channel-state, speaker-changed, etc.)
          } catch (err) {
            console.error('[usePermissionUpdates] Error parsing message:', err);
          }
        };

        ws.onerror = (error) => {
          console.warn('[usePermissionUpdates] WebSocket error:', error);
          // Let close handler trigger reconnect
        };

        ws.onclose = (event) => {
          if (!isMounted) return;

          wsRef.current = null;
          setConnected(false);

          // Normal closure (code 1000) - clean shutdown, don't reconnect
          if (event.code === 1000) {
            console.log('[usePermissionUpdates] WebSocket closed cleanly');
            return;
          }

          // Abnormal closure - auto-reconnect with backoff
          console.warn(`[usePermissionUpdates] WebSocket closed abnormally (code ${event.code}), reconnecting in ${reconnectDelayRef.current}ms`);

          reconnectTimeoutRef.current = setTimeout(() => {
            if (isMounted) {
              connect();
              // Exponential backoff: 2s → 4s → 8s → 16s → 30s (max)
              reconnectDelayRef.current = Math.min(reconnectDelayRef.current * 2, 30000);
            }
          }, reconnectDelayRef.current);
        };
      } catch (err) {
        console.error('[usePermissionUpdates] Error creating WebSocket:', err);
        setConnected(false);

        // Retry with backoff
        if (isMounted) {
          reconnectTimeoutRef.current = setTimeout(() => {
            if (isMounted) {
              connect();
              reconnectDelayRef.current = Math.min(reconnectDelayRef.current * 2, 30000);
            }
          }, reconnectDelayRef.current);
        }
      }
    };

    // Initial connection
    connect();

    // Cleanup on unmount
    return () => {
      isMounted = false;

      // Clear reconnect timeout
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }

      // Close WebSocket cleanly (code 1000)
      if (wsRef.current) {
        wsRef.current.close(1000, 'Component unmounted');
        wsRef.current = null;
      }

      setConnected(false);
    };
  }, [wsUrl, token, onPermissionUpdate]);

  return { connected };
};
