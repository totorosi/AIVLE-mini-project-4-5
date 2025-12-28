#!/bin/bash
set -e

export JWT_SECRET="SecretKey123456SecretKey123456SecretKey123456"
export DB_USERNAME="sa"
export DB_PASSWORD="1234"

APP_DIR=/home/ubuntu/app
LOG_FILE=${APP_DIR}/app.log

cd "$APP_DIR" || exit 1

echo "=== Stop existing WAR ==="
PID=$(pgrep -f 'java.*\.war' || true)
if [ -n "$PID" ]; then
  kill -15 $PID
  sleep 5
fi

echo "=== Find executable WAR (exclude plain.war) ==="
WAR_FILE=$(ls *.war 2>/dev/null | grep -v plain | head -n 1)

if [ -z "$WAR_FILE" ]; then
  echo "❌ Executable WAR not found (plain.war excluded)" >> "$LOG_FILE"
  exit 1
fi

echo "=== Start WAR: $WAR_FILE ==="
nohup /usr/bin/java -jar "$WAR_FILE" >> "$LOG_FILE" 2>&1 &
