package net.minecraft.world.entity.animal;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.Particles;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.RandomSource;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityExperienceOrb;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IBlockLightAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
// CraftBukkit end

public abstract class EntityAnimal extends EntityAgeable {

    protected static final int PARENT_AGE_AFTER_BREEDING = 6000;
    private static final int DEFAULT_IN_LOVE_TIME = 0;
    public int inLove = 0;
    @Nullable
    public EntityReference<EntityPlayer> loveCause;
    public ItemStack breedItem; // CraftBukkit - Add breedItem variable

    protected EntityAnimal(EntityTypes<? extends EntityAnimal> entitytypes, World world) {
        super(entitytypes, world);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
    }

    public static AttributeProvider.Builder createAnimalAttributes() {
        return EntityInsentient.createMobAttributes().add(GenericAttributes.TEMPT_RANGE, 10.0D);
    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        super.customServerAiStep(worldserver);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        if (this.inLove > 0) {
            --this.inLove;
            if (this.inLove % 10 == 0) {
                double d0 = this.random.nextGaussian() * 0.02D;
                double d1 = this.random.nextGaussian() * 0.02D;
                double d2 = this.random.nextGaussian() * 0.02D;

                this.level().addParticle(Particles.HEART, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
            }
        }

    }

    @Override
    // CraftBukkit start - void -> boolean
    public boolean actuallyHurt(WorldServer worldserver, DamageSource damagesource, float f, EntityDamageEvent event) {
        boolean damageResult = super.actuallyHurt(worldserver, damagesource, f, event);
        if (!damageResult) {
            return false;
        }
        this.resetLove();
        return true;
        // CraftBukkit end
    }

    @Override
    public float getWalkTargetValue(BlockPosition blockposition, IWorldReader iworldreader) {
        return iworldreader.getBlockState(blockposition.below()).is(Blocks.GRASS_BLOCK) ? 10.0F : iworldreader.getPathfindingCostFromLightLevels(blockposition);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putInt("InLove", this.inLove);
        EntityReference.store(this.loveCause, valueoutput, "LoveCause");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.inLove = valueinput.getIntOr("InLove", 0);
        this.loveCause = EntityReference.<EntityPlayer>read(valueinput, "LoveCause");
    }

    public static boolean checkAnimalSpawnRules(EntityTypes<? extends EntityAnimal> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        boolean flag = EntitySpawnReason.ignoresLightRequirements(entityspawnreason) || isBrightEnoughToSpawn(generatoraccess, blockposition);

        return generatoraccess.getBlockState(blockposition.below()).is(TagsBlock.ANIMALS_SPAWNABLE_ON) && flag;
    }

    protected static boolean isBrightEnoughToSpawn(IBlockLightAccess iblocklightaccess, BlockPosition blockposition) {
        return iblocklightaccess.getRawBrightness(blockposition, 0) > 8;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return false;
    }

    @Override
    protected int getBaseExperienceReward(WorldServer worldserver) {
        return 1 + this.random.nextInt(3);
    }

    public abstract boolean isFood(ItemStack itemstack);

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (this.isFood(itemstack)) {
            int i = this.getAge();

            if (entityhuman instanceof EntityPlayer) {
                EntityPlayer entityplayer = (EntityPlayer) entityhuman;

                if (i == 0 && this.canFallInLove()) {
                    this.usePlayerItem(entityhuman, enumhand, itemstack);
                    this.setInLove(entityplayer);
                    this.playEatingSound();
                    return EnumInteractionResult.SUCCESS_SERVER;
                }
            }

            if (this.isBaby()) {
                this.usePlayerItem(entityhuman, enumhand, itemstack);
                this.ageUp(getSpeedUpSecondsWhenFeeding(-i), true);
                this.playEatingSound();
                return EnumInteractionResult.SUCCESS;
            }

            if (this.level().isClientSide) {
                return EnumInteractionResult.CONSUME;
            }
        }

        return super.mobInteract(entityhuman, enumhand);
    }

    protected void playEatingSound() {}

    protected void usePlayerItem(EntityHuman entityhuman, EnumHand enumhand, ItemStack itemstack) {
        int i = itemstack.getCount();
        UseRemainder useremainder = (UseRemainder) itemstack.get(DataComponents.USE_REMAINDER);

        itemstack.consume(1, entityhuman);
        if (useremainder != null) {
            boolean flag = entityhuman.hasInfiniteMaterials();

            Objects.requireNonNull(entityhuman);
            ItemStack itemstack1 = useremainder.convertIntoRemainder(itemstack, i, flag, entityhuman::handleExtraItemsCreatedOnUse);

            entityhuman.setItemInHand(enumhand, itemstack1);
        }

    }

