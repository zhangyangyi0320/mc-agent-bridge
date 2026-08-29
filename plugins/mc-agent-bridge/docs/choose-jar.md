# 按版本选择 McAgentBridge 的 jar

McAgentBridge 针对不同 Minecraft 版本提供了多个构建（jar）。原因是：

- **Minecraft 1.8 – 1.18 的 Paper / Spigot**：不读取 `paper-plugin.yml`，插件以 legacy（旧）模式加载，任意 `api-version` 都能用。
- **Minecraft 1.19.x**：要求 `api-version ≤ 1.19.x`，使用更高的版本会被服务端拒绝加载。
- **Minecraft 1.20 – 1.21.x（Paper）与 Folia**：要求 `api-version ≥ 1.20`。

因为 `paper-plugin.yml` 只能写一个 `api-version`，无法用单个 jar 同时覆盖 1.19.x 与 1.20+，所以发布了多个 jar。**请按你的服务端版本选择：**

| 你的服务端版本 | 使用的 jar | 备注 |
| --- | --- | --- |
| Minecraft **1.8 – 1.12**（Paper / Spigot） | `McAgentBridge-1.8-1.18.jar` 或 `McAgentBridge.jar` | 旧版走 legacy 模式 |
| Minecraft **1.13 – 1.18**（Paper） | `McAgentBridge-1.8-1.18.jar` 或 `McAgentBridge.jar` | 同上 |
| Minecraft **1.19.0 – 1.19.4**（Paper / Folia 1.19.4） | **`McAgentBridge-1.19.jar`** | 必须用此 jar，其他 jar 会被拒绝加载 |
| Minecraft **1.20 – 1.21.x**（Paper） | `McAgentBridge-1.20-1.21.jar` 或 `McAgentBridge.jar` | |
| **Folia 1.20.1 – 1.20.6** | `McAgentBridge-1.20-1.21.jar` 或 `McAgentBridge.jar` | |
| **Folia 1.21.x** | `McAgentBridge-1.20-1.21.jar` 或 `McAgentBridge.jar` | |

> 一句话总结：**除 1.19.x 必须用 `McAgentBridge-1.19.jar` 外，其余版本用 `McAgentBridge.jar` 即可。**

## 安装方法

1. 到 [Releases](../../releases) 下载对应版本的 jar。
2. 放入服务端的 `plugins/` 目录。
3. 重启（或 reload）服务端。
4. 验证：访问 `http://<服务器IP>:8080/api/status`（默认端口 8080）应返回 JSON。

## 兼容性矩阵

完整测试结果见 [compatibility-matrix.md](compatibility-matrix.md)。
