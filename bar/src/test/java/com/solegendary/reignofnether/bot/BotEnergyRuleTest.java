package com.solegendary.reignofnether.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BAR's opening for the bots (lovish, Oct 29): two extractors on the capitol's 20 energy/s and the bank, then energy
 * before the next one. A bot never reaches its 4th extractor with only the capitol's energy.
 */
class BotEnergyRuleTest {
    static final float CAPITOL = 20f, WIND = 14f;

    @Test
    void theFirstTwoExtractorsNeedNoEnergyBuilding() {
        assertFalse(BotEnergyRule.energyBeforeNextExtractor(0, 0, CAPITOL));
        assertFalse(BotEnergyRule.energyBeforeNextExtractor(1, 0, CAPITOL));
    }

    @Test
    void energyGoesUpBeforeTheFourthExtractor() {
        boolean before3rd = BotEnergyRule.energyBeforeNextExtractor(2, 0, CAPITOL);
        boolean before4th = BotEnergyRule.energyBeforeNextExtractor(3, 0, CAPITOL);
        assertTrue(before3rd || before4th, "a bot on the capitol's energy alone went on to a 4th extractor");
    }

    @Test
    void oneWindCarriesAboutTwoMoreExtractors() {
        // BAR's "a wind for every ~3 mexes" at its 3 energy/s upkeep: here 6 energy/s a mex counts its construction too
        assertFalse(BotEnergyRule.energyBeforeNextExtractor(3, 0, CAPITOL + WIND));
        assertTrue(BotEnergyRule.energyBeforeNextExtractor(6, 0, CAPITOL + WIND));
    }

    @Test
    void theCapitolStopsTrainingWorkersWhileEnergyIsStalled() {
        // PR #388 CI: a hard bot's capitol trained 250-energy workers two at a time (11-23 E/s) on its 20 E/s and every
        // site stalled together - no wind, no lab in 4 minutes. A BAR player stops the factory while energy stalls
        assertTrue(BotEnergyRule.pauseWorkerTraining(0f));
        assertTrue(BotEnergyRule.pauseWorkerTraining(0.05f));
        assertFalse(BotEnergyRule.pauseWorkerTraining(0.3f));
        assertFalse(BotEnergyRule.pauseWorkerTraining(1f));
    }

    @Test
    void aTrainingLabWantsItsOwnEnergy() {
        assertFalse(BotEnergyRule.energyBeforeNextExtractor(4, 0, CAPITOL + 2 * WIND));
        assertTrue(BotEnergyRule.energyBeforeNextExtractor(4, 1, CAPITOL + 2 * WIND));
    }
}
