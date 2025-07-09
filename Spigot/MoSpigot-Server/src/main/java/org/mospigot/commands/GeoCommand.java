package org.mospigot.commands;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class GeoCommand extends Command
{

    public GeoCommand(String name)
    {
        super( name );
        this.description = "Gets the current info for the player";
        this.usageMessage = "/geo";
        this.setPermission( "mospigot.command.geo" );
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args)
    {
        if ( !testPermission( sender ) )
        {
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(String.valueOf(Component.text("Uso corretto: /geo <player>", NamedTextColor.RED)));
            return false;
        }

        String targetName = args[0];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);

        if (onlineTarget != null && onlineTarget.isOnline()) {
            sender.sendMessage(buildOnlinePlayerInfo(onlineTarget));
        } else {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            if (offlineTarget == null || offlineTarget.getUniqueId() == null) {
                sender.sendMessage(String.valueOf(Component.text("Giocatore non trovato.", NamedTextColor.RED)));
                return true;
            }

            sender.sendMessage(buildOfflinePlayerInfo(offlineTarget));
        }

        return true;
    }

    private String buildOnlinePlayerInfo(Player player) {
        String ip = "N/D";
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            ip = player.getAddress().getAddress().getHostAddress();
        }

        String vpnCheck = checkVPN(ip) ? "§cSospetta VPN" : "§aNessuna VPN rilevata";

        return ChatColor.GREEN + "Informazioni di " + ChatColor.GOLD + player.getName() + "\n" +
                ChatColor.GREEN + "UUID: " + ChatColor.WHITE + player.getUniqueId().toString() + "\n" +
                ChatColor.GREEN + "Online: " + ChatColor.WHITE + "Sì\n" +
                ChatColor.GREEN + "Posizione: " + ChatColor.WHITE + formatLocation(player.getLocation().getWorld(),
                player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()) + "\n" +
                ChatColor.GREEN + "Mondo: " + ChatColor.WHITE + player.getWorld().getName() + "\n" +
                ChatColor.GREEN + "IP: " + ChatColor.WHITE + ip + "\n" +
                ChatColor.GREEN + "VPN: " + ChatColor.WHITE + vpnCheck;
    }


    private String buildOfflinePlayerInfo(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName() != null ? player.getName() : "Sconosciuto";

        return ChatColor.GREEN + "Informazioni di " + ChatColor.GOLD + name + "\n" +
                ChatColor.GREEN + "UUID: " + ChatColor.WHITE + uuid.toString() + "\n" +
                ChatColor.GREEN + "Online: " + ChatColor.RED + "No";
    }


    private String formatLocation(World world, double x, double y, double z) {
        return String.format("X: %.1f Y: %.1f Z: %.1f", x, y, z);
    }

    private boolean checkVPN(String ip) {
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
