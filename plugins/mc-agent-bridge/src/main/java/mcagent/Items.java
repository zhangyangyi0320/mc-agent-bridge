package mcagent;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializes Bukkit items/inventories into plain JSON-friendly structures. */
public final class Items {

    private Items() {}

    @SuppressWarnings("deprecation")
    public static Object serialize(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", item.getType().name());
        m.put("amount", item.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) m.put("display_name", meta.getDisplayName());
            if (meta.hasLore()) m.put("lore", new ArrayList<>(meta.getLore()));
            if (!meta.getEnchants().isEmpty()) {
                Map<String, Integer> ench = new LinkedHashMap<>();
                for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                    ench.put(e.getKey().getKey().getKey(), e.getValue());
                }
                m.put("enchantments", ench);
            }
            if (meta instanceof Damageable d && d.getDamage() > 0) m.put("damage", d.getDamage());
            if (meta.hasCustomModelData()) m.put("custom_model_data", meta.getCustomModelData());
        }
        return m;
    }

    public static List<Object> serialize(Inventory inv) {
        List<Object> list = new ArrayList<>(inv.getSize());
        for (int i = 0; i < inv.getSize(); i++) list.add(serialize(inv.getItem(i)));
        return list;
    }

    public static List<Object> serialize(ItemStack[] arr) {
        List<Object> list = new ArrayList<>(arr.length);
        for (ItemStack item : arr) list.add(serialize(item));
        return list;
    }
}
