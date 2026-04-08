package ua.favn.baseManager.base.gui;

import org.bukkit.inventory.ItemStack;

public class SectionButton extends GuiButton {
   private final int height;
   private final int width;

   public SectionButton(ItemStack icon, GuiButton.ButtonAction action, int slot, int height, int width) {
      super(icon, action, slot);
      this.height = height;
      this.width = width;
   }

   public SectionButton(ItemStack icon, GuiButton.CompleteButtonAction action, int slot, int height, int width) {
      super(icon, action, slot);
      this.height = height;
      this.width = width;
   }

   public int getHeight() {
      return this.height;
   }

   public int getWidth() {
      return this.width;
   }

   @Override
   public boolean contains(int slot) {
      return GuiInventory.isInSquareZone(slot, this.getSlot(), this.getSlot() + (this.getWidth() - 1) + (this.getHeight() - 1) * 9);
   }
}
