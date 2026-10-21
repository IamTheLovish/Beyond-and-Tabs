package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * Where the drawn moving parts sit on a building (design/moving_buildings.md: "draw the machinery, don't wire it"),
 * slice 1: the metal extractor RIG of the Gravebound Bone-Ribbed Well (Oct 19; the Oct 16 headframes it began with are
 * gone), tier 1 and tier 2. The structures leave the parts' room as air; this table says where that room is, in
 * each structure's own unrotated block space, exactly as tools/gen_structures.py declares it in
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
     * @param flagAbove   true: the pennant flies from a staff on the pole's top face (the 5x5 layer above it is air);
     *                    false: it is tied at the tip of a rod and streams beside it (the 5x5 layer round the rod is
     *                    air). It streams with the world's wind whatever way the building faces, up to 1.7 blocks out.
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

    // Gravebound (GRAVE_RIG_ANCHORS): the Bone-Ribbed Well (Oct 19) - the wheel turns between the ribs on the west face
    // of the bone keystone (the axle), the bucket's chain winds on it, so the shaft runs straight up the middle of the
    // well under the hub; the pennant flies over the north-east spire's finial at both tiers. Hub cells become cell
    // centres here.
    public static final List<Rig> RIGS = List.of(
        new Rig("metal_extractor_dark", Skin.GRAVE, 2.5f, 7.5f, 2.5f, Direction.Axis.X, 1.5f, 1.25f,
            cell(3, 7, 2), cell(2, 1, 2), cell(2, 5, 2), null, cell(4, 9, 0), true, Direction.EAST, 48),
        new Rig("metal_extractor_t2_dark", Skin.GRAVE, 2.5f, 10.5f, 2.5f, Direction.Axis.X, 1.5f, 1.25f,
            cell(3, 10, 2), cell(2, 1, 2), cell(2, 8, 2), null, cell(4, 11, 0), true, Direction.EAST, 36)
    );

    // The Sunforged extractor had a row here (its cathedral headframe's sheave wheel) until the Angel Fountain replaced
    // the headframe (Oct 19, lovish): a fountain has no machinery to draw. Skin.SUN stays for any later Sunforged rig.

    /** The rig of a structure, or null. A linear scan of the rows on a string the placement holds (no hashing). */
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

    // ---- statues: a sculpted figure drawn on a building like a unit model, not built of blocks ----

    /**
     * A statue on a building (lovish, Oct 20, on the White Angel capitol's 13 x 13 block angel: "the angel looks bad" -
     * he chose a real sculpted statue drawn as a model, in stone, on the dome, its wings unfolding as the capitol
     * levels up). The figure is a generated unit-style model (tools/gen_angel_statue.py -> dsl_to_game.py); this row
     * says where it stands, how big, and which structures carry it. Drawn by StatueRenderClientEvents.
     *
     * @param structure the base structure name: the row matches it and its levels ("_l2" .. "_l9", BuildingLevels)
     * @param anchor    the air cell the statue's soles stand in, centred on its floor, in the structure's own frame
     *                  (tools/capitol_white_angel_v5.py STATUE_ANCHOR - tools/check_parts.py keeps the two equal)
     * @param height    blocks from the soles to the top of the figure (its wing tips)
     * @param modelPx   the model's own height in px (soles to wing tips): scale = height * 16 / modelPx
     * @param halfSpan  half the figure's widest reach in blocks (the culling box; the wings)
     */
    public record Statue(String structure, BlockPos anchor, float height, float modelPx, float halfSpan) {
        /** The model's scale: model px to blocks (a unit model is 16 px a block). */
        public float scale() {
            return height / (modelPx / 16f);
        }
    }

    /**
     * The White Angel capitol (tools/capitol_white_angel_v5.py; its structures "capitol_white_angel" and
     * "capitol_white_angel_l2" .. "_l6"): the marble angel v2 (tools/gen_angel_statue.py) on the plinth crowning the
     * dome, 20 blocks from its soles to its wing tips. It faces the way the capitol faces - the structure's front (+z:
     * the hall's gable, the great stair, the gate) - and turns with the placement's rotation (StatueRenderClientEvents:
     * yawOf(rotation)), so a capitol placed facing east has its angel facing east (lovish, Oct 21 14:20: "the angel
     * should be pointing where the capital is pointing"; v2 to v3 r5 turned it 45 degrees to the RTS camera's
     * quarter). The culling radius is the wings' reach in any direction - 12.4 blocks mid-fold (L4) for the mesh angel
     * since round 6, whose spread wings arc forward round the figure.
     */
    public static final List<Statue> STATUES = List.of(
        new Statue("capitol_white_angel", cell(29, 103, 16), 20f, 80.9f, 12.6f)
    );

    /** The statue a structure (or one of its levels) carries, or null. No allocation: a prefix test on the rows. */
    public static Statue statueFor(String structure) {
        if (structure == null)
            return null;
        for (int i = 0; i < STATUES.size(); i++) {
            Statue s = STATUES.get(i);
            String base = s.structure();
            if (structure.equals(base)
                    || (structure.startsWith(base) && structure.length() == base.length() + 3
                        && structure.charAt(base.length()) == '_' && structure.charAt(base.length() + 1) == 'l'
                        && structure.charAt(base.length() + 2) >= '2' && structure.charAt(base.length() + 2) <= '9'))
                return s;
        }
        return null;
    }

    /**
     * The statue's culling box rises this many times the row's height over the anchor: the mesh angel's raised wings
     * (round 10) reach 21.7 blocks where the cube statue's row says 20 (sculpt_angel.check_reach keeps the mesh inside
     * at every fold, and fails CI otherwise).
     */
    public static final float STATUE_BOX_HEADROOM = 1.12f;

    /** How far the wings fold back at most (degrees about the shoulder blade, from the spread rest pose). */
    public static final float WING_FOLD_MAX = 70f;
    /**
     * With the fold: the wing's droop (degrees of roll per degree of fold) and the HAND bone's fold along the forearm
     * (the primaries swing down behind the back) - the three numbers of gen_angel_statue.fold_pose, so the offline
     * preview and the game pose the same statue. At L1 the wings stand closed behind the shoulders; at L6 spread.
     */
    public static final float WING_DROOP = 0.45f, WING_HAND = -1.1f;
    /**
     * The mesh angel's fold (the smooth marble sculpt, tools/sculpt_angel.py, drawn when its .bmesh is loaded) is KEYED:
     * the left wing's and its hand's ModelPart rotations (Minecraft part space, quaternions w x y z) at three keys -
     * SPREAD (the rest, L6: a tall V raised and swept back), RAISED (at MESH_WING_RAISE_AT of WING_FOLD_MAX, which is
     * L2's fold: the wings up behind the shoulders, the primaries half folded) and CLOSED (L1: down the back) - slerped
     * the short way between neighbouring keys; the right side mirrors the left (w x -y -z). Why (lovish, Oct 21 16:13:
     * "the wings read lopsided from the RTS camera, one up, one sideways"): the RTS camera looks down 50 degrees from a
     * diagonal, so a point's screen height is 0.64 y + 0.54 (x + z) - a wing reaching sideways OR back is sheared up
     * on one side of the figure and pressed flat on the other; only an upward reach reads the same from all four
     * diagonals. r6-r9's one straight sweep to the closed pose swung the wings out sideways at L2-L5. Solved by
     * sculpt_angel._fold_targets (its main() fails CI when these drift); meshFold turns them into part angles.
     */
    public static final float MESH_WING_RAISE_AT = 0.80000f;
    public static final float MESH_WING_SPREAD_W = 0.96954f,
        MESH_WING_SPREAD_X = 0.02985f,
        MESH_WING_SPREAD_Y = -0.15356f,
        MESH_WING_SPREAD_Z = -0.18846f;
    public static final float MESH_WING_RAISED_W = 0.80364f,
        MESH_WING_RAISED_X = 0.22824f,
        MESH_WING_RAISED_Y = -0.32755f,
        MESH_WING_RAISED_Z = -0.44133f;
    public static final float MESH_WING_CLOSED_W = 0.77904f,
        MESH_WING_CLOSED_X = -0.06956f,
        MESH_WING_CLOSED_Y = -0.50849f,
        MESH_WING_CLOSED_Z = 0.36014f;
    public static final float MESH_HAND_SPREAD_W = 0.99027f,
        MESH_HAND_SPREAD_X = 0.00000f,
        MESH_HAND_SPREAD_Y = 0.13917f,
        MESH_HAND_SPREAD_Z = 0.00000f;
    public static final float MESH_HAND_RAISED_W = 0.86603f,
        MESH_HAND_RAISED_X = -0.05982f,
        MESH_HAND_RAISED_Y = 0.09540f,
        MESH_HAND_RAISED_Z = 0.48716f;
    public static final float MESH_HAND_CLOSED_W = 0.50000f,
        MESH_HAND_CLOSED_X = -0.10360f,
        MESH_HAND_CLOSED_Y = 0.16525f,
        MESH_HAND_CLOSED_Z = 0.84378f;
    /** One unfolding after a level-up, in ticks (6 s), and the slow breath of the wings (40 s a cycle). */
    public static final int WING_UNFOLD_TICKS = 120, WING_BREATH_TICKS = 800;

    /** How open the wings stand at a capitol level: closed on the L1 tower, fully spread on the L6 crown. */
    public static float wingOpenAt(int level) {
        int lv = Math.max(1, Math.min(6, level));
        return 0.2f * (lv - 1);
    }

    /**
     * The wings' fold in degrees (0 = spread as modelled, WING_FOLD_MAX = folded back against the back): eased from the
     * level before to this level's openness over WING_UNFOLD_TICKS after a level-up (sinceLevelUp ticks ago; a large
     * value once settled), and breathing +-1.2 degrees over WING_BREATH_TICKS (t: the clock, ticks).
     */
    public static float statueWingFold(int level, float sinceLevelUp, float t) {
        float to = wingOpenAt(level);
        float from = wingOpenAt(level - 1);
        float u = Math.max(0f, Math.min(1f, sinceLevelUp / WING_UNFOLD_TICKS));
        float open = from + (to - from) * smooth(u);
        float breath = 1.2f * (float) Math.sin(t / WING_BREATH_TICKS * 2 * Math.PI);
        return Math.max(0f, Math.min(WING_FOLD_MAX, (1f - open) * WING_FOLD_MAX + breath + 1.2f));
    }

    private static final float[][] MESH_WING_KEYS = {
        {MESH_WING_SPREAD_W, MESH_WING_SPREAD_X, MESH_WING_SPREAD_Y, MESH_WING_SPREAD_Z},
        {MESH_WING_RAISED_W, MESH_WING_RAISED_X, MESH_WING_RAISED_Y, MESH_WING_RAISED_Z},
        {MESH_WING_CLOSED_W, MESH_WING_CLOSED_X, MESH_WING_CLOSED_Y, MESH_WING_CLOSED_Z}};
    private static final float[][] MESH_HAND_KEYS = {
        {MESH_HAND_SPREAD_W, MESH_HAND_SPREAD_X, MESH_HAND_SPREAD_Y, MESH_HAND_SPREAD_Z},
        {MESH_HAND_RAISED_W, MESH_HAND_RAISED_X, MESH_HAND_RAISED_Y, MESH_HAND_RAISED_Z},
        {MESH_HAND_CLOSED_W, MESH_HAND_CLOSED_X, MESH_HAND_CLOSED_Y, MESH_HAND_CLOSED_Z}};

    /**
     * The mesh angel's LEFT wing (hand = false) or its hand's ModelPart angles (xRot, yRot, zRot in radians, for
     * rotationZYX) at a fold of {@code foldDeg} degrees (statueWingFold): the keys slerped the short way - exactly
     * sculpt_angel.keyed_quat, so the sheets show what the game draws. The right side is (x, -y, -z). No allocation:
     * the angles go into {@code out}.
     */
    public static void meshFold(boolean hand, float foldDeg, float[] out) {
        float[][] k = hand ? MESH_HAND_KEYS : MESH_WING_KEYS;
        float fr = MESH_WING_RAISE_AT * WING_FOLD_MAX;
        float[] a, b;
        float t;
        if (foldDeg <= fr) {
            a = k[0];
            b = k[1];
            t = Math.max(0f, foldDeg / fr);
        } else {
            a = k[1];
            b = k[2];
            t = Math.min(1f, (foldDeg - fr) / (WING_FOLD_MAX - fr));
        }
        float c = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        float sb = 1f;
        if (c < 0f) {                    // the short way round
            c = -c;
            sb = -1f;
        }
        float wa, wb;
        if (c > 0.9995f) {               // nearly equal: a normalised lerp
            wa = 1f - t;
            wb = t * sb;
        } else {
            float th = (float) Math.acos(c), s = (float) Math.sin(th);
            wa = (float) Math.sin((1f - t) * th) / s;
            wb = (float) Math.sin(t * th) / s * sb;
        }
        float w = wa * a[0] + wb * b[0], x = wa * a[1] + wb * b[1], y = wa * a[2] + wb * b[2], z = wa * a[3] + wb * b[3];
        float n = (float) Math.sqrt(w * w + x * x + y * y + z * z);
        w /= n;
        x /= n;
        y /= n;
        z /= n;
        // the matrix entries rotationZYX's angles come from (Rz Ry Rx: m20 = -sin y, m21 / m22 and m10 / m00)
        float m00 = 1f - 2f * (y * y + z * z), m10 = 2f * (x * y + z * w), m20 = 2f * (x * z - y * w);
        float m21 = 2f * (y * z + x * w), m22 = 1f - 2f * (x * x + y * y);
        out[0] = (float) Math.atan2(m21, m22);
        out[1] = (float) Math.asin(Math.max(-1f, Math.min(1f, -m20)));
        out[2] = (float) Math.atan2(m10, m00);
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
