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
  'chokidar',
  'dotenv',
  'esbuild',
  'https-proxy-agent',
  'node-fetch',
  'pako',
];

for (const name of requiredRuntimeDeps) {
  if (!deps[name]) {
    fail(`module_template/app/package.json missing dependency: ${name}`);
  }
  if (!fs.existsSync(path.join(appDir, 'node_modules', name, 'package.json'))) {
    fail(`module_template/app/node_modules missing installed package: ${name}`);
  }
}

for (const name of ['redis']) {
  if (deps[name]) {
    fail(`module_template/app/package.json should not include optional heavy dependency in default bundle: ${name}`);
  }
  if (fs.existsSync(path.join(appDir, 'node_modules', name))) {
    fail(`module_template/app/node_modules should not vendor optional heavy package by default: ${name}`);
  }
}

for (const name of ['@redis', 'cluster-key-slot']) {
  if (fs.existsSync(path.join(appDir, 'node_modules', name))) {
    fail(`module_template/app/node_modules should not vendor optional redis transitive package by default: ${name}`);
  }
}
NODE

sh -n "$CORE_SH"
sh -n "$CTRL_SH"
node --check "$SERVER_MJS" >/dev/null

grep -q 'ensure_core_node_modules_link()' "$CORE_SH" \
  || fail "danmu_core.sh must define ensure_core_node_modules_link()"

grep -q 'app/node_modules' "$CORE_SH" \
  || fail "danmu_core.sh must link each downloaded core to module app/node_modules"

link_call_count="$(grep -c 'ensure_core_node_modules_link' "$CORE_SH" || true)"
[ "$link_call_count" -ge 3 ] \
  || fail "danmu_core.sh must call ensure_core_node_modules_link during install and activation"

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

echo "module runtime contract ok"
