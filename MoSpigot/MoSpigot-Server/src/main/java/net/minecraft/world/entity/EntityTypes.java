package net.minecraft.world.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.resources.DependantName;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.fixes.DataConverterTypes;
import net.minecraft.world.entity.ambient.EntityBat;
import net.minecraft.world.entity.animal.EntityBee;
import net.minecraft.world.entity.animal.EntityCat;
import net.minecraft.world.entity.animal.EntityChicken;
import net.minecraft.world.entity.animal.EntityCod;
import net.minecraft.world.entity.animal.EntityCow;
import net.minecraft.world.entity.animal.EntityDolphin;
import net.minecraft.world.entity.animal.EntityFox;
import net.minecraft.world.entity.animal.EntityIronGolem;
import net.minecraft.world.entity.animal.EntityMushroomCow;
import net.minecraft.world.entity.animal.EntityOcelot;
import net.minecraft.world.entity.animal.EntityPanda;
import net.minecraft.world.entity.animal.EntityParrot;
import net.minecraft.world.entity.animal.EntityPig;
import net.minecraft.world.entity.animal.EntityPolarBear;
import net.minecraft.world.entity.animal.EntityPufferFish;
import net.minecraft.world.entity.animal.EntityRabbit;
import net.minecraft.world.entity.animal.EntitySalmon;
import net.minecraft.world.entity.animal.EntitySnowman;
import net.minecraft.world.entity.animal.EntitySquid;
import net.minecraft.world.entity.animal.EntityTropicalFish;
import net.minecraft.world.entity.animal.EntityTurtle;
import net.minecraft.world.entity.animal.HappyGhast;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.EntityHorse;
import net.minecraft.world.entity.animal.horse.EntityHorseDonkey;
import net.minecraft.world.entity.animal.horse.EntityHorseMule;
import net.minecraft.world.entity.animal.horse.EntityHorseSkeleton;
import net.minecraft.world.entity.animal.horse.EntityHorseZombie;
import net.minecraft.world.entity.animal.horse.EntityLlama;
import net.minecraft.world.entity.animal.horse.EntityLlamaTrader;
import net.minecraft.world.entity.animal.sheep.EntitySheep;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.wolf.EntityWolf;
import net.minecraft.world.entity.boss.enderdragon.EntityEnderCrystal;
import net.minecraft.world.entity.boss.enderdragon.EntityEnderDragon;
import net.minecraft.world.entity.boss.wither.EntityWither;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.decoration.EntityItemFrame;
import net.minecraft.world.entity.decoration.EntityLeash;
import net.minecraft.world.entity.decoration.EntityPainting;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.item.EntityFallingBlock;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.item.EntityTNTPrimed;
import net.minecraft.world.entity.monster.Bogged;
import net.minecraft.world.entity.monster.EntityBlaze;
import net.minecraft.world.entity.monster.EntityCaveSpider;
import net.minecraft.world.entity.monster.EntityCreeper;
import net.minecraft.world.entity.monster.EntityDrowned;
import net.minecraft.world.entity.monster.EntityEnderman;
import net.minecraft.world.entity.monster.EntityEndermite;
import net.minecraft.world.entity.monster.EntityEvoker;
import net.minecraft.world.entity.monster.EntityGhast;
import net.minecraft.world.entity.monster.EntityGiantZombie;
import net.minecraft.world.entity.monster.EntityGuardian;
import net.minecraft.world.entity.monster.EntityGuardianElder;
import net.minecraft.world.entity.monster.EntityIllagerIllusioner;
import net.minecraft.world.entity.monster.EntityMagmaCube;
import net.minecraft.world.entity.monster.EntityPhantom;
import net.minecraft.world.entity.monster.EntityPigZombie;
import net.minecraft.world.entity.monster.EntityPillager;
import net.minecraft.world.entity.monster.EntityRavager;
import net.minecraft.world.entity.monster.EntityShulker;
import net.minecraft.world.entity.monster.EntitySilverfish;
import net.minecraft.world.entity.monster.EntitySkeleton;
import net.minecraft.world.entity.monster.EntitySkeletonStray;
import net.minecraft.world.entity.monster.EntitySkeletonWither;
import net.minecraft.world.entity.monster.EntitySlime;
import net.minecraft.world.entity.monster.EntitySpider;
import net.minecraft.world.entity.monster.EntityStrider;
import net.minecraft.world.entity.monster.EntityVex;
import net.minecraft.world.entity.monster.EntityVindicator;
import net.minecraft.world.entity.monster.EntityWitch;
import net.minecraft.world.entity.monster.EntityZoglin;
import net.minecraft.world.entity.monster.EntityZombie;
import net.minecraft.world.entity.monster.EntityZombieHusk;
import net.minecraft.world.entity.monster.EntityZombieVillager;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.EntityHoglin;
import net.minecraft.world.entity.monster.piglin.EntityPiglin;
import net.minecraft.world.entity.monster.piglin.EntityPiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.EntityVillager;
import net.minecraft.world.entity.npc.EntityVillagerTrader;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.projectile.EntityDragonFireball;
import net.minecraft.world.entity.projectile.EntityEgg;
import net.minecraft.world.entity.projectile.EntityEnderPearl;
import net.minecraft.world.entity.projectile.EntityEnderSignal;
import net.minecraft.world.entity.projectile.EntityEvokerFangs;
import net.minecraft.world.entity.projectile.EntityFireworks;
import net.minecraft.world.entity.projectile.EntityFishingHook;
import net.minecraft.world.entity.projectile.EntityLargeFireball;
import net.minecraft.world.entity.projectile.EntityLlamaSpit;
import net.minecraft.world.entity.projectile.EntityShulkerBullet;
import net.minecraft.world.entity.projectile.EntitySmallFireball;
import net.minecraft.world.entity.projectile.EntitySnowball;
import net.minecraft.world.entity.projectile.EntitySpectralArrow;
import net.minecraft.world.entity.projectile.EntityThrownExpBottle;
import net.minecraft.world.entity.projectile.EntityThrownTrident;
import net.minecraft.world.entity.projectile.EntityTippedArrow;
import net.minecraft.world.entity.projectile.EntityWitherSkull;
import net.minecraft.world.entity.projectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.ThrownSplashPotion;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.entity.vehicle.ChestRaft;
import net.minecraft.world.entity.vehicle.EntityBoat;
import net.minecraft.world.entity.vehicle.EntityMinecartChest;
import net.minecraft.world.entity.vehicle.EntityMinecartCommandBlock;
import net.minecraft.world.entity.vehicle.EntityMinecartFurnace;
import net.minecraft.world.entity.vehicle.EntityMinecartHopper;
import net.minecraft.world.entity.vehicle.EntityMinecartMobSpawner;
import net.minecraft.world.entity.vehicle.EntityMinecartRideable;
import net.minecraft.world.entity.vehicle.EntityMinecartTNT;
import net.minecraft.world.entity.vehicle.Raft;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.pathfinder.PathfinderAbstract;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapes;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.slf4j.Logger;

