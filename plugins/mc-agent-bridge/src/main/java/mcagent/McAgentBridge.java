package mcagent;

import org.bukkit.plugin.java.JavaPlugin;

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
        var cfg = getConfig();

        if (!cfg.getBoolean("enabled", true)) {
            getLogger().info("mc-agent-bridge is disabled in config.yml");
            return;
        }

        String host = cfg.getString("host", "127.0.0.1");
        int port = cfg.getInt("port", 8080);
        readOnly = cfg.getBoolean("read-only", false);
        token = cfg.getString("token", "");

        if (token == null || token.isEmpty()) {
            token = java.util.UUID.randomUUID().toString();
            cfg.set("token", token);
            saveConfig();
            getLogger().warning("No token set in config.yml — generated a random one. Copy it from config.yml.");
        }

        logCapture = new LogCapture(cfg.getInt("log-lines", 2000));
        logCapture.attach();

        File serverRoot = new File(".").getAbsoluteFile();

        try {
            apiServer = new ApiServer(this, host, port, token, readOnly, logCapture, serverRoot);
            apiServer.start();
            getLogger().info("mc-agent-bridge API listening on http://" + host + ":" + port + "/");
            getLogger().info("Authorization: Bearer " + token);
            if ("127.0.0.1".equals(host)) {
                getLogger().info("Bound to localhost only. Set host: 0.0.0.0 in config.yml to expose (use a reverse proxy + TLS).");
            }
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
