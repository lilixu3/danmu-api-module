# Danmu API Magisk Module（含管理器 App）

把 `danmu-api` 核心打包成 **Magisk 可刷入模块**，并在系统里安装一个 **Danmu API 管理器 App**，用于：

* 启动/停止/重启服务
* 开机自启开关（不轮询、事件驱动）
* 多核心仓库管理（安装/切换/删除/更新检测）
* 读取核心版本号（从 `globals.js` 解析）
* 日志查看/一键清空/定期自动清理

> 适用人群：没有电脑也想维护/切换 danmu-api 核心、并尽量省电的用户。

---

## 功能特性

### ✅ 模块侧（Magisk）

* **两种构建版本**

  * **Node 完整版**：模块内自带 Node 运行时（来自 Termux 包，已做裁剪与依赖提取）
  * **No-Node 版**：不内置 Node（适合你已在系统内有可用 Node 的场景）
* **事件驱动的启停**（非轮询）

  * 通过 `inotifyd` 监听 Magisk 模块禁用/启用状态变化
  * 禁用模块可立即触发停服务（不需要等下一次轮询）
* **持久化数据目录**（升级模块不会丢数据）

  * 核心、多版本信息、日志、设置均存放在 `/data/adb/danmu_api_server/`

### ✅ App 侧（Danmu API 管理器）

* **服务控制**：Start / Stop / Restart，显示运行状态
* **开机自启开关**：只影响“是否自启”，不影响手动启动
* **核心管理（多版本共存）**

  * 预置上游：`huangxd-/danmu_api`、`lilixu3/danmu_api`
  * 支持自定义：输入 `owner/repo` + `ref（branch/tag/commit）`
  * 已下载核心可：**切换 / 删除 / 更新检测**
* **版本号显示**

  * 从核心的 `globals.js` 解析版本字段（以你的核心定义为准）
* **日志管理**

  * 查看日志尾部（tail）
  * 一键清空
  * 设置“定期自动清理周期”（由 WorkManager 触发，不做常驻轮询）

---

## 下载与安装

### 1) 下载模块

到仓库的 **Releases** 页面下载你需要的 zip：

* `danmu_api_server_node_<version>.zip`（推荐：自带 Node）
* `danmu_api_server_<version>.zip`（No-Node）

> 如果你不知道选哪个：**选 Node 完整版**。

### 2) Magisk 刷入

1. Magisk → 模块 → 从本地安装 → 选择下载的 zip
2. 刷入完成后 **重启手机**

### 3) 打开管理器 App

重启后桌面会出现 **“Danmu API 管理器”**（若没出现，见下方排障）。

首次打开会请求 Root 权限（SU），请允许。

---

## 使用指南

### 服务启停与自启

* **Start/Stop/Restart**：立即控制服务
* **Autostart**：

  * 打开：开机会自动启动服务
  * 关闭：开机不启动（但你仍可手动 Start）

### 核心管理（重点）

在 “核心” 页面你可以：

* 安装/切换到不同核心仓库
* 输入自定义仓库与 ref（branch/tag/commit）
* 对已安装核心执行：**切换 / 删除 / 更新检测**

> 安全提醒：切换核心等价于下载并运行第三方代码（root 下运行），请只使用你信任的仓库。

### 日志

在 “日志” 页面可以：

* 查看 `server.log` / `control.log` 等
* 一键清空日志
* 设置 “自动清理周期”（建议 7 天或 30 天）

---

## 目录结构与关键文件

模块使用持久化目录（升级不丢）：

* `/data/adb/danmu_api_server/`

  * `cores/`：已安装的核心列表（多版本共存）
  * `core/`：当前启用核心的软链接目标目录
  * `active_core_id`：当前启用核心 id
  * `logs/`：日志目录
  * `autostart.disabled`：存在则禁止自启
  * `service.pid`：服务 PID 文件（存在并且 pid 存活表示服务运行中）

日志位置：

* `/data/adb/danmu_api_server/logs/server.log`
* `/data/adb/danmu_api_server/logs/control.log`
* `/data/adb/danmu_api_server/logs/inotifyd.log`

---

## 省电建议（很重要）

如果你觉得耗电：

1. **不用时在 App 里 Stop 服务**
2. 关闭 **Autostart**
3. 把日志等级调低（例如 `warn` 或 `error`）
4. 开启 “日志自动清理”（避免长期写入堆积）

> 本模块已经尽量避免“轮询唤醒”：启停状态使用 inotify 事件驱动；日志清理使用 WorkManager 定时任务。

---

## 兼容性说明

* **推荐：arm64（aarch64）设备**

  * Node 完整版默认从 Termux 拉取 **aarch64** 包，因此主要面向 arm64
* No-Node 版理论上可适配更多架构（取决于你自行提供的 Node），但未做全面测试
* 需要 Magisk 环境与 Root

---

## 常见问题（FAQ）

### Q1：刷入后桌面没看到管理器 App？

* 先重启一次（系统应用挂载有时需要重启）
* 到系统“应用列表”里搜索 “Danmu”
* 仍没有：请在 Magisk 里确认模块已启用，并贴出模块安装日志 + `ls -l /system/app/` 相关信息（或使用第三方应用列表查看）

### Q2：服务启动失败 / 一直显示 stopped？

* 打开 App → 日志页 → 查看 `server.log`
* 常见原因：

  * 端口被占用
  * 核心文件损坏/下载不完整
  * No-Node 版没有可用 Node 环境

### Q3：更新检测失败或很慢？

* GitHub 可能存在 API 限流；可在设置页填入 GitHub Token（可选）
* 网络环境不稳定时建议稍后重试

### Q4：禁用模块后服务没停？

* 模块使用 `inotifyd` 监听状态变化；如果系统环境异常，可能需要重启一次
* 也可在 App 中手动 Stop

---

## 给开发者：如何用 GitHub Actions 一键打包

本仓库提供 workflow：**Build Danmu API Magisk Modules**（手动触发）

1. Fork 本仓库
2. Actions → 选择 workflow → Run workflow
3. 选择参数：

   * 上游仓库（或自定义仓库）
   * ref（branch/tag/commit）
   * build 版本（both/node/no_node）
   * 模块版本号
4. 构建完成后在 Artifacts 或 Release 下载 zip，直接刷入

> 你不需要本地 Android Studio，也不需要电脑，Actions 会完成 APK 编译与模块打包。

---

## 免责声明与安全

* 本仓库提供的是 **打包/管理工具链**；`danmu-api` 核心代码归上游仓库作者所有并遵循其许可协议
* 自定义核心仓库的代码会在 root 下运行，请自行评估风险
* Termux 的 Node/BusyBox 组件归 Termux 项目及其上游许可协议所有

---

## 致谢

* danmu-api 上游项目作者与贡献者
* Magisk / Termux 生态与相关工具链
* 所有测试与反馈的用户
