package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.TierGate;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;

/**
 * A unit's tech tier, 1-3, for the abilities that only work on lower tiers (the Voodoo Shaman's Hex: T1-T2, Spirit
 * Puppet: T1). By where it is trained, the way a player reads it: a unit of a T2 lab ({@link TierGate#t2Labs}) is T2, one
 * of a faction's T3 building (the bot kits' {@code t3Building}, or the kit's {@code t3Unit}) is T3, any other trained
 * unit is T1. Heroes and commanders are T3 (never a low-tier target). A unit nobody trains (a summon, a neutral, a
 * capture-camp recruit) falls back to its cost: {@link BarFx#tierOf} (T2 from 120 metal, T3 from 500). Cached per type.
 */
public final class UnitTier {
    private UnitTier() { }

    private static final Map<EntityType<?>, Integer> BY_TYPE = new HashMap<>();

    public static int of(LivingEntity e) {
        if (e instanceof HeroUnit || CommanderServerEvents.isCommander(e))
            return 3;
        if (!(e instanceof Mob mob) || !(e instanceof Unit unit))
            return 3;
        Integer cached = BY_TYPE.get(e.getType());
        if (cached != null)
            return cached;
        int tier = byLab(mob);
        if (tier == 0) {
            ResourceCost c = unit.getCost();
            tier = BarFx.tierOf(c == null ? 0 : c.metal());
        }
        BY_TYPE.put(e.getType(), tier);
        return tier;
    }

    /** 1-3 by the building that trains it, 0 when nothing does. */
    @SuppressWarnings("unchecked")
    private static int byLab(Mob mob) {
        ProductionItem item = ProductionItems.getProductionItem((EntityType<? extends Mob>) mob.getType());
        if (item == null)
            return 0;
        for (Building lab : TierGate.t2Labs()) {
            BotPlayer.Kit kit = BotPlayer.kitFor(lab.getFaction());
            if (kit != null && (kit.t3Unit() == item || trains(kit.t3Building(), item)))
                return 3;
        }
        for (Building lab : TierGate.t2Labs())
            if (trains(lab, item))
                return 2;
        return 1;
    }

    private static boolean trains(Building b, ProductionItem item) {
        return b instanceof ProductionBuilding pb && pb.productions.get().contains(item);
    }
}
