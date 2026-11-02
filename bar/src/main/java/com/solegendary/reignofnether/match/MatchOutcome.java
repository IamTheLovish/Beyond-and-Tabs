package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.matchstart.MultiplayerMatch;
import com.solegendary.reignofnether.player.DefeatCleanupServerEvents;
import com.solegendary.reignofnether.player.MatchStatsClientboundPacket;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a match is lost and won (moved out of RoN's PlayerServerEvents, which keeps thin delegates): a side's defeat,
 * the victory check it triggers, the beacon victory, the commander rule and the results table.
 * <p>
 * Rules (pinned by gametest/MatchLifecycleGameTests):
 * <ul>
 *   <li>A side is beaten when it surrenders (alone, or by a passed team vote), loses everything (UnitServerEvents,
 *       BuildingPlacement), loses its commander with no ally left in play (lovish's rule: instant loss in a 1v1, a
 *       team player fights on), or is removed (bots, commands).</li>
 *   <li>A beaten side's base goes the way of BAR's: buildings become neutral wrecks holding half their metal and its
 *       units die, over a few seconds (DefeatCleanupServerEvents).</li>
 *   <li>The match is won when the sides left of <i>that</i> match (MultiplayerMatch; every side on the server for a
 *       classic match) are one alliance: the whole team wins together and stays in play until it spectates.</li>
 * </ul>
 * BAR (Spring) for comparison: a team is killed by its game-end rule (all units, all builders or all commanders,
 * per ally team) and the game is over when one ally team remains ({@code GameOver(winningAllyTeams)}); a killed team's
 * units are destroyed. The win condition here is BAR's; the commander rule differs in team games (BAR's
 * commander-ends mode kills an ally team when its last commander falls; here the team fights on until it has nothing
 * left) - see design/ron_rewrite_plan.md "Rewrite 5" for that open question.
 * Game thread only.
 */
public final class MatchOutcome {
    private MatchOutcome() { }

    /** Ticks after a defeat before the beaten player's chunks are re-sent without fog (they now see everything). */
    static final int FOG_RESEND_TICKS = 60;

    /**
     * RoN callers pass a defeat reason as text; a few passed a bare translation key, which printed raw ("X has
     * server.reignofnether.defeat_commander and is defeated!"). Those keys name whole sentences, so they map onto
     * the fragment that fits "%s has %s and is defeated!".
     */
    static final Map<String, String> KEY_REASONS = Map.of(
        "server.reignofnether.defeat_commander", "server.reignofnether.lost_commander",
        "server.reignofnether.defeat_bot_removed", "server.reignofnether.was_removed");

    /** The reason as shown in "%s has %s and is defeated!": a key is translated (on each client), text kept as is. */
    public static Component reasonText(String reason) {
        if (reason == null || reason.isBlank())
            return Component.translatable("server.reignofnether.lost_all");
        if (reason.startsWith("server.reignofnether.") && reason.indexOf(' ') < 0)
            return Component.translatable(KEY_REASONS.getOrDefault(reason, reason));
        return Component.literal(reason);
    }

    // ------------------------------------------------------------------------------------------ defeat

    // (one defeat method only, taking the reason as a Component: a String overload next to it can't be told apart by
    // tools/check_java.py, which compiles without the Minecraft classes - callers with text use reasonText)

    /**
     * Beats a side: out of play (fog lifts, observer HUD), told to everyone, its faction state dropped, its base
     * stripped and handed to the cleanup, its research and resources forgotten - then the victory check for its
     * match. Nothing for a sandbox player or a name not in play.
     */
    public static void defeat(String playerName, Component reason) {
        if (playerName == null || SandboxServer.isSandboxPlayer(playerName))
            return;
        RTSPlayer side = MatchRoster.leave(playerName);
        if (side == null)
            return;
        ReignOfNether.LOGGER.info("[Match] defeat: {} ({})", playerName, reason.getString());
        MatchRoster.finish(side);
        PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.is_defeated", true, playerName, reason);
        PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.players_remaining", false, MatchRoster.LIVE.size());
        PlayerClientboundPacket.defeat(playerName);
        // they watch the rest unfogged: their client gets every chunk again once the defeat has gone through
        TaskSchedulerServerEvents.schedule(FOG_RESEND_TICKS, () -> {
            for (ServerPlayer sp : PlayerServerEvents.players)
                if (sp.getName().getString().equals(playerName)) {
                    FogOfWarServerEvents.resendAllTrackedChunks(sp);
                    break;
                }
        });
        forgetFactionState(playerName);
        List<LivingEntity> units = new ArrayList<>();
        List<BuildingPlacement> buildings = new ArrayList<>();
        stripOwnership(playerName, units, buildings);

        PlayerServerEvents.saveRTSPlayers();
        ResearchServerEvents.removeAllResearchFor(playerName);
        ResearchServerEvents.syncResearch(playerName);
        ResearchServerEvents.saveResearch();
        ResearchServerEvents.removeAllCheatsFor(playerName);
        ResourcesServerEvents.resourcesList.removeIf(rl -> rl.ownerName.equals(playerName));

        decide(playerName);
        DefeatCleanupServerEvents.begin(PlayerServerEvents.serverLevel, playerName, buildings, units);
    }

