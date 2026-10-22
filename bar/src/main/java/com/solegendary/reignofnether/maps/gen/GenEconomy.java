package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MetalOptions;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * The generated map's economy and objectives, on its finished ground: home rings, the centre, contested capture sites on
 * the front seams, a warden's own sites, the modifiers' sites, the reserved jungle with its (data-only) camps, every
 * seat's natural pair, the hole-driven open-ground fill (lovish Oct 21: "more metal along the map edges and randomly
 * filling empty areas"), patch values by exposure, the rich vein, and last the forest, which keeps clear of all of it.
 * <p>
 * Every patch obeys the M1 rules (design/maps.md section 2): a level 7x7 pad of land, no deep water within 7, reachable by
 * ground from its owner's start, off the roads, clear of sites, vents and starts, never in a reserved zone. On a mirror
 * map every placement is made on side A's half and copied through the centre, so the economy is exactly symmetric.
 */
final class GenEconomy {
    enum PKind { HOME, CENTRE, NATURAL, FILL, VEIN, NUDGE }

    static final class Patch {
        int x, z;
        float value;
        final PKind kind;
        int twin = -1;
        boolean alive = true;

        Patch(int x, int z, float value, PKind kind) {
            this.x = x;
            this.z = z;
            this.value = value;
            this.kind = kind;
        }
    }

    static final class Cand {
        final int x, z;
        final double[] d;
        final int owner;
        final double edge;
        double near = Double.MAX_VALUE;
        boolean used;
        /** Clear of every site, vent, camp and jungle (worked out once those are placed, before the fill). */
        boolean open = true;

        Cand(int x, int z, double[] d, int owner, double edge) {
            this.x = x;
            this.z = z;
            this.d = d;
            this.owner = owner;
            this.edge = edge;
        }
    }

    static final int FILL_MIN = 20, SITE_GAP = 40, START_CLEAR = 46, ROAD_CLEAR = 9;
    static final float RICH = MapLayout.STANDARD * 1.5f, CENTRE = 2.0f, STANDARD = 1.0f, LEAN = MapLayout.LEAN;

    final GenPlan p;
    final GenMap m;
    final GenTerrain t;
    final MetalOptions metal;
    final WalkGrid g;
    final int[][] dist;
    final List<Patch> patches = new ArrayList<>();
    final List<Cand> cands = new ArrayList<>();
    final List<Cand> siteCands = new ArrayList<>();
    final List<Cand> wildCands = new ArrayList<>();
    final int[] count;
    private final GenRng rng;

    GenEconomy(GenPlan p, GenMap m, GenTerrain t, MetalOptions metal) {
        this.p = p;
        this.m = m;
        this.t = t;
        this.metal = metal;
        this.g = new WalkGrid(m, false);
        this.dist = Fairness.fields(m, g);
        this.rng = GenRng.stream(p.draw ^ (p.flip ? 0x5A5A : 0) ^ ((long) p.rotation << 20), "economy");
        this.count = new int[p.n];
    }

    void build() {
        candidates();
        homeRings();
        centre();
        seamSites();
        frontSites();
        ownSites();
        modifierSites();
        jungle();
        for (Cand c : cands)
            c.open = clearOfSites(c.x, c.z) >= 18 && clearOfVents(c.x, c.z) >= 12 && !inJungle(c.x, c.z, 4);
        naturals();
        fill();
        values();
        if (p.modifiers.contains(Modifier.RICH_VEIN))
            vein();
        forest();
        write();
    }

    // ------------------------------------------------------------------------------------------------- helpers

    boolean canon(double x, double z) {
        if (!p.mirror)
            return true;
        double dot = x * p.axisX + z * p.axisZ;
        return dot > 1e-9 || (Math.abs(dot) <= 1e-9 && (x > 0 || (x == 0 && z >= 0)));
    }

    double[] d(double x, double z) {
        double[] out = new double[p.n];
        for (int s = 0; s < p.n; s++)
            out[s] = g.blocks(dist[s], x, z);
        return out;
    }

    static int argmin(double[] d) {
        int best = -1;
        for (int s = 0; s < d.length; s++)
            if (!Double.isInfinite(d[s]) && (best < 0 || d[s] < d[best]))
                best = s;
        return best;
    }

    double exposure(double[] d, int s) {
        return Fairness.exposure(m, d, s);
    }

    private boolean land(int x, int z) {
        if (!m.inGrid(x, z))
            return false;
        int i = m.idx(x, z);
        return t.water[i] == 0 && m.surface[i] != (byte) MapLayout.Surface.CLIFF.ordinal();
    }

    /** A level pad of (2r+1)^2 land columns within one block of its centre's height. */
    private boolean level(int x, int z, int r) {
        if (!land(x, z))
            return false;
        int h0 = m.heightAt(x, z);
        for (int a = -r; a <= r; a++)
            for (int b = -r; b <= r; b++)
                if (!land(x + a, z + b) || Math.abs(m.heightAt(x + a, z + b) - h0) > 1)
                    return false;
        return true;
    }

