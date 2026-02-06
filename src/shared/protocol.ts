/**
 * Signaling protocol definitions for WebRTC negotiation and PTT control
 * This is the API contract that both server and client implement
 */

/**
 * Signaling message types for client-server communication
 */
export enum SignalingType {
  // Channel management
  JOIN_CHANNEL = 'join-channel',
  LEAVE_CHANNEL = 'leave-channel',

  // WebRTC negotiation
  GET_ROUTER_CAPABILITIES = 'get-router-capabilities',
  CREATE_TRANSPORT = 'create-transport',
  CONNECT_TRANSPORT = 'connect-transport',
  PRODUCE = 'produce',
  CONSUME = 'consume',

  // PTT control
  PTT_START = 'ptt-start',
  PTT_STOP = 'ptt-stop',
  PTT_DENIED = 'ptt-denied',

  // Channel state updates
  SPEAKER_CHANGED = 'speaker-changed',
  CHANNEL_STATE = 'channel-state',

  // Error handling
  ERROR = 'error',

  // Connection health
  PING = 'ping',
  PONG = 'pong',

  // Phase 2: Permission and role management
  PERMISSION_UPDATE = 'permission-update',
  CHANNEL_LIST = 'channel-list',
  FORCE_DISCONNECT = 'force-disconnect',
  PRIORITY_PTT_START = 'priority-ptt-start',
  PRIORITY_PTT_STOP = 'priority-ptt-stop',
  EMERGENCY_BROADCAST_START = 'emergency-broadcast-start',
  EMERGENCY_BROADCAST_STOP = 'emergency-broadcast-stop',
  PTT_INTERRUPTED = 'ptt-interrupted',
  ROLE_INFO = 'role-info',
  BAN_USER = 'ban-user',
  UNBAN_USER = 'unban-user',
}

/**
 * Base signaling message structure
 */
export interface SignalingMessage {
  type: SignalingType;
  id?: string;
  data?: Record<string, unknown>;
  error?: string;
}

/**
 * Signaling request with required correlation ID
 */
export interface SignalingRequest extends SignalingMessage {
  id: string;
}

/**
 * Signaling response with required correlation ID
 */
export interface SignalingResponse extends SignalingMessage {
  id: string;
}

/**
 * Create a properly typed signaling message
 */
export function createMessage(
  type: SignalingType,
  data?: Record<string, unknown>,
  id?: string
): SignalingMessage {
  const message: SignalingMessage = { type };

  if (id !== undefined) {
    message.id = id;
  }

  if (data !== undefined) {
    message.data = data;
  }

  return message;
}
