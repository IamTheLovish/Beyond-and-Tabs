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
 *   <li>a luminous cloud RING round the layer being built - not a cloud overhead, the RTS camera looks down - that
 *   climbs with the build front and narrows as the building does (ConstructionShape: the ring hugs each layer);</li>
 *   <li>the part still to come is absent but for one bold golden outline of its steps (slice 2 - the outline-only
 *   paintover beat the "built but foggy" one a fresh critic scored 5/10), so how much is left reads at a glance;</li>
 *   <li>the build front's cut line glows where blocks are being set;</li>
 *   <li>shafts of light land on the front, a few at a time;</li>
 *   <li>golden hammers and a chisel walk the cut line and strike it (slice 2; round 5: screen-plane silhouettes);</li>
 *   <li>done: the ring flares into a halo and fades.</li>
 * </ul>
 * Building-agnostic: everything comes from the placement's own blocks (the buildings are being reworked). Pure client
 * rendering through BarFxClient's passes; thinner when zoomed out; nothing allocated per frame once a site's shape is
 * cached (one ConstructionShape per placement, built from its blocks the first time it is seen rising).
 */
final class ConstructionFx {

    private ConstructionFx() { }

    private static final int CLOUD = 0xFFFBF2, BELLY = 0xA9B6CB, GOLD = 0xFFD27A, LIGHT = 0xFFF3D2;
    /** The outline and cut line: a saturated gold (round 2 critic: the warm-white core over gold read cream-white). */
    private static final int LINE_GOLD = 0xFFC21A;
    /** The outline of the part still to come: a warm gold, quieter than the cut line (round 3 critic, eyes 41023134378:
     *  a saturated full-opacity box read as a debug wireframe / CAD tool, not the White Angel). */
    private static final int OUTLINE_GOLD = 0xF0C35A;
    /** The tools, normal-blended: additive gold over the white walls vanished (round 3: "pale smudges"); a gold head
     *  and an ivory handle inside a dark rim, so a tool reads on the white walls and on the lawn alike (round 4). */
    private static final int TOOL_BODY = 0xE0A83A, TOOL_HANDLE = 0xF1E7CF, TOOL_RIM = 0x3A2710;
    /** Seconds the finished halo takes to flare and fade. */
    private static final float DONE_SECONDS = 2.2f;
    /** Past this RTS zoom (strategic) the ring keeps half its puffs and the light shafts stop. */
    private static final float FAR_ZOOM = 70;
    /** An outline rim this close above the cut line is left out: the cut line is the one bright line there. */
    private static final float RIM_CLEAR = 0.75f;
    /** Seconds between recounts of what stands in each layer (a few hundred block-state reads per site). */
    private static final float RECOUNT = 0.5f;

    /** Per rising site: its shape, the placed count drawn (eased toward the server's), when it finished. */
    private static final class Site {
        final ConstructionShape shape;
        final float[] outline;
        final float[] cut = new float[4];
        final float[] pt = new float[4];   // a tool's point on the cut line {x, z, nx, nz}
        final int[] standing;     // blocks standing per layer, recounted every RECOUNT seconds
        float countedAt = -1;
        float shown;
        float doneAt = -1;
        float lastFront, lastRadius;
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

    /** Called from BarFxClient.render before its early return (a site can rise with no other effect alive). */
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

