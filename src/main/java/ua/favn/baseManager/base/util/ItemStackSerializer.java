package ua.favn.baseManager.base.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class ItemStackSerializer {

    /**
     * Serializes an ItemStack to a Base64-encoded YAML string.
     */
    public static String serialize(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", item);
        return Base64.getEncoder().encodeToString(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserializes an ItemStack from a Base64-encoded YAML string.
     */
    public static ItemStack deserialize(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            String yamlStr = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(yamlStr);
            return yaml.getItemStack("item");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts the custom display name from an ItemStack, if present.
     * Returns null if the item has no custom name.
     */
    public static String extractDisplayName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        return null;
    }

    /**
     * Categorizes an ItemStack by its metadata type for filtering and search.
     */
    public static String categorize(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "PLAIN";
        }
        ItemMeta meta = item.getItemMeta();

        if (meta instanceof EnchantmentStorageMeta) {
            return "ENCHANTED_BOOK";
        }
        if (meta instanceof PotionMeta) {
            return "POTION";
        }
        if (meta instanceof BannerMeta) {
            return "BANNER";
        }
        if (meta instanceof FireworkMeta) {
            return "FIREWORK";
        }
        if (meta instanceof BookMeta) {
            return "BOOK";
        }
        if (meta instanceof SkullMeta) {
            return "PLAYER_HEAD";
        }
        if (meta instanceof BlockStateMeta bsm) {
            if (bsm.getBlockState() instanceof ShulkerBox) {
                return "SHULKER_BOX";
            }
            return "BLOCK_STATE";
        }
        if (meta.hasDisplayName() || meta.hasLore()) {
            return "CUSTOM_NAMED";
        }
        if (meta.hasEnchants()) {
            return "ENCHANTED";
        }
        return "PLAIN";
    }

    /**
     * Computes a hash of key metadata for equality comparison.
     * Items with the same hash are considered the "same item type".
     */
    public static String computeMetaHash(ItemStack item) {
        if (item == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(item.getType().name());

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();

            // Include display name in hash
            if (meta.hasDisplayName()) {
                sb.append(":name=").append(extractDisplayName(item));
            }

            // Include enchantments
            if (meta instanceof EnchantmentStorageMeta esm) {
                esm.getStoredEnchants().entrySet().stream()
                    .sorted((a, b) -> a.getKey().getKey().toString().compareTo(b.getKey().getKey().toString()))
                    .forEach(entry -> sb.append(":ench=")
                        .append(entry.getKey().getKey())
                        .append("@")
                        .append(entry.getValue()));
            } else if (meta.hasEnchants()) {
                meta.getEnchants().entrySet().stream()
                    .sorted((a, b) -> a.getKey().getKey().toString().compareTo(b.getKey().getKey().toString()))
                    .forEach(entry -> sb.append(":ench=")
                        .append(entry.getKey().getKey())
                        .append("@")
                        .append(entry.getValue()));
            }

            // Include potion type
            if (meta instanceof PotionMeta pm && pm.getBasePotionType() != null) {
                sb.append(":potion=").append(pm.getBasePotionType().name());
            }

            // Include skull owner
            if (meta instanceof SkullMeta sm && sm.hasOwner()) {
                sb.append(":skull=").append(sm.getOwningPlayer() != null
                    ? sm.getOwningPlayer().getUniqueId()
                    : "unknown");
            }
        }

        // Compute MD5 hash for compact storage
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }

    /**
     * Checks if two items are functionally equivalent (same type with same key metadata).
     */
    public static boolean areEquivalent(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return a == b;
        }
        String hashA = computeMetaHash(a);
        String hashB = computeMetaHash(b);
        return hashA != null && hashA.equals(hashB);
    }

    /**
     * Checks if an ItemStack has any meaningful metadata beyond its Material type.
     */
    public static boolean hasMetadata(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName()
            || meta.hasLore()
            || meta.hasEnchants()
            || meta instanceof EnchantmentStorageMeta
            || meta instanceof PotionMeta
            || meta instanceof BannerMeta
            || meta instanceof FireworkMeta
            || meta instanceof BookMeta
            || (meta instanceof SkullMeta sm && sm.hasOwner())
            || meta instanceof BlockStateMeta;
    }

    /**
     * Extracts searchable content from a Shulker Box.
     * Returns a space-separated string of all contained material names.
     */
    public static String extractShulkerContents(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) {
            return null;
        }
        if (!(bsm.getBlockState() instanceof ShulkerBox shulker)) {
            return null;
        }
        Inventory inv = shulker.getInventory();
        StringBuilder sb = new StringBuilder();
        for (ItemStack content : inv.getContents()) {
            if (content != null && !content.getType().isAir()) {
                String matName = content.getType().name().toLowerCase().replace("_", " ");
                sb.append(matName).append(" ");
                // Also include display name if present
                String displayName = extractDisplayName(content);
                if (displayName != null) {
                    sb.append(displayName).append(" ");
                }
            }
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    /**
     * Checks if an ItemStack is a Shulker Box.
     */
    public static boolean isShulkerBox(ItemStack item) {
        if (item == null) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("SHULKER_BOX");
    }
}
