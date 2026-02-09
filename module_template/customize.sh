#!/system/bin/sh
# This script is sourced by Magisk's module installer.

set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

# Magisk Action button
if [ -f "$MODPATH/action.sh" ]; then
  set_perm "$MODPATH/action.sh" 0 0 0755
fi

# Control + helper scripts
if [ -d "$MODPATH/scripts" ]; then
  set_perm_recursive "$MODPATH/scripts" 0 0 0755 0755
fi

# Node binary must be executable
if [ -f "$MODPATH/node/bin/node" ]; then
  set_perm "$MODPATH/node/bin/node" 0 0 0755
fi

# Module-bundled BusyBox (for event-driven toggle + core switching helpers)
if [ -f "$MODPATH/bin/busybox" ]; then
  set_perm "$MODPATH/bin/busybox" 0 0 0755
fi
if [ -d "$MODPATH/bin/lib" ]; then
  set_perm_recursive "$MODPATH/bin/lib" 0 0 0755 0644
fi

# Persistent runtime/data directory
MODID="danmu_api_server"
PERSIST="/data/adb/danmu_api_server"
CFG_DIR="$PERSIST/config"
LOGDIR="$PERSIST/logs"
BIN_DIR="$PERSIST/bin"

# Older versions stored config inside the module folder (and sometimes bind-mounted).
# We'll migrate once and then use ONLY $CFG_DIR as the single source of truth.
OLD_MOD_CFG="/data/adb/modules/$MODID/app/config"

# Ensure directories exist
mkdir -p "$PERSIST" "$CFG_DIR" "$LOGDIR" "$BIN_DIR" 2>/dev/null

# Persist control/core scripts + busybox for the Manager App
cp -f "$MODPATH/scripts/danmu_control.sh" "$BIN_DIR/danmu_control.sh" 2>/dev/null
cp -f "$MODPATH/scripts/danmu_core.sh" "$BIN_DIR/danmu_core.sh" 2>/dev/null
chmod 0755 "$BIN_DIR/danmu_control.sh" "$BIN_DIR/danmu_core.sh" 2>/dev/null

if [ -f "$MODPATH/bin/busybox" ]; then
  cp -f "$MODPATH/bin/busybox" "$BIN_DIR/busybox" 2>/dev/null
  chmod 0755 "$BIN_DIR/busybox" 2>/dev/null
fi

# If BusyBox is dynamically linked (libbusybox.so.*), copy its shared libs to
# the persistent runtime directory as well.
#
# The Manager App runs /data/adb/danmu_api_server/bin/busybox directly; without
# these libs Android linker may fail with:
#   CANNOT LINK EXECUTABLE ... libbusybox.so.* not found
if [ -d "$MODPATH/bin/lib" ]; then
  mkdir -p "$BIN_DIR/lib" "$PERSIST/lib" 2>/dev/null
  cp -af "$MODPATH/bin/lib/." "$BIN_DIR/lib/" 2>/dev/null || true
  cp -af "$MODPATH/bin/lib/." "$PERSIST/lib/" 2>/dev/null || true
  chmod 0755 "$BIN_DIR/lib" "$PERSIST/lib" 2>/dev/null || true
  chmod 0644 "$BIN_DIR/lib/"* "$PERSIST/lib/"* 2>/dev/null || true
fi

# One-time migrate from previous installed module (if user曾经直接改过模块内配置)
# 目标：最终只保留一个配置文件：$CFG_DIR/.env
if [ ! -f "$CFG_DIR/.env" ] && [ -f "$OLD_MOD_CFG/.env" ]; then
  cp -f "$OLD_MOD_CFG/.env" "$CFG_DIR/.env" 2>/dev/null
fi

