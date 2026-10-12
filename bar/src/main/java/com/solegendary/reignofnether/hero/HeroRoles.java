package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Who plays the Hero role, and for which team (design/hero_mode.md, section 1). A Hero-role player is a human seat
 * in the multiplayer lobby that has no base of its own: at match start MultiplayerLobby.startMatch attaches them to
 * their team's commanders, and from then on PossessionServer.mayPossess lets them take the team's hero units (and
 * any unit of a commander who lends theirs with /rts-possession allow).
 *
 * Server state only, keyed by player name like every other RTS registry (alliances, RTS players). Cleared on
 * /rts-reset and server stop (MultiplayerLobby.onReset / onServerStopping).
 *
 * Hero slice 3: a Hero-role player plays under their team's fog (FogOfWarServerEvents.fogAppliesTo /
 * teamOwnersFor), so every change here drops the fog's per-player "is fogged" cache, and the client is told when
 * the role ends (CR-17) so its fog and Backspace never disagree with the server.
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
        FogOfWarServerEvents.invalidateRtsCache();
    }

    /** Silent removal (game tests, the lobby re-seating a player): no packet. */
    public static void unassign(String heroPlayer) {
        if (heroPlayer != null && TEAM_OF.remove(heroPlayer) != null)
            FogOfWarServerEvents.invalidateRtsCache();
    }

    /**
     * Where "the role is now on/off for this player" goes: by default the ROLE packet to that player if they are
     * online. A field so a game test can watch what reset sends without a real connection (swapped and restored
     * inside one server-thread callback, so parallel tests never see it).
     */
    public static volatile BiConsumer<String, Boolean> roleSink = HeroRoles::sendRole;

    /**
     * Ends {@code heroPlayer}'s role and tells their client (design/code_review.md CR-17): before, the client kept
     * {@code heroRole} until logout, so after /rts-reset Backspace with nothing selected still asked for a hero - and,
     * with team fog (slice 3), a client still fogged while the server sent no vision would see a black world.
     */
    public static boolean revoke(String heroPlayer) {
        if (heroPlayer == null || TEAM_OF.remove(heroPlayer) == null)
            return false;
        FogOfWarServerEvents.invalidateRtsCache();
        roleSink.accept(heroPlayer, false);
        return true;
    }

    /** The ROLE packet (with the commanders, for the client's team fog) to {@code name} if they are online. */
    static void sendRole(String name, boolean on) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer sp = server == null ? null : server.getPlayerList().getPlayerByName(name);
        if (sp != null)
            PossessionClientboundPacket.role(sp, on);
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

    /** /rts-reset, lobby close, server stop: every Hero-role player is told the role is over (CR-17). */
    public static void clearAll() {
        for (String name : List.copyOf(TEAM_OF.keySet()))
            revoke(name);
        TEAM_OF.clear();
        LENDERS.clear();
    }
}
