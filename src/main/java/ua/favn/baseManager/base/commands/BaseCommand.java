package ua.favn.baseManager.base.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import ua.favn.baseManager.BaseManager;
import ua.favn.baseManager.base.Base;

public abstract class BaseCommand extends Base implements CommandExecutor {
   public BaseCommand(BaseManager plugin) {
      super(plugin);
      plugin.getCommand(this.getLabel()).setExecutor(this);
   }

   protected abstract String getLabel();

   public Command getCommand() {
      return this.getPlugin().getCommand(this.getLabel());
   }
}
