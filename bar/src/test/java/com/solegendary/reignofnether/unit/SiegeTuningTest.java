package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.units.shared.SiegeTuning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The siege lobber's promises from design/siege_t1.md, from SiegeTuning's arithmetic alone. */
class SiegeTuningTest {

    @Test
    void aShellTakes54OffADefenceAnd27OffAnythingElse() {
        assertEquals(54f, SiegeTuning.healthPerShell(true), 0.01f);
        assertEquals(27f, SiegeTuning.healthPerShell(false), 0.01f);
    }

    @Test
    void wallsAndGatesFallAtTheDefenceRate() {
        // one lobber: a wall segment (400) in 20.5 s, a fortified wall (1100) in 53 s, a gate (320) in 15.5 s
        assertEquals(20.5f, SiegeTuning.secondsToRaze(400, 1, true), 0.01f);
        assertEquals(53f, SiegeTuning.secondsToRaze(1100, 1, true), 0.01f);
        assertEquals(15.5f, SiegeTuning.secondsToRaze(320, 1, true), 0.01f);
        assertEquals(10.5f, SiegeTuning.secondsToRaze(400, 2, true), 0.01f);
    }

    @Test
    void oneLobberBreaksTheAntiRaidTowerInThirteenSeconds() {
        assertEquals(5, SiegeTuning.shellsToRaze(260, true));
        assertEquals(13f, SiegeTuning.secondsToRaze(260, 1, true), 0.01f);
    }

    @Test
    void aT1LabTakesTwiceAsManyShellsAsADefenceOfTheSameHealth() {
        assertEquals(3, SiegeTuning.shellsToRaze(150, true));
        assertEquals(6, SiegeTuning.shellsToRaze(150, false));
    }

    @Test
    void itSeesLessFarThanItShootsAndOutReachesOnlyTheFirstTower() {
        assertTrue(SiegeTuning.SIGHT_RANGE < SiegeTuning.ATTACK_RANGE);
        assertTrue(SiegeTuning.ATTACK_RANGE > 14 && SiegeTuning.ATTACK_RANGE < 16);   // anti-raid 14, enhanced 16
        assertEquals(50, SiegeTuning.cooldownTicks());
    }
}
