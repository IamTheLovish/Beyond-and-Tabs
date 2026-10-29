package com.solegendary.reignofnether.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.match.MatchRoster;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.Engage;
import com.solegendary.reignofnether.unit.StuckNudge;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The nightly bot ladder (.github/workflows/bot-ladder.yml, design 4.3 "Bot ladder" and 4.6 "Pacing"): ONE full
 * bot-vs-bot match per server, chosen by the environment, measured and written out. Measurement only - no unit stat,
 * cost or bot behaviour is touched; the only code outside this class is a read-only hook in StuckNudge.
 * <p>
 * <b>Gated.</b> The test sits in its own batch ({@link #BATCH}) and does nothing unless {@link #MATCH_ENV} is set, so
 * the per-push shards pass it in a tick (shard 1 runs it like any unnamed test; bar-audit still sees every test run
 * once). Only the ladder workflow sets the variable and selects the batch with {@code BAT_GAMETEST_ONLY=bot_ladder}.
 * <ul>
 *   <li>{@code BAT_LADDER_MATCH=hard:villagers|medium:monsters} - side A and side B as {@code tier:faction}; the tier is
 *       a {@link BotPlayer.Difficulty} name (so a new Difficulty needs no change here) and the faction a /bot add name;</li>
 *   <li>{@code BAT_LADDER_MINUTES} (default 30: the first CI run, Oct 22, ran 6x faster than real time and every 15-minute match was still a draw) - the cap in game minutes; a match still running is a draw;</li>
 *   <li>{@code BAT_LADDER_WALL_MINUTES} (default 26) - the cap in real minutes, so a slow runner ends a match as a draw
 *       with the army values at that moment rather than hitting the job's watchdog and losing the result;</li>
 *   <li>{@code BAT_LADDER_ID} / {@code BAT_LADDER_OUT} - the result file's name and folder (default
 *       {@code ladder/<id>.json} in the server's working directory).</li>
 * </ul>
 * <b>Speed.</b> The headless game-test server ticks as fast as the runner allows (no 50 ms wait), so a match runs
 * faster than the clock without any speed knob (1.20 has no /tick rate); the achieved factor is in every [Sim] line.
 * <p>
 * <b>Setup.</b> Two dry bases 160 blocks apart ~1500 blocks out, found like the bot soak's (SkirmishGameTests), started
 * through the real bot start path. A side has lost when it leaves the match roster (commander lost, everything lost);
 * the other is the winner. Both gone in one second, or neither by the cap, is a draw.
 * <p>
 * <b>Output.</b> One {@code [Sim]} line, one {@code [Pace]} line and up to {@link #MAX_STUCK_LINES} {@code [Stuck]}
 * lines in the log (the workflow turns them into annotations) and the whole result as JSON for the aggregate job
 * (tools/ladder_report.py).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class LadderGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    public static final String BATCH = "bot_ladder";
    public static final String MATCH_ENV = "BAT_LADDER_MATCH";
    static final String MINUTES_ENV = "BAT_LADDER_MINUTES";
    static final String WALL_ENV = "BAT_LADDER_WALL_MINUTES";
    static final String ID_ENV = "BAT_LADDER_ID";
    static final String OUT_ENV = "BAT_LADDER_OUT";

    static final int DEFAULT_MINUTES = 30;
    static final int DEFAULT_WALL_MINUTES = 26;
    /** The longest cap the test's own timeout allows (BAT_LADDER_MINUTES above this is clamped). */
    static final int MAX_MINUTES = 40;
    /** Stuck spots listed per match (the same unit stuck on the same spot is one spot with a count). */
    static final int MAX_STUCK_LINES = 12;
    /** Units first seen in this many ticks after the start are the base's own, not production. */
    static final int START_GRACE = 60;
    /** A side that has produced nothing for this long (after its first minutes) is logged as stalled. */
    static final long STALL_TICKS = 20 * 60 * 3;

    /** A side of a ladder match: the tier, the faction's name as typed and the faction itself. */
    record Side(BotPlayer.Difficulty tier, String factionName, Faction faction) {
        String label() {
            return tier.name().toLowerCase(Locale.ROOT) + "(" + factionName + ")";
        }
    }

    /** "hard:villagers" -> Side; null when the tier or the faction (or its bot kit) is unknown. */
    static Side parseSide(String text) {
        String[] p = text.trim().split(":");
        if (p.length != 2)
            return null;
        BotPlayer.Difficulty tier;
        try {
            tier = BotPlayer.Difficulty.valueOf(p[0].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String name = p[1].trim().toLowerCase(Locale.ROOT);
        Faction f = switch (name) {
            case "villagers" -> Factions.VILLAGERS;
            case "monsters" -> Factions.MONSTERS;
            case "piglins" -> Factions.PIGLINS;
            case "verdant_court" -> Factions.VERDANT_COURT;
            case "tidewrought" -> Factions.TIDEWROUGHT;
            case "stormcallers" -> Factions.STORMCALLERS;
            case "brassworks" -> Factions.BRASSWORKS;
            default -> null;
        };
        return f == null || BotPlayer.kitFor(f) == null ? null : new Side(tier, name, f);
    }

    @GameTest(template = ARENA, timeoutTicks = 20 * 60 * MAX_MINUTES + 2400, batch = BATCH)
    public static void ladder_match(GameTestHelper helper) {
        String spec = System.getenv(MATCH_ENV);
        if (spec == null || spec.isBlank()) {
            helper.succeed();   // every per-push run: the ladder is the nightly workflow's, not a unit test
            return;
        }
        String[] parts = spec.split("\\|");
        Side a = parts.length == 2 ? parseSide(parts[0]) : null, b = parts.length == 2 ? parseSide(parts[1]) : null;
        if (a == null || b == null) {
            helper.fail(MATCH_ENV + "=" + spec + " is not 'tier:faction|tier:faction' with a known tier and a bot faction");
            return;
        }
        new Match(helper, a, b).start();
    }

    private static int envInt(String key, int fallback) {
        try {
            String v = System.getenv(key);
            return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** One running match: setup, a sample per game second, and the verdict. */
    private static final class Match {
        final GameTestHelper helper;
        final ServerLevel level;
        final Side[] sides;
        final String[] names = { "gametest_ladder_a", "gametest_ladder_b" };
        final String id;
        final long capTicks;
        final long wallCapMs;
        long wallStart = System.nanoTime();   // reset when the bots are seated: the speed figure leaves the chunk-loading setup out
        final LadderStats stats = new LadderStats();
        final Map<UUID, Long>[] born;
        final Map<UUID, Boolean>[] fighterOf;
        /** First enemy hit each unit took (game tick), for time to kill. */
        final Map<UUID, Long>[] firstHit;
        /** Last seen cooldown of each ability of each unit: a rise is a cast (sampled once a second). */
        final Map<UUID, Map<com.solegendary.reignofnether.ability.Ability, Float>> cooldowns = new HashMap<>();
        final long[] lastProducedAt = { 0, 0 };
        final boolean[] stalled = { false, false };
        final List<String> stalls = new ArrayList<>();
        /** Stuck spots: side, unit type and a 4-block cell -> {events, first tick, last tick, highest stage}, with the last approach. */
        final Map<String, long[]> stuckSpots = new java.util.LinkedHashMap<>();
        final Map<String, String> stuckApproach = new HashMap<>();
        final int[] stuckCount = { 0, 0 }, giveUps = { 0, 0 };
        final List<String> errors = new ArrayList<>();
        final int[] lastArmy = { 0, 0 }, lastUnits = { 0, 0 }, lastBuildings = { 0, 0 };
        final float[] lastMetal = { 0, 0 }, lastDamage = { 0, 0 };
        long t0;
        boolean done;
        Consumer<LivingDeathEvent> onDeath;
        Consumer<LivingDamageEvent> onDamage;

        @SuppressWarnings("unchecked")
        Match(GameTestHelper helper, Side a, Side b) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.sides = new Side[] { a, b };
            String envId = System.getenv(ID_ENV);
            this.id = envId != null && !envId.isBlank() ? envId.trim()
                : a.label().replace("(", "-").replace(")", "") + "_v_" + b.label().replace("(", "-").replace(")", "");
            this.capTicks = 20L * 60 * Math.max(1, Math.min(MAX_MINUTES, envInt(MINUTES_ENV, DEFAULT_MINUTES)));
            this.wallCapMs = 60_000L * Math.max(1, envInt(WALL_ENV, DEFAULT_WALL_MINUTES));
            this.born = new Map[] { new HashMap<UUID, Long>(), new HashMap<UUID, Long>() };
            this.fighterOf = new Map[] { new HashMap<UUID, Boolean>(), new HashMap<UUID, Boolean>() };
            this.firstHit = new Map[] { new HashMap<UUID, Long>(), new HashMap<UUID, Long>() };
        }

        String title() {
            return "ladder " + sides[0].label() + " vs " + sides[1].label();
        }

        int sideOf(String owner) {
            return names[0].equals(owner) ? 0 : names[1].equals(owner) ? 1 : -1;
        }

        void start() {
            BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
            // two dry bases 160 blocks apart, found the way the bot soak finds its row (a random-ish coast can drown a
            // line, so every line and 16 steps along it are tried, then the same further out: soakAnyRow)
            Vec3[] seats = SkirmishGameTests.soakAnyRow(level, origin.getX() + 1500, origin.getZ(), 2);
            if (seats == null) {
                writeResult(failedResult("no dry ground for two bases near x=" + (origin.getX() + 1500)));
                helper.fail("no dry ground for two bot bases near x=" + (origin.getX() + 1500));
                return;
            }
            var brains = BotServerEvents.brains;
            for (int i = 0; i < 2; i++) {
                BotServerEvents.thinkFailures.remove(names[i]);
                PlayerServerEvents.startRTSBot(level, names[i], seats[i], sides[i].faction(), 0);
                brains.put(names[i], new BotPlayer(names[i], sides[i].faction(), sides[i].tier(), BlockPos.containing(seats[i])));
            }
            for (String n : names)
                if (!PlayerServerEvents.isRTSPlayer(n)) {
                    writeResult(failedResult("startRTSBot did not add " + n + " to the match"));
                    helper.fail("startRTSBot did not add " + n + " to the match");
                    cleanup();
                    return;
                }
            t0 = level.getGameTime();
            wallStart = System.nanoTime();
            ReignOfNether.LOGGER.info("[Ladder] {} starts: {} at {} vs {} at {}, seed {}, cap {} game minutes / {} real minutes",
                title(), names[0], BlockPos.containing(seats[0]).toShortString(), names[1],
                BlockPos.containing(seats[1]).toShortString(), level.getSeed(), capTicks / 1200, wallCapMs / 60_000);

            onDeath = evt -> {
                if (done || evt.getEntity().level() != level || !(evt.getEntity() instanceof Unit u))
                    return;
                int side = sideOf(u.getOwnerName());
                if (side < 0)
                    return;
                LivingEntity le = evt.getEntity();
                var src = evt.getSource();
                boolean fighter = le instanceof AttackerUnit && !(le instanceof WorkerUnit);
                int value = u.getCost() == null ? 0 : (int) u.getCost().metal();
                Boolean knownFighter = fighterOf[side].get(le.getUUID());
                Long bornAt = born[side].get(le.getUUID());
                Long hitAt = firstHit[side].get(le.getUUID());
                // killed by the other side's unit or anything that is not the world (towers, abilities, projectiles of
                // theirs); not by its own side (friendly fire) and not by a fall, the water or fire
                boolean byEnemy = true;
                if (src.getEntity() instanceof Unit killer && side == sideOf(killer.getOwnerName()))
                    byEnemy = false;
                if (src.getEntity() == null && ENVIRONMENT.contains(src.getMsgId()))
                    byEnemy = false;
                stats.death(new LadderStats.Death(level.getGameTime() - t0, side, value,
                    knownFighter != null ? knownFighter : fighter, byEnemy, bornAt == null ? 0 : bornAt, hitAt == null ? -1 : hitAt));
            };
            onDamage = evt -> {
                if (done || evt.getEntity().level() != level || !(evt.getEntity() instanceof Unit victim)
                        || !(evt.getSource().getEntity() instanceof Unit hitter))
                    return;
                int v = sideOf(victim.getOwnerName()), h = sideOf(hitter.getOwnerName());
                if (v >= 0 && h >= 0 && v != h && evt.getAmount() > 0) {
                    stats.fighting(level.getGameTime() - t0);
                    firstHit[v].putIfAbsent(evt.getEntity().getUUID(), level.getGameTime() - t0);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDeathEvent.class, onDeath);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, onDamage);
            StuckNudge.observer = (mob, unit, stage) -> {
                int side = sideOf(unit.getOwnerName());
                if (side < 0 || done)
                    return;
                stuckCount[side]++;
                if (stage == StuckNudge.STAGE_GIVE_UP)
                    giveUps[side]++;
                BlockPos at = mob.blockPosition();
                String key = sides[side].label() + " " + EntityType.getKey(mob.getType()).getPath() + " near "
                    + (at.getX() >> 2 << 2) + ", " + at.getY() + ", " + (at.getZ() >> 2 << 2);
                long tick = level.getGameTime() - t0;
                long[] spot = stuckSpots.computeIfAbsent(key, k -> new long[] { 0, tick, tick, 0 });
                spot[0]++;
                spot[2] = tick;
                spot[3] = Math.max(spot[3], stage);
                stuckApproach.put(key, Engage.lastApproach(mob));
            };
            // a fresh Runnable per sample: GameTestInfo keys its delayed tasks by the Runnable itself (SkirmishGameTests soak)
            for (long t = 20; t <= capTicks; t += 20)
                helper.runAfterDelay(t, new Runnable() {
                    @Override
                    public void run() {
                        sample();
                    }
                });
        }

        static final Set<String> ENVIRONMENT = Set.of("fall", "drown", "lava", "inFire", "onFire", "inWall", "outOfWorld",
            "cramming", "freeze", "starve", "fallingBlock", "hotFloor", "cactus", "sweetBerryBush", "stalagmite",
            "lightningBolt", "dryout", "fellOutOfWorld");

        void sample() {
            if (done)
                return;
            long now = level.getGameTime() - t0;
            int[] army = new int[2], units = new int[2], fighters = new int[2];
            for (int s = 0; s < 2; s++) {
                for (LivingEntity le : SkirmishGameTests.soakUnits(names[s])) {
                    if (!(le instanceof Unit u))
                        continue;
                    boolean fighter = le instanceof AttackerUnit && !(le instanceof WorkerUnit);
                    units[s]++;
                    if (fighter)
                        fighters[s]++;
                    countCasts(s, le, u, fighter);
                    if (fighter && u.getCost() != null)
                        army[s] += (int) u.getCost().metal();
                    if (!born[s].containsKey(le.getUUID())) {
                        born[s].put(le.getUUID(), now);
                        fighterOf[s].put(le.getUUID(), fighter);
                        if (fighter)
                            stats.fighterSeen(s);
                        if (now > START_GRACE) {
                            stats.produced(s, fighter);
                            lastProducedAt[s] = now;
                        }
                    }
                }
                lastBuildings[s] = SkirmishGameTests.soakBuildings(names[s]).size();
                RTSPlayer p = rtsPlayer(names[s]);
                if (p != null) {
                    lastMetal[s] = p.metalProduced;
                    lastDamage[s] = p.damageDealt;
                }
                var think = BotServerEvents.thinkFailures.get(names[s]);
                if (think != null && errors.size() < 5) {
                    String msg = sides[s].label() + " think threw " + think;
                    if (!errors.contains(msg))
                        errors.add(msg);
                }
                if (!stalled[s] && now > STALL_TICKS + 20 * 60 * 2 && now - lastProducedAt[s] >= STALL_TICKS) {
                    stalled[s] = true;
                    var brain = BotServerEvents.brains.get(names[s]);
                    String why = sides[s].label() + " produced nothing for " + (now - lastProducedAt[s]) / 1200 + " min from "
                        + LadderStats.clock(now) + (brain == null ? "" : " (army state " + brain.army.state + ", blocked "
                        + brain.lastBlockedReason + ")");
                    stalls.add(why);
                    ReignOfNether.LOGGER.warn("[Ladder] stall: {}", why);
                }
            }
            stats.sample(now, army, units, fighters);
            lastArmy[0] = army[0];
            lastArmy[1] = army[1];
            lastUnits[0] = units[0];
            lastUnits[1] = units[1];
            if (now % 1200 == 0)
                ReignOfNether.LOGGER.info("[Ladder] {} at {}: army {} vs {}, units {} vs {}, buildings {} vs {}, bots {} / {}, "
                    + "real {} s", title(), LadderStats.clock(now), army[0], army[1], units[0], units[1], lastBuildings[0],
                    lastBuildings[1], armyState(0), armyState(1), (System.nanoTime() - wallStart) / 1_000_000_000L);
            boolean in0 = MatchRoster.isInPlay(names[0]), in1 = MatchRoster.isInPlay(names[1]);
            if (!in0 || !in1) {
                finish(in0 ? 0 : in1 ? 1 : -1, "victory", now);
            } else if (now >= capTicks) {
                finish(-1, "timeout", now);
            } else if ((System.nanoTime() - wallStart) / 1_000_000L > wallCapMs) {
                finish(-1, "wall-timeout", now);
            }
        }

        /**
         * Ability casts, seen from outside: a cooldown that rose since the last second is a cast (nothing in the abilities
         * is touched). A unit's first sighting only records its cooldowns, so a unit that spawns on cooldown is not a cast.
         */
        void countCasts(int s, LivingEntity le, Unit u, boolean fighter) {
            var seen = cooldowns.get(le.getUUID());
            boolean first = seen == null;
            if (first) {
                seen = new HashMap<>();
                cooldowns.put(le.getUUID(), seen);
            }
            for (var ability : u.getAbilities().get()) {
                float cd = u.getCooldown(ability);
                Float before = seen.put(ability, cd);
                if (!first && fighter && before != null && ability.cooldownMax > 0 && cd > before + 0.01f)
                    stats.cast(s);
            }
        }

        String armyState(int s) {
            var brain = BotServerEvents.brains.get(names[s]);
            return brain == null ? "?" : brain.army.state.name();
        }

        static RTSPlayer rtsPlayer(String name) {
            RTSPlayer p = MatchRoster.get(name);
            if (p != null)
                return p;
            for (RTSPlayer f : MatchRoster.FINISHED)
                if (f != null && name.equals(f.name))
                    return f;
            return null;
        }

        /** winner: 0 or 1, or -1 for a draw. */
        void finish(int winner, String reason, long now) {
            if (done)
                return;
            done = true;
            stats.end(now);
            JsonObject result = result(winner, reason, now);
            logLines(winner, reason, now, result);
            writeResult(result);
            cleanup();
            helper.succeed();
        }

        void cleanup() {
            done = true;
            StuckNudge.observer = null;
            if (onDeath != null)
                MinecraftForge.EVENT_BUS.unregister(onDeath);
            if (onDamage != null)
                MinecraftForge.EVENT_BUS.unregister(onDamage);
            List<LivingEntity> leftovers = new ArrayList<>();
            for (String n : names)
                leftovers.addAll(SkirmishGameTests.soakUnits(n));
            for (String n : names) {
                PlayerServerEvents.defeat(n, "gametest finished");
                BotServerEvents.brains.remove(n);
                BotServerEvents.thinkFailures.remove(n);
            }
            leftovers.forEach(net.minecraft.world.entity.Entity::discard);
        }

        // ------------------------------------------------------------------------------------------ output

        JsonObject failedResult(String why) {
            JsonObject o = header();
            o.addProperty("outcome", "error");
            o.addProperty("reason", why);
            return o;
        }

        JsonObject header() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.add("a", sideJson(0));
            o.add("b", sideJson(1));
            o.addProperty("seed", level.getSeed());
            return o;
        }

        JsonObject sideJson(int s) {
            JsonObject o = new JsonObject();
            o.addProperty("tier", sides[s].tier().name().toLowerCase(Locale.ROOT));
            o.addProperty("faction", sides[s].factionName());
            o.addProperty("label", sides[s].label());
            return o;
        }

        static JsonArray pair(double a, double b) {
            JsonArray arr = new JsonArray();
            arr.add(Math.round(a * 100) / 100.0);
            arr.add(Math.round(b * 100) / 100.0);
            return arr;
        }

        JsonObject result(int winner, String reason, long now) {
            double wallSeconds = (System.nanoTime() - wallStart) / 1e9;
            JsonObject o = header();
            o.addProperty("outcome", winner == 0 ? "a" : winner == 1 ? "b" : "draw");
            o.addProperty("reason", winner >= 0 ? "victory" : (reason.equals("victory") ? "both-fell" : reason));
            o.addProperty("ticks", now);
            o.addProperty("clock", LadderStats.clock(now));
            o.addProperty("capTicks", capTicks);
            o.addProperty("wallSeconds", Math.round(wallSeconds));
            o.addProperty("simSpeed", wallSeconds <= 0 ? 0 : Math.round(now / 20.0 / wallSeconds * 100) / 100.0);
            o.add("peakArmy", pair(stats.peakArmy(0), stats.peakArmy(1)));
            o.add("lost", pair(stats.lostValue(0), stats.lostValue(1)));
            o.add("armyEnd", pair(lastArmy[0], lastArmy[1]));
            o.add("unitsEnd", pair(lastUnits[0], lastUnits[1]));
            o.add("buildingsEnd", pair(lastBuildings[0], lastBuildings[1]));
            o.add("metalProduced", pair(lastMetal[0], lastMetal[1]));
            o.add("damageDealt", pair(lastDamage[0], lastDamage[1]));
            o.add("producedPerMin", pair(stats.producedPerMinute(0, false), stats.producedPerMinute(1, false)));
            o.add("fightersPerMin", pair(stats.producedPerMinute(0, true), stats.producedPerMinute(1, true)));
            o.add("timeToKillS", pair(stats.medianTimeToKill(0), stats.medianTimeToKill(1)));
            o.add("castsPerLife", pair(stats.castsPerLife(0), stats.castsPerLife(1)));
            o.add("casts", pair(stats.casts(0), stats.casts(1)));
            o.add("peakFighters", pair(stats.peakFighters(0), stats.peakFighters(1)));
            o.add("peakArmyAtS", pair(stats.peakArmyTick(0) / 20, stats.peakArmyTick(1) / 20));
            JsonObject armyAt = new JsonObject();
            for (int m : new int[] { 10, 20, 30 })
                armyAt.add(String.valueOf(m), pair(stats.armyAt(0, m), stats.armyAt(1, m)));
            o.add("armyAt", armyAt);
            o.add("medianCombatLifetimeS", pair(stats.medianCombatLifetime(0), stats.medianCombatLifetime(1)));
            o.addProperty("fightingShare", Math.round(stats.fightingShare() * 1000) / 1000.0);
            JsonArray fights = new JsonArray();
            for (LadderStats.Fight f : stats.decisiveFights()) {
                JsonObject fo = new JsonObject();
                fo.addProperty("tick", f.tick());
                fo.addProperty("clock", LadderStats.clock(f.tick()));
                fo.addProperty("loser", f.loser() == 0 ? "a" : "b");
                fo.addProperty("lost", f.lostValue());
                fo.addProperty("armyBefore", f.armyBefore());
                fights.add(fo);
            }
            o.add("decisiveFights", fights);
            JsonArray gaps = new JsonArray();
            stats.fightGapsSeconds().forEach(gaps::add);
            o.add("fightGapsS", gaps);
            JsonObject alive = new JsonObject();
            for (int m : new int[] { 10, 20, 30 })
                alive.add(String.valueOf(m), pair(stats.unitsAliveAt(0, m), stats.unitsAliveAt(1, m)));
            o.add("aliveAt", alive);
            JsonObject stuck = new JsonObject();
            stuck.add("count", pair(stuckCount[0], stuckCount[1]));
            stuck.add("giveUps", pair(giveUps[0], giveUps[1]));
            JsonArray lines = new JsonArray();
            stuckLines().forEach(lines::add);
            stuck.add("events", lines);
            stuck.addProperty("spots", stuckSpots.size());
            o.add("stuck", stuck);
            JsonArray st = new JsonArray();
            stalls.forEach(st::add);
            o.add("stalls", st);
            JsonArray er = new JsonArray();
            errors.forEach(er::add);
            o.add("errors", er);
            JsonArray bots = new JsonArray();
            bots.add(armyState(0));
            bots.add(armyState(1));
            o.add("botState", bots);
            return o;
        }

        void logLines(int winner, String reason, long now, JsonObject r) {
            String verdict = winner >= 0 ? (winner == 0 ? "A " : "B ") + "win in " + LadderStats.clock(now)
                : (reason.equals("victory") ? "draw, both fell at " + LadderStats.clock(now)
                    : "draw, timeout at " + LadderStats.clock(now) + (reason.equals("wall-timeout") ? " (real-time cap)" : "")
                    + ", army " + lastArmy[0] + " vs " + lastArmy[1]);
            ReignOfNether.LOGGER.info("[Sim] {}: {}, peak army {}/{}, lost {}/{}, buildings {}/{}, sim {}x, seed {} (A = {}, B = {})",
                title(), verdict, stats.peakArmy(0), stats.peakArmy(1), stats.lostValue(0), stats.lostValue(1),
                lastBuildings[0], lastBuildings[1], r.get("simSpeed").getAsString(), level.getSeed(), sides[0].label(), sides[1].label());
            StringBuilder fights = new StringBuilder();
            for (LadderStats.Fight f : stats.decisiveFights())
                fights.append(fights.length() == 0 ? "" : " ").append(LadderStats.clock(f.tick())).append(f.loser() == 0 ? "A" : "B");
            StringBuilder gaps = new StringBuilder();
            for (long g : stats.fightGapsSeconds())
                gaps.append(gaps.length() == 0 ? "" : " ").append(LadderStats.clock(g * 20));
            ReignOfNether.LOGGER.info("[Pace] {}: time to kill {}/{} s, combat lifetime {}/{} s, casts per unit-life {}/{}, peak army {}/{} at {}/{} "
                    + "({}/{} fighters), army value at min 10 {}, min 20 {}, min 30 {}; produced/min {}/{} (fighters {}/{}), fighting {}% of the match, "
                    + "{} decisive fights [{}] gaps [{}], units alive at min 10 {}, min 20 {}, min 30 {} (A/B, -1 = match over)",
                title(), fmt(stats.medianTimeToKill(0)), fmt(stats.medianTimeToKill(1)),
                fmt(stats.medianCombatLifetime(0)), fmt(stats.medianCombatLifetime(1)),
                fmt(stats.castsPerLife(0)), fmt(stats.castsPerLife(1)), stats.peakArmy(0), stats.peakArmy(1),
                LadderStats.clock(stats.peakArmyTick(0)), LadderStats.clock(stats.peakArmyTick(1)),
                stats.peakFighters(0), stats.peakFighters(1), armyAtText(10), armyAtText(20), armyAtText(30),
                fmt(stats.producedPerMinute(0, false)), fmt(stats.producedPerMinute(1, false)),
                fmt(stats.producedPerMinute(0, true)), fmt(stats.producedPerMinute(1, true)),
                Math.round(stats.fightingShare() * 100), stats.decisiveFights().size(), fights, gaps,
                alive(10), alive(20), alive(30));
            for (String line : stuckLines())
                ReignOfNether.LOGGER.info("[Stuck] {}: {}", title(), line);
            ReignOfNether.LOGGER.info("[Stuck] {}: {} stuck events on {} spots in total (A {}, B {}), {} give-ups (A {}, B {})", title(),
                stuckCount[0] + stuckCount[1], stuckSpots.size(), stuckCount[0], stuckCount[1], giveUps[0] + giveUps[1], giveUps[0], giveUps[1]);
            for (String s : stalls)
                ReignOfNether.LOGGER.info("[Sim] {}: stall - {}", title(), s);
            for (String e : errors)
                ReignOfNether.LOGGER.info("[Sim] {}: error - {}", title(), e);
        }

        /** The most repeated stuck spots, one line each: who, where, how often, first and last time, worst stage. */
        List<String> stuckLines() {
            List<Map.Entry<String, long[]>> spots = new ArrayList<>(stuckSpots.entrySet());
            spots.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
            List<String> out = new ArrayList<>();
            for (var e : spots.subList(0, Math.min(MAX_STUCK_LINES, spots.size()))) {
                long[] v = e.getValue();
                out.add(String.format(Locale.ROOT, "%s x%d (first %s, last %s, worst stage %d, last approach %s)", e.getKey(), v[0],
                    LadderStats.clock(v[1]), LadderStats.clock(v[2]), v[3], stuckApproach.get(e.getKey())));
            }
            return out;
        }

        String armyAtText(int minute) {
            return stats.armyAt(0, minute) + "/" + stats.armyAt(1, minute);
        }

        String alive(int minute) {
            return stats.unitsAliveAt(0, minute) + "/" + stats.unitsAliveAt(1, minute);
        }

        static String fmt(double v) {
            return String.format(Locale.ROOT, "%.1f", v);
        }

        void writeResult(JsonObject result) {
            try {
                String dir = System.getenv(OUT_ENV);
                Path out = Path.of(dir == null || dir.isBlank() ? "ladder" : dir);
                Files.createDirectories(out);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(out.resolve(id + ".json"), gson.toJson(result), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                ReignOfNether.LOGGER.error("[Ladder] could not write the result of {}", id, e);
            }
        }
    }
}
