#!/bin/bash
# dev.sh - VoicePing dev environment management
# Usage:
#   ./scripts/dev.sh status        # Check all services
#   ./scripts/dev.sh up            # Start all docker services
#   ./scripts/dev.sh down          # Stop all docker services
#   ./scripts/dev.sh restart       # Restart all services
#   ./scripts/dev.sh logs [svc]    # Tail logs (optional: service name)
#   ./scripts/dev.sh test          # Run server unit tests
#   ./scripts/dev.sh test:server   # Server integration test (curl health checks)
#   ./scripts/dev.sh build-apk     # Build debug APK
#   ./scripts/dev.sh devices       # List connected ADB devices
#   ./scripts/dev.sh emulator      # Start Android emulator (no audio, UI only)

set -e
export ANDROID_HOME=/home/oppy/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

REPO="$(cd "$(dirname "$0")/.." && pwd)"
CMD="${1:-status}"

case "$CMD" in
  status)
    echo "=== Docker services ==="
    sudo docker compose -f "$REPO/docker-compose.yml" ps
    echo ""
    echo "=== Health checks ==="
    echo -n "nginx (port 3000):        " && curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/ && echo ""
    echo -n "control-plane (port 4000):" && curl -s http://localhost:4000/health | python3 -c "import sys,json; d=json.load(sys.stdin); print(' ' + d.get('status','?'))" 2>/dev/null || echo " unreachable"
    echo -n "web-ui (port 8080):       " && curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ && echo ""
    echo ""
    echo "=== Endpoints ==="
    echo "  Web UI:        http://localhost:8080"
    echo "  Control plane: http://localhost:4000"
    echo "  Audio WS:      ws://localhost:3000"
    ;;

  up)
    echo "Starting services..."
    sudo docker compose -f "$REPO/docker-compose.yml" up -d
    echo "Done. Run './scripts/dev.sh status' to check."
    ;;

  down)
    sudo docker compose -f "$REPO/docker-compose.yml" down
    ;;

  restart)
    SVC="${2:-}"
    if [ -n "$SVC" ]; then
      echo "Restarting $SVC..."
      sudo docker compose -f "$REPO/docker-compose.yml" restart "$SVC"
    else
      echo "Restarting all services..."
      sudo docker compose -f "$REPO/docker-compose.yml" restart
    fi
    ;;

  logs)
    SVC="${2:-}"
    sudo docker compose -f "$REPO/docker-compose.yml" logs -f --tail=100 $SVC
    ;;

  test)
    cd "$REPO"
    npm install --silent
    npm test
    ;;

  test:server)
    echo "=== Server integration checks ==="
    echo -n "Control plane health: " && curl -s http://localhost:4000/health
    echo ""
    echo -n "Nginx (audio router): " && curl -s http://localhost:3000/
    echo ""
    ;;

  build-apk)
    "$REPO/scripts/build-apk.sh" "${2:-debug}" "${3:-}"
    ;;

  devices)
    echo "=== Connected ADB devices ==="
    adb devices -l
    ;;

  emulator)
    echo "Starting emulator (no KVM — software rendering, UI testing only)..."
    emulator -avd VoicePing_Test -no-audio -no-window &
    echo "Emulator starting in background. Watch with: adb wait-for-device && adb devices"
    ;;

  *)
    echo "Unknown command: $CMD"
    echo "Usage: ./scripts/dev.sh [status|up|down|restart|logs|test|test:server|build-apk|devices|emulator]"
    exit 1
    ;;
esac
