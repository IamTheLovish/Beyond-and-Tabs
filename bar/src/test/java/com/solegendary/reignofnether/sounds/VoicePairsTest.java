package com.solegendary.reignofnether.sounds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VoicePairs: a sound event heard twice in the RTS view (a local copy and the server's) plays once. */
class VoicePairsTest {

    private static final String HURT = "minecraft:entity.zombie.hurt", DEATH = "minecraft:entity.zombie.death";

    @Test
    void aNearFightsVoicePlaysOnceWhicheverCopyComesFirst() {
        VoicePairs p = new VoicePairs();
        // hurt: the client's damage event first, the server's sound packet in the same tick
        assertFalse(p.secondCopy(HURT, 10, 64, 10, true, 100));
        assertTrue(p.secondCopy(HURT, 10.4, 64, 9.8, false, 100));
        // death: the server's sound first, the client's entity event 3 a tick later
        assertFalse(p.secondCopy(DEATH, 30, 64, 30, false, 200));
        assertTrue(p.secondCopy(DEATH, 30, 64, 30.5, true, 201));
    }

    @Test
    void aFarFightsLocalVoiceAlwaysPlays() {
        // beyond 16 blocks of the body the server sends nothing: every local copy is the only copy
        VoicePairs p = new VoicePairs();
        for (int i = 0; i < 5; i++)
            assertFalse(p.secondCopy(HURT, 80, 64, 80, true, 100 + i * 12L));
    }

    @Test
    void twoCopiesFromTheSameSideAreTwoEvents() {
        // two zombies hurt side by side: two local copies (or two server copies) are two voices, both heard
        VoicePairs p = new VoicePairs();
        assertFalse(p.secondCopy(HURT, 10, 64, 10, true, 100));
        assertFalse(p.secondCopy(HURT, 11, 64, 10, true, 100));
        assertFalse(p.secondCopy(HURT, 20, 64, 20, false, 100));
        assertFalse(p.secondCopy(HURT, 20, 64, 21, false, 100));
    }

    @Test
    void aTwinMustBeTheSameSoundAtTheSameSpotAtTheSameTime() {
        VoicePairs p = new VoicePairs();
        assertFalse(p.secondCopy(HURT, 10, 64, 10, true, 100));
        assertFalse(p.secondCopy(DEATH, 10, 64, 10, false, 100));                       // another sound
        assertFalse(p.secondCopy(HURT, 10 + VoicePairs.NEAR + 1, 64, 10, false, 100));    // another unit
        assertFalse(p.secondCopy(HURT, 40, 64, 40, true, 300));
        assertFalse(p.secondCopy(HURT, 40, 64, 40, false, 300 + VoicePairs.WINDOW + 1));  // too late: a new hit
    }

    @Test
    void oneTwinPerEvent() {
        // the pair is used up: the next blow on the same unit (10+ ticks later, past its invulnerability) is heard again
        VoicePairs p = new VoicePairs();
        assertFalse(p.secondCopy(HURT, 10, 64, 10, true, 100));
        assertTrue(p.secondCopy(HURT, 10, 64, 10, false, 100));
        assertFalse(p.secondCopy(HURT, 10, 64, 10, false, 101));
        assertTrue(p.secondCopy(HURT, 10, 64, 10, true, 102));
    }

    @Test
    void theMemoryStaysSmall() {
        VoicePairs p = new VoicePairs();
        for (int i = 0; i < 10_000; i++)
            p.secondCopy(HURT, i * 10, 64, 0, true, 100);
        // the oldest are gone, the newest still pair
        assertTrue(p.secondCopy(HURT, 9_999 * 10, 64, 0, false, 100));
    }
}
