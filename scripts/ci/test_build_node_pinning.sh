#!/usr/bin/env bash
# 供应链加固测试：Termux node 下载的版本固定与 SHA256 校验。
# 覆盖：Packages 索引字段解析、版本漂移拒绝、哈希不匹配拒绝、缺哈希 fail-closed。
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./node_pinning_lib.sh
source "$SCRIPT_DIR/node_pinning_lib.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

# ---------- 构造 mock Packages 索引 ----------
cat > "$TMP/Packages" <<'INDEX'
Package: nodejs
Version: 18.20.4-1
Installed-Size: 40562
Filename: pool/main/n/nodejs/nodejs_18.20.4-1_aarch64.deb
Size: 14511660
SHA256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
Depends: libc++ (>= 24c), zlib (>= 1:1.3.1), libuv, openssl, nghttp2, libc-ares
Homepage: https://nodejs.org

Package: libc++-shared
Version: 27b-0
Filename: pool/main/libc/libc++/libc++-shared_27b-0_aarch64.deb
Size: 421104
SHA256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb

Package: nodejs-old
Version: 16.20.2-1
Filename: pool/main/n/nodejs/nodejs_16.20.2-1_aarch64.deb
Size: 14511660
SHA256: cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
INDEX

# ---------- 测试 1: 字段解析 ----------
ver="$(pkg_field "$TMP/Packages" nodejs Version)"
[ "$ver" = "18.20.4-1" ] || fail "测试1: Version 解析错误: $ver"

sha="$(pkg_field "$TMP/Packages" nodejs SHA256)"
[ "$sha" = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" ] \
  || fail "测试1: SHA256 解析错误: $sha"

fn="$(pkg_field "$TMP/Packages" nodejs Filename)"
[ "$fn" = "pool/main/n/nodejs/nodejs_18.20.4-1_aarch64.deb" ] \
  || fail "测试1: Filename 解析错误: $fn"

dep="$(pkg_field "$TMP/Packages" nodejs Depends)"
echo "$dep" | grep -q "libc++" || fail "测试1: Depends 续接行解析错误: $dep"

# ---------- 测试 2: 版本固定校验 ----------
check_node_version_pinned "26.4.0" || fail "测试2: 26.4.0 应通过"
check_node_version_pinned "26.4.0-1" || fail "测试2: 26.4.0-1 应通过"
check_node_version_pinned "26.4.1" && fail "测试2: 26.4.1 应拒绝"
check_node_version_pinned "18.20.4-1" && fail "测试2: 18.20.4 应拒绝"
check_node_version_pinned "" && fail "测试2: 空版本应拒绝"
check_node_version_pinned "26.5.0-1" && fail "测试2: 26.5.0 应拒绝"

# ---------- 测试 3: SHA256 校验 ----------
printf 'fake-deb-content' > "$TMP/nodejs.deb"
EXPECTED_GOOD="$(sha256sum "$TMP/nodejs.deb" | awk '{print $1}')"

verify_file_sha256 "$TMP/nodejs.deb" "$EXPECTED_GOOD" || fail "测试3: 正确哈希应通过"
verify_file_sha256 "$TMP/nodejs.deb" "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
  && fail "测试3: 错误哈希应拒绝"
verify_file_sha256 "$TMP/nodejs.deb" "" && fail "测试3: 缺失哈希应 fail-closed"
verify_file_sha256 "$TMP/missing.deb" "$EXPECTED_GOOD" && fail "测试3: 文件不存在应拒绝"

# ---------- 测试 4: NODE_PINNED_VERSION 可覆盖（CI 迁移期） ----------
NODE_PINNED_VERSION="18.20.2" check_node_version_pinned "18.20.2-1" \
  || fail "测试4: 覆盖固定版本应生效"

# ---------- 测试 5: node 锁文件契约 ----------
LOCK_FILE="$SCRIPT_DIR/termux-node-aarch64.lock.json"
[ -f "$LOCK_FILE" ] || fail "测试5: 缺少 node 锁文件"
python3 - "$LOCK_FILE" <<'PY'
import json, re, sys
lock = json.load(open(sys.argv[1], encoding='utf-8'))
assert lock.get('schema') == 1, 'schema'
assert lock.get('architecture') == 'aarch64', 'arch'
assert lock.get('nodeMajor'), 'nodeMajor'
packages = lock.get('packages', [])
assert packages, 'packages empty'
names = [p['name'] for p in packages]
assert 'nodejs' in names, 'missing nodejs entry'
assert names[0] == 'nodejs', 'nodejs must be first'
sha_re = re.compile(r'^[0-9a-f]{64}$')
for entry in packages:
    assert entry.get('name'), 'name'
    assert entry.get('version'), f"version {entry.get('name')}"
    assert entry.get('filename', '').endswith('.deb'), f"filename {entry.get('name')}"
    assert sha_re.match(entry.get('sha256', '')), f"sha256 {entry.get('name')}"
print(f"lock entries={len(packages)} root={lock['rootPackage']} nodeMajor={lock['nodeMajor']}")
PY

echo "node pinning lib ok"
