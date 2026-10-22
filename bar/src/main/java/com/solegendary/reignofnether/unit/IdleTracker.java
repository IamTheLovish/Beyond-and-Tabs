package com.solegendary.reignofnether.unit;

import java.util.HashMap;
import java.util.Map;

/**
 * "Has this unit been standing still for a while" - the rule behind the idle-army marker, plain Java so a GameTest can
 * drive it with a fake clock. The client cannot read a unit's goals (they are server state), so idle is judged from
 * what it can see: the unit has not moved (more than {@link #MOVE_EPS} blocks between two passes) and is not busy
 * (swinging, being hit, using an item) for {@link #IDLE_AFTER_MS}. Playtest clip: whole blocks of units stood in rows
 * near base for minutes and, as same-size icons, looked just like the army that was fighting.
 *
 * Usage per pass: {@link #beginPass()}, {@link #observe} for every unit, {@link #endPass()} (forgets units not seen).
 */
public final class IdleTracker {

    public static final long IDLE_AFTER_MS = 10_000;
    public static final double MOVE_EPS = 0.15;

    private static final class Rec {
        double x, z;
        long stillSince;
        int pass;
        boolean idle;
    }

    private final Map<Integer, Rec> recs = new HashMap<>();
    private int pass = 0;
    private int idleCount = 0;

    public void beginPass() {
        pass++;
        idleCount = 0;
    }

    /** Records one unit's position this pass; returns whether it now counts as idle. */
    public boolean observe(int id, double x, double z, boolean busy, long nowMs) {
        Rec r = recs.get(id);
        if (r == null) {
            r = new Rec();
            r.x = x;
            r.z = z;
            r.stillSince = nowMs;
            recs.put(id, r);
        }
        double dx = x - r.x, dz = z - r.z;
        if (busy || dx * dx + dz * dz > MOVE_EPS * MOVE_EPS || nowMs < r.stillSince) {
            r.x = x;
            r.z = z;
            r.stillSince = nowMs;
        }
        r.pass = pass;
        r.idle = nowMs - r.stillSince >= IDLE_AFTER_MS;
        if (r.idle)
            idleCount++;
        return r.idle;
    }

    /** Drops every unit that was not observed in the pass just made (dead, out of range, no longer military). */
    public void endPass() {
        recs.values().removeIf(r -> r.pass != pass);
    }

    public boolean isIdle(int id) {
        Rec r = recs.get(id);
        return r != null && r.idle;
    }

    public int idleCount() {
        return idleCount;
    }

    public void clear() {
        recs.clear();
        idleCount = 0;
    }
}
