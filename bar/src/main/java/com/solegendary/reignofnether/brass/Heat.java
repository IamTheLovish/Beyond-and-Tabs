package com.solegendary.reignofnether.brass;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * The Brassworks' <b>Heat</b> rules (design/brassworks_plan.md section 3), shared by every {@link Machine}:
 * <ul>
 *   <li>every attack adds the machine's {@link Machine#heatPerShot} ({@link #OVERCLOCK_HEAT}x while overclocked);</li>
 *   <li>at {@link #MAX} the machine <b>vents</b> for {@link #VENT_TICKS}: it can't fire ({@link #canFire}), takes
 *       {@link #VENT_DAMAGE_TAKEN}x damage (BrassUnitEvents) and hisses steam; when the vent ends heat is 0;</li>
 *   <li>otherwise heat cools {@link #COOL_PER_SECOND} a second ({@link #tick}, one subtraction every 5 ticks);</li>
 *   <li><b>Overclock</b> (the Guildmaster): {@link #OVERCLOCK_TICKS} of {@link #OVERCLOCK_RATE}x fire rate and
 *       {@link #OVERCLOCK_HEAT}x heat per shot, then {@link #OVERCLOCK_AFTER_HEAT} heat at once - usually the vent.</li>
 * </ul>
 * Also the <b>Field Upgrade</b> modifiers ({@link #applyUpgrade}): Brass Plating = ARMOR +{@link #PLATING_ARMOUR},
 * Long Barrel = attack range +{@link #BARREL_RANGE}. Both are permanent modifiers re-applied from the saved byte.
 * Server thread only; nothing here allocates per tick.
 */
public final class Heat {
    private Heat() { }

    public static final int MAX = 100;
    public static final int VENT_TICKS = 120;
    public static final float VENT_DAMAGE_TAKEN = 1.25f;
    public static final int COOL_PER_SECOND = 4;
    public static final int OVERCLOCK_TICKS = 160;
    public static final float OVERCLOCK_RATE = 1.4f;
    public static final float OVERCLOCK_HEAT = 1.5f;
    public static final int OVERCLOCK_AFTER_HEAT = 30;
    /** Heat at which a machine starts leaking smoke (the warning before the vent). */
    public static final int HOT = 70;

    public static final byte UPGRADE_NONE = 0, UPGRADE_PLATING = 1, UPGRADE_BARREL = 2;
    public static final float PLATING_ARMOUR = 6f;
    public static final float BARREL_RANGE = 3f;
    static final UUID PLATING_UUID = UUID.fromString("7b2c1d4e-55a0-4c1b-9d3e-0a6b7c8d9e01");
    static final UUID BARREL_UUID = UUID.fromString("7b2c1d4e-55a0-4c1b-9d3e-0a6b7c8d9e02");

    public static boolean isVenting(Machine m) {
        return m.ventTicks() > 0;
    }

    /** False while venting: a ranged machine dry-fires, a melee machine's swing lands nothing. */
    public static boolean canFire(Machine m) {
        return m.ventTicks() <= 0;
    }

    public static boolean isOverclocked(Machine m) {
        return m.overclockTicks() > 0;
    }

    /** The attack cooldown scale: faster while overclocked. */
    public static float cooldownScale(Machine m) {
        return isOverclocked(m) ? 1f / OVERCLOCK_RATE : 1f;
    }

    /** One attack's worth of heat; starts the vent at {@link #MAX}. Returns true if the machine just started venting. */
    public static boolean onShot(Machine m, LivingEntity self) {
        if (m.heatPerShot() <= 0 || isVenting(m))
            return false;
        int add = isOverclocked(m) ? Math.round(m.heatPerShot() * OVERCLOCK_HEAT) : m.heatPerShot();
        return addHeat(m, self, add);
    }

    /** Adds heat (Overclock's aftermath uses it too); starts the vent at {@link #MAX}. */
    public static boolean addHeat(Machine m, LivingEntity self, int add) {
        if (isVenting(m) || add <= 0)
            return false;
        int h = Math.min(MAX, m.heat() + add);
        m.setHeat(h);
        if (h < MAX)
            return false;
        m.setVentTicks(VENT_TICKS);
        if (self.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, self.getX(), self.getY() + self.getBbHeight() * 0.7, self.getZ(), 12, 0.3, 0.3, 0.3, 0.08);
            sl.playSound(null, self.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.2f, 0.7f);
        }
        return true;
    }

    /**
     * Every tick from the machine's own tick (server): cools, counts the vent and the overclock down, and shows the
     * steam. One particle packet every 10 ticks while venting, one every 20 while hot - nothing otherwise.
     */
    public static void tick(Machine m, LivingEntity self) {
        if (self.level().isClientSide())
            return;
        int vent = m.ventTicks();
        if (vent > 0) {
            vent--;
            m.setVentTicks(vent);
            if (vent == 0)
                m.setHeat(0);
            else if (vent % 10 == 0 && self.level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, self.getX(), self.getY() + self.getBbHeight() * 0.8,
                    self.getZ(), 3, 0.2, 0.1, 0.2, 0.03);
        } else if (m.heat() > 0 && self.tickCount % 5 == 0) {
            m.setHeat(Math.max(0, m.heat() - COOL_PER_SECOND / 4));
            if (m.heat() >= HOT && self.tickCount % 20 == 0 && self.level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.SMOKE, self.getX(), self.getY() + self.getBbHeight() * 0.9, self.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
        }
        int oc = m.overclockTicks();
        if (oc > 0) {
            oc--;
            m.setOverclockTicks(oc);
            if (oc == 0)
                addHeat(m, self, OVERCLOCK_AFTER_HEAT);   // the aftermath: the machine runs hot for having run fast
            else if (oc % 8 == 0 && self.level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.FLAME, self.getX(), self.getY() + self.getBbHeight() * 0.5, self.getZ(), 1, 0.25, 0.2, 0.25, 0.01);
        }
    }

    /** Starts the Guildmaster's overclock on a machine (the Deployed Turret is already hot: never). */
    public static void overclock(Machine m) {
        if (m.heatPerShot() > 0 && !(m instanceof com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit))
            m.setOverclockTicks(OVERCLOCK_TICKS);
    }

    /**
     * Applies (or re-applies after a reload) the Field Upgrade's modifier for the machine's upgrade byte. Idempotent:
     * a modifier already present is left alone. Returns false for an upgrade the machine can't take.
     */
    public static boolean applyUpgrade(Machine m, LivingEntity self, byte upgrade) {
        if (upgrade == UPGRADE_PLATING) {
            AttributeInstance a = self.getAttribute(Attributes.ARMOR);
            if (a == null)
                return false;
            if (a.getModifier(PLATING_UUID) == null)
                a.addPermanentModifier(new AttributeModifier(PLATING_UUID, "brass_plating", PLATING_ARMOUR, AttributeModifier.Operation.ADDITION));
            m.setUpgrade(UPGRADE_PLATING);
            return true;
        }
        if (upgrade == UPGRADE_BARREL) {
            if (!m.isRangedMachine())
                return false;
            AttributeInstance a = self.getAttribute(AttributeRegistrar.ATTACK_RANGE.get());
            if (a == null)
                return false;
            if (a.getModifier(BARREL_UUID) == null)
                a.addPermanentModifier(new AttributeModifier(BARREL_UUID, "brass_barrel", BARREL_RANGE, AttributeModifier.Operation.ADDITION));
            m.setUpgrade(UPGRADE_BARREL);
            return true;
        }
        return false;
    }

    // save data (called from each machine's addUnitSaveData / readUnitSaveData)
    public static void save(Machine m, CompoundTag tag) {
        tag.putInt("bt_heat", m.heat());
        tag.putInt("bt_vent", m.ventTicks());
        tag.putByte("bt_upgrade", m.upgrade());
    }

    public static void load(Machine m, LivingEntity self, CompoundTag tag) {
        if (tag.contains("bt_heat"))
            m.setHeat(tag.getInt("bt_heat"));
        if (tag.contains("bt_vent"))
            m.setVentTicks(tag.getInt("bt_vent"));
        if (tag.contains("bt_upgrade") && tag.getByte("bt_upgrade") != UPGRADE_NONE)
            applyUpgrade(m, self, tag.getByte("bt_upgrade"));
    }

    /** The unit card's heat line. */
    public static String heatLine(Machine m) {
        return isVenting(m) ? "Venting (" + (m.ventTicks() + 19) / 20 + " s)" : "Heat " + m.heat() + "%";
    }
}
