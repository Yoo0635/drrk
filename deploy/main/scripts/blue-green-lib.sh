#!/bin/sh

: "${PROJECT_DIR:=/opt/drrk/main}"
: "${ENV_FILE:=/opt/drrk/env/main.env}"
: "${COMPOSE_FILE:=${PROJECT_DIR}/compose.yml}"
: "${NGINX_TEMPLATE:=${PROJECT_DIR}/nginx/templates/api.conf.template}"
: "${NGINX_CONF:=${PROJECT_DIR}/nginx/conf.d/api.conf}"
: "${STATE_FILE:=${PROJECT_DIR}/.active-slot}"
: "${NGINX_CONTAINER:=drrk-main-nginx}"
: "${ACTUATOR_HEALTH_URL:=http://localhost:8080/actuator/health/readiness}"
: "${HEALTH_RETRIES:=30}"
: "${HEALTH_INTERVAL_SECONDS:=2}"
: "${STOP_PREVIOUS_AFTER_SWITCH:=}"
: "${SSE_ROLLBACK_WINDOW_SECONDS:=}"
: "${SSE_DRAIN_TIMEOUT_SECONDS:=}"
: "${SSE_DRAIN_RETRY_SECONDS:=}"
: "${BLUE_GREEN_DRAIN_SECONDS:=}"
: "${APP_MAIN_STOP_TIMEOUT_SECONDS:=}"
: "${DRY_RUN:=0}"

log() {
  printf '%s\n' "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

dry_log() {
  mkdir -p "$PROJECT_DIR"
  printf '%s\n' "$*" >> "$PROJECT_DIR/.blue-green-dry-run.log"
}

read_env_value() {
  key="$1"
  [ -f "$ENV_FILE" ] || return 0
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$ENV_FILE"
}

set_env_value() {
  key="$1"
  value="$2"
  mkdir -p "$(dirname "$ENV_FILE")"
  [ -f "$ENV_FILE" ] || : > "$ENV_FILE"
  tmp="${ENV_FILE}.tmp.$$"
  awk -v key="$key" -v value="$value" '
    BEGIN { found = 0 }
    $0 ~ "^[[:space:]]*#" { print; next }
    index($0, key "=") == 1 {
      print key "=" value
      found = 1
      next
    }
    { print }
    END {
      if (!found) {
        print key "=" value
      }
    }
  ' "$ENV_FILE" > "$tmp"
  mv "$tmp" "$ENV_FILE"
}

load_runtime_option() {
  key="$1"
  default_value="$2"
  current_value="$(eval "printf '%s' \"\${$key:-}\"")"
  [ -z "$current_value" ] || return 0

  file_value="$(read_env_value "$key")"
  if [ -n "$file_value" ]; then
    export "$key=$file_value"
    return 0
  fi

  export "$key=$default_value"
}

load_deploy_runtime_options() {
  load_runtime_option STOP_PREVIOUS_AFTER_SWITCH false
  load_runtime_option SSE_ROLLBACK_WINDOW_SECONDS 60
  load_runtime_option SSE_DRAIN_TIMEOUT_SECONDS 30
  load_runtime_option SSE_DRAIN_RETRY_SECONDS 1
  load_runtime_option BLUE_GREEN_DRAIN_SECONDS 0
  load_runtime_option APP_MAIN_STOP_TIMEOUT_SECONDS 70
}

is_slot() {
  [ "$1" = "blue" ] || [ "$1" = "green" ]
}

other_slot() {
  case "$1" in
    blue) printf '%s\n' green ;;
    green) printf '%s\n' blue ;;
    *) die "unknown slot: $1" ;;
  esac
}

slot_service() {
  case "$1" in
    blue) printf '%s\n' app-main-blue ;;
    green) printf '%s\n' app-main-green ;;
    *) die "unknown slot: $1" ;;
  esac
}

slot_image_key() {
  case "$1" in
    blue) printf '%s\n' APP_MAIN_BLUE_IMAGE ;;
    green) printf '%s\n' APP_MAIN_GREEN_IMAGE ;;
    *) die "unknown slot: $1" ;;
  esac
}

slot_upstream() {
  printf '%s:8080\n' "$(slot_service "$1")"
}

