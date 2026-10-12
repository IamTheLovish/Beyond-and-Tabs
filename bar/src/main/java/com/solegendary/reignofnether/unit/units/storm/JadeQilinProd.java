package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/** Stormcallers T2 beast (design/big_units.md 1.6): a jade-scaled qilin, a walking Mana Well with a spring of energy. Trained at the Celestial Observatory. */
public class JadeQilinProd extends StormT2Prod {

    public final static String itemName = "Jade Qilin";
    public final static ResourceCost cost = ResourceCosts.JADE_QILIN;

    public JadeQilinProd() {
        super(itemName, "jade_qilin_unit", "jade_qilin", 3, cost, () -> EntityRegistrar.JADE_QILIN_UNIT.get(), false);
    }
}
