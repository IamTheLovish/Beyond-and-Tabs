package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.match.MatchPrep;
import com.solegendary.reignofnether.maps.ExtractorClearance;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.startpos.BattlefieldWall;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Metal beside the arena wall (lovish Oct 22: "edgeline means maybe enough blocks away that the extractor doesn't get
 * blocked by the wall"). {@link ExtractorClearance} derives how far from the wall a patch must stand; these tests prove
 * the number against the real thing, twice:
 * <ul>
 *   <li>on every pre-made map (2/4/8 players, three seeds), the ring wall's real cells with the widest piece (the
 *       gatehouse) laid on every one of them, against the biggest extractor's room (footprint, gap, worker lane) round
 *       every rim patch, plus the ground under it and the flood-fill reach from the slot's start - pure maths on
 *       MapLayout, batch "maps_metal";</li>
 *   <li>in a world, with the real BattlefieldWall (towers and a gatehouse included) and the real placement validator:
 *       every faction's extractor, tier 1 and the tier-2 swap, is accepted on patches stamped exactly the clearance
 *       from the ring at 24 angles and at every tower and gatehouse, stamping cuts no hole in the wall, and a patch
 *       2 blocks from the wall is refused (so the check can fail), batch "maps_rim_world".</li>
 * </ul>
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MapRimGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int ROOM = MetalPatches.EXTRACTOR_HALF + MetalPatches.BUILDING_GAP + ExtractorClearance.WORKER_LANE;

    /**
     * Every rim patch of every pre-made map has the biggest extractor's room clear of the wall, level dry ground under
     * its pad, a walkable lane round it, and (the open-ground ones, the flood is asked of those only: some template
     * patches are cut off by design, see MapMetalGameTests) a worker's route from its start to the cell beside it.
     */
    @GameTest(template = ARENA, batch = "maps_metal")
    public static void map_metal_rim_patches_have_room_for_the_biggest_extractor(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        MapLayout.Sample s = new MapLayout.Sample();
        int rim = 0, rimOpen = 0, nearest = Integer.MAX_VALUE;
        for (MapPreset preset : MapMetalGameTests.premade())
            for (int n : new int[] { 2, 4, 8 })
                for (long seed : new long[] { 0L, 0x5EED0001L, 0xBA7C0DEL }) {
                    MapLayout l = MapLayout.build(preset, RealmMapGameTests.teams(n), seed);
                    String tag = preset + " n=" + n + " seed " + seed + ": ";
                    int half = l.radius + 8, w = 2 * half + 1;
                    boolean[] wall = wallZone(l, half);
                    MapLayout.Reach[] reach = new MapLayout.Reach[l.n];
                    for (int i = 0; i < l.mexes.size(); i++) {
                        MapLayout.Mex m = l.mexes.get(i);
                        if (l.distanceToArenaEdge(m.dx(), m.dz()) > 40)
                            continue;
                        rim++;
                        boolean open = i >= l.spreadFrom();
                        // the room round the patch against the wall's cells (any of them may be a gatehouse)
                        for (int a = -ROOM; a <= ROOM; a++)
                            for (int b = -ROOM; b <= ROOM; b++)
                                if (wall[(m.dx() + a + half) * w + (m.dz() + b + half)])
                                    problems.add(tag + "patch " + m.dx() + "," + m.dz() + " is within " + ROOM
                                        + " blocks of a wall cell at " + (m.dx() + a) + "," + (m.dz() + b));
                        nearest = Math.min(nearest, wallDistance(wall, half, m.dx(), m.dz()));
                        String fault = MapMetalGameTests.padFault(l, m.dx(), m.dz(), s);
                        if (fault != null)
                            problems.add(tag + "patch " + m.dx() + "," + m.dz() + " has " + fault + " in its pad");
                        if (!open)
                            continue;
                        rimOpen++;
                        for (int a = -ROOM; a <= ROOM; a++)
                            for (int b = -ROOM; b <= ROOM; b++) {
                                if (Math.max(Math.abs(a), Math.abs(b)) != ROOM)
                                    continue;   // the worker lane: the ring just outside the gap
                                l.sample(m.dx() + a, m.dz() + b, s);
                                if (s.surface == MapLayout.Surface.VOID || s.surface == MapLayout.Surface.LAVA
                                        || (s.surface == MapLayout.Surface.WATER && s.height < -1))
                                    problems.add(tag + "patch " + m.dx() + "," + m.dz() + " has " + s.surface + " in its worker lane at "
                                        + (m.dx() + a) + "," + (m.dz() + b));
                            }
                        MapLayout.Reach r = reach[m.slot()];
                        if (r == null) {
                            MapLayout.Slot sl = l.slots.get(m.slot());
                            r = reach[m.slot()] = l.reachFrom(sl.dx(), sl.dz());
                        }
                        boolean beside = false;   // a cell of the gap ring, where the builder stands
                        int gap = MetalPatches.EXTRACTOR_HALF + MetalPatches.BUILDING_GAP;
                        for (int a = -gap; a <= gap; a++)
                            for (int b = -gap; b <= gap; b++)
                                if (Math.max(Math.abs(a), Math.abs(b)) == gap && r.contains(m.dx() + a, m.dz() + b))
                                    beside = true;
                        if (!r.contains(m.dx(), m.dz()) || !beside)
                            problems.add(tag + "no worker can reach patch " + m.dx() + "," + m.dz() + " from slot " + m.slot());
                    }
                }
        ReignOfNether.LOGGER.info("[MapRim] {} rim patches checked ({} open-ground), the nearest to a wall cell is {} blocks",
            rim, rimOpen, nearest);
        if (rim < 100)
            problems.add("only " + rim + " rim patches were checked: the sweep is broken");
        // the check can fail: a ghost 3 blocks inside the ring meets the wall zone
        MapLayout l = MapLayout.build(MapPreset.VERDANT_VALLEY, RealmMapGameTests.teams(2));
        int half = l.radius + 8, w = 2 * half + 1;
        boolean[] wall = wallZone(l, half);
        boolean meets = false;
        for (int a = -ROOM; a <= ROOM; a++)
            for (int b = -ROOM; b <= ROOM; b++)
                meets |= wall[(l.radius - 3 + a + half) * w + (b + half)];
        if (!meets)
            problems.add("a patch 3 blocks inside the ring does not meet the wall zone: the check is vacuous");
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /**
     * Every cell the wall can occupy: each ring column (as BattlefieldWall plans them, the ring centred half a block off
     * the layout's centre as MatchPrep.startWall does) with the gatehouse's reach laid on it, which holds a tower, the
     * plain wall's second layer and the portcullis. Indexed [(x + half) * width + (z + half)].
     */
    static boolean[] wallZone(MapLayout l, int half) {
        int w = 2 * half + 1;
        boolean[] wall = new boolean[w * w];
        int g = BattlefieldWall.GATEHOUSE_HALF;
        for (int[] c : BattlefieldWall.ringColumns(0.5, 0.5, l.radius))
            for (int a = -g; a <= g; a++)
                for (int b = -g; b <= g; b++) {
                    int x = c[0] + a + half, z = c[1] + b + half;
                    if (x >= 0 && z >= 0 && x < w && z < w)
                        wall[x * w + z] = true;
                }
        return wall;
    }

    /** Chebyshev distance from (dx, dz) to the nearest wall-zone cell (searched out to 40). */
    static int wallDistance(boolean[] wall, int half, int dx, int dz) {
        int w = 2 * half + 1;
        for (int d = 0; d <= 40; d++)
            for (int a = -d; a <= d; a++)
                for (int b = -d; b <= d; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != d)
                        continue;
                    int x = dx + a + half, z = dz + b + half;
                    if (x >= 0 && z >= 0 && x < w && z < w && wall[x * w + z])
                        return d;
                }
        return 41;
    }

    /**
     * The real thing, small: a ring wall of radius 48 (BattlefieldWall.raise: towers every 40-56 columns, one of them a
     * gatehouse) on a flat platform; patches stamped exactly the clearance from the ring at 24 angles and at the centre of
     * every tower and gatehouse. Each patch takes the extractor of every faction at tier 1 and at the tier-2 swap through
     * BuildingValidators (the same call a click makes); stamping the pads cut no hole in the wall; and a patch 2 blocks
     * from the wall is refused, so the acceptance is not vacuous.
     */
    @GameTest(template = ARENA, batch = "maps_rim_world", timeoutTicks = 400)
    public static void map_metal_extractors_fit_beside_the_real_wall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 200, radius = 48, half = radius + 8, top = y + 16;
        int cx = base.getX(), cz = base.getZ();
        double wx = cx + 0.5, wz = cz + 0.5;
        int clear = ExtractorClearance.blocks();
        List<String> problems = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        try {
            MetalPatches.forgetAll();
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++) {
                    for (int yy = y + 1; yy <= top; yy++)
                        if (!level.getBlockState(p.set(cx + dx, yy, cz + dz)).isAir())
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(p.set(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 2);
                    level.setBlock(p.set(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            BattlefieldWall.raise(level, wx, wz, radius, 0, 99L, MatchPrep.WALL_COLUMNS_PER_TICK, null);
            int planned = BattlefieldWall.columnsPlanned();
            BattlefieldWall.finishNow();
            if (BattlefieldWall.isBuilding() || BattlefieldWall.columnsDone() != planned)
                problems.add("the ring was not built whole");

            // the towers and the gatehouse: a campfire tops each, a gatehouse also has its portcullis bars
            List<int[]> pieces = new ArrayList<>();   // {x, z, gatehouse ? 1 : 0}
            int walls = 0;
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++)
                    for (int yy = y + 1; yy <= top; yy++) {
                        var st = level.getBlockState(p.set(cx + dx, yy, cz + dz));
                        if (st.isAir())
                            continue;
                        walls++;
                        if (st.is(Blocks.CAMPFIRE))
                            pieces.add(new int[] { cx + dx, cz + dz, 0 });
                    }
            for (int[] piece : pieces)
                for (int a = -BattlefieldWall.GATEHOUSE_HALF; a <= BattlefieldWall.GATEHOUSE_HALF; a++)
                    for (int b = -BattlefieldWall.GATEHOUSE_HALF; b <= BattlefieldWall.GATEHOUSE_HALF; b++)
                        for (int yy = y + 1; yy <= y + 6; yy++)
                            if (level.getBlockState(p.set(piece[0] + a, yy, piece[1] + b)).is(Blocks.IRON_BARS))
                                piece[2] = 1;
            int gates = 0;
            for (int[] piece : pieces)
                gates += piece[2];
            ReignOfNether.LOGGER.info("[MapRim] ring of {} columns, {} wall blocks, {} towers of which {} gatehouse(s); clearance {}",
                planned, walls, pieces.size(), gates, clear);
            if (pieces.isEmpty() || gates == 0)
                problems.add("the ring has " + pieces.size() + " towers and " + gates + " gatehouses: the worst case is not on it");

            // the angles: 24 round the ring and the centre of every tower and gatehouse
            List<Double> angles = new ArrayList<>();
            for (int k = 0; k < 24; k++)
                angles.add(k * Math.PI / 12);
            for (int[] piece : pieces)
                angles.add(Math.atan2(piece[1] + 0.5 - wz, piece[0] + 0.5 - wx));

            // every faction's extractor, tier 1 and the tier-2 swap (an unregistered instance of the swap's structure)
            List<MetalExtractor> ghosts = new ArrayList<>();
            for (Building b : ReignOfNetherRegistries.BUILDING)
                if (b instanceof MetalExtractor me) {
                    ghosts.add(me);
                    ghosts.add(new MetalExtractor("_t2" + me.variant));
                }
            if (ghosts.size() != 14)
                problems.add("expected 14 extractor ghosts, built " + ghosts.size());

            int wallBefore = countAbove(level, cx, cz, half, y, top, p);
            int placed = 0;
            List<BlockPos> centres = new ArrayList<>();
            for (double a : angles) {
                int px = cx + (int) Math.round(Math.cos(a) * (radius - clear));
                int pz = cz + (int) Math.round(Math.sin(a) * (radius - clear));
                boolean crowded = false;   // a pad stamped over a neighbour's patch would overwrite its blocks
                for (BlockPos o : centres)
                    crowded |= Math.abs(o.getX() - px) <= 2 * MetalPatches.PAD_HALF && Math.abs(o.getZ() - pz) <= 2 * MetalPatches.PAD_HALF;
                if (crowded)
                    continue;
                MetalPatches.stamp(level, px, pz);
                centres.add(new BlockPos(px, y, pz));
            }
            int wallAfter = countAbove(level, cx, cz, half, y, top, p);
            if (wallAfter != wallBefore)
                problems.add("stamping the pads changed the wall: " + wallBefore + " blocks above the ground before, "
                    + wallAfter + " after");
            for (BlockPos c : centres) {
                BlockPos origin = new BlockPos(c.getX() - MetalPatches.EXTRACTOR_HALF, y, c.getZ() - MetalPatches.EXTRACTOR_HALF);
                for (MetalExtractor g : ghosts) {
                    String err = BuildingValidators.getPlacementValidityError(level, g, origin, "gametest_rim", Rotation.NONE,
                        false, false, true);
                    placed++;
                    if (err != null)
                        problems.add(g.structureName + " refused at " + (radius - Math.hypot(c.getX() - wx + 0.5, c.getZ() - wz + 0.5))
                            + " from the ring (" + c.toShortString() + "): " + err);
                }
            }

            // the control: a patch block 2 inside the ring beside the gatehouse (nothing stamped, so the wall stands) is refused
            int[] gate = pieces.stream().filter(pc -> pc[2] == 1).findFirst().orElse(pieces.isEmpty() ? null : pieces.get(0));
            if (gate != null) {
                double a = Math.atan2(gate[1] + 0.5 - wz, gate[0] + 0.5 - wx);
                int px = cx + (int) Math.round(Math.cos(a) * (radius - 2)), pz = cz + (int) Math.round(Math.sin(a) * (radius - 2));
                level.setBlock(p.set(px, y, pz), MetalPatches.PATCH_BLOCK.defaultBlockState(), 2);
                String err = BuildingValidators.getPlacementValidityError(level, ghosts.get(0), new BlockPos(px - 2, y, pz - 2),
                    "gametest_rim", Rotation.NONE, false, false, true);
                if (err == null)
                    problems.add("an extractor 2 blocks from the wall was accepted: the test cannot fail");
            }
            ReignOfNether.LOGGER.info("[MapRim] {} ghosts checked on {} patches", placed, centres.size());
        } finally {
            BattlefieldWall.cancel();
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++)
                    for (int yy = y; yy <= top; yy++)
                        if (!level.getBlockState(p.set(cx + dx, yy, cz + dz)).isAir())
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            MetalPatches.forgetAll();
        }
        if (!problems.isEmpty())
            helper.fail(MapMetalGameTests.fail(problems));
        helper.succeed();
    }

    /** Solid blocks above the platform (the wall; the patches and pads lie at or under y). */
    private static int countAbove(ServerLevel level, int cx, int cz, int half, int y, int top, BlockPos.MutableBlockPos p) {
        int n = 0;
        for (int dx = -half; dx <= half; dx++)
            for (int dz = -half; dz <= half; dz++)
                for (int yy = y + 1; yy <= top; yy++)
                    if (!level.getBlockState(p.set(cx + dx, yy, cz + dz)).isAir())
                        n++;
        return n;
    }
}
