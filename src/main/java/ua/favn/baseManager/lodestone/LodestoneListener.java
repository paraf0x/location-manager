package ua.favn.baseManager.lodestone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.base.util.BlockLocation;
import ua.favn.baseManager.base.util.FormatUtil;
import ua.favn.baseManager.base.util.ItemStackSerializer;
import ua.favn.baseManager.location.SavedLocation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles lodestone + sign registration via waxing, and destruction events.
 *
 * Flow:
 * 1. Player places sign on lodestone with [TAG] + name -> validation only, info message
 * 2. Player optionally places item frame on lodestone with an icon item
 * 3. Player waxes the sign with honeycomb -> location is registered
 * 4. Breaking lodestone or sign -> location is deleted
 */
public class LodestoneListener extends Base implements Listener {

    private static final Pattern TAG_PATTERN = Pattern.compile("^\\[([A-Z0-9]+)]$", Pattern.CASE_INSENSITIVE);
    private static final BlockFace[] ADJACENT_FACES = {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    public LodestoneListener(BaseManager plugin) {
        super(plugin);
    }

    /**
     * Handle sign placement - validate only, do NOT register.
     * Just inform the player to wax the sign to confirm.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSignChange(SignChangeEvent event) {
        LodestoneManager manager = getPlugin().getLodestoneManager();
        if (!manager.isEnabled()) {
            return;
        }

        // Check first line for tag
        String firstLine = PlainTextComponentSerializer.plainText().serialize(event.line(0)).trim();
        if (firstLine.isEmpty()) {
            return; // No tag at all, not a location sign
        }

        String tag = extractTag(firstLine);
        if (tag == null) {
            // No [] brackets - use the text directly as tag
            tag = firstLine.toUpperCase();
        }

        // Build name from lines 2-4
        String name = buildName(event.line(1), event.line(2), event.line(3));
        if (name.isEmpty()) {
            return; // No name, not a location sign
        }

        // Find adjacent lodestone
        Block signBlock = event.getBlock();
        Block lodestone = findAdjacentLodestone(signBlock);
        if (lodestone == null) {
            return; // Not adjacent to lodestone, just a regular sign
        }

        // Validation passed - inform player to wax
        getPlugin().getMessageManager().send(event.getPlayer(), "lodestone.wax-to-confirm",
            new FormatUtil.Format("{tag}", tag),
            new FormatUtil.Format("{name}", name));
    }

    /**
     * Handle sign waxing - this is where location registration happens.
     * Player right-clicks a sign with honeycomb adjacent to a lodestone.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSignWax(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.HONEYCOMB) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !isSign(block.getType())) {
            return;
        }

        Sign sign = (Sign) block.getState();
        if (sign.isWaxed()) {
            return; // Already waxed
        }

        LodestoneManager manager = getPlugin().getLodestoneManager();
        if (!manager.isEnabled()) {
            return;
        }

        // Read tag from line 1
        String firstLine = PlainTextComponentSerializer.plainText().serialize(sign.line(0)).trim();
        if (firstLine.isEmpty()) {
            return; // No tag, not a location sign
        }

        String tag = extractTag(firstLine);
        if (tag == null) {
            tag = firstLine.toUpperCase();
        }

        // Build name from lines 2-4
        String name = buildName(sign.line(1), sign.line(2), sign.line(3));
        if (name.isEmpty()) {
            return; // No name
        }

        // Find adjacent lodestone
        Block lodestone = findAdjacentLodestone(block);
        if (lodestone == null) {
            return; // Not adjacent to lodestone
        }

        // Check if lodestone or sign already registered
        BlockLocation lodestonePos = BlockLocation.fromLocation(lodestone.getLocation());
        BlockLocation signPos = BlockLocation.fromLocation(block.getLocation());
        if (manager.isRegisteredBlock(lodestonePos) || manager.isRegisteredBlock(signPos)) {
            getPlugin().getMessageManager().send(event.getPlayer(), "lodestone.already-registered");
            return;
        }

        // Find item frame icon on the lodestone
        ItemStack iconItem = findItemFrameIcon(lodestone);
        String iconData = ItemStackSerializer.serialize(iconItem);

        // Validate name length
        int minLen = getPlugin().getConfig().getInt("limits.min-name-length", 2);
        int maxLen = getPlugin().getConfig().getInt("limits.max-name-length", 32);
        if (name.length() < minLen || name.length() > maxLen) {
            getPlugin().getMessageManager().send(event.getPlayer(), "errors.invalid-name-length",
                new FormatUtil.Format("{min}", minLen),
                new FormatUtil.Format("{max}", maxLen));
            return;
        }

        Player player = event.getPlayer();
        String worldName = lodestone.getWorld().getName();

        // Check if location with same tag+name already exists
        SavedLocation existing = getPlugin().getLocationManager().getByTagAndName(tag, name);

        if (existing != null) {
            // Block if this location already has coords in this dimension
            if (existing.hasCoords(worldName)) {
                getPlugin().getMessageManager().send(player, "lodestone.duplicate-dimension",
                    new FormatUtil.Format("{tag}", tag),
                    new FormatUtil.Format("{name}", name),
                    new FormatUtil.Format("{world}", worldName));
                return;
            }

            // Add coords for current dimension
            existing.setCoords(worldName,
                lodestone.getLocation().getBlockX(),
                lodestone.getLocation().getBlockY(),
                lodestone.getLocation().getBlockZ());
            existing.saveCoords(worldName);

            // Update icon if item frame present
            if (iconData != null) {
                existing.icon(iconData);
                existing.save();
            }

            // Create lodestone structure linked to existing location
            BlockLocation lodestoneBlock = BlockLocation.fromLocation(lodestone.getLocation());
            BlockLocation signBlock = BlockLocation.fromLocation(block.getLocation());
            LodestoneStructure structure = new LodestoneStructure(
                manager, existing.id(), lodestoneBlock, signBlock, tag);
            structure.save();
            manager.addToCache(structure);

            getPlugin().getMessageManager().send(player, "commands.location-coords-updated",
                new FormatUtil.Format("{tag}", tag),
                new FormatUtil.Format("{name}", name),
                new FormatUtil.Format("{world}", worldName));
        } else {
            // Check max locations limit
            int maxLocations = getPlugin().getConfig().getInt("limits.max-locations-per-player", 0);
            if (maxLocations > 0) {
                int currentCount = getPlugin().getLocationManager().getByOwner(player.getUniqueId()).size();
                if (currentCount >= maxLocations) {
                    getPlugin().getMessageManager().send(player, "errors.max-locations-reached",
                        new FormatUtil.Format("{max}", maxLocations));
                    return;
                }
            }

            // Create new location with lodestone structure
            LodestoneStructure structure = manager.create(
                player,
                lodestone.getLocation(),
                block.getLocation(),
                tag,
                name
            );

            // Set icon if item frame present
            if (iconData != null) {
                SavedLocation loc = getPlugin().getLocationManager().get(structure.locationId());
                if (loc != null) {
                    loc.icon(iconData);
                    loc.save();
                }
            }

            getPlugin().getMessageManager().send(player, "lodestone.registered",
                new FormatUtil.Format("{name}", name),
                new FormatUtil.Format("{tag}", tag));
        }

        // Let vanilla waxing proceed (sign becomes uneditable)
    }

    /**
     * Handle block break - check for lodestone structure destruction.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        LodestoneManager manager = getPlugin().getLodestoneManager();
        if (!manager.isEnabled()) {
            return;
        }

        Block block = event.getBlock();
        if (!isLodestoneOrSign(block)) {
            return;
        }

        BlockLocation blockPos = BlockLocation.fromLocation(block.getLocation());
        LodestoneStructure structure = manager.getByBlock(blockPos);

        if (structure == null) {
            return;
        }

        // Get location name for message
        SavedLocation location = getPlugin().getLocationManager().get(structure.locationId());
        String locationName = location != null ? location.displayName() : "Unknown";

        // Delete the structure (and linked location)
        manager.delete(structure);

        // Send message
        getPlugin().getMessageManager().send(event.getPlayer(), "lodestone.destroyed",
            new FormatUtil.Format("{name}", locationName));
    }

    /**
     * Handle block explosions.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    /**
     * Handle entity explosions (creeper, TNT, etc).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> affectedBlocks) {
        LodestoneManager manager = getPlugin().getLodestoneManager();
        if (!manager.isEnabled()) {
            return;
        }

        for (Block block : affectedBlocks) {
            if (!isLodestoneOrSign(block)) {
                continue;
            }

            BlockLocation pos = BlockLocation.fromLocation(block.getLocation());
            LodestoneStructure structure = manager.getByBlock(pos);
            if (structure != null) {
                SavedLocation location = getPlugin().getLocationManager().get(structure.locationId());
                String locationName = location != null ? location.displayName() : "Unknown";

                manager.delete(structure);
                getPlugin().getLogger().info("Lodestone structure destroyed by explosion: " + locationName);
            }
        }
    }

    // --- Helpers ---

    /**
     * Extract tag from bracket syntax like "[PUBLIC]" -> "PUBLIC".
     * Returns null if no brackets found.
     */
    private String extractTag(String text) {
        Matcher matcher = TAG_PATTERN.matcher(text);
        if (matcher.matches()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    /**
     * Build a location name from sign lines 2-4, joining non-empty lines with spaces.
     */
    private String buildName(Component... lines) {
        StringBuilder sb = new StringBuilder();
        for (Component line : lines) {
            String text = PlainTextComponentSerializer.plainText().serialize(line).trim();
            if (!text.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(text);
            }
        }
        return sb.toString();
    }

    /**
     * Find an item in an item frame attached to the lodestone block.
     * Returns the item (not the frame), or null if no frame/item found.
     */
    private ItemStack findItemFrameIcon(Block lodestone) {
        for (Entity entity : lodestone.getWorld().getNearbyEntities(
                lodestone.getLocation().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (entity instanceof ItemFrame frame) {
                // Check if this frame is attached to the lodestone
                Block attached = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
                if (attached.getLocation().getBlockX() == lodestone.getLocation().getBlockX()
                    && attached.getLocation().getBlockY() == lodestone.getLocation().getBlockY()
                    && attached.getLocation().getBlockZ() == lodestone.getLocation().getBlockZ()) {
                    ItemStack item = frame.getItem();
                    if (item != null && item.getType() != Material.AIR) {
                        return item.clone();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find a lodestone adjacent to the sign block.
     */
    private Block findAdjacentLodestone(Block signBlock) {
        BlockData data = signBlock.getBlockData();

        // Wall sign - check the block it's attached to
        if (data instanceof WallSign wallSign) {
            BlockFace attachedFace = wallSign.getFacing().getOppositeFace();
            Block attached = signBlock.getRelative(attachedFace);
            if (attached.getType() == Material.LODESTONE) {
                return attached;
            }
        }

        // Standing sign - check block below
        if (isStandingSign(signBlock)) {
            Block below = signBlock.getRelative(BlockFace.DOWN);
            if (below.getType() == Material.LODESTONE) {
                return below;
            }
        }

        // Check all adjacent blocks as fallback
        for (BlockFace face : ADJACENT_FACES) {
            Block adjacent = signBlock.getRelative(face);
            if (adjacent.getType() == Material.LODESTONE) {
                return adjacent;
            }
        }

        return null;
    }

    private boolean isLodestoneOrSign(Block block) {
        Material type = block.getType();
        return type == Material.LODESTONE || isSign(type);
    }

    private boolean isSign(Material material) {
        return material.name().endsWith("_SIGN");
    }

    private boolean isStandingSign(Block block) {
        Material type = block.getType();
        return type.name().endsWith("_SIGN") && !type.name().contains("WALL") && !type.name().contains("HANGING");
    }
}
