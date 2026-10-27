package com.solegendary.reignofnether.unit.pathfinding;

import com.solegendary.reignofnether.blocks.TidepoolBlock;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

// classifies one cell (x, y, z) as LAND / WATER / SHALLOW_WATER / FIRE / LAVA / SLIME / TIDEPOOL / BLOCKED.
// a cell is occupiable when feet (y) and head (y+1) are non-solid AND the floor (y-1) supports.
public final class WalkabilityBuilder {
    private WalkabilityBuilder() {}

    public static final byte KIND_BLOCKED = 0;
    public static final byte KIND_LAND    = 1;
    public static final byte KIND_WATER   = 2;
    public static final byte KIND_FIRE    = 3;
    public static final byte KIND_LAVA    = 4;
    public static final byte KIND_SLIME    = 5;
    // Tidewrought tidepool on solid ground: walkable for every mobility class (MobilityClass.costFor), only cheaper
    // for TIDAL units - so a pool can never wall off a choke, and a stale (not yet reclassified) cell reading as
    // LAND is just as walkable. PathConverter maps it to WALKABLE.
    public static final byte KIND_TIDEPOOL = 6;
    // Wading water: the feet cell holds water, the floor under it is solid ground and the head is out of the water -
    // a ford, a 1-deep pool, a river's shallow edge. Before this every wet cell was KIND_WATER at one price, so a ford
    // cost as much as the deep channel beside it and A* crossed wherever the river was narrowest, swimming the deep
    // part. Priced per class in MobilityClass (PathfinderConfig.SHALLOW_WATER_COST for land units). Code that asks
    // "is this water" uses isWater, which reads both kinds.
    public static final byte KIND_SHALLOW_WATER = 7;

    /** Whether a cell kind is water of any depth (wading or deep). */
    public static boolean isWater(byte kind) {
        return kind == KIND_WATER || kind == KIND_SHALLOW_WATER;
    }

    // hot path (per cell during a chunk build); reuse mutable positions to avoid BlockPos allocations.
    private static final ThreadLocal<BlockPos.MutableBlockPos> FEET  = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static final ThreadLocal<BlockPos.MutableBlockPos> HEAD  = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static final ThreadLocal<BlockPos.MutableBlockPos> FLOOR = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static final ThreadLocal<BlockPos.MutableBlockPos> CROWN = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    public static byte classify(Level level, int wx, int y, int wz) {
        BlockPos.MutableBlockPos feet  = FEET.get().set(wx, y, wz);
        BlockPos.MutableBlockPos head  = HEAD.get().set(wx, y + 1, wz);
        BlockPos.MutableBlockPos floor = FLOOR.get().set(wx, y - 1, wz);

        BlockState feetBs = level.getBlockState(feet);
        BlockState headBs = level.getBlockState(head);
        // feet/head can't sit inside a body-blocking block. leaves report non-solid but block the body, and
        // fence-likes are impassable barriers (see isFenceLike), so reject both explicitly. The tag, not
        // BlockUtils.isLeafBlock: its List.of fallback allocated for every non-leaf cell of a chunk build, and the
        // blocks only it lists (mushroom / wart blocks) are full cubes that blocksBody rejects anyway.
        if (blocksBody(level, feet, feetBs, false) || feetBs.is(BlockTags.LEAVES) || isFenceLike(feetBs)) return KIND_BLOCKED;
        if (blocksBody(level, head, headBs, true) || headBs.is(BlockTags.LEAVES) || isFenceLike(headBs)) return KIND_BLOCKED;
        if (raisedIntoCrown(level, feet, feetBs, wx, y, wz)) return KIND_BLOCKED;

        BlockState floorBs = level.getBlockState(floor);
        if (floorBs.getBlock() == Blocks.WATER && feetBs.getBlock() == Blocks.LILY_PAD)
            return KIND_LAND;
        // before the fluid checks (a tidepool has no fluid state anyway); without solid support it falls through to
        // the ordinary floor rules below
        if (feetBs.getBlock() instanceof TidepoolBlock && MiscUtil.isSolidBlocking(level, floor)
                && !isFenceLike(floorBs) && !floorBs.is(BlockTags.LEAVES) && !isPitFloor(floorBs))
            return KIND_TIDEPOOL;

        FluidState feetFluid = feetBs.getFluidState();
        if (feetFluid.is(FluidTags.LAVA)) return KIND_LAVA;
        if (!feetFluid.isEmpty())
            return isWadingDepth(level, floor, floorBs, headBs) ? KIND_SHALLOW_WATER : KIND_WATER;

        if (floorBs.getBlock() == Blocks.SLIME_BLOCK) return KIND_SLIME;
        if (feetBs.getBlock() == Blocks.FIRE || feetBs.getBlock() == Blocks.SOUL_FIRE) return KIND_FIRE;
        if (floorBs.getBlock() == Blocks.MAGMA_BLOCK
                || floorBs.getBlock() == Blocks.CAMPFIRE
                || floorBs.getBlock() == Blocks.SOUL_CAMPFIRE) return KIND_FIRE;

        // a fence/wall/closed-gate floor isn't standable (can't perch on a railing; vanilla agrees), and a leaf
        // floor must not count either or units path up over the tree canopy (and goal snapping lands a worker on
        // the very leaf it's told to mine). don't count these as support: the cell falls through to "no floor" so
        // the unit routes around and stands on the real ground below. BlockTags.LEAVES is allocation-free, unlike
        // BlockUtils.isLeafBlock whose List.of fallback would allocate on this hot path for every solid floor.
        // a cauldron or composter is hollow: its rim reads as a solid floor, but a unit "standing" on it drops into the
        // bowl and its 0.75-block lip is taller than a unit's step - the Circle of Elders' and Heart of the Wild's
        // moonwell basins swallowed every unit trained there (they spawned on top) or routed over them. Not support.
        if (MiscUtil.isSolidBlocking(level, floor) && !isFenceLike(floorBs) && !floorBs.is(BlockTags.LEAVES)
                && !isPitFloor(floorBs)) return KIND_LAND;
        if (floorBs.getFluidState().is(FluidTags.LAVA)) return KIND_LAVA;
        if (!floorBs.getFluidState().isEmpty()) return KIND_WATER;
        return KIND_BLOCKED; // no floor support
    }

