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
 *   <li>a cloud RING hugging the layer being built - not a cloud overhead, the RTS camera looks down - just under the
 *   build front and climbing with it: overlapping top-lit puffs with a cool shaded underside on one continuous band;</li>
 *   <li>the part still to come is a gold GHOST of its steps (a ~0.3 gold silhouette with 1 px gold edges), so the
 *   gold-vs-stone split says how far along it is at a glance;</li>
 *   <li>the build front's cut line: a white-gold core in a gold glow, with a bright knot running round it;</li>
 *   <li>light from above: one or two soft wide shafts landing in a pool on the cut, slow gold motes rising;</li>
 *   <li>golden hammers and a chisel move from spot to spot on the wall course being laid and strike it: sparks, a
 *   flash on the struck face, a puff of dust;</li>
 *   <li>done: the ring swells and fades, a flash on the top, a light ring runs out over the ground, the tools fly up.</li>
 * </ul>
 * Building-agnostic: everything comes from the placement's own blocks (the buildings are being reworked). Pure client
 * rendering through BarFxClient's passes; thinner when zoomed out; nothing allocated per frame once a site's shape is
 * cached (one ConstructionShape per placement, built from its blocks the first time it is seen rising). Glow budget
 * (round 6 critic): the ghost, cloud, cut line and tools are normal-blended; only small or soft parts are additive, so
 * the built white stone keeps its edges, and the effect stays warm gold - the builders' cool-white beams are the only
 * white line family.
 */
final class ConstructionFx {

    private ConstructionFx() { }

    private static final int CLOUD = 0xFFFBF2, GOLD = 0xFFD27A, LIGHT = 0xFFF3D2;
    /** The cloud's underside: a cool shade so the cloud separates from the white stone (round 6 critic). */
    private static final int UNDER = 0xB9C3D6;
    /** The cut line: a white-gold core (round 6: 2 px) in a #F0B840 glow; the knot running round it. */
    private static final int CUT_CORE = 0xFFF8E0, CUT_GLOW = 0xF0B840, CUT_KNOT = 0xFFE7A8;
    /** The ghost of the part still to come (round 6 critic: #E8C25A at ~0.3) - its top a shade lighter than its sides -
     *  and its 1 px edges. */
    private static final int GHOST_TOP = 0xEDCB68, GHOST_SIDE = 0xE0B64C, GHOST_EDGE = 0xD99A1E;
    /** The light from above: a warm gold-white, additive. */
    private static final int RAY = 0xFFE9B8;
    /** Sparks at each blow: a gold fading to transparent; the struck face's flash; the blow's dust. */
    private static final int SPARK = 0xFFE08A, STRUCK_FLASH = 0xFFF6D8, DUST = 0xD9D3C6;
    /** The tools, normal-blended: a gold head and an ivory handle inside a dark rim inside a 1 px gold outline, so a
     *  tool reads on the white walls and on the lawn alike (rounds 3-6). */
    private static final int TOOL_BODY = 0xE0A83A, TOOL_HANDLE = 0xF1E7CF, TOOL_RIM = 0x3A2710, TOOL_OUTLINE = 0xF0B840;
    /** Seconds the completion beat lasts (the ring's 0.6 s swell is the longest part, the tools' flight 0.6 s). */
    private static final float DONE_SECONDS = 0.8f;
    /** Past this RTS zoom (strategic) the ring keeps fewer puffs, the tools become glints and the light stops. */
    private static final float FAR_ZOOM = 70;
    /** The cloud ring stands this far outside the layer's footprint (round 6 critic: at most a block). */
    private static final float RING_PAD = 0.5f;
    /** The cloud ring's centre sits this far below the cut line (round 6 critic: ~0.5 block). */
    private static final float RING_DROP = 0.5f;
    /** The cut line sits this far inside the wall faces, never on the selection box's edge (round 6 critic). */
    private static final float CUT_INSET = 0.05f;
    /** Seconds between recounts of what stands in each layer (a few hundred block-state reads per site). */
    private static final float RECOUNT = 0.5f;

    /** Per rising site: its shape, the front drawn (eased), the ring's eased footprint, when it finished. */
    private static final class Site {
        final ConstructionShape shape;
        final float[] outline;
        final float[] cut = new float[4];              // the layer's footprint at the front
        final float[] cutIn = new float[4];            // ... inset CUT_INSET: the cut line
        final float[] course = new float[4];           // ... inset half a block: the middle of the wall course
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

