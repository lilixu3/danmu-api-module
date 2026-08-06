#!/system/bin/sh
# danmu_api_server core + maintenance CLI
#
# This script is designed to be called by:
# - Magisk Action button / inotify handler (root)
# - Danmu API Manager App (via su)
#
# Goals:
# - Manage multiple downloaded cores (install / list / activate / delete)
# - Provide JSON output for the app
# - No polling / background loops

set -eu

MODID="danmu_api_server"
MODDIR="/data/adb/modules/${MODID}"

PERSIST="/data/adb/danmu_api_server"
CFG_DIR="${PERSIST}/config"
CORES_DIR="${PERSIST}/cores"
CORE_LINK="${PERSIST}/core"              # symlink -> active core dir (danmu_api)
ACTIVE_FILE="${PERSIST}/active_core_id"  # text: active core id

TMP_DIR="${PERSIST}/tmp"
LOGDIR="${PERSIST}/logs"

DOWNLOAD_CONF="${PERSIST}/core_download.conf"
DEFAULT_CORE_REPO="huangxd-/danmu_api"
DEFAULT_CORE_REF="main"
GH_MODE="direct"
GH_PROXY_BASE=""

PIDFILE="${PERSIST}/danmu_api.pid"

# flags
FLAG_AUTOSTART_NEW="${PERSIST}/autostart.disabled"
FLAG_AUTOSTART_OLD="${PERSIST}/service.disabled"  # legacy

# 测试注入：仅 CORE_DANMU_TEST_MODE=1 时允许覆盖持久路径/二进制。
# 防止生产 su 调用方通过同名环境变量重定向 root 文件操作。
CORE_DANMU_TEST_MODE="${CORE_DANMU_TEST_MODE:-0}"
if [ "${CORE_DANMU_TEST_MODE}" = "1" ]; then
  if [ -n "${CORE_DANMU_TEST_PERSIST:-}" ]; then
    PERSIST="${CORE_DANMU_TEST_PERSIST}"
    CFG_DIR="${PERSIST}/config"
    CORES_DIR="${PERSIST}/cores"
    CORE_LINK="${PERSIST}/core"
    ACTIVE_FILE="${PERSIST}/active_core_id"
    TMP_DIR="${PERSIST}/tmp"
    LOGDIR="${PERSIST}/logs"
    DOWNLOAD_CONF="${PERSIST}/core_download.conf"
    PIDFILE="${PERSIST}/danmu_api.pid"
    FLAG_AUTOSTART_NEW="${PERSIST}/autostart.disabled"
    FLAG_AUTOSTART_OLD="${PERSIST}/service.disabled"
  fi
  if [ -n "${CORE_DANMU_TEST_MODDIR:-}" ]; then
    MODDIR="${CORE_DANMU_TEST_MODDIR}"
  fi
  if [ -n "${CORE_DANMU_TEST_NODE:-}" ]; then
    MODULE_NODE_MODULES="${CORE_DANMU_TEST_NODE}"
  fi
  if [ -n "${CORE_DANMU_TEST_NODE_MODULES:-}" ]; then
    MODULE_NODE_MODULES="${CORE_DANMU_TEST_NODE_MODULES}"
  fi
  if [ -n "${CORE_DANMU_TEST_NODE_BIN:-}" ]; then
    TEST_NODE_BIN="${CORE_DANMU_TEST_NODE_BIN}"
  fi
  if [ -n "${CORE_DANMU_TEST_RUNTIME_DEPS:-}" ]; then
    RUNTIME_DEPS_JS="${CORE_DANMU_TEST_RUNTIME_DEPS}"
  fi
fi

mkdir -p "${PERSIST}" "${CFG_DIR}" "${CORES_DIR}" "${TMP_DIR}" "${LOGDIR}" 2>/dev/null || true

DEPS_DIR="${PERSIST}/deps"

# Prefer persistent scripts (survive module disable/update)
CTRL="${PERSIST}/bin/danmu_control.sh"
if [ ! -x "${CTRL}" ]; then
  CTRL="${MODDIR}/scripts/danmu_control.sh"
fi

# Prefer module/persistent BusyBox (for wget/unzip/tail on minimal ROMs)
BB=""
if [ -x "${PERSIST}/bin/busybox" ]; then
  BB="${PERSIST}/bin/busybox"
elif [ -x "${MODDIR}/bin/busybox" ]; then
  BB="${MODDIR}/bin/busybox"
fi
if [ -n "${BB}" ]; then
  export PATH="$(dirname "${BB}"):${PATH}"
fi

# Termux BusyBox may be dynamically linked (libbusybox.so.*). Add likely library
# locations to LD_LIBRARY_PATH so BusyBox can run reliably on Android.
if [ -d "${PERSIST}/bin/lib" ]; then
  export LD_LIBRARY_PATH="${PERSIST}/bin/lib:${LD_LIBRARY_PATH:-}"
fi
if [ -d "${PERSIST}/lib" ]; then
  export LD_LIBRARY_PATH="${PERSIST}/lib:${LD_LIBRARY_PATH:-}"
fi
if [ -d "${MODDIR}/bin/lib" ]; then
  export LD_LIBRARY_PATH="${MODDIR}/bin/lib:${LD_LIBRARY_PATH:-}"
fi

# Some Termux binaries may rely on libs shipped with the module
if [ -d "${MODDIR}/node/lib" ]; then
  export LD_LIBRARY_PATH="${MODDIR}/node/lib:${LD_LIBRARY_PATH:-}"
fi

log() {
  :
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

load_download_config() {
  GH_MODE="direct"
  GH_PROXY_BASE=""

  if [ -f "${DOWNLOAD_CONF}" ]; then
    # shellcheck disable=SC1090
    . "${DOWNLOAD_CONF}" 2>/dev/null || true
    if [ -n "${MODE:-}" ]; then GH_MODE="${MODE}"; fi
    if [ -n "${PROXY_BASE:-}" ]; then GH_PROXY_BASE="${PROXY_BASE}"; fi
  fi

  if [ -n "${DANMU_GH_MODE:-}" ]; then
    GH_MODE="${DANMU_GH_MODE}"
  fi
  if [ -n "${DANMU_GH_PROXY_BASE:-}" ]; then
    GH_PROXY_BASE="${DANMU_GH_PROXY_BASE}"
  fi

  if [ "${GH_MODE}" = "proxy" ]; then
    if [ -z "${GH_PROXY_BASE}" ]; then
      GH_PROXY_BASE="$(pick_proxy_base)"
    fi
    case "${GH_PROXY_BASE}" in
      */) : ;;
      *) GH_PROXY_BASE="${GH_PROXY_BASE}/" ;;
    esac
  else
    GH_PROXY_BASE=""
  fi
}

apply_proxy_url() {
  url="$1"
  if [ "${GH_MODE}" = "proxy" ] && [ -n "${GH_PROXY_BASE}" ]; then
    case "${url}" in
      https://github.com/*|https://api.github.com/*|https://codeload.github.com/*|https://raw.githubusercontent.com/*)
        echo "${GH_PROXY_BASE}${url}"
        return 0
        ;;
    esac
  fi
  echo "${url}"
}

