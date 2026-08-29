import os, sys, subprocess, threading, json, re, time, shutil, urllib.request, argparse, socket

SERVERS_ROOT = r"D:\code\plugins\servers"
PLUGIN_JAR   = r"D:\code\plugins\mc-agent-bridge\McAgentBridge.jar"
RUN_BASE     = r"C:\testrun"
RESULTS      = r"D:\code\plugins\mc-agent-bridge\tools\results.json"
CSV          = r"D:\code\plugins\mc-agent-bridge\tools\results.csv"
WORKERS      = 2
PER_TIMEOUT  = 480
BASE_PORT    = 26000

JDKS = {
    8:  r"C:\java\jdk8u504-b01\bin\java.exe",
    11: r"C:\Program Files\Microsoft\jdk-11.0.31.11-hotspot\bin\java.exe",
    17: r"C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\bin\java.exe",
    21: r"C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot\bin\java.exe",
    25: r"C:\Program Files\Microsoft\jdk-25.0.2.10-hotspot\bin\java.exe",
}

FEATURES = ["status","worlds","players","inventory","plugins","plugin_action","logs",
            "backups","fs","command","commands","broadcast","whitelist","maintenance",
            "server_stop","backup_create","player_action"]

lock = threading.Lock()
port_counter = [BASE_PORT]
results = {}
if os.path.exists(RESULTS):
    try: results = json.load(open(RESULTS))
    except Exception: results = {}

def parse_version(jar):
    m = re.search(r"(?:paper|folia)[-_]?((\d+)\.(\d+)(?:\.(\d+))?)", jar, re.IGNORECASE)
    if not m: return None
    ver = m.group(1)
    major, minor, patch = int(m.group(2)), int(m.group(3)), int(m.group(4) or 0)
    return ver, (major, minor, patch)

def pick_jdk(vt):
    major, minor, patch = vt
    if minor < 17: return JDKS[8]
    if minor < 20: return JDKS[17]
    if minor == 20 and patch < 5: return JDKS[17]
    return JDKS[21]

def next_port():
    with lock:
        p = port_counter[0]; port_counter[0] += 1; return p

def alloc_port():
    # pick a port that is actually free right now (avoids leftovers from prior runs)
    while True:
        cand = next_port()
        try:
            s = socket.socket(); s.settimeout(1); s.bind(("127.0.0.1", cand)); s.close()
            return cand
        except OSError:
            continue

def api_get(port, path, token=None):
    url = "http://127.0.0.1:%d%s" % (port, path)
    req = urllib.request.Request(url)
    if token: req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return -1, str(e)

def run_one(jar):
    info = parse_version(jar)
    if not info: return None
    ver, vt = info
    software = "folia" if "folia" in jar.lower() else "paper"
    java = pick_jdk(vt)
    srv_port = alloc_port()
    port = alloc_port()  # plugin HTTP port (must differ from server-port)
    token = "testtoken123"
    base = os.path.basename(jar).replace(".jar", "")
    name = re.sub(r"[^A-Za-z0-9._-]", "_", "%s-%s-%s" % (software, ver, base))
    rundir = os.path.join(RUN_BASE, name)
    if os.path.exists(rundir): shutil.rmtree(rundir)
    os.makedirs(os.path.join(rundir, "plugins", "McAgentBridge"), exist_ok=True)

    open(os.path.join(rundir, "eula.txt"), "w").write("eula=true\n")
    props = [
        "server-port=%d" % srv_port,
        "level-type=FLAT",
        "generator-settings=",
        "spawn-monsters=false",
        "spawn-npcs=false",
        "spawn-animals=false",
        "online-mode=false",
        "max-players=1",
        "motd=compat-test",
        "allow-nether=false",
        "allow-end=false",
        "enable-command-block=false",
        "enable-rcon=false",
        "query.port=%d" % (srv_port + 1),
        "white-list=false",
        "op-permission-level=4",
    ]
    open(os.path.join(rundir, "server.properties"), "w").write("\n".join(props) + "\n")
    clines = ["enabled: true", "host: 127.0.0.1", "port: %d" % port,
              'token: "%s"' % token, "read-only: false", "log-lines: 500",
              "exposure:", "  allow_lan: false", "  allow_public: false", "features:"]
    for f in FEATURES: clines.append("  %s: true" % f)
    open(os.path.join(rundir, "plugins", "McAgentBridge", "config.yml"), "w").write("\n".join(clines) + "\n")
    shutil.copy(PLUGIN_JAR, os.path.join(rundir, "plugins", "McAgentBridge.jar"))

    logpath = os.path.join(rundir, "server.log")
    res = {"jar": jar, "software": software, "version": ver, "jdk": os.path.basename(os.path.dirname(java)),
           "port": port, "status": "?", "reason": "", "log": logpath}

    def tail_log():
        try:
            with open(logpath, "r", errors="replace") as f:
                f.seek(0, 2); sz = f.tell(); f.seek(max(0, sz - 250000))
                return f.read()
        except Exception:
            return ""

    try:
        with open(logpath, "w") as lf:
            p = subprocess.Popen([java, "-Xms512M", "-Xmx1G", "-jar", jar, "nogui"],
                                 cwd=rundir, stdout=lf, stderr=subprocess.STDOUT)
            boot_ok = plugin_ok = False
            t0 = time.time()
            while time.time() - t0 < PER_TIMEOUT:
                time.sleep(3)
                tail = tail_log()
                if 'Done' in tail or 'For help, type "help"' in tail: boot_ok = True
                if "mc-agent-bridge API listening" in tail: plugin_ok = True
                if boot_ok and plugin_ok:
                    time.sleep(2)
                    hc, hbody = api_get(port, "/api/health")
                    sc, sbody = api_get(port, "/api/status", token)
                    res["health"] = hc; res["status_code"] = sc; res["status_body"] = sbody[:2000]
                    if hc == 200 and sc == 200 and '"features"' in sbody:
                        res["status"] = "PASS"; res["reason"] = "api health+status ok"
                    else:
                        res["status"] = "FAIL_RUNTIME"
                        res["reason"] = "health=%s status=%s body=%s" % (hc, sc, sbody[:400])
                        ex = extract_plugin_errors(tail)
                        if ex: res["reason"] += " | " + ex[:300]
                    break
                if p.poll() is not None:
                    break
            if p.poll() is None:
                # still running but timeout or never reached ready
                if not boot_ok:
                    res["status"] = "FAIL_ENV"
                    res["reason"] = extract_fatal(tail_log())[:300]
                elif not plugin_ok:
                    if "Unsupported API version" in tail_log():
                        res["status"] = "FAIL_API_VERSION"
                        res["reason"] = "plugin api-version higher than server"
                    else:
                        res["status"] = "FAIL_PLUGIN"
                        res["reason"] = (extract_plugin_errors(tail_log()) or "plugin did not enable")[:300]
                try: p.kill()
                except Exception: pass
            else:
                # process exited
                if not boot_ok:
                    res["status"] = "FAIL_ENV"
                    res["reason"] = extract_fatal(tail_log())[:300]
                elif not plugin_ok:
                    if "Unsupported API version" in tail_log():
                        res["status"] = "FAIL_API_VERSION"
                        res["reason"] = "plugin api-version higher than server"
                    else:
                        res["status"] = "FAIL_PLUGIN"
                        res["reason"] = (extract_plugin_errors(tail_log()) or "plugin did not enable")[:300]
    except Exception as e:
        res["status"] = "ERROR"; res["reason"] = "harness exception: %s" % e

    with lock:
        results[jar] = res
        json.dump(results, open(RESULTS, "w"), indent=2)
        write_csv()
    return res

