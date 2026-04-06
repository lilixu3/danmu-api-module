#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

variant="both"
version="$(default_build_version)"
version_code=""
manager_apk="$MANAGER_APP_RELEASE_APK_PATH"
out_dir="$CI_OUT_DIR"
build_root="$CI_TMP_DIR/modules"

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
    --manager-apk)
      manager_apk="$2"
      shift 2
      ;;
    --out-dir)
      out_dir="$2"
      shift 2
      ;;
    --build-root)
      build_root="$2"
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
  both|node|no_node) ;;
  *)
    echo "不支持的模块构建类型: $variant" >&2
    exit 1
    ;;
esac

for cmd in zip unzip rsync curl strip readelf dpkg-deb xz; do
  require_command "$cmd"
done

if [ ! -f "$manager_apk" ]; then
  echo "未找到管理器 APK: $manager_apk" >&2
  exit 1
fi

mkdir -p "$out_dir" "$build_root"

termux_prepare_index() {
  local arch="aarch64"
  local repo_base="https://packages.termux.dev/apt/termux-main"
  local index_xz="${repo_base}/dists/stable/main/binary-${arch}/Packages.xz"
  local index_plain="${repo_base}/dists/stable/main/binary-${arch}/Packages"

  local work_dir
  work_dir="$(mktemp -d)"

  echo "$repo_base" > "$work_dir/.repo_base"

  if curl -fLsS --retry 3 "$index_xz" -o "$work_dir/Packages.xz"; then
    xz -dc "$work_dir/Packages.xz" > "$work_dir/Packages"
  else
    curl -fLsS --retry 3 "$index_plain" -o "$work_dir/Packages"
  fi

  echo "$work_dir"
}

termux_pkg_filename() {
  local pkg="$1"
  local pkg_index="$2"
  awk -v pkg="$pkg" '
    BEGIN{RS="";FS="\n"}
    {
      hit=0
      for(i=1;i<=NF;i++){
        if($i == "Package: " pkg){ hit=1; break }
      }
      if(hit){
        for(i=1;i<=NF;i++){
          if(index($i,"Filename: ")==1){
            sub(/^Filename: /,"",$i)
            print $i
            exit
          }
        }
      }
    }' "$pkg_index"
}

prepare_termux_busybox() {
  if [ -f "$CI_TMP_DIR/busybox/busybox" ]; then
    return 0
  fi

  mkdir -p "$CI_TMP_DIR/busybox"
  local idx_dir
  idx_dir="$(termux_prepare_index)"
  local repo_base
  repo_base="$(cat "$idx_dir/.repo_base")"
  local pkg_index="$idx_dir/Packages"
  local filename
  filename="$(termux_pkg_filename "busybox" "$pkg_index" || true)"

  if [ -z "$filename" ]; then
    echo "Termux Packages 索引中未找到 busybox" >&2
    exit 1
  fi

  curl -fLsS --retry 3 "$repo_base/$filename" -o "$idx_dir/busybox.deb"
  mkdir -p "$idx_dir/extract"
  dpkg-deb -x "$idx_dir/busybox.deb" "$idx_dir/extract"

  local busybox_bin
  busybox_bin="$(find "$idx_dir/extract" -type f -path '*/files/usr/bin/busybox' | head -n 1 || true)"
  if [ -z "$busybox_bin" ]; then
    busybox_bin="$(find "$idx_dir/extract" -type f -path '*/usr/bin/busybox' | head -n 1 || true)"
  fi
  if [ -z "$busybox_bin" ]; then
    echo "解压后未找到 busybox 可执行文件" >&2
    exit 1
  fi

  cp -f "$busybox_bin" "$CI_TMP_DIR/busybox/busybox"
  chmod 0755 "$CI_TMP_DIR/busybox/busybox"
  rm -rf "$idx_dir"
}

