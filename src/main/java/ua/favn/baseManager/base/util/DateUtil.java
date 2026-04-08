package ua.favn.baseManager.base.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateUtil {
   public static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
   private static final DateTimeFormatter LEGACY_FORMATTER =
       DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH);

   public static LocalDateTime parseDate(String dateStr) {
      if (dateStr.equalsIgnoreCase("null")) {
         return null;
      }
      try {
         return LocalDateTime.parse(dateStr, DATE_FORMATTER);
      } catch (DateTimeParseException e) {
         // Normalize Unicode whitespace (e.g., narrow no-break space U+202F) to regular space
         String normalized = dateStr.replaceAll("[\\p{Zs}]", " ");
         return LocalDateTime.parse(normalized, LEGACY_FORMATTER);
      }
   }

   public static String formatDate(LocalDateTime date) {
      if (date == null) {
         return "null";
      } else {
         date.atZone(ZoneId.systemDefault());
         return DATE_FORMATTER.format(date);
      }
   }

   public static String formatDuration(Duration duration) {
      return duration.toString();
   }

   public static Duration parseDuration(String string) {
      return string.equalsIgnoreCase("null") ? null : Duration.parse(string);
   }

   public static Duration parseBeautifulDuration(String string) {
      Pattern pattern = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");
      Matcher matcher = pattern.matcher(string);
      if (!matcher.matches()) {
         return null;
      } else {
         int days = 0;
         int hours = 0;
         int minutes = 0;
         int seconds = 0;
         if (matcher.group(1) != null) {
            days = Integer.parseInt(matcher.group(1));
         }

         if (matcher.group(2) != null) {
            hours = Integer.parseInt(matcher.group(2));
         }

         if (matcher.group(3) != null) {
            minutes = Integer.parseInt(matcher.group(3));
         }

         if (matcher.group(4) != null) {
            seconds = Integer.parseInt(matcher.group(4));
         }

         return Duration.ofSeconds((long)seconds + (long)minutes * 60L + (long)hours * 3600L + (long)days * 3600L * 24L);
      }
   }

   public static String formatBeautifulDuration(Duration duration) {
      long seconds = duration.getSeconds();
      long s = Math.abs(seconds);
      return String.format("%d:%02d:%02d", s / 3600L, s % 3600L / 60L, s % 60L);
   }
}
