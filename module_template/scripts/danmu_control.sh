#!/system/bin/sh
# danmu_api_server control script: start/stop/restart/status
# Works for both bundled Node runtime and external Node installs.
#
# Battery/storage notes:
# - Server stdout/stderr is written to logs/server.log (rotated)
# - This script itself logs to logs/control.log
set -u

# Module id
MODID="danmu_api_server"

# Resolve module directory (this script may be executed from:
# - /data/adb/modules/$MODID/scripts (module)
# - /data/adb/danmu_api_server/bin     (persistent copy for the Manager App)
# Prefer the real Magisk module directory when possible.
if [ -d "/data/adb/modules/${MODID}" ]; then
  MODDIR="/data/adb/modules/${MODID}"
elif [ -d "/data/adb/modules_update/${MODID}" ]; then
  MODDIR="/data/adb/modules_update/${MODID}"
else
  MODDIR="$(cd "${0%/*}/.." 2>/dev/null && pwd)"
fi

PERSIST="/data/adb/danmu_api_server"
LOGDIR="$PERSIST/logs"
CTRL_LOG="$LOGDIR/control.log"
SERVER_LOG="$LOGDIR/server.log"
PIDFILE="$PERSIST/danmu_api.pid"

NODE_HOME="$MODDIR/node"
MODULE_NODE="$NODE_HOME/bin/node"
APP_ENTRY="$MODDIR/app/android-server.mjs"

mkdir -p "$PERSIST" "$LOGDIR" 2>/dev/null

log() {
  # Avoid failing if log dir not writable for any reason
  echo "[danmu_api][control] $(date '+%F %T') $*" >> "$CTRL_LOG" 2>/dev/null
}

rotate_log_if_needed() {
  # args: file max_bytes keep
  f="$1"; maxb="$2"; keep="$3"
  [ -f "$f" ] || return 0

  sz="$(wc -c < "$f" 2>/dev/null || echo 0)"
  case "$sz" in
    ''|*[!0-9]*) sz=0 ;;
  esac
  [ "$sz" -gt "$maxb" ] || return 0

  # rotate: f.(keep-1) -> f.keep, ... , f -> f.1
  i="$keep"
  while [ "$i" -ge 2 ]; do
    prev=$((i-1))
    [ -f "$f.$prev" ] && mv -f "$f.$prev" "$f.$i" 2>/dev/null || true
    i=$((i-1))
  done
  mv -f "$f" "$f.1" 2>/dev/null || true
}

ensure_bundled_libcxx() {
  # For bundled Node: ensure libc++_shared.so exists in $NODE_HOME/lib.
  [ -x "$MODULE_NODE" ] || return 0
  [ -e "$NODE_HOME/lib/libc++_shared.so" ] && return 0

  for p in \
    /system/lib64/libc++_shared.so \
    /system/lib/libc++_shared.so \
    /apex/*/lib64*/libc++_shared.so \
    /apex/*/lib*/libc++_shared.so
  do
    if [ -f "$p" ]; then
      cp -f "$p" "$NODE_HOME/lib/libc++_shared.so" 2>/dev/null \
        || ln -s "$p" "$NODE_HOME/lib/libc++_shared.so" 2>/dev/null
      break
    fi
  done
}

pick_node_bin() {
  NODE_BIN=""

  # 0) bundled runtime
  if [ -x "$MODULE_NODE" ]; then
    NODE_BIN="$MODULE_NODE"
  fi

  # 1) user override
  if [ -n "${DANMU_API_NODE:-}" ] && [ -x "${DANMU_API_NODE:-}" ]; then
    NODE_BIN="$DANMU_API_NODE"
  fi

  # 2) common locations
  if [ -z "$NODE_BIN" ]; then
    for p in \
      /data/data/com.termux/files/usr/bin/node \
      /data/data/com.termux/files/usr/bin/nodejs \
      /data/adb/modules/nodejs/system/bin/node \
      /system/bin/node \
      /system/xbin/node \
      /vendor/bin/node
    do
      [ -x "$p" ] && NODE_BIN="$p" && break
    done
  fi

  # 3) PATH
  if [ -z "$NODE_BIN" ]; then
    NODE_BIN="$(command -v node 2>/dev/null || true)"
    [ -x "${NODE_BIN:-}" ] || NODE_BIN=""
  fi

  echo "$NODE_BIN"
}

is_running() {
  if [ -f "$PIDFILE" ]; then
    PID="$(cat "$PIDFILE" 2>/dev/null || true)"
    if [ -n "${PID:-}" ] && kill -0 "$PID" 2>/dev/null; then
      return 0
    fi
  fi
  return 1
}

