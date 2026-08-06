#!/usr/bin/env node
// Tests for module_template/app/runtime-deps.mjs (Node built-ins only, no network).
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

import {
  classifyDependency,
  parseRange,
  satisfies,
  parseEnvFile,
  readCoreManifest,
  inspectCore,
} from '../../module_template/app/runtime-deps.mjs';

const RUNTIME_DEPS_MJS = fileURL('../../module_template/app/runtime-deps.mjs');
const REPO_ROOT = fileURL('../../');
const MODULE_APP_NODE_MODULES = path.join(REPO_ROOT, 'module_template/app/node_modules');

function fileURL(rel) {
  return new URL(rel, import.meta.url).pathname;
}

function tmpdir(prefix) {
  return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

function makeCoreRoot({ deps = {}, optionalDeps = {}, manifest = true, type = 'module', name = 'fake-core', version = '1.2.3' } = {}) {
  const dir = tmpdir('deps-core-');
  if (manifest) {
    const pkg = { name, version, type };
    if (Object.keys(deps).length) pkg.dependencies = deps;
    if (Object.keys(optionalDeps).length) pkg.optionalDependencies = optionalDeps;
    fs.writeFileSync(path.join(dir, 'package.json'), JSON.stringify(pkg));
  }
  fs.mkdirSync(path.join(dir, 'danmu_api'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'danmu_api', 'worker.js'), 'export default function worker() {}\n');
  return dir;
}

// specs: { name: version } ; version null → dir without package.json
function makeNodeModules(specs) {
  const dir = tmpdir('deps-nm-');
  for (const [name, version] of Object.entries(specs)) {
    fs.mkdirSync(path.join(dir, name), { recursive: true });
    if (version !== null) {
      fs.writeFileSync(path.join(dir, name, 'package.json'), JSON.stringify({ name, version }));
    }
  }
  return dir;
}

// ---------------------------------------------------------------- classify

test('classifyDependency: esbuild is knownNonRuntime, chokidar/dotenv required, redis conditional', () => {
  assert.strictEqual(classifyDependency('esbuild'), 'knownNonRuntime');
  assert.strictEqual(classifyDependency('chokidar'), 'required');
  assert.strictEqual(classifyDependency('dotenv'), 'required');
  assert.strictEqual(classifyDependency('redis'), 'conditional');
  assert.strictEqual(classifyDependency('node-fetch'), 'required');
});

test('classifyDependency: redis is conditional, everything else required', () => {
  assert.equal(classifyDependency('redis'), 'conditional');
  assert.equal(classifyDependency('node-fetch'), 'required');
  assert.equal(classifyDependency('pako'), 'required');
  assert.equal(classifyDependency('whatever-new-pkg'), 'required');
});

// ------------------------------------------------------------- parseRange

test('parseRange: supports exact/caret/tilde/>=/* and compound ranges', () => {
  assert.ok(parseRange('1.2.3'));
  assert.ok(parseRange('^1.2.3'));
  assert.ok(parseRange('^1.2'));
  assert.ok(parseRange('~1.2.3'));
  assert.ok(parseRange('~1.2'));
  assert.ok(parseRange('~1'));
  assert.ok(parseRange('>=1.2.3'));
  assert.ok(parseRange('*'));
  assert.ok(parseRange('>=1.0.0 <2.0.0'));
});

test('parseRange: unparseable ranges return null (fail closed)', () => {
  assert.equal(parseRange('latest'), null);
  assert.equal(parseRange('abc'), null);
  assert.equal(parseRange('1.2.x'), null);
  assert.equal(parseRange(''), null);
  assert.equal(parseRange('^'), null);
});

// -------------------------------------------------------------- satisfies

test('satisfies: exact versions', () => {
  assert.equal(satisfies('1.2.3', '1.2.3'), true);
  assert.equal(satisfies('1.2.4', '1.2.3'), false);
});

test('satisfies: caret ranges', () => {
  assert.equal(satisfies('1.5.0', '^1.2.3'), true);
  assert.equal(satisfies('2.0.0', '^1.2.3'), false);
  assert.equal(satisfies('0.2.9', '^0.2.3'), true);
  assert.equal(satisfies('0.3.0', '^0.2.3'), false);
  assert.equal(satisfies('0.0.3', '^0.0.3'), true);
  assert.equal(satisfies('0.0.4', '^0.0.3'), false);
  assert.equal(satisfies('1.9.9', '^1.2'), true);
  assert.equal(satisfies('2.0.0', '^1.2'), false);
});

test('satisfies: tilde ranges', () => {
  assert.equal(satisfies('1.2.9', '~1.2.3'), true);
  assert.equal(satisfies('1.3.0', '~1.2.3'), false);
  assert.equal(satisfies('1.2.0', '~1.2'), true);
  assert.equal(satisfies('1.3.0', '~1.2'), false);
  assert.equal(satisfies('1.9.0', '~1'), true);
  assert.equal(satisfies('2.0.0', '~1'), false);
});

test('satisfies: >= and compound and wildcard', () => {
  assert.equal(satisfies('2.0.0', '>=1.2.3'), true);
  assert.equal(satisfies('1.2.2', '>=1.2.3'), false);
  assert.equal(satisfies('1.5.0', '>=1.0.0 <2.0.0'), true);
  assert.equal(satisfies('2.5.0', '>=1.0.0 <2.0.0'), false);
  assert.equal(satisfies('9.9.9', '*'), true);
});

test('satisfies: unparseable spec or installed version fails closed with null', () => {
  assert.equal(satisfies('1.2.3', 'latest'), null);
  assert.equal(satisfies('garbage', '^1.0.0'), null);
  // v-prefixed installed versions are unambiguous and tolerated
  assert.equal(satisfies('v1.2.3', '^1.0.0'), true);
});

test('satisfies: caret/tilde single and dual component semantics match npm', () => {
  assert.equal(satisfies('1.9.9', '^1'), true);
  assert.equal(satisfies('2.0.0', '^1'), false);
  assert.equal(satisfies('0.9.9', '^0'), true);
  assert.equal(satisfies('1.0.0', '^0'), false);
  assert.equal(satisfies('1.0.9', '~1'), true);
  assert.equal(satisfies('2.0.0', '~1'), false);
  assert.equal(satisfies('1.2.9', '^1.2'), true);
  assert.equal(satisfies('2.0.0', '^1.2'), false);
});

test('satisfies: comma separated ranges are supported', () => {
  assert.equal(satisfies('1.5.0', '>=1.0.0, <2.0.0'), true);
  assert.equal(satisfies('2.5.0', '>=1.0.0, <2.0.0'), false);
});

// ------------------------------------------------------------ parseEnvFile

test('parseEnvFile: reads KEY=VALUE, skips comments/blank lines, strips quotes', () => {
  const dir = tmpdir('deps-env-');
  const f = path.join(dir, '.env');
  fs.writeFileSync(f, [
    '# comment line',
    'LOCAL_REDIS_URL=redis://127.0.0.1:6379',
    'EMPTY=',
    'QUOTED="hello world"',
    '',
  ].join('\n'));
  const env = parseEnvFile(f);
  assert.equal(env.LOCAL_REDIS_URL, 'redis://127.0.0.1:6379');
  assert.equal(env.QUOTED, 'hello world');
  assert.equal(env.EMPTY, '');
  assert.equal(env['# comment line'], undefined);
});

test('parseEnvFile: missing file returns empty object', () => {
  assert.deepEqual(parseEnvFile(path.join(tmpdir('deps-env-'), 'nope.env')), {});
});

// --------------------------------------------------------- readCoreManifest

test('readCoreManifest: merges dependencies + optionalDependencies (optional wins)', () => {
  const dir = tmpdir('deps-manifest-');
  fs.writeFileSync(path.join(dir, 'package.json'), JSON.stringify({
    name: 'core',
    dependencies: { a: '^1.0.0', b: '^2.0.0' },
    optionalDependencies: { a: '^3.0.0' },
  }));
  const m = readCoreManifest(dir);
  assert.equal(m.source, 'root');
  assert.deepEqual(m.dependencies, { a: '^3.0.0', b: '^2.0.0' });
  assert.equal(m.raw.name, 'core');
});

test('readCoreManifest: falls back to danmu_api/package.json when root missing', () => {
  const dir = tmpdir('deps-manifest-');
  fs.mkdirSync(path.join(dir, 'danmu_api'));
  fs.writeFileSync(path.join(dir, 'danmu_api', 'package.json'), JSON.stringify({ dependencies: { x: '1.0.0' } }));
  const m = readCoreManifest(dir);
  assert.equal(m.source, 'core');
  assert.deepEqual(m.dependencies, { x: '1.0.0' });
});

test('readCoreManifest: no manifest anywhere → null dependencies with manifest_not_found', () => {
  const dir = tmpdir('deps-manifest-');
  const m = readCoreManifest(dir);
  assert.equal(m.dependencies, null);
  assert.equal(m.error, 'manifest_not_found');
});

test('readCoreManifest: invalid package.json fails closed', () => {
  const dir = tmpdir('deps-manifest-');
  fs.writeFileSync(path.join(dir, 'package.json'), 'not json {');
  const m = readCoreManifest(dir);
  assert.equal(m.dependencies, null);
  assert.equal(m.error, 'invalid_package_json');
});

test('readCoreManifest: explicit bootstrap fallback is used last', () => {
  const dir = tmpdir('deps-manifest-');
  const boot = path.join(tmpdir('deps-boot-'), 'package.json');
  fs.writeFileSync(boot, JSON.stringify({ dependencies: { z: '^1.0.0' } }));
  const m = readCoreManifest(dir, boot);
  assert.equal(m.source, 'bootstrap');
  assert.deepEqual(m.dependencies, { z: '^1.0.0' });
});

// ------------------------------------------------------------- inspectCore

test('inspectCore: healthy core with all deps installed', () => {
  const core = makeCoreRoot({ deps: {
    'node-fetch': '^3.3.2', pako: '^2.1.0', chokidar: '^4.0.3', dotenv: '^16.4.7', esbuild: '^0.25.10',
  } });
  const nm = makeNodeModules({
    'node-fetch': '3.3.2', pako: '2.1.0', chokidar: '4.0.3', dotenv: '16.4.7', esbuild: '0.25.10',
  });
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: null });
  assert.equal(r.healthy, true);
  assert.equal(r.blocked, false);
  assert.deepEqual(r.missing, []);
  assert.deepEqual(r.incompatible, []);
  // chokidar/dotenv 是运行时依赖（server.js 顶层 import），esbuild 才是构建期
  assert.equal(r.required.length, 4);
  assert.ok(r.required.every((e) => e.compatible === true));
  assert.deepEqual(r.ignored.map((i) => i.name), ['esbuild']);
});

