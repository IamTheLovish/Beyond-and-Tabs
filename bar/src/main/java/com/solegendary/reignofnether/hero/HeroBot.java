package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The skirmish bot's use of its faction hero (design/hero_mode.md, slice 2): it trains the hero mid-game (Tier 2
 * researched and {@link #TRAIN_FROM_MINUTE} minutes in) at whichever of its labs offers it, revives it when it falls,
 * spends its ability points (the utility fourth as soon as it can, then the lowest-ranked of the other three, so every
 * ability is usable) and fights with them ({@link #useAbilities}). The same fight brain drives a human player's hero
 * while nobody possesses it and the player's Hero AI switch is on (points stay manual for players); possessing the
 * hero hands it straight to the player, and letting go hands it back.
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

    /**
     * The players who switched their Hero AI off (lovish, Oct 24 2026: heroes "use their abilities and act extremely
     * smart when there is no hero player this can be a toggle players can set"). On by default, so only the off
     * switches are kept; the client sends its setting (ReignOfNetherClientConfigs.HERO_AI) when it logs in and when it
     * changes. A bot's hero ignores it.
     */
    private static final Set<String> AI_OFF = ConcurrentHashMap.newKeySet();

    public static void setAiEnabled(String player, boolean on) {
        if (player == null)
            return;
        if (on)
            AI_OFF.remove(player);
        else
            AI_OFF.add(player);
    }

    public static boolean isAiEnabled(String player) {
        return player != null && !AI_OFF.contains(player);
    }

    /**
     * Every second, for a hero nobody possesses: a bot's hero spends its points and fights; a player's hero fights while
     * its owner's Hero AI is on, and its points stay the player's to spend (the rank-up choice is part of playing a hero).
     */
    static void tickHero(Mob mob, FactionHero hero) {
        String owner = hero.getOwnerName();
        if (owner == null || owner.isBlank() || PossessionServer.isPossessed(mob))
            return;
        if (BotServerEvents.brains.containsKey(owner))
            spendPoints(hero);
        else if (!isAiEnabled(owner))
            return;
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

    // ---- the fight brain: one decision a second, the most urgent first --------------------------------------------
    static final double LOW_HP = 0.30;        // below this the hero saves itself first
    static final double HURT_HP = 0.70;       // a friend below this is worth a heal
    static final double ULT_RADIUS = 14;      // a "real fight" for the fourth: this many enemies this close ...
    static final int ULT_CROWD = 3;
    static final double ESCAPE_DISTANCE = 10;

    /** What the hero sees this second. */
    record Fight(ServerLevel level, Mob mob, FactionHero hero, List<LivingEntity> enemies, List<LivingEntity> friends,
                 double hp, boolean melee) {
        int enemiesWithin(Vec3 c, double r) {
            int n = 0;
            for (LivingEntity e : enemies)
                if (e.position().distanceToSqr(c) <= r * r)
                    n++;
            return n;
        }

        int hurtFriendsWithin(Vec3 c, double r) {
            int n = 0;
            for (LivingEntity f : friends)
                if (f.position().distanceToSqr(c) <= r * r && fraction(f) < HURT_HP)
                    n++;
            return n;
        }

        /** Away from the enemies' centre, {@code dist} blocks out (where an escape lands or a retreat walks to). */
        Vec3 awayFromEnemies(double dist) {
            Vec3 c = Vec3.ZERO;
            for (LivingEntity e : enemies)
                c = c.add(e.position());
            c = c.scale(1.0 / Math.max(1, enemies.size()));
            Vec3 dir = mob.position().subtract(c).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1e-4)
                dir = new Vec3(1, 0, 0);
            return mob.position().add(dir.normalize().scale(dist));
        }
    }

    static double fraction(LivingEntity e) {
        return e.getMaxHealth() <= 0 ? 1 : e.getHealth() / e.getMaxHealth();
    }

    /**
     * The hero's abilities, used the way a good player would (design/hero_mode.md "Hero AI"): save itself when low
     * (escape away, heal, shield), heal hurt friends, keep the fourth (the long-cooldown utility) for a real fight,
     * area spells only where they hit several, strikes at the best target (one it can finish, an enemy hero or
     * commander, the weakest, a cluster for the chaining and splash ones), gap closers only when healthy and not into a
     * crowd, and mana kept back for the escape while it is hurt. Bots and players' heroes share it.
     */
    static void useAbilities(Mob mob, FactionHero hero) {
        if (!(mob.level() instanceof ServerLevel sl))
            return;
        HeroCore core = hero.heroCore();
        if (core.inMotion() || (core.castGoal != null && (core.castGoal.isCasting() || core.castGoal.getMoveTarget() != null)))
            return;   // one thing at a time
        boolean melee = hero.kit().role == HeroKit.Role.VANGUARD || hero.kit().role == HeroKit.Role.RAIDER;
        Fight f = new Fight(sl, mob, hero, HeroFx.enemies(sl, mob, mob.position(), FIGHT_RADIUS),
            HeroFx.friends(sl, mob, mob.position(), FIGHT_RADIUS), fraction(mob), melee);
        List<FactionHeroAbility> ready = new ArrayList<>();
        for (FactionHeroAbility a : core.abilities)
            if (a.getRank(hero) > 0 && a.isOffCooldown(hero) && hero.getMana() >= a.manaCost)
                ready.add(a);
        if (ready.isEmpty())
            return;
        if (f.hp < LOW_HP && !f.enemies.isEmpty() && saveItself(f, ready))
            return;
        if (heal(f, ready))
            return;
        if (f.enemies.isEmpty())
            return;
        // while hurt, keep the mana for the way out
        float reserve = 0;
        if (f.hp < 0.6)
            for (FactionHeroAbility a : core.abilities)
                if (isEscape(a) && a.getRank(hero) > 0)
                    reserve = Math.max(reserve, a.manaCost);
        for (FactionHeroAbility a : ready) {
            if (isEscape(a) || (reserve > 0 && hero.getMana() - a.manaCost < reserve))
                continue;
            if (fight(f, a))
                return;
        }
    }

    /** Ground-aimed movers with no fight use of their own: the hero's way out. */
    static boolean isEscape(FactionHeroAbility a) {
        return a.spec.ai == HeroKit.Ai.NONE && a.spec.target == HeroKit.Target.GROUND;
    }

    static boolean isHeal(FactionHeroAbility a) {
        return a.spec.id.equals("rejuvenate") || a.spec.id.equals("field_repair");
    }

    static boolean isShield(FactionHeroAbility a) {
        return a.spec.id.equals("aegis_of_dawn") || a.spec.id.equals("sun_banner");
    }

    /** Low and in danger: escape away, else a shield or heal on itself, else peel the nearest off, else walk back. */
    static boolean saveItself(Fight f, List<FactionHeroAbility> ready) {
        Vec3 away = f.awayFromEnemies(ESCAPE_DISTANCE);
        for (FactionHeroAbility a : ready)
            if (isEscape(a)) {
                double r = Math.min(a.range, ESCAPE_DISTANCE);
                Vec3 to = f.awayFromEnemies(r);
                a.use(f.level, f.hero, BlockPos.containing(to.x, f.mob.getY(), to.z));
                return true;
            }
        for (FactionHeroAbility a : ready)
            if (isShield(a) || isHeal(a)) {
                if (a.spec.target == HeroKit.Target.SELF ? a.castNow(f.hero, null, f.mob.position()) : use(f, a, f.mob.getOnPos()))
                    return true;
            }
        for (FactionHeroAbility a : ready)
            if (a.spec.ai == HeroKit.Ai.FIGHT_SELF && a.spec.radius >= 3 && !a.spec.id.equals("frenzy")
                    && f.enemiesWithin(f.mob.position(), a.spec.radius + 1) > 0 && a.castNow(f.hero, null, f.mob.position()))
                return true;   // a nova or a wave buys the time to get out
        // nothing to cast: step back out of the fight unless the player ordered a move of their own
        Unit u = (Unit) f.mob;
        if (u.getMoveGoal() != null && u.getMoveGoal().getMoveTarget() == null && f.enemiesWithin(f.mob.position(), 8) > 0)
            u.setMoveTarget(BlockPos.containing(away.x, f.mob.getY(), away.z));
        return false;
    }

    /** Heals where they mend the most: the spot among hurt friends (the hero too) with the most of them. */
    static boolean heal(Fight f, List<FactionHeroAbility> ready) {
        for (FactionHeroAbility a : ready) {
            if (!isHeal(a))
                continue;
            if (a.spec.target == HeroKit.Target.SELF) {
                if (f.hurtFriendsWithin(f.mob.position(), a.spec.radius) >= (f.hp < HURT_HP ? 1 : 2)
                        && a.castNow(f.hero, null, f.mob.position()))
                    return true;
                continue;
            }
            LivingEntity best = null;
            int bestN = 0;
            for (LivingEntity fr : f.friends) {
                if (fraction(fr) >= HURT_HP || fr.distanceTo(f.mob) > a.range + 2)
                    continue;
                int n = f.hurtFriendsWithin(fr.position(), Math.max(3, a.spec.radius));
                if (n > bestN) {
                    best = fr;
                    bestN = n;
                }
            }
            if (best != null && use(f, a, best.getOnPos()))
                return true;
        }
        return false;
    }

    static boolean use(Fight f, FactionHeroAbility a, BlockPos at) {
        a.use(f.level, f.hero, at);
        return true;
    }

    /** One fighting ability against what is round the hero now; false to look at the next one. */
    static boolean fight(Fight f, FactionHeroAbility a) {
        Mob mob = f.mob;
        if (isHeal(a))
            return false;   // heal() decided already
        if (a.slot == 3 && !realFight(f, a))
            return false;
        int rank = a.getRank(f.hero);
        switch (a.spec.ai) {
            case STRIKE -> {
                float dmg = a.spec.a[Math.max(0, Math.min(rank, a.spec.a.length) - 1)];
                LivingEntity t = bestTarget(f, a.range + 1, dmg, a.spec.radius);
                if (t == null)
                    return false;
                if (a.spec.target == HeroKit.Target.ENEMY)
                    a.use(f.level, f.hero, t);
                else
                    a.use(f.level, f.hero, t.getOnPos());
                return true;
            }
            case ENGAGE -> {
                // gap closers only for the melee heroes, healthy, onto a target not standing in a crowd
                if (!f.melee || f.hp < 0.5)
                    return false;
                LivingEntity t = bestTarget(f, a.range, 0, 0);
                if (t == null || t.distanceTo(mob) < 4)
                    return false;
                int around = f.enemiesWithin(t.position(), 5);
                int backup = f.friends.size();
                if (around > 3 && around > backup)
                    return false;
                return use(f, a, t.getOnPos());
            }
            case FIGHT_SELF -> {
                if (isShield(a)) {
                    // shields when the hero or its friends are taking a beating, or the crowd is on them
                    int hurt = f.hurtFriendsWithin(mob.position(), a.spec.radius);
                    if (hurt == 0 && f.enemiesWithin(mob.position(), a.spec.radius) < 2 && f.hp >= HURT_HP)
                        return false;
                    return a.castNow(f.hero, null, mob.position());
                }
                if (a.spec.id.equals("frenzy"))
                    return f.hp > 0.4 && f.enemiesWithin(mob.position(), 4) > 0 && a.castNow(f.hero, null, mob.position());
                if (a.spec.id.equals("mana_font") || a.spec.id.equals("grave_sight") || a.spec.id.equals("war_drums"))
                    return a.castNow(f.hero, null, mob.position());   // realFight() said it is time
                // area bursts round the hero: worth it on two or more (or one finishing blow / when it must peel)
                double r = Math.max(3, a.spec.radius) + 0.5;
                int n = f.enemiesWithin(mob.position(), r);
                if (n == 0 || (n < 2 && f.hp >= 0.5 && f.enemies.size() > 1))
                    return false;
                return a.castNow(f.hero, null, mob.position());
            }
            case FIGHT_GROUND -> {
                LivingEntity t = bestTarget(f, 12, 0, 0);
                if (t == null)
                    return false;
                double d = t.distanceTo(mob);
                // summons and turrets go toward the enemy, cover and pools at the hero's own feet
                boolean forward = a.spec.id.equals("raise_the_fallen") || a.spec.id.equals("deploy_turret");
                return use(f, a, forward && d <= a.range ? t.getOnPos() : mob.getOnPos());
            }
            default -> {
                return false;
            }
        }
    }

    /** The fourth (the long cooldown): a crowd on the hero, or a hurt hero with enemies on it (the shields). */
    static boolean realFight(Fight f, FactionHeroAbility a) {
        int near = f.enemiesWithin(f.mob.position(), Math.max(ULT_RADIUS, a.spec.radius));
        if (near >= ULT_CROWD)
            return true;
        if (isShield(a) || isHeal(a))
            return near > 0 && (f.hp < 0.4 || f.hurtFriendsWithin(f.mob.position(), a.spec.radius) >= 3);
        return false;
    }

    /**
     * The enemy most worth the ability: one it can finish ({@code damage} at least its health), then an enemy hero or
     * commander, then the weakest; a splash or chain ({@code splash} > 0) also counts the enemies round the target.
     * Nearer is a little better. Null when none is within {@code range}.
     */
    @Nullable
    static LivingEntity bestTarget(Fight f, double range, float damage, float splash) {
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (LivingEntity e : f.enemies) {
            double d = e.distanceTo(f.mob);
            if (d > range)
                continue;
            double s = -d;
            if (damage > 0 && e.getHealth() <= damage)
                s += 100;
            if (e instanceof HeroUnit || CommanderServerEvents.isCommander(e))
                s += 60;
            s += 30 * (1 - fraction(e));
            if (splash > 0)
                s += 8 * (f.enemiesWithin(e.position(), splash) - 1);
            if (s > bestScore) {
                bestScore = s;
                best = e;
            }
        }
        return best;
    }
}
