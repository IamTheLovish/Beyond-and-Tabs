package com.solegendary.reignofnether.brass;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side event hooks of the Brassworks' units (registered in ServerEventRegistrar). A venting {@link Machine}
 * takes {@link Heat#VENT_DAMAGE_TAKEN}x damage (design/brassworks_plan.md section 3), and the Iron Leviathan Engine
 * takes its BELOW_DAMAGE_MULT from Sappers and Drill Burrowers (slice 4): one instanceof per hurt event for everyone
 * else, like StormUnitEvents.
 */
public class BrassUnitEvents {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!(evt.getEntity() instanceof Machine m) || evt.getEntity().level().isClientSide())
            return;
        float amount = evt.getAmount();
        if (Heat.isVenting(m))
            amount *= Heat.VENT_DAMAGE_TAKEN;
        // the Iron Leviathan Engine is weak from below: Sappers and Drill Burrowers strike its boilers (slice 4)
        if (m instanceof com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit
                && com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.isStruckFromBelow(evt.getSource()))
            amount *= com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit.BELOW_DAMAGE_MULT;
        if (amount != evt.getAmount())
            evt.setAmount(amount);
    }
}
