package ua.favn.baseManager.lodestone;

import ua.favn.baseManager.base.util.BlockLocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Represents a lodestone + sign structure that is linked to a SavedLocation.
 * When either block is destroyed, the location is deleted.
 */
public class LodestoneStructure {

    private Integer locationId;
    private BlockLocation lodestone;
    private BlockLocation sign;
    private String tag;

    private LodestoneManager manager;

    public LodestoneStructure() {
    }

    public LodestoneStructure(LodestoneManager manager, Integer locationId,
                              BlockLocation lodestone, BlockLocation sign, String tag) {
        this.manager = manager;
        this.locationId = locationId;
        this.lodestone = lodestone;
        this.sign = sign;
        this.tag = tag;
    }

    public void initialize(LodestoneManager manager) {
        this.manager = manager;
    }

    // Getters

    public Integer locationId() {
        return this.locationId;
    }

    public BlockLocation lodestone() {
        return this.lodestone;
    }

    public BlockLocation sign() {
        return this.sign;
    }

    public String tag() {
        return this.tag;
    }

    // Persistence

    public void save() {
        try {
            Connection connection = this.manager.getDatabaseManager().connect();
            try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO lodestone_structures "
                + "(locationId, lodestoneWorld, lodestoneX, lodestoneY, lodestoneZ, "
                + "signWorld, signX, signY, signZ, tag) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )) {
                ps.setInt(1, this.locationId);
                ps.setString(2, this.lodestone.world().getName());
                ps.setInt(3, this.lodestone.x());
                ps.setInt(4, this.lodestone.y());
                ps.setInt(5, this.lodestone.z());
                ps.setString(6, this.sign.world().getName());
                ps.setInt(7, this.sign.x());
                ps.setInt(8, this.sign.y());
                ps.setInt(9, this.sign.z());
                ps.setString(10, this.tag);
                ps.execute();
            }
            connection.close();
        } catch (SQLException e) {
            this.manager.getPlugin().getLogger().severe("Failed to save lodestone structure: " + e.getMessage());
        }
    }

    public void delete() {
        if (this.locationId == null) {
            return;
        }
        try {
            Connection connection = this.manager.getDatabaseManager().connect();
            try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM lodestone_structures WHERE locationId = ?"
            )) {
                ps.setInt(1, this.locationId);
                ps.execute();
            }
            connection.close();
            this.manager.removeFromCache(this);
        } catch (SQLException e) {
            this.manager.getPlugin().getLogger().severe("Failed to delete lodestone structure: " + e.getMessage());
        }
    }
}
