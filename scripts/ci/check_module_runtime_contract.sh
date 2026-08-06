#!/usr/bin/env sh
# Verify the Magisk module runtime contract needed by downloaded cores.
set -eu

ROOT="${1:-$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

APP_PKG="$ROOT/module_template/app/package.json"
CORE_SH="$ROOT/module_template/scripts/danmu_core.sh"
CTRL_SH="$ROOT/module_template/scripts/danmu_control.sh"
SERVER_MJS="$ROOT/module_template/app/android-server.mjs"
REPOSITORY_KT="$ROOT/manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/data/DanmuRepository.kt"
FETCH_SH="$ROOT/module_template/scripts/fetch_runtime_deps.sh"

[ -f "$APP_PKG" ] || fail "missing $APP_PKG"
[ -f "$CORE_SH" ] || fail "missing $CORE_SH"
[ -f "$CTRL_SH" ] || fail "missing $CTRL_SH"
[ -f "$SERVER_MJS" ] || fail "missing $SERVER_MJS"
[ -f "$REPOSITORY_KT" ] || fail "missing $REPOSITORY_KT"

PKG_PATH="$APP_PKG" node <<'NODE'
const fs = require('fs');
const path = require('path');
const pkgPath = process.env.PKG_PATH;
const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
const deps = pkg.dependencies || {};
const appDir = path.dirname(pkgPath);

function fail(message) {
  console.error(`FAIL: ${message}`);
  process.exit(1);
}

if (pkg.type !== 'module') {
  fail('module_template/app/package.json must declare "type": "module"');
}

const requiredRuntimeDeps = [
  '@dan-uni/dan-any',
  'brotli',
  'chokidar',
  'dotenv',
  'https-proxy-agent',
  'node-fetch',
  'opencc-js',
];

for (const name of requiredRuntimeDeps) {
  if (!deps[name]) {
    fail(`module_template/app/package.json missing dependency: ${name}`);
  }
  if (!fs.existsSync(path.join(appDir, 'node_modules', name, 'package.json'))) {
    fail(`module_template/app/node_modules missing installed package: ${name}`);
  }
}

// redis 是可选依赖（LOCAL_REDIS_URL 启用时才需要），默认不随包分发。
for (const name of ['redis']) {
  if (deps[name]) {
    fail(`module_template/app/package.json should not include optional heavy dependency in default bundle: ${name}`);
  }
  if (fs.existsSync(path.join(appDir, 'node_modules', name))) {
    fail(`module_template/app/node_modules should not vendor optional heavy package by default: ${name}`);
  }
}
NODE

sh -n "$CORE_SH"
sh -n "$CTRL_SH"
node --check "$SERVER_MJS" >/dev/null

# 运行时依赖树必须能支撑核心启动（纯 JS 闭包 + worker import smoke）
APP_DIR="$ROOT/module_template/app" node --input-type=module <<'NODE'
import { createRequire } from 'module';
import path from 'path';
const require = createRequire(import.meta.url);
const appDir = process.env.APP_DIR;
const req = createRequire(path.join(appDir, 'package.json'));

const probes = [
  '@dan-uni/dan-any/core/main/pure',
  '@dan-uni/dan-any/adapters',
  'brotli/decompress.js',
  'opencc-js/core',
  'opencc-js/dict/STCharacters',
  'node-fetch',
  'https-proxy-agent',
];
for (const spec of probes) {
  try {
    req.resolve(spec);
  } catch (e) {
    console.error(`FAIL: cannot resolve ${spec}`);
    process.exit(1);
  }
}
console.log('runtime closure resolve ok');
NODE

grep -q 'ensure_core_node_modules_link()' "$CORE_SH" \
  || fail "danmu_core.sh must define ensure_core_node_modules_link()"

grep -q 'app/node_modules' "$CORE_SH" \
  || fail "danmu_core.sh must link each downloaded core to module app/node_modules"

link_call_count="$(grep -c 'ensure_core_node_modules_link' "$CORE_SH" || true)"
[ "$link_call_count" -ge 3 ] \
  || fail "danmu_core.sh must call ensure_core_node_modules_link during install and activation"

# 依赖检查/阻断契约
grep -q 'runtime-deps.mjs' "$CORE_SH" \
  || fail "danmu_core.sh must use runtime-deps.mjs dependency inspection before activation"

grep -q 'dependency_repair_required' "$CORE_SH" \
  || fail "danmu_core.sh must emit dependency_repair_required on missing deps"

grep -q 'startup.log' "$CTRL_SH" \
  || fail "danmu_control.sh must preserve startup stderr/stdout in startup.log"

grep -Eq 'nohup .+>>.*STARTUP_LOG.*2>&1' "$CTRL_SH" \
  || fail "danmu_control.sh must redirect startup output to STARTUP_LOG"

grep -q 'readListenConfig' "$SERVER_MJS" \
  || fail "android-server.mjs must read listen config after loading .env"

grep -q 'mainServer.listen(port, host' "$SERVER_MJS" \
  || fail "android-server.mjs must bind main server with runtime .env host/port"

grep -q 'proxyServer.listen(proxyPort, host' "$SERVER_MJS" \
  || fail "android-server.mjs must bind proxy server with runtime .env host/port"

grep -q 'createMainServer(port)' "$SERVER_MJS" \
  || fail "android-server.mjs must pass runtime port into createMainServer()"

if grep -q '127.0.0.1:${PORT}' "$SERVER_MJS"; then
  fail "android-server.mjs must not reference removed PORT constant"
fi

grep -q 'https://api.github.com/repos/${repo}/commits/${ref}' "$CORE_SH" \
  || fail "danmu_core.sh must resolve branch/tag refs to commit sha for meta.json"

if grep -q 'avoid GitHub API dependency' "$CORE_SH"; then
  fail "danmu_core.sh must not skip sha resolution for branch/tag refs"
fi

grep -q 'val latestCommit = gitHubApi.getLatestCommit(core.repo, core.ref, token)' "$REPOSITORY_KT" \
  || fail "DanmuRepository.checkUpdate must fetch latest commit even without GitHub token"

grep -q 'refOrSha = latestCommit?.sha' "$REPOSITORY_KT" \
  || fail "DanmuRepository.checkUpdate must read latest version from resolved commit sha when available"

# 下载器不允许 TLS 降级
if grep -Eq -- 'curl -k|curl --insecure|--no-check-certificate' "$CORE_SH" "$FETCH_SH" 2>/dev/null; then
  fail "downloaders must not use insecure TLS fallback"
fi

echo "module runtime contract ok"
