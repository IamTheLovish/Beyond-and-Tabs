package com.solegendary.reignofnether.network;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.hero.HeroRoles;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.PacketTarget;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Who receives a clientbound packet (design/ron_rewrite_plan.md, rewrite 3). New file; it replaces the 138
 * {@code PacketDistributor.ALL} sends Reign of Nether used for nearly everything - worker loads, hero mana / XP / ranks,
 * ability cooldowns, research, stockpiles, sounds in the fog - with one deliberate audience per send. With remote
 * players in the match (lobby + e4mc), anything a client receives is something a modified client can show, so an
 * audience is a fairness rule, not an optimisation (though it is one too: most packets now reach far fewer players).
 *
 * <p>The policies, narrowest first. Spectators see everything (BAR's spectator rules): every policy from {@link
 * Policy#TEAM} up includes them, and so does anyone who is not seated - a defeated player spectates.
 * <ul>
 *   <li>{@link Policy#OWNER} - the one player a packet is about: their warnings, camera, research, idle workers.</li>
 *   <li>{@link Policy#COMMANDERS} - whoever may order the owner's units ({@link PacketAuth#mayActFor}: the owner,
 *       allies the owner shares control with, sandbox editors): orders, paths, fog-queued build sites.</li>
 *   <li>{@link Policy#ALLIES} - the owner's side: the owner, their allies, and the Hero-role players serving the
 *       owner or an ally ({@link HeroRoles#commandersOf}, the same side {@link FogOfWarServerEvents#teamOwnersFor}
 *       shares vision with): team alerts.</li>
 *   <li>{@link Policy#TEAM} - ALLIES plus spectators: a side's private state (stockpile, cooldowns, skill points).</li>
 *   <li>{@link Policy#SEEN} - TEAM plus everyone whose fog shows the spot right now
 *       ({@link FogOfWarServerEvents#isBlockVisibleFor}, true for anyone the fog does not apply to): what you would
 *       read off a unit, building or effect you can see. {@link AudienceResync} re-sends unit state when it comes
 *       back into view, so a narrowed audience never leaves a client with stale data.</li>
 *   <li>{@link Policy#SPECTATORS} - only those who see everything.</li>
 *   <li>{@link Policy#ALL} - match-wide announcements and public data (game rules, the player list, the match clock,
 *       a game ender's start, which design/big_units.md announces on purpose).</li>
 * </ul>
 * {@link #tracking} (vanilla's entity trackers) is offered for client-loaded visuals, with a caveat: the fog mixin
 * only stops a tracker from being added, it never removes one, so trackers can include players who no longer see the
 * entity. Fog-sensitive state therefore uses SEEN.
 *
 * <p>The rules themselves ({@link #receives}, {@link #recipients}) are pure functions over {@link Facts}, so
 * gametest/AudienceGameTests checks recipient sets per policy without a running match, like
 * {@code MapDrawRules.recipients}. The packet targets below apply the same rules to the online players with
 * {@link #LIVE} facts. Packets about an entity the server does not know (not added yet, or already gone) have no
 * audience: no client can hold that entity either, so they would have been dropped on arrival.
 *
 * <p>Server thread only, like all game state (the targets read alliances, seats and fog vision).
 */
public final class Audience {
    private Audience() { }

    public enum Policy { OWNER, COMMANDERS, ALLIES, TEAM, SEEN, SPECTATORS, ALL }

    // ------------------------------------------------------------------------------------------------ the rules

    /** The facts the policies read, as functions, so a game test can check recipient sets without a match. */
    public interface Facts {
        /** Are {@code a} and {@code b} allied (a player is not their own ally; see {@link #onSide}). */
        boolean allied(String a, String b);

        /** The commanders a Hero-role player serves; empty for anyone else. */
        List<String> commandersOf(String player);

        /** Sees everything: no seat and no Hero role (a spectator, or a defeated player), or a sandbox editor. */
        boolean spectator(String player);

        /** May order {@code owner}'s units. */
        boolean mayCommand(String player, String owner);
    }

    /** Is {@code player} on {@code owner}'s side: the owner, an ally, or a Hero-role player serving either. */
    public static boolean onSide(String player, String owner, Facts facts) {
        if (player == null || owner == null || owner.isEmpty())
            return false;   // neutral things (no owner) have no side
        if (player.equals(owner) || facts.allied(player, owner))
            return true;
        List<String> commanders = facts.commandersOf(player);
        for (int i = 0; i < commanders.size(); i++) {
            String c = commanders.get(i);
            if (c.equals(owner) || facts.allied(c, owner))
                return true;
        }
        return false;
    }

    /**
     * Does {@code player} receive a packet about {@code owner}'s thing under {@code policy}? {@code canSee} (the fog
     * test for the thing's spot) is only asked for {@link Policy#SEEN}, and only when the cheaper tests failed.
     */
    public static boolean receives(Policy policy, String player, String owner, Facts facts, BooleanSupplier canSee) {
        if (player == null)
            return false;
        return switch (policy) {
            case OWNER -> player.equals(owner);
            case COMMANDERS -> owner != null && facts.mayCommand(player, owner);
            case ALLIES -> onSide(player, owner, facts);
            case TEAM -> onSide(player, owner, facts) || facts.spectator(player);
            case SEEN -> onSide(player, owner, facts) || canSee.getAsBoolean() || facts.spectator(player);
            case SPECTATORS -> facts.spectator(player);
            case ALL -> true;
        };
    }

    /** The players among {@code online} who receive a packet about {@code owner}'s thing, in {@code online}'s order. */
    public static Set<String> recipients(Policy policy, String owner, Collection<String> online, Facts facts,
                                         Predicate<String> canSee) {
        Set<String> out = new LinkedHashSet<>();
        for (String p : online)
            if (receives(policy, p, owner, facts, () -> canSee.test(p)))
                out.add(p);
        return out;
    }

    /** The owners among {@code owners} whose private state {@code player} gets (TEAM): all of them for a spectator. */
    public static Set<String> knownOwners(String player, Collection<String> owners, Facts facts) {
        Set<String> out = new LinkedHashSet<>();
        boolean all = facts.spectator(player);
        for (String o : owners)
            if (all || onSide(player, o, facts))
                out.add(o);
        return out;
    }

    /** The facts of the running server: alliances, Hero roles, seats and allied control. */
    public static final Facts LIVE = new Facts() {
        @Override
        public boolean allied(String a, String b) {
            return AlliancesServerEvents.isAllied(a, b);
        }

        @Override
        public List<String> commandersOf(String player) {
            return HeroRoles.commandersOf(player);
        }

        @Override
        public boolean spectator(String player) {
            RTSPlayer seat = PlayerServerEvents.getRTSPlayer(player);
            if (seat != null)
                return seat.faction == Factions.NONE;   // a sandbox editor (SandboxServer) works on every side
            return !HeroRoles.isHeroRole(player);         // a Hero has no seat but plays under their team's fog
        }

        @Override
        public boolean mayCommand(String player, String owner) {
            return PacketAuth.mayActFor(player, owner);
        }
    };

    // ------------------------------------------------------------------------------------------ one online player

    private static String nameOf(ServerPlayer sp) {
        return sp.getGameProfile().getName();   // = getName().getString() for a player, without building a Component
    }

    /** May {@code sp} see block column (x, z) through the fog? Always true for anyone the fog does not apply to. */
    public static boolean canSee(ServerPlayer sp, double x, double z) {
        return FogOfWarServerEvents.isBlockVisibleFor(sp, Mth.floor(x), Mth.floor(z));
    }

    /** TEAM for one player: is {@code sp} on {@code owner}'s side, or a spectator? */
    public static boolean knowsPrivately(ServerPlayer sp, String owner) {
        String name = nameOf(sp);
        return onSide(name, owner, LIVE) || LIVE.spectator(name);
    }

    /** SEEN for one player: may {@code sp} know about {@code owner}'s thing at block column (x, z)? */
    public static boolean mayKnowAt(ServerPlayer sp, String owner, int x, int z) {
        String name = nameOf(sp);
        return onSide(name, owner, LIVE) || FogOfWarServerEvents.isBlockVisibleFor(sp, x, z) || LIVE.spectator(name);
    }

    /** SEEN for one player and an entity (its own column; a unit's owner, "" for anything else). */
    public static boolean mayKnow(ServerPlayer sp, Entity e) {
        return e != null && mayKnowAt(sp, ownerOf(e), e.getBlockX(), e.getBlockZ());
    }

    /** SEEN for one player and a building (any corner in view; a hidden trap stays hidden from its enemies). */
    public static boolean mayKnowBuilding(ServerPlayer sp, BuildingPlacement b) {
        if (b == null)
            return false;
        String name = nameOf(sp);
        return onSide(name, b.ownerName, LIVE) || FogOfWarServerEvents.canPlayerSeeBuilding(sp, b)
            || LIVE.spectator(name);
    }

    /**
     * What one online player privately knows during one pass over many units (UnitSyncBatcher): everything for a
     * spectator, otherwise the sides they are on, decided once per owner instead of once per unit. {@link #seats} -
     * the seated players on their side - is what {@link AudienceResync} compares to notice that it changed (a
     * defeat that turned them into a spectator, a new alliance, a Hero role).
     */
    public static final class View {
        public final String name;
        public final boolean spectator;
        final Set<String> seats;
        private final Map<String, Boolean> sides = new HashMap<>();

        View(String name, boolean spectator, Set<String> seats) {
            this.name = name;
            this.spectator = spectator;
            this.seats = seats;
        }

        public boolean knowsPrivately(String owner) {
            if (spectator)
                return true;
            if (owner == null || owner.isEmpty())
                return false;
            Boolean known = sides.get(owner);
            if (known == null) {
                known = onSide(name, owner, LIVE);
                sides.put(owner, known);
            }
            return known;
        }

        boolean sameAs(View other) {
            return other != null && spectator == other.spectator && seats.equals(other.seats);
        }
    }

    public static View viewOf(ServerPlayer sp) {
        String name = nameOf(sp);
        boolean spectator = LIVE.spectator(name);
        List<String> seated = new ArrayList<>();
        synchronized (PlayerServerEvents.rtsPlayers) {
            for (RTSPlayer p : PlayerServerEvents.rtsPlayers)
                seated.add(p.name);
        }
        return new View(name, spectator, spectator ? Set.of() : knownOwners(name, seated, LIVE));
    }

    // ------------------------------------------------------------------------------------------- what things are

    /** A unit's owner; "" for anything else (neutral: no side, so only SEEN's fog test or spectators reach it). */
    public static String ownerOf(@Nullable Entity e) {
        if (e instanceof Unit unit) {
            String owner = unit.getOwnerName();
            return owner == null ? "" : owner;
        }
        return "";
    }

    /** The entity with this id on the server (the overworld first, where every RTS unit lives), or null. */
    @Nullable
    public static Entity entity(int id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return null;
        ServerLevel overworld = server.overworld();
        Entity e = overworld.getEntity(id);
        if (e != null)
            return e;
        for (ServerLevel level : server.getAllLevels())
            if (level != overworld && (e = level.getEntity(id)) != null)
                return e;
        return null;
    }

    /** The registered building whose origin is exactly {@code origin} (no footprint test: senders pass the origin). */
    @Nullable
    public static BuildingPlacement buildingAt(@Nullable BlockPos origin) {
        if (origin == null)
            return null;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (origin.equals(b.originPos))
                return b;
        return null;
    }

    // ------------------------------------------------------------------------------------------- packet targets

    /** Test hook (gametest/AudienceGameTests): told about every target built here. Null in play; server thread. */
    public interface Accountant {
        /** A packet about {@code owner}'s thing (at block x / z when {@code positional}) goes out under {@code policy}. */
        void sent(Policy policy, String owner, boolean positional, int x, int z);
    }

    public static volatile Accountant accountant = null;

    private static void account(Policy policy, String owner, boolean positional, int x, int z) {
        Accountant a = accountant;
        if (a != null)
            a.sent(policy, owner == null ? "" : owner, positional, x, z);
    }

    // the online players a predicate accepts; the packet is encoded once and the same bytes go to each of them
    private static final PacketDistributor<Predicate<ServerPlayer>> MATCHING =
        new PacketDistributor<Predicate<ServerPlayer>>((distributor, filter) -> packet -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null)
                return;
            Predicate<ServerPlayer> accepts = filter.get();
            for (ServerPlayer sp : server.getPlayerList().getPlayers())
                if (accepts.test(sp))
                    sp.connection.send(packet);
        }, NetworkDirection.PLAY_TO_CLIENT);

    // one player by name (a direct lookup rather than a test of every player)
    private static final PacketDistributor<String> NAMED =
        new PacketDistributor<String>((distributor, name) -> packet -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            String n = name.get();
            ServerPlayer sp = server == null || n == null || n.isEmpty() ? null : server.getPlayerList().getPlayerByName(n);
            if (sp != null)
                sp.connection.send(packet);
        }, NetworkDirection.PLAY_TO_CLIENT);

    // nobody: a packet about an entity the server does not know (see the class comment)
    private static final PacketDistributor<Void> NOBODY =
        new PacketDistributor<Void>((distributor, unused) -> packet -> { }, NetworkDirection.PLAY_TO_CLIENT);

    private static PacketTarget matching(Predicate<ServerPlayer> accepts) {
        return MATCHING.with(() -> accepts);
    }

    public static PacketTarget nobody() {
        return NOBODY.noArg();
    }

    /** {@link Policy#OWNER}: the player named {@code name}, if online. */
    public static PacketTarget owner(String name) {
        account(Policy.OWNER, name, false, 0, 0);
        return NAMED.with(() -> name);
    }

    /** {@link Policy#COMMANDERS}: whoever may order {@code owner}'s units. */
    public static PacketTarget commanders(String owner) {
        account(Policy.COMMANDERS, owner, false, 0, 0);
        return matching(sp -> owner != null && LIVE.mayCommand(nameOf(sp), owner));
    }

    /** {@link Policy#COMMANDERS} of a unit's owner. */
    public static PacketTarget commandersOf(@Nullable Entity unit) {
        return unit == null ? nobody() : commanders(ownerOf(unit));
    }

    /** {@link Policy#ALLIES}: {@code owner}'s side. */
    public static PacketTarget allies(String owner) {
        account(Policy.ALLIES, owner, false, 0, 0);
        return matching(sp -> onSide(nameOf(sp), owner, LIVE));
    }

    /** {@link Policy#ALLIES} of a unit's owner. */
    public static PacketTarget alliesOf(@Nullable Entity unit) {
        return unit == null ? nobody() : allies(ownerOf(unit));
    }

    /** {@link Policy#TEAM}: {@code owner}'s side and the spectators. */
    public static PacketTarget team(String owner) {
        account(Policy.TEAM, owner, false, 0, 0);
        return matching(sp -> knowsPrivately(sp, owner));
    }

    /** {@link Policy#TEAM} of an entity's owner (nobody if the server doesn't know it). */
    public static PacketTarget teamOf(@Nullable Entity e) {
        return e == null ? nobody() : team(ownerOf(e));
    }

    /** {@link Policy#TEAM} of the owner of the entity with this id. */
    public static PacketTarget teamOfUnit(int entityId) {
        return teamOf(entity(entityId));
    }

    /** {@link Policy#TEAM} of the building at this origin (nobody if there is none). */
    public static PacketTarget teamOfBuildingAt(BlockPos origin) {
        BuildingPlacement b = buildingAt(origin);
        return b == null ? nobody() : team(b.ownerName);
    }

    /** {@link Policy#SEEN} for an entity: its owner's side, spectators, and whoever sees its column. */
    public static PacketTarget seen(@Nullable Entity e) {
        if (e == null)
            return nobody();
        String owner = ownerOf(e);
        int x = e.getBlockX(), z = e.getBlockZ();
        account(Policy.SEEN, owner, true, x, z);
        return matching(sp -> mayKnowAt(sp, owner, x, z));
    }

    /** {@link Policy#SEEN} for the entity with this id. */
    public static PacketTarget seenUnit(int entityId) {
        return seen(entity(entityId));
    }

    /** {@link Policy#SEEN} for several of {@code owner}'s entities (a conversion): whoever may know any of them. */
    public static PacketTarget seenUnits(String owner, List<Integer> entityIds) {
        List<Entity> found = new ArrayList<>(entityIds.size());
        for (int id : entityIds) {
            Entity e = entity(id);
            if (e != null)
                found.add(e);
        }
        if (found.isEmpty())
            return nobody();
        account(Policy.SEEN, owner, true, found.get(0).getBlockX(), found.get(0).getBlockZ());
        return matching(sp -> {
            if (knowsPrivately(sp, owner))
                return true;
            for (Entity e : found)
                if (FogOfWarServerEvents.isBlockVisibleFor(sp, e.getBlockX(), e.getBlockZ()))
                    return true;
            return false;
        });
    }

    /** {@link Policy#SEEN} for something at a spot with no owner (a sound, an effect): whoever sees it, spectators. */
    public static PacketTarget seenAt(BlockPos pos) {
        int x = pos.getX(), z = pos.getZ();
        account(Policy.SEEN, "", true, x, z);
        return matching(sp -> mayKnowAt(sp, "", x, z));
    }

    /** {@link Policy#SEEN} for a building: its owner's side, spectators, whoever sees a corner of it. */
    public static PacketTarget seenBuilding(@Nullable BuildingPlacement b) {
        if (b == null)
            return nobody();
        account(Policy.SEEN, b.ownerName, true, b.centrePos.getX(), b.centrePos.getZ());
        return matching(sp -> mayKnowBuilding(sp, b));
    }

    /** {@link Policy#SEEN} for the building at this origin (nobody if there is none). */
    public static PacketTarget seenBuildingAt(BlockPos origin) {
        return seenBuilding(buildingAt(origin));
    }

    /** Vanilla's trackers of an entity (see the class comment for why fog-sensitive state uses SEEN instead). */
    public static PacketTarget tracking(Entity e) {
        return PacketDistributor.TRACKING_ENTITY.with(() -> e);
    }

    /** {@link Policy#SPECTATORS}: only those who see everything. */
    public static PacketTarget spectators() {
        account(Policy.SPECTATORS, "", false, 0, 0);
        return matching(sp -> LIVE.spectator(nameOf(sp)));
    }

    /** Everyone the fog does not apply to right now: all players while fog is off, the spectators while it is on. */
    public static PacketTarget unfogged() {
        account(Policy.SPECTATORS, "", false, 0, 0);
        return matching(sp -> !FogOfWarServerEvents.isFogActiveFor(sp));
    }

    /** {@link Policy#ALL}: every player. */
    public static PacketTarget all() {
        account(Policy.ALL, "", false, 0, 0);
        return PacketDistributor.ALL.noArg();
    }
}
