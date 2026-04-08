package ua.favn.baseManager.lodestone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.base.util.BlockLocation;
import ua.favn.baseManager.location.SavedLocation;
import ua.favn.baseManager.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages lodestone structures with caching for fast block lookups.
 */
public class LodestoneManager extends Base {

    // Cache by block position - both lodestone and sign positions point to the same structure
    private final Map<BlockLocation, LodestoneStructure> cacheByBlock = new HashMap<>();
    // Cache by location ID for reverse lookups
    private final Map<Integer, LodestoneStructure> cacheByLocationId = new HashMap<>();

    public LodestoneManager(BaseManager plugin) {
        super(plugin);
    }

    public void initialize() {
        createTables();
        fetchAll();
    }

    private void createTables() {
        try (Connection conn = getDatabaseManager().connect();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS lodestone_structures (
                    locationId INTEGER PRIMARY KEY,
                    lodestoneWorld TEXT NOT NULL,
                    lodestoneX INTEGER NOT NULL,
                    lodestoneY INTEGER NOT NULL,
                    lodestoneZ INTEGER NOT NULL,
                    signWorld TEXT NOT NULL,
                    signX INTEGER NOT NULL,
                    signY INTEGER NOT NULL,
                    signZ INTEGER NOT NULL,
                    tag TEXT NOT NULL,
                    FOREIGN KEY (locationId) REFERENCES locations(id) ON DELETE CASCADE
                )
                """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_lodestone_pos "
                + "ON lodestone_structures(lodestoneWorld, lodestoneX, lodestoneY, lodestoneZ)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sign_pos "
                + "ON lodestone_structures(signWorld, signX, signY, signZ)");

        } catch (SQLException e) {
            getPlugin().getLogger().severe("Failed to create lodestone tables: " + e.getMessage());
        }
    }

    private void fetchAll() {
        cacheByBlock.clear();
        cacheByLocationId.clear();

        try (Connection conn = getDatabaseManager().connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM lodestone_structures")) {

            while (rs.next()) {
                LodestoneStructure structure = parseFromResultSet(rs);
                if (structure != null) {
                    addToCache(structure);
                }
            }

            getPlugin().getLogger().info("Loaded " + cacheByLocationId.size() + " lodestone structures.");

        } catch (SQLException e) {
            getPlugin().getLogger().severe("Failed to fetch lodestone structures: " + e.getMessage());
        }
    }

    private LodestoneStructure parseFromResultSet(ResultSet rs) throws SQLException {
        int locationId = rs.getInt("locationId");

        String lodestoneWorldName = rs.getString("lodestoneWorld");
        World lodestoneWorld = Bukkit.getWorld(lodestoneWorldName);
        if (lodestoneWorld == null) {
            getPlugin().getLogger().warning("World not found for lodestone structure: " + lodestoneWorldName);
            return null;
        }

        String signWorldName = rs.getString("signWorld");
        World signWorld = Bukkit.getWorld(signWorldName);
        if (signWorld == null) {
            getPlugin().getLogger().warning("World not found for lodestone structure sign: " + signWorldName);
            return null;
        }

        BlockLocation lodestone = new BlockLocation(
            lodestoneWorld,
            rs.getInt("lodestoneX"),
            rs.getInt("lodestoneY"),
            rs.getInt("lodestoneZ")
        );

        BlockLocation sign = new BlockLocation(
            signWorld,
            rs.getInt("signX"),
            rs.getInt("signY"),
            rs.getInt("signZ")
        );

        String tag = rs.getString("tag");

        LodestoneStructure structure = new LodestoneStructure(this, locationId, lodestone, sign, tag);
        return structure;
    }

    void addToCache(LodestoneStructure structure) {
        cacheByBlock.put(structure.lodestone(), structure);
        cacheByBlock.put(structure.sign(), structure);
        cacheByLocationId.put(structure.locationId(), structure);
    }

    void removeFromCache(LodestoneStructure structure) {
        cacheByBlock.remove(structure.lodestone());
        cacheByBlock.remove(structure.sign());
        cacheByLocationId.remove(structure.locationId());
    }

    // Public API

    /**
     * Creates a new lodestone structure linked to a new location.
     *
     * @param owner     The player who created the structure
     * @param lodestone The lodestone block location
     * @param sign      The sign block location
     * @param tag       The tag from the sign (without brackets)
     * @param name      The location name
     * @return The created structure, or null if creation failed
     */
    public LodestoneStructure create(Player owner, Location lodestone, Location sign, String tag, String name) {
        // First create the SavedLocation
        SavedLocation location = getPlugin().getLocationManager().create(
            owner.getUniqueId(),
            tag,
            name,
            lodestone
        );

        // Make it public (all lodestone locations are public)
        location.isPublic(true);
        location.save();

        // Create the structure
        BlockLocation lodestoneBlock = BlockLocation.fromLocation(lodestone);
        BlockLocation signBlock = BlockLocation.fromLocation(sign);

        LodestoneStructure structure = new LodestoneStructure(
            this,
            location.id(),
            lodestoneBlock,
            signBlock,
            tag
        );

        structure.save();
        addToCache(structure);

        return structure;
    }

    /**
     * Gets a structure by any of its block positions (lodestone or sign).
     */
    public LodestoneStructure getByBlock(BlockLocation block) {
        return cacheByBlock.get(block);
    }

    /**
     * Gets a structure by its linked location ID.
     */
    public LodestoneStructure getByLocationId(int locationId) {
        return cacheByLocationId.get(locationId);
    }

    /**
     * Checks if a block is part of a registered lodestone structure.
     */
    public boolean isRegisteredBlock(BlockLocation block) {
        return cacheByBlock.containsKey(block);
    }

    /**
     * Deletes a structure and its linked SavedLocation.
     */
    public void delete(LodestoneStructure structure) {
        // Delete the SavedLocation first
        SavedLocation location = getPlugin().getLocationManager().get(structure.locationId());
        if (location != null) {
            location.delete();
        }

        // Delete the structure record
        structure.delete();
    }

    /**
     * Gets the list of allowed tags from config.
     */
    public List<String> getAllowedTags() {
        return getPlugin().getConfig().getStringList("lodestone.allowed-tags");
    }

    /**
     * Checks if lodestone registration is enabled.
     */
    public boolean isEnabled() {
        return getPlugin().getConfig().getBoolean("lodestone.enabled", true);
    }

    public DatabaseManager getDatabaseManager() {
        return getPlugin().getDatabaseManager();
    }
}
