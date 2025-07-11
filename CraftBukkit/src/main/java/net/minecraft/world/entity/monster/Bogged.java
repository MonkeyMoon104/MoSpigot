package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.IShearable;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityArrow;
import net.minecraft.world.entity.projectile.EntityTippedArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.World;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTables;

public class Bogged extends EntitySkeletonAbstract implements IShearable {

    private static final int HARD_ATTACK_INTERVAL = 50;
    private static final int NORMAL_ATTACK_INTERVAL = 70;
    private static final DataWatcherObject<Boolean> DATA_SHEARED = DataWatcher.<Boolean>defineId(Bogged.class, DataWatcherRegistry.BOOLEAN);
    private static final String SHEARED_TAG_NAME = "sheared";
    private static final boolean DEFAULT_SHEARED = false;

    public static AttributeProvider.Builder createAttributes() {
        return EntitySkeletonAbstract.createAttributes().add(GenericAttributes.MAX_HEALTH, 16.0D);
    }

    public Bogged(EntityTypes<? extends Bogged> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(Bogged.DATA_SHEARED, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("sheared", this.isSheared());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setSheared(valueinput.getBooleanOr("sheared", false));
    }

    public boolean isSheared() {
        return (Boolean) this.entityData.get(Bogged.DATA_SHEARED);
    }

    public void setSheared(boolean flag) {
        this.entityData.set(Bogged.DATA_SHEARED, flag);
    }

    @Override
    protected EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.is(Items.SHEARS) && this.readyForShearing()) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                // CraftBukkit start
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerShearEntityEvent(entityhuman, this, itemstack, enumhand)) {
                    this.getEntityData().markDirty(Bogged.DATA_SHEARED); // CraftBukkit - mark dirty to restore sheared state to clients
                    return EnumInteractionResult.PASS;
                }
                // CraftBukkit end
                this.shear(worldserver, SoundCategory.PLAYERS, itemstack);
                this.gameEvent(GameEvent.SHEAR, entityhuman);
                itemstack.hurtAndBreak(1, entityhuman, getSlotForHand(enumhand));
            }

            return EnumInteractionResult.SUCCESS;
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.BOGGED_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.BOGGED_HURT;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.BOGGED_DEATH;
    }

    @Override
    protected SoundEffect getStepSound() {
        return SoundEffects.BOGGED_STEP;
    }

    @Override
    protected EntityArrow getArrow(ItemStack itemstack, float f, @Nullable ItemStack itemstack1) {
        EntityArrow entityarrow = super.getArrow(itemstack, f, itemstack1);

        if (entityarrow instanceof EntityTippedArrow entitytippedarrow) {
            entitytippedarrow.addEffect(new MobEffect(MobEffects.POISON, 100));
        }

        return entityarrow;
    }

    @Override
    protected int getHardAttackInterval() {
        return 50;
    }

    @Override
    protected int getAttackInterval() {
        return 70;
    }

    @Override
    public void shear(WorldServer worldserver, SoundCategory soundcategory, ItemStack itemstack) {
        worldserver.playSound((Entity) null, (Entity) this, SoundEffects.BOGGED_SHEAR, soundcategory, 1.0F, 1.0F);
        this.spawnShearedMushrooms(worldserver, itemstack);
        this.setSheared(true);
    }

    private void spawnShearedMushrooms(WorldServer worldserver, ItemStack itemstack) {
        this.dropFromShearingLootTable(worldserver, LootTables.BOGGED_SHEAR, itemstack, (worldserver1, itemstack1) -> {
            this.spawnAtLocation(worldserver1, itemstack1, this.getBbHeight());
        });
    }

    @Override
    public boolean readyForShearing() {
        return !this.isSheared() && this.isAlive();
    }
}
