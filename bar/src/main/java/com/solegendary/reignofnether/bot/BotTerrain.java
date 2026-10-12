package com.solegendary.reignofnether.bot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Terrain a bot can trust before anyone has loaded it (playtest Oct 12, finding 1; design/bot.md "Seating far away").
 * <p>
 * Every heightmap read of a chunk that is not loaded answers the world's floor: {@code Level.getHeight} returns the
 * minimum build height for an absent chunk, and the block "under" it is void air - dry, flat, perfect. The skirmish
 * lobby's bot spawn search read 144 such columns 300-525 blocks from a Watch Battle spectator and could not tell an
 * ocean from a meadow, so it seated bots in the sea at y = -64. A column here is read from the loaded chunk when there
 * is one, else from the chunk generator's base terrain (the noise alone: no chunk is generated, loaded or kept) - the
 * same answer the chunk will give once it exists, minus decoration (trees, small lakes), which the caller re-checks on
 * the real chunk for the one spot it picks.
 */
public final class BotTerrain {
    private BotTerrain() { }

    /**
     * One column: {@code floor} is the y of its top solid block, {@code surface} the y of its top block of any kind
     * (water and lava included), so {@code surface > floor} means a fluid lies on the ground. {@code noGround} = nothing
     * solid at all (the void of the End maps, an unbuilt sky). {@code loaded}: read from a real chunk.
     */
    public record Column(int floor, int surface, boolean noGround, boolean loaded) {
        /** Fluid on top: water (an ocean, a river, a flooded valley) or lava. A start here is lost. */
        public boolean wet() {
            return surface > floor;
        }

        /** Ground a capitol can stand on: solid, nothing liquid over it. */
        public boolean dryGround() {
            return !noGround && !wet();
        }
    }

    /** Reads a column without loading anything (see the class comment). */
    public static Column probe(ServerLevel level, int x, int z) {
        int min = level.getMinBuildHeight();
        if (level.hasChunk(x >> 4, z >> 4)) {
            // loaded: the real world, with whatever was built, dug or stamped there (a pre-made map, a lake)
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            return new Column(floor, Math.max(floor, surface), floor < min, true);
        }
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        // the base terrain: WORLD_SURFACE_WG stops at water, OCEAN_FLOOR_WG only at solid blocks
        int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs) - 1;
        int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - 1;
        return new Column(floor, Math.max(floor, surface), floor < min, false);
    }
}
