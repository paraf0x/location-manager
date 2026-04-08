package ua.favn.baseManager.compass;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.base.util.FormatUtil;
import ua.favn.baseManager.location.SavedLocation;

/**
 * Manages tracking compasses that point to saved locations.
 */
public class TrackingCompassManager extends Base {
    private final NamespacedKey keyLocationCompass;
    private final NamespacedKey keyLocationId;
    private final NamespacedKey keyLocationName;
    private final NamespacedKey keyLocationTag;
    private final NamespacedKey keyOwnerUuid;
    private final NamespacedKey keyOriginWorld;
    private final NamespacedKey keyTargetWorld;
    private final NamespacedKey keyTargetX;
    private final NamespacedKey keyTargetY;
    private final NamespacedKey keyTargetZ;

    public TrackingCompassManager(BaseManager plugin) {
        super(plugin);
        this.keyLocationCompass = new NamespacedKey(plugin, "location_compass");
        this.keyLocationId = new NamespacedKey(plugin, "location_id");
        this.keyLocationName = new NamespacedKey(plugin, "location_name");
        this.keyLocationTag = new NamespacedKey(plugin, "location_tag");
        this.keyOwnerUuid = new NamespacedKey(plugin, "owner_uuid");
        this.keyOriginWorld = new NamespacedKey(plugin, "origin_world");
        this.keyTargetWorld = new NamespacedKey(plugin, "target_world");
        this.keyTargetX = new NamespacedKey(plugin, "target_x");
        this.keyTargetY = new NamespacedKey(plugin, "target_y");
        this.keyTargetZ = new NamespacedKey(plugin, "target_z");
    }

    /**
     * Create a tracking compass for the given location.
     * Stores the player's current world as the origin dimension for auto-dispose tracking.
     *
     * @param location The saved location to track
     * @param player The player requesting the compass
     * @return The compass ItemStack
     */
    public ItemStack createCompass(SavedLocation location, Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();

        String currentWorld = player.getWorld().getName();

        // Set lodestone location for current dimension
        Location target = location.loc(currentWorld);
        if (target != null) {
            meta.setLodestone(target);
            meta.setLodestoneTracked(false);
        }

        // Store location data in PersistentDataContainer
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyLocationCompass, PersistentDataType.BOOLEAN, true);
        pdc.set(keyLocationId, PersistentDataType.INTEGER, location.id());
        pdc.set(keyLocationName, PersistentDataType.STRING, location.name());
        pdc.set(keyLocationTag, PersistentDataType.STRING, location.tag());
        pdc.set(keyOwnerUuid, PersistentDataType.STRING, location.owner().toString());
        pdc.set(keyOriginWorld, PersistentDataType.STRING, currentWorld);

        // Store fallback coordinates (first available)
        String primaryWorld = target != null ? currentWorld : location.primaryWorld();
        int[] coords = location.getCoords(primaryWorld);
        if (coords != null) {
            pdc.set(keyTargetWorld, PersistentDataType.STRING, primaryWorld);
            pdc.set(keyTargetX, PersistentDataType.INTEGER, coords[0]);
            pdc.set(keyTargetY, PersistentDataType.INTEGER, coords[1]);
            pdc.set(keyTargetZ, PersistentDataType.INTEGER, coords[2]);
        }

        // Display name
        Component displayName = getPlugin().getMessageManager().get("compass.item-name",
            new FormatUtil.Format("{location_name}", location.displayName()));
        meta.displayName(FormatUtil.normalize(displayName));

        // Lore
        String coordsStr = target != null ? location.coordsString(currentWorld) : location.coordsString();
        List<String> loreLines = getPlugin().getMessageManager().getConfig()
            .getStringList("compass.item-lore");
        List<Component> lore = loreLines.stream()
            .map(line -> FormatUtil.formatComponent(line,
                new FormatUtil.Format("{location_name}", location.displayName()),
                new FormatUtil.Format("{location}", coordsStr),
                new FormatUtil.Format("{world}", currentWorld)))
            .map(FormatUtil::normalize)
            .toList();
        meta.lore(lore);

