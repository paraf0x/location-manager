package ua.favn.baseManager.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.gui.GuiButton;
import ua.favn.baseManager.base.gui.PagedGuiInventory;
import ua.favn.baseManager.base.util.Colors;
import ua.favn.baseManager.base.util.FormatUtil;
import ua.favn.baseManager.location.SavedLocation;

/**
 * GUI for browsing saved locations.
 */
public class LocationBrowserGui extends PagedGuiInventory<SavedLocation> {
    private final Player player;
    private final Filter filter;

    public enum Filter {
        ALL,
        OWN
    }

    public LocationBrowserGui(BaseManager plugin, Player player, Filter filter) {
        super(plugin, buildLocationList(plugin, player, filter));
        this.player = player;
        this.filter = filter;
    }

    private static List<SavedLocation> buildLocationList(BaseManager plugin, Player player, Filter filter) {
        return switch (filter) {
            case ALL -> plugin.getLocationManager().getAll();
            case OWN -> plugin.getLocationManager().getByOwner(player.getUniqueId());
        };
    }

    @Override
    public void compile() {
        // Background
        this.setBackground(createBackground());
        this.setOutline(createOutline(), OutlineType.BOTTOM);

        // Filter buttons
        int filterY = getHeight();
        createFilterButton(Filter.ALL, Material.COMPASS, 3, filterY);
        createFilterButton(Filter.OWN, Material.ENDER_PEARL, 4, filterY);

        // Page navigation
        if (this.getPage() > 0) {
            this.registerTempButton(new GuiButton(
                createSimpleItem(Material.ARROW, Component.text("Previous Page", Colors.GOLD)),
                clicker -> this.setPage(this.getPage() - 1, true),
                this.getSlot(1, getHeight())
            ));
        }

        if (this.getPage() < this.getLastPage()) {
            this.registerTempButton(new GuiButton(
                createSimpleItem(Material.ARROW, Component.text("Next Page", Colors.GOLD)),
                clicker -> this.setPage(this.getPage() + 1, true),
                this.getSlot(9, getHeight())
            ));
        }

        // Page indicator
        ItemStack pageIndicator = createSimpleItem(Material.PAPER,
            Component.text("Page " + (getPage() + 1) + "/" + (getLastPage() + 1), Colors.SILVER));
        this.setItem(8, getHeight(), pageIndicator);

        // Location entries
        List<SavedLocation> locationsOnPage = this.getSubjectsInPage();
        for (int i = 0; i < locationsOnPage.size(); i++) {
            SavedLocation loc = locationsOnPage.get(i);
            int x = (i % 7) + 2; // Columns 2-8
            int y = (i / 7) + 1; // Rows 1-4

            ItemStack item = createLocationItem(loc);

            this.registerTempButton(new GuiButton(
                item,
                (GuiButton.CompleteButtonAction) (clicker, info) -> {
                    if (info.click() != null && info.click().isShiftClick()) {
                        // Shift+Click = details
                        getPlugin().getGuiManager().openLocationDetails(player, loc);
                    } else {
                        // Normal click = compass
                        ItemStack compass = getPlugin().getCompassManager().createCompass(loc, player);
                        player.getInventory().addItem(compass);
                        getPlugin().getCompassListener().tryStartActionBar(player);
                        player.closeInventory();
                        getPlugin().getMessageManager().send(player, "commands.compass-given",
                            new FormatUtil.Format("{tag}", loc.tag()),
                            new FormatUtil.Format("{name}", loc.name()));
                    }
                    return GuiButton.ActionResult.EMPTY;
                },
                this.getSlot(x, y)
            ));
        }

        // Empty state
        if (this.subjects.isEmpty()) {
            ItemStack empty = createSimpleItem(Material.BARRIER,
                Component.text("No locations found", Colors.RED),
                Component.text("Place a sign on a lodestone and wax it!", Colors.SILVER));
            this.setItem(5, 3, empty);
        }
    }

    private void createFilterButton(Filter buttonFilter, Material material, int x, int y) {
        boolean active = this.filter == buttonFilter;
        String name = buttonFilter.name().charAt(0) + buttonFilter.name().substring(1).toLowerCase();

        ItemStack item;
        if (active) {
            item = createSimpleItem(material,
                Component.text(name + " (Active)", Colors.GOLD),
                Component.text("Currently showing " + name.toLowerCase() + " locations", Colors.SILVER));
        } else {
            item = createSimpleItem(material,
                Component.text(name, Colors.WHITE),
                Component.text("Click to show " + name.toLowerCase() + " locations", Colors.SILVER));
        }

        this.registerTempButton(new GuiButton(
            item,
            clicker -> {
                if (this.filter != buttonFilter) {
                    getPlugin().getGuiManager().openLocationBrowser(player, buttonFilter);
                }
            },
            this.getSlot(x, y)
        ));
    }

    private ItemStack createLocationItem(SavedLocation loc) {
        // Use custom icon ItemStack if available
        ItemStack iconItem = loc.iconItem();
        ItemStack item;

        if (iconItem != null) {
            item = iconItem.clone();
            item.setAmount(1);
        } else {
            // Default material
            item = new ItemStack(Material.LODESTONE);
        }

        ItemMeta meta = item.getItemMeta();
        meta.displayName(FormatUtil.normalize(Component.text(loc.displayName(), Colors.GOLD)));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Tag: ", Colors.WHITE).append(Component.text(loc.tag(), Colors.LIGHT_GREEN)));

        // Show which dimensions have coords
        for (String world : loc.getWorlds()) {
            int[] coords = loc.getCoords(world);
            lore.add(Component.text(world + ": ", Colors.WHITE)
                .append(Component.text(coords[0] + " " + coords[1] + " " + coords[2], Colors.SILVER)));
        }

        String ownerName = getPlayerName(loc.owner());
        lore.add(Component.text("Owner: ", Colors.WHITE).append(Component.text(ownerName, Colors.SILVER)));
        lore.add(Component.empty());
        lore.add(Component.text("Click for compass", Colors.GOLD));
        lore.add(Component.text("Shift+Click for details", Colors.GOLD));

        meta.lore(lore.stream().map(FormatUtil::normalize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String getPlayerName(java.util.UUID uuid) {
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }

    private ItemStack createBackground() {
        return createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
    }

    private ItemStack createOutline() {
        return createSimpleItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
    }

    private ItemStack createSimpleItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(FormatUtil.normalize(name));
        if (lore.length > 0) {
            meta.lore(java.util.Arrays.stream(lore).map(FormatUtil::normalize).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Component getTitle() {
        String filterName = filter.name().charAt(0) + filter.name().substring(1).toLowerCase();
        return Component.text("Locations - " + filterName, Colors.GOLD);
    }

    @Override
    public int getHeight() {
        return 6;
    }

    @Override
    protected int getSubjectsPerPage() {
        return 28; // 7 columns x 4 rows
    }
}