test('inspectCore: missing required dep is listed and blocks', () => {
  const core = makeCoreRoot({ deps: { 'node-fetch': '^3.3.2' } });
  const nm = makeNodeModules({});
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: null });
  assert.equal(r.healthy, false);
  assert.equal(r.blocked, true);
  assert.deepEqual(r.missing, ['node-fetch']);
});

test('inspectCore: incompatible version is reported with details and blocks', () => {
  const core = makeCoreRoot({ deps: { 'node-fetch': '^3.0.0' } });
  const nm = makeNodeModules({ 'node-fetch': '2.6.9' });
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: null });
  assert.equal(r.healthy, false);
  assert.deepEqual(r.incompatible, [
    { name: 'node-fetch', spec: '^3.0.0', installed: '2.6.9', reason: 'version_mismatch' },
  ]);
});

test('inspectCore: unsupported range fails closed as incompatible', () => {
  const core = makeCoreRoot({ deps: { 'node-fetch': 'latest' } });
  const nm = makeNodeModules({ 'node-fetch': '3.3.2' });
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: null });
  assert.equal(r.healthy, false);
  assert.equal(r.incompatible.length, 1);
  assert.equal(r.incompatible[0].reason, 'unparseable_range');
});

test('inspectCore: package dir without package.json fails closed', () => {
  const core = makeCoreRoot({ deps: { pako: '^2.1.0' } });
  const nm = makeNodeModules({ pako: null });
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: null });
  assert.equal(r.healthy, false);
  assert.equal(r.incompatible.length, 1);
  assert.equal(r.incompatible[0].reason, 'missing_package_json');
});

