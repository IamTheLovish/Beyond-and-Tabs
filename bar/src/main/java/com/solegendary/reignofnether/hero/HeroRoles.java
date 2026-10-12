package com.solegendary.reignofnether.hero;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who plays the Hero role, and for which team (design/hero_mode.md, section 1). A Hero-role player is a human seat
 * in the multiplayer lobby that has no base of its own: at match start MultiplayerLobby.startMatch attaches them to
 * their team's commanders, and from then on PossessionServer.mayPossess lets them take the team's hero units (and
 * any unit of a commander who lends theirs with /rts-possession allow).
 *
 * Server state only, keyed by player name like every other RTS registry (alliances, RTS players). Cleared on
 * /rts-reset and server stop (MultiplayerLobby.onReset / onServerStopping).
 */
public final class HeroRoles {
    private HeroRoles() { }

    /** Hero-role player -> the commanders (RTS player names) of their team. */
    private static final Map<String, List<String>> TEAM_OF = new ConcurrentHashMap<>();
    /** Commanders who let Hero-role teammates possess any of their units, not only the hero. */
    private static final Set<String> LENDERS = ConcurrentHashMap.newKeySet();

    public static void assign(String heroPlayer, Collection<String> commanders) {
        if (heroPlayer == null || commanders == null)
            return;
        TEAM_OF.put(heroPlayer, List.copyOf(commanders));
    }

    public static void unassign(String heroPlayer) {
        if (heroPlayer != null)
            TEAM_OF.remove(heroPlayer);
    }

    public static boolean isHeroRole(String name) {
        return name != null && TEAM_OF.containsKey(name);
    }

    /** The commanders a Hero-role player serves (empty for anyone else). */
    public static List<String> commandersOf(String name) {
        List<String> l = name == null ? null : TEAM_OF.get(name);
        return l == null ? List.of() : l;
    }

    /** Is {@code heroPlayer} a Hero-role player on {@code owner}'s team? */
    public static boolean servesOwner(String heroPlayer, String owner) {
        return owner != null && commandersOf(heroPlayer).contains(owner);
    }

    public static void setLends(String owner, boolean lends) {
        if (owner == null)
            return;
        if (lends)
            LENDERS.add(owner);
        else
            LENDERS.remove(owner);
    }

    public static boolean lends(String owner) {
        return owner != null && LENDERS.contains(owner);
    }

    public static void clearAll() {
        TEAM_OF.clear();
        LENDERS.clear();
    }
}
