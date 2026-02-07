# Multi-stage Dockerfile for mediasoup-based audio server
# Stage 1: Builder - Install all deps and compile TypeScript
FROM node:22-bookworm AS builder

# Install build dependencies for mediasoup C++ compilation
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    make \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy package files
COPY package.json package-lock.json ./

# Install all dependencies (including devDependencies for building)
RUN npm ci

# Copy source code
COPY . ./

# Build TypeScript
RUN npm run build

# Stage 2: Production - Copy only what's needed
FROM node:22-bookworm-slim

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy package files
COPY package.json package-lock.json ./

# Install production dependencies only (skip postinstall scripts to avoid
# rebuilding mediasoup worker - we copy the prebuilt binary from builder)
RUN npm ci --omit=dev --ignore-scripts

# Copy mediasoup worker binary from builder stage
COPY --from=builder /app/node_modules/mediasoup/worker/out/Release/mediasoup-worker ./node_modules/mediasoup/worker/out/Release/mediasoup-worker

# Copy compiled code from builder
COPY --from=builder /app/dist ./dist

# Expose HTTP/WebSocket port
EXPOSE 3000

# Expose mediasoup RTC port range
EXPOSE 40000-49999/udp

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:3000/health || exit 1

# Start the server
CMD ["node", "dist/server/index.js"]
