package com.solegendary.reignofnether.brass;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side event hooks of the Brassworks' units (registered in ServerEventRegistrar). A venting {@link Machine}
 * takes {@link Heat#VENT_DAMAGE_TAKEN}x damage (design/brassworks_plan.md section 3): one instanceof per hurt event
 * for everyone else, like StormUnitEvents.
 */
public class BrassUnitEvents {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!(evt.getEntity() instanceof Machine m) || evt.getEntity().level().isClientSide() || !Heat.isVenting(m))
            return;
        evt.setAmount(evt.getAmount() * Heat.VENT_DAMAGE_TAKEN);
    }
}
