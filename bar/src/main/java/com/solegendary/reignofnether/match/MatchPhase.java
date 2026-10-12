package com.solegendary.reignofnether.match;

/**
 * The phases of one match, in the order a match goes through them (design/ron_rewrite_plan.md, rewrite 5). Saved by
 * name ({@link MatchState#save}), so values may be appended or reordered, never renamed.
 * <p>
 * BAR (Spring) splits the same road between the lobby (who plays, which map), the pre-game (start boxes, everyone
 * places a start, the game starts when all are ready) and the game itself, which ends when one ally team is left
 * ({@code GameOver}). Minecraft has no separate lobby process and autosaves at any moment, so here every phase is a
 * state of the world that must survive a save: see {@link MatchState} for what each one keeps.
 */
public enum MatchPhase {
    /** No match: a fresh world, an open or closed lobby, or after /rts-reset. */
    LOBBY,
    /** A pre-made map is being stamped into the world (maps.MapBuilder). Nobody is seated yet. */
    MAP_RISING,
    /** BAR's start-position mode: every human places a start in their team's box (maps.StartPhase). */
    START_POSITIONS,
    /** Sides are seated and fighting. */
    PLAYING,
    /**
     * The match is decided and its winners known. The battlefield stays as it is - winners keep playing on or
     * spectate - until a reset or a new match on the same world.
     */
    ENDED;

    /** A match is being set up: nobody may start a base by hand, and the lobby may not start another. */
    public boolean preparing() {
        return this == MAP_RISING || this == START_POSITIONS;
    }

    /** No match is being set up or fought: a new one may start (the field of an ENDED match is simply reused). */
    public boolean idle() {
        return this == LOBBY || this == ENDED;
    }

    /**
     * The transition table, the only one there is. LOBBY is reachable from anywhere (/rts-reset, a setup that can't
     * resume); a new match may start from LOBBY or over an ENDED one; preparing only moves forward.
     */
    public static boolean canMove(MatchPhase from, MatchPhase to) {
        if (from == null || to == null || from == to)
            return false;
        if (to == LOBBY)
            return true;
        return switch (from) {
            case LOBBY, ENDED -> to == MAP_RISING || to == START_POSITIONS || to == PLAYING;
            case MAP_RISING -> to == START_POSITIONS || to == PLAYING;
            case START_POSITIONS -> to == PLAYING;
            case PLAYING -> to == ENDED;
        };
    }

    /** The phase saved under {@code name}; LOBBY for an unknown or missing one (a corrupt file starts clean). */
    public static MatchPhase byName(String name) {
        if (name != null)
            for (MatchPhase p : values())
                if (p.name().equals(name))
                    return p;
        return LOBBY;
    }
}
