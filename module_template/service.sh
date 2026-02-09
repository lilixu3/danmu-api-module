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

wait_for_pm() {
  # 等待包管理器就绪
  i=0
  while [ "$i" -lt 30 ]; do
    pm list packages >/dev/null 2>&1 && return 0
    i=$((i+1))
    sleep 2
  done
  return 1
}

ensure_pkg_enabled() {
  PKG="$1"
  # 确保用户0已启用
  pm enable --user 0 "$PKG" >/dev/null 2>&1 || true
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

  wait_for_pm || true

  # Only update when needed
  if [ "$INS_VC" -ge "$EXP_VC" ] && [ "$INS_VC" -ne 0 ]; then
    ensure_pkg_enabled "$PKG"
    return 0
  fi

  log "manager app update: installed_vc=$INS_VC expected_vc=$EXP_VC"

  pm install -r --user 0 "$APK" >/dev/null 2>&1 && { log "pm install -r success"; ensure_pkg_enabled "$PKG"; return 0; }
  pm install -r "$APK" >/dev/null 2>&1 && { log "pm install -r success (no user flag)"; ensure_pkg_enabled "$PKG"; return 0; }

  # Some ROMs need install-existing first (system app not yet installed for user)
  cmd package install-existing --user 0 "$PKG" >/dev/null 2>&1     || cmd package install-existing "$PKG" >/dev/null 2>&1     || pm install-existing --user 0 "$PKG" >/dev/null 2>&1     || pm install-existing "$PKG" >/dev/null 2>&1     || true

  pm install -r --user 0 "$APK" >/dev/null 2>&1 && { log "pm install -r success after install-existing"; ensure_pkg_enabled "$PKG"; return 0; }
  pm install -r "$APK" >/dev/null 2>&1 && { log "pm install -r success after install-existing (no user flag)"; ensure_pkg_enabled "$PKG"; return 0; }

  ensure_pkg_enabled "$PKG"

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

# Ensure core is downloaded after boot (best-effort)
CORECTL="$PERSIST/bin/danmu_core.sh"
if [ ! -x "$CORECTL" ]; then
  CORECTL="$MODDIR/scripts/danmu_core.sh"
fi
if [ -x "$CORECTL" ]; then
  "$CORECTL" ensure >/dev/null 2>&1 || true
fi

# Respect autostart flag for the core service
if [ -f "$FLAG_NEW" ] || [ -f "$FLAG_OLD" ]; then
  exit 0
fi

# Magisk entrypoint: delegate to control script.
"$CTRL" start
