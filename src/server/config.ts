/**
 * Server configuration management
 * Loads environment variables and provides typed configuration object
 */

import * as dotenv from 'dotenv';
import * as os from 'os';

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
    rtcpFeedback: [{ type: 'nack' }, { type: 'transport-cc' }],
    // Note: We'll configure mono in the SDP parameters at the transport/producer level
    // mediasoup requires channels: 2 in codec capabilities, but we can use mono for actual transmission
  },
];

/**
 * Detect the container's primary non-loopback IPv4 address.
 *
 * When running inside Docker, this returns the container's bridge IP (e.g. 172.18.0.6).
 * This is used as a SECONDARY mediasoup ICE candidate so that the co-located coturn
 * container can reach mediasoup directly via Docker bridge networking — without needing
 * the TURN relay traffic to hairpin through the public router.
 *
 * Why this matters: coturn (TURN server) needs to forward relay packets to mediasoup's
 * ICE candidate. If mediasoup only announces the PUBLIC IP (e.g. 203.40.59.18), coturn
 * must route packets through the internet gateway (hairpin NAT), requiring port 40000-40099
 * to be forwarded on the router. By ALSO announcing the Docker-internal IP, coturn can
 * forward packets directly across the Docker bridge — no additional router config needed.
 *
 * The env var MEDIASOUP_CONTAINER_IP overrides auto-detection (useful for static IP setups).
 */
function detectContainerIP(): string | undefined {
  const override = process.env.MEDIASOUP_CONTAINER_IP;
  if (override) return override;

  const interfaces = os.networkInterfaces();
  for (const [name, addrs] of Object.entries(interfaces)) {
    if (name === 'lo') continue;
    for (const addr of addrs ?? []) {
      if (addr.family === 'IPv4' && !addr.internal) {
        return addr.address;
      }
    }
  }
  return undefined;
}

/**
 * Build mediasoup listenIps with optional Docker-internal secondary candidate.
 */
function buildListenIps(): Array<{ ip: string; announcedIp?: string }> {
  const listenIp = process.env.MEDIASOUP_LISTEN_IP || '0.0.0.0';
  const announcedIp = process.env.MEDIASOUP_ANNOUNCED_IP || undefined;

  const ips: Array<{ ip: string; announcedIp?: string }> = [{ ip: listenIp, announcedIp }];

  // Secondary candidate: Docker-internal IP for coturn → mediasoup relay path.
  // Skip if it equals the announced public IP (no point duplicating).
  const containerIp = detectContainerIP();
  if (containerIp && containerIp !== announcedIp) {
    console.log(`[config] Adding Docker-internal ICE candidate: ${containerIp} (enables coturn relay without hairpin NAT)`);
    ips.push({ ip: containerIp, announcedIp: undefined });
  }

  return ips;
}

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
    nodeEnv: process.env.NODE_ENV || 'development',
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
    listenIps: buildListenIps(),
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
    tokenTtlSeconds: 3600, // 1 hour per user decision
    permissionRefreshIntervalMs: 30000, // 30s heartbeat-based permission refresh
  },

  ptt: {
    lockTtlSeconds: 30,
    busyTimeoutMs: 30000,
  },

  dispatch: {
    emergencyBroadcastHoldMs: 2000, // 2-second long press guard
    priorityPttEnabled: true,
  },

  channels: {
    defaultMaxUsersPerChannel: 100,
    defaultSimultaneousChannelLimit: 10,
    dispatchSimultaneousChannelLimit: 50,
  },

  jitterBuffer: {
    minMs: 40,
    maxMs: 80,
    defaultMs: 60,
  },

  power: {
    wakeLockTimeoutSeconds: parseInt(process.env.WAKELOCK_TIMEOUT_SECONDS || '300', 10),
  },
};