have_cmd() {
  command -v "$1" >/dev/null 2>&1
}

json_escape() {
  # Minimal JSON string escaper
  # shellcheck disable=SC2001
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\r/\\r/g' -e 's/\n/\\n/g' -e 's/\t/\\t/g'
}

normalize_repo() {
  r="$1"
  r="${r#https://github.com/}"
  r="${r#http://github.com/}"
  r="${r%/}"
  r="${r%.git}"
  echo "$r"
}

sanitize_id() {
  # keep: A-Z a-z 0-9 . _ -
  # replace separators with _
  printf '%s' "$1" | tr '/:@ ' '____' | tr -cd 'A-Za-z0-9._-'
}

is_running() {
  if [ -f "${PIDFILE}" ]; then
    pid="$(cat "${PIDFILE}" 2>/dev/null || true)"
    if [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null; then
      return 0
    fi
  fi
  return 1
}

module_enabled() {
  [ -d "${MODDIR}" ] && [ ! -f "${MODDIR}/disable" ]
}

autostart_status() {
  # migrate legacy
  if [ -f "${FLAG_AUTOSTART_OLD}" ] && [ ! -f "${FLAG_AUTOSTART_NEW}" ]; then
    mv -f "${FLAG_AUTOSTART_OLD}" "${FLAG_AUTOSTART_NEW}" 2>/dev/null || true
  fi
  if [ -f "${FLAG_AUTOSTART_NEW}" ] || [ -f "${FLAG_AUTOSTART_OLD}" ]; then
    echo "off"
  else
    echo "on"
  fi
}

read_active_id() {
  cat "${ACTIVE_FILE}" 2>/dev/null || true
}

meta_path_for() {
  echo "${CORES_DIR}/$1/meta.json"
}

core_dir_for() {
  echo "${CORES_DIR}/$1/danmu_api"
}

read_meta_string() {
  # args: meta_path key
  mp="$1"
  key="$2"
  [ -f "${mp}" ] || {
    echo ""
    return 0
  }

  line="$(grep -m1 "\"${key}\"" "${mp}" 2>/dev/null || true)"
  if [ -z "${line}" ]; then
    echo ""
    return 0
  fi

  printf '%s' "${line}" | sed -E "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"([^\"]*)\".*/\1/" | sed 's/\\"/"/g'
}

cleanup_repo_ref_duplicates() {
  # args: repo ref keep_id
  repo="$1"
  ref="$2"
  keep_id="$3"
  removed="0"

  for d in "${CORES_DIR}"/*; do
    [ -d "${d}" ] || continue
    id="$(basename "${d}")"
    [ "${id}" = "${keep_id}" ] && continue

    mp="${d}/meta.json"
    [ -f "${mp}" ] || continue

    meta_repo="$(read_meta_string "${mp}" "repo")"
    meta_ref="$(read_meta_string "${mp}" "ref")"
    [ "${meta_repo}" = "${repo}" ] || continue
    [ "${meta_ref}" = "${ref}" ] || continue

    rm -rf "${d}" 2>/dev/null || true
    removed=$((removed + 1))
    log "prune old core: keep=${keep_id} removed=${id} repo=${repo} ref=${ref}"
  done

  printf '%s' "${removed}"
}

read_version_from_globals() {
  # $1 = core dir (danmu_api)
  g="$1/configs/globals.js"
  if [ -f "$g" ]; then
    # VERSION: '1.10.2'
    v="$(grep -m1 -E "VERSION[[:space:]]*:" "$g" 2>/dev/null | sed -E "s/.*VERSION[[:space:]]*:[[:space:]]*['\"]([^'\"]+)['\"].*/\1/" | head -n 1)"
    printf '%s' "$v"
    return 0
  fi
  echo ""
}

patch_core_worker_logs() {
  # Avoid self-polluting logs when the manager polls GET /api/logs.
  # Note: before token stripping, the real incoming path is usually /<token>/api/logs.
  worker="$1"
  [ -f "${worker}" ] || return 0

  if grep -q 'danmu_api_manager_patch_v2' "${worker}" 2>/dev/null; then
    return 0
  fi

  tmp="${TMP_DIR}/worker.js.patch.$$"

  if awk '
    BEGIN {
      patched_method = 0
      patched_url = 0
      patched_path = 0
      patched_ip = 0
      patched_route = 0
    }
    {
      if ($0 == "  const method = req.method;") {
        print
        print "  const shouldLogRequest = !(method === \"GET\" && (path === \"/api/logs\" || /^\\/[^/]+\\/api\\/logs$/.test(path))); // danmu_api_manager_patch_v2"
        patched_method = 1
        next
      }
      if ($0 == "  const shouldLogRequest = !(path === \"/api/logs\" && method === \"GET\"); // danmu_api_manager_patch" ||
          $0 == "  const shouldLogRequest = !(method === \"GET\" && (path === \"/api/logs\" || /^\\/[^/]+\\/api\\/logs$/.test(path))); // danmu_api_manager_patch_v2") {
        next
      }
      if ($0 == "  log(\"info\", `request url: ${JSON.stringify(url)}`);" ||
          $0 == "  shouldLogRequest && log(\"info\", `request url: ${JSON.stringify(url)}`);") {
        print "  shouldLogRequest && log(\"info\", `request url: ${JSON.stringify(url)}`);"
        patched_url = 1
        next
      }
      if ($0 == "  log(\"info\", `request path: ${path}`);" ||
          $0 == "  shouldLogRequest && log(\"info\", `request path: ${path}`);") {
        print "  shouldLogRequest && log(\"info\", `request path: ${path}`);"
        patched_path = 1
        next
      }
      if ($0 == "  log(\"info\", `client ip: ${clientIp}`);" ||
          $0 == "  shouldLogRequest && log(\"info\", `client ip: ${clientIp}`);") {
        print "  shouldLogRequest && log(\"info\", `client ip: ${clientIp}`);"
        patched_ip = 1
        next
      }
      if ($0 == "  log(\"info\", path);" ||
          $0 == "  shouldLogRequest && log(\"info\", path);") {
        print "  shouldLogRequest && log(\"info\", path);"
        patched_route = 1
        next
      }
      print
    }
    END {
      if (!(patched_method && patched_url && patched_path && patched_ip && patched_route)) {
        exit 2
      }
    }
  ' "${worker}" > "${tmp}"; then
    if ! mv -f "${tmp}" "${worker}" 2>/dev/null; then
      if ! cp -f "${tmp}" "${worker}" 2>/dev/null; then
        rm -f "${tmp}" 2>/dev/null || true
        log "failed to write patched worker.js: ${worker}"
        return 1
      fi
      rm -f "${tmp}" 2>/dev/null || true
    fi
    rm -f "${tmp}" 2>/dev/null || true
    log "patched worker.js request logging: ${worker}"
    return 0
  fi

  rm -f "${tmp}" 2>/dev/null || true
  log "skip patch worker.js request logging: ${worker}"
  return 1
}

write_meta_json() {
  # args: id repo ref sha version installedAt sizeBytes
  id="$1"; repo="$2"; ref="$3"; sha="$4"; ver="$5"; installed="$6"; sizeb="$7"
  sha_short=""
  if [ -n "${sha}" ]; then
    sha_short="$(printf '%s' "${sha}" | cut -c1-7)"
  fi

  mp="$(meta_path_for "${id}")"
  {
    echo '{'
    echo "  \"id\": \"$(json_escape "${id}")\","
    echo "  \"repo\": \"$(json_escape "${repo}")\","
    echo "  \"ref\": \"$(json_escape "${ref}")\","
    echo "  \"sha\": \"$(json_escape "${sha}")\","
    echo "  \"shaShort\": \"$(json_escape "${sha_short}")\","
    echo "  \"version\": \"$(json_escape "${ver}")\","
    echo "  \"installedAt\": \"$(json_escape "${installed}")\","
    echo "  \"sizeBytes\": ${sizeb}"
    echo '}'
  } > "${mp}" 2>/dev/null || true
}

ensure_symlink_layout() {
  # Module path must point to PERSIST/core (which points to active core)
  if [ -d "${MODDIR}" ]; then
    rm -rf "${MODDIR}/app/danmu_api" 2>/dev/null || true
    ln -s "${CORE_LINK}" "${MODDIR}/app/danmu_api" 2>/dev/null || true
  fi
}

ensure_core_config_link() {
  # Ensure each core root has: <core-id>/config -> /data/adb/danmu_api_server/config
  # This fixes Web UI config writes when core code is loaded via symlink (Node resolves realpath).
  cid="$1"
  [ -n "${cid}" ] || return 0

  root="${CORES_DIR}/${cid}"
  [ -d "${root}" ] || return 0

  mkdir -p "${CFG_DIR}" 2>/dev/null || true

  # If there is a legacy per-core config directory, back it up once (no data loss),
  # and try to migrate into the global config if the global .env is missing.
  if [ -e "${root}/config" ] && [ ! -L "${root}/config" ]; then
    bk="${root}/config.bak.$(date +%s)"
    mv "${root}/config" "${bk}" 2>/dev/null || rm -rf "${root}/config" 2>/dev/null || true
    log "migrating legacy per-core config -> ${bk}"

    if [ ! -f "${CFG_DIR}/.env" ] && [ -f "${bk}/.env" ]; then
      cp -f "${bk}/.env" "${CFG_DIR}/.env" 2>/dev/null || true
    fi

  fi

  # Seed global .env if missing (do NOT overwrite user config)
  if [ ! -f "${CFG_DIR}/.env" ] && [ -f "${MODDIR}/defaults/config/.env.example" ]; then
    cp -f "${MODDIR}/defaults/config/.env.example" "${CFG_DIR}/.env" 2>/dev/null || true
    chmod 600 "${CFG_DIR}/.env" 2>/dev/null || true
  fi

  # Keep a single source of truth
  rm -f "${CFG_DIR}/config.yaml" 2>/dev/null || true

  # Re-link (safe: rm symlink only)
  rm -rf "${root}/config" 2>/dev/null || true
  ln -s "${CFG_DIR}" "${root}/config" 2>/dev/null || true
}

ensure_core_node_modules_link() {
  # Ensure each core code dir has node_modules near its realpath.
  # Node resolves symlinked core files to /data/adb/danmu_api_server/cores/<id>/danmu_api,
  # so package imports such as node-fetch must also be visible from that realpath.
  cid="$1"
  [ -n "${cid}" ] || return 0

  root="$(core_dir_for "${cid}")"
  [ -d "${root}" ] || return 0

  # 优先使用持久化依赖目录（按 dependencyId），否则回退到模块内置 node_modules
  deps="${MODULE_NODE_MODULES:-${MODDIR}/app/node_modules}"
  if [ -n "${cid}" ]; then
    mp="$(meta_path_for "${cid}")"
    dep_id="$(read_meta_string "${mp}" "dependencyId" 2>/dev/null || true)"
    if [ -n "${dep_id}" ] && [ -d "${DEPS_DIR}/${dep_id}/node_modules" ]; then
      deps="${DEPS_DIR}/${dep_id}/node_modules"
    fi
  fi

  if [ ! -d "${deps}" ]; then
    log "module node_modules missing: ${deps}"
    return 0
  fi

  link="${root}/node_modules"
  if [ -L "${link}" ]; then
    current="$(readlink "${link}" 2>/dev/null || true)"
    if [ "${current}" = "${deps}" ]; then
      return 0
    fi
    rm -f "${link}" 2>/dev/null || true
  elif [ -e "${link}" ]; then
    # Do not delete a custom core's real node_modules; move it aside once.
    bk="${root}/node_modules.bak.$(date +%s)"
    if mv "${link}" "${bk}" 2>/dev/null; then
      log "backed up per-core node_modules -> ${bk}"
    else
      log "cannot replace per-core node_modules: ${link}"
      return 0
    fi
  fi

  ln -s "${deps}" "${link}" 2>/dev/null || true
}

compute_dependency_id() {
  # 以核心根 package-lock.json 的 sha256 作为依赖指纹（无锁文件时退化为 package.json）
  local src="$1"
  local id_file=""
  if [ -f "${src}/package-lock.json" ]; then
    id_file="${src}/package-lock.json"
  elif [ -f "${src}/package.json" ]; then
    id_file="${src}/package.json"
  fi
  [ -n "${id_file}" ] || { echo "unknown"; return 1; }
  if have_cmd sha256sum; then
    sha256sum "${id_file}" 2>/dev/null | awk '{print substr($1,1,16)}'
  elif [ -n "${BB}" ] && "${BB}" sha256sum --help >/dev/null 2>&1; then
    "${BB}" sha256sum "${id_file}" 2>/dev/null | awk '{print substr($1,1,16)}'
  else
    echo "unknown"
    return 1
  fi
}

core_dependency_fingerprint() {
  # 输出核心依赖指纹（64 位 sha256）。算法与 runtime-packs 发布端一致：
  # sha256(canonical_json(sorted(dependencies + optionalDependencies)))，其中
  # canonical_json 为 JSON.stringify 的 sort_keys + separators=(",",":")，
  # 与 App 的 RuntimeDependencyPackProtocol.dependencyFingerprint 完全等价。
  # 输出: {"result":"ok","core":"<id>","fingerprint":"<sha256>"}
  cid="$1"
  core_root="$(core_dir_for "${cid}")"
  pkg_file="${core_root}/package.json"
  [ -f "${pkg_file}" ] || {
    echo '{"result":"error","error":"package_json_missing"}'
    return 1
  }

  if have_cmd node; then
    node_bin="node"
  elif [ -x "${MODDIR}/node/bin/node" ]; then
    node_bin="${MODDIR}/node/bin/node"
  elif [ -x "${PERSIST}/node/bin/node" ]; then
    node_bin="${PERSIST}/node/bin/node"
  else
    node_bin=""
  fi

  fingerprint=""
  if [ -n "${node_bin}" ]; then
    fingerprint="$("${node_bin}" -e '
      const fs = require("node:fs");
      const crypto = require("node:crypto");
      const pkg = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
      const deps = { ...(pkg.dependencies || {}), ...(pkg.optionalDependencies || {}) };
      const canonical = JSON.stringify(deps, Object.keys(deps).sort());
      process.stdout.write(crypto.createHash("sha256").update(canonical, "utf8").digest("hex"));
    ' "${pkg_file}" 2>/dev/null || true)"
  fi

  if [ -z "${fingerprint}" ]; then
    echo '{"result":"error","error":"fingerprint_compute_failed"}'
    return 1
  fi

  printf '{"result":"ok","core":"%s","fingerprint":"%s"}\n' "$(json_escape "${cid}")" "${fingerprint}"
  return 0
}

resolve_runtime_deps_js() {
  # 显式指定的检查器路径必须真实存在；否则 fail closed
  if [ -n "${RUNTIME_DEPS_JS:-}" ]; then
    if [ -f "${RUNTIME_DEPS_JS}" ]; then
      echo "${RUNTIME_DEPS_JS}"
      return 0
    fi
    return 1
  fi
  if [ -f "${MODDIR}/app/runtime-deps.mjs" ]; then
    echo "${MODDIR}/app/runtime-deps.mjs"
    return 0
  fi
  return 1
}

check_core_dependencies() {
  # 在激活/安装前检查核心依赖。healthy=0；缺失/不兼容=1 并输出 JSON 到 stdout。
  # 输出: {"result":"dependency_repair_required","core":"<id>","missing":[...],"incompatible":[...],"conditional":[...]}
  cid="$1"
  core_root="$(core_dir_for "${cid}")"
  [ -d "${core_root}" ] || { echo '{"result":"error","error":"core_not_found"}'; return 1; }

  runtime_deps="$(resolve_runtime_deps_js)" || {
    echo '{"result":"error","error":"runtime_deps_missing"}'
    return 1
  }

  node_bin="${TEST_NODE_BIN:-}"
  if [ -z "${node_bin}" ]; then
    if have_cmd node; then
      node_bin="node"
    elif [ -x "${MODDIR}/node/bin/node" ]; then
      node_bin="${MODDIR}/node/bin/node"
    elif [ -x "${PERSIST}/node/bin/node" ]; then
      node_bin="${PERSIST}/node/bin/node"
    fi
  fi
  if [ -z "${node_bin}" ]; then
    # 无 node 时无法精确检查；不阻断（no_node 变体由用户外部环境提供 node）
    echo '{"result":"ok","skipped":"no_node_binary"}'
    return 0
  fi

  # 先确保 node_modules 链接就位，再检查
  ensure_core_node_modules_link "${cid}" || true
  nm_dir=""
  if [ -d "${core_root}/node_modules" ]; then
    nm_dir="$(readlink "${core_root}/node_modules" 2>/dev/null || echo "${core_root}/node_modules")"
  fi
  [ -n "${nm_dir}" ] || nm_dir="${MODULE_NODE_MODULES:-${MODDIR}/app/node_modules}"

  env_file=""
  if [ -f "${CFG_DIR}/.env" ]; then
    env_file="${CFG_DIR}/.env"
  fi

  if [ -n "${env_file}" ]; then
    output="$("${node_bin}" "${runtime_deps}" inspect \
      --core-root "${core_root}" \
      --node-modules "${nm_dir}" \
      --env-file "${env_file}" 2>/dev/null || true)"
  else
    output="$("${node_bin}" "${runtime_deps}" inspect \
      --core-root "${core_root}" \
      --node-modules "${nm_dir}" 2>/dev/null || true)"
  fi
  if [ -z "${output}" ]; then
    # 检查器异常/不可解析：fail closed，拒绝激活（保持可用性需显式降级）
    echo '{"result":"dependency_repair_required","core":"'$(json_escape "${cid}")'","missing":[],"incompatible":[],"conditional":[],"skipped":"inspect_failed"}'
    return "${DEP_EXIT_CODE}"
  fi

  blocked="$(printf '%s' "${output}" | grep -o '"blocked":true' || true)"
  if [ -n "${blocked}" ]; then
    missing="$(printf '%s' "${output}" | grep -o '"missing":\[[^]]*\]' | head -n 1)"
    incompatible="$(printf '%s' "${output}" | grep -o '"incompatible":\[[^]]*\]' | head -n 1)"
    conditional="$(printf '%s' "${output}" | grep -o '"conditional":\[[^]]*\]' | head -n 1)"
    printf '{"result":"dependency_repair_required","core":"%s",%s,%s,%s}\n' \
      "$(json_escape "${cid}")" \
      "${missing:-"\"missing\":[]"}" \
      "${incompatible:-"\"incompatible\":[]"}" \
      "${conditional:-"\"conditional\":[]"}"
    return "${DEP_EXIT_CODE}"
  fi

  echo '{"result":"ok"}'
  return 0
}

# 依赖阻断固定退出码（Manager 可识别）
DEP_EXIT_CODE=78

write_meta_dependency_id() {
  # 使用模块实际 Node 原子更新 meta.json，避免依赖 Android 上未必存在的 python/jq。
  mp="$1"
  dep_id="$2"
  node_bin="$3"
  tmp_meta="${mp}.tmp.$$"

  [ -f "${mp}" ] || return 1
  if ! "${node_bin}" -e '
    const fs = require("node:fs");
    const [src, dst, dependencyId] = process.argv.slice(1);
    const data = JSON.parse(fs.readFileSync(src, "utf8"));
    data.dependencyId = dependencyId;
    fs.writeFileSync(dst, `${JSON.stringify(data, null, 2)}\n`);
  ' "${mp}" "${tmp_meta}" "${dep_id}" 2>/dev/null; then
    rm -f "${tmp_meta}" 2>/dev/null || true
    return 1
  fi
  if ! mv -f "${tmp_meta}" "${mp}" 2>/dev/null; then
    rm -f "${tmp_meta}" 2>/dev/null || true
    return 1
  fi
  return 0
}

deps_install() {
  # deps install <core-id> <source-node-modules> <dependency-id>
  # source -> 同文件系统 staging -> 校验 -> live -> backup，失败恢复旧目录
  cid="$1"
  source_nm="$2"
  dep_id="$3"

  case "${dep_id}" in
    ''|*[!A-Za-z0-9._-]*) echo '{"result":"error","error":"invalid_dependency_id"}'; return 2 ;;
  esac

  [ -d "${source_nm}" ] || { echo '{"result":"error","error":"source_missing"}'; return 1; }

  core_root="$(core_dir_for "${cid}")"
  [ -d "${core_root}" ] || { echo '{"result":"error","error":"core_not_found"}'; return 1; }

  mkdir -p "${DEPS_DIR}" 2>/dev/null || true

  target="${DEPS_DIR}/${dep_id}"
  staging="${DEPS_DIR}/.staging-${dep_id}-$$-$(date +%s)"
  backup="${DEPS_DIR}/.backup-${dep_id}-$$-$(date +%s)"

  # 1) staging：在同一文件系统创建标准 deps/<id>/node_modules 布局
  mkdir -p "${staging}" 2>/dev/null || {
    echo '{"result":"error","error":"staging_failed"}'
    return 1
  }
  if ! cp -a "${source_nm}" "${staging}/node_modules" 2>/dev/null; then
    rm -rf "${staging}" 2>/dev/null || true
    echo '{"result":"error","error":"staging_failed"}'
    return 1
  fi

  # 2) 校验 staging（用 runtime-deps 检查核心依赖是否满足）
  runtime_deps="$(resolve_runtime_deps_js)" || {
    rm -rf "${staging}" 2>/dev/null || true
    echo '{"result":"error","error":"runtime_deps_missing"}'
    return 1
  }
  node_bin=""
  if have_cmd node; then
    node_bin="node"
  elif [ -x "${MODDIR}/node/bin/node" ]; then
    node_bin="${MODDIR}/node/bin/node"
  elif [ -x "${PERSIST}/node/bin/node" ]; then
    node_bin="${PERSIST}/node/bin/node"
  fi

  env_file=""
  if [ -f "${CFG_DIR}/.env" ]; then
    env_file="${CFG_DIR}/.env"
  fi

  if [ -z "${node_bin}" ]; then
    rm -rf "${staging}" 2>/dev/null || true
    echo '{"result":"error","error":"node_binary_missing"}'
    return 1
  fi

  env_file=""
  if [ -f "${CFG_DIR}/.env" ]; then
    env_file="${CFG_DIR}/.env"
  fi

  if [ -n "${env_file}" ]; then
    inspect_out="$("${node_bin}" -- "${runtime_deps}" inspect \
      --core-root "${core_root}" \
      --node-modules "${staging}/node_modules" \
      --env-file "${env_file}" 2>/dev/null || true)"
  else
    inspect_out="$("${node_bin}" -- "${runtime_deps}" inspect \
      --core-root "${core_root}" \
      --node-modules "${staging}/node_modules" 2>/dev/null || true)"
  fi
  if [ -z "${inspect_out}" ]; then
    rm -rf "${staging}" 2>/dev/null || true
    echo '{"result":"error","error":"dependency_inspect_failed"}'
    return 1
  fi
  if printf '%s' "${inspect_out}" | grep -q '"blocked":true'; then
    rm -rf "${staging}" 2>/dev/null || true
    printf '{"result":"error","error":"invalid_candidate","detail":%s}\n' "${inspect_out}"
    return 1
  fi

  # 2b) staging 可导入性冒烟（node 存在且未显式禁用时）：版本正确但文件损坏的
  # 候选会被真实 import 拒绝，避免损坏包进入 live 并删掉旧备份。
  if [ "${CORE_DANMU_SMOKE_OFF:-0}" != "1" ]; then
    smoke_status=0
    "${node_bin}" -- "${runtime_deps}" smoke \
      --core-root "${core_root}" \
      --node-modules "${staging}/node_modules" >/dev/null 2>&1 || smoke_status=$?
    if [ "${smoke_status}" -ne 0 ]; then
      rm -rf "${staging}" 2>/dev/null || true
      printf '{"result":"error","error":"smoke_failed","detail":"worker import 失败（exit %s）"}\n' "${smoke_status}"
      return 1
    fi
  fi

  # 3) 原子替换：先备份同指纹 live，再 staging -> live
  mp="$(meta_path_for "${cid}")"
  if [ ! -f "${mp}" ]; then
    rm -rf "${staging}" 2>/dev/null || true
    echo '{"result":"error","error":"core_meta_missing"}'
    return 1
  fi
  meta_backup="${TMP_DIR}/meta-${cid}-$$.bak"
  if ! cp -f "${mp}" "${meta_backup}" 2>/dev/null; then
    rm -rf "${staging}" 2>/dev/null || true
    echo '{"result":"error","error":"meta_backup_failed"}'
    return 1
  fi

  if [ -e "${target}" ]; then
    if ! mv "${target}" "${backup}" 2>/dev/null; then
      rm -f "${meta_backup}" 2>/dev/null || true
      rm -rf "${staging}" 2>/dev/null || true
      echo '{"result":"error","error":"backup_failed"}'
      return 1
    fi
  fi

  if ! mv "${staging}" "${target}" 2>/dev/null; then
    # 回滚依赖 live
    rm -rf "${target}" 2>/dev/null || true
    if [ -e "${backup}" ]; then
      mv "${backup}" "${target}" 2>/dev/null || true
    fi
    rm -f "${meta_backup}" 2>/dev/null || true
    echo '{"result":"error","error":"install_failed"}'
    return 1
  fi

  # 4) 元数据和核心链接必须与 live 依赖一起提交；任一步失败都回滚
  if ! write_meta_dependency_id "${mp}" "${dep_id}" "${node_bin}"; then
    rm -rf "${target}" 2>/dev/null || true
    if [ -e "${backup}" ]; then mv "${backup}" "${target}" 2>/dev/null || true; fi
    mv -f "${meta_backup}" "${mp}" 2>/dev/null || true
    echo '{"result":"error","error":"meta_update_failed"}'
    return 1
  fi

  ensure_core_node_modules_link "${cid}" || true
  link_target="$(readlink "${core_root}/node_modules" 2>/dev/null || true)"
  if [ "${link_target}" != "${target}/node_modules" ]; then
    rm -rf "${target}" 2>/dev/null || true
    if [ -e "${backup}" ]; then mv "${backup}" "${target}" 2>/dev/null || true; fi
    mv -f "${meta_backup}" "${mp}" 2>/dev/null || true
    ensure_core_node_modules_link "${cid}" || true
    echo '{"result":"error","error":"symlink_update_failed"}'
    return 1
  fi

  rm -rf "${backup}" "${meta_backup}" 2>/dev/null || true
  printf '{"result":"ok","dependencyId":"%s"}\n' "${dep_id}"
  return 0
}