ensure_blue_green_env() {
  [ -f "$ENV_FILE" ] || die "env file not found: $ENV_FILE"
  load_deploy_runtime_options
  legacy_image="$(read_env_value APP_MAIN_IMAGE)"
  blue_image="$(read_env_value APP_MAIN_BLUE_IMAGE)"
  green_image="$(read_env_value APP_MAIN_GREEN_IMAGE)"

  if [ -z "$blue_image" ]; then
    [ -n "$legacy_image" ] || die "APP_MAIN_BLUE_IMAGE or APP_MAIN_IMAGE is required"
    set_env_value APP_MAIN_BLUE_IMAGE "$legacy_image"
  fi

  if [ -z "$green_image" ]; then
    [ -n "$legacy_image" ] || die "APP_MAIN_GREEN_IMAGE or APP_MAIN_IMAGE is required"
    set_env_value APP_MAIN_GREEN_IMAGE "$legacy_image"
  fi

  active_slot="$(read_env_value ACTIVE_APP_SLOT)"
  if ! is_slot "$active_slot"; then
    active_slot="$(detect_active_slot_from_files)"
    set_env_value ACTIVE_APP_SLOT "$active_slot"
  fi
}

detect_active_slot_from_files() {
  if [ -f "$STATE_FILE" ]; then
    state_slot="$(tr -d '[:space:]' < "$STATE_FILE")"
    if is_slot "$state_slot"; then
      printf '%s\n' "$state_slot"
      return 0
    fi
  fi

  if [ -f "$NGINX_CONF" ]; then
    if grep -Fq "app-main-green:8080" "$NGINX_CONF"; then
      printf '%s\n' green
      return 0
    fi
    if grep -Fq "app-main-blue:8080" "$NGINX_CONF"; then
      printf '%s\n' blue
      return 0
    fi
  fi

  printf '%s\n' blue
}

detect_active_slot() {
  active_slot="$(read_env_value ACTIVE_APP_SLOT)"
  if is_slot "$active_slot"; then
    printf '%s\n' "$active_slot"
    return 0
  fi
  detect_active_slot_from_files
}

