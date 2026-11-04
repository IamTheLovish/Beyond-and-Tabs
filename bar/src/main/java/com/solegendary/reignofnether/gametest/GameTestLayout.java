package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.hero.HeroExperienceOrb;
import com.solegendary.reignofnether.hero.HeroProgression;

/**
 * How far apart the suite lays out its arenas (mixin/GameTestBatchRunnerMixin). Vanilla left 5 blocks between two
 * arenas in a row and 6 between rows - 21 blocks from one 16-wide arena's spot to the same spot next door - and game
 * rules reach further than that: a hero earns XP from any enemy death within 20 blocks, 28 for a decisive kill, and
 * captures within 24, so one test's kill paid another test's hero (hero_auto_learn_logout_ends_the_menu_hold, "points 0,
 * ranks 2", 9 of 31 CI shard-8 jobs, Nov 1-3; HeroXpArenaGameTests). The gap is the longest of those reaches plus a
 * margin, so no point of one arena is within reach of any point of another.
 */
public final class GameTestLayout {

    private GameTestLayout() {
    }

    /** The longest reach of a hero XP rule (kill XP, decisive-kill burst, capture), in blocks. */
    public static final double XP_REACH = Math.max(HeroProgression.BURST_RANGE,
        Math.max(HeroProgression.CAPTURE_RANGE, HeroExperienceOrb.RANGE));

    /** Blocks left between two arenas, in a row and between rows. */
    public static final int GAP = (int) Math.ceil(XP_REACH) + 4;
}
