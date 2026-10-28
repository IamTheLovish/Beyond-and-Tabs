package com.solegendary.reignofnether.maps.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * Every decision the generator takes from the seed before it touches a column: the arena, the seats and their
 * characters, the roads and the river as centre lines, the modifiers. Pure geometry and dice, so the balancer can make
 * the same landscape with the seats turned or the sides swapped (GenOptions.Balance.SWAP) by changing two arguments.
 */
final class GenPlan {
    final long seed;          // the lobby's seed (shown, re-rolled)
    final long draw;          // the seed of this attempt (a reroll draws again)
    final int[] teams;
    final int n;
    final GenMap.Shape shape;
    final int ring, half;
    final double rot;
    final GenOptions.Contrast contrast;
    final boolean flip;
    final int rotation;
    final Archetype[] seat;
    final int[] baseTier;
    final boolean mirror;
    /** Unit vector from the centre toward side A (the first team's seats); used by mirror maps and the river. */
    final double axisX, axisZ;
    final int plazaTier;
    final double centreBias;
    final List<Modifier> modifiers = new ArrayList<>();
    final boolean hydra;
    /** The river's centre line (null: no river) and which side it hugs (+1 side A, -1 side B, 0 none). */
    final double[][] river;
    final int riverSide;
    final List<Road> roads = new ArrayList<>();
    /** Choke spurs: centre x, z, unit direction of the road through it, half-length, gap half-width. */
    final List<double[]> spurs = new ArrayList<>();

    /** A road's centre line, sampled every block; starts at seat {@code from}'s start (or a point on its main road). */
    static final class Road {
        final int seat, to;
        final boolean main;
        final double[] xs, zs;

        Road(int seat, int to, boolean main, double[] xs, double[] zs) {
            this.seat = seat;
            this.to = to;
            this.main = main;
            this.xs = xs;
            this.zs = zs;
        }
    }

    static final int BASE_RING = 150, MARGIN = 90, MIN_NEIGHBOUR = 116;
    /** A square arena's half side per round radius: the same area (sqrt(pi) / 2). */
    static final double SQUARE_SIDE = 0.886;
    static final double PLAZA = 30;

