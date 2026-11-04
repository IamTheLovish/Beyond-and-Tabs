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
        assertTrue(a.cooldownMs > 0 && a.cooldownMs <= 5_000,
            "two heroes falling in one fight are both news, but not one line per tick");
    }
}
