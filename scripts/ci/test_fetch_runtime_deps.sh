#!/usr/bin/env sh
# 校验签名运行时依赖包的下载脚本（fetch_runtime_deps.sh）契约。
# 使用一个本地 mock 仓库验证：校验失败拒绝安装、sha256 不符拒绝、
# 路径穿越拒绝、成功路径会安装到目标目录。
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
SCRIPT="$ROOT/module_template/scripts/fetch_runtime_deps.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$SCRIPT" ] || fail "missing $SCRIPT"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/mock/manifest-dir" "$TMP/out"
MOCK_REPO="$TMP/mock"

# 生成 mock manifest + 一个 node_modules 目录
cat > "$TMP/mock/manifest.json" <<'JSON'
{
  "schema": 3,
  "serial": 1,
  "runtimeProtocol": 2,
  "nodeMajor": 18,
  "dependencies": {"brotli": "1.3.3"},
  "artifactUrl": "https://example.invalid/node_modules.zip",
  "artifactSha256": "0000000000000000000000000000000000000000000000000000000000000000",
  "artifactSize": 100
}
JSON
# 签名：manifest 的 sha256 用固定测试密钥自签（RSA 2048）
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$TMP/mock/private.pem" 2>/dev/null
openssl rsa -in "$TMP/mock/private.pem" -pubout -out "$TMP/mock/public.pem" 2>/dev/null
printf '%s' "$TMP/mock/public.pem" > "$TMP/mock/pubkey-path"
openssl dgst -sha256 -sign "$TMP/mock/private.pem" \
  -out "$TMP/mock/manifest.sig" "$TMP/mock/manifest.json" 2>/dev/null

# 构造一个最小合法 zip（含 node_modules/brotli/package.json）
mkdir -p "$TMP/zip/node_modules/brotli"
cat > "$TMP/zip/node_modules/brotli/package.json" <<'JSON'
{"name":"brotli","version":"1.3.3"}
JSON
(cd "$TMP/zip" && zip -q -r "$TMP/node_modules.zip" node_modules)

# 期望的 sha256
EXPECTED_SHA="$(sha256sum "$TMP/node_modules.zip" | awk '{print $1}')"
EXPECTED_SIZE="$(wc -c < "$TMP/node_modules.zip" | tr -d ' ')"
cat > "$TMP/mock/manifest.json" <<JSON
{
  "schema": 3,
  "serial": 1,
  "runtimeProtocol": 2,
  "nodeMajor": 18,
  "dependencies": {"brotli": "1.3.3"},
  "artifactUrl": "https://example.invalid/node_modules.zip",
  "artifactSha256": "$EXPECTED_SHA",
  "artifactSize": $EXPECTED_SIZE
}
JSON
openssl dgst -sha256 -sign "$TMP/mock/private.pem" \
  -out "$TMP/mock/manifest.sig" "$TMP/mock/manifest.json" 2>/dev/null

# 1) 成功路径：使用 file:// 协议代替 https，避免测试依赖网络
if ! FETCH_FILE_PROTOCOL_OK=1 sh "$SCRIPT" \
  --manifest "$TMP/mock/manifest.json" \
  --signature "$TMP/mock/manifest.sig" \
  --public-key "$TMP/mock/public.pem" \
  --url "file://$TMP/node_modules.zip" \
  --expected-sha256 "$EXPECTED_SHA" \
  --expected-size "$EXPECTED_SIZE" \
  --dest "$TMP/out" 2>"$TMP/err.log"; then
  fail "成功路径失败: $(cat "$TMP/err.log")"
fi
[ -f "$TMP/out/node_modules/brotli/package.json" ] || fail "成功路径未安装依赖"

# 2) sha256 不匹配必须拒绝
if FETCH_FILE_PROTOCOL_OK=1 sh "$SCRIPT" \
  --manifest "$TMP/mock/manifest.json" \
  --signature "$TMP/mock/manifest.sig" \
  --public-key "$TMP/mock/public.pem" \
  --url "file://$TMP/node_modules.zip" \
  --expected-sha256 "$(printf 'a%.0s' $(seq 1 64))" \
  --expected-size "$EXPECTED_SIZE" \
  --dest "$TMP/out2" 2>/dev/null; then
  fail "sha256 不匹配未被拒绝"
fi

# 3) 签名无效必须拒绝
openssl dgst -sha256 -sign "$TMP/mock/private.pem" \
  -out "$TMP/mock/bad.sig" "$TMP/mock/manifest.json" 2>/dev/null
# 篡改 manifest 后签名不再有效
sed 's/"serial": 1/"serial": 2/' "$TMP/mock/manifest.json" > "$TMP/mock/tampered.json"
if FETCH_FILE_PROTOCOL_OK=1 sh "$SCRIPT" \
  --manifest "$TMP/mock/tampered.json" \
  --signature "$TMP/mock/manifest.sig" \
  --public-key "$TMP/mock/public.pem" \
  --url "file://$TMP/node_modules.zip" \
  --expected-sha256 "$EXPECTED_SHA" \
  --expected-size "$EXPECTED_SIZE" \
  --dest "$TMP/out3" 2>/dev/null; then
  fail "篡改 manifest 未被拒绝"
fi

# 4) 路径穿越条目必须拒绝
mkdir -p "$TMP/evil/node_modules"
cat > "$TMP/evil/node_modules/evil.txt" <<'EOF'
evil
EOF
(cd "$TMP/evil" && zip -q -r "$TMP/evil.zip" node_modules ../../etc/passwd 2>/dev/null || true)
# 构造包含 ../ 条目的 zip
python3 - "$TMP/evil.zip" <<'PY'
import sys, zipfile
zip_path = sys.argv[1]
out = zip_path.replace('.zip', '-traversal.zip')
with zipfile.ZipFile(zip_path, 'r') as zin, zipfile.ZipFile(out, 'w') as zout:
    for info in zin.infolist():
        zout.writestr('../escape-' + info.filename, zin.read(info.filename))
PY
EVIL_SHA="$(sha256sum "$TMP/evil-traversal.zip" | awk '{print $1}')"
EVIL_SIZE="$(wc -c < "$TMP/evil-traversal.zip" | tr -d ' ')"
cat > "$TMP/mock/evil-manifest.json" <<JSON
{
  "schema": 3,
  "serial": 1,
  "runtimeProtocol": 2,
  "nodeMajor": 18,
  "dependencies": {"brotli": "1.3.3"},
  "artifactUrl": "https://example.invalid/node_modules.zip",
  "artifactSha256": "$EVIL_SHA",
  "artifactSize": $EVIL_SIZE
}
JSON
if FETCH_FILE_PROTOCOL_OK=1 sh "$SCRIPT" \
  --manifest "$TMP/mock/evil-manifest.json" \
  --signature "$TMP/mock/manifest.sig" \
  --public-key "$TMP/mock/public.pem" \
  --url "file://$TMP/evil-traversal.zip" \
  --expected-sha256 "$EVIL_SHA" \
  --expected-size "$EVIL_SIZE" \
  --dest "$TMP/out4" 2>/dev/null; then
  fail "路径穿越条目未被拒绝"
fi

echo "fetch runtime deps contract ok"
