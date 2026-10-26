package com.solegendary.reignofnether.unit.modelling.animations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dragon rig's bank ({@link Dragon#bankStep}) as numbers: a turn rolls the body INTO it, no further than the
 * ceiling, with no overshoot and no snap (the roll's velocity continuous), and settles level a few ticks after the
 * turn ends; a yaw the server snapped in one tick still reads as a turn. tools/dragon_poses.py's bank_step is this
 * function's Python twin - a failure here means the two disagree or the spring is unstable.
 */
class DragonBankTest {

    private static float[] fresh() {
        float[] st = Dragon.newState(0f);
        Dragon.bankStep(st, new Dragon.Params(), 0f, 0f, 1f);   // seeds the heading
        return st;
    }

    @Test
    void sustainedRightTurnBanksRightUpToTheCeilingAndSettles() {
        Dragon.Params P = new Dragon.Params();
        float[] st = fresh();
        float yaw = 0f, peak = 0f;
        float prev = 0f, maxStep = 0f;
        for (int t = 0; t < 24; t++) {
            yaw += 6f;                                   // a hard turn: 6 deg per tick, clockwise = right
            Dragon.bankStep(st, P, yaw, 1f, 1f);
            peak = Math.max(peak, st[Dragon.ST_BANK]);
            maxStep = Math.max(maxStep, Math.abs(st[Dragon.ST_BANK] - prev));
            prev = st[Dragon.ST_BANK];
        }
        assertTrue(peak > 20f, "a hard turn rolls well in: " + peak);
        assertTrue(peak <= P.bankMax + 1e-3f, "never past the ceiling: " + peak);
        assertTrue(maxStep < 6f, "no snap: largest change per tick " + maxStep);
        int settled = -1;
        for (int t = 0; t < 80; t++) {
            Dragon.bankStep(st, P, yaw, 1f, 1f);         // the turn is over: the heading holds
            assertTrue(st[Dragon.ST_BANK] > -1f, "no overshoot through level");
            if (settled < 0 && Math.abs(st[Dragon.ST_BANK]) < 1f)
                settled = t + 1;
        }
        assertTrue(settled > 0 && settled <= 30, "settles level within 30 ticks: " + settled);
        assertTrue(Math.abs(st[Dragon.ST_TAIL_B]) < 2f, "the tail has settled too");
    }

    @Test
    void snappedYawStillReadsAsATurn() {
        Dragon.Params P = new Dragon.Params();
        float[] st = fresh();
        float peak = 0f;
        for (int t = 0; t < 20; t++) {
            Dragon.bankStep(st, P, -90f, 1f, 1f);       // the server set the yaw 90 deg left in one tick
            peak = Math.min(peak, st[Dragon.ST_BANK]);
        }
        assertTrue(peak < -15f, "a snapped left turn banks left: " + peak);
    }

    @Test
    void groundAndHoverScaleTheBankDown() {
        Dragon.Params P = new Dragon.Params();
        float[] air = fresh(), ground = fresh();
        float yaw = 0f;
        for (int t = 0; t < 24; t++) {
            yaw += 6f;
            Dragon.bankStep(air, P, yaw, 1f, 1f);
            Dragon.bankStep(ground, P, yaw, 1f, P.bankGround);
        }
        assertEquals(air[Dragon.ST_BANK] * P.bankGround, ground[Dragon.ST_BANK], 0.05f, "the ground bank is the scaled one");
    }

    @Test
    void tailLagsTheBody() {
        Dragon.Params P = new Dragon.Params();
        float[] st = fresh();
        float yaw = 0f;
        for (int t = 0; t < 6; t++) {
            yaw += 6f;
            Dragon.bankStep(st, P, yaw, 1f, 1f);
            assertTrue(Math.abs(st[Dragon.ST_TAIL_A]) < Math.abs(st[Dragon.ST_BANK]), "the tail's first stage trails the body");
            assertTrue(Math.abs(st[Dragon.ST_TAIL_B]) < Math.abs(st[Dragon.ST_TAIL_A]), "the tip trails that");
        }
    }
}
