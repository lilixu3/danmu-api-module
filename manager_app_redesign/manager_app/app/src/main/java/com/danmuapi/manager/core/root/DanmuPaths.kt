package com.danmuapi.manager.core.root

object DanmuPaths {
    const val PERSIST_DIR = "/data/adb/danmu_api_server"
    const val BIN_DIR = "/data/adb/danmu_api_server/bin"

    const val CORE_CLI = "$BIN_DIR/danmu_core.sh"
    const val CONTROL_CLI = "$BIN_DIR/danmu_control.sh"
    const val LOG_DIR = "$PERSIST_DIR/logs"
    const val ENV_FILE = "$PERSIST_DIR/config/.env"
}
