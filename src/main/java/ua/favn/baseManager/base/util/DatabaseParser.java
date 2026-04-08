package ua.favn.baseManager.base.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;

public class DatabaseParser {
   public static <T> T deserializeResultSet(ResultSet set, Class<? extends T> clazz) throws InstantiationException, IllegalAccessException, NoSuchFieldException, SQLException, NoSuchMethodException, InvocationTargetException {
      T instance = (T)clazz.getDeclaredConstructor().newInstance();

      for (int i = 0; i < clazz.getDeclaredFields().length; i++) {
         Field field = clazz.getDeclaredField(clazz.getDeclaredFields()[i].getName());
         field.setAccessible(true);
         if (field.isAnnotationPresent(Serialize.class)) {
            if (field.getType().equals(LocalDateTime.class)) {
               field.set(instance, DateUtil.parseDate(set.getString(field.getName())));
            } else if (field.getType().equals(UUID.class)) {
               field.set(instance, UUID.fromString(set.getString(field.getName())));
            } else if (field.getType().equals(Material.class)) {
               field.set(instance, Material.valueOf(set.getString(field.getName())));
            } else {
               field.set(instance, set.getObject(field.getName()));
            }
         }
      }

      return instance;
   }

   public static <T> String serializeValuesString(Class<? extends T> clazz, T... instances) {
      List<Field> fields = Arrays.stream(clazz.getDeclaredFields()).filter(field -> {
         field.setAccessible(true);
         return field.isAnnotationPresent(Serialize.class);
      }).toList();
      List<String> fieldNames = fields.stream().map(Field::getName).toList();
      StringBuilder result = new StringBuilder("(").append(String.join(", ", fieldNames)).append(") values ");

      for (int i = 0; i < instances.length; i++) {
         T instance = instances[i];
         result.append("(");
         result.append(String.join(", ", fields.stream().map(field -> {
            try {
               return convertValues(field.get(instance));
            } catch (IllegalAccessException var3x) {
               throw new RuntimeException(var3x);
            }
         }).toList()));
         result.append(")");
         if (i != instances.length - 1) {
            result.append(", ");
         }
      }

      return result.toString();
   }

   private static String convertValues(Object value) {
      if (value == null) {
         return "null";
      } else {
         if (value instanceof LocalDateTime time) {
            value = DateUtil.formatDate(time);
         } else if (value instanceof UUID uuid) {
            value = uuid.toString();
         } else if (value instanceof Material material) {
            value = material.toString();
         }

         if (value instanceof String string) {
            value = "\"" + string + "\"";
         }

         return value.toString();
      }
   }
}
