#!/system/bin/sh
MODDIR=${0%/*}

# Persistent directories
PERSIST=/data/adb/danmu_api_server
LOGDIR="$PERSIST/logs"
LOGFILE="$LOGDIR/service.log"

# Optional manual service toggle (Magisk Action button)
# If this flag exists, do not auto-start on boot.
# New flag name (separate "autostart" from manual start/stop)
FLAG_NEW="$PERSIST/autostart.disabled"
FLAG_OLD="$PERSIST/service.disabled"

# Migrate legacy flag (<= v1.x)
if [ -f "$FLAG_OLD" ] && [ ! -f "$FLAG_NEW" ]; then
  mv -f "$FLAG_OLD" "$FLAG_NEW" 2>/dev/null || true
fi

# Prefer persistent control script (survive module disable/update)
CTRL="$PERSIST/bin/danmu_control.sh"
if [ ! -x "$CTRL" ]; then
  CTRL="$MODDIR/scripts/danmu_control.sh"
fi


log() {
  mkdir -p "$LOGDIR" 2>/dev/null || true
  echo "[danmu_api][service] $(date '+%F %T') $*" >> "$LOGFILE" 2>/dev/null || true
}

get_installed_vc() {
  # dumpsys output contains: versionCode=123 minSdk=...
  dumpsys package "$1" 2>/dev/null     | grep -m1 "versionCode="     | sed -E 's/.*versionCode=([0-9]+).*/\1/'     | head -n 1
}

ensure_manager_app() {
  PKG="com.danmuapi.manager"
  APK="$MODDIR/system/app/DanmuApiManager/DanmuApiManager.apk"

  [ -f "$APK" ] || return 0

  # Expected versionCode from module.prop (best-effort)
  EXP_VC="0"
  if [ -f "$MODDIR/module.prop" ]; then
    t="$(grep -m1 '^versionCode=' "$MODDIR/module.prop" 2>/dev/null | cut -d= -f2- | tr -cd '0-9')"
    [ -n "$t" ] && EXP_VC="$t"
  fi

  INS_VC="$(get_installed_vc "$PKG")"
  [ -n "$INS_VC" ] || INS_VC="0"

  # Only update when needed
  if [ "$INS_VC" -ge "$EXP_VC" ] && [ "$INS_VC" -ne 0 ]; then
    return 0
  fi

  log "manager app update: installed_vc=$INS_VC expected_vc=$EXP_VC"

  pm install -r "$APK" >/dev/null 2>&1 && { log "pm install -r success"; return 0; }

  # Some ROMs need install-existing first (system app not yet installed for user)
  cmd package install-existing "$PKG" >/dev/null 2>&1     || pm install-existing "$PKG" >/dev/null 2>&1     || true

  pm install -r "$APK" >/dev/null 2>&1 && { log "pm install -r success after install-existing"; return 0; }

  log "pm install failed"
  return 0
}

# Wait for boot completion (PackageManager ready)
while [ "$(getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
  sleep 5
done
sleep 5

# Ensure companion Manager App gets updated even if autostart is disabled
ensure_manager_app

# Respect autostart flag for the core service
if [ -f "$FLAG_NEW" ] || [ -f "$FLAG_OLD" ]; then
  exit 0
fi

# Magisk entrypoint: delegate to control script.
"$CTRL" start
