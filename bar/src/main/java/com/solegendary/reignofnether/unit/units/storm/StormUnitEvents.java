package com.solegendary.reignofnether.unit.units.storm;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side event hooks of the Stormcallers' units (registered in ServerEventRegistrar). Today only the Jade Guard's
 * Jade Mirror ({@link JadeGuardUnit#mirror}); one instanceof per hurt event for everyone else.
 */
public class StormUnitEvents {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!(evt.getEntity() instanceof JadeGuardUnit guard) || guard.level().isClientSide())
            return;
        float left = JadeGuardUnit.mirror(guard, evt.getSource(), evt.getAmount());
        if (left != evt.getAmount())
            evt.setAmount(left);
    }
}
