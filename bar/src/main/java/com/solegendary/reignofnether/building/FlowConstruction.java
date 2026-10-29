package com.solegendary.reignofnether.building;

/**
 * The flow-construction step's arithmetic (BuildingPlacement.tickFlowConstruction), as plain numbers so
 * FlowConstructionTest can pin it in milliseconds: progress per tick is the builders' summed build power over the
 * building's build ticks (BAR: build powers add, so N builders raise a site N times as fast and drain N times the
 * metal and energy a second - the stall in EconomyMath throttles them all alike), and the blocks laid follow the paid
 * progress. One scan of the site queues a tick's blocks (BuildingPlacement.queueNextBlocks) and one place sound plays a
 * tick per site, however many blocks went in.
 * <p>
 * lovish, Oct 29: "the more workers the faster something should build". Laying blocks was capped at one a tick, so past
 * ~7-12 build power (the art has 1.5-2.8x the blocks its build time counts: a Brassworks extractor 113 blocks over 40
 * timed) more builders paid for progress the blocks could not keep up with, and the site rose no faster.
 * {@link #MAX_BLOCKS_PER_TICK} lets a crowded site lay what its progress has paid for.
 */
public final class FlowConstruction {
    private FlowConstruction() { }

    /** The most blocks one site lays in a tick (each is a world block change; 8 is ~160 blocks a second). */
    public static final int MAX_BLOCKS_PER_TICK = 8;

    /** Fraction of the whole job this tick's build power asks for. */
    public static float progressPerTick(float buildPower, float buildTicks) {
        return buildPower <= 0 ? 0 : buildPower / Math.max(1f, buildTicks);
    }

    /**
     * How far progress may run ahead of the blocks actually standing (blocks destroyed meanwhile must be rebuilt, so
     * builders never pay for much they cannot see): two build actions plus one tick's worth of blocks. The window was
     * two blocks, which also held a crowded site to ~3 blocks a tick whatever its build power.
     */
    public static float maxProgress(int blocksPlaced, int queued, int blocksPerBuild, int blocksTotal) {
        int ahead = 2 * Math.max(1, blocksPerBuild) + MAX_BLOCKS_PER_TICK;
        return Math.min(1f, (float) (blocksPlaced + queued + ahead) / blocksTotal);
    }

    /** Blocks that should stand at this progress. */
    public static int blocksDue(float progress, int blocksTotal) {
        return progress >= 1f ? blocksTotal : (int) Math.ceil(progress * blocksTotal - 0.0001f);
    }

    /** Blocks to queue this tick to catch up with the paid progress: the shortfall, at most {@link #MAX_BLOCKS_PER_TICK}. */
    public static int blocksToLay(int due, int blocksPlaced, int queued) {
        return Math.max(0, Math.min(MAX_BLOCKS_PER_TICK, due - blocksPlaced - queued));
    }

    /**
     * Queued blocks the flow step lays itself this tick: what the progress has paid for, at most
     * {@link #MAX_BLOCKS_PER_TICK} a tick in all, less the one the building tick's own buildNextBlock pass lays.
     */
    public static int layNow(int due, int blocksPlaced) {
        return Math.max(0, Math.min(MAX_BLOCKS_PER_TICK, due - blocksPlaced) - 1);
    }
}