    /** The cut's footprint and its two insets, for the front. */
    private static void boxes(Site s, float front) {
        s.shape.layerBox(front, s.cut);
        inset(s.cut, CUT_INSET, s.cutIn);
        inset(s.cut, 0.5f, s.course);
    }

    /** {@code src} shrunk by {@code d} on every side, never past its middle. */
    private static void inset(float[] src, float d, float[] out) {
        float mx = (src[0] + src[1]) * 0.5f, mz = (src[2] + src[3]) * 0.5f;
        out[0] = Math.min(mx, src[0] + d); out[1] = Math.max(mx, src[1] - d);
        out[2] = Math.min(mz, src[2] + d); out[3] = Math.max(mz, src[3] - d);
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

        // normal-blended: the ghost, the cloud, the cut line, the tools and their dust
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
                    boxes(s, s.lastFront);
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
            boxes(s, front);
            // the ring's footprint glides to a narrower layer rather than snapping at a step
            float ease = Float.isNaN(s.ring[0]) ? 1 : Math.min(1, dt * 2.5f);
            for (int i = 0; i < 4; i++)
                s.ring[i] = Float.isNaN(s.ring[i]) || ease >= 1 ? s.cut[i] : s.ring[i] + (s.cut[i] - s.ring[i]) * ease;
            drawRing(s, front, clock, far, 1f, 1f);
            drawGhost(s, front, far);
            drawCut(s, front, far);
            if (!far)
                for (int i = 0; i < 3; i++) {
                    float t = clock + i * 0.37f;
                    drawDust(s, i, t);
                    // a faint trail: three fading copies a moment behind (they coincide when the tool is still)
                    drawTool(s, i, t - 0.12f, 0.1f, 0, false);
                    drawTool(s, i, t - 0.08f, 0.18f, 0, false);
                    drawTool(s, i, t - 0.04f, 0.3f, 0, false);
                    drawTool(s, i, t, 1f, 0, true);
                }
        }
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);

