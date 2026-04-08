package ua.favn.baseManager.location;

import org.bukkit.Location;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.base.util.DateUtil;
import ua.favn.baseManager.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages saved locations with caching and database persistence.
 */
public class LocationManager extends Base {

    private final Map<Integer, SavedLocation> cacheById = new HashMap<>();
    private final Map<UUID, List<SavedLocation>> cacheByOwner = new HashMap<>();
    private ShareManager shareManager;

    public LocationManager(BaseManager plugin) {
        super(plugin);
    }

    public void initialize() {
        createTables();
        migrateData();
        this.shareManager = new ShareManager(this);
        fetchAll();
    }

    private void createTables() {
        try (Connection conn = getDatabaseManager().connect();
             Statement stmt = conn.createStatement()) {

            // Locations table (metadata only, coords in location_coords)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS locations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner TEXT NOT NULL,
                    tag TEXT NOT NULL DEFAULT 'BASE',
                    name TEXT NOT NULL,
                    created TEXT NOT NULL,
                    isPublic INTEGER DEFAULT 0,
                    icon TEXT DEFAULT NULL
                )
                """);

            // Multi-dimension coordinates table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS location_coords (
                    locationId INTEGER NOT NULL,
                    world TEXT NOT NULL,
                    locX INTEGER NOT NULL,
                    locY INTEGER NOT NULL,
                    locZ INTEGER NOT NULL,
                    PRIMARY KEY (locationId, world),
                    FOREIGN KEY (locationId) REFERENCES locations(id) ON DELETE CASCADE
                )
                """);