activate_core() {
  id="$1"
  cdir="$(core_dir_for "${id}")"
  if [ ! -d "${cdir}" ] || [ ! -f "${cdir}/worker.js" ]; then
    echo '{"result":"error","error":"core_not_found"}'
    return 1
  fi

  # 依赖门禁：缺失/不兼容依赖时拒绝激活，保留现有 CORE_LINK 与运行中的服务。
  # 必须透传 78；core_not_found/runtime_deps_missing 等普通错误保持 exit 1。
  set +e
  check_core_dependencies "${id}"
  dependency_status=$?
  set -e
  if [ "${dependency_status}" -ne 0 ]; then
    return "${dependency_status}"
  fi

  patch_core_worker_logs "${cdir}/worker.js" || true

  running_before=0
  if is_running; then running_before=1; fi

  # stop before switch (avoid partial reads)
  if [ "${running_before}" -eq 1 ] && [ -x "${CTRL}" ]; then
    "${CTRL}" stop >/dev/null 2>&1 || true
  fi

  rm -f "${CORE_LINK}" 2>/dev/null || true
  ln -s "${cdir}" "${CORE_LINK}" 2>/dev/null || true
  echo "${id}" > "${ACTIVE_FILE}" 2>/dev/null || true

  ensure_core_config_link "${id}" || true
  ensure_core_node_modules_link "${id}" || true

  ensure_symlink_layout

  if [ "${running_before}" -eq 1 ] && [ -x "${CTRL}" ]; then
    "${CTRL}" start >/dev/null 2>&1 || true
  fi

  return 0
}

