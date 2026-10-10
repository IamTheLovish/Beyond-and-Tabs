package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.tide.Drydock;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The Drydock's production items (design/tidewrought_plan.md, slice 3: the Tidewrought T2 army and the Harbourmaster):
 * {@link TideProd}'s buttons plus the server-side Tier 2 gate ({@link Drydock#tier2Error}), the twin of VerdantT2Prod.
 * The build button only greys the Drydock without Tier 2; this check runs on the server whenever production starts,
 * so a Drydock that exists without the research (a scenario, a cheat, a research reset) still cannot train T2.
 */
public abstract class TideT2Prod extends TideProd {

    protected TideT2Prod(String name, String entityId, String mobhead, int tooltipLines, ResourceCost cost,
                         Supplier<? extends EntityType<? extends Unit>> type, boolean spawnIndoors) {
        super(name, entityId, mobhead, tooltipLines, cost, type, spawnIndoors);
    }

    /** No Tier 2, no T2: checked by ProductionPlacement.startProductionItem on the server, and greys the button. */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return Drydock.tier2Error(pp);
    }
}
