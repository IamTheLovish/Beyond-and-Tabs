package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.StartPhase;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;

import javax.annotation.Nullable;

/**
 * What {@link MatchState}'s transitions do to the world. MatchState decides <i>which</i> phase comes next and keeps
 * what must survive a save; the effects prepare the battlefield, run the start-position phase and seat the lineup.
 * The live implementation is maps.MapMatch's ({@code MapMatch.EFFECTS}); the game tests drive a MatchState with a
 * recording implementation, so every transition and every save / load / resume is tested without stamping a
 * 500-block map or touching the server's one live phase.
 * <p>
 * Every method runs on the server thread. A method that completes later (the preparation, the start phase) reports
 * back through the MatchState it was given: {@link MatchState#mapRaised} and {@link MatchState#startsPlaced}.
 */
public interface MatchEffects {

    /**
     * Prepare the setup's battlefield ({@link MatchPrep}: forge the pre-made map, clear the starts, raise the wall,
     * generate the arena, drain the lights) and call {@code state.mapRaised()} when it is READY. {@code fromStage}
     * is -1 for a fresh start, else the stage a reload found saved ({@link MatchPrep#savedStage}), which the job
     * re-runs from, with {@code fromDetail} that stage's saved detail ({@link MatchPrep#savedDetail}: the wall's
     * columns done).
     */
    void prepare(MatchState state, int fromStage, int fromDetail);

    /**
     * How far the running preparation got, for the save: -1 none, 0-3 the map builder's own stage (a reload re-runs
     * it from there; 3 = the economy next), then {@link MatchPrep#SAVED_CLEAR} .. {@link MatchPrep#SAVED_READY}. A
     * reload re-runs below READY and must not prepare twice at READY.
     */
    int prepStage();

    /** The running preparation's detail for the save (the wall's columns done); 0 for any other stage or none. */
    default int prepDetail() {
        return 0;
    }

    /**
     * A start-position phase for the setup's sides, every one at its slot (bots placed), ending into
     * {@code state.startsPlaced(phase)}. Not started: {@link #beginStartPhase} makes it the live one.
     */
    StartPhase newStartPhase(MatchState state);

    /** Make {@code phase} the live one players click into. {@code resumed}: restored from a save (no bot re-pick). */
    void beginStartPhase(MatchState state, StartPhase phase, boolean resumed);

    /** Seat every side where the finished phase put it. Null when seating failed (logged and told by the effect). */
    @Nullable
    MultiplayerMatch seat(MatchState state, StartPhase finished);

    /**
     * Drop a running preparation and a live start phase without seating anyone (reset, a setup that can't resume,
     * a preparation stage that threw).
     */
    void cancelPreparation();

    /** The map in play changed (clients tint their fog and show its name from this). */
    void showMap(MapPreset preset, int cx, int cz, int radius);

    /** One plain chat line to everyone online. */
    void say(String message);
}
