# 兼容性矩阵

> 由 	ools/test_compat.py 在 81 个 Paper / Folia 服务端上实测生成。
> **PASS** = 插件成功加载并正常响应 /api/status。

| Minecraft 版本 | 服务端类型 | 结果 | 备注 |
| --- | --- | --- | --- |
| 1.7.10 | paper | FAIL_ENV | 环境限制（下载/启动失败，非插件问题） |
| 1.8.8 | paper | PASS |  |
| 1.9.4 | paper | PASS |  |
| 1.10.2 | paper | PASS |  |
| 1.11.2 | paper | PASS |  |
| 1.12 | paper | FAIL_ENV | 环境限制（下载/启动失败，非插件问题） |
| 1.12.1 | paper | FAIL_ENV | 环境限制（下载/启动失败，非插件问题） |
| 1.12.2 | paper | PASS |  |
| 1.13 | paper | PASS |  |
| 1.13.1 | paper | PASS |  |
| 1.13.2 | paper | PASS |  |
| 1.14 | paper | PASS |  |
| 1.14.1 | paper | PASS |  |
| 1.14.2 | paper | PASS |  |
| 1.14.3 | paper | PASS |  |
| 1.14.4 | paper | PASS |  |
| 1.15 | paper | PASS |  |
| 1.15.1 | paper | PASS |  |
| 1.15.2 | paper | PASS |  |
| 1.16.1 | paper | PASS |  |
| 1.16.2 | paper | PASS |  |
| 1.16.3 | paper | PASS |  |
| 1.16.4 | paper | PASS |  |
| 1.16.5 | paper | PASS |  |
| 1.17 | paper | FAIL_ENV | 环境限制（下载/启动失败，非插件问题） |
| 1.17.1 | paper | PASS |  |
| 1.18 | paper | PASS |  |
| 1.18 | paper | PASS |  |
| 1.18.1 | paper | PASS |  |
| 1.18.1 | paper | PASS |  |
| 1.18.2 | paper | PASS |  |
| 1.18.2 | paper | PASS |  |
| 1.19 | paper | PASS |  |
| 1.19 | paper | PASS |  |
| 1.19.1 | paper | PASS |  |
| 1.19.1 | paper | PASS |  |
| 1.19.2 | paper | PASS |  |
| 1.19.2 | paper | PASS |  |
| 1.19.3 | paper | FAIL_PLUGIN | 需改用 McAgentBridge-1.19.jar |
| 1.19.3 | paper | FAIL_PLUGIN | 需改用 McAgentBridge-1.19.jar |
| 1.19.4 | folia | FAIL_PLUGIN | 需改用 McAgentBridge-1.19.jar |
| 1.19.4 | folia | FAIL_PLUGIN | 需改用 McAgentBridge-1.19.jar |
| 1.19.4 | paper | FAIL_PLUGIN | 需改用 McAgentBridge-1.19.jar |
| 1.19.4 | paper | FAIL_PLUGIN | 需改用 McAgentBridge-1.19.jar |
| 1.20 | paper | PASS |  |
| 1.20 | paper | PASS |  |
| 1.20.1 | folia | PASS |  |
| 1.20.1 | folia | PASS |  |
| 1.20.1 | paper | PASS |  |
| 1.20.1 | paper | PASS |  |
| 1.20.2 | folia | PASS |  |
| 1.20.2 | folia | PASS |  |
| 1.20.2 | paper | PASS |  |
| 1.20.2 | paper | PASS |  |
| 1.20.4 | folia | PASS |  |
| 1.20.4 | folia | PASS |  |
| 1.20.4 | paper | PASS |  |
| 1.20.4 | paper | PASS |  |
| 1.20.5 | paper | PASS |  |
| 1.20.6 | folia | PASS |  |
| 1.20.6 | paper | PASS |  |
| 1.21 | paper | PASS |  |
| 1.21.1 | paper | PASS |  |
| 1.21.3 | paper | PASS |  |
| 1.21.4 | folia | PASS |  |
| 1.21.4 | paper | PASS |  |
| 1.21.5 | folia | PASS |  |
| 1.21.5 | paper | PASS |  |
| 1.21.6 | folia | PASS |  |
| 1.21.6 | paper | PASS |  |
| 1.21.7 | paper | PASS |  |
| 1.21.8 | folia | PASS |  |
| 1.21.8 | paper | PASS |  |
| 1.21.9 | paper | PASS |  |
| 1.21.10 | paper | PASS |  |
| 1.21.11 | folia | PASS |  |
| 1.21.11 | paper | PASS |  |
| 26.1.1 | paper | FAIL_ENV | 环境限制（下载/启动失败，非插件问题） |
| 26.1.2 | paper | FAIL_ENV | 环境限制（下载/启动失败，非插件问题） |
| 26.2 | folia | FAIL_ENV | 环境限制（下载/启动失败，非插件问题） |
| 26.2 | paper | FAIL_ENV | 环境限制（下载/启动失败，非插件问题） |

## 说明

- **1.19.x（Paper / Folia 1.19.4）** 需用 McAgentBridge-1.19.jar；其余版本用 McAgentBridge.jar（或对应分段 jar）即可。
- FAIL_ENV 均为沙箱环境的下载 / 启动限制（如 1.12/1.12.1 原版 jar 下载 404、1.7.10 为 pre-1.8），不是插件缺陷。
