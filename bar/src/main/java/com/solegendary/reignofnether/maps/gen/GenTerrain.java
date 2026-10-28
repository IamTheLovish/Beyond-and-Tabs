package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;

import java.util.Arrays;

/**
 * The generated map's ground (maps spec 3.1 on a natural, unfolded field): height tiers, cliffs and ramps, ridges with
 * chokes, a river with fords, lakes, the rim shelf, flat base pads and a plaza, roads that are always walkable.
 * <p>
 * Heights (relative to the build's base level): water surface 0; deep beds -3, fords and shores -1; T-1 valley floors 1;
 * T0 lowland 4; T1 8; T2 12; T3 16. The tier field is a warped fractal sum plus thin ridges, mapped by percentile so
 * every map has the same share of each tier (5% lakes, 11% valleys, 44% lowland, 25% T1, 11.5% T2, 3.5% crests), then
 * terraced: where a "cliffiness" field is high the step between tiers is squeezed into a cliff, where it is low it is a
 * walkable slope. Roads are slope-limited profiles (one block up per 3.3 along, maps spec RAMP_RUN) through anchors: the
 * pads, the plaza, the fords and the roads already laid. Every step a unit takes on a road is one block at most.
 */
final class GenTerrain {
    static final int PAD = 52;
    static final double SLOPE = 0.3;
    static final int ROAD_HALF = 6, ROAD_BLEND = 7;
    /** Heights of tiers -1 .. 4. */
    static final double[] TIER_H = { 1, 4, 8, 12, 16, 18 };
    static final double WATER_LINE = -1.32, DEEP_LINE = -1.5;

    final GenPlan p;
    final GenMap m;
    final int half, w;
    final double[] tau, cliff, hf;
    /** 0 land, 1 shallow water (bed -1: fords, shores), 2 deep (bed -3). */
    final byte[] water;
    /** Distance to the nearest road's centre line (capped), the river's, and whether a column is on a main road. */
    final float[] roadDist, riverDist;
    final boolean[] mainRoad;

    GenTerrain(GenPlan p, GenMap m) {
        this.p = p;
        this.m = m;
        this.half = m.half;
        this.w = m.width;
        int cells = w * w;
        tau = new double[cells];
        cliff = new double[cells];
        hf = new double[cells];
        water = new byte[cells];
        roadDist = new float[cells];
        riverDist = new float[cells];
        mainRoad = new boolean[cells];
        Arrays.fill(roadDist, 99);
        Arrays.fill(riverDist, 99);
    }

    int idx(int x, int z) {
        return (x + half) * w + (z + half);
    }

    void build() {
        fields();
        seats();
        plaza();
        spurs();
        heights();
        river();
        rim();
        flats();
        homePads();
        for (GenPlan.Road r : p.roads)
            road(r);
        flats();
        finish();
    }

    // ------------------------------------------------------------------------------------------------- the tier field

    private double symX, symZ;

    /**
     * Blocks either side of a mirror map's fold (the line through the centre between the sides) over which a field is
     * blended with its point mirror. Reading the far half straight off the canonical half leaves a seam: the two points
     * that meet on the fold are a map apart in the noise (Oct 22, found on the sheets: 538 of 1206 fold columns jumped 2+).
     */
    static final double SEAM_BAND = 24;

    private interface Field {
        double at(double x, double z);
    }

    /**
     * A noise field as this map reads it: as is, or on a mirror map point-symmetric through the centre and continuous
     * across the fold. Beyond the band it is the canonical half's value (the old fold); inside it a smooth blend with
     * the mirror point, half and half on the fold. Both points of a pair compute from the same canonical point, so the
     * symmetry is exact.
     */
    private double field(Field f, double x, double z) {
        if (!p.mirror)
            return f.at(x, z);
        sym(x, z);
        double cx = symX, cz = symZ;
        double k = Fairness.smooth(-SEAM_BAND, SEAM_BAND, cx * p.axisX + cz * p.axisZ);
        return k >= 1 ? f.at(cx, cz) : k * f.at(cx, cz) + (1 - k) * f.at(-cx, -cz);
    }

    /** A mirror map reads every field at the canonical half's point (point symmetry through the centre). */
    private void sym(double x, double z) {
        if (p.mirror) {
            double dot = x * p.axisX + z * p.axisZ;
            boolean keep = dot > 1e-9 || (Math.abs(dot) <= 1e-9 && (x > 0 || (x == 0 && z >= 0)));
            if (!keep) {
                symX = -x;
                symZ = -z;
                return;
            }
        }
        symX = x;
        symZ = z;
    }

