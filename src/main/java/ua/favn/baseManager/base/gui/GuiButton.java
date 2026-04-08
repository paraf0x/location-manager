package ua.favn.baseManager.base.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class GuiButton {
   private final ItemStack icon;
   private final GuiButton.ButtonAction action;
   private final int slot;
   private GuiButton.ButtonAction rightClickAction;

   public GuiButton(GuiItem item, GuiButton.ButtonAction action, GuiInventory inventory) {
      this.icon = item.icon();
      this.action = action;
      this.slot = inventory.getSlot(item.slotX(), item.slotY());
   }

   public GuiButton(GuiItem item, GuiButton.CompleteButtonAction action, GuiInventory inventory) {
      this.icon = item.icon();
      this.action = action;
      this.slot = inventory.getSlot(item.slotX(), item.slotY());
   }

   public GuiButton(ItemStack icon, GuiButton.ButtonAction action, int slot) {
      this.icon = icon;
      this.action = action;
      this.slot = slot;
   }

   public GuiButton(ItemStack icon, GuiButton.CompleteButtonAction action, int slot) {
      this.icon = icon;
      this.action = action;
      this.slot = slot;
   }

   public GuiButton.ButtonAction getAction() {
      return this.action;
   }

   public GuiButton.ButtonAction getRightClickAction() {
      return this.rightClickAction;
   }

   public GuiButton onRightClick(GuiButton.ButtonAction action) {
      this.rightClickAction = action;
      return this;
   }

   public int getSlot() {
      return this.slot;
   }

   public ItemStack getIcon() {
      return this.icon;
   }

   public boolean contains(int slot) {
      return this.slot == slot;
   }

   public static record ActionInfo(int slot, ClickType click, ItemStack cursor) {
   }

   public static record ActionResult(ItemStack cursor) {
      public static GuiButton.ActionResult EMPTY = new GuiButton.ActionResult(null);
   }

   public interface ButtonAction {
      void run(HumanEntity var1);
   }

   public interface CompleteButtonAction extends GuiButton.ButtonAction {
      GuiButton.ActionResult run(HumanEntity var1, GuiButton.ActionInfo var2);

      @Override
      default void run(HumanEntity clicker) {
         this.run(clicker, new GuiButton.ActionInfo(0, null, null));
      }
   }
}
