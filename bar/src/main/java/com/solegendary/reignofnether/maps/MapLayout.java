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

    // the realm maps' key positions in slot frame, worked out once in populate(): sample() runs once per column of
    // a build (hundreds of thousands of times), so it must not redo the trig of the templates above
    private double dPlaza, dValley, dLake, dLakeU, dLakeV, dGarU, dGarV;
    private double[] dRich, dFlank, dPillarG, dPillarL;
    private double cHome, cCentre, cLaneU, cLane, cGarU, cGarV, cGar, cChorusU, cChorusV;

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

    // Soulfire Deltas (design/maps.md 4.7). Every feature sits either on the slot axis (the causeway) or on the
    // boundary between neighbours (valley, lake, garrison), so both neighbours stand the same distance from it.
    double deltaPlaza() { return Math.max(centreMexRadius() + 8, 0.12 * radius); }
    /** Half-width of the soul-sand valley floor on each boundary (its banks add 4 more). */
    double valleyHalf() { return Math.max(5, Math.min(10, 0.08 * ring * Math.sin(half))); }
    double lakeR() { return 0.5 * ring; }
    /** Mean lava-lake radius; the shore wobbles by up to LAKE_WOBBLE. */
    double lakeSize() { return Math.max(7, Math.min(20, 0.3 * lakeR() * Math.sin(half))); }
    static final double LAKE_WOBBLE = 2;
    double garrisonR() { return 0.78 * ring; }
    static final double GARRISON_ISLET = 7, GARRISON_MOAT = 6;
    static final double CAUSEWAY_HALF = 4.5;
    /** The rich pair: just past each lake, toward the slot, never nearer the boundary than half the wedge. */
    double[] deltaRich() {
        double r = lakeR() + lakeSize() + 8;
        return polar(r, Math.max(half / 2, half - (lakeSize() + 16) / r));
    }
    /** The backfield pair, out among the warped groves behind the base. */
    double[] deltaFlank() { return new double[] { ring + 50, 40 }; }

    // Chorus Isles (design/maps.md 4.8)
    /** Home island radius: as big as the wedge allows while a void channel still separates neighbours. */
    double isleHome() { return Math.max(40, Math.min(56, ring * Math.sin(half) - 12)); }
    double isleCentre() { return Math.max(centreMexRadius() + 12, 0.16 * radius); }
    double laneIsleU() { return (isleCentre() + ring - isleHome()) / 2; }
    double laneIsleSize() { return Math.max(12, Math.min(18, (ring - isleHome() - isleCentre()) / 2 - 8)); }
    static final double NECK_HALF = 7.5, BRIDGE_HALF = 4.5, ISLE_WOBBLE = 3;
    /** The boundary islet (garrison): on the boundary, at least as far out as the foot of the start's perpendicular. */
    double isleGarrisonR() { return Math.max(0.62 * ring, ring * Math.cos(half)); }
    double isleGarrisonSize() {
        double gu = isleGarrisonR() * Math.cos(half), gv = isleGarrisonR() * Math.sin(half);
        return Math.max(7, Math.min(16, Math.hypot(ring - gu, gv) - isleHome() - ISLE_WOBBLE - 6));
    }
    /** The scenery islet of chorus on every boundary, out by the wall: nothing walks there, it is the End's look. */
    double chorusIsleR() { return radius - 26; }

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
            case SOULFIRE_DELTAS -> {
                dPlaza = deltaPlaza();
                dValley = valleyHalf();
                dLake = lakeSize();
                dLakeU = lakeR() * Math.cos(half);
                dLakeV = lakeR() * Math.sin(half);
                dGarU = garrisonR() * Math.cos(half);
                dGarV = garrisonR() * Math.sin(half);
                homeRing(5);
                double[] rich = deltaRich();
                mexPair(rich[0], rich[1], RICH);
                double[] flank = deltaFlank();
                mexPair(flank[0], flank[1], STANDARD);
                dRich = rich;
                dFlank = flank;
                centreRing();
                // a nether-brick garrison in every valley, far from both bases; the bastion forge in the plaza
                boundarySite(garrisonR(), Kind.GARRISON);
                sites.add(new Site(0, 0, Kind.FORGE));
                // geothermal: two private pools behind every base (as on Nether Rift) and two contested ones on every
                // lake's shore, on the boundary line itself so neither neighbour is closer
                ventPair(ring + 6, 34);
                boundaryVent(lakeR() - lakeSize() - LAKE_WOBBLE - 5);
                boundaryVent(lakeR() + lakeSize() + LAKE_WOBBLE + 5);
                // basalt columns on the valley banks: landmarks for the garrison and the lake from the RTS camera
                double gr = garrisonR() + 15, lr = lakeR() - lakeSize() - 14;
                double[] gp = polar(gr, half - (valleyHalf() + 7) / gr);
                pillarPair(gp[0], gp[1], 11);
                double[] lp = polar(lr, half - (valleyHalf() + 7) / lr);
                pillarPair(lp[0], lp[1], 8);
                dPillarG = gp;
                dPillarL = lp;
            }
            case CHORUS_ISLES -> {
                cHome = isleHome();
                cCentre = isleCentre();
                cLaneU = laneIsleU();
                cLane = laneIsleSize();
                cGarU = isleGarrisonR() * Math.cos(half);
                cGarV = isleGarrisonR() * Math.sin(half);
                cGar = isleGarrisonSize();
                cChorusU = chorusIsleR() * Math.cos(half);
                cChorusV = chorusIsleR() * Math.sin(half);
                homeRing(5);
                // the lane island's rich pair sits beside the neck, so the neck's middle stays open
                mexPair(laneIsleU(), Math.max(4, laneIsleSize() - 8), RICH);
                // one standard patch per neighbour on every garrison islet big enough to hold one beside the ruin
                // (up to 4 players; with more the islet is a stepping stone between two close home islands)
                double gr = isleGarrisonR(), gs = isleGarrisonSize();
                if (gs >= 15) {
                    double[] gp = polar(gr, half - Math.asin(7 / gr));
                    mexPair(gp[0], gp[1], STANDARD);
                }
                centreRing();
                boundarySite(gr, Kind.GARRISON);
                sites.add(new Site(0, 0, Kind.WATCHTOWER));
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
            case SOULFIRE_DELTAS -> soulfireDeltas(r, u, v, db, hd, out);
            case CHORUS_ISLES -> chorusIsles(r, u, v, db, hd, out);
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

    /**
     * Soulfire Deltas: low netherrack wastes drifted with crimson nylium (warped nylium out in the backfield), every
     * base on a flat nylium terrace with a blackstone causeway to the paved plaza. Along every boundary runs a valley
     * of soul sand - the short way to a neighbour, at vanilla's 40% speed - with a lava lake sunk three below its
     * banks (so only the vent pools take a geothermal plant) and, further out, the garrison's blackstone islet in a
     * soul-sand moat. Every step between walkable columns is at most one block (the pathing test holds it).
     */
    private void soulfireDeltas(double r, double u, double v, double db, double hd, Sample s) {
        double h = 1 + noise(u, v, 0.05) * 1.4;
        Surface surf = noise(u + 31, v, 0.07) > 0.2 ? Surface.GRASS : Surface.LAND;
        if (r > ring * 1.08 && noise(u, v + 17, 0.06) > -0.3)
            surf = Surface.MESA;   // warped nylium (the palette's alternate top), where the warped groves grow
        if (hd < 54) {
            h = 2;
            surf = Surface.GRASS;
        } else if (hd < 62) {
            h = lerp(2, h, smooth(54, 62, hd));
        }
        boolean paved = false;
        if (r < dPlaza || (v < CAUSEWAY_HALF && u < ring && hd >= 50)) {
            h = 1;
            surf = Surface.BRIDGE;
            paved = true;
        }
        // the valley (and the garrison's moat): fades in past the plaza and out before the home terraces, so it
        // never cuts a base off and always meets the paving at the same height
        double depth = smooth(dPlaza, dPlaza + 10, r) * smooth(60, 70, hd);
        double dg = Math.hypot(u - dGarU, v - dGarV);
        double dv = Math.min(db - dValley, dg - GARRISON_ISLET - GARRISON_MOAT);
        if (!paved && depth > 0 && dv < 4) {
            double floor = lerp(h, -1, depth);
            if (dv < 0) {
                h = floor;
                surf = Surface.SAND;   // soul sand in this palette
            } else {
                h = lerp(floor, h, smooth(0, 4, dv));
            }
        }
        if (dg < GARRISON_ISLET) {
            h = 0;
            surf = Surface.BRIDGE;
        }
        double dl = Math.hypot(u - dLakeU, v - dLakeV);
        if (dl < dLake + noise(u, v, 0.11) * LAKE_WOBBLE) {
            set(s, -6, Surface.LAVA);
            s.fluidTop = -3;
            return;
        }
        set(s, (int) Math.round(h), surf);
        // crimson fungus groves on the valley banks, warped ones in the backfield: reclaim lanes for energy, never
        // near a base, the causeway, a patch, a vent, a pillar or a site (MapBuilder grows short fungi there)
        boolean bank = db > dValley + 5 && db < dValley + 16 && r > dPlaza + 20 && r < ring;
        boolean back = r > ring * 1.08 && db > dValley + 5 && noise(u, v + 51, 0.045) > 0.1;   // stands, not a carpet
        s.forest = (bank || back) && !paved && hd > 72 && r < radius - 12 && dl > dLake + 10 && dg > 20
            && v > CAUSEWAY_HALF + 8 && farFrom(u, v, dRich, 9) && farFrom(u, v, dFlank, 9)
            && farFrom(u, v, dPillarG, 5) && farFrom(u, v, dPillarL, 5);
    }

    /** Whether a grove at (u, v) on Soulfire Deltas is warped (backfield) rather than crimson (valley banks). */
    public boolean groveWarped(double u, double v) {
        return Math.hypot(u, v) > ring;
    }

    /**
     * Chorus Isles: end-stone islands over a void 14 deep. Each base has its island; a land neck (15 wide) runs to a
     * lane island with the rich pair and on to the centre island, whose raised dais carries the watchtower spire.
     * Railed purpur bridges (9 wide, about 6 walkable between the rails) join every home island to the garrison
     * islets on both its boundaries. Small chorus islets out by the wall are scenery only. MapBuilder rails every
     * walkable column that touches the void, so ground units can reach everything they need and fall off nothing,
     * while air units cross the void directly - the shortcut that gives them their value on this map.
     */
    private void chorusIsles(double r, double u, double v, double db, double hd, Sample s) {
        set(s, -14, Surface.VOID);
        double wob = noise(u, v, 0.08) * ISLE_WOBBLE;
        double dc = Math.hypot(u - cChorusU, v - cChorusV);
        boolean chorusIslet = dc < 9 + wob;
        boolean land = hd < cHome + wob || r < cCentre + wob || Math.hypot(u - cLaneU, v) < cLane + wob
            || (v < NECK_HALF && u > cCentre - 4 && u < ring - cHome + 4)
            || Math.hypot(u - cGarU, v - cGarV) < cGar + wob || chorusIslet || r > radius - 8;
        if (land) {
            set(s, r < cCentre * 0.55 ? 1 : 0, Surface.LAND);
            s.forest = chorusIslet;
            return;
        }
        if (seg(u, v, ring, 0, cGarU, cGarV) < BRIDGE_HALF)
            set(s, 0, Surface.BRIDGE);
    }

    private static boolean farFrom(double u, double v, double[] p, double d) {
        return p == null || Math.hypot(u - p[0], v - p[1]) > d;
    }

    /**
     * Whether a column with surface {@code self} carries a guard rail: on a railed map (MapPreset.railedVoid), every
     * non-void column with void among its 8 neighbours. The 8-neighbourhood makes the rail line 4-connected round any
     * shoreline, so with the wall blocks joined along it there is no diagonal gap a unit could be pushed through.
     * Shared by MapBuilder (which reads its neighbours from the chunk's sampled border) and the pathing game test.
     */
    public boolean railAt(Surface self, boolean voidNeighbour) {
        return voidNeighbour && self != Surface.VOID && preset.railedVoid();
    }

    /** Half the side of the square startGround checks: the widest capitol (13) plus a block. */
    static final int START_HALF = 7;

    /**
     * Whether a capitol centred at (dx, dz) from the centre stands on ground (design/maps.md section 5): its square,
     * sampled at 3x3 points, has no void, lava or water deeper than a ford. StartPhase refuses a start anywhere else,
     * so nobody founds a base in the void of the End maps or in Archipelago's sea. Random terrain is all ground.
     */
    public boolean startGround(double dx, double dz) {
        if (!preset.premade())
            return true;
        Sample s = new Sample();
        for (int i = -1; i <= 1; i++)
            for (int k = -1; k <= 1; k++) {
                sample(dx + i * START_HALF, dz + k * START_HALF, s);
                if (s.surface == Surface.VOID || s.surface == Surface.LAVA
                        || (s.surface == Surface.WATER && s.height < -1))
                    return false;
            }
        return true;
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
