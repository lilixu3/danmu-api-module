## 基于项目

本服务基于上游 danmu-api：

```text
https://github.com/huangxd-/danmu_api
```

---

## 适用人群 / 你需要什么

* ✅ 已 Root 的设备（Magisk 或 KernelSU）
* ✅ 想在本机长期跑 danmu-api 服务，并通过 UI 端配置页面稳定写入配置

---

## Magisk / KernelSU 是什么（简短说明）

* **Magisk**：Android 上常见的 Root 方案，支持刷入「模块」实现开机脚本、自启服务等能力。
* **KernelSU（KSU）**：基于内核的 Root 方案，也支持模块机制，使用方式与 Magisk 类似。

> 本仓库提供的是 **Magisk/KSU 模块**：需要 Root + 可刷模块环境。

---

## 特性

* ✅ **刷入即用**：内置 Node.js 运行时，无需额外安装依赖
* ✅ **开机自启**：系统启动后自动运行 danmu-api 服务
* ✅ **配置稳定**：配置目录固定在模块内，避免更新覆盖

---

## 安装（Magisk / KernelSU）

1. 在 Magisk / KernelSU 的「模块」里刷入 zip 文件
2. **重启手机**
3. 重启后服务会自动启动

---

## 访问与端口

* 主服务端口：`9321`
* 代理端口：`5321`

示例（本机测试）：

```bash
curl http://127.0.0.1:9321/
```

---

## 配置文件（重点）

为了解决「UI 端保存环境变量偶发失效 / 软链接不稳定」的问题，本模块将配置目录固定到模块目录内（不会因上游更新覆盖模块外的内容；同时路径与 UI 端保持一致）。

### 配置目录（以后都在这里）

```text
/data/adb/modules/danmu_api_server/app/config/
```

常用文件：

* `/data/adb/modules/danmu_api_server/app/config/.env`
* `/data/adb/modules/danmu_api_server/app/config/config.yaml`

### 默认 Token（请按需修改）

* `TOKEN=87654321`
* `ADMIN_TOKEN=admin`

> 如果你把服务暴露到局域网/公网，务必修改默认 Token。

---

## 服务管理（可选）

模块默认开机自启；如需手动控制，可使用控制脚本：

```text
/data/adb/modules/danmu_api_server/scripts/danmu_control.sh
```

常用命令：

```bash
su -c /data/adb/modules/danmu_api_server/scripts/danmu_control.sh status
su -c /data/adb/modules/danmu_api_server/scripts/danmu_control.sh restart
su -c /data/adb/modules/danmu_api_server/scripts/danmu_control.sh stop
su -c /data/adb/modules/danmu_api_server/scripts/danmu_control.sh start
```

日志位置（如模块内有日志输出）：

```text
/data/adb/modules/danmu_api_server/logs/service.log
```

---

## 没有 Magisk / KernelSU？或者无法解锁 BL？

如果你的设备 **无法解锁 Bootloader（BL）**，通常就无法 Root，也就没法使用 Magisk/KSU 模块版本。

这种情况下请安装 **App 版本**（免 Root 的替代方案）：

```text
https://github.com/lilixu3/danmu-api-android
```

---

## 致谢

* 上游 danmu-api：`huangxd- / danmu_api`
* 本仓库：Android（Magisk/KSU）服务化打包与配置路径适配