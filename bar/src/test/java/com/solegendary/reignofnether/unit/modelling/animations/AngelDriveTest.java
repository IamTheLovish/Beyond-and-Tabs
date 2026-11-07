package com.solegendary.reignofnether.unit.modelling.animations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The angels' rig in play (AngelDrive): the arrival plays when a young angel is first drawn, and each kind's attack
 * clip starts BEFORE its contact - the archer on the server's wind-up cue and, while it still has a target, on its
 * shot cooldown; the mender on its heal interval while its patient is still hurt; the sword on its swing, its blow
 * held to the cut's contact tick (BlowTiming) - and the contact lands on the clip's event tick.
 */
class AngelDriveTest {

    private static final AngelDrive.Timing ARCHER = new AngelDrive.Timing(3, 0, 36);
    private static final AngelDrive.Timing MENDER = new AngelDrive.Timing(0, 0, 40);
    private static final AngelDrive.Timing SWORD = new AngelDrive.Timing(0, 10, 0);

    private static AngelDrive drive(AngelRig.Kind kind, AngelDrive.Timing timing, int startTick) {
        AngelDrive d = new AngelDrive(kind, 7, timing);
        d.catchUp(startTick);
        return d;
    }

    @Test
    void the_arrival_plays_when_a_young_angel_is_first_drawn() {
        AngelDrive young = drive(AngelRig.Kind.SWORD, SWORD, 0);
        assertEquals(1, young.state().arriveT(), "drawn on its first tick: the arrival's first tick has played");
        AngelDrive late = drive(AngelRig.Kind.MENDER, MENDER, 12);
        assertEquals(13, late.state().arriveT(), "first drawn at tick 12: the arrival caught up to the angel's age");
        late.catchUp(13);
        assertEquals(14, late.state().arriveT(), "then one arrival tick per game tick");
        AngelDrive old = drive(AngelRig.Kind.ARCHER, ARCHER, 200);
        assertEquals(-1, old.state().arriveT(), "an angel first seen long after its summoning does not arrive again");
    }

    @Test
    void the_archer_draws_on_the_windup_cue_and_predicts_its_next_release() {
        int event = AngelRig.EVENT[AngelRig.Kind.ARCHER.ordinal()];
        AngelDrive d = drive(AngelRig.Kind.ARCHER, ARCHER, 100);
        d.signals.engaged = true;
        d.catchUp(110);
        assertEquals(-1, d.state().attackT(), "no clip before any cue");
        d.signals.windupSeq = 1;                            // the server's wind-up: the release 3 ticks on
        d.catchUp(111);
        assertTrue(d.state().attackT() > 0, "the wind-up cue starts the draw before the release");
        d.catchUp(113);
        d.signals.contactSeq = 1;                           // the release
        d.catchUp(114);
        assertEquals(event + 1, d.state().attackT(), "the release lands on the clip's contact tick");
        // the next release: 36 ticks on (cooldown 33 + the wind-up's 3); the draw starts EVENT ticks before it
        int next = 114 + 36;
        d.catchUp(next - event - 1);
        assertEquals(-1, d.state().attackT(), "the first clip is over and the next draw has not begun");
        d.catchUp(next - event);
        assertEquals(event, d.attackIn(), "the next release predicted from the cooldown");
        assertEquals(1, d.state().attackT(), "the next draw starts " + event + " ticks before the predicted release");
    }

    @Test
    void the_mender_raises_the_lantern_before_its_next_heal_only_while_its_patient_is_hurt() {
        int event = AngelRig.EVENT[AngelRig.Kind.MENDER.ordinal()];
        AngelDrive d = drive(AngelRig.Kind.MENDER, MENDER, 100);
        d.signals.engaged = true;
        d.signals.contactSeq = 1;                           // a heal; the patient is still hurt
        d.catchUp(101);
        assertEquals(event + 1, d.state().attackT(), "an unpredicted heal jumps to the clip's contact tick");
        d.catchUp(101 + 40 - event);
        assertEquals(1, d.state().attackT(), "the next heal (40 ticks on) is wound up " + event + " ticks before it");
        d.catchUp(141);
        d.signals.engaged = false;                          // healed in full: nothing more to mend
        d.signals.contactSeq = 2;
        d.catchUp(142);
        d.catchUp(142 + 40 - event);
        assertEquals(-1, d.state().attackT(), "no heal is predicted once the patient is whole");
        assertEquals(-1, d.attackIn(), "nothing due");
    }

    @Test
    void the_sword_cut_starts_on_the_swing_and_lands_on_the_held_blow() {
        int event = AngelRig.EVENT[AngelRig.Kind.SWORD.ordinal()];
        AngelDrive d = drive(AngelRig.Kind.SWORD, SWORD, 100);
        d.signals.swinging = true;                          // the swing's first tick (vanilla's animate packet)
        d.catchUp(101);
        assertEquals(1, d.state().attackT(), "the cut's wind-up starts on the swing");
        d.catchUp(106);
        d.signals.swinging = false;
        d.catchUp(111);
        assertEquals(event + 1, d.state().attackT(), "the blow (10 ticks after the swing) on the cut's contact tick");
        assertEquals(-1, d.attackIn(), "nothing more due");
    }

    @Test
    void the_blend_turns_the_short_way() {
        assertEquals(180.0, Math.abs(AngelDrive.lerpAngle(170.0, -170.0, 0.5)), 1e-9);
        assertEquals(-175.0, AngelDrive.lerpAngle(-170.0, 180.0, 0.5), 1e-9);
    }
}
