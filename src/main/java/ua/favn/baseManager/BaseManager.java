package ua.favn.baseManager;

import org.bukkit.plugin.java.JavaPlugin;
import ua.favn.baseManager.base.gui.GuiListener;
import ua.favn.baseManager.commands.BaseCommand;
import ua.favn.baseManager.compass.GlowManager;
import ua.favn.baseManager.compass.TrackingCompassListener;
import ua.favn.baseManager.compass.TrackingCompassManager;
import ua.favn.baseManager.config.MessageManager;
import ua.favn.baseManager.gui.AnvilSearchHandler;
import ua.favn.baseManager.gui.GuiManager;
import ua.favn.baseManager.location.LocationManager;
import ua.favn.baseManager.lodestone.LodestoneListener;
import ua.favn.baseManager.lodestone.LodestoneManager;
import ua.favn.baseManager.storage.DatabaseManager;

/**
 * Main plugin class for BaseManager.
 * Provides location/waypoint management with compass navigation and sharing.
 */
public class BaseManager extends JavaPlugin {

    private DatabaseManager databaseManager;
    private LocationManager locationManager;
    private LodestoneManager lodestoneManager;
    private TrackingCompassManager compassManager;
    private TrackingCompassListener compassListener;
    private GlowManager glowManager;
    private MessageManager messageManager;
    private GuiManager guiManager;
    private AnvilSearchHandler anvilSearchHandler;

    @Override
    public void onEnable() {
        // Save default configs
        this.saveDefaultConfig();

        // Initialize database
        this.databaseManager = new DatabaseManager(this);

        // Initialize managers
        this.locationManager = new LocationManager(this);
        this.locationManager.initialize();

        this.lodestoneManager = new LodestoneManager(this);
        this.lodestoneManager.initialize();

        this.compassManager = new TrackingCompassManager(this);
        this.glowManager = new GlowManager(this);
        this.messageManager = new MessageManager(this);
        this.guiManager = new GuiManager(this);

        // Register event listeners
        this.compassListener = new TrackingCompassListener(this);
        this.anvilSearchHandler = new AnvilSearchHandler(this);
        this.getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        this.getServer().getPluginManager().registerEvents(this.compassListener, this);
        this.getServer().getPluginManager().registerEvents(new LodestoneListener(this), this);

        // Register commands
        new BaseCommand(this);

        this.getLogger().info("BaseManager enabled!");
    }

    @Override
    public void onDisable() {
        if (this.glowManager != null) {
            this.glowManager.cleanup();
        }
        this.getLogger().info("BaseManager disabled!");
    }

    public void reloadConfigs() {
        this.reloadConfig();
        this.messageManager.reload();
        this.getLogger().info("Configuration reloaded.");
    }

    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public LocationManager getLocationManager() {
        return this.locationManager;
    }

    public LodestoneManager getLodestoneManager() {
        return this.lodestoneManager;
    }

    public TrackingCompassManager getCompassManager() {
        return this.compassManager;
    }

    public MessageManager getMessageManager() {
        return this.messageManager;
    }

    public TrackingCompassListener getCompassListener() {
        return this.compassListener;
    }

    public GlowManager getGlowManager() {
        return this.glowManager;
    }

    public GuiManager getGuiManager() {
        return this.guiManager;
    }

    public AnvilSearchHandler getAnvilSearchHandler() {
        return this.anvilSearchHandler;
    }
}
