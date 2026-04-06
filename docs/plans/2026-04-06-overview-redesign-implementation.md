# Overview Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 重构首页为控制台型首页，去掉重复信息，保留现有配色体系。

**Architecture:** 继续使用现有 `OverviewScreen.kt` 和 Compose 组件，不引入新的主题层。重排首页为顶部栏、主控制卡、访问卡、系统信息卡、管理入口卡，并收敛状态展示位置。

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Overview colors/components.

---

### Task 1: 盘点现有首页组件
- 查看 `manager_app_redesign/manager_app/app/src/main/java/com/danmuapi/manager/feature/overview/OverviewScreen.kt`
- 识别哪些组件可复用、哪些需要改造或删除

### Task 2: 重排首页骨架
- 调整 `OverviewScreen` 主体顺序
- 顶部栏仅保留标题和设置
- 将主控制卡、访问卡、系统信息卡、管理入口卡按新顺序排列

### Task 3: 收敛重复信息
- 服务状态只出现在主控制卡
- 地址只出现在访问卡
- 参数只出现在系统信息卡
- 跳转能力只出现在管理入口卡

### Task 4: 编译验证
- 运行 `./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :app:assembleDebug`
- 确认 APK 产物存在
