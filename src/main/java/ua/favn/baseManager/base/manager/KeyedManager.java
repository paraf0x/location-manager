package ua.favn.baseManager.base.manager;

import java.util.HashMap;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;

public abstract class KeyedManager<K, V> extends Base {
   protected HashMap<K, V> cache = new HashMap<>();

   public KeyedManager(BaseManager plugin) {
      super(plugin);
   }

   public HashMap<K, V> getAll() {
      return this.cache;
   }

   public V get(K key) {
      return this.cache.get(key);
   }

   public abstract V fetch(K var1);
}
