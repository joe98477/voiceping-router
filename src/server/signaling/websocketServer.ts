/**
 * WebSocket signaling server with JWT authentication
 * Manages WebSocket connections and routes signaling messages to handlers
 */

import * as http from 'http';
import * as ws from 'ws';
import * as jwt from 'jsonwebtoken';
import { SignalingMessage, SignalingType, createMessage } from '../../shared/protocol';
import { UserRole } from '../../shared/types';
import { config } from '../config';
import { createLogger } from '../logger';
import { SignalingHandlers } from './handlers';
import { PermissionManager } from '../auth/permissionManager';
import { AuditLogger, AuditAction } from '../auth/auditLogger';
import { rateLimiter } from '../auth/rateLimiter';

const logger = createLogger('SignalingServer');

/**
 * Client context attached to each WebSocket connection
 */
export interface ClientContext {
  ws: ws.WebSocket;
  userId: string;
  userName: string;
  channels: Set<string>;
  connectionId: string;
  isAlive: boolean;
  role: UserRole;
  eventId: string;
  authorizedChannels: Set<string>;
  globalRole: string;
}

/**
 * JWT payload structure
 */
interface JwtPayload {
  userId: string;
  userName: string;
  eventId: string;
  channelIds: string[];
  globalRole: string;
  eventRole?: string;
  role?: string;
  [key: string]: unknown;
}

/**
 * SignalingServer manages WebSocket connections and message routing
 */
export class SignalingServer {
  private wss: ws.WebSocketServer;
  private clients = new Map<string, ClientContext>();
  private handlers: SignalingHandlers;
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private permissionManager: PermissionManager;
  private auditLogger: AuditLogger;

  constructor(
    server: http.Server,
    handlers: SignalingHandlers,
    permissionManager: PermissionManager,
    auditLogger: AuditLogger
  ) {
    this.handlers = handlers;
    this.permissionManager = permissionManager;
    this.auditLogger = auditLogger;

    // Create WebSocket server at /ws path
    this.wss = new ws.WebSocketServer({
      server,
      path: '/ws',
      verifyClient: this.verifyClient.bind(this),
    });

    this.wss.on('connection', this.handleConnection.bind(this));

    // Start heartbeat for dead connection detection
    this.startHeartbeat();

    logger.info('WebSocket signaling server initialized at /ws');
  }

  /**
   * Verify JWT token from client connection
   * Supports three token locations: Authorization header, query param, sec-websocket-protocol
   * Includes rate limiting and role extraction
   */
  private verifyClient(
    info: { origin: string; secure: boolean; req: http.IncomingMessage },
    callback: (result: boolean, code?: number, message?: string) => void
  ): void {
    // Run async verification
    this.verifyClientAsync(info, callback).catch((err) => {
      logger.error(`Error in verifyClient: ${err instanceof Error ? err.message : String(err)}`);
      callback(false, 500, 'Internal server error');
    });
  }

  /**
   * Async implementation of client verification
   */
  private async verifyClientAsync(
    info: { origin: string; secure: boolean; req: http.IncomingMessage },
    callback: (result: boolean, code?: number, message?: string) => void
  ): Promise<void> {
    // Extract client IP for rate limiting
    const ip = info.req.socket.remoteAddress || 'unknown';

    // Check connection rate limit
    const connectionLimit = await rateLimiter.consumeConnection(ip);
    if (!connectionLimit.allowed) {
      logger.warn(`Connection rate limit exceeded for IP ${ip}`);
      callback(false, 429, 'Too many connection attempts');
      return;
    }

    let token: string | undefined;

    // 1. Check Authorization header (Bearer token)
    const authHeader = info.req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      token = authHeader.substring(7);
    }

    // 2. Check query param ?token=xxx
    if (!token && info.req.url) {
      const url = new URL(info.req.url, `http://${info.req.headers.host}`);
      token = url.searchParams.get('token') || undefined;
    }

    // 3. Check sec-websocket-protocol header (legacy compatibility)
    if (!token) {
      const protocols = info.req.headers['sec-websocket-protocol'];
      if (protocols) {
        const protocolArray = Array.isArray(protocols) ? protocols : [protocols];
        for (const protocol of protocolArray) {
          // Format: "token-<jwt>"
          if (protocol.startsWith('token-')) {
            token = protocol.substring(6);
            break;
          }
        }
      }
    }