    /** The low-frequency fields are evaluated on a lattice this many blocks apart and interpolated (a 9x saving). */
    static final int LATTICE = 3;

    private void fields() {
        long ns = GenRng.stream(p.draw, "noise").nextLong();
        GenNoise base = new GenNoise(ns), warpA = new GenNoise(ns + 1), warpB = new GenNoise(ns + 2),
            ridge = new GenNoise(ns + 3), gate = new GenNoise(ns + 4), cl = new GenNoise(ns + 5);
        double cr = 0.3 * half;
        // the lattice is centred on the map (multiples of LATTICE), so a mirror map's lattice is its own mirror image
        int lh = half / LATTICE + 2, lw = 2 * lh + 1;
        double[] lt = new double[lw * lw], lc = new double[lw * lw];
        Field tier = (sx, sz) -> {
            double wx = sx + 28 * warpA.fbm(sx, sz, 150, 2, 0.5), wz = sz + 28 * warpB.fbm(sx, sz, 150, 2, 0.5);
            double bb = base.fbm(wx, wz, 190, 4, 0.5);
            double rg = ridge.ridged(wx, wz, 140);
            double gt = Fairness.smooth(0.0, 0.35, gate.fbm(sx, sz, 300, 2, 0.5));
            return bb + 0.5 * Math.max(0, rg - 0.8) / 0.2 * gt
                + p.centreBias * StrictMath.exp(-(sx * sx + sz * sz) / (cr * cr));
        };
        Field cliffiness = (sx, sz) -> Fairness.smooth(-0.15, 0.35, cl.fbm(sx, sz, 170, 3, 0.5));
        for (int a = -lh; a <= lh; a++)
            for (int b = -lh; b <= lh; b++) {
                int li = (a + lh) * lw + (b + lh);
                lt[li] = field(tier, a * LATTICE, b * LATTICE);
                lc[li] = field(cliffiness, a * LATTICE, b * LATTICE);
            }
        double[] t = new double[w * w];
        int sampled = 0;
        double[] sample = new double[(w / 2 + 1) * (w / 2 + 1)];
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                int i = idx(x, z);
                if (!m.inside(x, z))
                    continue;
                int a0 = Math.floorDiv(x, LATTICE), b0 = Math.floorDiv(z, LATTICE);
                double fx = (x - a0 * LATTICE) / (double) LATTICE, fz = (z - b0 * LATTICE) / (double) LATTICE;
                int l00 = (a0 + lh) * lw + (b0 + lh);
                t[i] = bilerp(lt, l00, lw, fx, fz);
                cliff[i] = bilerp(lc, l00, lw, fx, fz);
                if (((x + half) & 1) == 0 && ((z + half) & 1) == 0)
                    sample[sampled++] = t[i];
            }
        double[] sorted = Arrays.copyOf(sample, sampled);
        Arrays.sort(sorted);
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                int i = idx(x, z);
                if (m.inside(x, z))
                    tau[i] = tierOf(rank(sorted, t[i]));
            }
    }

    private static double bilerp(double[] f, int i00, int lw, double fx, double fz) {
        double a = f[i00], b = f[i00 + lw], c = f[i00 + 1], d = f[i00 + lw + 1];
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    private static double rank(double[] sorted, double v) {
        int lo = 0, hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] < v)
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo / (double) Math.max(1, sorted.length);
    }

    private static final double[] P = { 0, 0.05, 0.16, 0.60, 0.85, 0.965, 1.0 };
    private static final double[] T = { -1.9, -1.32, -0.5, 0.5, 1.5, 2.5, 3.3 };

    private static double tierOf(double pct) {
        for (int k = 1; k < P.length; k++)
            if (pct <= P[k])
                return T[k - 1] + (T[k] - T[k - 1]) * (pct - P[k - 1]) / (P[k] - P[k - 1]);
        return T[T.length - 1];
    }

    // ---------------------------------------------------------------------------------------- seats, plaza, spurs

    /**
     * Each seat's ground: a flat pad on its tier, blended into the land round it. A bastion's pad is a plateau a tier
     * up, ringed by a cliff (the ground just outside held a tier lower), reached only where its roads cut ramps. An
     * open seat's surroundings are pulled toward its tier and lose their cliffs out to 130 blocks.
     */
    private void seats() {
        GenNoise wob = new GenNoise(GenRng.stream(p.draw, "seat-wobble").nextLong());
        for (int s = 0; s < p.n; s++) {
            double sx = p.slotX(s), sz = p.slotZ(s);
            int k = p.baseTier[s];
            Archetype a = p.seat[s];
            int reach = a.smooth ? 132 : 112;
            for (int x = (int) sx - reach; x <= sx + reach; x++)
                for (int z = (int) sz - reach; z <= sz + reach; z++) {
                    if (!m.inGrid(x, z) || !m.inside(x, z))
                        continue;
                    int i = idx(x, z);
                    double hd = Geo.len(x - sx, z - sz);
                    // the rims wobble with the angle (no compass circles: maps spec F8), the same way on a mirror twin
                    boolean twin = p.mirror && s >= p.n / 2;
                    int key = twin ? s - p.n / 2 : s;
                    double wv = 0;
                    if (hd > 48 && hd < 112) {
                        // the direction from the seat, as a point on a circle of radius 40 in the noise
                        double ux = (x - sx) / hd * (twin ? -1 : 1), uz = (z - sz) / hd * (twin ? -1 : 1);
                        wv = wob.fbm(ux * 40 + key * 200, uz * 40, 30, 2, 0.5);
                    }
                    if (a.plateau) {
                        double rp = 62 + 10 * wv;
                        if (hd < rp) {
                            tau[i] = k;
                            cliff[i] = hd < rp - 6 ? 0 : 1;
                        } else if (hd < rp + 22) {
                            tau[i] = Math.min(tau[i], k - 0.9);
                            cliff[i] = hd < rp + 8 ? 1 : cliff[i];
                        } else if (hd < rp + 36) {
                            tau[i] = lerp(Math.min(tau[i], k - 0.9), tau[i], Fairness.smooth(rp + 22, rp + 36, hd));
                        }
                    } else {
                        double rb = 72 + 8 * wv;
                        if (hd < PAD) {
                            tau[i] = k;
                            cliff[i] = 0;
                        } else if (hd < rb) {
                            tau[i] = lerp(k, tau[i], Fairness.smooth(PAD, rb, hd));
                            cliff[i] *= Fairness.smooth(56, rb + 8, hd);
                        }
                        if (a.smooth && hd >= rb && hd < 130) {
                            tau[i] = lerp(k, tau[i], 0.45 + 0.55 * Fairness.smooth(rb, 130, hd));
                            cliff[i] = 0;
                        }
                    }
                }
        }
    }

    private void plaza() {
        int r = (int) GenPlan.PLAZA + 18;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                if (!m.inGrid(x, z))
                    continue;
                int i = idx(x, z);
                double d = Geo.len(x, z);
                if (d < GenPlan.PLAZA) {
                    tau[i] = p.plazaTier;
                    cliff[i] = 0;
                } else if (d < GenPlan.PLAZA + 16) {
                    tau[i] = lerp(p.plazaTier, tau[i], Fairness.smooth(GenPlan.PLAZA, GenPlan.PLAZA + 16, d));
                    cliff[i] *= Fairness.smooth(GenPlan.PLAZA, GenPlan.PLAZA + 18, d);
                }
            }
    }

    /** Ridge spurs across main roads, broken where the road runs: the choke. */
    private void spurs() {
        GenNoise wob = new GenNoise(GenRng.stream(p.draw, "spur-noise").nextLong());
        for (double[] sp : p.spurs) {
            double cx = sp[0], cz = sp[1], dx = sp[2], dz = sp[3], len = sp[4], gap = sp[5];
            double qx = -dz, qz = dx;   // across the road
            int r = (int) (len + 12);
            for (int x = (int) cx - r; x <= cx + r; x++)
                for (int z = (int) cz - r; z <= cz + r; z++) {
                    if (!m.inGrid(x, z) || !m.inside(x, z))
                        continue;
                    double along = (x - cx) * qx + (z - cz) * qz, across = (x - cx) * dx + (z - cz) * dz;
                    double a = Math.abs(along);
                    if (a > len)
                        continue;
                    double thick = (6 + 3 * wob.fbm(x, z, 30, 2, 0.5)) * Math.min(1, (len - a) / 10 + 0.3);
                    int i = idx(x, z);
                    if (Math.abs(across) < thick + 3)
                        cliff[i] = 1;
                    if (Math.abs(across) < thick && a > gap)
                        tau[i] = Math.max(tau[i], 2.3);
                }
        }
    }

    // ------------------------------------------------------------------------------------------------- heights

    /** Terraced heights from the tier field; lakes where it is lowest; a block of roughness on open ground. */
    private void heights() {
        GenNoise micro = new GenNoise(GenRng.stream(p.draw, "micro").nextLong());
        Field roughness = (sx, sz) -> micro.fbm(sx, sz, 22, 2, 0.5);
        double[] flat = flatness();
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                int i = idx(x, z);
                if (!m.inside(x, z)) {
                    hf[i] = 1;
                    continue;
                }
                double t = tau[i];
                if (t < WATER_LINE) {
                    water[i] = (byte) (t < DEEP_LINE ? 2 : 1);
                    hf[i] = t < DEEP_LINE ? -3 : -1;
                    continue;
                }
                double sharp = lerp(1.0, 0.08, cliff[i]);
                hf[i] = heightOf(terrace(t, sharp));
                if (flat[i] < 1)
                    hf[i] += 1.1 * field(roughness, x, z) * (1 - flat[i]);
            }
    }

    /** 1 on base pads and the plaza (no roughness), fading out round them; 0 elsewhere. */
    private double[] flatness() {
        double[] f = new double[w * w];
        stampFlat(f, 0, 0, GenPlan.PLAZA, GenPlan.PLAZA + 10);
        for (int s = 0; s < p.n; s++)
            stampFlat(f, p.slotX(s), p.slotZ(s), PAD, PAD + 8);
        return f;
    }

    private void stampFlat(double[] f, double cx, double cz, double r0, double r1) {
        int r = (int) Math.ceil(r1);
        for (int x = (int) cx - r; x <= cx + r; x++)
            for (int z = (int) cz - r; z <= cz + r; z++) {
                if (!m.inGrid(x, z))
                    continue;
                double v = 1 - Fairness.smooth(r0, r1, Geo.len(x - cx, z - cz));
                int i = idx(x, z);
                if (v > f[i])
                    f[i] = v;
            }
    }

    /**
     * A tier coordinate made into steps: inside a tier the ground stays near the tier's height and the change to the
     * next tier happens in the outer {@code width} of its range - all of it (1.0, a slope) or squeezed (0.08, a cliff).
     */
    static double terrace(double t, double width) {
        double k = Math.floor(t + 0.5);
        double f = t + 0.5 - k;   // 0..1 across tier k, 0.5 at its middle
        double g;
        double hw = width / 2;
        if (f > 1 - hw)
            g = 0.5 * (f - (1 - hw)) / hw;
        else if (f < hw)
            g = -0.5 * (hw - f) / hw;
        else
            g = 0;
        if (width >= 0.999)
            g = f - 0.5;
        return k + g;
    }

    static double heightOf(double ts) {
        double c = Math.max(-1, Math.min(4, ts));
        int k = (int) Math.floor(c);
        if (k >= 4)
            return TIER_H[5];
        double f = c - k;
        return TIER_H[k + 1] + (TIER_H[k + 2] - TIER_H[k + 1]) * f;
    }

    // ------------------------------------------------------------------------------------------------- river, rim

    private void river() {
        if (p.river == null)
            return;
        double[] xs = p.river[0], zs = p.river[1];
        stampDistance(xs, zs, 24, riverDist);
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                int i = idx(x, z);
                double d = riverDist[i];
                if (d >= 24 || !m.inside(x, z))
                    continue;
                if (d < 5.5) {
                    water[i] = (byte) (d < 4 ? 2 : 1);
                    hf[i] = d < 4 ? -3 : -1;
                } else if (d < 7.5) {
                    water[i] = 0;
                    hf[i] = 0;
                } else if (cliff[i] < 0.5) {
                    hf[i] = Math.min(hf[i], lerp(1, hf[i], Fairness.smooth(7.5, 24, d)));   // a valley
                } else if (d < 9.5) {
                    hf[i] = Math.min(hf[i], 1);   // a canyon: the bank, then the wall
                }
            }
    }

    /**
     * The rim shelf (maps spec R3, "edgeline"): most of the arena's edge is raised to T1 with a gentle slope up to it,
     * so the border metal sits on its own terrace; the wall's foot is always dry land.
     */
    private void rim() {
        GenNoise rn = new GenNoise(GenRng.stream(p.draw, "rim").nextLong());
        Field shelf = (sx, sz) -> rn.fbm(sx, sz, 90, 2, 0.5);
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                int i = idx(x, z);
                double e = m.distanceToEdge(x, z);
                if (e > 44 || e < 0)
                    continue;
                boolean on = field(shelf, x, z) > -0.05;
                if (on) {
                    double target = e < 30 ? 8 : lerp(8, hf[i], Fairness.smooth(30, 44, e));
                    if (target > hf[i]) {
                        hf[i] = target;
                        water[i] = 0;
                    }
                }
                if (e < 10) {
                    water[i] = 0;
                    hf[i] = Math.max(hf[i], 1);
                }
            }
    }

    /** Base pads and the plaza: exactly flat at their tier, dry. */
    private void flats() {
        for (int s = 0; s < p.n; s++) {
            double sx = p.slotX(s), sz = p.slotZ(s);
            double hh = TIER_H[p.baseTier[s] + 1];
            for (int x = (int) sx - PAD; x <= sx + PAD; x++)
                for (int z = (int) sz - PAD; z <= sz + PAD; z++)
                    if (m.inGrid(x, z) && Geo.len(x - sx, z - sz) < PAD) {
                        int i = idx(x, z);
                        hf[i] = hh;
                        water[i] = 0;
                    }
        }
        double ph = TIER_H[p.plazaTier + 1];
        int r = (int) GenPlan.PLAZA;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                if (Geo.len(x, z) < GenPlan.PLAZA) {
                    int i = idx(x, z);
                    hf[i] = ph;
                    water[i] = 0;
                }
    }

    /** Dry and level within this of a home patch (its 7x7 pad and the deep-water margin)... */
    static final double HOME_PAD = 5, HOME_DRY = 10;
    /** ...blending back into the land by this. */
    static final double HOME_BLEND = 12;

    /**
     * A small level pad at the seat's own height under each of its planned home patches (GenEconomy.plannedHome), blended
     * into the land round it, before the roads are laid. The home clumps are one template for every seat (lovish Oct 22:
     * "starting mexes around the base can be standardized"), 48-82 blocks out, where the base pad has already blended into
     * the wild ground; without this a slope or a bank under one seat's spot would cost every seat that clump. On a
     * bastion the pad is a lobe of its plateau, reached from the plateau.
     */
    private void homePads() {
        GenEconomy.HomeOption[] planned = GenEconomy.plannedHome(p, GenEconomy.homeOptions());
        for (int s = 0; s < p.n; s++) {
            double hh = TIER_H[p.baseTier[s] + 1];
            for (GenEconomy.HomeOption o : planned) {
                if (o == null)
                    continue;
                for (int[] c : o.spots(p, s)) {
                    int r = (int) Math.ceil(HOME_BLEND);
                    for (int x = c[0] - r; x <= c[0] + r; x++)
                        for (int z = c[1] - r; z <= c[1] + r; z++) {
                            if (!m.inGrid(x, z) || !m.inside(x, z))
                                continue;
                            double d = Geo.len(x - c[0], z - c[1]);
                            if (d >= HOME_BLEND)
                                continue;
                            int i = idx(x, z);
                            double base = water[i] != 0 ? 0 : hf[i];
                            if (d < HOME_DRY)
                                water[i] = 0;
                            hf[i] = d <= HOME_PAD ? hh : lerp(hh, base, Fairness.smooth(HOME_PAD, HOME_BLEND, d));
                        }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- roads

    private float[] tmpD;
    private double[] tmpQ;

    /**
     * Lays one road: the ground along it is smoothed, then held to a slope of at most SLOPE through its anchors (its
     * pads, the plaza, every water crossing at 0, every road already laid where it crosses one), and written into a
     * core ROAD_HALF wide each side with a ROAD_BLEND fade into the land. Water under the core becomes a ford.
     */
    private void road(GenPlan.Road r) {
        int k = r.xs.length;
        double[] th = new double[k];
        for (int s = 0; s < k; s++)
            th[s] = hfAt(r.xs[s], r.zs[s]);
        double[] sm = new double[k];
        for (int s = 0; s < k; s++) {
            double sum = 0;
            int c = 0;
            for (int o = -4; o <= 4; o++) {
                int q = s + o;
                if (q >= 0 && q < k) {
                    sum += th[q];
                    c++;
                }
            }
            sm[s] = sum / c;
        }
        // anchors by priority: pads and plaza (must), water (0), roads already laid
        double[] anchor = new double[k];
        int[] prio = new int[k];
        Arrays.fill(anchor, Double.NaN);
        for (int s = 0; s < k; s++) {
            double x = r.xs[s], z = r.zs[s];
            for (int q = 0; q < p.n; q++)
                if (Geo.len(x - p.slotX(q), z - p.slotZ(q)) < PAD - 3) {
                    anchor[s] = TIER_H[p.baseTier[q] + 1];
                    prio[s] = 3;
                }
            if (prio[s] == 0 && Geo.len(x, z) < GenPlan.PLAZA - 2) {
                anchor[s] = TIER_H[p.plazaTier + 1];
                prio[s] = 3;
            }
            if (prio[s] == 0) {
                int i = idxOf(x, z);
                if (i >= 0 && (water[i] != 0 || riverDist[i] < 7.5)) {
                    anchor[s] = 0;
                    prio[s] = 2;
                } else if (i >= 0 && roadDist[i] <= ROAD_HALF) {
                    anchor[s] = hf[i];
                    prio[s] = 1;
                }
            }
        }
        // keep the anchors that agree with every stronger one (and with the weaker ones kept before them)
        double[] lo = new double[k], hi = new double[k];
        boolean[] keep = new boolean[k];
        for (int s = 0; s < k; s++)
            keep[s] = prio[s] == 3;
        for (int level = 2; level >= 1; level--)
            for (int s = 0; s < k; s++)
                if (prio[s] == level) {
                    bands(anchor, keep, lo, hi);
                    if (anchor[s] >= lo[s] - 1e-6 && anchor[s] <= hi[s] + 1e-6)
                        keep[s] = true;
                }
        bands(anchor, keep, lo, hi);
        double[] q = new double[k];
        for (int s = 0; s < k; s++) {
            double v = Math.max(lo[s], Math.min(hi[s], sm[s]));
            if (s > 0)
                v = Math.max(Math.max(lo[s], q[s - 1] - SLOPE), Math.min(Math.min(hi[s], q[s - 1] + SLOPE), v));
            q[s] = v;
        }
        // stamp
        if (tmpD == null) {
            tmpD = new float[w * w];
            tmpQ = new double[w * w];
        }
        Arrays.fill(tmpD, 99);
        int reach = ROAD_HALF + ROAD_BLEND;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int s = 0; s < k; s++) {
            int cx = (int) Math.round(r.xs[s]), cz = (int) Math.round(r.zs[s]);
            minX = Math.min(minX, cx - reach);
            maxX = Math.max(maxX, cx + reach);
            minZ = Math.min(minZ, cz - reach);
            maxZ = Math.max(maxZ, cz + reach);
            for (int x = cx - reach; x <= cx + reach; x++)
                for (int z = cz - reach; z <= cz + reach; z++) {
                    if (!m.inGrid(x, z))
                        continue;
                    double d = Geo.len(x - r.xs[s], z - r.zs[s]);
                    int i = idx(x, z);
                    if (d < tmpD[i]) {
                        tmpD[i] = (float) d;
                        tmpQ[i] = q[s];
                    }
                }
        }
        for (int x = Math.max(-half, minX); x <= Math.min(half, maxX); x++)
            for (int z = Math.max(-half, minZ); z <= Math.min(half, maxZ); z++) {
                int i = idx(x, z);
                double d = tmpD[i];
                if (d > reach || !m.inside(x, z))
                    continue;
                double wgt = d <= ROAD_HALF ? 1 : 1 - Fairness.smooth(ROAD_HALF, reach, d);
                if (water[i] != 0 && d <= ROAD_HALF + 1.5) {
                    water[i] = 1;     // a ford: one block of water over the bed, walkable at half speed
                    hf[i] = -1;
                } else if (water[i] == 0) {
                    hf[i] = lerp(hf[i], tmpQ[i], wgt);
                }
                if (d < roadDist[i])
                    roadDist[i] = (float) d;
                if (r.main && d <= ROAD_HALF)
                    mainRoad[i] = true;
            }
    }

    /** Slope bands through the kept anchors: lo/hi = the lowest/highest the road may be at each sample. */
    private static void bands(double[] anchor, boolean[] keep, double[] lo, double[] hi) {
        int k = anchor.length;
        Arrays.fill(lo, -1e9);
        Arrays.fill(hi, 1e9);
        for (int s = 0; s < k; s++)
            if (keep[s] && !Double.isNaN(anchor[s])) {
                lo[s] = anchor[s];
                hi[s] = anchor[s];
            }
        for (int s = 1; s < k; s++) {
            lo[s] = Math.max(lo[s], lo[s - 1] - SLOPE);
            hi[s] = Math.min(hi[s], hi[s - 1] + SLOPE);
        }
        for (int s = k - 2; s >= 0; s--) {
            lo[s] = Math.max(lo[s], lo[s + 1] - SLOPE);
            hi[s] = Math.min(hi[s], hi[s + 1] + SLOPE);
        }
    }

    private int idxOf(double x, double z) {
        int ix = (int) Math.round(x), iz = (int) Math.round(z);
        return m.inGrid(ix, iz) ? idx(ix, iz) : -1;
    }

    private double hfAt(double x, double z) {
        int i = idxOf(x, z);
        return i < 0 ? 4 : hf[i];
    }

    /** Distance from every column within {@code reach} of a line to the line (min over its samples). */
    private void stampDistance(double[] xs, double[] zs, int reach, float[] out) {
        for (int s = 0; s < xs.length; s++) {
            int cx = (int) Math.round(xs[s]), cz = (int) Math.round(zs[s]);
            for (int x = cx - reach; x <= cx + reach; x++)
                for (int z = cz - reach; z <= cz + reach; z++) {
                    if (!m.inGrid(x, z))
                        continue;
                    double d = Geo.len(x - xs[s], z - zs[s]);
                    int i = idx(x, z);
                    if (d < out[i])
                        out[i] = (float) d;
                }
        }
    }

    // ------------------------------------------------------------------------------------------------- finish

    /** Whole blocks, dry shores at 0 (water never spills onto a lower bank), surfaces. */
    private void finish() {
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                int i = idx(x, z);
                if (water[i] != 0) {
                    m.height[i] = (byte) (water[i] == 2 ? -3 : -1);
                    continue;
                }
                int hh = (int) Math.round(hf[i]);
                m.height[i] = (byte) Math.max(0, Math.min(30, hh));
            }
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                int i = idx(x, z);
                if (water[i] != 0 || m.height[i] > 1)
                    continue;
                if (nearWater(x, z, 1))
                    m.height[i] = 0;
            }
        byte waterS = (byte) MapLayout.Surface.WATER.ordinal(), sand = (byte) MapLayout.Surface.SAND.ordinal(),
            grass = (byte) MapLayout.Surface.GRASS.ordinal(), cliffS = (byte) MapLayout.Surface.CLIFF.ordinal(),
            alt = (byte) MapLayout.Surface.MESA.ordinal(), land = (byte) MapLayout.Surface.LAND.ordinal();
        GenNoise sn = new GenNoise(GenRng.stream(p.draw, "surface").nextLong());
        Field stone = (sx, sz) -> sn.fbm(sx, sz, 26, 2, 0.5);
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                int i = idx(x, z);
                if (!m.inside(x, z)) {
                    m.surface[i] = land;
                    continue;
                }
                if (water[i] != 0) {
                    m.surface[i] = waterS;
                    continue;
                }
                int hh = m.height[i];
                int step = 0;
                if (x > -half) step = Math.max(step, Math.abs(hh - m.height[i - w]));
                if (x < half) step = Math.max(step, Math.abs(hh - m.height[i + w]));
                if (z > -half) step = Math.max(step, Math.abs(hh - m.height[i - 1]));
                if (z < half) step = Math.max(step, Math.abs(hh - m.height[i + 1]));
                if (step >= 3)
                    m.surface[i] = cliffS;
                else if (hh <= 1 && nearWater(x, z, 3))
                    m.surface[i] = sand;
                else
                    m.surface[i] = hh >= 12 && field(stone, x, z) > -0.1 ? alt : grass;
            }
    }

    private boolean nearWater(int x, int z, int r) {
        for (int a = -r; a <= r; a++)
            for (int b = -r; b <= r; b++) {
                int xx = x + a, zz = z + b;
                if (m.inGrid(xx, zz) && water[idx(xx, zz)] != 0)
                    return true;
            }
        return false;
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
