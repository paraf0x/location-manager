package ua.favn.baseManager.base.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;

public class ItemBuilder {
   private final Material material;
   private String itemId;
   private int durability;
   private Component displayName;
   private List<Component> lore;
   private int customModelData;
   private ItemFlag[] itemFlags;
   private int maxStackSize;
   private PersistentDataContainer data;
   public static ItemStack EMPTY = buildItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, Component.empty());
   public static ItemStack OUTLINE = buildItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());

   public ItemBuilder(Material material) {
      this.material = material;
   }

   public ItemBuilder(ItemStack item) {
      this.material = item.getType();
      if (item.hasItemMeta()) {
         this.data = item.getItemMeta().getPersistentDataContainer();
         if (item.getItemMeta() instanceof Damageable damageable) {
            this.durability = damageable.getDamage();
         }

         if (item.getItemMeta().hasDisplayName()) {
            this.displayName = item.getItemMeta().displayName();
         }

         if (item.getItemMeta().hasLore()) {
            this.lore = item.getItemMeta().lore();
         }

         if (item.getItemMeta().hasCustomModelData()) {
            this.customModelData = item.getItemMeta().getCustomModelData();
         }

         this.itemFlags = item.getItemFlags().toArray(new ItemFlag[0]);
         if (item.getItemMeta().hasMaxStackSize()) {
            this.maxStackSize = item.getItemMeta().getMaxStackSize();
         }
      }
   }

   public ItemStack build() {
      ItemStack item = new ItemStack(this.material);
      ItemMeta meta = item.getItemMeta();
      if (this.customModelData != 0) {
         meta.setCustomModelData(this.customModelData);
      }

      if (this.lore != null && !this.lore.isEmpty()) {
         meta.lore(this.lore);
      }

      if (this.itemFlags != null && this.itemFlags.length > 0) {
         meta.addItemFlags(this.itemFlags);
      }

      if (this.maxStackSize > 0) {
         meta.setMaxStackSize(this.maxStackSize);
      }

      if (meta instanceof Damageable damageable && this.durability > 0) {
         damageable.setDamage(this.durability);
      }

      meta.displayName(this.displayName);
      if (this.data != null) {
         this.data.copyTo(item.getItemMeta().getPersistentDataContainer(), true);
      }

      item.setItemMeta(meta);
      return item;
   }

   public ItemBuilder itemId(String itemId) {
      this.itemId = itemId;
      return this;
   }

   public ItemBuilder displayName(Component name) {
      this.displayName = FormatUtil.normalize(name);
      return this;
   }

   public ItemBuilder displayName(String name) {
      this.displayName = FormatUtil.formatComponent(name);
      return this;
   }

   public ItemBuilder maxStackSize(int maxStackSize) {
      this.maxStackSize = maxStackSize;
      return this;
   }

   public ItemBuilder lore(List<Component> lore) {
      this.lore = lore;
      return this;
   }

   public ItemBuilder loreAddStart(Component... lore) {
      return this.loreAddStart(true, lore);
   }

   public ItemBuilder loreAddStart(boolean normalize, Component... lore) {
      if (this.lore == null) {
         this.lore = new ArrayList<>();
      }

      if (normalize) {
         this.lore.addAll(0, Arrays.stream(lore).map(FormatUtil::normalize).toList());
      } else {
         this.lore.addAll(0, Arrays.asList(lore));
      }

      return this;
   }

   public ItemBuilder loreAddAll(List<Component> lore) {
      this.lore.addAll(lore);
      return this;
   }

   public ItemBuilder loreAdd(Component... lore) {
      return this.loreAdd(true, lore);
   }

   public ItemBuilder loreAdd(boolean normalize, Component... lore) {
      if (this.lore == null) {
         this.lore = new ArrayList<>();
      }

      if (normalize) {
         this.lore.addAll(Arrays.stream(lore).map(FormatUtil::normalize).toList());
      } else {
         this.lore.addAll(Arrays.asList(lore));
      }

      return this;
   }

   public ItemBuilder loreAdd(String... lore) {
      if (this.lore == null) {
         this.lore = new ArrayList<>();
      }

      this.lore.addAll(Arrays.stream(lore).map(FormatUtil::formatComponent).toList());
      return this;
   }

   public ItemBuilder customModelData(int customModelData) {
      this.customModelData = customModelData;
      return this;
   }

   public ItemBuilder hideFlags(ItemFlag... flags) {
      this.itemFlags = flags;
      return this;
   }

   public void formatAll(FormatUtil.Format... formats) {
      for (FormatUtil.Format format : formats) {
         this.displayName = FormatUtil.replace(this.displayName, format);
         this.lore = this.lore.stream().map(entry -> FormatUtil.replace(entry, format)).toList();
      }
   }

   public static ItemStack air() {
      return new ItemStack(Material.AIR);
   }

   public static ItemStack emptyInventoryItem() {
      return buildItem(Material.CYAN_STAINED_GLASS_PANE, Component.empty());
   }

   public static ItemStack buildSkullItem(PlayerProfile profile, Component displayName, List<Component> lore) {
      ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta skullMeta = (SkullMeta)skull.getItemMeta();
      skullMeta.setPlayerProfile(profile);
      skullMeta.displayName(FormatUtil.normalize(displayName));
      skullMeta.lore(lore.stream().map(FormatUtil::normalize).toList());
      skull.setItemMeta(skullMeta);
      return skull;
   }

   public static ItemStack buildSkullItem(Component displayName, String textures) {
      ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)itemStack.getItemMeta();
      PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
      profile.setProperty(new ProfileProperty("textures", textures));
      meta.setPlayerProfile(profile);
      meta.displayName(FormatUtil.normalize(displayName));
      itemStack.setItemMeta(meta);
      return itemStack;
   }

   public static ItemStack buildSkullItem(Component displayName, List<Component> lore, String textures) {
      ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)itemStack.getItemMeta();
      PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
      profile.setProperty(new ProfileProperty("textures", textures));
      meta.setPlayerProfile(profile);
      meta.displayName(FormatUtil.normalize(displayName));
      meta.lore(lore);
      itemStack.setItemMeta(meta);
      return itemStack;
   }

   public static ItemStack buildSkullItem(ItemStack itemStack, String textures) {
      SkullMeta meta = (SkullMeta)itemStack.getItemMeta();
      PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
      profile.setProperty(new ProfileProperty("textures", textures));
      meta.setPlayerProfile(profile);
      itemStack.setItemMeta(meta);
      return itemStack;
   }

   public static ItemStack buildItem(Material material, Component displayName, List<Component> lore, int customModelData) {
      ItemBuilder builder = new ItemBuilder(material);
      builder.displayName(FormatUtil.normalize(displayName)).customModelData(customModelData).hideFlags(ItemFlag.values()).lore(lore);
      return builder.build();
   }

   public static ItemStack buildItem(Material material, Component displayName, List<Component> lore) {
      ItemBuilder builder = new ItemBuilder(material);
      builder.displayName(FormatUtil.normalize(displayName)).hideFlags(ItemFlag.values()).lore(lore);
      return builder.build();
   }

   public static ItemStack buildItem(Material material, Component displayName, int customModelData) {
      ItemBuilder builder = new ItemBuilder(material);
      builder.displayName(FormatUtil.normalize(displayName)).customModelData(customModelData).hideFlags(ItemFlag.values());
      return builder.build();
   }

   public static ItemStack buildItem(Material material, Component displayName) {
      ItemBuilder builder = new ItemBuilder(material);
      builder.displayName(FormatUtil.normalize(displayName)).hideFlags(ItemFlag.values());
      return builder.build();
   }

   public static ItemStack buildGUIItem(Material type, Component displayName) {
      ItemBuilder builder = new ItemBuilder(type);
      builder.displayName(FormatUtil.normalize(displayName)).hideFlags(ItemFlag.values()).maxStackSize(1);
      return builder.build();
   }

   public static ItemStack buildGUIItem(Material type, Component displayName, List<Component> lore) {
      ItemBuilder builder = new ItemBuilder(type);
      builder.displayName(FormatUtil.normalize(displayName))
         .maxStackSize(1)
         .hideFlags(ItemFlag.values())
         .lore(lore.stream().map(FormatUtil::normalize).toList());
      return builder.build();
   }

   public static ItemStack buildGUIItem(Component displayName, int customModelData) {
      ItemBuilder builder = new ItemBuilder(Material.ACACIA_SIGN);
      builder.displayName(FormatUtil.normalize(displayName)).maxStackSize(1).customModelData(customModelData);
      return builder.build();
   }
}
