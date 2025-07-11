package net.minecraft.world.entity.animal.armadillo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsEntity;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.INamable;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeRange;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.BehaviorController;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.EntityAIBodyControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.EntityAnimal;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTables;

// CraftBukkit start
import org.bukkit.event.entity.EntityDamageEvent;
// CraftBukkit end

public class Armadillo extends EntityAnimal {

    public static final float BABY_SCALE = 0.6F;
    public static final float MAX_HEAD_ROTATION_EXTENT = 32.5F;
    public static final int SCARE_CHECK_INTERVAL = 80;
    private static final double SCARE_DISTANCE_HORIZONTAL = 7.0D;
    private static final double SCARE_DISTANCE_VERTICAL = 2.0D;
    private static final DataWatcherObject<Armadillo.a> ARMADILLO_STATE = DataWatcher.<Armadillo.a>defineId(Armadillo.class, DataWatcherRegistry.ARMADILLO_STATE);
    private long inStateTicks = 0L;
    public final AnimationState rollOutAnimationState = new AnimationState();
    public final AnimationState rollUpAnimationState = new AnimationState();
    public final AnimationState peekAnimationState = new AnimationState();
    private int scuteTime;
    private boolean peekReceivedClient = false;

    public Armadillo(EntityTypes<? extends EntityAnimal> entitytypes, World world) {
        super(entitytypes, world);
        this.getNavigation().setCanFloat(true);
        this.scuteTime = this.pickNextScuteDropTime();
    }

