package ua.favn.baseManager.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.gui.GuiButton;
import ua.favn.baseManager.base.gui.GuiInventory;
import ua.favn.baseManager.base.util.Colors;
import ua.favn.baseManager.base.util.DateUtil;
import ua.favn.baseManager.base.util.FormatUtil;
import ua.favn.baseManager.location.SavedLocation;

/**
 * GUI showing details of a specific location.
 */
public class LocationDetailsGui extends GuiInventory {
    private final Player player;
    private final SavedLocation location;

    public LocationDetailsGui(BaseManager plugin, Player player, SavedLocation location) {
        super(plugin);
        this.player = player;
        this.location = location;
    }

    @Override
    public void compile() {
        // Background
        ItemStack bg = createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        this.setBackground(bg);

        // Location info (center)
        ItemStack info = createLocationInfoItem();
        this.setItem(5, 2, info);

        // Get compass button
        this.registerTempButton(new GuiButton(
            createSimpleItem(Material.COMPASS,
                Component.text("Get Compass", Colors.GOLD),
                Component.text("Click to get a tracking compass", Colors.SILVER)),
            clicker -> {
                ItemStack compass = getPlugin().getCompassManager().createCompass(location, player);
                player.getInventory().addItem(compass);
                getPlugin().getCompassListener().tryStartActionBar(player);
                player.closeInventory();
                getPlugin().getMessageManager().send(player, "commands.compass-given",
                    new FormatUtil.Format("{tag}", location.tag()),
                    new FormatUtil.Format("{name}", location.name()));
            },
            this.getSlot(4, 3)
        ));

        // Delete button (available to all)
        this.registerTempButton(new GuiButton(
            createSimpleItem(Material.BARRIER,
                Component.text("Delete Location", Colors.RED),
                Component.text("Shift+Click to delete", Colors.SILVER)),
            (GuiButton.CompleteButtonAction) (clicker, actionInfo) -> {
                if (actionInfo.click() != null && actionInfo.click().isShiftClick()) {
                    location.delete();
                    player.closeInventory();
                    getPlugin().getMessageManager().send(player, "commands.location-deleted",
                        new FormatUtil.Format("{tag}", location.tag()),
                        new FormatUtil.Format("{name}", location.name()));
                }
                return GuiButton.ActionResult.EMPTY;
            },
            this.getSlot(6, 3)
        ));

        // Back button
        this.registerTempButton(new GuiButton(
            createSimpleItem(Material.ARROW,
                Component.text("Back", Colors.WHITE),
                Component.text("Return to location browser", Colors.SILVER)),
            clicker -> getPlugin().getGuiManager().openLocationBrowser(player),
            this.getSlot(1, 5)
        ));
    }

    private ItemStack createLocationInfoItem() {
        // Use custom icon if available
        ItemStack iconItem = location.iconItem();
        ItemStack item;

        if (iconItem != null) {
            item = iconItem.clone();
            item.setAmount(1);
        } else {
            item = new ItemStack(Material.LODESTONE);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Tag: ", Colors.WHITE)
            .append(Component.text(location.tag(), Colors.LIGHT_GREEN)));

        // Show coordinates for all dimensions
        for (String world : location.getWorlds()) {
            lore.add(Component.text(world + ": ", Colors.WHITE)
                .append(Component.text(location.coordsString(world), Colors.SILVER)));
        }

        lore.add(Component.text("Owner: ", Colors.WHITE)
            .append(Component.text(getPlayerName(location.owner()), Colors.SILVER)));
        lore.add(Component.text("Created: ", Colors.WHITE)
            .append(Component.text(DateUtil.formatDate(location.created()), Colors.SILVER)));

        ItemMeta meta = item.getItemMeta();
        meta.displayName(FormatUtil.normalize(Component.text(location.displayName(), Colors.GOLD)));
        meta.lore(lore.stream().map(FormatUtil::normalize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String getPlayerName(UUID uuid) {
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
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
        return Component.text("Location: " + location.displayName(), Colors.GOLD);
    }

    @Override
    public int getHeight() {
        return 5;
    }
}
