/**
 * VoicePing Router Server Entry Point
 * WebRTC audio infrastructure with mediasoup
 */

import * as http from 'http';
import * as winston from 'winston';
import { config } from './config.js';

// Configure logger
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.json()
  ),
  transports: [
    new winston.transports.Console({
      format: winston.format.combine(
        winston.format.colorize(),
        winston.format.simple()
      ),
    }),
  ],
});

/**
 * Create HTTP server with health check endpoint
 */
const server = http.createServer((req, res) => {
  if (req.url === '/health' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok', uptime: process.uptime() }));
  } else {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not Found' }));
  }
});

/**
 * Start server
 */
server.listen(config.server.port, config.server.host, () => {
  logger.info(`VoicePing Router listening on ${config.server.host}:${config.server.port}`);
  logger.info(`Node.js ${process.version} | mediasoup workers: ${config.mediasoup.numWorkers}`);
  logger.info('Health check: GET /health');
});

/**
 * Graceful shutdown
 */
process.on('SIGTERM', () => {
  logger.info('SIGTERM received, shutting down gracefully');
  server.close(() => {
    logger.info('Server closed');
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  logger.info('SIGINT received, shutting down gracefully');
  server.close(() => {
    logger.info('Server closed');
    process.exit(0);
  });
});
