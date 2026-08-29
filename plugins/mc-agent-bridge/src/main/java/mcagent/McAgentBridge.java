package mcagent;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.logging.Logger;

public class McAgentBridge extends JavaPlugin {

    private ApiServer apiServer;
    private LogCapture logCapture;
    private String token;
    private boolean readOnly;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration cfg = getConfig();

        if (!cfg.getBoolean("enabled", true)) {
            getLogger().info("mc-agent-bridge is disabled in config.yml");
            return;
        }

        String cfgHost = cfg.getString("host", "127.0.0.1");
        int port = cfg.getInt("port", 8080);
        readOnly = cfg.getBoolean("read-only", false);
        token = cfg.getString("token", "");

        boolean allowLan = cfg.getBoolean("exposure.allow_lan", false);
        boolean allowPublic = cfg.getBoolean("exposure.allow_public", false);

        java.util.Map<String, Boolean> features = new java.util.HashMap<>();
        String[] known = {"status", "worlds", "players", "inventory", "plugins", "plugin_action",
                "logs", "backups", "fs", "command", "commands", "broadcast", "whitelist",
                "maintenance", "server_stop", "backup_create", "player_action"};
        for (String k : known) features.put(k, Boolean.TRUE);
        org.bukkit.configuration.ConfigurationSection fsec = cfg.getConfigurationSection("features");
        if (fsec != null) for (String k : fsec.getKeys(false)) features.put(k, fsec.getBoolean(k));

        if (token == null || token.isEmpty()) {
            token = java.util.UUID.randomUUID().toString();
            cfg.set("token", token);
            saveConfig();
            getLogger().warning("No token set in config.yml — generated a random one. Copy it from config.yml.");
        }

        logCapture = new LogCapture(cfg.getInt("log-lines", 2000));
        logCapture.attach();

        File serverRoot = new File(".").getAbsoluteFile();

        String bindHost = (allowPublic || allowLan) ? "0.0.0.0" : cfgHost;

        try {
            apiServer = new ApiServer(this, bindHost, port, token, readOnly, allowLan, allowPublic, features, logCapture, serverRoot);
            apiServer.start();
            getLogger().info("mc-agent-bridge API listening on http://" + bindHost + ":" + port + "/");
            getLogger().info("Authorization: Bearer " + token);
            if (allowPublic) {
                getLogger().severe("=============================================================");
                getLogger().severe("WARNING: API bound to 0.0.0.0 = PUBLIC network access.");
                getLogger().severe("Anyone who can reach this port AND knows the token can fully control the server!");
                getLogger().severe("Use a firewall / reverse proxy + TLS. You have been warned.");
                getLogger().severe("=============================================================");
            } else if (allowLan) {
                getLogger().warning("API bound to 0.0.0.0 = LAN access. Restrict untrusted networks via firewall.");
            } else {
                getLogger().info("Bound to localhost only. Enable exposure.allow_lan / allow_public in config.yml to widen (see warnings).");
            }

            final java.util.Map<String, Boolean> fCopy = new java.util.HashMap<>(features);
            Schedulers.submit(this, () -> {
                PluginCommand mabCmd = getCommand("mab");
                if (mabCmd != null) {
                    mabCmd.setExecutor(new McAgentCommand(this, fCopy));
                    mabCmd.setTabCompleter(new McAgentCommand(this, fCopy));
                    getLogger().info("Registered /mab command for in-game feature toggles (permission: mcagentbridge.admin).");
                }
            });
        } catch (Exception e) {
            getLogger().severe("Failed to start mc-agent-bridge API: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (apiServer != null) apiServer.stop();
        if (logCapture != null) logCapture.detach();
        getLogger().info("mc-agent-bridge disabled.");
    }
}
