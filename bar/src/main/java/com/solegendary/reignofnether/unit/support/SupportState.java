package com.solegendary.reignofnether.unit.support;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * One support unit's standing order and memory (SupportBrain): which support order it carries out, the last standoff
 * spot it held, its danger-step clock and the counters the game tests read. Each SupportUnit owns one (a field, so the
 * brain needs no map lookup). Server side only; nothing here is saved - a reloaded healer simply stands where it is,
 * like an attacker whose attack order did not survive the reload.
 */
public final class SupportState {

    public enum Mode {
        /** No support order: idle, or doing a plain order (move, follow, patrol, guard...) the dispatch gave it. */
        NONE,
        /** An attack order with fighters in the selection (pure healers): stay behind {@link #anchor}, out of melee. */
        ESCORT,
        /** An attack order on {@link #orderTarget}: hold the cast-range standoff from it (a unit with an area harm
         * casts it at the target whenever that is safe - SupportBrain.cast). */
        STANDOFF_UNIT,
        /** A Fight (attack-move) order: walk to {@link #destination}, the rear rank's spot; while our front is in a
         * fight, hold the standoff behind it instead. */
        SUPPORT_MOVE,
        /** An attack-building order: outside the building's own weapon range, behind the front attacking it. */
        STANDOFF_BUILDING
    }

    Mode mode = Mode.NONE;
    @Nullable LivingEntity orderTarget;
    @Nullable LivingEntity anchor;
    int[] escortIds = new int[0];
    @Nullable BlockPos destination;
    @Nullable BlockPos buildingPos;
    double buildingX, buildingZ, keepOut;

    // the move the brain last gave the move goal (so it re-paths only when the spot really moved)
    @Nullable BlockPos lastIssued;
    long lastIssueTick = Long.MIN_VALUE / 2;

    // standoff memory: where it last held its standoff, to go back to after a cast pulled it forward
    boolean hasStand;
    double standX, standY, standZ;

    // an idle unit that walked a few blocks for a cast walks back to its post afterwards
    boolean homing;
    double homeX, homeY, homeZ;
    boolean homeArmed;

    // the danger step
    long kiteUntil = Long.MIN_VALUE / 2;
    long lastKite = Long.MIN_VALUE / 2;

    // counters for the game tests (SupportAiGameTests)
    public int heals, areaHeals, harms, cleanses, kites;
    /** Heals (single or area) cast while the unit was under a moving order (support order, follow, patrol, guard). */
    public int healsWhileOrdered;
    /** Where the last cast was aimed. */
    public double lastCastX, lastCastZ;

    public Mode mode() {
        return mode;
    }

    @Nullable
    public BlockPos destination() {
        return destination;
    }

    /** No support order and no walk back to its post: what Unit.isIdle adds for a support unit (SupportUnit.isIdle). */
    public boolean isIdle() {
        return mode == Mode.NONE && !homing;
    }

    /** Any new order ends the support order (Stop, Move, Hold, Follow, an ability click...), and the walk home. */
    public void endOrder() {
        mode = Mode.NONE;
        orderTarget = null;
        anchor = null;
        escortIds = new int[0];
        destination = null;
        buildingPos = null;
        lastIssued = null;
        hasStand = false;
        homing = false;
        homeArmed = false;
    }

    void escort(LivingEntity target, LivingEntity anchor, int[] fighterIds) {
        endOrder();
        mode = Mode.ESCORT;
        orderTarget = target;
        this.anchor = anchor;
        escortIds = fighterIds;
    }

    void standoffFrom(LivingEntity target) {
        endOrder();
        mode = Mode.STANDOFF_UNIT;
        orderTarget = target;
    }

    void fight(BlockPos destination) {
        endOrder();
        mode = Mode.SUPPORT_MOVE;
        this.destination = destination;
    }

    void standoffBuilding(BlockPos buildingPos, double x, double z, double keepOut) {
        endOrder();
        mode = Mode.STANDOFF_BUILDING;
        this.buildingPos = buildingPos;
        buildingX = x;
        buildingZ = z;
        this.keepOut = keepOut;
    }
}