test('inspectCore: invalid installed version fails closed', () => {
  const core = makeCoreRoot({ deps: { pako: '^2.1.0' } });
  const nm = makeNodeModules({ pako: 'not-a-version' });
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: null });
  assert.equal(r.healthy, false);
  assert.equal(r.incompatible.length, 1);
  assert.equal(r.incompatible[0].reason, 'invalid_installed_version');
});

test('inspectCore: redis is not required when LOCAL_REDIS_URL is empty', () => {
  const core = makeCoreRoot({ deps: { redis: '^4.0.0' } });
  const nm = makeNodeModules({});
  const env = path.join(tmpdir('deps-env-'), '.env');
  fs.writeFileSync(env, 'PORT=8080\n');
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: env });
  assert.equal(r.healthy, true);
  assert.equal(r.blocked, false);
  assert.equal(r.env.localRedisUrl, false);
  assert.deepEqual(r.missing, []);
  const cond = r.conditional.find((c) => c.name === 'redis');
  assert.ok(cond);
  assert.equal(cond.required, false);
});

test('inspectCore: redis is required and blocks when LOCAL_REDIS_URL is set', () => {
  const core = makeCoreRoot({ deps: { redis: '^4.0.0' } });
  const nm = makeNodeModules({});
  const env = path.join(tmpdir('deps-env-'), '.env');
  fs.writeFileSync(env, 'LOCAL_REDIS_URL=redis://127.0.0.1:6379\n');
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: env });
  assert.equal(r.healthy, false);
  assert.equal(r.blocked, true);
  assert.equal(r.env.localRedisUrl, true);
  assert.deepEqual(r.missing, ['redis']);
  const cond = r.conditional.find((c) => c.name === 'redis');
  assert.equal(cond.required, true);
});

