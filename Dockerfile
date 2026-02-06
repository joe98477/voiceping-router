# Multi-stage Dockerfile for mediasoup-based audio server
# Stage 1: Builder - Install all deps and compile TypeScript
FROM node:20-bullseye AS builder

# Install build dependencies for mediasoup C++ compilation
RUN apt-get update && apt-get install -y \
    python3 \
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
FROM node:20-bullseye

# Install runtime dependencies for mediasoup
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy package files
COPY package.json package-lock.json ./

# Install production dependencies only
RUN npm ci --production

# Copy compiled code from builder
COPY --from=builder /app/dist ./dist

# Copy audio assets (if they exist)
COPY --from=builder /app/audio ./audio 2>/dev/null || true

# Expose HTTP/WebSocket port
EXPOSE 3000

# Expose mediasoup RTC port range
EXPOSE 40000-49999/udp

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:3000/health || exit 1

# Start the server
CMD ["node", "dist/server/index.js"]
