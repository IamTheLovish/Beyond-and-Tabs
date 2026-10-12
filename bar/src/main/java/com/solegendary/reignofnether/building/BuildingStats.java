package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.shared.LabAnnex;
import com.solegendary.reignofnether.resources.ResourceCost;

import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place that says what a building's numbers are (design/building_upgrades.md, "Stats display"): level, HP,
 * production speed (levels + annexes), income, the next level's cost and effect, and what reclaiming it returns.
 * The hover card, the portrait title and the Upgrade / Decommission tooltips all read from here, so they can never
 * disagree with each other or with the server's numbers (BuildingLevels, BuildingReclaim).
 *
 * The string helpers are client-side (I18n); the numeric ones are side-neutral.
 */
public final class BuildingStats {
    private BuildingStats() { }

    // ------------------------------------------------------------------ numbers (side-neutral)

    public static int level(BuildingPlacement bp) {
        return bp.getBuilding().maxLevel > 1 ? bp.getBuildingLevel() : 0;
    }

    /** Production speed in percent of the lab's base (levels + annexes), or 0 for a building that produces nothing. */
    public static int productionPercent(BuildingPlacement bp) {
        if (!(bp instanceof ProductionPlacement) || bp.getBuilding().maxLevel <= 1)
            return 0;
        return Math.round(bp.getProductionBuildPower() * 100f);
    }

    /** Metal a Decommission of this building would return (BuildingReclaim.REFUND_SHARE of the metal put in). */
    public static int reclaimValue(BuildingPlacement bp) {
        return BuildingReclaim.refundFor(bp);
    }

    // ------------------------------------------------------------------ text (client)

    /** " (Lv 2)" for a levelled building, "" otherwise - appended to the building's name. */
    public static String titleSuffix(BuildingPlacement bp) {
        int lv = level(bp);
        return lv > 0 ? " " + I18n.get("hud.reignofnether.stats.level_short", lv) : "";
    }

    /**
     * One compact line for the hover card: speed / income, and (for your own) the reclaim value. Reuse the
     * returned string while the inputs are unchanged - the card caches it per building and refreshes it slowly.
     */
    public static String summaryLine(BuildingPlacement bp, boolean owned) {
        StringBuilder sb = new StringBuilder();
        int lv = level(bp);
        if (lv > 0)
            sb.append(I18n.get("hud.reignofnether.stats.level", lv, bp.getBuilding().maxLevel));
        int prod = productionPercent(bp);
        if (prod > 0)
            append(sb, I18n.get("hud.reignofnether.stats.speed", prod));
        if (bp.getBuilding() instanceof LabAnnex)
            append(sb, I18n.get("hud.reignofnether.stats.annex", Math.round(BuildingLevels.ANNEX_BUILD_POWER * 100)));
        if (bp.isBuilt) {
            float m = bp.getMetalIncome(), e = bp.getEnergyIncome();
            if (m > 0)
                append(sb, "+" + trim(m) + " " + I18n.get("hud.reignofnether.metal_per_s"));
            if (e > 0)
                append(sb, "+" + trim(e) + " " + I18n.get("hud.reignofnether.energy_per_s"));
        }
        if (owned && bp.isBuilt && BuildingReclaim.canEverDecommission(bp))
            append(sb, I18n.get("hud.reignofnether.stats.reclaim", reclaimValue(bp)));
        return sb.toString();
    }

    /** The Upgrade order's description: what the lab is at {@code toLevel - 1} and what it becomes. */
    public static List<String> upgradeLines(BuildingPlacement bp, int toLevel) {
        List<String> lines = new ArrayList<>();
        int from = toLevel - 1;
        int hpFrom = (int) Math.round(bp.getBuilding().maxHealth * BuildingLevels.hpMultiplier(from));
        int hpTo = (int) Math.round(bp.getBuilding().maxHealth * BuildingLevels.hpMultiplier(toLevel));
        lines.add(I18n.get("hud.reignofnether.stats.upgrade_speed",
            Math.round(BuildingLevels.buildPowerAt(from) * 100), Math.round(BuildingLevels.buildPowerAt(toLevel) * 100)));
        if (hpFrom > 0)
            lines.add(I18n.get("hud.reignofnether.stats.upgrade_hp", hpFrom, hpTo));
        lines.add(I18n.get("hud.reignofnether.stats.upgrade_shape"));
        ResourceCost next = BuildingLevels.upgradeCost(toLevel);
        if (next != null)
            lines.add(I18n.get("hud.reignofnether.stats.upgrade_next", toLevel + 1, next.metal(), next.energy()));
        return lines;
    }

    /** The Decommission button's description. */
    public static List<String> reclaimLines(BuildingPlacement bp) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.get("hud.reignofnether.stats.reclaim", reclaimValue(bp)));
        lines.add(I18n.get("hud.reignofnether.decommission.tooltip1", Math.round(BuildingReclaim.REFUND_SHARE * 100)));
        lines.add(I18n.get("hud.reignofnether.decommission.tooltip2"));
        lines.add(I18n.get("hud.reignofnether.decommission.tooltip3"));
        return lines;
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0)
            sb.append("  ");
        sb.append(s);
    }

    private static String trim(float f) {
        return f == Math.floor(f) ? String.valueOf((int) f) : String.valueOf(Math.round(f * 10) / 10f);
    }
}
