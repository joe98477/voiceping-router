/**
 * WebSocket signaling server with JWT authentication
 * Manages WebSocket connections and routes signaling messages to handlers
 */

import * as http from 'http';
import * as ws from 'ws';
import * as jwt from 'jsonwebtoken';
import { SignalingMessage, SignalingType } from '../../shared/protocol';
import { config } from '../config';
import { createLogger } from '../logger';
import { SignalingHandlers } from './handlers';

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
}

/**
 * JWT payload structure
 */
interface JwtPayload {
  userId: string;
  userName: string;
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

  constructor(server: http.Server, handlers: SignalingHandlers) {
    this.handlers = handlers;

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
   */
  private verifyClient(
    info: { origin: string; secure: boolean; req: http.IncomingMessage },
    callback: (result: boolean, code?: number, message?: string) => void
  ): void {
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

    try {
      const decoded = jwt.verify(token, config.auth.jwtSecret) as JwtPayload;

      if (!decoded.userId || !decoded.userName) {
        logger.warn('Connection rejected: Invalid JWT payload (missing userId or userName)');
        callback(false, 401, 'Unauthorized: Invalid token payload');
        return;
      }

      // Attach user info to request for connection handler
      (info.req as any).userId = decoded.userId;
      (info.req as any).userName = decoded.userName;

      logger.info(`JWT verified for user ${decoded.userId} (${decoded.userName})`);
      callback(true);
    } catch (err) {
      logger.warn(`Connection rejected: JWT verification failed: ${err instanceof Error ? err.message : String(err)}`);
      callback(false, 401, 'Unauthorized: Invalid or expired token');
    }
  }

  /**
   * Handle new WebSocket connection
   */
  private handleConnection(socket: ws.WebSocket, req: http.IncomingMessage): void {
    const userId = (req as any).userId;
    const userName = (req as any).userName;
    const connectionId = `${userId}:${Date.now()}`;

    const clientContext: ClientContext = {
      ws: socket,
      userId,
      userName,
      channels: new Set(),
      connectionId,
      isAlive: true,
    };

    this.clients.set(connectionId, clientContext);

    logger.info(`User ${userId} (${userName}) connected [${connectionId}]`);

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
   * Start heartbeat interval for dead connection detection
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
      }
    }, 30000); // 30 second interval

    logger.info('WebSocket heartbeat started (30s interval)');
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
