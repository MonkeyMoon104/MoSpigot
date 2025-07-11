package net.minecraft.world.entity.animal;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.INamable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityLightning;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.IShearable;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemLiquidUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTables;

// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityTransformEvent;
// CraftBukkit end

public class EntityMushroomCow extends AbstractCow implements IShearable {

    private static final DataWatcherObject<Integer> DATA_TYPE = DataWatcher.<Integer>defineId(EntityMushroomCow.class, DataWatcherRegistry.INT);
    private static final int MUTATE_CHANCE = 1024;
    private static final String TAG_STEW_EFFECTS = "stew_effects";
    @Nullable
    public SuspiciousStewEffects stewEffects;
    @Nullable
    private UUID lastLightningBoltUUID;

    public EntityMushroomCow(EntityTypes<? extends EntityMushroomCow> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    public float getWalkTargetValue(BlockPosition blockposition, IWorldReader iworldreader) {
        return iworldreader.getBlockState(blockposition.below()).is(Blocks.MYCELIUM) ? 10.0F : iworldreader.getPathfindingCostFromLightLevels(blockposition);
    }

    public static boolean checkMushroomSpawnRules(EntityTypes<EntityMushroomCow> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return generatoraccess.getBlockState(blockposition.below()).is(TagsBlock.MOOSHROOMS_SPAWNABLE_ON) && isBrightEnoughToSpawn(generatoraccess, blockposition);
    }

    @Override
    public void thunderHit(WorldServer worldserver, EntityLightning entitylightning) {
        UUID uuid = entitylightning.getUUID();

        if (!uuid.equals(this.lastLightningBoltUUID)) {
            this.setVariant(this.getVariant() == EntityMushroomCow.Type.RED ? EntityMushroomCow.Type.BROWN : EntityMushroomCow.Type.RED);
            this.lastLightningBoltUUID = uuid;
            this.playSound(SoundEffects.MOOSHROOM_CONVERT, 2.0F, 1.0F);
        }

    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityMushroomCow.DATA_TYPE, EntityMushroomCow.Type.DEFAULT.id);
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.is(Items.BOWL) && !this.isBaby()) {
            boolean flag = false;
            ItemStack itemstack1;

            if (this.stewEffects != null) {
                flag = true;
                itemstack1 = new ItemStack(Items.SUSPICIOUS_STEW);
                itemstack1.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.stewEffects);
                this.stewEffects = null;
            } else {
                itemstack1 = new ItemStack(Items.MUSHROOM_STEW);
            }

            ItemStack itemstack2 = ItemLiquidUtil.createFilledResult(itemstack, entityhuman, itemstack1, false);

            entityhuman.setItemInHand(enumhand, itemstack2);
            SoundEffect soundeffect;

            if (flag) {
                soundeffect = SoundEffects.MOOSHROOM_MILK_SUSPICIOUSLY;
            } else {
                soundeffect = SoundEffects.MOOSHROOM_MILK;
            }

