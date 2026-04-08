package ua.favn.baseManager.compass;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.location.SavedLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player glowing effects on item frames at lodestone locations.
 * Uses ProtocolLib to send entity metadata packets only to specific players.
 */
public class GlowManager extends Base {

    private final Map<UUID, List<Integer>> glowingEntities = new HashMap<>();
    private boolean protocolLibAvailable;
    private ProtocolManager protocolManager;

    public GlowManager(BaseManager plugin) {
        super(plugin);
        try {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
                this.protocolManager = ProtocolLibrary.getProtocolManager();
                this.protocolLibAvailable = true;
                plugin.getLogger().info("ProtocolLib found - per-player glowing enabled.");
            } else {
                this.protocolLibAvailable = false;
                plugin.getLogger().info("ProtocolLib not found - per-player glowing disabled.");
            }
        } catch (Exception e) {
            this.protocolLibAvailable = false;
            plugin.getLogger().warning("Failed to initialize ProtocolLib: " + e.getMessage());
        }
    }

    /**
     * Update glowing for a player holding a compass.
     * Finds item frames at the target lodestone and makes them glow for this player only.
     */
    public void updateGlow(Player player, SavedLocation location) {
        if (!protocolLibAvailable) {
            return;
        }

        String currentWorld = player.getWorld().getName();
        Location target = location.loc(currentWorld);

        // Remove previous glow first
        removeGlow(player);

        if (target == null) {
            return;
        }

        // Find item frames near the target lodestone
        List<ItemFrame> frames = findItemFramesAtLodestone(target);
        if (frames.isEmpty()) {
            return;
        }

        List<Integer> entityIds = new ArrayList<>();
        for (ItemFrame frame : frames) {
            sendGlowPacket(player, frame, true);
            entityIds.add(frame.getEntityId());
        }
        glowingEntities.put(player.getUniqueId(), entityIds);
    }

    /**
     * Remove all glowing effects for a player.
     */
    public void removeGlow(Player player) {
        if (!protocolLibAvailable) {
            return;
        }

        List<Integer> entityIds = glowingEntities.remove(player.getUniqueId());
        if (entityIds == null) {
            return;
        }

        // Find the actual entities and remove glow
        for (Entity entity : player.getWorld().getEntities()) {
            if (entityIds.contains(entity.getEntityId()) && entity instanceof ItemFrame frame) {
                sendGlowPacket(player, frame, false);
            }
        }
    }

    /**
     * Send a per-player entity metadata packet to set/unset glowing.
     */
    private void sendGlowPacket(Player player, Entity entity, boolean glowing) {
        try {
            PacketContainer packet = protocolManager.createPacket(
                PacketType.Play.Server.ENTITY_METADATA);

            // Set entity ID
            packet.getIntegers().write(0, entity.getEntityId());

            // Build metadata: index 0 = entity flags byte, bit 0x40 = glowing
            byte flags = 0;
            if (glowing) {
                flags |= 0x40;
            }

            List<WrappedDataValue> wrappedDataValues = new ArrayList<>();
            wrappedDataValues.add(new WrappedDataValue(
                0, // index 0 = entity base flags
                WrappedDataWatcher.Registry.get(Byte.class),
                flags
            ));

            packet.getDataValueCollectionModifier().write(0, wrappedDataValues);

            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to send glow packet: " + e.getMessage());
        }
    }

    /**
     * Find item frames attached to a lodestone at the given location.
     */
    private List<ItemFrame> findItemFramesAtLodestone(Location lodestoneLocation) {
        List<ItemFrame> frames = new ArrayList<>();
        Block lodestone = lodestoneLocation.getBlock();

        if (lodestone.getType() != Material.LODESTONE) {
            return frames;
        }

        for (Entity entity : lodestone.getWorld().getNearbyEntities(
                lodestone.getLocation().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (entity instanceof ItemFrame frame) {
                Block attached = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
                if (attached.getLocation().getBlockX() == lodestone.getLocation().getBlockX()
                    && attached.getLocation().getBlockY() == lodestone.getLocation().getBlockY()
                    && attached.getLocation().getBlockZ() == lodestone.getLocation().getBlockZ()) {
                    ItemStack item = frame.getItem();
                    if (item != null && item.getType() != Material.AIR) {
                        frames.add(frame);
                    }
                }
            }
        }
        return frames;
    }

    /**
     * Clean up all glow effects (called on plugin disable).
     */
    public void cleanup() {
        if (!protocolLibAvailable) {
            return;
        }
        for (UUID playerId : new ArrayList<>(glowingEntities.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                removeGlow(player);
            }
        }
        glowingEntities.clear();
    }
}