        // the clouds: soft translucent puffs (drawn under the light so the glow sits on top)
        BarFxClient.begin(buffers, BarFxRenderTypes.TRANSLUCENT, mat);
        for (BuildingPlacement p : scratch) {
            Site s = siteOf(p);
            if (p.isBuilt) {
                if (s.doneAt < 0)
                    s.doneAt = clock;
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
            float r = s.shape.ringRadiusAt(front);
            // a radius change at a layer step glides rather than snaps
            s.lastRadius = s.lastRadius <= 0 ? r : s.lastRadius + (r - s.lastRadius) * Math.min(1, dt * 2.5f);
            s.lastFront = front;
            drawRing(s.shape.centreX(), front, s.shape.centreZ(), s.lastRadius, clock, far, 1f);
            // the cut line and the outline get a normal-blended gold core: additive gold alone over the lawn read olive
            // (eyes 41003840710); a warm-WHITE core read cream (round 2, 41014293234), so the core is gold too
            drawCut(s, front, (far ? 0.3f : 0.16f) * 0.5f, LINE_GOLD, 0.8f);
            if (!far)
                drawTools(s, front, clock, true);
        }
        buffers.endBatch(BarFxRenderTypes.TRANSLUCENT);

        // the light: the cut line, shafts landing on the front, the orbiting glints, the finished halo
        BarFxClient.begin(buffers, BarFxRenderTypes.ADDITIVE, mat);
        for (BuildingPlacement p : scratch) {
            Site s = SITES.get(p);
            if (s == null)
                continue;
            float cx = s.shape.centreX(), cz = s.shape.centreZ();
            if (p.isBuilt) {
                float k = (clock - s.doneAt) / DONE_SECONDS;
                if (k >= 0 && k < 1) {
                    float rr = s.lastRadius * (1 + 0.5f * k);
                    float a = (1 - k) * (1 - k);
                    BarFxClient.flatRing(cx, s.lastFront + 0.6f + 1.5f * k, cz, rr, 0.5f + 0.6f * k, GOLD, 0.8f * a, 40);
                    BarFxClient.flatRing(cx, s.lastFront + 0.6f + 1.5f * k, cz, rr * 0.92f, 0.25f, LIGHT, a, 40);
                }
                continue;
            }
            float front = s.lastFront, r = s.lastRadius;
            // the cut line: the layer being built's own footprint at the front (round 2 critic: a circle round the corners
            // read as a glow band round the base, not where blocks are being set)
            drawCut(s, front, far ? 0.3f : 0.16f, LINE_GOLD, 0.9f);
            if (!far)
                drawShafts(s, front, clock);
            // the part still to come: warm gold, ~1.5x the selection box's weight, fading toward the top - corners and
            // top / step rims only, quieter than round 4's 0.5 (the critic: keep the outline QUIET; the cut line below
            // it stays the one bright line)
            drawOutline(s, front, far ? 0.2f : 0.1f, OUTLINE_GOLD, far ? 0.55f : 0.42f);
            if (far)
                drawGlints(cx, front, cz, r, clock, true);
            else
                drawTools(s, front, clock, false);
        }
        buffers.endBatch(BarFxRenderTypes.ADDITIVE);
        BarFxClient.vc = null;
    }

    /** The cloud ring: puffs on a circle at the front, each a lit top over a grey-blue belly, slowly orbiting. */
    static void drawRing(float cx, float front, float cz, float r, float clock, boolean far, float alpha) {
        // round 3 overshot (a few faint puffs, no ring): closer puffs so they join into one continuous thin band
        int n = Math.max(10, Math.min(56, Math.round((float) (2 * Math.PI * r / 0.5f))));
        if (far)
            n = Math.max(6, n / 2);
        // a tighter, brighter band than the first stills (eyes 41003840710: a fresh critic read 1.5-block puffs at 0.55
        // as grey-green haze over the lawn - smoke, not cloud): smaller puffs, a whiter top, a darker belly under it
        // round 2 (41014293234): still fat cotton balls covering the wall corners - ~45% of that size and a third the
        // opacity, so the ring is a thin wisp at the front, not a necklace
        float size = 0.45f * Math.min(1.0f, 0.5f + r * 0.06f);
        for (int k = 0; k < n; k++) {
            float ang = (float) (k * 2 * Math.PI / n + clock * 0.06f);
            float wob = 0.18f * (float) Math.sin(clock * 0.7f + k * 1.7f);
            float x = cx + (float) Math.cos(ang) * (r + wob), z = cz + (float) Math.sin(ang) * (r + wob);
            float y = front + 0.35f + 0.12f * (float) Math.sin(clock * 0.9f + k * 2.3f);
            float s = size * (0.85f + 0.3f * hash(k));
            BarFxClient.softDisc(x, y - s * 0.3f, z, s * 1.05f, BELLY, 0.2f * alpha, BELLY, 0, 12, k, 0.08f);
            BarFxClient.softDisc(x, y + s * 0.12f, z, s, CLOUD, 0.5f * alpha, CLOUD, 0.15f * alpha, 12, k * 1.3f, 0.12f);
        }
    }

