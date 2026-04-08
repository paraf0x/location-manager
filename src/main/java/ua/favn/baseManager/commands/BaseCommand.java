package ua.favn.baseManager.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.lucko.commodore.Commodore;
import me.lucko.commodore.CommodoreProvider;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.commands.TabCompleteCommand;
import ua.favn.baseManager.base.util.Colors;
import ua.favn.baseManager.base.util.FormatUtil;
import ua.favn.baseManager.base.util.ItemStackSerializer;
import ua.favn.baseManager.base.util.Predicate;
import ua.favn.baseManager.location.MemberManager;
import ua.favn.baseManager.location.SavedLocation;

/**
 * Main command handler for /loc command.
 * GUI is available to all players. Subcommands require basemanager.admin (default: op).
 */
public class BaseCommand extends TabCompleteCommand {

    private static final String ADMIN_PERMISSION = "basemanager.admin";

    public BaseCommand(BaseManager plugin) {
        super(plugin);
        registerCompletions(CommodoreProvider.getCommodore(this.getPlugin()), this.getCommand());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!", Colors.RED));
            return true;
        }

        // No args or "gui" subcommand = open GUI (available to all)
        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            getPlugin().getGuiManager().openLocationBrowser(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        // All subcommands except gui and share require admin permission
        if (!subcommand.equals("gui") && !subcommand.equals("menu")
                && !subcommand.equals("share")
                && !player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(Component.text("You don't have permission!", Colors.RED));
            return true;
        }

        switch (subcommand) {
            case "save" -> handleSave(player, args);
            case "delete" -> handleDelete(player, args);
            case "list" -> handleList(player, args);
            case "compass" -> handleCompass(player, args);
            case "icon" -> handleIcon(player, args);
            case "member" -> handleMember(player, args);
            case "share" -> handleShare(player, args);
            case "reload" -> handleReload(player);
            default -> {
                player.sendMessage(Component.text("Unknown subcommand. Use /loc for GUI.", Colors.RED));
            }
        }

        return true;
    }

