package mcagent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class ApiServer {

    private static final Object NOT_HANDLED = new Object();

    private final JavaPlugin plugin;
    private final String token;
    private final boolean readOnly;
    private final LogCapture logCapture;
    private final FsOps fsOps;
    private HttpServer server;

    public ApiServer(JavaPlugin plugin, String host, int port, String token, boolean readOnly,
                     LogCapture logCapture, File serverRoot) throws IOException {
        this.plugin = plugin;
        this.token = token;
        this.readOnly = readOnly;
        this.logCapture = logCapture;
        this.fsOps = new FsOps(serverRoot);
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.createContext("/", new Handler());
        this.server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mc-agent-bridge-http");
            t.setDaemon(true);
            return t;
        }));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // ------------------------------------------------------------------ utils

    private boolean authed(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7).equals(token);
        String q = ex.getRequestURI().getQuery();
        if (q != null) {
            for (String pair : q.split("&")) {
                if (pair.startsWith("token=")) return pair.substring(6).equals(token);
            }
        }
        return false;
    }

    private static String serverSoftware() {
        if (Schedulers.isFolia()) return "Folia";
        String name = Bukkit.getServer().getName();
        if (name != null) {
            String n = name.toLowerCase();
            if (n.contains("purpur")) return "Purpur";
            if (n.contains("paper")) return "Paper";
            if (n.contains("spigot")) return "Spigot";
            if (n.contains("craftbukkit")) return "CraftBukkit";
            return name;
        }
        return "Unknown";
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        ex.sendResponseHeaders(code, b.length);
        if (b.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(HttpExchange ex) {
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.isEmpty()) return new LinkedHashMap<>();
            Object parsed = Json.parse(body);
            if (parsed instanceof Map) return (Map<String, Object>) parsed;
        } catch (Exception ignored) {
        }
        return new LinkedHashMap<>();
    }

    private String qp(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return null;
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(key) && kv.length == 2) {
                try {
                    return URLDecoder.decode(kv[1], "UTF-8");
                } catch (Exception e) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private static Map<String, Object> map(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    private static double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    private static Object err(String m) {
        return map("error", m);
    }

    private static Object forbidden() {
        return map("error", "server is in read-only mode");
    }

    private Object mutating() {
        return readOnly ? forbidden() : null;
    }

    // --------------------------------------------------------------- routing

    private Object route(String method, String path, HttpExchange ex) throws Exception {
        if (method.equals("GET") && path.equals("/api/health")) {
            return map("status", "ok", "folia", Schedulers.isFolia(),
                    "server_software", serverSoftware(),
                    "minecraft_version", Bukkit.getServer().getBukkitVersion());
        }
        if (method.equals("GET") && path.equals("/api/status")) return status();
        if (method.equals("GET") && path.equals("/api/worlds")) return worlds();
        if (method.equals("GET") && path.equals("/api/players")) return players(null);
        if (method.equals("GET") && path.equals("/api/plugins")) return pluginsList();
        if (method.equals("GET") && path.equals("/api/logs")) return logs(ex);
        if (method.equals("GET") && path.equals("/api/backups")) return backupsList();
        if (method.equals("GET") && path.startsWith("/api/fs/")) return fsRead(method, path, ex);

        if (path.equals("/api/command") && method.equals("POST")) {
            Object m = mutating(); if (m != null) return m;
            Map<String, Object> j = readJson(ex);
            String cmd = (String) j.get("command");
            if (cmd == null || cmd.isEmpty()) return err("missing 'command'");
            return runCommand(cmd);
        }
        if (path.equals("/api/commands") && method.equals("POST")) {
            Object m = mutating(); if (m != null) return m;
            return runCommands(readJson(ex));
        }
        if (path.equals("/api/broadcast") && method.equals("POST")) {
            Object m = mutating(); if (m != null) return m;
            Map<String, Object> j = readJson(ex);
            String msg = (String) j.get("message");
            if (msg == null || msg.isEmpty()) return err("missing 'message'");
            Schedulers.syncRun(plugin, () -> Bukkit.broadcastMessage(msg));
            return map("broadcasted", true);
        }
        if (path.equals("/api/whitelist") && method.equals("POST")) return whitelist(readJson(ex));
        if (path.equals("/api/maintenance") && method.equals("POST")) return maintenance(readJson(ex));
        if (path.equals("/api/server/stop") && method.equals("POST")) {
            Object m = mutating(); if (m != null) return m;
            Schedulers.syncRun(plugin, () -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "stop"));
            return map("stopping", true);
        }
        if (path.equals("/api/backup") && method.equals("POST")) {
            Object m = mutating(); if (m != null) return m;
            return backup(readJson(ex));
        }

        if (path.startsWith("/api/players/") && method.equals("GET")) {
            String[] seg = path.split("/");
            if (seg.length == 4) return players(seg[3]);
            if (seg.length == 5 && seg[4].equals("inventory")) return inventory(seg[3]);
            if (seg.length == 5 && seg[4].equals("enderchest")) return enderchest(seg[3]);
        }
        if (path.startsWith("/api/players/") && method.equals("POST")) {
            String[] seg = path.split("/");
            if (seg.length == 5) {
                Object m = mutating(); if (m != null) return m;
                return playerAction(seg[4], seg[3], readJson(ex));
            }
        }

        if (path.startsWith("/api/plugins/") && method.equals("POST")) {
            String[] seg = path.split("/");
            if (seg.length == 5) {
                Object m = mutating(); if (m != null) return m;
                return pluginAction(seg[3], seg[4]);
            }
        }

        if (path.startsWith("/api/fs/") && method.equals("POST")) return fsWrite(path, ex);

        return NOT_HANDLED;
    }

    // ------------------------------------------------------------ data access

    private Object status() throws Exception {
        return Schedulers.sync(plugin, () -> {
        Map<String, Object> r = new LinkedHashMap<>();
        double[] tps;
        try { tps = Bukkit.getServer().getTPS(); } catch (Throwable t) { tps = new double[]{-1, -1, -1}; }
        r.put("tps", new double[]{round(tps[0]), round(tps[1]), round(tps[2])});
        double mspt;
        try { mspt = Bukkit.getServer().getAverageTickTime(); } catch (Throwable t) { mspt = -1; }
        r.put("mspt", round(mspt));
        r.put("uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
        r.put("server_software", serverSoftware());
        r.put("minecraft_version", Bukkit.getServer().getBukkitVersion());
        r.put("server_version", Bukkit.getServer().getVersion());
        r.put("folia", Schedulers.isFolia());
            r.put("online_players", Bukkit.getOnlinePlayers().size());
            r.put("max_players", Bukkit.getServer().getMaxPlayers());
            Runtime rt = Runtime.getRuntime();
            r.put("memory", map(
                    "used_mb", (rt.totalMemory() - rt.freeMemory()) / 1048576L,
                    "free_mb", rt.freeMemory() / 1048576L,
                    "max_mb", rt.maxMemory() / 1048576L));
            r.put("worlds", worlds());
            return r;
        });
    }

    private Object worlds() throws Exception {
        return Schedulers.sync(plugin, () -> {
            List<Object> list = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", w.getName());
                m.put("environment", w.getEnvironment().name());
                m.put("entities", w.getEntityCount());
                m.put("chunks", w.getLoadedChunks().length);
                m.put("players", w.getPlayers().size());
                list.add(m);
            }
            return list;
        });
    }

    @SuppressWarnings("deprecation")
    private Object players(String id) throws Exception {
        return Schedulers.sync(plugin, () -> {
            if (id == null) {
                List<Object> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) list.add(playerDetail(p));
                return map("count", list.size(), "players", list);
            }
            Player online = resolvePlayer(id);
            if (online != null) return playerDetail(online);
            // offline lookup
            UUID uuid = tryUuid(id);
            OfflinePlayer off = uuid != null ? Bukkit.getOfflinePlayer(uuid)
                    : Bukkit.getOfflinePlayer(id);
            if (off != null && (off.hasPlayedBefore() || uuid != null)) return offlineDetail(off);
            return err("player not found");
        });
    }

    @SuppressWarnings("deprecation")
    private Map<String, Object> playerDetail(Player p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("uuid", p.getUniqueId().toString());
        m.put("online", true);
        m.put("world", p.getWorld().getName());
        m.put("x", round(p.getLocation().getX()));
        m.put("y", round(p.getLocation().getY()));
        m.put("z", round(p.getLocation().getZ()));
        m.put("yaw", round(p.getLocation().getYaw()));
        m.put("gamemode", p.getGameMode().name());
        m.put("health", p.getHealth());
        m.put("max_health", p.getMaxHealth());
        m.put("food", p.getFoodLevel());
        m.put("saturation", round(p.getSaturation()));
        m.put("exhaustion", round(p.getExhaustion()));
        m.put("exp_level", p.getLevel());
        m.put("exp_progress", round(p.getExp()));
        m.put("total_exp", p.getTotalExperience());
        m.put("flying", p.isFlying());
        m.put("allow_flight", p.getAllowFlight());
        m.put("op", p.isOp());
        m.put("ping", p.getPing());
        m.put("ip", p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : null);
        m.put("locale", p.getLocale());
        m.put("first_played", p.getFirstPlayed());
        m.put("last_played", p.getLastPlayed());
        List<Object> pots = new ArrayList<>();
        for (PotionEffect pe : p.getActivePotionEffects()) {
            pots.add(map("type", pe.getType().getName(), "amplifier", pe.getAmplifier(), "duration_ticks", pe.getDuration()));
        }
        m.put("potion_effects", pots);
        return m;
    }

    @SuppressWarnings("deprecation")
    private Map<String, Object> offlineDetail(OfflinePlayer p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("uuid", p.getUniqueId().toString());
        m.put("online", false);
        m.put("op", p.isOp());
        m.put("banned", p.isBanned());
        m.put("whitelisted", p.isWhitelisted());
        m.put("has_played_before", p.hasPlayedBefore());
        m.put("first_played", p.getFirstPlayed());
        m.put("last_played", p.getLastPlayed());
        return m;
    }

    private Player resolvePlayer(String id) {
        UUID uuid = tryUuid(id);
        if (uuid != null) return Bukkit.getPlayer(uuid);
        return Bukkit.getPlayer(id);
    }

    private static UUID tryUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Object inventory(String id) throws Exception {
        return Schedulers.sync(plugin, () -> {
            Player p = resolvePlayer(id);
            if (p == null) return err("player not online");
            PlayerInventory inv = p.getInventory();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("player", p.getName());
            r.put("uuid", p.getUniqueId().toString());
            r.put("main", Items.serialize(inv));
            r.put("armor", Items.serialize(inv.getArmorContents()));
            r.put("offhand", Items.serialize(inv.getItemInOffHand()));
            return r;
        });
    }

    private Object enderchest(String id) throws Exception {
        return Schedulers.sync(plugin, () -> {
            Player p = resolvePlayer(id);
            if (p == null) return err("player not online");
            Inventory inv = p.getEnderChest();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("player", p.getName());
            r.put("uuid", p.getUniqueId().toString());
            r.put("contents", Items.serialize(inv));
            return r;
        });
    }

    private Object runCommand(String cmd) throws Exception {
        return Schedulers.sync(plugin, () -> {
            org.bukkit.command.BufferedCommandSender sender = new org.bukkit.command.BufferedCommandSender();
            boolean ok;
            try {
                ok = Bukkit.getServer().dispatchCommand(sender, cmd);
            } catch (Exception e) {
                return map("command", cmd, "success", false, "error", e.getMessage(), "output", sender.getBuffer());
            }
            return map("command", cmd, "success", ok, "output", sender.getBuffer());
        });
    }

    @SuppressWarnings({"removal", "deprecation"})
    private Object runCommands(Map<String, Object> j) throws Exception {
        return Schedulers.sync(plugin, () -> {
            List<String> cmds = new ArrayList<>();
            Object list = j.get("commands");
            if (list instanceof List) {
                for (Object o : (List<?>) list) cmds.add(String.valueOf(o));
            } else if (j.get("command") instanceof String) {
                for (String c : ((String) j.get("command")).split("\n")) {
                    if (!c.trim().isEmpty()) cmds.add(c);
                }
            }
            List<Object> results = new ArrayList<>();
            for (String c : cmds) {
                org.bukkit.command.BufferedCommandSender sender = new org.bukkit.command.BufferedCommandSender();
                boolean ok;
                try {
                    ok = Bukkit.getServer().dispatchCommand(sender, c);
                } catch (Exception e) {
                    results.add(map("command", c, "success", false, "error", e.getMessage(), "output", sender.getBuffer()));
                    continue;
                }
                results.add(map("command", c, "success", ok, "output", sender.getBuffer()));
            }
            return map("count", results.size(), "results", results);
        });
    }

    @SuppressWarnings("deprecation")
    private Object whitelist(Map<String, Object> j) throws Exception {
        Object m = mutating(); if (m != null) return m;
        String action = (String) j.get("action");
        if ("list".equals(action)) {
            return Schedulers.sync(plugin, () -> {
                List<String> names = new ArrayList<>();
                for (OfflinePlayer p : Bukkit.getWhitelistedPlayers()) names.add(p.getName());
                return map("whitelisted", names);
            });
        }
        String name = (String) j.get("name");
        if (name == null || name.isEmpty()) return err("missing 'name'");
        String cmd;
        switch (action == null ? "" : action) {
            case "add": cmd = "whitelist add " + name; break;
            case "remove": cmd = "whitelist remove " + name; break;
            case "on": cmd = "whitelist on"; break;
            case "off": cmd = "whitelist off"; break;
            default: return err("unknown whitelist action (add|remove|on|off|list)");
        }
        return runCommand(cmd);
    }

    private Object maintenance(Map<String, Object> j) {
        Object m = mutating(); if (m != null) return m;
        String action = (String) j.get("action");
        if ("enable".equals(action)) {
            Schedulers.syncRun(plugin, () -> {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "whitelist on");
                Bukkit.broadcastMessage("[Maintenance] Server is now in maintenance mode.");
            });
            return map("maintenance", true);
        } else if ("disable".equals(action)) {
            Schedulers.syncRun(plugin, () -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "whitelist off"));
            return map("maintenance", false);
        }
        return err("unknown action (enable|disable)");
    }

    private Object pluginsList() throws Exception {
        return Schedulers.sync(plugin, () -> {
            List<Object> list = new ArrayList<>();
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.getName());
                m.put("version", p.getDescription().getVersion());
                m.put("enabled", p.isEnabled());
                m.put("authors", new ArrayList<>(p.getDescription().getAuthors()));
                list.add(m);
            }
            return list;
        });
    }

    private Object pluginAction(String name, String action) throws Exception {
        return Schedulers.sync(plugin, () -> {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p == null) return err("plugin not found: " + name);
            switch (action) {
                case "enable":
                    if (!p.isEnabled()) Bukkit.getPluginManager().enablePlugin(p);
                    break;
                case "disable":
                    if (p.isEnabled()) Bukkit.getPluginManager().disablePlugin(p);
                    break;
                case "reload":
                    if (p.isEnabled()) Bukkit.getPluginManager().disablePlugin(p);
                    Bukkit.getPluginManager().enablePlugin(p);
                    break;
                default:
                    return err("unknown plugin action (enable|disable|reload)");
            }
            return map("plugin", name, "enabled", p.isEnabled(), "action", action);
        });
    }

    private Object logs(HttpExchange ex) throws Exception {
        String sinceS = qp(ex, "since");
        String untilS = qp(ex, "until");
        String lastS = qp(ex, "last");
        String level = qp(ex, "level");
        String contains = qp(ex, "contains");
        String limitS = qp(ex, "limit");
        long since = sinceS != null ? Long.parseLong(sinceS) : 0;
        long until = untilS != null ? Long.parseLong(untilS) : 0;
        int limit = limitS != null ? Integer.parseInt(limitS) : 200;
        if (lastS != null) {
            long ms = Durations.parse(lastS);
            if (ms > 0) since = System.currentTimeMillis() - ms;
        }
        if (since == 0 && until == 0 && lastS == null) {
            return map("lines", logCapture.recent(limit));
        }
        return map("lines", logCapture.query(since, until, level, contains, limit));
    }

    // ---- file system ----

    private Object fsRead(String method, String path, HttpExchange ex) throws Exception {
        String sub = path.substring("/api/fs/".length()); // info|list|read
        String p = qp(ex, "path");
        if (p == null) p = "";
        switch (sub) {
            case "info":
                return fsOps.info(p);
            case "list":
                return fsOps.list(p);
            case "read": {
                int max = 1_000_000;
                String mb = qp(ex, "maxBytes");
                if (mb != null) max = Integer.parseInt(mb);
                if (readOnly) return forbidden();
                return fsOps.read(p, max);
            }
            default:
                return NOT_HANDLED;
        }
    }

    private Object fsWrite(String path, HttpExchange ex) throws Exception {
        Object m = mutating(); if (m != null) return m;
        String sub = path.substring("/api/fs/".length());
        Map<String, Object> j = readJson(ex);
        String p = (String) j.get("path");
        if (p == null || p.isEmpty()) return err("missing 'path'");
        switch (sub) {
            case "write": {
                String content = (String) j.get("content");
                boolean append = Boolean.TRUE.equals(j.get("append"));
                return fsOps.write(p, content, append);
            }
            case "delete":
                return fsOps.delete(p);
            case "mkdir":
                return fsOps.mkdir(p);
            case "copy":
                return fsOps.copy(p, (String) j.get("dst"));
            case "move":
                return fsOps.move(p, (String) j.get("dst"));
            default:
                return err("unknown fs action (write|delete|mkdir|copy|move)");
        }
    }

    // ---- backup ----

    private Object backup(Map<String, Object> j) throws Exception {
        File destDir = new File(fsOps.getRoot(), j.get("dest") != null ? (String) j.get("dest") : "backups");
        String name = j.get("name") != null ? (String) j.get("name")
                : "backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        if (!name.endsWith(".zip")) name += ".zip";
        long start = System.currentTimeMillis();
        File out = Backup.zip(fsOps.getRoot(), destDir, name);
        return map("file", out.getName(), "path", out.getAbsolutePath(), "size", out.length(),
                "duration_ms", System.currentTimeMillis() - start);
    }

    private Object backupsList() {
        File dir = new File(fsOps.getRoot(), "backups");
        List<Object> list = new ArrayList<>();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, n) -> n.endsWith(".zip"));
            if (files != null) {
                for (File f : files) {
                    list.add(map("name", f.getName(), "size", f.length(), "modified", f.lastModified()));
                }
            }
        }
        return map("count", list.size(), "backups", list);
    }

    // ---- player actions (console commands) ----

    private Object playerAction(String action, String id, Map<String, Object> j) throws Exception {
        String cmd;
        switch (action) {
            case "kick": {
                String reason = (String) j.get("reason");
                cmd = "kick " + id + (reason != null && !reason.isEmpty() ? " " + reason : "");
                break;
            }
            case "ban": {
                String reason = (String) j.get("reason");
                String duration = (String) j.get("duration");
                cmd = "ban " + id;
                if (duration != null && !duration.isEmpty()) cmd += " " + duration;
                if (reason != null && !reason.isEmpty()) cmd += " " + reason;
                break;
            }
            case "op": cmd = "op " + id; break;
            case "deop": cmd = "deop " + id; break;
            case "tp": {
                if (j.get("target") != null) cmd = "tp " + id + " " + j.get("target");
                else if (j.get("x") != null && j.get("y") != null && j.get("z") != null)
                    cmd = "tp " + id + " " + j.get("x") + " " + j.get("y") + " " + j.get("z");
                else return err("tp requires 'target' or 'x','y','z'");
                break;
            }
            case "give": {
                String item = (String) j.get("item");
                if (item == null || item.isEmpty()) return err("missing 'item'");
                int amount = j.get("amount") != null ? ((Number) j.get("amount")).intValue() : 1;
                cmd = "give " + id + " " + item + " " + amount;
                break;
            }
            case "msg": {
                String message = (String) j.get("message");
                if (message == null || message.isEmpty()) return err("missing 'message'");
                cmd = "msg " + id + " " + message;
                break;
            }
            default:
                return err("unknown player action (kick|ban|op|deop|tp|give|msg)");
        }
        return runCommand(cmd);
    }

    // -------------------------------------------------------------- handler

    private class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                String method = ex.getRequestMethod();
                String path = ex.getRequestURI().getPath();
                if (method.equals("OPTIONS")) {
                    send(ex, 204, null);
                    return;
                }
                if (!path.equals("/api/health") && !authed(ex)) {
                    plugin.getLogger().warning("McAgentBridge: unauthorized request rejected: "
                            + method + " " + path + " from " + ex.getRemoteAddress());
                    send(ex, 401, Json.toJson(err("unauthorized")));
                    return;
                }
                Object result = route(method, path, ex);
                if (result == NOT_HANDLED) {
                    send(ex, 404, Json.toJson(err("not_found")));
                    return;
                }
                send(ex, 200, Json.toJson(result));
            } catch (Exception e) {
                send(ex, 500, Json.toJson(map("error", String.valueOf(e.getMessage()))));
            } finally {
                ex.close();
            }
        }
    }
}
