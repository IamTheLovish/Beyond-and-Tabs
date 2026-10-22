package com.solegendary.reignofnether.maps.gen;

/**
 * What the lobby (or a test, or an experiment) asks of the map generator. Pure data; the same options and seed always
 * give the same map.
 *
 * @param shape      round arena (the ring wall) or square (straight wall sides, corner seats), or either by seed
 * @param modifiers  per-match map modifiers (lovish Oct 21 22:28: "sometimes changes rng with different buffs and
 *                   bonuses"): off, on (named in the lobby) or surprise (rolled, named only once the map has risen)
 * @param contrast   how the sides differ (balanced asymmetry, lovish Oct 21 21:47): AUTO picks one by seed
 * @param balance    how the generator evens the seats out (Phase 1 compared them: swapping seats and sides then
 *                   nudging the metal won; the nudge judged under weight draws too, SWAP_ROBUST, is the default)
 * @param tolerance  the largest spread of the seats' fairness scores accepted, (max - min) / mean (lovish: about 5%)
 * @param liveOnly   only modifiers whose mechanic exists in the game today (the lobby); experiments may roll all
 */
public record GenOptions(Shape shape, Modifiers modifiers, Contrast contrast, Balance balance, double tolerance,
                         boolean liveOnly) {

    public enum Shape { ROUND, SQUARE, ANY }

    public enum Modifiers { OFF, ON, SURPRISE }

    /**
     * The side contrasts. MIRROR is the control (point-symmetric terrain and economy, the "tournament" map); the others
     * give the two sides different characters that the fairness score has to even out.
     */
    public enum Contrast { AUTO, MIRROR, PROSPECTOR_WARDEN, OPEN_BASTION, RIVER, MIXED }

    /**
     * NONE: the raw draw. REROLL: fresh draws until one is even. NUDGE: metal edits. SWAP: seats turned and sides swapped
     * over the same landscape. SWAP_NUDGE: both. REROLL_NUDGE: a few rerolls then the nudge. SWAP_ROBUST: SWAP_NUDGE
     * with every edit judged under eight draws of the weights as well as the central ones (the lobby's).
     */
    public enum Balance { NONE, REROLL, NUDGE, SWAP, SWAP_NUDGE, REROLL_NUDGE, SWAP_ROBUST }

    public static final double DEFAULT_TOLERANCE = 0.05;

    /** What the lobby plays: either shape by seed, modifiers on, every contrast, the winning balancer. */
    public static final GenOptions DEFAULT = new GenOptions(Shape.ANY, Modifiers.ON, Contrast.AUTO, Balance.SWAP_ROBUST,
        DEFAULT_TOLERANCE, true);

    public GenOptions {
        if (shape == null)
            shape = Shape.ANY;
        if (modifiers == null)
            modifiers = Modifiers.ON;
        if (contrast == null)
            contrast = Contrast.AUTO;
        if (balance == null)
            balance = Balance.SWAP_ROBUST;
        tolerance = Math.max(0.005, Math.min(0.5, tolerance));
    }

    public GenOptions withShape(Shape s) {
        return new GenOptions(s, modifiers, contrast, balance, tolerance, liveOnly);
    }

    public GenOptions withModifiers(Modifiers m) {
        return new GenOptions(shape, m, contrast, balance, tolerance, liveOnly);
    }

    public GenOptions withContrast(Contrast c) {
        return new GenOptions(shape, modifiers, c, balance, tolerance, liveOnly);
    }

    public GenOptions withBalance(Balance b) {
        return new GenOptions(shape, modifiers, contrast, b, tolerance, liveOnly);
    }

    public GenOptions withTolerance(double t) {
        return new GenOptions(shape, modifiers, contrast, balance, t, liveOnly);
    }

    public GenOptions withLiveOnly(boolean live) {
        return new GenOptions(shape, modifiers, contrast, balance, tolerance, live);
    }
}
