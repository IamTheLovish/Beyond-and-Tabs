package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.hud.NotificationClientEvents.Alert;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backlog 10 (Command Center, Nov 4): the "Hero fallen" alert. A hero's death is the owner's biggest loss short of the
 * commander, and until now the HUD said only "Unit lost" - the same line as a peasant's death. The server already
 * tells the owner's client with FallenHeroClientboundPacket (HeroServerEvents.onLivingDeath), which reaches it however
 * far away the hero fell; the alert comes from that packet, so the entity-leave path must not ALSO say "Unit lost"
 * for a hero. Only the enum is loaded here (the outer class needs a Minecraft client).
 */
class HeroFallenAlertTest {

    @Test
    void aHeroDeathIsNotAlsoAUnitLost() {
        assertNull(Alert.forDeath(false, true),
            "a hero's death is announced by the fallen-hero packet as HERO_FALLEN; the entity-leave path stays quiet");
    }

    @Test
    void otherDeathsKeepTheirLines() {
        assertEquals(Alert.COMMANDER_KILLED, Alert.forDeath(true, false));
        assertEquals(Alert.COMMANDER_KILLED, Alert.forDeath(true, true), "the commander's line wins: the blast is coming");
        assertEquals(Alert.UNIT_LOST, Alert.forDeath(false, false));
    }

    @Test
    void heroFallenIsAShownLineMoreUrgentThanAUnitLost() {
        Alert a = Alert.HERO_FALLEN;
        assertEquals("hero_fallen", a.key);
        assertTrue(a.color >= 0, "a text line, not voice only: there is no voice line for it yet");
        assertTrue(a.priority > Alert.UNIT_LOST.priority, "it must not lose its slot to a unit lost");
        assertTrue(a.priority < Alert.COMMANDER_KILLED.priority, "the commander outranks it");
        // review round 1 on #508: an alert-wide cooldown silenced a second hero falling within it, line and chord.
        // The rate limit is per hero (HeroFallenNews, HeroFallenAlertGameTests), so the alert itself has none
        assertEquals(0, a.cooldownMs, "two heroes falling in one fight are both news");
    }

    @Test
    void heroFallenIsNotTheAmberOfTheAttackAndAllyLines() {
        // 0xFFB040 against ALLY_NEEDS_HELP's 0xFFA040 read as the same line (review round 1, non-blocking)
        for (Alert other : new Alert[] { Alert.ALLY_NEEDS_HELP, Alert.UNITS_ATTACKED, Alert.BASE_ATTACKED,
                Alert.COMMANDER_ATTACKED, Alert.IDLE_CONSTRUCTOR })   // the warm lines; UNIT_LOST is grey
            assertTrue(distance(Alert.HERO_FALLEN.color, other.color) > 120,
                "HERO_FALLEN's colour is too close to " + other.name() + "'s");
    }

    private static double distance(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