public class EntityTypes<T extends Entity> implements FeatureElement, EntityTypeTest<Entity, T> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Holder.c<EntityTypes<?>> builtInRegistryHolder;
    public static final Codec<EntityTypes<?>> CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec();
    private static final float MAGIC_HORSE_WIDTH = 1.3964844F;
    private static final int DISPLAY_TRACKING_RANGE = 10;
    public static final EntityTypes<EntityBoat> ACACIA_BOAT = register("acacia_boat", EntityTypes.Builder.of(boatFactory(() -> {
        return Items.ACACIA_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<ChestBoat> ACACIA_CHEST_BOAT = register("acacia_chest_boat", EntityTypes.Builder.of(chestBoatFactory(() -> {
        return Items.ACACIA_CHEST_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<Allay> ALLAY = register("allay", EntityTypes.Builder.of(Allay::new, EnumCreatureType.CREATURE).sized(0.35F, 0.6F).eyeHeight(0.36F).ridingOffset(0.04F).clientTrackingRange(8).updateInterval(2));
    public static final EntityTypes<EntityAreaEffectCloud> AREA_EFFECT_CLOUD = register("area_effect_cloud", EntityTypes.Builder.of(EntityAreaEffectCloud::new, EnumCreatureType.MISC).noLootTable().fireImmune().sized(6.0F, 0.5F).clientTrackingRange(10).updateInterval(10)); // CraftBukkit - SPIGOT-3729: track area effect clouds
    public static final EntityTypes<Armadillo> ARMADILLO = register("armadillo", EntityTypes.Builder.of(Armadillo::new, EnumCreatureType.CREATURE).sized(0.7F, 0.65F).eyeHeight(0.26F).clientTrackingRange(10));
    public static final EntityTypes<EntityArmorStand> ARMOR_STAND = register("armor_stand", EntityTypes.Builder.of(EntityArmorStand::new, EnumCreatureType.MISC).sized(0.5F, 1.975F).eyeHeight(1.7775F).clientTrackingRange(10));
    public static final EntityTypes<EntityTippedArrow> ARROW = register("arrow", EntityTypes.Builder.of(EntityTippedArrow::new, EnumCreatureType.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    public static final EntityTypes<Axolotl> AXOLOTL = register("axolotl", EntityTypes.Builder.of(Axolotl::new, EnumCreatureType.AXOLOTLS).sized(0.75F, 0.42F).eyeHeight(0.2751F).clientTrackingRange(10));
    public static final EntityTypes<ChestRaft> BAMBOO_CHEST_RAFT = register("bamboo_chest_raft", EntityTypes.Builder.of(chestRaftFactory(() -> {
        return Items.BAMBOO_CHEST_RAFT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<Raft> BAMBOO_RAFT = register("bamboo_raft", EntityTypes.Builder.of(raftFactory(() -> {
        return Items.BAMBOO_RAFT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<EntityBat> BAT = register("bat", EntityTypes.Builder.of(EntityBat::new, EnumCreatureType.AMBIENT).sized(0.5F, 0.9F).eyeHeight(0.45F).clientTrackingRange(5));
    public static final EntityTypes<EntityBee> BEE = register("bee", EntityTypes.Builder.of(EntityBee::new, EnumCreatureType.CREATURE).sized(0.7F, 0.6F).eyeHeight(0.3F).clientTrackingRange(8));
    public static final EntityTypes<EntityBoat> BIRCH_BOAT = register("birch_boat", EntityTypes.Builder.of(boatFactory(() -> {
        return Items.BIRCH_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<ChestBoat> BIRCH_CHEST_BOAT = register("birch_chest_boat", EntityTypes.Builder.of(chestBoatFactory(() -> {
        return Items.BIRCH_CHEST_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<EntityBlaze> BLAZE = register("blaze", EntityTypes.Builder.of(EntityBlaze::new, EnumCreatureType.MONSTER).fireImmune().sized(0.6F, 1.8F).clientTrackingRange(8));
    public static final EntityTypes<Display.BlockDisplay> BLOCK_DISPLAY = register("block_display", EntityTypes.Builder.of(Display.BlockDisplay::new, EnumCreatureType.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(10).updateInterval(1));
    public static final EntityTypes<Bogged> BOGGED = register("bogged", EntityTypes.Builder.of(Bogged::new, EnumCreatureType.MONSTER).sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8));
    public static final EntityTypes<Breeze> BREEZE = register("breeze", EntityTypes.Builder.of(Breeze::new, EnumCreatureType.MONSTER).sized(0.6F, 1.77F).eyeHeight(1.3452F).clientTrackingRange(10));
    public static final EntityTypes<BreezeWindCharge> BREEZE_WIND_CHARGE = register("breeze_wind_charge", EntityTypes.Builder.of(BreezeWindCharge::new, EnumCreatureType.MISC).noLootTable().sized(0.3125F, 0.3125F).eyeHeight(0.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<Camel> CAMEL = register("camel", EntityTypes.Builder.of(Camel::new, EnumCreatureType.CREATURE).sized(1.7F, 2.375F).eyeHeight(2.275F).clientTrackingRange(10));
    public static final EntityTypes<EntityCat> CAT = register("cat", EntityTypes.Builder.of(EntityCat::new, EnumCreatureType.CREATURE).sized(0.6F, 0.7F).eyeHeight(0.35F).passengerAttachments(0.5125F).clientTrackingRange(8));
    public static final EntityTypes<EntityCaveSpider> CAVE_SPIDER = register("cave_spider", EntityTypes.Builder.of(EntityCaveSpider::new, EnumCreatureType.MONSTER).sized(0.7F, 0.5F).eyeHeight(0.45F).clientTrackingRange(8));
    public static final EntityTypes<EntityBoat> CHERRY_BOAT = register("cherry_boat", EntityTypes.Builder.of(boatFactory(() -> {
        return Items.CHERRY_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<ChestBoat> CHERRY_CHEST_BOAT = register("cherry_chest_boat", EntityTypes.Builder.of(chestBoatFactory(() -> {
        return Items.CHERRY_CHEST_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<EntityMinecartChest> CHEST_MINECART = register("chest_minecart", EntityTypes.Builder.of(EntityMinecartChest::new, EnumCreatureType.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityTypes<EntityChicken> CHICKEN = register("chicken", EntityTypes.Builder.of(EntityChicken::new, EnumCreatureType.CREATURE).sized(0.4F, 0.7F).eyeHeight(0.644F).passengerAttachments(new Vec3D(0.0D, 0.7D, -0.1D)).clientTrackingRange(10));
    public static final EntityTypes<EntityCod> COD = register("cod", EntityTypes.Builder.of(EntityCod::new, EnumCreatureType.WATER_AMBIENT).sized(0.5F, 0.3F).eyeHeight(0.195F).clientTrackingRange(4));
    public static final EntityTypes<EntityMinecartCommandBlock> COMMAND_BLOCK_MINECART = register("command_block_minecart", EntityTypes.Builder.of(EntityMinecartCommandBlock::new, EnumCreatureType.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityTypes<EntityCow> COW = register("cow", EntityTypes.Builder.of(EntityCow::new, EnumCreatureType.CREATURE).sized(0.9F, 1.4F).eyeHeight(1.3F).passengerAttachments(1.36875F).clientTrackingRange(10));
    public static final EntityTypes<Creaking> CREAKING = register("creaking", EntityTypes.Builder.of(Creaking::new, EnumCreatureType.MONSTER).sized(0.9F, 2.7F).eyeHeight(2.3F).clientTrackingRange(8));
    public static final EntityTypes<EntityCreeper> CREEPER = register("creeper", EntityTypes.Builder.of(EntityCreeper::new, EnumCreatureType.MONSTER).sized(0.6F, 1.7F).clientTrackingRange(8));
    public static final EntityTypes<EntityBoat> DARK_OAK_BOAT = register("dark_oak_boat", EntityTypes.Builder.of(boatFactory(() -> {
        return Items.DARK_OAK_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<ChestBoat> DARK_OAK_CHEST_BOAT = register("dark_oak_chest_boat", EntityTypes.Builder.of(chestBoatFactory(() -> {
        return Items.DARK_OAK_CHEST_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<EntityDolphin> DOLPHIN = register("dolphin", EntityTypes.Builder.of(EntityDolphin::new, EnumCreatureType.WATER_CREATURE).sized(0.9F, 0.6F).eyeHeight(0.3F));
    public static final EntityTypes<EntityHorseDonkey> DONKEY = register("donkey", EntityTypes.Builder.of(EntityHorseDonkey::new, EnumCreatureType.CREATURE).sized(1.3964844F, 1.5F).eyeHeight(1.425F).passengerAttachments(1.1125F).clientTrackingRange(10));
    public static final EntityTypes<EntityDragonFireball> DRAGON_FIREBALL = register("dragon_fireball", EntityTypes.Builder.of(EntityDragonFireball::new, EnumCreatureType.MISC).noLootTable().sized(1.0F, 1.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityDrowned> DROWNED = register("drowned", EntityTypes.Builder.of(EntityDrowned::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8));
    public static final EntityTypes<EntityEgg> EGG = register("egg", EntityTypes.Builder.of(EntityEgg::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityGuardianElder> ELDER_GUARDIAN = register("elder_guardian", EntityTypes.Builder.of(EntityGuardianElder::new, EnumCreatureType.MONSTER).sized(1.9975F, 1.9975F).eyeHeight(0.99875F).passengerAttachments(2.350625F).clientTrackingRange(10));
    public static final EntityTypes<EntityEnderman> ENDERMAN = register("enderman", EntityTypes.Builder.of(EntityEnderman::new, EnumCreatureType.MONSTER).sized(0.6F, 2.9F).eyeHeight(2.55F).passengerAttachments(2.80625F).clientTrackingRange(8));
    public static final EntityTypes<EntityEndermite> ENDERMITE = register("endermite", EntityTypes.Builder.of(EntityEndermite::new, EnumCreatureType.MONSTER).sized(0.4F, 0.3F).eyeHeight(0.13F).passengerAttachments(0.2375F).clientTrackingRange(8));
    public static final EntityTypes<EntityEnderDragon> ENDER_DRAGON = register("ender_dragon", EntityTypes.Builder.of(EntityEnderDragon::new, EnumCreatureType.MONSTER).fireImmune().sized(16.0F, 8.0F).passengerAttachments(3.0F).clientTrackingRange(10));
    public static final EntityTypes<EntityEnderPearl> ENDER_PEARL = register("ender_pearl", EntityTypes.Builder.of(EntityEnderPearl::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityEnderCrystal> END_CRYSTAL = register("end_crystal", EntityTypes.Builder.of(EntityEnderCrystal::new, EnumCreatureType.MISC).noLootTable().fireImmune().sized(2.0F, 2.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE));
    public static final EntityTypes<EntityEvoker> EVOKER = register("evoker", EntityTypes.Builder.of(EntityEvoker::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8));
    public static final EntityTypes<EntityEvokerFangs> EVOKER_FANGS = register("evoker_fangs", EntityTypes.Builder.of(EntityEvokerFangs::new, EnumCreatureType.MISC).noLootTable().sized(0.5F, 0.8F).clientTrackingRange(6).updateInterval(2));
    public static final EntityTypes<EntityThrownExpBottle> EXPERIENCE_BOTTLE = register("experience_bottle", EntityTypes.Builder.of(EntityThrownExpBottle::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityExperienceOrb> EXPERIENCE_ORB = register("experience_orb", EntityTypes.Builder.of(EntityExperienceOrb::new, EnumCreatureType.MISC).noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(20));
    public static final EntityTypes<EntityEnderSignal> EYE_OF_ENDER = register("eye_of_ender", EntityTypes.Builder.of(EntityEnderSignal::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(4));
    public static final EntityTypes<EntityFallingBlock> FALLING_BLOCK = register("falling_block", EntityTypes.Builder.of(EntityFallingBlock::new, EnumCreatureType.MISC).noLootTable().sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(20));
    public static final EntityTypes<EntityLargeFireball> FIREBALL = register("fireball", EntityTypes.Builder.of(EntityLargeFireball::new, EnumCreatureType.MISC).noLootTable().sized(1.0F, 1.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityFireworks> FIREWORK_ROCKET = register("firework_rocket", EntityTypes.Builder.of(EntityFireworks::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityFox> FOX = register("fox", EntityTypes.Builder.of(EntityFox::new, EnumCreatureType.CREATURE).sized(0.6F, 0.7F).eyeHeight(0.4F).passengerAttachments(new Vec3D(0.0D, 0.6375D, -0.25D)).clientTrackingRange(8).immuneTo(Blocks.SWEET_BERRY_BUSH));
    public static final EntityTypes<Frog> FROG = register("frog", EntityTypes.Builder.of(Frog::new, EnumCreatureType.CREATURE).sized(0.5F, 0.5F).passengerAttachments(new Vec3D(0.0D, 0.375D, -0.25D)).clientTrackingRange(10));
    public static final EntityTypes<EntityMinecartFurnace> FURNACE_MINECART = register("furnace_minecart", EntityTypes.Builder.of(EntityMinecartFurnace::new, EnumCreatureType.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityTypes<EntityGhast> GHAST = register("ghast", EntityTypes.Builder.of(EntityGhast::new, EnumCreatureType.MONSTER).fireImmune().sized(4.0F, 4.0F).eyeHeight(2.6F).passengerAttachments(4.0625F).ridingOffset(0.5F).clientTrackingRange(10));
    public static final EntityTypes<HappyGhast> HAPPY_GHAST = register("happy_ghast", EntityTypes.Builder.of(HappyGhast::new, EnumCreatureType.CREATURE).sized(4.0F, 4.0F).eyeHeight(2.6F).passengerAttachments(new Vec3D(0.0D, 4.0D, 1.7D), new Vec3D(-1.7D, 4.0D, 0.0D), new Vec3D(0.0D, 4.0D, -1.7D), new Vec3D(1.7D, 4.0D, 0.0D)).ridingOffset(0.5F).clientTrackingRange(10));
    public static final EntityTypes<EntityGiantZombie> GIANT = register("giant", EntityTypes.Builder.of(EntityGiantZombie::new, EnumCreatureType.MONSTER).sized(3.6F, 12.0F).eyeHeight(10.44F).ridingOffset(-3.75F).clientTrackingRange(10));
    public static final EntityTypes<GlowItemFrame> GLOW_ITEM_FRAME = register("glow_item_frame", EntityTypes.Builder.of(GlowItemFrame::new, EnumCreatureType.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.0F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityTypes<GlowSquid> GLOW_SQUID = register("glow_squid", EntityTypes.Builder.of(GlowSquid::new, EnumCreatureType.UNDERGROUND_WATER_CREATURE).sized(0.8F, 0.8F).eyeHeight(0.4F).clientTrackingRange(10));
    public static final EntityTypes<Goat> GOAT = register("goat", EntityTypes.Builder.of(Goat::new, EnumCreatureType.CREATURE).sized(0.9F, 1.3F).passengerAttachments(1.1125F).clientTrackingRange(10));
    public static final EntityTypes<EntityGuardian> GUARDIAN = register("guardian", EntityTypes.Builder.of(EntityGuardian::new, EnumCreatureType.MONSTER).sized(0.85F, 0.85F).eyeHeight(0.425F).passengerAttachments(0.975F).clientTrackingRange(8));
    public static final EntityTypes<EntityHoglin> HOGLIN = register("hoglin", EntityTypes.Builder.of(EntityHoglin::new, EnumCreatureType.MONSTER).sized(1.3964844F, 1.4F).passengerAttachments(1.49375F).clientTrackingRange(8));
    public static final EntityTypes<EntityMinecartHopper> HOPPER_MINECART = register("hopper_minecart", EntityTypes.Builder.of(EntityMinecartHopper::new, EnumCreatureType.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityTypes<EntityHorse> HORSE = register("horse", EntityTypes.Builder.of(EntityHorse::new, EnumCreatureType.CREATURE).sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(1.44375F).clientTrackingRange(10));
    public static final EntityTypes<EntityZombieHusk> HUSK = register("husk", EntityTypes.Builder.of(EntityZombieHusk::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.075F).ridingOffset(-0.7F).clientTrackingRange(8));
    public static final EntityTypes<EntityIllagerIllusioner> ILLUSIONER = register("illusioner", EntityTypes.Builder.of(EntityIllagerIllusioner::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8));
    public static final EntityTypes<Interaction> INTERACTION = register("interaction", EntityTypes.Builder.of(Interaction::new, EnumCreatureType.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(10));
    public static final EntityTypes<EntityIronGolem> IRON_GOLEM = register("iron_golem", EntityTypes.Builder.of(EntityIronGolem::new, EnumCreatureType.MISC).sized(1.4F, 2.7F).clientTrackingRange(10));
    public static final EntityTypes<EntityItem> ITEM = register("item", EntityTypes.Builder.of(EntityItem::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).eyeHeight(0.2125F).clientTrackingRange(6).updateInterval(20));
    public static final EntityTypes<Display.ItemDisplay> ITEM_DISPLAY = register("item_display", EntityTypes.Builder.of(Display.ItemDisplay::new, EnumCreatureType.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(10).updateInterval(1));
    public static final EntityTypes<EntityItemFrame> ITEM_FRAME = register("item_frame", EntityTypes.Builder.of(EntityItemFrame::new, EnumCreatureType.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.0F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityTypes<EntityBoat> JUNGLE_BOAT = register("jungle_boat", EntityTypes.Builder.of(boatFactory(() -> {
        return Items.JUNGLE_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<ChestBoat> JUNGLE_CHEST_BOAT = register("jungle_chest_boat", EntityTypes.Builder.of(chestBoatFactory(() -> {
        return Items.JUNGLE_CHEST_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<EntityLeash> LEASH_KNOT = register("leash_knot", EntityTypes.Builder.of(EntityLeash::new, EnumCreatureType.MISC).noLootTable().noSave().sized(0.375F, 0.5F).eyeHeight(0.0625F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityTypes<EntityLightning> LIGHTNING_BOLT = register("lightning_bolt", EntityTypes.Builder.of(EntityLightning::new, EnumCreatureType.MISC).noLootTable().noSave().sized(0.0F, 0.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE));
    public static final EntityTypes<EntityLlama> LLAMA = register("llama", EntityTypes.Builder.of(EntityLlama::new, EnumCreatureType.CREATURE).sized(0.9F, 1.87F).eyeHeight(1.7765F).passengerAttachments(new Vec3D(0.0D, 1.37D, -0.3D)).clientTrackingRange(10));
    public static final EntityTypes<EntityLlamaSpit> LLAMA_SPIT = register("llama_spit", EntityTypes.Builder.of(EntityLlamaSpit::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityMagmaCube> MAGMA_CUBE = register("magma_cube", EntityTypes.Builder.of(EntityMagmaCube::new, EnumCreatureType.MONSTER).fireImmune().sized(0.52F, 0.52F).eyeHeight(0.325F).spawnDimensionsScale(4.0F).clientTrackingRange(8));
    public static final EntityTypes<EntityBoat> MANGROVE_BOAT = register("mangrove_boat", EntityTypes.Builder.of(boatFactory(() -> {
        return Items.MANGROVE_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<ChestBoat> MANGROVE_CHEST_BOAT = register("mangrove_chest_boat", EntityTypes.Builder.of(chestBoatFactory(() -> {
        return Items.MANGROVE_CHEST_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<Marker> MARKER = register("marker", EntityTypes.Builder.of(Marker::new, EnumCreatureType.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(0));
    public static final EntityTypes<EntityMinecartRideable> MINECART = register("minecart", EntityTypes.Builder.of(EntityMinecartRideable::new, EnumCreatureType.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityTypes<EntityMushroomCow> MOOSHROOM = register("mooshroom", EntityTypes.Builder.of(EntityMushroomCow::new, EnumCreatureType.CREATURE).sized(0.9F, 1.4F).eyeHeight(1.3F).passengerAttachments(1.36875F).clientTrackingRange(10));
    public static final EntityTypes<EntityHorseMule> MULE = register("mule", EntityTypes.Builder.of(EntityHorseMule::new, EnumCreatureType.CREATURE).sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(1.2125F).clientTrackingRange(8));
    public static final EntityTypes<EntityBoat> OAK_BOAT = register("oak_boat", EntityTypes.Builder.of(boatFactory(() -> {
        return Items.OAK_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<ChestBoat> OAK_CHEST_BOAT = register("oak_chest_boat", EntityTypes.Builder.of(chestBoatFactory(() -> {
        return Items.OAK_CHEST_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<EntityOcelot> OCELOT = register("ocelot", EntityTypes.Builder.of(EntityOcelot::new, EnumCreatureType.CREATURE).sized(0.6F, 0.7F).passengerAttachments(0.6375F).clientTrackingRange(10));
    public static final EntityTypes<OminousItemSpawner> OMINOUS_ITEM_SPAWNER = register("ominous_item_spawner", EntityTypes.Builder.of(OminousItemSpawner::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(8));
    public static final EntityTypes<EntityPainting> PAINTING = register("painting", EntityTypes.Builder.of(EntityPainting::new, EnumCreatureType.MISC).noLootTable().sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityTypes<EntityBoat> PALE_OAK_BOAT = register("pale_oak_boat", EntityTypes.Builder.of(boatFactory(() -> {
        return Items.PALE_OAK_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<ChestBoat> PALE_OAK_CHEST_BOAT = register("pale_oak_chest_boat", EntityTypes.Builder.of(chestBoatFactory(() -> {
        return Items.PALE_OAK_CHEST_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<EntityPanda> PANDA = register("panda", EntityTypes.Builder.of(EntityPanda::new, EnumCreatureType.CREATURE).sized(1.3F, 1.25F).clientTrackingRange(10));
    public static final EntityTypes<EntityParrot> PARROT = register("parrot", EntityTypes.Builder.of(EntityParrot::new, EnumCreatureType.CREATURE).sized(0.5F, 0.9F).eyeHeight(0.54F).passengerAttachments(0.4625F).clientTrackingRange(8));
    public static final EntityTypes<EntityPhantom> PHANTOM = register("phantom", EntityTypes.Builder.of(EntityPhantom::new, EnumCreatureType.MONSTER).sized(0.9F, 0.5F).eyeHeight(0.175F).passengerAttachments(0.3375F).ridingOffset(-0.125F).clientTrackingRange(8));
    public static final EntityTypes<EntityPig> PIG = register("pig", EntityTypes.Builder.of(EntityPig::new, EnumCreatureType.CREATURE).sized(0.9F, 0.9F).passengerAttachments(0.86875F).clientTrackingRange(10));
    public static final EntityTypes<EntityPiglin> PIGLIN = register("piglin", EntityTypes.Builder.of(EntityPiglin::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.79F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8));
    public static final EntityTypes<EntityPiglinBrute> PIGLIN_BRUTE = register("piglin_brute", EntityTypes.Builder.of(EntityPiglinBrute::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.79F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8));
    public static final EntityTypes<EntityPillager> PILLAGER = register("pillager", EntityTypes.Builder.of(EntityPillager::new, EnumCreatureType.MONSTER).canSpawnFarFromPlayer().sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8));
    public static final EntityTypes<EntityPolarBear> POLAR_BEAR = register("polar_bear", EntityTypes.Builder.of(EntityPolarBear::new, EnumCreatureType.CREATURE).immuneTo(Blocks.POWDER_SNOW).sized(1.4F, 1.4F).clientTrackingRange(10));
    public static final EntityTypes<ThrownSplashPotion> SPLASH_POTION = register("splash_potion", EntityTypes.Builder.of(ThrownSplashPotion::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<ThrownLingeringPotion> LINGERING_POTION = register("lingering_potion", EntityTypes.Builder.of(ThrownLingeringPotion::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityPufferFish> PUFFERFISH = register("pufferfish", EntityTypes.Builder.of(EntityPufferFish::new, EnumCreatureType.WATER_AMBIENT).sized(0.7F, 0.7F).eyeHeight(0.455F).clientTrackingRange(4));
    public static final EntityTypes<EntityRabbit> RABBIT = register("rabbit", EntityTypes.Builder.of(EntityRabbit::new, EnumCreatureType.CREATURE).sized(0.4F, 0.5F).clientTrackingRange(8));
    public static final EntityTypes<EntityRavager> RAVAGER = register("ravager", EntityTypes.Builder.of(EntityRavager::new, EnumCreatureType.MONSTER).sized(1.95F, 2.2F).passengerAttachments(new Vec3D(0.0D, 2.2625D, -0.0625D)).clientTrackingRange(10));
    public static final EntityTypes<EntitySalmon> SALMON = register("salmon", EntityTypes.Builder.of(EntitySalmon::new, EnumCreatureType.WATER_AMBIENT).sized(0.7F, 0.4F).eyeHeight(0.26F).clientTrackingRange(4));
    public static final EntityTypes<EntitySheep> SHEEP = register("sheep", EntityTypes.Builder.of(EntitySheep::new, EnumCreatureType.CREATURE).sized(0.9F, 1.3F).eyeHeight(1.235F).passengerAttachments(1.2375F).clientTrackingRange(10));
    public static final EntityTypes<EntityShulker> SHULKER = register("shulker", EntityTypes.Builder.of(EntityShulker::new, EnumCreatureType.MONSTER).fireImmune().canSpawnFarFromPlayer().sized(1.0F, 1.0F).eyeHeight(0.5F).clientTrackingRange(10));
    public static final EntityTypes<EntityShulkerBullet> SHULKER_BULLET = register("shulker_bullet", EntityTypes.Builder.of(EntityShulkerBullet::new, EnumCreatureType.MISC).noLootTable().sized(0.3125F, 0.3125F).clientTrackingRange(8));
    public static final EntityTypes<EntitySilverfish> SILVERFISH = register("silverfish", EntityTypes.Builder.of(EntitySilverfish::new, EnumCreatureType.MONSTER).sized(0.4F, 0.3F).eyeHeight(0.13F).passengerAttachments(0.2375F).clientTrackingRange(8));
    public static final EntityTypes<EntitySkeleton> SKELETON = register("skeleton", EntityTypes.Builder.of(EntitySkeleton::new, EnumCreatureType.MONSTER).sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).clientTrackingRange(8));
    public static final EntityTypes<EntityHorseSkeleton> SKELETON_HORSE = register("skeleton_horse", EntityTypes.Builder.of(EntityHorseSkeleton::new, EnumCreatureType.CREATURE).sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(1.31875F).clientTrackingRange(10));
    public static final EntityTypes<EntitySlime> SLIME = register("slime", EntityTypes.Builder.of(EntitySlime::new, EnumCreatureType.MONSTER).sized(0.52F, 0.52F).eyeHeight(0.325F).spawnDimensionsScale(4.0F).clientTrackingRange(10));
    public static final EntityTypes<EntitySmallFireball> SMALL_FIREBALL = register("small_fireball", EntityTypes.Builder.of(EntitySmallFireball::new, EnumCreatureType.MISC).noLootTable().sized(0.3125F, 0.3125F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<Sniffer> SNIFFER = register("sniffer", EntityTypes.Builder.of(Sniffer::new, EnumCreatureType.CREATURE).sized(1.9F, 1.75F).eyeHeight(1.05F).passengerAttachments(2.09375F).nameTagOffset(2.05F).clientTrackingRange(10));
    public static final EntityTypes<EntitySnowball> SNOWBALL = register("snowball", EntityTypes.Builder.of(EntitySnowball::new, EnumCreatureType.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntitySnowman> SNOW_GOLEM = register("snow_golem", EntityTypes.Builder.of(EntitySnowman::new, EnumCreatureType.MISC).immuneTo(Blocks.POWDER_SNOW).sized(0.7F, 1.9F).eyeHeight(1.7F).clientTrackingRange(8));
    public static final EntityTypes<EntityMinecartMobSpawner> SPAWNER_MINECART = register("spawner_minecart", EntityTypes.Builder.of(EntityMinecartMobSpawner::new, EnumCreatureType.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityTypes<EntitySpectralArrow> SPECTRAL_ARROW = register("spectral_arrow", EntityTypes.Builder.of(EntitySpectralArrow::new, EnumCreatureType.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    public static final EntityTypes<EntitySpider> SPIDER = register("spider", EntityTypes.Builder.of(EntitySpider::new, EnumCreatureType.MONSTER).sized(1.4F, 0.9F).eyeHeight(0.65F).passengerAttachments(0.765F).clientTrackingRange(8));
    public static final EntityTypes<EntityBoat> SPRUCE_BOAT = register("spruce_boat", EntityTypes.Builder.of(boatFactory(() -> {
        return Items.SPRUCE_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<ChestBoat> SPRUCE_CHEST_BOAT = register("spruce_chest_boat", EntityTypes.Builder.of(chestBoatFactory(() -> {
        return Items.SPRUCE_CHEST_BOAT;
    }), EnumCreatureType.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10));
    public static final EntityTypes<EntitySquid> SQUID = register("squid", EntityTypes.Builder.of(EntitySquid::new, EnumCreatureType.WATER_CREATURE).sized(0.8F, 0.8F).eyeHeight(0.4F).clientTrackingRange(8));
    public static final EntityTypes<EntitySkeletonStray> STRAY = register("stray", EntityTypes.Builder.of(EntitySkeletonStray::new, EnumCreatureType.MONSTER).sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F).immuneTo(Blocks.POWDER_SNOW).clientTrackingRange(8));
    public static final EntityTypes<EntityStrider> STRIDER = register("strider", EntityTypes.Builder.of(EntityStrider::new, EnumCreatureType.CREATURE).fireImmune().sized(0.9F, 1.7F).clientTrackingRange(10));
    public static final EntityTypes<Tadpole> TADPOLE = register("tadpole", EntityTypes.Builder.of(Tadpole::new, EnumCreatureType.CREATURE).sized(0.4F, 0.3F).eyeHeight(0.19500001F).clientTrackingRange(10));
    public static final EntityTypes<Display.TextDisplay> TEXT_DISPLAY = register("text_display", EntityTypes.Builder.of(Display.TextDisplay::new, EnumCreatureType.MISC).noLootTable().sized(0.0F, 0.0F).clientTrackingRange(10).updateInterval(1));
    public static final EntityTypes<EntityTNTPrimed> TNT = register("tnt", EntityTypes.Builder.of(EntityTNTPrimed::new, EnumCreatureType.MISC).noLootTable().fireImmune().sized(0.98F, 0.98F).eyeHeight(0.15F).clientTrackingRange(10).updateInterval(10));
    public static final EntityTypes<EntityMinecartTNT> TNT_MINECART = register("tnt_minecart", EntityTypes.Builder.of(EntityMinecartTNT::new, EnumCreatureType.MISC).noLootTable().sized(0.98F, 0.7F).passengerAttachments(0.1875F).clientTrackingRange(8));
    public static final EntityTypes<EntityLlamaTrader> TRADER_LLAMA = register("trader_llama", EntityTypes.Builder.of(EntityLlamaTrader::new, EnumCreatureType.CREATURE).sized(0.9F, 1.87F).eyeHeight(1.7765F).passengerAttachments(new Vec3D(0.0D, 1.37D, -0.3D)).clientTrackingRange(10));
    public static final EntityTypes<EntityThrownTrident> TRIDENT = register("trident", EntityTypes.Builder.of(EntityThrownTrident::new, EnumCreatureType.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
    public static final EntityTypes<EntityTropicalFish> TROPICAL_FISH = register("tropical_fish", EntityTypes.Builder.of(EntityTropicalFish::new, EnumCreatureType.WATER_AMBIENT).sized(0.5F, 0.4F).eyeHeight(0.26F).clientTrackingRange(4));
    public static final EntityTypes<EntityTurtle> TURTLE = register("turtle", EntityTypes.Builder.of(EntityTurtle::new, EnumCreatureType.CREATURE).sized(1.2F, 0.4F).passengerAttachments(new Vec3D(0.0D, 0.55625D, -0.25D)).clientTrackingRange(10));
    public static final EntityTypes<EntityVex> VEX = register("vex", EntityTypes.Builder.of(EntityVex::new, EnumCreatureType.MONSTER).fireImmune().sized(0.4F, 0.8F).eyeHeight(0.51875F).passengerAttachments(0.7375F).ridingOffset(0.04F).clientTrackingRange(8));
    public static final EntityTypes<EntityVillager> VILLAGER = register("villager", EntityTypes.Builder.of(EntityVillager::new, EnumCreatureType.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
    public static final EntityTypes<EntityVindicator> VINDICATOR = register("vindicator", EntityTypes.Builder.of(EntityVindicator::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).passengerAttachments(2.0F).ridingOffset(-0.6F).clientTrackingRange(8));
    public static final EntityTypes<EntityVillagerTrader> WANDERING_TRADER = register("wandering_trader", EntityTypes.Builder.of(EntityVillagerTrader::new, EnumCreatureType.CREATURE).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
    public static final EntityTypes<Warden> WARDEN = register("warden", EntityTypes.Builder.of(Warden::new, EnumCreatureType.MONSTER).sized(0.9F, 2.9F).passengerAttachments(3.15F).attach(EntityAttachment.WARDEN_CHEST, 0.0F, 1.6F, 0.0F).clientTrackingRange(16).fireImmune());
    public static final EntityTypes<WindCharge> WIND_CHARGE = register("wind_charge", EntityTypes.Builder.of(WindCharge::new, EnumCreatureType.MISC).noLootTable().sized(0.3125F, 0.3125F).eyeHeight(0.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityWitch> WITCH = register("witch", EntityTypes.Builder.of(EntityWitch::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.62F).passengerAttachments(2.2625F).clientTrackingRange(8));
    public static final EntityTypes<EntityWither> WITHER = register("wither", EntityTypes.Builder.of(EntityWither::new, EnumCreatureType.MONSTER).fireImmune().immuneTo(Blocks.WITHER_ROSE).sized(0.9F, 3.5F).clientTrackingRange(10));
    public static final EntityTypes<EntitySkeletonWither> WITHER_SKELETON = register("wither_skeleton", EntityTypes.Builder.of(EntitySkeletonWither::new, EnumCreatureType.MONSTER).fireImmune().immuneTo(Blocks.WITHER_ROSE).sized(0.7F, 2.4F).eyeHeight(2.1F).ridingOffset(-0.875F).clientTrackingRange(8));
    public static final EntityTypes<EntityWitherSkull> WITHER_SKULL = register("wither_skull", EntityTypes.Builder.of(EntityWitherSkull::new, EnumCreatureType.MISC).noLootTable().sized(0.3125F, 0.3125F).clientTrackingRange(4).updateInterval(10));
    public static final EntityTypes<EntityWolf> WOLF = register("wolf", EntityTypes.Builder.of(EntityWolf::new, EnumCreatureType.CREATURE).sized(0.6F, 0.85F).eyeHeight(0.68F).passengerAttachments(new Vec3D(0.0D, 0.81875D, -0.0625D)).clientTrackingRange(10));
    public static final EntityTypes<EntityZoglin> ZOGLIN = register("zoglin", EntityTypes.Builder.of(EntityZoglin::new, EnumCreatureType.MONSTER).fireImmune().sized(1.3964844F, 1.4F).passengerAttachments(1.49375F).clientTrackingRange(8));
    public static final EntityTypes<EntityZombie> ZOMBIE = register("zombie", EntityTypes.Builder.of(EntityZombie::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8));
    public static final EntityTypes<EntityHorseZombie> ZOMBIE_HORSE = register("zombie_horse", EntityTypes.Builder.of(EntityHorseZombie::new, EnumCreatureType.CREATURE).sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(1.31875F).clientTrackingRange(10));
    public static final EntityTypes<EntityZombieVillager> ZOMBIE_VILLAGER = register("zombie_villager", EntityTypes.Builder.of(EntityZombieVillager::new, EnumCreatureType.MONSTER).sized(0.6F, 1.95F).passengerAttachments(2.125F).ridingOffset(-0.7F).eyeHeight(1.74F).clientTrackingRange(8));
    public static final EntityTypes<EntityPigZombie> ZOMBIFIED_PIGLIN = register("zombified_piglin", EntityTypes.Builder.of(EntityPigZombie::new, EnumCreatureType.MONSTER).fireImmune().sized(0.6F, 1.95F).eyeHeight(1.79F).passengerAttachments(2.0F).ridingOffset(-0.7F).clientTrackingRange(8));
    public static final EntityTypes<EntityHuman> PLAYER = register("player", EntityTypes.Builder.createNothing(EnumCreatureType.MISC).noSave().noSummon().sized(0.6F, 1.8F).eyeHeight(1.62F).vehicleAttachment(EntityHuman.DEFAULT_VEHICLE_ATTACHMENT).clientTrackingRange(32).updateInterval(2));
    public static final EntityTypes<EntityFishingHook> FISHING_BOBBER = register("fishing_bobber", EntityTypes.Builder.of(EntityFishingHook::new, EnumCreatureType.MISC).noLootTable().noSave().noSummon().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(5));
    private static final Set<EntityTypes<?>> OP_ONLY_CUSTOM_DATA = Set.of(EntityTypes.FALLING_BLOCK, EntityTypes.COMMAND_BLOCK_MINECART, EntityTypes.SPAWNER_MINECART);
    private final EntityTypes.b<T> factory;
    private final EnumCreatureType category;
    private final ImmutableSet<Block> immuneTo;
    private final boolean serialize;
    private final boolean summon;
    private final boolean fireImmune;
    private final boolean canSpawnFarFromPlayer;
    private final int clientTrackingRange;
    private final int updateInterval;
    private final String descriptionId;
    @Nullable
    private IChatBaseComponent description;
    private final Optional<ResourceKey<LootTable>> lootTable;
    private final EntitySize dimensions;
    private final float spawnDimensionsScale;
    private final FeatureFlagSet requiredFeatures;

    private static <T extends Entity> EntityTypes<T> register(ResourceKey<EntityTypes<?>> resourcekey, EntityTypes.Builder<T> entitytypes_builder) {
        return (EntityTypes) IRegistry.register(BuiltInRegistries.ENTITY_TYPE, resourcekey, entitytypes_builder.build(resourcekey));
    }

    private static ResourceKey<EntityTypes<?>> vanillaEntityId(String s) {
        return ResourceKey.create(Registries.ENTITY_TYPE, MinecraftKey.withDefaultNamespace(s));
    }

    private static <T extends Entity> EntityTypes<T> register(String s, EntityTypes.Builder entitytypes_builder) { // CraftBukkit - decompile error
        return register(vanillaEntityId(s), entitytypes_builder);
    }

    public static MinecraftKey getKey(EntityTypes<?> entitytypes) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entitytypes);
    }

    public static Optional<EntityTypes<?>> byString(String s) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(MinecraftKey.tryParse(s));
    }

    public EntityTypes(EntityTypes.b<T> entitytypes_b, EnumCreatureType enumcreaturetype, boolean flag, boolean flag1, boolean flag2, boolean flag3, ImmutableSet<Block> immutableset, EntitySize entitysize, float f, int i, int j, String s, Optional<ResourceKey<LootTable>> optional, FeatureFlagSet featureflagset) {
        this.builtInRegistryHolder = BuiltInRegistries.ENTITY_TYPE.createIntrusiveHolder(this);
        this.factory = entitytypes_b;
        this.category = enumcreaturetype;
        this.canSpawnFarFromPlayer = flag3;
        this.serialize = flag;
        this.summon = flag1;
        this.fireImmune = flag2;
        this.immuneTo = immutableset;
        this.dimensions = entitysize;
        this.spawnDimensionsScale = f;
        this.clientTrackingRange = i;
        this.updateInterval = j;
        this.descriptionId = s;
        this.lootTable = optional;
        this.requiredFeatures = featureflagset;
    }

    @Nullable
    public T spawn(WorldServer worldserver, @Nullable ItemStack itemstack, @Nullable EntityLiving entityliving, BlockPosition blockposition, EntitySpawnReason entityspawnreason, boolean flag, boolean flag1) {
        // CraftBukkit start
        return this.spawn(worldserver, itemstack, entityliving, blockposition, entityspawnreason, flag, flag1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
    }

    @Nullable
    public T spawn(WorldServer worldserver, @Nullable ItemStack itemstack, @Nullable EntityLiving entityliving, BlockPosition blockposition, EntitySpawnReason entityspawnreason, boolean flag, boolean flag1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason) {
        // CraftBukkit end
        Consumer<T> consumer;

        if (itemstack != null) {
            consumer = createDefaultStackConfig(worldserver, itemstack, entityliving);
        } else {
            consumer = (entity) -> {
            };
        }

        return (T) this.spawn(worldserver, consumer, blockposition, entityspawnreason, flag, flag1, spawnReason); // CraftBukkit
    }

    public static <T extends Entity> Consumer<T> createDefaultStackConfig(World world, ItemStack itemstack, @Nullable EntityLiving entityliving) {
        return appendDefaultStackConfig((entity) -> {
        }, world, itemstack, entityliving);
    }

    public static <T extends Entity> Consumer<T> appendDefaultStackConfig(Consumer<T> consumer, World world, ItemStack itemstack, @Nullable EntityLiving entityliving) {
        return appendCustomEntityStackConfig(appendComponentsConfig(consumer, itemstack), world, itemstack, entityliving);
    }

    public static <T extends Entity> Consumer<T> appendComponentsConfig(Consumer<T> consumer, ItemStack itemstack) {
        return consumer.andThen((entity) -> {
            entity.applyComponentsFromItemStack(itemstack);
        });
    }

    public static <T extends Entity> Consumer<T> appendCustomEntityStackConfig(Consumer<T> consumer, World world, ItemStack itemstack, @Nullable EntityLiving entityliving) {
        CustomData customdata = (CustomData) itemstack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);

        return !customdata.isEmpty() ? consumer.andThen((entity) -> {
            try { updateCustomEntityTag(world, entityliving, entity, customdata); } catch (Throwable t) { LOGGER.warn("Error loading spawn egg NBT", t); } // CraftBukkit - SPIGOT-5665
        }) : consumer;
    }

    @Nullable
    public T spawn(WorldServer worldserver, BlockPosition blockposition, EntitySpawnReason entityspawnreason) {
        // CraftBukkit start
        return (T) this.spawn(worldserver, blockposition, entityspawnreason, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Nullable
    public T spawn(WorldServer worldserver, BlockPosition blockposition, EntitySpawnReason entityspawnreason, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason) {
        return (T) this.spawn(worldserver, (Consumer) null, blockposition, entityspawnreason, false, false, spawnReason); // CraftBukkit
        // CraftBukkit end
    }

    @Nullable
    public T spawn(WorldServer worldserver, @Nullable Consumer<T> consumer, BlockPosition blockposition, EntitySpawnReason entityspawnreason, boolean flag, boolean flag1) {
        // CraftBukkit start
        return this.spawn(worldserver, consumer, blockposition, entityspawnreason, flag, flag1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Nullable
    public T spawn(WorldServer worldserver, @Nullable Consumer<T> consumer, BlockPosition blockposition, EntitySpawnReason entityspawnreason, boolean flag, boolean flag1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason) {
        // CraftBukkit end
        T t0 = this.create(worldserver, consumer, blockposition, entityspawnreason, flag, flag1);

        if (t0 != null) {
            // CraftBukkit start
            worldserver.addFreshEntityWithPassengers(t0, spawnReason);
            if (t0.isRemoved()) {
                return null; // Don't return an entity when CreatureSpawnEvent is canceled
            }
            // CraftBukkit end
            if (t0 instanceof EntityInsentient) {
                EntityInsentient entityinsentient = (EntityInsentient) t0;

                entityinsentient.playAmbientSound();
            }
        }

        return t0;
    }

    @Nullable
    public T create(WorldServer worldserver, @Nullable Consumer<T> consumer, BlockPosition blockposition, EntitySpawnReason entityspawnreason, boolean flag, boolean flag1) {
        T t0 = this.create(worldserver, entityspawnreason);

        if (t0 == null) {
            return null;
        } else {
            double d0;

            if (flag) {
                t0.setPos((double) blockposition.getX() + 0.5D, (double) (blockposition.getY() + 1), (double) blockposition.getZ() + 0.5D);
                d0 = getYOffset(worldserver, blockposition, flag1, t0.getBoundingBox());
            } else {
                d0 = 0.0D;
            }

            t0.snapTo((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + d0, (double) blockposition.getZ() + 0.5D, MathHelper.wrapDegrees(worldserver.random.nextFloat() * 360.0F), 0.0F);
            if (t0 instanceof EntityInsentient) {
                EntityInsentient entityinsentient = (EntityInsentient) t0;

                entityinsentient.yHeadRot = entityinsentient.getYRot();
                entityinsentient.yBodyRot = entityinsentient.getYRot();
                entityinsentient.finalizeSpawn(worldserver, worldserver.getCurrentDifficultyAt(entityinsentient.blockPosition()), entityspawnreason, (GroupDataEntity) null);
            }

            if (consumer != null) {
                consumer.accept(t0);
            }

            return t0;
        }
    }

    protected static double getYOffset(IWorldReader iworldreader, BlockPosition blockposition, boolean flag, AxisAlignedBB axisalignedbb) {
        AxisAlignedBB axisalignedbb1 = new AxisAlignedBB(blockposition);

        if (flag) {
            axisalignedbb1 = axisalignedbb1.expandTowards(0.0D, -1.0D, 0.0D);
        }

        Iterable<VoxelShape> iterable = iworldreader.getCollisions((Entity) null, axisalignedbb1);

        return 1.0D + VoxelShapes.collide(EnumDirection.EnumAxis.Y, axisalignedbb, iterable, flag ? -2.0D : -1.0D);
    }

    public static void updateCustomEntityTag(World world, @Nullable EntityLiving entityliving, @Nullable Entity entity, CustomData customdata) {
        MinecraftServer minecraftserver = world.getServer();

        if (minecraftserver != null && entity != null) {
            EntityTypes<?> entitytypes = (EntityTypes) customdata.parseEntityType(minecraftserver.registryAccess(), Registries.ENTITY_TYPE);

            if (entity.getType() == entitytypes) {
                if (!world.isClientSide && entity.getType().onlyOpCanSetNbt()) {
                    if (!(entityliving instanceof EntityHuman)) {
                        return;
                    }

                    EntityHuman entityhuman = (EntityHuman) entityliving;

                    if (!minecraftserver.getPlayerList().isOp(entityhuman.getGameProfile())) {
                        return;
                    }
                }

                customdata.loadInto(entity);
            }
        }
    }

    public boolean canSerialize() {
        return this.serialize;
    }

    public boolean canSummon() {
        return this.summon;
    }

    public boolean fireImmune() {
        return this.fireImmune;
    }

    public boolean canSpawnFarFromPlayer() {
        return this.canSpawnFarFromPlayer;
    }

    public EnumCreatureType getCategory() {
        return this.category;
    }

    public String getDescriptionId() {
        return this.descriptionId;
    }

    public IChatBaseComponent getDescription() {
        if (this.description == null) {
            this.description = IChatBaseComponent.translatable(this.getDescriptionId());
        }

        return this.description;
    }

    public String toString() {
        return this.getDescriptionId();
    }

    public String toShortString() {
        int i = this.getDescriptionId().lastIndexOf(46);

        return i == -1 ? this.getDescriptionId() : this.getDescriptionId().substring(i + 1);
    }

    public Optional<ResourceKey<LootTable>> getDefaultLootTable() {
        return this.lootTable;
    }

    public float getWidth() {
        return this.dimensions.width();
    }

    public float getHeight() {
        return this.dimensions.height();
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    @Nullable
    public T create(World world, EntitySpawnReason entityspawnreason) {
        return (T) (!this.isEnabled(world.enabledFeatures()) ? null : this.factory.create(this, world));
    }

    public static Optional<Entity> create(ValueInput valueinput, World world, EntitySpawnReason entityspawnreason) {
        return SystemUtils.<Entity>ifElse(by(valueinput).map((entitytypes) -> {
            return entitytypes.create(world, entityspawnreason);
        }), (entity) -> {
            entity.load(valueinput);
        }, () -> {
            EntityTypes.LOGGER.warn("Skipping Entity with id {}", valueinput.getStringOr("id", "[invalid]"));
        });
    }

    public AxisAlignedBB getSpawnAABB(double d0, double d1, double d2) {
        float f = this.spawnDimensionsScale * this.getWidth() / 2.0F;
        float f1 = this.spawnDimensionsScale * this.getHeight();

        return new AxisAlignedBB(d0 - (double) f, d1, d2 - (double) f, d0 + (double) f, d1 + (double) f1, d2 + (double) f);
    }

    public boolean isBlockDangerous(IBlockData iblockdata) {
        return this.immuneTo.contains(iblockdata.getBlock()) ? false : (!this.fireImmune && PathfinderAbstract.isBurningBlock(iblockdata) ? true : iblockdata.is(Blocks.WITHER_ROSE) || iblockdata.is(Blocks.SWEET_BERRY_BUSH) || iblockdata.is(Blocks.CACTUS) || iblockdata.is(Blocks.POWDER_SNOW));
    }

    public EntitySize getDimensions() {
        return this.dimensions;
    }

    public static Optional<EntityTypes<?>> by(ValueInput valueinput) {
        return valueinput.<EntityTypes<?>>read("id", EntityTypes.CODEC);
    }

    @Nullable
    public static Entity loadEntityRecursive(NBTTagCompound nbttagcompound, World world, EntitySpawnReason entityspawnreason, Function<Entity, Entity> function) {
        try (ProblemReporter.j problemreporter_j = new ProblemReporter.j(EntityTypes.LOGGER)) {
            return loadEntityRecursive(TagValueInput.create(problemreporter_j, world.registryAccess(), nbttagcompound), world, entityspawnreason, function);
        }
    }

    @Nullable
    public static Entity loadEntityRecursive(ValueInput valueinput, World world, EntitySpawnReason entityspawnreason, Function<Entity, Entity> function) {
        return (Entity) loadStaticEntity(valueinput, world, entityspawnreason).map(function).map((entity) -> {
            for (ValueInput valueinput1 : valueinput.childrenListOrEmpty("Passengers")) {
                Entity entity1 = loadEntityRecursive(valueinput1, world, entityspawnreason, function);

                if (entity1 != null) {
                    entity1.startRiding(entity, true);
                }
            }

            return entity;
        }).orElse(null); // CraftBukkit - decompile error
    }

    public static Stream<Entity> loadEntitiesRecursive(ValueInput.b valueinput_b, World world, EntitySpawnReason entityspawnreason) {
        return valueinput_b.stream().mapMulti((valueinput, consumer) -> {
            loadEntityRecursive(valueinput, world, entityspawnreason, (entity) -> {
                consumer.accept(entity);
                return entity;
            });
        });
    }

    private static Optional<Entity> loadStaticEntity(ValueInput valueinput, World world, EntitySpawnReason entityspawnreason) {
        try {
            return create(valueinput, world, entityspawnreason);
        } catch (RuntimeException runtimeexception) {
            EntityTypes.LOGGER.warn("Exception loading entity: ", runtimeexception);
            return Optional.empty();
        }
    }

    public int clientTrackingRange() {
        return this.clientTrackingRange;
    }

    public int updateInterval() {
        return this.updateInterval;
    }

    public boolean trackDeltas() {
        return this != EntityTypes.PLAYER && this != EntityTypes.LLAMA_SPIT && this != EntityTypes.WITHER && this != EntityTypes.BAT && this != EntityTypes.ITEM_FRAME && this != EntityTypes.GLOW_ITEM_FRAME && this != EntityTypes.LEASH_KNOT && this != EntityTypes.PAINTING && this != EntityTypes.END_CRYSTAL && this != EntityTypes.EVOKER_FANGS;
    }

    public boolean is(TagKey<EntityTypes<?>> tagkey) {
        return this.builtInRegistryHolder.is(tagkey);
    }

    public boolean is(HolderSet<EntityTypes<?>> holderset) {
        return holderset.contains(this.builtInRegistryHolder);
    }

    @Nullable
    public T tryCast(Entity entity) {
        return (T) (entity.getType() == this ? entity : null);
    }

    @Override
    public Class<? extends Entity> getBaseClass() {
        return Entity.class;
    }

    /** @deprecated */
    @Deprecated
    public Holder.c<EntityTypes<?>> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    private static EntityTypes.b<EntityBoat> boatFactory(Supplier<Item> supplier) {
        return (entitytypes, world) -> {
            return new EntityBoat(entitytypes, world, supplier);
        };
    }

    private static EntityTypes.b<ChestBoat> chestBoatFactory(Supplier<Item> supplier) {
        return (entitytypes, world) -> {
            return new ChestBoat(entitytypes, world, supplier);
        };
    }

    private static EntityTypes.b<Raft> raftFactory(Supplier<Item> supplier) {
        return (entitytypes, world) -> {
            return new Raft(entitytypes, world, supplier);
        };
    }

    private static EntityTypes.b<ChestRaft> chestRaftFactory(Supplier<Item> supplier) {
        return (entitytypes, world) -> {
            return new ChestRaft(entitytypes, world, supplier);
        };
    }

    public boolean onlyOpCanSetNbt() {
        return EntityTypes.OP_ONLY_CUSTOM_DATA.contains(this);
    }

    public static class Builder<T extends Entity> {

        private final EntityTypes.b<T> factory;
        private final EnumCreatureType category;
        private ImmutableSet<Block> immuneTo = ImmutableSet.of();
        private boolean serialize = true;
        private boolean summon = true;
        private boolean fireImmune;
        private boolean canSpawnFarFromPlayer;
        private int clientTrackingRange = 5;
        private int updateInterval = 3;
        private EntitySize dimensions = EntitySize.scalable(0.6F, 1.8F);
        private float spawnDimensionsScale = 1.0F;
        private EntityAttachments.a attachments = EntityAttachments.builder();
        private FeatureFlagSet requiredFeatures;
        private DependantName<EntityTypes<?>, Optional<ResourceKey<LootTable>>> lootTable;
        private final DependantName<EntityTypes<?>, String> descriptionId;

        private Builder(EntityTypes.b<T> entitytypes_b, EnumCreatureType enumcreaturetype) {
            this.requiredFeatures = FeatureFlags.VANILLA_SET;
            this.lootTable = (resourcekey) -> {
                return Optional.of(ResourceKey.create(Registries.LOOT_TABLE, resourcekey.location().withPrefix("entities/")));
            };
            this.descriptionId = (resourcekey) -> {
                return SystemUtils.makeDescriptionId("entity", resourcekey.location());
            };
            this.factory = entitytypes_b;
            this.category = enumcreaturetype;
            this.canSpawnFarFromPlayer = enumcreaturetype == EnumCreatureType.CREATURE || enumcreaturetype == EnumCreatureType.MISC;
        }

        public static <T extends Entity> EntityTypes.Builder<T> of(EntityTypes.b entitytypes_b, EnumCreatureType enumcreaturetype) { // CraftBukkit - decompile error
            return new EntityTypes.Builder<T>(entitytypes_b, enumcreaturetype);
        }

        public static <T extends Entity> EntityTypes.Builder<T> createNothing(EnumCreatureType enumcreaturetype) {
            return new EntityTypes.Builder<T>((entitytypes, world) -> {
                return null;
            }, enumcreaturetype);
        }

        public EntityTypes.Builder<T> sized(float f, float f1) {
            this.dimensions = EntitySize.scalable(f, f1);
            return this;
        }

        public EntityTypes.Builder<T> spawnDimensionsScale(float f) {
            this.spawnDimensionsScale = f;
            return this;
        }

        public EntityTypes.Builder<T> eyeHeight(float f) {
            this.dimensions = this.dimensions.withEyeHeight(f);
            return this;
        }

        public EntityTypes.Builder<T> passengerAttachments(float... afloat) {
            for (float f : afloat) {
                this.attachments = this.attachments.attach(EntityAttachment.PASSENGER, 0.0F, f, 0.0F);
            }

            return this;
        }

        public EntityTypes.Builder<T> passengerAttachments(Vec3D... avec3d) {
            for (Vec3D vec3d : avec3d) {
                this.attachments = this.attachments.attach(EntityAttachment.PASSENGER, vec3d);
            }

            return this;
        }

        public EntityTypes.Builder<T> vehicleAttachment(Vec3D vec3d) {
            return this.attach(EntityAttachment.VEHICLE, vec3d);
        }

        public EntityTypes.Builder<T> ridingOffset(float f) {
            return this.attach(EntityAttachment.VEHICLE, 0.0F, -f, 0.0F);
        }

        public EntityTypes.Builder<T> nameTagOffset(float f) {
            return this.attach(EntityAttachment.NAME_TAG, 0.0F, f, 0.0F);
        }

        public EntityTypes.Builder<T> attach(EntityAttachment entityattachment, float f, float f1, float f2) {
            this.attachments = this.attachments.attach(entityattachment, f, f1, f2);
            return this;
        }

        public EntityTypes.Builder<T> attach(EntityAttachment entityattachment, Vec3D vec3d) {
            this.attachments = this.attachments.attach(entityattachment, vec3d);
            return this;
        }

        public EntityTypes.Builder<T> noSummon() {
            this.summon = false;
            return this;
        }

        public EntityTypes.Builder<T> noSave() {
            this.serialize = false;
            return this;
        }

        public EntityTypes.Builder<T> fireImmune() {
            this.fireImmune = true;
            return this;
        }

        public EntityTypes.Builder<T> immuneTo(Block... ablock) {
            this.immuneTo = ImmutableSet.copyOf(ablock);
            return this;
        }

        public EntityTypes.Builder<T> canSpawnFarFromPlayer() {
            this.canSpawnFarFromPlayer = true;
            return this;
        }

        public EntityTypes.Builder<T> clientTrackingRange(int i) {
            this.clientTrackingRange = i;
            return this;
        }

        public EntityTypes.Builder<T> updateInterval(int i) {
            this.updateInterval = i;
            return this;
        }

        public EntityTypes.Builder<T> requiredFeatures(FeatureFlag... afeatureflag) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(afeatureflag);
            return this;
        }

        public EntityTypes.Builder<T> noLootTable() {
            this.lootTable = DependantName.<EntityTypes<?>, Optional<ResourceKey<LootTable>>>fixed(Optional.empty());
            return this;
        }

        public EntityTypes<T> build(ResourceKey<EntityTypes<?>> resourcekey) {
            if (this.serialize) {
                SystemUtils.fetchChoiceType(DataConverterTypes.ENTITY_TREE, resourcekey.location().toString());
            }

            return new EntityTypes<T>(this.factory, this.category, this.serialize, this.summon, this.fireImmune, this.canSpawnFarFromPlayer, this.immuneTo, this.dimensions.withAttachments(this.attachments), this.spawnDimensionsScale, this.clientTrackingRange, this.updateInterval, this.descriptionId.get(resourcekey), this.lootTable.get(resourcekey), this.requiredFeatures);
        }
    }

    @FunctionalInterface
    public interface b<T extends Entity> {

        @Nullable
        T create(EntityTypes<T> entitytypes, World world);
    }
}
