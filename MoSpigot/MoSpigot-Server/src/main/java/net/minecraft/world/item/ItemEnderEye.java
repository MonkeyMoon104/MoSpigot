package net.minecraft.world.item;

import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityEnderSignal;
import net.minecraft.world.item.context.ItemActionContext;
import net.minecraft.world.level.RayTrace;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockEnderPortalFrame;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.pattern.ShapeDetector;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.Vec3D;

public class ItemEnderEye extends Item {

    public ItemEnderEye(Item.Info item_info) {
        super(item_info);
    }

    @Override
    public EnumInteractionResult useOn(ItemActionContext itemactioncontext) {
        World world = itemactioncontext.getLevel();
        BlockPosition blockposition = itemactioncontext.getClickedPos();
        IBlockData iblockdata = world.getBlockState(blockposition);

        if (iblockdata.is(Blocks.END_PORTAL_FRAME) && !(Boolean) iblockdata.getValue(BlockEnderPortalFrame.HAS_EYE)) {
            if (world.isClientSide) {
                return EnumInteractionResult.SUCCESS;
            } else {
                IBlockData iblockdata1 = (IBlockData) iblockdata.setValue(BlockEnderPortalFrame.HAS_EYE, true);

                Block.pushEntitiesUp(iblockdata, iblockdata1, world, blockposition);
                world.setBlock(blockposition, iblockdata1, 2);
                world.updateNeighbourForOutputSignal(blockposition, Blocks.END_PORTAL_FRAME);
                itemactioncontext.getItemInHand().shrink(1);
                world.levelEvent(1503, blockposition, 0);
                ShapeDetector.ShapeDetectorCollection shapedetector_shapedetectorcollection = BlockEnderPortalFrame.getOrCreatePortalShape().find(world, blockposition);

                if (shapedetector_shapedetectorcollection != null) {
                    BlockPosition blockposition1 = shapedetector_shapedetectorcollection.getFrontTopLeft().offset(-3, 0, -3);

                    for (int i = 0; i < 3; ++i) {
                        for (int j = 0; j < 3; ++j) {
                            BlockPosition blockposition2 = blockposition1.offset(i, 0, j);

                            world.destroyBlock(blockposition2, true, (Entity) null);
                            world.setBlock(blockposition2, Blocks.END_PORTAL.defaultBlockState(), 2);
                        }
                    }

                    // CraftBukkit start - Use relative location for far away sounds
                    // world.globalLevelEvent(1038, blockposition1.offset(1, 0, 1), 0);
                    int viewDistance = world.getCraftServer().getViewDistance() * 16;
                    BlockPosition soundPos = blockposition1.offset(1, 0, 1);
                    for (EntityPlayer player : world.getServer().getPlayerList().players) {
                        double deltaX = soundPos.getX() - player.getX();
                        double deltaZ = soundPos.getZ() - player.getZ();
                        double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                        if (world.spigotConfig.endPortalSoundRadius > 0 && distanceSquared > world.spigotConfig.endPortalSoundRadius * world.spigotConfig.endPortalSoundRadius) continue; // Spigot
                        if (distanceSquared > viewDistance * viewDistance) {
                            double deltaLength = Math.sqrt(distanceSquared);
                            double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                            double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                            player.connection.send(new net.minecraft.network.protocol.game.PacketPlayOutWorldEvent(1038, new BlockPosition((int) relativeX, (int) soundPos.getY(), (int) relativeZ), 0, true));
                        } else {
                            player.connection.send(new net.minecraft.network.protocol.game.PacketPlayOutWorldEvent(1038, soundPos, 0, true));
                        }
                    }
                    // CraftBukkit end
                }

                return EnumInteractionResult.SUCCESS;
            }
        } else {
            return EnumInteractionResult.PASS;
        }
    }

    @Override
    public int getUseDuration(ItemStack itemstack, EntityLiving entityliving) {
        return 0;
    }

    @Override
    public EnumInteractionResult use(World world, EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        MovingObjectPositionBlock movingobjectpositionblock = getPlayerPOVHitResult(world, entityhuman, RayTrace.FluidCollisionOption.NONE);

        if (movingobjectpositionblock.getType() == MovingObjectPosition.EnumMovingObjectType.BLOCK && world.getBlockState(movingobjectpositionblock.getBlockPos()).is(Blocks.END_PORTAL_FRAME)) {
            return EnumInteractionResult.PASS;
        } else {
            entityhuman.startUsingItem(enumhand);
            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;
                BlockPosition blockposition = worldserver.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, entityhuman.blockPosition(), 100, false);

                if (blockposition == null) {
                    return EnumInteractionResult.CONSUME;
                }

                EntityEnderSignal entityendersignal = new EntityEnderSignal(world, entityhuman.getX(), entityhuman.getY(0.5D), entityhuman.getZ());

                entityendersignal.setItem(itemstack);
                entityendersignal.signalTo(Vec3D.atLowerCornerOf(blockposition));
                world.gameEvent(GameEvent.PROJECTILE_SHOOT, entityendersignal.position(), GameEvent.a.of((Entity) entityhuman));
                // CraftBukkit start
                if (!world.addFreshEntity(entityendersignal)) {
                    return EnumInteractionResult.FAIL;
                }
                // CraftBukkit end
                if (entityhuman instanceof EntityPlayer) {
                    EntityPlayer entityplayer = (EntityPlayer) entityhuman;

                    CriterionTriggers.USED_ENDER_EYE.trigger(entityplayer, blockposition);
                }

                float f = MathHelper.lerp(world.random.nextFloat(), 0.33F, 0.5F);

                world.playSound((Entity) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEffects.ENDER_EYE_LAUNCH, SoundCategory.NEUTRAL, 1.0F, f);
                itemstack.consume(1, entityhuman);
                entityhuman.awardStat(StatisticList.ITEM_USED.get(this));
            }

            return EnumInteractionResult.SUCCESS_SERVER;
        }
    }
}
