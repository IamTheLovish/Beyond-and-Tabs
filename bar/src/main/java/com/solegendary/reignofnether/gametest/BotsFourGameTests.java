package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.bot.BotTerrain;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests of the bots-4 round (design/bot.md "Bots-4"): the whole game - beasts, game enders and their Ward Spire
 * counter, heroes, start positions on every pre-made map, and bots that start wherever they are seated (playtest Oct 12,
 * finding 1). Their own holder (same arena template as SkirmishGameTests, unique owners, no global counts); the heavy
 * ones - real bots playing for a minute or more on terrain far from the grid - share the batch {@link #SOAK}, so they
 * never run beside the default batch's block-stamping tests.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BotsFourGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** The heavy batch: real bots on real terrain, far from the test grid. */
    static final String SOAK = "bots4_soak";

    // ------------------------------------------------------------------ finding 1: every seated bot starts

    /** Seconds after seating by which every bot must have started (spent, or laid blocks of its capitol). */
    static final int START_SECONDS = 60;
    /** Watch Battle's "Far" spawn ring for four bots (SkirmishServerboundPacket.spawnMinR/MaxR plus 35 a bot). */
    static final int FAR_MIN = 300, FAR_MAX = 420 + 3 * 35;

    /**
     * Playtest Oct 12, finding 1: in lovish's 4-bot Watch Battle (Huge arena, Far spawns) three of four bots never
     * started - no workers seen, pools untouched for minutes. This seats four hard bots exactly the way that lobby did
     * (BotServerEvents.seatNear: the Far ring round a spectator's spot, every chunk there unloaded) on terrain ~3200
     * blocks from the test grid, at a centre chosen so that water lies where a blind search would put the bots, and
     * demands that each one stands on dry ground at the real ground height and has started within
     * {@link #START_SECONDS} - spent from its pool or laid blocks of its capitol. The failure message says, per bot,
     * where it was seated, what the ground there really is, and what became of its units and its capitol.
     */
    @GameTest(template = ARENA, timeoutTicks = START_SECONDS * 20 + 600, batch = SOAK)
    public static void bots_seated_far_apart_on_a_big_arena_all_start(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        Vec3 from = wateryCentre(level, origin.getX() - 3200, origin.getZ());
        Faction[] factions = { Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT };
        String[] names = new String[factions.length];
        Vec3[] seats = new Vec3[factions.length];
        float[] startMetal = new float[factions.length], startEnergy = new float[factions.length];
        for (int i = 0; i < factions.length; i++) {
            names[i] = "gametest_b4far_" + i + " (hard)";
            BotServerEvents.thinkFailures.remove(names[i]);
            seats[i] = BotServerEvents.seatNear(level, from, names[i], factions[i], BotPlayer.Difficulty.HARD, FAR_MIN, FAR_MAX, 0);
            Resources pool = poolOf(names[i]);
            startMetal[i] = pool == null ? -1 : pool.getMetal();
            startEnergy[i] = pool == null ? -1 : pool.getEnergy();
        }
        boolean[] started = new boolean[factions.length];
        long t0 = level.getGameTime();
        long[] startedAt = new long[factions.length];
        for (int t = 20; t <= START_SECONDS * 20; t += 20) {
            final int at = t;
            helper.runAfterDelay(t, new Runnable() {   // a fresh Runnable each: GameTestInfo keys its tasks by instance
                @Override
                public void run() {
                    for (int i = 0; i < names.length; i++)
                        if (!started[i] && hasStarted(names[i], startMetal[i], startEnergy[i])) {
                            started[i] = true;
                            startedAt[i] = level.getGameTime() - t0;
                        }
                    if (at < START_SECONDS * 20)
                        return;
                    List<String> problems = new ArrayList<>();
                    StringBuilder diag = new StringBuilder();
                    for (int i = 0; i < names.length; i++) {
                        diag.append(" | ").append(describe(level, names[i], seats[i], startMetal[i], startedAt[i]));
                        BlockPos seat = BlockPos.containing(seats[i]);
                        level.getChunk(seat.getX() >> 4, seat.getZ() >> 4);   // (held by the bot's start anyway)
                        BotTerrain.Column real = BotTerrain.probe(level, seat.getX(), seat.getZ());
                        if (!real.dryGround())
                            problems.add(names[i] + " was seated on " + (real.noGround() ? "no ground" : "water/lava "
                                + (real.surface() - real.floor()) + " deep"));
                        else if (Math.abs(seat.getY() - real.floor()) > 2)
                            problems.add(names[i] + " was seated at y=" + seat.getY() + " over ground at y=" + real.floor());
                        if (!started[i])
                            problems.add(names[i] + " did not start in " + START_SECONDS + " s");
                        Throwable err = BotServerEvents.thinkFailures.get(names[i]);
                        if (err != null)
                            problems.add(names[i] + " think threw " + err);
                    }
                    cleanUp(names);
                    if (problems.isEmpty())
                        helper.succeed();
                    else
                        helper.fail(String.join("; ", problems) + " || centre " + BlockPos.containing(from).toShortString() + diag);
                }
            });
        }
    }

    /**
     * A centre near (x, z) whose Far ring has water where the old blind search seated its first bots (the ring's
     * first four picks: 0, 60, 120 and 180 degrees at the inner radius) but enough dry land elsewhere on the ring for a
     * search that can see. Read through BotTerrain (the chunk generator's base terrain): nothing is loaded. Falls back
     * to (x, z) when no such centre is near - the test then still checks that four bots start wherever they land.
     */
    static Vec3 wateryCentre(ServerLevel level, int x, int z) {
        Vec3 best = null;
        int bestWet = 0;
        for (int step = 0; step < 24 && bestWet < 3; step++) {
            int cx = x - (step % 6) * 400, cz = z + (step / 6 - 2) * 400;
            int wetPicks = 0;
            for (int k = 0; k <= 12; k += 4) {
                double a = Math.PI * 2 * k / 24;
                if (BotTerrain.probe(level, cx + (int) (Math.cos(a) * FAR_MIN), cz + (int) (Math.sin(a) * FAR_MIN)).wet())
                    wetPicks++;
            }
            if (wetPicks <= bestWet)
                continue;
            int dry = 0;
            for (int k = 0; k < 24; k++) {
                double a = Math.PI * 2 * k / 24;
                for (int r = FAR_MIN; r <= FAR_MAX; r += 80)
                    if (BotTerrain.probe(level, cx + (int) (Math.cos(a) * r), cz + (int) (Math.sin(a) * r)).dryGround())
                        dry++;
            }
            if (dry < 24)
                continue;   // mostly sea: no fair test of a search that can see
            bestWet = wetPicks;
            best = new Vec3(cx + 0.5, 64, cz + 0.5);
        }
        return best != null ? best : new Vec3(x + 0.5, 64, z + 0.5);
    }

    static Resources poolOf(String owner) {
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                return r;
        return null;
    }

    static BuildingPlacement capitolOf(String owner) {
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
            if (owner.equals(bp.ownerName) && !bp.isDestroyedServerside && bp.getBuilding().isCapitol)
                return bp;
        return null;
    }

    static List<LivingEntity> unitsOf(String owner) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof Unit u && owner.equals(u.getOwnerName()) && le.isAlive())
                out.add(le);
        return out;
    }

    /** Started: the pool moved (a worker trained, a block paid for, income) or the capitol has blocks up. */
    static boolean hasStarted(String owner, float metal0, float energy0) {
        Resources pool = poolOf(owner);
        if (pool != null && (Math.abs(pool.getMetal() - metal0) > 0.5f || Math.abs(pool.getEnergy() - energy0) > 0.5f))
            return true;
        BuildingPlacement cap = capitolOf(owner);
        return cap != null && (cap.isBuilt || cap.getBlocksPlaced() > 0);
    }

    /** One bot's story for a failure message: its seat, the real ground there, its units, its capitol, its pool. */
    static String describe(ServerLevel level, String owner, Vec3 seat, float metal0, long startedAt) {
        BlockPos s = BlockPos.containing(seat);
        BotTerrain.Column real = BotTerrain.probe(level, s.getX(), s.getZ());
        List<LivingEntity> units = unitsOf(owner);
        int workers = 0, inWater = 0;
        for (LivingEntity le : units) {
            if (le instanceof WorkerUnit)
                workers++;
            if (le.isInWater())
                inWater++;
        }
        BuildingPlacement cap = capitolOf(owner);
        Resources pool = poolOf(owner);
        BotPlayer brain = BotServerEvents.brains.get(owner);
        return owner.substring(9, owner.indexOf(' ')) + " seat " + s.toShortString() + " ground y" + real.floor()
            + (real.wet() ? " WET+" + (real.surface() - real.floor()) : "") + (real.noGround() ? " VOID" : "")
            + " units " + units.size() + " (workers " + workers + ", in water " + inWater + ")"
            + " capitol " + (cap == null ? "none" : (cap.isBuilt ? "built" : cap.getBlocksPlaced() + "/" + cap.getBlocksTotal())
                + " at " + cap.originPos.toShortString())
            + " metal " + (pool == null ? "?" : String.format("%.0f->%.0f", metal0, pool.getMetal()))
            + (startedAt > 0 ? " started t" + startedAt : " NOT STARTED")
            + (brain == null ? "" : " home " + brain.home().toShortString() + " placeFailures " + brain.placeFailures
                + " lastFailed " + (brain.lastFailed == null ? "-" : brain.lastFailed.structureName));
    }

    /** Leave the match, forget the brains, drop the units (buildings stay, ownerless and far away). */
    static void cleanUp(String[] names) {
        List<LivingEntity> leftovers = new ArrayList<>();
        for (String n : names)
            leftovers.addAll(unitsOf(n));
        for (String n : names) {
            PlayerServerEvents.defeat(n, "gametest finished");
            BotServerEvents.brains.remove(n);
            BotServerEvents.thinkFailures.remove(n);
        }
        leftovers.forEach(Entity::discard);
    }
}