    @Nullable
    @Override
    public EntityAgeable getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        return EntityTypes.ARMADILLO.create(worldserver, EntitySpawnReason.BREEDING);
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MAX_HEALTH, 12.0D).add(GenericAttributes.MOVEMENT_SPEED, 0.14D);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(Armadillo.ARMADILLO_STATE, Armadillo.a.IDLE);
    }

    public boolean isScared() {
        return this.entityData.get(Armadillo.ARMADILLO_STATE) != Armadillo.a.IDLE;
    }

    public boolean shouldHideInShell() {
        return this.getState().shouldHideInShell(this.inStateTicks);
    }

    public boolean shouldSwitchToScaredState() {
        return this.getState() == Armadillo.a.ROLLING && this.inStateTicks > (long) Armadillo.a.ROLLING.animationDuration();
    }

    public Armadillo.a getState() {
        return (Armadillo.a) this.entityData.get(Armadillo.ARMADILLO_STATE);
    }

    @Override
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        PacketDebug.sendEntityBrain(this);
    }

    public void switchToState(Armadillo.a armadillo_a) {
        this.entityData.set(Armadillo.ARMADILLO_STATE, armadillo_a);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (Armadillo.ARMADILLO_STATE.equals(datawatcherobject)) {
            this.inStateTicks = 0L;
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    protected BehaviorController.b<Armadillo> brainProvider() {
        return ArmadilloAi.brainProvider();
    }

    @Override
    protected BehaviorController<?> makeBrain(Dynamic<?> dynamic) {
        return ArmadilloAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("armadilloBrain");
        ((BehaviorController<Armadillo>) this.brain).tick(worldserver, this); // CraftBukkit - decompile error
        gameprofilerfiller.pop();
        gameprofilerfiller.push("armadilloActivityUpdate");
        ArmadilloAi.updateActivity(this);
        gameprofilerfiller.pop();
        if (this.isAlive() && !this.isBaby() && --this.scuteTime <= 0) {
            this.forceDrops = true; // CraftBukkit
            if (this.dropFromGiftLootTable(worldserver, LootTables.ARMADILLO_SHED, this::spawnAtLocation)) {
                this.playSound(SoundEffects.ARMADILLO_SCUTE_DROP, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                this.gameEvent(GameEvent.ENTITY_PLACE);
            }
            this.forceDrops = false; // CraftBukkit

            this.scuteTime = this.pickNextScuteDropTime();
        }

        super.customServerAiStep(worldserver);
    }

    private int pickNextScuteDropTime() {
        return this.random.nextInt(20 * TimeRange.SECONDS_PER_MINUTE * 5) + 20 * TimeRange.SECONDS_PER_MINUTE * 5;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.setupAnimationStates();
        }

        if (this.isScared()) {
            this.clampHeadRotationToBody();
        }

        ++this.inStateTicks;
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? 0.6F : 1.0F;
    }

    private void setupAnimationStates() {
        switch (this.getState().ordinal()) {
            case 0:
                this.rollOutAnimationState.stop();
                this.rollUpAnimationState.stop();
                this.peekAnimationState.stop();
                break;
            case 1:
                this.rollOutAnimationState.stop();
                this.rollUpAnimationState.startIfStopped(this.tickCount);
                this.peekAnimationState.stop();
                break;
            case 2:
                this.rollOutAnimationState.stop();
                this.rollUpAnimationState.stop();
                if (this.peekReceivedClient) {
                    this.peekAnimationState.stop();
                    this.peekReceivedClient = false;
                }

                if (this.inStateTicks == 0L) {
                    this.peekAnimationState.start(this.tickCount);
                    this.peekAnimationState.fastForward(Armadillo.a.SCARED.animationDuration(), 1.0F);
                } else {
                    this.peekAnimationState.startIfStopped(this.tickCount);
                }
                break;
            case 3:
                this.rollOutAnimationState.startIfStopped(this.tickCount);
                this.rollUpAnimationState.stop();
                this.peekAnimationState.stop();
        }

    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 64 && this.level().isClientSide) {
            this.peekReceivedClient = true;
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEffects.ARMADILLO_PEEK, this.getSoundSource(), 1.0F, 1.0F, false);
        } else {
            super.handleEntityEvent(b0);
        }

    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.ARMADILLO_FOOD);
    }

    public static boolean checkArmadilloSpawnRules(EntityTypes<Armadillo> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return generatoraccess.getBlockState(blockposition.below()).is(TagsBlock.ARMADILLO_SPAWNABLE_ON) && isBrightEnoughToSpawn(generatoraccess, blockposition);
    }

    public boolean isScaredBy(EntityLiving entityliving) {
        if (!this.getBoundingBox().inflate(7.0D, 2.0D, 7.0D).intersects(entityliving.getBoundingBox())) {
            return false;
        } else if (entityliving.getType().is(TagsEntity.UNDEAD)) {
            return true;
        } else if (this.getLastHurtByMob() == entityliving) {
            return true;
        } else if (entityliving instanceof EntityHuman) {
            EntityHuman entityhuman = (EntityHuman) entityliving;

            return entityhuman.isSpectator() ? false : entityhuman.isSprinting() || entityhuman.isPassenger();
        } else {
            return false;
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.store("state", Armadillo.a.CODEC, this.getState());
        valueoutput.putInt("scute_time", this.scuteTime);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.switchToState((Armadillo.a) valueinput.read("state", Armadillo.a.CODEC).orElse(Armadillo.a.IDLE));
        valueinput.getInt("scute_time").ifPresent((integer) -> {
            this.scuteTime = integer;
        });
    }

    public void rollUp() {
        if (!this.isScared()) {
            this.stopInPlace();
            this.resetLove();
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.makeSound(SoundEffects.ARMADILLO_ROLL);
            this.switchToState(Armadillo.a.ROLLING);
        }
    }

    public void rollOut() {
        if (this.isScared()) {
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.makeSound(SoundEffects.ARMADILLO_UNROLL_FINISH);
            this.switchToState(Armadillo.a.IDLE);
        }
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isScared()) {
            f = (f - 1.0F) / 2.0F;
        }

        return super.hurtServer(worldserver, damagesource, f);
    }

    @Override
    // CraftBukkit start - void -> boolean
    public boolean actuallyHurt(WorldServer worldserver, DamageSource damagesource, float f, EntityDamageEvent event) {
        boolean damageResult = super.actuallyHurt(worldserver, damagesource, f, event);
        if (!damageResult) {
            return false;
        }
        // CraftBukkit end
        if (!this.isNoAi() && !this.isDeadOrDying()) {
            if (damagesource.getEntity() instanceof EntityLiving) {
                this.getBrain().setMemoryWithExpiry(MemoryModuleType.DANGER_DETECTED_RECENTLY, true, 80L);
                if (this.canStayRolledUp()) {
                    this.rollUp();
                }
            } else if (damagesource.is(DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES)) {
                this.rollOut();
            }

        }
        return true; // CraftBukkit
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.is(Items.BRUSH) && this.brushOffScute()) {
            itemstack.hurtAndBreak(16, entityhuman, getSlotForHand(enumhand));
            return EnumInteractionResult.SUCCESS;
        } else {
            return (EnumInteractionResult) (this.isScared() ? EnumInteractionResult.FAIL : super.mobInteract(entityhuman, enumhand));
        }
    }

    public boolean brushOffScute() {
        if (this.isBaby()) {
            return false;
        } else {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                this.forceDrops = true; // CraftBukkit
                this.spawnAtLocation(worldserver, new ItemStack(Items.ARMADILLO_SCUTE));
                this.forceDrops = false; // CraftBukkit
                this.gameEvent(GameEvent.ENTITY_INTERACT);
                this.playSound(SoundEffects.ARMADILLO_BRUSH);
            }

            return true;
        }
    }

    public boolean canStayRolledUp() {
        return !this.isPanicking() && !this.isInLiquid() && !this.isLeashed() && !this.isPassenger() && !this.isVehicle();
    }

    @Override
    public boolean canFallInLove() {
        return super.canFallInLove() && !this.isScared();
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return this.isScared() ? null : SoundEffects.ARMADILLO_AMBIENT;
    }

    @Override
    protected void playEatingSound() {
        this.makeSound(SoundEffects.ARMADILLO_EAT);
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.ARMADILLO_DEATH;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return this.isScared() ? SoundEffects.ARMADILLO_HURT_REDUCED : SoundEffects.ARMADILLO_HURT;
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.ARMADILLO_STEP, 0.15F, 1.0F);
    }

    @Override
    public int getMaxHeadYRot() {
        return this.isScared() ? 0 : 32;
    }

    @Override
    protected EntityAIBodyControl createBodyControl() {
        return new EntityAIBodyControl(this) {
            @Override
            public void clientTick() {
                if (!Armadillo.this.isScared()) {
                    super.clientTick();
                }

            }
        };
    }

    public static enum a implements INamable {

        IDLE("idle", false, 0, 0) {
            @Override
            public boolean shouldHideInShell(long i) {
                return false;
            }
        },
        ROLLING("rolling", true, 10, 1) {
            @Override
            public boolean shouldHideInShell(long i) {
                return i > 5L;
            }
        },
        SCARED("scared", true, 50, 2) {
            @Override
            public boolean shouldHideInShell(long i) {
                return true;
            }
        },
        UNROLLING("unrolling", true, 30, 3) {
            @Override
            public boolean shouldHideInShell(long i) {
                return i < 26L;
            }
        };

        static final Codec<Armadillo.a> CODEC = INamable.<Armadillo.a>fromEnum(Armadillo.a::values);
        private static final IntFunction<Armadillo.a> BY_ID = ByIdMap.<Armadillo.a>continuous(Armadillo.a::id, values(), ByIdMap.a.ZERO);
        public static final StreamCodec<ByteBuf, Armadillo.a> STREAM_CODEC = ByteBufCodecs.idMapper(Armadillo.a.BY_ID, Armadillo.a::id);
        private final String name;
        private final boolean isThreatened;
        private final int animationDuration;
        private final int id;

        a(final String s, final boolean flag, final int i, final int j) {
            this.name = s;
            this.isThreatened = flag;
            this.animationDuration = i;
            this.id = j;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        private int id() {
            return this.id;
        }

        public abstract boolean shouldHideInShell(long i);

        public boolean isThreatened() {
            return this.isThreatened;
        }

        public int animationDuration() {
            return this.animationDuration;
        }
    }
}
