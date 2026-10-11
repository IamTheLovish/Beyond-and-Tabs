package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * BAR's team resign vote. A player on a team (allies still in the match) who hits Resign calls a vote instead of
 * leaving on their own: the living <i>human</i> allies (bots never vote) have {@link #WINDOW_TICKS} to answer with
 * F6 (yes) / F7 (no); a strict majority of them passes it and every human on the team is defeated through the usual
 * surrender path (bots on the team fight on - BAR's AIs do not resign with you). The caller's own vote is yes. A
 * player with no living human ally resigns at once. One vote per team at a time; a second call while one is open
 * just counts as a yes.
 *
 * {@link Vote} holds the tally and is pure (no players, no level), so the game test drives it directly; the
 * events around it only find the voters, broadcast the state and apply the result.
 */
public class ResignVoteServerEvents {

    public static final int WINDOW_TICKS = 30 * 20;
    static final int SYNC_TICKS = 20;   // the countdown on the banner moves once a second

    public enum Result { OPEN, PASSED, FAILED }

    /** One open vote: who may vote, who said what, when it closes. */
    public static class Vote {
        public final String caller;
        public final Set<String> voters;   // the living human team members, caller included
        public final Set<String> yes = new LinkedHashSet<>();
        public final Set<String> no = new LinkedHashSet<>();
        public long endTick;

        public Vote(String caller, Set<String> voters, long endTick) {
            this.caller = caller;
            this.voters = new LinkedHashSet<>(voters);
            this.endTick = endTick;
            this.yes.add(caller);
        }

        /** Votes needed to pass: a strict majority of the voters (2 of 2, 2 of 3, 3 of 4, 5 of 8). */
        public int needed() {
            return voters.size() / 2 + 1;
        }

        /** Records a vote from a voter (a non-voter or a repeat of the same answer changes nothing). */
        public boolean cast(String name, boolean inFavour) {
            if (!voters.contains(name))
                return false;
            (inFavour ? no : yes).remove(name);   // changing your mind is allowed while it is open
            return (inFavour ? yes : no).add(name);
        }

        /** Passed once the yes count reaches the majority; failed once it can no longer be reached, or on time out. */
        public Result result(long nowTick) {
            if (yes.size() >= needed())
                return Result.PASSED;
            if (voters.size() - no.size() < needed() || nowTick >= endTick)
                return Result.FAILED;
            return Result.OPEN;
        }
    }

    private static final List<Vote> votes = new ArrayList<>();
    private static long tick = 0;
    private static int syncTicks = 0;

    public static long currentTick() {
        return tick;
    }

    /** The living human RTS players on {@code name}'s team, itself included. */
    public static Set<String> livingHumanTeam(String name) {
        Set<String> team = new LinkedHashSet<>();
        if (PlayerServerEvents.isRTSPlayer(name) && !PlayerServerEvents.isBot(name))
            team.add(name);
        for (String ally : AlliancesServerEvents.getAllConnectedAllies(name))
            if (PlayerServerEvents.isRTSPlayer(ally) && !PlayerServerEvents.isBot(ally))
                team.add(ally);
        return team;
    }

    private static Vote voteFor(String name) {
        for (Vote v : votes)
            if (v.voters.contains(name))
                return v;
        return null;
    }

    /** The Resign button / RESIGN_VOTE packet: alone it is the plain surrender, on a team it opens (or joins) a vote. */
    public static void call(int playerId) {
        ServerPlayer sp = serverPlayer(playerId);
        if (sp == null)
            return;
        String name = sp.getName().getString();
        if (!PlayerServerEvents.isRTSPlayer(name))
            return;
        Vote open = voteFor(name);
        if (open != null) {
            answer(playerId, true);
            return;
        }
        Set<String> team = livingHumanTeam(name);
        if (team.size() <= 1) {
            PlayerServerEvents.defeat(name, Component.translatable("server.reignofnether.surrendered").getString());
            return;
        }
        Vote v = new Vote(name, team, tick + WINDOW_TICKS);
        votes.add(v);
        ReignOfNether.LOGGER.info("[Player] {} called a resign vote for {}", name, team);
        for (String member : v.voters)
            PlayerServerEvents.sendMessageToPlayerNoNewLines(member, "server.reignofnether.resign_vote_called", true,
                name, v.yes.size(), v.needed());
        sync(v);
        settle(v);   // a 2-voter team where the other half already answered can't happen yet, but keeps one path
    }

    /** F6 / F7: a team member's answer. */
    public static void answer(int playerId, boolean inFavour) {
        ServerPlayer sp = serverPlayer(playerId);
        if (sp == null)
            return;
        String name = sp.getName().getString();
        Vote v = voteFor(name);
        if (v == null || !v.cast(name, inFavour))
            return;
        for (String member : v.voters)
            PlayerServerEvents.sendMessageToPlayerNoNewLines(member, inFavour
                ? "server.reignofnether.resign_vote_yes" : "server.reignofnether.resign_vote_no", false,
                name, v.yes.size(), v.needed());
        sync(v);
        settle(v);
    }

    private static void settle(Vote v) {
        Result r = v.result(tick);
        if (r == Result.OPEN)
            return;
        votes.remove(v);
        if (r == Result.PASSED) {
            for (String member : v.voters)
                PlayerServerEvents.sendMessageToPlayerNoNewLines(member, "server.reignofnether.resign_vote_passed", true);
            // the state line first, so the banner is gone before the defeat title plays
            syncClosed(v, true);
            String reason = Component.translatable("server.reignofnether.surrendered").getString();
            for (String member : new ArrayList<>(v.voters))
                PlayerServerEvents.defeat(member, reason);
        } else {
            for (String member : v.voters)
                PlayerServerEvents.sendMessageToPlayerNoNewLines(member, "server.reignofnether.resign_vote_failed", true);
            syncClosed(v, false);
        }
    }

    private static void sync(Vote v) {
        int secondsLeft = (int) Math.max(0, (v.endTick - tick + 19) / 20);
        for (String member : v.voters)
            send(member, v.caller, v.yes.size(), v.needed(), secondsLeft, v.yes.contains(member) ? 1 : v.no.contains(member) ? 2 : 0, 1);
    }

    private static void syncClosed(Vote v, boolean passed) {
        for (String member : v.voters)
            send(member, v.caller, v.yes.size(), v.needed(), 0, 0, passed ? 2 : 3);
    }

    // one PlayerClientboundPacket per member: value1 packs yes | needed<<8 | secondsLeft<<16 | mine<<24, value2 the state
    private static void send(String member, String caller, int yes, int needed, int secondsLeft, int mine, int state) {
        for (ServerPlayer sp : PlayerServerEvents.players)
            if (sp.getName().getString().equals(member)) {
                long packed = (yes & 0xFF) | ((long) (needed & 0xFF) << 8) | ((long) (secondsLeft & 0xFF) << 16)
                    | ((long) mine << 24);
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp),
                    new PlayerClientboundPacket(PlayerAction.RESIGN_VOTE_SYNC, caller, packed, state, com.solegendary.reignofnether.faction.Factions.NONE, true));
                return;
            }
    }

    private static ServerPlayer serverPlayer(int playerId) {
        for (ServerPlayer sp : PlayerServerEvents.players)
            if (sp.getId() == playerId)
                return sp;
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        tick++;
        if (votes.isEmpty())
            return;
        boolean doSync = ++syncTicks >= SYNC_TICKS;
        if (doSync)
            syncTicks = 0;
        for (Vote v : new ArrayList<>(votes)) {
            // voters who left the match (defeated, disconnected) meanwhile no longer count
            v.voters.removeIf(n -> !PlayerServerEvents.isRTSPlayer(n));
            v.yes.retainAll(v.voters);
            v.no.retainAll(v.voters);
            if (v.voters.isEmpty()) {
                votes.remove(v);
                continue;
            }
            settle(v);
            if (doSync && votes.contains(v))
                sync(v);
        }
    }

    /** Match reset / server stop: nothing to vote on any more. */
    public static void clear() {
        votes.clear();
    }
}
