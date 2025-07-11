package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.arguments.ArgumentAngle;
import net.minecraft.commands.arguments.ArgumentEntity;
import net.minecraft.commands.arguments.coordinates.ArgumentPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.level.World;

public class CommandSpawnpoint {

    public CommandSpawnpoint() {}

    public static void register(CommandDispatcher<CommandListenerWrapper> commanddispatcher) {
        commanddispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.CommandDispatcher.literal("spawnpoint").requires(net.minecraft.commands.CommandDispatcher.hasPermission(2))).executes((commandcontext) -> {
            return setSpawn((CommandListenerWrapper) commandcontext.getSource(), Collections.singleton(((CommandListenerWrapper) commandcontext.getSource()).getPlayerOrException()), BlockPosition.containing(((CommandListenerWrapper) commandcontext.getSource()).getPosition()), 0.0F);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.CommandDispatcher.argument("targets", ArgumentEntity.players()).executes((commandcontext) -> {
            return setSpawn((CommandListenerWrapper) commandcontext.getSource(), ArgumentEntity.getPlayers(commandcontext, "targets"), BlockPosition.containing(((CommandListenerWrapper) commandcontext.getSource()).getPosition()), 0.0F);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.CommandDispatcher.argument("pos", ArgumentPosition.blockPos()).executes((commandcontext) -> {
            return setSpawn((CommandListenerWrapper) commandcontext.getSource(), ArgumentEntity.getPlayers(commandcontext, "targets"), ArgumentPosition.getSpawnablePos(commandcontext, "pos"), 0.0F);
        })).then(net.minecraft.commands.CommandDispatcher.argument("angle", ArgumentAngle.angle()).executes((commandcontext) -> {
            return setSpawn((CommandListenerWrapper) commandcontext.getSource(), ArgumentEntity.getPlayers(commandcontext, "targets"), ArgumentPosition.getSpawnablePos(commandcontext, "pos"), ArgumentAngle.getAngle(commandcontext, "angle"));
        })))));
    }

    private static int setSpawn(CommandListenerWrapper commandlistenerwrapper, Collection<EntityPlayer> collection, BlockPosition blockposition, float f) {
        ResourceKey<World> resourcekey = commandlistenerwrapper.getLevel().dimension();

        for (EntityPlayer entityplayer : collection) {
            entityplayer.setRespawnPosition(new EntityPlayer.RespawnConfig(resourcekey, blockposition, f, true), false, org.bukkit.event.player.PlayerSpawnChangeEvent.Cause.COMMAND); // CraftBukkit
        }

        String s = resourcekey.location().toString();

        if (collection.size() == 1) {
            commandlistenerwrapper.sendSuccess(() -> {
                return IChatBaseComponent.translatable("commands.spawnpoint.success.single", blockposition.getX(), blockposition.getY(), blockposition.getZ(), f, s, ((EntityPlayer) collection.iterator().next()).getDisplayName());
            }, true);
        } else {
            commandlistenerwrapper.sendSuccess(() -> {
                return IChatBaseComponent.translatable("commands.spawnpoint.success.multiple", blockposition.getX(), blockposition.getY(), blockposition.getZ(), f, s, collection.size());
            }, true);
        }

        return collection.size();
    }
}
