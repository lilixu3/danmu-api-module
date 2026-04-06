#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

variant="release"
version="$(default_build_version)"
version_code=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --variant)
      variant="$2"
      shift 2
      ;;
    --version)
      version="$2"
      shift 2
      ;;
    --version-code)
      version_code="$2"
      shift 2
      ;;
    *)
      echo "未知参数: $1" >&2
      exit 1
      ;;
  esac
done

if [ -z "$version_code" ]; then
  version_code="$(calc_version_code "$version")"
fi

case "$variant" in
  debug|release|both) ;;
  *)
    echo "不支持的构建类型: $variant" >&2
    exit 1
    ;;
esac

require_command bash
require_command chmod

chmod +x "$MANAGER_APP_DIR/gradlew"

log_section "构建管理器 APK ($variant)"
echo "版本: $version ($version_code)"

GRADLE_ARGS=(
  --no-daemon
  -Dkotlin.compiler.execution.strategy=in-process
  -Dkotlin.incremental=false
  "-PappVersionName=$version"
  "-PappVersionCode=$version_code"
)

TASKS=()
case "$variant" in
  debug)
    TASKS+=(":app:assembleDebug")
    ;;
  release)
    TASKS+=(":app:assembleRelease")
    ;;
  both)
    TASKS+=(":app:assembleDebug" ":app:assembleRelease")
    ;;
esac

(
  cd "$MANAGER_APP_DIR"
  ./gradlew "${GRADLE_ARGS[@]}" "${TASKS[@]}"
)

if [ "$variant" = "debug" ] || [ "$variant" = "both" ]; then
  test -f "$MANAGER_APP_DEBUG_APK_PATH"
  echo "Debug APK: $MANAGER_APP_DEBUG_APK_PATH"
  append_github_output manager_debug_apk "$MANAGER_APP_DEBUG_APK_PATH"
fi

if [ "$variant" = "release" ] || [ "$variant" = "both" ]; then
  test -f "$MANAGER_APP_RELEASE_APK_PATH"
  echo "Release APK: $MANAGER_APP_RELEASE_APK_PATH"
  append_github_output manager_release_apk "$MANAGER_APP_RELEASE_APK_PATH"
fi