    /** Faction state that has no block or unit of its own to fall with the base. */
    static void forgetFactionState(String playerName) {
        com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(playerName);   // pools dry up at once
        com.solegendary.reignofnether.tide.FogBankServerEvents.clearOwner(playerName);
        com.solegendary.reignofnether.mana.ManaServerEvents.clearOwner(playerName);       // Overcharge, wells, ley links
        com.solegendary.reignofnether.mana.WardServerEvents.clearOwner(playerName);
        com.solegendary.reignofnether.mana.FrostServerEvents.clearOwner(playerName);
        com.solegendary.reignofnether.mana.DecoyServerEvents.clearOwner(playerName);
        com.solegendary.reignofnether.mana.LanternDomeServerEvents.clearOwner(playerName);
        com.solegendary.reignofnether.mana.TempestServerEvents.clearOwner(playerName);
        com.solegendary.reignofnether.ability.abilities.Thunderhead.clearOwner(playerName);
    }

    /**
     * Takes every unit and building away from the beaten side (orders cleared, production queues emptied) and
     * collects them for DefeatCleanupServerEvents: a beaten side's capitol must not stand on as a live target.
     */
    static void stripOwnership(String playerName, List<LivingEntity> units, List<BuildingPlacement> buildings) {
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (entity instanceof Unit unit && playerName.equals(unit.getOwnerName())) {
                unit.resetBehaviours();
                Unit.resetBehaviours(unit);
                if (unit instanceof AttackerUnit aUnit)
                    AttackerUnit.resetBehaviours(aUnit);
                if (unit instanceof WorkerUnit wUnit)
                    WorkerUnit.resetBehaviours(wUnit);
                unit.setOwnerName("");
                units.add(entity);
            }
        }
        for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
            if (playerName.equals(building.ownerName)) {
                if (building instanceof ProductionPlacement productionBuilding)
                    productionBuilding.productionQueue.clear();
                building.ownerName = "";
                buildings.add(building);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ victory

    /**
     * The winners when only {@code remaining} are left: everyone of them if they are all one connected alliance
     * (a lone survivor included), nobody while two sides still stand or when nobody is left.
     */
    public static Set<String> winnersAmong(Set<String> remaining) {
        if (remaining == null || remaining.isEmpty())
            return Set.of();
        if (remaining.size() == 1)
            return Set.copyOf(remaining);
        // the first remaining side's alliance group must hold all the others (containsAll rather than equals: the
        // group may also hold an ally who was already beaten)
        Set<String> group = AlliancesServerEvents.getAllConnectedAllies(remaining.iterator().next());
        return group.containsAll(remaining) ? new LinkedHashSet<>(remaining) : Set.of();
    }

    /**
     * After {@code defeated} fell: who is left of its match (its MultiplayerMatch's sides still in play, or every side
     * on the server for a classic match) - and if that is one alliance, they win. Off in coop mode (everyone is
     * allied there; victory only by command).
     */
    static void decide(String defeated) {
        ServerLevel level = PlayerServerEvents.serverLevel;
        if (level != null && level.getServer().getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get())
            return;
        MultiplayerMatch match = MultiplayerMatch.of(defeated);
        Set<String> remaining = new LinkedHashSet<>();
        if (match != null)
            remaining.addAll(match.remaining());
        else
            for (RTSPlayer p : MatchRoster.LIVE)
                remaining.add(p.name);
        Set<String> winners = winnersAmong(remaining);
        if (winners.isEmpty())
            return;
        declareVictory(winners);
        if (match != null)
            match.end();
        MatchState.live().onDecided(match, winners);
    }

    /** Victory titles and chat for {@code winners}, who join the finished sides; then the results for everyone. */
    static void declareVictory(Set<String> winners) {
        boolean alone = winners.size() == 1;
        for (String winner : winners) {
            MatchRoster.finish(MatchRoster.get(winner));
            PlayerServerEvents.sendMessageToAllPlayers(alone ? "server.reignofnether.victorious"
                : "server.reignofnether.victory_alliance", true, winner);
            PlayerClientboundPacket.victory(winner);
        }
        broadcastMatchStats(winners);
    }

    /**
     * A beacon held long enough (RoN): in survival it ends the wave with the holder's alliance victorious; otherwise
     * every side in play that isn't the holder or its ally is beaten (the roster walk is a snapshot, so the defeats
     * inside it are safe - with RoN's synchronized list this loop threw ConcurrentModificationException).
     */
    public static void beaconVictory(String playerName) {
        ReignOfNether.LOGGER.info("[Match] beacon victory: {}", playerName);
        if (SurvivalServerEvents.isEnabled()) {
            Set<String> allies = new LinkedHashSet<>(AlliancesServerEvents.getAllAllies(playerName));
            PlayerServerEvents.sendMessageToAllPlayers(allies.isEmpty() ? "server.reignofnether.victorious"
                : "server.reignofnether.victory_alliance", true, playerName);
            PlayerClientboundPacket.victory(playerName);
            for (String allyName : allies)
                PlayerClientboundPacket.victory(allyName);
            SurvivalServerEvents.endCurrentWave();
            Set<String> winners = new LinkedHashSet<>(allies);
            winners.add(playerName);
            broadcastMatchStats(winners);
            return;
        }
        Component reason = Component.translatable("server.reignofnether.beacon_defeat");
        for (RTSPlayer p : MatchRoster.LIVE) {
            if (p.name.equals(playerName) || AlliancesServerEvents.isAllied(playerName, p.name))
                continue;
            defeat(p.name, reason);
        }
    }

    /**
     * The final scoreboard for every client's results screen (MatchEndScreen): every side in play or finished, once,
     * winners marked, with the match time.
     */
    public static void broadcastMatchStats(Set<String> winnerNames) {
        MatchStatsClientboundPacket.broadcast(MatchState.live().clock(), statsRows(winnerNames));
    }

    /**
     * The results again for one participant logging in to a decided match (MatchServerEvents): the popup - and with
     * it Spectate, and the Match Menu offering Spectate rather than Resign - is client memory that a reload or a
     * dropped connection wiped. Only that player: everyone else still has theirs. {@code ticks} is the match time
     * at the decision (MatchState.endClock), not the clock now, which runs on while winners keep their bases.
     */
    public static void sendMatchStats(ServerPlayer player, Set<String> winnerNames, long ticks) {
        if (player instanceof FakePlayer)
            return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new MatchStatsClientboundPacket(ticks, statsRows(winnerNames)));
    }