    GenPlan(int[] teams, long seed, long draw, GenOptions o, boolean flip, int rotation) {
        this.seed = seed;
        this.draw = draw;
        this.teams = teams.clone();
        this.n = teams.length;
        this.flip = flip;
        this.rotation = rotation;
        GenRng r = GenRng.stream(draw, "plan");
        GenOptions.Shape sh = o.shape();
        if (sh == GenOptions.Shape.ANY)
            sh = r.chance(0.5) ? GenOptions.Shape.ROUND : GenOptions.Shape.SQUARE;
        shape = sh == GenOptions.Shape.SQUARE ? GenMap.Shape.SQUARE : GenMap.Shape.ROUND;
        ring = (int) Math.max(BASE_RING, Math.ceil(MIN_NEIGHBOUR / 2.0 / StrictMath.sin(Math.PI / n)));
        int radius = ring + MARGIN;
        half = shape == GenMap.Shape.SQUARE ? (int) Math.round(radius * SQUARE_SIDE) : radius;
        // a square's seats sit in its corners or on its sides (lovish Oct 21: "corners make natural strong seats");
        // the seed picks, the balancer evens out what that costs a seat
        double rot0 = Math.PI;
        boolean cornerSeats = shape == GenMap.Shape.SQUARE && r.chance(0.5);
        if (cornerSeats)   // 1v1 and 2v2: the corners; 4v4: every seat between a corner and a side (else half of each)
            rot0 += n <= 4 ? Math.PI / 4 : Math.PI / n;
        rot = rot0 + 2 * Math.PI * rotation / n;
        int sides = teamCount(teams);
        boolean halves = sides == 2 && n % 2 == 0 && countOf(teams, teams[0]) == n / 2;
        GenOptions.Contrast c = o.contrast();
        if (c == GenOptions.Contrast.AUTO) {
            GenOptions.Contrast[] pick = { GenOptions.Contrast.PROSPECTOR_WARDEN, GenOptions.Contrast.OPEN_BASTION,
                GenOptions.Contrast.RIVER, GenOptions.Contrast.MIXED };
            c = pick[r.weighted(new double[] { 3, 3, 2, 1 })];
        }
        if (c == GenOptions.Contrast.MIRROR && !halves)
            c = GenOptions.Contrast.MIXED;   // point symmetry needs two equal sides facing each other
        contrast = c;
        mirror = c == GenOptions.Contrast.MIRROR;
        seat = new Archetype[n];
        baseTier = new int[n];
        GenRng ra = GenRng.stream(draw, "archetypes");
        Archetype a = Archetype.PLAIN, b = Archetype.PLAIN;
        switch (c) {
            case PROSPECTOR_WARDEN -> { a = Archetype.PROSPECTOR; b = Archetype.WARDEN; }
            case OPEN_BASTION -> { a = Archetype.OPEN; b = Archetype.BASTION; }
            case RIVER -> { a = Archetype.MOATED; b = Archetype.BANKS; }
            default -> { }
        }
        if (flip) {
            Archetype t = a;
            a = b;
            b = t;
        }
        Archetype[] mixed = { Archetype.PROSPECTOR, Archetype.WARDEN, Archetype.OPEN, Archetype.BASTION, Archetype.PLAIN };
        for (int i = 0; i < n; i++) {
            if (c == GenOptions.Contrast.MIXED)
                seat[i] = mixed[ra.nextInt(mixed.length)];
            else if (sides == 2)
                seat[i] = teams[i] == teams[0] ? a : b;
            else   // free-for-all: alternate the pair round the ring
                seat[i] = i % 2 == 0 ? a : b;
            baseTier[i] = seat[i].baseTier;
        }
        if (mirror)   // a mirror map's second half copies the first
            for (int i = n / 2; i < n; i++) {
                seat[i] = seat[i - n / 2];
                baseTier[i] = baseTier[i - n / 2];
            }
        // the axis from the centre toward side A
        double ax = 0, az = 0;
        for (int i = 0; i < n; i++) {
            double ang = rot + 2 * Math.PI * i / n;
            double sgn = teams[i] == teams[0] ? 1 : -1;
            ax += sgn * StrictMath.cos(ang);
            az += sgn * StrictMath.sin(ang);
        }
        double al = Geo.len(ax, az);
        if (al < 1e-6) {
            ax = StrictMath.cos(rot);
            az = StrictMath.sin(rot);
            al = 1;
        }
        axisX = ax / al;
        axisZ = az / al;
        GenRng rt = GenRng.stream(draw, "terrain-plan");
        plazaTier = rt.chance(0.25) ? -1 : rt.chance(0.2) ? 1 : 0;
        centreBias = rt.range(-0.5, 0.5);
        // modifiers
        GenRng rm = GenRng.stream(draw, "modifiers");
        int count = switch (o.modifiers()) {
            case OFF -> 0;
            case ON -> rm.chance(0.4) ? 2 : 1;
            case SURPRISE -> rm.chance(0.35) ? 3 : 2;
        };
        List<Modifier> pool = new ArrayList<>();
        for (Modifier mo : Modifier.values())
            if (mo.live || !o.liveOnly())
                pool.add(mo);
        for (int k = 0; k < count && !pool.isEmpty(); k++) {
            double[] wts = new double[pool.size()];
            for (int i = 0; i < wts.length; i++)
                wts[i] = pool.get(i).weight;
            modifiers.add(pool.remove(rm.weighted(wts)));
        }
        hydra = modifiers.contains(Modifier.HYDRA_LAIR);
        // the river: always for the river contrast, sometimes otherwise
        GenRng rr = GenRng.stream(draw, "river");
        boolean wantRiver = c == GenOptions.Contrast.RIVER || (!mirror && rr.chance(0.3));
        if (wantRiver && sides >= 2) {
            int side = c == GenOptions.Contrast.RIVER ? (seat[0] == Archetype.MOATED ? 1 : -1) : (rr.chance(0.5) ? 1 : -1);
            river = riverLine(rr, side);
            riverSide = side;
        } else {
            river = null;
            riverSide = 0;
        }
        roads(GenRng.stream(draw, "roads"));
        spurs(GenRng.stream(draw, "spurs"));
    }

