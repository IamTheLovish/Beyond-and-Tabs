package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MetalOptions;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * One generated battlefield (design/maps.md, "Generated maps"): the terrain as a grid of columns plus the economy and
 * objectives in MapLayout's own records, so MapLayout, MapBuilder, MapPreview and the tests read a generated map as
 * they read a pre-made one. Pure data, built by {@link MapGenerator}; coordinates are relative to the map centre.
 * <p>
 * Unlike the pre-made maps this one is not folded into slot frames: the terrain is natural and every seat differs.
 * Fairness is not by construction but measured ({@link Fairness}) and evened out by the generator's balancer.
 */
public final class GenMap {

    public enum Shape { ROUND, SQUARE }

    /** Data-only spots for mechanics that do not exist yet (Modifier.needs): the map keeps room for them. */
    public enum CampKind { CREEP, HYDRA, RELIC }

    public record Camp(int dx, int dz, CampKind kind, int team) { }

    /**
     * A reserved wild area for the future hero jungle (lovish Oct 20/21: "jungle start and end points differ every
     * game"): a few discs, an entry and an exit on its edge, the side it lies on (-1 = the middle). No metal is placed
     * inside (MetalOptions.reserved).
     */
    public record Jungle(int team, List<int[]> discs, int[] entry, int[] exit) {
        public boolean contains(double dx, double dz) {
            for (int[] d : discs)
                if ((dx - d[0]) * (dx - d[0]) + (dz - d[1]) * (dz - d[1]) <= (double) d[2] * d[2])
                    return true;
            return false;
        }
    }

    /** A road the generator carved (a lane to the centre, a seam road between neighbours): its centre line. */
    public record Lane(int seat, boolean main, int[] xs, int[] zs) { }

    public final long seed;
    public final int n;
    public final int[] teams;
    public final Shape shape;
    /** Distance of every start from the centre. */
    public final int ring;
    /** The arena: its radius (round) or half its side (square). */
    public final int half;
    /** Angle of slot 0; slot i is at rot + 2 pi i / n. */
    public final double rot;
    final int width;
    final byte[] height;
    final byte[] surface;
    final BitSet forest = new BitSet();
    final BitSet tree = new BitSet();

    public final List<MapLayout.Slot> slots = new ArrayList<>();
    public final List<MapLayout.Mex> mexes = new ArrayList<>();
    public final List<MapLayout.Site> sites = new ArrayList<>();
    public final List<MapLayout.Spot> vents = new ArrayList<>();
    public final List<MapLayout.Spot> pillars = new ArrayList<>();
    public final List<Camp> camps = new ArrayList<>();
    public final List<Jungle> jungle = new ArrayList<>();
    public final List<Lane> lanes = new ArrayList<>();
    public final List<Modifier> modifiers = new ArrayList<>();
    public Archetype[] seats;
    public GenOptions.Contrast contrast;
    /** Which reroll attempt and which swap option the balancer kept (0 = the seed's first draw). */
    public int attempt, swapOption;
    /** The first homeCount mexes are the seats' standard home clumps (GenEconomy.homeClumps; MapLayout.homeEnd()). */
    public int homeCount;
    /** Steps the nudge balancer took. */
    public int nudges;
    /** The seats' fairness scores as finally balanced. */
    public Fairness.Report report;
    /** Whether the map passed its own checks (every seat reaches every seat and the middle, every patch its owner). */
    public boolean valid = true;
    public String invalidWhy = "";

    GenMap(long seed, int[] teams, Shape shape, int ring, int half, double rot) {
        this.seed = seed;
        this.n = teams.length;
        this.teams = teams.clone();
        this.shape = shape;
        this.ring = ring;
        this.half = half;
        this.rot = rot;
        this.width = 2 * half + 1;
        this.height = new byte[width * width];
        this.surface = new byte[width * width];
    }

    int idx(int dx, int dz) {
        return (dx + half) * width + (dz + half);
    }

    boolean inGrid(int dx, int dz) {
        return dx >= -half && dx <= half && dz >= -half && dz <= half;
    }

    /** Blocks from (dx, dz) to the arena's edge (the ring wall or the square's nearest side); negative outside. */
    public double distanceToEdge(double dx, double dz) {
        if (shape == Shape.SQUARE)
            return half - Math.max(Math.abs(dx), Math.abs(dz));
        return half - Math.sqrt(dx * dx + dz * dz);
    }

    public boolean inside(double dx, double dz) {
        return distanceToEdge(dx, dz) >= 0;
    }

    public int heightAt(int dx, int dz) {
        return inGrid(dx, dz) ? height[idx(dx, dz)] : 0;
    }

    public MapLayout.Surface surfaceAt(int dx, int dz) {
        return inGrid(dx, dz) ? SURFACES[surface[idx(dx, dz)]] : MapLayout.Surface.LAND;
    }

    public boolean forestAt(int dx, int dz) {
        return inGrid(dx, dz) && forest.get(idx(dx, dz));
    }

    /** Whether a tree grows on this column (a reclaimable stem: units walk round it). */
    public boolean treeAt(int dx, int dz) {
        return inGrid(dx, dz) && tree.get(idx(dx, dz));
    }

    static final MapLayout.Surface[] SURFACES = MapLayout.Surface.values();

    /** The column under (dx, dz), as MapLayout.sample reports it (nearest column; outside the arena: plain land). */
    public void sample(double dx, double dz, MapLayout.Sample out) {
        int x = (int) Math.round(dx), z = (int) Math.round(dz);
        out.u = dx;
        out.v = dz;
        out.fluidTop = 0;
        if (!inGrid(x, z)) {
            out.height = 0;
            out.surface = MapLayout.Surface.LAND;
            out.forest = false;
            return;
        }
        int i = idx(x, z);
        out.height = height[i];
        out.surface = SURFACES[surface[i]];
        out.forest = forest.get(i);
    }

    /** The jungle (and the hydra's pit) as MetalOptions' reserved zones: the open-ground fill leaves them alone. */
    public MetalOptions.ReservedZones reserved() {
        List<Jungle> j = List.copyOf(jungle);
        return (dx, dz) -> {
            for (Jungle z : j)
                if (z.contains(dx, dz))
                    return true;
            return false;
        };
    }

    /** Patch value a seat owns (MapLayout.mexValueOfSlot's sum). */
    public float metalOf(int seat) {
        float v = 0;
        for (MapLayout.Mex m : mexes)
            if (m.slot() == seat)
                v += m.value();
        return v;
    }

    public int team(int seat) {
        return teams[seat];
    }
}