        // additive, kept small and soft: the cut line's running knot, the shafts and their pool, the motes, the tools'
        // glints, sparks and struck-face flashes, the completion flash and ground ring
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
            drawKnot(s, front, clock, far);
            if (far) {
                drawGlints(s, front, clock);
                continue;
            }
            drawShafts(s, front, clock);
            drawMotes(s, front, clock);
            for (int i = 0; i < 3; i++)
                drawToolLight(s, i, clock + i * 0.37f);
        }
        buffers.endBatch(BarFxRenderTypes.ADDITIVE);
        BarFxClient.vc = null;
    }

    // ------------------------------------------------------------------ the cloud ring

    /**
     * The cloud ring hugging the layer (ConstructionShape.ringPoint: the footprint grown by RING_PAD, rounded corners),
     * centred RING_DROP under the cut line. Round 6 critic: the puffs framed the building but their white merged with
     * the white stone. Now one continuous soft band (so it can never become a necklace again) under overlapping puffs -
     * each overlapping its neighbours by ~40%, in three sizes (1.5 / 1.0 / 0.6 blocks across), a cool #B9C3D6 underside
     * at 0.5 under a white top at 0.75 falling to nothing, breathing +-10% at 0.4 Hz and bobbing +-0.15 on its own
     * phase, the whole ring orbiting once in 12 s - and a few small flecks drifting off it.
     * {@code grow} scales the ring about its centre (the completion swell).
     */
    static void drawRing(Site s, float front, float clock, boolean far, float alpha, float grow) {
        float[] box = s.tmp, pt = s.pt;
        float mx = (s.ring[0] + s.ring[1]) * 0.5f, mz = (s.ring[2] + s.ring[3]) * 0.5f;
        box[0] = mx + (s.ring[0] - mx) * grow; box[1] = mx + (s.ring[1] - mx) * grow;
        box[2] = mz + (s.ring[2] - mz) * grow; box[3] = mz + (s.ring[3] - mz) * grow;
        float pad = RING_PAD * grow, y = front - RING_DROP;
        // the band: a flat soft strip along the rounded rectangle, sampled every ~0.8 block
        float len = ConstructionShape.ringLength(box, pad);
        int n = Math.max(24, Math.min(96, Math.round(len / 0.8f)));
        float px = 0, pz = 0, pnx = 0, pnz = 0;
        for (int k = 0; k <= n; k++) {
            ConstructionShape.ringPoint(box, pad, (float) k / n, pt);
            if (k > 0) {
                band(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y - 0.2f, 0.8f, UNDER, 0.4f * alpha);
                band(px, pz, pnx, pnz, pt[0], pt[1], pt[2], pt[3], y, 0.6f, CLOUD, 0.45f * alpha);
            }
            px = pt[0]; pz = pt[1]; pnx = pt[2]; pnz = pt[3];
        }
        // the puffs: placed along the ring so neighbours overlap ~40% (centres 0.6 x the sum of their radii apart),
        // the spacing stretched a little so the last meets the first. Two passes (sum the spacing, then draw), no
        // allocation. Strategic zoom: every other puff.
        int puffs = Math.max(12, Math.min(110, Math.round(len / 0.68f)));
        float sum = 0;
        for (int k = 0; k < puffs; k++)
            sum += 0.6f * (puffRadius(k) + puffRadius((k + 1) % puffs));
        float stretch = len / Math.max(1e-3f, sum), along = 0;
        float a = alpha * (far ? 0.7f : 1f);
        for (int k = 0; k < puffs; k++) {
            float r = puffRadius(k) * (1 + 0.1f * (float) Math.sin(clock * 2.51f + k * 1.9f));   // breathing, 0.4 Hz
            float u = along / len + clock / 12f;
            along += 0.6f * (puffRadius(k) + puffRadius((k + 1) % puffs)) * stretch;
            if (far && (k & 1) == 1)
                continue;
            ConstructionShape.ringPoint(box, pad + 0.1f * hash(k * 13 + 5), u, pt);
            float py = y + r * 0.3f + 0.15f * (float) Math.sin(clock * 0.8f + k * 2.1f);
            BarFxClient.softDisc(pt[0], py - r * 0.35f, pt[1], r * 1.15f, UNDER, 0.5f * a, UNDER, 0, 10, k, 0.1f);
            BarFxClient.softDisc(pt[0], py + r * 0.1f, pt[1], r, CLOUD, 0.75f * a, CLOUD, 0, 12, k * 1.3f, 0.12f);
            BarFxClient.softDisc(pt[0], py + r * 0.4f, pt[1], r * 0.5f, 0xFFFFFF, 0.4f * a, 0xFFFFFF, 0, 8, k * 2.1f, 0);
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
            BarFxClient.softDisc(fx, y + 0.3f + 0.6f * k, fz, 0.12f, CLOUD, 0.55f * alpha * (float) Math.sin(Math.PI * k),
                    CLOUD, 0, 8, f, 0);
        }
    }

    /** Puff k's radius: three sizes (1.5 / 1.0 / 0.6 blocks across, mostly the bigger two), a little jitter. */
    private static float puffRadius(int k) {
        float hs = hash(k * 5 + 2);
        return (hs < 0.45f ? 0.75f : hs < 0.8f ? 0.5f : 0.3f) * (0.9f + 0.2f * hash(k * 11 + 7));
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
     * The unbuilt part as a gold GHOST. Round 6 critic: at ~45% the Barracks "reads fully built", no gold above the
     * cut. Root cause (eyes 41060902092, log "320 blocks"; barracks.nbt per layer): the front was right - layers 0-2,
     * the courtyard floor and the hall's 3-block plinth, are 47% of the blocks and stood; the hall above was unbuilt -
     * but the ghost drawn over it was every face of every box at 0.065, warm-light gold over the white stone and the
     * lawn, so it vanished, and the plinth read as a finished building. Now only the faces turned to the camera are
     * drawn (a box's front faces never overlap on screen, so the silhouette has one even alpha): ~0.3 #E8C25A, the
     * top a shade lighter than the sides, with 1 px gold edges (the top rim and the visible corners; no bottom edge,
     * which is the cut line). Everything still to come is ghosted, so there are always as many ghost courses above
     * the cut as the building has left.
     */
    static void drawGhost(Site s, float front, boolean far) {
        int n = s.shape.outline(front, s.outline);
        if (n == 0)
            return;
        float[] o = s.outline;
        float lx = BarFxClient.lookX, lz = BarFxClient.lookZ;
        boolean showMinX = lx > 0, showMaxX = lx < 0, showMinZ = lz > 0, showMaxZ = lz < 0;   // faces turned to us
        float aSide = far ? 0.34f : 0.3f, aTop = far ? 0.3f : 0.26f;
        for (int i = 0; i < n; i++) {
            int b = i * 6;
            float x0 = o[b], x1 = o[b + 1], z0 = o[b + 2], z1 = o[b + 3], y0 = o[b + 4] + 0.02f, y1 = o[b + 5];
            if (showMinZ) face(x0, z0, x1, z0, y0, y1, GHOST_SIDE, aSide);
            if (showMaxZ) face(x1, z1, x0, z1, y0, y1, GHOST_SIDE, aSide);
            if (showMinX) face(x0, z1, x0, z0, y0, y1, GHOST_SIDE, aSide * 0.9f);
            if (showMaxX) face(x1, z0, x1, z1, y0, y1, GHOST_SIDE, aSide * 0.9f);
            BarFxClient.v(x0, y1, z0, GHOST_TOP, aTop);
            BarFxClient.v(x1, y1, z0, GHOST_TOP, aTop);
            BarFxClient.v(x1, y1, z1, GHOST_TOP, aTop);
            BarFxClient.v(x0, y1, z1, GHOST_TOP, aTop);
            // the 1 px edges: the top rim, and each corner vertical next to a face turned to us
            float w = far ? 0.08f : 0.04f, ea = 0.9f;
            line(x0, y1, z0, x1, y1, z0, w, GHOST_EDGE, ea);
            line(x1, y1, z0, x1, y1, z1, w, GHOST_EDGE, ea);
            line(x1, y1, z1, x0, y1, z1, w, GHOST_EDGE, ea);
            line(x0, y1, z1, x0, y1, z0, w, GHOST_EDGE, ea);
            if (showMinX || showMinZ) line(x0, y0, z0, x0, y1, z0, w, GHOST_EDGE, ea);
            if (showMaxX || showMinZ) line(x1, y0, z0, x1, y1, z0, w, GHOST_EDGE, ea);
            if (showMaxX || showMaxZ) line(x1, y0, z1, x1, y1, z1, w, GHOST_EDGE, ea);
            if (showMinX || showMaxZ) line(x0, y0, z1, x0, y1, z1, w, GHOST_EDGE, ea);
        }
    }

    private static void face(float xa, float za, float xb, float zb, float y0, float y1, int c, float a) {
        BarFxClient.v(xa, y0, za, c, a);
        BarFxClient.v(xb, y0, zb, c, a);
        BarFxClient.v(xb, y1, zb, c, a);
        BarFxClient.v(xa, y1, za, c, a);
    }

    // ------------------------------------------------------------------ the cut line

    /**
     * The cut line, normal-blended, CUT_INSET inside the wall faces (so it never lies on the selection box's edge): a
     * soft #F0B840 glow round a 2 px #FFF8E0 core (round 6 critic). Normal-blended so it adds no glare to the stone.
     */
    static void drawCut(Site s, float front, boolean far) {
        float x0 = s.cutIn[0], x1 = s.cutIn[1], z0 = s.cutIn[2], z1 = s.cutIn[3], y = front + 0.04f;
        float wg = far ? 0.34f : 0.22f, wc = far ? 0.12f : 0.07f;
        BarFxClient.streak(x0, y, z0, x1, y, z0, wg, CUT_GLOW, 0.6f, 0.6f);
        BarFxClient.streak(x1, y, z0, x1, y, z1, wg, CUT_GLOW, 0.6f, 0.6f);
        BarFxClient.streak(x1, y, z1, x0, y, z1, wg, CUT_GLOW, 0.6f, 0.6f);
        BarFxClient.streak(x0, y, z1, x0, y, z0, wg, CUT_GLOW, 0.6f, 0.6f);
        line(x0, y, z0, x1, y, z0, wc, CUT_CORE, 1f);
        line(x1, y, z0, x1, y, z1, wc, CUT_CORE, 1f);
        line(x1, y, z1, x0, y, z1, wc, CUT_CORE, 1f);
        line(x0, y, z1, x0, y, z0, wc, CUT_CORE, 1f);
    }

    /**
     * The running knot: a bright 2-block stretch of the cut line travelling round it at 6 blocks a second, +60%
     * (additive) - motion the static selection rings never have. Walked with perimeterPoint; a step that crosses a
     * corner goes by the corner, so the knot bends round it.
     */
    static void drawKnot(Site s, float front, float clock, boolean far) {
        float[] box = s.cutIn, a = s.pt, b = s.pt2;
        float per = 2 * (Math.max(0, box[1] - box[0]) + Math.max(0, box[3] - box[2]));
        if (per <= 0)
            return;
        float y = front + 0.05f, w = far ? 0.4f : 0.22f;
        float centre = clock * 6f;
        int steps = 8;
        ConstructionShape.perimeterPoint(box, (centre - 1f) / per, a);
        for (int j = 1; j <= steps; j++) {
            ConstructionShape.perimeterPoint(box, (centre - 1f + 2f * j / steps) / per, b);
            float al = 0.6f * (float) Math.sin(Math.PI * (j - 0.5f) / steps);
            if (a[2] != b[2] || a[3] != b[3]) {   // round the corner between two sides
                float cx = a[2] != 0 ? a[0] : b[0], cz = a[3] != 0 ? a[1] : b[1];
                BarFxClient.streak(a[0], y, a[1], cx, y, cz, w, CUT_KNOT, al, al);
                BarFxClient.streak(cx, y, cz, b[0], y, b[1], w, CUT_KNOT, al, al);
            } else
                BarFxClient.streak(a[0], y, a[1], b[0], y, b[1], w, CUT_KNOT, al, al);
            a[0] = b[0]; a[1] = b[1]; a[2] = b[2]; a[3] = b[3];
        }
    }

    // ------------------------------------------------------------------ light from above

    /**
     * Shafts, not rain (round 6 critic: the thin vertical streaks read as gold rain and merged with the builders' beam
     * fan): two soft shafts ~1.8 blocks wide at the top tapering to ~0.6 at the cut, 0.2 at most, drifting slowly over
     * the footprint, each landing in a soft bright pool on the cut plane.
     */
    static void drawShafts(Site s, float front, float clock) {
        float mx = (s.cut[0] + s.cut[1]) * 0.5f, mz = (s.cut[2] + s.cut[3]) * 0.5f;
        float hx = Math.max(0, (s.cut[1] - s.cut[0]) * 0.5f - 1f), hz = Math.max(0, (s.cut[3] - s.cut[2]) * 0.5f - 1f);
        for (int i = 0; i < 2; i++) {
            float x = mx + hx * 0.7f * (float) Math.sin(clock * 0.07f + i * 2.5f);
            float z = mz + hz * 0.7f * (float) Math.cos(clock * 0.05f + i * 1.7f);
            float pulse = 0.85f + 0.15f * (float) Math.sin(clock * 0.6f + i * 3.1f);
            float yb = front + 0.05f, ym = front + 3f, yt = front + 8f;
            taper(x, yt, z, 0.9f, 0f, x, ym, z, 0.6f, 0.17f * pulse);
            taper(x, ym, z, 0.6f, 0.17f * pulse, x, yb, z, 0.3f, 0.2f * pulse);
            flatDisc(x, yb + 0.02f, z, 1.0f, RAY, 0.18f * pulse, 14);
        }
    }

    /** A soft camera-facing band from p to q: half-width hp / hq, centre alpha ap / aq, nothing at its sides. */
    private static void taper(float px, float py, float pz, float hp, float ap, float qx, float qy, float qz, float hq,
                              float aq) {
        float dx = qx - px, dy = qy - py, dz = qz - pz;
        float sx = dy * BarFxClient.lookZ - dz * BarFxClient.lookY, sy = dz * BarFxClient.lookX - dx * BarFxClient.lookZ;
        float sz = dx * BarFxClient.lookY - dy * BarFxClient.lookX;
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-5f) { sx = BarFxClient.leftX; sy = BarFxClient.leftY; sz = BarFxClient.leftZ; l = 1; }
        sx /= l; sy /= l; sz /= l;
        BarFxClient.v(px - sx * hp, py - sy * hp, pz - sz * hp, RAY, 0);
        BarFxClient.v(px, py, pz, RAY, ap);
        BarFxClient.v(qx, qy, qz, RAY, aq);
        BarFxClient.v(qx - sx * hq, qy - sy * hq, qz - sz * hq, RAY, 0);
        BarFxClient.v(px, py, pz, RAY, ap);
        BarFxClient.v(px + sx * hp, py + sy * hp, pz + sz * hp, RAY, 0);
        BarFxClient.v(qx + sx * hq, qy + sy * hq, qz + sz * hq, RAY, 0);
        BarFxClient.v(qx, qy, qz, RAY, aq);
    }

    /** Slow gold motes rising inside the footprint: eight at a time, 0.3 block a second, each living 6 s. */
    static void drawMotes(Site s, float front, float clock) {
        float x0 = s.course[0], x1 = s.course[1], z0 = s.course[2], z1 = s.course[3];
        for (int j = 0; j < 8; j++) {
            float t = clock / 6f + j / 8f;
            int cyc = (int) Math.floor(t);
            float k = t - cyc;
            float x = x0 + (x1 - x0) * hash(cyc * 7 + j * 131) + 0.15f * (float) Math.sin(clock * 0.9f + j);
            float z = z0 + (z1 - z0) * hash(cyc * 13 + j * 71) + 0.15f * (float) Math.cos(clock * 0.7f + j * 2);
            BarFxClient.softDisc(x, front + 0.2f + 1.8f * k, z, 0.07f, GOLD, 0.7f * (float) Math.sin(Math.PI * k), GOLD,
                    0, 8, 0, 0);
        }
    }

    // ------------------------------------------------------------------ the tools

    /** Hammer / chisel parts (blocks; ~30 px a block at the RTS zoom): ~1.0-1.1 blocks long (round 6 critic). */
    private static final float HANDLE = 0.85f, HANDLE_W = 0.14f, HEAD_L = 0.55f, HEAD_W = 0.26f;
    private static final float CHISEL_GRIP = 0.4f, CHISEL_GRIP_W = 0.2f, CHISEL_BLADE = 0.6f;
    /** The 1 px gold outline outside a 1 px dark rim (blocks). */
    private static final float OUTLINE = 0.07f, RIM = 0.035f;
    /** Struck (head on the course, the handle leaning in over the building) and the wind-up: back ~40 degrees. */
    private static final float STRUCK = 0.35f, WIND_BACK = 0.7f;
    /** The tools are pulled this far toward the camera so the course they strike never hides their heads. */
    private static final float NUDGE = 0.6f;
    /** How far round the course a tool moves between blows (a fraction of it), plus a little jitter. */
    private static final float STEP = 0.06f;

    /**
     * Tool {@code i}'s spot at time {@code t} into {@code s.pt} (with the strikeBeat in {@code s.beat}): on the MIDDLE
     * of the wall course being laid (s.course, half a block in from the faces - round 6 critic: tools hovered over the
     * air outside the wall), gliding between blows from its last spot to the next one a little further round. Returns
     * the lift (blocks) of that hop.
     */
    private static float toolSpot(Site s, int i, float t) {
        ConstructionShape.strikeBeat(t, s.beat);
        float c = s.beat[0], m = s.beat[1];
        float e = m * m * (3 - 2 * m);
        float uPrev = spotU(i, (int) c - 1), uNext = spotU(i, (int) c);
        ConstructionShape.perimeterPoint(s.course, uPrev + (uNext - uPrev) * e, s.pt);
        return 0.4f * (float) Math.sin(Math.PI * m);
    }

    private static float spotU(int i, int cycle) {
        return i / 3f + cycle * STEP + 0.03f * hash(cycle * 31 + i * 977);
    }

    /**
     * One tool at time {@code t}: a hard-edged silhouette in the SCREEN plane (camera left / up), so it always shows
     * full length with its head across the handle (round 4: soft streaks swinging in a world plane went edge-on and
     * vanished). It hops to a spot, winds back ~40 degrees (0.3 s), strikes (0.1 s) and moves on
     * (ConstructionShape.strikeBeat), its handle leaning in over the building. {@code full}: the 1 px gold outline,
     * the dark rim and the body (the tool itself); without it, only a faint fill (a trail copy). {@code fly}: blocks it
     * has risen (the completion beat).
     */
    static void drawTool(Site s, int i, float t, float alpha, float fly, boolean full) {
        if (alpha <= 0.01f)
            return;
        boolean chisel = i == 1;
        float lift = toolSpot(s, i, t);
        float[] pt = s.pt, b = s.beat;
        // the inside of the wall, in screen terms: +1 when it is toward screen-left
        float in = pt[2] * BarFxClient.leftX + pt[3] * BarFxClient.leftZ >= 0 ? -1 : 1;
        float lo = chisel ? 0.2f : STRUCK;
        float since = b[3];
        float theta = lo + (chisel ? 0.5f : 1f) * WIND_BACK * b[2] + 0.3f * (float) Math.sin(Math.PI * b[1])
                + (since >= 0 && since < 0.25f && b[1] >= 1 ? 0.15f * (float) Math.sin(Math.PI * since / 0.25f) : 0);
        float reach = chisel ? CHISEL_GRIP + CHISEL_BLADE : HANDLE;
        // struck, the working end sits on the spot and the grip is up and IN from it
        float su = (float) -Math.cos(lo), sl = (float) -Math.sin(lo) * in;
        float px = pt[0] - BarFxClient.lookX * NUDGE, py = s.lastFront + 0.04f + lift + fly - BarFxClient.lookY * NUDGE,
                pz = pt[1] - BarFxClient.lookZ * NUDGE;
        float gx = px - (su * BarFxClient.upX + sl * BarFxClient.leftX) * reach;
        float gy = py - (su * BarFxClient.upY + sl * BarFxClient.leftY) * reach;
        float gz = pz - (su * BarFxClient.upZ + sl * BarFxClient.leftZ) * reach;
        float du = (float) -Math.cos(theta), dl = (float) -Math.sin(theta) * in;   // grip -> working end
        for (int pass = full ? 0 : 2; pass < 3; pass++) {   // the gold outline, the dark rim, then the fill
            float e = pass == 0 ? OUTLINE : pass == 1 ? RIM : 0;
            int c0 = pass == 0 ? TOOL_OUTLINE : pass == 1 ? TOOL_RIM : TOOL_HANDLE;
            int c1 = pass == 0 ? TOOL_OUTLINE : pass == 1 ? TOOL_RIM : TOOL_BODY;
            float a = (pass == 0 ? 0.9f : pass == 1 ? 0.85f : 1f) * alpha;
            if (chisel) {
                bar(gx, gy, gz, du, dl, -e, CHISEL_GRIP + e, CHISEL_GRIP_W + 2 * e, CHISEL_GRIP_W + 2 * e, c0, a);
                bar(gx, gy, gz, du, dl, CHISEL_GRIP, CHISEL_BLADE + e, 0.14f + 2 * e, 0.05f + 2 * e, c1, a);
            } else {
                bar(gx, gy, gz, du, dl, -e, HANDLE + e, HANDLE_W + 2 * e, HANDLE_W + 2 * e, c0, a);
                float hx = gx + (du * BarFxClient.upX + dl * BarFxClient.leftX) * HANDLE;
                float hy = gy + (du * BarFxClient.upY + dl * BarFxClient.leftY) * HANDLE;
                float hz = gz + (du * BarFxClient.upZ + dl * BarFxClient.leftZ) * HANDLE;
                bar(hx, hy, hz, -dl, du, -HEAD_L / 2 - e, HEAD_L + 2 * e, HEAD_W + 2 * e, HEAD_W + 2 * e, c1, a);
            }
        }
        if (full && fly == 0) {   // the tool itself: remember its working end for the light pass's glint
            s.heads[i * 3] = gx + (du * BarFxClient.upX + dl * BarFxClient.leftX) * reach;
            s.heads[i * 3 + 1] = gy + (du * BarFxClient.upY + dl * BarFxClient.leftY) * reach;
            s.heads[i * 3 + 2] = gz + (du * BarFxClient.upZ + dl * BarFxClient.leftZ) * reach;
        }
    }

    /**
     * Where tool {@code i}'s last blow landed into {@code s.pt}, and how long ago (seconds, -1 for none): this cycle's
     * spot if it has struck in it, else the last cycle's (sparks and dust outlive the cycle).
     */
    private static float lastBlow(Site s, int i, float t) {
        ConstructionShape.strikeBeat(t, s.beat);
        float since = s.beat[3], c = s.beat[0];
        if (since < 0)
            return -1;
        float hit = ConstructionShape.BEAT_MOVE + ConstructionShape.BEAT_WIND + ConstructionShape.BEAT_STRIKE;
        int sc = (int) (t - c * ConstructionShape.BEAT_CYCLE >= hit ? c : c - 1);
        ConstructionShape.perimeterPoint(s.course, spotU(i, sc), s.pt);
        s.beat[0] = sc;
        return since;
    }

    /** A blow's dust: three small soft puffs of stone dust swelling and rising off the struck face (0.5 s). */
    static void drawDust(Site s, int i, float t) {
        float since = lastBlow(s, i, t);
        if (since < 0 || since >= 0.5f)
            return;
        int sc = (int) s.beat[0];
        float e = since / 0.5f, y = s.lastFront + 0.1f;
        for (int j = 0; j < 3; j++) {
            float ang = (float) (hash(sc * 41 + i * 7 + j * 3) * 2 * Math.PI);
            float d = 0.15f + 0.3f * e;
            BarFxClient.softDisc(s.pt[0] + (float) Math.cos(ang) * d, y + 0.35f * e, s.pt[1] + (float) Math.sin(ang) * d,
                    0.18f + 0.22f * e, DUST, 0.4f * (1 - e), DUST, 0, 8, j, 0.15f);
        }
    }

    /**
     * A tool's light: a small glint on its head and, after each blow, 8 gold sparks from where it landed (0.4 s,
     * #FFE08A fading out) over a small flash flat on the struck face (0.25 s).
     */
    static void drawToolLight(Site s, int i, float t) {
        float tw = 0.75f + 0.25f * (float) Math.sin(t * 5 + i * 2.1f);
        BarFxClient.softDisc(s.heads[i * 3], s.heads[i * 3 + 1], s.heads[i * 3 + 2], 0.1f, LIGHT, 0.25f * tw, GOLD, 0,
                8, 0, 0);
        float since = lastBlow(s, i, t);
        if (since < 0 || since >= 0.4f)
            return;
        int sc = (int) s.beat[0];
        float[] pt = s.pt;
        float fx = pt[0], fz = pt[1], fy = s.lastFront + 0.06f;
        if (since < 0.25f)
            flatDisc(fx, fy, fz, 0.3f + 0.2f * since / 0.25f, STRUCK_FLASH, 0.7f * (1 - since / 0.25f), 12);
        float x = fx - BarFxClient.lookX * NUDGE, y = fy - BarFxClient.lookY * NUDGE, z = fz - BarFxClient.lookZ * NUDGE;
        float f = 1 - since / 0.4f;
        for (int j = 0; j < 8; j++) {
            float ang = (float) (hash(sc * 131 + i * 17 + j * 7) * 2 * Math.PI);
            float sp = 1.2f + 1.3f * hash(sc * 57 + j * 29 + i);
            float vx = (float) Math.cos(ang) * sp, vz = (float) Math.sin(ang) * sp;
            float vy = 2f + 1.5f * hash(sc * 23 + j * 3 + i * 5);
            float sx = x + vx * since, sy = y + vy * since - 6 * since * since, sz = z + vz * since;
            float vyNow = vy - 12 * since, l = 0.04f;
            BarFxClient.streak(sx - vx * l, sy - vyNow * l, sz - vz * l, sx, sy, sz, 0.07f, SPARK, 0.3f * f, f);
        }
    }

    /** Strategic zoom: the tools shrink to golden glints walking the course. */
    static void drawGlints(Site s, float front, float clock) {
        for (int i = 0; i < 2; i++) {
            ConstructionShape.perimeterPoint(s.course, i / 2f + clock * 0.05f, s.pt);
            BarFxClient.softDisc(s.pt[0], front + 0.6f, s.pt[1], 0.32f, LIGHT, 0.8f, GOLD, 0.2f, 10, 0, 0);
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

    /**
     * A hard-edged camera-facing line between two world points, {@code w} wide, one alpha throughout - a crisp 1-2 px
     * edge where BarFxClient.streak (alpha on its centre line only) would vanish.
     */
    private static void line(float x0, float y0, float z0, float x1, float y1, float z1, float w, int c, float a) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float sx = dy * BarFxClient.lookZ - dz * BarFxClient.lookY, sy = dz * BarFxClient.lookX - dx * BarFxClient.lookZ;
        float sz = dx * BarFxClient.lookY - dy * BarFxClient.lookX;
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-5f) { sx = BarFxClient.leftX; sy = BarFxClient.leftY; sz = BarFxClient.leftZ; l = 1; }
        float h = w * 0.5f / l;
        sx *= h; sy *= h; sz *= h;
        BarFxClient.v(x0 - sx, y0 - sy, z0 - sz, c, a);
        BarFxClient.v(x1 - sx, y1 - sy, z1 - sz, c, a);
        BarFxClient.v(x1 + sx, y1 + sy, z1 + sz, c, a);
        BarFxClient.v(x0 + sx, y0 + sy, z0 + sz, c, a);
    }

    /** A soft disc lying flat (a decal or a pool of light): {@code a} at its centre, nothing at its rim. */
    private static void flatDisc(float x, float y, float z, float r, int c, float a, int seg) {
        float px = x + r, pz = z;
        for (int i = 1; i <= seg; i++) {
            double ang = i * Math.PI * 2 / seg;
            float qx = x + (float) Math.cos(ang) * r, qz = z + (float) Math.sin(ang) * r;
            BarFxClient.v(x, y, z, c, a);
            BarFxClient.v(px, y, pz, c, 0);
            BarFxClient.v(qx, y, qz, c, 0);
            BarFxClient.v(x, y, z, c, a);
            px = qx; pz = qz;
        }
    }

    private static float hash(int i) {
        int h = i * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535f;
    }
}
