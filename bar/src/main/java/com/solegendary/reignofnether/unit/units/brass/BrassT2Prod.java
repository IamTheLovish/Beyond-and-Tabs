package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.building.buildings.brass.AssemblyWorks;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The Assembly Works' production items (design/brassworks_plan.md, slice 2: the Brassworks T2 army and the
 * Engineer-Captain): {@link BrassProd}'s buttons plus the server-side Tier 2 gate ({@link AssemblyWorks#tier2Error}),
 * the twin of StormT2Prod / TideT2Prod. The build button only greys the Assembly Works without Tier 2; this check runs
 * on the server whenever production starts, so a Works that exists without the research (a scenario, a cheat, a
 * research reset) still cannot train T2.
 */
public abstract class BrassT2Prod extends BrassProd {

    protected BrassT2Prod(String name, String entityId, String mobhead, int tooltipLines, ResourceCost cost,
                          Supplier<? extends EntityType<? extends Unit>> type, boolean spawnIndoors) {
        super(name, entityId, mobhead, tooltipLines, cost, type, spawnIndoors);
    }

    /** No Tier 2, no T2: checked by ProductionPlacement.startProductionItem on the server, and greys the button. */
    @Nullable
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return AssemblyWorks.tier2Error(pp);
    }
}
