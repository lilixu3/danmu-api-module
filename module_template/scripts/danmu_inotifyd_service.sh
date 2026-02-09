#!/system/bin/sh
# Installed into /data/adb/service.d
# Runs inotifyd to react to Magisk module enable/disable WITHOUT polling.

set -u

MODID="danmu_api_server"
MODULE_DIR="/data/adb/modules/$MODID"
PERSIST="/data/adb/danmu_api_server"
LOGDIR="$PERSIST/logs"
LOGFILE="$LOGDIR/inotifyd.log"
PIDFILE="$PERSIST/inotifyd.pid"
HANDLER="$MODULE_DIR/scripts/danmu_inotify_handler.sh"

mkdir -p "$PERSIST" "$LOGDIR" 2>/dev/null

log() {
  echo "[danmu_api][inotifyd] $(date '+%F %T') $*" >> "$LOGFILE" 2>/dev/null
}

# Single-instance guard
if [ -f "$PIDFILE" ]; then
  oldpid="$(cat "$PIDFILE" 2>/dev/null || true)"
  if [ -n "${oldpid:-}" ] && kill -0 "$oldpid" 2>/dev/null; then
    exit 0
  fi
fi

# service.d may run early on some devices
while [ "$(getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
  sleep 5
done
sleep 2

# Ensure module dir exists (Magisk may create it slightly later)
if [ ! -d "$MODULE_DIR" ]; then
  for _ in 1 2 3 4 5; do
    sleep 2
    [ -d "$MODULE_DIR" ] && break
  done
fi

if [ ! -d "$MODULE_DIR" ]; then
  log "module dir not found: $MODULE_DIR; exiting"
  exit 0
fi

# Prefer module-bundled tools if present (busybox inotifyd / wget / unzip)
if [ -d "$MODULE_DIR/bin" ]; then
  export PATH="$MODULE_DIR/bin:$PATH"
  export LD_LIBRARY_PATH="$MODULE_DIR/bin/lib:${LD_LIBRARY_PATH:-}"
fi
# Some Termux binaries may rely on libs shipped with the module
if [ -d "$MODULE_DIR/node/lib" ]; then
  export LD_LIBRARY_PATH="$MODULE_DIR/node/lib:${LD_LIBRARY_PATH:-}"
fi

# Find inotifyd implementation (busybox / toybox / standalone)
INOTIFYD="$(command -v inotifyd 2>/dev/null || true)"
if [ -z "$INOTIFYD" ]; then
  if command -v toybox >/dev/null 2>&1; then
    if toybox inotifyd --help >/dev/null 2>&1; then
      INOTIFYD="toybox inotifyd"
    fi
  fi
fi
if [ -z "$INOTIFYD" ]; then
  if command -v busybox >/dev/null 2>&1; then
    if busybox inotifyd --help >/dev/null 2>&1; then
      INOTIFYD="busybox inotifyd"
    fi
  fi
fi

if [ -z "$INOTIFYD" ]; then
  log "inotifyd not found; module toggle won't be instant. (Action button still works.)"
  exit 0
fi

if [ ! -x "$HANDLER" ]; then
  log "handler not executable: $HANDLER; exiting"
  exit 0
fi

echo $$ > "$PIDFILE" 2>/dev/null

# Apply desired state once at startup
"$HANDLER" init "$MODULE_DIR" "" >/dev/null 2>&1 || true

log "starting $INOTIFYD watcher on $MODULE_DIR"
# Watch for create/delete/move events in module dir.
# BusyBox inotifyd masks: n=create, d=delete, m=move, y=move-self
# (We don't parse per-event; handler re-applies state.)
exec $INOTIFYD "$HANDLER" "$MODULE_DIR":ndmy