    /** Two shafts at a time, each landing on the front for ~0.7 s at its own spot round the walls. */
    /**
     * Two shafts at a time, each landing for ~0.7 s on its own spot INSIDE the layer being built (round 3: a shaft
     * landing past the walls read as a flare cone on the lawn beside the site).
     */
    static void drawShafts(Site s, float front, float clock) {
        s.shape.layerBox(front, s.cut);
        float x0 = s.cut[0], x1 = s.cut[1], z0 = s.cut[2], z1 = s.cut[3];
        for (int i = 0; i < 2; i++) {
            float t = clock / 0.7f + i / 2f;
            int cycle = (int) Math.floor(t);
            float k = t - cycle;
            float x = x0 + 0.5f + (x1 - x0 - 1f) * hash(cycle * 7 + i * 131);
            float z = z0 + 0.5f + (z1 - z0 - 1f) * hash(cycle * 13 + i);
            float a = (float) Math.sin(Math.PI * k);   // fades in and out
            BarFxClient.streak(x, front + 4.5f, z, x, front + 0.05f, z, 0.28f, LIGHT, 0.10f * a, 0.85f * a);
        }
    }

    static void drawCut(Site s, float front, float w, int c, float a) {
        s.shape.layerBox(front, s.cut);
        float x0 = s.cut[0], x1 = s.cut[1], z0 = s.cut[2], z1 = s.cut[3], y = front + 0.04f;
        edge(x0, y, z0, x1, y, z0, w, c, a);
        edge(x1, y, z0, x1, y, z1, w, c, a);
        edge(x1, y, z1, x0, y, z1, w, c, a);
        edge(x0, y, z1, x0, y, z0, w, c, a);
    }

    /**
     * The part still to come: each box's four corner verticals (fading upward) and its TOP rim - the step ledges and
     * the roof line - and nothing else. Round 4 critic (eyes 41031649391): the full wireframe read as a cage, and a box's
     * bottom rim lay on the box below's top rim, so that height was drawn twice and read as a second saturated gold line
     * beside the cut line. Now each height is drawn once, no bottoms, and a rim within RIM_CLEAR of the cut line is
     * left out - the cut line stays the one bright line.
     */
    static void drawOutline(Site s, float front, float w, int c, float a) {
        int n = s.shape.outline(front, s.outline);
        if (n == 0)
            return;
        float[] o = s.outline;
        float top = o[(n - 1) * 6 + 5];
        float span = Math.max(1f, top - front);
        for (int i = 0; i < n; i++) {
            int b = i * 6;
            float x0 = o[b], x1 = o[b + 1], z0 = o[b + 2], z1 = o[b + 3], y0 = o[b + 4], y1 = o[b + 5];
            float a0 = a * (1f - 0.75f * (y0 - front) / span), a1 = a * (1f - 0.75f * (y1 - front) / span);
            if (y1 - front > RIM_CLEAR) {
                edge(x0, y1, z0, x1, y1, z0, w, c, a1);
                edge(x1, y1, z0, x1, y1, z1, w, c, a1);
                edge(x1, y1, z1, x0, y1, z1, w, c, a1);
                edge(x0, y1, z1, x0, y1, z0, w, c, a1);
            }
            edge(x0, y0, z0, x0, y1, z0, w, c, a0, a1);   // the corners, fading upward
            edge(x1, y0, z0, x1, y1, z0, w, c, a0, a1);
            edge(x1, y0, z1, x1, y1, z1, w, c, a0, a1);
            edge(x0, y0, z1, x0, y1, z1, w, c, a0, a1);
        }
    }

