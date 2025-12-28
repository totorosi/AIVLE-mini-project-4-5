#!/bin/bash
set -e

APP_DIR=/home/ubuntu/app
PORT=3000
DEPLOY_ROOT=/opt/codedeploy-agent/deployment-root

echo "=== stop existing app ==="
pm2 delete front || true

echo "=== start next.js with pm2 ==="
cd ${APP_DIR}

npm ci --omit=dev

export NODE_ENV=production
export PORT=${PORT}

pm2 start npm --name front -- run start
