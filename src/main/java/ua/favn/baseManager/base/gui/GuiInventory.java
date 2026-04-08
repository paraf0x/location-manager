package ua.favn.baseManager.base.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;

public abstract class GuiInventory extends Base implements InventoryHolder {
   private Inventory inventory;
   private final List<GuiButton> buttons = new ArrayList<>();
   private final List<GuiButton> tempButtons = new ArrayList<>();
   private ItemStack background;
   private ItemStack outline;
   private GuiInventory.OutlineType outlineType;

   public GuiInventory(BaseManager plugin) {
      super(plugin);
   }

   public abstract void compile();

   public final void build() {
      if (this.inventory == null) {
         this.inventory = Bukkit.createInventory(this, this.getHeight() * 9, this.getTitle());
      } else {
         this.inventory.clear();
      }

      this.tempButtons.clear();
      this.compile();
      this.placeBackground();
      this.placeOutline();
      this.placeButtons();
   }

   public void setItem(int x, int y, ItemStack item) {
      this.getInventory().setItem(this.getSlot(x, y), item);
   }

   public void setItem(GuiItem item) {
      this.getInventory().setItem(this.getSlot(item.slotX(), item.slotY()), item.icon());
   }

   @NotNull
   public Inventory getInventory() {
      if (this.inventory == null) {
         this.build();
      }

      return this.inventory;
   }

   protected void setBackground(ItemStack type) {
      this.background = type;
   }

   private void placeBackground() {
      if (this.background != null) {
         for (int i = 0; i < this.getHeight() * 9; i++) {
            this.getInventory().setItem(i, this.background);
         }
      }
   }

   protected void setOutline(ItemStack outline, GuiInventory.OutlineType type) {
      this.outline = outline;
      this.outlineType = type;
   }

   public void placeOutline() {
      if (this.outline != null && this.outlineType != null) {
         if (this.outlineType.equals(GuiInventory.OutlineType.FULL)) {
            int i = 0;

            while (i < this.getHeight() * 9) {
               this.getInventory().setItem(i, this.outline);
               if (i >= 9 && i < this.getHeight() * 9 - 9 && i % 9 == 0) {
                  i += 8;
               } else {
                  i++;
               }
            }
         } else if (this.outlineType.equals(GuiInventory.OutlineType.BOTTOM)) {
            for (int i = 0; i < 9; i++) {
               this.getInventory().setItem(this.getSlot(i + 1, -1), this.outline);
            }
         }
      }
   }

   protected void registerButton(GuiButton button) {
      this.buttons.add(button);
   }

   protected void registerTempButton(GuiButton button) {
      this.tempButtons.add(button);
   }

   protected void placeButtons() {
      for (GuiButton button : this.getButtons()) {
         if (button.getIcon() != null) {
            if (button instanceof SectionButton) {
               SectionButton sectionButton = (SectionButton)button;

               for (int x = 0; x < sectionButton.getWidth(); x++) {
                  for (int y = 0; y < sectionButton.getHeight(); y++) {
                     this.inventory.setItem(button.getSlot() + x + y * 9, button.getIcon());
                  }
               }
            } else {
               this.inventory.setItem(button.getSlot(), button.getIcon());
            }
         }
      }
   }

   List<GuiButton> getButtons() {
      return Stream.concat(this.buttons.stream(), this.tempButtons.stream()).toList();
   }

   public int getSlot(int x, int y) {
      if (y < 0) {
         y = this.getHeight() + y + 1;
      }

      if (x < 0) {
         x = 9 + x + 1;
      }

      return (y - 1) * 9 + (x - 1);
   }

   public void open(HumanEntity entity) {
      entity.openInventory(this.getInventory());
   }

   public void onClose(HumanEntity player) {
   }

   public boolean allowItemPlace(Inventory inventory, ClickType click, int slot) {
      return false;
   }

   public void onItemPlace(HumanEntity player, int slot, ItemStack item) {
   }

   public abstract Component getTitle();

   public abstract int getHeight();

   public static int getX(int index) {
      return index - getY(index) * 9;
   }

   public static int getY(int index) {
      return index / 9;
   }

   public static boolean isInSquareZone(int x, int y, int startX, int startY, int endX, int endY) {
      return startX <= x && x <= endX && startY <= y && y <= endY;
   }

   public static boolean isInSquareZone(int index, int startX, int startY, int endX, int endY) {
      return isInSquareZone(getX(index), getY(index), startX, startY, endX, endY);
   }

   public static boolean isInSquareZone(int index, int start, int end) {
      return isInSquareZone(getX(index), getY(index), getX(start), getY(start), getX(end), getY(end));
   }

   public static int getSlotWithinWidth(int index, int width) {
      int y = index / width;
      return y * 9 + index - y * width;
   }

   public static enum OutlineType {
      BOTTOM,
      FULL;
   }
}
