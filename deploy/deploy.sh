#!/usr/bin/env bash
# Build + (re)deploy the backend on the VPS.
# Run from the deploy/ directory on the server:  bash deploy.sh
# The JAR must already be built (see DEPLOY.md step 3).
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f ".env" ]; then
  echo "ERROR: .env not found. Copy .env.example to .env and fill in real values first."
  exit 1
fi
set -a; source .env; set +a

echo "==> Building image and starting stack..."
docker compose up -d --build

echo "==> Status:"
docker compose ps

echo "==> App logs (last 30 lines):"
docker compose logs --tail=30 app

echo "DONE. Check https://$API_DOMAIN (from .env) once DNS is pointed."
