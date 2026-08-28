package mcagent;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class McAgentCommand implements CommandExecutor, TabCompleter {

    private static final List<String> FEATURE_KEYS = Arrays.asList(
            "status", "worlds", "players", "inventory", "plugins", "plugin_action",
            "logs", "backups", "fs", "command", "commands", "broadcast", "whitelist",
            "maintenance", "server_stop", "backup_create", "player_action"
    );

    private final McAgentBridge plugin;
    private final Map<String, Boolean> features;

    public McAgentCommand(McAgentBridge plugin, Map<String, Boolean> features) {
        this.plugin = plugin;
        this.features = features;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mcagentbridge.admin")) {
            sender.sendMessage("§c[MAB] 你没有权限执行该命令（需要 mcagentbridge.admin）。");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("§e[MAB] 功能开关（/mab <功能> [on|off|toggle]）：");
            for (String k : FEATURE_KEYS) {
                boolean on = features.getOrDefault(k, Boolean.TRUE);
                sender.sendMessage("  " + (on ? "§a✔ " : "§c✘ ") + k + " §7= " + (on ? "开启" : "关闭"));
            }
            return true;
        }

        String key = args[0].toLowerCase();
        if (!FEATURE_KEYS.contains(key)) {
            sender.sendMessage("§c[MAB] 未知功能：§f" + args[0] + " §7（用 /mab list 查看可用功能）");
            return true;
        }

        boolean newVal;
        if (args.length >= 2) {
            String v = args[1].toLowerCase();
            if (v.equals("on") || v.equals("true") || v.equals("enable") || v.equals("1")) {
                newVal = true;
            } else if (v.equals("off") || v.equals("false") || v.equals("disable") || v.equals("0")) {
                newVal = false;
            } else if (v.equals("toggle")) {
                newVal = !features.getOrDefault(key, Boolean.TRUE);
            } else {
                sender.sendMessage("§c[MAB] 用法：/mab " + key + " <on|off|toggle>");
                return true;
            }
        } else {
            newVal = !features.getOrDefault(key, Boolean.TRUE);
        }

        features.put(key, newVal);
        plugin.getConfig().set("features." + key, newVal);
        plugin.saveConfig();

        sender.sendMessage("§a[MAB] 功能 §f" + key + " §a已" + (newVal ? "开启" : "关闭")
                + " §7（已保存到 config.yml，立即生效）");
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§e[MAB] McAgentBridge 指令：");
        s.sendMessage("  §f/mab list §7- 列出所有功能开关");
        s.sendMessage("  §f/mab <功能> §7- 切换该功能的开/关（自动取反）");
        s.sendMessage("  §f/mab <功能> on|off|toggle §7- 设定状态");
        s.sendMessage("  §7功能名：" + String.join(" / ", FEATURE_KEYS));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> res = new ArrayList<>();
        if (args.length <= 1) {
            String p = args.length == 1 ? args[0].toLowerCase() : "";
            if ("list".startsWith(p)) res.add("list");
            for (String k : FEATURE_KEYS) if (k.startsWith(p)) res.add(k);
        } else if (args.length == 2) {
            String p = args[1].toLowerCase();
            for (String v : new String[]{"on", "off", "toggle"}) if (v.startsWith(p)) res.add(v);
        }
        return res;
    }
}
