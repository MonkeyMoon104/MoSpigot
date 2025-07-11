package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;
import net.minecraft.core.particles.Particles;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public abstract class EntityAgeable extends EntityCreature {

    private static final DataWatcherObject<Boolean> DATA_BABY_ID = DataWatcher.<Boolean>defineId(EntityAgeable.class, DataWatcherRegistry.BOOLEAN);
    public static final int BABY_START_AGE = -24000;
    private static final int FORCED_AGE_PARTICLE_TICKS = 40;
    protected static final int DEFAULT_AGE = 0;
    protected static final int DEFAULT_FORCED_AGE = 0;
    protected int age = 0;
    protected int forcedAge = 0;
    protected int forcedAgeTimer;
    public boolean ageLocked; // CraftBukkit

    protected EntityAgeable(EntityTypes<? extends EntityAgeable> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        if (groupdataentity == null) {
            groupdataentity = new EntityAgeable.a(true);
        }

        EntityAgeable.a entityageable_a = (EntityAgeable.a) groupdataentity;

        if (entityageable_a.isShouldSpawnBaby() && entityageable_a.getGroupSize() > 0 && worldaccess.getRandom().nextFloat() <= entityageable_a.getBabySpawnChance()) {
            this.setAge(-24000);
        }

        entityageable_a.increaseGroupSizeByOne();
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Nullable
    public abstract EntityAgeable getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable);

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityAgeable.DATA_BABY_ID, false);
    }

    public boolean canBreed() {
        return false;
    }

    public int getAge() {
        return this.level().isClientSide ? ((Boolean) this.entityData.get(EntityAgeable.DATA_BABY_ID) ? -1 : 1) : this.age;
    }

    public void ageUp(int i, boolean flag) {
        int j = this.getAge();
        int k = j;

        j += i * 20;
        if (j > 0) {
            j = 0;
        }

        int l = j - k;

        this.setAge(j);
        if (flag) {
            this.forcedAge += l;
            if (this.forcedAgeTimer == 0) {
                this.forcedAgeTimer = 40;
            }
        }

        if (this.getAge() == 0) {
            this.setAge(this.forcedAge);
        }

    }

    public void ageUp(int i) {
        this.ageUp(i, false);
    }

    public void setAge(int i) {
        int j = this.getAge();

        this.age = i;
        if (j < 0 && i >= 0 || j >= 0 && i < 0) {
            this.entityData.set(EntityAgeable.DATA_BABY_ID, i < 0);
            this.ageBoundaryReached();
        }

    }

    @Override
    protected void addAdditionalSaveData(ValueOutput valueoutput) {
        super.addAdditionalSaveData(valueoutput);
        valueoutput.putInt("Age", this.getAge());
        valueoutput.putInt("ForcedAge", this.forcedAge);
        valueoutput.putBoolean("AgeLocked", this.ageLocked); // CraftBukkit
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueinput) {
        super.readAdditionalSaveData(valueinput);
        this.setAge(valueinput.getIntOr("Age", 0));
        this.forcedAge = valueinput.getIntOr("ForcedAge", 0);
        this.ageLocked = valueinput.getBooleanOr("AgeLocked", this.ageLocked); // CraftBukkit
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntityAgeable.DATA_BABY_ID.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || ageLocked) { // CraftBukkit
            if (this.forcedAgeTimer > 0) {
                if (this.forcedAgeTimer % 4 == 0) {
                    this.level().addParticle(Particles.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
                }

                --this.forcedAgeTimer;
            }
        } else if (this.isAlive()) {
            int i = this.getAge();

            if (i < 0) {
                ++i;
                this.setAge(i);
            } else if (i > 0) {
                --i;
                this.setAge(i);
            }
        }

    }

    protected void ageBoundaryReached() {
        if (!this.isBaby() && this.isPassenger()) {
            Entity entity = this.getVehicle();

            if (entity instanceof AbstractBoat) {
                AbstractBoat abstractboat = (AbstractBoat) entity;

                if (!abstractboat.hasEnoughSpaceFor(this)) {
                    this.stopRiding();
                }
            }
        }

    }

    @Override
    public boolean isBaby() {
        return this.getAge() < 0;
    }

    @Override
    public void setBaby(boolean flag) {
        this.setAge(flag ? -24000 : 0);
    }

    public static int getSpeedUpSecondsWhenFeeding(int i) {
        return (int) ((float) (i / 20) * 0.1F);
    }

    @VisibleForTesting
    public int getForcedAge() {
        return this.forcedAge;
    }

    @VisibleForTesting
    public int getForcedAgeTimer() {
        return this.forcedAgeTimer;
    }

    public static class a implements GroupDataEntity {

        private int groupSize;
        private final boolean shouldSpawnBaby;
        private final float babySpawnChance;

        public a(boolean flag, float f) {
            this.shouldSpawnBaby = flag;
            this.babySpawnChance = f;
        }

        public a(boolean flag) {
            this(flag, 0.05F);
        }

        public a(float f) {
            this(true, f);
        }

        public int getGroupSize() {
            return this.groupSize;
        }

        public void increaseGroupSizeByOne() {
            ++this.groupSize;
        }

        public boolean isShouldSpawnBaby() {
            return this.shouldSpawnBaby;
        }

        public float getBabySpawnChance() {
            return this.babySpawnChance;
        }
    }
}
