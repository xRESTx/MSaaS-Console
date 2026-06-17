#!/bin/sh
set -eu

export SERVER_PORT="${SERVER_PORT:-8082}"
export APP_RUNTIME_ROLE="${APP_RUNTIME_ROLE:-EMBEDDED}"
export BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:${SERVER_PORT}}"

java -jar /app/app.jar &
backend_pid=$!

java -cp /app/monolith-gateway.jar MonolithGateway &
gateway_pid=$!

stop_all() {
  kill "$gateway_pid" "$backend_pid" 2>/dev/null || true
  wait "$gateway_pid" "$backend_pid" 2>/dev/null || true
}

trap stop_all INT TERM

while true; do
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    wait "$backend_pid"
    stop_all
    exit 1
  fi
  if ! kill -0 "$gateway_pid" 2>/dev/null; then
    wait "$gateway_pid"
    stop_all
    exit 1
  fi
  sleep 2
done
