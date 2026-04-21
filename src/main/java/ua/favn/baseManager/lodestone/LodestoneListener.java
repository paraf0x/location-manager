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
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.inventory.ItemStack;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.base.util.BlockLocation;
import ua.favn.baseManager.base.util.FormatUtil;
import ua.favn.baseManager.base.util.ItemStackSerializer;
import ua.favn.baseManager.location.MemberManager;
import ua.favn.baseManager.location.SavedLocation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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

        Block lodestone = findAdjacentLodestone(event.getBlock());
        if (lodestone == null) {
            return; // Not adjacent to lodestone, just a regular sign
        }

        ParsedSign parsed = parseSignLines(event.line(0), event.line(1), event.line(2), event.line(3));
        reportConstructionProgress(event.getPlayer(), lodestone, parsed, findItemFrameIcon(lodestone));
    }

    /**
     * Item frame attached to a lodestone → nudge player toward next step.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemFramePlace(HangingPlaceEvent event) {
        LodestoneManager manager = getPlugin().getLodestoneManager();
        if (!manager.isEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ItemFrame)) {
            return;
        }
        Block attached = event.getBlock();
        if (attached == null || attached.getType() != Material.LODESTONE) {
            return;
        }
        if (event.getPlayer() == null) {
            return;
        }
        reportConstructionProgress(event.getPlayer(), attached,
            findSignForLodestone(attached), null);
    }

    /**
     * Item placed/removed in an item frame on a lodestone → nudge player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        LodestoneManager manager = getPlugin().getLodestoneManager();
        if (!manager.isEnabled()) {
            return;
        }
        ItemFrame frame = event.getItemFrame();
        Block attached = frame.getLocation().getBlock()
            .getRelative(frame.getAttachedFace());
        if (attached.getType() != Material.LODESTONE) {
            return;
        }
        ItemStack newItem = event.getItemStack();
        if (newItem == null || newItem.getType() == Material.AIR) {
            // Item removed — still report so player knows where they stand
            reportConstructionProgress(event.getPlayer(), attached,
                findSignForLodestone(attached), null);
            return;
        }
        reportConstructionProgress(event.getPlayer(), attached,
            findSignForLodestone(attached), newItem.clone());
    }

    /**
     * Inspect the current state of a lodestone-based waypoint under
     * construction and send the player a context-aware message telling them
     * what's done, what's optional, and what's still needed. Does nothing if
     * the lodestone is already a registered waypoint.
     */
    private void reportConstructionProgress(
        Player player, Block lodestone, ParsedSign sign, ItemStack frameItem
    ) {
        LodestoneManager manager = getPlugin().getLodestoneManager();
        if (manager.isRegisteredBlock(BlockLocation.fromLocation(lodestone.getLocation()))) {
            return; // already a registered waypoint
        }
        boolean hasFrame = frameItem != null || hasFrameOnLodestone(lodestone);

        if (sign != null && frameItem != null) {
            getPlugin().getMessageManager().send(player, "lodestone.progress.ready",
                new FormatUtil.Format("{tag}", sign.tag()),
                new FormatUtil.Format("{name}", sign.name()),
                new FormatUtil.Format("{icon}", formatIcon(frameItem)));
        } else if (sign != null && hasFrame) {
            getPlugin().getMessageManager().send(player, "lodestone.progress.sign-ok-frame-empty",
                new FormatUtil.Format("{tag}", sign.tag()),
                new FormatUtil.Format("{name}", sign.name()));
        } else if (sign != null) {
            getPlugin().getMessageManager().send(player, "lodestone.wax-to-confirm",
                new FormatUtil.Format("{tag}", sign.tag()),
                new FormatUtil.Format("{name}", sign.name()));
        } else if (frameItem != null) {
            getPlugin().getMessageManager().send(player, "lodestone.progress.icon-set-need-sign",
                new FormatUtil.Format("{icon}", formatIcon(frameItem)));
        } else if (hasFrame) {
            getPlugin().getMessageManager().send(player, "lodestone.progress.frame-empty-need-sign");
        }
        // Neither sign nor frame → nothing relevant to report yet.
    }

    /**
     * Format an icon ItemStack as human-readable text for chat. Uses the
     * item's display name (e.g. HDB head's "Melon") when present, falling
     * back to the material name. Example outputs:
     *   "Melon (PLAYER_HEAD)"
     *   "DIAMOND_BLOCK"
     */
    private String formatIcon(ItemStack item) {
        if (item == null) {
            return "";
        }
        String display = ItemStackSerializer.extractDisplayName(item);
        String material = item.getType().name();
        if (display != null && !display.isEmpty() && !display.equalsIgnoreCase(material)) {
            return display + " (" + material + ")";
        }
        return material;
    }

    /**
     * Returns true if any item frame is attached to the lodestone (empty or not).
     */
    private boolean hasFrameOnLodestone(Block lodestone) {
        for (Entity entity : lodestone.getWorld().getNearbyEntities(
                lodestone.getLocation().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            Block attached = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
            if (attached.getLocation().getBlockX() == lodestone.getLocation().getBlockX()
                && attached.getLocation().getBlockY() == lodestone.getLocation().getBlockY()
                && attached.getLocation().getBlockZ() == lodestone.getLocation().getBlockZ()) {
                return true;
            }
        }
        return false;
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

        ParsedSign parsed = parseSignLines(sign.line(0), sign.line(1), sign.line(2), sign.line(3));
        if (parsed == null) {
            return; // No tag/name found
        }
        String tag = parsed.tag();
        String name = parsed.name();

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

            // Refresh map markers so the new coords (and possibly new icon)
            // propagate — saveCoords()/save() don't trigger this themselves.
            getPlugin().getLocationManager().refreshPl3xmap();

            getPlugin().getMessageManager().send(player, "commands.location-coords-updated",
                new FormatUtil.Format("{tag}", tag),
                new FormatUtil.Format("{name}", name),
                new FormatUtil.Format("{world}", worldName));

            scanAndAddMembers(lodestone, existing.id(), player);
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
                    // manager.create() already refreshed, but that was before
                    // the icon was attached — refresh again so the icon shows.
                    getPlugin().getLocationManager().refreshPl3xmap();
                }
            }

            getPlugin().getMessageManager().send(player, "lodestone.registered",
                new FormatUtil.Format("{name}", name),
                new FormatUtil.Format("{tag}", tag));

            scanAndAddMembers(lodestone, structure.locationId(), player);
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

    /**
     * Scans the structure above a lodestone for player heads and adds detected owners as members.
     */
    private void scanAndAddMembers(Block lodestone, int locationId, Player player) {
        int maxScanBlocks = getPlugin().getConfig().getInt("lodestone.structure-scan-max-blocks", 128);
        if (maxScanBlocks <= 0) {
            return;
        }

        Map<UUID, String> detectedMembers = StructureScanner.scanForPlayerHeads(lodestone, maxScanBlocks);
        detectedMembers.remove(player.getUniqueId());

        if (detectedMembers.isEmpty()) {
            return;
        }

        MemberManager memberManager = getPlugin().getLocationManager().getMemberManager();
        for (Map.Entry<UUID, String> entry : detectedMembers.entrySet()) {
            memberManager.addMember(locationId, entry.getKey(), entry.getValue());
        }

        String memberNames = String.join(", ", detectedMembers.values());
        getPlugin().getMessageManager().send(player, "lodestone.members-detected",
            new FormatUtil.Format("{count}", detectedMembers.size()),
            new FormatUtil.Format("{members}", memberNames));
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
     * Parse a lodestone sign. Blank lines anywhere are allowed: the first
     * non-empty line is the tag, remaining non-empty lines (joined with
     * spaces) are the name. Returns null if the sign has no tag or no name.
     */
    private ParsedSign parseSignLines(Component... lines) {
        String[] text = new String[lines.length];
        int tagIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            text[i] = PlainTextComponentSerializer.plainText().serialize(lines[i]).trim();
            if (tagIdx == -1 && !text[i].isEmpty()) {
                tagIdx = i;
            }
        }
        if (tagIdx == -1) {
            return null;
        }
        String tag = extractTag(text[tagIdx]);
        if (tag == null) {
            tag = text[tagIdx].toUpperCase();
        }
        StringBuilder nameSb = new StringBuilder();
        for (int i = tagIdx + 1; i < text.length; i++) {
            if (text[i].isEmpty()) {
                continue;
            }
            if (nameSb.length() > 0) {
                nameSb.append(' ');
            }
            nameSb.append(text[i]);
        }
        String name = nameSb.toString();
        if (name.isEmpty()) {
            return null;
        }
        return new ParsedSign(tag, name);
    }

    private record ParsedSign(String tag, String name) {}

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
    /**
     * Reverse of findAdjacentLodestone: given a lodestone, find an adjacent
     * sign and parse it. Returns null if no valid sign is attached yet.
     */
    private ParsedSign findSignForLodestone(Block lodestone) {
        for (BlockFace face : ADJACENT_FACES) {
            Block neighbor = lodestone.getRelative(face);
            if (!isSign(neighbor.getType())) {
                continue;
            }
            if (!(neighbor.getState() instanceof Sign sign)) {
                continue;
            }
            ParsedSign parsed = parseSignLines(sign.line(0), sign.line(1), sign.line(2), sign.line(3));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

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
