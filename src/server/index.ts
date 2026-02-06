/**
 * VoicePing Router Server Entry Point
 * WebRTC audio infrastructure with mediasoup SFU, Redis state management, and WebSocket signaling
 */

import * as http from 'http';
import { config } from './config';
import { logger } from './logger';
import { workerPool } from './mediasoup/workerPool';
import { RouterManager } from './mediasoup/routerManager';
import { TransportManager } from './mediasoup/transportManager';
import { ProducerConsumerManager } from './mediasoup/producerConsumerManager';
import { connectRedis, disconnectRedis } from './state/redisClient';
import { ChannelStateManager } from './state/channelState';
import { SessionStore } from './state/sessionStore';
import { SignalingServer } from './signaling/websocketServer';
import { SignalingHandlers } from './signaling/handlers';

/**
 * Main server initialization and startup
 */
async function main() {
  try {
    logger.info('Starting VoicePing audio server...');

    // 1. Initialize Redis
    logger.info('Connecting to Redis...');
    await connectRedis();
    logger.info('Redis connected');

    // 2. Initialize mediasoup workers
    logger.info('Initializing mediasoup workers...');
    await workerPool.init();
    logger.info(`mediasoup workers initialized: ${workerPool.getWorkerCount()}`);

    // 3. Create mediasoup managers
    const routerManager = new RouterManager(workerPool);
    const transportManager = new TransportManager(routerManager);
    const producerConsumerManager = new ProducerConsumerManager(transportManager);

    // 4. Create state managers
    const channelStateManager = new ChannelStateManager();
    await channelStateManager.initialize();
    logger.info('Channel state manager initialized');

    const sessionStore = new SessionStore();

    // 5. Create HTTP server with health endpoint and test page (dev only)
    const server = http.createServer((req, res) => {
      if (req.url === '/health' && req.method === 'GET') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(
          JSON.stringify({
            status: 'ok',
            uptime: process.uptime(),
            workers: workerPool.getWorkerCount(),
            connections: signalingServer?.getConnectedClients() || 0,
          })
        );
      } else if (req.url === '/test' && req.method === 'GET' && process.env.NODE_ENV !== 'production') {
        // Serve test demo page (development only)
        const fs = require('fs');
        const path = require('path');
        const htmlPath = path.join(__dirname, '../client/test/pttDemo.html');

        fs.readFile(htmlPath, 'utf8', (err: Error | null, data: string) => {
          if (err) {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Failed to load test page' }));
            return;
          }
          res.writeHead(200, { 'Content-Type': 'text/html' });
          res.end(data);
        });
      } else if (req.url === '/test/pttDemo.js' && req.method === 'GET' && process.env.NODE_ENV !== 'production') {
        // Serve compiled test page JavaScript
        const fs = require('fs');
        const path = require('path');
        const jsPath = path.join(__dirname, '../client/test/pttDemo.js');

        fs.readFile(jsPath, 'utf8', (err: Error | null, data: string) => {
          if (err) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Test page JavaScript not found. Run: npm run build' }));
            return;
          }
          res.writeHead(200, { 'Content-Type': 'application/javascript' });
          res.end(data);
        });
      } else {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Not Found' }));
      }
    });

    // 6. Create signaling handlers and WebSocket server
    const handlers = new SignalingHandlers(
      routerManager,
      transportManager,
      producerConsumerManager,
      channelStateManager,
      sessionStore,
      (channelId, msg, excludeUserId) => signalingServer.broadcastToChannel(channelId, msg, excludeUserId)
    );

    const signalingServer = new SignalingServer(server, handlers);

    // 7. Start HTTP server
    server.listen(config.server.port, config.server.host, () => {
      logger.info(`VoicePing audio server listening on ${config.server.host}:${config.server.port}`);
      logger.info(`mediasoup workers: ${workerPool.getWorkerCount()}`);
      logger.info(`WebSocket signaling: ws://${config.server.host}:${config.server.port}/ws`);
      logger.info(`Health check: http://${config.server.host}:${config.server.port}/health`);
    });

    // 8. Graceful shutdown handlers
    const shutdown = async (signal: string) => {
      logger.info(`${signal} received, shutting down gracefully...`);

      try {
        // Close WebSocket server and connections
        logger.info('Closing WebSocket server...');
        await signalingServer.close();

        // Close HTTP server
        logger.info('Closing HTTP server...');
        await new Promise<void>((resolve, reject) => {
          server.close((err) => {
            if (err) reject(err);
            else resolve();
          });
        });

        // Shutdown channel state manager
        logger.info('Shutting down channel state manager...');
        await channelStateManager.shutdown();

        // Close mediasoup workers
        logger.info('Closing mediasoup workers...');
        await workerPool.close();

        // Disconnect Redis
        logger.info('Disconnecting Redis...');
        await disconnectRedis();

        logger.info('Graceful shutdown complete');
        process.exit(0);
      } catch (err) {
        logger.error('Error during shutdown:', err);
        process.exit(1);
      }
    };

    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));
  } catch (err) {
    logger.error('Fatal startup error:', err);
    process.exit(1);
  }
}

// Start the server
main().catch((err) => {
  logger.error('Unhandled error in main:', err);
  process.exit(1);
});
