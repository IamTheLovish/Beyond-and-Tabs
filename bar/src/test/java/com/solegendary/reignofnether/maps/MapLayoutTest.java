package com.solegendary.reignofnether.maps;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The map is the match's fairness: a seed must always draw the same map (the server builds it, every client previews
 * it, a late joiner rebuilds it), a new seed must draw a different one ("no two matches the same map"), and every
 * slot must get exactly what its neighbour gets ("the fairness by construction holds for every seed").
 */
class MapLayoutTest {

    private static final long[] SEEDS = { 0L, 42L, Long.MIN_VALUE };
    private static final int[][] TEAMS = { { 0, 1 }, { 0, 0, 1, 1 }, { 0, 1, 2, 3, 4, 5, 6, 7 } };

    /** Layouts are never changed after build(), so tests that only read one share it (the build is the costly part). */
    private static final java.util.Map<String, MapLayout> BUILT = new java.util.HashMap<>();

    private static MapLayout layout(MapPreset preset, int[] teams, long seed) {
        return BUILT.computeIfAbsent(preset + java.util.Arrays.toString(teams) + seed, k -> MapLayout.build(preset, teams, seed));
    }

    /**
     * The hand-made maps: copies of one slot's ground and metal for every slot, the start positions fixed by the map.
     * A generated map is fair by measure, not by copy (different sides evened out, a square arena), so these rules
     * don't apply to it - its own are in MapGenGameTests and maps/gen (the same split as MapSeedGameTests.premade).
     */
    private static List<MapPreset> premade() {
        List<MapPreset> out = new ArrayList<>();
        for (MapPreset p : MapPreset.values())
            if (p.handmade())
                out.add(p);
        return out;
    }

    /** Terrain on a coarse grid over the whole arena - the map's fingerprint. */
    private static List<String> terrain(MapLayout l, int step) {
        List<String> out = new ArrayList<>();
        MapLayout.Sample s = new MapLayout.Sample();
        for (int dx = -l.radius; dx <= l.radius; dx += step)
            for (int dz = -l.radius; dz <= l.radius; dz += step) {
                l.sample(dx, dz, s);
                out.add(s.height + "/" + s.surface + "/" + s.fluidTop + "/" + s.forest + "/" + s.u + "/" + s.v);
            }
        return out;
    }

    private static boolean sameMap(MapLayout a, MapLayout b) {
        return a.slots.equals(b.slots) && a.boxes.equals(b.boxes) && a.mexes.equals(b.mexes) && a.sites.equals(b.sites)
            && a.vents.equals(b.vents) && a.pillars.equals(b.pillars) && terrain(a, 13).equals(terrain(b, 13));
    }

    // ---------------------------------------------------------------- determinism

    @Test
    void theSameSeedAlwaysDrawsTheSameMap() {
        for (MapPreset preset : premade())
            for (int[] teams : TEAMS)
                for (long seed : SEEDS) {
                    MapLayout a = MapLayout.build(preset, teams, seed);
                    MapLayout b = MapLayout.build(preset, teams, seed);
                    assertEquals(seed, a.seed);
                    assertTrue(sameMap(a, b), preset + " " + teams.length + " slots seed " + seed + " drew two different maps");
                }
    }

    @Test
    void theUnseededMapIsSeedZero() {
        // older saves, the lobby preview and the older tests build without a seed: they must keep their map
        for (MapPreset preset : premade())
            for (int[] teams : TEAMS)
                assertTrue(sameMap(MapLayout.build(preset, teams), MapLayout.build(preset, teams, 0L)), preset.name());
    }

    @Test
    void aMapDoesNotDependOnWhatWasBuiltBeforeIt() {
        // no hidden static state: building other maps in between changes nothing
        MapPreset preset = MapPreset.HIGHLANDS;
        MapLayout first = MapLayout.build(preset, TEAMS[1], 99L);
        for (MapPreset other : premade())
            MapLayout.build(other, TEAMS[2], 5L).sample(10, 10, new MapLayout.Sample());
        assertTrue(sameMap(first, MapLayout.build(preset, TEAMS[1], 99L)));
    }

    @Test
    void aDifferentSeedDrawsADifferentMapOnEveryPresetThatHasNoiseToShape() {
        for (MapPreset preset : premade()) {
            if (preset == MapPreset.ARCHIPELAGO)
                continue;   // see archipelagoIgnoresTheSeed
            for (int[] teams : TEAMS) {
                MapLayout a = layout(preset, teams, 1L);
                MapLayout b = layout(preset, teams, 2L);
                assertFalse(sameMap(a, b), preset + " " + teams.length + " slots: seeds 1 and 2 drew the same map");
            }
        }
    }

