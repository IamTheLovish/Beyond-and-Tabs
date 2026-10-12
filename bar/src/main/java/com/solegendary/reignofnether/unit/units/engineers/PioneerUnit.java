package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.abilities.Overdrive;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.CombatEngineers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Brassworks combat engineer - the <b>Pioneer</b> (design/combat_engineers.md): an armoured dwarf in a riveted brass
 * cuirass with a steam rig on his back, trained at the Assembly Works. The sturdiest and slowest engineer (a dwarf in
 * plate); its trick is <b>Overdrive</b> - x3 build power for 8 s (construction, reclaim and field repair all read it),
 * then a 4 s vent with none at all. The burst lives here: {@link #getBuildPower} is what the building flow, the wreck
 * reclaim pass and the field repair all ask. Not saved - a reload simply ends a burst early.
 */
public class PioneerUnit extends CombatEngineerUnit {

    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new Overdrive(), Keybindings.hotkey1);   // V: free on the Brassworks build menu
    }

    final static public float maxHealth = 120.0f;
    final static public float movementSpeed = 0.28f;

    // game ticks: driving until overdriveUntil, venting until ventUntil (server side; the client never needs them)
    private long overdriveUntil = Long.MIN_VALUE, ventUntil = Long.MIN_VALUE;
    private boolean ventAnnounced = true;

    public PioneerUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected Abilities engineerAbilities() {
        return ABILITIES;
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.PIONEER; }

    public static AttributeSupplier.Builder createAttributes() {
        return CombatEngineerUnit.createAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed);
    }

    /** Opens the rig at {@code now}; false while it is still driving or venting. Public for the ability and the test. */
    public boolean startOverdrive(long now) {
        if (now < ventUntil)
            return false;
        overdriveUntil = now + Overdrive.DRIVE_TICKS;
        ventUntil = overdriveUntil + Overdrive.VENT_TICKS;
        ventAnnounced = false;
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.blockPosition(), SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 1.4f, 1.3f);
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.6, getZ(), 8, 0.3, 0.2, 0.3, 0.04);
        }
        return true;
    }

    public boolean isOverdriven() {
        return this.level().getGameTime() < overdriveUntil;
    }

    public boolean isVenting() {
        long now = this.level().getGameTime();
        return now >= overdriveUntil && now < ventUntil;
    }

    @Override
    public float getBuildPower() {
        long now = this.level().getGameTime();
        if (now < overdriveUntil)
            return CombatEngineers.BUILD_POWER * Overdrive.POWER_MULT;
        if (now < ventUntil)
            return 0f;   // venting: no construction, no reclaim, no repair
        return CombatEngineers.BUILD_POWER;
    }

    /** Steam off the rig while it drives, a hiss and smoke when it vents. Twice a second at most. */
    @Override
    protected void engineerServerTick() {
        if (!(this.level() instanceof ServerLevel sl))
            return;
        long now = sl.getGameTime();
        if (now >= ventUntil)
            return;
        if (now >= overdriveUntil && !ventAnnounced) {
            ventAnnounced = true;
            sl.playSound(null, this.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.0f, 0.7f);
        }
        if (now % 10 != 0)
            return;
        // the rig sits on the back: behind the body, at shoulder height
        float yaw = this.yBodyRot * ((float) Math.PI / 180f);
        double bx = getX() + Mth.sin(yaw) * 0.35, bz = getZ() - Mth.cos(yaw) * 0.35;
        if (now < overdriveUntil)
            sl.sendParticles(ParticleTypes.CLOUD, bx, getY() + 1.7, bz, 2, 0.1, 0.1, 0.1, 0.03);
        else
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, bx, getY() + 1.6, bz, 1, 0.05, 0.05, 0.05, 0.01);
    }
}