    private boolean deepNear(int x, int z, int r) {
        for (int a = -r; a <= r; a++)
            for (int b = -r; b <= r; b++) {
                int xx = x + a, zz = z + b;
                if (m.inGrid(xx, zz) && t.water[m.idx(xx, zz)] == 2)
                    return true;
            }
        return false;
    }

    double nearestStart(double x, double z) {
        double best = Double.MAX_VALUE;
        for (int s = 0; s < p.n; s++)
            best = Math.min(best, Geo.len(x - p.slotX(s), z - p.slotZ(s)));
        return best;
    }

    // ------------------------------------------------------------------------------------------------- candidates

    private void candidates() {
        int ox = rng.nextInt(4), oz = rng.nextInt(4);
        int rim = Math.max(metal.rimInset(), MetalOptions.MIN_RIM_INSET);
        for (int x = -m.half + ox; x <= m.half; x += 4)
            for (int z = -m.half + oz; z <= m.half; z += 4) {
                // a seeded jitter so the grid never shows (maps spec R4)
                long h = GenRng.mix(p.draw ^ (x * 73856093L) ^ (z * 19349663L));
                int jx = x + (int) ((h & 3) - 1), jz = z + (int) (((h >> 2) & 3) - 1);
                if (!m.inGrid(jx, jz) || !canon(jx, jz))
                    continue;
                double e = m.distanceToEdge(jx, jz);
                if (e < rim)
                    continue;
                int i = m.idx(jx, jz);
                if (t.roadDist[i] < ROAD_CLEAR || nearestStart(jx, jz) < START_CLEAR)
                    continue;
                if (!level(jx, jz, MapLayout.PAD) || deepNear(jx, jz, 7))
                    continue;
                double[] dd = d(jx, jz);
                int o = argmin(dd);
                if (o < 0)
                    continue;
                if (metal.reserved().reserves(jx, jz))
                    continue;
                cands.add(new Cand(jx, jz, dd, o, e));
            }
        // site candidates: a 7x7 level pad (the 5x5 plinth and its posts) beside a road, on a 3-block grid
        for (int x = -m.half + 1; x <= m.half; x += 3)
            for (int z = -m.half + 1; z <= m.half; z += 3) {
                if (!canon(x, z) || m.distanceToEdge(x, z) < 24 || nearestStart(x, z) < 60)
                    continue;
                int i = m.idx(x, z);
                if (t.roadDist[i] < 8 || t.roadDist[i] > 60 || !level(x, z, 3) || deepNear(x, z, 5))
                    continue;
                double[] dd = d(x, z);
                boolean all = true;
                for (double v : dd)
                    all &= !Double.isInfinite(v);
                if (all)
                    siteCands.add(new Cand(x, z, dd, argmin(dd), m.distanceToEdge(x, z)));
            }
        // wild ground for the jungle: any reachable dry land off the roads, rough or level
        for (int x = -m.half + 4; x <= m.half; x += 8)
            for (int z = -m.half + 4; z <= m.half; z += 8) {
                if (!canon(x, z) || m.distanceToEdge(x, z) < 32 || nearestStart(x, z) < 80)
                    continue;
                int i = m.idx(x, z);
                if (t.water[i] != 0 || t.roadDist[i] < 22)
                    continue;
                double[] dd = d(x, z);
                boolean all = true;
                for (double v : dd)
                    all &= !Double.isInfinite(v);
                if (all)
                    wildCands.add(new Cand(x, z, dd, argmin(dd), m.distanceToEdge(x, z)));
            }
    }

    // ------------------------------------------------------------------------------------------------- placing

    int addPatch(int x, int z, float value, PKind kind) {
        if (metal.reserved().reserves(x, z) || (p.mirror && metal.reserved().reserves(-x, -z)))
            return -1;   // the caller's reserved ground (the hero jungle's hook) takes no patch, a home or centre one neither
        Patch a = new Patch(x, z, value, kind);
        patches.add(a);
        int o = argmin(d(x, z));
        if (o >= 0) {
            count[o]++;
            if (p.mirror && (x != 0 || z != 0))
                count[p.twin(o)]++;
        }
        int ia = patches.size() - 1;
        nearUpdate(x, z);
        if (p.mirror && (x != 0 || z != 0)) {
            Patch b = new Patch(-x, -z, value, kind);
            patches.add(b);
            a.twin = patches.size() - 1;
            b.twin = ia;
            nearUpdate(-x, -z);
        }
        return ia;
    }

    private void nearUpdate(int x, int z) {
        for (Cand c : cands) {
            double dd = Geo.len(c.x - x, c.z - z);
            if (dd < c.near)
                c.near = dd;
        }
    }

    void addSite(int x, int z, Kind kind) {
        m.sites.add(new MapLayout.Site(x, z, kind));
        if (p.mirror && (x != 0 || z != 0))
            m.sites.add(new MapLayout.Site(-x, -z, kind));
    }

    void addCamp(int x, int z, GenMap.CampKind kind, int team) {
        m.camps.add(new GenMap.Camp(x, z, kind, team));
        if (p.mirror && (x != 0 || z != 0))
            m.camps.add(new GenMap.Camp(-x, -z, kind, team < 0 ? -1 : otherTeam(team)));
    }

