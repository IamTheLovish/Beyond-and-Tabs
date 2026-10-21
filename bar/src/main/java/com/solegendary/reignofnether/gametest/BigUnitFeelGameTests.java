package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.pathfinding.ChunkSnapshot;
import com.solegendary.reignofnether.unit.pathfinding.GridAStar;
import com.solegendary.reignofnether.unit.pathfinding.GridNeighbors;
import com.solegendary.reignofnether.unit.pathfinding.MobilityClass;
import com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig;
import com.solegendary.reignofnether.unit.pathfinding.RtsPathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The feel rules every big body shares (pathfinding/GridNeighbors.footprintBox for every body wider than two blocks,
 * unit/TitanMass for the ground T3s; project doc claude/t3-roles.md section 5, lovish Oct 21: every T3 "extremely slow
 * and almost useless", "slow and clunky almost unplayable"). Folded in from PR #87 (claude/t3-feel-mammoth). Own holder:
 * the CI guard keeps new tests out of SkirmishGameTests; the roster's speed rules are T3RoleGameTests'.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BigUnitFeelGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /**
     * Never stuck: the RTS grid refuses a gap a body cannot fit and routes round it. A stone pad high above the arena
     * with a wall across it that has a 2-wide gap on the straight line and a 5-wide opening off to the side: a 2-wide
     * body (footprint radius 1, the Siege Ox's class) takes the gap, the 3.3-wide War Mammoth (radius 3: a 4x4 box) is
     * routed through the opening - until this every wide body was pathed as 2x2 and the mammoth ground on the gap.
     * Runs GridAStar on a snapshot of the pad directly, so the verdict is the pathfinder's own, whatever the
     * rtsPathfinding gamerule says on this server (off in CI).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void a_body_wider_than_a_gap_paths_round_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 200;   // the floor; feet at y + 1
        // its own pad far south of the test grid (the y=200 pads over the arenas can meet a neighbour's), past every
        // other far pad (the biome bases' 30000, the every-unit rows)
        int ox = ((base.getX() >> 4) << 4) + 8, oz = (((base.getZ() + 36000) >> 4) << 4) + 8;
        final int w = 36, d = 22, wallX = ox + 16, gapZ0 = oz + 9, gapZ1 = oz + 10, openZ0 = oz + 16;
        BlockPos start = new BlockPos(ox + 4, y + 1, oz + 9), goal = new BlockPos(ox + 30, y + 1, oz + 9);
        final int dilation = 14;   // covers the pad and no more: no far chunk is pulled in
        ChunkSnapshot.CaptureRegion region = ChunkSnapshot.regionFor(level, start, goal, dilation);
        for (int cx = region.cx0; cx <= region.cx1; cx++)
            for (int cz = region.cz0; cz <= region.cz1; cz++)
                GameTestChunks.loadNowOrFail(level, cx, cz);
        var stone = Blocks.STONE.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        for (int x = ox; x < ox + w; x++)
            for (int z = oz; z < oz + d; z++) {
                level.setBlock(new BlockPos(x, y, z), stone, 2);
                for (int dy = 1; dy <= 7; dy++)
                    level.setBlock(new BlockPos(x, y + dy, z), air, 2);
            }
        // the wall: 6 high (over any clearance), the gap and the opening cut through it
        for (int z = oz; z < oz + d; z++) {
            if ((z >= gapZ0 && z <= gapZ1) || z >= openZ0)
                continue;
            for (int dy = 1; dy <= 6; dy++)
                level.setBlock(new BlockPos(wallX, y + dy, z), stone, 2);
        }
        List<String> problems = new ArrayList<>();
        var mammothType = EntityRegistrar.WAR_MAMMOTH_UNIT.get();
        var oxType = EntityRegistrar.RAVAGER_UNIT.get();
        Mob mammoth = mammothType.create(level), ox2 = oxType.create(level);
        if (mammoth == null || ox2 == null) {
            helper.fail("could not create the War Mammoth / Siege Ox");
            return;
        }
        int rMammoth = RtsPathfinder.footprintRadiusFor(mammoth), rOx = RtsPathfinder.footprintRadiusFor(ox2);
        mammoth.discard();
        ox2.discard();
        if (rMammoth < 3)
            problems.add("the War Mammoth (" + mammothType.getWidth() + " wide) has footprint radius " + rMammoth + ", expected 3 (a 4x4 box)");
        if (rOx != 1)
            problems.add("the Siege Ox (" + oxType.getWidth() + " wide) has footprint radius " + rOx + ", expected 1 (a 2x2 box)");
        List<BlockPos> wide = search(level, start, goal, dilation, rMammoth, Mth.ceil(mammothType.getHeight()));
        List<BlockPos> narrow = search(level, start, goal, dilation, rOx, Mth.ceil(oxType.getHeight()));
        if (wide.isEmpty() || !wide.get(wide.size() - 1).closerThan(goal, 2.5))
            problems.add("the 4x4 body found no path to the goal (" + wide.size() + " nodes, ends "
                + (wide.isEmpty() ? "nowhere" : rel(wide.get(wide.size() - 1), ox, y, oz)) + ")");
        if (narrow.isEmpty() || !narrow.get(narrow.size() - 1).closerThan(goal, 2.5))
            problems.add("the 2x2 body found no path to the goal (" + narrow.size() + " nodes)");
        boolean wideThroughGap = false, wideThroughOpening = false, narrowThroughGap = false;
        for (BlockPos p : wide) {
            if (p.getX() == wallX && p.getZ() >= gapZ0 - 1 && p.getZ() <= gapZ1 + 1)
                wideThroughGap = true;
            if (p.getX() == wallX && p.getZ() >= openZ0)
                wideThroughOpening = true;
        }
        for (BlockPos p : narrow)
            if (p.getX() == wallX && p.getZ() >= gapZ0 && p.getZ() <= gapZ1)
                narrowThroughGap = true;
        if (wideThroughGap)
            problems.add("the 4x4 body was routed through the 2-wide gap it cannot fit: " + trace(wide, ox, y, oz));
        if (!wideThroughOpening)
            problems.add("the 4x4 body did not take the 5-wide opening: " + trace(wide, ox, y, oz));
        if (!narrowThroughGap)
            problems.add("the 2x2 body should take the 2-wide gap (the quadrant footprint is unchanged): " + trace(narrow, ox, y, oz));
        // the box the path converter puts the body in: at the opening the wall side is refused, so the body sits off
        // the wall; in the open the body walks the cell centre
        if (problems.isEmpty()) {
            ChunkSnapshot view = ChunkSnapshot.capture(level, start, goal, dilation, MobilityClass.LARGE,
                Math.max(2, Mth.ceil(mammothType.getHeight())), rMammoth, 1.0f, false);
            int open = GridNeighbors.footprintBox(view, MobilityClass.LARGE, ox + 8, y + 1, oz + 9, rMammoth + 1,
                view.clearanceCells(), 1.0f, 1, 0);
            if (open != GridNeighbors.CENTRED_BOX)
                problems.add("in the open the footprint box should be centred, got " + open);
            int atWall = GridNeighbors.footprintBox(view, MobilityClass.LARGE, wallX, y + 1, openZ0, rMammoth + 1,
                view.clearanceCells(), 1.0f, 1, 0);
            if (atWall == GridNeighbors.NO_BOX || atWall == GridNeighbors.CENTRED_BOX || GridNeighbors.boxB(atWall) != 0)
                problems.add("at the opening's wall side the box should start at the node row and run away from the wall, got " + atWall);
            if (GridNeighbors.footprintBox(view, MobilityClass.LARGE, wallX, y + 1, gapZ0, rMammoth + 1,
                    view.clearanceCells(), 1.0f, 0, 0) != GridNeighbors.NO_BOX)
                problems.add("a 4x4 box fitted in the 2-wide gap");
        }
        for (int x = ox; x < ox + w; x++)
            for (int z = oz; z < oz + d; z++)
                for (int dy = 0; dy <= 7; dy++)
                    level.setBlock(new BlockPos(x, y + dy, z), air, 2);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    private static List<BlockPos> search(ServerLevel level, BlockPos start, BlockPos goal, int dilation, int footprintRadius, int height) {
        ChunkSnapshot snap = ChunkSnapshot.capture(level, start, goal, dilation, MobilityClass.LARGE, Math.max(2, height),
            footprintRadius, 1.0f, false);
        GridAStar.Result r = GridAStar.search(snap, start, goal, 0, PathfinderConfig.MAX_RADIUS, 60000);
        return r.waypoints;
    }

    private static String rel(BlockPos p, int ox, int y, int oz) {
        return (p.getX() - ox) + "/" + (p.getY() - y) + "/" + (p.getZ() - oz);
    }

    private static String trace(List<BlockPos> path, int ox, int y, int oz) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i += Math.max(1, path.size() / 12))
            sb.append(rel(path.get(i), ox, y, oz)).append(' ');
        return sb.toString().trim();
    }

    /**
     * Mass, end to end: a titan has right of way - it is never pushed by the bodies it overlaps, and walking through a
     * line of its own infantry it shoves them aside (TitanMass.doPush, from vanilla's push pass) and arrives where it
     * was sent on time. Three Brutes held on the line a War Mammoth is ordered through; a plain Brute stays pushable.
     * Also the speed the mammoth really walks at: the Siege Ox's tick used to reset every ravager body's speed to the
     * Ox's own 0.25 (the mammoth's sheet said 0.17) - RavagerUnit.designedMovementSpeed.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void a_heavy_body_has_right_of_way_and_ploughs_through_a_line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "plough");
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        Mob mammoth = BigUnitGameTests.spawn(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), owner, 2.5, 8.5);
        List<Mob> line = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            // in the mammoth's lane whichever way vanilla's wide-body path offsets it (the body walks node + 2.0)
            Mob b = BigUnitGameTests.spawn(helper, EntityRegistrar.BRUTE_UNIT.get(), owner, 6.5 + i, 9.5);
            if (b != null)
                line.add(b);
        }
        if (mammoth == null || line.size() != 3) {
            if (mammoth != null)
                mammoth.discard();
            for (Mob b : line)
                b.discard();
            helper.fail("could not create the plough test units");
            return;
        }
        List<String> problems = new ArrayList<>();
        if (mammoth.isPushable() || !TitanMass.isSmallBody(mammoth, line.get(0)))
            problems.add("the War Mammoth should be unpushable and a Brute a small body to it (pushable " + mammoth.isPushable()
                + ", small " + TitanMass.isSmallBody(mammoth, line.get(0)) + ")");
        if (!line.get(0).isPushable())
            problems.add("a Brute should be a plain pushable body");
        for (Mob b : line)
            ((Unit) b).setHoldPosition(true);
        BlockPos to = helper.absolutePos(new BlockPos(11, 2, 8));   // its 4x4 box stays inside the open ground
        double[] startZ = new double[3];
        for (int i = 0; i < 3; i++)
            startZ[i] = line.get(i).getZ();
        float speed = com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.movementSpeed;
        helper.runAfterDelay(10, () -> {
            double base = mammoth.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            if (Math.abs(base - speed) > 1e-4)
                problems.add("after ten ticks the mammoth's speed is " + base + ", not its own " + speed + " (RavagerUnit.aiStep reset it)");
            UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, new int[] { mammoth.getId() }, to, BlockPos.ZERO);
        });
        final int[] t = { 0 };
        final boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            t[0]++;
            double dx = mammoth.getX() - (to.getX() + 0.5), dz = mammoth.getZ() - (to.getZ() + 0.5);
            boolean arrived = dx * dx + dz * dz <= 3.0 * 3.0;   // vanilla sits a 3.3-wide body up to 2 off its node
            if (t[0] < 30 || (!arrived && t[0] < 170))
                return;
            done[0] = true;
            int shoved = 0;
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                double moved = Math.abs(line.get(i).getZ() - startZ[i]);
                where.append(String.format(java.util.Locale.ROOT, "%.2f ", moved));
                if (moved > 0.6)
                    shoved++;
            }
            if (!arrived)
                problems.add(String.format(java.util.Locale.ROOT, "the mammoth did not get through its own line in %d ticks (%.1f from its target)",
                    t[0], Math.sqrt(dx * dx + dz * dz)));
            if (shoved < 2)
                problems.add("the line should have been shoved aside (sideways moves " + where.toString().trim() + ")");
            mammoth.discard();
            for (Mob b : line)
                b.discard();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }
}
