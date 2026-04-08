package ua.favn.baseManager.map;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.Vector;
import net.pl3x.map.core.markers.marker.Icon;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.location.SavedLocation;

/**
 * Manages Pl3xMap integration for displaying location markers on the web map.
 * Downloads Minecraft item/block textures for per-location icons.
 */
public class Pl3xmapManager extends Base {
    private static final String LAYER_KEY = "basemanager";
    private static final String DEFAULT_ICON_KEY = "basemanager_default";
    private static final String TEXTURE_CDN = "https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/";

    private boolean pl3xmapAvailable;
    private final Set<String> registeredIcons = new HashSet<>();
    private final File iconCacheDir;

    public Pl3xmapManager(BaseManager plugin) {
        super(plugin);
        this.iconCacheDir = new File(plugin.getDataFolder(), "icons");
        try {
            if (Bukkit.getPluginManager().getPlugin("Pl3xMap") != null) {
                this.pl3xmapAvailable = true;
                plugin.getLogger().info("Pl3xMap found - map markers enabled.");
                Bukkit.getScheduler().runTaskLater(plugin, this::initialize, 20L);
            } else {
                this.pl3xmapAvailable = false;
                plugin.getLogger().info("Pl3xMap not found - map markers disabled.");
            }
        } catch (Exception e) {
            this.pl3xmapAvailable = false;
            plugin.getLogger().warning("Failed to initialize Pl3xMap: " + e.getMessage());
        }
    }

