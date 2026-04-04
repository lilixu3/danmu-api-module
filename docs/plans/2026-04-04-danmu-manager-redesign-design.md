# Danmu Manager Redesign Design

## 背景

当前管理器已经使用 Compose 与 Material 3，但整体形态仍然是“在单体页面壳上持续堆功能”。

- 现有 App 已启用 Compose 构建能力，见 `manager_app/manager_app/app/build.gradle.kts`。
- 主导航壳集中在 `manager_app/manager_app/app/src/main/java/com/danmuapi/manager/ui/DanmuManagerApp.kt`。
- 大量业务状态、Root 调用、网络访问、配置读写与模块安装逻辑集中在 `manager_app/manager_app/app/src/main/java/com/danmuapi/manager/ui/MainViewModel.kt`。
- 功能范围已经很完整，覆盖服务控制、多核心管理、日志、配置、WebDAV、更新检查与模块安装，见 `README.md`。

结论不是“把 Compose 换成 Compose + Material”，而是做一次完整的产品层与工程层重建。

## 重构目标

1. 在新目录中全新重写管理器，不污染现有 `manager_app/`。
2. 保持现有功能全覆盖，不丢能力。
3. 重建信息架构，让常用操作更短链路。
4. 重建设计系统与视觉风格，达到“专业命令中心”观感，而不是默认 Material 模板感。
5. 拆分状态层、数据层、Root 调用层，移除超大 ViewModel 与页面耦合。
6. 预留手机/平板自适应导航与双栏布局能力。
7. 最后再切入现有 GitHub Actions 打包链路，降低替换风险。

## 非目标

- 本次不改 Magisk 模块核心脚本的对外协议。
- 本次不改持久化目录结构 `/data/adb/danmu_api_server/`。
- 本次不新增服务端业务能力，只重构管理器端交互与工程结构。
- 本次不直接删除旧管理器目录，旧目录作为回退基线保留。

## 参考来源

本设计参考以下公开产品与设计文档：

- Home Assistant Android Gallery  
  https://companion.home-assistant.io/docs/gallery/android/
- Obtainium  
  https://github.com/ImranR98/Obtainium
- LogFox  
  https://github.com/F0x1d/LogFox
- Material Design 3 Dynamic Tablet Dashboard  
  https://github.com/ElementZoom/Material-Design-3-Dynamic-Tablet-Dashboard
- Android Developers: Adaptive Navigation  
  https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- Android Developers: Search Bar  
  https://developer.android.com/develop/ui/compose/components/search-bar

提炼出的核心原则：

- 总览页要具备“状态总控”属性，不是普通卡片堆叠。
- 核心管理要像应用源管理器，支持搜索、筛选、状态徽标、详情页与分步安装。
- 日志与请求记录要更像专业调试工具，强调可读性、筛选与上下文。
- 设置页要偏“持久配置中心”，危险能力要独立隔离。
- 手机和大屏设备使用不同导航容器，但共享同一套信息架构。

## 设计方向

最终采用“命令中心风”。

### 风格定义

- 关键词：专业、冷静、可信、轻工业、命令中心。
- 视觉目标：不像脚手架 App，不走夸张玻璃态，不走极客终端纯黑风。
- 交互目标：高频动作前置，危险动作明确，技术信息可直接扫读。

### 色彩与材质

- 主色：冷青蓝，承担导航焦点与主操作。
- 成功色：青绿，承担运行中、已同步、可访问。
- 警告色：琥珀，承担可更新、待处理、网络异常。
- 危险色：红色，承担停止、删除、覆盖、恢复。
- 背景：浅色主题偏雾白，深色主题偏石墨灰。
- 卡片：低透明渐变底 + 轻边框 + 轻阴影，避免廉价发光。

### 字体

- 中文主字体：Noto Sans SC。
- 技术文本：JetBrains Mono。

### 动效

- 首页 Hero 卡使用轻量渐变流动与状态光晕。
- 列表筛选、页面切换、卡片展开使用 120ms 到 220ms 的短动效。
- 危险操作不做夸张动画，强调明确反馈。

