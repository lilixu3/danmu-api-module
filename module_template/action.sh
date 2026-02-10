#!/system/bin/sh
# Magisk Action button: toggle danmu_api_server service without any background polling.
# - If service is running: stop it and set a persistent flag to prevent auto-start.
# - If service is stopped: clear the flag and start it.

MODID="danmu_api_server"
MODULE_DIR="/data/adb/modules/$MODID"
CTRL="$MODULE_DIR/scripts/danmu_control.sh"

PERSIST="/data/adb/danmu_api_server"
FLAG_NEW="$PERSIST/autostart.disabled"
FLAG_OLD="$PERSIST/service.disabled"
PIDFILE="$PERSIST/danmu_api.pid"

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

if [ ! -x "$CTRL" ]; then
  echo "[danmu_api] control script not found: $CTRL"
  exit 1
fi

# If the module is disabled in Magisk, don't try to start it.
if [ -f "$MODULE_DIR/disable" ]; then
  echo "[danmu_api] 模块当前已禁用：无法启动服务。请先在 Magisk 里启用模块。"
  # Still allow stop if it is running
  if svc_running; then
    "$CTRL" stop >/dev/null 2>&1
    echo "[danmu_api] 已停止服务。"
  fi
  exit 0
fi

# Migrate legacy flag (<= v1.x)
if [ -f "$FLAG_OLD" ] && [ ! -f "$FLAG_NEW" ]; then
  mv -f "$FLAG_OLD" "$FLAG_NEW" 2>/dev/null || true
fi

if svc_running; then
  # Turn OFF
  : > "$FLAG_NEW" 2>/dev/null || touch "$FLAG_NEW" 2>/dev/null
  rm -f "$FLAG_OLD" 2>/dev/null || true
  "$CTRL" stop >/dev/null 2>&1
  echo "[danmu_api] 已停止服务（已设置为不自启动）。"
else
  # Turn ON
  rm -f "$FLAG_NEW" "$FLAG_OLD" 2>/dev/null || true
  "$CTRL" start >/dev/null 2>&1
  echo "[danmu_api] 已启动服务。"
fi
