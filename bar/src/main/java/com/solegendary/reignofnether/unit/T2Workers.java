package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.Buildings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * BAR-style T2 constructors: only they can raise a faction's T3 lab (Castle / Fortress / Stronghold / Heart of the Wild /
 * Abyssal Shipyard - the Tidewrought's needs the Harbourmaster; Heaven's Gate - the Stormcallers' needs the Ley Master).
 */
public final class T2Workers {
    private T2Workers() { }

    public static boolean isT2Worker(Entity e) {
        return e instanceof com.solegendary.reignofnether.unit.units.villagers.RoyalArchitectUnit
            || e instanceof com.solegendary.reignofnether.unit.units.monsters.EmbalmerUnit
            || e instanceof com.solegendary.reignofnether.unit.units.piglins.BonewrightUnit
            || e instanceof com.solegendary.reignofnether.unit.units.verdant.ElderDruidUnit
            || e instanceof com.solegendary.reignofnether.unit.units.tide.HarbourmasterUnit
            || e instanceof com.solegendary.reignofnether.unit.units.storm.LeyMasterUnit
            || e instanceof com.solegendary.reignofnether.unit.units.brass.EngineerCaptainUnit;
    }

    /** The T3 labs, which only a T2 constructor may place. */
    public static boolean isT3Lab(Building b) {
        return b == Buildings.CASTLE || b == Buildings.STRONGHOLD || b == Buildings.FORTRESS
            || b == Buildings.HEART_OF_THE_WILD || b == Buildings.ABYSSAL_SHIPYARD || b == Buildings.HEAVENS_GATE;
    }

    /** Buildings only a T2 constructor may place: the T3 labs, the Geothermal Plant (design/lively_buildings.md) and the game enders (design/big_units.md). */
    public static boolean needsT2Builder(Building b) {
        return isT3Lab(b) || b instanceof com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant
            || b instanceof com.solegendary.reignofnether.building.gameender.GameEnder;   // big-units: game enders
    }

    /**
     * Server side: may these builders place this building? Anything that doesn't need a T2 builder always may; a T3
     * lab or a Geothermal Plant needs a T2 constructor among the builders (the server-side twin of the build button's
     * selectedHasT2Worker check).
     */
    public static boolean buildersMayPlace(ServerLevel level, Building b, int[] builderUnitIds) {
        if (!needsT2Builder(b))
            return true;
        if (level == null || builderUnitIds == null)
            return false;
        for (int id : builderUnitIds)
            if (isT2Worker(level.getEntity(id)))
                return true;
        return false;
    }

    /** Client side: is a T2 constructor among the selected units (the build menu belongs to the selection)? */
    public static boolean selectedHasT2Worker() {
        for (LivingEntity le : UnitClientEvents.getSelectedUnits())
            if (isT2Worker(le))
                return true;
        return false;
    }
}