    private static void edge(float x0, float y0, float z0, float x1, float y1, float z1, float w, int c, float a) {
        edge(x0, y0, z0, x1, y1, z1, w, c, a, a);
    }

    private static void edge(float x0, float y0, float z0, float x1, float y1, float z1, float w, int c, float a0,
                             float a1) {
        BarFxClient.streak(x0, y0, z0, x1, y1, z1, w, c, a0, a1);
    }

    /** Strategic zoom: the tools shrink to golden glints on the ring. */
    static void drawGlints(float cx, float front, float cz, float r, float clock, boolean far) {
        for (int i = 0; i < 2; i++) {
            float ang = (float) (i * Math.PI + clock * 0.55f);
            float rs = r / ConstructionShape.RING_STANDOFF * 0.92f;
            float x = cx + (float) Math.cos(ang) * rs, z = cz + (float) Math.sin(ang) * rs;
            BarFxClient.softDisc(x, front + 1.0f, z, 0.32f, LIGHT, 1f, GOLD, 0.2f, 10, 0, 0);
        }
    }

    /** Hammer / chisel parts (blocks at the RTS zoom: 30 blocks tall on screen, so ~30 px a block). */
    private static final float HANDLE = 1.15f, HANDLE_W = 0.15f, HEAD_L = 0.72f, HEAD_W = 0.32f, RIM = 0.06f;
    private static final float CHISEL_GRIP = 0.55f, CHISEL_GRIP_W = 0.22f, CHISEL_BLADE = 0.7f;
    /** Struck (head on the cut line, the handle leaning out from the wall) and raised (head back over the grip). */
    private static final float STRUCK = 0.35f, RAISED = 3.5f;
    /** The tools are pulled this far toward the camera so the wall top they strike never hides their heads. */
    private static final float NUDGE = 0.6f;

