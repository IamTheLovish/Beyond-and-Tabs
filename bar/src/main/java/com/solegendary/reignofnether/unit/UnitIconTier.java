package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.brass.RepairCrawlerUnit;
import com.solegendary.reignofnether.unit.units.monsters.CreeperUnit;
import com.solegendary.reignofnether.unit.units.monsters.WraithUnit;
import com.solegendary.reignofnether.unit.units.piglins.BlazeUnit;
import com.solegendary.reignofnether.unit.units.piglins.MagmaCubeUnit;
import com.solegendary.reignofnether.unit.units.storm.SeerUnit;
import com.solegendary.reignofnether.unit.units.verdant.BloomPriestessUnit;
import com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit;
import com.solegendary.reignofnether.unit.units.villagers.WindcallerUnit;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import net.minecraft.world.entity.LivingEntity;

/**
 * How important a unit is for the strategic (zoomed-out) icon view, BAR-style: a Spring icontype's size grows
 * with the unit's tier, so a T3 experimental or the commander reads as a big mark among the T1 chaff and the
 * eye finds what matters first. Pure functions, server-safe (GameTest: StrategicIconGameTests).
 *
 * There is no tier field on a unit: the tier is which lab trains it (tools/unit_stats.py ROSTER). Client-side
 * the cheapest faithful proxy is metal cost - every experimental costs {@code 1840} and nothing else is above
 * {@code 780}, and T1 tops out at {@code 170} (the Sentinel Treant) - with the ten T2 units priced inside the
 * T1 band listed explicitly. Workers (T1 builders, T2 constructors, combat engineers) are their own bucket.
 */
public final class UnitIconTier {

    public static final int WORKER = 0, T1 = 1, T2 = 2, T3 = 3, HERO = 4;

    /** Metal at or above which a unit reads as T3: every experimental is 1840, the dearest T2 (a beast) 780. */
    public static final int T3_METAL = 1000;
    /** Metal at or above which a unit reads as T2: the dearest T1 is 170; the T2 heavies and beasts start at 180. */
    public static final int T2_METAL = 180;
    /** Icon radius multiplier per tier (times the player's Icon size setting). */
    public static final float SIZE_WORKER = 0.9f, SIZE_T1 = 1f, SIZE_T2 = 1.25f, SIZE_T3 = 1.6f, SIZE_HERO = 1.6f;
    /** A unit at least this wide keeps its model further out than the infantry (it is still readable as a model). */
    public static final float BIG_BB_WIDTH = 1.5f;

    private UnitIconTier() { }

    /** WORKER, T1, T2, T3 or HERO (heroes and the commander). Cheap: a few instanceof checks and a static cost. */
    public static int tierOf(LivingEntity entity) {
        return tierOf(entity, CommanderServerEvents.looksLikeCommander(entity));
    }

    /** As {@link #tierOf(LivingEntity)} for a caller that has already made the commander check. */
    public static int tierOf(LivingEntity entity, boolean commander) {
        if (commander || entity instanceof HeroUnit)
            return HERO;
        if (entity instanceof WorkerUnit)
            return WORKER;
        if (!(entity instanceof Unit unit))
            return T1;
        int metal = metalOf(unit);
        if (metal >= T3_METAL)
            return T3;
        if (metal >= T2_METAL || isCheapT2(entity))
            return T2;
        return T1;
    }

    /** Pure: the icon radius multiplier for a tier from {@link #tierOf}. */
    public static float iconSize(int tier) {
        return switch (tier) {
            case WORKER -> SIZE_WORKER;
            case T2 -> SIZE_T2;
            case T3 -> SIZE_T3;
            case HERO -> SIZE_HERO;
            default -> SIZE_T1;
        };
    }

    /** Pure: which tier a metal price alone implies (T1 below {@link #T2_METAL}, T3 from {@link #T3_METAL}). */
    public static int tierForMetal(int metal) {
        return metal >= T3_METAL ? T3 : metal >= T2_METAL ? T2 : T1;
    }

    /**
     * Big units (T3, heroes, the commander, anything {@link #BIG_BB_WIDTH} blocks wide or more) stay models until
     * a further-out zoom than the infantry: see StrategicViewClientEvents.BIG_UNIT_ZOOM_MULT.
     */
    public static boolean isBig(LivingEntity entity) {
        return isBig(entity, tierOf(entity));
    }

    /** As {@link #isBig(LivingEntity)} with the tier already known. */
    public static boolean isBig(LivingEntity entity, int tier) {
        return tier == T3 || tier == HERO || entity.getBbWidth() >= BIG_BB_WIDTH;
    }

    private static int metalOf(Unit unit) {
        ResourceCost cost = unit.getCost();
        return cost == null ? 0 : cost.ore;   // after ResourceCost.bakeValues the ore field is Metal
    }

    // the T2 units priced inside the T1 band (tools/unit_stats.py: 75-175 metal): a lab's roster, not a price list
    private static boolean isCheapT2(LivingEntity e) {
        return e instanceof CreeperUnit || e instanceof WraithUnit            // Gravebound Dungeon
                || e instanceof BlazeUnit || e instanceof MagmaCubeUnit      // Horde Flame Sanctuary
                || e instanceof WitchUnit || e instanceof WindcallerUnit     // Sunforged Arcane Tower
                || e instanceof WispChoirUnit || e instanceof BloomPriestessUnit   // Verdant Circle of Elders
                || e instanceof SeerUnit                                     // Stormcallers Observatory
                || e instanceof RepairCrawlerUnit;                           // Brassworks Assembly Works
    }
}