compose() {
  if [ "$DRY_RUN" = "1" ]; then
    dry_log "compose $*"
    return 0
  fi
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

docker_exec() {
  if [ "$DRY_RUN" = "1" ]; then
    dry_log "docker exec $*"
    return 0
  fi
  docker exec "$@"
}

copy_nginx_config_into_container() {
  if [ "$DRY_RUN" = "1" ]; then
    dry_log "nginx config copy"
    return 0
  fi
  docker cp "$NGINX_CONF" "$NGINX_CONTAINER:/etc/nginx/conf.d/api.conf"
}

wait_for_slot_health() {
  slot="$1"
  service="$(slot_service "$slot")"

  if [ "$DRY_RUN" = "1" ]; then
    dry_log "healthcheck $service $ACTUATOR_HEALTH_URL"
    [ "${DRY_RUN_HEALTH_STATUS:-success}" = "success" ]
    return $?
  fi

  attempt=1
  while [ "$attempt" -le "$HEALTH_RETRIES" ]; do
    if compose exec -T "$service" curl -fsS "$ACTUATOR_HEALTH_URL" >/dev/null; then
      return 0
    fi
    sleep "$HEALTH_INTERVAL_SECONDS"
    attempt=$((attempt + 1))
  done

  return 1
}

wait_for_slot_health_after_switch() {
  slot="$1"
  if [ "$DRY_RUN" = "1" ]; then
    dry_log "post-switch healthcheck $(slot_service "$slot") $ACTUATOR_HEALTH_URL"
    [ "${DRY_RUN_POST_SWITCH_HEALTH_STATUS:-${DRY_RUN_HEALTH_STATUS:-success}}" = "success" ]
    return $?
  fi
  wait_for_slot_health "$slot"
}

sleep_seconds() {
  seconds="$1"
  [ "$seconds" -gt 0 ] || return 0
  if [ "$DRY_RUN" = "1" ]; then
    dry_log "sleep $seconds"
    return 0
  fi
  sleep "$seconds"
}

render_nginx_config() {
  slot="$1"
  root_domain="$(read_env_value ROOT_DOMAIN)"
  [ -n "$root_domain" ] || die "ROOT_DOMAIN is required in $ENV_FILE"
  upstream="$(slot_upstream "$slot")"
  mkdir -p "$(dirname "$NGINX_CONF")"
  tmp="${NGINX_CONF}.tmp.$$"
  awk -v root_domain="$root_domain" -v active_upstream="$upstream" '
    {
      gsub(/\$\{ROOT_DOMAIN\}/, root_domain)
      gsub(/\$\{ACTIVE_APP_UPSTREAM\}/, active_upstream)
      print
    }
  ' "$NGINX_TEMPLATE" > "$tmp"
  mv "$tmp" "$NGINX_CONF"
}

reload_nginx() {
  if [ "$DRY_RUN" = "1" ]; then
    dry_log "nginx reload"
    return 0
  fi
  if ! docker exec "$NGINX_CONTAINER" true >/dev/null 2>&1; then
    compose up -d nginx
  fi
  copy_nginx_config_into_container
  docker_exec "$NGINX_CONTAINER" nginx -t
  docker_exec "$NGINX_CONTAINER" nginx -s reload
}

switch_nginx_to_slot() {
  slot="$1"
  image_key="$(slot_image_key "$slot")"
  image="$(read_env_value "$image_key")"
  [ -n "$image" ] || die "$image_key is required"

  backup=""
  if [ -f "$NGINX_CONF" ]; then
    backup="${NGINX_CONF}.bak.$$"
    cp "$NGINX_CONF" "$backup"
  fi

  render_nginx_config "$slot"
  if ! reload_nginx; then
    if [ -n "$backup" ] && [ -f "$backup" ]; then
      mv "$backup" "$NGINX_CONF"
      copy_nginx_config_into_container >/dev/null 2>&1 || true
    fi
    return 1
  fi

  [ -z "$backup" ] || rm -f "$backup"
  set_env_value ACTIVE_APP_SLOT "$slot"
  set_env_value APP_MAIN_IMAGE "$image"
  printf '%s\n' "$slot" > "$STATE_FILE"
}

drain_slot_sse() {
  slot="$1"
  service="$(slot_service "$slot")"
  url="http://localhost:8080/internal/sse/drain"

  if [ "$DRY_RUN" = "1" ]; then
    dry_log "sse drain $service $url retry=${SSE_DRAIN_RETRY_SECONDS}s"
    return 0
  fi

  compose exec -T "$service" curl -fsS -X POST "$url" >/dev/null
}

slot_sse_connection_count() {
  slot="$1"
  service="$(slot_service "$slot")"
  url="http://localhost:8080/internal/sse/connections"

  if [ "$DRY_RUN" = "1" ]; then
    printf '%s\n' "${DRY_RUN_SSE_CONNECTIONS:-0}"
    return 0
  fi

  compose exec -T "$service" curl -fsS "$url" 2>/dev/null | tr -d '[:space:]'
}

wait_for_slot_sse_drain() {
  slot="$1"
  service="$(slot_service "$slot")"
  elapsed=0

  if [ "$DRY_RUN" = "1" ]; then
    dry_log "sse wait $service timeout=${SSE_DRAIN_TIMEOUT_SECONDS}s"
  fi

  while [ "$elapsed" -le "$SSE_DRAIN_TIMEOUT_SECONDS" ]; do
    count="$(slot_sse_connection_count "$slot" || printf '%s\n' unknown)"
    if [ "$count" = "0" ]; then
      return 0
    fi
    sleep_seconds "$SSE_DRAIN_RETRY_SECONDS"
    elapsed=$((elapsed + SSE_DRAIN_RETRY_SECONDS))
  done

  return 1
}

stop_previous_slot_if_enabled() {
  previous_slot="$1"
  previous_service="$(slot_service "$previous_slot")"

  [ "$STOP_PREVIOUS_AFTER_SWITCH" = "true" ] || return 0

  if [ "$BLUE_GREEN_DRAIN_SECONDS" -gt 0 ]; then
    if [ "$DRY_RUN" = "1" ]; then
      dry_log "sleep $BLUE_GREEN_DRAIN_SECONDS"
    else
      sleep "$BLUE_GREEN_DRAIN_SECONDS"
    fi
  fi

  compose stop -t "$APP_MAIN_STOP_TIMEOUT_SECONDS" "$previous_service"
}
