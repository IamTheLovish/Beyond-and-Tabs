package com.solegendary.reignofnether.hero.wager;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Lovish's answer to a wager (design "Asking him for things" and "Intents ... and twists"): agree, refuse or twist,
 * by the hero's level and the size of the ask, rolled on the server with a seeded Random so a "he never gives me
 * anything" report can be replayed. Pure arithmetic: the GameTests check it without a world.
 */
public final class WagerVerdict {
    private WagerVerdict() { }

    public enum Kind { AGREE, REFUSE, TWIST }

    /** The verdict, the granted tier (0 on a refusal) and the two chances it was rolled against. */
    public record Result(Kind kind, int tier, double agreeChance, double twistChance) { }

    public static final int MAX_LEVEL = 10;

    /** The highest phenomenon tier a level may get: T1, T2 from level 2, T3 from 4, T4 from 7 (the roll table). */
    public static int unlockedTier(int level) {
        return 1;
    }

    /** Agree chance: base + 5% a level - 10% a tier asked above T1 - 10% a tier asked above the unlocked one - 15% if asked in the last 60 s; 5-95%. */
    public static double agreeChance(WagerIntents.Intent intent, int level, int askedTier, boolean askedRecently) {
        return 0;
    }

    /** Twist chance: 15% at level 1 falling to 5% at level 10, +10% when the ask is above the unlocked tier. */
    public static double twistChance(int level, int askedTier) {
        return 0;
    }

    /** The roll table's T1..T4 weights (percent) for a level; the T0 fizzles are the refusals here. */
    public static int[] tierWeights(int level) {
        return new int[] { 100, 0, 0, 0 };
    }

    /**
     * Rolls the verdict. Draws, in order: the agree roll; on agree, the twist roll and the tier pick (from the level's
     * weights, capped at the asked and the unlocked tier). The same intent is never twisted twice in a row.
     */
    public static Result roll(WagerIntents.Intent intent, int level, int askedTier, boolean askedRecently,
                              @Nullable String lastTwistIntent, Random rng) {
        return new Result(Kind.REFUSE, 0, 0, 0);
    }

    /** Ticks between wagers: 20 s at level 1, 1.5 s less a level (6.5 s at level 10). */
    public static int cooldownTicks(int level) {
        return 0;
    }

    /** Ink (the hero's mana) a granted wager costs by tier: 10 / 25 / 40 / 60. */
    public static int inkCost(int tier) {
        return 0;
    }

    /** Pages the book holds: 20, +4 a level. One regrows every 30 s. */
    public static int maxPages(int level) {
        return 0;
    }
}
