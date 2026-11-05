package com.solegendary.reignofnether.unit.units.monsters;

/**
 * The Tithe Binder's rules as plain arithmetic (no Minecraft types, so JUnit pins every number; TitheLedgerTest).
 *
 * <p><b>STUB (tests first).</b> This commit registers the unit end to end and holds only the ledger's API: the count
 * stays at {@link #START_WEAPONS}, no kill or assist binds anything, nothing decays, a volley throws only the hook
 * ({@link #HOOK_DAMAGE}) and Grave Hook has no outcome (so the unit refuses it). The constants are the final ones.
 * Every behaviour test in TitheLedgerTest and TitheBinderGameTests fails against this; the rules land in the next
 * commit.
 */
public final class TitheLedger {

    // ------------------------------------------------------------------------------------------- the count
    public static final int START_WEAPONS = 1;
    public static final int MAX_WEAPONS = 10;
    /** Weapons 1-5 are his while he lives; 6-10 are restless. */
    public static final int PERMANENT_WEAPONS = 5;
    /** At most one completed bind per this many ticks (the bind clip is 32 ticks; queued binds wait). */
    public static final int BIND_GAP_TICKS = 20;
    /** An assist: he damaged the victim at most this many ticks (3 s) before it died. */
    public static final int ASSIST_WINDOW_TICKS = 60;
    /** 20 s without dealing damage before the first restless weapon leaves... */
    public static final int RESTLESS_AFTER_TICKS = 400;
    /** ...then one more every 15 s. */
    public static final int RESTLESS_EVERY_TICKS = 300;

    // ------------------------------------------------------------------------------------------- the volley
    /** Slot 1, his own rusted hook. */
    public static final float HOOK_DAMAGE = 6f;
    /** Every bound weapon (slots 2-10): the shapes are cosmetic, count = power. */
    public static final float WEAPON_DAMAGE = 3f;
    /** The volley's telegraph: the first weapon leaves at clip tick 14. */
    public static final int WINDUP_TICKS = 14;
    /** One weapon released every 2 ticks after the first. */
    public static final int RELEASE_GAP_TICKS = 2;
    /** One volley per 60 ticks (3 s), whatever the count. */
    public static final int VOLLEY_CYCLE_TICKS = 60;
    /** Buildings take half: an anti-unit snowball, not a siege unit. */
    public static final float BUILDING_MULT = 0.5f;
    /** A weapon whose target died before it landed flies on to the nearest enemy within this many blocks of it. */
    public static final double RETARGET_RADIUS = 4.0;

    // ------------------------------------------------------------------------------------------- Grave Hook
    public static final int HOOK_RANGE = 12;
    public static final int HOOK_COOLDOWN_SECONDS = 25;
    /** The clip's wrap (5.7: telegraph t0-12, sling t13-14, flight t15-20): the pull or the root starts here. */
    public static final int HOOK_WRAP_TICK = 21;
    public static final double DRAG_BLOCKS = 4.0;
    public static final int DRAG_TICKS = 16;
    /** After the drag the light target stays stunned 1.5 s (it is stunned through the drag as well). */
    public static final int STUN_TICKS = 30;
    /** A heavy target is rooted 1.5 s instead of dragged. */
    public static final int ROOT_TICKS = 30;
    /**
     * The design's "population >= 3 heavies" (this game has no population; lead's correction, Nov 5): a target with
     * at least this much max health, or a hitbox at least {@link #HEAVY_WIDTH} blocks wide, is rooted, not dragged.
     */
    public static final double HEAVY_MAX_HEALTH = 150;
    public static final double HEAVY_WIDTH = 1.4;
    /** A heavy one pulls the Binder this far toward it instead (the chain shows who outweighs whom). */
    public static final double BINDER_PULL_BLOCKS = 1.0;
    /** A drag never pulls the target closer than this to the Binder (no pulling a unit into his robe). */
    public static final double DRAG_MIN_GAP = 1.5;

    public enum Hook { DRAG, ROOT }

    public int weapons() {
        return START_WEAPONS;   // STUB
    }

    public boolean hasHalf() {
        return false;   // STUB
    }

    public int queued() {
        return 0;   // STUB
    }

    public void credit(int halves, long now) {
        // STUB: no bind
    }

    public void onDamageDealt(long now) {
        // STUB
    }

    public int tick(long now) {
        return 0;   // STUB: nothing lands, nothing leaves
    }

    public void restore(int weapons, boolean half, long now) {
        // STUB: the count stays at 1
    }

    public static int bindHalves(boolean lastHit, boolean worker, boolean summon, boolean inAssistWindow) {
        return 0;   // STUB
    }

    public static boolean inAssistWindow(long hitTick, long deathTick) {
        return false;   // STUB
    }

    public static int volleySize(int weapons) {
        return START_WEAPONS;   // STUB: only the hook
    }

    public static float weaponDamage(int k) {
        return HOOK_DAMAGE;   // STUB
    }

    public static float volleyDamage(int weapons) {
        return HOOK_DAMAGE;   // STUB
    }

    public static int flightTicks(double distance) {
        return (int) Math.round(6 + 0.4 * Math.max(0, distance));
    }

    public static int hitDelay(int k, double distance) {
        return RELEASE_GAP_TICKS * k + flightTicks(distance);
    }

    public static float buildingDamage(float damage) {
        return damage;   // STUB: no building rule
    }

    /** STUB: no outcome, so the unit refuses the cast. */
    public static Hook hookOutcome(double maxHealth, double width) {
        return null;
    }

    public static double dragDistance(double distance) {
        return 0;   // STUB
    }

    public static double dragProgress(int tick) {
        return 0;   // STUB
    }
}