test('inspectCore: redis present and compatible when active', () => {
  const core = makeCoreRoot({ deps: { redis: '^4.0.0' } });
  const nm = makeNodeModules({ redis: '4.7.1' });
  const env = path.join(tmpdir('deps-env-'), '.env');
  fs.writeFileSync(env, 'LOCAL_REDIS_URL=redis://127.0.0.1:6379\n');
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: env });
  assert.equal(r.healthy, true);
  const cond = r.conditional.find((c) => c.name === 'redis');
  assert.equal(cond.required, true);
  assert.equal(cond.compatible, true);
});

test('inspectCore: missing core manifest blocks with structured error', () => {
  const core = makeCoreRoot({ manifest: false });
  const nm = makeNodeModules({});
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: null });
  assert.equal(r.healthy, false);
  assert.equal(r.blocked, true);
  assert.ok(r.errors.includes('manifest_not_found'));
  assert.equal(r.manifest.source, null);
});

test('inspectCore: invalid core manifest blocks with structured error', () => {
  const core = tmpdir('deps-core-');
  fs.writeFileSync(path.join(core, 'package.json'), 'not json {');
  const nm = makeNodeModules({});
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: null });
  assert.equal(r.healthy, false);
  assert.ok(r.errors.includes('invalid_package_json'));
});

test('inspectCore: optionalDependencies participate in classification', () => {
  const core = makeCoreRoot({ optionalDeps: { 'node-fetch': '^3.3.2' } });
  const nm = makeNodeModules({ 'node-fetch': '3.3.2' });
  const r = inspectCore({ coreRoot: core, nodeModulesDir: nm, envFile: null });
  assert.equal(r.healthy, true);
  assert.equal(r.required.length, 1);
  assert.equal(r.required[0].name, 'node-fetch');
});

// ------------------------------------------------------------------- CLI

test('CLI inspect: healthy → exit 0 and JSON contains all contract keys', () => {
  const core = makeCoreRoot({ deps: { pako: '^2.1.0' } });
  const nm = makeNodeModules({ pako: '2.1.0' });
  const res = spawnSync(process.execPath, [RUNTIME_DEPS_MJS, 'inspect', '--core-root', core, '--node-modules', nm], { encoding: 'utf8' });
  assert.equal(res.status, 0, res.stdout + res.stderr);
  const out = JSON.parse(res.stdout);
  for (const k of ['required', 'conditional', 'ignored', 'missing', 'incompatible', 'healthy']) {
    assert.ok(k in out, `missing key ${k}`);
  }
  assert.equal(out.healthy, true);
});

