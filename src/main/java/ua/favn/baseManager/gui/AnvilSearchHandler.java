package ua.favn.baseManager.gui;

import java.util.Collections;
import java.util.List;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;

/**
 * Handles anvil-based search for the location browser GUI using AnvilGUI library.
 */
public class AnvilSearchHandler extends Base {

    public AnvilSearchHandler(BaseManager plugin) {
        super(plugin);
    }

    /**
     * Open an anvil search GUI for the player.
     */
    public void openSearch(Player player, LocationBrowserGui.Filter currentFilter) {
        new AnvilGUI.Builder()
            .plugin(getPlugin())
            .title("Search Locations")
            .itemLeft(new ItemStack(Material.NAME_TAG))
            .text(" ")
            .onClick((slot, state) -> {
                if (slot != 2) {
                    return Collections.emptyList();
                }
                String text = state.getText();
                Player clicker = state.getPlayer();

                // Delay GUI opening by 1 tick so the anvil close finishes first
                Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
                    if (text != null && !text.isBlank()) {
                        getPlugin().getGuiManager().openLocationBrowser(
                            clicker, currentFilter, text.trim());
                    } else {
                        getPlugin().getGuiManager().openLocationBrowser(
                            clicker, currentFilter);
                    }
                }, 1L);

                return List.of(AnvilGUI.ResponseAction.close());
            })
            .open(player);
    }
}