    void addVent(int x, int z) {
        m.vents.add(new MapLayout.Spot(x, z, 1));
        if (p.mirror)
            m.vents.add(new MapLayout.Spot(-x, -z, 1));
    }

    private int otherTeam(int team) {
        for (int t2 : p.teams)
            if (t2 != team)
                return t2;
        return team;
    }

    /** Seats placed directly: all of them, or side A's on a mirror map (the copy makes the rest). */
    int seatsToPlace() {
        return p.mirror ? p.n / 2 : p.n;
    }

    private double clearOfSites(double x, double z) {
        double best = Double.MAX_VALUE;
        for (MapLayout.Site s : m.sites)
            best = Math.min(best, Geo.len(s.dx() - x, s.dz() - z));
        for (GenMap.Camp c : m.camps)
            best = Math.min(best, Geo.len(c.dx() - x, c.dz() - z));
        return best;
    }

    private double clearOfPatches(double x, double z) {
        double best = Double.MAX_VALUE;
        for (Patch q : patches)
            if (q.alive)
                best = Math.min(best, Geo.len(q.x - x, q.z - z));
        return best;
    }

    private double clearOfVents(double x, double z) {
        double best = Double.MAX_VALUE;
        for (MapLayout.Spot v : m.vents)
            best = Math.min(best, Geo.len(v.dx() - x, v.dz() - z));
        return best;
    }

    private boolean inJungle(double x, double z, double margin) {
        for (GenMap.Jungle j : m.jungle)
            for (int[] dsc : j.discs())
                if (Geo.len(x - dsc[0], z - dsc[1]) <= dsc[2] + margin)
                    return true;
        return false;
    }

    private boolean siteFree(Cand c) {
        return clearOfSites(c.x, c.z) >= SITE_GAP && clearOfPatches(c.x, c.z) >= 18 && clearOfVents(c.x, c.z) >= 14
            && !inJungle(c.x, c.z, 6) && (!p.mirror || Geo.len(c.x, c.z) * 2 >= SITE_GAP);
    }

    private Kind pick(Kind[] kinds, double[] weights) {
        return kinds[rng.weighted(weights)];
    }

    // ------------------------------------------------------------------------------------------------- the stages

    /** Six patches round every start at 18 (as the pre-made maps' home ring), turned the same way for every seat. */
    private void homeRings() {
        for (int s = 0; s < seatsToPlace(); s++) {
            double sx = p.slotX(s), sz = p.slotZ(s);
            double toCentre = StrictMath.atan2(-sz, -sx);
            for (int k = 0; k < 6; k++) {
                double a = toCentre + Math.PI / 6 + k * Math.PI / 3;
                addPatch((int) Math.round(sx + 18 * StrictMath.cos(a)), (int) Math.round(sz + 18 * StrictMath.sin(a)),
                    STANDARD, PKind.HOME);
            }
        }
    }

    /**
     * The middle: a capture site on the plaza and one 2.0x patch per seat facing it (two with Gold rush) - or, on a
     * Hydra lair map, the lair and no gold ("some maps' middle can spawn hydras ... instead of rich mexes", Oct 20).
     */
    private void centre() {
        if (p.hydra) {
            addCamp(0, 0, GenMap.CampKind.HYDRA, -1);
            List<int[]> pit = new ArrayList<>();
            pit.add(new int[] { 0, 0, 16 });
            m.jungle.add(new GenMap.Jungle(-1, pit, new int[] { 16, 0 }, new int[] { -16, 0 }));
            return;
        }
        addSite(0, 0, pick(new Kind[] { Kind.FORGE, Kind.WATCHTOWER, Kind.SHRINE, Kind.MINE }, new double[] { 3, 2, 2, 2 }));
        boolean gold = p.modifiers.contains(Modifier.GOLD_RUSH);
        for (int s = 0; s < seatsToPlace(); s++) {
            double a = rotOf(s);
            addPatch((int) Math.round(20 * StrictMath.cos(a)), (int) Math.round(20 * StrictMath.sin(a)), CENTRE, PKind.CENTRE);
            if (gold) {   // the second ring at 25: its 7x7 pads still end inside the flat plaza (radius 30)
                double b = a + Math.PI / p.n;
                addPatch((int) Math.round(25 * StrictMath.cos(b)), (int) Math.round(25 * StrictMath.sin(b)), CENTRE,
                    PKind.CENTRE);
            }
        }
    }

    private double rotOf(int s) {
        return StrictMath.atan2(p.slotZ(s), p.slotX(s));
    }

    /** Pairs of neighbouring seats on different teams: the front seams (a 1v1 has two, one per flank). */
    List<int[]> frontPairs() {
        List<int[]> out = new ArrayList<>();
        if (p.n == 2) {
            out.add(new int[] { 0, 1, 1 });
            out.add(new int[] { 0, 1, -1 });
            return out;
        }
        for (int i = 0; i < p.n; i++) {
            int j = (i + 1) % p.n;
            if (p.teams[i] != p.teams[j])
                out.add(new int[] { i, j, 0 });
        }
        return out;
    }

