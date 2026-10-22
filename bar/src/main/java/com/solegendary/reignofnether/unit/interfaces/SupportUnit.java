package com.solegendary.reignofnether.unit.interfaces;

import com.solegendary.reignofnether.unit.support.SupportField;
import com.solegendary.reignofnether.unit.support.SupportState;
import net.minecraft.server.level.ServerLevel;

/**
 * A unit that supports instead of fighting - the healers and their kin (the Field Medic, Moonwell Bearer, Bloom
 * Priestess, Tide Priest, Ink Scribe). None is an {@link AttackerUnit}, so the dispatch used to read an attack order on
 * an enemy as "follow it" and a Fight order as a plain walk: lovish (Oct 21 20:52) saw them "end up killing
 * themselves". Support units get one shared brain instead (unit/support/SupportBrain, claude/healer-ai-oct21.md):
 * attack, Fight and attack-building orders hold a standoff behind the front (SupportOrders), they step out of melee
 * that closes in, and they keep supporting while they move.
 * <p>
 * The unit class keeps one {@link SupportState}, ticks {@code SupportBrain.tick(this, this)} after {@code Unit.tick},
 * and says how far its support reaches.
 */
public interface SupportUnit extends Unit {

    /** This unit's support order and memory (one per unit, server side). */
    SupportState getSupportState();

    /**
     * How far its support reaches, in blocks: the potion throw, the healing pulse, its ability's range. The standoff
     * behind a fight is kept inside it (reach - SupportBrain.STANDOFF_MARGIN).
     */
    double getSupportReach();

    /** It can hurt an enemy (the Field Medic's harming potion): an attack order is then a throw at the target. */
    default boolean hasSupportDamage() {
        return false;
    }

    /** Its own cast is under way (a potion on its way to being thrown): the brain does not steer it meanwhile. */
    default boolean isSupportCasting() {
        return false;
    }

    /** Drops a cast of its own choosing that has not begun its wind-up (it is stepping out of danger). */
    default void cancelSupportCast() { }

    /** A danger step began (the medic raises her staff as she steps back). */
    default void onSupportStep() { }

    /** Its own decisions on the brain's look (the medic's potions), after the brain moved it. Server side. */
    default void supportThink(ServerLevel level, SupportField field, long gameTime) { }

    /**
     * Idle only without a support order too: a healer holding a Fight standoff is busy, so the command queue waits for
     * it like it waits for an attack-moving fighter.
     */
    @Override
    default boolean isIdle() {
        return Unit.super.isIdle() && getSupportState().isIdle();
    }
}
