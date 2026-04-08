package ua.favn.baseManager.base.commands;

import org.bukkit.command.TabCompleter;
import ua.favn.baseManager.BaseManager;

public abstract class TabCompleteCommand extends BaseCommand implements TabCompleter {
   public TabCompleteCommand(BaseManager plugin) {
      super(plugin);
      plugin.getCommand(this.getLabel()).setTabCompleter(this);
   }
}
