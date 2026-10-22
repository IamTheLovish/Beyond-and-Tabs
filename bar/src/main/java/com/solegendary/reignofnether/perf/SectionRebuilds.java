package com.solegendary.reignofnether.perf;

/**
 * How many chunk sections the client's renderer re-meshed, whatever asked for it (fog of war, block changes, chunk
 * loads, branch wind): counted where the renderer starts a section's rebuild, by
 * mixin/perf/RenderChunkRebuildCountMixin (vanilla) and mixin/perf/EmbeddiumRebuildCountMixin (Embeddium, the testers'
 * Windows pack and the eyes shader lane). Read by fogofwar.FogRemesh for its {@code [FogRemesh]} line and the eyes
 * tour's fog A/B (Oct 21 perf plan, PR R1: "rebuilds per second before and after").
 *
 * A request (LevelRenderer.setSectionDirty) is not a rebuild: both renderers re-mesh a dirty section only once their
 * visibility pass reaches it, so a sealed cave stays dirty, unbuilt, for as long as nobody can see it. "Empty" counts
 * sections with no blocks: Embeddium answers those without a task, vanilla still runs one over 4096 air blocks.
 *
 * Both hooks run on the render thread; the counters are plain fields read by the same thread. No client classes here.
 */
public final class SectionRebuilds {
    private SectionRebuilds() { }

    private static long built = 0;
    private static long empty = 0;
    private static volatile String renderer = "vanilla";

    /** A section rebuild started; {@code noBlocks} when the section holds only air. */
    public static void count(boolean noBlocks, String by) {
        if (noBlocks)
            empty++;
        else
            built++;
        renderer = by;
    }

    /** Sections with blocks re-meshed since the game started. */
    public static long built() {
        return built;
    }

    /** All-air sections "re-meshed" since the game started. */
    public static long empty() {
        return empty;
    }

    /** Which renderer counted last: vanilla or embeddium. */
    public static String renderer() {
        return renderer;
    }
}
