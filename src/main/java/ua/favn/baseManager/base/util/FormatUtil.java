package ua.favn.baseManager.base.util;

import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

public class FormatUtil {
   private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

   public static Component normalize(Component component) {
      return component.decorationIfAbsent(TextDecoration.ITALIC, State.FALSE).colorIfAbsent(TextColor.color(16777215));
   }

   public static Component error(String message) {
      return Component.text(message, TextColor.color(Color.RED.asRGB()));
   }

   public static Component success(String message) {
      return Component.text(message, TextColor.color(Color.LIME.asRGB()));
   }

   public static Component formatItemComponent(ItemStack itemStack) {
      return getDisplayName(itemStack)
         .append(Component.text(" x" + itemStack.getAmount(), Style.style(TextColor.color(Color.GRAY.asRGB())).decoration(TextDecoration.ITALIC, false)));
   }

   private static Component getDisplayName(ItemStack itemStack) {
      if (itemStack == null) {
         return Component.empty();
      } else if (!itemStack.hasItemMeta()) {
         return ((TranslatableComponent)Component.translatable(itemStack.translationKey()).colorIfAbsent(TextColor.color(16777215)))
            .decorationIfAbsent(TextDecoration.ITALIC, State.FALSE);
      } else {
         Component result = itemStack.getItemMeta().hasDisplayName()
            ? itemStack.getItemMeta().displayName().decorationIfAbsent(TextDecoration.ITALIC, State.TRUE)
            : Component.translatable(itemStack.translationKey()).decorationIfAbsent(TextDecoration.ITALIC, State.FALSE);
         return result.colorIfAbsent(TextColor.color(16777215));
      }
   }

   public static Component formatComponent(String message) {
      if (message == null || message.isEmpty()) {
         return Component.empty();
      } else if (isMiniMessageFormat(message)) {
         // MiniMessage format (e.g., <red>, <bold>, <gradient:color1:color2>)
         return normalize(MINI_MESSAGE.deserialize(message));
      } else if (isValidJson(message)) {
         // JSON format
         return JSONComponentSerializer.json().deserialize(message);
      } else {
         // Legacy ampersand format (e.g., &c, &l)
         return normalize(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
      }
   }

   /**
    * Check if a string appears to be in MiniMessage format.
    * Detects tags like <red>, <bold>, <gradient:...>, etc.
    */
   private static boolean isMiniMessageFormat(String message) {
      // Check for MiniMessage tags: <word> or <word:...>
      // Avoid false positives with HTML-like content or comparisons
      return message.contains("<") && message.contains(">")
         && (message.contains("<red>") || message.contains("<green>") || message.contains("<blue>")
            || message.contains("<yellow>") || message.contains("<aqua>") || message.contains("<gold>")
            || message.contains("<gray>") || message.contains("<white>") || message.contains("<black>")
            || message.contains("<dark_") || message.contains("<light_")
            || message.contains("<bold>") || message.contains("<italic>") || message.contains("<underlined>")
            || message.contains("<strikethrough>") || message.contains("<obfuscated>")
            || message.contains("<gradient") || message.contains("<rainbow>")
            || message.contains("<reset>") || message.contains("<newline>")
            || message.matches(".*<[a-z_]+>.*"));
   }

   public static Component formatComponent(String message, FormatUtil.Format... formatting) {
      Component result = formatComponent(message);

      for (FormatUtil.Format format : formatting) {
         if (format.toReplace instanceof Component component) {
            result = result.replaceText((TextReplacementConfig)TextReplacementConfig.builder().match(Pattern.quote(format.placeholder)).replacement(component).build());
         } else {
            String replacement;
            if (format.toReplace instanceof Duration duration) {
               replacement = DateUtil.formatBeautifulDuration(duration);
            } else {
               replacement = String.valueOf(format.toReplace);
            }

            result = result.replaceText(
               (TextReplacementConfig)TextReplacementConfig.builder().match(Pattern.quote(format.placeholder)).replacement(Component.text(replacement)).build()
            );
         }
      }

      return result;
   }

   public static Component replace(Component toChange, FormatUtil.Format format) {
      Component result;
      if (format.toReplace instanceof Component component) {
         result = toChange.replaceText((TextReplacementConfig)TextReplacementConfig.builder().match(Pattern.quote(format.placeholder)).replacement(component).build());
      } else {
         result = toChange.replaceText(
            (TextReplacementConfig)TextReplacementConfig.builder().match(Pattern.quote(format.placeholder)).replacement(String.valueOf(format.toReplace)).build()
         );
      }

      return result;
   }

   public static List<Component> splitByLines(String text, TextColor color, int charsPerLine) {
      ArrayList<Component> result = new ArrayList<>();
      String[] split = text.split(" ");
      int sum = 0;
      StringBuilder currentBuilder = new StringBuilder();

      for (int i = 0; i < split.length; i++) {
         if (sum + split[i].length() > charsPerLine) {
            result.add(normalize(Component.text(currentBuilder.toString(), color)));
            sum = 0;
            currentBuilder = new StringBuilder();
         }

         currentBuilder.append(split[i]).append(" ");
         sum += split[i].length();
         if (i == split.length - 1) {
            result.add(normalize(Component.text(currentBuilder.toString(), color)));
         }
      }

      return result;
   }

   private static boolean isValidJson(String string) {
      try {
         JsonParser.parseString(string);
         return string.startsWith("{") && string.endsWith("}")
            || string.startsWith("[") && string.endsWith("]")
            || string.startsWith("\"") && string.endsWith("\"");
      } catch (Exception var2) {
         return false;
      }
   }

   public static record Format(String placeholder, Object toReplace) {
   }
}