fetch_termux_node() {
  local root="$1"
  local arch="aarch64"
  local repo_base="https://packages.termux.dev/apt/termux-main"
  local index_xz="${repo_base}/dists/stable/main/binary-${arch}/Packages.xz"
  local index_plain="${repo_base}/dists/stable/main/binary-${arch}/Packages"

  local work_dir
  work_dir="$(mktemp -d)"
  local extracted="${work_dir}/extracted"
  mkdir -p "$extracted"

  if curl -fLsS --retry 3 "$index_xz" -o "$work_dir/Packages.xz"; then
    xz -dc "$work_dir/Packages.xz" > "$work_dir/Packages"
  else
    curl -fLsS --retry 3 "$index_plain" -o "$work_dir/Packages"
  fi
  local pkg_index="$work_dir/Packages"

  pkg_filename() {
    local pkg="$1"
    awk -v pkg="$pkg" '
      BEGIN{RS="";FS="\n"}
      {
        hit=0
        for(i=1;i<=NF;i++){
          if($i == "Package: " pkg){ hit=1; break }
        }
        if(hit){
          for(i=1;i<=NF;i++){
            if(index($i,"Filename: ")==1){
              sub(/^Filename: /,"",$i)
              print $i
              exit
            }
          }
        }
      }' "$pkg_index"
  }

  pkg_depends() {
    local pkg="$1"
    awk -v pkg="$pkg" '
      BEGIN{RS="";FS="\n"}
      {
        hit=0
        for(i=1;i<=NF;i++){
          if($i == "Package: " pkg){ hit=1; break }
        }
        if(hit){
          for(i=1;i<=NF;i++){
            if(index($i,"Depends: ")==1){
              dep=substr($i,10)
              for(j=i+1;j<=NF && $j ~ /^ /; j++){
                dep=dep " " substr($j,2)
              }
              print dep
              exit
            }
          }
        }
      }' "$pkg_index"
  }

  parse_dep_groups() {
    local dep_string="${1:-}"
    [ -z "$dep_string" ] && return 0

    echo "$dep_string" | tr ',' '\n' | while read -r group; do
      group="$(echo "$group" | xargs)"
      [ -z "$group" ] && continue
      group="$(echo "$group" | sed -E 's/\([^)]*\)//g')"
      group="$(echo "$group" | sed -E 's/:[a-z0-9_-]+//g')"
      group="$(echo "$group" | tr -s ' ' ' ' | xargs)"
      [ -n "$group" ] && echo "$group"
    done
  }

  resolve_group() {
    local group="$1"
    local alt
    IFS='|' read -ra alts <<< "$group"

    for alt in "${alts[@]}"; do
      alt="$(echo "$alt" | xargs)"
      [ -z "$alt" ] && continue

      local candidates=("$alt")
      if [ "$alt" = "libc++" ]; then
        candidates=("libc++" "libc++-shared")
      elif [ "$alt" = "libc++-shared" ]; then
        candidates=("libc++-shared" "libc++")
      fi

      local candidate
      for candidate in "${candidates[@]}"; do
        local filename
        filename="$(pkg_filename "$candidate" || true)"
        if [ -n "$filename" ]; then
          echo "$candidate"
          return 0
        fi
      done
    done

    return 1
  }

  download_and_extract() {
    local pkg="$1"
    local filename="$2"
    curl -fLsS --retry 3 "${repo_base}/${filename}" -o "${work_dir}/${pkg}.deb"
    dpkg-deb -x "${work_dir}/${pkg}.deb" "$extracted"
    rm -f "${work_dir}/${pkg}.deb"
  }

  declare -A seen=()
  local queue=("nodejs")

  while [ "${#queue[@]}" -gt 0 ]; do
    local pkg="${queue[0]}"
    queue=("${queue[@]:1}")

    [ -n "$pkg" ] || continue
    if [ "${seen[$pkg]+x}" = "x" ]; then
      continue
    fi
    seen["$pkg"]=1

    local filename
    filename="$(pkg_filename "$pkg" || true)"
    if [ -z "$filename" ]; then
      echo "在 Packages 索引中找不到包: $pkg" >&2
      exit 1
    fi

    download_and_extract "$pkg" "$filename"

    local deps
    deps="$(pkg_depends "$pkg" || true)"
    while read -r group; do
      [ -n "$group" ] || continue
      local resolved
      if ! resolved="$(resolve_group "$group")"; then
        echo "依赖组无法解析: $group" >&2
        exit 1
      fi
      queue+=("$resolved")
    done < <(parse_dep_groups "$deps")
  done

  local node_bin_path
  node_bin_path="$(find "$extracted" -type f -path '*/files/usr/bin/node' | head -n 1 || true)"
  if [ -z "$node_bin_path" ]; then
    node_bin_path="$(find "$extracted" -type f -path '*/usr/bin/node' | head -n 1 || true)"
  fi
  if [ -z "$node_bin_path" ]; then
    echo "解压后未找到 node 可执行文件" >&2
    exit 1
  fi

  local prefix_dir
  prefix_dir="$(dirname "$(dirname "$node_bin_path")")"
  local lib_dir="${prefix_dir}/lib"
  if [ ! -d "$lib_dir" ]; then
    echo "未找到 node 依赖目录: $lib_dir" >&2
    exit 1
  fi

  mkdir -p "$root/node/bin" "$root/node/lib"
  cp -f "$node_bin_path" "$root/node/bin/node"
  chmod 0755 "$root/node/bin/node"

  needed_of() {
    local file="$1"
    readelf -d "$file" 2>/dev/null | awk '/NEEDED/{gsub(/\[|\]/,"",$5); print $5}' || true
  }

  find_lib_in_termux() {
    local soname="$1"
    if [ -e "$lib_dir/$soname" ] || [ -L "$lib_dir/$soname" ]; then
      echo "$lib_dir/$soname"
      return 0
    fi
    return 1
  }

  copy_lib_to_module() {
    local src="$1"
    cp -a "$src" "$root/node/lib/"

    if [ -L "$src" ]; then
      local target
      target="$(readlink -f "$src" || true)"
      if [ -n "$target" ] && [ -e "$target" ]; then
        cp -a "$target" "$root/node/lib/"
      fi
    fi
  }

  declare -A done_lib=()
  local file_queue=("$node_bin_path")

  while [ "${#file_queue[@]}" -gt 0 ]; do
    local current_file="${file_queue[0]}"
    file_queue=("${file_queue[@]:1}")

    for soname in $(needed_of "$current_file"); do
      if [ "${done_lib[$soname]+x}" = "x" ]; then
        continue
      fi

      local src
      if src="$(find_lib_in_termux "$soname")"; then
        done_lib["$soname"]=1
        copy_lib_to_module "$src"

        local real_src
        real_src="$(readlink -f "$src" 2>/dev/null || echo "$src")"
        if [ -e "$real_src" ]; then
          file_queue+=("$real_src")
        fi
      else
        done_lib["$soname"]=1
      fi
    done
  done

  strip --strip-unneeded "$root/node/bin/node" 2>/dev/null || true
  find "$root/node/lib" -maxdepth 1 -type f -name '*.so*' -exec strip --strip-unneeded {} \; 2>/dev/null || true

  rm -rf "$work_dir"
}