    static int teamCount(int[] teams) {
        java.util.Set<Integer> s = new java.util.HashSet<>();
        for (int t : teams)
            s.add(t);
        return s.size();
    }

    static int countOf(int[] teams, int t) {
        int c = 0;
        for (int x : teams)
            if (x == t)
                c++;
        return c;
    }

    double slotX(int i) {
        if (mirror && i >= n / 2)   // exactly the twin's negation, so the mirror is exact on the block grid
            return -slotX(i - n / 2);
        return Math.round(StrictMath.cos(rot + 2 * Math.PI * i / n) * ring);
    }

    double slotZ(int i) {
        if (mirror && i >= n / 2)
            return -slotZ(i - n / 2);
        return Math.round(StrictMath.sin(rot + 2 * Math.PI * i / n) * ring);
    }

    /** Twin seat of a mirror map (point symmetry through the centre). */
    int twin(int i) {
        return (i + n / 2) % n;
    }

    double edge(double x, double z) {
        if (shape == GenMap.Shape.SQUARE)
            return half - Math.max(Math.abs(x), Math.abs(z));
        return half - Math.sqrt(x * x + z * z);
    }

    // ---------------------------------------------------------------------------------------------- the river

    /**
     * A river across the map, roughly square to the axis between the sides, offset toward side {@code side} (the river
     * contrast puts it 70-90 blocks in front of the moated side's starts), meandering, and pushed off every base: no
     * point closer than 80 to a start. Ends 12 inside the arena's edge.
     */
    private double[][] riverLine(GenRng r, int side) {
        double off = contrast == GenOptions.Contrast.RIVER
            ? side * (n == 2 ? r.range(0.42, 0.52) * ring : r.range(0.10, 0.18) * ring)
            : side * r.range(0.0, 0.35) * ring;
        double tx = -axisZ, tz = axisX;   // along the river
        double ox = axisX * off, oz = axisZ * off;
        GenNoise mn = new GenNoise(r.nextLong());
        double amp = r.range(10, 22), wl = r.range(70, 120);
        List<double[]> pts = new ArrayList<>();
        for (double t = -2.0 * half; t <= 2.0 * half; t += 2) {
            double x = ox + tx * t, z = oz + tz * t;
            double w = amp * mn.fbm(t, 0, wl, 2, 0.5) * 2;
            x += axisX * w;
            z += axisZ * w;
            if (edge(x, z) < 12)
                continue;
            pts.add(new double[] { x, z });
        }
        // push off the bases and the plaza, then smooth, a few rounds
        for (int round = 0; round < 4; round++) {
            for (double[] p : pts) {
                for (int i = 0; i < n; i++) {
                    double sx = slotX(i), sz = slotZ(i);
                    double d = Geo.len(p[0] - sx, p[1] - sz);
                    if (d < 82 && d > 1e-6) {
                        p[0] = sx + (p[0] - sx) * 82 / d;
                        p[1] = sz + (p[1] - sz) * 82 / d;
                    }
                }
                double dc = Geo.len(p[0], p[1]);
                if (dc < PLAZA + 18 && dc > 1e-6) {
                    p[0] *= (PLAZA + 18) / dc;
                    p[1] *= (PLAZA + 18) / dc;
                }
            }
            for (int i = 1; i + 1 < pts.size(); i++) {
                double[] a = pts.get(i - 1), b = pts.get(i), c = pts.get(i + 1);
                b[0] = (a[0] + 2 * b[0] + c[0]) / 4;
                b[1] = (a[1] + 2 * b[1] + c[1]) / 4;
            }
        }
        if (pts.size() < 8)
            return null;
        double[][] out = new double[2][pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            out[0][i] = pts.get(i)[0];
            out[1][i] = pts.get(i)[1];
        }
        return resample(out[0], out[1]);
    }

