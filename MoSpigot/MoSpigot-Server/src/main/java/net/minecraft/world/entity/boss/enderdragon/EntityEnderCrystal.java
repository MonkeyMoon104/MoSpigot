package net.minecraft.world.entity.boss.enderdragon;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockFireAbstract;
import net.minecraft.world.level.dimension.end.EnderDragonBattle;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class EntityEnderCrystal extends Entity {

    private static final DataWatcherObject<Optional<BlockPosition>> DATA_BEAM_TARGET = DataWatcher.<Optional<BlockPosition>>defineId(EntityEnderCrystal.class, DataWatcherRegistry.OPTIONAL_BLOCK_POS);
    private static final DataWatcherObject<Boolean> DATA_SHOW_BOTTOM = DataWatcher.<Boolean>defineId(EntityEnderCrystal.class, DataWatcherRegistry.BOOLEAN);
    private static final boolean DEFAULT_SHOW_BOTTOM = true;
    public int time;

    public EntityEnderCrystal(EntityTypes<? extends EntityEnderCrystal> entitytypes, World world) {
        super(entitytypes, world);
        this.blocksBuilding = true;
        this.time = this.random.nextInt(100000);
    }

    public EntityEnderCrystal(World world, double d0, double d1, double d2) {
        this(EntityTypes.END_CRYSTAL, world);
        this.setPos(d0, d1, d2);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        datawatcher_a.define(EntityEnderCrystal.DATA_BEAM_TARGET, Optional.empty());
        datawatcher_a.define(EntityEnderCrystal.DATA_SHOW_BOTTOM, true);
    }

    @Override
    public void tick() {
        ++this.time;
        this.applyEffectsFromBlocks();
        this.handlePortal();
        if (this.level() instanceof WorldServer) {
            BlockPosition blockposition = this.blockPosition();

            if (((WorldServer) this.level()).getDragonFight() != null && this.level().getBlockState(blockposition).isAir()) {
                // CraftBukkit start
                if (!CraftEventFactory.callBlockIgniteEvent(this.level(), blockposition, this).isCancelled()) {
                    this.level().setBlockAndUpdate(blockposition, BlockFireAbstract.getState(this.level(), blockposition));
                }
                // CraftBukkit end
            }
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        valueoutput.storeNullable("beam_target", BlockPosition.CODEC, this.getBeamTarget());
        valueoutput.putBoolean("ShowBottom", this.showsBottom());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        this.setBeamTarget((BlockPosition) valueinput.read("beam_target", BlockPosition.CODEC).orElse(null)); // CraftBukkit - decompile error
        this.setShowBottom(valueinput.getBooleanOr("ShowBottom", true));
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public final boolean hurtClient(DamageSource damagesource) {
        return this.isInvulnerableToBase(damagesource) ? false : !(damagesource.getEntity() instanceof EntityEnderDragon);
    }

    @Override
    public final boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableToBase(damagesource)) {
            return false;
        } else if (damagesource.getEntity() instanceof EntityEnderDragon) {
            return false;
        } else {
            if (!this.isRemoved()) {
                // CraftBukkit start - All non-living entities need this
                if (CraftEventFactory.handleNonLivingEntityDamageEvent(this, damagesource, f, false)) {
                    return false;
                }
                // CraftBukkit end
                if (!damagesource.is(DamageTypeTags.IS_EXPLOSION)) {
                    DamageSource damagesource1 = damagesource.getEntity() != null ? this.damageSources().explosion(this, damagesource.getEntity()) : null;

                    // CraftBukkit start
                    ExplosionPrimeEvent event = CraftEventFactory.callExplosionPrimeEvent(this, 6.0F, false);
                    if (event.isCancelled()) {
                        return false;
                    }

                    this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
                    worldserver.explode(this, damagesource1, (ExplosionDamageCalculator) null, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), World.a.BLOCK);
                } else {
                    this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
                }
                // CraftBukkit end

                this.onDestroyedBy(worldserver, damagesource);
            }

            return true;
        }
    }

    @Override
    public void kill(WorldServer worldserver) {
        this.onDestroyedBy(worldserver, this.damageSources().generic());
        super.kill(worldserver);
    }

    private void onDestroyedBy(WorldServer worldserver, DamageSource damagesource) {
        EnderDragonBattle enderdragonbattle = worldserver.getDragonFight();

        if (enderdragonbattle != null) {
            enderdragonbattle.onCrystalDestroyed(this, damagesource);
        }

    }

    public void setBeamTarget(@Nullable BlockPosition blockposition) {
        this.getEntityData().set(EntityEnderCrystal.DATA_BEAM_TARGET, Optional.ofNullable(blockposition));
    }

    @Nullable
    public BlockPosition getBeamTarget() {
        return (BlockPosition) ((Optional) this.getEntityData().get(EntityEnderCrystal.DATA_BEAM_TARGET)).orElse((Object) null);
    }

    public void setShowBottom(boolean flag) {
        this.getEntityData().set(EntityEnderCrystal.DATA_SHOW_BOTTOM, flag);
    }

    public boolean showsBottom() {
        return (Boolean) this.getEntityData().get(EntityEnderCrystal.DATA_SHOW_BOTTOM);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d0) {
        return super.shouldRenderAtSqrDistance(d0) || this.getBeamTarget() != null;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.END_CRYSTAL);
    }
}
