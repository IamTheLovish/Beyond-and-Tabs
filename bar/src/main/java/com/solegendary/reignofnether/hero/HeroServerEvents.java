package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.items.UnitItems;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.commons.lang3.text.WordUtils;

import java.util.ArrayList;

public class HeroServerEvents {

    public static ArrayList<HeroUnitSave> fallenHeroes = new ArrayList<>();

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide())
            return;

        if (evt.getEntity() instanceof UnitInventory inv && inv.canUseUnitItems()) {
            ItemStack itemStack = inv.get(UnitItems.TOTEM_OF_UNDYING);
            if (itemStack != null) {
                evt.getEntity().setHealth(evt.getEntity().getMaxHealth() / 2);
                evt.getEntity().addEffect(new MobEffectInstance(MobEffectRegistrar.INVINCIBLE.get(),
                        UnitItems.TOTEM_OF_UNDYING_INVINCIBILITY_DURATION_SECONDS * 20, 0, true, false));
                itemStack.setCount(itemStack.getCount() - 1);
                if (itemStack.isEmpty())
                    inv.deleteItem(UnitItems.TOTEM_OF_UNDYING);
                evt.getEntity().level().broadcastEntityEvent(evt.getEntity(), (byte) 35);
                evt.setCanceled(true);
                return;
            }
        }

        // a commander or a hero fell (no totem saved it): a one-time XP burst for the enemy heroes near it
        HeroProgression.onDecisiveKill(evt.getEntity());

        Level level = evt.getEntity().level();
        // a summon's death pays no XP orbs (design/essence_casters.md Revision 1: a side must not feed its own free
        // units to its own heroes); a fallen summoned hero still gives the decisive-kill burst above
        if (evt.getEntity() instanceof Unit deadUnit && !com.solegendary.reignofnether.unit.Summons.isSummoned(evt.getEntity())) {
            int popCost = deadUnit.getCost().population;
            if (popCost > 0) {
                for (LivingEntity unit : UnitServerEvents.getAllUnits()) {
                    boolean inRange = unit.distanceToSqr((LivingEntity) deadUnit) < HeroExperienceOrb.RANGE * HeroExperienceOrb.RANGE;
                    // a fallen hero (its body stays in the unit list a moment) earns nothing: its record is written
                    if (unit instanceof HeroUnit heroUnit && unit.isAlive() && inRange && heroUnit != evt.getEntity()) {
                        String heroOwner = ((Unit) heroUnit).getOwnerName();
                        String deadOwner = deadUnit.getOwnerName();

                        if (!AlliancesServerEvents.isAllied(heroOwner, deadOwner) && !heroOwner.equals(deadOwner) &&
                                heroUnit.getHeroLevel() < HeroUnit.MAX_LEVEL &&
                                (heroUnit.getHeroLevel() < HeroUnit.MAX_NEUTRAL_EXP_LEVEL || !deadOwner.isBlank())) {

                            int expValue = (popCost + 1) * 5;
                            if (evt.getEntity() instanceof HeroUnit killedHero)
                                expValue += killedHero.getHeroLevel() * 5;

                            // paid now, in full (design-draft-heroes-better #8): a point is 10 XP from a neutral, 5
                            // from a player's unit - the orbs paid int(value / 2) each, so an odd last orb paid
                            // nothing, and the XP was lost when the hero fell before its orbs reached it
                            int xp = expValue * (deadOwner.isBlank() ? 10 : 5);
                            heroUnit.addExperience(xp);
                            HeroClientboundPacket.xpGained(((LivingEntity) heroUnit).getId(), heroOwner, xp);

                            while (expValue > 0) {   // the orbs are the look of it now: they pay nothing
                                HeroExperienceOrb expOrb = HeroExperienceOrb.visualOrb(level,
                                        heroUnit,
                                        evt.getEntity().getX(),
                                        evt.getEntity().getY(),
                                        evt.getEntity().getZ()
                                );
                                expValue -= expValue >= 2 ? 2 : 1;
                                evt.getEntity().level().addFreshEntity(expOrb);
                            }
                        }
                    }
                }
            }
        }
        // save killed hero unit for revival
        if (evt.getEntity() instanceof HeroUnit heroUnit) {
            String heroName = ((LivingEntity) heroUnit).getType().getDescriptionId();
            fallenHeroes.removeIf(fHero -> fHero.ownerName.equals(heroUnit.getOwnerName()) && fHero.name.equals(heroName));

            NonNullList<ItemStack> items = heroUnit instanceof UnitInventory inv ?
                    inv.getAllItems() : NonNullList.withSize(UnitInventory.MAX_INVENTORY_SIZE, ItemStack.EMPTY);

            HeroUnitSave fallenHero = new HeroUnitSave(
                    ((Entity) heroUnit).getStringUUID(),
                    heroName,
                    heroUnit.getOwnerName(),
                    heroUnit.getExperience(),
                    heroUnit.getSkillPoints(),
                    0,
                    heroUnit.getHeroAbilities().size() > 0 ? heroUnit.getHeroAbilities().get(0).getRank(heroUnit) : 0,
                    heroUnit.getHeroAbilities().size() > 1 ? heroUnit.getHeroAbilities().get(1).getRank(heroUnit) : 0,
                    heroUnit.getHeroAbilities().size() > 2 ? heroUnit.getHeroAbilities().get(2).getRank(heroUnit) : 0,
                    heroUnit.getHeroAbilities().size() > 3 ? heroUnit.getHeroAbilities().get(3).getRank(heroUnit) : 0,
                    items
            );
            fallenHeroes.add(fallenHero);
            FallenHeroClientboundPacket.addFallenHero(fallenHero);
            UnitServerEvents.saveFallenHeroUnits((ServerLevel) evt.getEntity().level());   // marked dirty, no disk write mid-fight

            for (RTSPlayer rtsPlayer : PlayerServerEvents.rtsPlayers) {
                if (rtsPlayer.name.equals(heroUnit.getOwnerName()) ||
                    AlliancesServerEvents.isAllied(rtsPlayer.name, heroUnit.getOwnerName())) {
                    PlayerServerEvents.sendMessageToPlayer(rtsPlayer.name, "hud.hero.reignofnether.death", true,
                            heroUnit.getOwnerName(),
                            WordUtils.capitalize(MiscUtil.getSimpleEntityName(evt.getEntity()).replace("_", " ")),
                            heroUnit.getHeroLevel());
                    SoundClientboundPacket.playSoundForPlayer(SoundAction.ENEMY, rtsPlayer.name);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        for (HeroUnitSave fallenHero : fallenHeroes) {
            if (evt.getEntity() instanceof ServerPlayer sp &&
                fallenHero.ownerName.equals(sp.getName().getString())) {
                FallenHeroClientboundPacket.addFallenHero(sp, fallenHero);
            }
        }
    }
}
