package com.solegendary.reignofnether.unit;

import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class FormationDragMove {

    /** Least distance between two slots of a drag formation, along the line and between ranks (blocks). */
    public static final double MIN_SPACING = 1.0;

    private static BlockPos dragStartBp = null;
    private static BlockPos dragEndBp = null;
    private static List<BlockPos> formationTargets = new ArrayList<>();
    private static boolean dragging = false;

    public static boolean isDragging() {
        return dragging;
    }

    public static void startDrag(BlockPos bp) {
        dragStartBp = bp;
        dragEndBp = bp;
        dragging = true;
        formationTargets.clear();
    }

    public static void updateDrag(BlockPos currentBp, int unitCount, Level level) {
        if (!dragging) return;
        dragEndBp = currentBp;
        calculateTargets(unitCount, level);
    }

    public static List<BlockPos> getFormationTargets() {
        return formationTargets;
    }

    public static Vec3 getLineStart() {
        if (dragStartBp == null) return Vec3.ZERO;
        return new Vec3(dragStartBp.getX() + 0.5, dragStartBp.getY() + 1.0, dragStartBp.getZ() + 0.5);
    }

    public static Vec3 getLineEnd() {
        if (dragEndBp == null) return Vec3.ZERO;
        return new Vec3(dragEndBp.getX() + 0.5, dragEndBp.getY() + 1.0, dragEndBp.getZ() + 0.5);
    }

    public static List<Pair<LivingEntity, BlockPos>> endDrag(List<LivingEntity> units) {
        List<Pair<LivingEntity, BlockPos>> result = assignUnitsToPositions(units);
        dragStartBp = null;
        dragEndBp = null;
        formationTargets.clear();
        dragging = false;
        return result;
    }

    public static void cancelDrag() {
        dragStartBp = null;
        dragEndBp = null;
        formationTargets.clear();
        dragging = false;
    }

    private static void calculateTargets(int unitCount, Level level) {
        formationTargets.clear();
        if (dragStartBp == null || dragEndBp == null || unitCount <= 0) return;
        formationTargets.addAll(computeSlots(level, dragStartBp, dragEndBp, unitCount));
    }

    /**
     * The line's slots, side-neutral: evenly spaced centres along start..end (one slot at the end for a single
     * unit), each dropped to the ground. Used by the drag preview and by the attack-move formation GameTest.
     */
    public static List<BlockPos> computeSlots(Level level, BlockPos startBp, BlockPos endBp, int unitCount) {
        List<BlockPos> slots = new ArrayList<>(Math.max(0, unitCount));
        if (unitCount <= 0)
            return slots;
        if (unitCount == 1) {
            slots.add(getHeightAdjustedPos(level, endBp));
            return slots;
        }

        Vec3 start = Vec3.atCenterOf(startBp);
        Vec3 end = Vec3.atCenterOf(endBp);

        // a line too short for its units gets ranks instead of stacking several on one block (the Oct 19 blob: a
        // 20-block drag with 300 units put ~15 in each block): at least MIN_SPACING apart along the line, extra ranks
        // MIN_SPACING apart, alternating both sides of the dragged line so the formation stays centred on it
        double len = Math.sqrt((end.x - start.x) * (end.x - start.x) + (end.z - start.z) * (end.z - start.z));
        int perRank = Math.max(1, (int) Math.floor(len / MIN_SPACING) + 1);
        if (perRank < unitCount && len > 0.5) {
            double ux = (end.x - start.x) / len, uz = (end.z - start.z) / len;
            double nx = -uz, nz = ux;
            java.util.Set<Long> used = new java.util.HashSet<>();
            int placed = 0;
            for (int rank = 0; placed < unitCount; rank++) {
                double off = ((rank + 1) / 2) * MIN_SPACING * (rank % 2 == 1 ? 1 : -1);
                int inRank = Math.min(perRank, unitCount - placed);
                for (int i = 0; i < inRank; i++) {
                    double t = inRank == 1 ? 0.5 : (double) i / (inRank - 1);
                    double x = start.x + (end.x - start.x) * t + nx * off;
                    double z = start.z + (end.z - start.z) * t + nz * off;
                    BlockPos bp = new BlockPos((int) Math.floor(x), startBp.getY(), (int) Math.floor(z));
                    if (!used.add(bp.asLong()))
                        continue;
                    slots.add(getHeightAdjustedPos(level, bp));
                    placed++;
                }
                if (rank > unitCount)
                    break;   // safety: never loop forever on a degenerate line
            }
            while (slots.size() < unitCount)   // degenerate rounding: fill with the line's end
                slots.add(getHeightAdjustedPos(level, endBp));
            return slots;
        }

        for (int i = 0; i < unitCount; i++) {
            double t = (i + 0.5) / unitCount;
            double x = start.x + (end.x - start.x) * t;
            double z = start.z + (end.z - start.z) * t;
            BlockPos bp = new BlockPos((int) Math.floor(x), startBp.getY(), (int) Math.floor(z));
            slots.add(getHeightAdjustedPos(level, bp));
        }
        return slots;
    }

	private static BlockPos getHeightAdjustedPos(Level level, BlockPos bp) {
		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bp.getX(), bp.getZ()) - 1;
		BlockPos checkBp = new BlockPos(bp.getX(), groundY, bp.getZ());
		while (groundY > level.getMinBuildHeight()) {
			BlockState bs = level.getBlockState(checkBp);
			boolean isLeaves = bs.getBlock() instanceof LeavesBlock;
			boolean isPassable = !MiscUtil.isSolidBlocking(level, checkBp) && bs.getFluidState().isEmpty();
			if (!isLeaves && !isPassable) break;
			groundY--;
			checkBp = new BlockPos(bp.getX(), groundY, bp.getZ());
		}
		return new BlockPos(bp.getX(), groundY, bp.getZ());
	}

    private static List<Pair<LivingEntity, BlockPos>> assignUnitsToPositions(List<LivingEntity> units) {
        return assign(units, formationTargets);
    }

    /** Greedy nearest-unit assignment, one unit per slot in line order (side-neutral, game-tested). */
    public static List<Pair<LivingEntity, BlockPos>> assign(List<LivingEntity> units, List<BlockPos> targets) {
        List<Pair<LivingEntity, BlockPos>> result = new ArrayList<>();
        if (units.isEmpty() || targets.isEmpty()) return result;

        List<LivingEntity> unassigned = new ArrayList<>(units);

        for (BlockPos target : targets) {
            LivingEntity closest = null;
            double closestDist = Double.MAX_VALUE;
            for (LivingEntity unit : unassigned) {
                double dist = unit.getOnPos().distSqr(target);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = unit;
                }
            }
            if (closest != null) {
                result.add(new Pair<>(closest, target));
                unassigned.remove(closest);
            }
        }
        return result;
    }
}