    /**
     * A contested site on every front seam: where both neighbours walk about as far (within 8%), about 60% of the
     * way between them. The kind is rolled (watchtower, camp, garrison, mine, oasis, bell); Garrison ruins make it a
     * garrison.
     */
    private void seamSites() {
        List<int[]> pairs = frontPairs();
        int own = p.mirror ? pairs.size() / 2 : pairs.size();
        boolean garrisons = p.modifiers.contains(Modifier.GARRISON_RUINS);
        for (int k = 0; k < own; k++) {
            int[] pr = pairs.get(k);
            Cand c = seamCandidate(pr, 0.6);
            if (c == null)
                continue;
            Kind kind = garrisons ? Kind.GARRISON : pick(
                new Kind[] { Kind.WATCHTOWER, Kind.CAMP, Kind.GARRISON, Kind.MINE, Kind.OASIS, Kind.BELL },
                new double[] { 3, 2, 2, 1, 1, 1 });
            addSite(c.x, c.z, kind);
        }
    }

    /**
     * The free site candidate on a seam: balanced between the pair (their walks within 8%, relaxed to 14% and 20% when
     * the ground offers nothing closer - a bastion's cliff can push every even spot toward the middle), about
     * {@code along} of the walk between them, nearer to the pair than to any third seat.
     */
    Cand seamCandidate(int[] pr, double along) {
        for (double tol : new double[] { 0.08, 0.14, 0.2 }) {
            Cand c = seamCandidate(pr, along, tol);
            if (c != null)
                return c;
        }
        return null;
    }

    private Cand seamCandidate(int[] pr, double along, double tol) {
        int i = pr[0], j = pr[1];
        double between = g.blocks(dist[i], p.slotX(j), p.slotZ(j));
        if (Double.isInfinite(between))
            between = Geo.len(p.slotX(i) - p.slotX(j), p.slotZ(i) - p.slotZ(j)) * 1.2;
        double want = along * between;
        Cand best = null;
        double bestF = Double.MAX_VALUE;
        for (Cand c : siteCands) {
            if (pr[2] != 0) {
                double cross = c.x * p.axisZ - c.z * p.axisX;
                if (Math.signum(cross) != pr[2])
                    continue;
            }
            double di = c.d[i], dj = c.d[j];
            double bal = Math.abs(di - dj) / (di + dj);
            if (bal > tol || !siteFree(c))
                continue;
            // nobody else is nearer to it than the pair (it sits on their seam, not in a third seat's yard)
            boolean theirs = true;
            for (int s = 0; s < p.n; s++)
                if (s != i && s != j && c.d[s] < Math.min(di, dj))
                    theirs = false;
            if (!theirs)
                continue;
            double f = 4 * bal + Math.abs((di + dj) / 2 - want) / want;
            if (f < bestF) {
                bestF = f;
                best = c;
            }
        }
        return best;
    }

    /**
     * Big games: two more contested sites on the front line (exposure 0.44-0.56 to the nearest enemy), 60+ from any
     * other site - the team boundary is long and the seams alone leave most of it empty.
     */
    private void frontSites() {
        if (p.n <= 2)
            return;
        int want = p.mirror ? 1 : 2;
        for (int k = 0; k < want; k++) {
            Cand best = null;
            double bestF = Double.MAX_VALUE;
            for (Cand c : siteCands) {
                if (!siteFree(c) || clearOfSites(c.x, c.z) < 60)
                    continue;
                double x = exposure(c.d, c.owner);
                if (x < 0.44)
                    continue;
                double f = Math.abs(x - 0.5) + rng.range(0, 0.02);
                if (f < bestF) {
                    bestF = f;
                    best = c;
                }
            }
            if (best == null)
                break;
            addSite(best.x, best.z, pick(new Kind[] { Kind.WATCHTOWER, Kind.CAMP, Kind.GARRISON, Kind.MINE, Kind.SHRINE,
                Kind.OASIS }, new double[] { 2, 2, 2, 2, 1, 1 }));
        }
    }

    /** A warden's sites: on its own forward ground (exposure 0.2-0.42, 60-160 blocks' walk), spread apart. */
    private void ownSites() {
        for (int s = 0; s < seatsToPlace(); s++) {
            int want = p.seat[s].ownSites;
            for (int k = 0; k < want; k++) {
                List<Cand> ok = new ArrayList<>();
                for (Cand c : siteCands) {
                    if (c.owner != s || !siteFree(c))
                        continue;
                    double x = exposure(c.d, s);
                    if (x >= 0.2 && x <= 0.42 && c.d[s] >= 60 && c.d[s] <= 160)
                        ok.add(c);
                }
                if (ok.isEmpty())
                    break;
                ok.sort((a, b) -> Double.compare(clearOfSites(b.x, b.z), clearOfSites(a.x, a.z)));
                Cand c = ok.get(rng.nextInt(Math.min(3, ok.size())));
                addSite(c.x, c.z, pick(new Kind[] { Kind.MINE, Kind.SHRINE, Kind.GARRISON, Kind.WATCHTOWER, Kind.CAMP },
                    new double[] { 3, 3, 2, 1, 1 }));
            }
        }
    }