stop_proc_tree() {
  # Best-effort child termination: kill children first if possible, then parent.
  PID="$1"
  [ -n "${PID:-}" ] || return 0

  # Try to kill children (if pgrep available)
  if command -v pgrep >/dev/null 2>&1; then
    for c in $(pgrep -P "$PID" 2>/dev/null); do
      stop_proc_tree "$c"
    done
  fi

  kill "$PID" 2>/dev/null || true
}

do_start() {
  if is_running; then
    PID="$(cat "$PIDFILE" 2>/dev/null || true)"
    log "already running (pid=$PID)"
    return 0
  fi

  # Respect Magisk module enable/disable + user toggle flag
  if [ -f "$MODDIR/disable" ]; then
    log "module disabled (disable file present); refusing to start"
    return 1
  fi

  NODE_BIN="$(pick_node_bin)"
  if [ -z "$NODE_BIN" ]; then
    log "node not found; aborting"
    return 1
  fi

  # If using bundled runtime, ensure libc++ and search paths are set.
  if [ "$NODE_BIN" = "$MODULE_NODE" ]; then
    ensure_bundled_libcxx
    if [ ! -e "$NODE_HOME/lib/libc++_shared.so" ]; then
      log "bundled node selected but libc++_shared.so is missing; aborting"
      return 1
    fi
    export PATH="$NODE_HOME/bin:$PATH"
    export LD_LIBRARY_PATH="$NODE_HOME/lib:${LD_LIBRARY_PATH:-}"
  fi

  if [ ! -f "$APP_ENTRY" ]; then
    log "app entry not found: $APP_ENTRY"
    return 1
  fi

  cd "$MODDIR/app" 2>/dev/null || return 1
  export DANMU_API_HOME="$PERSIST"
  # Single source of truth: /data/adb/danmu_api_server/config/.env
  CFG_DIR="$PERSIST/config"

  mkdir -p "$CFG_DIR" 2>/dev/null

  # Ensure Web UI path (app/config) is a symlink to persistent config
  MOD_CFG="$MODDIR/app/config"
  umount -l "$MOD_CFG" 2>/dev/null || true
  rm -rf "$MOD_CFG" 2>/dev/null
  ln -s "$CFG_DIR" "$MOD_CFG" 2>/dev/null

  # Seed .env once (do not overwrite user config)
  if [ ! -f "$CFG_DIR/.env" ] && [ -f "$MODDIR/defaults/config/.env.example" ]; then
    cp -f "$MODDIR/defaults/config/.env.example" "$CFG_DIR/.env" 2>/dev/null
  fi

  # Keep ONLY one config file
  rm -f "$CFG_DIR/config.yaml" 2>/dev/null

  chmod 600 "$CFG_DIR/.env" 2>/dev/null

  export DANMU_API_CONFIG_DIR="$CFG_DIR"
  export DANMU_API_LOG_DIR="$LOGDIR"

  export NODE_ENV=production

  # Keep logs from growing forever (storage + battery impact)
  rotate_log_if_needed "$SERVER_LOG" 2097152 3

  log "starting (node=$NODE_BIN, entry=$APP_ENTRY)"
  nohup "$NODE_BIN" "$APP_ENTRY" >> "$SERVER_LOG" 2>&1 &
  echo $! > "$PIDFILE"
  return 0
}

do_stop() {
  if ! is_running; then
    rm -f "$PIDFILE" 2>/dev/null || true
    log "already stopped"
    return 0
  fi

  PID="$(cat "$PIDFILE" 2>/dev/null || true)"
  log "stopping pid=$PID"
  stop_proc_tree "$PID"
  # Give it a moment
  sleep 1

  if kill -0 "$PID" 2>/dev/null; then
    log "still running; sending SIGKILL pid=$PID"
    kill -9 "$PID" 2>/dev/null || true
  fi

  rm -f "$PIDFILE" 2>/dev/null || true
  return 0
}

do_status() {
  if is_running; then
    PID="$(cat "$PIDFILE" 2>/dev/null || true)"
    echo "running (pid=$PID)"
    return 0
  fi
  echo "stopped"
  return 3
}

case "${1:-}" in
  start) do_start ;;
  stop) do_stop ;;
  restart) do_stop; do_start ;;
  status) do_status ;;
  *) echo "Usage: $0 {start|stop|restart|status}" ; exit 2 ;;
esac
