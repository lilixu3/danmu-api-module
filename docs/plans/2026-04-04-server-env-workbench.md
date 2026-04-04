# 服务端环境页工作台化改造 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将“服务端环境”页面从全量展开的配置清单重构为移动端分类切换式工作台，同时保留管理员模式、系统动作、环境变量维护等全部能力。

**Architecture:** 继续沿用现有设置页的沉浸式外层与面板组件，不改数据源和后端接口，只重组 `ServerEnvScreen()` 内的布局结构与变量项交互。环境变量区域改为“分类切换 + 紧凑列表 + 按需展开”的两层结构，管理员与系统动作区域保持在列表上方，作为固定的运维入口。

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing `SettingsUi` design primitives, `ManagerViewModel`

---

### Task 1: 重组服务端环境页的顶层结构

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsToolScreens.kt`

**Step 1: 调整 `ServerEnvScreen()` 的页面状态组织**

- 增加当前分类选择状态
- 基于 `serverConfig.categorizedEnvVars` 生成稳定分类列表
- 为当前展开的环境变量增加局部展开状态

**Step 2: 重写页面主体结构**

- 保留沉浸头部
- 将管理员模式卡压缩为状态卡 + 输入区
- 将系统动作卡压缩为操作区 + 摘要信息区
- 将服务通知放入更轻量的提示区

**Step 3: 运行 Kotlin 编译检查**

Run:

```bash
./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :app:compileDebugKotlin
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 2: 实现分类切换工作台与紧凑变量项

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsToolScreens.kt`

**Step 1: 新增分类切换器组件**

- 参考记录页的胶囊切换器，但复用当前 settings 页的 palette
- 每个分类显示名称与数量摘要
- 切换后只渲染当前分类变量

**Step 2: 将 `EnvVarRow()` 改为紧凑行卡片**

- 收起态展示：变量名、类型、说明、当前值摘要
- 移除常驻“编辑 / 删除”文字按钮
- 用次级操作入口或展开区承载维护动作

**Step 3: 新增变量展开内容**

- 展开后显示完整值
- 有 options 时显示 chips
- 在展开区放置编辑与删除操作

**Step 4: 运行 Assemble Debug**

Run:

```bash
./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 3: 收口视觉细节与空态

**Files:**
- Modify: `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsToolScreens.kt`

**Step 1: 收口加载 / 错误 / 空分类状态**

- 保持现有加载态与失败态完整
- 分类为空时给出紧凑提示
- 当前分类丢失时自动回退到首个分类

**Step 2: 调整文案和信息密度**

- 缩短副标题与说明
- 控制标签数量
- 统一间距与分隔节奏

**Step 3: 最终验证**

Run:

```bash
./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

**Step 4: Commit**

```bash
git add docs/plans/2026-04-04-server-env-workbench.md \
  manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/settings/SettingsToolScreens.kt
git commit -m "feat: 重构服务端环境页工作台"
```
