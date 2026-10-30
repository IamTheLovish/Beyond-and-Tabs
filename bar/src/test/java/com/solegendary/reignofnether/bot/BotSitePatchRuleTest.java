package com.solegendary.reignofnether.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which metal patch a cancelled extractor site belongs to (BotSiteWatch.patchUnderSite). The site's centre sits at the
 * middle of its height, and a site on a rise or pillar has its centre well above the patch it covers: the Oct 23 CI
 * run 40027854308 hard bot cancelled the extractor at 2749,70,-456 over and over because the patch was not recognised
 * as the site's, so nothing kept the bot off it.
 */
class BotSitePatchRuleTest {

    @Test
    void thePatchRightUnderTheSiteIsItsPatch() {
        assertTrue(BotSiteWatch.patchUnderSite(100, 64, 100, 100, 64, 100));
        assertTrue(BotSiteWatch.patchUnderSite(100, 64, 100, 102, 65, 99));
    }

    @Test
    void aSiteRaisedOnARiseStillCoversThePatchBelowIt() {
        // 6 blocks of height between the patch and the site's centre: same column, same patch
        assertTrue(BotSiteWatch.patchUnderSite(100, 64, 100, 101, 70, 100));
        assertTrue(BotSiteWatch.patchUnderSite(100, 64, 100, 102, 72, 102));
    }

    @Test
    void aPatchAnotherFootprintAwayIsNotTheSites() {
        assertFalse(BotSiteWatch.patchUnderSite(100, 64, 100, 110, 64, 100));
        assertFalse(BotSiteWatch.patchUnderSite(100, 64, 100, 100, 64, 106));
    }

    @Test
    void theSitesPatchIsAvoidedForLongerThanTheTwoMinutesAPlacementFailureWaits() {
        assertTrue(BotSiteWatch.avoidUntil(1000) - 1000 > 20 * 120);
        assertEquals(1000 + BotSiteWatch.PATCH_AVOID_TICKS, BotSiteWatch.avoidUntil(1000));
    }
}
