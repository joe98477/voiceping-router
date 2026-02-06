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
