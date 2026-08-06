#!/usr/bin/env node
// runtime-deps.mjs — core runtime dependency inspection (Node built-ins only).
//
// CLI:
//   runtime-deps.mjs inspect --core-root <dir> --node-modules <dir> [--env-file <file>]
//       JSON on stdout; exit 0 = healthy, 1 = dependency-blocked, 2 = usage error.
//   runtime-deps.mjs smoke --core-root <dir> --node-modules <dir>
//       Really imports <core-root>/danmu_api/worker.js against <dir> via a temp
//       symlink farm; exit 0 = import ok, non-zero = failure. Never modifies the core.
//
// Classification:
//   - esbuild                      -> knownNonRuntime (build-time, ignored)
//   - redis                        -> conditional (required only when the .env file
//                                      has a non-empty LOCAL_REDIS_URL)
//   - everything else              -> required
// Note: chokidar / dotenv are RUNTIME deps (danmu_api/server.js imports them at
// top level) and must be treated as required like any other.
//
// Version ranges: exact, ^, ~, >=, compound (">=1.0.0 <2.0.0"), *. Anything that
// cannot be parsed fails closed (treated as incompatible).

import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const KNOWN_NON_RUNTIME = new Set(['esbuild']);
const CONDITIONAL_DEPS = new Set(['redis']);
const ENV_KEY_REDIS = 'LOCAL_REDIS_URL';
const SMOKE_TIMEOUT_MS = 15000;

const DEFAULT_FALLBACK_MANIFEST = path.join(path.dirname(fileURLToPath(import.meta.url)), 'package.json');

const USAGE = `usage:
  runtime-deps.mjs inspect --core-root <dir> --node-modules <dir> [--env-file <file>]
  runtime-deps.mjs smoke --core-root <dir> --node-modules <dir>`;

// ------------------------------------------------------------ classification

export function classifyDependency(name) {
  if (KNOWN_NON_RUNTIME.has(name)) return 'knownNonRuntime';
  if (CONDITIONAL_DEPS.has(name)) return 'conditional';
  return 'required';
}

// -------------------------------------------------------------- version math

export function parseVersion(v) {
  if (typeof v !== 'string') return null;
  const s = v.trim().replace(/^v/, '');
  const m = /^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:[-+].*)?$/.exec(s);
  if (!m) return null;
  return [Number(m[1]), Number(m[2] ?? 0), Number(m[3] ?? 0)];
}

function cmpVersion(a, b) {
  for (let i = 0; i < 3; i += 1) {
    if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1;
  }
  return 0;
}

function componentCount(t) {
  const stripped = t.replace(/^(>=|<=|>|<|=|~|\^)?v?/, '');
  const parts = stripped.split('.');
  return parts.filter((p) => p !== '').length;
}

export function parseRange(spec) {
  if (typeof spec !== 'string') return null;
  const s = spec.trim();
  if (!s) return null;
  if (/^[*xX]$/.test(s)) return { kind: 'any' };

  // 支持逗号分隔（">=1.0.0, <2.0.0"）与空格分隔的联合比较器
  const tokens = s.split(/[\s,]+/).filter(Boolean);
  const comparators = [];
  for (const t of tokens) {
    const m = /^(>=|<=|>|<|=|~|\^)?v?(\d+)(?:\.(\d+))?(?:\.(\d+))?$/.exec(t);
    if (!m) return null;
    const op = m[1] ?? '=';
    const comps = componentCount(t);
    const ver = [Number(m[2]), Number(m[3] ?? 0), Number(m[4] ?? 0)];
    if (comps === 1 && op === '^' && ver[0] === 0) {
      // ^0 -> >=0.0.0 <1.0.0（npm 语义；补零后的普通 caret 会误判为 <0.0.1）
      comparators.push({ op: '>=', ver });
      comparators.push({ op: '<', ver: [1, 0, 0] });
    } else {
      comparators.push(...expandComparator(op, ver, comps));
    }
  }
  if (comparators.length === 0) return null;
  return { kind: 'comparators', comparators };
}

function expandComparator(op, ver, comps) {
  const [maj, min, pat] = ver;
  const out = [];
  if (op === '=') out.push({ op: '=', ver });
  else if (op === '>') out.push({ op: '>', ver });
  else if (op === '>=') out.push({ op: '>=', ver });
  else if (op === '<') out.push({ op: '<', ver });
  else if (op === '<=') out.push({ op: '<=', ver });
  else if (op === '~') {
    out.push({ op: '>=', ver });
    if (comps >= 2) out.push({ op: '<', ver: [maj, min + 1, 0] });
    else out.push({ op: '<', ver: [maj + 1, 0, 0] });
  } else if (op === '^') {
    out.push({ op: '>=', ver });
    if (maj > 0) out.push({ op: '<', ver: [maj + 1, 0, 0] });
    else if (min > 0) out.push({ op: '<', ver: [0, min + 1, 0] });
    else out.push({ op: '<', ver: [0, 0, comps >= 3 ? pat + 1 : 1] });
  } else {
    return out; // unknown operator -> no comparators (caller treats as unparseable)
  }
  return out;
}

