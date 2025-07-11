package net.minecraft.world.entity.animal;

import java.util.List;
import net.minecraft.network.protocol.game.PacketPlayOutGameStateChange;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.World;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class EntityPufferFish extends EntityFish {

    private static final DataWatcherObject<Integer> PUFF_STATE = DataWatcher.<Integer>defineId(EntityPufferFish.class, DataWatcherRegistry.INT);
    int inflateCounter;
    int deflateTimer;
    private static final PathfinderTargetCondition.a SCARY_MOB = (entityliving, worldserver) -> {
        if (entityliving instanceof EntityHuman entityhuman) {
            if (entityhuman.isCreative()) {
                return false;
            }
        }

        return !entityliving.getType().is(TagsEntity.NOT_SCARY_FOR_PUFFERFISH);
    };
    static final PathfinderTargetCondition TARGETING_CONDITIONS = PathfinderTargetCondition.forNonCombat().ignoreInvisibilityTesting().ignoreLineOfSight().selector(EntityPufferFish.SCARY_MOB);
    public static final int STATE_SMALL = 0;
    public static final int STATE_MID = 1;
    public static final int STATE_FULL = 2;
    private static final int DEFAULT_PUFF_STATE = 0;

    public EntityPufferFish(EntityTypes<? extends EntityPufferFish> entitytypes, World world) {
        super(entitytypes, world);
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityPufferFish.PUFF_STATE, 0);
    }

    public int getPuffState() {
        return (Integer) this.entityData.get(EntityPufferFish.PUFF_STATE);
    }

    public void setPuffState(int i) {
        this.entityData.set(EntityPufferFish.PUFF_STATE, i);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntityPufferFish.PUFF_STATE.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putInt("PuffState", this.getPuffState());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setPuffState(Math.min(valueinput.getIntOr("PuffState", 0), 2));
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.PUFFERFISH_BUCKET);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new EntityPufferFish.a(this));
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && this.isEffectiveAi()) {
            if (this.inflateCounter > 0) {
                if (this.getPuffState() == 0) {
                    this.makeSound(SoundEffects.PUFFER_FISH_BLOW_UP);
                    this.setPuffState(1);
                } else if (this.inflateCounter > 40 && this.getPuffState() == 1) {
                    this.makeSound(SoundEffects.PUFFER_FISH_BLOW_UP);
                    this.setPuffState(2);
                }

                ++this.inflateCounter;
            } else if (this.getPuffState() != 0) {
                if (this.deflateTimer > 60 && this.getPuffState() == 2) {
                    this.makeSound(SoundEffects.PUFFER_FISH_BLOW_OUT);
                    this.setPuffState(1);
                } else if (this.deflateTimer > 100 && this.getPuffState() == 1) {
                    this.makeSound(SoundEffects.PUFFER_FISH_BLOW_OUT);
                    this.setPuffState(0);
                }

                ++this.deflateTimer;
            }
        }

        super.tick();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.isAlive() && this.getPuffState() > 0) {
                for (EntityInsentient entityinsentient : this.level().getEntitiesOfClass(EntityInsentient.class, this.getBoundingBox().inflate(0.3D), (entityinsentient1) -> {
                    return EntityPufferFish.TARGETING_CONDITIONS.test(worldserver, this, entityinsentient1);
                })) {
                    if (entityinsentient.isAlive()) {
                        this.touch(worldserver, entityinsentient);
                    }
                }
            }
        }

    }

    private void touch(WorldServer worldserver, EntityInsentient entityinsentient) {
        int i = this.getPuffState();

        if (entityinsentient.hurtServer(worldserver, this.damageSources().mobAttack(this), (float) (1 + i))) {
            entityinsentient.addEffect(new MobEffect(MobEffects.POISON, 60 * i, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            this.playSound(SoundEffects.PUFFER_FISH_STING, 1.0F, 1.0F);
        }

    }

    @Override
    public void playerTouch(EntityHuman entityhuman) {
        int i = this.getPuffState();

        if (entityhuman instanceof EntityPlayer entityplayer) {
            if (i > 0 && entityhuman.hurtServer(entityplayer.level(), this.damageSources().mobAttack(this), (float) (1 + i))) {
                if (!this.isSilent()) {
                    entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.PUFFER_FISH_STING, 0.0F));
                }

                entityhuman.addEffect(new MobEffect(MobEffects.POISON, 60 * i, 0), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            }
        }

    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.PUFFER_FISH_DEATH;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.PUFFER_FISH_HURT;
    }

    @Override
    protected SoundEffect getFlopSound() {
        return SoundEffects.PUFFER_FISH_FLOP;
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return super.getDefaultDimensions(entitypose).scale(getScale(this.getPuffState()));
    }

    private static float getScale(int i) {
        switch (i) {
            case 0:
                return 0.5F;
            case 1:
                return 0.7F;
            default:
                return 1.0F;
        }
    }

    private static class a extends PathfinderGoal {

        private final EntityPufferFish fish;

        public a(EntityPufferFish entitypufferfish) {
            this.fish = entitypufferfish;
        }

        @Override
        public boolean canUse() {
            List<EntityLiving> list = this.fish.level().<EntityLiving>getEntitiesOfClass(EntityLiving.class, this.fish.getBoundingBox().inflate(2.0D), (entityliving) -> {
                return EntityPufferFish.TARGETING_CONDITIONS.test(getServerLevel((Entity) this.fish), this.fish, entityliving);
            });

            return !list.isEmpty();
        }

        @Override
        public void start() {
            this.fish.inflateCounter = 1;
            this.fish.deflateTimer = 0;
        }

        @Override
        public void stop() {
            this.fish.inflateCounter = 0;
        }
    }
}
