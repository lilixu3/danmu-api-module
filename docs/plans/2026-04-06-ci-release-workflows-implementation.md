# CI / Release Workflows Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将现有单体式 GitHub Actions 拆分为专业化的 CI、Debug 构建、Release 构建、正式发版四类工作流。

**Architecture:** 使用 `scripts/ci/*.sh` 统一承载版本解析、管理器构建、Magisk 模块打包、发布日志生成逻辑；GitHub Actions 只负责触发、环境准备、产物上传与发版动作，避免把大量 Bash 内联在 YAML 中。

**Tech Stack:** GitHub Actions, Bash, Gradle Wrapper, Android SDK, softprops/action-gh-release.

---

### Task 1: 抽离构建脚本
- 新建 `scripts/ci/common.sh`
- 新建 `scripts/ci/build_manager.sh`
- 新建 `scripts/ci/build_modules.sh`
- 新建 `scripts/ci/resolve_version.sh`
- 新建 `scripts/ci/generate_release_notes.sh`

### Task 2: 让 Android 版本号支持工作流注入
- 修改 `manager_app_redesign/manager_app/app/build.gradle.kts`
- 支持通过 `-PappVersionName` / `-PappVersionCode` 覆盖版本，不再在 workflow 里直接改源码

### Task 3: 拆分 GitHub Actions 工作流
- 删除旧的 `build-danmu-magisk.yml`
- 新增 `.github/workflows/ci.yml`
- 新增 `.github/workflows/build-debug.yml`
- 新增 `.github/workflows/build-release.yml`
- 新增 `.github/workflows/release.yml`

### Task 4: 补充文档与仓库卫生
- 新增根目录 `.gitignore` 忽略 `build/`
- 更新 `README.md`，说明新的 Actions 分工与使用方式

### Task 5: 本地验证
- `bash -n scripts/ci/*.sh`
- 解析 `.github/workflows/*.yml`
- 本地构建管理器 Debug / Release
- 本地打包 No-Node / Node 两种模块