    // ---------------------------------------------------------------------------------------------- the roads

    /**
     * The roads: every seat's main road to the plaza, and a seam road to each neighbour round the ring (two for a
     * 1v1, one each side). A bastion keeps one seam road off its plateau: that road starts at the foot of its main
     * ramp, so the plateau has two ways up, not three. Meanders come from a seeded noise along each road.
     */
    private void roads(GenRng r) {
        GenNoise mn = new GenNoise(r.nextLong());
        int[] noRamp = new int[n];
        for (int i = 0; i < n; i++)
            noRamp[i] = seat[i].plateau ? (r.chance(0.5) ? 1 : -1) : 0;
        int mains = mirror ? n / 2 : n;
        for (int i = 0; i < mains; i++) {
            double sx = slotX(i), sz = slotZ(i);
            double len = Geo.len(sx, sz);
            double ex = sx / len * (PLAZA - 2), ez = sz / len * (PLAZA - 2);
            double amp = r.range(8, 16);
            double[][] line = meander(new double[][] { { sx, sz }, { ex, ez } }, mn, i * 31.7, amp);
            roads.add(new Road(i, -1, true, line[0], line[1]));
        }
        for (int i = mains; i < n; i++)   // a mirror map's second half: the first half turned through the centre
            roads.add(twinRoad(roads.get(i - n / 2), i, -1));
        int seams = n == 2 ? 2 : n;
        int own = mirror ? seams / 2 : seams;
        for (int k = 0; k < own; k++) {
            int i = n == 2 ? 0 : k, j = n == 2 ? 1 : (k + 1) % n;
            double sx = slotX(i), sz = slotZ(i), tx = slotX(j), tz = slotZ(j);
            double wx, wz;
            if (n == 2) {
                // round the side of the map: k = 0 one flank, k = 1 the other
                double px = -axisZ * (k == 0 ? 1 : -1), pz = axisX * (k == 0 ? 1 : -1);
                double rad = r.range(0.62, 0.82) * ring;
                wx = px * rad;
                wz = pz * rad;
            } else {
                double mx = (sx + tx) / 2, mz = (sz + tz) / 2, ml = Geo.len(mx, mz);
                double rad = ml * r.range(0.95, 1.1);
                wx = mx / ml * rad;
                wz = mz / ml * rad;
            }
            double[] from = { sx, sz }, to = { tx, tz };
            // a bastion's no-ramp side starts the road at its main road's foot
            if (noRamp[i] != 0 && (n == 2 ? (k == 0) == (noRamp[i] > 0) : noRamp[i] > 0))
                from = footOf(i);
            if (noRamp[j] != 0 && (n == 2 ? (k == 0) == (noRamp[j] > 0) : noRamp[j] < 0))
                to = footOf(j);
            double[][] line = meander(new double[][] { from, { wx, wz }, to }, mn, 500 + k * 47.3, r.range(6, 12));
            roads.add(new Road(i, j, false, line[0], line[1]));
        }
        if (mirror) {
            int seamStart = n;
            for (int k = own; k < seams; k++) {
                Road src = roads.get(seamStart + k - own);
                roads.add(twinRoad(src, twin(src.seat), src.to < 0 ? -1 : twin(src.to)));
            }
        }
    }

    private static Road twinRoad(Road src, int seat, int to) {
        double[] xs = new double[src.xs.length], zs = new double[src.zs.length];
        for (int k = 0; k < xs.length; k++) {
            xs[k] = -src.xs[k];
            zs[k] = -src.zs[k];
        }
        return new Road(seat, to, src.main, xs, zs);
    }

    /** A point on seat i's main road about 95 blocks out from its start (past the ramp). */
    private double[] footOf(int i) {
        Road main = roads.get(i);
        double sx = slotX(i), sz = slotZ(i);
        for (int k = 0; k < main.xs.length; k++)
            if (Geo.len(main.xs[k] - sx, main.zs[k] - sz) >= 95)
                return new double[] { main.xs[k], main.zs[k] };
        return new double[] { main.xs[main.xs.length - 1], main.zs[main.zs.length - 1] };
    }

