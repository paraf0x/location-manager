package ua.favn.baseManager.map;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.Vector;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.marker.Icon;
import net.pl3x.map.core.markers.marker.Rectangle;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
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
    private static final String RECT_LAYER_KEY = "basemanager_areas";
    private static final String DEFAULT_ICON_KEY = "basemanager_default";
    private static final String TEXTURE_CDN = "https://assets.mcasset.cloud/1.21.1/assets/minecraft/textures/";

    private boolean pl3xmapAvailable;
    private final Set<String> registeredIcons = new HashSet<>();
    private final Set<String> pendingHeadRegistrations = Collections.synchronizedSet(new HashSet<>());
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
                } else if (isBanner(iconItem.getType())) {
                    ensureBannerIconRegistered(iconItem);
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

        // Download from CDN. Try paths in order of specificity. Multi-face
        // blocks (melon, enchanting_table, ancient_debris, end_portal_frame)
        // don't expose block/<name>.png — only block/<name>_side.png or
        // block/<name>_top.png. Item-only materials (sticks, enchanted_book)
        // live under item/<name>.png.
        BufferedImage image = downloadTexture("block/" + name + ".png");
        if (image == null) image = downloadTexture("block/" + name + "_side.png");
        if (image == null) image = downloadTexture("block/" + name + "_top.png");
        if (image == null) image = downloadTexture("block/" + name + "_front.png");
        if (image == null) image = downloadTexture("item/" + name + ".png");

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
                // Head icon wasn't available in the registry yet. Queue a non-blocking
                // registration so a follow-up refresh can pick the real face icon.
                ensureHeadIconRegisteredAsync(iconItem);
            } else if (isBanner(iconItem.getType())) {
                String bannerKey = getBannerIconKey(iconItem);
                if (bannerKey != null && registeredIcons.contains(bannerKey)) {
                    return bannerKey;
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

        // Try non-blocking profile extraction first.
        URL skinUrl = getSkinUrlFromProfile(profile);
        if (skinUrl != null) {
            return skinUrl;
        }

        // Profile might only have a name — complete it via Mojang API to get textures
        if (profile.getName() != null && !profile.getName().isBlank()) {
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

        return getSkinUrlFromSerializedProperties(profile);
    }

    /**
     * Decode a Base64 textures value and extract the skin URL.
     */
    static URL extractUrlFromTextureValue(String base64Value) {
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
     * Resolve a skin URL from the current profile snapshot without any network
     * calls. This is safe to use on the main thread. Uses Paper's direct
     * ProfileProperty API to avoid serialize() format ambiguity across
     * Paper/Purpur builds.
     */
    private URL getSkinUrlFromProfile(PlayerProfile profile) {
        URL fromProperties = getSkinUrlFromPaperProperties(profile);
        if (fromProperties != null) {
            return fromProperties;
        }

        try {
            URL direct = profile.getTextures().getSkin();
            if (direct != null) {
                return direct;
            }
        } catch (Exception ignored) {
            // fall through to serialized fallback
        }

        return getSkinUrlFromSerializedProperties(profile);
    }

    /**
     * Read texture properties via Paper's direct ProfileProperty API. This is
     * the primary extraction path — it works regardless of how the underlying
     * server build serializes profiles to YAML maps.
     */
    private URL getSkinUrlFromPaperProperties(PlayerProfile profile) {
        if (!(profile instanceof com.destroystokyo.paper.profile.PlayerProfile paperProfile)) {
            return null;
        }
        try {
            for (com.destroystokyo.paper.profile.ProfileProperty prop : paperProfile.getProperties()) {
                if (!"textures".equals(prop.getName())) {
                    continue;
                }
                String value = prop.getValue();
                if (value == null || value.isBlank()) {
                    continue;
                }
                URL resolved = extractUrlFromTextureValue(value);
                if (resolved != null) {
                    return resolved;
                }
            }
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to read Paper profile properties: " + e.getMessage());
        }
        return null;
    }

    /**
     * Last-resort fallback: parse skin URL from {@code profile.serialize()}.
     * Paper builds normally serialize properties as {@code List<Map<String,
     * String>>}, but the format is implementation-defined and has varied
     * across versions, so this path is only used after Paper's direct API
     * has been tried.
     */
    private URL getSkinUrlFromSerializedProperties(PlayerProfile profile) {
        try {
            Map<String, Object> serialized = profile.serialize();
            Object propsObj = serialized.get("properties");
            if (propsObj instanceof List<?> propsList) {
                for (Object entry : propsList) {
                    if (!(entry instanceof Map<?, ?> propMap)) {
                        continue;
                    }
                    if (!"textures".equals(propMap.get("name"))) {
                        continue;
                    }
                    Object valueObj = propMap.get("value");
                    if (!(valueObj instanceof String value) || value.isBlank()) {
                        continue;
                    }
                    URL resolved = extractUrlFromTextureValue(value);
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to parse head texture: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get icon key for a head. Derived from the texture URL so every unique
     * skin (real player heads AND HDB heads — which all share profile.name
     * "HeadDatabase") gets its own icon. Falls back to profile name only when
     * no texture URL is resolvable on the main thread.
     */
    private String getHeadIconKey(ItemStack head) {
        if (!(head.getItemMeta() instanceof SkullMeta skullMeta)) {
            return null;
        }
        PlayerProfile profile = skullMeta.getOwnerProfile();
        if (profile == null) {
            return null;
        }

        String urlKey = headKeyFromProfileTextureUrl(profile);
        if (urlKey != null) {
            String key = "basemanager_head_" + urlKey;
            if (registeredIcons.contains(key)) {
                return key;
            }
        }
        // Legacy fallback: name-based key (real player heads already
        // registered this way pre-fix).
        String name = profile.getName();
        if (name != null && !name.isBlank()) {
            String nameKey = "basemanager_head_" + name.toLowerCase();
            if (registeredIcons.contains(nameKey)) {
                return nameKey;
            }
        }
        return null;
    }

    /**
     * Extract a stable per-texture cache key from the profile. The texture
     * URL ends in a 64-char hex hash unique to each skin; take the first 16
     * chars as a compact identifier. Returns null if no URL can be resolved
     * on the main thread (typical for real player heads with only a name).
     */
    private String headKeyFromProfileTextureUrl(PlayerProfile profile) {
        try {
            return headKeyFromSkinUrl(getSkinUrlFromProfile(profile));
        } catch (Exception e) {
            return null;
        }
    }

    private String headKeyFromSkinUrl(URL skinUrl) {
        if (skinUrl == null) {
            return null;
        }
        String path = skinUrl.getPath();
        int slash = path.lastIndexOf('/');
        String hash = slash >= 0 ? path.substring(slash + 1) : path;
        if (hash.isEmpty()) {
            return null;
        }
        return hash.length() > 16 ? hash.substring(0, 16) : hash;
    }

    private String getPendingHeadRegistrationKey(ItemStack head) {
        if (!(head.getItemMeta() instanceof SkullMeta skullMeta)) {
            return null;
        }
        PlayerProfile profile = skullMeta.getOwnerProfile();
        if (profile == null) {
            return null;
        }

        String urlKey = headKeyFromProfileTextureUrl(profile);
        if (urlKey != null) {
            return "url:" + urlKey;
        }

        String name = profile.getName();
        if (name != null && !name.isBlank()) {
            return "name:" + name.toLowerCase();
        }

        return "item:" + Integer.toHexString(head.hashCode());
    }

    private void ensureHeadIconRegisteredAsync(ItemStack head) {
        String pendingKey = getPendingHeadRegistrationKey(head);
        if (pendingKey != null && !pendingHeadRegistrations.add(pendingKey)) {
            return;
        }

        ItemStack copy = head.clone();
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            try {
                boolean registered = ensureHeadIconRegistered(copy);
                if (registered) {
                    Bukkit.getScheduler().runTask(getPlugin(), this::refreshMarkers);
                }
            } finally {
                if (pendingKey != null) {
                    pendingHeadRegistrations.remove(pendingKey);
                }
            }
        });
    }

    /**
     * Download a player head skin, crop the face, and register with Pl3xmap.
     * Must be called from an async thread.
     */
    private synchronized boolean ensureHeadIconRegistered(ItemStack head) {
        if (!(head.getItemMeta() instanceof SkullMeta skullMeta)) {
            return false;
        }
        PlayerProfile profile = skullMeta.getOwnerProfile();
        if (profile == null) {
            return false;
        }

        // getSkinUrl may block on Mojang API — this method must be async.
        URL skinUrl = getSkinUrl(head);
        if (skinUrl == null) {
            int propCount = 0;
            if (profile instanceof com.destroystokyo.paper.profile.PlayerProfile pp) {
                propCount = pp.getProperties().size();
            }
            getPlugin().getLogger().warning(
                "Could not resolve skin URL for head icon "
                + "(profile name='" + profile.getName()
                + "', uniqueId=" + profile.getUniqueId()
                + ", paper-properties=" + propCount + ")");
            return false;
        }

        // Use the skin URL's hash as the cache key so different HDB heads
        // (which all share profile.name "HeadDatabase") produce different icons.
        String keyName = headKeyFromSkinUrl(skinUrl);
        if (keyName == null) {
            return false;
        }
        String iconKey = "basemanager_head_" + keyName;
        if (registeredIcons.contains(iconKey)) {
            return false;
        }

        try {
            File cacheFile = new File(iconCacheDir, "head_" + keyName + ".png");
            BufferedImage face;

            if (cacheFile.exists()) {
                face = ImageIO.read(cacheFile);
            } else {
                // Download skin and crop face region (8,8 to 16,16 on a 64x64 skin)
                BufferedImage skin = ImageIO.read(skinUrl);
                if (skin == null) {
                    return false;
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
                return true;
            }
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to register head icon: " + e.getMessage());
        }
        return false;
    }

    // ── Banner rendering ─────────────────────────────────────────────────

    private static final int BANNER_FACE_X = 1;
    private static final int BANNER_FACE_Y = 1;
    private static final int BANNER_FACE_W = 20;
    private static final int BANNER_FACE_H = 40;

    private static boolean isBanner(Material material) {
        return material.name().endsWith("_BANNER");
    }

    private static DyeColor bannerBaseColor(Material material) {
        String name = material.name().replace("_BANNER", "");
        try {
            return DyeColor.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DyeColor.WHITE;
        }
    }

    private String getBannerIconKey(ItemStack banner) {
        DyeColor base = bannerBaseColor(banner.getType());
        StringBuilder sb = new StringBuilder("basemanager_banner_").append(base.name().toLowerCase());
        if (banner.getItemMeta() instanceof BannerMeta meta) {
            for (Pattern p : meta.getPatterns()) {
                sb.append('_').append(p.getPattern().getKey().getKey());
                sb.append('_').append(p.getColor().name().toLowerCase());
            }
        }
        return sb.toString();
    }

    private synchronized void ensureBannerIconRegistered(ItemStack banner) {
        String iconKey = getBannerIconKey(banner);
        if (registeredIcons.contains(iconKey)) {
            return;
        }

        try {
            DyeColor baseColor = bannerBaseColor(banner.getType());
            List<Pattern> patterns = (banner.getItemMeta() instanceof BannerMeta meta)
                    ? meta.getPatterns() : List.of();

            BufferedImage image = renderBanner(baseColor, patterns);
            if (image != null) {
                File cacheFile = new File(iconCacheDir, iconKey + ".png");
                ImageIO.write(image, "png", cacheFile);

                IconImage iconImage = new IconImage(iconKey, image, "png");
                Pl3xMap.api().getIconRegistry().register(iconKey, iconImage);
                registeredIcons.add(iconKey);
            }
        } catch (Exception e) {
            getPlugin().getLogger().warning("Failed to register banner icon: " + e.getMessage());
        }
    }

    private BufferedImage renderBanner(DyeColor baseColor, List<Pattern> patterns) {
        BufferedImage canvas = new BufferedImage(BANNER_FACE_W, BANNER_FACE_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            org.bukkit.Color bc = baseColor.getColor();
            g.setColor(new java.awt.Color(bc.getRed(), bc.getGreen(), bc.getBlue()));
            g.fillRect(0, 0, BANNER_FACE_W, BANNER_FACE_H);

            for (Pattern pattern : patterns) {
                String textureName = pattern.getPattern().getKey().getKey();
                BufferedImage mask = loadBannerPatternMask(textureName);
                if (mask == null) {
                    continue;
                }
                BufferedImage colorized = colorizeMask(mask, pattern.getColor());
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(colorized, 0, 0, null);
            }
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private BufferedImage loadBannerPatternMask(String patternName) {
        File cacheFile = new File(iconCacheDir, "banner_pattern_" + patternName + ".png");
        if (cacheFile.exists()) {
            try {
                return ImageIO.read(cacheFile);
            } catch (Exception e) {
                cacheFile.delete();
            }
        }

        BufferedImage full = downloadTexture("entity/banner/" + patternName + ".png");
        if (full == null) {
            return null;
        }

        if (full.getWidth() < BANNER_FACE_X + BANNER_FACE_W
                || full.getHeight() < BANNER_FACE_Y + BANNER_FACE_H) {
            return null;
        }
        BufferedImage face = full.getSubimage(BANNER_FACE_X, BANNER_FACE_Y, BANNER_FACE_W, BANNER_FACE_H);

        try {
            ImageIO.write(face, "png", cacheFile);
        } catch (Exception e) {
            // non-fatal
        }
        return face;
    }

    private static BufferedImage colorizeMask(BufferedImage mask, DyeColor dyeColor) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        org.bukkit.Color dc = dyeColor.getColor();
        int cr = dc.getRed();
        int cg = dc.getGreen();
        int cb = dc.getBlue();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = mask.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                int mr = (pixel >> 16) & 0xFF;
                int mg = (pixel >> 8) & 0xFF;
                int mb = pixel & 0xFF;
                int nr = (mr * cr) / 255;
                int ng = (mg * cg) / 255;
                int nb = (mb * cb) / 255;
                result.setRGB(x, y, (alpha << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return result;
    }

    // ── Marker refresh ──────────────────────────────────────────────────

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

            boolean rectsEnabled = isRectanglesEnabled();

            for (World world : Pl3xMap.api().getWorldRegistry()) {
                String worldName = world.getName();

                SimpleLayer layer = getOrCreateLayer(world);
                layer.clearMarkers();

                SimpleLayer rectLayer = null;
                if (rectsEnabled) {
                    rectLayer = getOrCreateRectangleLayer(world);
                    rectLayer.clearMarkers();
                }

                for (SavedLocation loc : locations) {
                    int[] coords = loc.getCoords(worldName);
                    if (coords == null) {
                        continue;
                    }

                    String iconKey = getIconKeyForLocation(loc);
                    String markerKey = "basemanager_" + loc.id() + "_" + worldName;
                    int iconSize = getIconSize();
                    Icon marker = Icon.of(markerKey, coords[0], coords[2], iconKey);

                    boolean locIsBanner = loc.iconItem() != null && isBanner(loc.iconItem().getType());
                    int markerW;
                    int markerH;
                    if (locIsBanner) {
                        markerW = getBannerIconWidth();
                        markerH = markerW * 2;
                    } else {
                        markerW = iconSize;
                        markerH = iconSize;
                    }
                    marker.setSize(Vector.of(markerW, markerH));
                    marker.setAnchor(Vector.of(markerW / 2.0, markerH / 2.0));

                    String creatorName = getPlayerName(loc.owner());
                    String coordsStr = coords[0] + " " + coords[1] + " " + coords[2];
                    String tooltipHtml = getTooltipTemplate()
                        .replace("{name}", loc.name())
                        .replace("{tag}", loc.tag())
                        .replace("{coords}", coordsStr)
                        .replace("{creator}", creatorName)
                        .replace("{owner}", creatorName);

                    Tooltip tooltip = new Tooltip(tooltipHtml)
                        .setDirection(Tooltip.Direction.TOP);
                    marker.setOptions(new Options().setTooltip(tooltip));
                    layer.addMarker(marker);

                    // Add rectangle overlay if configured for this tag
                    if (rectLayer != null) {
                        ConfigurationSection tagSection = getRectangleTagSection(loc.tag());
                        if (tagSection != null) {
                            int size = tagSection.getInt("size", 200);
                            int half = size / 2;
                            Point p1 = Point.of(coords[0] - half, coords[2] - half);
                            Point p2 = Point.of(coords[0] + half, coords[2] + half);

                            String rectKey = "basemanager_rect_" + loc.id() + "_" + worldName;
                            Rectangle rect = Rectangle.of(rectKey, p1, p2);

                            Options rectOptions = Options.builder()
                                .stroke(true)
                                .strokeWeight(tagSection.getInt("stroke-weight", 2))
                                .strokeColor(tagSection.getInt("stroke-color", 0x800088FF))
                                .fill(true)
                                .fillColor(tagSection.getInt("fill-color", 0x330088FF))
                                .tooltipContent(tooltipHtml)
                                .tooltipDirection(Tooltip.Direction.TOP)
                                .build();

                            rect.setOptions(rectOptions);
                            rectLayer.addMarker(rect);
                        }
                    }
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

    private SimpleLayer getOrCreateRectangleLayer(World world) {
        String label = getRectangleLayerLabel();
        if (world.getLayerRegistry().has(RECT_LAYER_KEY)) {
            return (SimpleLayer) world.getLayerRegistry().get(RECT_LAYER_KEY);
        }
        SimpleLayer layer = new SimpleLayer(RECT_LAYER_KEY, () -> label);
        layer.setShowControls(true);
        layer.setDefaultHidden(isRectangleDefaultHidden());
        layer.setUpdateInterval(30);
        layer.setLiveUpdate(true);
        layer.setPriority(0);
        world.getLayerRegistry().register(RECT_LAYER_KEY, layer);
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
                if (world.getLayerRegistry().has(RECT_LAYER_KEY)) {
                    world.getLayerRegistry().unregister(RECT_LAYER_KEY);
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

    private int getBannerIconWidth() {
        return getPlugin().getConfig().getInt("pl3xmap.banner-icon-width", 24);
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

    private boolean isRectanglesEnabled() {
        return getPlugin().getConfig().getBoolean("pl3xmap.rectangles.enabled", false);
    }

    private String getRectangleLayerLabel() {
        return getPlugin().getConfig().getString("pl3xmap.rectangles.layer-label", "Location Borders");
    }

    private boolean isRectangleDefaultHidden() {
        return getPlugin().getConfig().getBoolean("pl3xmap.rectangles.default-hidden", true);
    }

    private ConfigurationSection getRectangleTagSection(String tag) {
        return getPlugin().getConfig().getConfigurationSection("pl3xmap.rectangles.tags." + tag);
    }

    private String getPlayerName(java.util.UUID uuid) {
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }

}
