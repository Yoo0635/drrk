#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_SCRIPT="${SCRIPT_DIR}/deploy-blue-green.sh"
ROLLBACK_SCRIPT="${SCRIPT_DIR}/rollback-blue-green.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  file="$1"
  expected="$2"
  grep -Fq "$expected" "$file" || fail "expected '$expected' in $file"
}

assert_not_contains() {
  file="$1"
  unexpected="$2"
  if grep -Fq "$unexpected" "$file"; then
    fail "did not expect '$unexpected' in $file"
  fi
}

create_fixture() {
  fixture_root="$(mktemp -d)"
  mkdir -p "$fixture_root/main/nginx/templates" "$fixture_root/main/nginx/conf.d" "$fixture_root/env"
  cat > "$fixture_root/main/nginx/templates/api.conf.template" <<'TEMPLATE'
server {
    listen 80;
    server_name api.${ROOT_DOMAIN};

    location / {
        proxy_pass http://${ACTIVE_APP_UPSTREAM};
    }
}
TEMPLATE
  cat > "$fixture_root/env/main.env" <<'ENV'
APP_MAIN_IMAGE=registry/app-main:old-blue
APP_MAIN_BLUE_IMAGE=registry/app-main:old-blue
APP_MAIN_GREEN_IMAGE=registry/app-main:old-green
ACTIVE_APP_SLOT=blue
ROOT_DOMAIN=example.com
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/drrk
SPRING_DATASOURCE_USERNAME=drrk
SPRING_DATASOURCE_PASSWORD=secret
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=secret
SPRING_RABBITMQ_HOST=rabbitmq
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=drrk
SPRING_RABBITMQ_PASSWORD=secret
INFERENCE_STREAM_REDIS_RETENTION=PT10M
SSE_ROLLBACK_WINDOW_SECONDS=0
SSE_DRAIN_TIMEOUT_SECONDS=0
SSE_DRAIN_RETRY_SECONDS=1
JWT_SECRET=secret
CORS_ALLOWED_ORIGINS=https://app.example.com
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=user
SMTP_PASSWORD=secret
ENV
  cat > "$fixture_root/main/nginx/conf.d/api.conf" <<'CONF'
server {
    listen 80;
    server_name api.example.com;

    location / {
        proxy_pass http://app-main-blue:8080;
    }
}
CONF
  printf '%s\n' "$fixture_root"
}

run_deploy() {
  fixture_root="$1"
  image="$2"
  health_status="$3"
  post_switch_health_status="${4:-success}"
  DRY_RUN=1 \
  DRY_RUN_HEALTH_STATUS="$health_status" \
  DRY_RUN_POST_SWITCH_HEALTH_STATUS="$post_switch_health_status" \
  DRY_RUN_SSE_CONNECTIONS=0 \
  SSE_ROLLBACK_WINDOW_SECONDS=0 \
  SSE_DRAIN_TIMEOUT_SECONDS=0 \
  SSE_DRAIN_RETRY_SECONDS=1 \
  PROJECT_DIR="$fixture_root/main" \
  ENV_FILE="$fixture_root/env/main.env" \
  "$DEPLOY_SCRIPT" "$image"
}

test_switches_inactive_slot_after_health_success() {
  fixture_root="$(create_fixture)"
  run_deploy "$fixture_root" "registry/app-main:new-green" "success"

  assert_contains "$fixture_root/env/main.env" "APP_MAIN_GREEN_IMAGE=registry/app-main:new-green"
  assert_contains "$fixture_root/env/main.env" "APP_MAIN_IMAGE=registry/app-main:new-green"
  assert_contains "$fixture_root/env/main.env" "ACTIVE_APP_SLOT=green"
  assert_contains "$fixture_root/main/.active-slot" "green"
  assert_contains "$fixture_root/main/nginx/conf.d/api.conf" "server_name api.example.com;"
  assert_contains "$fixture_root/main/nginx/conf.d/api.conf" "proxy_pass http://app-main-green:8080;"
  assert_contains "$fixture_root/main/.blue-green-dry-run.log" "compose up -d --no-deps app-main-blue"
  assert_contains "$fixture_root/main/.blue-green-dry-run.log" "compose up -d --no-deps app-main-green"
  assert_contains "$fixture_root/main/.blue-green-dry-run.log" "nginx reload"
  assert_contains "$fixture_root/main/.blue-green-dry-run.log" "post-switch healthcheck app-main-green"
  assert_contains "$fixture_root/main/.blue-green-dry-run.log" "sse drain app-main-blue"
  assert_contains "$fixture_root/main/.blue-green-dry-run.log" "sse wait app-main-blue timeout=0s"
  assert_not_contains "$fixture_root/main/.blue-green-dry-run.log" "compose stop app-main-blue"
}