    private void handleSave(Player player, String[] args) {
        if (Predicate.check(args.length < 3, player, "Usage: /loc save <tag> <name>")) {
            return;
        }

        String tag = args[1].toUpperCase();
        String name = args[2];

        if (Predicate.check(name.length() < 2 || name.length() > 32, player,
            "Location name should be between 2 and 32 characters")) {
            return;
        }

        String currentWorld = player.getWorld().getName();

        // Check if location with this tag+name already exists (any owner)
        SavedLocation existing = getPlugin().getLocationManager().getByTagAndName(tag, name);

        if (existing != null) {
            // Location exists - add/update coords for current dimension
            existing.setCoords(currentWorld, player.getLocation().getBlockX(),
                player.getLocation().getBlockY(), player.getLocation().getBlockZ());
            existing.saveCoords(currentWorld);
            getPlugin().getMessageManager().send(player, "commands.location-coords-updated",
                new FormatUtil.Format("{tag}", tag),
                new FormatUtil.Format("{name}", name),
                new FormatUtil.Format("{world}", currentWorld));
        } else {
            // Create new location
            SavedLocation loc = getPlugin().getLocationManager()
                .create(player.getUniqueId(), tag, name, player.getLocation());
            getPlugin().getMessageManager().send(player, "commands.location-saved",
                new FormatUtil.Format("{tag}", tag),
                new FormatUtil.Format("{name}", name),
                new FormatUtil.Format("{location}", loc.coordsString()));
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (Predicate.check(args.length < 3, player, "Usage: /loc delete <tag> <name>")) {
            return;
        }

        String tag = args[1].toUpperCase();
        String name = args[2];
        SavedLocation loc = getPlugin().getLocationManager().getByTagAndName(tag, name);
        if (Predicate.check(loc == null, player, "Location not found: " + tag + ":" + name)) {
            return;
        }

        loc.delete();
        getPlugin().getMessageManager().send(player, "commands.location-deleted",
            new FormatUtil.Format("{tag}", tag),
            new FormatUtil.Format("{name}", name));
    }

    private void handleList(Player player, String[] args) {
        List<SavedLocation> locations = getPlugin().getLocationManager().getAll();

        if (locations.isEmpty()) {
            player.sendMessage(Component.text("No saved locations.", Colors.SILVER));
            return;
        }

        player.sendMessage(Component.text("All saved locations:", Colors.SILVER));
        player.sendMessage(Component.text("-----------------------------------------------------", Colors.SILVER));

        for (SavedLocation loc : locations) {
            String worlds = String.join(", ", loc.getWorlds());
            player.sendMessage(Component.text("- ", Colors.WHITE)
                .append(Component.text("[" + loc.tag() + "] ", Colors.LIGHT_GREEN))
                .append(Component.text(loc.name(), Colors.GOLD))
                .append(Component.text(" (" + worlds + ")", Colors.WHITE)));
        }

        player.sendMessage(Component.text("-----------------------------------------------------", Colors.SILVER));
    }

    private void handleCompass(Player player, String[] args) {
        if (Predicate.check(args.length < 3, player, "Usage: /loc compass <tag> <name>")) {
            return;
        }

        String tag = args[1].toUpperCase();
        String name = args[2];
        SavedLocation loc = getPlugin().getLocationManager().getByTagAndName(tag, name);
        if (Predicate.check(loc == null, player, "Location not found: " + tag + ":" + name)) {
            return;
        }

        ItemStack compass = getPlugin().getCompassManager().createCompass(loc, player);
        player.getInventory().addItem(compass);
        getPlugin().getCompassListener().tryStartActionBar(player);
        getPlugin().getMessageManager().send(player, "commands.compass-given",
            new FormatUtil.Format("{tag}", loc.tag()),
            new FormatUtil.Format("{name}", loc.name()));
    }

    private void handleIcon(Player player, String[] args) {
        if (Predicate.check(args.length < 4, player, "Usage: /loc icon <tag> <name> <material>")) {
            return;
        }

        String tag = args[1].toUpperCase();
        String name = args[2];
        String materialName = args[3].toUpperCase();

        SavedLocation loc = getPlugin().getLocationManager().getByTagAndName(tag, name);
        if (Predicate.check(loc == null, player, "Location not found: " + tag + ":" + name)) {
            return;
        }

        try {
            Material mat = Material.valueOf(materialName);
            // Serialize a simple ItemStack as icon
            loc.icon(ItemStackSerializer.serialize(new ItemStack(mat)));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid material: " + materialName, Colors.RED));
            return;
        }

        loc.save();
        getPlugin().getMessageManager().send(player, "commands.icon-changed",
            new FormatUtil.Format("{name}", loc.displayName()),
            new FormatUtil.Format("{icon}", materialName));
    }

    private void handleMember(Player player, String[] args) {
        if (Predicate.check(args.length < 5, player,
            "Usage: /loc member <add|remove> <tag> <name> <player>")) {
            return;
        }

        String action = args[1].toLowerCase();
        String tag = args[2].toUpperCase();
        String name = args[3];
        String targetName = args[4];

        SavedLocation loc = getPlugin().getLocationManager().getByTagAndName(tag, name);
        if (Predicate.check(loc == null, player, "Location not found: " + tag + ":" + name)) {
            return;
        }

        MemberManager memberManager = getPlugin().getLocationManager().getMemberManager();

        // Resolve player async via Mojang profile API
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            com.destroystokyo.paper.profile.PlayerProfile profile =
                Bukkit.createProfile(targetName);
            profile.complete(true);

            // Use profile UUID if resolved, otherwise fall back to offline UUID
            @SuppressWarnings("deprecation")
            UUID targetUUID = profile.getId() != null
                ? profile.getId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
            String resolvedName = profile.getName() != null ? profile.getName() : targetName;

            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                switch (action) {
                    case "add" -> {
                        memberManager.addMember(loc.id(), targetUUID, resolvedName);
                        getPlugin().getMessageManager().send(player, "commands.member-added",
                            new FormatUtil.Format("{player}", resolvedName),
                            new FormatUtil.Format("{tag}", tag),
                            new FormatUtil.Format("{name}", name));
                    }
                    case "remove" -> {
                        boolean removed = memberManager.removeMember(loc.id(), targetUUID);
                        if (removed) {
                            getPlugin().getMessageManager().send(player, "commands.member-removed",
                                new FormatUtil.Format("{player}", resolvedName),
                                new FormatUtil.Format("{tag}", tag),
                                new FormatUtil.Format("{name}", name));
                        } else {
                            player.sendMessage(Component.text(
                                resolvedName + " is not a member of this location.", Colors.RED));
                        }
                    }
                    default -> player.sendMessage(Component.text(
                        "Usage: /loc member <add|remove> <tag> <name> <player>", Colors.RED));
                }
            });
        });
    }

    private void handleShare(Player player, String[] args) {
        if (Predicate.check(args.length < 4, player, "Usage: /loc share <tag> <name> <player>")) {
            return;
        }

        String tag = args[1].toUpperCase();
        String name = args[2];
        String targetName = args[3];

        SavedLocation loc = getPlugin().getLocationManager().getByTagAndName(tag, name);
        if (Predicate.check(loc == null, player, "Location not found: " + tag + ":" + name)) {
            return;
        }

        if (!getPlugin().getLocationManager().canAccess(loc, player.getUniqueId())
                && !player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(Component.text("You don't have access to this location!", Colors.RED));
            return;
        }

        Player recipient = Bukkit.getPlayerExact(targetName);
        if (Predicate.check(recipient == null, player, "Player not found: " + targetName)) {
            return;
        }

        int locationId = loc.id();
        Component hoverText = Component.text(loc.displayName(), Colors.GOLD)
            .append(Component.newline())
            .append(Component.text(loc.coordsString(), Colors.SILVER))
            .append(Component.newline())
            .append(Component.text("Click to receive a tracking compass", Colors.LIGHT_GREEN));

        Component clickable = Component.text("[Click for Compass]", Colors.LIGHT_GREEN)
            .hoverEvent(HoverEvent.showText(hoverText))
            .clickEvent(ClickEvent.callback(audience -> {
                if (!(audience instanceof Player clicker)) {
                    return;
                }
                SavedLocation target = getPlugin().getLocationManager().get(locationId);
                if (target == null) {
                    clicker.sendMessage(Component.text("This location no longer exists!", Colors.RED));
                    return;
                }
                ItemStack compass = getPlugin().getCompassManager().createCompass(target, clicker);
                clicker.getInventory().addItem(compass);
                getPlugin().getCompassListener().tryStartActionBar(clicker);
                getPlugin().getMessageManager().send(clicker, "commands.compass-given",
                    new FormatUtil.Format("{tag}", target.tag()),
                    new FormatUtil.Format("{name}", target.name()));
            }, ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(Duration.ofHours(1))
                .build()));

        Component message = getPlugin().getMessageManager().get("commands.location-shared",
            new FormatUtil.Format("{player}", player.getName()),
            new FormatUtil.Format("{tag}", tag),
            new FormatUtil.Format("{name}", name));

        Component fullMessage = message.append(Component.space()).append(clickable);
        getPlugin().getMessageManager().sendRaw(recipient, fullMessage);
        getPlugin().getMessageManager().send(player, "commands.location-share-sent",
            new FormatUtil.Format("{player}", recipient.getName()),
            new FormatUtil.Format("{tag}", tag),
            new FormatUtil.Format("{name}", name));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("basemanager.reload")) {
            player.sendMessage(Component.text("You don't have permission to reload the config!", Colors.RED));
            return;
        }
        getPlugin().reloadConfigs();
        player.sendMessage(Component.text("BaseManager configuration reloaded!", Colors.LIGHT_GREEN));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(List.of("gui", "menu", "share"));
            if (player.hasPermission(ADMIN_PERMISSION)) {
                subcommands.addAll(List.of("save", "delete", "list", "compass", "icon", "member"));
            }
            if (player.hasPermission("basemanager.reload")) {
                subcommands.add("reload");
            }
            return subcommands.stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .toList();
        }

        String sub = args[0].toLowerCase();

        // Share tab completion is available to all players
        if (sub.equals("share")) {
            if (args.length == 2) {
                return getPlugin().getLocationManager().getAll().stream()
                    .map(SavedLocation::tag)
                    .distinct()
                    .filter(t -> t.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
            }
            if (args.length == 3) {
                String tag = args[1].toUpperCase();
                return getPlugin().getLocationManager().getAll().stream()
                    .filter(l -> l.tag().equalsIgnoreCase(tag))
                    .map(SavedLocation::name)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
            }
            if (args.length == 4) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
                    .toList();
            }
            return List.of();
        }

        if (!player.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }

        // Member subcommand has shifted positions: member <add|remove> <tag> <name> <player>
        if (sub.equals("member")) {
            if (args.length == 2) {
                return List.of("add", "remove").stream()
                    .filter(a -> a.startsWith(args[1].toLowerCase()))
                    .toList();
            }
            if (args.length == 3) {
                return getPlugin().getLocationManager().getAll().stream()
                    .map(SavedLocation::tag)
                    .distinct()
                    .filter(t -> t.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
            }
            if (args.length == 4) {
                String tag = args[2].toUpperCase();
                return getPlugin().getLocationManager().getAll().stream()
                    .filter(l -> l.tag().equalsIgnoreCase(tag))
                    .map(SavedLocation::name)
                    .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
                    .toList();
            }
            if (args.length == 5) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[4].toLowerCase()))
                    .toList();
            }
            return List.of();
        }

        // arg[1] = tag
        if (args.length == 2 && needsTag(sub)) {
            return getPlugin().getLocationManager().getAll().stream()
                .map(SavedLocation::tag)
                .distinct()
                .filter(t -> t.toLowerCase().startsWith(args[1].toLowerCase()))
                .toList();
        }

        // arg[2] = name
        if (args.length == 3 && needsTag(sub)) {
            String tag = args[1].toUpperCase();
            return getPlugin().getLocationManager().getAll().stream()
                .filter(l -> l.tag().equalsIgnoreCase(tag))
                .map(SavedLocation::name)
                .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                .toList();
        }

        return List.of();
    }

    private boolean needsTag(String sub) {
        return sub.equals("save") || sub.equals("delete")
            || sub.equals("compass") || sub.equals("icon");
    }

    @Override
    protected String getLabel() {
        return "loc";
    }

    private static void registerCompletions(Commodore commodore, Command command) {
        LiteralCommandNode<?> baseCommand = LiteralArgumentBuilder.literal("loc")
            .then(LiteralArgumentBuilder.literal("save")
                .then(RequiredArgumentBuilder.argument("tag", StringArgumentType.string())
                    .then(RequiredArgumentBuilder.argument("name", StringArgumentType.string()))))
            .then(LiteralArgumentBuilder.literal("delete")
                .then(RequiredArgumentBuilder.argument("tag", StringArgumentType.string())
                    .then(RequiredArgumentBuilder.argument("name", StringArgumentType.string()))))
            .then(LiteralArgumentBuilder.literal("list"))
            .then(LiteralArgumentBuilder.literal("compass")
                .then(RequiredArgumentBuilder.argument("tag", StringArgumentType.string())
                    .then(RequiredArgumentBuilder.argument("name", StringArgumentType.string()))))
            .then(LiteralArgumentBuilder.literal("icon")
                .then(RequiredArgumentBuilder.argument("tag", StringArgumentType.string())
                    .then(RequiredArgumentBuilder.argument("name", StringArgumentType.string())
                        .then(RequiredArgumentBuilder.argument("material", StringArgumentType.string())))))
            .then(LiteralArgumentBuilder.literal("member")
                .then(LiteralArgumentBuilder.literal("add")
                    .then(RequiredArgumentBuilder.argument("tag", StringArgumentType.string())
                        .then(RequiredArgumentBuilder.argument("name", StringArgumentType.string())
                            .then(RequiredArgumentBuilder.argument("player", StringArgumentType.string())))))
                .then(LiteralArgumentBuilder.literal("remove")
                    .then(RequiredArgumentBuilder.argument("tag", StringArgumentType.string())
                        .then(RequiredArgumentBuilder.argument("name", StringArgumentType.string())
                            .then(RequiredArgumentBuilder.argument("player", StringArgumentType.string()))))))
            .then(LiteralArgumentBuilder.literal("share")
                .then(RequiredArgumentBuilder.argument("tag", StringArgumentType.string())
                    .then(RequiredArgumentBuilder.argument("name", StringArgumentType.string())
                        .then(RequiredArgumentBuilder.argument("player", StringArgumentType.string())))))
            .then(LiteralArgumentBuilder.literal("gui"))
            .then(LiteralArgumentBuilder.literal("menu"))
            .build();

        commodore.register(command, baseCommand);
        Bukkit.getLogger().info("[BaseManager] Registered command completions");
    }
}
