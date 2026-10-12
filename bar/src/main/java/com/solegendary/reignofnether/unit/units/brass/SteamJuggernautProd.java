package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Brassworks T2 tank: a boiler-plated war engine that shrugs off arrows and vents after a long brawl. Trained at the Assembly Works once Tier 2 is researched (BrassT2Prod). */
public class SteamJuggernautProd extends BrassT2Prod {

    public final static String itemName = "Steam Juggernaut";
    public final static ResourceCost cost = ResourceCosts.STEAM_JUGGERNAUT;

    public SteamJuggernautProd() {
        super(itemName, "steam_juggernaut_unit", "steam_juggernaut", 3, cost, () -> EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), false);
    }
}
