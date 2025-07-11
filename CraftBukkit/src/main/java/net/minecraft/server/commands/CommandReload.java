package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.ResourcePackRepository;
import net.minecraft.world.level.storage.SaveData;
import org.slf4j.Logger;

public class CommandReload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public CommandReload() {}

    public static void reloadPacks(Collection<String> collection, CommandListenerWrapper commandlistenerwrapper) {
        commandlistenerwrapper.getServer().reloadResources(collection).exceptionally((throwable) -> {
            CommandReload.LOGGER.warn("Failed to execute reload", throwable);
            commandlistenerwrapper.sendFailure(IChatBaseComponent.translatable("commands.reload.failure"));
            return null;
        });
    }

    private static Collection<String> discoverNewPacks(ResourcePackRepository resourcepackrepository, SaveData savedata, Collection<String> collection) {
        resourcepackrepository.reload();
        Collection<String> collection1 = Lists.newArrayList(collection);
        Collection<String> collection2 = savedata.getDataConfiguration().dataPacks().getDisabled();

        for (String s : resourcepackrepository.getAvailableIds()) {
            if (!collection2.contains(s) && !collection1.contains(s)) {
                collection1.add(s);
            }
        }

        return collection1;
    }

    // CraftBukkit start
    public static void reload(MinecraftServer minecraftserver) {
        ResourcePackRepository resourcepackrepository = minecraftserver.getPackRepository();
        SaveData savedata = minecraftserver.getWorldData();
        Collection<String> collection = resourcepackrepository.getSelectedIds();
        Collection<String> collection1 = discoverNewPacks(resourcepackrepository, savedata, collection);
        minecraftserver.reloadResources(collection1);
    }
    // CraftBukkit end

    public static void register(CommandDispatcher<CommandListenerWrapper> commanddispatcher) {
        commanddispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.CommandDispatcher.literal("reload").requires(net.minecraft.commands.CommandDispatcher.hasPermission(2))).executes((commandcontext) -> {
            CommandListenerWrapper commandlistenerwrapper = (CommandListenerWrapper) commandcontext.getSource();
            MinecraftServer minecraftserver = commandlistenerwrapper.getServer();
            ResourcePackRepository resourcepackrepository = minecraftserver.getPackRepository();
            SaveData savedata = minecraftserver.getWorldData();
            Collection<String> collection = resourcepackrepository.getSelectedIds();
            Collection<String> collection1 = discoverNewPacks(resourcepackrepository, savedata, collection);

            commandlistenerwrapper.sendSuccess(() -> {
                return IChatBaseComponent.translatable("commands.reload.success");
            }, true);
            reloadPacks(collection1, commandlistenerwrapper);
            return 0;
        }));
    }
}
