package ua.favn.baseManager.base.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;

public class GuiListener extends Base implements Listener {
   public GuiListener(BaseManager plugin) {
      super(plugin);
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (event.getInventory().getHolder() instanceof GuiInventory inventory) {
         inventory.onClose(event.getPlayer());
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getInventory().getHolder() instanceof GuiInventory inventory)) {
         return;
      }

      // Check if clicking in the GUI inventory (top) or player inventory (bottom)
      boolean clickedInGui = event.getClickedInventory() != null
         && event.getClickedInventory().equals(event.getInventory());

      if (clickedInGui) {
         // Always cancel clicks in GUI inventory first
         event.setCancelled(true);

         // Check if this is an item place action (cursor has item)
         boolean hasCursorItem = event.getCursor() != null && !event.getCursor().getType().isAir();
         boolean allowPlace = inventory.allowItemPlace(event.getClickedInventory(), event.getClick(), event.getSlot());

         if (hasCursorItem && allowPlace) {
            inventory.onItemPlace(event.getWhoClicked(), event.getSlot(), event.getCursor());
            return; // Don't process button clicks when placing items
         }

         // Process button clicks
         for (GuiButton button : inventory.getButtons()) {
            if (button.contains(event.getSlot())) {
               GuiButton.ButtonAction result = button.getAction();
               if (result instanceof GuiButton.CompleteButtonAction action) {
                  GuiButton.ActionResult actionResult = action.run(
                     event.getWhoClicked(), new GuiButton.ActionInfo(event.getSlot(), event.getClick(), event.getCursor())
                  );
                  if (actionResult.cursor() != null) {
                     event.getView().setCursor(actionResult.cursor());
                  }
               } else {
                  result.run(event.getWhoClicked());
               }
               break;
            }
         }
      } else {
         // Clicking in player inventory - cancel shift-clicks to prevent moving items into GUI
         if (event.getClick().isShiftClick()) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof GuiInventory) {
         event.setCancelled(true);
      }
   }
}