    private void initialize() {
        if (!pl3xmapAvailable || !isEnabled()) {
            return;
        }
        try {
            iconCacheDir.mkdirs();
            registerDefaultIcon();
            // Download textures async, then refresh markers on main thread
            Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
                preloadLocationIcons();
                Bukkit.getScheduler().runTask(getPlugin(), () -> {
                    refreshMarkers();
                    getPlugin().getLogger().info("Pl3xMap markers initialized.");
                });
            });
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to initialize Pl3xMap markers: " + e.getMessage());
            pl3xmapAvailable = false;
        }
    }

    private void registerDefaultIcon() {
        try {
            BufferedImage image = ImageIO.read(getPlugin().getResource("icon.png"));
            IconImage iconImage = new IconImage(DEFAULT_ICON_KEY, image, "png");
            Pl3xMap.api().getIconRegistry().register(DEFAULT_ICON_KEY, iconImage);
            registeredIcons.add(DEFAULT_ICON_KEY);
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to register default icon: " + e.getMessage());
        }
    }

    /**
     * Pre-download textures for all location icons.
     */
    private void preloadLocationIcons() {
        List<SavedLocation> locations = isPublicOnly()
            ? getPlugin().getLocationManager().getPublicLocations()
            : getPlugin().getLocationManager().getAll();

        for (SavedLocation loc : locations) {
            ItemStack iconItem = loc.iconItem();
            if (iconItem != null) {
                if (iconItem.getType() == Material.PLAYER_HEAD) {
                    ensureHeadIconRegistered(iconItem);
                } else {
                    ensureIconRegistered(iconItem.getType());
                }
            }
        }
    }

    /**
     * Ensure a Material's texture is downloaded, cached, and registered with Pl3xmap.
     */
    private synchronized void ensureIconRegistered(Material material) {
        String iconKey = "basemanager_" + material.name().toLowerCase();
        if (registeredIcons.contains(iconKey)) {
            return;
        }

        try {
            BufferedImage image = loadMaterialTexture(material);
            if (image != null) {
                IconImage iconImage = new IconImage(iconKey, image, "png");
                Pl3xMap.api().getIconRegistry().register(iconKey, iconImage);
                registeredIcons.add(iconKey);
            }
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to register icon for " + material + ": " + e.getMessage());
        }
    }

    /**
     * Load a material's texture from local cache, or download from CDN.
     */
    private BufferedImage loadMaterialTexture(Material material) {
        String name = material.name().toLowerCase();
        File cacheFile = new File(iconCacheDir, name + ".png");

        // Try cache first
        if (cacheFile.exists()) {
            try {
                return ImageIO.read(cacheFile);
            } catch (Exception e) {
                cacheFile.delete();
            }
        }

        // Download from CDN — try block path first, then item path
        BufferedImage image = downloadTexture("block/" + name + ".png");
        if (image == null) {
            image = downloadTexture("item/" + name + ".png");
        }

        if (image != null) {
            try {
                ImageIO.write(image, "png", cacheFile);
            } catch (Exception e) {
                getPlugin().getLogger().warning("Failed to cache icon for " + name);
            }
        }

        return image;
    }

    private BufferedImage downloadTexture(String path) {
        try {
            URI uri = URI.create(TEXTURE_CDN + path);
            return ImageIO.read(uri.toURL());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the Pl3xmap icon key for a location. Falls back to default if no custom icon.
     */
    private String getIconKeyForLocation(SavedLocation loc) {
        ItemStack iconItem = loc.iconItem();
        if (iconItem != null) {
            if (iconItem.getType() == Material.PLAYER_HEAD) {
                String headKey = getHeadIconKey(iconItem);
                if (headKey != null) {
                    return headKey;
                }
            } else {
                String key = "basemanager_" + iconItem.getType().name().toLowerCase();
                if (registeredIcons.contains(key)) {
                    return key;
                }
                ensureIconRegistered(iconItem.getType());
                if (registeredIcons.contains(key)) {
                    return key;
                }
            }
        }
        return DEFAULT_ICON_KEY;
    }

    /**
     * Extract the skin URL from a player head ItemStack.
     * Supports player heads (completes profile via Mojang API) and custom texture heads (HDB).
     * Must be called from an async thread since profile.complete() is blocking.
     */
    private URL getSkinUrl(ItemStack head) {
        if (!(head.getItemMeta() instanceof SkullMeta skullMeta)) {
            return null;
        }
        PlayerProfile profile = skullMeta.getOwnerProfile();
        if (profile == null) {
            return null;
        }

        // Try the clean Bukkit API first
        URL skinUrl = profile.getTextures().getSkin();
        if (skinUrl != null) {
            return skinUrl;
        }

        // Profile might only have a name — complete it via Mojang API to get textures
        if (profile.getName() != null) {
            try {
                com.destroystokyo.paper.profile.PlayerProfile paperProfile =
                    Bukkit.createProfile(profile.getName());
                paperProfile.complete(true);
                for (com.destroystokyo.paper.profile.ProfileProperty prop : paperProfile.getProperties()) {
                    if ("textures".equals(prop.getName())) {
                        URL resolved = extractUrlFromTextureValue(prop.getValue());
                        if (resolved != null) {
                            return resolved;
                        }
                    }
                }
            } catch (Exception e) {
                // Profile completion failed
            }
        }

        // Fall back: check serialized properties for raw Base64 texture data
        try {
            Map<String, Object> serialized = profile.serialize();
            Object propsObj = serialized.get("properties");
            if (propsObj instanceof List<?> propsList) {
                for (Object entry : propsList) {
                    if (entry instanceof Map<?, ?> propMap) {
                        if (!"textures".equals(propMap.get("name"))) {
                            continue;
                        }
                        String value = (String) propMap.get("value");
                        if (value == null) {
                            continue;
                        }
                        return extractUrlFromTextureValue(value);
                    }
                }
            }
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to parse head texture: " + e.getMessage());
        }
        return null;
    }

    /**
     * Decode a Base64 textures value and extract the skin URL.
     */
    private URL extractUrlFromTextureValue(String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value));
            int httpStart = json.indexOf("http://textures.minecraft.net");
            if (httpStart == -1) {
                httpStart = json.indexOf("https://textures.minecraft.net");
            }
            if (httpStart == -1) {
                return null;
            }
            int httpEnd = json.indexOf("\"", httpStart);
            String url = json.substring(httpStart, httpEnd);
            return URI.create(url).toURL();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get icon key for a head. Uses player name as fallback key lookup
     * since skin URL hash may not be available on the main thread.
     */
    private String getHeadIconKey(ItemStack head) {
        if (!(head.getItemMeta() instanceof SkullMeta skullMeta)) {
            return null;
        }
        PlayerProfile profile = skullMeta.getOwnerProfile();
        if (profile == null) {
            return null;
        }

        // Check by name-based key (set during async preload)
        String name = profile.getName();
        if (name != null) {
            String nameKey = "basemanager_head_" + name.toLowerCase();
            if (registeredIcons.contains(nameKey)) {
                return nameKey;
            }
        }
        return null;
    }

    /**
     * Download a player head skin, crop the face, and register with Pl3xmap.
     * Must be called from an async thread.
     */
    private synchronized void ensureHeadIconRegistered(ItemStack head) {
        if (!(head.getItemMeta() instanceof SkullMeta skullMeta)) {
            return;
        }
        PlayerProfile profile = skullMeta.getOwnerProfile();
        if (profile == null || profile.getName() == null) {
            return;
        }

        String name = profile.getName().toLowerCase();
        String iconKey = "basemanager_head_" + name;
        if (registeredIcons.contains(iconKey)) {
            return;
        }

        URL skinUrl = getSkinUrl(head);
        if (skinUrl == null) {
            return;
        }

        try {
            File cacheFile = new File(iconCacheDir, "head_" + name + ".png");
            BufferedImage face;

            if (cacheFile.exists()) {
                face = ImageIO.read(cacheFile);
            } else {
                // Download skin and crop face region (8,8 to 16,16 on a 64x64 skin)
                BufferedImage skin = ImageIO.read(skinUrl);
                if (skin == null) {
                    return;
                }
                BufferedImage faceCrop = skin.getSubimage(8, 8, 8, 8);

                // Scale 8x8 to 16x16
                face = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = face.createGraphics();
                g.drawImage(faceCrop, 0, 0, 16, 16, null);
                g.dispose();

                ImageIO.write(face, "png", cacheFile);
            }

            if (face != null) {
                IconImage iconImage = new IconImage(iconKey, face, "png");
                Pl3xMap.api().getIconRegistry().register(iconKey, iconImage);
                registeredIcons.add(iconKey);
            }
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to register head icon: " + e.getMessage());
        }
    }

    /**
     * Refresh all markers on all worlds.
     */
    public void refreshMarkers() {
        if (!pl3xmapAvailable || !isEnabled()) {
            return;
        }

        try {
            List<SavedLocation> locations = isPublicOnly()
                ? getPlugin().getLocationManager().getPublicLocations()
                : getPlugin().getLocationManager().getAll();

            for (World world : Pl3xMap.api().getWorldRegistry()) {
                String worldName = world.getName();

                SimpleLayer layer = getOrCreateLayer(world);
                layer.clearMarkers();

                for (SavedLocation loc : locations) {
                    int[] coords = loc.getCoords(worldName);
                    if (coords == null) {
                        continue;
                    }

                    String iconKey = getIconKeyForLocation(loc);
                    String markerKey = "basemanager_" + loc.id() + "_" + worldName;
                    int iconSize = getIconSize();
                    Icon marker = Icon.of(markerKey, coords[0], coords[2], iconKey);
                    marker.setSize(Vector.of(iconSize, iconSize));
                    marker.setAnchor(Vector.of(iconSize / 2.0, iconSize / 2.0));

                    String ownerName = getPlayerName(loc.owner());
                    String coordsStr = coords[0] + " " + coords[1] + " " + coords[2];
                    String tooltipHtml = getTooltipTemplate()
                        .replace("{name}", loc.name())
                        .replace("{tag}", loc.tag())
                        .replace("{coords}", coordsStr)
                        .replace("{owner}", ownerName);

                    Tooltip tooltip = new Tooltip(tooltipHtml)
                        .setDirection(Tooltip.Direction.TOP);
                    marker.setOptions(new Options().setTooltip(tooltip));
                    layer.addMarker(marker);
                }
            }
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to refresh Pl3xMap markers: " + e.getMessage());
        }
    }

    private SimpleLayer getOrCreateLayer(World world) {
        String label = getLayerLabel();
        if (world.getLayerRegistry().has(LAYER_KEY)) {
            return (SimpleLayer) world.getLayerRegistry().get(LAYER_KEY);
        }
        SimpleLayer layer = new SimpleLayer(LAYER_KEY, () -> label);
        layer.setShowControls(true);
        layer.setDefaultHidden(false);
        layer.setUpdateInterval(30);
        layer.setLiveUpdate(true);
        world.getLayerRegistry().register(LAYER_KEY, layer);
        return layer;
    }

    public void cleanup() {
        if (!pl3xmapAvailable) {
            return;
        }
        try {
            for (World world : Pl3xMap.api().getWorldRegistry()) {
                if (world.getLayerRegistry().has(LAYER_KEY)) {
                    world.getLayerRegistry().unregister(LAYER_KEY);
                }
            }
        } catch (Exception ignored) {
            // Pl3xMap may already be disabled
        }
    }

    private boolean isEnabled() {
        return getPlugin().getConfig().getBoolean("pl3xmap.enabled", true);
    }

    private String getLayerLabel() {
        return getPlugin().getConfig().getString("pl3xmap.layer-label", "Bases");
    }

    private boolean isPublicOnly() {
        return getPlugin().getConfig().getBoolean("pl3xmap.public-only", true);
    }

    private int getIconSize() {
        return getPlugin().getConfig().getInt("pl3xmap.icon-size", 24);
    }

    private String getTooltipTemplate() {
        return getPlugin().getConfig().getString("pl3xmap.tooltip",
            "<div style=\"text-align:center;\">"
            + "<b style=\"font-size:13px;color:#f5a623;\">{name}</b>"
            + "<br><span style=\"color:#aaa;font-size:11px;\">{tag}</span>"
            + "<br><span style=\"font-size:11px;\">{coords}</span>"
            + "<br><span style=\"color:#8bc34a;font-size:11px;\">{owner}</span>"
            + "</div>");
    }

    private String getPlayerName(java.util.UUID uuid) {
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }
}
