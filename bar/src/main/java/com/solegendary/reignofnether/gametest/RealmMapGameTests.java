package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant;
import com.solegendary.reignofnether.maps.MapBuilder;
import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.EconomyPace;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The realm maps (design/maps.md 4.7 Soulfire Deltas, 4.8 Chorus Isles) and the Forsaken Garrison (section 3). Own
 * holder, same arena template as SkirmishGameTests; the layout tests are pure maths (MapLayout has no world access),
 * the world tests stay inside their own arena's footprint, high above it, and drive only their own capture site
 * (CapturePointServerEvents.tickSite), so they run side by side with every other test.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class RealmMapGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final MapPreset[] REALMS = { MapPreset.SOULFIRE_DELTAS, MapPreset.CHORUS_ISLES };
    static final int[] COUNTS = { 2, 3, 4, 6, 8, 16 };
    /** MapBuilder.GROVE_DENSITY: the share of grove columns that grow a plant (a stem the flood cannot cross). */
    static final double GROVE_DENSITY = 0.03;

    /** Teams for n slots as the lobby seats them: the first half team 0, the rest team 1, teammates adjacent. */
    static int[] teams(int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++)
            t[i] = i < (n + 1) / 2 ? 0 : 1;
        return t;
    }

    static String fail(List<String> problems) {
        return problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size())));
    }

    // ------------------------------------------------------------------------------------------------ fairness

    /**
     * Fair for every supported player count: every slot owns the same metal (so equal teams own equal metal), every
     * start stands the same distance from the centre and from the next start, on the same ground height, inside its
     * team's box and on ground a capitol can stand on; every start has its nearest Forsaken Garrison at the same
     * distance, never closer than 60 blocks (outside every home terrace); Soulfire Deltas gives every start two
     * private geothermal vents and the same distance to its nearest contested one.
     */
    @GameTest(template = ARENA)
    public static void realm_maps_are_fair_for_every_player_count(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        MapLayout.Sample sample = new MapLayout.Sample();
        for (MapPreset preset : REALMS)
            for (int n : COUNTS) {
                MapLayout l = MapLayout.build(preset, teams(n));
                String tag = preset + " n=" + n + ": ";
                float[] slotValue = new float[n];
                for (MapLayout.Mex m : l.mexes)
                    slotValue[l.slotAt(m.dx(), m.dz())] += m.value();
                float lo = Float.MAX_VALUE, hi = 0, team0 = 0, team1 = 0;
                for (int s = 0; s < n; s++) {
                    lo = Math.min(lo, slotValue[s]);
                    hi = Math.max(hi, slotValue[s]);
                    if (l.slots.get(s).team() == 0)
                        team0 += slotValue[s];
                    else
                        team1 += slotValue[s];
                }
                if (lo <= 0 || hi - lo > 1e-3f)
                    problems.add(tag + "slot metal " + lo + ".." + hi);
                if (n % 2 == 0 && Math.abs(team0 - team1) > 1e-3f)
                    problems.add(tag + "team metal " + team0 + " vs " + team1);
                double cLo = Double.MAX_VALUE, cHi = 0, nLo = Double.MAX_VALUE, nHi = 0, gLo = Double.MAX_VALUE, gHi = 0;
                double vLo = Double.MAX_VALUE, vHi = 0;
                Set<Integer> heights = new HashSet<>();
                for (int i = 0; i < n; i++) {
                    MapLayout.Slot s = l.slots.get(i), next = l.slots.get((i + 1) % n);
                    double c = Math.hypot(s.dx(), s.dz()), nb = Math.hypot(s.dx() - next.dx(), s.dz() - next.dz());
                    cLo = Math.min(cLo, c);
                    cHi = Math.max(cHi, c);
                    nLo = Math.min(nLo, nb);
                    nHi = Math.max(nHi, nb);
                    l.sample(s.dx(), s.dz(), sample);
                    heights.add(sample.height);
                    if (!l.boxOf(s.team()).contains(s.dx(), s.dz()))
                        problems.add(tag + "slot " + i + " outside its box");
                    if (!l.startGround(s.dx(), s.dz()))
                        problems.add(tag + "slot " + i + " not on start ground");
                    double g = nearest(l.sites, s, CapturePointServerEvents.Kind.GARRISON);
                    gLo = Math.min(gLo, g);
                    gHi = Math.max(gHi, g);
                    if (preset == MapPreset.SOULFIRE_DELTAS) {
                        int home = 0;
                        double contested = Double.MAX_VALUE;
                        for (MapLayout.Spot v : l.vents) {
                            double d = Math.hypot(v.dx() - s.dx(), v.dz() - s.dz());
                            if (d < 40)
                                home++;
                            else
                                contested = Math.min(contested, d);
                        }
                        if (home != 2)
                            problems.add(tag + "slot " + i + " has " + home + " private vents, not 2");
                        vLo = Math.min(vLo, contested);
                        vHi = Math.max(vHi, contested);
                    }
                }
                if (cHi - cLo > 2 || nHi - nLo > 2)
                    problems.add(tag + "start distances: centre " + (int) cLo + ".." + (int) cHi + ", next " + (int) nLo + ".." + (int) nHi);
                if (heights.size() != 1)
                    problems.add(tag + "start heights " + heights);
                if (gHi - gLo > 2 || gLo < 60)
                    problems.add(tag + "nearest garrison " + (int) gLo + ".." + (int) gHi + " (equal, and at least 60)");
                if (preset == MapPreset.SOULFIRE_DELTAS && vHi - vLo > 2)
                    problems.add(tag + "nearest contested vent " + (int) vLo + ".." + (int) vHi);
                int garrisons = 0;
                for (MapLayout.Site st : l.sites)
                    if (st.kind() == CapturePointServerEvents.Kind.GARRISON)
                        garrisons++;
                if (garrisons != n)
                    problems.add(tag + garrisons + " garrisons, expected one per boundary (" + n + ")");
            }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(fail(problems));
    }

    static double nearest(List<MapLayout.Site> sites, MapLayout.Slot s, CapturePointServerEvents.Kind kind) {
        double best = Double.MAX_VALUE;
        for (MapLayout.Site st : sites)
            if (st.kind() == kind)
                best = Math.min(best, Math.hypot(st.dx() - s.dx(), st.dz() - s.dz()));
        return best;
    }

    // ------------------------------------------------------------------------------------------------ ground paths

    /**
     * The map as a ground unit sees it: every column of the layout with its height, blocked where a ground unit
     * cannot stand (void, lava, deep water, a guard rail, a grove stem, a pillar, a vent pool, a site's posts) or
     * outside the wall. Steps of more than one block are not taken (a unit can climb one, and a path must work back).
     */
    static final class Ground {
        final MapLayout l;
        final int R, W;
        final int[] h;
        final MapLayout.Surface[] surf;
        final int[] fluid;
        final boolean[] rail, blocked;

        Ground(MapLayout l) {
            this.l = l;
            R = l.radius + 2;
            W = 2 * R + 1;
            h = new int[W * W];
            fluid = new int[W * W];
            surf = new MapLayout.Surface[W * W];
            rail = new boolean[W * W];
            blocked = new boolean[W * W];
            boolean[] stem = new boolean[W * W];
            MapLayout.Sample s = new MapLayout.Sample();
            double inside = (double) (l.radius - 3) * (l.radius - 3);
            for (int x = -R; x <= R; x++)
                for (int z = -R; z <= R; z++) {
                    int i = idx(x, z);
                    l.sample(x, z, s);
                    h[i] = s.height;
                    surf[i] = s.surface;
                    fluid[i] = s.fluidTop;
                    stem[i] = s.forest && l.decorHash(s.u, s.v) < GROVE_DENSITY;
                    blocked[i] = x * x + z * z > inside;
                }
            for (int x = -R + 1; x < R; x++)
                for (int z = -R + 1; z < R; z++) {
                    int i = idx(x, z);
                    boolean voidNb = false;
                    for (int a = -1; a <= 1 && !voidNb; a++)
                        for (int b = -1; b <= 1; b++)
                            if ((a != 0 || b != 0) && surf[idx(x + a, z + b)] == MapLayout.Surface.VOID) {
                                voidNb = true;
                                break;
                            }
                    rail[i] = l.railAt(surf[i], voidNb);
                }
            for (int i = 0; i < W * W; i++) {
                MapLayout.Surface su = surf[i];
                if (su == MapLayout.Surface.VOID || su == MapLayout.Surface.LAVA || rail[i] || stem[i]
                        || (su == MapLayout.Surface.WATER && h[i] < -1))
                    blocked[i] = true;
            }
            for (MapLayout.Spot p : l.pillars)
                block(p.dx(), p.dz(), 1);
            for (MapLayout.Spot p : l.vents)
                block(p.dx(), p.dz(), 1);   // the pool; its magma rim is walkable (if unpleasant)
            for (MapLayout.Site st : l.sites) {
                block(st.dx(), st.dz(), 0);
                for (int[] c : new int[][] { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 } })
                    block(st.dx() + c[0], st.dz() + c[1], 0);
            }
        }

        void block(int x, int z, int r) {
            for (int a = -r; a <= r; a++)
                for (int b = -r; b <= r; b++)
                    if (Math.abs(x + a) <= R && Math.abs(z + b) <= R)
                        blocked[idx(x + a, z + b)] = true;
        }

        int idx(int x, int z) {
            return (x + R) * W + (z + R);
        }

        boolean[] flood(int sx, int sz) {
            boolean[] seen = new boolean[W * W];
            int start = idx(sx, sz);
            if (blocked[start])
                return seen;
            ArrayDeque<Integer> q = new ArrayDeque<>();
            seen[start] = true;
            q.add(start);
            int[] dx = { 1, -1, 0, 0 }, dz = { 0, 0, 1, -1 };
            while (!q.isEmpty()) {
                int c = q.poll();
                int cx = c / W - R, cz = c % W - R;
                for (int d = 0; d < 4; d++) {
                    int nx = cx + dx[d], nz = cz + dz[d];
                    if (Math.abs(nx) > R || Math.abs(nz) > R)
                        continue;
                    int ni = idx(nx, nz);
                    if (!seen[ni] && !blocked[ni] && Math.abs(h[ni] - h[c]) <= 1) {
                        seen[ni] = true;
                        q.add(ni);
                    }
                }
            }
            return seen;
        }

        boolean reachedNear(boolean[] seen, int x, int z, int r) {
            for (int a = -r; a <= r; a++)
                for (int b = -r; b <= r; b++)
                    if (Math.abs(x + a) <= R && Math.abs(z + b) <= R && seen[idx(x + a, z + b)])
                        return true;
            return false;
        }

        /** Chebyshev distance (up to max + 1) from (x, z) to the nearest void, lava, deep water or rail column. */
        int clearance(int x, int z, int max) {
            for (int d = 0; d <= max; d++)
                for (int a = -d; a <= d; a++)
                    for (int b = -d; b <= d; b++) {
                        if (Math.max(Math.abs(a), Math.abs(b)) != d)
                            continue;
                        if (Math.abs(x + a) > R || Math.abs(z + b) > R)
                            return d;
                        int i = idx(x + a, z + b);
                        MapLayout.Surface su = surf[i];
                        if (su == MapLayout.Surface.VOID || su == MapLayout.Surface.LAVA || rail[i]
                                || (su == MapLayout.Surface.WATER && h[i] < -1))
                            return d;
                    }
            return max + 1;
        }
    }

    /**
     * Ground units can reach everything they must on both realm maps, for every player count: from slot 0's start
     * the flood reaches every other start, every metal patch, every capture site and every geothermal vent. Patches
     * keep 5 blocks from any void, lava or guard rail (their pad clears 3 round them: a rail inside it would open a
     * gap), sites 4 and vents 3. And a lava lake's surface lies two below every bank, so a Geothermal Plant fits only
     * over a vent's pool, never over the edge of a lake (the vents are the geothermal spots, BAR-style).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void realm_maps_connect_every_start_by_ground(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (MapPreset preset : REALMS)
            for (int n : COUNTS) {
                MapLayout l = MapLayout.build(preset, teams(n));
                Ground g = new Ground(l);
                String tag = preset + " n=" + n + ": ";
                MapLayout.Slot s0 = l.slots.get(0);
                boolean[] seen = g.flood(s0.dx(), s0.dz());
                for (MapLayout.Slot s : l.slots)
                    if (!seen[g.idx(s.dx(), s.dz())])
                        problems.add(tag + "start " + s.index() + " unreachable from start 0");
                for (MapLayout.Mex m : l.mexes) {
                    if (!g.reachedNear(seen, m.dx(), m.dz(), 1))
                        problems.add(tag + "patch unreachable at " + m.dx() + "," + m.dz());
                    if (g.clearance(m.dx(), m.dz(), 5) < 5)
                        problems.add(tag + "patch at " + m.dx() + "," + m.dz() + " too near void/lava/rail");
                }
                for (MapLayout.Site st : l.sites) {
                    if (!g.reachedNear(seen, st.dx(), st.dz(), 3))
                        problems.add(tag + st.kind() + " unreachable at " + st.dx() + "," + st.dz());
                    if (g.clearance(st.dx(), st.dz(), 4) < 4)
                        problems.add(tag + st.kind() + " at " + st.dx() + "," + st.dz() + " too near void/lava/rail");
                }
                for (MapLayout.Spot v : l.vents) {
                    if (!g.reachedNear(seen, v.dx(), v.dz(), 3))
                        problems.add(tag + "vent unreachable at " + v.dx() + "," + v.dz());
                    if (g.clearance(v.dx(), v.dz(), 3) < 3)
                        problems.add(tag + "vent at " + v.dx() + "," + v.dz() + " too near lava");
                }
                // lava lakes: every bank column stands at least two above the lava next to it
                int[] ox = { 1, -1, 0, 0 }, oz = { 0, 0, 1, -1 };
                for (int x = -g.R + 1; x < g.R; x++)
                    for (int z = -g.R + 1; z < g.R; z++) {
                        int i = g.idx(x, z);
                        if (g.surf[i] != MapLayout.Surface.LAVA)
                            continue;
                        for (int d = 0; d < 4; d++) {
                            int j = g.idx(x + ox[d], z + oz[d]);
                            if (g.surf[j] != MapLayout.Surface.LAVA && g.h[j] < g.fluid[i] + 2) {
                                problems.add(tag + "lake bank at " + (x + ox[d]) + "," + (z + oz[d]) + " only "
                                    + (g.h[j] - g.fluid[i]) + " above the lava");
                                x = g.R;   // one report per map is enough
                                break;
                            }
                        }
                    }
            }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(fail(problems));
    }

    // ------------------------------------------------------------------------------------------------ the builder

    /** Where a window of a realm map is raised for the builder test: a point of the map (relative to its centre). */
    record Window(String what, int px, int pz) { }

    /**
     * MapBuilder raises both realm maps without errors inside this test's arena: 16x16 windows of each (a lake shore
     * with its vent, a garrison islet, a grove, a private vent; a bridge, a garrison islet, a chorus islet, a neck),
     * stacked high above the arena's own footprint. Every column then shows the palette block its surface asks for
     * (soul sand in a valley, lava under a lake, the void's black floor, purpur on a bridge), every rail column of
     * Chorus Isles carries its end-stone wall, groves grew, and a Geothermal Plant is accepted over a stamped vent
     * but refused over the edge of a lava lake ("must be placed over lava": the lakes are not geothermal spots).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void realm_maps_raise_in_their_arena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int x0 = origin.getX(), z0 = origin.getZ();
        List<String> problems = new ArrayList<>();
        // six windows, 32 apart from y 120: each spans 18 under its base (the void's floor and fill) to 14 over it
        // (pillars, fungi), so none reaches the next, and all stay clear of the terrain and the build limit
        int baseY = 120 - 32;
        int groves = 0, rails = 0;
        boolean ventAccepted = false, lakeRefused = false;
        for (MapPreset preset : REALMS) {
            MapLayout l = MapLayout.build(preset, teams(4));
            MapBuilder.Palette pal = MapBuilder.paletteFor(preset);
            for (Window w : windows(l)) {
                baseY += 32;
                if (baseY + 14 >= level.getMaxBuildHeight()) {
                    problems.add("ran out of height for " + w.what());
                    break;
                }
                // a slab under the window gives the heightmap a floor near the base, so the builder fills only a
                // shallow band instead of every column down to the arena
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        level.setBlock(new BlockPos(x0 + x, baseY - 10, z0 + z), Blocks.STONE.defaultBlockState(), 2);
                int cx = x0 + 8 - w.px(), cz = z0 + 8 - w.pz();
                String tag = preset + " " + w.what() + ": ";
                List<BlockPos> grown;
                try {
                    grown = MapBuilder.buildWindow(level, l, cx, cz, baseY, x0, z0, 16, 16);
                } catch (Exception e) {
                    problems.add(tag + "the builder threw " + e);
                    continue;
                }
                groves += grown.size();
                rails += checkWindow(level, l, pal, cx, cz, baseY, x0, z0, grown, tag, problems);
                // geothermal: over a vent stamped in this window, and refused at a lake's edge in it
                for (MapLayout.Spot v : l.vents) {
                    int vx = cx + v.dx(), vz = cz + v.dz();
                    if (vx - 2 < x0 || vx + 2 >= x0 + 16 || vz - 2 < z0 || vz + 2 >= z0 + 16)
                        continue;
                    int g = topNonAir(level, vx, vz, baseY + 8, baseY - 8);
                    if (!GeothermalPlant.isLava(level.getBlockState(new BlockPos(vx, g, vz))))
                        problems.add(tag + "the vent at " + vx + "," + vz + " has no lava pool");
                    String err = BuildingValidators.getPlacementValidityError(level, Buildings.GEOTHERMAL_PLANT_VILLAGERS,
                        new BlockPos(vx - 2, g, vz - 2), "gametest_realm_geo", Rotation.NONE, false, true, true);
                    if (err != null)
                        problems.add(tag + "Geothermal Plant refused over the vent: " + err);
                    else
                        ventAccepted = true;
                }
                BlockPos edge = lakeEdge(l, cx, cz, x0, z0);
                if (edge != null) {
                    int bank = baseY + bankHeight(l, edge.getX() - cx, edge.getZ() - cz);
                    String err = BuildingValidators.getPlacementValidityError(level, Buildings.GEOTHERMAL_PLANT_VILLAGERS,
                        new BlockPos(edge.getX(), bank, edge.getZ()), "gametest_realm_geo", Rotation.NONE, false, true, true);
                    if (err == null)
                        problems.add(tag + "a Geothermal Plant was accepted over the edge of a lava lake");
                    else
                        lakeRefused = true;
                }
            }
        }
        if (groves == 0)
            problems.add("no grove grew in any window");
        if (rails == 0)
            problems.add("no guard rail in any Chorus Isles window");
        if (!ventAccepted)
            problems.add("no window held a whole vent to test a Geothermal Plant on");
        if (!lakeRefused)
            problems.add("no window held a lake edge to refuse a Geothermal Plant on");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(fail(problems));
    }

    /**
     * The windows each realm map is raised in: map points (relative to its centre) chosen from the layout itself, so
     * they follow the map if its numbers change.
     */
    static List<Window> windows(MapLayout l) {
        List<Window> out = new ArrayList<>();
        MapLayout.Slot s0 = l.slots.get(0);
        // slot 0's nearest garrison
        MapLayout.Site garrison = null;
        for (MapLayout.Site st : l.sites)
            if (st.kind() == CapturePointServerEvents.Kind.GARRISON && (garrison == null
                    || Math.hypot(st.dx() - s0.dx(), st.dz() - s0.dz()) < Math.hypot(garrison.dx() - s0.dx(), garrison.dz() - s0.dz())))
                garrison = st;
        if (l.preset == MapPreset.SOULFIRE_DELTAS) {
            // the first 2n vents are the private ones (two per slot), then the lakes' inner shores: the window sits
            // half way from that vent to the nearest lava, so it holds the whole vent and the lake's edge
            MapLayout.Spot shore = l.vents.get(2 * l.n);
            int[] lava = nearestLava(l, shore.dx(), shore.dz());
            out.add(new Window("lake shore", (shore.dx() + lava[0]) / 2, (shore.dz() + lava[1]) / 2));
            if (garrison != null)
                out.add(new Window("garrison islet", garrison.dx() + 3, garrison.dz() + 3));
            int[] grove = densestGrove(l);
            out.add(new Window("fungus grove", grove[0], grove[1]));
        } else {
            if (garrison != null) {
                // half way along the bridge from slot 0's start to that islet, and the islet's edge
                out.add(new Window("bridge", (s0.dx() + garrison.dx()) / 2, (s0.dz() + garrison.dz()) / 2));
                out.add(new Window("garrison islet", garrison.dx() + 6, garrison.dz()));
            }
            int[] grove = densestGrove(l);
            out.add(new Window("chorus islet", grove[0], grove[1]));
        }
        return out;
    }

    /** The lava column nearest (x, z), searched out to 24 blocks; (x, z) itself when there is none. */
    static int[] nearestLava(MapLayout l, int x, int z) {
        MapLayout.Sample s = new MapLayout.Sample();
        for (int r = 1; r <= 24; r++)
            for (int a = -r; a <= r; a++)
                for (int b = -r; b <= r; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != r)
                        continue;
                    l.sample(x + a, z + b, s);
                    if (s.surface == MapLayout.Surface.LAVA)
                        return new int[] { x + a, z + b };
                }
        return new int[] { x, z };
    }

    /**
     * The map point with the most grove plants round it: candidates every 8 blocks over slot 0's wedge, each counted
     * on a 2-block grid of its 12x12 square - a few tens of thousands of samples, not millions.
     */
    static int[] densestGrove(MapLayout l) {
        MapLayout.Sample s = new MapLayout.Sample();
        int best = -1;
        int[] at = { 0, 0 };
        double max = (double) (l.radius - 12) * (l.radius - 12);
        for (int x = -l.radius; x <= l.radius; x += 8)
            for (int z = -l.radius; z <= l.radius; z += 8) {
                if (x * x + z * z > max || l.slotAt(x, z) != 0)
                    continue;
                int count = 0;
                for (int a = -6; a < 6; a += 2)
                    for (int b = -6; b < 6; b += 2) {
                        l.sample(x + a, z + b, s);
                        if (s.forest)
                            count++;
                    }
                if (count > best) {
                    best = count;
                    at = new int[] { x, z };
                }
            }
        return at;
    }

    /**
     * The origin (x, z) of a 5x5 footprint inside the window whose centre is over a lava lake and at least one corner
     * on its bank: where a player would try to push a Geothermal Plant out over a lake's edge. Null if none.
     */
    static BlockPos lakeEdge(MapLayout l, int cx, int cz, int x0, int z0) {
        MapLayout.Sample s = new MapLayout.Sample(), t = new MapLayout.Sample();
        for (int x = x0; x <= x0 + 11; x++)
            for (int z = z0; z <= z0 + 11; z++) {
                l.sample(x + 2 - cx, z + 2 - cz, s);
                if (s.surface != MapLayout.Surface.LAVA)
                    continue;
                for (int[] c : new int[][] { { 0, 0 }, { 4, 0 }, { 0, 4 }, { 4, 4 } }) {
                    l.sample(x + c[0] - cx, z + c[1] - cz, t);
                    if (t.surface != MapLayout.Surface.LAVA)
                        return new BlockPos(x, 0, z);
                }
            }
        return null;
    }

    /** The highest non-lava ground height round a footprint corner: where the plant's foundation would sit. */
    static int bankHeight(MapLayout l, int dx, int dz) {
        MapLayout.Sample s = new MapLayout.Sample();
        int best = Integer.MIN_VALUE;
        for (int a = 0; a <= 4; a++)
            for (int b = 0; b <= 4; b++) {
                l.sample(dx + a, dz + b, s);
                if (s.surface != MapLayout.Surface.LAVA)
                    best = Math.max(best, s.height);
            }
        return best == Integer.MIN_VALUE ? 0 : best;
    }

    static int topNonAir(ServerLevel level, int x, int z, int from, int to) {
        for (int y = from; y >= to; y--)
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir())
                return y;
        return to;
    }

    /**
     * Every column of a raised window against its layout: the surface block (or the cliff block on a steep column),
     * lava over a lake bed, the void's floor, a rail where MapLayout.railAt puts one. Columns a grove, vent or pillar
     * rebuilt are skipped. Returns the rails seen.
     */
    static int checkWindow(ServerLevel level, MapLayout l, MapBuilder.Palette pal, int cx, int cz, int baseY, int x0,
                           int z0, List<BlockPos> grown, String tag, List<String> problems) {
        MapLayout.Sample s = new MapLayout.Sample(), t = new MapLayout.Sample();
        Set<Long> skip = new HashSet<>();
        for (BlockPos g : grown)
            skip.add(BlockPos.asLong(g.getX(), 0, g.getZ()));
        for (MapLayout.Spot v : l.vents)
            for (int a = -2; a <= 2; a++)
                for (int b = -2; b <= 2; b++)
                    skip.add(BlockPos.asLong(cx + v.dx() + a, 0, cz + v.dz() + b));
        for (MapLayout.Spot p : l.pillars)
            for (int a = -1; a <= 1; a++)
                for (int b = -1; b <= 1; b++)
                    skip.add(BlockPos.asLong(cx + p.dx() + a, 0, cz + p.dz() + b));
        int rails = 0, wrong = 0;
        String firstWrong = null;
        for (int x = x0; x < x0 + 16; x++)
            for (int z = z0; z < z0 + 16; z++) {
                int dx = x - cx, dz = z - cz;
                if (skip.contains(BlockPos.asLong(x, 0, z)) || dx * dx + dz * dz > (double) l.radius * l.radius)
                    continue;
                l.sample(dx, dz, s);
                int slope = 0;
                boolean voidNb = false;
                for (int a = -1; a <= 1; a++)
                    for (int b = -1; b <= 1; b++) {
                        if (a == 0 && b == 0)
                            continue;
                        l.sample(dx + a, dz + b, t);
                        if (a == 0 || b == 0)
                            slope = Math.max(slope, Math.abs(t.height - s.height));
                        voidNb |= t.surface == MapLayout.Surface.VOID;
                    }
                BlockState top = level.getBlockState(new BlockPos(x, baseY + s.height, z));
                boolean ok = switch (s.surface) {
                    case LAVA -> GeothermalPlant.isLava(level.getBlockState(new BlockPos(x, baseY + s.fluidTop, z)))
                        && top.equals(pal.bed());
                    case VOID -> top.equals(pal.voidFloor());
                    case BRIDGE -> top.equals(pal.bridge());
                    case MESA -> top.equals(pal.mesaTop());
                    case SAND -> top.equals(slope >= 3 ? pal.cliff() : pal.sand());
                    case GRASS -> top.equals(slope >= 3 ? pal.cliff() : pal.grass());
                    case CLIFF -> top.equals(pal.cliff());
                    default -> top.equals(slope >= 3 ? pal.cliff() : pal.top());
                };
                boolean wantRail = l.railAt(s.surface, voidNb);
                BlockState above = level.getBlockState(new BlockPos(x, baseY + s.height + 1, z));
                boolean hasRail = above.is(Blocks.END_STONE_BRICK_WALL);
                if (hasRail)
                    rails++;
                if (!ok || wantRail != hasRail) {
                    wrong++;
                    if (firstWrong == null)
                        firstWrong = s.surface + " at " + x + "," + z + " shows " + top.getBlock() + " / " + above.getBlock()
                            + (wantRail ? " (wants a rail)" : "");
                }
            }
        if (wrong > 0)
            problems.add(tag + wrong + " column(s) wrong, first " + firstWrong);
        return rails;
    }

    // ------------------------------------------------------------------------------------------------ the garrison

    /**
     * The Forsaken Garrison grants and stops granting by its rules: neutral, it banks recruits (to the cap); taking it
     * releases the bank as the map's recruit (an enderman from the obsidian style); held and uncontested, it musters
     * to GARRISON_MAX and no further; a recruit's death frees a place that the next muster fills; an enemy on the site
     * holds the muster; and once the enemy has neutralized it, the old holder gets nothing more. A recruit saved and
     * loaded again counts once. Driven through tickSite, so no other test's sites move.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void garrison_musters_for_its_holder_and_stops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = base.getX(), cz = base.getZ(), y = 232;
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++) {
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.STONE.defaultBlockState(), 3);
                for (int up = 1; up <= 8; up++)
                    level.setBlock(new BlockPos(cx + dx, y + up, cz + dz), Blocks.AIR.defaultBlockState(), 3);
            }
        String holder = "gametest_garrison_holder", enemy = "gametest_garrison_enemy";
        Entity site = CapturePointServerEvents.place(level, cx, cz, CapturePointServerEvents.Kind.GARRISON,
            CapturePointServerEvents.Style.OBSIDIAN);
        if (site == null) {
            helper.fail("could not place the garrison");
            return;
        }
        List<Entity> spawned = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        // the musters run at the economy pace: the two sides are pinned to Standard; the neutral bank follows the
        // match's own pace (0.75 to 1.35), so its first stride is long enough for one recruit at any pace, never two
        EconomyPace.setTestOverride(holder, EconomyPace.STANDARD);
        EconomyPace.setTestOverride(enemy, EconomyPace.STANDARD);
        try {
            final int T = CapturePointServerEvents.GARRISON_SECONDS, MAX = CapturePointServerEvents.GARRISON_MAX;
            // neutral: it banks, to the cap
            CapturePointServerEvents.tickSite(level, site, T / 0.75f + 1);
            if (bank(site) != 1)
                problems.add("neutral for one muster banked " + bank(site));
            CapturePointServerEvents.tickSite(level, site, T * 3);
            if (bank(site) != CapturePointServerEvents.GARRISON_BANK)
                problems.add("a long-neutral garrison banked " + bank(site) + ", cap " + CapturePointServerEvents.GARRISON_BANK);
            // taken: the bank marches out at once, as the style's recruit
            Entity mine = unit(level, holder, cx + 2.5, y + 1, cz + 0.5);
            spawned.add(mine);
            CapturePointServerEvents.tickSite(level, site, CapturePointServerEvents.CAPTURE_SECONDS + 1);
            if (!holder.equals(CapturePointServerEvents.ownerOf(site)))
                problems.add("an uncontested side did not take it");
            int alive = CapturePointServerEvents.recruitsAlive(site, holder);
            if (alive != Math.min(MAX, CapturePointServerEvents.GARRISON_BANK) || bank(site) != 0)
                problems.add("the capture released " + alive + " recruits (bank left " + bank(site) + ")");
            for (Entity r : CapturePointServerEvents.recruitsOf(site))
                if (r.getType() != EntityRegistrar.ENDERMAN_UNIT.get())
                    problems.add("an obsidian garrison raised a " + EntityType.getKey(r.getType()));
            // held at the cap: no more
            CapturePointServerEvents.tickSite(level, site, T * 2);
            if (CapturePointServerEvents.recruitsAlive(site, holder) != MAX)
                problems.add("at the cap it raised past " + MAX + ": " + CapturePointServerEvents.recruitsAlive(site, holder));
            // a recruit falls: the ready muster fills the place straight away
            CapturePointServerEvents.recruitsOf(site).get(0).discard();
            CapturePointServerEvents.tickSite(level, site, 1);
            if (CapturePointServerEvents.recruitsAlive(site, holder) != MAX)
                problems.add("a freed place was not filled (" + CapturePointServerEvents.recruitsAlive(site, holder) + ")");
            // saved and loaded again: the recruit counts once
            Entity r0 = CapturePointServerEvents.recruitsOf(site).get(0);
            var tag = new net.minecraft.nbt.CompoundTag();
            if (!r0.save(tag)) {
                problems.add("a recruit refused to save");
            } else {
                r0.discard();
                Entity back = EntityType.create(SkirmishGameTests.bytesRoundTrip(tag), level).orElse(null);
                if (back == null) {
                    problems.add("a saved recruit did not load");
                } else {
                    level.addFreshEntity(back);   // onJoin re-enlists it by its tag
                    CapturePointServerEvents.reenlist(back);   // a second join must not double-count
                    if (CapturePointServerEvents.recruitsAlive(site, holder) != MAX)
                        problems.add("after a reload the garrison counts " + CapturePointServerEvents.recruitsAlive(site, holder));
                }
            }
            // contested: an enemy on the site holds the muster
            CapturePointServerEvents.recruitsOf(site).get(0).discard();
            Entity theirs = unit(level, enemy, cx - 2.5, y + 1, cz + 0.5);
            spawned.add(theirs);
            CapturePointServerEvents.tickSite(level, site, T * 2);
            if (CapturePointServerEvents.recruitsAlive(site, holder) != MAX - 1)
                problems.add("a contested garrison still mustered (" + CapturePointServerEvents.recruitsAlive(site, holder) + ")");
            // lost: the holder's units gone, the enemy neutralizes it, and the old holder gets nothing more
            mine.discard();
            for (Entity r : CapturePointServerEvents.recruitsOf(site))
                r.discard();
            CapturePointServerEvents.tickSite(level, site, CapturePointServerEvents.CAPTURE_SECONDS + 1);
            if (!CapturePointServerEvents.ownerOf(site).isEmpty())
                problems.add("the enemy alone did not neutralize it (owner '" + CapturePointServerEvents.ownerOf(site) + "')");
            CapturePointServerEvents.tickSite(level, site, T * 2);
            if (CapturePointServerEvents.recruitsAlive(site, holder) != 0)
                problems.add("the old holder kept getting recruits");
            if (!enemy.equals(CapturePointServerEvents.ownerOf(site)) || CapturePointServerEvents.recruitsAlive(site, enemy) < 1)
                problems.add("the enemy took it but got no recruit (owner '" + CapturePointServerEvents.ownerOf(site) + "', "
                    + CapturePointServerEvents.recruitsAlive(site, enemy) + ")");
        } catch (Exception e) {
            problems.add("threw " + e);
        } finally {
            for (Entity r : CapturePointServerEvents.recruitsOf(site))
                r.discard();
            for (Entity e : spawned)
                e.discard();
            site.discard();
            EconomyPace.setTestOverride(holder, null);
            EconomyPace.setTestOverride(enemy, null);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    static int bank(Entity site) {
        return site.getPersistentData().getInt(CapturePointServerEvents.KEY_BANK);
    }

    static Entity unit(ServerLevel level, String owner, double x, double y, double z) {
        var u = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (u == null)
            throw new IllegalStateException("no vindicator unit");
        u.moveTo(x, y, z, 0, 0);
        u.setOwnerName(owner);
        level.addFreshEntity(u);
        return u;
    }

    // ------------------------------------------------------------------------------------------------ the pickers

    /**
     * The lobby and skirmish map pickers name every map through a lang key (with a blurb), the realm maps included,
     * and every capture kind has its long and short name (the map labels and the capture notifications).
     */
    @GameTest(template = ARENA)
    public static void realm_maps_and_the_garrison_have_their_names(GameTestHelper helper) {
        var lang = SkirmishGameTests.enUsLang();
        List<String> missing = new ArrayList<>();
        for (MapPreset p : MapPreset.values())
            for (String key : List.of(p.langKey(), p.langKey() + ".blurb"))
                if (!lang.has(key))
                    missing.add(key);
        for (CapturePointServerEvents.Kind k : CapturePointServerEvents.Kind.values()) {
            String id = k.name().toLowerCase(java.util.Locale.ROOT);
            for (String key : List.of("capture.reignofnether." + id, "capture.reignofnether.short." + id))
                if (!lang.has(key))
                    missing.add(key);
        }
        for (String key : List.of("capture.reignofnether.state.garrison_waiting", "capture.reignofnether.state.garrison_muster"))
            if (!lang.has(key))
                missing.add(key);
        if (missing.isEmpty())
            helper.succeed();
        else
            helper.fail("lang keys missing: " + missing);
    }
}
