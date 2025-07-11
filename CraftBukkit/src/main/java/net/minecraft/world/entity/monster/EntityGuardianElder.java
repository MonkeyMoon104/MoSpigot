package net.minecraft.world.entity.monster;

import java.util.List;
import net.minecraft.network.protocol.game.PacketPlayOutGameStateChange;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.level.World;

public class EntityGuardianElder extends EntityGuardian {

    public static final float ELDER_SIZE_SCALE = EntityTypes.ELDER_GUARDIAN.getWidth() / EntityTypes.GUARDIAN.getWidth();
    private static final int EFFECT_INTERVAL = 1200;
    private static final int EFFECT_RADIUS = 50;
    private static final int EFFECT_DURATION = 6000;
    private static final int EFFECT_AMPLIFIER = 2;
    private static final int EFFECT_DISPLAY_LIMIT = 1200;

    public EntityGuardianElder(EntityTypes<? extends EntityGuardianElder> entitytypes, World world) {
        super(entitytypes, world);
        this.setPersistenceRequired();
        if (this.randomStrollGoal != null) {
            this.randomStrollGoal.setInterval(400);
        }

    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityGuardian.createAttributes().add(GenericAttributes.MOVEMENT_SPEED, (double) 0.3F).add(GenericAttributes.ATTACK_DAMAGE, 8.0D).add(GenericAttributes.MAX_HEALTH, 80.0D);
    }

    @Override
    public int getAttackDuration() {
        return 60;
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return this.isInWater() ? SoundEffects.ELDER_GUARDIAN_AMBIENT : SoundEffects.ELDER_GUARDIAN_AMBIENT_LAND;
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return this.isInWater() ? SoundEffects.ELDER_GUARDIAN_HURT : SoundEffects.ELDER_GUARDIAN_HURT_LAND;
    }

    @Override
    protected SoundEffect getDeathSound() {
        return this.isInWater() ? SoundEffects.ELDER_GUARDIAN_DEATH : SoundEffects.ELDER_GUARDIAN_DEATH_LAND;
    }

    @Override
    protected SoundEffect getFlopSound() {
        return SoundEffects.ELDER_GUARDIAN_FLOP;
    }

    @Override
    protected void customServerAiStep(WorldServer worldserver) {
        super.customServerAiStep(worldserver);
        if ((this.tickCount + this.getId()) % 1200 == 0) {
            MobEffect mobeffect = new MobEffect(MobEffects.MINING_FATIGUE, 6000, 2);
            List<EntityPlayer> list = MobEffectUtil.addEffectToPlayersAround(worldserver, this, this.position(), 50.0D, mobeffect, 1200, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit

            list.forEach((entityplayer) -> {
                entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.GUARDIAN_ELDER_EFFECT, this.isSilent() ? 0.0F : 1.0F));
            });
        }

        if (!this.hasHome()) {
            this.setHomeTo(this.blockPosition(), 16);
        }

    }
}
