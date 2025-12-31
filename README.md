# danmu-api Android Server（Magisk / KernelSU 模块）

把 **danmu-api** 作为 Android 上的开机自启动服务运行，并提供持久化配置目录，方便配合 UI 端的「系统配置 / 环境变量」界面使用。

> 默认端口：主服务 **9321**，代理端口 **5321**  
> 默认鉴权：`TOKEN=87654321`，`ADMIN_TOKEN=admin`

---

## 基于项目

本服务基于上游 danmu-api：

```text
https://github.com/huangxd-/danmu_api
````

---

## 适用人群 / 你需要什么

* ✅ 已 Root 的设备（Magisk 或 KernelSU）
* ✅ 设备上能找到 `node` 可执行文件（例如 Termux 安装 Node.js）
* ✅ 想在本机长期跑 danmu-api 服务，并通过 UI 端配置页面稳定写入配置

---

## Magisk / KernelSU 是什么（简短说明）

* **Magisk**：Android 上最常见的 Root 方案之一，支持刷入「模块」实现开机脚本、自启服务等能力。
* **KernelSU（KSU）**：基于内核的 Root 方案，同样支持模块机制，使用体验与 Magisk 类似。

> 本仓库提供的是 **Magisk/KSU 模块**：需要 Root + 可刷模块环境。

---

## 安装（Magisk / KernelSU）

1. 在 Magisk / KernelSU 的「模块」里刷入本仓库提供的模块 zip
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

为了解决「UI 端保存环境变量偶发失效 / 软链接不稳定」的问题，本模块把运行目录固定到了持久化路径：

* 配置目录（持久化、不会因上游更新覆盖）：

```text
/data/adb/danmu_api_server/app/config
```

常用文件：

* `/data/adb/danmu_api_server/app/config/.env`
* `/data/adb/danmu_api_server/app/config/config.yaml`

### 配置优先级（从高到低）

1. 系统环境变量
2. `.env`
3. `config.yaml`

### 默认 Token（请按需修改）

* `TOKEN=87654321`
* `ADMIN_TOKEN=admin`

> 如果你把服务暴露到局域网/公网，务必改掉默认 Token。

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

日志位置：

```text
/data/adb/danmu_api_server/logs/service.log
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
* 本仓库：Android（Magisk/KSU）服务化打包与持久化配置适配

```

