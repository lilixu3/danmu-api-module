#!/usr/bin/env sh
# 防止运行时依赖检查器退化为单一 Node 版本验证。
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/ci.yml"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$WORKFLOW" ] || fail "缺少 CI workflow"

grep -q '^  runtime-node-matrix:' "$WORKFLOW" || fail "缺少 runtime-node-matrix job"
grep -Eq 'node-version:.*\[18, *20, *22\]' "$WORKFLOW" || fail "Node 矩阵必须包含 18/20/22"
grep -q "uses: actions/setup-node@v4" "$WORKFLOW" || fail "矩阵 job 必须使用 setup-node@v4"
grep -q "node --test scripts/ci/test_runtime_deps.mjs" "$WORKFLOW" || fail "矩阵 job 未运行 runtime-deps 测试"
grep -q "check_runtime_lockfile.sh" "$WORKFLOW" || fail "CI 未校验运行时锁文件契约"
# test_build_node_pinning.sh 使用 pipefail/BASH_SOURCE/source，禁止用 dash(/bin/sh) 强制执行
# （GitHub ubuntu-latest 的 sh=dash，会在 set -o pipefail 处直接 exit 2）。
grep -q "run: bash scripts/ci/test_build_node_pinning.sh" "$WORKFLOW" \
  || fail "Node pinning 测试必须用 bash 执行"

echo "CI node matrix contract ok"
