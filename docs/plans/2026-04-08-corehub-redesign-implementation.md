# CoreHub Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将核心页重构为列表控制台布局，压缩当前核心区域并突出核心列表。

**Architecture:** 保持现有 `CoreHubScreen.kt` 为主承载文件，不改数据来源与导航流。通过抽出轻量展示格式化 helper + 重排 Compose 组件顺序，完成方案 B 的视觉落地，同时保留现有详情页与安装弹层行为。

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing immersive palette, JUnit4 unit tests.

---

### Task 1: 为核心页文案与状态摘要补充可测试 helper

**Files:**
- Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreHubSummaryFormatter.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/corehub/CoreHubSummaryFormatterTest.kt`

- [ ] **Step 1: 写失败中的测试**
- [ ] **Step 2: 运行指定测试，确认失败**
- [ ] **Step 3: 实现最小 formatter，覆盖当前核心状态条与面板摘要文案**
- [ ] **Step 4: 重新运行测试，确认通过**

### Task 2: 重构核心页顶部与当前核心区域

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreHubScreen.kt`

- [ ] **Step 1: 缩小 `CoreHubHeader` 字号、间距与状态 pill 尺寸**
- [ ] **Step 2: 将 `CurrentCoreHeroCard` 改为紧凑状态条布局**
- [ ] **Step 3: 保留无核心时的安装引导与已有交互行为**

### Task 3: 把搜索 / 筛选 / 核心操作合并为控制工具区

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreHubScreen.kt`

- [ ] **Step 1: 将“检查全部更新 / 安装新核心”并入列表面板顶部控制区**
- [ ] **Step 2: 收紧搜索框、筛选器与区块圆角 / 高度**
- [ ] **Step 3: 移除旧的大按钮行，保证列表区成为主视觉中心**

### Task 4: 扁平化核心列表行样式

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreHubScreen.kt`

- [ ] **Step 1: 缩小列表条目高度、标题与次级信息字号**
- [ ] **Step 2: 收敛状态标签样式，使当前 / 可更新 / 已安装更精致**
- [ ] **Step 3: 保持点击详情与状态排序逻辑不变**

### Task 5: 验证

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/corehub/CoreHubScreen.kt`
- Test: `manager_app_redesign/manager_app/app/src/test/java/com/danmuapi/manager/feature/corehub/CoreHubSummaryFormatterTest.kt`

- [ ] **Step 1: 运行 `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.danmuapi.manager.feature.corehub.CoreHubSummaryFormatterTest"`**
- [ ] **Step 2: 运行 `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :app:assembleDebug`**
- [ ] **Step 3: 检查输出并记录结果**