# 如果旧版只存在 config.yaml，则尽量转换为 .env（仅支持顶层 KEY: VALUE）
if [ ! -f "$CFG_DIR/.env" ] && [ -f "$OLD_MOD_CFG/config.yaml" ]; then
  awk '
    /^[[:space:]]*#/ {next}
    /^[[:space:]]*$/ {next}
    {
      if (match($0, /^[[:space:]]*([A-Za-z0-9_]+)[[:space:]]*:[[:space:]]*(.*)$/, a)) {
        key=a[1]; val=a[2];
        sub(/[[:space:]]+#.*$/, "", val);
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", val);
        if (val ~ /^\".*\"$/) { val=substr(val,2,length(val)-2); }
        print key "=" val;
      }
    }
  ' "$OLD_MOD_CFG/config.yaml" > "$CFG_DIR/.env" 2>/dev/null
fi

# If still missing, seed from defaults (do not overwrite user config)
if [ ! -f "$CFG_DIR/.env" ] && [ -f "$MODPATH/defaults/config/.env.example" ]; then
  cp -f "$MODPATH/defaults/config/.env.example" "$CFG_DIR/.env" 2>/dev/null
fi

# 强制只保留一个配置文件
rm -f "$CFG_DIR/config.yaml" 2>/dev/null

# permissions
chmod 755 "$PERSIST" "$CFG_DIR" "$LOGDIR" 2>/dev/null
chmod 600 "$CFG_DIR/.env" 2>/dev/null

# 清理旧版误复制到 $PERSIST 的运行时目录，避免本地文件重复占空间
rm -rf "$PERSIST/danmu_api" "$PERSIST/node_modules" \
       "$PERSIST/android-server.mjs" "$PERSIST/package.json" 2>/dev/null

# 让 Web UI / env-api 继续写 "app/config"，但实际落到持久化目录
# - app/config 作为软链接指向 $CFG_DIR
rm -rf "$MODPATH/app/config" 2>/dev/null
ln -s "$CFG_DIR" "$MODPATH/app/config" 2>/dev/null


# ============================================================
# Core (danmu_api): persistent multi-core store
# - Store:   /data/adb/danmu_api_server/cores/<id>/danmu_api
# - Active:  /data/adb/danmu_api_server/core -> (symlink) active core dir
# - Module:  $MODPATH/app/danmu_api -> (symlink) /data/adb/danmu_api_server/core
#
# The Manager App can install/switch/delete cores without reflashing.
# ============================================================
CORES_DIR="$PERSIST/cores"
CORE_LINK="$PERSIST/core"
ACTIVE_FILE="$PERSIST/active_core_id"

mkdir -p "$CORES_DIR" 2>/dev/null

# Seed bundled core only when there is no existing core store
if [ ! -f "$ACTIVE_FILE" ]; then
  BUNDLED_CORE="$MODPATH/app/danmu_api"
  if [ -d "$BUNDLED_CORE" ] && [ -f "$BUNDLED_CORE/worker.js" ]; then
    repo="unknown"; ref="unknown"; sha=""; ver=""
    if [ -f "$MODPATH/defaults/core_source.txt" ]; then
      repo="$(grep -m1 '^repo=' "$MODPATH/defaults/core_source.txt" 2>/dev/null | cut -d= -f2- || true)"
      ref="$(grep -m1 '^ref=' "$MODPATH/defaults/core_source.txt" 2>/dev/null | cut -d= -f2- || true)"
      sha="$(grep -m1 '^sha=' "$MODPATH/defaults/core_source.txt" 2>/dev/null | cut -d= -f2- || true)"
      ver="$(grep -m1 '^version=' "$MODPATH/defaults/core_source.txt" 2>/dev/null | cut -d= -f2- || true)"
    fi

    sha_short=""
    if [ -n "$sha" ]; then sha_short="$(echo "$sha" | cut -c1-7)"; fi
    id_raw="bundled_${repo}_${ref}_${sha_short}"
    id="$(echo "$id_raw" | tr '/:@ ' '____' | tr -cd 'A-Za-z0-9._-')"
    [ -n "$id" ] || id="bundled"

    dest_root="$CORES_DIR/$id"
    dest_core="$dest_root/danmu_api"
    mkdir -p "$dest_core" 2>/dev/null
    cp -a "$BUNDLED_CORE/." "$dest_core/" 2>/dev/null

    if [ -z "$ver" ] && [ -f "$dest_core/configs/globals.js" ]; then
      ver="$(grep -m1 -E 'VERSION[[:space:]]*:' "$dest_core/configs/globals.js" 2>/dev/null | sed -E "s/.*VERSION[[:space:]]*:[[:space:]]*['\"]([^'\"]+)['\"].*/\1/" | head -n 1)"
    fi

    installed="$(date '+%F %T')"
    sizeb="0"
    if command -v du >/dev/null 2>&1; then
      sizeb="$(du -sk "$dest_core" 2>/dev/null | awk '{print $1*1024}' || echo 0)"
    fi

    # meta.json (simple)
    sha_short_json=""
    if [ -n "$sha" ]; then sha_short_json="$(echo "$sha" | cut -c1-7)"; fi
    cat > "$dest_root/meta.json" <<EOF
{
  "id": "$id",
  "repo": "$repo",
  "ref": "$ref",
  "sha": "$sha",
  "shaShort": "$sha_short_json",
  "version": "$ver",
  "installedAt": "$installed",
  "sizeBytes": $sizeb
}
EOF

    rm -f "$CORE_LINK" 2>/dev/null
    ln -s "$dest_core" "$CORE_LINK" 2>/dev/null
    echo "$id" > "$ACTIVE_FILE" 2>/dev/null
  fi
fi

# Ensure module points to persistent core symlink
rm -rf "$MODPATH/app/danmu_api" 2>/dev/null
ln -s "$CORE_LINK" "$MODPATH/app/danmu_api" 2>/dev/null


# Install event-driven watcher into /data/adb/service.d (no polling)
SERVICE_D="/data/adb/service.d"
PERSIST="/data/adb/danmu_api_server"
mkdir -p "$SERVICE_D" 2>/dev/null

# Remove old polling watchdog if present
rm -f "$SERVICE_D/danmu_api_server-watchdog.sh" 2>/dev/null
rm -f "$PERSIST/watchdog.pid" 2>/dev/null

# Install inotifyd watcher (if device has inotifyd, module toggle will be instant)
cp -f "$MODPATH/scripts/danmu_inotifyd_service.sh" "$SERVICE_D/danmu_api_server-inotifyd.sh" 2>/dev/null
chmod 0755 "$SERVICE_D/danmu_api_server-inotifyd.sh" 2>/dev/null