    /**
     * The tools: two golden hammers and a chisel walking the cut line and striking it. Round 4 (eyes 41031649391) showed
     * only "a glowing sphere with a comet tail" - the root cause was the drawing, not the pass order: each tool was a
     * soft streak (alpha only on its centre line, nothing at the edges) swinging in the vertical plane of the ring's
     * tangent, so from the 65-degree RTS camera a tool on either side of the ring was edge-on (head folded onto the
     * handle) and every tool was a few-pixel brown smear, while the 0.5-block additive blow flash and the trail - the
     * only bright, wide parts - were all that read; and on the ring's circle the blows landed on the lawn.
     * Now each tool is a hard-edged silhouette laid out in the SCREEN plane (camera left / up), so it always shows full
     * length and its head always crosses the handle: a dark rim, then an ivory handle and a gold head (normal-blended,
     * {@code body}); the light pass adds only a small glint on the head and a short, small flash where the blow lands.
     * The tools walk the cut line rectangle (ConstructionShape.perimeterPoint) with the grip leaning out from the wall.
     */
    static void drawTools(Site s, float front, float clock, boolean body) {
        s.shape.layerBox(front, s.cut);
        float[] pt = s.pt;
        for (int i = 0; i < 3; i++) {
            boolean chisel = i == 1;
            ConstructionShape.perimeterPoint(s.cut, i / 3f + clock * 0.02f, pt);
            // outward in screen terms: +1 when the wall's outside is toward screen-left
            float out = pt[2] * BarFxClient.leftX + pt[3] * BarFxClient.leftZ >= 0 ? 1 : -1;
            float period = chisel ? 0.55f : 1.25f;
            float t = clock / period + i * 0.37f, k = t - (float) Math.floor(t);
            float lo = chisel ? 0.2f : STRUCK, hi = chisel ? 0.6f : RAISED;
            float theta;
            if (k < 0.08f)
                theta = lo;                                              // the blow lands: hold a beat
            else if (k < 0.75f) {
                float e = (k - 0.08f) / 0.67f;
                theta = lo + (hi - lo) * (1 - (1 - e) * (1 - e));        // raise, easing out
            } else {
                float e = (k - 0.75f) / 0.25f;
                theta = hi + (lo - hi) * e * e;                          // the blow, accelerating
            }
            float reach = chisel ? CHISEL_GRIP + CHISEL_BLADE : HANDLE;
            // the grip: fixed so that, struck, the working end sits on the cut line point
            float su = (float) -Math.cos(lo), sl = (float) -Math.sin(lo) * out;
            float px = pt[0] - BarFxClient.lookX * NUDGE, py = front + 0.04f - BarFxClient.lookY * NUDGE,
                    pz = pt[1] - BarFxClient.lookZ * NUDGE;
            float gx = px - (su * BarFxClient.upX + sl * BarFxClient.leftX) * reach;
            float gy = py - (su * BarFxClient.upY + sl * BarFxClient.leftY) * reach;
            float gz = pz - (su * BarFxClient.upZ + sl * BarFxClient.leftZ) * reach;
            float du = (float) -Math.cos(theta), dl = (float) -Math.sin(theta) * out;   // grip -> working end
            float flash = k < 0.15f ? 1 - k / 0.15f : 0;
            if (body) {
                for (int pass = 0; pass < 2; pass++) {   // the dark rim first, then the fill over it
                    float e = pass == 0 ? RIM : 0;
                    int c0 = pass == 0 ? TOOL_RIM : TOOL_HANDLE, c1 = pass == 0 ? TOOL_RIM : TOOL_BODY;
                    float a = pass == 0 ? 0.85f : 1f;
                    if (chisel) {
                        bar(gx, gy, gz, du, dl, -e, CHISEL_GRIP + e, CHISEL_GRIP_W + 2 * e, CHISEL_GRIP_W + 2 * e, c0, a);
                        bar(gx, gy, gz, du, dl, CHISEL_GRIP, CHISEL_BLADE + e, 0.15f + 2 * e, 0.05f + 2 * e, c1, a);
                    } else {
                        bar(gx, gy, gz, du, dl, -e, HANDLE + e, HANDLE_W + 2 * e, HANDLE_W + 2 * e, c0, a);
                        // the head crosses the handle's end: along the perpendicular (-dl, du), centred on it
                        float hx = gx + (du * BarFxClient.upX + dl * BarFxClient.leftX) * HANDLE;
                        float hy = gy + (du * BarFxClient.upY + dl * BarFxClient.leftY) * HANDLE;
                        float hz = gz + (du * BarFxClient.upZ + dl * BarFxClient.leftZ) * HANDLE;
                        bar(hx, hy, hz, -dl, du, -HEAD_L / 2 - e, HEAD_L + 2 * e, HEAD_W + 2 * e, HEAD_W + 2 * e, c1, a);
                    }
                }
                continue;
            }
            // the light: a glint on the head (or the chisel's edge) and, as a blow lands, a small short flash
            float tip = chisel ? reach : HANDLE;
            float ex = gx + (du * BarFxClient.upX + dl * BarFxClient.leftX) * tip;
            float ey = gy + (du * BarFxClient.upY + dl * BarFxClient.leftY) * tip;
            float ez = gz + (du * BarFxClient.upZ + dl * BarFxClient.leftZ) * tip;
            float tw = 0.75f + 0.25f * (float) Math.sin(clock * 5 + i * 2.1f);
            BarFxClient.softDisc(ex, ey, ez, 0.16f, LIGHT, 0.55f * tw, GOLD, 0, 8, 0, 0);
            if (flash > 0)
                BarFxClient.softDisc(px, py, pz, (chisel ? 0.22f : 0.32f) * (0.6f + 0.4f * flash), LIGHT, 0.85f * flash,
                        GOLD, 0.2f * flash, 10, 0, 0);
        }
    }

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
