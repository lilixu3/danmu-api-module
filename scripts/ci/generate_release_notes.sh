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
current_date="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

{
  echo "# Danmu API Server ${tag}"
  echo
  echo "## 发布信息"
  echo "- 发布时间（UTC）：${current_date}"
  echo "- 版本范围：${compare_text}"
  echo "- 提交数量：${commit_count}"
  if [ -n "$repo_slug" ] && [ -n "$previous_tag" ]; then
    echo "- 对比链接：https://github.com/${repo_slug}/compare/${previous_tag}...${tag}"
  fi
  echo
  echo "## 构建产物"
  echo "- Danmu API 管理器 Release APK"
  echo "- Magisk 模块（No-Node）"
  echo "- Magisk 模块（内置 Node）"
  echo "- SHA256SUMS.txt"
  echo
  echo "## 本次更新"
  if [ -n "$previous_tag" ]; then
    git -C "$REPO_ROOT" log --no-merges --pretty=format:'- %s (`%h`)' "$range"
  else
    git -C "$REPO_ROOT" log --no-merges --pretty=format:'- %s (`%h`)' HEAD
  fi
  echo
  echo
  echo "## 提交明细"
  if [ -n "$previous_tag" ]; then
    git -C "$REPO_ROOT" log --no-merges --date=short --pretty=format:'- %ad  %s (`%h`)' "$range"
  else
    git -C "$REPO_ROOT" log --no-merges --date=short --pretty=format:'- %ad  %s (`%h`)' HEAD
  fi
  echo
} > "$output_path"

echo "$output_path"
append_github_output release_notes_path "$output_path"
