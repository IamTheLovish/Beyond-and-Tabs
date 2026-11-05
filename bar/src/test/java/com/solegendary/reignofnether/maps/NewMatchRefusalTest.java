package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.match.MatchPhase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a new match may be asked for (NewMatch.refusal). A battlefield being raised or a start phase being played must
 * never be pulled out from under its players; a match in progress needs an explicit force (the op command's
 * argument), because "new match" there throws a game away; the lobby and a finished match are the normal cases.
 */
class NewMatchRefusalTest {

    @Test
    void aBattlefieldBeingPreparedIsNeverShifted() {
        for (boolean force : new boolean[] { false, true }) {
            assertNotNull(NewMatch.refusal(MatchPhase.MAP_RISING, force), "MAP_RISING, force " + force);
            assertNotNull(NewMatch.refusal(MatchPhase.START_POSITIONS, force), "START_POSITIONS, force " + force);
        }
    }

    @Test
    void aMatchInPlayNeedsForce() {
        String why = NewMatch.refusal(MatchPhase.PLAYING, false);
        assertNotNull(why);
        assertTrue(why.toLowerCase().contains("force"), "the refusal must say how to override it: " + why);
        assertNull(NewMatch.refusal(MatchPhase.PLAYING, true));
    }

    @Test
    void theLobbyAndAFinishedMatchMayStartANewOne() {
        for (boolean force : new boolean[] { false, true }) {
            assertNull(NewMatch.refusal(MatchPhase.LOBBY, force));
            assertNull(NewMatch.refusal(MatchPhase.ENDED, force));
        }
    }
}
