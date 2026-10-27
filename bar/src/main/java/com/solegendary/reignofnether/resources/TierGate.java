package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.research.ResearchServerEvents;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Tier 2 is an investment (lovish, Oct 22: "Tier 2 is too easy to access and people rush it rather than making
 * anything tier 1"; "even in BAR, having 6 or even 7 metal extractors makes it more difficult to level to tier 2; it
 * takes time"; "you need to build your energy ... before you can supply tier 2 metal extractors"; "converters can be
 * weak to punish players"). Then lovish, Oct 24: "tier 2 ... rendering tier 1 ineffective" - it still came too fast
 * and the refit's jump (2 -> 7 metal/s) made a T1 base pointless, so the refit is softer (4.5 metal/s, was 7; 300 metal
 * and 75 s, were 180 and 45), the research slower (180 s, was 120; same cost) and the bots tech a minute later
 * (BotTuning t2Minute easy 9 / medium 7 / hard 5, were 8 / 6 / 4). Every number of that gate lives HERE, one place,
 * the same for all seven factions: {@link #bake()} writes the costs into the shared ResourceCost objects after the
 * config is read, the T2 labs' build time comes from {@link #T2_LAB_BUILD_BLOCKS}, the extractors' energy drain and the
 * converter's rate are read from the constants below, and the bots' readiness rules use the income figures at the bottom.
 * <p>
 * <b>No extractor-count rule.</b> The gate is cost, time and energy, as in BAR: a player with more extractors simply
 * pays it sooner, a player with fewer waits, and a player who skips the army is slower to defend what it paid for.
 *
 * <h3>The income maths (the PR table is made by tools/t2_gate_sim.py from the same numbers)</h3>
 * A normal opener is the capitol (2 metal/s, 20 energy/s) plus 6-7 T1 extractors (2 metal/s each, so 14-16 metal/s)
 * and 5-7 wind generators (14 energy/s on average: wind swings 4-24). A T1 army unit is 50-100 metal / 70-130
 * energy and a lab spends about 2.4 metal/s on it, so a real T1 army (3-5 labs) spends 7-12 of those 16 metal/s.
 * <ul>
 *   <li><b>Tier 2 research</b> {@value #T2_RESEARCH_METAL} metal / {@value #T2_RESEARCH_ENERGY} energy over
 *       {@value #T2_RESEARCH_SECONDS} s: it drains 5.6 metal/s and 56 energy/s while it runs (a time-based job at the
 *       capitol: a second worker cannot help, and the capitol trains no workers meanwhile - for three minutes now, not
 *       two). 5.6 metal/s is a third of a normal economy, so a player who also feeds 3+ labs runs it at a stall and it
 *       takes longer than 180 s.</li>
 *   <li><b>The T2 lab</b> {@value #T2_LAB_METAL} metal / {@value #T2_LAB_ENERGY} energy, built like any building: its
 *       work is {@value #T2_LAB_BUILD_BLOCKS} blocks x 21 ticks = 504 builder-seconds, so one worker needs 8.4 minutes,
 *       the commander + 3 workers (6 build power) 84 s, and a dozen workers assisting 42 s - assisting speeds it up
 *       (BAR). While it rises it drains 700 / that time: 8.3 metal/s at 6 build power, 17 at 12.</li>
 *   <li>The package is {@value #T2_PACKAGE_METAL} metal / {@value #T2_PACKAGE_ENERGY} energy: 100 s of a 16 metal/s
 *       economy and 1.9 minutes of a 118 energy/s one (capitol + 7 winds) - before it was 645 metal / 1420 energy, 40 s
 *       of metal. The T3 package (research 1320 / 3600 + lab 420 / 600 = 1740 metal) is untouched and stays above it.</li>
 *   <li><b>Energy is a real gate</b> (the owner's correction): research + lab pull 44-90 energy/s on top of the
 *       40-60 energy/s a base spends on labs, units and buildings, so research and lab together flow at full speed only
 *       above ~110-120 energy/s: capitol + 7 winds. And a refitted extractor drains {@value
 *       #T2_EXTRACTOR_ENERGY_UPKEEP} energy/s for its +2.5 metal/s (T1: {@value #T1_EXTRACTOR_ENERGY_UPKEEP}): six of
 *       them need 120 energy/s more, another 8-9 winds, or they run at the fraction energy can supply - the metal does
 *       not come until the energy does.</li>
 *   <li><b>The refit</b> {@value #UPGRADE_EXTRACTOR_METAL} metal / {@value #UPGRADE_EXTRACTOR_ENERGY} energy over
 *       {@value #UPGRADE_EXTRACTOR_SECONDS} s, with the drill stopped meanwhile (150 metal of T1 output lost): 450 metal
 *       back at +2.5 metal/s is 3 minutes, so refitting is a mid-game investment, not a free doubling.</li>
 *   <li><b>The converter is a last resort</b>: {@value #CONVERTER_ENERGY_PER_METAL} energy per metal at most
 *       {@value #CONVERTER_ENERGY_PER_SECOND} energy/s a building = 0.5 metal/s each (was 1.0): a metal/s costs 2
 *       converters (150 metal) and 100 energy/s of winds (7 winds, 350 metal), ~500 metal against 70 for an extractor's
 *       2 metal/s - it cannot stand in for extractors or for the energy build-up.</li>
 * </ul>
 * Arrival (the T2 lab finished; the nominal fluid model of tools/t2_gate_sim.py): an all-in eco rush (7 extractors,
 * no army) reached it at 4:32 before and gets it at 5:03-5:24 after (4-7 winds; more winds only delay it, because
 * they are built first); a player with a real T1 army (labs 1/2/3 at 0:30/2:30/4:30) who decides on T2 at 4:30 got it
 * at 6:59 and now at 7:26-7:28. Oct 24 (180 s research): the rush 6:03-6:24, the army player 8:26-8:28; and the
 * army player who then refits all seven extractors as the energy allows earns 21.5 metal/s at minute 12 (was 51)
 * and finishes the refits at 16:22 (was 11:45), against 16 for the same base at tier 1 (tools/t2_gate_sim.py
 * --refits). The model keeps metal near the storage cap, so the research's 180 s and the lab's
 * build time are the floors; a player who spends the metal on units instead arrives later (minute 9-12, the bots'
 * readiness rules). The nightly bot ladder reports the bots' T2 minute so these can be tuned from data.
 */
public final class TierGate {
    private TierGate() { }

    // ---- Tier 2 research (the capitol) ----
    public static final int T2_RESEARCH_METAL = 1000;
    public static final int T2_RESEARCH_ENERGY = 10080;   // was 8000 (lovish, Oct 27: keep 56 energy/s at 180 s)
    public static final int T2_RESEARCH_SECONDS = 180;   // was 120 (lovish, Oct 24)

    // ---- the T2 lab (every faction's: Arcane Tower, Dungeon, Flame Sanctuary, Circle of Elders, Drydock, Celestial
    // Observatory, Assembly Works). Its build time is a fixed block count, the same for all of them: the art's block
    // counts differ 6x (63-402 blocks) and buildTimeModifier was hand-tuned per faction (0.7-2.0), so builds took 130-295
    // builder-seconds depending on the faction.
    public static final int T2_LAB_METAL = 700;
    public static final int T2_LAB_ENERGY = 6000;
    public static final int T2_LAB_BUILD_BLOCKS = 480;   // x Building.BUILD_TICKS_PER_BLOCK (21) = 504 builder-seconds

    public static final int T2_PACKAGE_METAL = T2_RESEARCH_METAL + T2_LAB_METAL;
    public static final int T2_PACKAGE_ENERGY = T2_RESEARCH_ENERGY + T2_LAB_ENERGY;

    // ---- the in-place extractor refit (Oct 22: 180 metal / 600 energy / 45 s; lovish, Oct 24: 300 / 600 / 75, for
    // 4.5 metal/s instead of 7 - MetalExtractor.T2_METAL_INCOME) ----
    public static final int UPGRADE_EXTRACTOR_METAL = 300;
    public static final int UPGRADE_EXTRACTOR_ENERGY = 600;
    public static final int UPGRADE_EXTRACTOR_SECONDS = 75;

    // ---- extractors drain energy while they run (EconomyServerEvents.payExtractorUpkeep): the metal they make scales
    // with the share of the drain the player's energy can pay, so a T2 economy needs an energy economy under it ----
    public static final float T1_EXTRACTOR_ENERGY_UPKEEP = 0f;    // energy/s each: a normal opener has no energy gate
    public static final float T2_EXTRACTOR_ENERGY_UPKEEP = 20f;   // energy/s each, for 4.5 metal/s (T1: 2)

    // ---- the energy converter: a last resort, not a second economy ----
    public static final float CONVERTER_ENERGY_PER_METAL = 100f;   // was 50
    public static final float CONVERTER_ENERGY_PER_SECOND = 50f;   // the most one building drains: 0.5 metal/s

    // ---- what a bot wants before it techs (BotPlayer): the incomes above, as readiness rules ----
    /** Metal/s before researching Tier 2: the capitol + five T1 extractors. */
    public static final float BOT_MIN_METAL_INCOME = 12f;
    /** Energy/s before researching Tier 2: the research's 56 energy/s (67 before Oct 24, 44 on Oct 24-27) on top of the base's own
     *  spending, with the T2 lab's pull right after it - kept at 110, the lab needs it next. */
    public static final float BOT_MIN_ENERGY_INCOME = 110f;
    /** Energy/s a base spends on its own before refits: labs, units and builds. */
    public static final float BOT_BASE_ENERGY_SPEND = 60f;

    /** Energy income a bot wants before refitting its {@code refitted}th extractor (counting the one about to start). */
    public static float botEnergyIncomeForRefit(int refitted) {
        return BOT_BASE_ENERGY_SPEND + refitted * T2_EXTRACTOR_ENERGY_UPKEEP;
    }

    /** The error shown when a T2 building / refit is wanted without the research. */
    public static final String NEEDS_TIER_2 = "building.reignofnether.needs_tier_2";
    /** The error shown when a T2 lab is wanted without its faction's finished T1 lab. */
    public static final String NEEDS_T1_LAB = "building.reignofnether.needs_t1_lab";

    // ---------------------------------------------------------------------------------------------------- costs

    /**
     * Writes the gate's costs into the shared cost objects. ResourceCosts.deferredLoadResourceCosts calls it last, after
     * the config bake, so these constants win over the config entries (which still hold Reign of Nether's old prices):
     * one tuning place, and a tester's old config file cannot hold the gate open.
     */
    public static void bake() {
        set(ResourceCosts.RESEARCH_TIER_2, T2_RESEARCH_METAL, T2_RESEARCH_ENERGY, T2_RESEARCH_SECONDS);
        set(ResourceCosts.UPGRADE_EXTRACTOR, UPGRADE_EXTRACTOR_METAL, UPGRADE_EXTRACTOR_ENERGY, UPGRADE_EXTRACTOR_SECONDS);
        for (ResourceCost lab : t2LabCosts())
            set(lab, T2_LAB_METAL, T2_LAB_ENERGY, 0);
    }

    private static void set(ResourceCost cost, int metal, int energy, int seconds) {
        cost.ore = metal;      // metal lives in the ore field, energy in the wood field (ResourceCost.bakeValues)
        cost.wood = energy;
        cost.ticks = seconds * ResourceCost.TICKS_PER_SECOND;
    }

    private static List<ResourceCost> t2LabCosts() {
        return List.of(ResourceCosts.ARCANE_TOWER, ResourceCosts.DUNGEON, ResourceCosts.FLAME_SANCTUARY,
            ResourceCosts.CIRCLE_OF_ELDERS, ResourceCosts.DRYDOCK, ResourceCosts.CELESTIAL_OBSERVATORY,
            ResourceCosts.ASSEMBLY_WORKS);
    }

    // ---------------------------------------------------------------------------------------------------- the rule

    /**
     * The finished building a T2 lab needs (its faction's T1 army lab - the Horde's is the Hoglin Stables, as its build
     * button has always asked), or null when {@code b} is not a T2 lab. One table for all seven factions.
     */
    @Nullable
    public static Building t1LabFor(Building b) {
        if (b == Buildings.ARCANE_TOWER) return Buildings.BARRACKS;                // Sunforged
        if (b == Buildings.DUNGEON) return Buildings.GRAVEYARD;                     // Gravebound
        if (b == Buildings.FLAME_SANCTUARY) return Buildings.HOGLIN_STABLES;        // Horde
        if (b == Buildings.CIRCLE_OF_ELDERS) return Buildings.GROVE;                // Verdant
        if (b == Buildings.DRYDOCK) return Buildings.SLIPWAY;                       // Tidewrought
        if (b == Buildings.CELESTIAL_OBSERVATORY) return Buildings.LOTUS_ACADEMY;   // Stormcallers
        if (b == Buildings.ASSEMBLY_WORKS) return Buildings.MACHINE_SHOP;           // Brassworks
        return null;
    }

    public static boolean isT2Lab(Building b) {
        return t1LabFor(b) != null;
    }

    /** Every faction's T2 lab, in faction order (the GameTests walk it). */
    public static List<Building> t2Labs() {
        return List.of(Buildings.ARCANE_TOWER, Buildings.DUNGEON, Buildings.FLAME_SANCTUARY, Buildings.CIRCLE_OF_ELDERS,
            Buildings.DRYDOCK, Buildings.CELESTIAL_OBSERVATORY, Buildings.ASSEMBLY_WORKS);
    }

    /** True when {@code owner} has a finished {@code building} (their own; an ally's does not count on the server). */
    public static boolean ownsFinished(String owner, Building building) {
        if (owner == null)
            return false;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
            if (bp.isBuilt && !bp.isDestroyedServerside && owner.equals(bp.ownerName) && bp.getBuilding() == building)
                return true;
        return false;
    }

    /**
     * Server side, on placement: null when {@code owner} may place {@code b}, else the message key. Anything that is
     * not a T2 lab always may. A T2 lab needs the Tier 2 research and its faction's finished T1 lab - the twin of the
     * build button's grey-out, which a crafted packet or a stale client skips.
     */
    @Nullable
    public static String placementError(String owner, Building b) {
        Building prerequisite = t1LabFor(b);
        if (prerequisite == null)
            return null;
        if (!ResearchServerEvents.playerHasResearch(owner, ProductionItems.RESEARCH_TIER_2))
            return NEEDS_TIER_2;
        if (!ownsFinished(owner, prerequisite))
            return NEEDS_T1_LAB;
        return null;
    }

    /**
     * Server side, when an item is queued: a T2 lab produces nothing without Tier 2 (a lab that exists without the
     * research - a save from before this gate, a cheat that was switched off), and the extractor refit needs it too.
     * Null when the item may start.
     */
    @Nullable
    public static String productionError(ProductionPlacement pp, ProductionItem item) {
        boolean t2Item = isT2Lab(pp.getBuilding()) || item == ProductionItems.UPGRADE_EXTRACTOR;
        if (t2Item && !ResearchServerEvents.playerHasResearch(pp.ownerName, ProductionItems.RESEARCH_TIER_2))
            return NEEDS_TIER_2;
        return null;
    }
}
