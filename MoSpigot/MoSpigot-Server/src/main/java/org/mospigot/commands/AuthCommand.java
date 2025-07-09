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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

public class AuthCommand extends Command
{

    public AuthCommand(String name)
    {
        super( name );
        this.description = "Gets the current info for authentication for the players in server";
        this.usageMessage = "/auth";
        this.setPermission( "mospigot.command.auth" );
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args)
    {
        if ( !testPermission( sender ) )
        {
            return true;
        }

        StringBuilder premiumPlayers = new StringBuilder();
        StringBuilder crackedPlayers = new StringBuilder();

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean isPremium = isPremiumAccount(player.getName());
            if (isPremium) {
                premiumPlayers.append(player.getName()).append(", ");
            } else {
                crackedPlayers.append(player.getName()).append(", ");
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Lista giocatori premium online:");
        sender.sendMessage(ChatColor.GOLD + trimTrailingComma(premiumPlayers.toString()));

        sender.sendMessage(ChatColor.RED + "Lista giocatori cracked online:");
        sender.sendMessage(ChatColor.GOLD + trimTrailingComma(crackedPlayers.toString()));

        return true;
    }

    private boolean isPremiumAccount(String playerName) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            int responseCode = conn.getResponseCode();

            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String trimTrailingComma(String str) {
        if (str == null || str.isEmpty()) return "";
        if (str.endsWith(", ")) {
            return str.substring(0, str.length() - 2);
        }
        return str;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        return List.of();
    }
}