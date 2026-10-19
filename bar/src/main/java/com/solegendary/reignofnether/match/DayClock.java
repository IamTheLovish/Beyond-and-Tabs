package com.solegendary.reignofnether.match;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;

import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The match's sun: the lobby's start time (TimeOfDay) and day/night cycle (DayCycle), lovish's Oct 18 ask.
 * <p>
 * Vanilla's doDaylightCycle only knows one speed, so it stays OFF (as PR #2 left every skirmish world) and this moves
 * the clock itself: while a match is being played, one day-time tick every {@code matchDayCycle} game ticks. One rule
 * in one tick handler. The speed is a game rule, so it is saved with the world on every road a match starts by (the
 * skirmish lobby's instant start, a pre-made map, the multiplayer lobby) and a reloaded match keeps its sun. The clock
 * stands still while the map rises and starts are placed, and once the match is decided (MatchPhase.PLAYING only).
 * <p>
 * Clients: RoN's ClientPacketMixin turns every time packet into the client's target time and ClientLevelMixin walks
 * the client clock to it. Vanilla only sends that packet once a second, which would make the sun jump a second's worth
 * at a time; so each step sends it at once to the overworld's players - a 20-byte packet, 7 a second at Slow.
 */
public final class DayClock {

    private DayClock() {
    }

    /** At Start: the host's light and cycle on the overworld (server thread; any level of the server will do). */
    public static void apply(ServerLevel anyLevel, TimeOfDay start, DayCycle cycle) {
        MinecraftServer server = anyLevel.getServer();
        ServerLevel overworld = server.overworld();
        overworld.setDayTime(start.dayTime);
        overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        overworld.getGameRules().getRule(GameRuleRegistrar.MATCH_DAY_CYCLE).set(cycle.ticksPerStep, server);
        broadcast(overworld);
        ReignOfNether.LOGGER.info("[DayClock] start time {} (day time {}), cycle {} ({} game ticks per day-time tick)",
            start.name(), start.dayTime, cycle.name(), cycle.ticksPerStep);
    }

    /** The cycle this world's matches run with (the saved rule). */
    public static DayCycle cycleOf(ServerLevel anyLevel) {
        return DayCycle.bySpeed(anyLevel.getServer().overworld().getGameRules().getInt(GameRuleRegistrar.MATCH_DAY_CYCLE));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        ServerLevel overworld = evt.getServer().overworld();
        if (overworld != null)
            step(overworld, overworld.getGameTime(), MatchState.live().phase() == MatchPhase.PLAYING);
    }

    /**
     * One game tick of the match sun at {@code gameTime}: moves the overworld's day time one tick on every
     * {@code matchDayCycle}-th game tick of a match being played. Returns whether it moved. Its own method (gameTime
     * passed in) so a GameTest can run an hour of it in one tick and put the clock back.
     */
    public static boolean step(ServerLevel overworld, long gameTime, boolean playing) {
        if (!playing)
            return false;
        GameRules rules = overworld.getGameRules();
        int speed = rules.getInt(GameRuleRegistrar.MATCH_DAY_CYCLE);
        if (speed <= 0 || rules.getBoolean(GameRules.RULE_DAYLIGHT) || gameTime % speed != 0)
            return false;
        overworld.setDayTime(overworld.getDayTime() + 1);
        broadcast(overworld);
        return true;
    }

    /** The overworld's clock to its players now, as vanilla's once-a-second sync sends it. */
    static void broadcast(ServerLevel overworld) {
        overworld.getServer().getPlayerList().broadcastAll(new ClientboundSetTimePacket(overworld.getGameTime(),
            overworld.getDayTime(), overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)), overworld.dimension());
    }
}