    public boolean canFallInLove() {
        return this.inLove <= 0;
    }

    public void setInLove(@Nullable EntityHuman entityhuman) {
        // CraftBukkit start
        EntityEnterLoveModeEvent entityEnterLoveModeEvent = CraftEventFactory.callEntityEnterLoveModeEvent(entityhuman, this, 600);
        if (entityEnterLoveModeEvent.isCancelled()) {
            return;
        }
        this.inLove = entityEnterLoveModeEvent.getTicksInLove();
        // CraftBukkit end
        if (entityhuman instanceof EntityPlayer entityplayer) {
            this.loveCause = new EntityReference<EntityPlayer>(entityplayer);
        }
        this.breedItem = entityhuman.getInventory().getSelectedItem(); // CraftBukkit

        this.level().broadcastEntityEvent(this, (byte) 18);
    }

    public void setInLoveTime(int i) {
        this.inLove = i;
    }

    public int getInLoveTime() {
        return this.inLove;
    }

    @Nullable
    public EntityPlayer getLoveCause() {
        EntityReference entityreference = this.loveCause;
        World world = this.level();

        Objects.requireNonNull(world);
        return (EntityPlayer) EntityReference.get(entityreference, (uuid) -> (EntityPlayer) world.getPlayerByUUID(uuid), EntityPlayer.class); // CraftBukkit - decompile error
    }

    public boolean isInLove() {
        return this.inLove > 0;
    }

    public void resetLove() {
        this.inLove = 0;
    }

    public boolean canMate(EntityAnimal entityanimal) {
        return entityanimal == this ? false : (entityanimal.getClass() != this.getClass() ? false : this.isInLove() && entityanimal.isInLove());
    }

    public void spawnChildFromBreeding(WorldServer worldserver, EntityAnimal entityanimal) {
        EntityAgeable entityageable = this.getBreedOffspring(worldserver, entityanimal);

        if (entityageable != null) {
            entityageable.setBaby(true);
            entityageable.snapTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            // CraftBukkit start - call EntityBreedEvent
            EntityPlayer breeder = Optional.ofNullable(this.getLoveCause()).or(() -> {
                return Optional.ofNullable(entityanimal.getLoveCause());
            }).orElse(null);
            int experience = this.getRandom().nextInt(7) + 1;
            EntityBreedEvent entityBreedEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(entityageable, this, entityanimal, breeder, this.breedItem, experience);
            if (entityBreedEvent.isCancelled()) {
                return;
            }
            experience = entityBreedEvent.getExperience();
            this.finalizeSpawnChildFromBreeding(worldserver, entityanimal, entityageable, experience);
            worldserver.addFreshEntityWithPassengers(entityageable, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING);
            // CraftBukkit end
        }
    }

    public void finalizeSpawnChildFromBreeding(WorldServer worldserver, EntityAnimal entityanimal, @Nullable EntityAgeable entityageable) {
        // CraftBukkit start
        this.finalizeSpawnChildFromBreeding(worldserver, entityanimal, entityageable, this.getRandom().nextInt(7) + 1);
    }

    public void finalizeSpawnChildFromBreeding(WorldServer worldserver, EntityAnimal entityanimal, @Nullable EntityAgeable entityageable, int experience) {
        // CraftBukkit end
        Optional.ofNullable(this.getLoveCause()).or(() -> {
            return Optional.ofNullable(entityanimal.getLoveCause());
        }).ifPresent((entityplayer) -> {
            entityplayer.awardStat(StatisticList.ANIMALS_BRED);
            CriterionTriggers.BRED_ANIMALS.trigger(entityplayer, this, entityanimal, entityageable);
        });
        this.setAge(6000);
        entityanimal.setAge(6000);
        this.resetLove();
        entityanimal.resetLove();
        worldserver.broadcastEntityEvent(this, (byte) 18);
        if (worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            // CraftBukkit start - use event experience
            if (experience > 0) {
                worldserver.addFreshEntity(new EntityExperienceOrb(worldserver, this.getX(), this.getY(), this.getZ(), experience));
            }
            // CraftBukkit end
        }

    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 18) {
            for (int i = 0; i < 7; ++i) {
                double d0 = this.random.nextGaussian() * 0.02D;
                double d1 = this.random.nextGaussian() * 0.02D;
                double d2 = this.random.nextGaussian() * 0.02D;

                this.level().addParticle(Particles.HEART, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
            }
        } else {
            super.handleEntityEvent(b0);
        }

    }
}
