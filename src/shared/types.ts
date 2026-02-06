/**
 * Shared type definitions used by both server and client
 */

/**
 * Channel state representation
 */
export interface ChannelState {
  channelId: string;
  currentSpeaker: string | null;
  speakerName: string | null;
  isBusy: boolean;
  lockTimestamp: number | null;
}

/**
 * User session information
 */
export interface UserSession {
  userId: string;
  userName: string;
  deviceId: string;
  connectedAt: number;
  channels: string[];
}

/**
 * WebRTC transport configuration options
 */
export interface TransportOptions {
  id: string;
  iceParameters: object;
  iceCandidates: object[];
  dtlsParameters: object;
}

/**
 * Audio producer information
 */
export interface ProducerInfo {
  id: string;
  kind: string;
  userId: string;
  channelId: string;
}

/**
 * PTT interaction mode
 */
export enum PttMode {
  HOLD_TO_TALK = 'hold',
  TOGGLE = 'toggle',
}

/**
 * PTT transmission state
 */
export enum PttState {
  IDLE = 'idle',
  TRANSMITTING = 'transmitting',
  BLOCKED = 'blocked',
}

/**
 * Result of attempting to acquire speaker lock
 */
export interface SpeakerLockResult {
  acquired: boolean;
  currentSpeaker?: string;
  currentSpeakerName?: string;
}

/**
 * User role enumeration (maps to Prisma GlobalRole + EventRole)
 */
export enum UserRole {
  ADMIN = 'ADMIN',
  DISPATCH = 'DISPATCH',
  GENERAL = 'GENERAL',
}

/**
 * Authenticated user with JWT claims and permissions
 */
export interface AuthenticatedUser {
  userId: string;
  userName: string;
  eventId: string;
  role: UserRole;
  channelIds: string[];
  globalRole: string;
}

/**
 * Permission set for a user based on role
 */
export interface PermissionSet {
  canJoinChannel: boolean;
  canPtt: boolean;
  canPriorityPtt: boolean;
  canEmergencyBroadcast: boolean;
  canForceDisconnect: boolean;
  canManageChannels: boolean;
}

/**
 * Channel-specific permission
 */
export interface ChannelPermission {
  channelId: string;
  canListen: boolean;
  canSpeak: boolean;
}

/**
 * Audit event for security tracking
 */
export interface AuditEvent {
  id: string;
  actorId: string;
  eventId: string | null;
  action: string;
  targetId: string | null;
  metadata: Record<string, unknown>;
  timestamp: number;
}
