package com.solegendary.reignofnether.unit.reactions;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.BraceAbility;
import com.solegendary.reignofnether.player.Relationship;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Brace (claude/combat-reactions.md): a shield unit with the switch on plants its shield when {@link #THREATS} or
 * more enemies have it as their target, or a charger (a hostile at least {@link #CHARGER_SPEED} times its own
 * speed) is within {@link #CHARGE_RANGE} blocks: for {@link #DURATION_TICKS} it takes {@link #DAMAGE_MULT} of every
 * hit ({@link #DAMAGE_MULT_VETERAN} at rank 3) and moves at {@link #SLOW} speed; then {@link #COOLDOWN_TICKS} on the
 * ability's own cooldown, so the card shows it. The damage cut lives in LivingEntityMixin.actuallyHurt (the one
 * damage path), the slow is a transient movement modifier, the look is a packet to the tracking clients (the bar's
 * rim). Server side, from Unit.tick, one check every {@link #CHECK_INTERVAL} ticks per unit.
 */
public final class Brace {
    private Brace() { }

    public static final int CHECK_INTERVAL = 5;
    public static final int THREATS = 2;
    public static final double THREAT_RANGE = 6.0;
    public static final double CHARGE_RANGE = 4.0;
    public static final double CHARGER_SPEED = 1.3;
    public static final int DURATION_TICKS = 40;
    public static final int COOLDOWN_TICKS = 160;
    public static final float DAMAGE_MULT = 0.75f;
    public static final float DAMAGE_MULT_VETERAN = 0.65f;
    public static final double SLOW = -0.5;   // MULTIPLY_TOTAL
    static final UUID SLOW_MOD = UUID.fromString("c3d1f2e4-5a6b-4c7d-8e9f-0a1b2c3d4e11");

    private static final Map<LivingEntity, Long> BRACED_UNTIL = new WeakHashMap<>();
    private static final Map<LivingEntity, Integer> BRACES = new WeakHashMap<>();   // for the game test
    private static final List<LivingEntity> scratch = new ArrayList<>();

    public static boolean has(Unit unit) {
        for (var a : unit.getAbilities().get())
            if (a == BraceAbility.INSTANCE)
                return true;
        return false;
    }

    public static boolean isBraced(LivingEntity le) {
        Long until = BRACED_UNTIL.get(le);
        return until != null && le.level().getGameTime() < until;
    }

    /** For tests: how many times this unit has braced. */
    public static int braces(LivingEntity le) {
        return BRACES.getOrDefault(le, 0);
    }

    /** The multiplier on damage {@code victim} takes right now: the victim's side of LivingEntityMixin.actuallyHurt. */
    public static float damageMultiplier(LivingEntity victim) {
        if (BRACED_UNTIL.isEmpty() || !isBraced(victim))
            return 1f;
        return VeterancyServerEvents.getRank(victim) >= VeterancyServerEvents.MAX_RANK ? DAMAGE_MULT_VETERAN : DAMAGE_MULT;
    }

    /** Server side, every tick from Unit.tick. */
    public static void tick(Mob mob, Unit unit) {
        if ((mob.tickCount + mob.getId()) % CHECK_INTERVAL != 0 || !(mob.level() instanceof ServerLevel level))
            return;
        if (!has(unit))
            return;
        Long until = BRACED_UNTIL.get(mob);
        long now = level.getGameTime();
        if (until != null) {
            if (now < until)
                return;   // braced: nothing to decide
            BRACED_UNTIL.remove(mob);
            unslow(mob);
        }
        if (!AutocastState.isOn(unit, BraceAbility.INSTANCE) || mob.isPassenger() || !BraceAbility.INSTANCE.isOffCooldown(unit))
            return;
        if (shouldBrace(level, mob, unit))
            brace(level, mob, unit, now);
    }

    static boolean shouldBrace(ServerLevel level, Mob mob, Unit unit) {
        int comingForMe = 0;
        boolean charger = false;
        float mySpeed = unit.getBaseMovementSpeed();
        for (LivingEntity le : UnitGrid.near(level, mob.getX(), mob.getZ(), THREAT_RANGE, scratch)) {
            if (le == mob || !le.isAlive() || !(le instanceof AttackerUnit) || le instanceof WorkerUnit
                    || !(le instanceof Unit lu) || !(le instanceof Mob foe))
                continue;
            if (UnitServerEvents.getUnitToEntityRelationship(unit, le) != Relationship.HOSTILE)
                continue;
            double d = mob.distanceToSqr(le);
            if (d > THREAT_RANGE * THREAT_RANGE)
                continue;
            if (foe.getTarget() == mob)
                comingForMe++;
            if (d <= CHARGE_RANGE * CHARGE_RANGE && lu.getBaseMovementSpeed() >= CHARGER_SPEED * mySpeed
                    && foe.getDeltaMovement().horizontalDistanceSqr() > 0.0025)   // moving, not parked
                charger = true;
        }
        scratch.clear();
        return comingForMe >= THREATS || charger;
    }

    static void brace(ServerLevel level, Mob mob, Unit unit, long now) {
        BRACED_UNTIL.put(mob, now + DURATION_TICKS);
        BRACES.merge(mob, 1, Integer::sum);
        AttributeInstance ms = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null && ms.getModifier(SLOW_MOD) == null)
            ms.addTransientModifier(new AttributeModifier(SLOW_MOD, "bt_brace_slow", SLOW, AttributeModifier.Operation.MULTIPLY_TOTAL));
        BraceAbility.INSTANCE.setToMaxCooldown(unit);
        AbilityClientboundPacket.sendSetCooldownPacket(mob.getId(), BraceAbility.INSTANCE.action, BraceAbility.INSTANCE.cooldownMax);
        ReactionClientboundPacket.brace(mob, DURATION_TICKS);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 10, 0.5, 0.4, 0.5, 0.02);
        level.playSound(null, mob.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.7f, 0.8f);
        ReignOfNether.LOGGER.debug("[Brace] {}#{} braces", mob.getType().toShortString(), mob.getId());
    }

    private static void unslow(Mob mob) {
        AttributeInstance ms = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null && ms.getModifier(SLOW_MOD) != null)
            ms.removeModifier(SLOW_MOD);
    }
}
