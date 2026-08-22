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
stop_previous_slot_if_enabled "$active_slot"

log "Switched active slot to $inactive_slot"
