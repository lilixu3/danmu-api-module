# API 调试页重构 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将“API 调试”页从通用请求器重构为核心前端思路的移动端测试台，包含“接口调试”和“弹幕测试”两条主路径。

**Architecture:** 基于现有 `SettingsToolScreens.kt` 页面外壳和 `ManagerViewModel.requestDanmuApi()` 请求能力，在 App 侧新增预设接口配置、响应解析模型和弹幕测试流程状态。接口调试采用配置驱动表单；弹幕测试采用“自动匹配”与“手动匹配页内步骤切换”双链路，不新增后端接口。

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing `SettingsUi` primitives, `ManagerViewModel`, `org.json`

---

### Task 1: 建立 API 调试配置与状态模型

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/state/ManagerViewModel.kt`
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/model/LogModels.kt`
- Modify or Create: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsToolScreens.kt`

**Step 1: 新增预设接口配置模型**

- 定义接口 key、名称、方法、路径、字段列表
- 字段支持文本、选择项、路径参数、JSON Body

**Step 2: 新增弹幕测试所需的轻量数据模型**

- 搜索结果
- 番剧详情
- 剧集条目
- 弹幕统计 / 预览项

**Step 3: 在 ViewModel 中新增 API 测试状态**

- 当前接口调试结果
- 自动匹配测试结果
- 手动匹配搜索结果
- 当前步骤与当前选中番剧 / 剧集

**Step 4: 运行 Kotlin 编译检查**

Run:

```bash
./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :app:compileDebugKotlin
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 2: 实现 ViewModel 的预设接口调试与弹幕测试逻辑

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/state/ManagerViewModel.kt`

**Step 1: 用预设配置替换当前通用请求拼装入口**

- 保留底层 `requestDanmuApi()`
- 上层新增根据接口配置自动构建路径、查询参数、请求体的逻辑

**Step 2: 实现自动匹配测试链路**

- 调用 `/api/v2/match`
- 提取最佳结果
- 再调用 `/api/v2/comment/:id`
- 生成结果摘要与弹幕预览

**Step 3: 实现手动匹配测试链路**

- 搜索动漫
- 获取番剧详情
- 获取剧集
- 获取弹幕
- 支持步骤前进与回退

**Step 4: 做基本异常处理**

- 空结果
- HTTP 失败
- JSON 解析失败
- 数据字段缺失

### Task 3: 重构 Compose 页面结构

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsToolScreens.kt`

**Step 1: 重构一级结构**

- 一级切换：接口调试 / 弹幕测试
- 保持沉浸式头部

**Step 2: 重构接口调试页签**

- 接口选择器
- 配置驱动参数表单
- 发送按钮
- 结果工作台

**Step 3: 重构弹幕测试页签**

- 自动匹配 / 手动匹配切换
- 手动匹配页内步骤切换：搜索 / 番剧 / 剧集 / 结果
- 统一结果工作台

**Step 4: 收口视觉细节**

- 减少说明文案
- 保持高信息密度
- 动作按钮与结果区层级清晰

### Task 4: 验证与提交

**Files:**
- Modify: `manager_app_redesign/manager_app/app/build.gradle.kts` (if needed for resources only)
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/state/ManagerViewModel.kt`
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/model/LogModels.kt`
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsToolScreens.kt`

**Step 1: 运行 Assemble Debug**

Run:

```bash
./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

**Step 2: Commit**

```bash
git add docs/plans/2026-04-04-api-debug-design.md \
  docs/plans/2026-04-04-api-debug-workbench.md \
  manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/app/state/ManagerViewModel.kt \
  manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/core/model/LogModels.kt \
  manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsToolScreens.kt
git commit -m "feat: 重构 API 调试页"
```
