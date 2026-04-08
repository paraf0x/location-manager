package ua.favn.baseManager.compass;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.base.util.FormatUtil;

/**
 * Handles tracking compass interactions: disposal, distance display, and auto-dispose.
 */
public class TrackingCompassListener extends Base implements Listener {
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();

    public TrackingCompassListener(BaseManager plugin) {
        super(plugin);
    }

    /**
     * Handle right-click interactions with tracking compass.
     * Sneak + right-click = dispose.
     * Normal right-click = show distance info.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        TrackingCompassManager manager = getPlugin().getCompassManager();

        if (item == null || !manager.isTrackingCompass(item)) {
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);

        // Sneak + right-click = dispose
        if (player.isSneaking()) {
            disposeCompass(player, item);
            return;
        }

        // Normal right-click = show distance info
        Location target = manager.getTargetLocation(item, player);
        String displayName = manager.getDisplayName(item);

        if (target == null) {
            // No base with this name in current dimension
            getPlugin().getMessageManager().send(player, "compass.no-base-here");
            return;
        }

        int distance = (int) player.getLocation().distance(target);
        getPlugin().getMessageManager().send(player, "compass.distance-info",
            new FormatUtil.Format("{distance}", distance),
            new FormatUtil.Format("{location_name}", displayName));
    }

    /**
     * Handle dropping tracking compass - dispose it (vanishes).
     */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        ItemStack item = dropped.getItemStack();
        TrackingCompassManager manager = getPlugin().getCompassManager();

        if (!manager.isTrackingCompass(item)) {
            return;
        }

        // Remove the dropped item and send message
        dropped.remove();
        getPlugin().getMessageManager().send(event.getPlayer(), "compass.disposed");
    }

    /**
     * Handle item held changes - start/stop action bar distance display.
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        TrackingCompassManager manager = getPlugin().getCompassManager();

        // Check the new item being held
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        if (newItem != null && manager.isTrackingCompass(newItem)) {
            if (manager.isShowDistanceActionBar()) {
                startActionBarTask(player);
            }
        } else {
            stopActionBarTask(player);
        }
    }

    /**
     * Handle player changing dimension - update all compass lodestones.
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        TrackingCompassManager manager = getPlugin().getCompassManager();

        // Update all tracking compasses in inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && manager.isTrackingCompass(item)) {
                manager.updateCompassLodestone(item, player);
            }
        }
    }

    /**
     * Handle player movement - auto-dispose when arriving at location.
     * Only auto-disposes if player is in the ORIGIN dimension (where compass was created).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        TrackingCompassManager manager = getPlugin().getCompassManager();

        if (!manager.isAutoDisposeOnArrival()) {
            return;
        }

        // Only check if player actually moved to a new block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        int arrivalRadius = manager.getArrivalRadius();
        String currentWorld = player.getWorld().getName();

        // Check entire inventory for tracking compasses
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && manager.isTrackingCompass(item)) {
                // Only auto-dispose if player is in the origin dimension
                String originWorld = manager.getOriginWorld(item);
                if (originWorld != null && !originWorld.equals(currentWorld)) {
                    continue; // Not in origin dimension, skip auto-dispose
                }

                Location target = manager.getTargetLocation(item, player);
                if (target != null && player.getLocation().distance(target) <= arrivalRadius) {

                    String displayName = manager.getDisplayName(item);
                    player.getInventory().setItem(i, null);
                    getPlugin().getMessageManager().send(player, "compass.arrived",
                        new FormatUtil.Format("{location_name}", displayName));

                    // Stop action bar if this was the held compass
                    if (i == player.getInventory().getHeldItemSlot()) {
                        stopActionBarTask(player);
                    }
                }
            }
        }
    }

    /**
     * Handle player join - start action bar if already holding a compass.
     */
    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        TrackingCompassManager manager = getPlugin().getCompassManager();
        // Delay 1 tick so inventory is fully loaded
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (manager.isTrackingCompass(held) && manager.isShowDistanceActionBar()) {
                startActionBarTask(player);
            }
        }, 1L);
    }

    /**
     * Clean up action bar tasks when player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopActionBarTask(event.getPlayer());
    }

    /**
     * Dispose of a compass item from player's hand.
     */
    private void disposeCompass(Player player, ItemStack compass) {
        // Remove from main hand or off hand
        if (player.getInventory().getItemInMainHand().equals(compass)) {
            player.getInventory().setItemInMainHand(null);
        } else if (player.getInventory().getItemInOffHand().equals(compass)) {
            player.getInventory().setItemInOffHand(null);
        }

        stopActionBarTask(player);
        getPlugin().getMessageManager().send(player, "compass.disposed");
    }

    /**
     * Try to start the action bar if the player is holding a tracking compass.
     * Called when a compass is given to the player.
     */
    public void tryStartActionBar(Player player) {
        TrackingCompassManager manager = getPlugin().getCompassManager();
        if (!manager.isShowDistanceActionBar()) {
            return;
        }
        // Delay 1 tick so the item is in inventory
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (manager.isTrackingCompass(held)) {
                startActionBarTask(player);
            }
        }, 1L);
    }

    /**
     * Start the action bar distance display task for a player.
     */
    private void startActionBarTask(Player player) {
        stopActionBarTask(player);

        TrackingCompassManager manager = getPlugin().getCompassManager();
        int interval = manager.getActionBarUpdateTicks();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!manager.isTrackingCompass(held)) {
                stopActionBarTask(player);
                return;
            }

            Location target = manager.getTargetLocation(held, player);
            String displayName = manager.getDisplayName(held);

            if (target == null) {
                Component message = getPlugin().getMessageManager().get("compass.actionbar-no-base-here");
                player.sendActionBar(message);
                return;
            }

            int distance = (int) player.getLocation().distance(target);
            Component message = getPlugin().getMessageManager().get("compass.actionbar-distance",
                new FormatUtil.Format("{distance}", distance),
                new FormatUtil.Format("{location_name}", displayName));
            player.sendActionBar(message);

        }, 0L, interval);

        actionBarTasks.put(player.getUniqueId(), task);
    }

    /**
     * Stop the action bar distance display task for a player.
     */
    private void stopActionBarTask(Player player) {
        BukkitTask task = actionBarTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
}
