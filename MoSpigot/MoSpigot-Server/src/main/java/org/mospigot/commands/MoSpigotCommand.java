package org.mospigot.commands;

import java.io.File;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldServer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.mospigot.config.MoSpigotConfig;

public class MoSpigotCommand extends Command {

    public MoSpigotCommand(String name) {
        super(name);
        this.description = "MoSpigot related commands";
        this.usageMessage = "/mospigot reload";
        this.setPermission("mospigot.command.reload");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) return true;

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            Command.broadcastCommandMessage(sender, ChatColor.RED + "Please note that this command is not supported and may cause issues.");
            Command.broadcastCommandMessage(sender, ChatColor.RED + "If you encounter any issues please use the /stop command to restart your server.");

            MinecraftServer console = MinecraftServer.getServer();
            MoSpigotConfig.init((File) console.options.valueOf("mospigot-settings"));
            for (WorldServer world : console.getAllLevels()) {
                world.spigotConfig.init();
            }
            console.server.reloadCount++;

            Command.broadcastCommandMessage(sender, ChatColor.GREEN + "Reload complete.");
        }

        return true;
    }
}