copy_template() {
  local target="$1"
  rm -rf "$target"
  mkdir -p "$target"
  rsync -a --delete "$MODULE_TEMPLATE_DIR/" "$target/"
}

patch_package_json_version() {
  local package_json="$1"
  local new_version="$2"

  if [ ! -f "$package_json" ]; then
    return 0
  fi

  python3 - "$package_json" "$new_version" <<'PY'
import json
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
version = sys.argv[2]
data = json.loads(path.read_text(encoding='utf-8'))
data['version'] = version
path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
PY
}

prepare_module_root() {
  local module_root="$1"
  local build_time_utc="$2"

  rm -rf "$module_root/app/danmu_api"
  mkdir -p "$module_root/defaults"
  cat > "$module_root/defaults/core_source.txt" <<TXT
repo=huangxd-/danmu_api
ref=main
built_at=${build_time_utc}
TXT

  mkdir -p "$module_root/system/app/DanmuApiManager"
  cp -f "$manager_apk" "$module_root/system/app/DanmuApiManager/DanmuApiManager.apk"

  prepare_termux_busybox
  mkdir -p "$module_root/bin"
  cp -f "$CI_TMP_DIR/busybox/busybox" "$module_root/bin/busybox"
  chmod 0755 "$module_root/bin/busybox"

  sed -i "s|^version=.*|version=${version}|g" "$module_root/module.prop"
  sed -i "s|^versionCode=.*|versionCode=${version_code}|g" "$module_root/module.prop"
  patch_package_json_version "$module_root/app/package.json" "$version"
}

finalize_zip() {
  local root="$1"
  local output_path="$2"

  rm -f "$output_path"
  mkdir -p "$(dirname "$output_path")"

  find "$root" -type f -name '*.sh' -exec chmod 0755 {} \;

  mapfile -t apk_files < <(cd "$root" && find . -type f -name '*.apk' | sort)
  if [ "${#apk_files[@]}" -gt 0 ]; then
    (
      cd "$root"
      printf '%s\0' "${apk_files[@]}" | xargs -0 zip -q -0 "$output_path"
    )
  fi

  (
    cd "$root"
    zip -qr "$output_path" . -x '*.apk'
  )

  echo "$output_path"
}

build_variant() {
  local build_variant="$1"
  local module_root="$build_root/$build_variant"
  local build_time_utc
  build_time_utc="$(date -u +%FT%TZ)"

  copy_template "$module_root"
  prepare_module_root "$module_root" "$build_time_utc"

  local output_name
  case "$build_variant" in
    no_node)
      output_name="danmu_api_server_${version}.zip"
      ;;
    node)
      fetch_termux_node "$module_root"
      output_name="danmu_api_server_node_${version}.zip"
      ;;
    *)
      echo "未知模块类型: $build_variant" >&2
      exit 1
      ;;
  esac

  finalize_zip "$module_root" "$out_dir/$output_name"
}

log_section "构建 Magisk 模块 ($variant)"
echo "版本: $version ($version_code)"
echo "管理器 APK: $manager_apk"

case "$variant" in
  both)
    build_variant no_node
    build_variant node
    ;;
  no_node)
    build_variant no_node
    ;;
  node)
    build_variant node
    ;;
esac
