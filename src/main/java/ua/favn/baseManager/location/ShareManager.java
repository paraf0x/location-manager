package ua.favn.baseManager.location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages location sharing between players.
 */
public class ShareManager {

    private final LocationManager locationManager;

    public ShareManager(LocationManager locationManager) {
        this.locationManager = locationManager;
    }

    /**
     * Shares a location with another player.
     *
     * @param locationId The location ID
     * @param player The player to share with
     * @return true if sharing was successful
     */
    public boolean share(int locationId, UUID player) {
        try (Connection conn = locationManager.getDatabaseManager().connect();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO location_shares (locationId, sharedWith) VALUES (?, ?)"
             )) {
            ps.setInt(1, locationId);
            ps.setString(2, player.toString());
            ps.execute();
            return true;
        } catch (SQLException e) {
            locationManager.getPlugin().getLogger().severe("Failed to share location: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes sharing from a player.
     *
     * @param locationId The location ID
     * @param player The player to unshare with
     * @return true if unsharing was successful
     */
    public boolean unshare(int locationId, UUID player) {
        try (Connection conn = locationManager.getDatabaseManager().connect();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM location_shares WHERE locationId = ? AND sharedWith = ?"
             )) {
            ps.setInt(1, locationId);
            ps.setString(2, player.toString());
            int deleted = ps.executeUpdate();
            return deleted > 0;
        } catch (SQLException e) {
            locationManager.getPlugin().getLogger().severe("Failed to unshare location: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a location is shared with a specific player.
     */
    public boolean isSharedWith(int locationId, UUID player) {
        try (Connection conn = locationManager.getDatabaseManager().connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM location_shares WHERE locationId = ? AND sharedWith = ?"
             )) {
            ps.setInt(1, locationId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            locationManager.getPlugin().getLogger().severe("Failed to check share: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets all players a location is shared with.
     */
    public Set<UUID> getSharedWith(int locationId) {
        Set<UUID> result = new HashSet<>();
        try (Connection conn = locationManager.getDatabaseManager().connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT sharedWith FROM location_shares WHERE locationId = ?"
             )) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString("sharedWith")));
                }
            }
        } catch (SQLException e) {
            locationManager.getPlugin().getLogger().severe("Failed to get shared players: " + e.getMessage());
        }
        return result;
    }

    /**
     * Gets all locations shared with a specific player.
     */
    public Set<Integer> getLocationsSharedWithPlayer(UUID player) {
        Set<Integer> result = new HashSet<>();
        try (Connection conn = locationManager.getDatabaseManager().connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT locationId FROM location_shares WHERE sharedWith = ?"
             )) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getInt("locationId"));
                }
            }
        } catch (SQLException e) {
            locationManager.getPlugin().getLogger().severe("Failed to get shared locations: " + e.getMessage());
        }
        return result;
    }
}