    @Disabled("BUG?: Archipelago ignores the seed - its terrain is hard-coded islands (no noise) and the metal scatter "
        + "lands on the same spots, so every Archipelago match is the same map. MapSeedGameTests only demands variation "
        + "on Verdant Valley, so nothing noticed. lovish: \"no two matches the same map\" - commander decides whether "
        + "Archipelago should vary (e.g. wobble the islet edges with noise) or is allowed to stay fixed.")
    @Test
    void archipelagoIgnoresTheSeed() {
        for (int[] teams : TEAMS)
            assertFalse(sameMap(layout(MapPreset.ARCHIPELAGO, teams, 1L), layout(MapPreset.ARCHIPELAGO, teams, 2L)),
                "Archipelago with " + teams.length + " slots drew the same map for seeds 1 and 2");
    }

    @Test
    void theSeedChangesTheShapeNotTheStartPositions() {
        // the starts, the arena and the team boxes are the lobby's business, never the seed's
        for (MapPreset preset : premade())
            for (int[] teams : TEAMS) {
                MapLayout a = layout(preset, teams, 0L);
                MapLayout b = layout(preset, teams, 987654321L);
                assertEquals(a.slots, b.slots, preset.name());
                assertEquals(a.boxes, b.boxes, preset.name());
                assertEquals(a.ring, b.ring);
                assertEquals(a.radius, b.radius);
            }
    }

    // ---------------------------------------------------------------- fairness by construction

    @Test
    void everySlotGetsTheSameMetalForEverySeed() {
        for (MapPreset preset : premade())
            for (int[] teams : TEAMS)
                for (long seed : SEEDS) {
                    MapLayout l = layout(preset, teams, seed);
                    float first = l.mexValueOfSlot(0);
                    assertTrue(first > 0f, preset + " gave slot 0 no metal at all");
                    for (int slot = 1; slot < l.slots.size(); slot++)
                        assertEquals(first, l.mexValueOfSlot(slot), 1e-3f,
                            preset + " " + teams.length + " slots seed " + seed + ": slot " + slot + " has a different share of metal");
                }
    }

    @Test
    void everySlotGetsTheSameNumberOfPatches() {
        for (MapPreset preset : premade())
            for (int[] teams : TEAMS)
                for (long seed : new long[] { 0L, 77L }) {
                    MapLayout l = layout(preset, teams, seed);
                    int n = l.slots.size();
                    int[] perSlot = new int[n];
                    for (MapLayout.Mex m : l.mexes)
                        perSlot[m.slot()]++;
                    for (int slot = 1; slot < n; slot++)
                        assertEquals(perSlot[0], perSlot[slot], preset + " " + n + " slots seed " + seed + ": patch count of slot " + slot);
                }
    }

    @Test
    void theTerrainIsTheSameSeenFromEverySlot() {
        // "every preset is a function of the folded (u, v) only - that is the symmetry"
        double[][] frame = { { 40.3, 5.7 }, { 90.7, 12.3 }, { 150.1, 3.3 }, { 130.9, -9.1 }, { 60.2, 20.9 }, { 200.6, 15.7 }, { 25.4, 2.2 } };
        for (MapPreset preset : premade())
            for (int[] teams : new int[][] { TEAMS[0], TEAMS[1], TEAMS[2] })
                for (long seed : new long[] { 0L, 31L }) {
                    MapLayout l = layout(preset, teams, seed);
                    MapLayout.Sample ref = new MapLayout.Sample(), other = new MapLayout.Sample();
                    for (double[] uv : frame) {
                        double[] w0 = MapLayout.toWorld(l.slots.get(0), uv[0], uv[1]);
                        l.sample(w0[0], w0[1], ref);
                        for (int k = 1; k < l.slots.size(); k++) {
                            double[] w = MapLayout.toWorld(l.slots.get(k), uv[0], uv[1]);
                            l.sample(w[0], w[1], other);
                            String where = preset + " " + l.n + " slots seed " + seed + " slot " + k + " at u=" + uv[0] + " v=" + uv[1];
                            assertEquals(ref.height, other.height, where + ": height");
                            assertEquals(ref.surface, other.surface, where + ": surface");
                            assertEquals(ref.fluidTop, other.fluidTop, where + ": fluid");
                            assertEquals(ref.forest, other.forest, where + ": forest");
                        }
                    }
                }
    }

    // ---------------------------------------------------------------- the starts

    @Test
    void everyStartIsOnGroundAndInsideItsOwnTeamsBox() {
        for (MapPreset preset : premade())
            for (int[] teams : TEAMS)
                for (long seed : SEEDS) {
                    MapLayout l = layout(preset, teams, seed);
                    assertEquals(teams.length, l.slots.size());
                    for (MapLayout.Slot s : l.slots) {
                        String who = preset + " " + teams.length + " slots seed " + seed + " slot " + s.index();
                        assertEquals(teams[s.index()], s.team(), who);
                        assertTrue(l.startGround(s.dx(), s.dz()), who + ": the start is in the void, lava or deep water");
                        MapLayout.Box box = l.boxOf(s.team());
                        assertNotNull(box, who);
                        assertTrue(box.contains(s.dx(), s.dz()), who + " starts outside its team's box");
                        for (MapLayout.Box enemy : l.boxes)
                            if (enemy.team() != s.team())
                                assertFalse(enemy.contains(s.dx(), s.dz()), who + " starts inside the enemy's box");
                    }
                }
    }

