package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.world.entity.LivingEntity;

/**
 * What one unit adds to its owner's population count on the client (the HUD, the observer panel). Summon tags stay on
 * the server, so the client counts the population the server last synced for the unit ({@link Summons#population}:
 * 0 for a summon); a unit not synced yet counts its cost until its first sync (at most a second).
 */
public final class PopulationTally {

    private PopulationTally() { }

    public static int populationOf(LivingEntity le, Int2IntMap synced) {
        if (synced.containsKey(le.getId()))
            return synced.get(le.getId());
        return le instanceof Unit u && u.getCost() != null ? u.getCost().population : 0;
    }
}
