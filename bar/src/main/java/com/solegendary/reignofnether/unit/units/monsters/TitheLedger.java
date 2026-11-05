package com.solegendary.reignofnether.unit.units.monsters;

/**
 * The Tithe Binder's rules as plain arithmetic (no Minecraft types, so JUnit pins every number; TitheLedgerTest):
 * how many weapons are bound to him, how a kill or an assist becomes one, the restless decay of weapons 6-10, the
 * volley's damage plan and Grave Hook's outcome. The unit ({@link TitheBinderUnit}) owns one ledger and feeds it the
 * clock ({@code now}, game ticks); the server events feed it the credits. Design sheet: .scratch/unit1/design-sheet.md
 * sections 5.3-5.7 and 7.1 (lovish's brief, designer pass 1, Nov 5 2026), with the lead's corrections: no population,
 * and Grave Hook's "pop >= 3 heavies" read as max health / hitbox width ({@link #hookOutcome}).
 *
 * <p><b>Count.</b> A fresh Binder carries {@link #START_WEAPONS} (his own Grave Hook). A last hit by any of his weapons
 * binds one more; an assist (he damaged the victim within {@link #ASSIST_WINDOW_TICKS} before it died, someone else
 * landed the last hit) binds half of one, two halves make one. A summon gives nothing (the same anti-farm rule as
 * veterancy and essence); a worker gives only half, even on a last hit (and half on an assist: never a quarter).
 * Completed binds queue and at most one lands per {@link #BIND_GAP_TICKS} ticks, so a mass death does not jump 1 -> 10
 * in a frame and every bind plays its 32-tick clip. The count stops at {@link #MAX_WEAPONS}: credits past it are
 * dropped (and only refresh the restless timer).
 *
 * <p><b>Restless.</b> Weapons 1-{@link #PERMANENT_WEAPONS} stay while he lives. Above that, once he has dealt no
 * damage (and had no credit) for {@link #RESTLESS_AFTER_TICKS} ticks, the top weapon leaves, and another every
 * {@link #RESTLESS_EVERY_TICKS} ticks after that, down to {@link #PERMANENT_WEAPONS}: the first leaves at 20 s, the next
 * at 35 s, 50 s... Damage dealt or a credit restarts the count.
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

    private int weapons = START_WEAPONS;
    private boolean half;
    private int queued;
    private long lastBind = Long.MIN_VALUE / 4;
    private long restlessFrom;
    private int leaves;

    public int weapons() {
        return weapons;
    }

    /** Half a bind waiting for its other half (the ghost in the next slot). */
    public boolean hasHalf() {
        return half;
    }

    /** Completed binds waiting for their turn ({@link #BIND_GAP_TICKS}). */
    public int queued() {
        return queued;
    }

    /** {@code halves} halves of a bind (2 = a full one): queued, capped, and the restless timer restarts. */
    public void credit(int halves, long now) {
        if (halves <= 0)
            return;
        refresh(now);
        for (int i = 0; i < halves; i++) {
            if (weapons + queued >= MAX_WEAPONS) {
                half = false;   // a ghost slot past the tenth has nowhere to go
                return;
            }
            if (half) {
                half = false;
                queued++;
            } else {
                half = true;
            }
        }
    }

    /** He dealt damage: the restless count starts again. */
    public void onDamageDealt(long now) {
        refresh(now);
    }

    private void refresh(long now) {
        restlessFrom = now;
        leaves = 0;
    }

    /**
     * One server tick: lands the next queued bind when the last was {@link #BIND_GAP_TICKS} ago, else lets one
     * restless weapon go when it is due. Returns +1 for a bind, -1 for a weapon that left, 0 otherwise.
     */
    public int tick(long now) {
        if (queued > 0 && now - lastBind >= BIND_GAP_TICKS) {
            queued--;
            weapons = Math.min(MAX_WEAPONS, weapons + 1);
            lastBind = now;
            return 1;
        }
        if (queued == 0 && weapons > PERMANENT_WEAPONS
                && now >= restlessFrom + RESTLESS_AFTER_TICKS + (long) RESTLESS_EVERY_TICKS * leaves) {
            weapons--;
            leaves++;
            return -1;
        }
        return 0;
    }

    /** Sets the count (a saved unit, a test's setup): clamped to 1-10, nothing queued, the restless count from now. */
    public void restore(int weapons, boolean half, long now) {
        this.weapons = Math.max(START_WEAPONS, Math.min(MAX_WEAPONS, weapons));
        this.half = half && this.weapons < MAX_WEAPONS;
        this.queued = 0;
        this.lastBind = Long.MIN_VALUE / 4;
        refresh(now);
    }

    // ------------------------------------------------------------------------------------------- the kill rule

    /**
     * Halves of a bind one Binder earns from one death: a last hit 2 (a worker 1), an assist inside the window 1, a
     * summon nothing, anything else nothing.
     */
    public static int bindHalves(boolean lastHit, boolean worker, boolean summon, boolean inAssistWindow) {
        if (summon)
            return 0;
        if (lastHit)
            return worker ? 1 : 2;
        return inAssistWindow ? 1 : 0;
    }

    /** Whether a hit at {@code hitTick} is an assist on a death at {@code deathTick}. */
    public static boolean inAssistWindow(long hitTick, long deathTick) {
        return deathTick >= hitTick && deathTick - hitTick <= ASSIST_WINDOW_TICKS;
    }

    // ------------------------------------------------------------------------------------------- the volley plan

    /** How many weapons a volley throws: every docked one, 1-10. */
    public static int volleySize(int weapons) {
        return Math.max(START_WEAPONS, Math.min(MAX_WEAPONS, weapons));
    }

    /** The k-th weapon's damage (k = 0 is the hook). */
    public static float weaponDamage(int k) {
        return k == 0 ? HOOK_DAMAGE : WEAPON_DAMAGE;
    }

    /** A whole volley's damage at {@code weapons}: 6 + 3 per bound weapon. */
    public static float volleyDamage(int weapons) {
        float sum = 0;
        for (int k = 0, n = volleySize(weapons); k < n; k++)
            sum += weaponDamage(k);
        return sum;
    }

    /** Flight time over {@code distance} blocks: 6 + 0.4 d ticks (10 blocks = 10 ticks), rounded. */
    public static int flightTicks(double distance) {
        return (int) Math.round(6 + 0.4 * Math.max(0, distance));
    }

    /** The k-th weapon's hit, in ticks after the volley's first release (clip t14): 2k + its flight. */
    public static int hitDelay(int k, double distance) {
        return RELEASE_GAP_TICKS * k + flightTicks(distance);
    }

    /** A weapon's damage on a building. */
    public static float buildingDamage(float damage) {
        return damage * BUILDING_MULT;
    }

    // ------------------------------------------------------------------------------------------- Grave Hook

    /** Dragged, or (a heavy: max health >= 150 or a hitbox >= 1.4 blocks wide) rooted. */
    public static Hook hookOutcome(double maxHealth, double width) {
        return maxHealth >= HEAVY_MAX_HEALTH || width >= HEAVY_WIDTH ? Hook.ROOT : Hook.DRAG;
    }

    /** How far a target {@code distance} blocks away is dragged: 4, but never closer than 1.5 to the Binder. */
    public static double dragDistance(double distance) {
        return Math.max(0, Math.min(DRAG_BLOCKS, distance - DRAG_MIN_GAP));
    }

    /** The share of the pull done after {@code tick} of {@link #DRAG_TICKS} ticks: smoothstep, 0 -> 1. */
    public static double dragProgress(int tick) {
        double x = Math.max(0, Math.min(1, tick / (double) DRAG_TICKS));
        return x * x * (3 - 2 * x);
    }
}