    @Test
    void enemyBoxesNeverOverlap() {
        for (MapPreset preset : premade())
            for (int[] teams : TEAMS) {
                MapLayout l = layout(preset, teams, 3L);
                for (MapLayout.Box a : l.boxes)
                    for (MapLayout.Box b : l.boxes) {
                        if (a == b)
                            continue;
                        for (double r = Math.max(a.r0(), b.r0()); r <= Math.min(a.r1(), b.r1()); r += 10)
                            for (double t = 0; t < Math.PI * 2; t += 0.02) {
                                double x = Math.cos(t) * r, z = Math.sin(t) * r;
                                assertFalse(a.contains(x, z) && b.contains(x, z),
                                    preset + ": boxes of team " + a.team() + " and " + b.team() + " overlap at " + x + "," + z);
                            }
                    }
            }
    }

    @Test
    void neighbouringStartsAreFarEnoughApartForABase() {
        for (MapPreset preset : premade())
            for (int n = 2; n <= 16; n++) {
                int[] teams = new int[n];
                for (int i = 0; i < n; i++)
                    teams[i] = i % 2;
                MapLayout l = layout(preset, teams, 0L);
                for (int i = 0; i < n; i++) {
                    MapLayout.Slot a = l.slots.get(i), b = l.slots.get((i + 1) % n);
                    double gap = Math.hypot(a.dx() - b.dx(), a.dz() - b.dz());
                    assertTrue(gap >= 114, preset + " " + n + " slots: starts " + i + " and " + (i + 1) % n + " are only " + gap + " apart");
                }
            }
    }

    @Test
    void theStartRingWidensWithMorePlayersAndNeverShrinks() {
        for (MapPreset preset : premade()) {
            assertEquals(preset.baseRing, MapLayout.ringFor(preset, 1));
            assertEquals(preset.baseRing, MapLayout.ringFor(preset, 2));
            int last = 0;
            for (int n = 2; n <= 24; n++) {
                int ring = MapLayout.ringFor(preset, n);
                assertTrue(ring >= preset.baseRing, preset + " " + n);
                assertTrue(ring >= last, preset + " ring shrank at " + n + " players");
                last = ring;
            }
        }
    }

    @Test
    void aPointIsInTheWedgeOfTheStartItIsNextTo() {
        for (int[] teams : TEAMS) {
            MapLayout l = layout(MapPreset.VERDANT_VALLEY, teams, 0L);
            for (MapLayout.Slot s : l.slots) {
                assertEquals(s.index(), l.slotAt(s.dx(), s.dz()));
                assertEquals(s.index(), l.slotAt(s.dx() * 0.5, s.dz() * 0.5), "half way to the centre is still its wedge");
            }
        }
    }

    // ---------------------------------------------------------------- the mexes

    @Test
    void everyPatchIsInsideTheArenaAndNoTwoShareASpot() {
        for (MapPreset preset : premade())
            for (int[] teams : TEAMS)
                for (long seed : new long[] { 0L, 11L }) {
                    MapLayout l = layout(preset, teams, seed);
                    List<String> seen = new ArrayList<>();
                    for (MapLayout.Mex m : l.mexes) {
                        String where = preset + " " + teams.length + " slots seed " + seed + " patch " + m;
                        assertTrue(Math.hypot(m.dx(), m.dz()) <= l.radius, where + " is outside the arena");
                        assertTrue(m.value() > 0f, where);
                        String key = m.dx() + "," + m.dz();
                        assertFalse(seen.contains(key), where + " stands on another patch");
                        seen.add(key);
                    }
                }
    }

    @Test
    void thePresetsAreDifferentMaps() {
        List<MapPreset> all = premade();
        for (int i = 0; i < all.size(); i++)
            for (int j = i + 1; j < all.size(); j++)
                assertNotEquals(terrain(layout(all.get(i), TEAMS[1], 0L), 17), terrain(layout(all.get(j), TEAMS[1], 0L), 17),
                    all.get(i) + " and " + all.get(j) + " have the same terrain");
    }

    @Test
    void theRandomPresetCarriesNoTerrainOfItsOwn() {
        MapLayout l = MapLayout.ring(new int[] { 0, 1 }, List.of(new int[] { -150, 0 }, new int[] { 150, 0 }), 150, 200);
        assertEquals(2, l.slots.size());
        assertTrue(l.startGround(-150, 0), "random terrain is all ground");
        assertTrue(l.mexes.isEmpty());
        assertTrue(l.radius >= 210, "the arena wall stays clear of the ring");
    }
}
