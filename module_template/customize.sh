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
DOWNLOAD_CONF="$PERSIST/core_download.conf"
DEFAULT_CORE_REPO="huangxd-/danmu_api"
DEFAULT_CORE_REF="main"

ui_msg() {
  if command -v ui_print >/dev/null 2>&1; then
    ui_print "$1"
  else
    echo "$1"
  fi
}

rand_num() {
  if [ -r /dev/urandom ]; then
    od -An -N2 -tu2 /dev/urandom 2>/dev/null | tr -d ' '
  else
    date +%s 2>/dev/null || echo 0
  fi
}

pick_proxy_base() {
  n="$(rand_num)"
  case $((n % 3)) in
    0) echo "https://hk.gh-proxy.org/" ;;
    1) echo "https://cdn.gh-proxy.org/" ;;
    *) echo "https://edgeone.gh-proxy.org/" ;;
  esac
}

choose_github_mode() {
  ui_msg "- 是否可以直连 GitHub？"
  ui_msg "- 音量上：可以（直连）"
  ui_msg "- 音量下：不行（使用加速）"
  ui_msg "- 10 秒内未选择将默认直连"

  if command -v keycheck >/dev/null 2>&1; then
    i=0
    while [ "$i" -lt 10 ]; do
      keycheck
      code="$?"
      case "$code" in
        41|115) echo "direct"; return 0 ;; # 常见音量上
        42|114) echo "proxy"; return 0 ;;  # 常见音量下
      esac
      i=$((i+1))
    done
  fi

  if [ -x /system/bin/getevent ]; then
    TIMEOUT_CMD=""
    if [ -x "$MODPATH/bin/busybox" ] && "$MODPATH/bin/busybox" timeout --help >/dev/null 2>&1; then
      TIMEOUT_CMD="$MODPATH/bin/busybox timeout"
    elif command -v timeout >/dev/null 2>&1; then
      TIMEOUT_CMD="timeout"
    fi

    if [ -n "$TIMEOUT_CMD" ]; then
      key="$($TIMEOUT_CMD 10 /system/bin/getevent -qlc 1 2>/dev/null | grep -m1 'KEY_VOLUME')"
      case "$key" in
        *KEY_VOLUMEUP*) echo "direct"; return 0 ;;
        *KEY_VOLUMEDOWN*) echo "proxy"; return 0 ;;
      esac
    else
      key="$(/system/bin/getevent -qlc 1 2>/dev/null | grep -m1 'KEY_VOLUME')"
      case "$key" in
        *KEY_VOLUMEUP*) echo "direct"; return 0 ;;
        *KEY_VOLUMEDOWN*) echo "proxy"; return 0 ;;
      esac
    fi
  fi

  ui_msg "- 未找到 getevent，默认直连"
  echo "direct"
}

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

# 首次安装：提示并下载核心（仅 danmu_api 目录）
if [ ! -f "$DOWNLOAD_CONF" ]; then
  mode="$(choose_github_mode)"
  proxy_base=""
  if [ "$mode" = "proxy" ]; then
    proxy_base="$(pick_proxy_base)"
    ui_msg "- 使用加速服务：$proxy_base"
  else
    ui_msg "- 使用直连下载"
  fi

  cat > "$DOWNLOAD_CONF" <<EOF
MODE=$mode
PROXY_BASE=$proxy_base
EOF
  chmod 600 "$DOWNLOAD_CONF" 2>/dev/null || true
else
  # shellcheck disable=SC1090
  . "$DOWNLOAD_CONF" 2>/dev/null || true
  mode="${MODE:-}"
  proxy_base="${PROXY_BASE:-}"
  if [ "$mode" != "direct" ] && [ "$mode" != "proxy" ]; then
    mode="$(choose_github_mode)"
    proxy_base=""
    if [ "$mode" = "proxy" ]; then
      proxy_base="$(pick_proxy_base)"
      ui_msg "- 使用加速服务：$proxy_base"
    else
      ui_msg "- 使用直连下载"
    fi
    cat > "$DOWNLOAD_CONF" <<EOF
MODE=$mode
PROXY_BASE=$proxy_base
EOF
    chmod 600 "$DOWNLOAD_CONF" 2>/dev/null || true
  fi
fi

if [ ! -f "$ACTIVE_FILE" ]; then
  if [ -x "$MODPATH/scripts/danmu_core.sh" ]; then
    ui_msg "- 开始下载核心：${DEFAULT_CORE_REPO}@${DEFAULT_CORE_REF}"
    TIMEOUT_CMD=""
    if [ -x "$MODPATH/bin/busybox" ] && "$MODPATH/bin/busybox" timeout --help >/dev/null 2>&1; then
      TIMEOUT_CMD="$MODPATH/bin/busybox timeout"
    elif command -v timeout >/dev/null 2>&1; then
      TIMEOUT_CMD="timeout"
    fi
    DL_TIMEOUT="${DANMU_CORE_TIMEOUT:-120}"

    if [ -n "$TIMEOUT_CMD" ]; then
      if DANMU_GH_MODE="$mode" DANMU_GH_PROXY_BASE="$proxy_base" \
        $TIMEOUT_CMD "$DL_TIMEOUT" "$MODPATH/scripts/danmu_core.sh" core install "$DEFAULT_CORE_REPO" "$DEFAULT_CORE_REF" >/dev/null 2>&1; then
        ui_msg "- 核心下载完成"
      else
        ui_msg "- 核心下载失败或超时，可在管理器里重试"
      fi
    else
      if DANMU_GH_MODE="$mode" DANMU_GH_PROXY_BASE="$proxy_base" \
        "$MODPATH/scripts/danmu_core.sh" core install "$DEFAULT_CORE_REPO" "$DEFAULT_CORE_REF" >/dev/null 2>&1; then
        ui_msg "- 核心下载完成"
      else
        ui_msg "- 核心下载失败，可在管理器里重试"
      fi
    fi
  else
    ui_msg "- 未找到核心下载脚本，跳过下载"
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
