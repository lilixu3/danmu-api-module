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
LOGFILE="${LOGDIR}/core_manager.log"

DOWNLOAD_CONF="${PERSIST}/core_download.conf"
DEFAULT_CORE_REPO="huangxd-/danmu_api"
DEFAULT_CORE_REF="main"
GH_MODE="direct"
GH_PROXY_BASE=""

PIDFILE="${PERSIST}/danmu_api.pid"

# flags
FLAG_AUTOSTART_NEW="${PERSIST}/autostart.disabled"
FLAG_AUTOSTART_OLD="${PERSIST}/service.disabled"  # legacy

mkdir -p "${PERSIST}" "${CFG_DIR}" "${CORES_DIR}" "${TMP_DIR}" "${LOGDIR}" 2>/dev/null || true

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
  # best-effort
  echo "[danmu_api][core] $(date '+%F %T') $*" >> "${LOGFILE}" 2>/dev/null || true
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
      https://github.com/*|https://api.github.com/*|https://codeload.github.com/*)
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


activate_core() {
  id="$1"
  cdir="$(core_dir_for "${id}")"
  if [ ! -d "${cdir}" ] || [ ! -f "${cdir}/worker.js" ]; then
    echo "error=core_not_found"
    return 1
  fi

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
  fi

  return 1
}

unzip_to() {
  zipf="$1"
  outdir="$2"
  rm -rf "$outdir" 2>/dev/null || true
  mkdir -p "$outdir" 2>/dev/null || true

  if have_cmd unzip; then
    unzip -q "$zipf" -d "$outdir" >/dev/null 2>&1 && return 0
  fi

  if [ -n "${BB}" ] && "${BB}" unzip --help >/dev/null 2>&1; then
    "${BB}" unzip -q "$zipf" -d "$outdir" >/dev/null 2>&1 && return 0
  fi

  return 1
}

resolve_sha() {
  repo="$1"
  ref="$2"
  url="https://api.github.com/repos/${repo}/commits/${ref}"
  url="$(apply_proxy_url "${url}")"

  # Optional token (to avoid rate limits)
  auth_args=""
  if [ -n "${DANMU_GH_TOKEN:-}" ]; then
    auth_args="-H Authorization: token ${DANMU_GH_TOKEN}"
  fi

  if ! have_cmd curl; then
    echo ""
    return 0
  fi

  # shellcheck disable=SC2086
  out="$(curl -fsSL -H 'User-Agent: danmu_api_manager' ${auth_args} "$url" 2>/dev/null || true)"
  sha="$(printf '%s' "$out" | grep -m1 -oE '"sha"[[:space:]]*:[[:space:]]*"[0-9a-f]+' | head -n1 | sed -E 's/.*"sha"[[:space:]]*:[[:space:]]*"([0-9a-f]+).*/\1/' || true)"
  printf '%s' "${sha}"
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

  sha="$(resolve_sha "$repo" "$ref")"
  zip_ref="$ref"
  if [ -n "${sha}" ]; then
    zip_ref="$sha"
  fi
  sha_short=""
  if [ -n "${sha}" ]; then sha_short="$(printf '%s' "${sha}" | cut -c1-7)"; fi

  id="$(sanitize_id "${repo}_${ref}_${sha_short}")"
  [ -n "${id}" ] || id="$(sanitize_id "${repo}_${ref}")"

  dest_root="${CORES_DIR}/${id}"
  dest_core="${dest_root}/danmu_api"

  # If already installed, just activate
  if [ -d "${dest_core}" ] && [ -f "${dest_core}/worker.js" ]; then
    activate_core "${id}" >/dev/null 2>&1 || true
    mp="$(meta_path_for "${id}")"
    if [ -f "$mp" ]; then
      printf '{"result":"ok","action":"already_installed","activated":true,"core":'
      cat "$mp" 2>/dev/null || echo '{}'
      echo '}'
      return 0
    fi
    echo '{"result":"ok","action":"already_installed","activated":true}'
    return 0
  fi

  mkdir -p "${dest_root}" 2>/dev/null || true

  url="https://codeload.github.com/${repo}/zip/${zip_ref}"
  url="$(apply_proxy_url "${url}")"
  zipf="${TMP_DIR}/${id}.zip"
  exdir="${TMP_DIR}/extract_${id}"

  log "install begin: repo=${repo} ref=${ref} sha=${sha}"

  if ! download_file "$url" "$zipf"; then
    log "download failed: $url"
    echo '{"result":"error","error":"download_failed"}'
    return 1
  fi

  if ! unzip_to "$zipf" "$exdir"; then
    log "unzip failed: $zipf"
    echo '{"result":"error","error":"unzip_failed"}'
    return 1
  fi

  worker_path="$(find "$exdir" -type f -name worker.js 2>/dev/null | grep '/danmu_api/worker.js$' | head -n 1 || true)"
  if [ -z "${worker_path}" ]; then
    log "worker.js not found in extracted zip"
    echo '{"result":"error","error":"core_not_found"}'
    return 1
  fi

  src_dir="$(dirname "${worker_path}")"

  rm -rf "${dest_core}" 2>/dev/null || true
  mkdir -p "${dest_core}" 2>/dev/null || true
  cp -a "${src_dir}/." "${dest_core}/" 2>/dev/null || true

  if [ ! -f "${dest_core}/worker.js" ]; then
    log "copy failed"
    rm -rf "${dest_root}" 2>/dev/null || true
    echo '{"result":"error","error":"copy_failed"}'
    return 1
  fi

  ver="$(read_version_from_globals "${dest_core}")"
  installed="$(date '+%F %T')"
  sizeb="0"
  if have_cmd du; then
    sizeb="$(du -sk "${dest_core}" 2>/dev/null | awk '{print $1*1024}' || echo 0)"
  fi

  write_meta_json "${id}" "${repo}" "${ref}" "${sha}" "${ver}" "${installed}" "${sizeb}"

  rm -f "${zipf}" 2>/dev/null || true
  rm -rf "${exdir}" 2>/dev/null || true

  activate_core "${id}" >/dev/null 2>&1 || true

  mp="$(meta_path_for "${id}")"
  printf '{"result":"ok","action":"installed","activated":true,"core":'
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
    ensure_core_config_link "${active}" || true
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
        if activate_core "${id}"; then
          mp="$(meta_path_for "${id}")"
          printf '{"result":"ok","activated":true,"core":'
          cat "$mp" 2>/dev/null || echo '{}'
          echo '}'
        else
          echo '{"result":"error","error":"activate_failed"}'
          exit 1
        fi
        ;;
      delete)
        id="${3:-}"
        [ -n "${id}" ] || { usage; exit 2; }
        delete_core "${id}"
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
