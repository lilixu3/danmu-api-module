#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
MODULE_TEMPLATE_DIR="${MODULE_TEMPLATE_DIR:-$REPO_ROOT/module_template}"
MANAGER_APP_DIR="${MANAGER_APP_DIR:-$REPO_ROOT/manager_app_redesign/manager_app}"
MANAGER_APP_RELEASE_APK_PATH="${MANAGER_APP_RELEASE_APK_PATH:-$MANAGER_APP_DIR/app/build/outputs/apk/release/app-release.apk}"
MANAGER_APP_DEBUG_APK_PATH="${MANAGER_APP_DEBUG_APK_PATH:-$MANAGER_APP_DIR/app/build/outputs/apk/debug/app-debug.apk}"
CI_BUILD_DIR="${CI_BUILD_DIR:-$REPO_ROOT/build/ci}"
CI_TMP_DIR="${CI_TMP_DIR:-$CI_BUILD_DIR/tmp}"
CI_OUT_DIR="${CI_OUT_DIR:-$CI_BUILD_DIR/out}"

mkdir -p "$CI_TMP_DIR" "$CI_OUT_DIR"

log_section() {
  echo "==================================================="
  echo ">>> $*"
  echo "==================================================="
}

require_command() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "缺少命令: $cmd" >&2
    exit 1
  fi
}

module_prop_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "$MODULE_TEMPLATE_DIR/module.prop" | head -n 1
}

current_module_version() {
  module_prop_value version
}

current_module_version_code() {
  module_prop_value versionCode
}

default_build_version() {
  local latest_tag
  latest_tag="$(latest_semver_tag)"
  if [ -n "$latest_tag" ]; then
    echo "${latest_tag#v}"
  else
    current_module_version
  fi
}

validate_semver() {
  [[ "${1:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

calc_version_code() {
  local version="$1"
  local major=0 minor=0 patch=0

  if [[ "$version" =~ ^[vV]?([0-9]+)\.([0-9]+)(\.([0-9]+))?$ ]]; then
    major="${BASH_REMATCH[1]}"
    minor="${BASH_REMATCH[2]:-0}"
    patch="${BASH_REMATCH[4]:-0}"

    major=$((major))
    minor=$((minor))
    patch=$((patch))

    local code=$(( major * 1000000 + minor * 1000 + patch ))
    if [ "$code" -gt 2147483647 ]; then
      code=2147483647
    fi
    echo "$code"
    return 0
  fi

  local digits
  digits="$(echo "$version" | tr -cd '0-9')"
  [ -n "$digits" ] || digits=1
  digits="${digits:0:9}"
  echo "$digits"
}

bump_semver() {
  local version="$1"
  local bump_type="$2"

  if ! validate_semver "$version"; then
    echo "非法版本号: $version" >&2
    exit 1
  fi

  local major minor patch
  IFS='.' read -r major minor patch <<< "$version"

  case "$bump_type" in
    major)
      major=$((major + 1))
      minor=0
      patch=0
      ;;
    minor)
      minor=$((minor + 1))
      patch=0
      ;;
    patch)
      patch=$((patch + 1))
      ;;
    *)
      echo "不支持的 bump 类型: $bump_type" >&2
      exit 1
      ;;
  esac

  echo "${major}.${minor}.${patch}"
}

latest_semver_tag() {
  git -C "$REPO_ROOT" tag --sort=-version:refname \
    | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' \
    | head -n 1 || true
}

append_github_output() {
  local key="$1"
  local value="$2"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "${key}=${value}" >> "$GITHUB_OUTPUT"
  fi
}

append_summary() {
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    echo "$*" >> "$GITHUB_STEP_SUMMARY"
  fi
}
