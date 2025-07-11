package net.minecraft.world.entity.boss.enderdragon;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.MathHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityExperienceOrb;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.boss.EntityComplexPart;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonControllerManager;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonControllerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.IDragonController;
import net.minecraft.world.entity.monster.IMonster;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.dimension.end.EnderDragonBattle;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.levelgen.feature.WorldGenEndTrophy;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathEntity;
import net.minecraft.world.level.pathfinder.PathPoint;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTableInfo;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameters;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class EntityEnderDragon extends EntityInsentient implements IMonster {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DataWatcherObject<Integer> DATA_PHASE = DataWatcher.<Integer>defineId(EntityEnderDragon.class, DataWatcherRegistry.INT);
    private static final PathfinderTargetCondition CRYSTAL_DESTROY_TARGETING = PathfinderTargetCondition.forCombat().range(64.0D);
    private static final int GROWL_INTERVAL_MIN = 200;
    private static final int GROWL_INTERVAL_MAX = 400;
    private static final float SITTING_ALLOWED_DAMAGE_PERCENTAGE = 0.25F;
    private static final String DRAGON_DEATH_TIME_KEY = "DragonDeathTime";
    private static final String DRAGON_PHASE_KEY = "DragonPhase";
    private static final int DEFAULT_DEATH_TIME = 0;
    public final DragonFlightHistory flightHistory = new DragonFlightHistory();
    public final EntityComplexPart[] subEntities;
    public final EntityComplexPart head;
    private final EntityComplexPart neck;
    private final EntityComplexPart body;
    private final EntityComplexPart tail1;
    private final EntityComplexPart tail2;
    private final EntityComplexPart tail3;
    private final EntityComplexPart wing1;
    private final EntityComplexPart wing2;
    public float oFlapTime;
    public float flapTime;
    public boolean inWall;
    public int dragonDeathTime = 0;
    public float yRotA;
    @Nullable
    public EntityEnderCrystal nearestCrystal;
    @Nullable
    private EnderDragonBattle dragonFight;
    private BlockPosition fightOrigin;
    private final DragonControllerManager phaseManager;
    private int growlTime;
    private float sittingDamageReceived;
    private final PathPoint[] nodes;
    private final int[] nodeAdjacency;
    private final Path openSet;
    private final Explosion explosionSource; // CraftBukkit - reusable source for CraftTNTPrimed.getSource()

    public EntityEnderDragon(EntityTypes<? extends EntityEnderDragon> entitytypes, World world) {
        super(EntityTypes.ENDER_DRAGON, world);
        this.fightOrigin = BlockPosition.ZERO;
        this.growlTime = 100;
        this.nodes = new PathPoint[24];
        this.nodeAdjacency = new int[24];
        this.openSet = new Path();
        this.head = new EntityComplexPart(this, "head", 1.0F, 1.0F);
        this.neck = new EntityComplexPart(this, "neck", 3.0F, 3.0F);
        this.body = new EntityComplexPart(this, "body", 5.0F, 3.0F);
        this.tail1 = new EntityComplexPart(this, "tail", 2.0F, 2.0F);
        this.tail2 = new EntityComplexPart(this, "tail", 2.0F, 2.0F);
        this.tail3 = new EntityComplexPart(this, "tail", 2.0F, 2.0F);
        this.wing1 = new EntityComplexPart(this, "wing", 4.0F, 2.0F);
        this.wing2 = new EntityComplexPart(this, "wing", 4.0F, 2.0F);
        this.subEntities = new EntityComplexPart[]{this.head, this.neck, this.body, this.tail1, this.tail2, this.tail3, this.wing1, this.wing2};
        this.setHealth(this.getMaxHealth());
        this.noPhysics = true;
        this.phaseManager = new DragonControllerManager(this);
        this.explosionSource = new ServerExplosion(world.getMinecraftWorld(), this, null, null, new Vec3D(Double.NaN, Double.NaN, Double.NaN), Float.NaN, true, Explosion.Effect.DESTROY); // CraftBukkit
    }

    public void setDragonFight(EnderDragonBattle enderdragonbattle) {
        this.dragonFight = enderdragonbattle;
    }

    public void setFightOrigin(BlockPosition blockposition) {
        this.fightOrigin = blockposition;
    }

    public BlockPosition getFightOrigin() {
        return this.fightOrigin;
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityInsentient.createMobAttributes().add(GenericAttributes.MAX_HEALTH, 200.0D).add(GenericAttributes.CAMERA_DISTANCE, 16.0D);
    }

    @Override
    public boolean isFlapping() {
        float f = MathHelper.cos(this.flapTime * ((float) Math.PI * 2F));
        float f1 = MathHelper.cos(this.oFlapTime * ((float) Math.PI * 2F));

        return f1 <= -0.3F && f >= -0.3F;
    }

    @Override
    public void onFlap() {
        if (this.level().isClientSide && !this.isSilent()) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEffects.ENDER_DRAGON_FLAP, this.getSoundSource(), 5.0F, 0.8F + this.random.nextFloat() * 0.3F, false);
        }

    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityEnderDragon.DATA_PHASE, DragonControllerPhase.HOVERING.getId());
    }

    @Override
    public void aiStep() {
        this.processFlappingMovement();
        if (this.level().isClientSide) {
            this.setHealth(this.getHealth());
            if (!this.isSilent() && !this.phaseManager.getCurrentPhase().isSitting() && --this.growlTime < 0) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEffects.ENDER_DRAGON_GROWL, this.getSoundSource(), 2.5F, 0.8F + this.random.nextFloat() * 0.3F, false);
                this.growlTime = 200 + this.random.nextInt(200);
            }
        }

        if (this.dragonFight == null) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;
                EnderDragonBattle enderdragonbattle = worldserver.getDragonFight();

                if (enderdragonbattle != null && this.getUUID().equals(enderdragonbattle.getDragonUUID())) {
                    this.dragonFight = enderdragonbattle;
                }
            }
        }

        this.oFlapTime = this.flapTime;
        if (this.isDeadOrDying()) {
            float f = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;

            this.level().addParticle(Particles.EXPLOSION, this.getX() + (double) f, this.getY() + 2.0D + (double) f1, this.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        } else {
            this.checkCrystals();
            Vec3D vec3d = this.getDeltaMovement();
            float f3 = 0.2F / ((float) vec3d.horizontalDistance() * 10.0F + 1.0F);

            f3 *= (float) Math.pow(2.0D, vec3d.y);
            if (this.phaseManager.getCurrentPhase().isSitting()) {
                this.flapTime += 0.1F;
            } else if (this.inWall) {
                this.flapTime += f3 * 0.5F;
            } else {
                this.flapTime += f3;
            }

            this.setYRot(MathHelper.wrapDegrees(this.getYRot()));
            if (this.isNoAi()) {
                this.flapTime = 0.5F;
            } else {
                this.flightHistory.record(this.getY(), this.getYRot());
                World world1 = this.level();

                if (world1 instanceof WorldServer) {
                    WorldServer worldserver1 = (WorldServer) world1;
                    IDragonController idragoncontroller = this.phaseManager.getCurrentPhase();

                    idragoncontroller.doServerTick(worldserver1);
                    if (this.phaseManager.getCurrentPhase() != idragoncontroller) {
                        idragoncontroller = this.phaseManager.getCurrentPhase();
                        idragoncontroller.doServerTick(worldserver1);
                    }

                    Vec3D vec3d1 = idragoncontroller.getFlyTargetLocation();

                    if (vec3d1 != null && idragoncontroller.getPhase() != DragonControllerPhase.HOVERING) { // CraftBukkit - Don't move when hovering
                        double d0 = vec3d1.x - this.getX();
                        double d1 = vec3d1.y - this.getY();
                        double d2 = vec3d1.z - this.getZ();
                        double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                        float f4 = idragoncontroller.getFlySpeed();
                        double d4 = Math.sqrt(d0 * d0 + d2 * d2);

                        if (d4 > 0.0D) {
                            d1 = MathHelper.clamp(d1 / d4, (double) (-f4), (double) f4);
                        }

                        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, d1 * 0.01D, 0.0D));
                        this.setYRot(MathHelper.wrapDegrees(this.getYRot()));
                        Vec3D vec3d2 = vec3d1.subtract(this.getX(), this.getY(), this.getZ()).normalize();
                        Vec3D vec3d3 = (new Vec3D((double) MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F)), this.getDeltaMovement().y, (double) (-MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F))))).normalize();
                        float f5 = Math.max(((float) vec3d3.dot(vec3d2) + 0.5F) / 1.5F, 0.0F);

                        if (Math.abs(d0) > (double) 1.0E-5F || Math.abs(d2) > (double) 1.0E-5F) {
                            float f6 = MathHelper.clamp(MathHelper.wrapDegrees(180.0F - (float) MathHelper.atan2(d0, d2) * (180F / (float) Math.PI) - this.getYRot()), -50.0F, 50.0F);

                            this.yRotA *= 0.8F;
                            this.yRotA += f6 * idragoncontroller.getTurnSpeed();
                            this.setYRot(this.getYRot() + this.yRotA * 0.1F);
                        }

                        float f7 = (float) (2.0D / (d3 + 1.0D));
                        float f8 = 0.06F;

                        this.moveRelative(0.06F * (f5 * f7 + (1.0F - f7)), new Vec3D(0.0D, 0.0D, -1.0D));
                        if (this.inWall) {
                            this.move(EnumMoveType.SELF, this.getDeltaMovement().scale((double) 0.8F));
                        } else {
                            this.move(EnumMoveType.SELF, this.getDeltaMovement());
                        }

                        Vec3D vec3d4 = this.getDeltaMovement().normalize();
                        double d5 = 0.8D + 0.15D * (vec3d4.dot(vec3d3) + 1.0D) / 2.0D;

                        this.setDeltaMovement(this.getDeltaMovement().multiply(d5, (double) 0.91F, d5));
                    }
                } else {
                    this.interpolation.interpolate();
                    this.phaseManager.getCurrentPhase().doClientTick();
                }

                if (!this.level().isClientSide()) {
                    this.applyEffectsFromBlocks();
                }

                this.yBodyRot = this.getYRot();
                Vec3D[] avec3d = new Vec3D[this.subEntities.length];

                for (int i = 0; i < this.subEntities.length; ++i) {
                    avec3d[i] = new Vec3D(this.subEntities[i].getX(), this.subEntities[i].getY(), this.subEntities[i].getZ());
                }

                float f9 = (float) (this.flightHistory.get(5).y() - this.flightHistory.get(10).y()) * 10.0F * ((float) Math.PI / 180F);
                float f10 = MathHelper.cos(f9);
                float f11 = MathHelper.sin(f9);
                float f12 = this.getYRot() * ((float) Math.PI / 180F);
                float f13 = MathHelper.sin(f12);
                float f14 = MathHelper.cos(f12);

                this.tickPart(this.body, (double) (f13 * 0.5F), 0.0D, (double) (-f14 * 0.5F));
                this.tickPart(this.wing1, (double) (f14 * 4.5F), 2.0D, (double) (f13 * 4.5F));
                this.tickPart(this.wing2, (double) (f14 * -4.5F), 2.0D, (double) (f13 * -4.5F));
                World world2 = this.level();

                if (world2 instanceof WorldServer) {
                    WorldServer worldserver2 = (WorldServer) world2;

                    if (this.hurtTime == 0) {
                        this.knockBack(worldserver2, worldserver2.getEntities(this, this.wing1.getBoundingBox().inflate(4.0D, 2.0D, 4.0D).move(0.0D, -2.0D, 0.0D), IEntitySelector.NO_CREATIVE_OR_SPECTATOR));
                        this.knockBack(worldserver2, worldserver2.getEntities(this, this.wing2.getBoundingBox().inflate(4.0D, 2.0D, 4.0D).move(0.0D, -2.0D, 0.0D), IEntitySelector.NO_CREATIVE_OR_SPECTATOR));
                        this.hurt(worldserver2, worldserver2.getEntities(this, this.head.getBoundingBox().inflate(1.0D), IEntitySelector.NO_CREATIVE_OR_SPECTATOR));
                        this.hurt(worldserver2, worldserver2.getEntities(this, this.neck.getBoundingBox().inflate(1.0D), IEntitySelector.NO_CREATIVE_OR_SPECTATOR));
                    }
                }

                float f15 = MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F) - this.yRotA * 0.01F);
                float f16 = MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F) - this.yRotA * 0.01F);
                float f17 = this.getHeadYOffset();

                this.tickPart(this.head, (double) (f15 * 6.5F * f10), (double) (f17 + f11 * 6.5F), (double) (-f16 * 6.5F * f10));
                this.tickPart(this.neck, (double) (f15 * 5.5F * f10), (double) (f17 + f11 * 5.5F), (double) (-f16 * 5.5F * f10));
                DragonFlightHistory.a dragonflighthistory_a = this.flightHistory.get(5);

                for (int j = 0; j < 3; ++j) {
                    EntityComplexPart entitycomplexpart = null;

                    if (j == 0) {
                        entitycomplexpart = this.tail1;
                    }

                    if (j == 1) {
                        entitycomplexpart = this.tail2;
                    }

                    if (j == 2) {
                        entitycomplexpart = this.tail3;
                    }

                    DragonFlightHistory.a dragonflighthistory_a1 = this.flightHistory.get(12 + j * 2);
                    float f18 = this.getYRot() * ((float) Math.PI / 180F) + this.rotWrap((double) (dragonflighthistory_a1.yRot() - dragonflighthistory_a.yRot())) * ((float) Math.PI / 180F);
                    float f19 = MathHelper.sin(f18);
                    float f20 = MathHelper.cos(f18);
                    float f21 = 1.5F;
                    float f22 = (float) (j + 1) * 2.0F;

                    this.tickPart(entitycomplexpart, (double) (-(f13 * 1.5F + f19 * f22) * f10), dragonflighthistory_a1.y() - dragonflighthistory_a.y() - (double) ((f22 + 1.5F) * f11) + 1.5D, (double) ((f14 * 1.5F + f20 * f22) * f10));
                }

                World world3 = this.level();

                if (world3 instanceof WorldServer) {
                    WorldServer worldserver3 = (WorldServer) world3;

                    this.inWall = this.checkWalls(worldserver3, this.head.getBoundingBox()) | this.checkWalls(worldserver3, this.neck.getBoundingBox()) | this.checkWalls(worldserver3, this.body.getBoundingBox());
                    if (this.dragonFight != null) {
                        this.dragonFight.updateDragon(this);
                    }
                }

                for (int k = 0; k < this.subEntities.length; ++k) {
                    this.subEntities[k].xo = avec3d[k].x;
                    this.subEntities[k].yo = avec3d[k].y;
                    this.subEntities[k].zo = avec3d[k].z;
                    this.subEntities[k].xOld = avec3d[k].x;
                    this.subEntities[k].yOld = avec3d[k].y;
                    this.subEntities[k].zOld = avec3d[k].z;
                }

            }
        }
    }

    private void tickPart(EntityComplexPart entitycomplexpart, double d0, double d1, double d2) {
        entitycomplexpart.setPos(this.getX() + d0, this.getY() + d1, this.getZ() + d2);
    }

    private float getHeadYOffset() {
        if (this.phaseManager.getCurrentPhase().isSitting()) {
            return -1.0F;
        } else {
            DragonFlightHistory.a dragonflighthistory_a = this.flightHistory.get(5);
            DragonFlightHistory.a dragonflighthistory_a1 = this.flightHistory.get(0);

            return (float) (dragonflighthistory_a.y() - dragonflighthistory_a1.y());
        }
    }

    private void checkCrystals() {
        if (this.nearestCrystal != null) {
            if (this.nearestCrystal.isRemoved()) {
                this.nearestCrystal = null;
            } else if (this.tickCount % 10 == 0 && this.getHealth() < this.getMaxHealth()) {
                // CraftBukkit start
                EntityRegainHealthEvent event = new EntityRegainHealthEvent(this.getBukkitEntity(), 1.0F, EntityRegainHealthEvent.RegainReason.ENDER_CRYSTAL);
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    this.setHealth((float) (this.getHealth() + event.getAmount()));
                }
                // CraftBukkit end
            }
        }

        if (this.random.nextInt(10) == 0) {
            List<EntityEnderCrystal> list = this.level().<EntityEnderCrystal>getEntitiesOfClass(EntityEnderCrystal.class, this.getBoundingBox().inflate(32.0D));
            EntityEnderCrystal entityendercrystal = null;
            double d0 = Double.MAX_VALUE;

            for (EntityEnderCrystal entityendercrystal1 : list) {
                double d1 = entityendercrystal1.distanceToSqr((Entity) this);

                if (d1 < d0) {
                    d0 = d1;
                    entityendercrystal = entityendercrystal1;
                }
            }

            this.nearestCrystal = entityendercrystal;
        }

    }

    private void knockBack(WorldServer worldserver, List<Entity> list) {
        double d0 = (this.body.getBoundingBox().minX + this.body.getBoundingBox().maxX) / 2.0D;
        double d1 = (this.body.getBoundingBox().minZ + this.body.getBoundingBox().maxZ) / 2.0D;

        for (Entity entity : list) {
            if (entity instanceof EntityLiving entityliving) {
                double d2 = entity.getX() - d0;
                double d3 = entity.getZ() - d1;
                double d4 = Math.max(d2 * d2 + d3 * d3, 0.1D);

                entity.push(d2 / d4 * 4.0D, (double) 0.2F, d3 / d4 * 4.0D);
                if (!this.phaseManager.getCurrentPhase().isSitting() && entityliving.getLastHurtByMobTimestamp() < entity.tickCount - 2) {
                    DamageSource damagesource = this.damageSources().mobAttack(this);

                    entity.hurtServer(worldserver, damagesource, 5.0F);
                    EnchantmentManager.doPostAttackEffects(worldserver, entity, damagesource);
                }
            }
        }

    }

    private void hurt(WorldServer worldserver, List<Entity> list) {
        for (Entity entity : list) {
            if (entity instanceof EntityLiving) {
                DamageSource damagesource = this.damageSources().mobAttack(this);

                entity.hurtServer(worldserver, damagesource, 10.0F);
                EnchantmentManager.doPostAttackEffects(worldserver, entity, damagesource);
            }
        }

    }

    private float rotWrap(double d0) {
        return (float) MathHelper.wrapDegrees(d0);
    }

    private boolean checkWalls(WorldServer worldserver, AxisAlignedBB axisalignedbb) {
        int i = MathHelper.floor(axisalignedbb.minX);
        int j = MathHelper.floor(axisalignedbb.minY);
        int k = MathHelper.floor(axisalignedbb.minZ);
        int l = MathHelper.floor(axisalignedbb.maxX);
        int i1 = MathHelper.floor(axisalignedbb.maxY);
        int j1 = MathHelper.floor(axisalignedbb.maxZ);
        boolean flag = false;
        boolean flag1 = false;
        // CraftBukkit start - Create a list to hold all the destroyed blocks
        List<org.bukkit.block.Block> destroyedBlocks = new java.util.ArrayList<org.bukkit.block.Block>();
        // CraftBukkit end

        for (int k1 = i; k1 <= l; ++k1) {
            for (int l1 = j; l1 <= i1; ++l1) {
                for (int i2 = k; i2 <= j1; ++i2) {
                    BlockPosition blockposition = new BlockPosition(k1, l1, i2);
                    IBlockData iblockdata = worldserver.getBlockState(blockposition);

                    if (!iblockdata.isAir() && !iblockdata.is(TagsBlock.DRAGON_TRANSPARENT)) {
                        if (worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && !iblockdata.is(TagsBlock.DRAGON_IMMUNE)) {
                            // CraftBukkit start - Add blocks to list rather than destroying them
                            // flag1 = worldserver.removeBlock(blockposition, false) || flag1;
                            flag1 = true;
                            destroyedBlocks.add(CraftBlock.at(worldserver, blockposition));
                            // CraftBukkit end
                        } else {
                            flag = true;
                        }
                    }
                }
            }
        }

        // CraftBukkit start - Set off an EntityExplodeEvent for the dragon exploding all these blocks
        // SPIGOT-4882: don't fire event if nothing hit
        if (!flag1) {
            return flag;
        }

        EntityExplodeEvent event = CraftEventFactory.callEntityExplodeEvent(this, destroyedBlocks, 0F, explosionSource.getBlockInteraction());
        if (event.isCancelled()) {
            // This flag literally means 'Dragon hit something hard' (Obsidian, White Stone or Bedrock) and will cause the dragon to slow down.
            // We should consider adding an event extension for it, or perhaps returning true if the event is cancelled.
            return flag;
        } else if (event.getYield() == 0F) {
            // Yield zero ==> no drops
            for (org.bukkit.block.Block block : event.blockList()) {
                this.level().removeBlock(new BlockPosition(block.getX(), block.getY(), block.getZ()), false);
            }
        } else {
            for (org.bukkit.block.Block block : event.blockList()) {
                org.bukkit.Material blockId = block.getType();
                if (blockId.isAir()) {
                    continue;
                }

                CraftBlock craftBlock = ((CraftBlock) block);
                BlockPosition blockposition = craftBlock.getPosition();

                Block nmsBlock = craftBlock.getNMS().getBlock();
                if (nmsBlock.dropFromExplosion(explosionSource)) {
                    TileEntity tileentity = craftBlock.getNMS().hasBlockEntity() ? this.level().getBlockEntity(blockposition) : null;
                    LootParams.a loottableinfo_builder = (new LootParams.a((WorldServer) this.level())).withParameter(LootContextParameters.ORIGIN, Vec3D.atCenterOf(blockposition)).withParameter(LootContextParameters.TOOL, ItemStack.EMPTY).withParameter(LootContextParameters.EXPLOSION_RADIUS, 1.0F / event.getYield()).withOptionalParameter(LootContextParameters.BLOCK_ENTITY, tileentity);

                    craftBlock.getNMS().getDrops(loottableinfo_builder).forEach((itemstack) -> {
                        Block.popResource(this.level(), blockposition, itemstack);
                    });
                    craftBlock.getNMS().spawnAfterBreak((WorldServer) this.level(), blockposition, ItemStack.EMPTY, false);
                }
                nmsBlock.wasExploded((WorldServer) this.level(), blockposition, explosionSource);

                this.level().removeBlock(blockposition, false);
            }
        }
        // CraftBukkit end

        if (flag1) {
            BlockPosition blockposition1 = new BlockPosition(i + this.random.nextInt(l - i + 1), j + this.random.nextInt(i1 - j + 1), k + this.random.nextInt(j1 - k + 1));

            worldserver.levelEvent(2008, blockposition1, 0);
        }

        return flag;
    }

    public boolean hurt(WorldServer worldserver, EntityComplexPart entitycomplexpart, DamageSource damagesource, float f) {
        if (this.phaseManager.getCurrentPhase().getPhase() == DragonControllerPhase.DYING) {
            return false;
        } else {
            f = this.phaseManager.getCurrentPhase().onHurt(damagesource, f);
            if (entitycomplexpart != this.head) {
                f = f / 4.0F + Math.min(f, 1.0F);
            }

            if (f < 0.01F) {
                return false;
            } else {
                if (damagesource.getEntity() instanceof EntityHuman || damagesource.is(DamageTypeTags.ALWAYS_HURTS_ENDER_DRAGONS)) {
                    float f1 = this.getHealth();

                    this.reallyHurt(worldserver, damagesource, f);
                    if (this.isDeadOrDying() && !this.phaseManager.getCurrentPhase().isSitting()) {
                        this.setHealth(1.0F);
                        this.phaseManager.setPhase(DragonControllerPhase.DYING);
                    }

                    if (this.phaseManager.getCurrentPhase().isSitting()) {
                        this.sittingDamageReceived = this.sittingDamageReceived + f1 - this.getHealth();
                        if (this.sittingDamageReceived > 0.25F * this.getMaxHealth()) {
                            this.sittingDamageReceived = 0.0F;
                            this.phaseManager.setPhase(DragonControllerPhase.TAKEOFF);
                        }
                    }
                }

                return true;
            }
        }
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        return this.hurt(worldserver, this.body, damagesource, f);
    }

    protected void reallyHurt(WorldServer worldserver, DamageSource damagesource, float f) {
        super.hurtServer(worldserver, damagesource, f);
    }

    @Override
    public void kill(WorldServer worldserver) {
        this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        this.gameEvent(GameEvent.ENTITY_DIE);
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
            this.dragonFight.setDragonKilled(this);
        }

    }

    // CraftBukkit start - SPIGOT-2420: Special case, the ender dragon drops 12000 xp for the first kill and 500 xp for every other kill and this over time.
    @Override
    public int getExpReward(WorldServer worldserver, Entity entity) {
        // CraftBukkit - Moved from #tickDeath method
        boolean flag = worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        short short0 = 500;

        if (this.dragonFight != null && !this.dragonFight.hasPreviouslyKilledDragon()) {
            short0 = 12000;
        }

        return flag ? short0 : 0;
    }
    // CraftBukkit end

    @Override
    protected void tickDeath() {
        if (this.dragonFight != null) {
            this.dragonFight.updateDragon(this);
        }

        ++this.dragonDeathTime;
        if (this.dragonDeathTime >= 180 && this.dragonDeathTime <= 200) {
            float f = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f1 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f2 = (this.random.nextFloat() - 0.5F) * 8.0F;

            this.level().addParticle(Particles.EXPLOSION_EMITTER, this.getX() + (double) f, this.getY() + 2.0D + (double) f1, this.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        }

        // CraftBukkit start - SPIGOT-2420: Moved up to #getExpReward method
        /*
        int i = 500;

        if (this.dragonFight != null && !this.dragonFight.hasPreviouslyKilledDragon()) {
            i = 12000;
        }
        */
        int i = expToDrop;
        // CraftBukkit end

        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.dragonDeathTime > 150 && this.dragonDeathTime % 5 == 0 && true) {  // CraftBukkit - SPIGOT-2420: Already checked for the game rule when calculating the xp
                EntityExperienceOrb.award(worldserver, this.position(), MathHelper.floor((float) i * 0.08F));
            }

            if (this.dragonDeathTime == 1 && !this.isSilent()) {
                worldserver.globalLevelEvent(1028, this.blockPosition(), 0);
            }
        }

        Vec3D vec3d = new Vec3D(0.0D, (double) 0.1F, 0.0D);

        this.move(EnumMoveType.SELF, vec3d);

        for (EntityComplexPart entitycomplexpart : this.subEntities) {
            entitycomplexpart.setOldPosAndRot();
            entitycomplexpart.setPos(entitycomplexpart.position().add(vec3d));
        }

        if (this.dragonDeathTime == 200) {
            World world1 = this.level();

            if (world1 instanceof WorldServer) {
                WorldServer worldserver1 = (WorldServer) world1;

                if (true) { // CraftBukkit - SPIGOT-2420: Already checked for the game rule when calculating the xp
                    EntityExperienceOrb.award(worldserver1, this.position(), MathHelper.floor((float) i * 0.2F));
                }

                if (this.dragonFight != null) {
                    this.dragonFight.setDragonKilled(this);
                }

                this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
                this.gameEvent(GameEvent.ENTITY_DIE);
            }
        }

    }

    public int findClosestNode() {
        if (this.nodes[0] == null) {
            for (int i = 0; i < 24; ++i) {
                int j = 5;
                int k;
                int l;

                if (i < 12) {
                    k = MathHelper.floor(60.0F * MathHelper.cos(2.0F * (-(float) Math.PI + 0.2617994F * (float) i)));
                    l = MathHelper.floor(60.0F * MathHelper.sin(2.0F * (-(float) Math.PI + 0.2617994F * (float) i)));
                } else if (i < 20) {
                    int i1 = i - 12;

                    k = MathHelper.floor(40.0F * MathHelper.cos(2.0F * (-(float) Math.PI + ((float) Math.PI / 8F) * (float) i1)));
                    l = MathHelper.floor(40.0F * MathHelper.sin(2.0F * (-(float) Math.PI + ((float) Math.PI / 8F) * (float) i1)));
                    j += 10;
                } else {
                    int j1 = i - 20;

                    k = MathHelper.floor(20.0F * MathHelper.cos(2.0F * (-(float) Math.PI + ((float) Math.PI / 4F) * (float) j1)));
                    l = MathHelper.floor(20.0F * MathHelper.sin(2.0F * (-(float) Math.PI + ((float) Math.PI / 4F) * (float) j1)));
                }

                int k1 = Math.max(73, this.level().getHeightmapPos(HeightMap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPosition(k, 0, l)).getY() + j);

                this.nodes[i] = new PathPoint(k, k1, l);
            }

            this.nodeAdjacency[0] = 6146;
            this.nodeAdjacency[1] = 8197;
            this.nodeAdjacency[2] = 8202;
            this.nodeAdjacency[3] = 16404;
            this.nodeAdjacency[4] = 32808;
            this.nodeAdjacency[5] = 32848;
            this.nodeAdjacency[6] = 65696;
            this.nodeAdjacency[7] = 131392;
            this.nodeAdjacency[8] = 131712;
            this.nodeAdjacency[9] = 263424;
            this.nodeAdjacency[10] = 526848;
            this.nodeAdjacency[11] = 525313;
            this.nodeAdjacency[12] = 1581057;
            this.nodeAdjacency[13] = 3166214;
            this.nodeAdjacency[14] = 2138120;
            this.nodeAdjacency[15] = 6373424;
            this.nodeAdjacency[16] = 4358208;
            this.nodeAdjacency[17] = 12910976;
            this.nodeAdjacency[18] = 9044480;
            this.nodeAdjacency[19] = 9706496;
            this.nodeAdjacency[20] = 15216640;
            this.nodeAdjacency[21] = 13688832;
            this.nodeAdjacency[22] = 11763712;
            this.nodeAdjacency[23] = 8257536;
        }

        return this.findClosestNode(this.getX(), this.getY(), this.getZ());
    }

    public int findClosestNode(double d0, double d1, double d2) {
        float f = 10000.0F;
        int i = 0;
        PathPoint pathpoint = new PathPoint(MathHelper.floor(d0), MathHelper.floor(d1), MathHelper.floor(d2));
        int j = 0;

        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            j = 12;
        }

        for (int k = j; k < 24; ++k) {
            if (this.nodes[k] != null) {
                float f1 = this.nodes[k].distanceToSqr(pathpoint);

                if (f1 < f) {
                    f = f1;
                    i = k;
                }
            }
        }

        return i;
    }

    @Nullable
    public PathEntity findPath(int i, int j, @Nullable PathPoint pathpoint) {
        for (int k = 0; k < 24; ++k) {
            PathPoint pathpoint1 = this.nodes[k];

            pathpoint1.closed = false;
            pathpoint1.f = 0.0F;
            pathpoint1.g = 0.0F;
            pathpoint1.h = 0.0F;
            pathpoint1.cameFrom = null;
            pathpoint1.heapIdx = -1;
        }

        PathPoint pathpoint2 = this.nodes[i];
        PathPoint pathpoint3 = this.nodes[j];

        pathpoint2.g = 0.0F;
        pathpoint2.h = pathpoint2.distanceTo(pathpoint3);
        pathpoint2.f = pathpoint2.h;
        this.openSet.clear();
        this.openSet.insert(pathpoint2);
        PathPoint pathpoint4 = pathpoint2;
        int l = 0;

        if (this.dragonFight == null || this.dragonFight.getCrystalsAlive() == 0) {
            l = 12;
        }

        while (!this.openSet.isEmpty()) {
            PathPoint pathpoint5 = this.openSet.pop();

            if (pathpoint5.equals(pathpoint3)) {
                if (pathpoint != null) {
                    pathpoint.cameFrom = pathpoint3;
                    pathpoint3 = pathpoint;
                }

                return this.reconstructPath(pathpoint2, pathpoint3);
            }

            if (pathpoint5.distanceTo(pathpoint3) < pathpoint4.distanceTo(pathpoint3)) {
                pathpoint4 = pathpoint5;
            }

            pathpoint5.closed = true;
            int i1 = 0;

            for (int j1 = 0; j1 < 24; ++j1) {
                if (this.nodes[j1] == pathpoint5) {
                    i1 = j1;
                    break;
                }
            }

            for (int k1 = l; k1 < 24; ++k1) {
                if ((this.nodeAdjacency[i1] & 1 << k1) > 0) {
                    PathPoint pathpoint6 = this.nodes[k1];

                    if (!pathpoint6.closed) {
                        float f = pathpoint5.g + pathpoint5.distanceTo(pathpoint6);

                        if (!pathpoint6.inOpenSet() || f < pathpoint6.g) {
                            pathpoint6.cameFrom = pathpoint5;
                            pathpoint6.g = f;
                            pathpoint6.h = pathpoint6.distanceTo(pathpoint3);
                            if (pathpoint6.inOpenSet()) {
                                this.openSet.changeCost(pathpoint6, pathpoint6.g + pathpoint6.h);
                            } else {
                                pathpoint6.f = pathpoint6.g + pathpoint6.h;
                                this.openSet.insert(pathpoint6);
                            }
                        }
                    }
                }
            }
        }

        if (pathpoint4 == pathpoint2) {
            return null;
        } else {
            EntityEnderDragon.LOGGER.debug("Failed to find path from {} to {}", i, j);
            if (pathpoint != null) {
                pathpoint.cameFrom = pathpoint4;
                pathpoint4 = pathpoint;
            }

            return this.reconstructPath(pathpoint2, pathpoint4);
        }
    }

    private PathEntity reconstructPath(PathPoint pathpoint, PathPoint pathpoint1) {
        List<PathPoint> list = Lists.newArrayList();
        PathPoint pathpoint2 = pathpoint1;

        list.add(0, pathpoint1);

        while (pathpoint2.cameFrom != null) {
            pathpoint2 = pathpoint2.cameFrom;
            list.add(0, pathpoint2);
        }

        return new PathEntity(list, new BlockPosition(pathpoint1.x, pathpoint1.y, pathpoint1.z), true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putInt("DragonPhase", this.phaseManager.getCurrentPhase().getPhase().getId());
        valueoutput.putInt("DragonDeathTime", this.dragonDeathTime);
        valueoutput.putInt("Bukkit.expToDrop", expToDrop); // CraftBukkit - SPIGOT-2420: The ender dragon drops xp over time which can also happen between server starts
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        valueinput.getInt("DragonPhase").ifPresent((integer) -> {
            this.phaseManager.setPhase(DragonControllerPhase.getById(integer));
        });
        this.dragonDeathTime = valueinput.getIntOr("DragonDeathTime", 0);
        this.expToDrop = valueinput.getIntOr("Bukkit.expToDrop", this.expToDrop); // CraftBukkit - SPIGOT-2420: The ender dragon drops xp over time which can also happen between server starts
    }

    @Override
    public void checkDespawn() {}

    public EntityComplexPart[] getSubEntities() {
        return this.subEntities;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public SoundCategory getSoundSource() {
        return SoundCategory.HOSTILE;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return SoundEffects.ENDER_DRAGON_AMBIENT;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.ENDER_DRAGON_HURT;
    }

    @Override
    protected float getSoundVolume() {
        return 5.0F;
    }

    public Vec3D getHeadLookVector(float f) {
        IDragonController idragoncontroller = this.phaseManager.getCurrentPhase();
        DragonControllerPhase<? extends IDragonController> dragoncontrollerphase = idragoncontroller.getPhase();
        Vec3D vec3d;

        if (dragoncontrollerphase != DragonControllerPhase.LANDING && dragoncontrollerphase != DragonControllerPhase.TAKEOFF) {
            if (idragoncontroller.isSitting()) {
                float f1 = this.getXRot();
                float f2 = 1.5F;

                this.setXRot(-45.0F);
                vec3d = this.getViewVector(f);
                this.setXRot(f1);
            } else {
                vec3d = this.getViewVector(f);
            }
        } else {
            BlockPosition blockposition = this.level().getHeightmapPos(HeightMap.Type.MOTION_BLOCKING_NO_LEAVES, WorldGenEndTrophy.getLocation(this.fightOrigin));
            float f3 = Math.max((float) Math.sqrt(blockposition.distToCenterSqr(this.position())) / 4.0F, 1.0F);
            float f4 = 6.0F / f3;
            float f5 = this.getXRot();
            float f6 = 1.5F;

            this.setXRot(-f4 * 1.5F * 5.0F);
            vec3d = this.getViewVector(f);
            this.setXRot(f5);
        }

        return vec3d;
    }

    public void onCrystalDestroyed(WorldServer worldserver, EntityEnderCrystal entityendercrystal, BlockPosition blockposition, DamageSource damagesource) {
        Entity entity = damagesource.getEntity();
        EntityHuman entityhuman;

        if (entity instanceof EntityHuman entityhuman1) {
            entityhuman = entityhuman1;
        } else {
            entityhuman = worldserver.getNearestPlayer(EntityEnderDragon.CRYSTAL_DESTROY_TARGETING, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
        }

        if (entityendercrystal == this.nearestCrystal) {
            this.hurt(worldserver, this.head, this.damageSources().explosion(entityendercrystal, entityhuman), 10.0F);
        }

        this.phaseManager.getCurrentPhase().onCrystalDestroyed(entityendercrystal, blockposition, damagesource, entityhuman);
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntityEnderDragon.DATA_PHASE.equals(datawatcherobject) && this.level().isClientSide) {
            this.phaseManager.setPhase(DragonControllerPhase.getById((Integer) this.getEntityData().get(EntityEnderDragon.DATA_PHASE)));
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    public DragonControllerManager getPhaseManager() {
        return this.phaseManager;
    }

    @Nullable
    public EnderDragonBattle getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public boolean addEffect(MobEffect mobeffect, @Nullable Entity entity) {
        return false;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean flag) {
        return false;
    }

    @Override
    public void recreateFromPacket(PacketPlayOutSpawnEntity packetplayoutspawnentity) {
        super.recreateFromPacket(packetplayoutspawnentity);
        EntityComplexPart[] aentitycomplexpart = this.getSubEntities();

        for (int i = 0; i < aentitycomplexpart.length; ++i) {
            aentitycomplexpart[i].setId(i + packetplayoutspawnentity.getId() + 1);
        }

    }

    @Override
    public boolean canAttack(EntityLiving entityliving) {
        return entityliving.canBeSeenAsEnemy();
    }

    @Override
    protected float sanitizeScale(float f) {
        return 1.0F;
    }
}
