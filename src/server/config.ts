/**
 * Server configuration management
 * Loads environment variables and provides typed configuration object
 */

import * as dotenv from 'dotenv';
import * as os from 'os';
import { types as mediasoupTypes } from 'mediasoup';

// Load environment variables
dotenv.config();

/**
 * mediasoup audio codec configuration
 * Opus optimized for real-time voice communication
 */
const mediaCodecs = [
  {
    kind: 'audio' as const,
    mimeType: 'audio/opus',
    preferredPayloadType: 111,
    clockRate: 48000,
    channels: 2,
    rtcpFeedback: [
      { type: 'nack' },
      { type: 'transport-cc' },
    ],
    // Note: We'll configure mono in the SDP parameters at the transport/producer level
    // mediasoup requires channels: 2 in codec capabilities, but we can use mono for actual transmission
  },
];

/**
 * Parse STUN server configuration
 */
function parseStunServer(url: string | undefined): { host: string; port: number } | null {
  if (!url) return null;

  try {
    // Format: stun:host:port
    const match = url.match(/^stun:([^:]+):(\d+)$/);
    if (match) {
      return { host: match[1], port: parseInt(match[2], 10) };
    }
  } catch (err) {
    console.warn('Failed to parse STUN_SERVER:', err);
  }

  return null;
}

/**
 * Parse TURN server configuration
 */
function parseTurnServer(url: string | undefined): { host: string; port: number; protocol: string } | null {
  if (!url) return null;

  try {
    // Format: turn:host:port or turns:host:port
    const match = url.match(/^(turns?):([^:]+):(\d+)$/);
    if (match) {
      return {
        protocol: match[1],
        host: match[2],
        port: parseInt(match[3], 10),
      };
    }
  } catch (err) {
    console.warn('Failed to parse TURN_SERVER:', err);
  }

  return null;
}

/**
 * Server configuration object
 */
export const config = {
  server: {
    port: parseInt(process.env.PORT || '3000', 10),
    host: process.env.HOST || '0.0.0.0',
  },

  mediasoup: {
    numWorkers: os.cpus().length,
    worker: {
      logLevel: (process.env.MEDIASOUP_LOG_LEVEL || 'warn') as 'debug' | 'warn' | 'error' | 'none',
      rtcMinPort: parseInt(process.env.MEDIASOUP_MIN_PORT || '40000', 10),
      rtcMaxPort: parseInt(process.env.MEDIASOUP_MAX_PORT || '49999', 10),
    },
    router: {
      mediaCodecs,
    },
  },

  webrtc: {
    listenIps: [
      {
        ip: process.env.MEDIASOUP_LISTEN_IP || '0.0.0.0',
        announcedIp: process.env.MEDIASOUP_ANNOUNCED_IP || undefined,
      },
    ],
    enableUdp: true,
    enableTcp: true,
    preferUdp: true,
  },

  redis: {
    url: process.env.REDIS_PASSWORD
      ? `redis://:${process.env.REDIS_PASSWORD}@${process.env.REDIS_HOST || '127.0.0.1'}:${process.env.REDIS_PORT || '6379'}`
      : `redis://${process.env.REDIS_HOST || '127.0.0.1'}:${process.env.REDIS_PORT || '6379'}`,
  },

  stun: parseStunServer(process.env.STUN_SERVER),

  turn: parseTurnServer(process.env.TURN_SERVER)
    ? {
      ...parseTurnServer(process.env.TURN_SERVER)!,
      username: process.env.TURN_USERNAME || '',
      password: process.env.TURN_PASSWORD || '',
    }
    : null,

  auth: {
    jwtSecret: process.env.ROUTER_JWT_SECRET || 'change-me',
  },

  ptt: {
    lockTtlSeconds: 30,
    busyTimeoutMs: 30000,
  },
};
