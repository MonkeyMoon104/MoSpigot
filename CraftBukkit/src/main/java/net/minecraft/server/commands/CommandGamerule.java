package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.world.level.GameRules;

public class CommandGamerule {

    public CommandGamerule() {}

    public static void register(CommandDispatcher<CommandListenerWrapper> commanddispatcher, CommandBuildContext commandbuildcontext) {
        final LiteralArgumentBuilder<CommandListenerWrapper> literalargumentbuilder = (LiteralArgumentBuilder) net.minecraft.commands.CommandDispatcher.literal("gamerule").requires(net.minecraft.commands.CommandDispatcher.hasPermission(2));

        (new GameRules(commandbuildcontext.enabledFeatures())).visitGameRuleTypes(new GameRules.GameRuleVisitor() {
            @Override
            public <T extends GameRules.GameRuleValue<T>> void visit(GameRules.GameRuleKey<T> gamerules_gamerulekey, GameRules.GameRuleDefinition<T> gamerules_gameruledefinition) {
                LiteralArgumentBuilder<CommandListenerWrapper> literalargumentbuilder1 = net.minecraft.commands.CommandDispatcher.literal(gamerules_gamerulekey.getId());

                literalargumentbuilder.then(((LiteralArgumentBuilder) literalargumentbuilder1.executes((commandcontext) -> {
                    return CommandGamerule.queryRule((CommandListenerWrapper) commandcontext.getSource(), gamerules_gamerulekey);
                })).then(gamerules_gameruledefinition.createArgument("value").executes((commandcontext) -> {
                    return CommandGamerule.setRule(commandcontext, gamerules_gamerulekey);
                })));
            }
        });
        commanddispatcher.register(literalargumentbuilder);
    }

    static <T extends GameRules.GameRuleValue<T>> int setRule(CommandContext<CommandListenerWrapper> commandcontext, GameRules.GameRuleKey<T> gamerules_gamerulekey) {
        CommandListenerWrapper commandlistenerwrapper = (CommandListenerWrapper) commandcontext.getSource();
        T t0 = commandlistenerwrapper.getLevel().getGameRules().getRule(gamerules_gamerulekey); // CraftBukkit

        ((GameRules.GameRuleValue) t0).setFromArgument(commandcontext, "value");
        commandlistenerwrapper.sendSuccess(() -> {
            return IChatBaseComponent.translatable("commands.gamerule.set", gamerules_gamerulekey.getId(), t0.toString());
        }, true);
        return t0.getCommandResult();
    }

    static <T extends GameRules.GameRuleValue<T>> int queryRule(CommandListenerWrapper commandlistenerwrapper, GameRules.GameRuleKey<T> gamerules_gamerulekey) {
        T t0 = commandlistenerwrapper.getLevel().getGameRules().getRule(gamerules_gamerulekey); // CraftBukkit

        commandlistenerwrapper.sendSuccess(() -> {
            return IChatBaseComponent.translatable("commands.gamerule.query", gamerules_gamerulekey.getId(), t0.toString());
        }, false);
        return t0.getCommandResult();
    }
}
