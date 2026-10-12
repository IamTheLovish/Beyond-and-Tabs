package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * The skirmish bot's use of its faction hero (design/hero_mode.md, slice 2): it trains the hero mid-game (Tier 2
 * researched and {@link #TRAIN_FROM_MINUTE} minutes in) at whichever of its labs offers it, revives it when it falls,
 * spends its ability points (the utility fourth as soon as it can, then the lowest-ranked of the other three, so every
 * ability is usable) and casts simply: strikes at the nearest enemy in range, gap closers for the melee heroes, its
 * self-centred abilities when a fight is on round it. Never while a player possesses the hero.
 */
public final class HeroBot {
    private HeroBot() { }

    public static final int TRAIN_FROM_MINUTE = 5;
    static final double FIGHT_RADIUS = 16;

    /** BotPlayer.think, once per think: queue the hero (or its revive) when the bot is ready for one. */
    public static void think(String bot, Faction faction, List<BuildingPlacement> buildings, boolean tier2, long minutes) {
        if (faction == null || !tier2 || minutes < TRAIN_FROM_MINUTE)
            return;
        HeroKit kit = HeroKits.forFaction(faction.getName());
        HeroProd prod = HeroProd.forKit(kit);
        HeroReviveProd revive = HeroReviveProd.forKit(kit);
        if (prod == null)
            return;
        if (!HeroUnit.getHeroes(false, bot, "entity.reignofnether." + kit.entityId()).isEmpty())
            return;
        boolean fallen = HeroUnit.getFallenHero(false, bot, "entity.reignofnether." + kit.entityId()) != null;
        for (BuildingPlacement bp : buildings)
            if (bp instanceof ProductionPlacement pp)
                for (ActiveProduction ap : pp.productionQueue)
                    if (ap.item == prod || ap.item == revive)
                        return;   // already on its way
        for (BuildingPlacement bp : buildings) {
            if (!bp.isBuilt || !(bp instanceof ProductionPlacement pp) || !(bp.getBuilding() instanceof ProductionBuilding pb))
                continue;
            if (fallen && revive != null && pb.productions.get().contains(revive)) {
                if (pp.startProductionItem(revive))
                    return;
            } else if (!fallen && pb.productions.get().contains(prod)) {
                if (pp.startProductionItem(prod))
                    return;
            }
        }
    }

    /** Every second, for a hero whose owner is a bot and nobody possesses. */
    static void tickHero(Mob mob, FactionHero hero) {
        String owner = hero.getOwnerName();
        if (owner == null || owner.isBlank() || !BotServerEvents.brains.containsKey(owner) || PossessionServer.isPossessed(mob))
            return;
        spendPoints(hero);
        useAbilities(mob, hero);
    }

    /** The utility fourth as soon as it may be ranked, then the lowest-ranked learnable of the other three. */
    public static void spendPoints(FactionHero hero) {
        for (int guard = 0; guard < 12 && hero.getSkillPoints() > 0; guard++) {
            FactionHeroAbility best = null;
            for (FactionHeroAbility a : hero.heroCore().abilities) {
                if (a.getRank(hero) >= a.maxRank || hero.getHeroLevel() < a.getLevelRequirement(hero))
                    continue;
                if (a.slot == 3) {
                    best = a;
                    break;
                }
                if (best == null || a.getRank(hero) < best.getRank(hero))
                    best = a;
            }
            if (best == null || !best.rankUp(hero))
                return;
        }
    }

    static void useAbilities(Mob mob, FactionHero hero) {
        if (!(mob.level() instanceof ServerLevel sl))
            return;
        HeroCore core = hero.heroCore();
        if (core.inMotion() || (core.castGoal != null && (core.castGoal.isCasting() || core.castGoal.getMoveTarget() != null)))
            return;   // one thing at a time
        LivingEntity enemy = HeroFx.nearestEnemy(sl, mob, mob.position(), FIGHT_RADIUS);
        if (enemy == null)
            return;
        double d = enemy.distanceTo(mob);
        boolean melee = hero.kit().role == HeroKit.Role.VANGUARD || hero.kit().role == HeroKit.Role.RAIDER;
        for (FactionHeroAbility a : core.abilities) {
            if (a.getRank(hero) <= 0 || !a.isOffCooldown(hero) || hero.getMana() < a.manaCost)
                continue;
            boolean used = switch (a.spec.ai) {
                case STRIKE -> {
                    if (d > a.range + 2)
                        yield false;
                    if (a.spec.target == HeroKit.Target.ENEMY)
                        a.use(sl, hero, enemy);
                    else
                        a.use(sl, hero, enemy.getOnPos());
                    yield true;
                }
                case ENGAGE -> {
                    if (!melee || d < 4 || d > a.range)
                        yield false;
                    a.use(sl, hero, enemy.getOnPos());
                    yield true;
                }
                case FIGHT_SELF -> {
                    if (d > Math.max(6, a.spec.radius))
                        yield false;
                    yield a.castNow(hero, null, mob.position());
                }
                case FIGHT_GROUND -> {
                    if (d > 12)
                        yield false;
                    // summons and turrets go toward the enemy, heals and cover at the hero's own feet
                    boolean forward = a.spec.id.equals("raise_the_fallen") || a.spec.id.equals("deploy_turret");
                    a.use(sl, hero, forward && d <= a.range ? enemy.getOnPos() : mob.getOnPos());
                    yield true;
                }
                case NONE -> false;
            };
            if (used)
                return;
        }
    }
}
