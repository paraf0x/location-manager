package ua.favn.baseManager.base.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import ua.favn.baseManager.base.util.FormatUtil;
import ua.favn.baseManager.base.util.ItemBuilder;

public record GuiItem(ItemStack icon, int slotX, int slotY) {
   public static GuiItem parse(ConfigurationSection section, FormatUtil.Format... format) {
      return new GuiItem(parseItemStack(section, format), section.getInt("slot.x", 1), section.getInt("slot.y", 1));
   }

   public static ItemStack parseItemStack(ConfigurationSection section, FormatUtil.Format... format) {
      return Material.valueOf(section.getString("type")).equals(Material.PLAYER_HEAD)
         ? ItemBuilder.buildSkullItem(
            FormatUtil.formatComponent(section.getString("name"), format),
            section.getStringList("lore").stream().map(string -> FormatUtil.formatComponent(string, format)).toList(),
            section.getString("texture")
         )
         : ItemBuilder.buildGUIItem(
            Material.valueOf(section.getString("type")),
            FormatUtil.formatComponent(section.getString("name"), format),
            section.getStringList("lore").stream().map(string -> FormatUtil.formatComponent(string, format)).toList()
         );
   }
}
