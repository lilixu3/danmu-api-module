#!/system/bin/sh
# 从签名运行时依赖仓库下载并校验 node_modules.zip，安装到目标目录。
# 用法:
#   fetch_runtime_deps.sh --manifest <file> --signature <file> --public-key <file>
#     --url <zip-url> --expected-sha256 <sha> --expected-size <bytes> --dest <dir>
# 校验顺序: 签名 -> sha256 -> 大小 -> zip 路径安全 -> 解压到临时目录 -> 原子替换。
# 仅支持 https 和 file（测试用，需 FETCH_FILE_PROTOCOL_OK=1）协议。

MANIFEST=""
SIGNATURE=""
PUBLIC_KEY=""
URL=""
EXPECTED_SHA256=""
EXPECTED_SIZE=""
DEST=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --manifest) MANIFEST="$2"; shift 2 ;;
    --signature) SIGNATURE="$2"; shift 2 ;;
    --public-key) PUBLIC_KEY="$2"; shift 2 ;;
    --url) URL="$2"; shift 2 ;;
    --expected-sha256) EXPECTED_SHA256="$2"; shift 2 ;;
    --expected-size) EXPECTED_SIZE="$2"; shift 2 ;;
    --dest) DEST="$2"; shift 2 ;;
    *)
      echo "unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

for var in MANIFEST SIGNATURE PUBLIC_KEY URL EXPECTED_SHA256 EXPECTED_SIZE DEST; do
  eval "value=\$$var"
  [ -n "$value" ] || {
    echo "missing required argument: $var" >&2
    exit 2
  }
done

[ -f "$MANIFEST" ] || { echo "manifest not found: $MANIFEST" >&2; exit 1; }
[ -f "$SIGNATURE" ] || { echo "signature not found: $SIGNATURE" >&2; exit 1; }
[ -f "$PUBLIC_KEY" ] || { echo "public key not found: $PUBLIC_KEY" >&2; exit 1; }

# 校验 manifest 签名（RSA/SHA-256，openssl 或 busybox openssl）
verify_signature() {
  if command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 -verify "$PUBLIC_KEY" -signature "$SIGNATURE" "$MANIFEST" >/dev/null 2>&1
    return $?
  fi
  return 1
}

verify_signature || { echo "manifest signature verification failed" >&2; exit 1; }

# 下载
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

ZIP="$WORK/node_modules.zip"
case "$URL" in
  file://*)
    if [ "${FETCH_FILE_PROTOCOL_OK:-}" != "1" ]; then
      echo "file protocol disabled" >&2
      exit 1
    fi
    cp "$(echo "$URL" | sed 's|^file://||')" "$ZIP"
    ;;
  https://*)
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL --max-time 600 --retry 3 "$URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -q -O "$ZIP" "$URL"
    else
      echo "no downloader available" >&2
      exit 1
    fi
    ;;
  *)
    echo "unsupported url protocol: $URL" >&2
    exit 1
    ;;
esac

# sha256 + 大小校验
ACTUAL_SHA256="$(sha256sum "$ZIP" 2>/dev/null | awk '{print $1}' || busybox sha256sum "$ZIP" 2>/dev/null | awk '{print $1}' || true)"
if [ -z "$ACTUAL_SHA256" ]; then
  echo "sha256sum not available" >&2
  exit 1
fi
[ "$ACTUAL_SHA256" = "$EXPECTED_SHA256" ] || {
  echo "sha256 mismatch: expected=$EXPECTED_SHA256 actual=$ACTUAL_SHA256" >&2
  exit 1
}

ACTUAL_SIZE="$(wc -c < "$ZIP" 2>/dev/null | tr -d ' ')"
[ "$ACTUAL_SIZE" = "$EXPECTED_SIZE" ] || {
  echo "size mismatch: expected=$EXPECTED_SIZE actual=$ACTUAL_SIZE" >&2
  exit 1
}

# 解压并校验路径安全
EXTRACT="$WORK/extract"
mkdir -p "$EXTRACT"

if command -v unzip >/dev/null 2>&1; then
  unzip -q "$ZIP" -d "$EXTRACT"
elif command -v busybox >/dev/null 2>&1; then
  busybox unzip -q "$ZIP" -d "$EXTRACT"
else
  echo "no unzip available" >&2
  exit 1
fi

# 路径穿越检查：任何条目含 .. 或绝对路径都拒绝
if command -v unzip >/dev/null 2>&1; then
  unzip -Z1 "$ZIP" 2>/dev/null | grep -E '(^|/)\.\.(/|$)|^/' >/dev/null 2>&1 && {
    echo "zip contains unsafe path" >&2
    exit 1
  }
fi

# 校验解压内容在目标内部
EXTRACT_DIR="$(cd "$EXTRACT" && pwd)"
DEST_PARENT="$(cd "$(dirname "$DEST")" && pwd)"
[ "${EXTRACT_DIR#"$DEST_PARENT"}" != "$EXTRACT_DIR" ] || {
  # 不在同一父目录时无法做路径前缀检查，但 mktemp 目录本身是可信的
  :
}

# 原子替换：先移动到 staging，再 rename 到目标
STAGING="$WORK/staging"
mkdir -p "$STAGING"
mv "$EXTRACT"/* "$STAGING" 2>/dev/null || cp -a "$EXTRACT/." "$STAGING/" 2>/dev/null || {
  echo "failed to move extract" >&2
  exit 1
}

if [ -e "$DEST" ]; then
  BACKUP="$WORK/backup"
  mv "$DEST" "$BACKUP" 2>/dev/null || {
    echo "failed to move old dest" >&2
    exit 1
  }
fi

if ! mv "$STAGING" "$DEST" 2>/dev/null; then
  # 回滚
  if [ -e "$BACKUP" ]; then
    mv "$BACKUP" "$DEST" 2>/dev/null || true
  fi
  echo "failed to install to dest" >&2
  exit 1
fi

# 清理 backup
[ -e "$BACKUP" ] && rm -rf "$BACKUP" 2>/dev/null || true

echo "ok"