            // Shares table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS location_shares (
                    locationId INTEGER NOT NULL,
                    sharedWith TEXT NOT NULL,
                    PRIMARY KEY (locationId, sharedWith),
                    FOREIGN KEY (locationId) REFERENCES locations(id) ON DELETE CASCADE
                )
                """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_locations_owner ON locations(owner)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_locations_tag_name ON locations(owner, tag, name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_location_coords ON location_coords(locationId)");

        } catch (SQLException e) {
            getPlugin().getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    /**
     * Migrate data from old schema (world/locX/locY/locZ in locations table)
     * to new schema (coords in location_coords table).
     */
    private void migrateData() {
        try (Connection conn = getDatabaseManager().connect();
             Statement stmt = conn.createStatement()) {

            // Check if old columns exist
            boolean hasOldColumns = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(locations)")) {
                while (rs.next()) {
                    if ("world".equals(rs.getString("name"))) {
                        hasOldColumns = true;
                        break;
                    }
                }
            }

            if (!hasOldColumns) {
                return; // Already migrated or fresh install
            }

            // Check if there's data to migrate
            int oldCount = 0;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM locations WHERE world IS NOT NULL")) {
                if (rs.next()) {
                    oldCount = rs.getInt(1);
                }
            }

            if (oldCount == 0) {
                return; // No data to migrate
            }

            getPlugin().getLogger().info("Migrating " + oldCount + " locations to new schema...");

            // Add tag column if missing
            try {
                stmt.execute("ALTER TABLE locations ADD COLUMN tag TEXT NOT NULL DEFAULT 'BASE'");
            } catch (SQLException e) {
                // Column already exists
            }

            // Add icon column if missing
            try {
                stmt.execute("ALTER TABLE locations ADD COLUMN icon TEXT DEFAULT NULL");
            } catch (SQLException e) {
                // Column already exists
            }

            // Migrate coords from old locations table to location_coords
            // For locations with same owner+name, merge them (OW/Nether linking)
            stmt.execute("""
                INSERT OR IGNORE INTO location_coords (locationId, world, locX, locY, locZ)
                SELECT id, world, locX, locY, locZ FROM locations
                WHERE world IS NOT NULL
                """);

            // Now merge duplicate-name locations (same owner, same name, different worlds)
            // Keep the oldest one and move coords from duplicates
            try (ResultSet rs = stmt.executeQuery("""
                SELECT owner, name, MIN(id) as keep_id, GROUP_CONCAT(id) as all_ids
                FROM locations
                WHERE world IS NOT NULL
                GROUP BY owner, name
                HAVING COUNT(*) > 1
                """)) {
                while (rs.next()) {
                    int keepId = rs.getInt("keep_id");
                    String allIds = rs.getString("all_ids");
                    String[] ids = allIds.split(",");

                    for (String idStr : ids) {
                        int locId = Integer.parseInt(idStr.trim());
                        if (locId != keepId) {
                            // Move coords to the kept location
                            try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE location_coords SET locationId = ? WHERE locationId = ?")) {
                                ps.setInt(1, keepId);
                                ps.setInt(2, locId);
                                ps.execute();
                            }
                            // Move shares
                            try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE OR IGNORE location_shares SET locationId = ? WHERE locationId = ?")) {
                                ps.setInt(1, keepId);
                                ps.setInt(2, locId);
                                ps.execute();
                            }
                            // Delete duplicate
                            try (PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM locations WHERE id = ?")) {
                                ps.setInt(1, locId);
                                ps.execute();
                            }
                        }
                    }
                }
            }

            getPlugin().getLogger().info("Migration complete.");

        } catch (SQLException e) {
            getPlugin().getLogger().severe("Failed to migrate data: " + e.getMessage());
        }
    }

    private void fetchAll() {
        cacheById.clear();
        cacheByOwner.clear();

        try (Connection conn = getDatabaseManager().connect();
             Statement stmt = conn.createStatement()) {

            // Load all locations
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT id, owner, tag, name, created, isPublic, icon FROM locations")) {
                while (rs.next()) {
                    SavedLocation loc = parseLocation(rs);
                    cacheById.put(loc.id(), loc);
                    cacheByOwner.computeIfAbsent(loc.owner(), k -> new ArrayList<>()).add(loc);
                }
            }

            // Load all coordinates
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT locationId, world, locX, locY, locZ FROM location_coords")) {
                while (rs.next()) {
                    int locationId = rs.getInt("locationId");
                    SavedLocation loc = cacheById.get(locationId);
                    if (loc != null) {
                        loc.setCoords(
                            rs.getString("world"),
                            rs.getInt("locX"),
                            rs.getInt("locY"),
                            rs.getInt("locZ")
                        );
                    }
                }
            }

            getPlugin().getLogger().info("Loaded " + cacheById.size() + " locations.");

        } catch (SQLException e) {
            getPlugin().getLogger().severe("Failed to fetch locations: " + e.getMessage());
        }
    }

    private SavedLocation parseLocation(ResultSet rs) throws SQLException {
        SavedLocation loc = new SavedLocation();
        loc.initialize(this);
        loc.setId(rs.getInt("id"));
        loc.setOwner(UUID.fromString(rs.getString("owner")));
        loc.setTag(rs.getString("tag") != null ? rs.getString("tag") : "BASE");
        loc.setName(rs.getString("name"));
        loc.setCreated(DateUtil.parseDate(rs.getString("created")));
        loc.setIsPublic(rs.getInt("isPublic") == 1);
        loc.setIcon(rs.getString("icon"));
        return loc;
    }

    private void addToCache(SavedLocation loc) {
        cacheById.put(loc.id(), loc);
        cacheByOwner.computeIfAbsent(loc.owner(), k -> new ArrayList<>()).add(loc);
    }

    void removeFromCache(SavedLocation loc) {
        cacheById.remove(loc.id());
        List<SavedLocation> ownerList = cacheByOwner.get(loc.owner());
        if (ownerList != null) {
            ownerList.remove(loc);
        }
    }

    // Public API

    /**
     * Creates and saves a new location.
     */
    public SavedLocation create(UUID owner, String tag, String name, Location location) {
        SavedLocation loc = new SavedLocation(this, owner, tag, name, location);
        loc.save();
        addToCache(loc);
        return loc;
    }

    /**
     * Gets a location by ID.
     */
    public SavedLocation get(int id) {
        return cacheById.get(id);
    }

    /**
     * Gets all locations owned by a player.
     */
    public List<SavedLocation> getByOwner(UUID owner) {
        return cacheByOwner.getOrDefault(owner, new ArrayList<>());
    }

    /**
     * Gets a specific location by owner, tag, and name.
     */
    public SavedLocation getByOwnerTagAndName(UUID owner, String tag, String name) {
        List<SavedLocation> ownerLocs = getByOwner(owner);
        for (SavedLocation loc : ownerLocs) {
            if (loc.tag().equalsIgnoreCase(tag) && loc.name().equalsIgnoreCase(name)) {
                return loc;
            }
        }
        return null;
    }

    /**
     * Gets a location by tag and name across all players (for admin access).
     */
    public SavedLocation getByTagAndName(String tag, String name) {
        for (SavedLocation loc : cacheById.values()) {
            if (loc.tag().equalsIgnoreCase(tag) && loc.name().equalsIgnoreCase(name)) {
                return loc;
            }
        }
        return null;
    }

    /**
     * Gets all locations across all players.
     */
    public List<SavedLocation> getAll() {
        return new ArrayList<>(cacheById.values());
    }

    /**
     * Gets a specific location by owner and name (first match, ignoring tag).
     * Used for backwards compatibility.
     */
    public SavedLocation getByOwnerAndName(UUID owner, String name) {
        List<SavedLocation> ownerLocs = getByOwner(owner);
        for (SavedLocation loc : ownerLocs) {
            if (loc.name().equalsIgnoreCase(name)) {
                return loc;
            }
        }
        return null;
    }

    /**
     * Gets all public locations.
     */
    public List<SavedLocation> getPublicLocations() {
        List<SavedLocation> result = new ArrayList<>();
        for (SavedLocation loc : cacheById.values()) {
            if (loc.isPublic()) {
                result.add(loc);
            }
        }
        return result;
    }

    /**
     * Gets all locations accessible by a player (own + shared + public).
     */
    public List<SavedLocation> getAccessibleLocations(UUID player) {
        List<SavedLocation> result = new ArrayList<>(getByOwner(player));

        // Add shared locations
        for (SavedLocation loc : cacheById.values()) {
            if (!loc.owner().equals(player)) {
                if (loc.isPublic() || shareManager.isSharedWith(loc.id(), player)) {
                    result.add(loc);
                }
            }
        }

        return result;
    }

    /**
     * Checks if a player can access a location.
     */
    public boolean canAccess(SavedLocation loc, UUID player) {
        return loc.owner().equals(player)
            || loc.isPublic()
            || shareManager.isSharedWith(loc.id(), player);
    }

    public DatabaseManager getDatabaseManager() {
        return getPlugin().getDatabaseManager();
    }

    public ShareManager getShareManager() {
        return shareManager;
    }
}
