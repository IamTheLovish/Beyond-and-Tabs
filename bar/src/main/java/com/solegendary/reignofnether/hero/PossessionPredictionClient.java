package com.solegendary.reignofnether.hero;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side movement prediction for the possessed unit (design/hero_mode.md, hero slice 3). lovish plays a friend
 * over e4mc, where a server round trip is 100-200 ms: with the server alone moving the unit, every WASD press waited
 * that long. Now:
 *
 * <ol>
 *   <li><b>Predict.</b> While possessing, the client's copy of the unit counts as "controlled by this client"
 *       (PossessionPredictionMixin on Entity.isControlledByLocalInstance - the same switch vanilla flips for a horse
 *       the local player rides). Vanilla then ignores the server's position packets for it and runs its real
 *       aiStep/travel on the client: the unit's own speed, collision, steps, jumps, water, and the unit class's own
 *       travel override (a flyer's flight). We only feed it the keys each tick ({@link #preTick}), exactly as
 *       PossessionServer.drive feeds the server's copy - so both sides compute the same motion.</li>
 *   <li><b>Claim.</b> After the client tick ({@link #postTick}) the position it reached goes to the server with the
 *       keys and a sequence number (PossessionServerboundPacket MOVE), every tick while possessing.</li>
 *   <li><b>Server authoritative.</b> PossessionServer.applyClaim moves the real unit to the claim through vanilla
 *       collision, never further than it could have walked; anything else is corrected (SYNC).</li>
 *   <li><b>Reconcile smoothly.</b> A correction names the claim it answers: the error is the server's position minus
 *       where we were at that claim (a 64-entry history), not minus where we are now, so the movement made since is
 *       kept. Small errors are blended in over a few ticks (35% a tick) - no rubber band; only a teleport-sized
 *       error (> 3 blocks, or the server says SNAP) snaps.</li>
 * </ol>
 *
 * An honest client under ~150 ms sees no correction at all: both sides run the same physics from the same keys.
 * While the server moves the unit itself (an ability's AI window, a leap arc) it says SYNC_SUSPEND and the unit is
 * interpolated from the server as before, until the server hands it back.
 *
 * Per tick: a handful of field writes and one small packet; no allocation besides the packet itself.
 */
public final class PossessionPredictionClient {
    private PossessionPredictionClient() { }

    private static final Minecraft MC = Minecraft.getInstance();

    /** The entity id the client is predicting (-1: none). Read by PossessionPredictionMixin, per entity per call. */
    public static volatile int controlledId = -1;

    static final int HISTORY = 64;
    static final double SNAP_DIST = 3.0;
    /** Fraction of the remaining correction applied per tick. */
    static final double BLEND = 0.35;

    private static int seq = 0;
    private static boolean suspended = false;
    private static final int[] histSeq = new int[HISTORY];
    private static final double[] histX = new double[HISTORY], histY = new double[HISTORY], histZ = new double[HISTORY];
    private static double pendX, pendY, pendZ;
    // the velocity we left the unit with: a motion packet that only echoes the server's (claim-mode, near-zero)
    // velocity is undone; a real shove (knockback, a blast) is kept and predicted
    private static double savedVx, savedVy, savedVz;
    private static boolean haveSaved = false;

    public static boolean isActive() {
        return controlledId >= 0 && !suspended;
    }

    static void start(LivingEntity mob) {
        controlledId = mob.getId();
        suspended = false;
        resetState();
    }

    static void stop() {
        controlledId = -1;
        suspended = false;
        resetState();
    }

    private static void resetState() {
        pendX = pendY = pendZ = 0;
        haveSaved = false;
        java.util.Arrays.fill(histSeq, Integer.MIN_VALUE);
    }

    /** Before the world ticks: the keys go into the unit the way PossessionServer.drive's input path writes them. */
    static void preTick(LivingEntity le, int forward, int strafe, boolean jump, boolean descend, boolean sprint,
                        float yaw, float pitch) {
        if (!isActive() || !(le instanceof Mob mob) || mob.getId() != controlledId)
            return;
        if (mob.isPassenger()) {   // a mounted unit moves with its vehicle: not ours to predict
            suspend(mob);
            return;
        }
        if (haveSaved) {
            Vec3 dm = mob.getDeltaMovement();
            double ddx = dm.x - savedVx, ddy = dm.y - savedVy, ddz = dm.z - savedVz;
            boolean changed = ddx * ddx + ddy * ddy + ddz * ddz > 1.0E-6;
            boolean shove = dm.x * dm.x + dm.z * dm.z > 0.04 || dm.y > 0.3;
            if (changed && !shove)
                mob.setDeltaMovement(savedVx, savedVy, savedVz);
        }
        // the very code the server's drive runs (sprint, air control and all), so both sides compute the same motion
        PossessionServer.applyKeys(mob, forward, strafe, jump, descend, sprint, yaw, pitch);
    }

    /** After the world ticked the unit: blend in what is left of a correction, remember the spot, claim it. */
    static void postTick(LivingEntity mob, int forward, int strafe, boolean jump, boolean descend, boolean sprint,
                         float yaw, float pitch) {
        if (!isActive() || mob.getId() != controlledId)
            return;
        if (pendX != 0 || pendY != 0 || pendZ != 0) {
            double sx = pendX * BLEND, sy = pendY * BLEND, sz = pendZ * BLEND;
            if (pendX * pendX + pendY * pendY + pendZ * pendZ < 0.0025) {
                sx = pendX;
                sy = pendY;
                sz = pendZ;
            }
            // xo keeps last tick's spot, so the renderer spreads this over the frame: a slide, not a jump
            mob.setPos(mob.getX() + sx, mob.getY() + sy, mob.getZ() + sz);
            pendX -= sx;
            pendY -= sy;
            pendZ -= sz;
        }
        seq++;
        int i = seq & (HISTORY - 1);
        histSeq[i] = seq;
        histX[i] = mob.getX();
        histY[i] = mob.getY();
        histZ[i] = mob.getZ();
        Vec3 dm = mob.getDeltaMovement();
        savedVx = dm.x;
        savedVy = dm.y;
        savedVz = dm.z;
        haveSaved = true;
        PossessionServerboundPacket.move(mob.getId(), forward, strafe, yaw, pitch, jump, descend, sprint,
            mob.getX(), mob.getY(), mob.getZ(), seq);
    }

    private static void suspend(LivingEntity mob) {
        suspended = true;
        controlledId = -1;   // vanilla interpolates it from the server's packets again
        resetState();
    }

    /** SYNC from the server: suspend, resume, or a correction of claim {@code p.objective}. */
    static void onSync(PossessionClientboundPacket p, int possessedId) {
        if (MC.level == null || p.unitId != possessedId || possessedId < 0)
            return;
        if (!(MC.level.getEntity(p.unitId) instanceof LivingEntity mob))
            return;
        if ((p.targetId & PossessionClientboundPacket.SYNC_SUSPEND) != 0) {
            suspend(mob);
            mob.lerpTo(p.x, p.y, p.z, mob.getYRot(), mob.getXRot(), 3, false);
            return;
        }
        if (suspended || controlledId != p.unitId) {
            // the server hands the unit back: predict again from where it really is
            suspended = false;
            controlledId = p.unitId;
            resetState();
            mob.setPos(p.x, p.y, p.z);
            mob.setDeltaMovement(Vec3.ZERO);
            return;
        }
        int i = p.objective & (HISTORY - 1);
        boolean known = histSeq[i] == p.objective;
        double ex = p.x - (known ? histX[i] : mob.getX());
        double ey = p.y - (known ? histY[i] : mob.getY());
        double ez = p.z - (known ? histZ[i] : mob.getZ());
        double e2 = ex * ex + ey * ey + ez * ez;
        if ((p.targetId & PossessionClientboundPacket.SYNC_SNAP) != 0 || e2 > SNAP_DIST * SNAP_DIST || !known) {
            resetState();
            mob.setPos(p.x, p.y, p.z);
            mob.setDeltaMovement(Vec3.ZERO);
            return;
        }
        // blend the error in; the claims already sent after the corrected one carry it too, so shift their history
        // (a later correction of one of them then measures only what is still wrong)
        pendX += ex;
        pendY += ey;
        pendZ += ez;
        for (int k = 0; k < HISTORY; k++) {
            if (histSeq[k] != Integer.MIN_VALUE && histSeq[k] > p.objective) {
                histX[k] += ex;
                histY[k] += ey;
                histZ[k] += ez;
            }
        }
    }
}
