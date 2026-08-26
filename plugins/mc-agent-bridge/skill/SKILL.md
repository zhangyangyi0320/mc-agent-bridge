---
name: mc-agent-bridge
description: Control and maintain a Minecraft (Folia/Paper) server through the McAgentBridge HTTP API. Use whenever the user wants to query server status/players, run or batch Minecraft commands, manage players (kick/ban/op/tp/give/msg), inspect player inventories/ender chests, enable maintenance mode, manage or hot-reload plugins, read/search server logs by time window, read/write/copy/move server files, or back up the whole server directory. Also use when an AI agent needs to operate or keep a Minecraft instance healthy.
---

# McAgentBridge — Minecraft server control API

This server runs the **McAgentBridge** plugin (a Folia/Paper plugin) exposing a JSON
HTTP API. Use it to let an AI agent inspect and maintain the Minecraft instance.

## Access

- Base URL: `http://127.0.0.1:25566` (configurable `host`/`port` in the plugin's `config.yml`).
- Auth: HTTP header `Authorization: Bearer <token>` (token is in the plugin `config.yml`, generated on first start). `GET /api/health` needs no auth.
- Python client (preferred): `mc_agent_client.py` in this skill folder. Set env vars
  `MC_API_URL` and `MC_API_TOKEN` (or edit defaults).

```bash
export MC_API_URL="http://127.0.0.1:25566"
export MC_API_TOKEN="<token from config.yml>"
python mc_agent_client.py status
python mc_agent_client.py player Notch
python mc_agent_client.py logs --last 30m --level WARNING
python mc_agent_client.py backup
```

## Endpoints (summary)

### Read-only
- `GET /api/health` — `{status:"ok", folia:bool}`
- `GET /api/status` — tps, mspt, uptime, memory, online/max players, worlds
- `GET /api/worlds` — per-world entity/chunk/player counts
- `GET /api/players` — list of online players (detailed)
- `GET /api/players/<name|uuid>` — detailed info (health, food, exp, gamemode, ping, ip, potion effects, first/last played; works for offline UUID too)
- `GET /api/players/<id>/inventory` — main + armor + offhand item stacks
- `GET /api/players/<id>/enderchest` — ender chest contents
- `GET /api/plugins` — installed plugins (name, version, enabled, authors)
- `GET /api/logs?since=&until=&last=30m&level=INFO&contains=&limit=200` — timestamped log lines (time windows!)
- `GET /api/backups` — existing backup zip files
- `GET /api/fs/info?path=` `GET /api/fs/list?path=` `GET /api/fs/read?path=&maxBytes=` — sandboxed to server root

### Mutating (require auth; blocked when plugin `read-only: true`)
- `POST /api/command {"command":"..."}` — run as console, returns `{success, output}`
- `POST /api/commands {"commands":["...","..."]}` or `{"command":"a\nb"}` — batch
- `POST /api/broadcast {"message":"..."}`
- `POST /api/players/<id>/kick|ban|op|deop|tp|give|msg` — see client for args
- `POST /api/whitelist {"action":"add|remove|on|off|list","name":""}`
- `POST /api/maintenance {"action":"enable|disable"}` — toggles whitelist + broadcast
- `POST /api/plugins/<name>/enable|disable|reload` — hot reload a plugin
- `POST /api/server/stop`
- `POST /api/fs/write|delete|mkdir|copy|move` — `{"path":..., "dst":..., "content":..., "append":bool}`
- `POST /api/backup {"name":optional,"dest":optional}` — zips the server root into `backups/`
  (excludes the backups dir). May be slow for large worlds; returns file/size/ms.

## Notes / safety
- `read-only` mode (config.yml) disables all mutating endpoints; file reads & state queries still work.
- File operations are sandboxed to the server root; path traversal above it is rejected.
- Backups capture the live filesystem; for a consistent snapshot, enable maintenance mode first.
- Bind to `127.0.0.1` by default. To expose, put it behind a reverse proxy with TLS — never expose the raw port publicly.
- On Folia (regionized multithreading) all reads/writes are routed to the global region thread, so it is safe.