test_keeps_existing_slot_when_health_fails() {
  fixture_root="$(create_fixture)"
  if run_deploy "$fixture_root" "registry/app-main:bad-green" "fail"; then
    fail "expected failed health check to fail deployment"
  fi

  assert_contains "$fixture_root/env/main.env" "APP_MAIN_GREEN_IMAGE=registry/app-main:bad-green"
  assert_contains "$fixture_root/env/main.env" "APP_MAIN_IMAGE=registry/app-main:old-blue"
  assert_contains "$fixture_root/env/main.env" "ACTIVE_APP_SLOT=blue"
  assert_contains "$fixture_root/main/nginx/conf.d/api.conf" "proxy_pass http://app-main-blue:8080;"
  assert_not_contains "$fixture_root/main/nginx/conf.d/api.conf" "app-main-green:8080"
}

test_rolls_back_when_post_switch_health_fails() {
  fixture_root="$(create_fixture)"
  if run_deploy "$fixture_root" "registry/app-main:bad-after-switch" "success" "fail"; then
    fail "expected failed post-switch health check to fail deployment"
  fi

  assert_contains "$fixture_root/env/main.env" "APP_MAIN_GREEN_IMAGE=registry/app-main:bad-after-switch"
  assert_contains "$fixture_root/env/main.env" "APP_MAIN_IMAGE=registry/app-main:old-blue"
  assert_contains "$fixture_root/env/main.env" "ACTIVE_APP_SLOT=blue"
  assert_contains "$fixture_root/main/nginx/conf.d/api.conf" "proxy_pass http://app-main-blue:8080;"
  assert_not_contains "$fixture_root/main/.blue-green-dry-run.log" "sse drain app-main-blue"
}

test_detects_active_slot_from_nginx_when_env_is_missing() {
  fixture_root="$(create_fixture)"
  sed -i.bak '/^ACTIVE_APP_SLOT=/d' "$fixture_root/env/main.env"
  sed -i.bak 's/app-main-blue/app-main-green/g' "$fixture_root/main/nginx/conf.d/api.conf"

  run_deploy "$fixture_root" "registry/app-main:new-blue" "success"

  assert_contains "$fixture_root/env/main.env" "APP_MAIN_BLUE_IMAGE=registry/app-main:new-blue"
  assert_contains "$fixture_root/env/main.env" "ACTIVE_APP_SLOT=blue"
  assert_contains "$fixture_root/main/nginx/conf.d/api.conf" "proxy_pass http://app-main-blue:8080;"
}

test_rollback_switches_to_previous_slot() {
  fixture_root="$(create_fixture)"
  run_deploy "$fixture_root" "registry/app-main:new-green" "success"

  DRY_RUN=1 \
  DRY_RUN_HEALTH_STATUS=success \
  PROJECT_DIR="$fixture_root/main" \
  ENV_FILE="$fixture_root/env/main.env" \
  "$ROLLBACK_SCRIPT"

  assert_contains "$fixture_root/env/main.env" "ACTIVE_APP_SLOT=blue"
  assert_contains "$fixture_root/env/main.env" "APP_MAIN_IMAGE=registry/app-main:old-blue"
  assert_contains "$fixture_root/main/nginx/conf.d/api.conf" "proxy_pass http://app-main-blue:8080;"
}

test_switches_inactive_slot_after_health_success
test_keeps_existing_slot_when_health_fails
test_rolls_back_when_post_switch_health_fails
test_detects_active_slot_from_nginx_when_env_is_missing
test_rollback_switches_to_previous_slot

echo "blue-green deploy tests passed"
