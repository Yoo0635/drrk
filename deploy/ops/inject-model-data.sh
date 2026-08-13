#!/bin/bash
# 합성 모델(추론) 메시지를 프로덕션 RabbitMQ에 발행한다 — Jetson이 보내는 것과 동일한 계약.
# app-main 파서 계약: 정확히 9개 필드, events 키 {t,dur,count,conf,snr}, window_sec=10,
# n_events == len(events), intensity ∈ [0,1], dur ∈ [0.5,6.0], count ∈ [0,4], count_est=null,
# AMQP message_id == JSON message_id, event t ∈ [ts-window_sec, ts].
# 환경: INJECT_COUNT(기본 30), INJECT_INTERVAL(기본 5초)
set -u

COUNT="${INJECT_COUNT:-30}"
INTERVAL="${INJECT_INTERVAL:-5}"
ENV_FILE=/opt/drrk/env/collector.env
SPACE_ID=$(sed -n 's/^CONGESTION_SENSOR_SPACE_ID=//p' "$ENV_FILE" | tr -d '"' | head -1)
RUSER=$(sed -n 's/^RABBITMQ_DEFAULT_USER=//p' "$ENV_FILE" | head -1)
RPASS=$(sed -n 's/^RABBITMQ_DEFAULT_PASS=//p' "$ENV_FILE" | head -1)

if [ -z "$SPACE_ID" ] || [ -z "$RUSER" ] || [ -z "$RPASS" ]; then
  echo "missing SPACE_ID or rabbit credentials in $ENV_FILE" >&2
  exit 1
fi
echo "injecting $COUNT messages every ${INTERVAL}s as space_id=$SPACE_ID"

ok=0
fail=0
for i in $(seq 1 "$COUNT"); do
  MID=$(cat /proc/sys/kernel/random/uuid)
  TS=$(date +%s)
  N=$(( (RANDOM % 3) + 1 ))
  T1=$(( TS - 4 ))
  T2=$(( TS - 2 ))
  if [ "$N" -ge 2 ]; then
    EVENTS="[{\"t\":$T1.0,\"dur\":1.5,\"count\":1,\"conf\":0.9,\"snr\":12.0},{\"t\":$T2.0,\"dur\":2.0,\"count\":$((N-1)),\"conf\":0.85,\"snr\":11.0}]"
    NE=2
  else
    EVENTS="[{\"t\":$T1.0,\"dur\":1.5,\"count\":1,\"conf\":0.9,\"snr\":12.0}]"
    NE=1
  fi
  PAYLOAD="{\"message_id\":\"$MID\",\"space_id\":\"$SPACE_ID\",\"ts\":$TS.0,\"window_sec\":10,\"events\":$EVENTS,\"n_events\":$NE,\"n_carriers\":$N,\"intensity\":0.42,\"count_est\":null}"
  if docker exec drrk-collector-rabbitmq rabbitmqadmin -u "$RUSER" -p "$RPASS" publish \
      exchange=drrk.inference.exchange routing_key=inference.window.v1 \
      properties="{\"message_id\":\"$MID\",\"content_type\":\"application/json\",\"delivery_mode\":2}" \
      payload="$PAYLOAD" >/dev/null 2>&1; then
    ok=$((ok+1))
  else
    fail=$((fail+1))
  fi
  [ "$i" -lt "$COUNT" ] && sleep "$INTERVAL"
done

echo "published ok=$ok fail=$fail"
echo "===== queue depth after injection ====="
docker exec drrk-collector-rabbitmq rabbitmqctl list_queues name messages 2>/dev/null || true
echo "===== collector congestion logs (last 2m) ====="
docker logs --since 2m drrk-app-collector 2>&1 | grep -aE 'CONGESTION|CALCULATION|PUBLISH|SKIPPED' | tail -30 || true
echo "===== DONE inject-model-data ====="
