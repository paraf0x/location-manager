package ua.favn.baseManager.base.manager;

import java.util.HashSet;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;

public abstract class CachedManager<T> extends Base {
   protected HashSet<T> cache;

   protected CachedManager(BaseManager plugin) {
      super(plugin);
   }

   public HashSet<T> getAll() {
      return this.cache != null ? this.cache : this.fetchAll();
   }

   protected abstract HashSet<T> fetchAll();
}
