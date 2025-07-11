package net.minecraft.server.commands;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic4CommandExceptionType;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandListenerWrapper;
import net.minecraft.commands.arguments.ArgumentEntity;
import net.minecraft.commands.arguments.coordinates.ArgumentVec2;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.phys.Vec2F;
import net.minecraft.world.scores.ScoreboardTeamBase;

public class CommandSpreadPlayers {

    private static final int MAX_ITERATION_COUNT = 10000;
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_TEAMS = new Dynamic4CommandExceptionType((object, object1, object2, object3) -> {
        return IChatBaseComponent.translatableEscape("commands.spreadplayers.failed.teams", object, object1, object2, object3);
    });
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_ENTITIES = new Dynamic4CommandExceptionType((object, object1, object2, object3) -> {
        return IChatBaseComponent.translatableEscape("commands.spreadplayers.failed.entities", object, object1, object2, object3);
    });
    private static final Dynamic2CommandExceptionType ERROR_INVALID_MAX_HEIGHT = new Dynamic2CommandExceptionType((object, object1) -> {
        return IChatBaseComponent.translatableEscape("commands.spreadplayers.failed.invalid.height", object, object1);
    });

    public CommandSpreadPlayers() {}

    public static void register(CommandDispatcher<CommandListenerWrapper> commanddispatcher) {
        commanddispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.CommandDispatcher.literal("spreadplayers").requires(net.minecraft.commands.CommandDispatcher.hasPermission(2))).then(net.minecraft.commands.CommandDispatcher.argument("center", ArgumentVec2.vec2()).then(net.minecraft.commands.CommandDispatcher.argument("spreadDistance", FloatArgumentType.floatArg(0.0F)).then(((RequiredArgumentBuilder) net.minecraft.commands.CommandDispatcher.argument("maxRange", FloatArgumentType.floatArg(1.0F)).then(net.minecraft.commands.CommandDispatcher.argument("respectTeams", BoolArgumentType.bool()).then(net.minecraft.commands.CommandDispatcher.argument("targets", ArgumentEntity.entities()).executes((commandcontext) -> {
            return spreadPlayers((CommandListenerWrapper) commandcontext.getSource(), ArgumentVec2.getVec2(commandcontext, "center"), FloatArgumentType.getFloat(commandcontext, "spreadDistance"), FloatArgumentType.getFloat(commandcontext, "maxRange"), ((CommandListenerWrapper) commandcontext.getSource()).getLevel().getMaxY() + 1, BoolArgumentType.getBool(commandcontext, "respectTeams"), ArgumentEntity.getEntities(commandcontext, "targets"));
        })))).then(net.minecraft.commands.CommandDispatcher.literal("under").then(net.minecraft.commands.CommandDispatcher.argument("maxHeight", IntegerArgumentType.integer()).then(net.minecraft.commands.CommandDispatcher.argument("respectTeams", BoolArgumentType.bool()).then(net.minecraft.commands.CommandDispatcher.argument("targets", ArgumentEntity.entities()).executes((commandcontext) -> {
            return spreadPlayers((CommandListenerWrapper) commandcontext.getSource(), ArgumentVec2.getVec2(commandcontext, "center"), FloatArgumentType.getFloat(commandcontext, "spreadDistance"), FloatArgumentType.getFloat(commandcontext, "maxRange"), IntegerArgumentType.getInteger(commandcontext, "maxHeight"), BoolArgumentType.getBool(commandcontext, "respectTeams"), ArgumentEntity.getEntities(commandcontext, "targets"));
        })))))))));
    }

    private static int spreadPlayers(CommandListenerWrapper commandlistenerwrapper, Vec2F vec2f, float f, float f1, int i, boolean flag, Collection<? extends Entity> collection) throws CommandSyntaxException {
        WorldServer worldserver = commandlistenerwrapper.getLevel();
        int j = worldserver.getMinY();

        if (i < j) {
            throw CommandSpreadPlayers.ERROR_INVALID_MAX_HEIGHT.create(i, j);
        } else {
            RandomSource randomsource = RandomSource.create();
            double d0 = (double) (vec2f.x - f1);
            double d1 = (double) (vec2f.y - f1);
            double d2 = (double) (vec2f.x + f1);
            double d3 = (double) (vec2f.y + f1);
            CommandSpreadPlayers.a[] acommandspreadplayers_a = createInitialPositions(randomsource, flag ? getNumberOfTeams(collection) : collection.size(), d0, d1, d2, d3);

            spreadPositions(vec2f, (double) f, worldserver, randomsource, d0, d1, d2, d3, i, acommandspreadplayers_a, flag);
            double d4 = setPlayerPositions(collection, worldserver, acommandspreadplayers_a, i, flag);

            commandlistenerwrapper.sendSuccess(() -> {
                return IChatBaseComponent.translatable("commands.spreadplayers.success." + (flag ? "teams" : "entities"), acommandspreadplayers_a.length, vec2f.x, vec2f.y, String.format(Locale.ROOT, "%.2f", d4));
            }, true);
            return acommandspreadplayers_a.length;
        }
    }

    private static int getNumberOfTeams(Collection<? extends Entity> collection) {
        Set<ScoreboardTeamBase> set = Sets.newHashSet();

        for (Entity entity : collection) {
            if (entity instanceof EntityHuman) {
                set.add(entity.getTeam());
            } else {
                set.add((ScoreboardTeamBase) null); // CraftBukkit - decompile error
            }
        }

        return set.size();
    }

    private static void spreadPositions(Vec2F vec2f, double d0, WorldServer worldserver, RandomSource randomsource, double d1, double d2, double d3, double d4, int i, CommandSpreadPlayers.a[] acommandspreadplayers_a, boolean flag) throws CommandSyntaxException {
        boolean flag1 = true;
        double d5 = (double) Float.MAX_VALUE;

        int j;

        for (j = 0; j < 10000 && flag1; ++j) {
            flag1 = false;
            d5 = (double) Float.MAX_VALUE;

            for (int k = 0; k < acommandspreadplayers_a.length; ++k) {
                CommandSpreadPlayers.a commandspreadplayers_a = acommandspreadplayers_a[k];
                int l = 0;
                CommandSpreadPlayers.a commandspreadplayers_a1 = new CommandSpreadPlayers.a();

                for (int i1 = 0; i1 < acommandspreadplayers_a.length; ++i1) {
                    if (k != i1) {
                        CommandSpreadPlayers.a commandspreadplayers_a2 = acommandspreadplayers_a[i1];
                        double d6 = commandspreadplayers_a.dist(commandspreadplayers_a2);

                        d5 = Math.min(d6, d5);
                        if (d6 < d0) {
                            ++l;
                            commandspreadplayers_a1.x += commandspreadplayers_a2.x - commandspreadplayers_a.x;
                            commandspreadplayers_a1.z += commandspreadplayers_a2.z - commandspreadplayers_a.z;
                        }
                    }
                }

                if (l > 0) {
                    commandspreadplayers_a1.x /= (double) l;
                    commandspreadplayers_a1.z /= (double) l;
                    double d7 = commandspreadplayers_a1.getLength();

                    if (d7 > 0.0D) {
                        commandspreadplayers_a1.normalize();
                        commandspreadplayers_a.moveAway(commandspreadplayers_a1);
                    } else {
                        commandspreadplayers_a.randomize(randomsource, d1, d2, d3, d4);
                    }

                    flag1 = true;
                }

                if (commandspreadplayers_a.clamp(d1, d2, d3, d4)) {
                    flag1 = true;
                }
            }

            if (!flag1) {
                for (CommandSpreadPlayers.a commandspreadplayers_a3 : acommandspreadplayers_a) {
                    if (!commandspreadplayers_a3.isSafe(worldserver, i)) {
                        commandspreadplayers_a3.randomize(randomsource, d1, d2, d3, d4);
                        flag1 = true;
                    }
                }
            }
        }

        if (d5 == (double) Float.MAX_VALUE) {
            d5 = 0.0D;
        }

        if (j >= 10000) {
            if (flag) {
                throw CommandSpreadPlayers.ERROR_FAILED_TO_SPREAD_TEAMS.create(acommandspreadplayers_a.length, vec2f.x, vec2f.y, String.format(Locale.ROOT, "%.2f", d5));
            } else {
                throw CommandSpreadPlayers.ERROR_FAILED_TO_SPREAD_ENTITIES.create(acommandspreadplayers_a.length, vec2f.x, vec2f.y, String.format(Locale.ROOT, "%.2f", d5));
            }
        }
    }

    private static double setPlayerPositions(Collection<? extends Entity> collection, WorldServer worldserver, CommandSpreadPlayers.a[] acommandspreadplayers_a, int i, boolean flag) {
        double d0 = 0.0D;
        int j = 0;
        Map<ScoreboardTeamBase, CommandSpreadPlayers.a> map = Maps.newHashMap();

        for (Entity entity : collection) {
            CommandSpreadPlayers.a commandspreadplayers_a;

            if (flag) {
                ScoreboardTeamBase scoreboardteambase = entity instanceof EntityHuman ? entity.getTeam() : null;

                if (!map.containsKey(scoreboardteambase)) {
                    map.put(scoreboardteambase, acommandspreadplayers_a[j++]);
                }

                commandspreadplayers_a = (CommandSpreadPlayers.a) map.get(scoreboardteambase);
            } else {
                commandspreadplayers_a = acommandspreadplayers_a[j++];
            }

            entity.teleportTo(worldserver, (double) MathHelper.floor(commandspreadplayers_a.x) + 0.5D, (double) commandspreadplayers_a.getSpawnY(worldserver, i), (double) MathHelper.floor(commandspreadplayers_a.z) + 0.5D, Set.of(), entity.getYRot(), entity.getXRot(), true, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND); // CraftBukkit - handle teleport reason
            double d1 = Double.MAX_VALUE;

            for (CommandSpreadPlayers.a commandspreadplayers_a1 : acommandspreadplayers_a) {
                if (commandspreadplayers_a != commandspreadplayers_a1) {
                    double d2 = commandspreadplayers_a.dist(commandspreadplayers_a1);

                    d1 = Math.min(d2, d1);
                }
            }

            d0 += d1;
        }

        if (collection.size() < 2) {
            return 0.0D;
        } else {
            d0 /= (double) collection.size();
            return d0;
        }
    }

    private static CommandSpreadPlayers.a[] createInitialPositions(RandomSource randomsource, int i, double d0, double d1, double d2, double d3) {
        CommandSpreadPlayers.a[] acommandspreadplayers_a = new CommandSpreadPlayers.a[i];

        for (int j = 0; j < acommandspreadplayers_a.length; ++j) {
            CommandSpreadPlayers.a commandspreadplayers_a = new CommandSpreadPlayers.a();

            commandspreadplayers_a.randomize(randomsource, d0, d1, d2, d3);
            acommandspreadplayers_a[j] = commandspreadplayers_a;
        }

        return acommandspreadplayers_a;
    }

    private static class a {

        double x;
        double z;

        a() {}

        double dist(CommandSpreadPlayers.a commandspreadplayers_a) {
            double d0 = this.x - commandspreadplayers_a.x;
            double d1 = this.z - commandspreadplayers_a.z;

            return Math.sqrt(d0 * d0 + d1 * d1);
        }

        void normalize() {
            double d0 = this.getLength();

            this.x /= d0;
            this.z /= d0;
        }

        double getLength() {
            return Math.sqrt(this.x * this.x + this.z * this.z);
        }

        public void moveAway(CommandSpreadPlayers.a commandspreadplayers_a) {
            this.x -= commandspreadplayers_a.x;
            this.z -= commandspreadplayers_a.z;
        }

        public boolean clamp(double d0, double d1, double d2, double d3) {
            boolean flag = false;

            if (this.x < d0) {
                this.x = d0;
                flag = true;
            } else if (this.x > d2) {
                this.x = d2;
                flag = true;
            }

            if (this.z < d1) {
                this.z = d1;
                flag = true;
            } else if (this.z > d3) {
                this.z = d3;
                flag = true;
            }

            return flag;
        }

        public int getSpawnY(IBlockAccess iblockaccess, int i) {
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition(this.x, (double) (i + 1), this.z);
            boolean flag = iblockaccess.getBlockState(blockposition_mutableblockposition).isAir();

            blockposition_mutableblockposition.move(EnumDirection.DOWN);

            boolean flag1;

            for (boolean flag2 = iblockaccess.getBlockState(blockposition_mutableblockposition).isAir(); blockposition_mutableblockposition.getY() > iblockaccess.getMinY(); flag2 = flag1) {
                blockposition_mutableblockposition.move(EnumDirection.DOWN);
                flag1 = iblockaccess.getBlockState(blockposition_mutableblockposition).isAir();
                if (!flag1 && flag2 && flag) {
                    return blockposition_mutableblockposition.getY() + 1;
                }

                flag = flag2;
            }

            return i + 1;
        }

        public boolean isSafe(IBlockAccess iblockaccess, int i) {
            BlockPosition blockposition = BlockPosition.containing(this.x, (double) (this.getSpawnY(iblockaccess, i) - 1), this.z);
            IBlockData iblockdata = iblockaccess.getBlockState(blockposition);

            return blockposition.getY() < i && !iblockdata.liquid() && !iblockdata.is(TagsBlock.FIRE);
        }

        public void randomize(RandomSource randomsource, double d0, double d1, double d2, double d3) {
            this.x = MathHelper.nextDouble(randomsource, d0, d2);
            this.z = MathHelper.nextDouble(randomsource, d1, d3);
        }
    }
}
