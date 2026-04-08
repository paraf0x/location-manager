package ua.favn.baseManager.base;

import ua.favn.baseManager.BaseManager;

public abstract class Base {
   private final BaseManager plugin;

   public Base(BaseManager plugin) {
      this.plugin = plugin;
   }

   public BaseManager getPlugin() {
      return this.plugin;
   }
}
