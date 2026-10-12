package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.unit.goals.GenericTargetedSpellGoal;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * A faction hero (design/hero_mode.md, slice 2): one per faction, trained at its T1 army lab, revived at its level, four
 * ranked abilities, boons on level-up, capture-point XP and the tapering growth of {@link HeroProgression}. Everything
 * Reign of Nether's {@link HeroUnit} asks of a hero is answered here from the unit's {@link HeroCore}, so a hero class
 * is a body (a subclass of an existing unit: its model, renderer, goals and attack) plus a kit:
 * <pre>
 *   heroCore()                 lazily: new HeroCore(HeroKits.X, this)
 *   getAbilities()             heroCore().abilitySet
 *   updateAbilityButtons()     nothing (the abilities are the hero's own instances: ranks are keyed by them)
 *   tick()                     super.tick(); FactionHero.tickHero(this)
 *   resetBehaviours()          super.resetBehaviours(); FactionHero.resetHero(this)
 *   hurt(src, amount)          super.hurt(src, amount * heroCore().damageTakenMult)
 *   getCost()                  kit cost
 *   save / load                + addHeroUnitSaveData / FactionHero.loadHero
 *   createAttributes()         FactionHero.heroAttributes(Body.createAttributes(), kit)
 * </pre>
 */
public interface FactionHero extends HeroUnit {

    HeroCore heroCore();

    default HeroKit kit() { return heroCore().kit; }

    default GenericTargetedSpellGoal castGoal() { return heroCore().castGoal(); }

    // ---- HeroUnit state, from the core ----

    @Override default boolean needsStatSync() { return heroCore().needsStatSync; }
    @Override default void setNeedsStatSync(boolean value) { heroCore().needsStatSync = value; }
    @Override default int getSkillPoints() { return heroCore().skillPoints; }
    @Override default void setSkillPoints(int points) { heroCore().skillPoints = points; }
    @Override default boolean isRankUpMenuOpen() { return heroCore().rankUpMenuOpen; }
    @Override default void showRankUpMenu(boolean show) { heroCore().rankUpMenuOpen = show; }
    @Override default int getExperience() { return heroCore().experience; }
    @Override default Object2ObjectArrayMap<HeroAbility, Integer> getHeroAbilityRanks() { return heroCore().ranks; }
    @Override default float getMana() { return heroCore().mana; }
    @Override default float getMaxMana() { return heroCore().maxMana; }

    @Override
    default void setExperience(int amount) {
        HeroCore c = heroCore();
        int before = HeroUnit.getHeroLevel(c.experience);
        c.experience = amount;
        setStatsForLevel();
        LivingEntity le = (LivingEntity) this;
        if (le.level().isClientSide()) {
            int after = HeroUnit.getHeroLevel(amount);
            // the first sync after the hero comes into view is a load, not a boon
            if (c.clientSynced && after > before) {
                HeroProgression.clientBoon(le.getId(), after);
                com.solegendary.reignofnether.hud.NotificationClientEvents.onHeroBoon(getOwnerName());
            }
            c.clientSynced = true;
        }
    }

    @Override
    default void setMana(float amount) {
        HeroCore c = heroCore();
        c.mana = Math.max(0, Math.min(c.maxMana, amount));
        LivingEntity le = (LivingEntity) this;
        // a packet when it moved by a whole point or topped up, not on every regen tick of a full bar
        if (!le.level().isClientSide() && (Math.abs(c.mana - c.lastSentMana) >= 1 || (c.mana >= c.maxMana && c.lastSentMana != c.mana))) {
            c.lastSentMana = c.mana;
            com.solegendary.reignofnether.hero.HeroClientboundPacket.setMana(le.getId(), c.mana);
        }
    }

    @Override
    default void setMaxMana(float amount) {
        HeroCore c = heroCore();
        boolean changed = c.maxMana != amount;
        c.maxMana = amount;
        LivingEntity le = (LivingEntity) this;
        if (changed && !le.level().isClientSide())
            com.solegendary.reignofnether.hero.HeroClientboundPacket.setMaxMana(le.getId(), amount);
    }

    /** Health and damage from the kit times the level's multiplier (the tapering growth), mana grows linearly. */
    @Override
    default void setStatsForLevel(boolean heal) {
        LivingEntity le = (LivingEntity) this;
        HeroKit kit = kit();
        int level = getHeroLevel();
        float m = HeroProgression.statMultiplier(level);
        AttributeInstance hp = le.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null)
            hp.setBaseValue(kit.health * m);
        AttributeInstance dmg = le.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get());
        if (dmg != null)
            dmg.setBaseValue(kit.damage * m);
        AttributeInstance vdmg = le.getAttribute(Attributes.ATTACK_DAMAGE);
        if (vdmg != null)
            vdmg.setBaseValue(kit.damage * m);
        setMaxMana(kit.mana + (level - 1) * kit.manaPerLevel);
        if (le.getHealth() > le.getMaxHealth())
            le.setHealth(le.getMaxHealth());
        if (heal)
            le.setHealth(le.getMaxHealth());
    }

    /** Reign of Nether's level-up (points, sound, burst) plus the boon: the heal and the announcement. */
    @Override
    default void addExperience(int amount) {
        int before = getHeroLevel();
        HeroUnit.super.addExperience(amount);
        int after = getHeroLevel();
        if (after > before && !((LivingEntity) this).level().isClientSide())
            HeroProgression.onBoon(this, before, after);
    }

    // ---- what the unit classes call ----

    /** The hero attributes on top of the body's own: Reign of Nether's hero set plus the kit's stats. */
    static AttributeSupplier.Builder heroAttributes(AttributeSupplier.Builder body, HeroKit kit) {
        return body
            .add(Attributes.MAX_HEALTH, kit.health)
            .add(Attributes.ATTACK_DAMAGE, kit.damage)
            .add(Attributes.ARMOR, kit.armour)
            .add(Attributes.MOVEMENT_SPEED, kit.speed)
            .add(AttributeRegistrar.ATTACK_DAMAGE.get(), kit.damage)
            .add(AttributeRegistrar.BASE_MAX_HEALTH.get(), kit.health)
            .add(AttributeRegistrar.BASE_MAX_MANA.get(), kit.mana)
            .add(AttributeRegistrar.MANA_REGEN_PER_SECOND.get(), kit.manaRegen)
            .add(AttributeRegistrar.MAX_MANA_BONUS_PER_LEVEL.get(), kit.manaPerLevel)
            // growth is HeroProgression's multiplier, applied in setStatsForLevel: Reign of Nether's flat per-level
            // bonuses (read by AttackerUnit.getUnitAttackDamage) stay at zero so nothing counts twice
            .add(AttributeRegistrar.MAX_HEALTH_BONUS_PER_LEVEL.get(), 0)
            .add(AttributeRegistrar.ATTACK_DAMAGE_BONUS_PER_LEVEL.get(), 0);
    }

    /** Every tick, after the body's own tick. */
    static void tickHero(Mob mob) {
        if (!(mob instanceof FactionHero hero))
            return;
        HeroUnit.tick(hero);   // mana regen and the stat sync Reign of Nether's heroes do
        if (mob.level().isClientSide())
            return;
        HeroCore c = hero.heroCore();
        c.tickServer();
        // ranks, XP and points reach clients that only just started tracking the hero (Reign of Nether syncs on change)
        if (mob.tickCount == 20)
            hero.syncToClients();
        if (mob.tickCount % 20 == 7)
            HeroBot.tickHero(mob, hero);
    }

    /** A new order: the aimed cast in flight is dropped (a scripted leap finishes - it is already in the air). */
    static void resetHero(FactionHero hero) {
        hero.heroCore().stopCast();
    }

    /** Call from readAdditionalSaveData after the body's own. */
    static void loadHero(FactionHero hero, CompoundTag tag) {
        hero.readHeroUnitSaveData(tag);
        hero.setStatsForLevel();
        hero.setMana(tag.getFloat("mana"));
    }

    /** Call when the hero dies or is removed: its summons crumble. */
    static void onRemoved(FactionHero hero) {
        hero.heroCore().clearSummons();
    }
}