    /** The modifiers that place objectives (rich vein and gold rush act on the metal instead). */
    private void modifierSites() {
        List<int[]> pairs = frontPairs();
        int own = p.mirror ? pairs.size() / 2 : pairs.size();
        for (Modifier mo : p.modifiers) {
            switch (mo) {
                case LEY_LINE -> {
                    double phi = rng.range(0, Math.PI * 2);
                    for (int sign = 1; sign >= (p.mirror ? 1 : -1); sign -= 2) {
                        double x = sign * 0.55 * p.ring * StrictMath.cos(phi), z = sign * 0.55 * p.ring * StrictMath.sin(phi);
                        Cand c = nearestFree(x, z, 30);
                        if (c != null)
                            addSite(c.x, c.z, Kind.SHRINE);
                    }
                }
                case GEOTHERMAL -> {
                    if (own > 0) {
                        Cand c = seamCandidate(pairs.get(rng.nextInt(own)), 0.5);
                        if (c != null)
                            addSite(c.x, c.z, Kind.VENT);
                    }
                    // a geothermal spot on every side's forward ground (a T2 plant there pays 120 energy/s)
                    for (int s = 0; s < seatsToPlace(); s++) {
                        if (p.n > 2 && s % 2 == 1)
                            continue;   // in team games every second seat: one per pair
                        Cand best = null;
                        for (Cand c : cands) {
                            if (c.owner != s || c.near < 14 || clearOfSites(c.x, c.z) < 14 || inJungle(c.x, c.z, 4))
                                continue;
                            double x = exposure(c.d, s);
                            if (x < 0.3 || x > 0.45)
                                continue;
                            if (best == null || Math.abs(exposure(c.d, s) - 0.38) < Math.abs(exposure(best.d, s) - 0.38))
                                best = c;
                        }
                        if (best != null) {
                            addVent(best.x, best.z);
                            best.used = true;
                        }
                    }
                }
                case MERCENARY_ROAD -> {
                    for (int k = 0; k < own; k++) {
                        Cand c = seamCandidate(pairs.get(k), 0.4);
                        if (c != null)
                            addSite(c.x, c.z, Kind.CAMP);
                    }
                }
                case GARRISON_RUINS -> {
                    for (int k = 0; k < own; k++) {
                        Cand c = seamCandidate(pairs.get(k), 0.75);
                        if (c != null)
                            addSite(c.x, c.z, Kind.GARRISON);
                    }
                }
                case WATCH_RIDGE -> {
                    Cand best = null;
                    for (Cand c : siteCands) {
                        if (!siteFree(c))
                            continue;
                        int s = c.owner;
                        double x = exposure(c.d, s);
                        if (x < 0.35 || x > 0.65)
                            continue;
                        if (best == null || m.heightAt(c.x, c.z) > m.heightAt(best.x, best.z))
                            best = c;
                    }
                    if (best != null)
                        addSite(best.x, best.z, Kind.WATCHTOWER);
                }
                case HERO_RELIC -> {
                    if (own > 0) {
                        Cand c = seamCandidate(pairs.get(rng.nextInt(own)), 0.5);
                        if (c != null)
                            addCamp(c.x, c.z, GenMap.CampKind.RELIC, -1);
                    }
                }
                default -> { }
            }
        }
    }

