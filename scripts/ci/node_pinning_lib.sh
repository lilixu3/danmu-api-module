#!/usr/bin/env bash
# Termux node 下载的供应链加固：版本固定 + SHA256 校验。
# 由 build_modules.sh 与 test_build_node_pinning.sh source 使用，无独立执行逻辑。

# 固定 Node 主版本（与 scripts/ci/termux-node-aarch64.lock.json 一致）。
# 构建期版本/SHA 权威来源是锁文件；此常量用于快速契约断言。
NODE_PINNED_VERSION="${NODE_PINNED_VERSION:-26.4.0}"

# 从 Debian Packages 索引解析指定包的字段值（支持行续接）。
# 用法: pkg_field <index-file> <package> <Field>
pkg_field() {
  local index="$1" pkg="$2" field="$3"
  awk -v pkg="$pkg" -v field="$field" '
    BEGIN { RS = ""; FS = "\n" }
    {
      hit = 0
      for (i = 1; i <= NF; i++) {
        if ($i == "Package: " pkg) { hit = 1; break }
      }
      if (hit) {
        prefix = field ": "
        for (i = 1; i <= NF; i++) {
          if (index($i, prefix) == 1) {
            val = substr($i, length(prefix) + 1)
            for (j = i + 1; j <= NF && substr($j, 1, 1) == " "; j++) {
              val = val " " substr($j, 2)
            }
            print val
            exit
          }
        }
      }
    }' "$index"
}

# 校验 Termux 包版本与固定版本一致（允许 -<revision> 后缀）。
# 返回 0=通过；1=漂移/为空。
check_node_version_pinned() {
  local version_line="$1"
  [ -n "$version_line" ] || return 1
  case "$version_line" in
    "${NODE_PINNED_VERSION}" | "${NODE_PINNED_VERSION}-"*) return 0 ;;
  esac
  return 1
}

# 校验下载文件 SHA-256 与索引声明一致。缺失期望哈希一律失败（fail-closed）。
verify_file_sha256() {
  local file="$1" expected="$2"
  [ -n "$expected" ] || return 1
  [ -f "$file" ] || return 1
  local actual
  actual="$(sha256sum "$file" 2>/dev/null | awk '{print $1}')"
  [ -n "$actual" ] && [ "$actual" = "$expected" ]
}