    // A wet feet cell is wading depth when the body stands on real ground (the same support rule as a dry floor) with
    // its head out of the water. Head under water = submerged, water under the feet = swimming: both deep. Reads only
    // the states classify already fetched, plus the floor's solidity.
    private static boolean isWadingDepth(Level level, BlockPos floor, BlockState floorBs, BlockState headBs) {
        if (!headBs.getFluidState().isEmpty()) return false;
        return MiscUtil.isSolidBlocking(level, floor) && !isFenceLike(floorBs) && !floorBs.is(BlockTags.LEAVES)
            && !isPitFloor(floorBs);
    }

    // Half the width of the narrowest unit body (0.6: villagers, zombies, cats), and how tall a thing in the feet cell
    // may be before it is a wall rather than something stepped over (a mob steps 0.6; a standing lantern is 0.5625 and
    // vanilla's own pathfinder won't walk a mob onto one, so the line sits under it). Units walk from cell centre to
    // cell centre, so what matters is whether a collision box crosses that centre line, not how big it is.
    static final double BODY_HALF = 0.3, STEP_OVER = 0.5;

    /** As {@link #blocksBody(BlockGetter, BlockPos, BlockState, boolean)} for a feet cell. */
    public static boolean blocksBody(BlockGetter level, BlockPos pos, BlockState bs) {
        return blocksBody(level, pos, bs, false);
    }

