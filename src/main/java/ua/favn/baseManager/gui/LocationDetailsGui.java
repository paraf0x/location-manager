package ua.favn.baseManager.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
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
    private Map<UUID, String> memberNames = new LinkedHashMap<>();

    public LocationDetailsGui(BaseManager plugin, Player player, SavedLocation location) {
        super(plugin);
        this.player = player;
        this.location = location;
    }

    /**
     * Opens the details GUI with member names loaded from database.
     */
    public static void openAsync(BaseManager plugin, Player player, SavedLocation location) {
        LocationDetailsGui gui = new LocationDetailsGui(plugin, player, location);
        gui.memberNames = plugin.getLocationManager().getMemberManager().getMembers(location.id());
        gui.open(player);
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
            this.getSlot(5, 6)
        ));

        // Members section (row 4)
        List<UUID> memberList = new ArrayList<>(memberNames.keySet());

        if (!memberList.isEmpty()) {
            // Members label
            this.setItem(1, 4, createSimpleItem(Material.OAK_SIGN,
                Component.text("Members (" + memberList.size() + ")", Colors.GOLD)));
        }

        int maxDisplay = Math.min(memberList.size(), 7);
        for (int i = 0; i < maxDisplay; i++) {
            UUID memberUUID = memberList.get(i);
            String memberName = memberNames.get(memberUUID);

            List<Component> skullLore = new ArrayList<>();
            skullLore.add(FormatUtil.normalize(Component.text("Member", Colors.SILVER)));
            if (player.hasPermission("basemanager.admin")) {
                skullLore.add(Component.empty());
                skullLore.add(FormatUtil.normalize(Component.text("Shift+Click to remove", Colors.RED)));
            }

            ItemStack skull = createPlayerHead(memberName, skullLore);

            final UUID capturedUUID = memberUUID;
            final String capturedName = memberName;
            this.registerTempButton(new GuiButton(
                skull,
                (GuiButton.CompleteButtonAction) (clicker, actionInfo) -> {
                    if (actionInfo.click() != null && actionInfo.click().isShiftClick()
                            && player.hasPermission("basemanager.admin")) {
                        getPlugin().getLocationManager().getMemberManager()
                            .removeMember(location.id(), capturedUUID);
                        getPlugin().getMessageManager().send(player, "commands.member-removed",
                            new FormatUtil.Format("{player}", capturedName),
                            new FormatUtil.Format("{tag}", location.tag()),
                            new FormatUtil.Format("{name}", location.name()));
                        // Rebuild to reflect change
                        this.build();
                    }
                    return GuiButton.ActionResult.EMPTY;
                },
                this.getSlot(i + 2, 4)
            ));
        }

        // "View All" arrow if more than 7 members
        if (memberList.size() > 7) {
            this.registerTempButton(new GuiButton(
                createSimpleItem(Material.ARROW,
                    Component.text("View All Members", Colors.GOLD),
                    Component.text(memberList.size() + " total members", Colors.SILVER)),
                clicker -> getPlugin().getGuiManager().openMembersList(player, location),
                this.getSlot(9, 4)
            ));
        }

        // Back button
        this.registerTempButton(new GuiButton(
            createSimpleItem(Material.ARROW,
                Component.text("Back", Colors.WHITE),
                Component.text("Return to location browser", Colors.SILVER)),
            clicker -> getPlugin().getGuiManager().openLocationBrowser(player),
            this.getSlot(1, 6)
        ));
    }

    private ItemStack createPlayerHead(String name, List<Component> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        // Use name-based lookup - works with both online and offline mode
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        meta.setOwningPlayer(offlinePlayer);
        meta.displayName(FormatUtil.normalize(Component.text(name, Colors.GOLD)));
        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
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
        return 6;
    }
}
