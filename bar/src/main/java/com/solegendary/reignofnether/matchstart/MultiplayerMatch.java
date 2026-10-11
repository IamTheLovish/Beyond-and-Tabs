package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.player.PlayerServerEvents;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One match started from the multiplayer lobby (MultiplayerLobby.startMatch): who is in it (humans and bots) and who
 * of them are humans. It scopes the end of the match: when a participant is defeated, victory is decided among this
 * match's remaining participants rather than among every RTS player on the server (PlayerServerEvents.defeat). On a
 * real server those are the same people; in the game tests, where several matches run side by side in one world,
 * they are not, and a 2-player test would otherwise never see a winner while another test's bots are alive.
 *
 * Several can exist at once (tests); the lobby's live one also decides who may still start mid-match
 * (MultiplayerLobby.refusesLateStart): anyone else who joins is a spectator.
 */
public class MultiplayerMatch {

    private static final Map<String, MultiplayerMatch> BY_PARTICIPANT = new ConcurrentHashMap<>();

    public final Set<String> participants;
    public final Set<String> humans;
    private volatile boolean over = false;

    public MultiplayerMatch(Set<String> participants, Set<String> humans) {
        this.participants = Collections.unmodifiableSet(new LinkedHashSet<>(participants));
        this.humans = Collections.unmodifiableSet(new LinkedHashSet<>(humans));
    }

    /** Makes this match the one its participants belong to (a participant of an older match moves over). */
    public MultiplayerMatch register() {
        for (String p : participants)
            BY_PARTICIPANT.put(p, this);
        return this;
    }

    /** The match is decided (or abandoned): its participants belong to no match any more. */
    public void end() {
        over = true;
        for (String p : participants)
            BY_PARTICIPANT.remove(p, this);
    }

    public boolean isOver() {
        return over;
    }

    /** The registered match {@code name} plays in, or null. */
    public static MultiplayerMatch of(String name) {
        return name == null ? null : BY_PARTICIPANT.get(name);
    }

    /** This match's participants that are still RTS players (not yet defeated), in seat order. */
    public Set<String> remaining() {
        Set<String> out = new LinkedHashSet<>();
        for (String p : participants)
            if (PlayerServerEvents.isRTSPlayer(p))
                out.add(p);
        return out;
    }

    /** Forget every match (server stop, /rts-reset). */
    public static void clearAll() {
        for (MultiplayerMatch m : BY_PARTICIPANT.values())
            m.over = true;
        BY_PARTICIPANT.clear();
    }
}
