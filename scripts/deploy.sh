#!/bin/bash

# 스크립트 중간에 어떤 뗨령어라도 실패하면, 즉시 스크립트 전체 중단
set -e

# 스크립트를 실행할 때 첫 번째 인자로 받은 값을 IMAGE 라는 변수에 저장
IMAGE="$1"

deploy_replica() {
  local name=$1
  local port=$2

  echo "----- Deploying $name (port $port) -----"

  docker stop "$name" > /dev/null 2>&1 || true
  docker rm "$name" > /dev/null 2>&1 || true

  docker run -d \
    --name "$name" \
    --network host \
    -e SERVER_PORT="$port" \
    --restart unless-stopped \
    "$IMAGE"

    echo "Waiting for $name to become healthy..."
    for i in $(seq 1 30); do
      if curl -sf "http://127.0.0.1:${port}/actuator/health" > /dev/null 2>&1; then
        echo "$name is healthy (attempt $i)"
        return 0
      fi
      sleep 2
    done

    echo "$name failed health check!"
    return 1
}

echo "Pulling image $IMAGE"
docker pull "$IMAGE"

deploy_replica "insighton-front-1" "10440"
deploy_replica "insighton-front-2" "10441"

echo "Rolling deployment complete!"

