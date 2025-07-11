package net.minecraft.world.entity;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockFireAbstract;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityLightning extends Entity {

    private static final int START_LIFE = 2;
    private static final double DAMAGE_RADIUS = 3.0D;
    private static final double DETECTION_RADIUS = 15.0D;
    public int life = 2;
    public long seed;
    public int flashes;
    public boolean visualOnly;
    @Nullable
    private EntityPlayer cause;
    private final Set<Entity> hitEntities = Sets.newHashSet();
    private int blocksSetOnFire;

    public EntityLightning(EntityTypes<? extends EntityLightning> entitytypes, World world) {
        super(entitytypes, world);
        this.seed = this.random.nextLong();
        this.flashes = this.random.nextInt(3) + 1;
    }

    public void setVisualOnly(boolean flag) {
        this.visualOnly = flag;
    }

    @Override
    public SoundCategory getSoundSource() {
        return SoundCategory.WEATHER;
    }

    @Nullable
    public EntityPlayer getCause() {
        return this.cause;
    }

    public void setCause(@Nullable EntityPlayer entityplayer) {
        this.cause = entityplayer;
    }

    private void powerLightningRod() {
        BlockPosition blockposition = this.getStrikePosition();
        IBlockData iblockdata = this.level().getBlockState(blockposition);

        if (iblockdata.is(Blocks.LIGHTNING_ROD)) {
            ((LightningRodBlock) iblockdata.getBlock()).onLightningStrike(iblockdata, this.level(), blockposition);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.life == 2) {
            if (this.level().isClientSide()) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEffects.LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 10000.0F, 0.8F + this.random.nextFloat() * 0.2F, false);
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEffects.LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 2.0F, 0.5F + this.random.nextFloat() * 0.2F, false);
            } else {
                EnumDifficulty enumdifficulty = this.level().getDifficulty();

                if (enumdifficulty == EnumDifficulty.NORMAL || enumdifficulty == EnumDifficulty.HARD) {
                    this.spawnFire(4);
                }

                this.powerLightningRod();
                clearCopperOnLightningStrike(this.level(), this.getStrikePosition());
                this.gameEvent(GameEvent.LIGHTNING_STRIKE);
            }
        }

        --this.life;
        if (this.life < 0) {
            if (this.flashes == 0) {
                if (this.level() instanceof WorldServer) {
                    List<Entity> list = this.level().getEntities(this, new AxisAlignedBB(this.getX() - 15.0D, this.getY() - 15.0D, this.getZ() - 15.0D, this.getX() + 15.0D, this.getY() + 6.0D + 15.0D, this.getZ() + 15.0D), (entity) -> {
                        return entity.isAlive() && !this.hitEntities.contains(entity);
                    });

                    for (EntityPlayer entityplayer : ((WorldServer) this.level()).getPlayers((entityplayer1) -> {
                        return entityplayer1.distanceTo(this) < 256.0F;
                    })) {
                        CriterionTriggers.LIGHTNING_STRIKE.trigger(entityplayer, this, list);
                    }
                }

                this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            } else if (this.life < -this.random.nextInt(10)) {
                --this.flashes;
                this.life = 1;
                this.seed = this.random.nextLong();
                this.spawnFire(0);
            }
        }

        if (this.life >= 0 && !this.visualOnly) { // CraftBukkit - add !this.visualOnly
            if (!(this.level() instanceof WorldServer)) {
                this.level().setSkyFlashTime(2);
            } else if (!this.visualOnly) {
                List<Entity> list1 = this.level().getEntities(this, new AxisAlignedBB(this.getX() - 3.0D, this.getY() - 3.0D, this.getZ() - 3.0D, this.getX() + 3.0D, this.getY() + 6.0D + 3.0D, this.getZ() + 3.0D), Entity::isAlive);

                for (Entity entity : list1) {
                    entity.thunderHit((WorldServer) this.level(), this);
                }

                this.hitEntities.addAll(list1);
                if (this.cause != null) {
                    CriterionTriggers.CHANNELED_LIGHTNING.trigger(this.cause, list1);
                }
            }
        }

    }

    private BlockPosition getStrikePosition() {
        Vec3D vec3d = this.position();

        return BlockPosition.containing(vec3d.x, vec3d.y - 1.0E-6D, vec3d.z);
    }

    private void spawnFire(int i) {
        if (!this.visualOnly) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                if (worldserver.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
                    BlockPosition blockposition = this.blockPosition();
                    IBlockData iblockdata = BlockFireAbstract.getState(this.level(), blockposition);

                    if (this.level().getBlockState(blockposition).isAir() && iblockdata.canSurvive(this.level(), blockposition)) {
                        // CraftBukkit start - add "!visualOnly"
                        if (!visualOnly && !CraftEventFactory.callBlockIgniteEvent(this.level(), blockposition, this).isCancelled()) {
                            this.level().setBlockAndUpdate(blockposition, iblockdata);
                            ++this.blocksSetOnFire;
                        }
                        // CraftBukkit end
                    }

                    for (int j = 0; j < i; ++j) {
                        BlockPosition blockposition1 = blockposition.offset(this.random.nextInt(3) - 1, this.random.nextInt(3) - 1, this.random.nextInt(3) - 1);

                        iblockdata = BlockFireAbstract.getState(this.level(), blockposition1);
                        if (this.level().getBlockState(blockposition1).isAir() && iblockdata.canSurvive(this.level(), blockposition1)) {
                            // CraftBukkit start - add "!visualOnly"
                            if (!visualOnly && !CraftEventFactory.callBlockIgniteEvent(this.level(), blockposition1, this).isCancelled()) {
                                this.level().setBlockAndUpdate(blockposition1, iblockdata);
                                ++this.blocksSetOnFire;
                            }
                            // CraftBukkit end
                        }
                    }

                    return;
                }
            }
        }

    }

    private static void clearCopperOnLightningStrike(World world, BlockPosition blockposition) {
        IBlockData iblockdata = world.getBlockState(blockposition);
        BlockPosition blockposition1;
        IBlockData iblockdata1;

        if (iblockdata.is(Blocks.LIGHTNING_ROD)) {
            blockposition1 = blockposition.relative(((EnumDirection) iblockdata.getValue(LightningRodBlock.FACING)).getOpposite());
            iblockdata1 = world.getBlockState(blockposition1);
        } else {
            blockposition1 = blockposition;
            iblockdata1 = iblockdata;
        }

        if (iblockdata1.getBlock() instanceof WeatheringCopper) {
            world.setBlockAndUpdate(blockposition1, WeatheringCopper.getFirst(world.getBlockState(blockposition1)));
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = blockposition.mutable();
            int i = world.random.nextInt(3) + 3;

            for (int j = 0; j < i; ++j) {
                int k = world.random.nextInt(8) + 1;

                randomWalkCleaningCopper(world, blockposition1, blockposition_mutableblockposition, k);
            }

        }
    }

    private static void randomWalkCleaningCopper(World world, BlockPosition blockposition, BlockPosition.MutableBlockPosition blockposition_mutableblockposition, int i) {
        blockposition_mutableblockposition.set(blockposition);

        for (int j = 0; j < i; ++j) {
            Optional<BlockPosition> optional = randomStepCleaningCopper(world, blockposition_mutableblockposition);

            if (optional.isEmpty()) {
                break;
            }

            blockposition_mutableblockposition.set((BaseBlockPosition) optional.get());
        }

    }

    private static Optional<BlockPosition> randomStepCleaningCopper(World world, BlockPosition blockposition) {
        for (BlockPosition blockposition1 : BlockPosition.randomInCube(world.random, 10, blockposition, 1)) {
            IBlockData iblockdata = world.getBlockState(blockposition1);

            if (iblockdata.getBlock() instanceof WeatheringCopper) {
                WeatheringCopper.getPrevious(iblockdata).ifPresent((iblockdata1) -> {
                    world.setBlockAndUpdate(blockposition1, iblockdata1);
                });
                world.levelEvent(3002, blockposition1, -1);
                return Optional.of(blockposition1);
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d0) {
        double d1 = 64.0D * getViewScale();

        return d0 < d1 * d1;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {}

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {}

    public int getBlocksSetOnFire() {
        return this.blocksSetOnFire;
    }

    public Stream<Entity> getHitEntities() {
        return this.hitEntities.stream().filter(Entity::isAlive);
    }

    @Override
    public final boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        return false;
    }
}
