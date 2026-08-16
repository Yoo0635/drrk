#!/bin/bash
# drrk main EC2 진단 — 비밀값(KEY/PASSWORD/SECRET/TOKEN)은 출력하지 않는다.
set -u

echo "===== [1] main.env (non-secret keys) ====="
grep -E '^(INFERENCE_|CONGESTION_|CORS_|ROOT_DOMAIN|SERVER_|SPRING_RABBITMQ_HOST|SPRING_RABBITMQ_PORT|APP_MAIN_IMAGE|COOKIE_)' /opt/drrk/env/main.env \
  | grep -vE 'PASSWORD|SECRET|TOKEN|_KEY' || true

echo "===== [2] containers ====="
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}' || true

echo "===== [3] nginx config actually loaded ====="
ls -la /opt/drrk/main/nginx/templates/ 2>/dev/null || true
docker exec drrk-main-nginx sh -c 'ls /etc/nginx/conf.d/; echo ---; cat /etc/nginx/conf.d/*.conf' 2>/dev/null || true

echo "===== [4] app-main recent logs (congestion / SSE / guide) ====="
docker logs --since 15m drrk-app-main 2>&1 \
  | grep -aE 'AIRPORT GUIDE|CONGESTION|carrier|SSE|inference|ERROR|WARN' | tail -60 || true

echo "===== [5] direct SSE sample from inside app-main (12s) ====="
docker compose --env-file /opt/drrk/env/main.env -f /opt/drrk/main/compose.yml exec -T app-main \
  sh -c 'curl -N -s -m 12 http://localhost:8080/api/v1/inference/carriers/stream | head -c 4000' || true
echo ""

echo "===== [6] REST congestion snapshot ====="
docker compose --env-file /opt/drrk/env/main.env -f /opt/drrk/main/compose.yml exec -T app-main \
  sh -c 'curl -s -m 5 -w "\nHTTP %{http_code}\n" http://localhost:8080/api/v1/platform/congestion' || true

echo "===== [7] via nginx with Host variants ====="
for h in www.drrk.store api.drrk.store drrk.store; do
  echo "--- Host: $h"
  curl -s -m 5 -H "Host: $h" -o /dev/null -w "GET /api/v1/platform/congestion -> %{http_code}\n" \
    http://localhost/api/v1/platform/congestion || true
done

echo "===== [8] listening sockets ====="
(ss -ltnp 2>/dev/null || netstat -ltnp 2>/dev/null) | head -20 || true

echo "===== DONE diagnose-main ====="