export function satisfies(version, spec) {
  const v = parseVersion(version);
  if (!v) return null; // unparseable installed version -> fail closed
  const r = parseRange(spec);
  if (!r) return null; // unparseable range -> fail closed
  if (r.kind === 'any') return true;
  for (const c of r.comparators) {
    if (!cmpSatisfies(v, c)) return false;
  }
  return true;
}

function cmpSatisfies(v, c) {
  const d = cmpVersion(v, c.ver);
  switch (c.op) {
    case '=': return d === 0;
    case '>': return d > 0;
    case '>=': return d >= 0;
    case '<': return d < 0;
    case '<=': return d <= 0;
    default: return false; // fail closed on unknown operator
  }
}

// ------------------------------------------------------------------- env file

export function parseEnvFile(filePath) {
  const result = {};
  if (!filePath) return result;
  let text;
  try {
    text = fs.readFileSync(filePath, 'utf8');
  } catch {
    return result;
  }
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq <= 0) continue;
    let key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if (value.length >= 2 && ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'")))) {
      value = value.slice(1, -1);
    }
    result[key] = value;
  }
  return result;
}

// ---------------------------------------------------------------- manifest

export function readCoreManifest(coreRoot, fallbackPkgPath = null) {
  const candidates = [
    { source: 'root', p: path.join(coreRoot, 'package.json') },
    { source: 'core', p: path.join(coreRoot, 'danmu_api', 'package.json') },
  ];
  if (fallbackPkgPath) candidates.push({ source: 'bootstrap', p: fallbackPkgPath });

  for (const c of candidates) {
    if (!fs.existsSync(c.p)) continue;
    let pkg;
    try {
      pkg = JSON.parse(fs.readFileSync(c.p, 'utf8'));
    } catch {
      return { source: c.source, dependencies: null, raw: null, error: 'invalid_package_json' };
    }
    const merged = { ...(pkg.dependencies || {}), ...(pkg.optionalDependencies || {}) };
    return { source: c.source, dependencies: merged, raw: pkg };
  }
  return { source: null, dependencies: null, raw: null, error: 'manifest_not_found' };
}

// ------------------------------------------------------------------ inspect

function evaluateDep(name, spec, nodeModulesDir, checkCompat) {
  const base = { name, spec, installed: null, compatible: null, reason: null };
  const pkgDir = path.join(nodeModulesDir, name);
  if (!fs.existsSync(pkgDir)) {
    return { ...base, reason: 'missing' };
  }
  const pkgFile = path.join(pkgDir, 'package.json');
  if (!fs.existsSync(pkgFile)) {
    return { ...base, reason: 'missing_package_json' };
  }
  let pkg;
  try {
    pkg = JSON.parse(fs.readFileSync(pkgFile, 'utf8'));
  } catch {
    return { ...base, reason: 'invalid_package_json' };
  }
  const installedVersion = typeof pkg.version === 'string' ? pkg.version : null;
  if (installedVersion === null || installedVersion === '') {
    return { ...base, installed: installedVersion, reason: 'invalid_installed_version' };
  }
  if (checkCompat === false) {
    return { ...base, installed: installedVersion, compatible: null, reason: null };
  }
  if (parseVersion(installedVersion) === null) {
    return { ...base, installed: installedVersion, compatible: false, reason: 'invalid_installed_version' };
  }
  const ok = satisfies(installedVersion, spec);
  if (ok === null) {
    return { ...base, installed: installedVersion, compatible: false, reason: 'unparseable_range' };
  }
  if (ok) return { ...base, installed: installedVersion, compatible: true, reason: null };
  return { ...base, installed: installedVersion, compatible: false, reason: 'version_mismatch' };
}

export function inspectCore({ coreRoot, nodeModulesDir, envFile, fallbackManifestPath = null }) {
  const env = parseEnvFile(envFile);
  const redisActive = Boolean((env[ENV_KEY_REDIS] || '').trim());
  const manifest = readCoreManifest(coreRoot, fallbackManifestPath);

  const required = [];
  const conditional = [];
  const ignored = [];
  const missing = [];
  const incompatible = [];

  let manifestBlocked = false;
  if (manifest.dependencies === null) {
    manifestBlocked = true;
  } else {
    for (const [name, spec] of Object.entries(manifest.dependencies)) {
      const cls = classifyDependency(name);
      if (cls === 'knownNonRuntime') {
        ignored.push({ name, spec });
        continue;
      }
      if (cls === 'conditional') {
        if (!redisActive) {
          conditional.push({ ...evaluateDep(name, spec, nodeModulesDir, false), required: false });
          continue;
        }
        const entry = evaluateDep(name, spec, nodeModulesDir, true);
        conditional.push({ ...entry, required: true });
        if (!entry.compatible) {
          if (entry.reason === 'missing') missing.push(name);
          else incompatible.push({ name, spec, installed: entry.installed, reason: entry.reason });
        }
        continue;
      }
      const entry = evaluateDep(name, spec, nodeModulesDir, true);
      required.push(entry);
      if (!entry.compatible) {
        if (entry.reason === 'missing') missing.push(name);
        else incompatible.push({ name, spec, installed: entry.installed, reason: entry.reason });
      }
    }
  }

  const blocked = manifestBlocked || missing.length > 0 || incompatible.length > 0;
  return {
    healthy: !blocked,
    blocked,
    manifest: {
      source: manifest.source,
      error: manifest.error || null,
      name: manifest.raw && manifest.raw.name ? manifest.raw.name : null,
      version: manifest.raw && manifest.raw.version ? manifest.raw.version : null,
    },
    env: { localRedisUrl: redisActive },
    required,
    conditional,
    ignored,
    missing,
    incompatible,
    errors: manifestBlocked ? [manifest.error] : [],
  };
}

