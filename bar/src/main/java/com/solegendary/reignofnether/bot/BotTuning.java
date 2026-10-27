package com.solegendary.reignofnether.bot;

/**
 * The knobs that separate the three difficulties (see design/bot.md). Everything timing- or size-related the brain
 * reads lives here so a tier is one row, not a dozen switch statements: easy is a slow, small-wave opponent for a
 * first match, medium is the old bot, hard is tuned to BARbarian-style pressure - early tech, big waves, raids, no
 * floating. None of it cheats: a hard bot gets the same income as the player and only spends it better.
 *
 * @param t2Minute        EARLIEST minute at which Tier 2 is researched (the T2 lab follows once it is done). Since the tier gate
 *                        (resources/TierGate) the research is also gated on readiness: a built army lab, {@code t2MinArmy}
 *                        fighters in the field, TierGate.BOT_MIN_METAL_INCOME and BOT_MIN_ENERGY_INCOME - the bot builds the
 *                        wind for that energy itself - or {@link #T2_PATIENCE_MINUTES} after t2Minute whatever the income
 *                        (a map with few patches must not skip T2 for ever). The identities hold: hard still techs first,
 *                        easy last, each ~1-2 minutes later than before because the research is 1000 metal, not 400
 * @param t3Minute        minute at which Tier 3 is researched and the T3 lab raised (never on easy)
 * @param workerBase      workers at the start of the match
 * @param workerPerMinute workers added per two minutes (the old clock rule)
 * @param workerPerIncome one more worker per this much metal income per second (BAR: constructors scale with eco)
 * @param workerCap       most workers the bot ever keeps
 * @param sitesBase       build sites run at once (plus one per 8 minutes, plus {@link #floatSites} while floating)
 * @param floatSites      extra build sites while metal floats, so the stockpile is turned into buildings fast
 * @param labsPerMinutes  one army lab per this many minutes (plus one more each time metal floats with every lab busy)
 * @param maxLabs         most army labs the bot raises
 * @param queueDepth      units kept queued in every lab (the lab never sits idle between units)
 * @param attackValueBase metal value of army that triggers the first wave
 * @param attackValuePerMinute growth of that threshold per minute (waves get bigger as the match goes on)
 * @param attackValueCap  the threshold never grows past this
 * @param attackCooldownTicks ticks between waves (a wave that reaches its target keeps going; this gates the next launch)
 * @param retreatRatio    enemy/own value around the army that makes a losing wave pull back (0 = never retreat)
 * @param raidCooldownTicks ticks between raids on enemy extractors (0 = no raids)
 * @param raidSize        fighters in a raid squad
 * @param scoutEveryTicks ticks between scout errands (0 = never scouts: the army finds the enemy by itself)
 * @param blindAttackMinute minute from which a wave may march on an enemy base it has not scouted yet (start
 *                        positions are public in the lobby, so this is a fallback, not a map hack)
 * @param defendValue     enemy metal value near home that pulls the whole army back (anything less meets a squad)
 * @param t2MinArmy       fighters the bot has fielded before it starts Tier 2 (a tier is for an army that already exists)
 */
public record BotTuning(int t2Minute, int t3Minute,
                        int workerBase, int workerPerMinute, float workerPerIncome, int workerCap,
                        int sitesBase, int floatSites, int labsPerMinutes, int maxLabs, int queueDepth,
                        int attackValueBase, int attackValuePerMinute, int attackValueCap, long attackCooldownTicks,
                        float retreatRatio, long raidCooldownTicks, int raidSize, long scoutEveryTicks,
                        int blindAttackMinute, int defendValue, int t2MinArmy) {

    public static final BotTuning EASY = new BotTuning(8, 99,
        5, 0, 0f, 10,
        1, 1, 6, 3, 2,
        600, 30, 1500, 20 * 240,
        0f, 0, 0, 0,
        6, 150, 4);

    public static final BotTuning MEDIUM = new BotTuning(6, 14,
        7, 1, 3f, 20,
        2, 1, 4, 6, 2,
        900, 50, 2600, 20 * 170,
        2.0f, 20 * 150, 3, 20 * 180,
        6, 150, 6);

    public static final BotTuning HARD = new BotTuning(4, 11,
        9, 2, 2f, 36,
        3, 2, 3, 12, 3,
        1100, 70, 3800, 20 * 100,
        1.8f, 20 * 90, 4, 20 * 120,
        6, 180, 8);

    /** Passive: Easy's economy; BotArmy never sends a wave, raid, scout or site squad whatever these numbers say. */
    public static final BotTuning PASSIVE = EASY;

    public static BotTuning of(BotPlayer.Difficulty difficulty) {
        return switch (difficulty) { case EASY -> EASY; case MEDIUM -> MEDIUM; case HARD -> HARD; case PASSIVE -> PASSIVE; };
    }

    /** The army metal value a wave waits for at this minute of the match. */
    public int attackValue(long minutes) {
        return (int) Math.min(attackValueCap, attackValueBase + minutes * attackValuePerMinute);
    }

    /** Minutes after t2Minute at which a bot techs whatever its income (the readiness rules never hold it for ever). */
    public static final int T2_PATIENCE_MINUTES = 6;

    /** Metal fraction of storage above which the bot counts itself as floating (BAR players sit well under this). */
    public static final float FLOAT_FRAC = 0.6f;
}
