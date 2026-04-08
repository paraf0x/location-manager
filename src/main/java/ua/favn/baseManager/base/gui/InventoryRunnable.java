package ua.favn.baseManager.base.gui;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryRunnable implements Consumer<ScheduledTask> {
   private final Inventory inventory;
   private HashMap<Integer, Supplier<ItemStack>> toUpdate = new HashMap<>();

   public InventoryRunnable(Inventory inventory) {
      this.inventory = inventory;
   }

   public void accept(ScheduledTask scheduledTask) {
      this.toUpdate.forEach((slot, item) -> this.inventory.setItem(slot, item.get()));
   }

   public InventoryRunnable addItem(int slot, Supplier<ItemStack> item) {
      this.toUpdate.put(slot, item);
      return this;
   }
}
