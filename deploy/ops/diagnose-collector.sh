#!/bin/bash
# drrk collector EC2 진단 — 비밀값(KEY/PASSWORD/SECRET/TOKEN)은 출력하지 않는다.
set -u

echo "===== [1] collector.env (non-secret keys) ====="
grep -E '^(CONGESTION_|AIRPORT_COLLECTION_|AIRPORT_API_NUM|INFERENCE_|APP_COLLECTOR_IMAGE|SPRING_RABBITMQ_HOST|SPRING_RABBITMQ_PORT|RABBITMQ_PRIVATE_BIND_IP)' /opt/drrk/env/collector.env \
  | grep -vE 'PASSWORD|SECRET|TOKEN|_KEY=' || true

echo "===== [2] containers ====="
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}' || true

echo "===== [3] rabbitmq queues (depth / DLQ) ====="
docker exec drrk-collector-rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged 2>/dev/null || true

echo "===== [4] app-collector recent logs (calculation / publish / skip) ====="
docker logs --since 15m drrk-app-collector 2>&1 \
  | grep -aE 'CONGESTION|CALCULATION|PUBLISH|AIRPORT|SKIPPED|ERROR|WARN' | tail -80 || true

echo "===== [5] server clock ====="
date -u +"%Y-%m-%dT%H:%M:%SZ"

echo "===== DONE diagnose-collector ====="
