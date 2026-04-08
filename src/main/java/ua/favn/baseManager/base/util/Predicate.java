package ua.favn.baseManager.base.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

public class Predicate {
   public static boolean check(boolean check, Audience audience, String message) {
      return check(check, audience, FormatUtil.error(message));
   }

   public static boolean check(boolean check, Audience audience, Component message) {
      if (check) {
         audience.sendMessage(message);
      }

      return check;
   }
}
