package com.solegendary.reignofnether.unit.interfaces;

import com.solegendary.reignofnether.unit.support.SupportField;
import com.solegendary.reignofnether.unit.support.SupportKind;
import com.solegendary.reignofnether.unit.support.SupportMath;
import com.solegendary.reignofnether.unit.support.SupportState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * A unit that supports instead of fighting - the healers and their kin (the Field Medic, Moonwell Bearer, Bloom
 * Priestess, Tide Priest, Ink Scribe). None is an {@link AttackerUnit}, so the dispatch used to read an attack order on
 * an enemy as "follow it" and a Fight order as a plain walk: lovish (Oct 21 20:52) saw them "end up killing
 * themselves". Support units get one shared brain instead (unit/support/SupportBrain, claude/healer-ai-oct21.md):
 * attack, Fight and attack-building orders hold a standoff behind the front (SupportOrders), they step out of melee
 * that closes in, and they keep supporting while they move.
 * <p>
 * The brain is delivery-agnostic (lovish, Oct 21 23:37: the potions are to be replaced by faction heal and harm
 * abilities): it picks a {@link SupportKind} and a target or spot and asks the unit to cast it here; how it gets there
 * (a thrown potion today, PotionDelivery) is the unit's. A unit with casts answers {@link #canCastSupport} and
 * {@link #castSupport}; a pulse healer (Moonwell, Bloom) or a utility support (Tide Priest, Ink Scribe) has none and
 * gets the orders and the danger step only.
 * <p>
 * The unit class keeps one {@link SupportState}, ticks {@code SupportBrain.tick(this, this)} after {@code Unit.tick},
 * and says how far its support reaches.
 */
public interface SupportUnit extends Unit {

    /** This unit's support order and memory (one per unit, server side). */
    SupportState getSupportState();

    /**
     * How far its support reaches, in blocks: its cast's range, its healing pulse, its ability's range. The standoff
     * behind a fight is kept inside it (reach - SupportBrain.STANDOFF_MARGIN).
     */
    double getSupportReach();

    /** It can hurt an enemy (an AREA_HARM cast): an attack order is then a cast at the target, not an escort. */
    default boolean hasSupportDamage() {
        return false;
    }

    // ------------------------------------------------------------------ casts (the brain chooses, the unit delivers)

    /**
     * Whether it can cast {@code kind} now: it has one, off cooldown, researched; for {@code autocast} also with the
     * player's autocast switch for it on (an attack order's harm ignores the switch, like a click).
     */
    default boolean canCastSupport(ServerLevel level, SupportKind kind, boolean autocast) {
        return false;
    }

    /** How far it can deliver {@code kind} (it walks the rest itself if the brain picks a target a little further). */
    default double supportCastRange(SupportKind kind) {
        return getSupportReach();
    }

    /** The area {@code kind} touches round its target (a single heal's splash too: the safety rules use it). */
    default double supportCastRadius(SupportKind kind) {
        return 0;
    }

    /** How {@code kind} treats the undead (vanilla potions work backwards on them; a plain heal does not). */
    default SupportMath.Undead supportUndeadRule(SupportKind kind) {
        return SupportMath.Undead.NORMAL;
    }

    /** What its CLEANSE lifts (SupportField flags): every affliction by default, only fire for a water potion. */
    default int supportCleanses() {
        return SupportField.F_AFFLICTED;
    }

    /**
     * Delivers {@code kind} at {@code target} (a unit to follow and hit) or, with no target, at the spot (x, y, z).
     * {@code ordered}: an order's cast (an attack order's harm) rather than its own autocast. True if it took it.
     */
    default boolean castSupport(ServerLevel level, SupportKind kind, @Nullable LivingEntity target, double x, double y, double z,
                                boolean ordered) {
        return false;
    }

    /** Its own cast is under way (walking to it, winding up): the brain does not steer it or start another meanwhile. */
    default boolean isSupportCasting() {
        return false;
    }

    /** Drops a cast of its own choosing that has not begun its wind-up (it is stepping out of danger). */
    default void cancelSupportCast() { }

    /** Nothing else to cast: a last job of its own (the medic's water on a burning block). */
    default void supportIdleCast(ServerLevel level, long gameTime) { }

    /** A danger step began (the medic raises her staff as she steps back). */
    default void onSupportStep() { }

    /**
     * Idle only without a support order too: a healer holding a Fight standoff is busy, so the command queue waits for
     * it like it waits for an attack-moving fighter.
     */
    @Override
    default boolean isIdle() {
        return Unit.super.isIdle() && getSupportState().isIdle();
    }
}
