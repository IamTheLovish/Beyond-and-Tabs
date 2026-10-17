package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * Where the drawn moving parts sit on a building (design/moving_buildings.md: "draw the machinery, don't wire it"),
 * slice 1: the metal extractor RIG of the Sunforged and Gravebound headframes, tier 1 and tier 2. The structures were
 * rebuilt (Oct 16) as tall open headframes that leave the parts' room as air; this table says where that room is, in
 * each structure's own unrotated block space, exactly as tools/gen_structures.py declares it in SUN_RIG_ANCHORS and
 * GRAVE_RIG_ANCHORS (tools/check_parts.py keeps the two equal; the GameTest
 * every_extractor_rig_part_has_room_to_move keeps the room air in the committed structures). Change a structure, change
 * its row here.
 *
 * Common code (no client classes): the renderer is PartRenderClientEvents; the pure stroke functions below are what it
 * animates with, so the GameTest can pin them.
 */
public final class BuildingParts {
    private BuildingParts() { }

    /** Which faction's materials the renderer dresses a rig in. */
    public enum Skin { SUN, GRAVE }

    /**
     * One extractor rig's anchors. Points are block-space floats (a cell's centre is x + 0.5); cells are block
     * positions, both in the structure's own frame (before the placement's rotation).
     *
     * @param structure   the structure it belongs to (BuildingPlacement.currentStructureName: tier 1 or tier 2)
     * @param hubX        the sheave wheel's axle centre (hubX, hubY, hubZ)
     * @param axis        the axis the wheel turns about (X or Z: it stands in the vertical plane across that axis)
     * @param clearRadius every cell within this distance of the hub, in the wheel's plane, is air
     * @param wheelRadius the rim radius the wheel is drawn at (the rope drops from it); + RIM_OUTSET stays inside the
     *                    clear radius
     * @param axle        the solid block the axle runs into beside the hub (no block, no wheel: it was knocked out)
     * @param skipBottom  the ore skip's shaft, bottom and top cell inclusive (all air): parked on the ore at the foot,
     * @param skipTop     tipping at the top
     * @param drum        the winding drum, whose rope's back fall rises from its top to the wheel's far rim (air); null
     *                    when the chain winds on the axle itself
     * @param pole        the pennant pole's top block
     * @param flagAbove   true: the pennant flies from a staff on the pole's top face (the 3x3 layer above it is air);
     *                    false: it is tied at the tip of a rod and streams beside it (the ring of cells round the rod is
     *                    air)
     * @param tip         the way the skip tips its ore at the top of the shaft
     * @param strokeTicks one hoist stroke (load, hoist, tip, lower): about 2 s, the tier 2 a little busier
     */
    public record Rig(String structure, Skin skin, float hubX, float hubY, float hubZ, Direction.Axis axis,
                      float clearRadius, float wheelRadius, BlockPos axle, BlockPos skipBottom, BlockPos skipTop,
                      BlockPos drum, BlockPos pole, boolean flagAbove, Direction tip, int strokeTicks) { }

    /** How far the drawn rim reaches past wheelRadius (half its depth in the wheel's plane). */
    public static final float RIM_OUTSET = 0.08f;

