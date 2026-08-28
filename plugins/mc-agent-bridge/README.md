# McAgentBridge

一个面向 **Paper / Folia / Spigot / Purpur（Minecraft 1.12 ~ 1.21+）** 的 HTTP API 桥接插件，让 AI Agent（或任意 HTTP 客户端）能够查看、控制并维护服务器。

> 配套 Agent 技能见仓库 `skill.zip`（`SKILL.md` + `config.yml` 示例）。

---

## 功能特性

- **服务器状态**：TPS / MSPT / 内存 / 在线人数 / 运行时长 / 版本信息
- **世界与玩家**：世界列表、在线玩家详情、玩家背包 / 末影箱查询
- **指令执行**：单条或批量执行控制台命令并回收输出
- **玩家管理**：踢出、封禁、OP / deop、传送、给予物品、私信（通过控制台命令）
- **插件管理**：列出插件，启用 / 禁用 / 重载（`/api/plugins/{name}/{action}`）
- **白名单与维护**：白名单管理、一键维护模式
- **日志查询**：按时间窗 / 级别 / 关键字过滤控制台日志
- **文件操作（沙箱）**：基于服务器根目录的 `info / list / read / write / delete / mkdir / copy / move`
- **全量备份**：将服务器目录打包为 zip
- **Folia 兼容**：通过全局区域调度器安全访问服务器，兼容 Folia 多区域线程模型

## 支持的服务器

| 服务端 | 版本 | 说明 |
|--------|------|------|
| Paper | 1.12+ | 原生支持 |
| Folia | 1.12+ | 通过 `RegionizedServer` 反射检测，命令统一走全局区域调度 |
| Spigot | 1.12+ | 标准 Bukkit API，兼容 |
| Purpur | 1.12+ | 基于 Paper，兼容 |

> `api-version: "1.12"`，`folia-compatible: true`。支持范围：Minecraft 1.12 ~ 1.21+，服务端类型 Paper / Folia / Spigot / Purpur。部分字段为尽力而为（例如 1.16 以下玩家的 `ping` / `locale` 可能取不到，返回 `null` / `-1`）；TPS 在服务器 API 可用时返回。`/api/status` 与 `/api/health` 会返回 `server_software` 与 `minecraft_version` 供识别。

---

## 安装

1. 下载 `McAgentBridge.jar`。
2. 放入服务器的 `plugins/` 目录。
3. 启动服务器，插件会生成 `config.yml` 并在首次运行时自动生成一个随机 `token`（请到 `config.yml` 中复制保存）。
4. 默认监听 `http://127.0.0.1:25566/`。

## 配置（`plugins/McAgentBridge/config.yml`）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enabled` | `true` | 是否启用 API |
| `host` | `127.0.0.1` | 绑定地址。生产环境请保持 `127.0.0.1`，在前面套反代 + TLS |
| `port` | `25566` | 监听端口 |
| `token` | `""` | 访问令牌。留空则首次启动随机生成并写回文件 |
| `read-only` | `false` | 为 `true` 时仅允许只读接口（status/players/worlds/logs/health） |
| `log-lines` | `2000` | 保留的最近日志行数 |

### 网络暴露（`exposure`）

默认 API 仅监听 `127.0.0.1`（本机）。是否向局域网 / 公网开放由你**显式决定**，开启前插件会打印警告：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `exposure.allow_lan` | `false` | 为 `true` 时绑定 `0.0.0.0`（可被局域网访问，打印警告） |
| `exposure.allow_public` | `false` | 为 `true` 时绑定 `0.0.0.0` 并**强烈警告**：任何能访问端口且知道 token 的人都能完全控制服务器 |

> 公网暴露前务必在前面加防火墙 / 反向代理 + TLS，并使用高强度 token。

### 功能开关（`features`）

每一项能力都可**独立**开启 / 关闭。被关闭的功能在调用时返回 `HTTP 403`，并可通过 `GET /api/status` 的 `features` 字段查询当前哪些功能可用。