    // Whether a block in the feet/head cell stops a unit's body. "Solid" alone (MiscUtil.isSolidBlocking) misses blocks
    // vanilla flags forceSolidOff though they have a full collision box - azalea and flowering azalea bushes (the old
    // PLANT material): the pathfinder routed straight through the Heartwood Hall's, Grove's and Heart of the Wild's
    // bushes and units pushed into them forever. It also misses THIN things with a real collision box through the
    // middle of the cell: a hanging lantern or chain at head height, an end rod, a line of bars or panes, a lightning
    // rod. The body (0.6 wide, on the cell's centre line) can't get past those either - units pushed at a lantern over
    // a doorway forever, and a unit that had stepped down beside one couldn't jump back up under it. So a non-solid
    // block blocks when its collision box crosses the centre of the cell and, in the feet cell, is taller than half a
    // block (a flower pot or a candle is stepped over; a carpet's 1/16 and a snow layer are stood on - see
    // raisedIntoCrown for what that lift does to the head room). Anything
    // crossing the head cell blocks. Things beside the centre line (a wall-side pane,
    // a bud on a side face) don't. VoxelShape.min/max read the shape without allocating; per cell of a chunk build.
    public static boolean blocksBody(BlockGetter level, BlockPos pos, BlockState bs, boolean head) {
        if (bs.isAir())
            return false;
        VoxelShape shape = bs.getCollisionShape(level, pos);
        if (shape.isEmpty())
            return false;
        if (bs.isSolid())
            return true;
        boolean crossesCentre = shape.max(Direction.Axis.X) > 0.5 - BODY_HALF && shape.min(Direction.Axis.X) < 0.5 + BODY_HALF
            && shape.max(Direction.Axis.Z) > 0.5 - BODY_HALF && shape.min(Direction.Axis.Z) < 0.5 + BODY_HALF;
        if (!crossesCentre)
            return false;
        return head || shape.max(Direction.Axis.Y) > STEP_OVER;
    }

    // The tallest ground units (villagers, illagers, zombies) are 1.95 tall: two empty cells hold them with 0.05 to
    // spare. A thin floor in the feet cell - a carpet (1/16), a snow layer - lifts the body by its height, and once the
    // lift passes that slack the head reaches into the cell ABOVE the head cell, which classify otherwise never reads.
    // The Sunforged keep's gate (barracks L2/L3: a carpet under upside-down stairs) was 1.94 high; the grid called it
    // open, units trained in the yard pathed into it forever and never left (lovish's clip, Oct 14 16:52).
    static final double BODY_HEIGHT = 1.95;

    /** True when a thin floor in the feet cell lifts a {@link #BODY_HEIGHT} body into a collision box over its head. */
    static boolean raisedIntoCrown(BlockGetter level, BlockPos feet, BlockState feetBs, int wx, int y, int wz) {
        if (feetBs.isAir())
            return false;
        VoxelShape floorShape = feetBs.getCollisionShape(level, feet);
        if (floorShape.isEmpty())
            return false;
        double lift = floorShape.max(Direction.Axis.Y);
        if (lift + BODY_HEIGHT <= 2.0)
            return false;
        BlockPos.MutableBlockPos crown = CROWN.get().set(wx, y + 2, wz);
        BlockState crownBs = level.getBlockState(crown);
        if (crownBs.isAir())
            return false;
        VoxelShape c = crownBs.getCollisionShape(level, crown);
        if (c.isEmpty())
            return false;
        boolean crossesCentre = c.max(Direction.Axis.X) > 0.5 - BODY_HALF && c.min(Direction.Axis.X) < 0.5 + BODY_HALF
            && c.max(Direction.Axis.Z) > 0.5 - BODY_HALF && c.min(Direction.Axis.Z) < 0.5 + BODY_HALF;
        return crossesCentre && lift + BODY_HEIGHT > 2.0 + c.min(Direction.Axis.Y);
    }

    // hollow blocks a unit falls into rather than stands on (see classify). instanceof only: this is on the hot path.
    public static boolean isPitFloor(BlockState bs) {
        return bs.getBlock() instanceof AbstractCauldronBlock || bs.getBlock() instanceof ComposterBlock;
    }

    // fences, walls and closed fence gates are 1.5-block barriers units can't pass or stand on (open gates are
    // walkable, so excluded). mirrors vanilla WalkNodeEvaluator's BlockPathTypes.FENCE handling.
    public static boolean isFenceLike(BlockState bs) {
        if (bs.is(BlockTags.FENCES) || bs.is(BlockTags.WALLS) || bs.getBlock() instanceof SculkCatalystBlock || bs.getBlock() instanceof SculkShriekerBlock) return true;
        return bs.getBlock() instanceof FenceGateBlock && !bs.getValue(FenceGateBlock.OPEN);
    }
}