    if (!token) {
      logger.warn('Connection rejected: No JWT token provided');
      callback(false, 401, 'Unauthorized: Missing token');
      return;
    }

    // Check auth rate limit with progressive slowdown
    const authLimit = await rateLimiter.consumeAuth(ip);
    if (!authLimit.allowed) {
      logger.warn(`Auth rate limit exceeded for IP ${ip}, slowdown: ${authLimit.penalty}ms`);
      callback(false, 429, `Too many authentication attempts, retry after ${authLimit.retryAfterMs}ms`);
      return;
    }

    try {
      const decoded = jwt.verify(token, config.auth.jwtSecret) as JwtPayload;

      if (!decoded.userId || !decoded.userName) {
        logger.warn('Connection rejected: Invalid JWT payload (missing userId or userName)');
        await rateLimiter.recordAuthFailure(ip);
        callback(false, 401, 'Unauthorized: Invalid token payload');
        return;
      }

      // Parse role from JWT
      const user = this.permissionManager.parseJwtClaims({
        userId: decoded.userId,
        userName: decoded.userName,
        eventId: decoded.eventId || '',
        channelIds: decoded.channelIds || [],
        globalRole: decoded.globalRole || 'USER',
        eventRole: decoded.eventRole,
        role: decoded.role,
      });

      // Attach user info to request for connection handler
      (info.req as any).userId = decoded.userId;
      (info.req as any).userName = decoded.userName;
      (info.req as any).eventId = user.eventId;
      (info.req as any).role = user.role;
      (info.req as any).channelIds = user.channelIds;
      (info.req as any).globalRole = user.globalRole;

      // Record successful auth
      await rateLimiter.recordAuthSuccess(ip);

      logger.info(`JWT verified for user ${decoded.userId} (${decoded.userName}) with role ${user.role}`);
      callback(true);
    } catch (err) {
      logger.warn(`Connection rejected: JWT verification failed: ${err instanceof Error ? err.message : String(err)}`);
      await rateLimiter.recordAuthFailure(ip);
      callback(false, 401, 'Unauthorized: Invalid or expired token');
    }
  }

  /**
   * Handle new WebSocket connection
   */
  private handleConnection(socket: ws.WebSocket, req: http.IncomingMessage): void {
    const userId = (req as any).userId;
    const userName = (req as any).userName;
    const eventId = (req as any).eventId;
    const role = (req as any).role as UserRole;
    const channelIds = (req as any).channelIds as string[];
    const globalRole = (req as any).globalRole;
    const connectionId = `${userId}:${Date.now()}`;

    const clientContext: ClientContext = {
      ws: socket,
      userId,
      userName,
      channels: new Set(),
      connectionId,
      isAlive: true,
      role,
      eventId,
      authorizedChannels: new Set(channelIds || []),
      globalRole,
    };

    this.clients.set(connectionId, clientContext);

    logger.info(`User ${userId} (${userName}) connected [${connectionId}] with role ${role}`);

    // Audit log AUTH_LOGIN
    this.auditLogger.log({
      action: AuditAction.AUTH_LOGIN,
      actorId: userId,
      eventId,
      metadata: {
        userName,
        role,
        globalRole,
        connectionId,
        channelCount: channelIds.length,
      },
    });

    // Send CHANNEL_LIST message with authorized channels
    const channelListMessage = createMessage(SignalingType.CHANNEL_LIST, {
      channels: Array.from(clientContext.authorizedChannels),
      role,
      globalRole,
    });
    this.sendToClient(socket, channelListMessage);

    // Handle pong responses for heartbeat
    socket.on('pong', () => {
      clientContext.isAlive = true;
    });

    // Handle incoming messages
    socket.on('message', (data: ws.RawData) => {
      this.handleMessage(clientContext, data);
    });

    // Handle connection close
    socket.on('close', () => {
      this.handleDisconnect(clientContext);
    });

    // Handle errors
    socket.on('error', (error: Error) => {
      logger.error(`WebSocket error for ${userId}: ${error.message}`);
      socket.close();
    });
  }

  /**
   * Handle incoming signaling message
   */
  private async handleMessage(ctx: ClientContext, data: ws.RawData): Promise<void> {
    try {
      const messageStr = data.toString();
      const message: SignalingMessage = JSON.parse(messageStr);

      // Validate message has a type
      if (!message.type || !Object.values(SignalingType).includes(message.type)) {
        logger.warn(`Invalid message type from ${ctx.userId}: ${message.type}`);
        this.sendError(ctx.ws, 'Invalid message type', message.id);
        return;
      }

      logger.info(`Received ${message.type} from ${ctx.userId}${message.id ? ` [id: ${message.id}]` : ''}`);

      // Route to appropriate handler
      await this.routeMessage(ctx, message);
    } catch (err) {
      logger.error(`Error handling message from ${ctx.userId}: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx.ws, 'Failed to process message');
    }
  }

  /**
   * Route message to appropriate handler
   */
  private async routeMessage(ctx: ClientContext, message: SignalingMessage): Promise<void> {
    try {
      switch (message.type) {
        case SignalingType.JOIN_CHANNEL:
          await this.handlers.handleJoinChannel(ctx, message);
          break;

        case SignalingType.LEAVE_CHANNEL:
          await this.handlers.handleLeaveChannel(ctx, message);
          break;

        case SignalingType.GET_ROUTER_CAPABILITIES:
          await this.handlers.handleGetRouterCapabilities(ctx, message);
          break;

        case SignalingType.CREATE_TRANSPORT:
          await this.handlers.handleCreateTransport(ctx, message);
          break;

        case SignalingType.CONNECT_TRANSPORT:
          await this.handlers.handleConnectTransport(ctx, message);
          break;

        case SignalingType.PRODUCE:
          await this.handlers.handleProduce(ctx, message);
          break;

        case SignalingType.CONSUME:
          await this.handlers.handleConsume(ctx, message);
          break;

        case SignalingType.PTT_START:
          await this.handlers.handlePttStart(ctx, message);
          break;

        case SignalingType.PTT_STOP:
          await this.handlers.handlePttStop(ctx, message);
          break;

        case SignalingType.PING:
          await this.handlers.handlePing(ctx, message);
          break;

        default:
          logger.warn(`Unhandled message type: ${message.type}`);
          this.sendError(ctx.ws, `Unhandled message type: ${message.type}`, message.id);
      }
    } catch (err) {
      logger.error(`Handler error for ${message.type}: ${err instanceof Error ? err.message : String(err)}`);
      this.sendError(ctx.ws, err instanceof Error ? err.message : 'Handler error', message.id);
    }
  }

  /**
   * Handle client disconnect
   */
  private async handleDisconnect(ctx: ClientContext): Promise<void> {
    logger.info(`User ${ctx.userId} disconnected [${ctx.connectionId}]`);

    // Call disconnect handler for cleanup
    await this.handlers.handleDisconnect(ctx);

    // Remove from clients map
    this.clients.delete(ctx.connectionId);
  }

  /**
   * Send message to a specific client
   */
  sendToClient(socket: ws.WebSocket, message: SignalingMessage): void {
    if (socket.readyState === ws.OPEN) {
      socket.send(JSON.stringify(message));
    }
  }

  /**
   * Send error message to client
   */
  private sendError(socket: ws.WebSocket, error: string, id?: string): void {
    const errorMessage: SignalingMessage = {
      type: SignalingType.ERROR,
      error,
    };

    if (id) {
      errorMessage.id = id;
    }

    this.sendToClient(socket, errorMessage);
  }

  /**
   * Broadcast message to all clients in a channel
   */
  broadcastToChannel(channelId: string, message: SignalingMessage, excludeUserId?: string): void {
    let sentCount = 0;

    for (const ctx of this.clients.values()) {
      if (ctx.channels.has(channelId) && ctx.userId !== excludeUserId) {
        this.sendToClient(ctx.ws, message);
        sentCount++;
      }
    }

    if (sentCount > 0) {
      logger.info(`Broadcast ${message.type} to ${sentCount} clients in channel ${channelId}`);
    }
  }

  /**
   * Get count of connected clients
   */
  getConnectedClients(): number {
    return this.clients.size;
  }

  /**
   * Start heartbeat interval for dead connection detection and permission refresh
   */
  private startHeartbeat(): void {
    this.heartbeatInterval = setInterval(() => {
      for (const [connectionId, ctx] of this.clients.entries()) {
        if (!ctx.isAlive) {
          logger.warn(`Client ${ctx.userId} failed heartbeat, terminating connection`);
          ctx.ws.terminate();
          this.clients.delete(connectionId);
          continue;
        }

        ctx.isAlive = false;
        ctx.ws.ping();

        // Heartbeat-based permission refresh
        this.refreshClientPermissions(ctx).catch((err) => {
          logger.error(`Error refreshing permissions for ${ctx.userId}: ${err instanceof Error ? err.message : String(err)}`);
        });
      }
    }, 30000); // 30 second interval

    logger.info('WebSocket heartbeat started (30s interval with permission refresh)');
  }

  /**
   * Refresh permissions for a client during heartbeat
   * Compares with current authorizedChannels and handles additions/removals
   */
  private async refreshClientPermissions(ctx: ClientContext): Promise<void> {
    try {
      // Get fresh permissions from Redis
      const freshChannelIds = await this.permissionManager.refreshPermissions(ctx.userId, ctx.eventId);
      const freshChannels = new Set(freshChannelIds);

      // Find added channels
      const addedChannels: string[] = [];
      for (const channelId of freshChannels) {
        if (!ctx.authorizedChannels.has(channelId)) {
          addedChannels.push(channelId);
        }
      }

      // Find removed channels
      const removedChannels: string[] = [];
      for (const channelId of ctx.authorizedChannels) {
        if (!freshChannels.has(channelId)) {
          removedChannels.push(channelId);
        }
      }

      // If no changes, return early
      if (addedChannels.length === 0 && removedChannels.length === 0) {
        return;
      }

      // Update authorized channels
      ctx.authorizedChannels = freshChannels;

      // Handle added channels
      if (addedChannels.length > 0) {
        logger.info(`User ${ctx.userId} gained access to ${addedChannels.length} channels`);

        // Send PERMISSION_UPDATE for additions
        const updateMessage = createMessage(SignalingType.PERMISSION_UPDATE, {
          added: addedChannels,
          removed: [],
          channels: Array.from(freshChannels),
        });
        this.sendToClient(ctx.ws, updateMessage);
      }

      // Handle removed channels
      if (removedChannels.length > 0) {
        logger.info(`User ${ctx.userId} lost access to ${removedChannels.length} channels`);

        // Check if user is currently in any of the removed channels
        for (const channelId of removedChannels) {
          if (ctx.channels.has(channelId)) {
            // User is in this channel - need to remove them
            // Check if they're transmitting (handled by handlers via pendingRemoval)
            await this.handlers.handlePermissionRevocation(ctx, channelId, false);
          }
        }

        // Send PERMISSION_UPDATE for removals
        const updateMessage = createMessage(SignalingType.PERMISSION_UPDATE, {
          added: [],
          removed: removedChannels,
          channels: Array.from(freshChannels),
        });
        this.sendToClient(ctx.ws, updateMessage);
      }
    } catch (err) {
      logger.error(`Failed to refresh permissions for ${ctx.userId}: ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  /**
   * Stop heartbeat interval
   */
  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
      logger.info('WebSocket heartbeat stopped');
    }
  }

  /**
   * Close WebSocket server and all connections
   */
  async close(): Promise<void> {
    this.stopHeartbeat();

    // Close all client connections
    for (const ctx of this.clients.values()) {
      ctx.ws.close();
    }

    this.clients.clear();

    // Close WebSocket server
    return new Promise((resolve, reject) => {
      this.wss.close((err) => {
        if (err) {
          logger.error('Error closing WebSocket server:', err);
          reject(err);
        } else {
          logger.info('WebSocket server closed');
          resolve();
        }
      });
    });
  }
}
