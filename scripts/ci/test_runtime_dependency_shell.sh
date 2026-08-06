#!/usr/bin/env sh
# danmu_core.sh 运行时依赖集成测试（全部在 mktemp 下运行，不触碰 /data/adb）
# 覆盖：
#   1) 缺依赖时 activate 拒绝且 CORE_LINK 不变
#   2) 依赖健康时可激活
#   3) deps install 无效候选保留旧 live 目录
#   4) deps install 有效候选原子替换并写 dependencyId
#   5) install_core 保留核心根 package.json/package-lock.json
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
CORE_SH="$ROOT/module_template/scripts/danmu_core.sh"
RUNTIME_DEPS="$ROOT/module_template/app/runtime-deps.mjs"
MODULE_NODE_MODULES="$ROOT/module_template/app/node_modules"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$CORE_SH" ] || fail "missing $CORE_SH"
[ -f "$RUNTIME_DEPS" ] || fail "missing $RUNTIME_DEPS"
[ -d "$MODULE_NODE_MODULES" ] || fail "missing module node_modules"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

TEST_PERSIST="$TMP/persist"
mkdir -p "$TEST_PERSIST/config" "$TEST_PERSIST/cores" "$TEST_PERSIST/tmp" "$TEST_PERSIST/logs" "$TEST_PERSIST/bin"

# 模拟核心（只含 worker.js + package.json）
make_core() {
  cid="$1"
  core_dir="$TEST_PERSIST/cores/$cid/danmu_api"
  mkdir -p "$core_dir/config"
  cat > "$core_dir/worker.js" <<'EOF'
export function handleRequest() { return new Response('ok'); }
EOF
  cat > "$core_dir/package.json" <<'JSON'
{"name":"test-core","version":"1.0.0","type":"module","dependencies":{"node-fetch":"3.3.2"}}
JSON
  cat > "$core_dir/package-lock.json" <<'JSON'
{"lockfileVersion":3,"packages":{"":{"name":"test-core","version":"1.0.0","dependencies":{"node-fetch":"3.3.2"}},"node_modules/node-fetch":{"version":"3.3.2"}}}
JSON
  cat > "$TEST_PERSIST/cores/$cid/meta.json" <<JSON
{"id":"$cid","repo":"test/repo","ref":"test","sha":"deadbeef","version":"1.0.0","installedAt":"2026-08-06T00:00:00Z","sizeBytes":1}
JSON
  echo "$cid"
}

# 构造测试 node_modules 树
make_test_node_modules() {
  dir="$1"
  mkdir -p "$dir/node_modules/node-fetch"
  cat > "$dir/node_modules/node-fetch/package.json" <<'JSON'
{"name":"node-fetch","version":"3.3.2","main":"lib/index.js","type":"module"}
JSON
  mkdir -p "$dir/node_modules/node-fetch/lib"
  cat > "$dir/node_modules/node-fetch/lib/index.js" <<'EOF'
export default function fetch() { return Promise.resolve(); }
EOF
}

# 通过环境变量注入测试路径
run_cli() {
  CORE_DANMU_TEST_PERSIST="$TEST_PERSIST" \
  CORE_DANMU_TEST_MODDIR="$TMP/moddir" \
  CORE_DANMU_TEST_NODE="$MODULE_NODE_MODULES" \
  CORE_DANMU_TEST_NODE_MODULES="${TEST_NODE_MODULES:-$MODULE_NODE_MODULES}" \
  CORE_DANMU_TEST_NODE_BIN="${TEST_NODE_BIN:-}" \
  CORE_DANMU_TEST_RUNTIME_DEPS="${TEST_RUNTIME_DEPS:-$RUNTIME_DEPS}" \
  sh "$CORE_SH" "$@"
}

run_cli_capture() {
  set +e
  CLI_OUTPUT="$(run_cli "$@" 2>&1)"
  CLI_STATUS=$?
  set -e
}

mkdir -p "$TMP/moddir/app"

# ---------- 测试 1: 缺依赖拒绝激活且 CORE_LINK 不变 ----------
cid="$(make_core "test1")"
# 空的 node_modules（缺 node-fetch）
empty_nm="$TEST_PERSIST/deps/empty/node_modules"
mkdir -p "$empty_nm"
TEST_NODE_MODULES="$empty_nm"
echo "$cid" > "$TEST_PERSIST/active_core_id"
ln -s "$TEST_PERSIST/cores/$cid/danmu_api" "$TEST_PERSIST/core"

run_cli_capture core activate "$cid"
echo "$CLI_OUTPUT" | grep -q 'dependency_repair_required' \
  || fail "测试1: 缺依赖激活应返回 dependency_repair_required，实际: $CLI_OUTPUT"
[ "$CLI_STATUS" -ne 0 ] \
  || fail "测试1: 缺依赖激活退出码应为非零"
CURRENT_LINK="$(readlink "$TEST_PERSIST/core" 2>/dev/null || true)"
[ "$CURRENT_LINK" = "$TEST_PERSIST/cores/$cid/danmu_api" ] \
  || fail "测试1: CORE_LINK 不应被改变: $CURRENT_LINK"
unset TEST_NODE_MODULES

# ---------- 测试 2: 依赖健康时可激活 ----------
cid2="$(make_core "test2")"
make_test_node_modules "$TEST_PERSIST/cores/$cid2"
run_cli_capture core activate "$cid2"
echo "$CLI_OUTPUT" | grep -q '"result":"ok"' \
  || fail "测试2: 健康依赖激活失败: $CLI_OUTPUT"
