package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.unit.goals.BuildRepairGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bot's watch on its own build sites (design/bot.md): a site that has not risen a block for {@link #STUCK_TICKS}
 * while a worker was free for it is stuck - its workers could not reach it, gave up on it (BuildRepairGoal's give-up),
 * or its blocks will not stay placed - and the bot would otherwise wait on it for the whole match. The Oct 13 CI soak
 * (run 38342219886, attempts 1 and 2) had a Tidewrought bot do exactly that: its capitol and two extractors sat
 * unbuilt for four minutes with both pools full, those three sites filled its site allowance (maxConcurrentSites),
 * so it never tried another placement, never trained a worker and - with no failed placement to count - the soak's
 * diagnostics read "placeFailures 0". The watch:
 * <ul>
 *   <li>reports the site through {@link BotPlayer#blocked} (one rate-limited "[Bot] ... blocked on ..." line, and the
 *       reason on the soak summary) with what it can see: builders in range, the nearest worker's distance and
 *       order, blocks placed, the paid progress;</li>
 *   <li>cancels a stuck site for its refund (BuildingServerEvents.cancelBuilding, exactly a player's cancel), so it
 *       stops counting toward the allowance and the think step moves on; an extractor's patch is skipped for a while
 *       ({@link BotPlayer#badPatches});</li>
 *   <li>re-lays a cancelled capitol a step further out each time ({@link #capitolWant}), up to
 *       {@link #MAX_CAPITOL_RELOCATIONS} times, so a bot can start from any valid start spot even when the ground
 *       right at its seat refuses to be built on; after that it keeps the site and only keeps reporting.</li>
 * </ul>
 * A site nobody is free to build (every worker busy on another site) is waiting, not stuck: its clock is held. A bot
 * with an empty pool is starved, not stuck: the site is reported as unaffordable and kept. Cheap: one pass over the
 * unbuilt sites per think with one block-count read each.
 */
public class BotSiteWatch {

    /** No progress for this long, with a worker free for the site, and it is stuck. */
    public static final long STUCK_TICKS = 20 * 45;
    /** The capitol is re-laid at most this often; each time a step further from the seat, round it. */
    public static final int MAX_CAPITOL_RELOCATIONS = 3;
    public static final int RELOCATE_STEP = 14;
    /** Under this fill of either pool a stalled site is the economy's fault, not the site's. */
    static final float STARVED_FILL = 0.05f;

    static final class Watch {
        long since;
        float progress;
    }

    final BotPlayer bot;
    final Map<BuildingPlacement, Watch> sites = new IdentityHashMap<>();
    /** How often the capitol has been cancelled and re-laid (public for the game test). */
    public int capitolRelocations = 0;
    /** Sites cancelled so far (public for the game test). */
    public int cancelled = 0;
    /** How a stuck site is dropped: the player's cancel (refund, site gone). The game test swaps in a recorder. */
    public java.util.function.Consumer<BuildingPlacement> canceller;
    /**
     * Whether a worker with no build target is free to take a site: truly idle (Unit.isIdle). The game test swaps in
     * "always" because its worker is created but never added to the world, where isIdle cannot read its goals.
     */
    public java.util.function.Predicate<LivingEntity> idleCheck = le -> le instanceof Unit u && u.isIdle();

    /** Game tests: a worker that was never added to the world counts as idle. */
    public void treatEveryWorkerAsIdle() {
        idleCheck = le -> true;
    }

    BotSiteWatch(BotPlayer bot) {
        this.bot = bot;
        this.canceller = bp -> BuildingServerEvents.cancelBuilding(bp, bot.name);
    }

    /** Where the next capitol is wanted: the seat itself, then a step further out round it per relocation. */
    public BlockPos capitolWant(BlockPos home) {
        if (capitolRelocations <= 0 || home == null)
            return home;
        double a = capitolRelocations * Math.PI * 2 / 3 + Math.PI / 6;
        int r = RELOCATE_STEP * capitolRelocations;
        return home.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r));
    }

    /** A site's progress: blocks in the world plus the paid (flow) progress, so a paid-but-unplaced block counts. */
    static float progressOf(BuildingPlacement bp) {
        return bp.getBlocksPlaced() + bp.buildProgress;
    }

    /**
     * One pass per think, after the builders were assigned. Returns true when it cancelled the capitol, so the think
     * step stops here and lays the next one on its next pass (the building list it holds is stale now).
     */
    public boolean think(ServerLevel level, long gameTime, List<BuildingPlacement> buildings, List<LivingEntity> workers) {
        sites.keySet().removeIf(bp -> bp.isBuilt || bp.isDestroyedServerside || !buildings.contains(bp));
        if (workers.isEmpty())
            return false;
        boolean cancelledCapitol = false;
        for (BuildingPlacement bp : new ArrayList<>(buildings)) {
            if (bp.isBuilt || bp.isDestroyedServerside || bp.decommissioning)
                continue;
            Watch w = sites.get(bp);
            float progress = progressOf(bp);
            if (w == null) {
                w = new Watch();
                w.since = gameTime;
                w.progress = progress;
                sites.put(bp, w);
            } else if (progress > w.progress) {
                w.progress = progress;
                w.since = gameTime;
            }
            // a worker free for this site: on it already, or truly idle. "No build target" is NOT free: a worker
            // gathering or walking has none either, and counting it as free made the watch cancel sites nobody could
            // build yet (CI run 38478383984: the court bot's wind generator cancelled twice at 45 s while its only
            // workers raised the heartwood hall 14 blocks away - 6 buildings, no units, metal full for 4 minutes)
            boolean free = false;
            for (LivingEntity le : workers) {
                BuildingPlacement target = targetOf(le);
                if (target == bp || (target == null && idleCheck.test(le))) {
                    free = true;
                    break;
                }
            }
            if (!free) {
                w.since = gameTime;
                continue;
            }
            long stalled = gameTime - w.since;
            if (stalled < STUCK_TICKS)
                continue;
            String step = bp.getBuilding().structureName;
            String seen = describe(bp, workers);
            if (bot.metalFill < STARVED_FILL || bot.energyFill < STARVED_FILL) {
                bot.blocked(gameTime, step, "unaffordable for " + stalled / 20 + " s (metal " + fmt(bot.metalFill)
                    + " energy " + fmt(bot.energyFill) + " of storage): " + seen);
                w.since = gameTime;
                continue;
            }
            if (bp.getBuilding().isCapitol) {
                if (capitolRelocations >= MAX_CAPITOL_RELOCATIONS) {
                    bot.blocked(gameTime, step, "no build progress for " + stalled / 20 + " s after " + capitolRelocations
                        + " relocations, keeping it: " + seen);
                    w.since = gameTime;
                    continue;
                }
                capitolRelocations++;
                bot.blocked(gameTime, step, "no build progress for " + stalled / 20 + " s at " + bp.originPos.toShortString()
                    + ", cancelled and re-laid a step away (" + capitolRelocations + "/" + MAX_CAPITOL_RELOCATIONS + "): " + seen);
                cancel(level, bp);
                cancelledCapitol = true;
                continue;
            }
            bot.blocked(gameTime, step, "no build progress for " + stalled / 20 + " s at " + bp.originPos.toShortString()
                + ", cancelled: " + seen);
            if (bp.getBuilding() == bot.kit.extractor())
                for (BlockPos patch : MetalPatches.getPatches(level))
                    if (patchUnderSite(patch.getX(), patch.getY(), patch.getZ(), bp.centrePos.getX(), bp.centrePos.getY(), bp.centrePos.getZ()))
                        bot.badPatches.put(patch, avoidUntil(gameTime));   // not this patch again for a while
            // not the same ground again either (the next think would re-lay a lab on the very spot its builders
            // could not reach, and the watch would cancel it again 45 s later, for the whole match)
            bot.siteCancelled(bp, gameTime);
            cancel(level, bp);
        }
        return cancelledCapitol;
    }

    /** Extra ticks a cancelled site's patch is avoided beyond the two minutes a failed placement waits (BotPlayer.freePatch). */
    static final long PATCH_AVOID_TICKS = 0;

    /** The time stamp a cancelled site's patch is recorded with in {@link BotPlayer#badPatches}. Pure. */
    static long avoidUntil(long gameTime) {
        return gameTime + PATCH_AVOID_TICKS;
    }

    /** Whether a metal patch is the one an extractor site (centred at c) was laid over. Pure, for the unit test. */
    static boolean patchUnderSite(int px, int py, int pz, int cx, int cy, int cz) {
        long dx = px - cx, dy = py - cy, dz = pz - cz;
        return dx * dx + dy * dy + dz * dz <= 4 * 4;
    }

    void cancel(ServerLevel level, BuildingPlacement bp) {
        sites.remove(bp);
        cancelled++;
        // the player's cancel: refunds what the flow economy spent, drops the site (its workers go idle)
        canceller.accept(bp);
    }

    static BuildingPlacement targetOf(LivingEntity le) {
        if (!(le instanceof WorkerUnit wu))
            return null;
        BuildRepairGoal goal = wu.getBuildRepairGoal();
        return goal == null ? null : goal.getBuildingTarget();
    }

    /** What the watch can see of a site: builders in range, blocks, paid progress, the nearest worker's distance and order. */
    static String describe(BuildingPlacement bp, List<LivingEntity> workers) {
        int onIt = 0, inRange = bp.getBuilders().size();
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity le : workers) {
            if (targetOf(le) == bp)
                onIt++;
            double d = le.distanceToSqr(bp.centrePos.getX() + 0.5, le.getY(), bp.centrePos.getZ() + 0.5);
            if (d < best) {
                best = d;
                nearest = le;
            }
        }
        String near = nearest == null ? "no worker" : String.format("nearest worker %.1f blocks away at %s, target %s, idle %s",
            Math.sqrt(best), nearest.blockPosition().toShortString(),
            targetOf(nearest) == null ? "none" : targetOf(nearest).getBuilding().structureName,
            nearest instanceof WorkerUnit wu && WorkerUnit.isIdle(wu));
        return "blocks " + bp.getBlocksPlaced() + "/" + bp.getBlocksTotal() + ", paid " + fmt(bp.buildProgress)
            + ", builders in range " + inRange + " of " + onIt + " ordered, " + near + "; " + bp.constructionState();
    }

    static String fmt(float f) {
        return String.format("%.2f", f);
    }
}
