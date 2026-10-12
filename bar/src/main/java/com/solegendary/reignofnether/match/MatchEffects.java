package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.maps.StartPhase;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;

import javax.annotation.Nullable;

/**
 * What {@link MatchState}'s transitions do to the world. MatchState decides <i>which</i> phase comes next and keeps
 * what must survive a save; the effects stamp the map, run the start-position phase and seat the lineup. The live
 * implementation is maps.MapMatch's ({@code MapMatch.EFFECTS}); the game tests drive a MatchState with a recording
 * implementation, so every transition and every save / load / resume is tested without stamping a 500-block map or
 * touching the server's one live phase.
 * <p>
 * Every method runs on the server thread. A method that completes later (raising the map, the start phase) reports
 * back through the MatchState it was given: {@link MatchState#mapRaised} and {@link MatchState#startsPlaced}.
 */
public interface MatchEffects {

    /** Stamp the setup's pre-made map; call {@code state.mapRaised()} when it stands. {@code resumed}: after a reload. */
    void raiseMap(MatchState state, boolean resumed);

    /**
     * How far the running map build got, for the save: -1 none, 0 terrain, 1 decoration, 2 economy next, 3 economy
     * stamped (only the hand-over is left). A reload rebuilds below 3 and must not stamp the economy twice at 3.
     */
    int mapBuildStage();

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

    /** Drop a map being raised and a live start phase without seating anyone (reset, a setup that can't resume). */
    void cancelPreparation();

    /** The map in play changed (clients tint their fog and show its name from this). */
    void showMap(MapPreset preset, int cx, int cz, int radius);

    /** One plain chat line to everyone online. */
    void say(String message);
}
