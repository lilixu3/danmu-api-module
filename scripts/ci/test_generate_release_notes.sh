#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
  echo "测试失败: $*" >&2
  exit 1
}

mkdir -p "$TMP_DIR/scripts/ci" "$TMP_DIR/module_template"
cp "$SCRIPT_DIR/common.sh" "$TMP_DIR/scripts/ci/common.sh"
cp "$SCRIPT_DIR/generate_release_notes.sh" "$TMP_DIR/scripts/ci/generate_release_notes.sh"
chmod +x "$TMP_DIR/scripts/ci/"*.sh

cat > "$TMP_DIR/module_template/module.prop" <<'EOF'
version=1.0.0
versionCode=1000000
EOF

git -C "$TMP_DIR" init -q
git -C "$TMP_DIR" config user.email "ci-test@example.invalid"
git -C "$TMP_DIR" config user.name "CI Test"

printf 'initial\n' > "$TMP_DIR/file.txt"
git -C "$TMP_DIR" add file.txt module_template/module.prop scripts/ci/common.sh scripts/ci/generate_release_notes.sh
git -C "$TMP_DIR" commit -q -m "chore: 初始发布"
git -C "$TMP_DIR" tag v1.0.0

printf 'feature\n' >> "$TMP_DIR/file.txt"
git -C "$TMP_DIR" add file.txt
git -C "$TMP_DIR" commit -q \
  -m "feat: 增加功能" \
  -m "第一段提交说明" \
  -m "- 第二段列表说明"

notes_path="$TMP_DIR/build/ci/out/release-notes.md"
"$TMP_DIR/scripts/ci/generate_release_notes.sh" \
  --version 1.0.1 \
  --tag v1.0.1 \
  --previous-tag v1.0.0 \
  --repo owner/repo \
  --output "$notes_path" >/dev/null

grep -F -- "- feat: 增加功能" "$notes_path" >/dev/null \
  || fail "发布说明缺少提交标题"
grep -F -- "第一段提交说明" "$notes_path" >/dev/null \
  || fail "发布说明缺少提交正文第一段"
grep -F -- "- 第二段列表说明" "$notes_path" >/dev/null \
  || fail "发布说明缺少提交正文列表项"

echo "test_generate_release_notes.sh: OK"