    /** Through the given way points, every block, bent sideways by noise (zero at both ends). */
    private double[][] meander(double[][] way, GenNoise mn, double key, double amp) {
        double[][] base = polyline(way);
        double[] xs = base[0], zs = base[1];
        int m = xs.length;
        double[] ox = new double[m], oz = new double[m];
        double total = m - 1;
        for (int k = 0; k < m; k++) {
            int a = Math.max(0, k - 3), b = Math.min(m - 1, k + 3);
            double dx = xs[b] - xs[a], dz = zs[b] - zs[a], dl = Geo.len(dx, dz);
            double nx = dl > 0 ? -dz / dl : 0, nz = dl > 0 ? dx / dl : 0;
            double t = k / Math.max(1, total);
            double env = StrictMath.sin(Math.PI * t);
            double w = amp * env * mn.fbm(k, key, 60, 2, 0.5) * 2;
            ox[k] = xs[k] + nx * w;
            oz[k] = zs[k] + nz * w;
        }
        return resample(ox, oz);
    }

    private static double[][] polyline(double[][] way) {
        List<double[]> pts = new ArrayList<>();
        for (int s = 0; s + 1 < way.length; s++) {
            double[] a = way[s], b = way[s + 1];
            double len = Geo.len(b[0] - a[0], b[1] - a[1]);
            int steps = Math.max(1, (int) Math.ceil(len));
            for (int k = 0; k < steps; k++)
                pts.add(new double[] { a[0] + (b[0] - a[0]) * k / steps, a[1] + (b[1] - a[1]) * k / steps });
        }
        pts.add(way[way.length - 1]);
        double[][] out = new double[2][pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            out[0][i] = pts.get(i)[0];
            out[1][i] = pts.get(i)[1];
        }
        return out;
    }

    /** The same line with one point per block of its length (so slopes along it are per block). */
    static double[][] resample(double[] xs, double[] zs) {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] { xs[0], zs[0] });
        double carry = 0;
        for (int i = 1; i < xs.length; i++) {
            double ax = xs[i - 1], az = zs[i - 1], bx = xs[i], bz = zs[i];
            double len = Geo.len(bx - ax, bz - az);
            double t = 1 - carry;
            while (t <= len) {
                double f = t / len;
                pts.add(new double[] { ax + (bx - ax) * f, az + (bz - az) * f });
                t += 1;
            }
            carry = len - (t - 1);
        }
        double[][] out = new double[2][pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            out[0][i] = pts.get(i)[0];
            out[1][i] = pts.get(i)[1];
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------- chokes

    /**
     * One choke on most main roads (maps spec 3.1: "each wedge's main lane gets one choke 14-20 wide"): a ridge spur
     * across the road 40-62% of the way to the plaza, broken where the road runs. Bastions have their ramp, open seats
     * mostly none.
     */
    private void spurs(GenRng r) {
        for (int i = 0; i < n; i++) {
            if (mirror && i >= n / 2)
                break;
            double p = seat[i].plateau ? 0.3 : seat[i].smooth ? 0.2 : 0.65;
            if (!r.chance(p))
                continue;
            Road main = roads.get(i);
            int k = (int) (main.xs.length * r.range(0.40, 0.62));
            k = Math.max(3, Math.min(main.xs.length - 4, k));
            double dx = main.xs[k + 3] - main.xs[k - 3], dz = main.zs[k + 3] - main.zs[k - 3], dl = Geo.len(dx, dz);
            spurs.add(new double[] { main.xs[k], main.zs[k], dx / dl, dz / dl, r.range(24, 40), r.range(7, 10) });
        }
        if (mirror) {
            int m = spurs.size();
            for (int s = 0; s < m; s++) {
                double[] sp = spurs.get(s);
                spurs.add(new double[] { -sp[0], -sp[1], -sp[2], -sp[3], sp[4], sp[5] });
            }
        }
    }
}
