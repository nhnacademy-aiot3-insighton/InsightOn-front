#!/bin/bash
set -e

IMAGE="$1"

# shellcheck disable=SC1090
set -a
source ~/insighton-config/front.env
set +a

deploy_replica() {
  local name=$1
  local port=$2

  echo "----- Deploying $name (port $port) -----"

  docker stop -t 15 "$name" > /dev/null 2>&1 || true
  docker rm "$name" > /dev/null 2>&1 || true

  docker run -d \
    --name "$name" \
    --network host \
    -e SERVER_PORT="$port" \
    -e EUREKA_USERNAME="$EUREKA_USERNAME" \
    -e EUREKA_PASSWORD="$EUREKA_PASSWORD" \
    --restart unless-stopped \
    "$IMAGE"

  echo "Waiting for $name to become healthy..."
  for i in $(seq 1 30); do
    if curl -sf "http://127.0.0.1:${port}/actuator/health" > /dev/null 2>&1; then
      echo "$name is healthy (attempt $i)"
      break
    fi
    if [ "$i" -eq 30 ]; then
      echo "$name failed health check!"
      return 1
    fi
    sleep 2
  done

  echo "Testing gateway connectivity via $name (informational only)..."
  status_code=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${port}/test-gateway" || echo "000")
  if [ "$status_code" = "200" ]; then
    echo "$name gateway connectivity OK (200)"
  else
    echo "WARNING: $name gateway connectivity check returned $status_code - front is deployed, but gateway may not be reachable yet"
  fi
}

echo "Pulling image $IMAGE"
docker pull "$IMAGE"

deploy_replica "insighton-front-1" "10440"
deploy_replica "insighton-front-2" "10441"

echo "Rolling deployment complete!"