package com.solegendary.reignofnether.raid;

import com.solegendary.reignofnether.unit.units.brass.MortarCrawlerUnit;
import com.solegendary.reignofnether.unit.units.piglins.HoglinUnit;
import com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit;
import com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit;
import com.solegendary.reignofnether.unit.units.tide.MudskipperSloopUnit;
import com.solegendary.reignofnether.unit.units.verdant.SentinelTreantUnit;

import net.minecraft.world.entity.Entity;

/**
 * The one place the early-raid numbers live (lovish, Oct 22: "Tier 1 units should be able to raid ... killing workers
 * early sets people behind" / "if you get tier 1 vehicles out, they will be able to kill buildings easier" /
 * "converters can be weak to punish players" / "extractors can be weak like in BAR ... maybe not as weak").
 * <p>
 * The roles, in one table:
 * <ul>
 *   <li><b>T1 infantry raiders</b> (Spider, Marauder, Leafblade, Cutlass Raider, Blade Dancer, Steam Runner): fast, hunt
 *       workers. They can hurt buildings, only slowly - nothing here makes a building easier for them.</li>
 *   <li><b>T1 vehicles</b> ({@link #isT1Vehicle}: the beasts and siege of the T1 labs): break buildings. The melee
 *       ones get a building-damage bonus worked out from {@link #VEHICLE_BUILDING_HPS}; the artillery already lands
 *       its shells on buildings at {@code BombardShell.BUILDING_DAMAGE_MULT}. Their damage against units is untouched.</li>
 *   <li><b>Fragile eco</b>: the converter (the one weak building, plus a small chain blast), then the T1 extractor
 *       (softer than the rest, not as weak as a converter), the T2 extractor about twice that.</li>
 * </ul>
 * Building health here is the number the HUD shows: a hit of A damage takes 2 A health off it (BuildingPlacement:
 * minBlocksPercent 0.5), and an infantry blow is the unit's damage x 0.25 (Building.getMeleeDamageMult), so a raider
 * does {@code damage x attacks/s x 0.5} health per second to a building (a Spider: 1.5).
 */
public final class RaidTuning {
    private RaidTuning() { }

    // ---------------------------------------------------------------- fragile economy buildings (health)

    // (The game tests measure these in play: a fight runs about a third quicker than health / (units x damage rate),
    // because the first blow of every unit lands at once and a building falls at half its blocks. The values below
    // were set from those measurements - CI's "[Raid TTK]" notice - not from the arithmetic.)

    /** Energy converter: the fragile one - 4-6 infantry raiders razing it takes about 4-6 s. (It was 30: 6 raiders razed that in 2.2 s.) */
    public static final double CONVERTER_HEALTH = 50d;
    /** Windmill: 3-4 T1 vehicles take 5-8 s. (It was 30, which 6 raiders razed in 3 s.) */
    public static final double WIND_HEALTH = 110d;
    /** T1 metal extractor: softer than the rest, not as fragile as a converter - 4-6 infantry raiders take 12-18 s. (It was 40.) */
    public static final double EXTRACTOR_T1_HEALTH = 150d;
    /** Added once the extractor is T2 (a copper tower over the T1): twice the T1's health in all. */
    public static final double EXTRACTOR_T2_BONUS_HEALTH = 150d;

    // ---------------------------------------------------------------- converter chain blast

    /** A dying converter damages every other converter whose centre is within this many blocks of its own (3x3 bodies one gap apart are inside, a spaced field is not). */
    public static final double CONVERTER_BLAST_RADIUS = 5.0;
    /** The blast's damage (the attacker-less destroyRandomBlocks amount, health x2): enough to finish a 50-health converter (a building falls at half its blocks: 25 damage). */
    public static final double CONVERTER_BLAST_DAMAGE = 28.0;
    /** The bot spaces its own converters this far apart (centre to centre): clear of the blast with a block to spare. */
    public static final int CONVERTER_SPACING = 8;

    // ---------------------------------------------------------------- vehicles

    /**
     * Health per second one T1 vehicle takes off a building with its normal attack: 3 of them raze a windmill in about
     * 7 s and a T1 extractor in about 9 s, 4 in 5 and 7 s. An infantry raider does 1.5-2.
     */
    public static final float VEHICLE_BUILDING_HPS = 3.7f;

    /** What a melee blow's damage is multiplied by on a building (Building.getMeleeDamageMult) and the health that costs (x2). */
    private static final float MELEE_BUILDING_MULT = 0.25f, HEALTH_PER_DAMAGE = 2f;

    /**
     * The BUILDING_DAMAGE_BONUS a melee vehicle with this attack needs to do {@link #VEHICLE_BUILDING_HPS} to a
     * building (the attribute is added to 1: 2.0 = double damage). Never negative: a unit that already out-hits the
     * target keeps what it has. Damage against units never reads it.
     */
    public static float vehicleBuildingBonus(float attackDamage, float attacksPerSecond) {
        float base = attackDamage * attacksPerSecond * MELEE_BUILDING_MULT * HEALTH_PER_DAMAGE;
        return base <= 0 ? 0 : Math.max(0f, VEHICLE_BUILDING_HPS / base - 1f);
    }

    /**
     * The T1 vehicles: the melee beasts (Hoglin, Sentinel Treant, Mudskipper Sloop), the T1 artillery (Bombard Crew,
     * Rocket Battery, Mortar Crawler) and every faction's siege lobber (design/siege_t1.md).
     */
    public static boolean isT1Vehicle(Entity e) {
        return e instanceof HoglinUnit || e instanceof SentinelTreantUnit || e instanceof MudskipperSloopUnit
            || e instanceof BombardCrewUnit || e instanceof RocketBatteryUnit || e instanceof MortarCrawlerUnit
            || e instanceof com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit;
    }

    // ---------------------------------------------------------------- workers

    /** Workers that an enemy unit hurts run to the nearest own tower or the capitol (WorkerFlee). Hold position (H) keeps one at work. */
    public static final boolean WORKERS_FLEE = true;
    /** A worker flees for this long after its last hurt (ticks) and re-checks every {@link #FLEE_CHECK_TICKS}. */
    public static final int FLEE_MEMORY_TICKS = 60, FLEE_CHECK_TICKS = 5;
    /** The refuge (an own built tower or the capitol) must be within this many blocks of the worker. */
    public static final double FLEE_RANGE = 48.0;
    /** A worker this close to its refuge has arrived. */
    public static final double FLEE_ARRIVED = 6.0;
}