test('CLI inspect: blocked → non-zero exit with healthy:false', () => {
  const core = makeCoreRoot({ deps: { pako: '^2.1.0' } });
  const nm = makeNodeModules({});
  const res = spawnSync(process.execPath, [RUNTIME_DEPS_MJS, 'inspect', '--core-root', core, '--node-modules', nm], { encoding: 'utf8' });
  assert.notEqual(res.status, 0);
  assert.equal(JSON.parse(res.stdout).healthy, false);
});

test('CLI inspect: missing required args → exit 2', () => {
  const res = spawnSync(process.execPath, [RUNTIME_DEPS_MJS, 'inspect'], { encoding: 'utf8' });
  assert.equal(res.status, 2);
});

test('CLI inspect: --env-file drives conditional redis evaluation', () => {
  const core = makeCoreRoot({ deps: { redis: '^4.0.0' } });
  const nm = makeNodeModules({});
  const env = path.join(tmpdir('deps-env-'), '.env');
  fs.writeFileSync(env, 'LOCAL_REDIS_URL=redis://127.0.0.1:6379\n');
  const res = spawnSync(process.execPath, [RUNTIME_DEPS_MJS, 'inspect', '--core-root', core, '--node-modules', nm, '--env-file', env], { encoding: 'utf8' });
  assert.notEqual(res.status, 0);
  assert.deepEqual(JSON.parse(res.stdout).missing, ['redis']);
});

function makeSmokeCore(deps) {
  const core = makeCoreRoot({ deps });
  fs.writeFileSync(path.join(core, 'danmu_api', 'worker.js'), [
    "import fetch from 'node-fetch';",
    'if (typeof fetch !== \'function\') process.exit(9);',
    'console.log("SMOKE_OK");',
    'process.exit(0);',
    '',
  ].join('\n'));
  return core;
}

test('CLI smoke: real import of worker.js succeeds with the module node_modules', () => {
  const core = makeSmokeCore({ 'node-fetch': '^3.3.2' });
  const res = spawnSync(process.execPath, [RUNTIME_DEPS_MJS, 'smoke', '--core-root', core, '--node-modules', MODULE_APP_NODE_MODULES], {
    encoding: 'utf8', timeout: 30000,
  });
  assert.equal(res.status, 0, `stdout=${res.stdout} stderr=${res.stderr}`);
});

test('CLI smoke: missing dependency → non-zero exit', () => {
  const core = makeSmokeCore({ 'node-fetch': '^3.3.2' });
  const nm = makeNodeModules({});
  const res = spawnSync(process.execPath, [RUNTIME_DEPS_MJS, 'smoke', '--core-root', core, '--node-modules', nm], {
    encoding: 'utf8', timeout: 30000,
  });
  assert.notEqual(res.status, 0);
});

test('CLI smoke: does not modify core source', () => {
  const core = makeSmokeCore({ 'node-fetch': '^3.3.2' });
  const worker = path.join(core, 'danmu_api', 'worker.js');
  const before = fs.readFileSync(worker, 'utf8');
  const pkgBefore = fs.readFileSync(path.join(core, 'package.json'), 'utf8');
  const res = spawnSync(process.execPath, [RUNTIME_DEPS_MJS, 'smoke', '--core-root', core, '--node-modules', MODULE_APP_NODE_MODULES], {
    encoding: 'utf8', timeout: 30000,
  });
  assert.equal(res.status, 0, res.stdout + res.stderr);
  assert.equal(fs.readFileSync(worker, 'utf8'), before);
  assert.equal(fs.readFileSync(path.join(core, 'package.json'), 'utf8'), pkgBefore);
});

test('CLI smoke: missing worker.js → non-zero exit', () => {
  const core = makeCoreRoot({ deps: { 'node-fetch': '^3.3.2' } });
  fs.rmSync(path.join(core, 'danmu_api', 'worker.js'));
  const res = spawnSync(process.execPath, [RUNTIME_DEPS_MJS, 'smoke', '--core-root', core, '--node-modules', MODULE_APP_NODE_MODULES], {
    encoding: 'utf8', timeout: 30000,
  });
  assert.notEqual(res.status, 0);
});
