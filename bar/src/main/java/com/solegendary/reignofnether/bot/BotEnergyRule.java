package com.solegendary.reignofnether.bot;

/**
 * The bots' energy-before-extractor rule (lovish, Oct 29: energy scales like BAR - "you can make 5 metal extractors and
 * other buildings without having to make an energy building" must stop being true, for the bots too). BAR's opening is
 * 2-3 mexes, then winds or solars, then more mexes: a bot that builds its next extractor before the energy to carry it
 * runs at a stall (resources/EnergyCosts: an extractor is 300 energy to build and 3 energy/s to run). Pure, so
 * BotEnergyRuleTest pins it.
 * <p>
 * The energy a base needs: {@link #ENERGY_PER_EXTRACTOR} for every extractor (its 3 energy/s upkeep and its share of the
 * construction around it) plus {@link #ENERGY_PER_LAB} for every lab training units (a Halberdier at 8 energy per metal
 * draws ~24 energy/s). The first {@link #FREE_EXTRACTORS} extractors go up on the capitol's income and the bank alone, as
 * in BAR.
 */
public final class BotEnergyRule {
    private BotEnergyRule() { }

    public static final float ENERGY_PER_EXTRACTOR = 6f;
    public static final float ENERGY_PER_LAB = 24f;
    /** Extractors a bot raises before it asks for energy: BAR's "2-3 mexes, then energy". */
    public static final int FREE_EXTRACTORS = 2;

    /** Energy/s a base with these extractors and labs wants (the next extractor counted when {@code nextExtractor}). */
    public static float energyWanted(int extractors, int labs, boolean nextExtractor) {
        return ENERGY_PER_EXTRACTOR * (extractors + (nextExtractor ? 1 : 0)) + ENERGY_PER_LAB * labs;
    }

    /**
     * Whether the bot should raise energy before its next extractor: past the free ones, when the energy it has and is
     * raising (counted at the average wind) is short of what the base wants with one more extractor.
     */
    public static boolean energyBeforeNextExtractor(int extractors, int labs, float plannedEnergyIncome) {
        if (extractors < FREE_EXTRACTORS)
            return false;
        return plannedEnergyIncome < energyWanted(extractors, labs, true);
    }
}
