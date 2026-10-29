package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.world.entity.LivingEntity;

/**
 * STUB (summons take no population): what one unit adds to its owner's population count on the client. Today's
 * behaviour, its cost; the rule lands in the next commit.
 */
public final class PopulationTally {

    private PopulationTally() { }

    public static int populationOf(LivingEntity le, Int2IntMap synced) {
        return le instanceof Unit u && u.getCost() != null ? u.getCost().population : 0;
    }
}
