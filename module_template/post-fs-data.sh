#!/system/bin/sh
MODDIR=${0%/*}

# Persistent runtime data (config/logs/runtime)
PERSIST=/data/adb/danmu_api_server
CFG_DIR="$PERSIST/config"
LOGDIR="$PERSIST/logs"
BIN_DIR="$PERSIST/bin"

mkdir -p "$PERSIST" "$CFG_DIR" "$LOGDIR" "$BIN_DIR" 2>/dev/null

# ------------------------------------------------------------
# Persist scripts + busybox so the Manager App can keep working
# across module disable/update (no need to hardcode module path)
# ------------------------------------------------------------
if [ -d "$MODDIR/scripts" ]; then
  cp -f "$MODDIR/scripts/danmu_core.sh" "$BIN_DIR/danmu_core.sh" 2>/dev/null
  cp -f "$MODDIR/scripts/danmu_control.sh" "$BIN_DIR/danmu_control.sh" 2>/dev/null
  chmod 0755 "$BIN_DIR/danmu_core.sh" "$BIN_DIR/danmu_control.sh" 2>/dev/null
fi

if [ -x "$MODDIR/bin/busybox" ]; then
  cp -f "$MODDIR/bin/busybox" "$BIN_DIR/busybox" 2>/dev/null
  chmod 0755 "$BIN_DIR/busybox" 2>/dev/null
fi

# Ensure module config path points to persistent config (single source of truth)
# Web UI / env-api writes to: $MODDIR/app/config
# We keep it as a symlink to: $CFG_DIR
MOD_CFG="$MODDIR/app/config"
umount -l "$MOD_CFG" 2>/dev/null || true
rm -rf "$MOD_CFG" 2>/dev/null
ln -s "$CFG_DIR" "$MOD_CFG" 2>/dev/null

# Ensure danmu_api core path points to persistent core symlink
CORE_LINK="$PERSIST/core"
MOD_CORE="$MODDIR/app/danmu_api"
rm -rf "$MOD_CORE" 2>/dev/null
ln -s "$CORE_LINK" "$MOD_CORE" 2>/dev/null

# Seed a default core (first install) if needed
if [ -x "$BIN_DIR/danmu_core.sh" ]; then
  "$BIN_DIR/danmu_core.sh" ensure >/dev/null 2>&1 || true
elif [ -x "$MODDIR/scripts/danmu_core.sh" ]; then
  "$MODDIR/scripts/danmu_core.sh" ensure >/dev/null 2>&1 || true
fi

# Protect secrets
chmod 755 "$PERSIST" "$CFG_DIR" "$LOGDIR" 2>/dev/null
chmod 600 "$CFG_DIR/.env" 2>/dev/null
rm -f "$CFG_DIR/config.yaml" 2>/dev/null

exit 0