def extract_plugin_errors(tail):
    out = []
    for line in tail.splitlines():
        if "mcagent" in line and ("Exception" in line or "Error" in line or "NoClassDef" in line
                                  or "NoSuchMethod" in line or "NoSuchField" in line or "Unsupported" in line):
            out.append(line.strip())
    return "\n".join(out[-8:])

def extract_fatal(tail):
    out = []
    for line in tail.splitlines():
        if "Error" in line or "Exception" in line or "UnsupportedClassVersion" in line or "Unable to" in line:
            out.append(line.strip())
    return "\n".join(out[-8:])

def write_csv():
    try:
        with open(CSV, "w") as f:
            f.write("software,version,jdk,status,reason,jar\n")
            for k, v in results.items():
                f.write("%s,%s,%s,%s,%s,%s\n" % (
                    v.get("software",""), v.get("version",""), v.get("jdk",""),
                    v.get("status",""), (v.get("reason","") or "").replace(",", " "), k))
    except Exception:
        pass

def kill_leftovers():
    # Kill any java server processes from previous (interrupted) test runs so they
    # don't hold the test ports.
    try:
        out = subprocess.run(["wmic", "process", "where", "name='java.exe'", "get",
                              "processid,commandline"], capture_output=True, text=True, timeout=30).stdout
    except Exception:
        return
    for line in out.splitlines():
        if "nogui" in line and ("paper-" in line or "folia-" in line):
            parts = line.split()
            pid = parts[-1]
            if pid.isdigit():
                try:
                    subprocess.run(["taskkill", "/F", "/PID", pid], capture_output=True, timeout=10)
                except Exception:
                    pass

def main():
    kill_leftovers()
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="regex filter on jar path")
    ap.add_argument("--limit", type=int, help="max number of servers")
    ap.add_argument("--workers", type=int, default=WORKERS)
    args = ap.parse_args()

    jars = []
    for root, dirs, files in os.walk(SERVERS_ROOT):
        for fn in files:
            if fn.lower().endswith(".jar") and ("paper-" in fn.lower() or "folia-" in fn.lower()):
                fp = os.path.join(root, fn)
                if os.path.getsize(fp) > 5_000_000:
                    jars.append(fp)
    jars.sort()
    if args.only:
        rx = re.compile(args.only, re.IGNORECASE)
        jars = [j for j in jars if rx.search(j)]
    # skip already done (PASS/FAIL_* recorded)
    jars = [j for j in jars if j not in results]
    if args.limit:
        jars = jars[:args.limit]

    print("TODO: %d servers" % len(jars))
    if not jars:
        print("nothing to do"); return

    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        fut = [ex.submit(run_one, j) for j in jars]
        for f in fut:
            r = f.result()
            if r: print("[%s] %s %s -> %s (%s)" % (r["software"], r["version"], r["jdk"], r["status"], r["reason"]))
    print("DONE. results -> %s" % RESULTS)

if __name__ == "__main__":
    main()
