package ua.favn.baseManager.location;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import ua.favn.baseManager.base.util.DateUtil;

import org.bukkit.inventory.ItemStack;
import ua.favn.baseManager.base.util.ItemStackSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a saved location/waypoint.
 * Identified by owner + tag + name.
 * Can store coordinates in multiple dimensions (Overworld/Nether linked, End separate).
 */
public class SavedLocation {

    private Integer id;
    private UUID owner;
    private String tag;
    private String name;
    private LocalDateTime created;
    private boolean isPublic;
    private String icon;

    private final Map<String, int[]> coordinates = new HashMap<>();

    private LocationManager manager;

    public SavedLocation() {
    }

    public SavedLocation(LocationManager manager, UUID owner, String tag, String name, Location location) {
        this.manager = manager;
        this.owner = owner;
        this.tag = tag;
        this.name = name;
        this.created = LocalDateTime.now();
        this.isPublic = false;
        setCoords(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public void initialize(LocationManager manager) {
        this.manager = manager;
    }

    // Getters

    public Integer id() {
        return this.id;
    }

    public UUID owner() {
        return this.owner;
    }

    public String tag() {
        return this.tag;
    }

    public String name() {
        return this.name;
    }

    public LocalDateTime created() {
        return this.created;
    }

    public boolean isPublic() {
        return this.isPublic;
    }

    public String icon() {
        return this.icon;
    }

    /**
     * Get the icon as a deserialized ItemStack.
     * Returns null if no icon is set or deserialization fails.
     */
    public ItemStack iconItem() {
        return ItemStackSerializer.deserialize(this.icon);
    }

    // Fluent setters

    public SavedLocation name(String name) {
        this.name = name;
        return this;
    }

    public SavedLocation tag(String tag) {
        this.tag = tag;
        return this;
    }

    public SavedLocation isPublic(boolean isPublic) {
        this.isPublic = isPublic;
        return this;
    }

    public SavedLocation icon(String icon) {
        this.icon = icon;
        return this;
    }

    // Multi-dimension coordinate methods

    public void setCoords(String world, int x, int y, int z) {
        coordinates.put(world, new int[]{x, y, z});
    }

    public int[] getCoords(String world) {
        return coordinates.get(world);
    }

    public boolean hasCoords(String world) {
        return coordinates.containsKey(world);
    }

    public Set<String> getWorlds() {
        return coordinates.keySet();
    }

    /**
     * Get a Location object for the given world.
     *
     * @param world The world name
     * @return The Location, or null if no coords for this world
     */
    public Location loc(String world) {
        int[] coords = coordinates.get(world);
        if (coords == null) {
            return null;
        }
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        return new Location(w, coords[0] + 0.5, coords[1], coords[2] + 0.5);
    }

    /**
     * Get a Location object for the first available world.
     * Used for backwards compatibility.
     */
    public Location loc() {
        if (coordinates.isEmpty()) {
            return null;
        }
        return loc(primaryWorld());
    }

    /**
     * Get coordinates string for a specific world.
     */
    public String coordsString(String world) {
        int[] coords = coordinates.get(world);
        if (coords == null) {
            return "N/A";
        }
        return coords[0] + " " + coords[1] + " " + coords[2];
    }

    /**
     * Get coordinates string for the first available world.
     */
    public String coordsString() {
        return coordsString(primaryWorld());
    }

    /**
     * Get the first available world name.
     */
    public String primaryWorld() {
        if (coordinates.isEmpty()) {
            return "unknown";
        }
        return coordinates.keySet().iterator().next();
    }

    /**
     * Get the display name including tag, e.g. "BASE:home".
     */
    public String displayName() {
        return this.tag + ":" + this.name;
    }

    // Persistence

    /**
     * Save location metadata to database (INSERT or UPDATE).
     */
    public void save() {
        try {
            Connection connection = this.manager.getDatabaseManager().connect();
            if (this.id == null) {
                // INSERT
                try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO locations (owner, tag, name, created, isPublic, icon) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS
                )) {
                    ps.setString(1, this.owner.toString());
                    ps.setString(2, this.tag);
                    ps.setString(3, this.name);
                    ps.setString(4, DateUtil.formatDate(this.created));
                    ps.setInt(5, this.isPublic ? 1 : 0);
                    ps.setString(6, this.icon);
                    ps.execute();

                    try (var rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            this.id = rs.getInt(1);
                        }
                    }
                }
                // Insert all coordinates
                saveAllCoords(connection);
            } else {
                // UPDATE metadata only
                try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE locations SET name = ?, tag = ?, isPublic = ?, icon = ? WHERE id = ?"
                )) {
                    ps.setString(1, this.name);
                    ps.setString(2, this.tag);
                    ps.setInt(3, this.isPublic ? 1 : 0);
                    ps.setString(4, this.icon);
                    ps.setInt(5, this.id);
                    ps.execute();
                }
            }
            connection.close();
        } catch (SQLException e) {
            this.manager.getPlugin().getLogger().severe("Failed to save location: " + e.getMessage());
        }
    }

    /**
     * Save or update coordinates for a specific world.
     */
    public void saveCoords(String world) {
        if (this.id == null) {
            return;
        }
        int[] coords = coordinates.get(world);
        if (coords == null) {
            return;
        }
        try {
            Connection connection = this.manager.getDatabaseManager().connect();
            try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO location_coords (locationId, world, locX, locY, locZ) "
                + "VALUES (?, ?, ?, ?, ?)"
            )) {
                ps.setInt(1, this.id);
                ps.setString(2, world);
                ps.setInt(3, coords[0]);
                ps.setInt(4, coords[1]);
                ps.setInt(5, coords[2]);
                ps.execute();
            }
            connection.close();
        } catch (SQLException e) {
            this.manager.getPlugin().getLogger().severe("Failed to save coords for " + world + ": " + e.getMessage());
        }
    }

    private void saveAllCoords(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT OR REPLACE INTO location_coords (locationId, world, locX, locY, locZ) "
            + "VALUES (?, ?, ?, ?, ?)"
        )) {
            for (Map.Entry<String, int[]> entry : coordinates.entrySet()) {
                ps.setInt(1, this.id);
                ps.setString(2, entry.getKey());
                ps.setInt(3, entry.getValue()[0]);
                ps.setInt(4, entry.getValue()[1]);
                ps.setInt(5, entry.getValue()[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void delete() {
        if (this.id == null) {
            return;
        }
        try {
            Connection connection = this.manager.getDatabaseManager().connect();
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM locations WHERE id = ?")) {
                ps.setInt(1, this.id);
                ps.execute();
            }
            connection.close();
            this.manager.removeFromCache(this);
        } catch (SQLException e) {
            this.manager.getPlugin().getLogger().severe("Failed to delete location: " + e.getMessage());
        }
    }

    // Internal setters for reflection-based parsing

    void setId(int id) {
        this.id = id;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    void setTag(String tag) {
        this.tag = tag;
    }

    void setName(String name) {
        this.name = name;
    }

    void setCreated(LocalDateTime created) {
        this.created = created;
    }

    void setIsPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    void setIcon(String icon) {
        this.icon = icon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SavedLocation that = (SavedLocation) o;
        if (this.id != null && that.id != null) {
            return Objects.equals(this.id, that.id);
        }
        return Objects.equals(this.owner, that.owner)
            && Objects.equals(this.tag, that.tag)
            && Objects.equals(this.name, that.name);
    }

    @Override
    public int hashCode() {
        if (this.id != null) {
            return Objects.hash(this.id);
        }
        return Objects.hash(this.owner, this.tag, this.name);
    }
}