| 功能键 | 控制的能力 |
|--------|-----------|
| `status` | `/api/status` |
| `worlds` | `/api/worlds` 及世界时间 / 天气控制 |
| `players` | `/api/players` 列表 |
| `inventory` | 玩家背包 / 末影箱查询 |
| `plugins` | `/api/plugins` 列表 |
| `plugin_action` | 插件启用 / 禁用 / 重载 |
| `logs` | `/api/logs` |
| `backups` | 备份列表 / 删除 |
| `fs` | 文件系统操作 |
| `command` | 单条命令 |
| `commands` | 批量命令 |
| `broadcast` | 全服广播 |
| `whitelist` | 白名单管理 |
| `maintenance` | 维护模式 |
| `server_stop` | 停止服务器 |
| `backup_create` | 创建备份 |
| `player_action` | 踢出 / OP / 传送等玩家操作 |

---

## API 概览

所有请求（除 `/api/health` 外）都需要认证。返回均为 JSON。

### 认证

```
Authorization: Bearer <token>
# 或
GET /api/...?token=<token>
```

鉴权失败返回 `401`。

### 只读接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查（无需 token） |
| GET | `/api/status` | 服务器状态 |
| GET | `/api/worlds` | 世界列表 |
| GET | `/api/players` | 在线玩家 |
| GET | `/api/players/{id}` | 玩家详情（id 可为名或 UUID） |
| GET | `/api/players/{id}/inventory` | 背包 |
| GET | `/api/players/{id}/enderchest` | 末影箱 |
| GET | `/api/plugins` | 插件列表 |
| GET | `/api/logs` | 日志（`?since=&until=&last=&level=&contains=&limit=`） |
| GET | `/api/backups` | 备份列表 |
| GET | `/api/fs/info?path=` | 文件信息 |
| GET | `/api/fs/list?path=` | 目录列表 |
| GET | `/api/fs/read?path=&maxBytes=` | 读取文件 |

### 写操作接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/command` | `{"command":"..."}` |
| POST | `/api/commands` | `{"commands":[...]}` 或 `{"command":"多行\n命令"}` |
| POST | `/api/broadcast` | `{"message":"..."}` |
| POST | `/api/whitelist` | `{"action":"add\|remove\|on\|off\|list","name":"..."}` |
| POST | `/api/maintenance` | `{"action":"enable\|disable"}` |
| POST | `/api/server/stop` | 停止服务器 |
| POST | `/api/backup` | `{"dest?":"backups","name?":"..."}` |
| POST | `/api/players/{id}/{kick\|ban\|op\|deop\|tp\|give\|msg}` | 玩家操作 |
| POST | `/api/plugins/{name}/{enable\|disable\|reload}` | 插件操作 |
| POST | `/api/fs/write` | `{"path","content","append?"}` |
| POST | `/api/fs/delete` | `{"path"}` |
| POST | `/api/fs/mkdir` | `{"path"}` |
| POST | `/api/fs/copy` | `{"path","dst"}` |
| POST | `/api/fs/move` | `{"path","dst"}` |

> 开启 `read-only: true` 时，所有写操作接口将返回 `{"error":"server is in read-only mode"}`。

---

## 安全建议

- **默认仅本机可访问**：不要手动把 `host` 设为 `0.0.0.0`；如需局域网 / 公网，请使用 `exposure.allow_lan` / `exposure.allow_public` 开关，并认清其风险。
- **公网暴露前**务必放在反向代理（Nginx/Caddy）后并启用 TLS + 防火墙。
- 使用强随机 `token`，并通过 HTTPS 传输。
- 若仅需查询，可开启 `read-only: true`；若只需部分能力，可在 `features` 中关闭对应项。
- 任何持有 `token` 的客户端都拥有**等同于控制台的权限**，请妥善保管。

---

## 免责声明

本项目以 MIT 许可证开源，仅供学习与研究使用。**使用本插件产生的一切后果均由使用者自行承担。**
详见 [DISCLAIMER.md](DISCLAIMER.md)。

## 许可证

[MIT](LICENSE)
