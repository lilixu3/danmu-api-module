#!/system/bin/sh
MODID="danmu_api_server"
MODULE_DIR="/data/adb/modules/$MODID"
PERSIST="/data/adb/danmu_api_server"

CTRL="$MODULE_DIR/scripts/danmu_control.sh"

# Stop service
if [ -x "$CTRL" ]; then
  "$CTRL" stop >/dev/null 2>&1
fi

# Remove service.d helpers (old polling watchdog + new inotifyd watcher)
rm -f /data/adb/service.d/danmu_api_server-watchdog.sh 2>/dev/null
rm -f /data/adb/service.d/danmu_api_server-inotifyd.sh 2>/dev/null

# Cleanup pid files (logs/config kept)
rm -f "$PERSIST/watchdog.pid" 2>/dev/null
rm -f "$PERSIST/inotifyd.pid" 2>/dev/null

LOGFILE="$PERSIST/logs/service.log"
echo "[danmu_api] Module removed. Persistent data kept in $PERSIST (delete manually if you want)." >> "$LOGFILE" 2>/dev/null
