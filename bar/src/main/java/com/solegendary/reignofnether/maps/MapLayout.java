package com.solegendary.reignofnether.maps;

import com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The deterministic spec of one battlefield for {@code n} start slots (design/maps.md). Pure maths, no world access,
 * so the lobby preview, the builder and the fairness game test all read the same thing.
 *
 * Fairness by construction: slot i sits at angle {@code pi + 2*pi*i/n} on a ring of radius {@link #ring}. Every
 * terrain query is folded into the frame of its nearest slot - {@code u} along that slot's axis (centre outwards),
 * {@code v = |lateral offset|} - and the terrain is a function of (u, v) only, so the map is n-fold rotationally
 * symmetric with a mirror line through every slot. Mexes, capture sites, vents and pillars are listed once in slot
 * frame and rotated to every slot. Coordinates in the records are relative to the map centre.
 */
public final class MapLayout {

    public enum Surface { LAND, SAND, GRASS, CLIFF, MESA, WATER, LAVA, VOID, BRIDGE }

    /** One terrain sample, reused by the builder (no allocation per column). Heights are relative to the base. */
    public static final class Sample {
        public int height;
        public Surface surface = Surface.LAND;
        /** Fluid surface for WATER / LAVA columns (relative), filled from height+1 up to it. */
        public int fluidTop;
        /** May carry a tree (reclaim lane), never near a start. */
        public boolean forest;
        /** Folded slot-frame coordinates of the sample, for symmetric decoration hashes. */
        public double u, v;
    }

    public record Slot(int index, int team, int dx, int dz, double angle) { }

    /** A team's start box: an annular sector [a0, a1] x [r0, r1] about the centre (angles in radians). */
    public record Box(int team, double a0, double a1, double r0, double r1) {
        public boolean contains(double dx, double dz) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r < r0 || r > r1)
                return false;
            double d = Math.atan2(dz, dx) - a0;
            d = ((d % TAU) + TAU) % TAU;
            return d <= a1 - a0;
        }
    }

    public record Mex(int dx, int dz, float value, int slot) { }
    public record Site(int dx, int dz, Kind kind) { }
    public record Spot(int dx, int dz, int size) { }

    static final double TAU = Math.PI * 2;
    /** Slot 0 sits due west (-x); the rest follow counter-clockwise on the map. */
    static final double ROT = Math.PI;
    /** Distance from a start to its home ring of patches (as StartAreaClearing.HOME_RING_RADIUS). */
    static final int HOME_RING = 18;
    /** Neighbouring starts are at least this far apart (a capitol, its ring and room to build between). */
    static final int MIN_NEIGHBOUR = 116;
    /** Values of the three tiers of patch (design/maps.md section 2). */
    public static final float STANDARD = 1.0f, RICH = 1.5f, CENTRE = 2.0f;

    public final MapPreset preset;
    public final int n;
    public final int ring;     // S: distance of every start from the centre
    public final int radius;   // R: the arena wall
    public final double half;  // half the angle between neighbours
    public final List<Slot> slots;
    public final List<Box> boxes;
    public final List<Mex> mexes = new ArrayList<>();
    public final List<Site> sites = new ArrayList<>();
    /** 3x3 lava pools (geothermal; size is the pool's half-width). */
    public final List<Spot> vents = new ArrayList<>();
    /** Decorative columns (basalt / obsidian), [size] = height. Never near a start. */
    public final List<Spot> pillars = new ArrayList<>();

    private MapLayout(MapPreset preset, int[] slotTeams, int ring, int radius, List<Slot> slotsOverride) {
        this.preset = preset;
        this.n = Math.max(2, slotTeams.length);
        this.ring = ring;
        this.radius = radius;
        this.half = Math.PI / n;
        List<Slot> s = new ArrayList<>();
        if (slotsOverride != null) {
            s.addAll(slotsOverride);
        } else {
            for (int i = 0; i < n; i++) {
                double a = slotAngle(i);
                s.add(new Slot(i, i < slotTeams.length ? slotTeams[i] : 0,
                    (int) Math.round(Math.cos(a) * ring), (int) Math.round(Math.sin(a) * ring), a));
            }
        }
        this.slots = Collections.unmodifiableList(s);
        this.boxes = Collections.unmodifiableList(buildBoxes(slotTeams));
    }

    public double slotAngle(int i) {
        return ROT + TAU * i / n;
    }

    /** Start ring radius for {@code n} slots on this preset: the 1v1 ring, widened so neighbours stay apart. */
    public static int ringFor(MapPreset preset, int n) {
        int base = preset.premade() ? preset.baseRing : 150;
        if (n < 2)
            return base;
        return (int) Math.max(base, Math.ceil(MIN_NEIGHBOUR / 2.0 / Math.sin(Math.PI / n)));
    }

    /** A pre-made map for these slots (teams per slot, teammates adjacent). */
    public static MapLayout build(MapPreset preset, int[] slotTeams) {
        int n = Math.max(2, slotTeams.length);
        int s = ringFor(preset, n);
        MapLayout l = new MapLayout(preset, slotTeams, s, s + preset.margin, null);
        l.populate();
        return l;
    }

    /**
     * Random terrain with the start-position phase: no terrain or economy of its own (BattlefieldSetup stamps the
     * procedural ring), only the given slot positions (relative to the centre) and the team boxes round them.
     */
    public static MapLayout ring(int[] slotTeams, List<int[]> slotOffsets, int ringRadius, int arenaRadius) {
        List<Slot> s = new ArrayList<>();
        int n = Math.max(2, slotTeams.length);
        for (int i = 0; i < slotOffsets.size(); i++) {
            int[] o = slotOffsets.get(i);
            s.add(new Slot(i, slotTeams[i], o[0], o[1], ROT + TAU * i / n));
        }
        return new MapLayout(MapPreset.RANDOM, slotTeams, ringRadius, Math.max(arenaRadius, ringRadius + 60), s);
    }

    // ------------------------------------------------------------------------------------------ boxes

    private List<Box> buildBoxes(int[] slotTeams) {
        List<Box> out = new ArrayList<>();
        // a gap between enemy boxes, so nobody can start on the enemy's doorstep
        double margin = Math.min(half * 0.35, 14.0 / Math.max(1, ring));
        double r0 = Math.max(20, ring - 40), r1 = Math.min(radius - 15, ring + 40);
        int i = 0;
        while (i < slotTeams.length) {
            int team = slotTeams[i];
            int j = i;
            while (j + 1 < slotTeams.length && slotTeams[j + 1] == team)
                j++;
            out.add(new Box(team, slotAngle(i) - half + margin, slotAngle(j) + half - margin, r0, r1));
            i = j + 1;
        }
        return out;
    }

    public Box boxOf(int team) {
        for (Box b : boxes)
            if (b.team() == team)
                return b;
        return null;
    }

    // ------------------------------------------------------------------------------------------ templates

    /** Adds a slot-frame point to every slot (rotated), if it lies inside its wedge with room for a patch. */
    private void mex(double u, double v, float value) {
        double r = Math.hypot(u, v), phi = Math.abs(Math.atan2(v, u));
        if (r > 1 && (phi > half || r * Math.sin(half - phi) < 3.5))
            return;   // would fall in (or crowd) the neighbour's wedge: dropped for every slot alike
        for (Slot s : slots) {
            double[] w = toWorld(s, u, v);
            mexes.add(new Mex((int) Math.round(w[0]), (int) Math.round(w[1]), value, s.index()));
        }
    }

    /** A mirror pair (u, +v) and (u, -v), the usual way a template places something. */
    private void mexPair(double u, double v, float value) {
        mex(u, v, value);
        if (Math.abs(v) > 0.5)
            mex(u, -v, value);
    }

    private void homeRing(int count) {
        for (int k = 0; k < count; k++) {
            double b = (k - (count - 1) / 2.0) * (Math.PI / 2.2);
            mex(ring + Math.cos(b) * HOME_RING, Math.sin(b) * HOME_RING, STANDARD);
        }
    }

    /** One patch per slot near the centre at the closest radius where neighbours' patches don't collide. */
    private void centreRing() {
        mex(centreMexRadius(), 0, CENTRE);
    }

    double centreMexRadius() {
        return Math.max(10, 4.5 / Math.sin(half));
    }

    /** A site on every boundary between neighbours, {@code r} from the centre (n copies, one per boundary). */
    private void boundarySite(double r, Kind kind) {
        for (Slot s : slots) {
            double a = s.angle() + half;
            sites.add(new Site((int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r), kind));
        }
    }

    private void boundaryVent(double r) {
        for (Slot s : slots) {
            double a = s.angle() + half;
            vents.add(new Spot((int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r), 1));
        }
    }

    private void ventPair(double u, double v) {
        for (Slot s : slots)
            for (int sign = -1; sign <= 1; sign += 2) {
                double[] w = toWorld(s, u, v * sign);
                vents.add(new Spot((int) Math.round(w[0]), (int) Math.round(w[1]), 1));
            }
    }

    private void pillarPair(double u, double v, int height) {
        for (Slot s : slots)
            for (int sign = -1; sign <= 1; sign += 2) {
                double[] w = toWorld(s, u, v * sign);
                pillars.add(new Spot((int) Math.round(w[0]), (int) Math.round(w[1]), height));
            }
    }

    static double[] toWorld(Slot s, double u, double v) {
        double c = Math.cos(s.angle()), si = Math.sin(s.angle());
        return new double[] { u * c - v * si, u * si + v * c };
    }

    private double[] polar(double r, double phi) {
        return new double[] { r * Math.cos(phi), r * Math.sin(phi) };
    }

    // per-preset key distances, shared by the templates and the terrain
    double centreIsle() { return 0.17 * radius; }
    double isletR() { return ring * 0.9; }
    double isletSize() { return Math.min(24, isletR() * Math.sin(half) * 0.75); }
    double fordBank() { return (centreIsle() + ring - 55) / 2; }
    double hillU() { return (0.2 * radius + ring - 74) / 2; }
    double riftR() { return 0.3 * radius; }
    double spireU() { return (0.2 * radius + ring - 58) / 2; }
    double spireSize() { return Math.max(9, Math.min(20, (ring - 58 - 0.2 * radius) / 2 - 4)); }
    double riverR() { return 0.4 * ring; }
    double[] mesa() { return polar(0.75 * ring, 0.55 * half); }
    double mesaSize() {
        double[] m = mesa();
        double r = Math.hypot(m[0], m[1]);
        double db = r * Math.sin(half - 0.55 * half);
        return Math.max(7, Math.min(16, Math.min(m[1], db) * 0.6));
    }

    private void populate() {
        switch (preset) {
            case ARCHIPELAGO -> {
                homeRing(5);
                mex(fordBank(), 0, STANDARD);
                double[] p = polar(isletR(), half - 7.0 / isletR());
                mexPair(p[0], p[1], RICH);
                centreRing();
                boundarySite(isletR(), Kind.BELL);
                sites.add(new Site(0, 0, Kind.WATCHTOWER));
            }
            case HIGHLANDS -> {
                homeRing(6);
                mexPair(hillU(), 6, RICH);
                double[] f = polar(0.7 * ring, 0.5 * half);
                mexPair(f[0], f[1], STANDARD);
                centreRing();
                boundarySite(ring, Kind.WATCHTOWER);
                sites.add(new Site(0, 0, Kind.FORGE));
            }
            case NETHER_RIFT -> {
                homeRing(5);
                mexPair(riftR() + 12, 7, RICH);
                centreRing();
                boundarySite(0.6 * radius, Kind.VENT);
                boundaryVent(0.6 * radius + 8);
                sites.add(new Site(0, 0, Kind.MINE));
                ventPair(ring + 6, 34);
                double[] a = polar(riftR() + 32, 0.5 * half);
                pillarPair(a[0], a[1], 9);
                double[] b = polar(ring + 62, 0.7 * half);
                pillarPair(b[0], b[1], 12);
            }
            case END_SPIRES -> {
                homeRing(5);
                mexPair(spireU(), 6, RICH);
                centreRing();
                boundarySite(0.95 * ring, Kind.WATCHTOWER);
                sites.add(new Site(0, 0, Kind.SHRINE));
                pillarPair(spireU(), Math.max(10, spireSize() - 3), 14);
            }
            case VERDANT_VALLEY -> {
                homeRing(6);
                mexPair(riverR() - 12, 8, RICH);
                centreRing();
                boundarySite(0.62 * radius, Kind.CAMP);
                sites.add(new Site(0, 0, Kind.SHRINE));
            }
            case DESERT_MESA -> {
                homeRing(5);
                double[] m = mesa();
                mexPair(m[0], m[1], RICH);
                centreRing();
                boundarySite(0.55 * radius, Kind.OASIS);
                sites.add(new Site(0, 0, Kind.MINE));
            }
            default -> { }
        }
    }

    /** Total patch value a slot owns (the patches listed for it). */
    public float mexValueOfSlot(int slot) {
        float v = 0;
        for (Mex m : mexes)
            if (m.slot() == slot)
                v += m.value();
        return v;
    }

    /** Index of the slot whose wedge holds (dx, dz). */
    public int slotAt(double dx, double dz) {
        double step = TAU / n;
        double th = Math.atan2(dz, dx) - ROT;
        long k = Math.round(th / step);
        return (int) (((k % n) + n) % n);
    }

    // ------------------------------------------------------------------------------------------ terrain

    /**
     * The terrain at (dx, dz) from the centre: height (relative to the centre's ground), surface, fluid, forest.
     * Every preset is a function of the folded (u, v) only - that is the symmetry.
     */
    public void sample(double dx, double dz, Sample out) {
        double r = Math.sqrt(dx * dx + dz * dz);
        double step = TAU / n;
        double th = Math.atan2(dz, dx) - ROT;
        double phi = Math.abs(th - Math.rint(th / step) * step);
        double u = r * Math.cos(phi), v = r * Math.sin(phi);
        double db = r * Math.sin(Math.max(0, half - phi));   // to the boundary with the neighbour
        double hd = Math.hypot(u - ring, v);                  // to this wedge's start
        out.u = u;
        out.v = v;
        out.forest = false;
        out.fluidTop = 0;
        switch (preset) {
            case ARCHIPELAGO -> archipelago(r, u, v, db, hd, out);
            case HIGHLANDS -> highlands(r, u, v, db, hd, out);
            case NETHER_RIFT -> netherRift(r, u, v, db, hd, out);
            case END_SPIRES -> endSpires(r, u, v, db, hd, out);
            case VERDANT_VALLEY -> verdantValley(r, u, v, db, hd, out);
            case DESERT_MESA -> desertMesa(r, u, v, db, hd, out);
            default -> {
                out.height = 0;
                out.surface = Surface.LAND;
            }
        }
    }

    private void set(Sample s, int h, Surface surface) {
        s.height = h;
        s.surface = surface;
    }

    private void archipelago(double r, double u, double v, double db, double hd, Sample s) {
        set(s, -6, Surface.WATER);
        double ci = centreIsle();
        // shallow fords (one block of water over sand: walkable, slow) - home to centre and home to the islets
        double ip = isletR();
        double iu = ip * Math.cos(half), iv = ip * Math.sin(half);
        if (seg(u, v, ci - 2, 0, ring - 50, 0) < 4.5 || seg(u, v, ring, 0, iu, iv) < 4.5)
            set(s, -1, Surface.WATER);
        if (Math.hypot(u - fordBank(), v) < 6.5)
            set(s, 1, Surface.SAND);
        double di = Math.hypot(u - iu, v - iv), is = isletSize();
        if (di < is)
            set(s, di > is - 4 ? 1 : 2, di > is - 4 ? Surface.SAND : Surface.GRASS);
        if (r < ci)
            set(s, r > ci - 5 ? 1 : 2, r > ci - 5 ? Surface.SAND : Surface.GRASS);
        if (hd < 60)
            set(s, hd > 54 ? 1 : 2, hd > 54 ? Surface.SAND : Surface.GRASS);
        if (r > radius - 9)
            set(s, 1, Surface.SAND);   // a beach under the wall keeps the sea from spilling out of the arena
    }

    private void highlands(double r, double u, double v, double db, double hd, Sample s) {
        double h = noise(u, v, 0.045) * 1.5;
        double basin = 0.18 * radius;
        if (r < basin + 8)
            h = lerp(-2, h, smooth(basin, basin + 8, r));
        // forward hill on the lane, ramps front and back
        double hu = hillU();
        double dh = Math.hypot(u - hu, v);
        if (dh < 14)
            h = 6;
        else if (v < 5 && Math.abs(u - hu) < 28)
            h = Math.max(h, 6 * (1 - (Math.abs(u - hu) - 14) / 14));
        // the plateau, a front ramp to the lowland and flank ramps to the passes
        if (hd < 50)
            h = 9;
        if (v < 6 && u > ring - 74 && u < ring - 48)
            h = Math.max(h, 9 * (u - (ring - 74)) / 26);
        if (Math.abs(u - ring) < 6 && v > 48 && v < 74)
            h = Math.max(h, 9 * (1 - (v - 48) / 26));
        Surface surf = Surface.GRASS;
        // ridges between neighbours, broken by one pass at base distance (the choke)
        if (db < 6 && r > 0.3 * radius && Math.abs(r - ring) > 14 && r < radius - 8 && hd > 52) {
            h = 14;
            surf = Surface.CLIFF;
        }
        set(s, (int) Math.round(h), surf);
        s.forest = h <= 1.5 && hd > 75 && r > 0.6 * radius && db > 10 && r < radius - 12 && noise(u, v, 0.08) > 0.15;
    }

    private void netherRift(double r, double u, double v, double db, double hd, Sample s) {
        double h = noise(u, v, 0.05) * 2;
        double rr = riftR();
        if (r < rr - 6)
            h = 0;
        if (hd < 52)
            h = 1;
        if (hd >= 52 && hd < 62)
            h = lerp(1, h, smooth(52, 62, hd));
        set(s, (int) Math.round(h), Surface.LAND);
        if (Math.abs(r - rr) < 6) {
            if (v < 3.5)
                set(s, 0, Surface.BRIDGE);   // the only crossings: chokes on every lane
            else {
                set(s, -3, Surface.LAVA);
                s.fluidTop = -1;
            }
        }
        if (r > radius - 8)
            set(s, 0, Surface.LAND);
    }

    private void endSpires(double r, double u, double v, double db, double hd, Sample s) {
        set(s, -16, Surface.VOID);
        double ci = 0.2 * radius, wob = noise(u, v, 0.09) * 4;
        double bu = 0.95 * ring * Math.cos(half), bv = 0.95 * ring * Math.sin(half);
        double bs = Math.min(20, 0.95 * ring * Math.sin(half) * 0.7);
        boolean land = hd < 58 + wob || r < ci + wob || Math.hypot(u - spireU(), v) < spireSize() + wob
            || Math.hypot(u - bu, v - bv) < bs + wob || r > radius - 8;
        if (land) {
            set(s, 0, Surface.LAND);
            return;
        }
        // purpur bridges: centre - spire island - home, and home - boundary island (to the neighbour)
        if ((v < 2.5 && u > ci - 4 && u < ring - 50) || seg(u, v, ring, 0, bu, bv) < 2.5)
            set(s, 0, Surface.BRIDGE);
    }

    private void verdantValley(double r, double u, double v, double db, double hd, Sample s) {
        double h = noise(u, v, 0.03) * 3;
        if (r < 0.15 * radius)
            h = 0;
        if (hd < 55)
            h = 1;
        else if (hd < 65)
            h = lerp(1, h, smooth(55, 65, hd));
        Surface surf = Surface.GRASS;
        double rv = riverR(), dr = Math.abs(r - rv);
        int fluid = 0;
        if (dr < 5) {
            boolean ford = v < 5 || db < 5;
            h = ford ? -1 : -3;
            surf = Surface.WATER;
        } else if (dr < 7) {
            h = 0;
            surf = Surface.SAND;
        }
        set(s, (int) Math.round(h), surf);
        s.fluidTop = fluid;
        // forest bands along the boundaries: reclaim lanes, with a clearing round each camp
        double camp = 0.62 * radius;
        double cu = camp * Math.cos(half), cv = camp * Math.sin(half);
        s.forest = surf == Surface.GRASS && db < 16 && hd > 70 && r > rv + 10 && r < radius - 12
            && Math.hypot(u - cu, v - cv) > 11;
    }

    private void desertMesa(double r, double u, double v, double db, double hd, Sample s) {
        double h = Math.sin(u * 0.07) * 1.2 + noise(u, v, 0.05) * 1.5;
        if (r < 0.15 * radius)
            h = 0;
        if (hd < 55)
            h = 1;
        else if (hd < 63)
            h = lerp(1, h, smooth(55, 63, hd));
        Surface surf = Surface.LAND;
        double[] m = mesa();
        double ms = mesaSize();
        double dm = Math.hypot(u - m[0], v - m[1]);
        if (dm < ms) {
            h = 16;
            surf = Surface.MESA;
        } else if (Math.abs(v - m[1]) < 3.5 && u < m[0] - ms + 2 && u > m[0] - ms - 30) {
            h = Math.max(h, 16 * (1 - (m[0] - ms - u) / 30));   // one radial ramp up each mesa
        }
        // an oasis pond on the outer side of each boundary oasis, grass round it
        double oa = 0.55 * radius;
        double ou = oa * Math.cos(half), ov = oa * Math.sin(half);
        double dOasis = Math.hypot(u - ou, v - ov);
        double pu = (oa + 8) * Math.cos(half), pv = (oa + 8) * Math.sin(half);
        if (Math.hypot(u - pu, v - pv) < 4.5) {
            h = -2;
            surf = Surface.WATER;
        } else if (dOasis < 13 && surf == Surface.LAND) {
            surf = Surface.GRASS;
            h = Math.min(h, 1);
            s.forest = dOasis > 9;
        }
        set(s, (int) Math.round(h), surf);
    }

    // ------------------------------------------------------------------------------------------ maths

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Distance from (u, v) to the segment (ax, ay)-(bx, by). */
    static double seg(double u, double v, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((u - ax) * dx + (v - ay) * dy) / len2));
        return Math.hypot(u - (ax + dx * t), v - (ay + dy * t));
    }

    /** Smooth value noise in [-1, 1] on folded coordinates (so it is symmetric too); seeded per preset. */
    double noise(double u, double v, double freq) {
        double x = u * freq, y = v * freq;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(x0, y0), b = hash(x0 + 1, y0), c = hash(x0, y0 + 1), d = hash(x0 + 1, y0 + 1);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fy);
    }

    double hash(int x, int y) {
        long h = x * 73856093L ^ y * 19349663L ^ (preset.ordinal() + 1) * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return ((h & 0xFFFF) / 32767.5) - 1;
    }

    /** A deterministic [0, 1) value for decoration at folded integer coordinates (symmetric scatter). */
    public double decorHash(double u, double v) {
        return (hash((int) Math.round(u), (int) Math.round(v) + 7919) + 1) / 2;
    }
}
