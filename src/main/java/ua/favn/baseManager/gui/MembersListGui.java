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
import ua.favn.baseManager.base.gui.PagedGuiInventory;
import ua.favn.baseManager.base.util.Colors;
import ua.favn.baseManager.base.util.FormatUtil;
import ua.favn.baseManager.location.SavedLocation;

/**
 * Paged GUI for viewing all members of a location.
 */
public class MembersListGui extends PagedGuiInventory<UUID> {
    private final Player player;
    private final SavedLocation location;
    private Map<UUID, String> memberNames;

    private MembersListGui(BaseManager plugin, Player player, SavedLocation location,
                           List<UUID> members, Map<UUID, String> names) {
        super(plugin, members);
        this.player = player;
        this.location = location;
        this.memberNames = names;
    }

    /**
     * Opens the members list GUI with member names from database.
     */
    public static void openAsync(BaseManager plugin, Player player, SavedLocation location) {
        Map<UUID, String> names = plugin.getLocationManager().getMemberManager().getMembers(location.id());
        MembersListGui gui = new MembersListGui(plugin, player, location,
            new ArrayList<>(names.keySet()), names);
        gui.open(player);
    }

    @Override
    public void compile() {
        // Background and outline
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        bg.editMeta(meta -> meta.setHideTooltip(true));
        this.setBackground(bg);
        ItemStack ol = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ol.editMeta(meta -> meta.setHideTooltip(true));
        this.setOutline(ol, OutlineType.BOTTOM);

        // Page navigation
        int navY = getHeight();
        if (this.getPage() > 0) {
            this.registerTempButton(new GuiButton(
                createSimpleItem(Material.ARROW, Component.text("Previous Page", Colors.GOLD)),
                clicker -> this.setPage(this.getPage() - 1, true),
                this.getSlot(1, navY)
            ));
        }

        if (this.getPage() < this.getLastPage()) {
            this.registerTempButton(new GuiButton(
                createSimpleItem(Material.ARROW, Component.text("Next Page", Colors.GOLD)),
                clicker -> this.setPage(this.getPage() + 1, true),
                this.getSlot(9, navY)
            ));
        }

        // Page indicator
        this.setItem(8, navY, createSimpleItem(Material.PAPER,
            Component.text("Page " + (getPage() + 1) + "/" + (getLastPage() + 1), Colors.SILVER)));

        // Back button
        this.registerTempButton(new GuiButton(
            createSimpleItem(Material.ARROW,
                Component.text("Back", Colors.WHITE),
                Component.text("Return to location details", Colors.SILVER)),
            clicker -> getPlugin().getGuiManager().openLocationDetails(player, location),
            this.getSlot(1, navY)
        ));

        // Member heads
        List<UUID> membersOnPage = this.getSubjectsInPage();
        boolean isAdmin = player.hasPermission("basemanager.admin");

        for (int i = 0; i < membersOnPage.size(); i++) {
            UUID memberUUID = membersOnPage.get(i);
            String memberName = memberNames.getOrDefault(memberUUID,
                memberUUID.toString().substring(0, 8));

            List<Component> lore = new ArrayList<>();
            lore.add(FormatUtil.normalize(Component.text("Member", Colors.SILVER)));
            if (isAdmin) {
                lore.add(Component.empty());
                lore.add(FormatUtil.normalize(Component.text("Shift+Click to remove", Colors.RED)));
            }

            ItemStack skull = createPlayerHead(memberName, lore);

            int x = (i % 7) + 2; // Columns 2-8
            int y = (i / 7) + 1; // Rows 1-4

            final UUID capturedUUID = memberUUID;
            final String capturedName = memberName;
            this.registerTempButton(new GuiButton(
                skull,
                (GuiButton.CompleteButtonAction) (clicker, actionInfo) -> {
                    if (actionInfo.click() != null && actionInfo.click().isShiftClick() && isAdmin) {
                        getPlugin().getLocationManager().getMemberManager()
                            .removeMember(location.id(), capturedUUID);
                        getPlugin().getMessageManager().send(player, "commands.member-removed",
                            new FormatUtil.Format("{player}", capturedName),
                            new FormatUtil.Format("{tag}", location.tag()),
                            new FormatUtil.Format("{name}", location.name()));
                        // Re-open with fresh data
                        openAsync(getPlugin(), player, location);
                    }
                    return GuiButton.ActionResult.EMPTY;
                },
                this.getSlot(x, y)
            ));
        }

        // Empty state
        if (this.subjects.isEmpty()) {
            this.setItem(5, 3, createSimpleItem(Material.BARRIER,
                Component.text("No members", Colors.RED),
                Component.text("Use /loc member add to add members", Colors.SILVER)));
        }
    }

    private ItemStack createPlayerHead(String name, List<Component> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        meta.setOwningPlayer(offlinePlayer);
        meta.displayName(FormatUtil.normalize(Component.text(name, Colors.GOLD)));
        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
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
        return Component.text("Members: " + location.displayName(), Colors.GOLD);
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
