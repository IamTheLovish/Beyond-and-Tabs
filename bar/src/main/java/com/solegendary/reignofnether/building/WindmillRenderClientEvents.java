package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The energy buildings' moving parts (design/lively_buildings.md). Mills with a rotor declare their hub
 * (WindGenerator.SAIL_HUBS) and get four cloth blades spun round it; the others declare a WindGenerator.Motion - the
 * Tidewrought waterwheel and Brassworks flywheel (WHEEL), the Gravebound soul-bell (BELL), the Verdant spirit tree's
 * petals (PETALS) and the Stormcallers' jade vane crown (VANES); a capitol may declare one too (CapitolLife: the
 * Mausoleum's soul bell). tools/check_sail_hubs.py and the GameTests keep each
 * anchor on its block. Pure client-side rendering - no block updates, no server cost: every anchor is worked out once
 * per placement (rotated with it), drawn only once the building stands, never in strategic zoom, and culled by
 * distance; the per-frame loop allocates nothing (PoseStack push/pop reuses its entries).
 */
public class WindmillRenderClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    private static final float SPIN_RADS_PER_TICK = 0.045f;  // a lazy ~8s per turn (at the average wind)
    private static float lastFrameTime = -1;
    private static double windClock = 0;
    private static final float BLADE_WIDTH = 0.8f;    // wider sails: at 0.52 they read as thin sticks from the RTS camera
    // playtest (Oct 10): seen from the RTS camera a vertical rotor is nearly edge-on and the sails looked like
    // broken sticks that didn't turn. Tilt the rotor back so its face points up towards the camera.
    private static final float ROTOR_TILT_DEG = 40f;
    private static final float BLADE_THICKNESS = 0.14f;
    private static final double MAX_RENDER_DIST_SQR = 220 * 220;
    // a bell and a few petals are a handful of pixels past this: not worth their draw calls
    private static final double SMALL_PART_DIST_SQR = 96 * 96;
    private static final int PETALS = 8;
    private static final int PETAL_CYCLE_TICKS = 160;

    private enum Kind { SAILS, WHEEL, BELL, PETALS, VANES }

    /**
     * A building's moving part in world space, worked out once per placement: the anchor block, the face/axis, the
     * size (blade length / wheel radius / swing / spiral radius / vane length), its materials, its phase (so a wind
     * farm doesn't march in lockstep), and for a wheel its spokes and whether it has paddles, how fast it turns.
     */
    private record Part(Kind kind, BlockPos pos, Direction outward, float size, BlockState a, BlockState b,
                        BlockState c, float phase, int spokes, float speed, float fall) { }
    private static final Map<BuildingPlacement, Part> PARTS = new WeakHashMap<>();
    private static final Part NONE = new Part(Kind.SAILS, BlockPos.ZERO, Direction.NORTH, 0, null, null, null, 0, 0, 0, 0);

    /**
     * The world anchor of a structure-relative position: rotated with the placement exactly as
     * BuildingUtils.getAbsoluteBlockData rotates the structure (BlockPos.rotate, then origin + (0, 1, 0)).
     */
    private static BlockPos worldPos(BuildingPlacement placement, BlockPos rel, Rotation rot) {
        BlockPos r = rel.rotate(rot);
        return placement.originPos.offset(r.getX(), r.getY() + 1, r.getZ());
    }

    private static Part partOf(BuildingPlacement placement, WindGenerator mill) {
        Part part = PARTS.get(placement);
        if (part != null)
            return part;
        if (placement.originPos == null)
            return NONE;
        Rotation rot = placement.rotation == null ? Rotation.NONE : placement.rotation;
        WindGenerator.SailHub sh = mill.getSailHub();
        WindGenerator.Motion mo = mill.getMotion();
        if (sh != null) {
            BlockPos pos = worldPos(placement, sh.pos(), rot);
            // each faction flies its own sails: Sunforged white canvas on gilded spars, the Ashborn brown hides on
            // bronze spars (their kit has no bone - tools/eco_ashborn.py)
            BlockState cloth, arm;
            if ("_nether".equals(mill.variant)) {
                cloth = Blocks.BROWN_WOOL.defaultBlockState();
                arm = Blocks.WAXED_CUT_COPPER.defaultBlockState();
            } else {
                cloth = Blocks.WHITE_WOOL.defaultBlockState();
                arm = Blocks.GOLD_BLOCK.defaultBlockState();
            }
            part = new Part(Kind.SAILS, pos, rot.rotate(sh.outward()), sh.length(), cloth, arm, null,
                phaseOf(pos), 4, SPIN_RADS_PER_TICK, 0);
        } else if (mo != null) {
            BlockPos pos = worldPos(placement, mo.pos(), rot);
            Direction out = rot.rotate(mo.outward());
            float phase = phaseOf(pos);
            part = switch (mo.kind()) {
                // Tidewrought: dark oak rim and spokes with spruce paddles, slow like a mill on the tide;
                // Brassworks: an iron flywheel with a brass rim, spinning fast
                case WHEEL -> "_brass".equals(mill.variant)
                    ? new Part(Kind.WHEEL, pos, out, mo.size(), Blocks.IRON_BLOCK.defaultBlockState(),
                        Blocks.WAXED_CUT_COPPER.defaultBlockState(), null, phase, 6, 0.16f, 0)
                    : new Part(Kind.WHEEL, pos, out, mo.size(), Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                        Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), phase, 8, 0.03f, 0);
                // Gravebound: a bone bell with a soul-lantern clapper
                case BELL -> new Part(Kind.BELL, pos, out, mo.size(), Blocks.BONE_BLOCK.defaultBlockState(),
                    Blocks.SOUL_LANTERN.defaultBlockState(), null, phase, 0, 0.07f, 0);
                // Verdant: pink and white petals falling from the crown to the pad
                case PETALS -> new Part(Kind.PETALS, pos, out, mo.size(), Blocks.PINK_WOOL.defaultBlockState(),
                    Blocks.WHITE_WOOL.defaultBlockState(), null, phase, 0, 0.02f, mo.pos().getY() - 0.9f);   // down to just above the pad
                // Stormcallers: red-lacquer arms carrying jade cups
                case VANES -> new Part(Kind.VANES, pos, out, mo.size(), Blocks.RED_TERRACOTTA.defaultBlockState(),
                    Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState(), null, phase, 4, 0.08f, 0);
            };
        } else {
            part = NONE;
        }
        PARTS.put(placement, part);
        return part;
    }

    /**
     * A capitol's moving part (CapitolLife.MOTIONS), worked out once per placement like a mill's. Only the Mausoleum
     * has one: its soul belfry swings a bone bell with a soul-lantern clapper, the Gravebound soul-bell tower's own.
     */
    private static Part capitolPartOf(BuildingPlacement placement) {
        Part part = PARTS.get(placement);
        if (part != null)
            return part;
        WindGenerator.Motion mo = CapitolLife.motionOf(placement);
        if (mo == null || placement.originPos == null || mo.kind() != WindGenerator.MotionKind.BELL) {
            part = NONE;
        } else {
            Rotation rot = placement.rotation == null ? Rotation.NONE : placement.rotation;
            BlockPos pos = worldPos(placement, mo.pos(), rot);
            part = new Part(Kind.BELL, pos, rot.rotate(mo.outward()), mo.size(), Blocks.BONE_BLOCK.defaultBlockState(),
                Blocks.SOUL_LANTERN.defaultBlockState(), null, phaseOf(pos), 0, 0.06f, 0);
        }
        PARTS.put(placement, part);
        return part;
    }

    private static float phaseOf(BlockPos pos) {
        return (pos.hashCode() & 255) / 255f * Mth.TWO_PI;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.BUILDING_DECOR);
        try {
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
                return;
            if (MC.level == null || MC.cameraEntity == null)
                return;
            // at strategic zoom the moving parts are subpixel; skip them entirely (zoom-out performance)
            if (com.solegendary.reignofnether.orthoview.StrategicViewClientEvents.isStrategicView())
                return;

            PoseStack matrix = evt.getPoseStack();
            // the RENDER camera: in the RTS view the camera entity (the player) is nowhere near it, and translating by the
            // entity's position is what made the sails float off their towers by a camera-dependent amount
            Vec3 cam = evt.getCamera().getPosition();
            BlockRenderDispatcher renderer = MC.getBlockRenderer();
            MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
            float time = MC.level.getGameTime() + evt.getPartialTick();
            // wind-driven parts run on a wind clock (resources/Wind): it advances faster in a strong wind and slower in a
            // calm one, so the sails speed up and slow down smoothly instead of jumping when the wind changes
            float dt = lastFrameTime < 0 ? 0 : Math.max(0, Math.min(5, time - lastFrameTime));
            lastFrameTime = time;
            windClock += dt * com.solegendary.reignofnether.resources.Wind.multiplier();
            if (windClock > 1.0e6)
                windClock -= 1.0e6;   // keep float precision; a jump every ~14 hours of play is invisible
            boolean renderedAny = false;
            net.minecraft.client.renderer.culling.Frustum frustum = evt.getFrustum();

            for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
                if (!placement.isBuilt || !placement.isExploredClientside)
                    continue;
                Part p;
                if (placement.getBuilding() instanceof WindGenerator mill)
                    p = partOf(placement, mill);
                else if (placement.isCapitol)
                    p = capitolPartOf(placement);
                else
                    continue;
                if (p == NONE)
                    continue;
                BlockPos at = p.pos();
                double d2 = cam.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
                boolean small = p.kind() == Kind.BELL || p.kind() == Kind.PETALS;
                if (d2 > (small ? SMALL_PART_DIST_SQR : MAX_RENDER_DIST_SQR))
                    continue;
                // off screen: no block-state read, no light lookup, no part boxes (sails reach ~p.size() from the hub)
                // (petals fall from the crown to the pad, so they get a taller reach)
                double reach = p.kind() == Kind.PETALS ? 40.0 : Math.max(4.0, p.size() + 1.0);
                if (frustum != null && !frustum.isVisible(new AABB(at.getX() + 0.5 - reach, at.getY() + 0.5 - reach,
                        at.getZ() + 0.5 - reach, at.getX() + 0.5 + reach, at.getY() + 0.5 + reach, at.getZ() + 0.5 + reach)))
                    continue;
                // only on a standing building: a site being rebuilt (or the anchor knocked out) has nothing to move;
                // a bell hangs in an empty cell from the beam above it
                if (MC.level.getBlockState(p.kind() == Kind.BELL ? at.above() : at).isAir())
                    continue;
                int light = LevelRenderer.getLightColor(MC.level, p.kind() == Kind.BELL ? at : at.above());
                float t = placement.getBuilding() instanceof WindGenerator wg && wg.isWindDriven() ? (float) windClock : time;
                switch (p.kind()) {
                    case SAILS -> renderSails(p, matrix, cam, renderer, buffers, t, light);
                    case WHEEL -> renderWheel(p, matrix, cam, renderer, buffers, t, light);
                    case BELL -> renderBell(p, matrix, cam, renderer, buffers, t, light);
                    case PETALS -> renderPetals(p, matrix, cam, renderer, buffers, t, light);
                    case VANES -> renderVanes(p, matrix, cam, renderer, buffers, t, light);
                }
                renderedAny = true;
            }
            if (renderedAny)
                buffers.endBatch();
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    /** A unit block scaled to a box: x from x0 (width w), y from y0 (height h), z from z0 (depth d), in the current pose. */
    private static void box(PoseStack m, BlockRenderDispatcher r, MultiBufferSource buffers, BlockState state, int light,
                            float x0, float y0, float z0, float w, float h, float d) {
        m.pushPose();
        m.translate(x0, y0, z0);
        m.scale(w, h, d);
        r.renderSingleBlock(state, m, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        m.popPose();
    }

    private static void renderSails(Part p, PoseStack matrix, Vec3 cam, BlockRenderDispatcher renderer,
                                    MultiBufferSource buffers, float time, int light) {
        BlockPos hub = p.pos();
        Direction.Axis axis = p.outward().getAxis();
        float bladeLength = p.size();
        // rotor plane sits just outside the hub's outward face
        double outward = p.outward().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.95 : -0.95;
        float angle = time * p.speed() + p.phase();

        matrix.pushPose();
        matrix.translate(
            hub.getX() + 0.5 + (axis == Direction.Axis.X ? outward : 0) - cam.x,
            hub.getY() + 0.5 - cam.y,
            hub.getZ() + 0.5 + (axis == Direction.Axis.Z ? outward : 0) - cam.z);
        // lean the rotor back (top towards the tower) so the sails' face points up at the sky and the camera
        if (axis == Direction.Axis.Z)
            matrix.mulPose(com.mojang.math.Axis.XP.rotationDegrees(outward > 0 ? -ROTOR_TILT_DEG : ROTOR_TILT_DEG));   // +X turns +Y toward +Z
        else
            matrix.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(outward > 0 ? ROTOR_TILT_DEG : -ROTOR_TILT_DEG));   // +Z turns +Y toward -X

        for (int i = 0; i < 4; i++) {
            matrix.pushPose();
            if (axis == Direction.Axis.Z)
                matrix.mulPose(com.mojang.math.Axis.ZP.rotation(angle + i * Mth.HALF_PI));
            else
                matrix.mulPose(com.mojang.math.Axis.XP.rotation(angle + i * Mth.HALF_PI));
            // the cloth sail: a long box reaching out from the hub along local +Y, and the spar up its middle
            if (axis == Direction.Axis.Z) {
                box(matrix, renderer, buffers, p.a(), light, -BLADE_WIDTH / 2, 0.35f, -BLADE_THICKNESS / 2,
                    BLADE_WIDTH, bladeLength, BLADE_THICKNESS);
                box(matrix, renderer, buffers, p.b(), light, -0.09f, 0f, -BLADE_THICKNESS / 2 - 0.02f,
                    0.18f, bladeLength + 0.4f, BLADE_THICKNESS + 0.04f);
            } else {
                box(matrix, renderer, buffers, p.a(), light, -BLADE_THICKNESS / 2, 0.35f, -BLADE_WIDTH / 2,
                    BLADE_THICKNESS, bladeLength, BLADE_WIDTH);
                box(matrix, renderer, buffers, p.b(), light, -BLADE_THICKNESS / 2 - 0.02f, 0f, -0.09f,
                    BLADE_THICKNESS + 0.04f, bladeLength + 0.4f, 0.18f);
            }
            matrix.popPose();
        }
        matrix.popPose();
    }

    /** Moves the pose to the centre of the anchor's outward face (+ off) and turns the frame so local -Z points
     *  outward, away from the wall (whatever the placement's rotation): a wheel's paddles reach out, never into it. */
    private static void faceFrame(Part p, PoseStack matrix, Vec3 cam, float off) {
        BlockPos at = p.pos();
        Direction out = p.outward();
        matrix.translate(at.getX() + 0.5 + out.getStepX() * (0.5 + off) - cam.x,
            at.getY() + 0.5 - cam.y,
            at.getZ() + 0.5 + out.getStepZ() * (0.5 + off) - cam.z);
        // rotation about +Y by t sends local +Z to (sin t, 0, cos t): NORTH 0, SOUTH 180, WEST 90, EAST -90
        float yaw = switch (out) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };
        if (yaw != 0f)
            matrix.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
    }

    /**
     * A wheel turning in the vertical plane just outside its axle: spokes, a polygon rim tangent at the spoke ends,
     * and (the waterwheel) a paddle board on every spoke, reaching back along the axle toward the wall.
     */
    private static void renderWheel(Part p, PoseStack matrix, Vec3 cam, BlockRenderDispatcher renderer,
                                    MultiBufferSource buffers, float time, int light) {
        float r = p.size();
        int n = p.spokes();
        float seg = 2 * r * (float) Math.tan(Math.PI / n) + 0.04f;
        float angle = time * p.speed() + p.phase();
        boolean paddles = p.c() != null;
        matrix.pushPose();
        faceFrame(p, matrix, cam, 0.12f);
        matrix.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
        box(matrix, renderer, buffers, p.b(), light, -0.22f, -0.22f, -0.12f, 0.44f, 0.44f, 0.24f);   // the hub boss
        for (int i = 0; i < n; i++) {
            matrix.pushPose();
            matrix.mulPose(com.mojang.math.Axis.ZP.rotation(i * Mth.TWO_PI / n));
            box(matrix, renderer, buffers, p.a(), light, -0.07f, 0.1f, -0.07f, 0.14f, r - 0.1f, 0.14f);   // spoke
            box(matrix, renderer, buffers, p.b(), light, -seg / 2, r - 0.08f, -0.1f, seg, 0.16f, 0.2f);  // rim
            if (paddles)
                box(matrix, renderer, buffers, p.c(), light, -0.05f, r - 0.62f, -0.5f, 0.1f, 0.72f, 0.56f);
            matrix.popPose();
        }
        matrix.popPose();
    }

    /** A bell swinging from the top of its empty cell, a soul-lantern clapper lagging behind the swing. */
    private static void renderBell(Part p, PoseStack matrix, Vec3 cam, BlockRenderDispatcher renderer,
                                   MultiBufferSource buffers, float time, int light) {
        BlockPos at = p.pos();
        float swing = p.size() * Mth.sin(time * p.speed() + p.phase());
        matrix.pushPose();
        matrix.translate(at.getX() + 0.5 - cam.x, at.getY() + 1.0 - cam.y, at.getZ() + 0.5 - cam.z);
        // it swings toward its outward face and back: about the horizontal axis across it
        if (p.outward().getAxis() == Direction.Axis.Z)
            matrix.mulPose(com.mojang.math.Axis.XP.rotation(swing));
        else
            matrix.mulPose(com.mojang.math.Axis.ZP.rotation(swing));
        box(matrix, renderer, buffers, p.a(), light, -0.14f, -0.18f, -0.14f, 0.28f, 0.18f, 0.28f);   // crown
        box(matrix, renderer, buffers, p.a(), light, -0.27f, -0.66f, -0.27f, 0.54f, 0.48f, 0.54f);   // waist
        box(matrix, renderer, buffers, p.a(), light, -0.37f, -0.82f, -0.37f, 0.74f, 0.16f, 0.74f);   // lip
        if (p.outward().getAxis() == Direction.Axis.Z)
            matrix.mulPose(com.mojang.math.Axis.XP.rotation(-swing * 0.6f));
        else
            matrix.mulPose(com.mojang.math.Axis.ZP.rotation(-swing * 0.6f));
        box(matrix, renderer, buffers, p.b(), light, -0.13f, -0.98f, -0.13f, 0.26f, 0.26f, 0.26f);   // clapper
        matrix.popPose();
    }

    /**
     * Petals spiralling down from the crown to the pad, each on its own staggered cycle and radius, fluttering as
     * they fall. All of it is a function of the game time - nothing is simulated or stored.
     */
    private static void renderPetals(Part p, PoseStack matrix, Vec3 cam, BlockRenderDispatcher renderer,
                                     MultiBufferSource buffers, float time, int light) {
        BlockPos at = p.pos();
        for (int i = 0; i < PETALS; i++) {
            float prog = ((time + i * (PETAL_CYCLE_TICKS / (float) PETALS) + p.phase() * 20) % PETAL_CYCLE_TICKS) / PETAL_CYCLE_TICKS;
            float spread = 0.35f + 0.65f * Mth.frac(i * 0.618f);
            float a = i * 0.785f + time * p.speed() + prog * 2.5f;
            float rad = p.size() * spread * (0.6f + 0.4f * prog);
            matrix.pushPose();
            matrix.translate(at.getX() + 0.5 + Mth.cos(a) * rad - cam.x,
                at.getY() + 0.3 - prog * p.fall() - cam.y,
                at.getZ() + 0.5 + Mth.sin(a) * rad - cam.z);
            matrix.mulPose(com.mojang.math.Axis.YP.rotation(a * 2));
            matrix.mulPose(com.mojang.math.Axis.XP.rotation(Mth.sin(time * 0.15f + i) * 0.7f));
            box(matrix, renderer, buffers, (i & 3) == 0 ? p.b() : p.a(), light, -0.1f, 0f, -0.07f, 0.2f, 0.03f, 0.14f);
            matrix.popPose();
        }
    }

    /** Jade cups on red arms spinning round the upright hub, like an anemometer crowning the pagoda. */
    private static void renderVanes(Part p, PoseStack matrix, Vec3 cam, BlockRenderDispatcher renderer,
                                    MultiBufferSource buffers, float time, int light) {
        BlockPos at = p.pos();
        float len = p.size();
        matrix.pushPose();
        matrix.translate(at.getX() + 0.5 - cam.x, at.getY() + 0.5 - cam.y, at.getZ() + 0.5 - cam.z);
        matrix.mulPose(com.mojang.math.Axis.YP.rotation(time * p.speed() + p.phase()));
        for (int i = 0; i < p.spokes(); i++) {
            matrix.pushPose();
            matrix.mulPose(com.mojang.math.Axis.YP.rotation(i * Mth.TWO_PI / p.spokes()));
            box(matrix, renderer, buffers, p.a(), light, 0f, -0.05f, -0.05f, len, 0.1f, 0.1f);               // arm
            box(matrix, renderer, buffers, p.b(), light, len - 0.5f, -0.32f, -0.04f, 0.5f, 0.64f, 0.08f);    // cup
            matrix.popPose();
        }
        matrix.popPose();
    }
}