## 信息架构

一级导航压缩为 4 个主页面：

1. 总览
2. 核心中心
3. 控制台
4. 设置

`关于` 从一级导航移出，放入设置二级页。

### 总览

总览页负责展示当前可执行状态和最短操作路径。

模块组成：

- Hero 服务状态卡
- 当前核心卡
- 访问入口卡
- 任务与提醒卡
- 模块更新卡

关键动作：

- 启动
- 停止
- 重启
- 切换自启
- 检查模块更新
- 切换当前核心

### 核心中心

核心中心是重构重点。

模块组成：

- 顶部 SearchBar
- 筛选 Chips：全部、已安装、当前启用、可更新、自定义来源
- 核心列表
- 核心详情页
- 安装核心分步流程页

关键动作：

- 安装
- 切换
- 检查更新
- 删除
- 查看版本差异
- 查看来源信息

### 控制台

控制台用于“技术视角”的操作与排障。

分段页签：

- 服务日志
- 模块日志
- 请求记录
- API 调试
- 系统环境

关键原则：

- 结构化信息优先，原始文本作为高级模式。
- 日志页面优先做阅读体验，不只是文字堆栈。
- 请求记录页先看统计，再看明细。

### 设置

设置页只保留持久配置与恢复能力。

分区：

- 外观
- 日志与省电
- GitHub 与更新
- WebDAV 备份恢复
- 配置文件
- 应用信息
- 危险操作

## 页面规格

### 1. 总览页

#### Hero 服务状态卡

展示：

- 服务状态 Running / Stopped / Error / Root Missing
- PID
- 模块版本
- Root 可用性
- 当前活动核心

操作：

- 启动
- 停止
- 重启
- 刷新

交互：

- 运行中显示柔和成功色状态条
- 停止显示中性态
- 异常显示警告或错误态

#### 访问入口卡

展示：

- 本机地址
- 局域网地址
- 端口
- TOKEN

操作：

- 复制 TOKEN
- 复制完整 URL
- 打开浏览器

#### 当前核心卡

展示：

- 仓库名
- ref
- 版本
- commit
- 是否有更新

操作：

- 打开核心详情
- 检查更新
- 切换核心

#### 任务与提醒卡

展示：

- 最近一次安装结果
- 最近一次模块安装结果
- Root 授权提示
- 网络/API 限流提示

### 2. 核心中心

#### 核心列表项

一张卡只表达一条核心记录，内容包括：

- 仓库名
- ref
- 版本号
- 短 commit
- 来源类型：预置 / 自定义
- 状态徽标：当前启用 / 可更新 / 失效

默认只展示两个主操作：

- 详情
- 切换或更新

删除放到详情页或溢出菜单，避免列表页按钮噪声。

#### 安装核心流程

采用分步式，不再直接裸露两个输入框。

步骤：

1. 选择来源：预置源 / 自定义
2. 填写仓库与 ref
3. 校验仓库格式
4. 预览将要安装的信息
5. 执行安装

#### 核心详情页

展示：

- 基本信息
- 当前版本
- 最新版本
- 更新差异
- 安全提示

操作：

- 设为当前
- 重新安装
- 删除
- 打开仓库链接

### 3. 控制台

#### 服务日志 / 模块日志

能力：

- 搜索
- 按级别筛选
- 自动滚动
- 清空
- 复制
- 导出

视觉：

- 等宽字体
- 时间戳对齐
- 级别颜色编码
- 长文本支持折叠/展开

#### 请求记录

顶部摘要：

- 今日请求数
- 失败数
- 最近错误接口

列表项：

- 时间
- 方法
- 路径
- 状态码
- 耗时

#### API 调试

能力：

- 预设接口卡片
- 自定义请求体
- 结果卡片化展示

#### 系统环境

默认模式展示结构化字段：

- 环境变量列表
- 缓存状态
- 部署状态

