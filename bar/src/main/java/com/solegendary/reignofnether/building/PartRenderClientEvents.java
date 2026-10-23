package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Drawn moving parts on buildings (design/moving_buildings.md, slice 1; lovish, Oct 16: "moving pieces like using
 * redstone to create animations ... with piston and blocks", "Metal extractors are hard to see", "as optimized as
 * possible"). Real pistons would be block changes - path-grid patches, fog resends, HP that reads as damage, packets to
 * every client - so the machinery is drawn by each client over a building that never changes, from the anchors in
 * {@link BuildingParts}: on every built Sunforged and Gravebound metal extractor (tier 1 and 2) the sheave wheel turns
 * as the ore skip rides its shaft up and down (a raw-iron load drawn fullbright - the patch gem's light - that tips out
 * at the top), and the owner's pennant streams from the pole (grey under fog, like the ring). One stroke every ~2 s,
 * the phase seeded from the position so a row of extractors never pumps in lockstep.
 *
 * Cost rules (the design's section 1 table): built only, never in strategic zoom; a frustum test per placement on a
 * cached AABB; the nearest 64 (Full) or 24 (Low) inside 200 blocks; simplified below a pixel size from the ortho zoom
 * (4 spokes, a solid skip, one chain, a flat pennant), nothing under 3 px a block; a fogged rig keeps the pose it was
 * last seen in. Everything a frame needs - rig, frame, visibility, owner colour, light, anchor blocks standing - is
 * worked out on the client tick every 10 ticks, never on a frame; the frame loop allocates nothing: quads go from the
 * cached baked models straight into the fixed cutout buffer through our own vertex loop (vanilla's putBulkData builds
 * arrays and vectors per quad), the pose is rewritten in place, selection uses fixed arrays. No particles.
 *
 * Client setting "Building animation" (Display settings): Off, Low (nearest 24, simplified sooner, no secondary parts -
 * axle stubs, bails, the drum's back rope) or Full (default; it drops to Low by itself for the session after 10 s under
 * 40 fps with rigs on screen, with a one-line notice - not in eyes tours, whose software GL is always slow). Any error
 * while drawing switches the parts off for the session with one WARN line instead of crashing the game.
 */
public class PartRenderClientEvents {

    static final Minecraft MC = Minecraft.getInstance();

    private static final int MAX_FULL = 64, MAX_LOW = 24;
    private static final double MAX_DIST_SQR = 200 * 200;
    private static final int REFRESH_TICKS = 10;
    private static final int FULLBRIGHT = LightTexture.pack(15, 15);
    // pixels per block below which a rig is drawn simplified (Full / Low), or not at all
    private static final float LOD_PX_FULL = 10f, LOD_PX_LOW = 20f, NONE_PX = 3f;
    // the game clock wraps here before it becomes a float: 72000 is a whole number of strokes for 48 and 36 ticks
    private static final long CLOCK_WRAP = 72000L;
    // flags stream to the south-east: broadside to the default RTS camera, which looks north-east
    private static final float WIND_YAW = -Mth.PI / 4;
    private static final float MAX_TIP = 0.96f;        // ~55 degrees
    private static final float SKIP_W = 0.62f, SKIP_H = 0.55f, SKIP_T = 0.07f, BAIL = 0.3f;
    private static final float FLAG_STAFF = 0.95f;
    // the pennant is the owner's colour high up - the signal that reads at ~10 px a block - so it is a big one: 1.6
    // blocks of cloth tapering from 0.72 to 0.38, inside the 5x5 layer BuildingParts keeps air round the pole
    private static final float[] FLAG_LEN = { 0.6f, 0.55f, 0.48f };
    private static final float[] FLAG_HGT = { 0.72f, 0.56f, 0.38f };
    private static final float FOG_GREY = 0.62f;

    /** A faction's materials for the rig (design/moving_buildings.md "Faction skins"). */
    private record Skin(BlockState rim, BlockState spoke, BlockState boss, BlockState skip, BlockState skipFloor,
                        BlockState staff, int spokes) { }

    // Sunforged: a gilded sun wheel with white quartz spokes, a gilded skip. Gravebound: a bone winch wheel on a
    // blackstone boss, a bone bucket on a blackstone floor, a blackstone staff over the pole.
    private static final Skin SUN = new Skin(Blocks.GOLD_BLOCK.defaultBlockState(),
        Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(),
        Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(),
        Blocks.GOLD_BLOCK.defaultBlockState(), 8);
    private static final Skin GRAVE = new Skin(Blocks.BONE_BLOCK.defaultBlockState(),
        Blocks.BONE_BLOCK.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
        Blocks.BONE_BLOCK.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 6);
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    private static final BlockState ORE = Blocks.RAW_IRON_BLOCK.defaultBlockState();
    private static final BlockState FLAG = Blocks.WHITE_WOOL.defaultBlockState();

    /**
     * Per placement, refreshed on the client tick. Holds no reference to its placement (it is a WeakHashMap value),
     * only what a frame needs.
     */
    private static final class State {
        BuildingParts.Rig rig;
        Skin skin;
        int ox, oy, oz;
        Rotation rotation;
        float yaw;
        float phase;                 // ticks: seeded from the position
        boolean visible;             // in a bright chunk
        boolean axleUp, poleUp;      // the anchor blocks stand (a part whose anchor is gone is not drawn)
        int light;
        float r = FOG_GREY, g = FOG_GREY, b = FOG_GREY;
        float frozenT = Float.NaN;   // the clock it was last seen at (the fogged pose)
        AABB box;
        double cx, cy, cz;           // the hub, for the distance pick
        int maxY = Integer.MIN_VALUE;
    }
    private static final Map<BuildingPlacement, State> STATES = new WeakHashMap<>();
    private static final ArrayList<State> CANDIDATES = new ArrayList<>();
    private static long refreshedAt = Long.MIN_VALUE;
    private static int refreshedCount = -1;
    private static final BlockPos.MutableBlockPos MUT = new BlockPos.MutableBlockPos();

    // nearest-N, reused every frame
    private static final State[] picked = new State[MAX_FULL];
    private static final double[] pickedDist = new double[MAX_FULL];
    private static int pickedCount = 0;

    // per-draw context (render thread only)
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Matrix4f BASE = new Matrix4f(), FRAME = new Matrix4f(), PART = new Matrix4f(), BOX = new Matrix4f();
    private static final Matrix3f BASE_N = new Matrix3f(), FRAME_N = new Matrix3f(), PART_N = new Matrix3f(), BOX_N = new Matrix3f();
    private static final Vector3f POS = new Vector3f(), NRM = new Vector3f();
    private static BlockRenderDispatcher renderer;
    private static VertexConsumer consumer;
    private static int light;
    private static float red = 1f, green = 1f, blue = 1f;

    // the setting, the session's own drop to Low, the first-use log line, the kill switch
    private static boolean disabled = false;
    private static boolean announced = false, firstDrawLogged = false;
    private static int lastConfigured = Integer.MIN_VALUE;
    private static boolean autoLow = false, playerChose = false;
    private static int frames = 0, rigsThisSecond = 0, slowSeconds = 0;
    private static long fpsSince = 0;
    // eyes tours only: what the parts cost (a [Parts] timing line a minute)
    private static long nanosTotal = 0, nanosPeak = 0;
    private static int timedFrames = 0, rigsPeak = 0, timingSeconds = 0;

    /** The setting in force: the player's (or the migrated ambience switch), Full dropped to Low if the frame rate asked. */
    private static int setting() {
        int s = DisplaySettings.buildingAnimation();
        if (s != lastConfigured) {
            if (lastConfigured != Integer.MIN_VALUE) {   // changed during the session: the player chose; keep it
                playerChose = true;
                autoLow = false;
                ReignOfNether.LOGGER.info("[Parts] building animation set to {}", DisplaySettings.BUILDING_ANIMATION_NAMES[s]);
            }
            lastConfigured = s;
        }
        return s == DisplaySettings.ANIM_FULL && autoLow ? DisplaySettings.ANIM_LOW : s;
    }

    private static void disable(Throwable e) {
        disabled = true;
        CANDIDATES.clear();
        StackTraceElement[] st = e.getStackTrace();
        ReignOfNether.LOGGER.warn("[Parts] extractor rigs switched off for this session after an error: {}{}", e,
            st.length > 0 ? " at " + st[0] : "");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the client tick: frame rate watch, and every REFRESH_TICKS the list of rigs to consider with all they need
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (MC.level == null) {
            if (!CANDIDATES.isEmpty() || !STATES.isEmpty()) {
                CANDIDATES.clear();
                STATES.clear();
            }
            refreshedAt = Long.MIN_VALUE;
            return;
        }
        if (disabled)
            return;
        try {
            watchFrameRate();
            long now = MC.level.getGameTime();
            List<BuildingPlacement> buildings = BuildingClientEvents.getBuildings();
            if (now - refreshedAt < REFRESH_TICKS && now >= refreshedAt && buildings.size() == refreshedCount)
                return;
            refreshedAt = now;
            refreshedCount = buildings.size();
            refresh(buildings);
        } catch (RuntimeException | LinkageError e) {
            disable(e);
        }
    }

    private static void refresh(List<BuildingPlacement> buildings) {
        CANDIDATES.clear();
        if (setting() == DisplaySettings.ANIM_OFF)
            return;
        for (int i = 0; i < buildings.size(); i++) {
            BuildingPlacement p = buildings.get(i);
            if (!p.isBuilt || !p.isExploredClientside || p.originPos == null || !(p.getBuilding() instanceof MetalExtractor))
                continue;
            BuildingParts.Rig rig = BuildingParts.rigFor(p.currentStructureName);
            if (rig == null)
                continue;   // a faction whose rig is not drawn yet
            State s = STATES.get(p);
            if (s == null) {
                s = new State();
                STATES.put(p, s);
            }
            Rotation rot = p.rotation == null ? Rotation.NONE : p.rotation;
            if (s.rig != rig || s.rotation != rot || s.ox != p.originPos.getX() || s.oy != p.originPos.getY()
                    || s.oz != p.originPos.getZ() || s.maxY != p.maxCorner.getY())
                place(s, p, rig, rot);
            s.visible = FogOfWarClientEvents.isInBrightChunk(p.centrePos);
            if (s.visible) {   // the owner's colour only where it is in sight: a remembered extractor flies grey
                int hex = p.ownerName == null ? 0xFFFFFF : PlayerColors.getPlayerDisplayColorHex(p.ownerName);
                s.r = ((hex >> 16) & 255) / 255f;
                s.g = ((hex >> 8) & 255) / 255f;
                s.b = (hex & 255) / 255f;
            } else {
                s.r = s.g = s.b = FOG_GREY;
            }
            s.axleUp = !MC.level.getBlockState(worldCell(s, rig.axle())).isAir();
            s.poleUp = !MC.level.getBlockState(worldCell(s, rig.pole())).isAir();
            s.light = LevelRenderer.getLightColor(MC.level,
                worldCell(s, Mth.floor(rig.hubX()), Mth.floor(rig.hubY()), Mth.floor(rig.hubZ())));
            if (s.axleUp || s.poleUp)
                CANDIDATES.add(s);
        }
    }

    /** Works out a rig's frame on its placement: origin, rotation, phase, the hub in the world and the culling box. */
    private static void place(State s, BuildingPlacement p, BuildingParts.Rig rig, Rotation rot) {
        s.rig = rig;
        s.skin = rig.skin() == BuildingParts.Skin.GRAVE ? GRAVE : SUN;
        s.rotation = rot;
        s.yaw = BuildingParts.yawOf(rot);
        s.ox = p.originPos.getX();
        s.oy = p.originPos.getY();
        s.oz = p.originPos.getZ();
        s.maxY = p.maxCorner.getY();
        s.phase = ((s.ox * 73856093) ^ (s.oz * 19349663)) & 1023;
        s.cx = s.ox + BuildingParts.pointX(rot, rig.hubX(), rig.hubZ());
        s.cy = s.oy + 1 + rig.hubY();
        s.cz = s.oz + BuildingParts.pointZ(rot, rig.hubX(), rig.hubZ());
        // the footprint, up past the pennant, with room for the flag and the wheel's overhang
        int top = Math.max(p.maxCorner.getY(), s.oy + 1 + rig.pole().getY()) + 3;
        s.box = new AABB(p.minCorner.getX() - 2, p.minCorner.getY() - 1, p.minCorner.getZ() - 2,
            p.maxCorner.getX() + 3, top, p.maxCorner.getZ() + 3);
    }

    private static BlockPos worldCell(State s, BlockPos rel) {
        return worldCell(s, rel.getX(), rel.getY(), rel.getZ());
    }

    private static BlockPos worldCell(State s, int x, int y, int z) {
        return MUT.set(s.ox + BuildingParts.cellX(s.rotation, x, z), s.oy + 1 + y, s.oz + BuildingParts.cellZ(s.rotation, x, z));
    }

    /**
     * Full drops to Low for the session after 10 s under 40 fps while rigs are on screen (design/moving_buildings.md
     * open question 5, its recommended answer), with a one-line notice. Never in an eyes tour (software GL on a CI
     * runner is always slow - the tour photographs the shipped default), never once the player picked a setting.
     */
    private static void watchFrameRate() {
        long nanos = System.nanoTime();
        if (fpsSince == 0) {
            fpsSince = nanos;
            return;
        }
        if (nanos - fpsSince < 1_000_000_000L)
            return;
        float fps = frames * 1e9f / (nanos - fpsSince);
        int rigs = rigsThisSecond;
        frames = 0;
        rigsThisSecond = 0;
        fpsSince = nanos;
        if (com.solegendary.reignofnether.matchstart.EyesDirector.ENABLED) {
            timingLine();
            return;
        }
        if (autoLow || playerChose || DisplaySettings.buildingAnimation() != DisplaySettings.ANIM_FULL || rigs == 0) {
            slowSeconds = 0;
            return;
        }
        slowSeconds = fps < 40f ? slowSeconds + 1 : 0;
        if (slowSeconds >= 10) {
            autoLow = true;
            ReignOfNether.LOGGER.info("[Parts] building animation: Full -> Low for this session ({} fps with rigs on screen, under 40)",
                Math.round(fps));
            com.solegendary.reignofnether.hud.NotificationClientEvents.showLine(
                "Building animation set to Low (under 40 fps) - Display settings to change", 0xE0B060);
        }
    }

    /** Eyes tours: one line a minute with what the parts cost on the render thread. */
    private static void timingLine() {
        if (++timingSeconds < 60)
            return;
        timingSeconds = 0;
        if (timedFrames > 0 && rigsPeak > 0)
            ReignOfNether.LOGGER.info(String.format("[Parts] timing: %.3f ms/frame on average, %.3f peak, up to %d rigs drawn, %d frames",
                nanosTotal / 1e6 / timedFrames, nanosPeak / 1e6, rigsPeak, timedFrames));
        nanosTotal = nanosPeak = 0;
        timedFrames = rigsPeak = 0;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // the frame
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.BUILDING_PARTS);
        try {
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
                return;
            frames++;
            if (disabled || MC.level == null)
                return;
            int setting = setting();
            if (!announced) {
                announced = true;
                ReignOfNether.LOGGER.info("[Parts] extractor rigs: {} variants, setting {}{}", BuildingParts.RIGS.size(),
                    DisplaySettings.BUILDING_ANIMATION_NAMES[setting],
                    DisplaySettings.buildingAnimationChosen() ? "" : " (default, from building_ambience)");
            }
            // at strategic zoom the parts are a few pixels: icons carry the map there
            if (setting == DisplaySettings.ANIM_OFF || CANDIDATES.isEmpty() || StrategicViewClientEvents.isStrategicView())
                return;
            long t0 = System.nanoTime();
            try {
                draw(evt, setting);
            } catch (RuntimeException | LinkageError e) {
                disable(e);
            }
            if (com.solegendary.reignofnether.matchstart.EyesDirector.ENABLED && pickedCount > 0) {
                long dt = System.nanoTime() - t0;
                nanosTotal += dt;
                nanosPeak = Math.max(nanosPeak, dt);
                timedFrames++;
                rigsPeak = Math.max(rigsPeak, pickedCount);
            }
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    private static void draw(RenderLevelStageEvent evt, int setting) {
        Vec3 cam = evt.getCamera().getPosition();   // the RENDER camera: in the RTS view the player entity is elsewhere
        Frustum frustum = evt.getFrustum();
        int cap = setting == DisplaySettings.ANIM_LOW ? MAX_LOW : MAX_FULL;
        pickedCount = 0;
        for (int i = 0, n = CANDIDATES.size(); i < n; i++) {
            State s = CANDIDATES.get(i);
            double dx = s.cx - cam.x, dy = s.cy - cam.y, dz = s.cz - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > MAX_DIST_SQR || (pickedCount == cap && d2 >= pickedDist[cap - 1]))
                continue;
            if (frustum != null && !frustum.isVisible(s.box))
                continue;
            insert(s, d2, cap);
        }
        rigsThisSecond += pickedCount;
        if (pickedCount == 0)
            return;
        if (!firstDrawLogged) {   // once a session: proof in a log (an eyes tour's) that a rig was really drawn
            firstDrawLogged = true;
            State s = picked[0];
            ReignOfNether.LOGGER.info("[Parts] first rig drawn: {} at {} {} {}, {} in view", s.rig.structure(), s.ox, s.oy,
                s.oz, pickedCount);
        }

        // pixel size: the RTS view is orthographic (zoom = blocks of view height); first person goes by distance
        boolean ortho = OrthoviewClientEvents.isEnabled();
        float screenH = MC.getWindow().getHeight();
        float orthoPx = screenH / Math.max(1f, OrthoviewClientEvents.getZoom());
        float lodPx = setting == DisplaySettings.ANIM_LOW ? LOD_PX_LOW : LOD_PX_FULL;
        boolean full = setting == DisplaySettings.ANIM_FULL;

        PoseStack.Pose last = evt.getPoseStack().last();
        BASE.set(last.pose());
        BASE_N.set(last.normal());
        renderer = MC.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());   // a fixed buffer: no allocation
        long clock = MC.level.getGameTime() % CLOCK_WRAP;
        float now = clock + evt.getPartialTick();
        try {
            for (int i = 0; i < pickedCount; i++) {
                State s = picked[i];
                float px = ortho ? orthoPx : screenH / (1.4f * (float) Math.sqrt(pickedDist[i]));
                if (px >= NONE_PX)
                    drawRig(s, now, cam, px < lodPx, full);
            }
        } finally {
            for (int i = 0; i < pickedCount; i++)
                picked[i] = null;
            consumer = null;
            red = green = blue = 1f;
            buffers.endBatch(Sheets.cutoutBlockSheet());
        }
    }

    /** Insertion into the distance-sorted pick list, dropping the furthest when full. */
    private static void insert(State s, double d2, int cap) {
        int i = pickedCount < cap ? pickedCount++ : cap - 1;
        while (i > 0 && pickedDist[i - 1] > d2) {
            picked[i] = picked[i - 1];
            pickedDist[i] = pickedDist[i - 1];
            i--;
        }
        picked[i] = s;
        pickedDist[i] = d2;
    }

    private static void drawRig(State s, float now, Vec3 cam, boolean simple, boolean full) {
        BuildingParts.Rig rig = s.rig;
        Skin skin = s.skin;
        float t;
        if (s.visible) {
            t = now + s.phase;
            s.frozenT = t;
        } else {   // fogged: the pose it was last seen in (or a still one if it never was)
            t = Float.isNaN(s.frozenT) ? s.phase : s.frozenT;
        }
        // the structure's frame: origin + (0, 1, 0), turned about the origin cell's centre like getAbsoluteBlockData
        FRAME.set(BASE)
            .translate((float) (s.ox + 0.5 - cam.x), (float) (s.oy + 1 - cam.y), (float) (s.oz + 0.5 - cam.z))
            .rotateY(s.yaw)
            .translate(-0.5f, 0f, -0.5f);
        FRAME_N.set(BASE_N).rotateY(s.yaw);
        light = s.light;
        if (s.axleUp)
            hoist(rig, skin, t, simple, full);
        if (s.poleUp)
            pennant(s, rig, skin, t, simple);
    }

    // ---- the hoist: wheel, skip, chain ----

    private static void hoist(BuildingParts.Rig rig, Skin skin, float t, boolean simple, boolean full) {
        float f = (t / rig.strokeTicks()) % 1f;
        float travel = rig.skipTop().getY() - rig.skipBottom().getY();
        float rise = BuildingParts.skipLift(f) * travel;
        float r = rig.wheelRadius();
        // the rope pays over the rim: the wheel turns exactly as far as the skip travels (rope on the wheel's -x side)
        wheel(rig, skin, -rise / r, simple, full);

        float sx = rig.skipBottom().getX() + 0.5f, sz = rig.skipBottom().getZ() + 0.5f;
        float floor = rig.skipBottom().getY() + 0.03f + rise;   // the skip's floor, parked on the ore at the foot
        float hang = floor + SKIP_H + (full && !simple ? BAIL : 0f);
        skip(rig, skin, f, sx, floor, sz, simple, full);

        // the chain: up to the wheel's rim tangent when it runs over the wheel to a drum, to the axle when it winds on it
        float top = rig.drum() == null ? rig.hubY() - 0.2f : rig.hubY();
        PART.set(FRAME);
        PART_N.set(FRAME_N);
        chain(sx, hang, top, sz, simple);
        if (rig.drum() != null && full && !simple)   // the back fall, drum to the far rim
            chain(rig.drum().getX() + 0.5f, rig.drum().getY() + 1f, rig.hubY(), rig.drum().getZ() + 0.5f, false);
    }

    /** The sheave wheel in its own frame: origin on the hub, local Z along the axle, turned by {@code angle}. */
    private static void wheel(BuildingParts.Rig rig, Skin skin, float angle, boolean simple, boolean full) {
        PART.set(FRAME).translate(rig.hubX(), rig.hubY(), rig.hubZ());
        PART_N.set(FRAME_N);
        if (rig.axis() == Direction.Axis.X) {   // local Z onto structure +X
            PART.rotateY(Mth.HALF_PI);
            PART_N.rotateY(Mth.HALF_PI);
        }
        if (full && !simple) {   // the axle stub, back into its bearing
            float toward = rig.axis() == Direction.Axis.X ? rig.axle().getX() + 0.5f - rig.hubX() : rig.axle().getZ() + 0.5f - rig.hubZ();
            box(skin.boss(), -0.09f, -0.09f, toward > 0 ? 0f : -0.62f, 0.18f, 0.18f, 0.62f);
        }
        PART.rotateZ(angle);
        PART_N.rotateZ(angle);
        box(skin.boss(), -0.2f, -0.2f, -0.13f, 0.4f, 0.4f, 0.26f);
        float r = rig.wheelRadius();
        int spokes = simple ? 4 : skin.spokes();
        float step = Mth.TWO_PI / spokes;
        for (int i = 0; i < spokes; i++) {
            box(skin.spoke(), -0.055f, 0.12f, -0.055f, 0.11f, r - 0.1f, 0.11f);
            PART.rotateZ(step);
            PART_N.rotateZ(step);
        }
        int segs = simple ? 8 : 12;
        float seg = 2f * r * (float) Math.tan(Math.PI / segs) + 0.03f;
        step = Mth.TWO_PI / segs;
        for (int i = 0; i < segs; i++) {
            box(skin.rim(), -seg / 2, r - BuildingParts.RIM_OUTSET, -0.09f, seg, 2 * BuildingParts.RIM_OUTSET, 0.18f);
            PART.rotateZ(step);
            PART_N.rotateZ(step);
        }
    }

    /**
     * The ore skip: an open bucket hung at (x, floor, z) from a bail that stays upright while the bucket tips over its
     * lip on the tip side at the top of the shaft (so the body stays in the shaft's column); the raw-iron load is drawn
     * fullbright - the same light as the patch gem - heaped a little over the rim so the RTS camera sees it from above.
     */
    private static void skip(BuildingParts.Rig rig, Skin skin, float f, float x, float floor, float z, boolean simple,
                             boolean full) {
        float w = SKIP_W / 2, h = SKIP_H, th = SKIP_T;
        Direction d = rig.tip();
        PART.set(FRAME).translate(x, floor + h, z);   // local origin: the bucket's top centre
        PART_N.set(FRAME_N);
        if (full && !simple) {
            // the bail: two straps on the sides the bucket turns between (its pins) and a crossbar the chain takes
            float o = w + 0.03f, len = 2 * o + 0.12f;
            if (d.getAxis() == Direction.Axis.X) {
                box(skin.skipFloor(), -0.03f, -0.14f, -o - 0.06f, 0.06f, BAIL + 0.14f, 0.06f);
                box(skin.skipFloor(), -0.03f, -0.14f, o, 0.06f, BAIL + 0.14f, 0.06f);
                box(skin.skipFloor(), -0.04f, BAIL - 0.06f, -o - 0.06f, 0.08f, 0.06f, len);
            } else {
                box(skin.skipFloor(), -o - 0.06f, -0.14f, -0.03f, 0.06f, BAIL + 0.14f, 0.06f);
                box(skin.skipFloor(), o, -0.14f, -0.03f, 0.06f, BAIL + 0.14f, 0.06f);
                box(skin.skipFloor(), -o - 0.06f, BAIL - 0.06f, -0.04f, len, 0.06f, 0.08f);
            }
        }
        float tip = BuildingParts.skipTip(f) * MAX_TIP;
        if (tip > 0f) {   // over the lip on the tip side
            float lipX = d.getStepX() * w, lipZ = d.getStepZ() * w;
            PART.translate(lipX, 0f, lipZ);
            switch (d) {
                case EAST -> { PART.rotateZ(-tip); PART_N.rotateZ(-tip); }
                case WEST -> { PART.rotateZ(tip); PART_N.rotateZ(tip); }
                case SOUTH -> { PART.rotateX(tip); PART_N.rotateX(tip); }
                default -> { PART.rotateX(-tip); PART_N.rotateX(-tip); }
            }
            PART.translate(-lipX, 0f, -lipZ);
        }
        float fill = BuildingParts.loadFill(f);
        float lw = 0.46f;
        if (simple) {
            box(skin.skip(), -w, -h, -w, SKIP_W, h - 0.08f, SKIP_W);
            if (fill > 0f)
                ore(-lw / 2, -0.08f, -lw / 2, lw, 0.06f + 0.08f * fill, lw);
            return;
        }
        box(skin.skipFloor(), -w, -h, -w, SKIP_W, th, SKIP_W);
        box(skin.skip(), -w, -h + th, -w, th, h - th, SKIP_W);
        box(skin.skip(), w - th, -h + th, -w, th, h - th, SKIP_W);
        box(skin.skip(), -w + th, -h + th, -w, SKIP_W - 2 * th, h - th, th);
        box(skin.skip(), -w + th, -h + th, w - th, SKIP_W - 2 * th, h - th, th);
        if (fill > 0f)
            ore(-lw / 2, -h + th, -lw / 2, lw, 0.52f * fill, lw);
    }

    /** The raw-iron load, fullbright: the light the player learns as "that is metal" (the patch gem's own). */
    private static void ore(float x, float y, float z, float sx, float sy, float sz) {
        int was = light;
        light = FULLBRIGHT;
        box(ORE, x, y, z, sx, sy, sz);
        light = was;
    }

    /** A chain from y0 up to y1 at (x, z) in PART's frame: one vanilla chain model per block or so (one when simple). */
    private static void chain(float x, float y0, float y1, float z, boolean simple) {
        float len = y1 - y0;
        if (len < 0.05f)
            return;
        int n = simple ? 1 : Math.max(1, Mth.ceil(len));
        float seg = len / n;
        for (int i = 0; i < n; i++)
            box(CHAIN, x - 0.5f, y0 + i * seg, z - 0.5f, 1f, seg, 1f);
    }

    // ---- the owner's pennant ----

    private static void pennant(State s, BuildingParts.Rig rig, Skin skin, float t, boolean simple) {
        PART.set(FRAME).translate(rig.pole().getX() + 0.5f, rig.pole().getY() + 1f, rig.pole().getZ() + 0.5f);
        PART_N.set(FRAME_N);
        if (rig.flagAbove()) {   // a staff on the pole's top face
            box(skin.staff(), -0.05f, 0f, -0.05f, 0.1f, FLAG_STAFF, 0.1f);
            PART.translate(0f, FLAG_STAFF, 0f);
        }
        // stream with the world's wind (to the south-east) whatever way the building faces, swaying a little
        float yaw = WIND_YAW - s.yaw + 0.18f * Mth.sin(t * 0.05f + s.phase);
        PART.rotateY(yaw);
        PART_N.rotateY(yaw);
        red = s.r;
        green = s.g;
        blue = s.b;
        if (simple) {
            box(FLAG, 0f, -0.6f, -0.025f, 1.6f, 0.6f, 0.05f);
        } else {
            for (int i = 0; i < FLAG_LEN.length; i++) {
                // a ripple running down the cloth: each panel turns a little against the one before it
                float a = 0.3f * Mth.sin(t * 0.22f + s.phase - i * 0.9f) * (i == 0 ? 0.5f : 1f);
                PART.rotateY(a);
                PART_N.rotateY(a);
                box(FLAG, 0f, -FLAG_HGT[i], -0.025f, FLAG_LEN[i] + 0.02f, FLAG_HGT[i], 0.05f);
                PART.translate(FLAG_LEN[i], 0f, 0f);
            }
        }
        red = green = blue = 1f;
    }

    // ---- quads ----

    /**
     * A unit block model stretched to (sx, sy, sz) at (x, y, z) in PART's frame: the pose is rewritten in place (the
     * maths of PoseStack.translate + scale, no pushPose), and the model's quads are written vertex by vertex.
     */
    private static void box(BlockState state, float x, float y, float z, float sx, float sy, float sz) {
        BOX.set(PART).translate(x, y, z).scale(sx, sy, sz);
        float fx = 1f / sx, fy = 1f / sy, fz = 1f / sz;
        float inv = Mth.fastInvCubeRoot(fx * fy * fz);
        BOX_N.set(PART_N).scale(inv * fx, inv * fy, inv * fz);
        BakedModel model = renderer.getBlockModel(state);
        for (Direction d : DIRECTIONS) {
            RANDOM.setSeed(42L);
            quads(model.getQuads(state, d, RANDOM, ModelData.EMPTY, null));
        }
        RANDOM.setSeed(42L);
        quads(model.getQuads(state, null, RANDOM, ModelData.EMPTY, null));
    }

    /**
     * Writes baked quads through BOX / BOX_N. Our own loop because VertexConsumer.putBulkData allocates arrays and
     * vectors per quad; the baked vertex layout is DefaultVertexFormat.BLOCK (8 ints a vertex: x y z, colour, u v,
     * light, normal). Colour is the tint (white, or the owner's for the pennant); the face normal lights it.
     */
    private static void quads(List<BakedQuad> list) {
        for (int q = 0, n = list.size(); q < n; q++) {
            BakedQuad quad = list.get(q);
            int[] v = quad.getVertices();
            Direction d = quad.getDirection();
            BOX_N.transform(d.getStepX(), d.getStepY(), d.getStepZ(), NRM);
            NRM.normalize();
            for (int k = 0; k < 4; k++) {
                int o = k * 8;
                BOX.transformPosition(Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]), Float.intBitsToFloat(v[o + 2]), POS);
                consumer.vertex(POS.x, POS.y, POS.z, red, green, blue, 1f,
                    Float.intBitsToFloat(v[o + 4]), Float.intBitsToFloat(v[o + 5]),
                    OverlayTexture.NO_OVERLAY, light, NRM.x, NRM.y, NRM.z);
            }
        }
    }
}