list_cores_json() {
  active="$(read_active_id)"
  printf '{"activeCoreId":"%s","cores":[' "$(json_escape "${active}")"

  first=1
  for d in "${CORES_DIR}"/*; do
    [ -d "$d" ] || continue
    mp="$d/meta.json"
    if [ ! -f "$mp" ]; then
      continue
    fi
    if [ "${first}" -eq 1 ]; then
      first=0
    else
      printf ','
    fi
    # meta.json is already JSON object
    cat "$mp" 2>/dev/null || true
  done

  echo ']}'
}

status_json() {
  # module version
  mver=""
  if [ -f "${MODDIR}/module.prop" ]; then
    mver="$(grep -m1 '^version=' "${MODDIR}/module.prop" 2>/dev/null | cut -d= -f2- || true)"
  fi

  enabled=false
  if module_enabled; then enabled=true; fi

  running=false
  pid=""
  if is_running; then
    running=true
    pid="$(cat "${PIDFILE}" 2>/dev/null || true)"
  fi

  autostart="$(autostart_status)"
  active="$(read_active_id)"

  # read active meta if possible
  active_meta=""
  if [ -n "${active}" ]; then
    mp="$(meta_path_for "${active}")"
    if [ -f "${mp}" ]; then
      active_meta="$(cat "${mp}" 2>/dev/null || true)"
    fi
  fi

  printf '{'
  printf '"module":{"id":"%s","enabled":%s,"version":"%s"},' "$(json_escape "${MODID}")" "${enabled}" "$(json_escape "${mver}")"
  printf '"service":{"running":%s,"pid":"%s"},' "${running}" "$(json_escape "${pid}")"
  printf '"autostart":"%s",' "$(json_escape "${autostart}")"
  printf '"activeCoreId":"%s",' "$(json_escape "${active}")"
  if [ -n "${active_meta}" ]; then
    printf '"activeCore":%s' "${active_meta}"
  else
    printf '"activeCore":null'
  fi
  printf '}'
  echo
}

download_file() {
  url="$1"
  out="$2"
  rm -f "$out" 2>/dev/null || true

  if have_cmd curl; then
    curl -fL --retry 3 --connect-timeout 10 --max-time 600 "$url" -o "$out" >/dev/null 2>&1 && return 0
  fi

  if have_cmd wget; then
    wget -O "$out" "$url" >/dev/null 2>&1 && return 0
  fi

  if [ -n "${BB}" ] && "${BB}" wget --help >/dev/null 2>&1; then
    "${BB}" wget -O "$out" "$url" >/dev/null 2>&1 && return 0
    "${BB}" wget -O "$out" "$url" >/dev/null 2>&1 && return 0
  fi

  return 1
}

unpack_archive_to() {
  archive="$1"
  outdir="$2"
  rm -rf "$outdir" 2>/dev/null || true
  mkdir -p "$outdir" 2>/dev/null || true

  case "$archive" in
    *.tar.gz|*.tgz)
      if have_cmd tar; then
        tar -xzf "$archive" -C "$outdir" >/dev/null 2>&1 && return 0
      fi

      if [ -n "${BB}" ] && "${BB}" tar --help >/dev/null 2>&1; then
        "${BB}" tar -xzf "$archive" -C "$outdir" >/dev/null 2>&1 && return 0
      fi
      ;;
    *.zip)
      if have_cmd unzip; then
        unzip -q "$archive" -d "$outdir" >/dev/null 2>&1 && return 0
      fi

      if [ -n "${BB}" ] && "${BB}" unzip --help >/dev/null 2>&1; then
        "${BB}" unzip -q "$archive" -d "$outdir" >/dev/null 2>&1 && return 0
      fi
      ;;
  esac

  return 1
}

resolve_sha() {
  repo="$1"
  ref="$2"

  case "$ref" in
    '' )
      printf '%s' ""
      return 0
      ;;
    * )
      if [ "${#ref}" -gt 200 ]; then
        printf '%s' ""
        return 0
      fi
      ;;
  esac

  url="https://api.github.com/repos/${repo}/commits/${ref}"
  url="$(apply_proxy_url "${url}")"

  if ! have_cmd curl; then
    echo ""
    return 0
  fi

  if [ -n "${DANMU_GH_TOKEN:-}" ]; then
    out="$(curl -fsSL -H 'User-Agent: danmu_api_manager' -H "Authorization: token ${DANMU_GH_TOKEN}" "$url" 2>/dev/null || true)"
  else
    out="$(curl -fsSL -H 'User-Agent: danmu_api_manager' "$url" 2>/dev/null || true)"
  fi
  sha="$(printf '%s' "$out" | grep -m1 -oE '"sha"[[:space:]]*:[[:space:]]*"[0-9a-f]+' | head -n1 | sed -E 's/.*"sha"[[:space:]]*:[[:space:]]*"([0-9a-f]+).*/\1/' || true)"
  printf '%s' "${sha}"
}