高级模式才显示 `.env` 原文编辑器。

### 4. 设置

#### 外观

- 主题模式：跟随系统 / 浅色 / 深色
- 动态色
- 密度模式：舒展 / 紧凑

#### 日志与省电

- 自动清理周期
- 控制台默认日志行数
- 省电建议说明

#### GitHub 与更新

- GitHub Token
- API 限流说明
- 模块更新策略

#### WebDAV 备份恢复

- 地址
- 用户名
- 密码
- 路径
- 备份
- 恢复

#### 配置文件

- 导出到本地
- 从本地导入
- 原文编辑

#### 关于

- App 版本
- 模块版本
- 构建时间
- 项目地址

## 视觉稿文字版

### 总览页

```text
Danmu API Manager
Running · PID 1234 · Module v1.2.0
[启动] [停止] [重启] [检查更新]

访问入口
127.0.0.1:9321
192.168.1.8:9321
TOKEN · 复制

当前核心
lilixu3/danmu_api
main · v2.4.1 · abc1234
[核心详情] [检查更新]

任务与提醒
最近模块更新可用
GitHub API 接近限流
```

### 核心中心

```text
[SearchBar________________]
[全部][已安装][当前][可更新][自定义]

┌ lilixu3/danmu_api ┐
│ main · v2.4.1 · abc1234 │
│ 当前启用    可更新       │
│ [详情] [更新]            │
└────────────────────────┘
```

### 控制台

```text
[服务日志][模块日志][请求记录][API调试][系统环境]
[搜索][WARN][ERROR][自动滚动]
10:22:03 INFO Server started
10:22:06 WARN GitHub rate limit low
```

## 技术架构

### 目录策略

新工程目录：

- `manager_app_redesign/manager_app/`

保留旧工程：

- `manager_app/manager_app/`

这样可以并行开发、并行验证，直到新工程稳定后再切构建路径。

### 模块划分

- `app`：应用壳、导航、依赖装配
- `core:designsystem`：主题、字体、组件、状态样式
- `core:model`：纯数据模型
- `core:data`：Repository、DataStore、网络源
- `core:root`：RootShell、CLI 适配、文件路径
- `feature:overview`
- `feature:corehub`
- `feature:console`
- `feature:settings`

### 状态管理

- 每个 feature 自己维护 `UiState`
- 全局只保留极少数跨页状态，如 Root 可用性、当前活动核心摘要、模块版本
- 拒绝再次出现超大 `MainViewModel`

### 导航

- 手机：Bottom Navigation
- 大屏：Navigation Rail
- 详情页：窄屏单页跳转，宽屏双栏

### 兼容策略

- 保持 `applicationId = "com.danmuapi.manager"`，便于未来替换现有 APK
- 保持 Root 调用协议和持久化目录不变
- 最后更新 `.github/workflows/build-danmu-magisk.yml` 中的 `MANAGER_APP_DIR` 与 `MANAGER_APP_APK_PATH`

## 风险与决策

### 已确认决策

- 重构必须新开目录
- 功能必须全覆盖
- UI 必须重新设计，不接受旧结构换皮
- 总体风格采用命令中心风

### 风险

- 现有逻辑集中在大 ViewModel 中，迁移时容易遗漏边缘行为
- Root 与模块安装行为较重，必须保留旧目录以便回退
- 当前本地目录不是 git 工作树，后续如果要提交或切工作树，需要在真正仓库中执行

## 实施策略

按以下顺序实施：

1. 先搭新工程和设计系统
2. 再迁移 Root 与 Repository 能力
3. 先做总览与核心中心
4. 再做控制台与设置
5. 最后接入构建工作流与模块打包

## 验收标准

- 新目录下存在可独立构建的新管理器工程
- 视觉风格明显区别于旧版
- 一级导航缩减为 4 个主页面
- 所有 README 列出的 App 功能在新版本中可达
- 模块打包流程可切到新工程输出 APK
- 旧工程目录保留且不被覆盖
