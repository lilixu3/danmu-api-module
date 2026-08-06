#!/usr/bin/env sh
# 打包期锁定文件校验：module_template/app 的依赖闭包必须与锁文件一致。
# 在 CI 的 build_modules.sh 打包之前运行；没有实际安装依赖的本地开发环境
# 也允许只校验锁文件与 package.json 的一致性。
set -eu

ROOT="${1:-$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)}"

APP_DIR="$ROOT/module_template/app"
PKG="$APP_DIR/package.json"
LOCK="$APP_DIR/package-lock.json"
NODE_MODULES="$APP_DIR/node_modules"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$PKG" ] || fail "missing $PKG"
[ -f "$LOCK" ] || fail "missing $LOCK (请先执行 npm install --package-lock-only 或完整 npm ci 生成锁文件)"

LOCK_ROOT_PKGS="$(PKG_PATH="$PKG" LOCK_PATH="$LOCK" node <<'NODE'
const fs = require('fs');
const pkg = JSON.parse(fs.readFileSync(process.env.PKG_PATH, 'utf8'));
const lock = JSON.parse(fs.readFileSync(process.env.LOCK_PATH, 'utf8'));
const root = lock.packages && lock.packages[''] ? lock.packages[''] : {};
const merged = Object.assign({}, root.dependencies || {}, root.optionalDependencies || {});
const expected = Object.assign({}, pkg.dependencies || {}, pkg.optionalDependencies || {});
for (const name of Object.keys(expected).sort()) {
  if (merged[name] !== expected[name]) {
    console.error(`FAIL: lockfile 与 package.json 不一致: ${name}`);
    process.exit(1);
  }
}
for (const name of Object.keys(merged).sort()) {
  if (!(name in expected)) {
    console.error(`FAIL: lockfile 包含 package.json 未声明的依赖: ${name}`);
    process.exit(1);
  }
}
console.log(Object.keys(expected).sort().join('\n'));
NODE
)"

# 校验实际安装的依赖目录（CI 场景必查；本地无 node_modules 时跳过目录校验）
if [ -d "$NODE_MODULES" ]; then
  MISSING=""
  for name in $LOCK_ROOT_PKGS; do
    if [ ! -f "$NODE_MODULES/$name/package.json" ]; then
      MISSING="$MISSING $name"
    fi
  done
  [ -z "$MISSING" ] || fail "node_modules 缺少锁文件声明包:$MISSING"
fi

echo "runtime lockfile contract ok"