            this.playSound(soundeffect, 1.0F, 1.0F);
            return EnumInteractionResult.SUCCESS;
        } else if (itemstack.is(Items.SHEARS) && this.readyForShearing()) {
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
        } else if (this.getVariant() == EntityMushroomCow.Type.BROWN) {
            Optional<SuspiciousStewEffects> optional = this.getEffectsFromItemStack(itemstack);

            if (optional.isEmpty()) {
                return super.mobInteract(entityhuman, enumhand);
            } else {
                if (this.stewEffects != null) {
                    for (int i = 0; i < 2; ++i) {
                        this.level().addParticle(Particles.SMOKE, this.getX() + this.random.nextDouble() / 2.0D, this.getY(0.5D), this.getZ() + this.random.nextDouble() / 2.0D, 0.0D, this.random.nextDouble() / 5.0D, 0.0D);
                    }
                } else {
                    itemstack.consume(1, entityhuman);

                    for (int j = 0; j < 4; ++j) {
                        this.level().addParticle(Particles.EFFECT, this.getX() + this.random.nextDouble() / 2.0D, this.getY(0.5D), this.getZ() + this.random.nextDouble() / 2.0D, 0.0D, this.random.nextDouble() / 5.0D, 0.0D);
                    }

                    this.stewEffects = (SuspiciousStewEffects) optional.get();
                    this.playSound(SoundEffects.MOOSHROOM_EAT, 2.0F, 1.0F);
                }

                return EnumInteractionResult.SUCCESS;
            }
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    @Override
    public void shear(WorldServer worldserver, SoundCategory soundcategory, ItemStack itemstack) {
        worldserver.playSound((Entity) null, (Entity) this, SoundEffects.MOOSHROOM_SHEAR, soundcategory, 1.0F, 1.0F);
        this.convertTo(EntityTypes.COW, ConversionParams.single(this, false, false), (entitycow) -> {
            worldserver.sendParticles(Particles.EXPLOSION, this.getX(), this.getY(0.5D), this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            this.dropFromShearingLootTable(worldserver, LootTables.SHEAR_MOOSHROOM, itemstack, (worldserver1, itemstack1) -> {
                for (int i = 0; i < itemstack1.getCount(); ++i) {
                    // CraftBukkit start
                    EntityItem entityitem = new EntityItem(this.level(), this.getX(), this.getY(1.0D), this.getZ(), itemstack1.copyWithCount(1));
                    EntityDropItemEvent event = new EntityDropItemEvent(this.getBukkitEntity(), (org.bukkit.entity.Item) entityitem.getBukkitEntity());
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        continue;
                    }
                    worldserver1.addFreshEntity(entityitem);
                    // CraftBukkit end
                }

            });
        }, EntityTransformEvent.TransformReason.SHEARED, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SHEARED); // CraftBukkit
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isBaby();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.store("Type", EntityMushroomCow.Type.CODEC, this.getVariant());
        valueoutput.storeNullable("stew_effects", SuspiciousStewEffects.CODEC, this.stewEffects);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setVariant((EntityMushroomCow.Type) valueinput.read("Type", EntityMushroomCow.Type.CODEC).orElse(EntityMushroomCow.Type.DEFAULT));
        this.stewEffects = (SuspiciousStewEffects) valueinput.read("stew_effects", SuspiciousStewEffects.CODEC).orElse(null); // CraftBukkit - decompile error
    }

    private Optional<SuspiciousStewEffects> getEffectsFromItemStack(ItemStack itemstack) {
        SuspiciousEffectHolder suspiciouseffectholder = SuspiciousEffectHolder.tryGet(itemstack.getItem());

        return suspiciouseffectholder != null ? Optional.of(suspiciouseffectholder.getSuspiciousEffects()) : Optional.empty();
    }

    public void setVariant(EntityMushroomCow.Type entitymushroomcow_type) {
        this.entityData.set(EntityMushroomCow.DATA_TYPE, entitymushroomcow_type.id);
    }

    public EntityMushroomCow.Type getVariant() {
        return EntityMushroomCow.Type.byId((Integer) this.entityData.get(EntityMushroomCow.DATA_TYPE));
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.MOOSHROOM_VARIANT ? castComponentValue(datacomponenttype, this.getVariant()) : super.get(datacomponenttype));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.MOOSHROOM_VARIANT);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.MOOSHROOM_VARIANT) {
            this.setVariant((EntityMushroomCow.Type) castComponentValue(DataComponents.MOOSHROOM_VARIANT, t0));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    @Nullable
    @Override
    public EntityMushroomCow getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        EntityMushroomCow entitymushroomcow = EntityTypes.MOOSHROOM.create(worldserver, EntitySpawnReason.BREEDING);

        if (entitymushroomcow != null) {
            entitymushroomcow.setVariant(this.getOffspringVariant((EntityMushroomCow) entityageable));
        }

        return entitymushroomcow;
    }

    private EntityMushroomCow.Type getOffspringVariant(EntityMushroomCow entitymushroomcow) {
        EntityMushroomCow.Type entitymushroomcow_type = this.getVariant();
        EntityMushroomCow.Type entitymushroomcow_type1 = entitymushroomcow.getVariant();
        EntityMushroomCow.Type entitymushroomcow_type2;

        if (entitymushroomcow_type == entitymushroomcow_type1 && this.random.nextInt(1024) == 0) {
            entitymushroomcow_type2 = entitymushroomcow_type == EntityMushroomCow.Type.BROWN ? EntityMushroomCow.Type.RED : EntityMushroomCow.Type.BROWN;
        } else {
            entitymushroomcow_type2 = this.random.nextBoolean() ? entitymushroomcow_type : entitymushroomcow_type1;
        }

        return entitymushroomcow_type2;
    }

    public static enum Type implements INamable {

        RED("red", 0, Blocks.RED_MUSHROOM.defaultBlockState()), BROWN("brown", 1, Blocks.BROWN_MUSHROOM.defaultBlockState());

        public static final EntityMushroomCow.Type DEFAULT = EntityMushroomCow.Type.RED;
        public static final Codec<EntityMushroomCow.Type> CODEC = INamable.<EntityMushroomCow.Type>fromEnum(EntityMushroomCow.Type::values);
        private static final IntFunction<EntityMushroomCow.Type> BY_ID = ByIdMap.<EntityMushroomCow.Type>continuous(EntityMushroomCow.Type::id, values(), ByIdMap.a.CLAMP);
        public static final StreamCodec<ByteBuf, EntityMushroomCow.Type> STREAM_CODEC = ByteBufCodecs.idMapper(EntityMushroomCow.Type.BY_ID, EntityMushroomCow.Type::id);
        private final String type;
        final int id;
        private final IBlockData blockState;

        private Type(final String s, final int i, final IBlockData iblockdata) {
            this.type = s;
            this.id = i;
            this.blockState = iblockdata;
        }

        public IBlockData getBlockState() {
            return this.blockState;
        }

        @Override
        public String getSerializedName() {
            return this.type;
        }

        private int id() {
            return this.id;
        }

        static EntityMushroomCow.Type byId(int i) {
            return (EntityMushroomCow.Type) EntityMushroomCow.Type.BY_ID.apply(i);
        }
    }
}
