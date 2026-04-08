package ua.favn.baseManager.config;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;
import ua.favn.baseManager.base.util.FormatUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Manages message templates from messages.yml.
 */
public class MessageManager extends Base {

    private File messagesFile;
    private FileConfiguration messagesConfig;
    private String prefix;

    public MessageManager(BaseManager plugin) {
        super(plugin);
        loadConfig();
    }

    private void loadConfig() {
        this.messagesFile = new File(getPlugin().getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            getPlugin().saveResource("messages.yml", false);
        }

        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Load defaults from JAR
        InputStream defaultStream = getPlugin().getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            messagesConfig.setDefaults(defaultConfig);
        }

        this.prefix = messagesConfig.getString("prefix", "&6[Base]&r ");
    }

    public void reload() {
        loadConfig();
        getPlugin().getLogger().info("Messages configuration reloaded.");
    }

    /**
     * Gets a raw message string from config.
     */
    public String getRaw(String key) {
        return messagesConfig.getString(key, "&cMessage not found: " + key);
    }

    /**
     * Gets a formatted message component.
     */
    public Component get(String key, FormatUtil.Format... formats) {
        String raw = getRaw(key);
        return FormatUtil.formatComponent(raw, formats);
    }

    /**
     * Sends a prefixed message to a command sender.
     */
    public void send(CommandSender sender, String key, FormatUtil.Format... formats) {
        Component prefixComponent = FormatUtil.formatComponent(prefix);
        Component message = get(key, formats);
        sender.sendMessage(prefixComponent.append(message));
    }

    /**
     * Sends a raw component message (no prefix).
     */
    public void sendRaw(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    public String getPrefix() {
        return this.prefix;
    }

    /**
     * Gets the raw configuration for direct access.
     */
    public FileConfiguration getConfig() {
        return this.messagesConfig;
    }
}
