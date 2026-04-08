#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

version=""
tag=""
previous_tag=""
output_path="$CI_OUT_DIR/release-notes.md"
repo_slug="${GITHUB_REPOSITORY:-}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      version="$2"
      shift 2
      ;;
    --tag)
      tag="$2"
      shift 2
      ;;
    --previous-tag)
      previous_tag="$2"
      shift 2
      ;;
    --output)
      output_path="$2"
      shift 2
      ;;
    --repo)
      repo_slug="$2"
      shift 2
      ;;
    *)
      echo "未知参数: $1" >&2
      exit 1
      ;;
  esac
done

[ -n "$version" ] || { echo "缺少 --version" >&2; exit 1; }
[ -n "$tag" ] || tag="v$version"
mkdir -p "$(dirname "$output_path")"

if [ -n "$previous_tag" ]; then
  range="${previous_tag}..HEAD"
  compare_text="${previous_tag} → ${tag}"
else
  range="HEAD"
  compare_text="初次发布"
fi

commit_count="$(git -C "$REPO_ROOT" rev-list --count "$range")"
asset_manager_apk="DanmuApiManager-v${version}-release.apk"
asset_module_nonode="danmu_api_server_${version}.zip"
asset_module_node="danmu_api_server_node_${version}.zip"
update_list_file="$(mktemp)"

cleanup() {
  rm -f "$update_list_file"
}
trap cleanup EXIT

if [ -n "$previous_tag" ]; then
  git -C "$REPO_ROOT" log --no-merges --pretty=format:'- %s' "$range" > "$update_list_file"
else
  git -C "$REPO_ROOT" log --no-merges --pretty=format:'- %s' HEAD > "$update_list_file"
fi

{
  echo "## 本次更新"
  if [ "$commit_count" -gt 0 ] && [ -s "$update_list_file" ]; then
    cat "$update_list_file"
  else
    echo "- 常规维护与构建更新"
  fi
  echo
  echo
  echo "## 发布产物"
  echo "- ${asset_manager_apk}"
  echo "- ${asset_module_nonode}"
  echo "- ${asset_module_node}"
  echo "- SHA256SUMS.txt"
  if [ -n "$repo_slug" ] && [ -n "$previous_tag" ]; then
    echo
    echo "## 对比链接"
    echo "- https://github.com/${repo_slug}/compare/${previous_tag}...${tag}"
  fi
} > "$output_path"

echo "$output_path"
append_github_output release_notes_path "$output_path"
