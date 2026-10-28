package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.startpos.BattlefieldWall;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * How close to the arena wall a metal patch may stand and still take an extractor: the "edgeline" lovish asked for
 * (Oct 22: "maybe enough blocks away that the extractor doesn't get blocked by the wall"), worked out from the real
 * buildings and the real wall instead of picked by hand. {@link MetalOptions#rimInset()} is this number unless a caller
 * asks for more.
 *
 * <p>Measured from the patch centre outwards, as a square (the footprints and the wall's towers are axis-aligned
 * squares, so the worst case is a corner pointing at the wall: that is the square half-width times root 2):
 * <ol>
 *   <li><b>The extractor.</b> The biggest footprint of any {@code metal_extractor*.nbt}: every faction's skin, the
 *       tier-1 build and the tier-2 structure the upgrade swaps in over the old one (MetalExtractor.getUpgradedStructureName,
 *       UpgradeExtractor). Read from the building definitions: every MetalExtractor in the building registry, both
 *       structure names, the block extent of each file as BuildingUtils.getMinCorner/getMaxCorner see it, the longer
 *       side (a building may be rotated). Today all fourteen are 5x5, so 2 either side of the centre
 *       ({@link MetalPatches#EXTRACTOR_HALF}).</li>
 *   <li><b>The placement margin.</b> {@link MetalPatches#BUILDING_GAP}: the 1 free block round a building that
 *       {@code MetalPatches.RESERVED_HALF} reserves and BuildingValidators keeps between buildings. The 7x7 pad that
 *       MetalPatches.stamp levels (and clears of everything above the ground, wall blocks included: a pad that reached the
 *       wall would cut a hole in it) is the same 3 ({@link MapLayout#PAD}); the larger of the two counts.</li>
 *   <li><b>Room for the worker.</b> {@link #WORKER_LANE}: one more free block outside that. The builder stands in the cell
 *       one outside the footprint (BuildRepairGoal.calcMoveTarget -> getClosestGroundPos(.., 1, ..)), which the gap
 *       holds; the lane beyond lets it walk round to any side and a second one pass it.</li>
 *   <li><b>The wall.</b> The widest piece the ring wall raises round a ring column: the gatehouse, 5x5
 *       ({@link BattlefieldWall#GATEHOUSE_HALF}); a tower is 3x3 and the plain wall two thick. The world border adds
 *       nothing: BuildingValidators.isBuildingPlacementWithinWorldBorder tests the footprint's corner blocks against it
 *       (no thickness) and the ring is the arena's edge, inside it.</li>
 *   <li><b>Rounding.</b> {@link #RING_ROUNDING}: one and a half blocks, half a block on each axis for each of three
 *       roundings: a ring column is the nearest block to the circle, MatchPrep.startWall centres the ring half a block
 *       off the layout's centre, and a patch's own centre is rounded to a block after the fill judged it.</li>
 * </ol>
 * The measured distance is {@code MapLayout.distanceToArenaEdge}, from the ring's own radius. With the 5x5 extractor that
 * is 2 + 1 + 1 + 2 = 6 blocks square, plus the 1.5 rounding, times root 2, rounded up: <b>11 blocks</b>
 * ({@link #FALLBACK_FOOTPRINT} stands in when the building definitions cannot be read, and MapMetalGameTests checks the
 * two agree). MapMetalGameTests then lays the widest wall piece on every ring column of every pre-made map and an
 * extractor ghost on every rim patch, so a change to any term shows up there.
 */
public final class ExtractorClearance {

    private ExtractorClearance() { }

    /** One free lane beyond the placement gap: a worker stands and walks there (see the class doc, item 3). */
    public static final int WORKER_LANE = 1;
    /** Half a block on each axis for the ring column, the ring's centre offset and the patch's own rounding (item 5). */
    public static final double RING_ROUNDING = 1.5;
    /** The footprint side, in blocks, used when the structure files cannot be read (registry empty, jar unreadable). */
    public static final int FALLBACK_FOOTPRINT = 5;

    private static final double SQRT2 = Math.sqrt(2.0);

    /** Every term of the clearance, for the knob table, the logs and the game tests. */
    public record Parts(int footprintSide, int footprintReach, int buildingGap, int pad, int workerLane, int wallHalf,
                        double ringRounding, int square, int blocks, int structures, String widest) {
        @Override
        public String toString() {
            return blocks + " blocks = ceil((max(" + footprintReach + " extractor + " + buildingGap + " gap, " + pad
                + " pad) + " + workerLane + " worker lane + " + wallHalf + " gatehouse + " + ringRounding
                + " rounding) x root 2), from " + structures + " extractor structures, widest " + widest + " ("
                + footprintSide + "x" + footprintSide + ")";
        }
    }

    private static volatile Parts cached;

    /** The clearance in blocks: how far from the arena's edge a patch centre must be. */
    public static int blocks() {
        return parts().blocks();
    }

    /**
     * The terms of the clearance. Read from the building definitions once they are registered (and then kept); until
     * then, or if they cannot be read, the fallback footprint answers and nothing is kept.
     */
    public static Parts parts() {
        Parts p = cached;
        if (p != null)
            return p;
        Footprint f = readFootprint();
        if (f == null)
            return derive(FALLBACK_FOOTPRINT, 0, "fallback");
        p = derive(f.side, f.structures, f.widest);
        cached = p;
        ReignOfNether.LOGGER.info("[ExtractorClearance] metal patches keep {}", p);
        return p;
    }

    /** The clearance for an extractor footprint {@code side} blocks wide. Pure arithmetic: the game tests drive it. */
    public static Parts derive(int side, int structures, String widest) {
        int reach = side / 2;
        int pad = Math.max(MapLayout.PAD, MetalPatches.PAD_HALF);
        int square = Math.max(reach + MetalPatches.BUILDING_GAP, pad) + WORKER_LANE + BattlefieldWall.GATEHOUSE_HALF;
        int blocks = (int) Math.ceil((square + RING_ROUNDING) * SQRT2);   // 7.5 x 1.414 = 10.6
        return new Parts(side, reach, MetalPatches.BUILDING_GAP, pad, WORKER_LANE, BattlefieldWall.GATEHOUSE_HALF,
            RING_ROUNDING, square, blocks, structures, widest);
    }

    /** Every extractor structure name a faction can have standing: the build and the tier-2 swap of each registered MetalExtractor. */
    public static List<String> structureNames() {
        TreeSet<String> names = new TreeSet<>();
        for (Building b : ReignOfNetherRegistries.BUILDING)
            if (b instanceof MetalExtractor me) {
                names.add(me.getUpgradedStructureName(0));
                names.add(me.getUpgradedStructureName(1));
            }
        return new ArrayList<>(names);
    }

    private record Footprint(int side, int structures, String widest) { }

    private static Footprint readFootprint() {
        List<String> names;
        try {
            names = structureNames();
        } catch (RuntimeException | LinkageError e) {
            // no game running (plain JUnit: the building registry can't initialise) -> the fallback footprint
            return null;
        }
        int side = 0, read = 0;
        String widest = "";
        for (String name : names) {
            int[] extent = extent(name);
            if (extent == null)
                continue;
            read++;
            int s = Math.max(extent[0], extent[1]);
            if (s > side) {
                side = s;
                widest = name;
            }
        }
        // a partial read could understate the footprint: only trust a read that found every file
        return read == 0 || read != names.size() ? null : new Footprint(side, read, widest);
    }

    /** {width in x, width in z} of a structure file's blocks (the extent the game places by), or null if unreadable. */
    public static int[] extent(String structureName) {
        for (String root : new String[] { "/data/", "/assets/" }) {
            String path = root + ReignOfNether.MOD_ID + "/structures/" + structureName + ".nbt";
            try (InputStream in = ExtractorClearance.class.getResourceAsStream(path)) {
                if (in == null)
                    continue;
                CompoundTag nbt = NbtIo.readCompressed(in);
                ListTag blocks = nbt.getList("blocks", 10);
                if (blocks.isEmpty())
                    return null;
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (int i = 0; i < blocks.size(); i++) {
                    ListTag pos = blocks.getCompound(i).getList("pos", 3);
                    minX = Math.min(minX, pos.getInt(0));
                    maxX = Math.max(maxX, pos.getInt(0));
                    minZ = Math.min(minZ, pos.getInt(2));
                    maxZ = Math.max(maxZ, pos.getInt(2));
                }
                return new int[] { maxX - minX + 1, maxZ - minZ + 1 };
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
