/**
 * Winston logger configuration
 * Provides structured logging with module-specific labels
 */

import * as winston from 'winston';

const logFormat = winston.format.combine(
  winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
  winston.format.errors({ stack: true }),
  winston.format.printf(({ timestamp, level, message, label, ...meta }) => {
    const labelStr = label ? `[${label}]` : '';
    const metaStr = Object.keys(meta).length > 0 ? ` ${JSON.stringify(meta)}` : '';
    return `${timestamp} ${level.toUpperCase()} ${labelStr} ${message}${metaStr}`;
  })
);

const baseLogger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  format: logFormat,
  transports: [
    new winston.transports.Console({
      format: winston.format.combine(winston.format.colorize(), logFormat),
    }),
  ],
});

/**
 * Create a child logger with module-specific label
 */
export function createLogger(label: string): winston.Logger {
  return baseLogger.child({ label });
}

/**
 * Default logger instance
 */
export const logger = baseLogger;
