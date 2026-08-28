---
name: mc-agent-bridge
description: >-
  Control and observe a Minecraft server (Paper / Folia / Spigot / Purpur, 1.12–1.21+)
  over a local HTTP/JSON API exposed by the McAgentBridge plugin. Use for server
  status, player/inventory inspection, command execution, backups, whitelist,
  maintenance mode, world/plugin management and more.
---

# McAgentBridge Skill

McAgentBridge turns a running Minecraft server into an HTTP/JSON control surface
for an AI agent. Every request (except `GET /api/health`) requires a Bearer token
configured in the plugin's `config.yml` (`token:` field).

> **Safety first.** The API can fully control the server (run commands, stop it,
> delete backups). Only enable what you need, prefer `127.0.0.1` (localhost), and
> never expose it to the public internet without a firewall / reverse proxy + TLS
> and a strong token. Disabled features return HTTP `403`.

## Connection

- Base URL: `http://<host>:<port>` (default `http://127.0.0.1:25566`).
- Auth header: `Authorization: Bearer <token>`.
- Health check (no auth): `GET /api/health` → `{"status":"ok", ...}`.
- Discover capabilities: `GET /api/status` returns `read_only`, `exposure`
  (`allow_lan` / `allow_public` / `bind_host`) and `features` (which capabilities
  are currently enabled). Before calling an endpoint, check its feature flag is
  `true`; otherwise you will get `403 feature disabled: <feature>`.

## Endpoints

All request/response bodies are JSON. `200` = success, `400` = bad input,
`401` = unauthorized, `403` = feature disabled / not permitted (read-only),
`404` = not found, `500` = server error.

### Read (status / observation)
| Method | Path | Feature | Notes |
|--------|------|---------|-------|
| GET | `/api/status` | `status` | Server info, TPS, memory, online players, features. |
| GET | `/api/health` | — (open) | Liveness probe, no auth. |
| GET | `/api/worlds` | `worlds` | Worlds, time, weather, loaded chunks, entity counts. |
| GET | `/api/players` | `players` | Online players (uuid, name, location, health, gamemode…). |
| GET | `/api/players/<uuid>/inventory` | `inventory` | Main + armor + offhand + ender chest contents. |
| GET | `/api/plugins` | `plugins` | Installed plugins and enabled state. |
| GET | `/api/logs` | `logs` | Recent console lines (configurable `log-lines`). |
| GET | `/api/backups` | `backups` | Existing backups under the backup directory. |

### World control
| Method | Path | Feature | Body / Notes |
|--------|------|---------|--------------|
| POST | `/api/worlds/<name>/time` | `worlds` | `{"tick":<long>}` or `{"add":<long>}` or `{"time":"day"|"night"|"noon"|"midnight"}`. |
| POST | `/api/worlds/<name>/weather` | `worlds` | `{"weather":"clear"|"rain"|"storm","duration":<ticks>}`. |

### Plugins
| Method | Path | Feature | Notes |
|--------|------|---------|-------|
| POST | `/api/plugins/<name>/enable` | `plugin_action` | Enable a plugin. |
| POST | `/api/plugins/<name>/disable` | `plugin_action` | Disable a plugin. |
| POST | `/api/plugins/<name>/reload` | `plugin_action` | Reload a plugin. |

### Commands & messaging
| Method | Path | Feature | Body |
|--------|------|---------|------|
| POST | `/api/command` | `command` | `{"command":"<mc command>"}` → `{"success":bool,"output":"..."}`. |
| POST | `/api/commands` | `commands` | `{"commands":["cmd1","cmd2"]}` → array of results. |
| POST | `/api/broadcast` | `broadcast` | `{"message":"<text>"}`. |

### Whitelist & maintenance
| Method | Path | Feature | Body |
|--------|------|---------|------|
| POST | `/api/whitelist` | `whitelist` | `{"action":"add"|"remove"|"on"|"off","player":"<name>"}`. |
| POST | `/api/maintenance` | `maintenance` | `{"enabled":bool,"kickmsg":"<text>"}` (kicks non-op players when enabled). |

### Players (targeted)
| Method | Path | Feature | Body |
|--------|------|---------|------|
| POST | `/api/players/<uuid>/kick` | `player_action` | `{"reason":"<text>"}`. |
| POST | `/api/players/<uuid>/gamemode` | `player_action` | `{"mode":"survival"|"creative"|"adventure"|"spectator"}`. |
| POST | `/api/players/<uuid>/op` | `player_action` | `{"op":bool}`. |
| POST | `/api/players/<uuid>/teleport` | `player_action` | `{"x":..,"y":..,"z":..,"world":"<name>"}`. |

### Server lifecycle & backups
| Method | Path | Feature | Notes |
|--------|------|---------|-------|
| POST | `/api/backup` | `backup_create` | Create a timestamped world backup. |
| DELETE | `/api/backups/<name>` | `backups` | Delete a backup. |
| POST | `/api/server/stop` | `server_stop` | Stop the server (after the configured delay). |

### Filesystem (server directory)
| Method | Path | Feature | Notes |
|--------|------|---------|-------|
| GET | `/api/fs/list?path=<rel>` | `fs` | List a directory under the server root. |
| GET | `/api/fs/read?path=<rel>` | `fs` | Read a (text) file. |
| POST | `/api/fs/write` | `fs` | `{"path":"<rel>","content":"..."}` write/append a file. |
| DELETE | `/api/fs/delete?path=<rel>` | `fs` | Delete a file or empty directory. |

## Behavior notes

- **Read-only mode** (`read-only: true`): write endpoints return `403` except
  whitelist OFF, maintenance off, and `server/stop`. Use it to safely observe.
- **Network exposure**: by default the API binds `127.0.0.1`. Enable `exposure.allow_lan`
  to bind `0.0.0.0` for LAN, or `exposure.allow_public` for public (logs a strong
  warning). Always front public exposure with a firewall / reverse proxy + TLS.
- **Compatibility**: works on Minecraft **1.12 through 1.21+**. Some fields are
  best-effort (e.g. player `ping` / `locale` may be unavailable on older versions
  and are returned as `null`/`-1`). TPS is reported when the server API exposes it.
- **Server software**: detected automatically (Paper / Folia / Spigot / Purpur /
  CraftBukkit / Forge / Unknown) and reported in `/api/status` and `/api/health`.
- **In-game toggles**: server operators with the `mcagentbridge.admin` permission
  (default: OP) can run `/mab list` and `/mab <feature> [on|off|toggle]` to switch
  any capability live; changes persist to `config.yml` and take effect immediately.

## Recommended workflow for an agent

1. `GET /api/health` to confirm reachability.
2. `GET /api/status` to learn `read_only`, `exposure`, and which `features` are on.
3. Use read endpoints to plan; call write endpoints only when the relevant feature
   is enabled and the action is within the user's intent.
4. Treat every write as consequential — prefer read-only observation unless the
   user explicitly asked to change server state.
