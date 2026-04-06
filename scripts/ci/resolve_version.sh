#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

requested_version=""
bump_type="patch"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      requested_version="$2"
      shift 2
      ;;
    --bump)
      bump_type="$2"
      shift 2
      ;;
    *)
      echo "未知参数: $1" >&2
      exit 1
      ;;
  esac
done

previous_tag="$(latest_semver_tag)"

if [ -n "$requested_version" ]; then
  version="$requested_version"
  if ! validate_semver "$version"; then
    echo "非法版本号: $version" >&2
    exit 1
  fi
else
  if [ -n "$previous_tag" ]; then
    version="$(bump_semver "${previous_tag#v}" "$bump_type")"
  else
    version="1.0.0"
  fi
fi

version_code="$(calc_version_code "$version")"
tag="v${version}"

printf 'version=%s\n' "$version"
printf 'tag=%s\n' "$tag"
printf 'version_code=%s\n' "$version_code"
printf 'previous_tag=%s\n' "$previous_tag"

append_github_output version "$version"
append_github_output tag "$tag"
append_github_output version_code "$version_code"
append_github_output previous_tag "$previous_tag"
