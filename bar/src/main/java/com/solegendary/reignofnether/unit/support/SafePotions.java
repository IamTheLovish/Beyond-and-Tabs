package com.solegendary.reignofnether.unit.support;

import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.SafePotionsAbility;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.entities.AdjustableAreaEffectCloud;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The Field Medic's <b>Safe potions</b> switch (SafePotionsAbility; claude/healer-ai-oct21.md, C - default on, lovish's
 * open call): a harming cloud she throws while it is on spares her side. ThrownPotionMixin marks the cloud with her
 * owner ({@link AdjustableAreaEffectCloud#sparesFriendsOf}); the cloud's instant damage reaches a unit as
 * {@code indirectMagic(cloud, medic)}, and that hit is cancelled here at the very start of {@code hurt} - no damage, no
 * flinch, no red flash, no retaliation - for any unit of the same owner or an ally. Undead friends are healed by
 * harming as before (vanilla heals them without a hurt). Registered in ServerEventRegistrar.
 */
public final class SafePotions {
    private SafePotions() { }

    /** Whether this medic's clouds spare her side (the switch on her card; on unless the player turned it off). */
    public static boolean isOn(WitchUnit witch) {
        return AutocastState.isOn(witch, SafePotionsAbility.INSTANCE);
    }

    /** The owner whose side a cloud thrown by {@code witch} spares, or null (switch off). */
    public static String sparedSideOf(WitchUnit witch) {
        String owner = witch.getOwnerName();
        return owner != null && !owner.isBlank() && isOn(witch) ? owner : null;
    }

    /** Whether a unit owned by {@code victimOwner} is on {@code side}'s side (the same owner, or allied). */
    public static boolean friendly(String side, String victimOwner) {
        return victimOwner != null && (side.equals(victimOwner) || AlliancesServerEvents.isAllied(side, victimOwner));
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide())
            return;
        if (!(evt.getSource().getDirectEntity() instanceof AdjustableAreaEffectCloud cloud) || cloud.sparesFriendsOf == null)
            return;
        if (evt.getEntity() instanceof Unit victim && friendly(cloud.sparesFriendsOf, victim.getOwnerName()))
            evt.setCanceled(true);
    }
}
