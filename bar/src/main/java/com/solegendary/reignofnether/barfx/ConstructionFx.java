package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.ConstructionShape;
import com.solegendary.reignofnether.faction.FactionTraits;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Each faction builds its own way (claude/faction-construction-oct29.md; lovish, Oct 29: "Building looks fake, each
 * race could get their own method of building"). The White Angel pilot, approved from a paintover ("i loveeee"):
 * <ul>
 *   <li>a cloud RING hugging the layer being built - not a cloud overhead, the RTS camera looks down - that climbs with
 *   the build front: one continuous soft band with lumpy top-lit puffs orbiting on it;</li>
 *   <li>the part still to come is a faint gold GHOST of its steps with gold corner verticals, so how much is left reads
 *   at a glance (nothing white: the game's selection box is the white wireframe);</li>
 *   <li>the build front's cut line: a gold-white core in a gold edge with a bright segment travelling round it;</li>
 *   <li>light from above: a slowly turning pillar, god rays sweeping down through the ring, gold motes rising;</li>
 *   <li>golden hammers and a chisel move from spot to spot on the cut line and strike it, throwing gold sparks;</li>
 *   <li>done: the ring swells and fades, a flash on the top, a light ring runs out over the ground, the tools fly up.</li>
 * </ul>
 * Building-agnostic: everything comes from the placement's own blocks (the buildings are being reworked). Pure client
 * rendering through BarFxClient's passes; thinner when zoomed out; nothing allocated per frame once a site's shape is
 * cached (one ConstructionShape per placement, built from its blocks the first time it is seen rising).
 */
final class ConstructionFx {

    private ConstructionFx() { }

    private static final int CLOUD = 0xFFFBF2, GOLD = 0xFFD27A, LIGHT = 0xFFF3D2;
    /** The cloud's underside: a grey-blue (round 5 critic: white tops over a #C8CFD8 underside read as volume). */
    private static final int UNDER = 0xC8CFD8;
    /** The cut line: a warm gold-white core in a gold edge, so colour (and the travelling segment's motion) separate it
     *  from the game's static yellow selection rings (round 5 critic). */
    private static final int CUT_CORE = 0xFFE9A0, CUT_EDGE = 0xD9A441, CUT_RUN = 0xFFF6D8;
    /** The ghost of the part still to come and its corner verticals: warm gold, never white (round 5 critic: the white
     *  selection box and a white-ish outline read as one cage). */
    private static final int GHOST = 0xF2C14E, OUTLINE_GOLD = 0xE9B44C;
    /** The light from above: a warm white, additive. */
    private static final int RAY = 0xFFE7B0;
    /** Sparks at each blow: a gold fading to transparent. */
    private static final int SPARK = 0xFFE08A;
    /** The tools, normal-blended: additive gold over the white walls vanished (round 3: "pale smudges"); a gold head
     *  and an ivory handle inside a dark rim, so a tool reads on the white walls and on the lawn alike (round 4). */
    private static final int TOOL_BODY = 0xE0A83A, TOOL_HANDLE = 0xF1E7CF, TOOL_RIM = 0x3A2710;
    /** Seconds the completion beat lasts (the ring's 0.6 s swell is the longest part, the tools' flight 0.6 s). */
    private static final float DONE_SECONDS = 0.8f;
    /** Past this RTS zoom (strategic) the ring keeps fewer puffs, the tools become glints and the light stops. */
    private static final float FAR_ZOOM = 70;
    /** The cloud ring stands this far outside the layer's footprint (round 5 critic: ~0.5 block). */
    private static final float RING_PAD = 0.5f;
    /** Seconds between recounts of what stands in each layer (a few hundred block-state reads per site). */
    private static final float RECOUNT = 0.5f;

    /** Per rising site: its shape, the front drawn (eased), the ring's eased footprint, when it finished. */
    private static final class Site {
        final ConstructionShape shape;
        final float[] outline;
        final float[] cut = new float[4];
        final float[] ring = { Float.NaN, 0, 0, 0 };   // the cloud ring's footprint, easing toward the cut's
        final float[] tmp = new float[4];
        final float[] pt = new float[4];               // ringPoint / perimeterPoint results {x, z, nx, nz}
        final float[] pt2 = new float[4];
        final float[] beat = new float[4];             // a tool's strikeBeat
        final float[] heads = new float[9];            // each tool's working end this frame {x, y, z} (for its glint)
        final int[] standing;     // blocks standing per layer, recounted every RECOUNT seconds
        float countedAt = -1;
        float shown;
        float doneAt = -1;
        float lastFront;
        Site(ConstructionShape s) { shape = s; outline = new float[6 * s.maxOutlineBoxes()]; standing = new int[s.layers()]; }
    }
    private static final Map<BuildingPlacement, Site> SITES = new WeakHashMap<>();
    private static final List<BuildingPlacement> scratch = new ArrayList<>();

    static boolean isAngel(BuildingPlacement p) {
        return FactionTraits.of(p.getFaction()) == FactionTraits.SUNFORGED;
    }

    private static Site siteOf(BuildingPlacement p) {
        Site s = SITES.get(p);
        if (s == null) {
            List<int[]> pts = new ArrayList<>();
            for (BuildingBlock b : p.getBlocks()) {
                if (b.getBlockState().isAir())
                    continue;
                BlockPos bp = b.getBlockPos();
                pts.add(new int[] { bp.getX(), bp.getY(), bp.getZ() });
            }
            s = new Site(ConstructionShape.of(pts));
            s.shown = Float.NaN;   // set from the first recount (world heights can be negative)
            SITES.put(p, s);
        }
        return s;
    }

    /**
     * Called from BarFxClient.render before its early return (a site can rise with no other effect alive). The
     * completion beat starts the first frame a known rising site reads {@code isBuilt}: the game's own hook is
     * BuildingPlacement.onBuilt (its client branch plays the completion sound and sets isBuilt), so this is within a
     * frame of it without touching that shared class; a site never seen rising (loaded in finished) gets no beat.
     */
    static void render(MultiBufferSource.BufferSource buffers, Matrix4f mat, float clock, float dt) {
        List<BuildingPlacement> all = BuildingClientEvents.getBuildings();
        if (all == null || all.isEmpty())
            return;
        scratch.clear();
        for (BuildingPlacement p : all) {
            if (p == null || p.centrePos == null || !isAngel(p))
                continue;
            Site known = SITES.get(p);
            boolean rising = !p.isBuilt && p.serverBlocksPlaced > 0;
            boolean finishing = p.isBuilt && known != null && (known.doneAt < 0 || clock - known.doneAt < DONE_SECONDS);
            if (!rising && !finishing)
                continue;
            if (!BarFxClient.near(p.centrePos.getX(), p.centrePos.getY(), p.centrePos.getZ(), 24))
                continue;
            scratch.add(p);
        }
        if (scratch.isEmpty())
            return;
        boolean far = BarFxClient.ortho && BarFxClient.zoom > FAR_ZOOM;

        // normal-blended: the ghost, the cloud, the cut line's body, the tools
        BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
        for (BuildingPlacement p : scratch) {
            Site s = siteOf(p);
            if (p.isBuilt) {
                if (s.doneAt < 0)
                    s.doneAt = clock;
                float k = clock - s.doneAt;
                // the ring swells to 1.5x and fades (0.6 s); the tools fly up and vanish (0.6 s)
                if (k < 0.6f && !Float.isNaN(s.ring[0])) {
                    float e = k / 0.6f;
                    drawRing(s, s.lastFront, clock, far, 1 - e, 1 + 0.5f * e);
                }
                if (!far && k < 0.6f) {
                    s.shape.layerBox(s.lastFront, s.cut);
                    for (int i = 0; i < 3; i++)
                        drawTool(s, i, s.doneAt + i * 0.37f, 1 - k / 0.6f, 4 * k * k / 0.36f, true);
                }
                continue;
            }
            // the front from what actually stands per layer (round 3: the count lagged the walls - stragglers like a
            // jammed pool held it down - and the outline wrapped built walls), eased so it climbs smoothly
            if (s.countedAt < 0 || clock - s.countedAt > RECOUNT) {
                s.countedAt = clock;
                java.util.Arrays.fill(s.standing, 0);
                net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null)
                    for (BuildingBlock b : p.getBlocks())
                        if (!b.getBlockState().isAir() && b.isPlaced(level))
                            s.standing[s.shape.layerOf(b.getBlockPos().getY())]++;
            }
            float target = s.shape.frontFromLayers(s.standing);
            s.shown = Float.isNaN(s.shown) ? target : s.shown + (target - s.shown) * Math.min(1, dt * 4);
            float front = s.shown;
            s.lastFront = front;
            // the ring's footprint glides to a narrower layer rather than snapping at a step
            s.shape.layerBox(front, s.cut);
            float ease = Float.isNaN(s.ring[0]) ? 1 : Math.min(1, dt * 2.5f);
            for (int i = 0; i < 4; i++)
                s.ring[i] = Float.isNaN(s.ring[i]) || ease >= 1 ? s.cut[i] : s.ring[i] + (s.cut[i] - s.ring[i]) * ease;
            drawGhost(s, front, far ? 0.08f : 0.065f, false);
            drawRing(s, front, clock, far, 1f, 1f);
            drawCutBody(s, front, far);
            if (!far)
                for (int i = 0; i < 3; i++) {
                    float t = clock + i * 0.37f;
                    // a faint trail: three fading copies a moment behind (they coincide when the tool is still)
                    drawTool(s, i, t - 0.12f, 0.1f, 0, false);
                    drawTool(s, i, t - 0.08f, 0.18f, 0, false);
                    drawTool(s, i, t - 0.04f, 0.3f, 0, false);
                    drawTool(s, i, t, 1f, 0, true);
                }
        }
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);

        // additive: the ghost's touch of light, the corner verticals, the cut line's glow and run, the light from above,
        // the tools' glints and sparks, the completion flash and ground ring
        BarFxClient.begin(buffers, BarFxRenderTypes.ADDITIVE, mat);
        for (BuildingPlacement p : scratch) {
            Site s = SITES.get(p);
            if (s == null)
                continue;
            if (p.isBuilt) {
                drawDone(s, clock - s.doneAt);
                continue;
            }
            float front = s.lastFront;
            drawGhost(s, front, 0.03f, true);
            drawCorners(s, front, far ? 0.2f : 0.1f, far ? 0.55f : 0.42f);
            drawCutLight(s, front, clock, far);
            if (far) {
                drawGlints(s, front, clock);
                continue;
            }
            drawPillar(s, front, clock);
            drawRays(s, front, clock);
            drawMotes(s, front, clock);
            for (int i = 0; i < 3; i++)
                drawToolLight(s, i, clock + i * 0.37f);
        }
        buffers.endBatch(BarFxRenderTypes.ADDITIVE);
        BarFxClient.vc = null;
    }

    // ------------------------------------------------------------------ the cloud ring

    /**
     * The cloud ring hugging the layer (ConstructionShape.ringPoint: the footprint grown by RING_PAD, rounded corners).
     * Round 5 critic (eyes 41049022535, 7/10): the round-5 band read as a lens-flare halo - a thin even circle through a
     * long building's corners. Now: ONE continuous soft band (a grey-blue underside under a white top) so it can never
     * become a necklace again, with lumpy volume on it - top-lit puff clusters in three sizes (1.5 / 1.0 / 0.6 blocks
     * across), white tops over a #C8CFD8 underside, 0.75 at the core falling to nothing, orbiting once in 12 s and each
     * bobbing +-0.15 on its own phase - and a few small flecks drifting off it.
     * {@code grow} scales the ring about its centre (the completion swell).
     */
    static void drawRing(Site s, float front, float clock, boolean far, float alpha, float grow) {
        float[] box = s.tmp, pt = s.pt;
        float mx = (s.ring[0] + s.ring[1]) * 0.5f, mz = (s.ring[2] + s.ring[3]) * 0.5f;
        box[0] = mx + (s.ring[0] - mx) * grow; box[1] = mx + (s.ring[1] - mx) * grow;
        box[2] = mz + (s.ring[2] - mz) * grow; box[3] = mz + (s.ring[3] - mz) * grow;
        float pad = RING_PAD * grow, y = front + 0.35f;
        // the band: a flat soft strip along the rounded rectangle, sampled every ~0.8 block
        float len = ConstructionShape.ringLength(box, pad);
        int n = Math.max(24, Math.min(96, Math.round(len / 0.8f)));
        float px = 0, pz = 0, pnx = 0, pnz = 0;
        for (int k = 0; k <= n; k++) {
            ConstructionShape.ringPoint(box, pad, (float) k / n, pt);
            if (k > 0) {
                band(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y - 0.15f, 0.8f, UNDER, 0.35f * alpha);
                band(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, 0.6f, CLOUD, 0.45f * alpha);
            }
            px = pt[0]; pz = pt[1]; pnx = pt[2]; pnz = pt[3];
        }
        // the puffs: three sizes, top-lit, orbiting once in 12 s, each bobbing on its own phase; each a cluster of a
        // main lobe and two smaller ones along the ring so the band's edge is lumpy, not beaded. One cluster per ~2.2
        // blocks of ring (10 at the least - a small tower's ring; a mock of the Barracks' 60-block ring needed ~28 to
        // read as cloud rather than a band with beads)
        int puffs = Math.max(10, Math.min(28, Math.round(len / 2.2f)));
        if (far)
            puffs = Math.max(8, puffs / 2);
        for (int k = 0; k < puffs; k++) {
            float hs = hash(k * 5 + 2);
            int size = hs < 0.45f ? 0 : hs < 0.8f ? 1 : 2;                // 1.5 / 1.0 / 0.6 blocks across
            float r = (size == 0 ? 0.75f : size == 1 ? 0.5f : 0.3f) * (0.9f + 0.2f * hash(k * 11 + 7));
            float u = (k + 0.35f * hash(k * 3 + 1)) / puffs + clock / 12f;
            ConstructionShape.ringPoint(box, pad + 0.15f * (1 - size * 0.5f), u, pt);
            float tx = -pt[3], tz = pt[2];                                   // along the ring
            float py = y + r * 0.25f + 0.15f * (float) Math.sin(clock * 0.8f + k * 2.1f);
            float a = alpha * (far ? 0.6f : 1f);
            for (int lobe = -1; lobe <= 1; lobe++) {
                float lr = lobe == 0 ? r : r * 0.7f, ly = lobe == 0 ? py : py - 0.12f * r;
                float lx = pt[0] + tx * lobe * r * 0.95f, lz = pt[1] + tz * lobe * r * 0.95f;
                BarFxClient.softDisc(lx, ly - lr * 0.35f, lz, lr * 1.25f, UNDER, 0.6f * a, UNDER, 0, 10, k + lobe, 0.1f);
                BarFxClient.softDisc(lx, ly + lr * 0.08f, lz, lr * 1.2f, CLOUD, 0.75f * a, CLOUD, 0, 12, k * 1.3f + lobe,
                        0.12f);
                BarFxClient.softDisc(lx, ly + lr * 0.4f, lz, lr * 0.6f, 0xFFFFFF, 0.45f * a, 0xFFFFFF, 0, 8,
                        k * 2.1f + lobe, 0);
            }
        }
        if (far)
            return;
        // flecks: five small wisps leaving the band, drifting out and up over 3 s
        for (int f = 0; f < 5; f++) {
            float t = clock / 3f + f / 5f;
            int cyc = (int) Math.floor(t);
            float k = t - cyc;
            ConstructionShape.ringPoint(box, pad, hash(cyc * 17 + f * 97), pt);
            float fx = pt[0] + pt[2] * 0.7f * k, fz = pt[1] + pt[3] * 0.7f * k;
            BarFxClient.softDisc(fx, y + 0.2f + 0.6f * k, fz, 0.12f, CLOUD, 0.55f * alpha * (float) Math.sin(Math.PI * k),
                    CLOUD, 0, 8, f, 0);
        }
    }

    /** One flat strip of the band between two ring points: its centre line at {@code a}, nothing at either side. */
    private static void band(float x0, float z0, float nx0, float nz0, float x1, float z1, float nx1, float nz1, float y,
                             float hw, int c, float a) {
        BarFxClient.v(x0 - nx0 * hw, y, z0 - nz0 * hw, c, 0);
        BarFxClient.v(x1 - nx1 * hw, y, z1 - nz1 * hw, c, 0);
        BarFxClient.v(x1, y, z1, c, a);
        BarFxClient.v(x0, y, z0, c, a);
        BarFxClient.v(x0, y, z0, c, a);
        BarFxClient.v(x1, y, z1, c, a);
        BarFxClient.v(x1 + nx1 * hw, y, z1 + nz1 * hw, c, 0);
        BarFxClient.v(x0 + nx0 * hw, y, z0 + nz0 * hw, c, 0);
    }

    // ------------------------------------------------------------------ the part still to come

    /**
     * The unbuilt part as a faint gold GHOST: every box still to come as translucent warm-gold faces (its four sides
     * and its top), fading upward - round 5 critic: lines alone read as a cage, and the white selection box twinned
     * it. Normal-blended at ~0.065 a face (two or three faces overlap on screen: ~0.12-0.18 in all), with a touch of
     * additive ({@code light}) so it glows a little on the lawn.
     */
    static void drawGhost(Site s, float front, float a, boolean light) {
        int n = s.shape.outline(front, s.outline);
        if (n == 0)
            return;
        float[] o = s.outline;
        float top = o[(n - 1) * 6 + 5], span = Math.max(1f, top - front);
        for (int i = 0; i < n; i++) {
            int b = i * 6;
            float x0 = o[b], x1 = o[b + 1], z0 = o[b + 2], z1 = o[b + 3], y0 = o[b + 4], y1 = o[b + 5];
            float a0 = a * (1f - 0.5f * (y0 - front) / span), a1 = a * (1f - 0.5f * (y1 - front) / span);
            if (!light) {
                face(x0, z0, x1, z0, y0, y1, a0, a1);
                face(x1, z0, x1, z1, y0, y1, a0, a1);
                face(x1, z1, x0, z1, y0, y1, a0, a1);
                face(x0, z1, x0, z0, y0, y1, a0, a1);
            }
            BarFxClient.v(x0, y1, z0, GHOST, a1);
            BarFxClient.v(x1, y1, z0, GHOST, a1);
            BarFxClient.v(x1, y1, z1, GHOST, a1);
            BarFxClient.v(x0, y1, z1, GHOST, a1);
        }
    }

    private static void face(float xa, float za, float xb, float zb, float y0, float y1, float a0, float a1) {
        BarFxClient.v(xa, y0, za, GHOST, a0);
        BarFxClient.v(xb, y0, zb, GHOST, a0);
        BarFxClient.v(xb, y1, zb, GHOST, a1);
        BarFxClient.v(xa, y1, za, GHOST, a1);
    }

    /** The ghost's one line element: each box's four corner verticals, fading upward (no rims - round 4 / 5). */
    static void drawCorners(Site s, float front, float w, float a) {
        int n = s.shape.outline(front, s.outline);
        if (n == 0)
            return;
        float[] o = s.outline;
        float top = o[(n - 1) * 6 + 5], span = Math.max(1f, top - front);
        for (int i = 0; i < n; i++) {
            int b = i * 6;
            float x0 = o[b], x1 = o[b + 1], z0 = o[b + 2], z1 = o[b + 3], y0 = o[b + 4], y1 = o[b + 5];
            float a0 = a * (1f - 0.75f * (y0 - front) / span), a1 = a * (1f - 0.75f * (y1 - front) / span);
            BarFxClient.streak(x0, y0, z0, x0, y1, z0, w, OUTLINE_GOLD, a0, a1);
            BarFxClient.streak(x1, y0, z0, x1, y1, z0, w, OUTLINE_GOLD, a0, a1);
            BarFxClient.streak(x1, y0, z1, x1, y1, z1, w, OUTLINE_GOLD, a0, a1);
            BarFxClient.streak(x0, y0, z1, x0, y1, z1, w, OUTLINE_GOLD, a0, a1);
        }
    }

    // ------------------------------------------------------------------ the cut line

    /**
     * The cut line's body, normal-blended: a #D9A441 edge with a warm gold-white #FFE9A0 core (round 5 critic: the
     * saturated yellow line twinned the game's static yellow selection rings).
     */
    static void drawCutBody(Site s, float front, boolean far) {
        float x0 = s.cut[0], x1 = s.cut[1], z0 = s.cut[2], z1 = s.cut[3], y = front + 0.04f;
        float we = far ? 0.32f : 0.2f, wc = far ? 0.14f : 0.08f;
        for (int pass = 0; pass < 2; pass++) {
            float w = pass == 0 ? we : wc, a = pass == 0 ? 0.85f : 1f;
            int c = pass == 0 ? CUT_EDGE : CUT_CORE;
            BarFxClient.streak(x0, y, z0, x1, y, z0, w, c, a, a);
            BarFxClient.streak(x1, y, z0, x1, y, z1, w, c, a, a);
            BarFxClient.streak(x1, y, z1, x0, y, z1, w, c, a, a);
            BarFxClient.streak(x0, y, z1, x0, y, z0, w, c, a, a);
        }
    }

    /**
     * The cut line's light: a soft glow, and a bright segment a fifth of its length travelling round it once every 2 s
     * - motion the static selection rings never have. Walked with perimeterPoint; a step that crosses a corner goes by
     * the corner, so the segment bends round it instead of cutting across.
     */
    static void drawCutLight(Site s, float front, float clock, boolean far) {
        float x0 = s.cut[0], x1 = s.cut[1], z0 = s.cut[2], z1 = s.cut[3], y = front + 0.05f;
        float w = far ? 0.3f : 0.18f;
        BarFxClient.streak(x0, y, z0, x1, y, z0, w, CUT_EDGE, 0.35f, 0.35f);
        BarFxClient.streak(x1, y, z0, x1, y, z1, w, CUT_EDGE, 0.35f, 0.35f);
        BarFxClient.streak(x1, y, z1, x0, y, z1, w, CUT_EDGE, 0.35f, 0.35f);
        BarFxClient.streak(x0, y, z1, x0, y, z0, w, CUT_EDGE, 0.35f, 0.35f);
        float[] a = s.pt, b = s.pt2;
        float u0 = clock / 2f;
        int steps = 10;
        ConstructionShape.perimeterPoint(s.cut, u0, a);
        for (int j = 1; j <= steps; j++) {
            ConstructionShape.perimeterPoint(s.cut, u0 + 0.2f * j / steps, b);
            float al = 0.95f * (float) Math.sin(Math.PI * (j - 0.5f) / steps);
            float ww = w * 1.4f;
            if (a[2] != b[2] || a[3] != b[3]) {   // round the corner between two sides
                float cx = a[2] != 0 ? a[0] : b[0], cz = a[3] != 0 ? a[1] : b[1];
                BarFxClient.streak(a[0], y, a[1], cx, y, cz, ww, CUT_RUN, al, al);
                BarFxClient.streak(cx, y, cz, b[0], y, b[1], ww, CUT_RUN, al, al);
            } else
                BarFxClient.streak(a[0], y, a[1], b[0], y, b[1], ww, CUT_RUN, al, al);
            a[0] = b[0]; a[1] = b[1]; a[2] = b[2]; a[3] = b[3];
        }
    }

    // ------------------------------------------------------------------ light from above

    /** The pillar: ~1 block wide, two crossed vertical planes turning slowly, 0.35 at its foot fading upward. */
    static void drawPillar(Site s, float front, float clock) {
        float cx = (s.cut[0] + s.cut[1]) * 0.5f, cz = (s.cut[2] + s.cut[3]) * 0.5f;
        float ang = clock * 0.25f;
        for (int k = 0; k < 2; k++) {
            float dx = (float) Math.cos(ang + k * Math.PI / 2), dz = (float) Math.sin(ang + k * Math.PI / 2);
            sheet(cx, cz, dx, dz, 0.5f, front, front + 7f, RAY, 0.35f);
        }
    }

    /** A vertical plane through (cx, cz) along (dx, dz): {@code a} on its centre line at the foot, nothing at its sides
     *  or top. */
    private static void sheet(float cx, float cz, float dx, float dz, float hw, float y0, float y1, int c, float a) {
        BarFxClient.v(cx - dx * hw, y0, cz - dz * hw, c, 0);
        BarFxClient.v(cx, y0, cz, c, a);
        BarFxClient.v(cx, y1, cz, c, 0);
        BarFxClient.v(cx - dx * hw, y1, cz - dz * hw, c, 0);
        BarFxClient.v(cx, y0, cz, c, a);
        BarFxClient.v(cx + dx * hw, y0, cz + dz * hw, c, 0);
        BarFxClient.v(cx + dx * hw, y1, cz + dz * hw, c, 0);
        BarFxClient.v(cx, y1, cz, c, 0);
    }

    /**
     * God rays: three soft angled sheets of light from one high sun descending through the cloud ring, 0.15 where they
     * land and nothing at the top, each sweeping +-10 degrees slowly.
     */
    static void drawRays(Site s, float front, float clock) {
        float[] pt = s.pt;
        for (int i = 0; i < 3; i++) {
            ConstructionShape.ringPoint(s.ring, RING_PAD, i / 3f + 0.1f + clock / 40f, pt);
            float tilt = 0.5f + 0.17f * (float) Math.sin(clock * 0.3f + i * 2.1f);   // ~29 deg +-10 off vertical
            float sx = 0.6f, sz = -0.8f, l = 9f;                                    // toward the sun, horizontally
            float bx = pt[0], by = front + 0.3f, bz = pt[1];
            float tx = bx + sx * (float) Math.sin(tilt) * l, ty = by + (float) Math.cos(tilt) * l;
            float tz = bz + sz * (float) Math.sin(tilt) * l;
            BarFxClient.streak(tx, ty, tz, bx, by, bz, 0.9f, RAY, 0f, 0.15f);
        }
    }

    /** Gold motes rising inside the footprint: about one a second, each 2 blocks up over 2 s. */
    static void drawMotes(Site s, float front, float clock) {
        float x0 = s.cut[0], x1 = s.cut[1], z0 = s.cut[2], z1 = s.cut[3];
        for (int j = 0; j < 2; j++) {
            float t = clock / 2f + j * 0.5f;
            int cyc = (int) Math.floor(t);
            float k = t - cyc;
            float x = x0 + 0.5f + Math.max(0, x1 - x0 - 1) * hash(cyc * 7 + j * 131);
            float z = z0 + 0.5f + Math.max(0, z1 - z0 - 1) * hash(cyc * 13 + j * 71);
            x += 0.15f * (float) Math.sin(clock * 2 + j);
            BarFxClient.softDisc(x, front + 0.2f + 2 * k, z, 0.09f, GOLD, 0.8f * (float) Math.sin(Math.PI * k), GOLD, 0,
                    8, 0, 0);
        }
    }

    // ------------------------------------------------------------------ the tools

    /** Hammer / chisel parts (blocks; ~30 px a block at the RTS zoom): ~1.2 blocks long. */
    private static final float HANDLE = 1.0f, HANDLE_W = 0.15f, HEAD_L = 0.62f, HEAD_W = 0.3f, RIM = 0.06f;
    private static final float CHISEL_GRIP = 0.45f, CHISEL_GRIP_W = 0.22f, CHISEL_BLADE = 0.65f;
    /** Struck (head on the cut line, the handle leaning out from the wall) and the wind-up: back ~40 degrees. */
    private static final float STRUCK = 0.35f, WIND_BACK = 0.7f;
    /** The tools are pulled this far toward the camera so the wall top they strike never hides their heads. */
    private static final float NUDGE = 0.6f;
    /** How far round the cut line a tool moves between blows (a fraction of it), plus a little jitter. */
    private static final float STEP = 0.06f;

    /**
     * Tool {@code i}'s spot on the cut line at time {@code t} into {@code s.pt} (with the strikeBeat in {@code s.beat}):
     * between blows it glides from its last spot to the next one a little further round. Returns the lift (blocks) of
     * that hop.
     */
    private static float toolSpot(Site s, int i, float t) {
        ConstructionShape.strikeBeat(t, s.beat);
        float c = s.beat[0], m = s.beat[1];
        float uPrev = i / 3f + (c - 1) * STEP + 0.03f * hash((int) c * 31 + i * 977 - 31);
        float uNext = i / 3f + c * STEP + 0.03f * hash((int) c * 31 + i * 977);
        float e = m * m * (3 - 2 * m);
        ConstructionShape.perimeterPoint(s.cut, uPrev + (uNext - uPrev) * e, s.pt);
        return 0.5f * (float) Math.sin(Math.PI * m);
    }

    /**
     * One tool at time {@code t}: a hard-edged silhouette in the SCREEN plane (camera left / up), so it always shows
     * full length with its head across the handle (round 4: soft streaks swinging in a world plane went edge-on and
     * vanished). The round 5 critic: they hovered - now each hops to a spot, winds back ~40 degrees (0.3 s), strikes
     * (0.1 s) and moves on (ConstructionShape.strikeBeat). {@code rim}: the dark rim and full body (the tool itself);
     * without it, only a faint fill (a trail copy). {@code fly}: blocks it has risen (the completion beat).
     */
    static void drawTool(Site s, int i, float t, float alpha, float fly, boolean rim) {
        if (alpha <= 0.01f)
            return;
        boolean chisel = i == 1;
        float lift = toolSpot(s, i, t);
        float[] pt = s.pt, b = s.beat;
        float out = pt[2] * BarFxClient.leftX + pt[3] * BarFxClient.leftZ >= 0 ? 1 : -1;   // the wall's outside: left?
        float lo = chisel ? 0.2f : STRUCK;
        float since = b[3];
        float theta = lo + (chisel ? 0.5f : 1f) * WIND_BACK * b[2] + 0.3f * (float) Math.sin(Math.PI * b[1])
                + (since >= 0 && since < 0.25f && b[1] >= 1 ? 0.15f * (float) Math.sin(Math.PI * since / 0.25f) : 0);
        float reach = chisel ? CHISEL_GRIP + CHISEL_BLADE : HANDLE;
        float su = (float) -Math.cos(lo), sl = (float) -Math.sin(lo) * out;
        float px = pt[0] - BarFxClient.lookX * NUDGE, py = s.lastFront + 0.04f + lift + fly - BarFxClient.lookY * NUDGE,
                pz = pt[1] - BarFxClient.lookZ * NUDGE;
        float gx = px - (su * BarFxClient.upX + sl * BarFxClient.leftX) * reach;
        float gy = py - (su * BarFxClient.upY + sl * BarFxClient.leftY) * reach;
        float gz = pz - (su * BarFxClient.upZ + sl * BarFxClient.leftZ) * reach;
        float du = (float) -Math.cos(theta), dl = (float) -Math.sin(theta) * out;   // grip -> working end
        for (int pass = rim ? 0 : 1; pass < 2; pass++) {   // the dark rim first, then the fill over it
            float e = pass == 0 ? RIM : 0;
            int c0 = pass == 0 ? TOOL_RIM : TOOL_HANDLE, c1 = pass == 0 ? TOOL_RIM : TOOL_BODY;
            float a = (pass == 0 ? 0.85f : 1f) * alpha;
            if (chisel) {
                bar(gx, gy, gz, du, dl, -e, CHISEL_GRIP + e, CHISEL_GRIP_W + 2 * e, CHISEL_GRIP_W + 2 * e, c0, a);
                bar(gx, gy, gz, du, dl, CHISEL_GRIP, CHISEL_BLADE + e, 0.15f + 2 * e, 0.05f + 2 * e, c1, a);
            } else {
                bar(gx, gy, gz, du, dl, -e, HANDLE + e, HANDLE_W + 2 * e, HANDLE_W + 2 * e, c0, a);
                float hx = gx + (du * BarFxClient.upX + dl * BarFxClient.leftX) * HANDLE;
                float hy = gy + (du * BarFxClient.upY + dl * BarFxClient.leftY) * HANDLE;
                float hz = gz + (du * BarFxClient.upZ + dl * BarFxClient.leftZ) * HANDLE;
                bar(hx, hy, hz, -dl, du, -HEAD_L / 2 - e, HEAD_L + 2 * e, HEAD_W + 2 * e, HEAD_W + 2 * e, c1, a);
            }
        }
        if (rim && fly == 0) {   // the tool itself: remember its working end for the light pass's glint
            s.heads[i * 3] = gx + (du * BarFxClient.upX + dl * BarFxClient.leftX) * reach;
            s.heads[i * 3 + 1] = gy + (du * BarFxClient.upY + dl * BarFxClient.leftY) * reach;
            s.heads[i * 3 + 2] = gz + (du * BarFxClient.upZ + dl * BarFxClient.leftZ) * reach;
        }
    }

    /**
     * A tool's light: a small glint on its head and, after each blow, a burst of 8 gold sparks from where it landed
     * (0.4 s, #FFE08A fading to nothing) over a brief small flash.
     */
    static void drawToolLight(Site s, int i, float t) {
        toolSpot(s, i, t);
        float[] pt = s.pt, b = s.beat;
        float since = b[3];
        float tw = 0.75f + 0.25f * (float) Math.sin(t * 5 + i * 2.1f);
        BarFxClient.softDisc(s.heads[i * 3], s.heads[i * 3 + 1], s.heads[i * 3 + 2], 0.12f, LIGHT, 0.35f * tw, GOLD, 0,
                8, 0, 0);
        if (since < 0 || since >= 0.4f)
            return;
        // the blow's spot: this cycle's if it has landed in it, else the last cycle's (the sparks outlive the cycle)
        float c = b[0];
        float hit = ConstructionShape.BEAT_MOVE + ConstructionShape.BEAT_WIND + ConstructionShape.BEAT_STRIKE;
        float k = t - c * ConstructionShape.BEAT_CYCLE;
        int sc = (int) (k >= hit ? c : c - 1);
        float u = i / 3f + sc * STEP + 0.03f * hash(sc * 31 + i * 977);
        ConstructionShape.perimeterPoint(s.cut, u, pt);
        float x = pt[0] - BarFxClient.lookX * NUDGE, y = s.lastFront + 0.05f - BarFxClient.lookY * NUDGE;
        float z = pt[1] - BarFxClient.lookZ * NUDGE;
        float f = 1 - since / 0.4f;
        if (since < 0.12f)
            BarFxClient.softDisc(x, y, z, 0.28f, LIGHT, 0.8f * (1 - since / 0.12f), GOLD, 0, 10, 0, 0);
        for (int j = 0; j < 8; j++) {
            float ang = (float) (hash(sc * 131 + i * 17 + j * 7) * 2 * Math.PI);
            float sp = 1.5f + 1.5f * hash(sc * 57 + j * 29 + i);
            float vx = (float) Math.cos(ang) * sp + pt[2] * 1.2f, vz = (float) Math.sin(ang) * sp + pt[3] * 1.2f;
            float vy = 2f + 1.5f * hash(sc * 23 + j * 3 + i * 5);
            float sx = x + vx * since, sy = y + vy * since - 6 * since * since, sz = z + vz * since;
            float vyNow = vy - 12 * since, l = 0.04f;
            BarFxClient.streak(sx - vx * l, sy - vyNow * l, sz - vz * l, sx, sy, sz, 0.07f, SPARK, 0.3f * f, f);
        }
    }

    /** Strategic zoom: the tools shrink to golden glints walking the cut line. */
    static void drawGlints(Site s, float front, float clock) {
        for (int i = 0; i < 2; i++) {
            ConstructionShape.perimeterPoint(s.cut, i / 2f + clock * 0.05f, s.pt);
            BarFxClient.softDisc(s.pt[0], front + 0.6f, s.pt[1], 0.32f, LIGHT, 1f, GOLD, 0.2f, 10, 0, 0);
        }
    }

    // ------------------------------------------------------------------ the completion beat

    /**
     * Done (the additive half; the ring's swell and the tools' flight are drawn in the normal-blended pass): a flash on
     * the building's top (0.3 s) and one ground-plane light ring radiating out and fading (0.5 s) - a flash, never
     * persistent.
     */
    static void drawDone(Site s, float k) {
        if (k < 0)
            return;
        float top = s.shape.minY() + s.shape.layers();
        s.shape.layerBox(top - 0.5f, s.tmp);
        if (k < 0.3f) {
            float e = k / 0.3f, a = (1 - e) * (1 - e);
            float tx = (s.tmp[0] + s.tmp[1]) * 0.5f, tz = (s.tmp[2] + s.tmp[3]) * 0.5f;
            BarFxClient.softDisc(tx, top + 0.3f, tz, 1.0f + 0.5f * e, LIGHT, a, GOLD, 0.3f * a, 14, 0, 0);
        }
        if (k < 0.5f) {
            float e = k / 0.5f;
            float r0 = s.shape.ringRadiusAt(s.shape.minY());
            BarFxClient.flatRing(s.shape.centreX(), s.shape.minY() + 0.1f, s.shape.centreZ(), r0 * (1 + 1.5f * e),
                    0.35f + 0.3f * e, GOLD, 0.7f * (1 - e), 48);
        }
    }

    // ------------------------------------------------------------------ primitives

    /**
     * A hard-edged bar in the screen plane: from (x, y, z) offset {@code from} along the screen direction (du up, dl
     * left; unit), {@code len} long, {@code w0} wide at its start and {@code w1} at its end, one alpha throughout
     * (BarFxClient.streak fades to nothing at its sides, so a thin one vanishes - the round 4 tools).
     */
    private static void bar(float x, float y, float z, float du, float dl, float from, float len, float w0, float w1,
                            int c, float a) {
        float dx = du * BarFxClient.upX + dl * BarFxClient.leftX;
        float dy = du * BarFxClient.upY + dl * BarFxClient.leftY;
        float dz = du * BarFxClient.upZ + dl * BarFxClient.leftZ;
        float nx = dl * BarFxClient.upX - du * BarFxClient.leftX;   // the screen perpendicular (-dl, du) up to sign
        float ny = dl * BarFxClient.upY - du * BarFxClient.leftY;
        float nz = dl * BarFxClient.upZ - du * BarFxClient.leftZ;
        float ax = x + dx * from, ay = y + dy * from, az = z + dz * from;
        float bx = ax + dx * len, by = ay + dy * len, bz = az + dz * len;
        float h0 = w0 * 0.5f, h1 = w1 * 0.5f;
        BarFxClient.v(ax - nx * h0, ay - ny * h0, az - nz * h0, c, a);
        BarFxClient.v(bx - nx * h1, by - ny * h1, bz - nz * h1, c, a);
        BarFxClient.v(bx + nx * h1, by + ny * h1, bz + nz * h1, c, a);
        BarFxClient.v(ax + nx * h0, ay + ny * h0, az + nz * h0, c, a);
    }

    private static float hash(int i) {
        int h = i * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535f;
    }
}