    private static BlockPos cell(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    // Sunforged (tools/gen_structures.py SUN_RIG_ANCHORS): the sheave wheel is the south face's rose window, its axle
    // in the gold bearing behind the hub (hub cell - z); the skip rides the west lancet and tips into the chute (east);
    // the drum's rope rises to the wheel's east rim; the pennant is tied at the tip of the spire's lightning rod.
    // Gravebound (GRAVE_RIG_ANCHORS): the wheel turns on the west face of the bone winch drum (the axle), the skip's
    // chain winds on that drum, so the shaft runs straight under the hub; the pennant flies over a corner post (T1) or
    // the spire's needle (T2). Hub cells become cell centres here.
    public static final List<Rig> RIGS = List.of(
        new Rig("metal_extractor", Skin.SUN, 2.5f, 6.5f, 4.5f, Direction.Axis.Z, 1.5f, 1.0f,
            cell(2, 6, 3), cell(1, 1, 4), cell(1, 5, 4), cell(3, 1, 4), cell(2, 10, 2), false, Direction.EAST, 48),
        new Rig("metal_extractor_t2", Skin.SUN, 2.5f, 6.5f, 4.5f, Direction.Axis.Z, 1.5f, 1.0f,
            cell(2, 6, 3), cell(1, 1, 4), cell(1, 5, 4), cell(3, 1, 4), cell(2, 13, 2), false, Direction.EAST, 36),
        new Rig("metal_extractor_dark", Skin.GRAVE, 0.5f, 9.5f, 2.5f, Direction.Axis.X, 1.5f, 1.25f,
            cell(1, 9, 2), cell(0, 1, 2), cell(0, 7, 2), null, cell(4, 10, 0), true, Direction.EAST, 48),
        new Rig("metal_extractor_t2_dark", Skin.GRAVE, 0.5f, 11.5f, 2.5f, Direction.Axis.X, 1.5f, 1.25f,
            cell(1, 11, 2), cell(0, 1, 2), cell(0, 9, 2), null, cell(3, 13, 2), true, Direction.EAST, 36)
    );

    /** The rig of a structure, or null. A linear scan of four rows on a string the placement holds (no hashing). */
    public static Rig rigFor(String structure) {
        if (structure == null)
            return null;
        for (int i = 0; i < RIGS.size(); i++) {
            Rig r = RIGS.get(i);
            if (r.structure().equals(structure))
                return r;
        }
        return null;
    }

    // ---- the placement's rotation, as BuildingUtils.getAbsoluteBlockData applies it (BlockPos.rotate, then
    //      origin + (0, 1, 0)); the GameTest checks these against it for all four rotations ----

    /** The yaw (radians, about +Y, right-handed like JOML's rotateY) that turns a structure the way {@code rot} does. */
    public static float yawOf(Rotation rot) {
        return switch (rot == null ? Rotation.NONE : rot) {
            case CLOCKWISE_90 -> -(float) Math.PI / 2;           // (x, z) -> (-z, x)
            case CLOCKWISE_180 -> (float) Math.PI;               // (x, z) -> (-x, -z)
            case COUNTERCLOCKWISE_90 -> (float) Math.PI / 2;     // (x, z) -> (z, -x)
            default -> 0f;
        };
    }

    /** x of a structure cell after the rotation (BlockPos.rotate), before the origin is added. */
    public static int cellX(Rotation rot, int x, int z) {
        return switch (rot == null ? Rotation.NONE : rot) {
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
            default -> x;
        };
    }

    /** z of a structure cell after the rotation (BlockPos.rotate), before the origin is added. */
    public static int cellZ(Rotation rot, int x, int z) {
        return switch (rot == null ? Rotation.NONE : rot) {
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
            default -> z;
        };
    }

    /**
     * x of a structure point after the rotation, before the origin is added: cells turn about their own centres, so a
     * point turns about (0.5, 0.5) - the cell (x, z) lands on cellX/cellZ and a point inside it stays inside it. This
     * is the transform the renderer builds (translate 0.5, rotateY(yawOf), translate -0.5).
     */
    public static double pointX(Rotation rot, double x, double z) {
        double a = yawOf(rot);
        return 0.5 + (x - 0.5) * Math.cos(a) + (z - 0.5) * Math.sin(a);
    }

    /** z of a structure point after the rotation (see pointX). */
    public static double pointZ(Rotation rot, double x, double z) {
        double a = yawOf(rot);
        return 0.5 - (x - 0.5) * Math.sin(a) + (z - 0.5) * Math.cos(a);
    }

    // ---- the hoist's stroke, as fractions of strokeTicks (pure; the renderer and the GameTest share them) ----
    // parked at the foot, filling (0 - LOAD_END); hoisting (- RISE_END); tipping at the top (- TIP_END); lowering
    // (- FALL_END); parked empty (- 1)

    public static final float LOAD_END = 0.14f, RISE_END = 0.44f, TIP_END = 0.58f, FALL_END = 0.88f;

    private static float smooth(float u) {
        return u * u * (3f - 2f * u);
    }

    /** The skip's height along its shaft at stroke fraction f: 0 parked at the foot, 1 at the top; eased both ways. */
    public static float skipLift(float f) {
        if (f < LOAD_END || f >= FALL_END)
            return 0f;
        if (f < RISE_END)
            return smooth((f - LOAD_END) / (RISE_END - LOAD_END));
        if (f < TIP_END)
            return 1f;
        return 1f - smooth((f - TIP_END) / (FALL_END - TIP_END));
    }

    /** How far the skip is tipped, 0 upright to 1 fully over: over and back while it dwells at the top. */
    public static float skipTip(float f) {
        if (f < RISE_END || f >= TIP_END)
            return 0f;
        return (float) Math.sin((f - RISE_END) / (TIP_END - RISE_END) * Math.PI);
    }

    /** The ore in the skip, 0 empty to 1 full: it fills at the foot, rides up, and is gone once the skip tips. */
    public static float loadFill(float f) {
        if (f < LOAD_END)
            return f / LOAD_END;
        if (f < RISE_END)
            return 1f;
        if (f < TIP_END)
            return (f - RISE_END) / (TIP_END - RISE_END) < 0.5f ? 1f : 0f;
        return 0f;
    }
}
