# MC Monitor（服务端 Mod）

[MC 服务器监控 Android App](https://github.com/OSSKn4w7/mc-monitor-app) 的服务端配套 Mod：在 Minecraft 服务器内开一条**鉴权监控通道**，让手机 App 查看实时日志、服务器状态并执行控制台命令。

- 当前版本：NeoForge **21.1.251**（Minecraft 1.21.1）
- 仅服务端需要安装（`displayTest="IGNORE_ALL_VERSION"`，客户端可不装）
- License: MIT

## 架构

```
┌─ App（手机）─┐      TCP 25580       ┌─ neoforge 适配层（薄）─┐
│ 状态/日志/控制台│ ◄──JSON Lines──► │ 事件/命令/日志捕获/TPS   │
└──────────────┘                    ├─ core（纯 Java 零依赖）─┤
                                    │ 协议/鉴权/限流/日志环/审计│
                                    └───────────────────────┘
```

- **`core/`**：纯 Java、零第三方依赖，与加载器和 MC 版本解耦——移植到 Forge/Fabric 只需新写一个适配层
  - `MiniJson`：极简 JSON 解析/序列化
  - `PasswordAuth`：密码策略校验、PBKDF2 验证器生成与挑战-响应校验
  - `AuthGuard`：按 IP 防爆破（5 次失败封禁 10 分钟）与操作限频
  - `MonitorServer` / `ClientSession`：TCP 服务、会话状态机（auth → status/logs/logsub/cmd/ping）
  - `LogRing`：最近 1000 行日志环形缓冲
- **`neoforge/`**：NeoForge 接线——命令 `/mcmonitor`、聊天式设密、log4j2 日志捕获（setpass 自动脱敏）、TPS 统计、命令执行
- **`harness/`**：不起 MC 的本地测试后端（含 SLP 应答器）+ 协议冒烟测试

## 安全设计

| 环节 | 措施 |
|------|------|
| 密码设置 | 游戏内 `/mcmonitor setpass` 走聊天输入（聊天事件被拦截取消，**密码不进日志不公屏**）；控制台可 `mcmonitor setpass <密码>`；强制 ≥8 位且含数字/大写/小写/符号 |
| 储存 | 只存 `PBKDF2WithHmacSHA256`（150000 迭代 + 16B 随机盐）验证器，**密码不落盘**（`config/mcmonitor-auth.json`） |
| 传输 | 挑战-响应：`response = HmacSHA256(verifier, nonce)`，nonce 一次性，**密码与验证器均不过网**，恒定时间比对 |
| 纵深防御 | 登录限频与 IP 封禁、命令限速 5 条/秒、全程审计、最多 3 连接、120s 空闲踢线、未知类型拒绝 |

> 通道本身未做 TLS，公网服务器建议配合防火墙白名单或 VPN 使用。

## 构建

```bash
gradlew :neoforge:build
```

- JDK 21（工具链自动匹配）
- 首次构建会下载 NeoForge/Mojang 工件
- 产物：`neoforge/build/libs/neoforge.jar`（core 源码已一并编入）

## 部署

1. `neoforge.jar` 放入服务端 `mods/`，启动
2. OP 在游戏内执行 `/mcmonitor setpass`，然后**直接在聊天栏输入密码**（不会公屏显示）；或服务器控制台执行 `mcmonitor setpass <密码>`
3. 端口/绑定地址见 `config/mcmonitor.properties`（默认 `0.0.0.0:25580`）
4. App 端添加服务器：填服务器地址 + 监控端口 + 密码

常用命令：

| 命令 | 说明 |
|------|------|
| `/mcmonitor setpass` | 设置/修改监控密码（玩家走聊天输入） |
| `/mcmonitor status` | 查看通道状态、连接数 |

重置密码：删除 `config/mcmonitor-auth.json` 后重新 setpass。

## 本地测试（无需 Minecraft）

```bash
gradlew :harness:installDist
java -cp "harness/build/install/harness/lib/*" com.osskn4w7.mcmonitor.harness.Harness 25580 "测试密码"
java -cp "harness/build/install/harness/lib/*" com.osskn4w7.mcmonitor.harness.SmokeTest 25580 "测试密码"
```

Harness 会同时开一个 25565 端口的 SLP 应答器（模拟真服务器）供直连查询测试。

## 许可证

MIT
