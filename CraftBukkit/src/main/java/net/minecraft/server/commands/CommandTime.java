package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.arguments.ArgumentTime;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.server.level.WorldServer;

// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.event.world.TimeSkipEvent;
// CraftBukkit end

public class CommandTime {

    public CommandTime() {}

    public static void register(CommandDispatcher<CommandListenerWrapper> commanddispatcher) {
        commanddispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.CommandDispatcher.literal("time").requires(net.minecraft.commands.CommandDispatcher.hasPermission(2))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.CommandDispatcher.literal("set").then(net.minecraft.commands.CommandDispatcher.literal("day").executes((commandcontext) -> {
            return setTime((CommandListenerWrapper) commandcontext.getSource(), 1000);
        }))).then(net.minecraft.commands.CommandDispatcher.literal("noon").executes((commandcontext) -> {
            return setTime((CommandListenerWrapper) commandcontext.getSource(), 6000);
        }))).then(net.minecraft.commands.CommandDispatcher.literal("night").executes((commandcontext) -> {
            return setTime((CommandListenerWrapper) commandcontext.getSource(), 13000);
        }))).then(net.minecraft.commands.CommandDispatcher.literal("midnight").executes((commandcontext) -> {
            return setTime((CommandListenerWrapper) commandcontext.getSource(), 18000);
        }))).then(net.minecraft.commands.CommandDispatcher.argument("time", ArgumentTime.time()).executes((commandcontext) -> {
            return setTime((CommandListenerWrapper) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(net.minecraft.commands.CommandDispatcher.literal("add").then(net.minecraft.commands.CommandDispatcher.argument("time", ArgumentTime.time()).executes((commandcontext) -> {
            return addTime((CommandListenerWrapper) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "time"));
        })))).then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.CommandDispatcher.literal("query").then(net.minecraft.commands.CommandDispatcher.literal("daytime").executes((commandcontext) -> {
            return queryTime((CommandListenerWrapper) commandcontext.getSource(), getDayTime(((CommandListenerWrapper) commandcontext.getSource()).getLevel()));
        }))).then(net.minecraft.commands.CommandDispatcher.literal("gametime").executes((commandcontext) -> {
            return queryTime((CommandListenerWrapper) commandcontext.getSource(), (int) (((CommandListenerWrapper) commandcontext.getSource()).getLevel().getGameTime() % 2147483647L));
        }))).then(net.minecraft.commands.CommandDispatcher.literal("day").executes((commandcontext) -> {
            return queryTime((CommandListenerWrapper) commandcontext.getSource(), (int) (((CommandListenerWrapper) commandcontext.getSource()).getLevel().getDayTime() / 24000L % 2147483647L));
        }))));
    }

    private static int getDayTime(WorldServer worldserver) {
        return (int) (worldserver.getDayTime() % 24000L);
    }

    private static int queryTime(CommandListenerWrapper commandlistenerwrapper, int i) {
        commandlistenerwrapper.sendSuccess(() -> {
            return IChatBaseComponent.translatable("commands.time.query", i);
        }, false);
        return i;
    }

    public static int setTime(CommandListenerWrapper commandlistenerwrapper, int i) {
        // CraftBukkit start - SPIGOT-6496: Only set the time for the world the command originates in
        {
            WorldServer worldserver = commandlistenerwrapper.getLevel();
            TimeSkipEvent event = new TimeSkipEvent(worldserver.getWorld(), TimeSkipEvent.SkipReason.COMMAND, i - worldserver.getDayTime());
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                worldserver.setDayTime((long) worldserver.getDayTime() + event.getSkipAmount());
            }
            // CraftBukkit end
        }

        commandlistenerwrapper.getServer().forceTimeSynchronization();
        commandlistenerwrapper.sendSuccess(() -> {
            return IChatBaseComponent.translatable("commands.time.set", i);
        }, true);
        return getDayTime(commandlistenerwrapper.getLevel());
    }

    public static int addTime(CommandListenerWrapper commandlistenerwrapper, int i) {
        // CraftBukkit start - SPIGOT-6496: Only set the time for the world the command originates in
        {
            WorldServer worldserver = commandlistenerwrapper.getLevel();
            TimeSkipEvent event = new TimeSkipEvent(worldserver.getWorld(), TimeSkipEvent.SkipReason.COMMAND, i);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                worldserver.setDayTime(worldserver.getDayTime() + event.getSkipAmount());
            }
            // CraftBukkit end
        }

        commandlistenerwrapper.getServer().forceTimeSynchronization();
        int j = getDayTime(commandlistenerwrapper.getLevel());

        commandlistenerwrapper.sendSuccess(() -> {
            return IChatBaseComponent.translatable("commands.time.set", j);
        }, true);
        return j;
    }
}