emit_install_activation_failure() {
  activation_json="$1"
  if [ -n "${activation_json}" ]; then
    # activate_core 的失败载荷均为单行 JSON；附加安装状态但保留顶层 result/error 契约。
    printf '%s\n' "${activation_json}" | sed '$ s/}$/,"action":"installed","activated":false}/'
  else
    echo '{"result":"error","error":"activation_failed","action":"installed","activated":false}'
  fi
}

install_core() {
  repo_raw="$1"
  ref="$2"

  load_download_config

  repo="$(normalize_repo "$repo_raw")"
  case "$repo" in
    */*) : ;;
    *) echo "error=invalid_repo"; return 2 ;;
  esac

  sha=""
  if [ "${CORE_DANMU_TEST_MODE}" != "1" ] || [ -z "${CORE_DANMU_TEST_ARCHIVE:-}" ]; then
    sha="$(resolve_sha "$repo" "$ref")"
  fi
  zip_ref="$ref"
  if [ -n "${sha}" ]; then
    zip_ref="$sha"
  fi
  sha_short=""
  if [ -n "${sha}" ]; then sha_short="$(printf '%s' "${sha}" | cut -c1-7)"; fi

  ref_is_commit_like=0
  case "$ref" in
    '' ) ;;
    *[!0-9a-fA-F]* ) ;;
    * )
      if [ "${#ref}" -ge 7 ] && [ "${#ref}" -le 40 ]; then
        ref_is_commit_like=1
      fi
      ;;
  esac

  id="$(sanitize_id "${repo}_${ref}_${sha_short}")"
  [ -n "${id}" ] || id="$(sanitize_id "${repo}_${ref}")"

  dest_root="${CORES_DIR}/${id}"
  dest_core="${dest_root}/danmu_api"

  # If already installed, refresh mutable metadata and activate.
  # For branch/tag installs, remote content may have advanced even if repo/ref is unchanged,
  # so do not trust stale meta.json blindly.
  if [ -d "${dest_core}" ] && [ -f "${dest_core}/worker.js" ]; then
    if [ "${ref_is_commit_like}" -eq 1 ]; then
      set +e
      activation_output="$(activate_core "${id}" 2>&1)"
      activation_status=$?
      set -e
      if [ "${activation_status}" -ne 0 ]; then
        emit_install_activation_failure "${activation_output}"
        return "${activation_status}"
      fi
      removed_old="$(cleanup_repo_ref_duplicates "${repo}" "${ref}" "${id}")"
      mp="$(meta_path_for "${id}")"
      if [ -f "$mp" ]; then
        printf '{"result":"ok","action":"already_installed","activated":true,"removedOldCount":%s,"core":' "${removed_old}"
        cat "$mp" 2>/dev/null || echo '{}'
        echo '}'
        return 0
      fi
      printf '{"result":"ok","action":"already_installed","activated":true,"removedOldCount":%s}\n' "${removed_old}"
      return 0
    fi

    rm -rf "${dest_root}" 2>/dev/null || true
    mkdir -p "${dest_root}" 2>/dev/null || true
  fi

  mkdir -p "${dest_root}" 2>/dev/null || true

  zipf="${TMP_DIR}/${id}.zip"
  exdir="${TMP_DIR}/extract_${id}"
  tarf="${TMP_DIR}/${id}.tar.gz"
  raw_tar_url="https://github.com/${repo}/archive/${zip_ref}.tar.gz"
  raw_tar_url="$(apply_proxy_url "${raw_tar_url}")"
  zip_url="https://codeload.github.com/${repo}/zip/${zip_ref}"
  zip_url="$(apply_proxy_url "${zip_url}")"

  log "install begin: repo=${repo} ref=${ref} sha=${sha}"

  downloaded_archive=""
  if [ "${CORE_DANMU_TEST_MODE}" = "1" ] && [ -n "${CORE_DANMU_TEST_ARCHIVE:-}" ]; then
    [ -f "${CORE_DANMU_TEST_ARCHIVE}" ] || {
      echo '{"result":"error","error":"test_archive_missing"}'
      return 1
    }
    downloaded_archive="${CORE_DANMU_TEST_ARCHIVE}"
  elif download_file "$raw_tar_url" "$tarf"; then
    downloaded_archive="$tarf"
  elif download_file "$zip_url" "$zipf"; then
    downloaded_archive="$zipf"
  else
    log "download failed: tar=${raw_tar_url} zip=${zip_url}"
    echo '{"result":"error","error":"download_failed"}'
    return 1
  fi

  if ! unpack_archive_to "$downloaded_archive" "$exdir"; then
    log "unpack failed: $downloaded_archive"
    echo '{"result":"error","error":"unpack_failed"}'
    return 1
  fi

  worker_path="$(find "$exdir" -type f -name worker.js 2>/dev/null | grep '/danmu_api/worker.js$' | head -n 1 || true)"
  if [ -z "${worker_path}" ]; then
    log "worker.js not found in extracted zip"
    echo '{"result":"error","error":"core_not_found"}'
    return 1
  fi

  src_dir="$(dirname "${worker_path}")"

  # staging：先完整复制到唯一 staging 目录（包含核心根 package.json/package-lock.json），
  # 校验通过后再原子 rename 到最终位置，避免 rm 正在使用的 dest。
  staging_root="${TMP_DIR}/core_staging_${id}_$(date +%s)"
  mkdir -p "${staging_root}" 2>/dev/null || true
  if ! cp -a "${src_dir}/." "${staging_root}/danmu_api/" 2>/dev/null; then
    mkdir -p "${staging_root}/danmu_api" 2>/dev/null || true
    cp -a "${src_dir}/." "${staging_root}/danmu_api/" 2>/dev/null || true
  fi

  # 保留归档根依赖声明。GitHub archive 布局为 <repo>-<ref>/danmu_api/worker.js，
  # 因此归档根就是 src_dir 的父目录，而不是 exdir 本身。
  archive_root="$(dirname "${src_dir}")"
  for root_file in package.json package-lock.json; do
    f="${archive_root}/${root_file}"
    if [ -f "$f" ]; then
      cp -f "$f" "${staging_root}/${root_file}" 2>/dev/null || {
        rm -rf "${staging_root}" 2>/dev/null || true
        echo '{"result":"error","error":"manifest_copy_failed"}'
        return 1
      }
      cp -f "$f" "${staging_root}/danmu_api/${root_file}" 2>/dev/null || {
        rm -rf "${staging_root}" 2>/dev/null || true
        echo '{"result":"error","error":"manifest_copy_failed"}'
        return 1
      }
    fi
  done

  # package.json 是运行时门禁与 dependencyFingerprint 的权威输入，缺失必须 fail closed。
  if [ ! -f "${staging_root}/package.json" ] || [ ! -f "${staging_root}/danmu_api/package.json" ]; then
    log "core package.json not found at archive root: ${archive_root}"
    rm -rf "${staging_root}" 2>/dev/null || true
    echo '{"result":"error","error":"core_manifest_missing"}'
    return 1
  fi

  if [ ! -f "${staging_root}/danmu_api/worker.js" ]; then
    log "copy failed"
    rm -rf "${staging_root}" 2>/dev/null || true
    echo '{"result":"error","error":"copy_failed"}'
    return 1
  fi

  # staging 内容完整后再原子落位；实际依赖门禁在 activate_core 中执行。
  patch_core_worker_logs "${staging_root}/danmu_api/worker.js" || true

  # 原子落位：staging -> dest
  if [ -d "${dest_core}" ]; then
    rm -rf "${dest_root}.old.$(date +%s)" 2>/dev/null || true
  fi
  if ! mv "${staging_root}/danmu_api" "${dest_core}" 2>/dev/null; then
    log "staging move failed"
    rm -rf "${staging_root}" 2>/dev/null || true
    echo '{"result":"error","error":"copy_failed"}'
    return 1
  fi
  # 根锁文件
  for root_file in package.json package-lock.json; do
    if [ -f "${staging_root}/${root_file}" ]; then
      cp -f "${staging_root}/${root_file}" "${dest_root}/${root_file}" 2>/dev/null || true
    fi
  done
  rm -rf "${staging_root}" 2>/dev/null || true

  if [ ! -f "${dest_core}/worker.js" ]; then
    log "copy failed"
    rm -rf "${dest_root}" 2>/dev/null || true
    echo '{"result":"error","error":"copy_failed"}'
    return 1
  fi

  patch_core_worker_logs "${dest_core}/worker.js" || true
  ensure_core_node_modules_link "${id}" || true

  ver="$(read_version_from_globals "${dest_core}")"
  installed="$(date '+%F %T')"
  sizeb="0"
  if have_cmd du; then
    sizeb="$(du -sk "${dest_core}" 2>/dev/null | awk '{print $1*1024}' || echo 0)"
  fi

  write_meta_json "${id}" "${repo}" "${ref}" "${sha}" "${ver}" "${installed}" "${sizeb}"

  rm -f "${zipf}" "${tarf}" 2>/dev/null || true
  rm -rf "${exdir}" 2>/dev/null || true

  set +e
  activation_output="$(activate_core "${id}" 2>&1)"
  activation_status=$?
  set -e
  if [ "${activation_status}" -ne 0 ]; then
    emit_install_activation_failure "${activation_output}"
    return "${activation_status}"
  fi
  removed_old="$(cleanup_repo_ref_duplicates "${repo}" "${ref}" "${id}")"

  mp="$(meta_path_for "${id}")"
  printf '{"result":"ok","action":"installed","activated":true,"removedOldCount":%s,"core":' "${removed_old}"
  cat "$mp" 2>/dev/null || echo '{}'
  echo '}'
  return 0
}

delete_core() {
  id="$1"
  active="$(read_active_id)"
  if [ "${id}" = "${active}" ]; then
    # stop service first
    if [ -x "${CTRL}" ]; then
      "${CTRL}" stop >/dev/null 2>&1 || true
    fi
    rm -f "${ACTIVE_FILE}" 2>/dev/null || true
    rm -f "${CORE_LINK}" 2>/dev/null || true
  fi
  rm -rf "${CORES_DIR}/${id}" 2>/dev/null || true
  ensure_symlink_layout
  echo '{"result":"ok"}'
}

ensure_seed() {
  load_download_config

  # If we already have an active core + link, just ensure layout + config link
  active="$(read_active_id)"
  if [ -n "${active}" ] && [ -L "${CORE_LINK}" ]; then
    patch_core_worker_logs "$(core_dir_for "${active}")/worker.js" || true
    ensure_core_config_link "${active}" || true
    ensure_core_node_modules_link "${active}" || true
    ensure_symlink_layout
    return 0
  fi

  # If cores exist, activate the first one
  for d in "${CORES_DIR}"/*; do
    [ -d "$d" ] || continue
    id="$(basename "$d")"
    if [ -f "$d/danmu_api/worker.js" ]; then
      activate_core "$id" >/dev/null 2>&1 || true
      return 0
    fi
  done

  # No bundled core: auto download default core from GitHub
  log "no core found, auto downloading: ${DEFAULT_CORE_REPO} ${DEFAULT_CORE_REF} (mode=${GH_MODE})"
  install_core "${DEFAULT_CORE_REPO}" "${DEFAULT_CORE_REF}" >/dev/null 2>&1 || \
    log "auto download failed"
  ensure_symlink_layout
  return 0
}

logs_list_json() {
  printf '{"dir":"%s","files":[' "$(json_escape "${LOGDIR}")"
  first=1
  for f in "${LOGDIR}"/*.log "${LOGDIR}"/*.log.*; do
    [ -f "$f" ] || continue
    name="$(basename "$f")"
    size="0"
    size="$(wc -c < "$f" 2>/dev/null || echo 0)"
    mtime="$(date -r "$f" '+%F %T' 2>/dev/null || echo '')"
    if [ "${first}" -eq 1 ]; then first=0; else printf ','; fi
    printf '{"name":"%s","path":"%s","sizeBytes":%s,"modifiedAt":"%s"}' \
      "$(json_escape "${name}")" "$(json_escape "${f}")" "${size}" "$(json_escape "${mtime}")"
  done
  echo ']}'
}

logs_clear_json() {
  # Truncate (safer than delete if file is being written)
  for f in "${LOGDIR}"/*.log "${LOGDIR}"/*.log.*; do
    [ -f "$f" ] || continue
    : > "$f" 2>/dev/null || true
  done
  echo '{"result":"ok"}'
}

usage() {
  cat <<EOF
Usage:
  $0 ensure
  $0 status [--json]
  $0 autostart {on|off|status}
  $0 core list [--json]
  $0 core install <owner/repo> <ref>
  $0 core activate <id>
  $0 core delete <id>
  $0 deps install <core-id> <source-node-modules> <dependency-id>
  $0 logs list [--json]
  $0 logs clear

Legacy aliases:
  $0 install <owner/repo> <ref>   (same as: core install)
EOF
}

cmd="${1:-}"
case "$cmd" in
  ensure)
    ensure_seed
    ;;

  status)
    if [ "${2:-}" = "--json" ] || [ "${2:-}" = "json" ]; then
      status_json
    else
      # key=value for humans
      if is_running; then echo "service=running"; else echo "service=stopped"; fi
      echo "autostart=$(autostart_status)"
      echo "activeCoreId=$(read_active_id)"
    fi
    ;;

  autostart)
    sub="${2:-status}"
    case "$sub" in
      status)
        echo "autostart=$(autostart_status)" ;;
      on)
        rm -f "${FLAG_AUTOSTART_NEW}" "${FLAG_AUTOSTART_OLD}" 2>/dev/null || true
        echo '{"result":"ok","autostart":"on"}' ;;
      off)
        : > "${FLAG_AUTOSTART_NEW}" 2>/dev/null || touch "${FLAG_AUTOSTART_NEW}" 2>/dev/null || true
        rm -f "${FLAG_AUTOSTART_OLD}" 2>/dev/null || true
        echo '{"result":"ok","autostart":"off"}' ;;
      *) usage; exit 2 ;;
    esac
    ;;

  core)
    sub="${2:-}"
    case "$sub" in
      list)
        if [ "${3:-}" = "--json" ] || [ "${3:-}" = "json" ] || [ -z "${3:-}" ]; then
          list_cores_json
        else
          list_cores_json
        fi
        ;;
      install)
        repo="${3:-}"; ref="${4:-}"
        if [ -z "${repo}" ] || [ -z "${ref}" ]; then usage; exit 2; fi
        install_core "${repo}" "${ref}"
        ;;
      activate)
        id="${3:-}"
        [ -n "${id}" ] || { usage; exit 2; }
        set +e
        activate_core "${id}"
        activate_status=$?
        set -e
        if [ "${activate_status}" -eq 0 ]; then
          mp="$(meta_path_for "${id}")"
          printf '{"result":"ok","activated":true,"core":'
          cat "$mp" 2>/dev/null || echo '{}'
          echo '}'
        else
          # activate_core 已输出结构化 JSON；严格透传实际退出码。
          exit "${activate_status}"
        fi
        ;;
      fingerprint)
        id="${3:-}"
        [ -n "${id}" ] || { usage; exit 2; }
        core_dependency_fingerprint "${id}"
        ;;
      delete)
        id="${3:-}"
        [ -n "${id}" ] || { usage; exit 2; }
        delete_core "${id}"
        ;;
      *) usage; exit 2 ;;
    esac
    ;;

  deps)
    sub="${2:-}"
    case "$sub" in
      install)
        cid="${3:-}"; source_nm="${4:-}"; dep_id="${5:-}"
        if [ -z "${cid}" ] || [ -z "${source_nm}" ] || [ -z "${dep_id}" ]; then usage; exit 2; fi
        deps_install "${cid}" "${source_nm}" "${dep_id}"
        ;;
      *) usage; exit 2 ;;
    esac
    ;;

  logs)
    sub="${2:-}"
    case "$sub" in
      list)
        logs_list_json
        ;;
      clear)
        logs_clear_json
        ;;
      *) usage; exit 2 ;;
    esac
    ;;

  install)
    repo="${2:-}"; ref="${3:-}"
    if [ -z "${repo}" ] || [ -z "${ref}" ]; then usage; exit 2; fi
    install_core "${repo}" "${ref}"
    ;;

  *)
    usage
    exit 2
    ;;
esac
