#!/usr/bin/env python3
"""
mc_agent_client.py - command-line client for the McAgentBridge plugin.

Controls a Minecraft (Folia) server over its HTTP API.

Auth: set MC_API_URL (default http://127.0.0.1:25566) and MC_API_TOKEN
(token from the plugin's config.yml). Most commands print JSON to stdout.

Examples:
  python mc_agent_client.py status
  python mc_agent_client.py player Notch
  python mc_agent_client.py inv Notch
  python mc_agent_client.py cmd "list"
  python mc_agent_client.py cmds "say hi" "list"
  python mc_agent_client.py logs --last 30m --level WARNING
  python mc_agent_client.py fs-list plugins
  python mc_agent_client.py backup
  python mc_agent_client.py kick Notch --reason "bye"
"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.error
import urllib.parse

API_URL = os.environ.get("MC_API_URL", "http://127.0.0.1:25566").rstrip("/")
API_TOKEN = os.environ.get("MC_API_TOKEN", "")


def _request(method, path, params=None, body=None):
    url = API_URL + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = None
    headers = {"Authorization": "Bearer " + API_TOKEN}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=600) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            detail = json.loads(raw)
        except Exception:
            detail = raw
        print(json.dumps({"error": str(e), "detail": detail}, ensure_ascii=False, indent=2))
        sys.exit(1)
    try:
        return json.loads(raw)
    except Exception:
        return raw


def out(obj):
    print(json.dumps(obj, ensure_ascii=False, indent=2))


def main():
    p = argparse.ArgumentParser(description="McAgentBridge client")
    sub = p.add_subparsers(dest="cmd", required=True)

    def add_id(sp):
        sp.add_argument("id", help="player name or UUID")

    sub.add_parser("health")
    sub.add_parser("status")
    sub.add_parser("worlds")
    sub.add_parser("players")

    sp = sub.add_parser("player"); add_id(sp)
    sp = sub.add_parser("inv"); add_id(sp)
    sp = sub.add_parser("ender"); add_id(sp)

    sp = sub.add_parser("cmd"); sp.add_argument("command", nargs="+")
    sp = sub.add_parser("cmds"); sp.add_argument("command", nargs="+")
    sp = sub.add_parser("broadcast"); sp.add_argument("message")

    sp = sub.add_parser("kick"); add_id(sp); sp.add_argument("--reason", default="")
    sp = sub.add_parser("ban"); add_id(sp); sp.add_argument("--reason", default=""); sp.add_argument("--duration", default="")
    sp = sub.add_parser("op"); add_id(sp)
    sp = sub.add_parser("deop"); add_id(sp)
    sp = sub.add_parser("tp"); add_id(sp); sp.add_argument("--target", default=None); sp.add_argument("--x", default=None); sp.add_argument("--y", default=None); sp.add_argument("--z", default=None)
    sp = sub.add_parser("give"); add_id(sp); sp.add_argument("item"); sp.add_argument("--amount", type=int, default=1)
    sp = sub.add_parser("msg"); add_id(sp); sp.add_argument("message")

    sp = sub.add_parser("whitelist"); sp.add_argument("action", choices=["add", "remove", "on", "off", "list"]); sp.add_argument("name", nargs="?")
    sp = sub.add_parser("maintenance"); sp.add_argument("action", choices=["enable", "disable"])

    sub.add_parser("plugins")
    sp = sub.add_parser("plugin"); sp.add_argument("name"); sp.add_argument("action", choices=["enable", "disable", "reload"])

    sp = sub.add_parser("logs")
    sp.add_argument("--last", default=None, help="e.g. 30m, 1h, 10s")
    sp.add_argument("--since", type=int, default=None, help="epoch ms")
    sp.add_argument("--until", type=int, default=None, help="epoch ms")
    sp.add_argument("--level", default=None, help="INFO/WARNING/SEVERE...")
    sp.add_argument("--contains", default=None)
    sp.add_argument("--limit", type=int, default=200)

    sp = sub.add_parser("fs-list"); sp.add_argument("path", nargs="?", default=".")
    sp = sub.add_parser("fs-info"); sp.add_argument("path")
    sp = sub.add_parser("fs-read"); sp.add_argument("path"); sp.add_argument("--max-bytes", type=int, default=1000000)
    sp = sub.add_parser("fs-write"); sp.add_argument("path"); sp.add_argument("content"); sp.add_argument("--append", action="store_true")
    sp = sub.add_parser("fs-mkdir"); sp.add_argument("path")
    sp = sub.add_parser("fs-del"); sp.add_argument("path")
    sp = sub.add_parser("fs-copy"); sp.add_argument("src"); sp.add_argument("dst")
    sp = sub.add_parser("fs-move"); sp.add_argument("src"); sp.add_argument("dst")

    sp = sub.add_parser("backup"); sp.add_argument("--name", default=None); sp.add_argument("--dest", default=None)
    sub.add_parser("backups")

    a = p.parse_args()

    if a.cmd == "health": out(_request("GET", "/api/health"))
    elif a.cmd == "status": out(_request("GET", "/api/status"))
    elif a.cmd == "worlds": out(_request("GET", "/api/worlds"))
    elif a.cmd == "players": out(_request("GET", "/api/players"))
    elif a.cmd == "player": out(_request("GET", "/api/players/" + a.id))
    elif a.cmd == "inv": out(_request("GET", "/api/players/" + a.id + "/inventory"))
    elif a.cmd == "ender": out(_request("GET", "/api/players/" + a.id + "/enderchest"))
    elif a.cmd == "cmd": out(_request("POST", "/api/command", body={"command": " ".join(a.command)}))
    elif a.cmd == "cmds": out(_request("POST", "/api/commands", body={"commands": a.command}))
    elif a.cmd == "broadcast": out(_request("POST", "/api/broadcast", body={"message": a.message}))
    elif a.cmd == "kick": out(_request("POST", "/api/players/" + a.id + "/kick", body={"reason": a.reason}))
    elif a.cmd == "ban": out(_request("POST", "/api/players/" + a.id + "/ban", body={"reason": a.reason, "duration": a.duration}))
    elif a.cmd == "op": out(_request("POST", "/api/players/" + a.id + "/op"))
    elif a.cmd == "deop": out(_request("POST", "/api/players/" + a.id + "/deop"))
    elif a.cmd == "tp":
        body = {}
        if a.target: body["target"] = a.target
        else: body.update({"x": a.x, "y": a.y, "z": a.z})
        out(_request("POST", "/api/players/" + a.id + "/tp", body=body))
    elif a.cmd == "give": out(_request("POST", "/api/players/" + a.id + "/give", body={"item": a.item, "amount": a.amount}))
    elif a.cmd == "msg": out(_request("POST", "/api/players/" + a.id + "/msg", body={"message": a.message}))
    elif a.cmd == "whitelist":
        body = {"action": a.action}
        if a.name: body["name"] = a.name
        out(_request("POST", "/api/whitelist", body=body))
    elif a.cmd == "maintenance": out(_request("POST", "/api/maintenance", body={"action": a.action}))
    elif a.cmd == "plugins": out(_request("GET", "/api/plugins"))
    elif a.cmd == "plugin": out(_request("POST", "/api/plugins/" + a.name + "/" + a.action))
    elif a.cmd == "logs":
        params = {"limit": a.limit}
        if a.last: params["last"] = a.last
        if a.since: params["since"] = a.since
        if a.until: params["until"] = a.until
        if a.level: params["level"] = a.level
        if a.contains: params["contains"] = a.contains
        out(_request("GET", "/api/logs", params=params))
    elif a.cmd == "fs-list": out(_request("GET", "/api/fs/list", params={"path": a.path}))
    elif a.cmd == "fs-info": out(_request("GET", "/api/fs/info", params={"path": a.path}))
    elif a.cmd == "fs-read": out(_request("GET", "/api/fs/read", params={"path": a.path, "maxBytes": a.max_bytes}))
    elif a.cmd == "fs-write": out(_request("POST", "/api/fs/write", body={"path": a.path, "content": a.content, "append": a.append}))
    elif a.cmd == "fs-mkdir": out(_request("POST", "/api/fs/mkdir", body={"path": a.path}))
    elif a.cmd == "fs-del": out(_request("POST", "/api/fs/delete", body={"path": a.path}))
    elif a.cmd == "fs-copy": out(_request("POST", "/api/fs/copy", body={"src": a.src, "dst": a.dst}))
    elif a.cmd == "fs-move": out(_request("POST", "/api/fs/move", body={"src": a.src, "dst": a.dst}))
    elif a.cmd == "backup":
        body = {}
        if a.name: body["name"] = a.name
        if a.dest: body["dest"] = a.dest
        out(_request("POST", "/api/backup", body=body))
    elif a.cmd == "backups": out(_request("GET", "/api/backups"))


if __name__ == "__main__":
    main()
