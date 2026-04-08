package ua.favn.baseManager.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages SQLite database connections.
 */
public final class DatabaseManager {

    private final String connectionUrl;

    public DatabaseManager(Plugin plugin) {
        plugin.getDataFolder().mkdirs();
        this.connectionUrl = "jdbc:sqlite:" + plugin.getDataFolder() + File.separator + "storage.db";
    }

    /**
     * Creates a new database connection.
     *
     * @return A new connection to the SQLite database
     * @throws SQLException if connection fails
     */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(this.connectionUrl);
    }

    public String getConnectionUrl() {
        return this.connectionUrl;
    }
}
