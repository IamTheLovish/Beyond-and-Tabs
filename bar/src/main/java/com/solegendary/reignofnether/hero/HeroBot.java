package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.bot.BotServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.hero.ai.DecisionLog.Why;
import com.solegendary.reignofnether.hero.ai.FightEval;
import com.solegendary.reignofnether.hero.ai.HeroBrain;
import com.solegendary.reignofnether.hero.ai.OrderLock;
import com.solegendary.reignofnether.hero.ai.Perception;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.UnitCommand;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
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
 * <p>
 * v2 (hero/ai, design/hero_mode.md "Hero AI"), for the kits in HeroBrain.ROLLED_OUT: the same fight, run behind the
 * OrderLock (the player's order first; D030 for a near-death hero), on what Perception saw (only what the hero's side
 * sees), with FightEval's reading (power ratio, enemy towers, near death) and the combo rule, every decision logged
 * (DecisionLog). See the v2 section at the end.
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
        if (HeroBrain.drives(hero))
            think(mob, hero);
        else
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
        if (heal(f, ready, 2))
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

    /**
     * Heals where they mend the most: the spot among hurt friends (the hero too) with the most of them, up to
     * {@code slack} blocks past the heal's range (v1 walks 2 blocks to cast; v2 never walks, so -0.5).
     */
    static boolean heal(Fight f, List<FactionHeroAbility> ready, double slack) {
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
                if (fraction(fr) >= HURT_HP || fr.distanceTo(f.mob) > a.range + slack)
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
        return bestTarget(f, range, damage, splash, false, -1);
    }

    /**
     * v2 adds what its brain knows ({@code v2} true): the enemy the player ordered the hero to attack
     * ({@code focusId}) comes first, and an enemy already stunned, rooted or slowed is worth more - the damage lands
     * while it cannot answer (the combo's second half).
     */
    @Nullable
    static LivingEntity bestTarget(Fight f, double range, float damage, float splash, boolean v2, int focusId) {
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
            if (v2) {
                if (e.getId() == focusId)
                    s += 80;
                if (e.hasEffect(MobEffectRegistrar.STUN.get()) || e.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
                    s += 40;
            }
            if (s > bestScore) {
                bestScore = s;
                best = e;
            }
        }
        return best;
    }

    // ================================================================================================ v2 (hero/ai)
    // For the kits in HeroBrain.ROLLED_OUT: v1's fight above, run behind the OrderLock (the player's order first), on
    // what Perception saw (only what the hero's side sees), with FightEval's reading (who is winning, where the enemy
    // towers reach, when the hero is near death) and the combo rule; every decision goes to the hero's DecisionLog.
    //
    // Cost per hero per tick: everyTick is a possession lookup, a set lookup and the command queue's two fields (plus,
    // only while a combo is armed, a few field reads); think runs once a second per hero, staggered by its tick count:
    // one UnitGrid query of 16 blocks, one BuildingGrid query, a pass over what they return, and at most one cast.

    /** Blocks a flight runs from the threats (v1's ESCAPE_DISTANCE). */
    static final double FLEE_DISTANCE = ESCAPE_DISTANCE;
    /** Ticks a bot's hero rests out of reach, still near death, before it goes back to its order by itself. */
    static final int BOT_REST_TICKS = 20 * 20;
    /** Ticks a gap closer's follow-up stays armed: the charge's wind-up and flight fit well inside it. */
    static final int FOLLOW_UP_TICKS = 40;

    /** Does the AI drive this hero now: nobody possesses it, and it is a bot's hero or its player's AI switch is on? */
    static boolean aiDrives(Mob mob, FactionHero hero) {
        String owner = hero.getOwnerName();
        if (owner == null || owner.isBlank() || PossessionServer.isPossessed(mob))
            return false;
        return BotServerEvents.brains.containsKey(owner) || isAiEnabled(owner);
    }

    /**
     * Every server tick for a hero v2 drives: the player's order first - a fresh command ends a flight and a pending
     * combo at once (D030) - then a gap closer's follow-up goes off the tick the hero lands.
     */
    static void everyTick(Mob mob, FactionHero hero) {
        HeroBrain b = hero.heroCore().brain();
        if (!aiDrives(mob, hero)) {
            if (b.active())
                b.off();
            return;
        }
        long now = mob.level().getGameTime();
        boolean bot = BotServerEvents.brains.containsKey(hero.getOwnerName());
        boolean fresh = b.lock.refresh(mob, (Unit) mob, now, !bot);
        if (b.state == HeroBrain.State.OFF) {
            // handed back by the player (possession released, the AI switched on): what they saw stands, danger included
            b.lock.acknowledge();
            b.state = HeroBrain.State.IDLE;
        } else if (fresh && bot && b.state == HeroBrain.State.FLEE) {
            // a bot's army order swept the fleeing hero along: the flight stands (D030 is the player's word, and the
            // hero's own brain knows its life better than the bot's sweep); the order waits until the run is over.
            // A resting hero is out of reach: the bot's next order takes it back (a hero with no heal would rest forever)
            Unit.fullResetBehaviours((Unit) mob);
            if (b.state == HeroBrain.State.FLEE && b.fleeTo != null)
                ((Unit) mob).setMoveTarget(b.fleeTo);
        } else if (fresh) {
            if (b.fleeing()) {
                b.state = HeroBrain.State.OBEY;
                b.lock.takeSuspended();   // the fresh order replaces the one the flight broke off
            }
            if (!b.lock.mode().mayMove)
                b.clearCombo();   // a move, attack or hold: no follow-up after the gap closer's target (Fight keeps it)
            b.record(now, Why.COMMAND, -1, -1);
        }
        // a cast under an order stops the hero's legs for its wind-up (the cast goal and the move share the navigator):
        // the moment it is done, the order's walk picks up again at once instead of after the move goal's re-path wait
        boolean casting = busy(hero.heroCore());
        if (b.wasCasting && !casting && b.lock.mode() != OrderLock.Mode.NONE) {
            var move = ((Unit) mob).getMoveGoal();
            if (move != null && move.getMoveTarget() != null && mob.getNavigation().isDone())
                move.forceRepath();
        }
        b.wasCasting = casting;
        continueCombo(mob, hero, b, now);
    }

    /** Once a second: look, read the fight, then the most urgent of flee, rest, obey and fight. */
    static void think(Mob mob, FactionHero hero) {
        if (!(mob.level() instanceof ServerLevel sl))
            return;
        HeroBrain b = hero.heroCore().brain();
        if (!b.active())
            return;
        long now = sl.getGameTime();
        Perception.scan(sl, mob, hero.getOwnerName(), b.seen);
        FightEval.Reading before = b.reading;
        FightEval.Reading r = FightEval.evaluate(mob, b.seen, before);
        b.reading = r;
        if (r.nearDeath() && !before.nearDeath())
            b.lock.newDanger();
        if (b.fleeing() && !r.nearDeath()) {
            // healed: back to what the player had ordered
            resume(sl, mob, b);
            b.state = HeroBrain.State.IDLE;
            b.record(now, Why.RESUME, -1, -1);
        }
        if (r.nearDeath() && r.threatened() && b.lock.fleeAllowed()) {
            if (!b.fleeing())
                b.lock.suspend();
            b.state = HeroBrain.State.FLEE;
            flee(sl, mob, hero, b, now);
            return;
        }
        if (b.state == HeroBrain.State.FLEE) {
            // out of reach and still near death: the run stops here (no further than it had to go, still near its
            // side) and it waits for a heal or the player's word - it never walks back in by itself
            ((Unit) mob).getMoveGoal().stopMoving();
            b.state = HeroBrain.State.REST;
            b.restSince = now;
            b.record(now, Why.REST, -1, -1);
        }
        if (b.state == HeroBrain.State.REST && now - b.restSince >= BOT_REST_TICKS
                && BotServerEvents.brains.containsKey(hero.getOwnerName())) {
            // a bot's hero has no player to give the word: after a while out of reach it goes back to its order
            // (a hero with no heal would otherwise rest for the rest of the match); a threat seen again starts a new run
            resume(sl, mob, b);
            b.state = HeroBrain.State.IDLE;
            b.record(now, Why.RESUME, -1, -1);
            return;
        }
        if (b.state == HeroBrain.State.REST) {
            Fight f = new Fight(sl, mob, hero, b.seen.enemies, b.seen.friends, r.hp(), isMelee(hero));
            if (heal(f, ready(hero), -0.5))
                b.record(now, Why.CAST, -1, -1);
            return;
        }
        OrderLock.Mode mode = b.lock.mode();
        b.state = mode != OrderLock.Mode.NONE ? HeroBrain.State.OBEY
            : b.seen.enemies.isEmpty() ? HeroBrain.State.IDLE : HeroBrain.State.FIGHT;
        fightV2(sl, mob, hero, b, mode, now);
    }

    static boolean isMelee(FactionHero hero) {
        return hero.kit().role == HeroKit.Role.VANGUARD || hero.kit().role == HeroKit.Role.RAIDER;
    }

    /** The abilities the hero could cast this moment (learnt, off cooldown, paid for), in kit order. */
    static List<FactionHeroAbility> ready(FactionHero hero) {
        List<FactionHeroAbility> ready = new ArrayList<>(4);
        for (FactionHeroAbility a : hero.heroCore().abilities)
            if (a.getRank(hero) > 0 && a.isOffCooldown(hero) && hero.getMana() >= a.manaCost)
                ready.add(a);
        return ready;
    }

    static boolean busy(HeroCore core) {
        return core.inMotion() || (core.castGoal != null && (core.castGoal.isCasting() || core.castGoal.getMoveTarget() != null));
    }

    /**
     * v1's fight under the OrderLock: aimed casts only at what is in reach from where the hero stands (the brain never
     * walks to cast, so it never drags the hero off an order); a gap closer only when the order lets the hero move, the
     * fight is not lost (FightEval), it would not land under an enemy tower, and its follow-up is ready; abilities that
     * stun, root or slow go before the rest, so the damage lands on a target that cannot answer.
     */
    static void fightV2(ServerLevel sl, Mob mob, FactionHero hero, HeroBrain b, OrderLock.Mode mode, long now) {
        if (b.followUp != null || busy(hero.heroCore()))
            return;   // one thing at a time
        Fight f = new Fight(sl, mob, hero, b.seen.enemies, b.seen.friends, b.reading.hp(), isMelee(hero));
        List<FactionHeroAbility> ready = ready(hero);
        HeroBrain.State s = b.state;
        if (!ready.isEmpty()) {
            // an acknowledged danger (the player sent it on): it may still shield or heal where it stands
            if (b.reading.nearDeath() && b.reading.threatened() && defendInPlace(f, ready)) {
                b.record(now, Why.CAST, -1, -1);
                return;
            }
            if (heal(f, ready, -0.5)) {
                b.record(now, Why.CAST, -1, -1);
                return;
            }
            if (!f.enemies.isEmpty()) {
                float reserve = 0;
                if (f.hp < 0.6)
                    for (FactionHeroAbility a : hero.heroCore().abilities)
                        if (isEscape(a) && a.getRank(hero) > 0)
                            reserve = Math.max(reserve, a.manaCost);
                ready.sort(Comparator.comparing(a -> !a.spec.cc));   // stable: kit order within each group
                for (FactionHeroAbility a : ready) {
                    if (isEscape(a) || (reserve > 0 && hero.getMana() - a.manaCost < reserve))
                        continue;
                    if (fightV2(f, b, a, mode, now))
                        return;
                }
            }
        }
        b.record(now, s == HeroBrain.State.OBEY ? Why.OBEY : s == HeroBrain.State.FIGHT ? Why.FIGHT : Why.IDLE, -1, -1);
    }

    /** One fighting ability under v2's rules; false to look at the next one. */
    static boolean fightV2(Fight f, HeroBrain b, FactionHeroAbility a, OrderLock.Mode mode, long now) {
        Mob mob = f.mob;
        if (isHeal(a) || (a.slot == 3 && !realFight(f, a)))
            return false;
        int focus = b.lock.focusId();
        switch (a.spec.ai) {
            case STRIKE -> {
                int rank = a.getRank(f.hero);
                float dmg = a.spec.a[Math.max(0, Math.min(rank, a.spec.a.length) - 1)];
                // in reach from where it stands: the cast goal measures to the target's block, so half a block of room
                LivingEntity t = bestTarget(f, a.range - 0.5, dmg, a.spec.radius, true, focus);
                if (t == null)
                    return false;
                if (a.spec.target == HeroKit.Target.ENEMY)
                    a.use(f.level, f.hero, t);
                else
                    a.use(f.level, f.hero, t.getOnPos());
                b.record(now, Why.CAST, a.slot, t.getId());
                return true;
            }
            case ENGAGE -> {
                // an attack order lets the gap closer go onto the target the player chose, and onto nothing else
                boolean ordered = mode == OrderLock.Mode.ATTACK && focus >= 0;
                if (!(mode.mayMove || ordered) || !f.melee || f.hp < 0.5 || b.reading.stance() == FightEval.Stance.DISENGAGE)
                    return false;
                LivingEntity t = ordered ? seenWithin(f, focus, a.range) : bestTarget(f, a.range, 0, 0, true, focus);
                if (t == null || t.distanceTo(mob) < 4)
                    return false;
                int around = f.enemiesWithin(t.position(), 5);
                if (around > 3 && around > f.friends.size())
                    return false;
                Vec3 land = HeroFx.landingSpot(f.level, mob, t.position(), a.range);
                if (b.seen.landsUnderTower(mob, land.x, land.z)) {
                    b.record(now, Why.TOWER, a.slot, t.getId());
                    return false;
                }
                FactionHeroAbility next = followUpFor(f, a);
                if (next == null) {
                    b.record(now, Why.NO_FOLLOW_UP, a.slot, t.getId());
                    return false;
                }
                use(f, a, t.getOnPos());
                b.followUp = next;
                b.followUpTarget = t;
                b.followUpUntil = now + FOLLOW_UP_TICKS;
                b.record(now, Why.GAP_CLOSER, a.slot, t.getId());
                return true;
            }
            default -> {
                // area bursts round the hero and ground casts at its own feet or at a target in reach: v1's rules,
                // none of which walks the hero anywhere
                if (!fight(f, a))
                    return false;
                b.record(now, Why.CAST, a.slot, -1);
                return true;
            }
        }
    }

    /** The enemy with this id, if the hero's side sees it within {@code range}; else null. */
    @Nullable
    static LivingEntity seenWithin(Fight f, int id, double range) {
        for (LivingEntity e : f.enemies)
            if (e.getId() == id)
                return e.distanceTo(f.mob) <= range ? e : null;
        return null;
    }

    /**
     * What finishes what a gap closer starts: a strike, or a burst round the hero (not a shield, a heal or the
     * fourth), ready now, with mana for both. Null: the gap closer waits (lovish's requirement 1: "gap-closer only when
     * follow-up is ready").
     */
    @Nullable
    static FactionHeroAbility followUpFor(Fight f, FactionHeroAbility gap) {
        for (FactionHeroAbility a : f.hero.heroCore().abilities) {
            if (a == gap || a.slot == 3 || a.getRank(f.hero) <= 0 || !a.isOffCooldown(f.hero)
                    || f.hero.getMana() < gap.manaCost + a.manaCost || isShield(a) || isHeal(a))
                continue;
            if (a.spec.ai == HeroKit.Ai.STRIKE || (a.spec.ai == HeroKit.Ai.FIGHT_SELF && a.spec.radius >= 3))
                return a;
        }
        return null;
    }

    /** The combo's second half, the tick the gap closer has landed (and only while it is still worth it). */
    static void continueCombo(Mob mob, FactionHero hero, HeroBrain b, long now) {
        FactionHeroAbility next = b.followUp;
        if (next == null)
            return;
        LivingEntity t = b.followUpTarget;
        if (now > b.followUpUntil || t == null || !t.isAlive() || !(mob.level() instanceof ServerLevel sl)) {
            b.clearCombo();
            return;
        }
        if (busy(hero.heroCore()))
            return;   // the gap closer is still winding up or in the air
        if (!next.isOffCooldown(hero) || hero.getMana() < next.manaCost) {
            b.clearCombo();
            return;
        }
        boolean self = next.spec.target == HeroKit.Target.SELF;
        double reach = self ? Math.max(3, next.spec.radius) : next.range - 0.5;
        if (t.distanceTo(mob) > reach)
            return;   // not in reach yet: the window may still bring it
        b.clearCombo();
        if (self)
            next.castNow(hero, null, mob.position());
        else if (next.spec.target == HeroKit.Target.ENEMY)
            next.use(sl, hero, t);
        else
            next.use(sl, hero, t.getOnPos());
        b.record(now, Why.COMBO, next.slot, t.getId());
    }

    /** Shields, heals and novas where the hero stands: v1's saveItself without the escape or the step back. */
    static boolean defendInPlace(Fight f, List<FactionHeroAbility> ready) {
        for (FactionHeroAbility a : ready)
            if (isShield(a) || isHeal(a))
                if (a.spec.target == HeroKit.Target.SELF ? a.castNow(f.hero, null, f.mob.position()) : use(f, a, f.mob.getOnPos()))
                    return true;
        for (FactionHeroAbility a : ready)
            if (a.spec.ai == HeroKit.Ai.FIGHT_SELF && a.spec.radius >= 3 && !a.spec.id.equals("frenzy")
                    && f.enemiesWithin(f.mob.position(), a.spec.radius + 1) > 0 && a.castNow(f.hero, null, f.mob.position()))
                return true;
        return false;
    }

    /**
     * Escape by any means (lovish: "escape by any means with any tool it has"): an escape mover away from the threats,
     * else a shield, heal or nova where it stands (v1's saveItself), and the run itself - away from the enemies it sees
     * and the enemy towers whose reach it is in. D030: this may break the player's order (OrderLock.suspend keeps it).
     */
    static void flee(ServerLevel sl, Mob mob, FactionHero hero, HeroBrain b, long now) {
        b.clearCombo();
        Fight f = new Fight(sl, mob, hero, b.seen.enemies, b.seen.friends, b.reading.hp(), isMelee(hero));
        HeroCore core = hero.heroCore();
        if (busy(core))
            return;   // an escape already under way
        List<FactionHeroAbility> ready = ready(hero);
        for (FactionHeroAbility a : ready)
            if (isEscape(a)) {
                Vec3 to = awayFromThreats(mob, b.seen, Math.min(a.range, FLEE_DISTANCE));
                a.use(sl, hero, BlockPos.containing(to.x, mob.getY(), to.z));
                b.record(now, Why.FLEE, a.slot, -1);
                return;
            }
        boolean cast = defendInPlace(f, ready);
        if (busy(core))
            return;   // an aimed heal at its feet is winding up: the run waits a second
        Unit u = (Unit) mob;
        Vec3 away = awayFromThreats(mob, b.seen, FLEE_DISTANCE);
        BlockPos to = BlockPos.containing(away.x, mob.getY(), away.z);
        BlockPos going = u.getMoveGoal().getMoveTarget();
        if (going == null || going.distSqr(to) > 9) {
            Unit.fullResetBehaviours(u);   // drops the order's target, hold and attack-move: the run is the order now
            u.setMoveTarget(to);
            b.fleeTo = to;
        }
        b.record(now, Why.FLEE, cast ? -2 : -1, -1);
    }

    /** {@code dist} blocks from the hero, away from the enemies it sees and the towers whose reach it is in. */
    static Vec3 awayFromThreats(Mob mob, Perception.Snapshot s, double dist) {
        double cx = 0, cz = 0;
        int n = 0;
        for (LivingEntity e : s.enemies)
            if (e.isAlive()) {
                cx += e.getX();
                cz += e.getZ();
                n++;
            }
        for (Perception.Tower t : s.towers)
            if (t.reaches(mob.getX(), mob.getZ(), 2)) {
                cx += t.x();
                cz += t.z();
                n++;
            }
        Vec3 dir = n == 0 ? Vec3.directionFromRotation(0, mob.getYRot() + 180)
            : new Vec3(mob.getX() - cx / n, 0, mob.getZ() - cz / n);
        if (dir.horizontalDistanceSqr() < 1e-4)
            dir = new Vec3(1, 0, 0);
        return mob.position().add(new Vec3(dir.x, 0, dir.z).normalize().scale(dist));
    }

    /** Back to the order a flight broke off (unless its target is gone); with none, the run stops where it is. */
    static void resume(ServerLevel sl, Mob mob, HeroBrain b) {
        UnitCommand c = b.lock.takeSuspended();
        if (c == null || CommandQueues.targetGone(sl, c)) {
            ((Unit) mob).getMoveGoal().stopMoving();
            return;
        }
        new UnitActionItem(c.owner(), c.action(), c.targetId(), new int[] { mob.getId() }, c.pos(), BlockPos.ZERO).action(sl);
    }

    /**
     * The player selected the hero (HeroServerboundPacket HERO_SELECTED; D030: "once the player selects or commands it
     * again, the player's order wins"): the danger so far is acknowledged, and a flight the brain started ends - the
     * hero goes back to the order it had.
     */
    public static void onPlayerSelect(Mob mob) {
        if (!(mob instanceof FactionHero hero) || !(mob.level() instanceof ServerLevel sl))
            return;
        HeroBrain b = hero.heroCore().brainIfAny();
        if (b == null || !b.active())
            return;
        b.lock.acknowledge();
        if (b.fleeing()) {
            resume(sl, mob, b);
            b.state = HeroBrain.State.OBEY;
        }
        b.record(sl.getGameTime(), Why.SELECTED, -1, -1);
    }

    /** Is the v2 AI running this hero away (or resting it out of reach)? Then it does not stop to answer a blow. */
    public static boolean fleeing(Mob mob) {
        if (!(mob instanceof FactionHero hero))
            return false;
        HeroBrain b = hero.heroCore().brainIfAny();
        return b != null && b.active() && b.fleeing();
    }

    /**
     * Asked by MiscUtil.isIdleOrMoveAttackable for every target a hero's own targeting looks at (idle aggression, the
     * melee retarget, Fight-move pick-ups). A hero v2 drives, with no order of the player's, does not walk under an
     * enemy tower's guns after a target standing there, and picks no fights while it flees or rests near death (it
     * still hits back: retaliation does not ask). Under a player's order the unit's own rules stand. No brain (a v1
     * hero, the client side): no say.
     */
    public static boolean mayAutoTarget(FactionHero hero, Mob mob, LivingEntity target) {
        HeroBrain b = hero.heroCore().brainIfAny();
        if (b == null || !b.active())
            return true;
        if (b.fleeing())
            return false;
        if (b.lock.mode() != OrderLock.Mode.NONE)
            return true;
        return !b.seen.chaseIntoTower(mob, target, 1.0);
    }
}
