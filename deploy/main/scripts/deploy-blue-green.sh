#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/blue-green-lib.sh"

new_image="${1:-}"
[ -n "$new_image" ] || die "usage: $0 <new-app-main-image>"

ensure_blue_green_env

active_slot="$(detect_active_slot)"
inactive_slot="$(other_slot "$active_slot")"
active_service="$(slot_service "$active_slot")"
inactive_service="$(slot_service "$inactive_slot")"
inactive_image_key="$(slot_image_key "$inactive_slot")"

log "Active slot: $active_slot"
log "Deploying $new_image to inactive slot: $inactive_slot"

set_env_value "$inactive_image_key" "$new_image"

compose up -d redis
compose up -d --no-deps "$active_service"
compose pull "$inactive_service"
compose up -d --no-deps "$inactive_service"

if ! wait_for_slot_health "$inactive_slot"; then
  die "health check failed for $inactive_service; keeping active slot $active_slot"
fi

switch_nginx_to_slot "$inactive_slot"
sleep_seconds "$SSE_ROLLBACK_WINDOW_SECONDS"

if ! wait_for_slot_health_after_switch "$inactive_slot"; then
  log "Post-switch health check failed for $inactive_service; rolling traffic back to $active_slot"
  switch_nginx_to_slot "$active_slot" || die "post-switch health failed and rollback reload failed"
  die "post-switch health check failed for $inactive_service; rolled back to $active_slot"
fi

drain_slot_sse "$active_slot" || log "SSE drain request failed for $active_service; continuing with graceful shutdown policy"
if ! wait_for_slot_sse_drain "$active_slot"; then
  log "SSE drain timeout for $active_service after ${SSE_DRAIN_TIMEOUT_SECONDS}s"
fi

stop_previous_slot_if_enabled "$active_slot"

log "Switched active slot to $inactive_slot"
