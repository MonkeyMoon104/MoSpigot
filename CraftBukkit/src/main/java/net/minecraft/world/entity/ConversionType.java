package net.minecraft.world.entity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.BehaviorController;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.EntityZombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Scoreboard;

// CraftBukkit start
import net.minecraft.core.BlockPosition;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public enum ConversionType {

    SINGLE(true) {
        @Override
        void convert(EntityInsentient entityinsentient, EntityInsentient entityinsentient1, ConversionParams conversionparams) {
            Entity entity = entityinsentient.getFirstPassenger();

            entityinsentient1.copyPosition(entityinsentient);
            entityinsentient1.setDeltaMovement(entityinsentient.getDeltaMovement());
            if (entity != null) {
                entity.stopRiding();
                entity.boardingCooldown = 0;

                for (Entity entity1 : entityinsentient1.getPassengers()) {
                    entity1.stopRiding();
                    entity1.remove(Entity.RemovalReason.DISCARDED, EntityRemoveEvent.Cause.TRANSFORMATION); // CraftBukkit - add Bukkit remove cause
                }

                entity.startRiding(entityinsentient1);
            }

            Entity entity2 = entityinsentient.getVehicle();

            if (entity2 != null) {
                entityinsentient.stopRiding();
                entityinsentient1.startRiding(entity2);
            }

            if (conversionparams.keepEquipment()) {
                for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
                    ItemStack itemstack = entityinsentient.getItemBySlot(enumitemslot);

                    if (!itemstack.isEmpty()) {
                        entityinsentient1.setItemSlot(enumitemslot, itemstack.copy()); // CraftBukkit - SPIGOT-7996: don't clear yet
                        entityinsentient1.setDropChance(enumitemslot, entityinsentient.getDropChances().byEquipment(enumitemslot));
                    }
                }
            }

            entityinsentient1.fallDistance = entityinsentient.fallDistance;
            entityinsentient1.setSharedFlag(7, entityinsentient.isFallFlying());
            entityinsentient1.lastHurtByPlayerMemoryTime = entityinsentient.lastHurtByPlayerMemoryTime;
            entityinsentient1.hurtTime = entityinsentient.hurtTime;
            entityinsentient1.yBodyRot = entityinsentient.yBodyRot;
            entityinsentient1.setOnGround(entityinsentient.onGround());
            Optional<BlockPosition> optional = entityinsentient.getSleepingPos(); // CraftBukkit - decompile error

            Objects.requireNonNull(entityinsentient1);
            optional.ifPresent(entityinsentient1::setSleepingPos);
            Entity entity3 = entityinsentient.getLeashHolder();

            if (entity3 != null) {
                entityinsentient1.setLeashedTo(entity3, true);
            }

            this.convertCommon(entityinsentient, entityinsentient1, conversionparams);
        }

        // CraftBukkit start
        @Override
        void postConvert(EntityInsentient entityinsentient, EntityInsentient entityinsentient1, ConversionParams conversionparams) {
            if (conversionparams.keepEquipment()) {
                for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
                    ItemStack itemstack = entityinsentient.getItemBySlot(enumitemslot);

                    if (!itemstack.isEmpty()) {
                        itemstack.setCount(0); // SPIGOT-7996: clear after conversion
                    }
                }
            }
        }
        // CraftBukkit end
    },
    SPLIT_ON_DEATH(false) {
        @Override
        void convert(EntityInsentient entityinsentient, EntityInsentient entityinsentient1, ConversionParams conversionparams) {
            Entity entity = entityinsentient.getFirstPassenger();

            if (entity != null) {
                entity.stopRiding();
            }

            Entity entity1 = entityinsentient.getLeashHolder();

            if (entity1 != null) {
                entityinsentient.dropLeash();
            }

            this.convertCommon(entityinsentient, entityinsentient1, conversionparams);
        }
    };

    private static final Set<DataComponentType<?>> COMPONENTS_TO_COPY = Set.of(DataComponents.CUSTOM_NAME, DataComponents.CUSTOM_DATA);
    private final boolean discardAfterConversion;

    ConversionType(final boolean flag) {
        this.discardAfterConversion = flag;
    }

    public boolean shouldDiscardAfterConversion() {
        return this.discardAfterConversion;
    }

    abstract void convert(EntityInsentient entityinsentient, EntityInsentient entityinsentient1, ConversionParams conversionparams);

    void postConvert(EntityInsentient entityinsentient, EntityInsentient entityinsentient1, ConversionParams conversionparams) {} // CraftBukkit

    void convertCommon(EntityInsentient entityinsentient, EntityInsentient entityinsentient1, ConversionParams conversionparams) {
        entityinsentient1.setAbsorptionAmount(entityinsentient.getAbsorptionAmount());

        for (MobEffect mobeffect : entityinsentient.getActiveEffects()) {
            entityinsentient1.addEffect(new MobEffect(mobeffect));
        }

        if (entityinsentient.isBaby()) {
            entityinsentient1.setBaby(true);
        }

        if (entityinsentient instanceof EntityAgeable entityageable) {
            if (entityinsentient1 instanceof EntityAgeable entityageable1) {
                entityageable1.setAge(entityageable.getAge());
                entityageable1.forcedAge = entityageable.forcedAge;
                entityageable1.forcedAgeTimer = entityageable.forcedAgeTimer;
            }
        }

        BehaviorController<?> behaviorcontroller = entityinsentient.getBrain();
        BehaviorController<?> behaviorcontroller1 = entityinsentient1.getBrain();

        if (behaviorcontroller.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED) && behaviorcontroller.hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
            behaviorcontroller1.setMemory(MemoryModuleType.ANGRY_AT, behaviorcontroller.getMemory(MemoryModuleType.ANGRY_AT));
        }

        if (conversionparams.preserveCanPickUpLoot()) {
            entityinsentient1.setCanPickUpLoot(entityinsentient.canPickUpLoot());
        }

        entityinsentient1.setLeftHanded(entityinsentient.isLeftHanded());
        entityinsentient1.setNoAi(entityinsentient.isNoAi());
        if (entityinsentient.isPersistenceRequired()) {
            entityinsentient1.setPersistenceRequired();
        }

        entityinsentient1.setCustomNameVisible(entityinsentient.isCustomNameVisible());
        entityinsentient1.setSharedFlagOnFire(entityinsentient.isOnFire());
        entityinsentient1.setInvulnerable(entityinsentient.isInvulnerable());
        entityinsentient1.setNoGravity(entityinsentient.isNoGravity());
        entityinsentient1.setPortalCooldown(entityinsentient.getPortalCooldown());
        entityinsentient1.setSilent(entityinsentient.isSilent());
        Set<String> set = entityinsentient.getTags(); // CraftBukkit - decompile error

        Objects.requireNonNull(entityinsentient1);
        set.forEach(entityinsentient1::addTag);

        for (DataComponentType<?> datacomponenttype : ConversionType.COMPONENTS_TO_COPY) {
            copyComponent(entityinsentient, entityinsentient1, datacomponenttype);
        }

        if (conversionparams.team() != null) {
            Scoreboard scoreboard = entityinsentient1.level().getScoreboard();

            scoreboard.addPlayerToTeam(entityinsentient1.getStringUUID(), conversionparams.team());
            if (entityinsentient.getTeam() != null && entityinsentient.getTeam() == conversionparams.team()) {
                scoreboard.removePlayerFromTeam(entityinsentient.getStringUUID(), entityinsentient.getTeam());
            }
        }

        if (entityinsentient instanceof EntityZombie entityzombie) {
            if (entityzombie.canBreakDoors() && entityinsentient1 instanceof EntityZombie entityzombie1) {
                entityzombie1.setCanBreakDoors(true);
            }
        }

    }

    private static <T> void copyComponent(EntityInsentient entityinsentient, EntityInsentient entityinsentient1, DataComponentType<T> datacomponenttype) {
        T t0 = (T) entityinsentient.get(datacomponenttype);

        if (t0 != null) {
            entityinsentient1.setComponent(datacomponenttype, t0);
        }

    }
}
