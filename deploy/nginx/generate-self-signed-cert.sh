#!/bin/bash
# Generate self-signed TLS certificates for development/testing
# WARNING: Self-signed certificates will show security warnings in browsers - this is expected for development

set -e

# Certificate output directory
CERT_DIR="$(dirname "$0")/certs"
mkdir -p "$CERT_DIR"

# Certificate details
DAYS_VALID=365
KEY_SIZE=2048
COUNTRY="US"
STATE="CA"
CITY="San Francisco"
ORG="VoicePing Development"
CN="localhost"

echo "Generating self-signed TLS certificate..."
echo "Output directory: $CERT_DIR"

# Generate certificate with SAN (Subject Alternative Name) for localhost and 127.0.0.1
# Use MSYS_NO_PATHCONV=1 on Windows Git Bash to prevent path conversion
MSYS_NO_PATHCONV=1 openssl req -x509 -nodes \
    -days $DAYS_VALID \
    -newkey rsa:$KEY_SIZE \
    -keyout "$CERT_DIR/server.key" \
    -out "$CERT_DIR/server.crt" \
    -subj "/C=$COUNTRY/ST=$STATE/L=$CITY/O=$ORG/CN=$CN" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

# Set appropriate permissions
chmod 600 "$CERT_DIR/server.key"
chmod 644 "$CERT_DIR/server.crt"

echo ""
echo "✓ Self-signed certificate generated successfully!"
echo ""
echo "Certificate: $CERT_DIR/server.crt"
echo "Private key: $CERT_DIR/server.key"
echo "Valid for: $DAYS_VALID days"
echo ""
echo "⚠️  IMPORTANT: Browsers will show security warnings for self-signed certificates."
echo "   This is expected for development. Click 'Advanced' -> 'Proceed to localhost' to continue."
echo ""
echo "To use with Docker Compose:"
echo "  1. Ensure deploy/nginx/certs/ is mounted in nginx container"
echo "  2. Start services: docker compose up -d"
echo "  3. Access via: https://localhost"
echo ""