        // Add enchant glow effect
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        compass.setItemMeta(meta);
        return compass;
    }

    /**
     * Check if an ItemStack is a tracking compass.
     */
    public boolean isTrackingCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(keyLocationCompass, PersistentDataType.BOOLEAN);
    }

    /**
     * Get the location name stored in the compass.
     */
    public String getLocationName(ItemStack compass) {
        if (!isTrackingCompass(compass)) {
            return null;
        }
        PersistentDataContainer pdc = compass.getItemMeta().getPersistentDataContainer();
        return pdc.get(keyLocationName, PersistentDataType.STRING);
    }

    /**
     * Get the location tag stored in the compass.
     */
    public String getLocationTag(ItemStack compass) {
        if (!isTrackingCompass(compass)) {
            return null;
        }
        PersistentDataContainer pdc = compass.getItemMeta().getPersistentDataContainer();
        return pdc.get(keyLocationTag, PersistentDataType.STRING);
    }

    /**
     * Get the location ID stored in the compass.
     */
    public Integer getLocationId(ItemStack compass) {
        if (!isTrackingCompass(compass)) {
            return null;
        }
        PersistentDataContainer pdc = compass.getItemMeta().getPersistentDataContainer();
        return pdc.get(keyLocationId, PersistentDataType.INTEGER);
    }

    /**
     * Get the owner UUID stored in the compass.
     */
    public UUID getOwnerUuid(ItemStack compass) {
        if (!isTrackingCompass(compass)) {
            return null;
        }
        PersistentDataContainer pdc = compass.getItemMeta().getPersistentDataContainer();
        String uuidStr = pdc.get(keyOwnerUuid, PersistentDataType.STRING);
        if (uuidStr == null) {
            return null;
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get the origin world (dimension where compass was created).
     * Used for auto-dispose: only trigger at origin dimension.
     */
    public String getOriginWorld(ItemStack compass) {
        if (!isTrackingCompass(compass)) {
            return null;
        }
        PersistentDataContainer pdc = compass.getItemMeta().getPersistentDataContainer();
        return pdc.get(keyOriginWorld, PersistentDataType.STRING);
    }

    /**
     * Get the target location for a compass, dynamically looking up based on player's current world.
     * This enables cross-dimension compass functionality.
     */
    public Location getTargetLocation(ItemStack compass, Player holder) {
        if (!isTrackingCompass(compass)) {
            return null;
        }

        Integer locationId = getLocationId(compass);
        if (locationId != null) {
            SavedLocation loc = getPlugin().getLocationManager().get(locationId);
            if (loc != null) {
                String currentWorld = holder.getWorld().getName();
                return loc.loc(currentWorld);
            }
        }

        // Fallback to stored coordinates for old compasses
        return getTargetLocationStored(compass);
    }

    /**
     * Get the target location stored in the compass (static coordinates).
     * Used for backwards compatibility with old compasses.
     */
    public Location getTargetLocationStored(ItemStack compass) {
        if (!isTrackingCompass(compass)) {
            return null;
        }
        PersistentDataContainer pdc = compass.getItemMeta().getPersistentDataContainer();

        String worldName = pdc.get(keyTargetWorld, PersistentDataType.STRING);
        Integer x = pdc.get(keyTargetX, PersistentDataType.INTEGER);
        Integer y = pdc.get(keyTargetY, PersistentDataType.INTEGER);
        Integer z = pdc.get(keyTargetZ, PersistentDataType.INTEGER);

        if (worldName == null || x == null || y == null || z == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(world, x, y, z);
    }

    /**
     * Update the compass lodestone to point to the correct location in the holder's current dimension.
     * Called when player changes dimension to keep the needle pointing correctly.
     */
    public void updateCompassLodestone(ItemStack compass, Player holder) {
        if (!isTrackingCompass(compass)) {
            return;
        }

        Location target = getTargetLocation(compass, holder);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        if (target != null) {
            meta.setLodestone(target);
            meta.setLodestoneTracked(false);
        } else {
            meta.setLodestone(null); // Compass will spin
        }
        compass.setItemMeta(meta);
    }

    /**
     * Get the display name for the compass (tag:name format).
     */
    public String getDisplayName(ItemStack compass) {
        String tag = getLocationTag(compass);
        String name = getLocationName(compass);
        if (tag != null && name != null) {
            return tag + ":" + name;
        }
        return name != null ? name : "Unknown";
    }

    // Configuration getters

    public boolean isAutoDisposeOnArrival() {
        return getPlugin().getConfig().getBoolean("compass.auto-dispose-on-arrival", true);
    }

    public int getArrivalRadius() {
        return getPlugin().getConfig().getInt("compass.arrival-radius", 10);
    }

    public boolean isShowDistanceActionBar() {
        return getPlugin().getConfig().getBoolean("compass.show-distance-actionbar", true);
    }

    public int getActionBarUpdateTicks() {
        return getPlugin().getConfig().getInt("compass.actionbar-update-ticks", 20);
    }

    public boolean isParticleTrailEnabled() {
        return getPlugin().getConfig().getBoolean("compass.particle-trail", false);
    }

    public int getParticleTrailDistance() {
        return getPlugin().getConfig().getInt("compass.particle-trail-distance", 30);
    }

    public double getParticleTrailSpacing() {
        return getPlugin().getConfig().getDouble("compass.particle-trail-spacing", 2.0);
    }

    public int getParticleTrailDensity() {
        return getPlugin().getConfig().getInt("compass.particle-trail-density", 1);
    }
}
