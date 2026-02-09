#!/system/bin/sh
# inotifyd handler: keep service state in sync with Magisk module enable/disable.
# This is event-driven (no polling): only runs when files in the module dir change.

set -u

MODID="danmu_api_server"
MODULE_DIR="/data/adb/modules/$MODID"
CTRL="$MODULE_DIR/scripts/danmu_control.sh"

PERSIST="/data/adb/danmu_api_server"
PIDFILE="$PERSIST/danmu_api.pid"
# Autostart flag (new) + legacy flag (<= v1.x)
FLAG_NEW="$PERSIST/autostart.disabled"
FLAG_OLD="$PERSIST/service.disabled"

mkdir -p "$PERSIST" 2>/dev/null

svc_running() {
  if [ -f "$PIDFILE" ]; then
    pid="$(cat "$PIDFILE" 2>/dev/null || true)"
    if [ -n "${pid:-}" ] && kill -0 "$pid" 2>/dev/null; then
      return 0
    fi
  fi
  return 1
}

module_enabled() {
  [ -d "$MODULE_DIR" ] && [ ! -f "$MODULE_DIR/disable" ]
}

should_run() {
  # Module must be enabled AND autostart must not be disabled.
  module_enabled && [ ! -f "$FLAG_NEW" ] && [ ! -f "$FLAG_OLD" ]
}

apply() {
  # If module directory is gone, stop and return.
  if [ ! -d "$MODULE_DIR" ]; then
    if [ -x "$CTRL" ]; then "$CTRL" stop >/dev/null 2>&1; fi
    return 0
  fi

  if should_run; then
    if ! svc_running && [ -x "$CTRL" ]; then
      "$CTRL" start >/dev/null 2>&1
    fi
  else
    if svc_running && [ -x "$CTRL" ]; then
      "$CTRL" stop >/dev/null 2>&1
    fi
  fi
}

# args from inotifyd: $1=events, $2=watched path, $3=filename
# We don't try to parse specific events; we just re-apply desired state.
apply
