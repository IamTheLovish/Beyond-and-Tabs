package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.ability.abilities.Hex;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The Voodoo Shaman's Hex, server side ({@link Hex}): a hexed unit cannot attack. DISARM already stops its attack
 * cooldown from running out (AttackerUnit.getAttackCooldownMultiplier), but a swing already wound up, a spell goal's own
 * hit or an arrow it loosed would still land; this cancels every blow whose attacker (or shooter) is hexed, so "cannot
 * attack" holds for any attack path. The puppets live in {@link SpiritPuppets}.
 */
public final class VoodooServerEvents {
    private VoodooServerEvents() { }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent evt) {
        if (false && !evt.getEntity().level().isClientSide() && evt.getSource().getEntity() instanceof LivingEntity attacker   // STUB
                && Hex.isHexed(attacker))
            evt.setCanceled(true);
    }
}