// -------------------------------------------------------------------- smoke

export function smokeCore({ coreRoot, nodeModulesDir }) {
  // 兼容两种调用布局：coreRoot 为 cores/<id>（根层）或 cores/<id>/danmu_api（内容层）
  const nestedWorker = path.join(coreRoot, 'danmu_api', 'worker.js');
  const directWorker = path.join(coreRoot, 'worker.js');
  const worker = fs.existsSync(nestedWorker) ? nestedWorker
    : fs.existsSync(directWorker) ? directWorker
    : null;
  if (!worker) return 3;
  const coreContentDir = worker === nestedWorker ? path.join(coreRoot, 'danmu_api') : coreRoot;

  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-deps-smoke-'));
  try {
    fs.symlinkSync(nodeModulesDir, path.join(tmp, 'node_modules'));
    // 核心树必须物化复制而非符号链接：--preserve-symlinks-main 只保留主入口，
    // 主模块的相对 import 子模块会被 canonicalize 到 realpath。若 tmp/danmu_api
    // 是指向 live 核心树的符号链接，realpath=live → 子模块裸 import 从 live 的
    // node_modules 解析，staging 从未被验证（round-3 实证：staging 空+live 满=假阳性
    // exit 0；staging 满+live 空=假阴性 exit 1，修复流程死锁 smoke_failed）。
    // 复制后 realpath 落在 tmp 内，裸 import 沿 tmp/danmu_api/node_modules(→staging) 解析。
    // 注意必须排除核心树自带的 node_modules 链接：dereference 复制会把 live 依赖
    // 物化成真实副本塞进 tmp，smoke 又变回验证 live（假阳性/假阴性复现）。
    fs.cpSync(coreContentDir, path.join(tmp, 'danmu_api'), {
      recursive: true,
      dereference: true,
      filter: (src) => !src.split(path.sep).includes('node_modules'),
    });
    fs.symlinkSync(nodeModulesDir, path.join(tmp, 'danmu_api', 'node_modules'));
    const rootPkg = path.join(coreRoot, 'package.json');
    if (fs.existsSync(rootPkg)) {
      fs.copyFileSync(rootPkg, path.join(tmp, 'package.json'));
    }

    // spawnSync + timeout：import 即崩（status != null 且非 0）立刻暴露；
    // 超时说明 worker 仍存活 —— danmu_api worker 是常驻服务（启动即监听），视为健康。
    // 注意：不能在此用异步 spawn 轮询 —— Atomics.wait 阻塞主线程时 exit 事件不派发。
    const res = spawnSync(
      process.execPath,
      ['--preserve-symlinks-main', path.join(tmp, 'danmu_api', path.basename(worker))],
      {
        cwd: tmp,
        timeout: SMOKE_TIMEOUT_MS,
        stdio: ['ignore', 'pipe', 'pipe'],
      },
    );
    if (res.error) {
      if (res.error.code === 'ETIMEDOUT') return 0; // worker kept running (healthy server)
      return 4; // spawn failure
    }
    if (res.status === 0) return 0;
    return res.status || 1;
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

// ---------------------------------------------------------------------- CLI

function parseArgs(argv) {
  const args = { command: null, coreRoot: null, nodeModules: null, envFile: null };
  let i = 0;
  while (i < argv.length) {
    const a = argv[i];
    if (a === 'inspect' || a === 'smoke') {
      args.command = a;
    } else if (a === '--core-root') {
      args.coreRoot = argv[i + 1];
      i += 1;
    } else if (a === '--node-modules') {
      args.nodeModules = argv[i + 1];
      i += 1;
    } else if (a === '--env-file') {
      args.envFile = argv[i + 1];
      i += 1;
    } else {
      return null;
    }
    i += 1;
  }
  return args;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args || !args.command || !args.coreRoot || !args.nodeModules) {
    console.error(USAGE);
    process.exit(2);
  }
  if (args.command === 'inspect') {
    const result = inspectCore({
      coreRoot: args.coreRoot,
      nodeModulesDir: args.nodeModules,
      envFile: args.envFile,
      fallbackManifestPath: DEFAULT_FALLBACK_MANIFEST,
    });
    console.log(JSON.stringify(result));
    process.exit(result.blocked ? 1 : 0);
  }
  if (args.command === 'smoke') {
    process.exit(smokeCore({ coreRoot: args.coreRoot, nodeModulesDir: args.nodeModules }));
  }
  console.error(USAGE);
  process.exit(2);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