    /** One results row per side in play or finished, once each (the live side first), winners marked. */
    static List<MatchStatsClientboundPacket.MatchStatRow> statsRows(Set<String> winnerNames) {
        LinkedHashMap<String, RTSPlayer> byName = new LinkedHashMap<>();
        for (RTSPlayer p : MatchRoster.LIVE)
            if (p != null)
                byName.putIfAbsent(p.name, p);
        for (RTSPlayer p : MatchRoster.FINISHED)
            if (p != null)
                byName.putIfAbsent(p.name, p);
        List<MatchStatsClientboundPacket.MatchStatRow> rows = new ArrayList<>();
        for (RTSPlayer p : byName.values())
            rows.add(new MatchStatsClientboundPacket.MatchStatRow(
                p.name, p.faction, winnerNames.contains(p.name), p.startPosColorId,
                p.scores.getScoreListAsArray(),
                Math.round(p.damageDealt), Math.round(p.metalProduced), Math.round(p.metalReclaimed),
                p.history.getIntervalTicks(), p.history.metalSamples(), p.history.energySamples(),
                p.history.armySamples()));
        return rows;
    }

    // ------------------------------------------------------------------------------------------ the commander rule

    /**
     * lovish's rule (CommanderServerEvents, gamerule commanderDefeat): losing your commander loses you the match in
     * a 1v1, or once no ally of yours is left in play; in a team game you fight on with what you have left while a
     * teammate still has a commander - and when the TEAM's last commander falls, the whole team is out (BAR's
     * commander-ends rule; lovish chose it on Oct 12). The commander blast has already gone off either way.
     */
    public static void onCommanderLost(String owner) {
        if (owner == null || owner.isEmpty() || !MatchRoster.isInPlay(owner))
            return;
        PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.commander_fallen", true, owner);
        if (!hasAllyInPlay(owner)) {
            defeat(owner, Component.translatable("server.reignofnether.lost_commander"));
            return;
        }
        if (teamHasLivingCommander(owner)) {
            PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.commander_fallen_team", false, owner);
            return;
        }
        // the team's last commander: collect first (defeat changes the roster), then beat every member
        List<String> team = new ArrayList<>();
        team.add(owner);
        for (RTSPlayer other : MatchRoster.LIVE)
            if (!other.name.equals(owner) && AlliancesServerEvents.isAllied(owner, other.name))
                team.add(other.name);
        for (String member : team)
            if (MatchRoster.isInPlay(member))
                defeat(member, Component.translatable("server.reignofnether.lost_last_commander"));
    }

    /**
     * Whether {@code owner} or an ally still in play has a living commander. Called once per commander death, so a
     * scan of the unit list is fine (no per-tick cost). The dying commander already reads !isAlive() here.
     */
    public static boolean teamHasLivingCommander(String owner) {
        for (net.minecraft.world.entity.LivingEntity le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits()) {
            if (!le.isAlive() || le.isRemoved() || !com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le)
                    || !(le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u))
                continue;
            String o = u.getOwnerName();
            if (o == null || !MatchRoster.isInPlay(o))
                continue;
            if (o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                return true;
        }
        return false;
    }

    /** Whether another side still in play (human or bot) is allied with {@code owner}. */
    public static boolean hasAllyInPlay(String owner) {
        for (RTSPlayer other : MatchRoster.LIVE)
            if (!other.name.equals(owner) && AlliancesServerEvents.isAllied(owner, other.name))
                return true;
        return false;
    }
}
