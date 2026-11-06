package com.solegendary.reignofnether.unit.modelling.animations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TitheBinderDrive, the client's mapping from the unit's state to the rig's inputs, with no game: which clip the
 * signals start (move, the volley cue, a bind, a hit, death), the Grave Hook overlay on slot 1, the once-per-tick
 * catch-up and the partialTick blend. The rig's own numbers are TitheBinderRigTest's.
 */
class TitheBinderDriveTest {

    private int now = 0;

    private TitheBinderDrive drive(int weapons) {
        TitheBinderDrive d = new TitheBinderDrive(1L);
        d.signals.weapons = weapons;
        d.catchUp(now);
        return d;
    }

    private void steps(TitheBinderDrive d, int n) {
        for (int i = 0; i < n; i++)
            d.catchUp(++now);
    }

    @Test
    void starts_idle_with_the_signalled_count() {
        TitheBinderDrive d = drive(4);
        assertTrue(d.ready());
        assertEquals(4, d.weapons());
        assertEquals(TitheBinderRig.Clip.IDLE, d.clip());
        for (int k = 1; k <= 4; k++)
            assertTrue(d.current().slotOn[k], "slot " + k + " docked");
        assertFalse(d.current().slotOn[5], "slot 5 empty");
    }

    @Test
    void forward_speed_moves_and_stillness_returns_to_idle() {
        TitheBinderDrive d = drive(1);
        d.signals.speed = TitheBinderDrive.MOVE_FULL;
        steps(d, 3);
        assertEquals(TitheBinderRig.Clip.MOVE, d.clip());
        d.signals.speed = 0.0;
        steps(d, 30);
        assertEquals(TitheBinderRig.Clip.IDLE, d.clip());
    }

    @Test
    void a_volley_cue_plays_the_volley_from_t0_and_every_weapon_comes_home() {
        TitheBinderDrive d = drive(3);
        d.signals.volleySeq++;
        d.signals.volleyHasTarget = true;
        d.signals.volleyTarget[0] = 0.0;
        d.signals.volleyTarget[1] = 20.0;
        d.signals.volleyTarget[2] = -96.0;
        steps(d, 1);
        assertEquals(TitheBinderRig.Clip.VOLLEY, d.clip());
        assertEquals(1, d.clipTick(), "the cue's tick is the clip's t0");
        steps(d, TitheBinderRig.VOLLEY_RELEASE + 1);
        boolean out = false;
        for (int k = 1; k <= 3; k++)
            out |= d.current().flyState[k] != TitheBinderRig.FLY_NONE;
        assertTrue(out, "a weapon is thrown after the strike (t13)");
        steps(d, TitheBinderDrive.volleyTicks(3));
        assertEquals(TitheBinderRig.Clip.IDLE, d.clip());
        for (int k = 1; k <= 3; k++)
            assertTrue(d.current().slotOn[k], "weapon " + k + " docked again");
    }

    @Test
    void a_hit_flinches_when_free_but_not_through_a_volley() {
        TitheBinderDrive d = drive(2);
        d.signals.volleySeq++;
        steps(d, 2);
        d.signals.hurtTime = 10;
        steps(d, 1);
        assertEquals(TitheBinderRig.Clip.VOLLEY, d.clip(), "the volley is not cut by a hit");
        d.signals.hurtTime = 0;
        steps(d, TitheBinderDrive.volleyTicks(2));
        assertEquals(TitheBinderRig.Clip.IDLE, d.clip());
        d.signals.hurtTime = 10;
        steps(d, 1);
        assertEquals(TitheBinderRig.Clip.HIT, d.clip());
    }

    @Test
    void one_more_weapon_binds_and_any_other_change_rebuilds_the_wheel() {
        TitheBinderDrive d = drive(2);
        d.signals.weapons = 3;
        steps(d, 1);
        assertEquals(TitheBinderRig.Clip.BIND, d.clip());
        assertEquals(3, d.weapons());
        steps(d, TitheBinderDrive.BIND_TICKS + 1);
        assertEquals(TitheBinderRig.Clip.IDLE, d.clip());
        d.signals.weapons = 1;                          // the restless decay (or a jump): no bind, a new wheel
        steps(d, 1);
        assertEquals(1, d.weapons());
        assertNotEquals(TitheBinderRig.Clip.BIND, d.clip());
    }

