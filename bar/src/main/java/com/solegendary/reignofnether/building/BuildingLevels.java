package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.shared.LabAnnex;
import com.solegendary.reignofnether.resources.ResourceCost;

import java.util.List;

/**
 * In-place building levels (design/building_upgrades.md): lovish wanted fewer, stronger labs instead of BAR's carpet
 * of labs and nano turrets. A levelled building ({@link Building#maxLevel} > 1) goes L1 -> L2 -> L3 through an
 * upgrade order in its own production queue (UpgradeLabLevel); each level is its own, taller structure
 * ({@link Building#getLevelStructureName}) and the level is read back from the structure a placement wears, so
 * the save and the late-joiner sync need nothing new. Annexes (LabAnnex) add build power on top.
 *
 * All numbers live here; the design doc explains them.
 */
public final class BuildingLevels {
    private BuildingLevels() { }

    public static final int MAX_LEVEL = 3;

    /** Production build power by level (index = level): 100% / 175% / 250% of the lab's base speed. */
    static final float[] BUILD_POWER = { 1.0f, 1.0f, 1.75f, 2.5f };
    /** Max HP multiplier by level. */
    static final float[] HP_MULT = { 1.0f, 1.0f, 1.5f, 2.0f };

    /** Upgrade to L2 / L3: metal and energy flow-paid while the upgrade runs, like any production item.
     *  (ResourceCost.Research takes food, wood = energy, ore = metal, seconds: no RoN conversion applies.) */
    public static final ResourceCost UPGRADE_L2_COST = ResourceCost.Research(0, 360, 90, 40);
    public static final ResourceCost UPGRADE_L3_COST = ResourceCost.Research(0, 720, 180, 60);

    /** Each annex adds this much of the lab's base build power; at most ANNEX_SLOTS count per lab. */
    public static final float ANNEX_BUILD_POWER = 0.4f;
    public static final int ANNEX_SLOTS = 2;

    public static float buildPowerAt(int level) {
        return BUILD_POWER[Math.max(1, Math.min(MAX_LEVEL, level))];
    }

    public static float hpMultiplier(int level) {
        return HP_MULT[Math.max(1, Math.min(MAX_LEVEL, level))];
    }

    /** The cost of going from {@code level} to {@code level + 1}, or null at the top. */
    public static ResourceCost upgradeCost(int level) {
        return level == 1 ? UPGRADE_L2_COST : level == 2 ? UPGRADE_L3_COST : null;
    }

    /** Metal put into levels up to {@code level} (for the reclaim refund). */
    public static int levelMetalInvested(int level) {
        int m = 0;
        if (level >= 2) m += UPGRADE_L2_COST.metal();
        if (level >= 3) m += UPGRADE_L3_COST.metal();
        return m;
    }

    /** "barracks_l2" -> 2, "graveyard_l3" -> 3, anything else 1. Allocation-free: called per tick per lab. */
    public static int levelOfStructure(String structureName) {
        if (structureName == null)
            return 1;
        int n = structureName.length();
        if (n > 3 && structureName.charAt(n - 3) == '_' && structureName.charAt(n - 2) == 'l') {
            char c = structureName.charAt(n - 1);
            if (c >= '2' && c <= '9')
                return c - '0';
        }
        return 1;
    }

    /** Every L2 / L3 structure name the registered levelled buildings use (the structure GameTest checks them all). */
    public static List<String> allLevelStructureNames() {
        List<String> names = new java.util.ArrayList<>();
        for (Building b : com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING)
            for (int lv = 2; lv <= b.maxLevel; lv++) {
                String s = b.getLevelStructureName(lv);
                if (!names.contains(s))
                    names.add(s);
            }
        return names;
    }

    // ------------------------------------------------------------------ production speed (levels + annexes)

    /**
     * The build power a completed factory applies to its production queue: its level's speed plus its annexes.
     * Annex links are found by proximity (LabAnnex.hostOf), identical on server and client, and cached for a second
     * per lab so the per-tick production step stays a field read.
     */
    public static float productionBuildPower(BuildingPlacement lab) {
        Building b = lab.getBuilding();
        if (b.maxLevel <= 1)
            return 1.0f;
        float p = buildPowerAt(lab.getBuildingLevel());
        return p + ANNEX_BUILD_POWER * annexCount(lab);
    }

    /** Built annexes attached to this lab (0..ANNEX_SLOTS), cached for 20 ticks. */
    public static int annexCount(BuildingPlacement lab) {
        if (lab.getLevel() == null)
            return 0;
        long now = lab.getLevel().getGameTime();
        if (lab.annexCacheTick >= 0 && now - lab.annexCacheTick < 20 && now >= lab.annexCacheTick)
            return lab.annexCacheCount;
        int n = 0;
        // owner and range first: hostOf walks every building itself, so asking it for every annex on the map made a
        // refresh O(buildings x annexes) per lab - at 8v8 late game the biggest building-side cost (CR-09). Same
        // answer: hostOf only ever returns an own lab within ANNEX_RANGE.
        for (BuildingPlacement other : BuildingUtils.getBuildingsList(lab.getLevel().isClientSide()))
            if (other.isBuilt && other.getBuilding() instanceof LabAnnex && other.ownerName.equals(lab.ownerName)
                    && LabAnnex.gap(other, lab) <= LabAnnex.ANNEX_RANGE && LabAnnex.hostOf(other) == lab)
                n++;
        lab.annexCacheCount = Math.min(ANNEX_SLOTS, n);
        lab.annexCacheTick = now;
        return lab.annexCacheCount;
    }

    /** True if this placement is a completed lab that can take annexes / levels (an army lab with maxLevel > 1). */
    public static boolean isLevelledLab(BuildingPlacement bp) {
        return bp instanceof ProductionPlacement && bp.getBuilding().maxLevel > 1;
    }

    // ------------------------------------------------------------------ load / sync

    /**
     * After a save load or a late-joiner's place-building packet: put a levelled building back into its level's
     * structure. {@code upgradeCode} is the getUpgradeLevel() value that was saved / synced.
     */
    public static void restore(BuildingPlacement placement, int upgradeCode) {
        Building b = placement.getBuilding();
        if (b.maxLevel <= 1)
            return;
        int level = b.getLevelFromUpgradeCode(upgradeCode);
        if (level > 1 && placement.getBuildingLevel() != level)
            placement.changeStructure(b.getLevelStructureName(level));
    }

    /** Server: raise a lab one level (the UpgradeLabLevel order completing). Returns false at the top level. */
    public static boolean levelUp(BuildingPlacement placement) {
        Building b = placement.getBuilding();
        int level = placement.getBuildingLevel();
        if (level >= b.maxLevel)
            return false;
        placement.changeStructure(b.getLevelStructureName(level + 1));
        return placement.getBuildingLevel() == level + 1;
    }
}
