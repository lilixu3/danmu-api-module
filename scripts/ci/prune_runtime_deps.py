#!/usr/bin/env python3
"""从完整 npm 闭包生成 Magisk 模块离线引导依赖树（module_template/app/node_modules）。

策略与 App danmu-api-runtime-packs 的 android-runtime-policy 对齐：
- 包级白名单 APPROVED_PACKAGES（含传递依赖），排除 dan-any 的 PGlite/drizzle 可选实现；
- 包内容整体复制，仅对明确列出的包应用文件保留白名单（opencc-js 等大包）；
- 不递归剪 src/test/README 等目录，避免破坏包的 exports 结构与深层导入。

运行方式（CI 打包前）：
  python3 scripts/ci/prune_runtime_deps.py \
    --source <完整npm闭包node_modules> --lock <package-lock.json> --dest <引导树node_modules>
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

APPROVED_PACKAGES: dict[str, str] = {
    "node_modules/@bufbuild/protobuf": "2.13.0",
    "node_modules/@dan-uni/dan-any": "2.3.9",
    "node_modules/@noble/hashes": "2.2.0",
    "node_modules/@nodable/entities": "3.0.0",
    "node_modules/agent-base": "7.1.4",
    "node_modules/anynum": "1.0.1",
    "node_modules/base64-js": "1.5.1",
    "node_modules/bignumber.js": "9.3.1",
    "node_modules/brotli": "1.3.3",
    "node_modules/chokidar": "4.0.3",
    "node_modules/data-uri-to-buffer": "4.0.1",
    "node_modules/debug": "4.4.3",
    "node_modules/dotenv": "16.4.7",
    "node_modules/fast-xml-builder": "1.3.0",
    "node_modules/fast-xml-parser": "5.10.1",
    "node_modules/fetch-blob": "3.2.0",
    "node_modules/formdata-polyfill": "4.0.10",
    "node_modules/https-proxy-agent": "7.0.6",
    "node_modules/is-unsafe": "2.0.0",
    "node_modules/json-bigint": "1.0.0",
    "node_modules/ms": "2.1.3",
    "node_modules/node-domexception": "1.0.0",
    "node_modules/node-fetch": "3.3.2",
    "node_modules/opencc-js": "1.4.1",
    "node_modules/pako": "2.1.0",
    "node_modules/path-expression-matcher": "1.6.2",
    "node_modules/readdirp": "4.1.1",
    "node_modules/strnum": "2.4.1",
    "node_modules/web-streams-polyfill": "3.3.3",
    "node_modules/xml-naming": "0.3.0",
    "node_modules/zod": "4.4.3",
}

EXCLUDED_PACKAGES: dict[str, str] = {
    "node_modules/@electric-sql/pglite": "0.5.4",
    "node_modules/@electric-sql/pglite-tools": "0.4.4",
    "node_modules/drizzle-orm": "1.0.0-rc.4-de6c356",
}

# 仅对大包做文件级白名单（与 App android-runtime-policy retainedPackageFiles 对齐）
RETAINED_PACKAGE_FILES: dict[str, set[str]] = {
    "node_modules/opencc-js": {
        "LICENSE",
        "LICENSES/Apache-2.0.txt",
        "THIRD_PARTY_LICENSES.md",
        "dist/esm-lib/core.js",
        "dist/esm-lib/dict/STCharacters.js",
        "dist/esm-lib/dict/TSCharacters.js",
        "dist/esm-lib/dict/TSPhrases.js",
        "dist/esm-lib/dict/TWVariants.js",
        "dist/esm-lib/dict/TWVariantsPhrases.js",
        "dist/esm-lib/to/cn.js",
        "dist/esm-lib/to/tw.js",
        "package.json",
    },
}


def copy_package(src_root: Path, pkg_path: str, dst_root: Path) -> None:
    rel = pkg_path.removeprefix("node_modules/").lstrip("/")
    src = src_root / rel
    dst = dst_root / rel
    if not src.is_dir():
        print(f"warn: missing package dir {pkg_path}", file=sys.stderr)
        return
    retained = RETAINED_PACKAGE_FILES.get(pkg_path)
    if retained is not None:
        for file_rel in sorted(retained):
            s = src / file_rel
            if not s.is_file():
                print(f"warn: retained file missing {pkg_path}/{file_rel}", file=sys.stderr)
                continue
            d = dst / file_rel
            d.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(s, d)
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst, symlinks=False)


def parse_lockfile(lock_path: Path) -> dict[str, str]:
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    root = lock.get("packages", {}).get("", {})
    merged: dict[str, str] = {}
    for field in ("dependencies", "optionalDependencies"):
        merged.update(root.get(field) or {})
    return merged


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path, help="完整 npm 闭包 node_modules 目录")
    parser.add_argument("--lock", required=True, type=Path, help="package-lock.json 路径")
    parser.add_argument("--dest", required=True, type=Path, help="输出 node_modules 目录")
    args = parser.parse_args()

    deps = parse_lockfile(args.lock)
    src = args.source
    dst = args.dest
    if dst.exists():
        shutil.rmtree(dst)
    dst.mkdir(parents=True, exist_ok=True)

    pkg_json = json.loads(args.lock.with_name("package.json").read_text(encoding="utf-8"))
    expected: dict[str, str] = {}
    for field in ("dependencies", "optionalDependencies"):
        expected.update(pkg_json.get(field) or {})
    for name, spec in expected.items():
        if name not in deps:
            print(f"error: lockfile missing root dep {name}", file=sys.stderr)
            return 1
        if deps[name] != spec:
            print(f"error: lockfile dep {name} mismatch: {deps[name]} != {spec}", file=sys.stderr)
            return 1
    for name in deps:
        if name not in expected:
            print(f"error: lockfile has undeclared root dep {name}", file=sys.stderr)
            return 1

    for pkg_path, version in APPROVED_PACKAGES.items():
        copy_package(src, pkg_path, dst)

    for pkg_path, version in EXCLUDED_PACKAGES.items():
        if (dst / pkg_path).exists():
            print(f"error: excluded package present: {pkg_path}", file=sys.stderr)
            return 1

    print(f"copied {len(APPROVED_PACKAGES)} packages to {dst}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