    private Cand nearestFree(double x, double z, double within) {
        Cand best = null;
        double bd = within;
        for (Cand c : siteCands) {
            double d = Geo.len(c.x - x, c.z - z);
            if (d < bd && siteFree(c)) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    /**
     * The hero jungle's reserved ground (lovish Oct 20/21): one wild area per side (two per side in big team games)
     * between the roads on that side's forward ground, three overlapping discs, an entry and an exit on its edge that
     * face the nearest roads - all rolled per match, so the jungle's start and end differ every game. Two creep camps
     * (data only) inside. The metal fill leaves it alone.
     */
    private void jungle() {
        int perSide = p.n >= 6 ? 2 : 1;
        java.util.Map<Integer, List<Integer>> byTeam = new java.util.TreeMap<>();
        for (int s = 0; s < seatsToPlace(); s++)
            byTeam.computeIfAbsent(p.teams[s], k -> new ArrayList<>()).add(s);
        List<Integer> sideSeats = new ArrayList<>();
        for (List<Integer> l : byTeam.values()) {
            List<Integer> pool = new ArrayList<>(l);
            for (int k = 0; k < perSide && !pool.isEmpty(); k++)
                sideSeats.add(pool.remove(rng.nextInt(pool.size())));
        }
        for (int s : sideSeats) {
            int team = p.teams[s];
            List<Cand> ok = new ArrayList<>();
            for (Cand c : wildCands) {
                if (p.teams[c.owner] != team)
                    continue;
                double x = exposure(c.d, c.owner);
                if (x < 0.25 || x > 0.5 || clearOfSites(c.x, c.z) < 38 || clearOfPatches(c.x, c.z) < 30
                        || inJungle(c.x, c.z, 40))
                    continue;
                if (perSide == 2 && Math.abs(angleDiff(StrictMath.atan2(c.z, c.x), rotOf(s))) > Math.PI / p.n * 1.6)
                    continue;   // two per side: each by its own seat
                ok.add(c);
            }
            if (ok.isEmpty())
                continue;
            Cand c = ok.get(rng.nextInt(ok.size()));
            List<int[]> discs = new ArrayList<>();
            int r0 = (int) rng.range(18, 24);
            discs.add(new int[] { c.x, c.z, r0 });
            for (int k = 0; k < 2; k++) {
                double a = rng.range(0, Math.PI * 2), off = rng.range(14, 20);
                int lx = (int) Math.round(c.x + off * StrictMath.cos(a)), lz = (int) Math.round(c.z + off * StrictMath.sin(a));
                int lr = (int) rng.range(12, 17);
                if (m.distanceToEdge(lx, lz) > lr + 14 && nearestStart(lx, lz) > lr + 60 && clearOfPatches(lx, lz) > lr + 8
                        && clearOfSites(lx, lz) > lr + 14)
                    discs.add(new int[] { lx, lz, lr });
            }
            int[][] doors = doors(discs);
            GenMap.Jungle j = new GenMap.Jungle(team, discs, doors[0], doors[1]);
            m.jungle.add(j);
            // the creep camps: one in each lobe, or in the middle when no lobe fitted
            if (discs.size() == 1)
                addCampRaw(discs.get(0)[0], discs.get(0)[1], team);
            for (int k = 1; k < discs.size(); k++)
                addCampRaw(discs.get(k)[0], discs.get(k)[1], team);
            if (p.mirror) {
                List<int[]> td = new ArrayList<>();
                for (int[] dsc : discs)
                    td.add(new int[] { -dsc[0], -dsc[1], dsc[2] });
                m.jungle.add(new GenMap.Jungle(otherTeam(team), td, new int[] { -doors[0][0], -doors[0][1] },
                    new int[] { -doors[1][0], -doors[1][1] }));
            }
        }
    }

    private void addCampRaw(int x, int z, int team) {
        addCamp(x, z, GenMap.CampKind.CREEP, team);
    }

    /** The jungle's entry and exit: the two edge points nearest a road, at least a quarter turn apart. */
    private int[][] doors(List<int[]> discs) {
        int[] main = discs.get(0);
        double bestA = 0, bestB = Math.PI;
        double bA = Double.MAX_VALUE;
        double[] score = new double[36];
        for (int k = 0; k < 36; k++) {
            double a = k * Math.PI * 2 / 36;
            int x = (int) Math.round(main[0] + (main[2] + 2) * StrictMath.cos(a));
            int z = (int) Math.round(main[1] + (main[2] + 2) * StrictMath.sin(a));
            score[k] = m.inGrid(x, z) ? t.roadDist[m.idx(x, z)] + rng.range(0, 6) : 999;
        }
        int ka = 0;
        for (int k = 0; k < 36; k++)
            if (score[k] < bA) {
                bA = score[k];
                ka = k;
            }
        int kb = (ka + 18) % 36;
        double bB = Double.MAX_VALUE;
        for (int k = 0; k < 36; k++) {
            int dk = Math.min(Math.abs(k - ka), 36 - Math.abs(k - ka));
            if (dk >= 9 && score[k] < bB) {
                bB = score[k];
                kb = k;
            }
        }
        bestA = ka * Math.PI * 2 / 36;
        bestB = kb * Math.PI * 2 / 36;
        return new int[][] {
            { (int) Math.round(main[0] + main[2] * StrictMath.cos(bestA)), (int) Math.round(main[1] + main[2] * StrictMath.sin(bestA)) },
            { (int) Math.round(main[0] + main[2] * StrictMath.cos(bestB)), (int) Math.round(main[1] + main[2] * StrictMath.sin(bestB)) } };
    }

    static double angleDiff(double a, double b) {
        double d = a - b;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    private boolean fillFree(Cand c) {
        return !c.used && c.open && c.near >= FILL_MIN && (!p.mirror || Geo.len(c.x, c.z) * 2 >= FILL_MIN);
    }

    /** Every seat's natural pair (maps spec R5, BAR's first expansion): 42-66 blocks' walk out, on its safest side. */
    private void naturals() {
        for (int s = 0; s < seatsToPlace(); s++) {
            Cand best = null;
            double bestF = Double.MAX_VALUE;
            for (Cand c : cands) {
                if (c.owner != s || c.d[s] < 42 || c.d[s] > 66 || !fillFree(c))
                    continue;
                double f = exposure(c.d, s) + rng.range(0, 0.03);
                if (f < bestF) {
                    bestF = f;
                    best = c;
                }
            }
            if (best == null)
                continue;
            best.used = true;
            addPatch(best.x, best.z, STANDARD, PKind.NATURAL);
            Cand second = null;
            for (Cand c : cands) {
                double dd = Geo.len(c.x - best.x, c.z - best.z);
                if (c.owner == s && !c.used && dd >= 9 && dd <= 13 && c.d[s] <= 70 && clearOfSites(c.x, c.z) >= 18
                        && !inJungle(c.x, c.z, 4) && clearOfOthers(c, best)) {
                    second = c;
                    break;
                }
            }
            if (second != null) {
                second.used = true;
                addPatch(second.x, second.z, STANDARD, PKind.NATURAL);
            }
        }
    }

    private boolean clearOfOthers(Cand c, Cand pairWith) {
        for (Patch q : patches)
            if (q.alive && !(q.x == pairWith.x && q.z == pairWith.z) && Geo.len(q.x - c.x, q.z - c.z) < FILL_MIN)
                return false;
        return true;
    }

    /**
     * The open-ground fill (maps spec R4 on a natural map): the reachable candidate farthest from every patch goes in,
     * then the next, while any lies farther than its owner's hole target (the archetype's; the rim band 25% denser, the
     * lobby's richness scales it), none closer than FILL_MIN to another patch.
     */
    private void fill() {
        double rich = Math.sqrt(Math.max(0.3, metal.richness()));
        int[] cap = new int[p.n];
        for (int s = 0; s < p.n; s++)
            cap[s] = capOf(s, rich);
        for (int guard = 0; guard < 40 * p.n; guard++) {
            Cand best = null;
            double bestS = 1.0;
            for (Cand c : cands) {
                if (count[c.owner] >= cap[c.owner] || !fillFree(c))
                    continue;
                double target = p.seat[c.owner].holeTarget / rich * (c.edge <= 36 ? 0.8 : 1.0);
                double sc = c.near / target;
                if (sc > bestS) {
                    bestS = sc;
                    best = c;
                }
            }
            if (best == null)
                break;
            best.used = true;
            addPatch(best.x, best.z, STANDARD, PKind.FILL);
        }
    }

    /**
     * Patches a seat may own in all (home ring and centre included): the pre-made maps' counts (20-24 a seat in a 1v1,
     * 10-23 in a 4v4, design/maps.md / maps spec 1.2) scaled by the archetype's density and the lobby's richness.
     * In team games the archetype's density counts at its square root: at full strength a 4v4 Prospector side drew 19
     * patches a seat to the Warden side's 12, a 22% seat spread before balancing (Phase 1), and the balancer then had
     * to undo most of it; at the root (17 to 13) the raw spread is 17%, the balancer needs half the edits and the map
     * stays even under more of the weights' range (38% of draws within 5% against 32%).
     */
    int capOf(int s, double rich) {
        int base = p.n <= 2 ? 28 : p.n <= 4 ? 20 : 15;
        double density = Archetype.PLAIN.holeTarget / p.seat[s].holeTarget;
        return (int) Math.round(base * rich * (p.n <= 2 ? density : Math.sqrt(density)));
    }

    /**
     * Values by where a patch lies for its owner: rich on the front line (exposure 0.38-0.62, "more map control = more
     * advantages"), lean out behind the base, standard elsewhere; Gold rush makes the whole contested band rich.
     */
    private void values() {
        boolean gold = p.modifiers.contains(Modifier.GOLD_RUSH);
        for (int k = 0; k < patches.size(); k++) {
            Patch q = patches.get(k);
            if (q.kind != PKind.FILL || (p.mirror && q.twin >= 0 && q.twin < k))
                continue;
            double[] dd = d(q.x, q.z);
            int o = argmin(dd);
            if (o < 0)
                continue;
            double x = exposure(dd, o);
            float v = STANDARD;
            double lo = gold ? 0.33 : 0.38, hi = gold ? 0.67 : 0.62;
            if (x >= lo && x <= hi)
                v = RICH;
            else if (x < 0.3 && Geo.len(q.x, q.z) > p.ring + 20)
                v = LEAN;
            q.value = v;
            if (q.twin >= 0)
                patches.get(q.twin).value = v;
        }
    }

    /**
     * Rich vein: five rich patches 13 apart across one seat's forward ground (exposure 0.28-0.42), crowding fill
     * patches within 10 of it removed. The side is rolled (the balancer evens out who got it).
     */
    private void vein() {
        int s = rng.nextInt(seatsToPlace());
        Cand c0 = null;
        for (Cand c : cands) {
            if (c.owner != s || c.used || inJungle(c.x, c.z, 4) || clearOfSites(c.x, c.z) < 18)
                continue;
            double x = exposure(c.d, s);
            if (x >= 0.28 && x <= 0.42 && (c0 == null || rng.chance(0.08)))
                c0 = c;
        }
        if (c0 == null)
            return;
        double sx = p.slotX(s), sz = p.slotZ(s);
        double dx = c0.x - sx, dz = c0.z - sz, dl = Geo.len(dx, dz);
        double qx = -dz / dl, qz = dx / dl;
        for (int k = -2; k <= 2; k++) {
            double tx = c0.x + qx * 13 * k, tz = c0.z + qz * 13 * k;
            Cand pick = null;
            double bd = 4.5;
            for (Cand c : cands) {
                double dd = Geo.len(c.x - tx, c.z - tz);
                if (dd < bd && c.owner == s && !inJungle(c.x, c.z, 4) && clearOfSites(c.x, c.z) >= 18
                        && clearOfVents(c.x, c.z) >= 12) {
                    bd = dd;
                    pick = c;
                }
            }
            if (pick == null)
                continue;
            for (Patch q : patches)
                if (q.alive && q.kind == PKind.FILL && Geo.len(q.x - pick.x, q.z - pick.z) < 10) {
                    q.alive = false;
                    if (q.twin >= 0)
                        patches.get(q.twin).alive = false;
                }
            boolean crowded = false;
            for (Patch q : patches)
                if (q.alive && Geo.len(q.x - pick.x, q.z - pick.z) < 9)
                    crowded = true;
            if (crowded)
                continue;
            pick.used = true;
            addPatch(pick.x, pick.z, RICH, PKind.VEIN);
        }
    }

    // ------------------------------------------------------------------------------------------------- forest

    /**
     * Groves where a seeded noise is high, never where they would block play: off bases (70), roads (+3), the plaza,
     * patch pads (+4), sites (10), camps' centres, vents, water and steep ground. The jungle grows thick. A grove
     * column carries a tree one time in 22 (TREE_DENSITY, as the pre-made maps), a jungle column one in 14.
     */
    private void forest() {
        GenNoise fn = new GenNoise(GenRng.stream(p.draw, "forest").nextLong());
        keepOut = new boolean[m.width * m.width];
        for (Patch q : patches)
            if (q.alive)
                stamp(q.x, q.z, MapLayout.PAD + 4);
        for (MapLayout.Site s : m.sites)
            stamp(s.dx(), s.dz(), 10);
        for (GenMap.Camp c : m.camps)
            stamp(c.dx(), c.dz(), 6);
        for (MapLayout.Spot v : m.vents)
            stamp(v.dx(), v.dz(), 5);
        for (GenMap.Jungle j : m.jungle) {
            stamp(j.entry()[0], j.entry()[1], 4);
            stamp(j.exit()[0], j.exit()[1], 4);
        }
        // masks first: the per-column test then costs a few array reads, and the noise only where a grove may grow
        boolean[] wild = new boolean[m.width * m.width];
        for (GenMap.Jungle j : m.jungle)
            for (int[] dsc : j.discs())
                disc(wild, dsc[0], dsc[1], dsc[2]);
        for (int s = 0; s < p.n; s++)
            discKeep((int) p.slotX(s), (int) p.slotZ(s), 70);
        discKeep(0, 0, (int) GenPlan.PLAZA + 10);
        byte cliffS = (byte) MapLayout.Surface.CLIFF.ordinal(), sandS = (byte) MapLayout.Surface.SAND.ordinal();
        for (int x = -m.half; x <= m.half; x++)
            for (int z = -m.half; z <= m.half; z++) {
                int i = m.idx(x, z);
                if (keepOut[i] || t.water[i] != 0 || t.roadDist[i] < GenTerrain.ROAD_HALF + 3)
                    continue;
                if (m.surface[i] == cliffS || m.surface[i] == sandS || m.distanceToEdge(x, z) < 12)
                    continue;
                double sx = x, sz = z;
                if (p.mirror && !canon(x, z)) {
                    sx = -x;
                    sz = -z;
                }
                boolean jungleHere = wild[i];
                double f = fn.fbm(sx, sz, 70, 3, 0.5);
                if (f < (jungleHere ? -0.35 : 0.2))
                    continue;
                m.forest.set(i);
                long h = GenRng.mix(p.draw * 31 + (long) Math.round(sx) * 73856093L + (long) Math.round(sz) * 19349663L);
                double u = (h >>> 11) * 0x1.0p-53;
                if (u < (jungleHere ? 0.07 : TREE_DENSITY))
                    m.tree.set(i);
            }
    }

    private void disc(boolean[] mask, int cx, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++)
                if (m.inGrid(x, z) && (x - cx) * (x - cx) + (z - cz) * (z - cz) <= r * r)
                    mask[m.idx(x, z)] = true;
    }

    private void discKeep(int cx, int cz, int r) {
        disc(keepOut, cx, cz, r);
    }

    static final double TREE_DENSITY = 0.045;

    private boolean[] keepOut;

    private void stamp(int cx, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++)
                if (m.inGrid(x, z))
                    keepOut[m.idx(x, z)] = true;
    }



    // ------------------------------------------------------------------------------------------------- output

    /** The patches as MapLayout records, owned by the seat nearest by walk (the final, tree-aware distances). */
    void write() {
        m.mexes.clear();
        for (Patch q : patches)
            if (q.alive)
                m.mexes.add(new MapLayout.Mex(q.x, q.z, q.value, Math.max(0, argmin(d(q.x, q.z)))));
    }

    void writeWith(WalkGrid fin, int[][] fd) {
        m.mexes.clear();
        for (Patch q : patches) {
            if (!q.alive)
                continue;
            double[] dd = new double[p.n];
            for (int s = 0; s < p.n; s++)
                dd[s] = fin.blocks(fd[s], q.x, q.z);
            int o = argmin(dd);
            if (o < 0)
                continue;
            m.mexes.add(new MapLayout.Mex(q.x, q.z, q.value, o));
        }
    }
}
