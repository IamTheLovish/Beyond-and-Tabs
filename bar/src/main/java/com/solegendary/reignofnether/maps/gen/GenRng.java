package com.solegendary.reignofnether.maps.gen;

/**
 * The generator's random numbers: SplitMix64, written out so every JVM draws the same sequence (java.util.Random is
 * specified too, but its streams cannot be forked by name). The lobby preview on a player's machine and the server's
 * build must draw exactly the same map from the same seed, so nothing in the generator may use another source.
 * <p>
 * Every stage of the generator takes its own named stream ({@link #stream}): a change that adds one draw to the river
 * never re-rolls the metal, and an experiment can re-run one stage alone.
 */
public final class GenRng {
    private long state;

    public GenRng(long seed) {
        this.state = seed;
    }

    /** An independent stream for one named stage of one seed. */
    public static GenRng stream(long seed, String stage) {
        return new GenRng(mix(seed ^ mix(stage.hashCode() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L)));
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform in [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 1)
            return 0;
        return (int) ((nextLong() >>> 1) % bound);
    }

    public double range(double lo, double hi) {
        return lo + (hi - lo) * nextDouble();
    }

    public boolean chance(double p) {
        return nextDouble() < p;
    }

    /** Picks an index with probability proportional to its weight (weights >= 0, at least one positive). */
    public int weighted(double[] weights) {
        double sum = 0;
        for (double w : weights)
            sum += Math.max(0, w);
        double t = nextDouble() * sum;
        for (int i = 0; i < weights.length; i++) {
            t -= Math.max(0, weights[i]);
            if (t < 0)
                return i;
        }
        return weights.length - 1;
    }

    /** SplitMix64's finaliser: spreads a seed over all 64 bits, so neighbouring seeds give unrelated maps. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
