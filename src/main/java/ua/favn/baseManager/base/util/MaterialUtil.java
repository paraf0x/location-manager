package ua.favn.baseManager.base.util;

import org.bukkit.Material;

public class MaterialUtil {
   public static Material parseMaterial(String material) {
      try {
         return Material.valueOf(material);
      } catch (Exception var2) {
         return Material.matchMaterial(material);
      }
   }
}
