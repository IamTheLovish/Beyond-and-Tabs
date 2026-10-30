package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingLevels;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.monsters.Graveyard;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitUnstick;
import com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "stuck units in the graveyard" (lovish, Oct 22 20:23, a match on the dedicated server after PR #167 let levelled
 * graveyards produce again). A graveyard's units are raised INSIDE its walled yard (ProductionBuilding
 * .getIndoorSpawnPoint: the footprint's centre, walked down) and walk out through its one gate, two blocks wide, to the
 * rally point. Every test here raises a batch of Zombies through the real production path (the item's onComplete ->
 * ProductionPlacement.produceUnit -> setDelayedRally), as the graveyard's queue does, with the match pathfinder on,
 * and wants every one of them OUT of the graveyard's footprint and at its rally point within {@link #LIMIT} ticks:
 * <ul>
 *   <li>with another building placed flush across half of the gate (a one-block gap left), and flush against the
 *       whole gate side - both placements a player can make (the placement check is asked first: buildings may
 *       touch), as can be:</li>
 *   <li>a gate facing a two-block bank (placement checks only the footprint's own ground).</li>
 * </ul>
 * lovish's rule (design/LANES.md, Oct 20): "Interiors allowed (units never trapped)". A unit raised in a yard whose
 * only gate is closed off has no way out at all, so it must be raised where it can leave.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class GraveyardExitGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    // its own batch: it takes the rtsPathfinding flag (a real match runs the RTS pathfinder: ArenaPathing)
    static final String BATCH = "graveyard_exit";

    /** One graveyard's batch of Zombies, finished {@link #SPACING} ticks apart (a queue that was waiting on a level-up). */
    static final int UNITS = 5;
    static final int SPACING = 10;
    /** Between stamping the buildings and the first unit: the pathfinder's walkability of the new blocks settles. */
    static final int SETTLE = 20;
    /** Ticks after the last unit for every one to be out of the graveyard and at its rally point. */
    static final int LIMIT = 400;
    /**
     * Ticks the graveyard's chunks get to become entity-ticking before the first unit is raised. A unit made in a chunk
     * whose entities are not accessible yet is in the world but invisible to getEntitiesOfClass, which is how the test
     * finds what the graveyard made: the Overflowing graveyard, standing in the farthest chunk of the batch's row of
     * arenas, "raised" its first units into nothing (stress 40769583532: 2 of 8 shards, "unit 0 was not raised").
     */
    static final int READY_CAP = 300;
    static final int TIMEOUT = READY_CAP + SETTLE + UNITS * SPACING + LIMIT + 100;
    /** "At the rally point": five Zombies sent to one cell stand round it, the farthest a few blocks off. */
    static final double RALLY_NEAR = 6.0;

    /** OPEN is only used by the open-ground walk-out tests (see below); they call {@link #run} from their own class. */
    enum Gate { OPEN, HALF_BLOCKED, BLOCKED, BANK }

    // ------------------------------------------------------------------------------------- open ground, each level

    // The four open-ground walk-out tests (plain, Overflowing, L2, L3: run(helper, code, Gate.OPEN)) live in the PR that
    // fixes the group stall on main's move goal: they fail ~4 in 28 runner-runs on main without this PR's code
    // (stress 40824369634, 40824379303, 40824403225), so they cannot be a gate for this PR.

    // ------------------------------------------------------- a building flush across half the gate (one cell left)

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l1_units_get_out_past_a_building_across_half_the_gate(GameTestHelper helper) { run(helper, 0, Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void overflowing_graveyard_units_get_out_past_a_building_across_half_the_gate(GameTestHelper helper) { run(helper, 1, Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l2_units_get_out_past_a_building_across_half_the_gate(GameTestHelper helper) { run(helper, 2, Gate.HALF_BLOCKED); }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l3_units_get_out_past_a_building_across_half_the_gate(GameTestHelper helper) { run(helper, 3, Gate.HALF_BLOCKED); }

    // ------------------------------------------------------------------ a building flush against the whole gate side

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l1_units_get_out_with_a_building_against_the_gate(GameTestHelper helper) { run(helper, 0, Gate.BLOCKED); }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void overflowing_graveyard_units_get_out_with_a_building_against_the_gate(GameTestHelper helper) { run(helper, 1, Gate.BLOCKED); }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l2_units_get_out_with_a_building_against_the_gate(GameTestHelper helper) { run(helper, 2, Gate.BLOCKED); }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l3_units_get_out_with_a_building_against_the_gate(GameTestHelper helper) { run(helper, 3, Gate.BLOCKED); }

    // -------------------------------------------------------------------------------- the gate facing a steep bank

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void graveyard_l2_units_get_out_with_the_gate_facing_a_bank(GameTestHelper helper) { run(helper, 2, Gate.BANK); }

    // ------------------------------------------------------------------------------------------------- the run

    /**
     * {@code code}: 0 plain, 1 Overflowing Graveyard research, 2 / 3 building level (Graveyard.getUpgradeLevel).
     * Open ground: the graveyard stands in the middle of the arena and its units go to the default rally point (the
     * footprint's outside corner). Otherwise it stands against the arena's north edge with its gate (south, rotation
     * NONE) facing the obstacle, and the rally point is set on the open west side.
     */
    static void run(GameTestHelper helper, int code, Gate gate) {
        ServerLevel level = helper.getLevel();
        Runnable giveBackPathing = RtsPathingLease.takeUntilEnd(helper);
        String owner = "gametest_gyexit_" + code + "_" + gate.name().toLowerCase(Locale.ROOT) + "_"
            + helper.absolutePos(BlockPos.ZERO).asLong();
        List<GraveyardPlacement> stamped = new ArrayList<>();
        List<ZombieUnit> raised = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        boolean[] finished = { false };
        // every way out of the test ends here: the flag lease is given back exactly once, everything made is removed
        java.util.function.Consumer<String> finish = failure -> {
            if (finished[0])
                return;
            finished[0] = true;
            for (ZombieUnit z : raised)
                if (!z.isRemoved())
                    z.discard();
            for (GraveyardPlacement p : stamped)
                clear(level, p);
            giveBackPathing.run();
            if (failure == null)
                helper.succeed();
            else
                helper.fail(failure);
        };
        GraveyardPlacement gy;
        BlockPos rally;
        String layout;
        try {
            gy = stamp(helper, owner, code, gate == Gate.OPEN ? new BlockPos(4, 1, 4) : new BlockPos(4, 1, 0));
            if (gy == null) {
                finish.accept("could not stamp a graveyard at upgrade code " + code);
                return;
            }
            stamped.add(gy);
            if (gy.getUpgradeLevel() != code) {
                finish.accept("graveyard set up for upgrade code " + code + " reads " + gy.getUpgradeLevel() + " (" + gy.currentStructureName + ")");
                return;
            }
            List<BlockPos> exits = exitCells(level, gy);
            if (exits.isEmpty() || exits.stream().anyMatch(e -> e.getZ() != gy.maxCorner.getZ())) {
                finish.accept(gy.currentStructureName + ": its way out is not one gate on the south side, as this test lays the "
                    + "obstacles out (exit cells " + exits + ", footprint " + gy.minCorner.toShortString() + ".." + gy.maxCorner.toShortString() + ")");
                return;
            }
            int floorY = gy.minCorner.getY();
            BlockPos blockerAt = switch (gate) {
                case BLOCKED -> new BlockPos(gy.minCorner.getX(), floorY, gy.maxCorner.getZ() + 1);
                // covers the gate's last cell and leaves the others open
                case HALF_BLOCKED -> new BlockPos(exits.stream().mapToInt(BlockPos::getX).max().getAsInt(), floorY, gy.maxCorner.getZ() + 1);
                default -> null;
            };
            if (blockerAt != null) {
                // a player could put it there: the game's own placement check, with the first graveyard already built
                String why = BuildingValidators.getPlacementValidityError(level, Buildings.GRAVEYARD, blockerAt, owner,
                    Rotation.NONE, false, false, true);
                if (why != null) {
                    finish.accept("the game refuses a building flush against the gate (" + why + "): this layout can't happen in a match");
                    return;
                }
                GraveyardPlacement blocker = stampAt(level, owner, 0, blockerAt);
                if (blocker == null) {
                    finish.accept("could not stamp the neighbouring graveyard at " + blockerAt.toShortString());
                    return;
                }
                stamped.add(blocker);
            }
            if (gate == Gate.BANK) {
                // ground two blocks above the yard's floor right outside the gate: x 0..15, z 8..15 of the arena
                for (int x = 0; x <= 15; x++)
                    for (int z = 8; z <= 15; z++)
                        for (int y = 2; y <= 3; y++)
                            helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
            if (gate == Gate.OPEN) {
                rally = gy.getDefaultOutdoorSpawnPoint();
            } else {
                rally = helper.absolutePos(new BlockPos(1, 2, 3));
                gy.setRallyPoint(rally);
            }
            List<BlockPos> openExits = new ArrayList<>();
            for (BlockPos e : exitCells(level, gy))
                if (UnitUnstick.isWalkable(level, e.south()))
                    openExits.add(e);
            layout = gy.currentStructureName + " " + gate + " (footprint " + gy.minCorner.toShortString() + ".."
                + gy.maxCorner.toShortString() + ", gate " + exits + ", open to the outside " + openExits + ", rally "
                + rally.toShortString() + ")";
        } catch (GameTestAssertException e) {
            throw e;   // helper.fail's own way of failing the test
        } catch (Throwable t) {
            finish.accept("setting up: " + t);
            return;
        }

        Set<Integer> known = new HashSet<>();
        int[] firstOut = new int[UNITS];
        java.util.Arrays.fill(firstOut, -1);
        boolean[] madeUnit = new boolean[UNITS];
        List<long[]> chunks = chunksAround(gy, rally);
        int[] readyAt = { -1 };
        // one callback, a state machine on the tick (DragonDiveGameTests: vanilla iterates its tick-time map while it
        // runs the callbacks, so nothing is scheduled from inside one): wait for the chunks, raise the units, then watch
        helper.onEachTick(() -> {
            if (finished[0])
                return;
            try {
                long tick = helper.getTick();
                if (readyAt[0] < 0) {
                    if (entitiesReady(level, chunks)) {
                        readyAt[0] = (int) tick;
                        ReignOfNether.LOGGER.info("[GraveyardExit] {}: chunks entity-ticking at tick {} (cap {})", layout, tick, READY_CAP);
                    } else if (tick >= READY_CAP) {
                        finish.accept(layout + ": the graveyard's chunks were not entity-ticking after " + READY_CAP + " ticks");
                    }
                    return;
                }
                for (int n = 0; n < UNITS; n++) {
                    if (madeUnit[n] || tick < readyAt[0] + SETTLE + n * SPACING)
                        continue;
                    madeUnit[n] = true;
                    for (ZombieUnit z : zombiesOf(level, gy, owner))
                        known.add(z.getId());
                    // the real path: GraveyardUnitProductionItem -> ZombieProd.onComplete -> ProductionPlacement.produceUnit
                    ProductionItems.ZOMBIE.onComplete.accept(level, gy);
                    ZombieUnit made = null;
                    for (ZombieUnit z : zombiesOf(level, gy, owner))
                        if (known.add(z.getId()))
                            made = z;
                    if (made == null) {
                        problems.add("unit " + n + " was not raised (skulls " + gy.getTotalSkulls() + ", chunks entity-ticking "
                            + entitiesReady(level, chunks) + ")");
                        continue;
                    }
                    // it must walk out, not pick a fight with a neighbouring arena's units on the way
                    EngagementStance.set(made, EngagementStance.HOLD_FIRE);
                    made.setInvulnerable(true);
                    raised.add(made);
                }
                final int lastAt = readyAt[0] + SETTLE + (UNITS - 1) * SPACING;
                if (tick < lastAt + 1)
                    return;
                boolean all = raised.size() == UNITS && problems.isEmpty();
                for (int i = 0; i < raised.size(); i++) {
                    ZombieUnit z = raised.get(i);
                    boolean ok = z.isAlive() && !inside(z, gy) && horizontal(z, rally) <= RALLY_NEAR;
                    if (!inside(z, gy) && firstOut[i] < 0)
                        firstOut[i] = (int) tick;
                    all &= ok;
                }
                if (all) {
                    ReignOfNether.LOGGER.info("[GraveyardExit] {}: {} of {} out and at the rally by tick {} (out at {}); {}",
                        layout, raised.size(), UNITS, tick - readyAt[0], java.util.Arrays.toString(firstOut), pathStatsOf(raised));
                    finish.accept(null);
                    return;
                }
                if (tick < lastAt + LIMIT)
                    return;
                List<String> stuck = new ArrayList<>(problems);
                for (ZombieUnit z : raised)
                    if (!z.isAlive() || inside(z, gy) || horizontal(z, rally) > RALLY_NEAR)
                        stuck.add(describe(level, z, gy, rally));
                ReignOfNether.LOGGER.info("[GraveyardExit] {}: {} of {} NOT out and at the rally after {} ticks",
                    layout, stuck.size(), UNITS, LIMIT);
                finish.accept(layout + ": " + stuck.size() + " of " + UNITS + " Zombies not out of the graveyard and at "
                    + "the rally " + LIMIT + " ticks after the last was raised: " + String.join("; ", stuck)
                    + " | the pathfinder's grid (live kind / cached): " + gridView(level, gy, rally));
            } catch (GameTestAssertException e) {
                throw e;   // helper.fail's own way of failing the test
            } catch (Throwable t) {
                finish.accept("running: " + t);
            }
        });
    }

    /** The chunks the graveyard, its walk to the rally point and the margin it must leave by stand in: [chunkX, chunkZ]. */
    private static List<long[]> chunksAround(BuildingPlacement gy, BlockPos rally) {
        int pad = UnitUnstick.LEAVE_MARGIN + 2;
        int x0 = Math.min(gy.minCorner.getX(), rally.getX()) - pad, x1 = Math.max(gy.maxCorner.getX(), rally.getX()) + pad;
        int z0 = Math.min(gy.minCorner.getZ(), rally.getZ()) - pad, z1 = Math.max(gy.maxCorner.getZ(), rally.getZ()) + pad;
        List<long[]> out = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
                out.add(new long[] { cx, cz });
        return out;
    }

    /** Every chunk has its entities loaded and is entity-ticking (an entity made in one that is not cannot be found yet). */
    private static boolean entitiesReady(ServerLevel level, List<long[]> chunks) {
        for (long[] c : chunks) {
            int cx = (int) c[0], cz = (int) c[1];
            if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(cx, cz))
                    || !level.isPositionEntityTicking(new BlockPos((cx << 4) + 8, 64, (cz << 4) + 8)))
                return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------- helpers

    /** A finished graveyard at upgrade code 0-3, stamped at {@code rel} (its minimum corner, rotation NONE). */
    private static GraveyardPlacement stamp(GameTestHelper helper, String owner, int code, BlockPos rel) {
        return stampAt(helper.getLevel(), owner, code, helper.absolutePos(rel));
    }

    private static GraveyardPlacement stampAt(ServerLevel level, String owner, int code, BlockPos at) {
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.GRAVEYARD, level, at, Rotation.NONE, owner, false);
        if (!(bp instanceof GraveyardPlacement gy))
            return null;
        if (code == 1)
            gy.changeStructure(Graveyard.upgradedStructureName);
        else if (code >= 2)
            BuildingLevels.restore(gy, code);
        for (BuildingBlock blk : gy.getBlocks())
            level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
        gy.initPlacedBlocks();
        gy.isBuilt = true;
        BuildingServerEvents.getBuildings().add(gy);
        return gy;
    }

    /**
     * The graveyard's ways out: cells on the footprint's edge, at the yard's standing height, that a unit can walk in
     * and that open onto a walkable cell inside (a notch in the outer wall that leads nowhere is not one).
     */
    static List<BlockPos> exitCells(ServerLevel level, BuildingPlacement p) {
        List<BlockPos> out = new ArrayList<>();
        int y = p.minCorner.getY() + 1;
        for (int x = p.minCorner.getX(); x <= p.maxCorner.getX(); x++)
            for (int z = p.minCorner.getZ(); z <= p.maxCorner.getZ(); z++) {
                boolean edgeX = x == p.minCorner.getX() || x == p.maxCorner.getX();
                boolean edgeZ = z == p.minCorner.getZ() || z == p.maxCorner.getZ();
                if (edgeX == edgeZ)
                    continue;   // the inside, or a corner
                BlockPos c = new BlockPos(x, y, z);
                int ix = edgeX ? (x == p.minCorner.getX() ? 1 : -1) : 0;
                int iz = edgeZ ? (z == p.minCorner.getZ() ? 1 : -1) : 0;
                if (UnitUnstick.isWalkable(level, c) && UnitUnstick.isWalkable(level, c.offset(ix, 0, iz)))
                    out.add(c);
            }
        return out;
    }

    /** Is the unit standing in the footprint (by its feet's block column)? */
    static boolean inside(net.minecraft.world.entity.Entity e, BuildingPlacement p) {
        int x = net.minecraft.util.Mth.floor(e.getX()), z = net.minecraft.util.Mth.floor(e.getZ());
        return x >= p.minCorner.getX() && x <= p.maxCorner.getX() && z >= p.minCorner.getZ() && z <= p.maxCorner.getZ();
    }

    static double horizontal(net.minecraft.world.entity.Entity e, BlockPos bp) {
        double dx = e.getX() - (bp.getX() + 0.5), dz = e.getZ() - (bp.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** What the walkability cache says about the cells that decide the walk out: the gate, three cells outside it, the rally. */
    private static String gridView(ServerLevel level, GraveyardPlacement gy, BlockPos rally) {
        com.solegendary.reignofnether.unit.pathfinding.WalkabilityGrid grid =
            com.solegendary.reignofnether.unit.pathfinding.WalkabilityGrid.get(level);
        List<String> out = new ArrayList<>();
        for (BlockPos e : exitCells(level, gy)) {
            out.add(cellView(level, grid, "gate", e));
            for (int k = 1; k <= 3; k++)
                out.add(cellView(level, grid, "gate+" + k + "south", e.south(k)));
        }
        out.add(cellView(level, grid, "rally", rally));
        return String.join("; ", out) + "; cached chunks " + grid.builtChunkKeys().length;
    }

    private static String cellView(ServerLevel level, com.solegendary.reignofnether.unit.pathfinding.WalkabilityGrid grid,
                                   String what, BlockPos p) {
        return what + " " + p.toShortString() + " live "
            + com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.classify(level, p.getX(), p.getY(), p.getZ())
            + " / " + grid.peek(p.getX(), p.getY(), p.getZ());
    }

    private static String pathStatsOf(List<ZombieUnit> units) {
        List<String> out = new ArrayList<>();
        for (ZombieUnit z : units) {
            MoveToTargetBlockGoal g = ((Unit) z).getMoveGoal();
            out.add(g == null ? "no goal" : g.pathStats());
        }
        return String.join(" | ", out);
    }

    private static String describe(ServerLevel level, ZombieUnit z, BuildingPlacement gy, BlockPos rally) {
        MoveToTargetBlockGoal goal = ((Unit) z).getMoveGoal();
        BlockPos cell = UnitUnstick.standCell(z);
        return String.format(Locale.ROOT, "#%d at %.1f/%.1f/%.1f %s, %.1f from the rally, move target %s, nav done %s, "
                + "path pending %s, goal running %s, last unreachable %s, idle %s, pocketed %s%s",
            z.getId(), z.getX(), z.getY(), z.getZ(), inside(z, gy) ? "INSIDE" : "outside", horizontal(z, rally),
            goal == null || goal.getMoveTarget() == null ? "none" : goal.getMoveTarget().toShortString(),
            z.getNavigation().isDone(), goal != null && goal.isPathPending(), goal != null && goal.isGoalRunning(),
            goal == null || goal.getLastUnreachableTarget() == null ? "none" : goal.getLastUnreachableTarget().toShortString(),
            ((Unit) z).isIdle(), UnitUnstick.isPocketed(level, cell), z.isAlive() ? "" : " DEAD")
            + (goal == null ? "" : ", " + goal.pathStats());
    }

    private static List<ZombieUnit> zombiesOf(ServerLevel level, GraveyardPlacement gy, String owner) {
        return new ArrayList<>(level.getEntitiesOfClass(ZombieUnit.class, gy.getAABB().inflate(24),
            z -> owner.equals(z.getOwnerName())));
    }

    private static void clear(ServerLevel level, GraveyardPlacement gy) {
        gy.productionQueue.clear();
        BuildingServerEvents.getBuildings().remove(gy);
        for (BuildingBlock blk : gy.getBlocks())
            level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
    }
}
