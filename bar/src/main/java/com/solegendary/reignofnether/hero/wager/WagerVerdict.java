package com.solegendary.reignofnether.hero.wager;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Lovish's answer to a wager (design "Asking him for things" and "Intents ... and twists"): agree, refuse or twist,
 * by the hero's level and the size of the ask, rolled on the server with a seeded Random so a "he never gives me
 * anything" report can be replayed. Pure arithmetic: the GameTests check it without a world.
 * <p>
 * Every number here is a first guess for lovish to tune (the PR lists them under "Numbers for lovish").
 */
public final class WagerVerdict {
    private WagerVerdict() { }

    public enum Kind { AGREE, REFUSE, TWIST }

    /** The verdict, the granted tier (0 on a refusal) and the two chances it was rolled against. */
    public record Result(Kind kind, int tier, double agreeChance, double twistChance) { }

    public static final int MAX_LEVEL = 10;

    // the RNG hero's roll table (design "Weights per level"), T1..T4 in percent; T0 (the fizzles) is the refusal here
    private static final int[][] WEIGHTS = {
        { 65, 0, 0, 0 }, { 55, 15, 0, 0 }, { 50, 24, 0, 0 }, { 42, 28, 8, 0 }, { 38, 32, 12, 0 },
        { 33, 34, 18, 0 }, { 28, 34, 22, 4 }, { 24, 34, 26, 7 }, { 20, 33, 30, 10 }, { 15, 32, 36, 12 } };

    static int clampLevel(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    /** The highest phenomenon tier a level may get: T1, T2 from level 2, T3 from 4, T4 from 7 (the roll table). */
    public static int unlockedTier(int level) {
        int l = clampLevel(level);
        return l >= 7 ? 4 : l >= 4 ? 3 : l >= 2 ? 2 : 1;
    }

    /** Agree chance: base + 5% a level - 10% a tier asked above T1 - 10% a tier asked above the unlocked one - 15% if asked in the last 60 s; 5-95%. */
    public static double agreeChance(WagerIntents.Intent intent, int level, int askedTier, boolean askedRecently) {
        int l = clampLevel(level);
        double p = intent.base() + 0.05 * l - 0.10 * (askedTier - 1) - 0.10 * Math.max(0, askedTier - unlockedTier(l))
            - (askedRecently ? 0.15 : 0);
        return Math.max(0.05, Math.min(0.95, p));
    }

    /** Twist chance: 15% at level 1 falling to 5% at level 10, +10% when the ask is above the unlocked tier. */
    public static double twistChance(int level, int askedTier) {
        int l = clampLevel(level);
        return 0.15 - (l - 1) * (0.10 / 9) + (askedTier > unlockedTier(l) ? 0.10 : 0);
    }

    /** The roll table's T1..T4 weights (percent) for a level; the T0 fizzles are the refusals here. */
    public static int[] tierWeights(int level) {
        return WEIGHTS[clampLevel(level) - 1].clone();
    }

    /**
     * Rolls the verdict. Draws, in order: the agree roll; on agree, the twist roll and the tier pick (from the level's
     * weights, capped at the asked and the unlocked tier). The same intent is never twisted twice in a row.
     */
    public static Result roll(WagerIntents.Intent intent, int level, int askedTier, boolean askedRecently,
                              @Nullable String lastTwistIntent, Random rng) {
        double agree = agreeChance(intent, level, askedTier, askedRecently);
        double twist = twistChance(level, askedTier);
        if (rng.nextDouble() >= agree)
            return new Result(Kind.REFUSE, 0, agree, twist);
        boolean twisted = rng.nextDouble() < twist && !intent.id().equals(lastTwistIntent);
        int tier = pickTier(level, askedTier, rng);
        return new Result(twisted ? Kind.TWIST : Kind.AGREE, tier, agree, twist);
    }

    /** A tier from the level's weights, never above the asked or the unlocked tier ("asking small gets small"). */
    static int pickTier(int level, int askedTier, Random rng) {
        int cap = Math.max(1, Math.min(askedTier, unlockedTier(level)));
        int[] w = WEIGHTS[clampLevel(level) - 1];
        int total = 0;
        for (int i = 0; i < cap; i++)
            total += w[i];
        double r = rng.nextDouble() * total;
        for (int i = 0; i < cap; i++) {
            r -= w[i];
            if (r < 0)
                return i + 1;
        }
        return cap;
    }

    /** Ticks between wagers: 20 s at level 1, 1.5 s less a level (6.5 s at level 10). */
    public static int cooldownTicks(int level) {
        return (int) Math.round((20 - 1.5 * (clampLevel(level) - 1)) * 20);
    }

    /** Ink (the hero's mana) a granted wager costs by tier: 10 / 25 / 40 / 60. */
    public static int inkCost(int tier) {
        return switch (Math.max(1, Math.min(4, tier))) {
            case 1 -> 10;
            case 2 -> 25;
            case 3 -> 40;
            default -> 60;
        };
    }

    /** Pages the book holds: 20, +4 a level. One regrows every 30 s. */
    public static int maxPages(int level) {
        return 20 + 4 * (clampLevel(level) - 1);
    }
}