    @Test
    void death_wins_and_holds() {
        TitheBinderDrive d = drive(5);
        d.signals.dead = true;
        steps(d, 1);
        assertEquals(TitheBinderRig.Clip.DEATH, d.clip());
        d.signals.volleySeq++;
        d.signals.hookSeq++;
        steps(d, 5);
        assertEquals(TitheBinderRig.Clip.DEATH, d.clip());
        assertEquals(-1, d.hookAge());
    }

    @Test
    void grave_hook_throws_slot_one_and_brings_it_home() {
        TitheBinderDrive d = drive(3);
        d.signals.hookSeq++;
        d.signals.hookHasTarget = true;
        d.signals.hookTarget[0] = 0.0;
        d.signals.hookTarget[1] = 14.0;
        d.signals.hookTarget[2] = -96.0;
        steps(d, TitheBinderDrive.HOOK_RELEASE + 2);
        TitheBinderRig.Pose p = d.current();
        assertEquals(TitheBinderRig.FLY_FLYING, p.flyState[1], "the hook is in the air after t13");
        assertFalse(p.slotOn[1], "slot 1 is empty while the hook is out");
        assertTrue(p.ribbons > 0);
        for (int c = 0; c < 3; c++)
            assertEquals(p.flyPos[1][c], p.ribbonEnd[0][c], 1e-9, "the chain ends at the hook");
        steps(d, TitheBinderDrive.HOOK_WRAP - TitheBinderDrive.HOOK_RELEASE - 1);
        p = d.current();
        assertEquals(TitheBinderRig.FLY_EMBED, p.flyState[1], "wrapped at t21");
        assertTrue(p.impacts > 0, "the wrap flashes");
        assertEquals(-96.0, p.flyPos[1][2], 1e-9, "the hook is on its target");
        steps(d, TitheBinderDrive.HOOK_DOCK - TitheBinderDrive.HOOK_WRAP + 2);
        assertEquals(-1, d.hookAge());
        assertTrue(d.current().slotOn[1], "the hook is docked again");
    }

    @Test
    void catch_up_steps_once_per_tick_and_starts_over_when_far_behind() {
        TitheBinderDrive d = drive(1);
        int t0 = d.clipTick();
        assertFalse(d.behind(now));
        d.catchUp(now);
        assertEquals(t0, d.clipTick(), "the same tick steps nothing (a second draw)");
        now += 3;
        assertTrue(d.behind(now));
        d.catchUp(now);
        assertEquals(t0 + 3, d.clipTick(), "three ticks behind: three steps");
        now += TitheBinderDrive.MAX_CATCH_UP + 1;
        d.catchUp(now);
        assertEquals(t0, d.clipTick(), "far behind: a fresh start");
    }

    @Test
    void blend_lands_on_the_newest_tick_and_turns_the_short_way() {
        TitheBinderDrive d = drive(3);
        steps(d, 7);
        TitheBinderRig.Pose cur = d.current();
        TitheBinderRig.Pose b = d.blend(1f);
        assertEquals(cur.rot[TitheBinderRig.HOVER][1], b.rot[TitheBinderRig.HOVER][1], 1e-9);
        assertEquals(cur.off[TitheBinderRig.HOVER][1], b.off[TitheBinderRig.HOVER][1], 1e-9);
        double da = TitheBinderRig.pmod(b.slotAngle[1] - cur.slotAngle[1] + 180.0, 360.0) - 180.0;
        assertEquals(0.0, da, 1e-9);
        assertEquals(0.0, TitheBinderRig.pmod(TitheBinderDrive.lerpAngle(350.0, 10.0, 0.5), 360.0), 1e-9);
        assertEquals(355.0, TitheBinderDrive.lerpAngle(350.0, 10.0, 0.25), 1e-9);
    }
}
