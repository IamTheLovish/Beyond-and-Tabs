package com.solegendary.reignofnether.fogofwar;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which chunks this client has ever had in sight, and the tint a fogged block gets from that (lovish, Oct 22: never-
 * scouted ground is "dark but visible" - the shape of the map shows faintly - not black). Three states:
 * visible (no tint), explored but out of sight (the Fog style's tint, FogOfWarClientEvents.FOG_TINT_RGB), and never
 * explored (FOG_TINT_UNEXPLORED, ~15% brightness).
 *
 * The tint is baked into chunk meshes, so the set is read by the off-thread mesher: it is published as an immutable
 * snapshot (copy on write, only when a chunk is explored for the first time - a few times a minute at most) and read
 * with one volatile load and a primitive lookup, no allocation. A chunk's first sighting is also its first re-mesh
 * (FogOfWarClientEvents.applyServerFogState marks {@link #markExplored}'s result with the existing FogRemesh path,
 * which keeps to the surface band).
 */
public final class FogExploration {
    private FogExploration() { }

    /** Never-explored ground: about 15% of the world's colour, a little blue so it reads as night, not as a hole. */
    public static final int TINT_UNEXPLORED = 0x242834;

    private static volatile LongOpenHashSet explored = new LongOpenHashSet();

    public static boolean isExplored(int chunkX, int chunkZ) {
        return explored.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** A new match or fog switched off or on: nothing has been seen yet. */
    public static void clear() {
        explored = new LongOpenHashSet();
    }

    /**
     * These chunks are in sight now: records them and returns those never seen before (they need one re-mesh, from the
     * unexplored tint to the visible one and, later, to the explored one). Client thread only.
     */
    public static List<ChunkPos> markExplored(Collection<ChunkPos> bright) {
        LongOpenHashSet cur = explored;
        boolean any = false;
        for (ChunkPos c : bright)
            if (!cur.contains(c.toLong())) {
                any = true;
                break;
            }
        if (!any)
            return List.of();
        LongOpenHashSet next = new LongOpenHashSet(cur);
        List<ChunkPos> first = firstExplored(next, bright);
        explored = next;
        return first;
    }

    /** The rule behind {@link #markExplored}, pure: adds {@code bright} to {@code known} and returns the new ones. */
    public static List<ChunkPos> firstExplored(LongOpenHashSet known, Collection<ChunkPos> bright) {
        List<ChunkPos> first = new ArrayList<>();
        for (ChunkPos c : bright)
            if (known.add(c.toLong()))
                first.add(c);
        return first;
    }

    /** The tint (an RGB multiplier) for a block: 0 = none (in sight), else the explored or unexplored one. */
    public static int pickTint(boolean visible, boolean explored, int exploredTint, int unexploredTint) {
        return visible ? 0 : explored ? exploredTint : unexploredTint;
    }

    /**
     * A biome-tinted quad (grass, leaves) already carries the explored tint from BiomeColorsMixin when it is fogged;
     * on never-explored ground this scales it on to the unexplored one (c * unexplored / explored per channel), so
     * the mixin stays as it is.
     */
    public static int dimToUnexplored(int rgb, int exploredTint, int unexploredTint) {
        int r = scale((rgb >> 16) & 0xFF, (unexploredTint >> 16) & 0xFF, (exploredTint >> 16) & 0xFF);
        int g = scale((rgb >> 8) & 0xFF, (unexploredTint >> 8) & 0xFF, (exploredTint >> 8) & 0xFF);
        int b = scale(rgb & 0xFF, unexploredTint & 0xFF, exploredTint & 0xFF);
        return (rgb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int scale(int c, int to, int from) {
        return from <= 0 ? c : Math.min(255, c * to / from);
    }
}