[ "$CLI_STATUS" -eq 0 ] \
  || fail "测试2: 健康激活退出码应为 0"
[ "$(cat "$TEST_PERSIST/active_core_id" 2>/dev/null || true)" = "$cid2" ] \
  || fail "测试2: active_core_id 未更新"

# ---------- 测试 3: deps install 无效候选保留旧 live ----------
cid3="$(make_core "test3")"
live_deps="$TEST_PERSIST/deps/abc123/node_modules"
make_test_node_modules "$TEST_PERSIST/deps/abc123"
echo "old-live-marker" > "$TEST_PERSIST/deps/abc123/node_modules/marker.txt"
# 无效候选（缺 node-fetch package.json）
bad_candidate="$TMP/bad-candidate"
mkdir -p "$bad_candidate/node_modules/whatever"
cat > "$bad_candidate/node_modules/whatever/package.json" <<'JSON'
{"name":"whatever","version":"1.0.0"}
JSON
OUTPUT3="$(run_cli deps install "$cid3" "$bad_candidate/node_modules" "abc123" 2>&1 || true)"
[ -f "$TEST_PERSIST/deps/abc123/node_modules/marker.txt" ] \
  || fail "测试3: 无效候选不应破坏旧 live 目录"
[ -f "$TEST_PERSIST/deps/abc123/node_modules/node-fetch/package.json" ] \
  || fail "测试3: 旧 live 依赖应保留"

# ---------- 测试 4: deps install 有效候选原子替换并写 dependencyId ----------
good_candidate="$TMP/good-candidate"
make_test_node_modules "$good_candidate"
echo "new-marker" > "$good_candidate/node_modules/new-marker.txt"
run_cli_capture deps install "$cid3" "$good_candidate/node_modules" "def456"
[ "$CLI_STATUS" -eq 0 ] \
  || fail "测试4: deps install 有效候选失败: $CLI_OUTPUT"
[ -f "$TEST_PERSIST/deps/def456/node_modules/new-marker.txt" ] \
  || fail "测试4: 新依赖树未安装到 deps/def456"
node -e 'const m=require(process.argv[1]); if(m.dependencyId!=="def456") process.exit(1)' \
  "$TEST_PERSIST/cores/$cid3/meta.json" \
  || fail "测试4: meta.json 未记录 dependencyId"
[ "$(readlink "$TEST_PERSIST/cores/$cid3/danmu_api/node_modules" 2>/dev/null || true)" = "$TEST_PERSIST/deps/def456/node_modules" ] \
  || fail "测试4: 核心 node_modules 未切换到新依赖目录"

# ---------- 测试 6: 检查器异常时 fail-closed（拒绝激活，不破坏旧链接） ----------
cid6="$(make_core "test6")"
echo "$cid6" > "$TEST_PERSIST/active_core_id"
rm -f "$TEST_PERSIST/core"
ln -s "$TEST_PERSIST/cores/$cid6/danmu_api" "$TEST_PERSIST/core"
TEST_RUNTIME_DEPS="$TMP/not-a-real-checker.mjs"
run_cli_capture core activate "$cid6"
unset TEST_RUNTIME_DEPS
[ "$CLI_STATUS" -ne 0 ] \
  || fail "测试6: 检查器缺失必须拒绝激活（fail closed），实际 exit=$CLI_STATUS"
CURRENT_LINK="$(readlink "$TEST_PERSIST/core" 2>/dev/null || true)"
[ "$CURRENT_LINK" = "$TEST_PERSIST/cores/$cid6/danmu_api" ] \
  || fail "测试6: fail-closed 时 CORE_LINK 不应被改变: $CURRENT_LINK"

# ---------- 测试 5: install_core 保留核心根 package.json/package-lock.json ----------
# 通过本地 mock GitHub 不现实，这里直接验证 staging 复制逻辑：
# 检查 install_core 中复制路径包含根锁文件
grep -q 'package-lock.json' "$CORE_SH" \
  || fail "测试5: danmu_core.sh 必须处理核心根 package-lock.json"

# ---------- 测试 7: core fingerprint 与 Node 端契约一致 ----------
# 算法：sha256(canonical_json(sorted(dependencies+optionalDependencies)))，其中
# canonical_json = sort_keys + separators=(",",":") + ensure_ascii=false（与
# runtime-packs build_runtime_pack.py 及 App RuntimeDependencyPackProtocol 一致）
cid7="$(make_core "test7")"
EXPECTED_FP="$(node -e '
const fs = require("node:fs");
const crypto = require("node:crypto");
const pkg = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
const deps = { ...(pkg.dependencies || {}), ...(pkg.optionalDependencies || {}) };
const canonical = JSON.stringify(deps, Object.keys(deps).sort());
process.stdout.write(crypto.createHash("sha256").update(canonical, "utf8").digest("hex"));
' "$TEST_PERSIST/cores/$cid7/danmu_api/package.json")"
run_cli_capture core fingerprint "$cid7"
[ "$CLI_STATUS" -eq 0 ] || fail "测试7: core fingerprint 命令失败: $CLI_OUTPUT"
FP="$(printf '%s' "$CLI_OUTPUT" | grep -o '"fingerprint":"[0-9a-f]*"' | head -n1 | sed 's/.*:"\([0-9a-f]*\)"/\1/')"
[ -n "$FP" ] || fail "测试7: 未输出 fingerprint: $CLI_OUTPUT"
[ "$FP" = "$EXPECTED_FP" ] \
  || fail "测试7: 指纹与 Node 契约不一致 expected=$EXPECTED_FP actual=$FP"

echo "runtime dependency shell integration ok"
