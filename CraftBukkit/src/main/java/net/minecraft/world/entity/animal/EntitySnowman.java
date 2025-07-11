package net.minecraft.world.entity.animal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.IShearable;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoalArrowAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.monster.IMonster;
import net.minecraft.world.entity.monster.IRangedEntity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntitySnowball;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class EntitySnowman extends EntityGolem implements IShearable, IRangedEntity {

    private static final DataWatcherObject<Byte> DATA_PUMPKIN_ID = DataWatcher.<Byte>defineId(EntitySnowman.class, DataWatcherRegistry.BYTE);
    private static final byte PUMPKIN_FLAG = 16;
    private static final boolean DEFAULT_PUMPKIN = true;

    public EntitySnowman(EntityTypes<? extends EntitySnowman> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PathfinderGoalArrowAttack(this, 1.25D, 20, 10.0F));
        this.goalSelector.addGoal(2, new PathfinderGoalRandomStrollLand(this, 1.0D, 1.0000001E-5F));
        this.goalSelector.addGoal(3, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 6.0F));
        this.goalSelector.addGoal(4, new PathfinderGoalRandomLookaround(this));
        this.targetSelector.addGoal(1, new PathfinderGoalNearestAttackableTarget(this, EntityInsentient.class, 10, true, false, (entityliving, worldserver) -> {
            return entityliving instanceof IMonster;
        }));
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityInsentient.createMobAttributes().add(GenericAttributes.MAX_HEALTH, 4.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.2F);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntitySnowman.DATA_PUMPKIN_ID, (byte) 16);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putBoolean("Pumpkin", this.hasPumpkin());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setPumpkin(valueinput.getBooleanOr("Pumpkin", true));
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.level().getBiome(this.blockPosition()).is(BiomeTags.SNOW_GOLEM_MELTS)) {
                this.hurtServer(worldserver, this.damageSources().melting(), 1.0F); // CraftBukkit - DamageSources.ON_FIRE -> CraftEventFactory.MELTING
            }

            if (!worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                return;
            }

            IBlockData iblockdata = Blocks.SNOW.defaultBlockState();

            for (int i = 0; i < 4; ++i) {
                int j = MathHelper.floor(this.getX() + (double) ((float) (i % 2 * 2 - 1) * 0.25F));
                int k = MathHelper.floor(this.getY());
                int l = MathHelper.floor(this.getZ() + (double) ((float) (i / 2 % 2 * 2 - 1) * 0.25F));
                BlockPosition blockposition = new BlockPosition(j, k, l);

                if (this.level().getBlockState(blockposition).isAir() && iblockdata.canSurvive(this.level(), blockposition)) {
                    // CraftBukkit start
                    if (!CraftEventFactory.handleBlockFormEvent(this.level(), blockposition, iblockdata, this)) {
                        continue;
                    }
                    // CraftBukkit end
                    this.level().gameEvent(GameEvent.BLOCK_PLACE, blockposition, GameEvent.a.of(this, iblockdata));
                }
            }
        }

    }

    @Override
    public void performRangedAttack(EntityLiving entityliving, float f) {
        double d0 = entityliving.getX() - this.getX();
        double d1 = entityliving.getEyeY() - (double) 1.1F;
        double d2 = entityliving.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2) * (double) 0.2F;
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            ItemStack itemstack = new ItemStack(Items.SNOWBALL);

            IProjectile.spawnProjectile(new EntitySnowball(worldserver, this, itemstack), worldserver, itemstack, (entitysnowball) -> {
                entitysnowball.shoot(d0, d1 + d3 - entitysnowball.getY(), d2, 1.6F, 12.0F);
            });
        }

        this.playSound(SoundEffects.SNOW_GOLEM_SHOOT, 1.0F, 0.4F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    protected EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.is(Items.SHEARS) && this.readyForShearing()) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                // CraftBukkit start
                if (!CraftEventFactory.handlePlayerShearEntityEvent(entityhuman, this, itemstack, enumhand)) {
                    return EnumInteractionResult.PASS;
                }
                // CraftBukkit end
                this.shear(worldserver, SoundCategory.PLAYERS, itemstack);
                this.gameEvent(GameEvent.SHEAR, entityhuman);
                itemstack.hurtAndBreak(1, entityhuman, getSlotForHand(enumhand));
            }

            return EnumInteractionResult.SUCCESS;
        } else {
            return EnumInteractionResult.PASS;
        }
    }

    @Override
    public void shear(WorldServer worldserver, SoundCategory soundcategory, ItemStack itemstack) {
        worldserver.playSound((Entity) null, (Entity) this, SoundEffects.SNOW_GOLEM_SHEAR, soundcategory, 1.0F, 1.0F);
        this.setPumpkin(false);
        this.dropFromShearingLootTable(worldserver, LootTables.SHEAR_SNOW_GOLEM, itemstack, (worldserver1, itemstack1) -> {
            this.forceDrops = true; // CraftBukkit
            this.spawnAtLocation(worldserver1, itemstack1, this.getEyeHeight());
            this.forceDrops = false; // CraftBukkit
        });
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && this.hasPumpkin();
    }

    public boolean hasPumpkin() {
        return ((Byte) this.entityData.get(EntitySnowman.DATA_PUMPKIN_ID) & 16) != 0;
    }

    public void setPumpkin(boolean flag) {
        byte b0 = (Byte) this.entityData.get(EntitySnowman.DATA_PUMPKIN_ID);

        if (flag) {
            this.entityData.set(EntitySnowman.DATA_PUMPKIN_ID, (byte) (b0 | 16));
        } else {
            this.entityData.set(EntitySnowman.DATA_PUMPKIN_ID, (byte) (b0 & -17));
        }

    }

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.SNOW_GOLEM_AMBIENT;
    }

    @Nullable
    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.SNOW_GOLEM_HURT;
    }

    @Nullable
    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.SNOW_GOLEM_DEATH;
    }

    @Override
    public Vec3D getLeashOffset() {
        return new Vec3D(0.0D, (double) (0.75F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }
}
