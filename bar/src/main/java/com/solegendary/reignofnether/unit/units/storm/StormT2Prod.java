package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.storm.CelestialObservatory;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The Celestial Observatory's production items (design/stormcallers_plan.md, slice 3: the Stormcallers T2 army and the
 * Ley Master): {@link StormProd}'s buttons plus the server-side Tier 2 gate ({@link CelestialObservatory#tier2Error}),
 * the twin of TideT2Prod / VerdantT2Prod. The build button only greys the Observatory without Tier 2; this check runs
 * on the server whenever production starts, so an Observatory that exists without the research (a scenario, a cheat,
 * a research reset) still cannot train T2.
 */
public abstract class StormT2Prod extends StormProd {

    protected StormT2Prod(String name, String entityId, String mobhead, int tooltipLines, ResourceCost cost,
                          Supplier<? extends EntityType<? extends Unit>> type, boolean spawnIndoors) {
        super(name, entityId, mobhead, tooltipLines, cost, type, spawnIndoors);
    }

    /** No Tier 2, no T2: checked by ProductionPlacement.startProductionItem on the server, and greys the button. */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return CelestialObservatory.tier2Error(pp);
    }
}
