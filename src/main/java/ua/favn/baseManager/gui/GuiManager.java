package ua.favn.baseManager.gui;

import org.bukkit.entity.Player;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.location.SavedLocation;

/**
 * Central manager for GUI navigation and creation.
 */
public class GuiManager extends Base {

    public GuiManager(BaseManager plugin) {
        super(plugin);
    }

    /**
     * Opens the main location browser GUI.
     */
    public void openLocationBrowser(Player player) {
        openLocationBrowser(player, LocationBrowserGui.Filter.ALL);
    }

    /**
     * Opens the location browser with a specific filter.
     */
    public void openLocationBrowser(Player player, LocationBrowserGui.Filter filter) {
        new LocationBrowserGui(getPlugin(), player, filter).open(player);
    }

    /**
     * Opens the location browser with a filter and search query.
     */
    public void openLocationBrowser(Player player, LocationBrowserGui.Filter filter, String searchQuery) {
        new LocationBrowserGui(getPlugin(), player, filter, searchQuery).open(player);
    }

    /**
     * Opens the location details GUI.
     */
    public void openLocationDetails(Player player, SavedLocation location) {
        LocationDetailsGui.openAsync(getPlugin(), player, location);
    }

    /**
     * Opens the location details GUI with a custom back command.
     * When backCommand is non-null, the back button runs that command instead of opening the browser.
     */
    public void openLocationDetails(Player player, SavedLocation location, String backCommand) {
        LocationDetailsGui.openAsync(getPlugin(), player, location, backCommand);
    }

    /**
     * Opens the members list GUI for a location.
     */
    public void openMembersList(Player player, SavedLocation location) {
        MembersListGui.openAsync(getPlugin(), player, location);
    }
}
