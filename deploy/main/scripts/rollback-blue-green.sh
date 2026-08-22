#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/blue-green-lib.sh"

ensure_blue_green_env

active_slot="$(detect_active_slot)"
target_slot="${1:-$(other_slot "$active_slot")}"

is_slot "$target_slot" || die "rollback slot must be blue or green"

target_service="$(slot_service "$target_slot")"
log "Rolling back from $active_slot to $target_slot"

compose up -d "$target_service"

if ! wait_for_slot_health "$target_slot"; then
  die "health check failed for rollback target $target_service"
fi

switch_nginx_to_slot "$target_slot"

log "Rolled back active slot to $target_slot"
