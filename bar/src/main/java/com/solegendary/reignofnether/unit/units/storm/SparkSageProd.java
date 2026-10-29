package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

/**
 * Stormcallers T2 essence caster: Static Step, Captured Spark, Thunderhead and Empower, paid in the essence the fallen
 * drop (SparkSageUnit). Does not fight. Trained at the Celestial Observatory once Tier 2 is researched (StormT2Prod).
 */
public class SparkSageProd extends StormT2Prod {

    public final static String itemName = "Spark Sage";
    public final static ResourceCost cost = ResourceCosts.SPARK_SAGE;

    public SparkSageProd() {
        super(itemName, "spark_sage_unit", "spark_sage", 3, cost, () -> EntityRegistrar.SPARK_SAGE_UNIT.get(), true);
    }
}
