package com.solegendary.reignofnether.maps;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The start-position phase's markers on the ground (design/maps.md section 5; inbox/spec-3 section 2.3). They
 * replace the 1 px line outline that was all but invisible at RTS zoom and left no mark once a start was placed
 * (rover run 1, Oct 17, findings 7-9; lovish 06:07: "the lines and barrier to show where you should spawn need
 * repair they are hard to see and don't fit the aesthetic of the game"). Per team box:
 * <ul>
 *   <li>the FILL: every ground column inside the box gets a plate on its top face, so the box reads as lit terrain -
 *       BAR's start boxes - that hills carry and the arena wall occludes. Tens of thousands of columns, so it is built
 *       once into a vertex buffer (runs of level ground merged into one quad) and rebuilt only when the ground under
 *       it changes (chunks arriving at the client), one box per frame. Drawn ADDITIVE at 28% (14% for the other
 *       teams): a translucent tint at 22% of the team colour over green grass was all but invisible under a shader
 *       pack (eyes run 39066637227, 01d: inside/outside the arc 1.1:1), light added to the ground reads at any zoom
 *       and does not grey the grass;</li>
 *   <li>the RIM: a ribbon of light along the two arcs and two radials, as wide in blocks as it needs to be to stay
 *       ~8 px at every zoom, lifted towards white and drawn additive over a darker, wider under-band, so the bright
 *       core has a dark edge whatever the ground is (the plain additive ribbon measured 1.9-2.4:1 against the grass
 *       under Complementary, below spec 5.3's 3:1), pulsing for your own box (faster while you have not placed);</li>
 *   <li>the FENCE: a low wall of light on the box's edge, fading up, so the boundary reads from the ground in the
 *       Minecraft view too (the "barrier").</li>
 * </ul>
 * And per placed start: a light pillar (two crossed additive sheets, 14 blocks tall, fading up) in the player's
 * colour and a disc of tinted ground under it. The fill and the discs are RTS-only (they never paint the ground
 * under a hero's feet); rim, fence and pillars show in first person as well.
 * <p>
 * All of it goes through MyRenderer.GROUND_ADDITIVE / GROUND_TRANSLUCENT, whose shader shard is
 * GameRenderer::getPositionColorShader: Oculus swaps that for the pack's gbuffers_basic (MixinGameRenderer), so the
 * same geometry draws with and without shaders - both eyes runs of Oct 17 (39066637227 shaders, 39086552639 plain)
 * show the rim and the fill at the right places; the fill goes through VertexBuffer.drawWithShader exactly as the
 * BufferSource path does inside BufferUploader, so it is no special case for the pipeline.
 * <p>
 * Client-only and cosmetic: MapClient owns the state this reads and calls in, and an exception here is logged once
 * and never breaks the frame.
 */
final class StartMarkers {
    private StartMarkers() { }

    private static final Minecraft MC = Minecraft.getInstance();

    /** Above a column's top face: under every unit, over the grass; the rim and discs sit a hair above the fill. */
    static final float LIFT = 0.04f;
    /** The fill's light (additive): your box, the others'. */
    static final float FILL_ALPHA_MINE = 0.28f, FILL_ALPHA_OTHERS = 0.14f;
    /** The rim's bright core (additive) and the dark band under it (translucent black), wider than the core. */
    static final float RIM_ALPHA = 0.9f, RIM_EDGE_ALPHA = 0.55f, RIM_EDGE_SCALE = 1.8f;
    /** Rim ribbon width as a fraction of the zoom (blocks of view height): about 8 px at every zoom. */
    static final float RIM_WIDTH_PER_ZOOM = 1f / 110f;
    static final float RIM_WIDTH_MIN = 0.3f, RIM_WIDTH_MAX = 3f, RIM_WIDTH_GROUND = 0.5f;
    static final float FENCE_HEIGHT = 2.5f, FENCE_ALPHA = 0.5f;
    static final float PILLAR_HEIGHT = 14f, PILLAR_ALPHA = 0.85f, PILLAR_WIDTH = 1.2f, PILLAR_WIDTH_MAX = 4f;
    static final int DISC_RADIUS = 3;
    static final float DISC_ALPHA = 0.45f;
    /** Perimeter points this far apart (blocks) carry the rim and fence heights; a 2-block chord on these radii is flat. */
    static final float EDGE_STEP = 2f;
    /** The ground under the markers is re-read this often (client ticks): it only changes as chunks arrive. */
    static final int REFRESH_TICKS = 40;
    /** Columns per box whose heights are kept from the last build, to notice the ground changing. */
    static final int SAMPLES = 64;

    private static final List<BoxMarker> markers = new ArrayList<>();
    private static int refreshTicks = 0;
    private static int myTeam = -1;
    private static boolean failed = false;
    private static int frames = 0;   // frames drawn this phase (status line)

    /** The team colour lifted towards white: light is added to the frame, and a dark colour adds nothing. */
    static float lit(int rgb, int shift) {
        return 0.35f + 0.65f * ((rgb >> shift) & 0xFF) / 255f;
    }

    /** One team's box: its fill buffer and the heights along its perimeter. */
    private static final class BoxMarker {
        final MapStateClientboundPacket.BoxInfo box;
        final MapLayout.Box shape;
        final int rgb;
        final boolean mine;
        VertexBuffer fill = null;
        int fillColumns = 0, fillQuads = 0;
        boolean dirty = true;
        // the perimeter: outer arc, inner arc, the two radials - each as x0, z0, x1, z1, ... with a height per point
        final List<float[]> edges = new ArrayList<>();
        final List<int[]> edgeY = new ArrayList<>();
        // a spread of columns inside the box and their heights at the last build: a change means chunks came in
        int[] sampleX = new int[0], sampleZ = new int[0], sampleY = new int[0];

        BoxMarker(MapStateClientboundPacket.BoxInfo box, boolean mine) {
            this.box = box;
            this.mine = mine;
            this.shape = new MapLayout.Box(box.team(), box.a0(), box.a1(), box.r0(), box.r1());
            this.rgb = MapMatch.teamColour(box.team());
            int cx = MapClient.cx, cz = MapClient.cz;
            double span = box.a1() - box.a0();
            for (float r : new float[] { box.r1(), box.r0() }) {
                int n = Math.max(2, (int) Math.round(span * r / EDGE_STEP) + 1);
                float[] pts = new float[2 * n];
                for (int i = 0; i < n; i++) {
                    double a = box.a0() + span * i / (n - 1);
                    pts[2 * i] = (float) (cx + Math.cos(a) * r);
                    pts[2 * i + 1] = (float) (cz + Math.sin(a) * r);
                }
                edges.add(pts);
                edgeY.add(new int[n]);
            }
            for (float a : new float[] { box.a0(), box.a1() }) {
                int n = Math.max(2, Math.round((box.r1() - box.r0()) / EDGE_STEP) + 1);
                float[] pts = new float[2 * n];
                for (int i = 0; i < n; i++) {
                    double r = box.r0() + (box.r1() - box.r0()) * i / (n - 1);
                    pts[2 * i] = (float) (cx + Math.cos(a) * r);
                    pts[2 * i + 1] = (float) (cz + Math.sin(a) * r);
                }
                edges.add(pts);
                edgeY.add(new int[n]);
            }
            readEdges();
        }

        void readEdges() {
            if (MC.level == null)
                return;
            for (int e = 0; e < edges.size(); e++) {
                float[] pts = edges.get(e);
                int[] ys = edgeY.get(e);
                for (int i = 0; i < ys.length; i++)
                    ys[i] = ground(Mth.floor(pts[2 * i]), Mth.floor(pts[2 * i + 1]));
            }
        }

        boolean groundChanged() {
            if (fillColumns == 0)
                return true;   // nothing was loaded at the last build
            for (int i = 0; i < sampleX.length; i++)
                if (ground(sampleX[i], sampleZ[i]) != sampleY[i])
                    return true;
            return false;
        }

        /**
         * The fill: one plate per run of level ground columns inside the box (a row's neighbours at the same height
         * share a quad), relative to the map centre. Render thread.
         */
        void build() {
            dirty = false;
            close();
            if (MC.level == null)
                return;
            int cx = MapClient.cx, cz = MapClient.cz;
            int rr = Mth.ceil(box.r1()) + 1;
            float r = lit(rgb, 16), g = lit(rgb, 8), b = lit(rgb, 0);
            float a = mine ? FILL_ALPHA_MINE : FILL_ALPHA_OTHERS;
            double r0sq = (double) box.r0() * box.r0(), r1sq = (double) box.r1() * box.r1();
            // every column of the sector's bounding square: the annulus test first (cheap), the angle test after
            double area = Math.max(1, (r1sq - r0sq) * (box.a1() - box.a0()) / 2);
            int stride = Math.max(1, (int) (area / SAMPLES));
            int[] sx = new int[SAMPLES], sz = new int[SAMPLES], sy = new int[SAMPLES];
            int ns = 0, seen = 0, columns = 0, quads = 0;
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int z = -rr; z <= rr; z++) {
                int runX = 0, runY = Integer.MIN_VALUE, runLen = 0;   // the open run of level columns on this row
                for (int x = -rr; x <= rr + 1; x++) {
                    int y = Integer.MIN_VALUE;
                    if (x <= rr) {
                        double dx = x + 0.5, dz = z + 0.5;
                        double d = dx * dx + dz * dz;
                        if (d >= r0sq && d <= r1sq && shape.contains(dx, dz)) {
                            int wx = cx + x, wz = cz + z;
                            y = ground(wx, wz);
                            if (seen++ % stride == 0 && ns < SAMPLES) {
                                sx[ns] = wx;
                                sz[ns] = wz;
                                sy[ns] = y;
                                ns++;
                            }
                            if (y != Integer.MIN_VALUE)
                                columns++;   // a column not here yet: the next refresh sees the sample change and rebuilds
                        }
                    }
                    if (y == runY && y != Integer.MIN_VALUE) {
                        runLen++;
                        continue;
                    }
                    if (runLen > 0) {
                        float fy = runY + LIFT;
                        bb.vertex(runX, fy, z).color(r, g, b, a).endVertex();
                        bb.vertex(runX, fy, z + 1).color(r, g, b, a).endVertex();
                        bb.vertex(runX + runLen, fy, z + 1).color(r, g, b, a).endVertex();
                        bb.vertex(runX + runLen, fy, z).color(r, g, b, a).endVertex();
                        quads++;
                    }
                    runX = x;
                    runY = y;
                    runLen = y == Integer.MIN_VALUE ? 0 : 1;
                }
            }
            sampleX = Arrays.copyOf(sx, ns);
            sampleZ = Arrays.copyOf(sz, ns);
            sampleY = Arrays.copyOf(sy, ns);
            BufferBuilder.RenderedBuffer rendered = bb.end();
            if (quads == 0) {
                rendered.release();
                return;
            }
            fill = new VertexBuffer(VertexBuffer.Usage.STATIC);
            fill.bind();
            fill.upload(rendered);
            VertexBuffer.unbind();
            fillColumns = columns;
            fillQuads = quads;
        }

        void close() {
            if (fill != null) {
                fill.close();
                fill = null;
            }
            fillColumns = 0;
            fillQuads = 0;
        }

        /** A flat ribbon along the perimeter, one plate per segment at the higher end's height, centred on the edge. */
        void drawRibbon(VertexConsumer vc, Matrix4f m, float width, float r, float g, float b, float a) {
            float half = width / 2f;
            int cx = MapClient.cx, cz = MapClient.cz;
            for (int e = 0; e < edges.size(); e++) {
                float[] pts = edges.get(e);
                int[] ys = edgeY.get(e);
                boolean arc = e < 2;
                for (int i = 0; i + 1 < ys.length; i++) {
                    if (ys[i] == Integer.MIN_VALUE || ys[i + 1] == Integer.MIN_VALUE)
                        continue;
                    float x0 = pts[2 * i], z0 = pts[2 * i + 1], x1 = pts[2 * i + 2], z1 = pts[2 * i + 3];
                    float y = Math.max(ys[i], ys[i + 1]) + LIFT + 0.01f;
                    // across the ribbon: radial for the arcs, the radial's perpendicular for the straight edges
                    float ax, az, bx, bz;
                    if (arc) {
                        float l0 = (float) Math.hypot(x0 - cx, z0 - cz), l1 = (float) Math.hypot(x1 - cx, z1 - cz);
                        ax = (x0 - cx) / l0;
                        az = (z0 - cz) / l0;
                        bx = (x1 - cx) / l1;
                        bz = (z1 - cz) / l1;
                    } else {
                        float dx = x1 - x0, dz = z1 - z0, l = (float) Math.hypot(dx, dz);
                        ax = bx = -dz / l;
                        az = bz = dx / l;
                    }
                    vc.vertex(m, x0 - ax * half, y, z0 - az * half).color(r, g, b, a).endVertex();
                    vc.vertex(m, x0 + ax * half, y, z0 + az * half).color(r, g, b, a).endVertex();
                    vc.vertex(m, x1 + bx * half, y, z1 + bz * half).color(r, g, b, a).endVertex();
                    vc.vertex(m, x1 - bx * half, y, z1 - bz * half).color(r, g, b, a).endVertex();
                }
            }
        }

        /** The fence: a vertical sheet per perimeter segment, bright at the ground and gone at FENCE_HEIGHT. */
        void drawFence(VertexConsumer vc, Matrix4f m, float pulse) {
            float r = lit(rgb, 16), g = lit(rgb, 8), b = lit(rgb, 0);
            float a = FENCE_ALPHA * pulse;
            for (int e = 0; e < edges.size(); e++) {
                float[] pts = edges.get(e);
                int[] ys = edgeY.get(e);
                for (int i = 0; i + 1 < ys.length; i++) {
                    if (ys[i] == Integer.MIN_VALUE || ys[i + 1] == Integer.MIN_VALUE)
                        continue;
                    float x0 = pts[2 * i], z0 = pts[2 * i + 1], x1 = pts[2 * i + 2], z1 = pts[2 * i + 3];
                    float y0 = ys[i] + LIFT, y1 = ys[i + 1] + LIFT;
                    vc.vertex(m, x0, y0, z0).color(r, g, b, a).endVertex();
                    vc.vertex(m, x0, y0 + FENCE_HEIGHT, z0).color(r, g, b, 0f).endVertex();
                    vc.vertex(m, x1, y1 + FENCE_HEIGHT, z1).color(r, g, b, 0f).endVertex();
                    vc.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ state

    /** MapClient.onState, after its fields are set: the phase, the boxes or the picks may have changed. */
    static void onState() {
        String me = MC.player == null ? "" : MC.player.getName().getString();
        int team = -1;
        for (MapStateClientboundPacket.PickInfo p : MapClient.picks)
            if (p.name().equals(me))
                team = p.team();
        boolean same = MapClient.phase && team == myTeam && markers.size() == MapClient.boxes.size();
        for (int i = 0; same && i < markers.size(); i++)
            same = markers.get(i).box.equals(MapClient.boxes.get(i));
        if (same)
            return;
        clear();
        myTeam = team;
        if (!MapClient.phase)
            return;
        for (MapStateClientboundPacket.BoxInfo b : MapClient.boxes)
            markers.add(new BoxMarker(b, b.team() == team));
        refreshTicks = 0;
        frames = 0;
    }

    /** MapClient.onClientTick, every tick in a world: re-reads the ground under the markers now and then. */
    static void tick() {
        if (markers.isEmpty() || MC.level == null)
            return;
        if (--refreshTicks > 0)
            return;
        refreshTicks = REFRESH_TICKS;
        for (BoxMarker m : markers) {
            m.readEdges();
            if (!m.dirty && m.groundChanged())
                m.dirty = true;
        }
    }

    /** The phase ended or the world went away: free the buffers (the client tick runs on the render thread). */
    static void clear() {
        for (BoxMarker m : markers)
            m.close();
        markers.clear();
        myTeam = -1;
    }

    /** One line for a log: boxes, what their fills hold, frames drawn - the eyes tour prints it beside its stills. */
    static String status() {
        if (failed)
            return "markers OFF (failed to draw, see the [Markers] error)";
        StringBuilder sb = new StringBuilder("markers: " + markers.size() + " boxes, " + frames + " frames drawn");
        for (BoxMarker m : markers) {
            int known = 0;
            for (int[] ys : m.edgeY)
                for (int y : ys)
                    if (y != Integer.MIN_VALUE)
                        known++;
            sb.append(String.format("; team %d%s fill %s (%d columns, %d quads), %d perimeter heights known%s",
                m.box.team(), m.mine ? " (mine)" : "", m.fill == null ? "none" : "built", m.fillColumns, m.fillQuads,
                known, m.dirty ? ", rebuild pending" : ""));
        }
        return sb.toString();
    }

    /** The y of the top face of the ground at x, z (the first free block), or MIN_VALUE when the column isn't here. */
    static int ground(int x, int z) {
        int y = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return y <= MC.level.getMinBuildHeight() + 1 ? Integer.MIN_VALUE : y;
    }

    // ------------------------------------------------------------------------------------------ drawing

    /** RenderLevelStage AFTER_TRANSLUCENT_BLOCKS while the phase runs; {@code pose} is the camera-relative stack. */
    static void render(PoseStack pose, Camera camera) {
        if (markers.isEmpty() || MC.level == null || failed)
            return;
        try {
            draw(pose, camera);
            frames++;
        } catch (Exception e) {
            failed = true;   // once per session: a cosmetic overlay must never break the frame, but CI must see why
            ReignOfNether.LOGGER.error("[Markers] the start markers failed to draw; off for this session", e);
        }
    }

    private static void draw(PoseStack pose, Camera camera) {
        Vec3 cam = camera.getPosition();
        boolean rts = OrthoviewClientEvents.isEnabled();
        for (BoxMarker m : markers)
            if (m.dirty) {
                m.build();   // one box per frame: tens of thousands of columns each
                break;
            }
        String me = MC.player == null ? "" : MC.player.getName().getString();
        boolean placedMine = false;
        for (MapStateClientboundPacket.PickInfo p : MapClient.picks)
            if (p.name().equals(me) && p.placed())
                placedMine = true;
        double t = (System.nanoTime() % 100_000_000_000L) / 1.0e9;

        // 1. the fills: light on the ground, RTS view only, straight from their buffers (the same render type and the
        //    same VertexBuffer.drawWithShader the immediate path ends in, so Oculus treats both alike)
        if (rts) {
            ShaderInstance shader;
            boolean any = false;
            for (BoxMarker m : markers)
                if (m.fill != null)
                    any = true;
            if (any) {
                pose.pushPose();
                pose.translate(MapClient.cx - cam.x, -cam.y, MapClient.cz - cam.z);
                Matrix4f mv = pose.last().pose();
                RenderType type = MyRenderer.GROUND_ADDITIVE;
                type.setupRenderState();
                shader = RenderSystem.getShader();
                if (shader != null)
                    for (BoxMarker m : markers) {
                        if (m.fill == null)
                            continue;
                        m.fill.bind();
                        m.fill.drawWithShader(mv, RenderSystem.getProjectionMatrix(), shader);
                    }
                VertexBuffer.unbind();
                type.clearRenderState();
                pose.popPose();
            }
        }

        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m4 = pose.last().pose();
        float zoom = OrthoviewClientEvents.getZoom();
        float width = rts ? Mth.clamp(zoom * RIM_WIDTH_PER_ZOOM, RIM_WIDTH_MIN, RIM_WIDTH_MAX) : RIM_WIDTH_GROUND;
        float pillarWidth = rts ? Mth.clamp(PILLAR_WIDTH * zoom / 40f, PILLAR_WIDTH, PILLAR_WIDTH_MAX) : PILLAR_WIDTH;

        // 2. the rim's dark edge: a wider black band under the bright core, so the core keeps its contrast on any
        //    ground (bright grass under a shader pack at noon included)
        VertexConsumer dark = buffers.getBuffer(MyRenderer.GROUND_TRANSLUCENT);
        for (BoxMarker m : markers)
            m.drawRibbon(dark, m4, width * RIM_EDGE_SCALE, 0f, 0f, 0f, RIM_EDGE_ALPHA);
        buffers.endBatch(MyRenderer.GROUND_TRANSLUCENT);

        // 3. the light: rim cores, fences and pillars, additive, visible from the ground too
        VertexConsumer add = buffers.getBuffer(MyRenderer.GROUND_ADDITIVE);
        for (BoxMarker m : markers) {
            float pulse = 1f;
            if (m.mine) {   // your box breathes: quickly until you have placed, slowly after (never below 0.75)
                double period = placedMine ? 2.4 : 1.2;
                pulse = 0.875f + 0.125f * (float) Math.sin(t / period * Math.PI * 2);
            }
            m.drawRibbon(add, m4, width, lit(m.rgb, 16), lit(m.rgb, 8), lit(m.rgb, 0), RIM_ALPHA * pulse);
            m.drawFence(add, m4, pulse);
        }
        for (MapStateClientboundPacket.PickInfo p : MapClient.picks) {
            if (!p.placed())
                continue;
            int y = ground(p.x(), p.z());
            if (y != Integer.MIN_VALUE)
                pillar(add, m4, p.x() + 0.5f, y + LIFT, p.z() + 0.5f, p.colour(), pillarWidth);
        }
        buffers.endBatch(MyRenderer.GROUND_ADDITIVE);

        // 4. the discs under the placed starts: tinted ground, RTS view only
        if (rts) {
            VertexConsumer tint = buffers.getBuffer(MyRenderer.GROUND_TRANSLUCENT);
            for (MapStateClientboundPacket.PickInfo p : MapClient.picks)
                if (p.placed())
                    disc(tint, m4, p.x(), p.z(), p.colour());
            buffers.endBatch(MyRenderer.GROUND_TRANSLUCENT);
        }
        pose.popPose();
    }

    /** A pillar of light: two crossed sheets (a soft wide one and a bright narrow core), fading up. */
    static void pillar(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float width) {
        // lifted towards white: a dark player colour would add nothing to the frame
        float r = lit(rgb, 16), g = lit(rgb, 8), b = lit(rgb, 0);
        float h = PILLAR_HEIGHT;
        for (int pass = 0; pass < 2; pass++) {
            float w = pass == 0 ? width / 2f : width / 6f;
            float a = pass == 0 ? PILLAR_ALPHA * 0.6f : PILLAR_ALPHA;
            vc.vertex(m, x - w, y, z).color(r, g, b, a).endVertex();
            vc.vertex(m, x - w, y + h, z).color(r, g, b, 0f).endVertex();
            vc.vertex(m, x + w, y + h, z).color(r, g, b, 0f).endVertex();
            vc.vertex(m, x + w, y, z).color(r, g, b, a).endVertex();
            vc.vertex(m, x, y, z - w).color(r, g, b, a).endVertex();
            vc.vertex(m, x, y + h, z - w).color(r, g, b, 0f).endVertex();
            vc.vertex(m, x, y + h, z + w).color(r, g, b, 0f).endVertex();
            vc.vertex(m, x, y, z + w).color(r, g, b, a).endVertex();
        }
    }

    /** A disc of tinted ground round a placed start, its outer ring stronger. */
    static void disc(VertexConsumer vc, Matrix4f m, int px, int pz, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        int r2 = DISC_RADIUS * DISC_RADIUS + 1, inner = (DISC_RADIUS - 1) * (DISC_RADIUS - 1) + 1;
        for (int dx = -DISC_RADIUS; dx <= DISC_RADIUS; dx++)
            for (int dz = -DISC_RADIUS; dz <= DISC_RADIUS; dz++) {
                int d = dx * dx + dz * dz;
                if (d > r2)
                    continue;
                int y = ground(px + dx, pz + dz);
                if (y == Integer.MIN_VALUE)
                    continue;
                float fy = y + LIFT + 0.01f;
                float a = d > inner ? DISC_ALPHA : DISC_ALPHA * 0.5f;
                float x = px + dx, z = pz + dz;
                vc.vertex(m, x, fy, z).color(r, g, b, a).endVertex();
                vc.vertex(m, x, fy, z + 1).color(r, g, b, a).endVertex();
                vc.vertex(m, x + 1, fy, z + 1).color(r, g, b, a).endVertex();
                vc.vertex(m, x + 1, fy, z).color(r, g, b, a).endVertex();
            }
    }
}
