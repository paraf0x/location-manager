package ua.favn.baseManager.location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages location members (builders/dwellers).
 */
public class MemberManager {

    private final LocationManager locationManager;

    public MemberManager(LocationManager locationManager) {
        this.locationManager = locationManager;
    }

    /**
     * Adds a member to a location.
     *
     * @param locationId The location ID
     * @param player The player UUID
     * @param name The player name
     * @return true if adding was successful
     */
    public boolean addMember(int locationId, UUID player, String name) {
        try (Connection conn = locationManager.getDatabaseManager().connect();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO location_members (locationId, memberUUID, memberName) VALUES (?, ?, ?)"
             )) {
            ps.setInt(1, locationId);
            ps.setString(2, player.toString());
            ps.setString(3, name);
            ps.execute();
            return true;
        } catch (SQLException e) {
            locationManager.getPlugin().getLogger().severe("Failed to add member: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes a member from a location.
     *
     * @param locationId The location ID
     * @param player The player to remove
     * @return true if removal was successful
     */
    public boolean removeMember(int locationId, UUID player) {
        try (Connection conn = locationManager.getDatabaseManager().connect();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM location_members WHERE locationId = ? AND memberUUID = ?"
             )) {
            ps.setInt(1, locationId);
            ps.setString(2, player.toString());
            int deleted = ps.executeUpdate();
            return deleted > 0;
        } catch (SQLException e) {
            locationManager.getPlugin().getLogger().severe("Failed to remove member: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a player is a member of a location.
     */
    public boolean isMember(int locationId, UUID player) {
        try (Connection conn = locationManager.getDatabaseManager().connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM location_members WHERE locationId = ? AND memberUUID = ?"
             )) {
            ps.setInt(1, locationId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            locationManager.getPlugin().getLogger().severe("Failed to check member: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets all members of a location as UUID -> name map.
     */
    public Map<UUID, String> getMembers(int locationId) {
        Map<UUID, String> result = new LinkedHashMap<>();
        try (Connection conn = locationManager.getDatabaseManager().connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT memberUUID, memberName FROM location_members WHERE locationId = ?"
             )) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("memberUUID"));
                    String name = rs.getString("memberName");
                    result.put(uuid, name != null && !name.isEmpty() ? name : uuid.toString().substring(0, 8));
                }
            }
        } catch (SQLException e) {
            locationManager.getPlugin().getLogger().severe("Failed to get members: " + e.getMessage());
        }
        return result;
    }
}
