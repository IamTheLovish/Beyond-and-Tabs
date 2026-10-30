package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroBot;
import com.solegendary.reignofnether.hero.HeroKit;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroProd;
import com.solegendary.reignofnether.hero.HeroProgression;
import com.solegendary.reignofnether.hero.HeroReviveProd;
import com.solegendary.reignofnether.hero.HeroTypes;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless tests of hero slice 2 (design/hero_mode.md): the seven faction heroes train at their labs, gain XP from kills
 * and capture points, get a boon at the level threshold, rank their abilities, cast them, and the XP curve tapers
 * (the tier-2.5 dip). Their own holder beside SkirmishGameTests (same arena template, unique owners per test, no
 * global counts) - every test runs in parallel with the rest.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Where each faction trains its hero (the T1 army lab; the Horde's is the Military Portal its bot refits to). */
    static final Map<String, Building> LABS = Map.of(
        "villagers", Buildings.BARRACKS,
        "monsters", Buildings.GRAVEYARD,
        "piglins", Buildings.PORTAL_MILITARY,
        "verdant_court", Buildings.GROVE,
        "tidewrought", Buildings.SLIPWAY,
        "stormcallers", Buildings.LOTUS_ACADEMY,
        "brassworks", Buildings.MACHINE_SHOP);

    static String owner(GameTestHelper helper, String what) {
        return "gametest_hero_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static Mob spawnHero(GameTestHelper helper, HeroKit kit, String owner, int x, int z) {
        ServerLevel level = helper.getLevel();
        EntityType<? extends Mob> type = HeroTypes.typeOf(kit);
        Mob m = type == null ? null : type.create(level);
        if (m == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ((Unit) m).setOwnerName(owner);
        level.addFreshEntity(m);
        return m;
    }

    static Mob spawnUnit(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = type.create(helper.getLevel());
        if (m == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ((Unit) m).setOwnerName(owner);
        helper.getLevel().addFreshEntity(m);
        return m;
    }

    /**
     * Every faction has exactly one hero: its type is registered to the faction, its production item and revive are in
     * ProductionItems and on the faction's T1 army lab, the lab's server accepts it into the queue (one per owner), and
     * completing it produces a FactionHero of that kit with four distinct ability slots and the kit's stats.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void each_faction_hero_trains_at_its_lab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        Set<String> factions = new HashSet<>();
        List<LivingEntity> made = new ArrayList<>();
        List<Resources> pools = new ArrayList<>();
        int i = 0;
        for (HeroKit kit : HeroKits.ALL) {
            i++;
            if (!factions.add(kit.factionName))
                problems.add(kit.id + ": a second hero for " + kit.factionName);
            var faction = Factions.getFaction(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, kit.factionName));
            var type = HeroTypes.typeOf(kit);
            if (type == null || faction == Factions.NONE) {
                problems.add(kit.id + ": no entity type or no faction " + kit.factionName);
                continue;
            }
            if (!faction.key.equals(Factions.ENTITY_FACTION.get(EntityType.getKey(type))))
                problems.add(kit.id + ": not registered to " + kit.factionName);
            HeroProd prod = HeroProd.forKit(kit);
            HeroReviveProd revive = HeroReviveProd.forKit(kit);
            if (prod == null || revive == null || ProductionItems.getProductionItem(type) != prod) {
                problems.add(kit.id + ": production item or revive missing");
                continue;
            }
            Building lab = LABS.get(kit.factionName);
            if (!(lab instanceof ProductionBuilding pb) || !pb.productions.get().contains(prod) || !pb.productions.get().contains(revive)) {
                problems.add(kit.id + ": " + (lab == null ? "no lab" : lab.name) + " does not train the hero and its revive");
                continue;
            }
            String owner = owner(helper, "train" + i);
            Resources pool = new Resources(owner, 0, 0, 0);
            ResourcesServerEvents.resourcesList.add(pool);
            pools.add(pool);
            BlockPos corner = helper.absolutePos(BlockPos.ZERO);
            // every lab at the same spot inside this test's arena (the placements are never registered, and the search
            // below filters by owner and type): at i * 40 blocks east the later labs spawned their hero into chunks only
            // loaded when a neighbouring test happened to be there, so the test failed or passed with the test layout
            // (design/code_review.md R2-06)
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(lab, level,
                new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, owner, false);
            if (!(bp instanceof ProductionPlacement pp)) {
                problems.add(kit.id + ": " + lab.name + " is not a production placement");
                continue;
            }
            pp.isBuilt = true;
            if (!pp.startProductionItem(prod))
                problems.add(kit.id + ": the server refused to queue the hero: " + prod.getProduceErrorMsg(pp));
            pp.productionQueue.clear();
            prod.onComplete.accept(level, pp);   // the real completion path: HeroProd.onComplete -> produceUnit
            Mob hero = null;
            AABB box = new AABB(pp.minCorner, pp.maxCorner.offset(1, 1, 1)).inflate(24);
            for (Mob m : level.getEntitiesOfClass(Mob.class, box))
                if (m.getType() == type && m instanceof Unit u && owner.equals(u.getOwnerName()))
                    hero = m;
            // belt and braces (tutorial-onboarding): the box query only sees entity-loaded chunks, the unit list sees
            // every joined unit - so a hero that did spawn is never reported missing because of where it landed
            if (hero == null)
                for (LivingEntity le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
                    if (le instanceof Mob m && m.getType() == type && m instanceof Unit u && owner.equals(u.getOwnerName()))
                        hero = m;
            if (!(hero instanceof FactionHero fh)) {
                problems.add(kit.id + ": completing the item produced no hero");
                continue;
            }
            made.add(hero);
            if (fh.kit() != kit)
                problems.add(kit.id + ": produced the wrong kit " + fh.kit().id);
            if (fh.getHeroAbilities().size() != 4)
                problems.add(kit.id + ": " + fh.getHeroAbilities().size() + " hero abilities, not 4");
            Set<UnitAction> actions = new HashSet<>();
            for (var a : fh.getHeroAbilities())
                if (a instanceof FactionHeroAbility fa)
                    actions.add(fa.action);
            if (actions.size() != 4 || !actions.contains(UnitAction.HERO_ABILITY_4))
                problems.add(kit.id + ": ability slots are not HERO_ABILITY_1..4: " + actions);
            if (Math.abs(hero.getMaxHealth() - kit.health) > 0.5f)
                problems.add(kit.id + ": max health " + hero.getMaxHealth() + " is not the kit's " + kit.health);
            if (fh.getCost() == null || fh.getCost().metal() <= 0)
                problems.add(kit.id + ": no metal cost");
            if (fh.getSkillPoints() != 1)
                problems.add(kit.id + ": a new hero should hold one ability point, has " + fh.getSkillPoints());
        }
        if (factions.size() != 7)
            problems.add("expected heroes for 7 factions, got " + factions);
        for (LivingEntity e : made)
            e.discard();
        ResourcesServerEvents.resourcesList.removeAll(pools);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * A hero trained the way a player trains it spawns: the lab is a registered placement (so the hero cap sees its
     * queue), the item is queued, and the completion runs through ProductionItem.tick with the finished item still at
     * the head of the queue - exactly as ProductionPlacement.tick does. The test above clears the queue before calling
     * onComplete, which hid the bug lovish met on Oct 16: the finishing hero counted itself as "in training", the cap of
     * one refused it, and no Dawn Paladin ever came out of the barracks.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_spawns_when_its_training_finishes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HeroKit kit = HeroKits.DAWN_PALADIN;
        HeroProd prod = HeroProd.forKit(kit);
        var type = HeroTypes.typeOf(kit);
        String owner = owner(helper, "finish");
        Resources pool = new Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(LABS.get(kit.factionName), level,
            new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, owner, false);
        if (!(bp instanceof ProductionPlacement pp) || prod == null || type == null) {
            ResourcesServerEvents.resourcesList.remove(pool);
            helper.fail("no barracks placement / hero production item for " + kit.id);
            return;
        }
        pp.isBuilt = true;
        com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().add(pp);
        Mob hero = null;
        String problem = null;
        try {
            if (!pp.startProductionItem(prod)) {
                problem = "the server refused to queue the hero: " + prod.getProduceErrorMsg(pp);
            } else {
                var head = pp.productionQueue.get(0);
                head.ticksLeft = 0;   // trained: the next tick completes it, with the item still heading the queue
                boolean done = head.item.tick(pp, head);
                if (done)
                    pp.productionQueue.remove(0);
                for (LivingEntity le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
                    if (le instanceof Mob m && m.getType() == type && m instanceof Unit u && owner.equals(u.getOwnerName()))
                        hero = m;
                if (!done)
                    problem = "the finished hero's production did not complete";
                else if (hero == null)
                    problem = "the hero finished training but never spawned (cap check: "
                        + com.solegendary.reignofnether.building.production.HeroProductionItem.heroesCountedAgainstCap(pp, prod)
                        + " counted)";
            }
        } finally {
            pp.productionQueue.clear();
            com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(pp);
            ResourcesServerEvents.resourcesList.remove(pool);
            if (hero != null)
                hero.discard();
        }
        if (problem != null)
            helper.fail(problem);
        else
            helper.succeed();
    }

    /** A kill near the hero drops XP orbs that fly to it: its experience rises (Reign of Nether's orbs, unchanged). */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void hero_gains_xp_from_a_kill(GameTestHelper helper) {
        String me = owner(helper, "killer"), them = owner(helper, "victim");
        Mob hero = spawnHero(helper, HeroKits.HORDE_WARCHIEF, me, 4, 4);
        Mob victim = spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 5, 4);
        if (hero == null || victim == null) {
            helper.fail("could not spawn the hero or its victim");
            return;
        }
        hero.setNoAi(true);   // stands still: the orbs come to it
        // kill() (generic damage): any death near an enemy hero drops orbs for it - who struck the blow doesn't matter
        helper.runAfterDelay(3, victim::kill);
        helper.runAfterDelay(6, () -> {
            if (victim.isAlive())
                helper.fail("the victim survived kill()");
        });
        helper.succeedWhen(() -> {
            if (((HeroUnit) hero).getExperience() <= 0) {
                int orbs = helper.getLevel().getEntitiesOfClass(com.solegendary.reignofnether.hero.HeroExperienceOrb.class,
                    hero.getBoundingBox().inflate(24)).size();
                helper.fail("the hero has no XP from the kill yet (" + orbs + " orbs about)");
            }
            hero.discard();
        });
    }

    /**
     * The boon: one XP short of level 2 nothing happens; the threshold gives a level, an ability point and the power
     * increase (health x1.08), and the hero is healed by what it gained.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_gets_a_boon_at_the_threshold(GameTestHelper helper) {
        Mob mob = spawnHero(helper, HeroKits.DAWN_PALADIN, owner(helper, "boon"), 4, 4);
        if (!(mob instanceof FactionHero hero)) {
            helper.fail("could not spawn the Dawn Paladin");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            int need = HeroProgression.xpForLevel(2);
            hero.addExperience(need - 1);
            if (hero.getHeroLevel() != 1 || hero.getSkillPoints() != 1)
                problems.add("one XP short: level " + hero.getHeroLevel() + ", points " + hero.getSkillPoints());
            float before = mob.getMaxHealth();
            hero.addExperience(1);
            if (hero.getHeroLevel() != 2)
                problems.add("at " + need + " XP the hero is level " + hero.getHeroLevel());
            if (hero.getSkillPoints() != 2)
                problems.add("the boon gave no ability point: " + hero.getSkillPoints());
            float expected = HeroKits.DAWN_PALADIN.health * HeroProgression.statMultiplier(2);
            if (Math.abs(mob.getMaxHealth() - expected) > 0.5f || mob.getMaxHealth() <= before)
                problems.add("the boon's power increase: max health " + before + " -> " + mob.getMaxHealth() + ", expected " + expected);
            float dmg = ((com.solegendary.reignofnether.unit.interfaces.AttackerUnit) mob).getUnitAttackDamage();
            if (dmg < HeroKits.DAWN_PALADIN.damage * HeroProgression.statMultiplier(2) - 0.01f)
                problems.add("damage did not grow with the boon: " + dmg);
            mob.discard();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /**
     * Ranking: the first point buys rank 1 of ability 1; rank 2 needs level 3; the utility fourth needs level 6 and
     * then ranks; a rank changes the ability's cooldown to that rank's. The bot's spender puts the utility first.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_ranks_an_ability(GameTestHelper helper) {
        Mob mob = spawnHero(helper, HeroKits.STORM_WITCH, owner(helper, "rank"), 4, 4);
        if (!(mob instanceof FactionHero hero)) {
            helper.fail("could not spawn the Storm Witch");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            FactionHeroAbility first = HeroKits.ability(hero, 0), utility = HeroKits.ability(hero, 3);
            if (!first.rankUp(hero) || first.getRank(hero) != 1 || hero.getSkillPoints() != 0)
                problems.add("the first point did not buy rank 1: rank " + first.getRank(hero) + ", points " + hero.getSkillPoints());
            hero.setSkillPoints(1);
            if (first.rankUp(hero))
                problems.add("rank 2 was bought at level 1 (needs level 3)");
            if (utility.rankUp(hero))
                problems.add("the utility ability was ranked at level 1 (needs level 6)");
            hero.addExperience(HeroProgression.xpForLevel(6));
            if (hero.getHeroLevel() != 6)
                problems.add("expected level 6, got " + hero.getHeroLevel());
            if (!first.rankUp(hero) || first.getRank(hero) != 2)
                problems.add("rank 2 not bought at level 6");
            if (first.cooldownMax != first.spec.cooldownSeconds[1] * 20)
                problems.add("rank 2 cooldown " + first.cooldownMax + " is not " + first.spec.cooldownSeconds[1] * 20);
            int before = utility.getRank(hero);
            HeroBot.spendPoints(hero);
            if (utility.getRank(hero) != before + 1)
                problems.add("the bot's spender did not rank the utility ability first (rank " + utility.getRank(hero) + ")");
            mob.discard();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /**
     * Casting: a self-centred ability (the Paladin's Sun Banner) lands on a friend next to it, goes on cooldown and costs
     * its mana; an aimed one (the Storm Witch's Chain Lightning) hurts an enemy and never the friend beside it.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_abilities_cast_and_cool_down(GameTestHelper helper) {
        String me = owner(helper, "cast"), them = owner(helper, "cast_enemy");
        Mob paladin = spawnHero(helper, HeroKits.DAWN_PALADIN, me, 3, 3);
        Mob witch = spawnHero(helper, HeroKits.STORM_WITCH, me, 3, 6);
        Mob friend = spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 4, 3);
        Mob enemy = spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 8, 6);
        if (!(paladin instanceof FactionHero p) || !(witch instanceof FactionHero w) || friend == null || enemy == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            FactionHeroAbility banner = HeroKits.ability(p, 2);
            banner.setRank(p, 1);
            banner.updateStatsForRank(p);
            float mana = p.getMana();
            if (!banner.castNow(p, null, paladin.position()))
                problems.add("Sun Banner did not cast");
            if (!friend.hasEffect(MobEffects.DAMAGE_RESISTANCE))
                problems.add("Sun Banner gave the friend no Resistance");
            if (banner.isOffCooldown(p))
                problems.add("Sun Banner is not on cooldown after casting");
            if (p.getMana() > mana - banner.manaCost + 0.01f)
                problems.add("Sun Banner cost no mana: " + mana + " -> " + p.getMana());
            if (banner.castNow(p, null, paladin.position()))
                problems.add("Sun Banner cast again while on cooldown");
            FactionHeroAbility chain = HeroKits.ability(w, 0);
            chain.setRank(w, 1);
            chain.updateStatsForRank(w);
            float enemyHp = enemy.getHealth(), friendHp = friend.getHealth();
            if (!chain.castNow(w, enemy, enemy.position()))
                problems.add("Chain Lightning did not cast on an enemy");
            if (enemy.getHealth() >= enemyHp)
                problems.add("Chain Lightning did not hurt the enemy");
            if (friend.getHealth() < friendHp)
                problems.add("Chain Lightning hurt a friend");
            FactionHeroAbility chainAgain = chain;
            chainAgain.setCooldown(0, w);
            if (chainAgain.castNow(w, friend, friend.position()))
                problems.add("Chain Lightning was cast on a friend");
            for (Mob m : List.of(paladin, witch, friend, enemy))
                m.discard();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /**
     * Capture points pay the team's heroes nearby: CAPTURE_XP when a site is taken, HOLD_XP_PER_SECOND while it is held;
     * an enemy hero standing just as close gets nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void hero_gains_capture_point_xp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "capture"), them = owner(helper, "capture_enemy");
        Mob mine = spawnHero(helper, HeroKits.CORSAIR_CAPTAIN, me, 4, 4);
        Mob theirs = spawnHero(helper, HeroKits.GRAVECALLER, them, 6, 4);
        var point = EntityType.MARKER.create(level);
        if (mine == null || theirs == null || point == null) {
            helper.fail("could not spawn the heroes or the site marker");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 6));
        point.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.runAfterDelay(3, () -> {   // after a server tick, so the unit grid has both heroes
            List<String> problems = new ArrayList<>();
            HeroProgression.onCapture(level, point, me);
            int xp = ((HeroUnit) mine).getExperience();
            if (xp != HeroProgression.CAPTURE_XP)
                problems.add("taking the site gave " + xp + " XP, expected " + HeroProgression.CAPTURE_XP);
            HeroProgression.onHold(level, point, me, 10);
            int held = ((HeroUnit) mine).getExperience() - xp;
            if (held != Math.round(HeroProgression.HOLD_XP_PER_SECOND * 10))
                problems.add("holding it 10 s gave " + held + " XP");
            if (((HeroUnit) theirs).getExperience() != 0)
                problems.add("the enemy hero got XP from our site: " + ((HeroUnit) theirs).getExperience());
            mine.discard();
            theirs.discard();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /**
     * The medium hero stat buff (design: hero-buff option B): each kit's health and one ability's cooldown carry the
     * new numbers, the Storm Witch's Cloud Ride is left alone, and the six Reign of Nether heroes got +10% health.
     * Reads the kit directly (no runtime multiplier), so it runs in the tick it starts.
     */
    @GameTest(template = ARENA)
    public static void heroes_get_the_medium_stat_buff(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        // kit, new health, slot of the checked ability, its rank 1/2/3 cooldown in seconds
        Object[][] expect = {
            { HeroKits.DAWN_PALADIN, 220f, 0, new int[] { 13, 11, 9 } },
            { HeroKits.GRAVECALLER, 205f, 2, new int[] { 36, 32, 27 } },
            { HeroKits.HORDE_WARCHIEF, 205f, 1, new int[] { 22, 18, 14 } },
            { HeroKits.THORNHEART_DRUID, 180f, 3, new int[] { 54, 45, 36 } },
            { HeroKits.CORSAIR_CAPTAIN, 215f, 0, new int[] { 6, 5, 4 } },
            { HeroKits.STORM_WITCH, 160f, 0, new int[] { 8, 7, 6 } },
            { HeroKits.ARCH_ENGINEER, 155f, 2, new int[] { 32, 27, 23 } },
            { HeroKits.ASH_SHAMAN, 180f, 1, new int[] { 13, 11, 9 } },
        };
        for (Object[] e : expect) {
            HeroKit kit = (HeroKit) e[0];
            if (kit.health != (float) e[1])
                problems.add(kit.id + ": health " + kit.health + " is not " + e[1]);
            if (!java.util.Arrays.equals(kit.abilities[(int) e[2]].cooldownSeconds, (int[]) e[3]))
                problems.add(kit.id + ": slot " + e[2] + " cooldown "
                    + java.util.Arrays.toString(kit.abilities[(int) e[2]].cooldownSeconds) + " is not "
                    + java.util.Arrays.toString((int[]) e[3]));
        }
        if (!java.util.Arrays.equals(HeroKits.STORM_WITCH.abilities[1].cooldownSeconds, new int[] { 8, 6, 4 }))
            problems.add("Cloud Ride's cooldown should be unchanged (8/6/4)");
        // Reign of Nether heroes: base health and per-level growth (+10%)
        float[][] ron = {
            { com.solegendary.reignofnether.unit.units.villagers.RoyalGuardUnit.maxHealth, 140f,
                com.solegendary.reignofnether.unit.units.villagers.RoyalGuardUnit.maxHealthBonusPerLevel, 16.5f },
            { com.solegendary.reignofnether.unit.units.villagers.EnchanterUnit.maxHealth, 110f,
                com.solegendary.reignofnether.unit.units.villagers.EnchanterUnit.maxHealthBonusPerLevel, 11f },
            { com.solegendary.reignofnether.unit.units.monsters.NecromancerUnit.maxHealth, 110f,
                com.solegendary.reignofnether.unit.units.monsters.NecromancerUnit.maxHealthBonusPerLevel, 13f },
            { com.solegendary.reignofnether.unit.units.monsters.WretchedWraithUnit.maxHealth, 155f,
                com.solegendary.reignofnether.unit.units.monsters.WretchedWraithUnit.maxHealthBonusPerLevel, 18.5f },
            { com.solegendary.reignofnether.unit.units.piglins.PiglinMerchantUnit.maxHealth, 165f,
                com.solegendary.reignofnether.unit.units.piglins.PiglinMerchantUnit.maxHealthBonusPerLevel, 11f },
            { com.solegendary.reignofnether.unit.units.piglins.WildfireUnit.maxHealth, 130f,
                com.solegendary.reignofnether.unit.units.piglins.WildfireUnit.maxHealthBonusPerLevel, 13f },
        };
        for (int i = 0; i < ron.length; i++)
            if (ron[i][0] != ron[i][1] || ron[i][2] != ron[i][3])
                problems.add("Reign of Nether hero #" + i + ": health " + ron[i][0] + " +" + ron[i][2] + "/level, expected "
                    + ron[i][1] + " +" + ron[i][3]);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The XP curve shows the tier-2.5 dip: the curve is Reign of Nether's (320 XP to level 2, 8640 to level 10), stat
     * growth falls from 8% a boon to 2% after level 8, buying 1% of power costs at least 8x more XP at level 9 than at
     * level 3, and the stats alone keep every hero within ~1.2-2.5 T2 units at level 5 and under 3 at level 10
     * (Lanchester: fighting strength goes with health x damage, so N units match a hero when N^2 = the ratio).
     */
    @GameTest(template = ARENA)
    public static void xp_curve_shows_the_tier_two_and_a_half_dip(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        if (HeroProgression.xpForLevel(2) != 320 || HeroUnit.getHeroLevel(319) != 1 || HeroUnit.getHeroLevel(320) != 2)
            problems.add("level 2 is not at 320 XP on Reign of Nether's curve");
        if (HeroProgression.xpForLevel(10) != 8640 || HeroUnit.getHeroLevel(8640) != 10)
            problems.add("level 10 is not at 8640 XP: " + HeroProgression.xpForLevel(10));
        for (int l = 2; l <= 10; l++)
            if (HeroUnit.getHeroLevel(HeroProgression.xpForLevel(l)) != l)
                problems.add("xpForLevel(" + l + ") disagrees with HeroUnit.getHeroLevel");
        if (!(HeroProgression.growthAt(3) > HeroProgression.growthAt(7) && HeroProgression.growthAt(7) > HeroProgression.growthAt(9)))
            problems.add("stat growth does not taper: " + HeroProgression.growthAt(3) + ", " + HeroProgression.growthAt(7)
                + ", " + HeroProgression.growthAt(9));
        float early = HeroProgression.xpPerPercentAt(3), late = HeroProgression.xpPerPercentAt(9);
        if (late < early * 8)
            problems.add("power does not get dearer late: " + early + " XP per % at level 3, " + late + " at level 9");
        if (HeroProgression.statMultiplier(10) > 1.6f)
            problems.add("a level-10 hero has x" + HeroProgression.statMultiplier(10) + " stats: too strong");
        float t2 = com.solegendary.reignofnether.unit.units.brass.TeslaWalkerUnit.maxHealth
            * com.solegendary.reignofnether.unit.units.brass.TeslaWalkerUnit.attackDamage;
        for (HeroKit kit : HeroKits.ALL) {
            float m5 = HeroProgression.statMultiplier(5), m10 = HeroProgression.statMultiplier(10);
            double units5 = Math.sqrt(kit.health * m5 * kit.damage * m5 / t2);
            double units10 = Math.sqrt(kit.health * m10 * kit.damage * m10 / t2);
            if (units5 < 1.2 || units5 > 2.5)
                problems.add(kit.id + " at level 5 is worth " + String.format("%.2f", units5) + " T2 units on stats");
            if (units10 > 3)
                problems.add(kit.id + " at level 10 is worth " + String.format("%.2f", units10) + " T2 units: a solo army");
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** The bot queues its faction hero at a lab that offers it once it is mid-game, and only once. */
    @GameTest(template = ARENA)
    public static void bot_trains_its_faction_hero_mid_game(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String bot = owner(helper, "bot");
        Resources pool = new Resources(bot, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        try {
            BlockPos corner = helper.absolutePos(BlockPos.ZERO);
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.SLIPWAY, level,
                new BlockPos(corner.getX() + 3, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, bot, false);
            if (!(bp instanceof ProductionPlacement pp)) {
                helper.fail("the Slipway is not a production placement");
                return;
            }
            pp.isBuilt = true;
            List<BuildingPlacement> buildings = List.of(bp);
            HeroBot.think(bot, Factions.TIDEWROUGHT, buildings, false, 20);
            if (!pp.productionQueue.isEmpty()) {
                helper.fail("the bot trained its hero before Tier 2");
                return;
            }
            HeroBot.think(bot, Factions.TIDEWROUGHT, buildings, true, 2);
            if (!pp.productionQueue.isEmpty()) {
                helper.fail("the bot trained its hero in the opening");
                return;
            }
            HeroBot.think(bot, Factions.TIDEWROUGHT, buildings, true, HeroBot.TRAIN_FROM_MINUTE + 1);
            HeroBot.think(bot, Factions.TIDEWROUGHT, buildings, true, HeroBot.TRAIN_FROM_MINUTE + 2);
            long heroes = pp.productionQueue.stream().filter(ap -> ap.item == ProductionItems.CORSAIR_CAPTAIN).count();
            if (heroes != 1)
                helper.fail("expected the Corsair Captain queued once, queue has " + heroes);
            else
                helper.succeed();
        } finally {
            ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    // ------------------------------------------------------------------ hero slice 3 (design/hero_mode.md)

    /**
     * Team fog for Hero-role players (CR-06): a Hero is fogged (FogOfWarServerEvents.fogAppliesTo) and shares exactly
     * the owners its commander shares - the commander and the commander's allies, never the enemy; once the role
     * ends, the observer rule is back. Unique names: parallel-safe.
     */
    @GameTest(template = ARENA)
    public static void hero_role_fog_equals_the_teams(GameTestHelper helper) {
        String cmd = owner(helper, "fogcmd"), ally = owner(helper, "fogally"), foe = owner(helper, "fogfoe");
        String hero = owner(helper, "fogh");
        List<String> problems = new ArrayList<>();
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(cmd, ally);
        com.solegendary.reignofnether.hero.HeroRoles.assign(hero, List.of(cmd));
        try {
            List<String> owners = List.of(cmd, ally, foe);
            if (!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.fogAppliesTo(hero))
                problems.add("a Hero-role player still plays without fog (observer view)");
            Set<String> heroSees = com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.teamOwnersFor(hero, owners);
            Set<String> cmdSees = com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.teamOwnersFor(cmd, owners);
            if (!heroSees.equals(cmdSees))
                problems.add("the Hero's vision owners " + heroSees + " differ from the commander's " + cmdSees);
            if (!heroSees.contains(cmd) || !heroSees.contains(ally))
                problems.add("the Hero does not see through its commander and the commander's ally: " + heroSees);
            if (heroSees.contains(foe) || heroSees.contains(hero))
                problems.add("the Hero sees through the enemy or a viewer of its own: " + heroSees);
            if (com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.fogAppliesTo(owner(helper, "fogobserver")))
                problems.add("a plain observer is fogged (spectators must keep the full view)");
        } finally {
            com.solegendary.reignofnether.hero.HeroRoles.unassign(hero);
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(cmd, ally);
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(ally, cmd);
        }
        if (com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.fogAppliesTo(hero))
            problems.add("still fogged after the role ended");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Hopping (individual-control.md 1): a player who possesses unit A and claims unit B gets B and lets go of A in
     * the same call - A is free again, idle, and nobody holds two units. The client's Tab hop is just that second
     * claim, so this is the rule it stands on. Own strip at y 228.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void possession_hop_releases_the_previous_unit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "hop");
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(me.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 228;
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -2; dz <= 2; dz++)
                level.setBlock(new BlockPos(base.getX() + dx, y, base.getZ() + dz),
                    net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        Runnable clearStrip = () -> {
            for (int dx = -7; dx <= 7; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    level.setBlock(new BlockPos(base.getX() + dx, y, base.getZ() + dz),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        };
        Mob a = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        Mob b = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (a == null || b == null) {
            clearStrip.run();
            helper.fail("could not create two vindicator units");
            return;
        }
        a.moveTo(base.getX() - 3 + 0.5, y + 1, base.getZ() + 0.5, -90, 0);
        b.moveTo(base.getX() + 3 + 0.5, y + 1, base.getZ() + 0.5, 90, 0);
        ((Unit) a).setOwnerName(me);
        ((Unit) b).setOwnerName(me);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        List<String> problems = new ArrayList<>();
        Runnable finish = () -> {
            com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            a.discard();
            b.discard();
            clearStrip.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        helper.runAfterDelay(6, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, me, uuid, a.getId(), true);
            if (why != null) {
                problems.add("could not possess A: " + why);
                finish.run();
                return;
            }
            why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, me, uuid, b.getId(), true);
            if (why != null)
                problems.add("the hop to B was refused: " + why);
            var p = com.solegendary.reignofnether.hero.PossessionServer.of(uuid);
            if (p == null || p.unitId != b.getId())
                problems.add("after the hop the player should hold B, holds " + (p == null ? "nothing" : String.valueOf(p.unitId)));
            if (com.solegendary.reignofnether.hero.PossessionServer.isPossessed(a.getId()))
                problems.add("A is still possessed after the hop");
            if (!com.solegendary.reignofnether.hero.PossessionServer.isPossessed(b.getId()))
                problems.add("B is not possessed after the hop");
            // hopping back is a claim too, and claiming what you already hold is a no-op, not an error
            why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, me, uuid, a.getId(), true);
            if (why != null)
                problems.add("the hop back to A was refused: " + why);
            why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, me, uuid, a.getId(), true);
            if (why != null)
                problems.add("re-claiming the held unit should be a no-op, got: " + why);
            if (com.solegendary.reignofnether.hero.PossessionServer.isPossessed(b.getId()))
                problems.add("B is still possessed after hopping back to A");
            finish.run();
        });
    }

    /**
     * Movement prediction is server authoritative (PossessionServer.applyClaim): a client claiming its possessed unit
     * is 40 blocks away is corrected - the unit moves at most what it could walk in one tick and the claim counts as a
     * correction - while a plausible claim (a fifth of a block) is applied as sent.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void possession_corrects_an_impossible_client_position(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "predict");
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(me.getBytes());
        // its own flat strip high above the arena, like possession_claims_a_unit_and_drives_it
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 216;
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -2; dz <= 2; dz++)
                level.setBlock(new BlockPos(base.getX() + dx, y, base.getZ() + dz),
                    net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        Runnable clearStrip = () -> {
            for (int dx = -7; dx <= 7; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    level.setBlock(new BlockPos(base.getX() + dx, y, base.getZ() + dz),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        };
        Mob unit = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (unit == null) {
            clearStrip.run();
            helper.fail("could not create a vindicator unit");
            return;
        }
        unit.moveTo(base.getX() + 0.5, y + 1, base.getZ() + 0.5, -90, 0);
        ((Unit) unit).setOwnerName(me);
        level.addFreshEntity(unit);
        List<String> problems = new ArrayList<>();
        Runnable finish = () -> {
            com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            unit.discard();
            clearStrip.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        helper.runAfterDelay(6, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, me, uuid, unit.getId(), true);
            if (why != null) {
                problems.add("the owner could not possess their own unit: " + why);
                finish.run();
                return;
            }
            double x0 = unit.getX(), y0 = unit.getY(), z0 = unit.getZ();
            // a hacked client: "my unit is 40 blocks east"
            com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, unit.getId(), 1, 0, -90, 0, false, false,
                x0 + 40, y0, z0, 1);
            helper.runAfterDelay(3, () -> {
                var p = com.solegendary.reignofnether.hero.PossessionServer.of(uuid);
                if (p == null) {
                    problems.add("the possession was lost");
                    finish.run();
                    return;
                }
                double moved = unit.getX() - x0;
                if (moved > 2.0)
                    problems.add(String.format("the impossible claim moved the unit %.2f blocks", moved));
                if (p.corrections < 1)
                    problems.add("the impossible claim was not counted as a correction");
                // an honest client: a fifth of a block further on
                double x1 = unit.getX(), y1 = unit.getY(), z1 = unit.getZ();
                int accepted = p.acceptedClaims;
                com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, unit.getId(), 1, 0, -90, 0, false,
                    false, x1 + 0.2, y1, z1, 2);
                helper.runAfterDelay(3, () -> {
                    if (p.acceptedClaims <= accepted)
                        problems.add("a plausible claim was not accepted (corrections " + p.corrections + ")");
                    if (Math.abs(unit.getX() - (x1 + 0.2)) > 0.36)
                        problems.add(String.format("the plausible claim left the unit %.2f blocks off",
                            Math.abs(unit.getX() - (x1 + 0.2))));
                    finish.run();
                });
            });
        });
    }

    // ---- possession feels like Minecraft (lovish, Oct 20 2026: "first person mode feels very clunky ... doesn't feel
    // like minecraft pov that allows players to sprint and move around freely") ----

    /** A flat grass pad high above the arena (generated terrain would put bumps in a measured walk); returns its remover. */
    static Runnable possessionPad(ServerLevel level, BlockPos base, int y, int halfX, int halfZ) {
        for (int dx = -halfX; dx <= halfX; dx++)
            for (int dz = -halfZ; dz <= halfZ; dz++)
                level.setBlock(new BlockPos(base.getX() + dx, y, base.getZ() + dz),
                    net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        return () -> {
            for (int dx = -halfX; dx <= halfX; dx++)
                for (int dz = -halfZ; dz <= halfZ; dz++)
                    level.setBlock(new BlockPos(base.getX() + dx, y, base.getZ() + dz),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        };
    }

    /** A vindicator unit of {@code owner} standing on the pad at (x, z), facing +X. */
    static Mob padUnit(ServerLevel level, String owner, double x, int y, double z) {
        Mob m = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (m == null)
            return null;
        m.moveTo(x, y + 1, z, -90, 0);
        ((Unit) m).setOwnerName(owner);
        level.addFreshEntity(m);
        return m;
    }

    /**
     * Sprint (PossessionServer.applyKeys): two identical units driven forward for the same ticks, one with the sprint
     * key held, side by side on one pad - the sprinter covers about 1.3x the ground (vanilla's sprint) and wears the
     * sprinting flag; the walker does neither.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void possession_sprint_covers_more_ground_than_walking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String walkName = owner(helper, "walk"), runName = owner(helper, "run");
        java.util.UUID walkId = java.util.UUID.nameUUIDFromBytes(walkName.getBytes());
        java.util.UUID runId = java.util.UUID.nameUUIDFromBytes(runName.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 232;
        Runnable clearPad = possessionPad(level, base, y, 8, 4);
        Mob walker = padUnit(level, walkName, base.getX() - 7.5, y, base.getZ() - 2 + 0.5);
        Mob runner = padUnit(level, runName, base.getX() - 7.5, y, base.getZ() + 2 + 0.5);
        if (walker == null || runner == null) {
            clearPad.run();
            helper.fail("could not create two vindicator units");
            return;
        }
        List<String> problems = new ArrayList<>();
        Runnable finish = () -> {
            com.solegendary.reignofnether.hero.PossessionServer.release(walkId, null);
            com.solegendary.reignofnether.hero.PossessionServer.release(runId, null);
            if (runner.isSprinting())
                problems.add("the unit still wears the sprinting flag after release");
            walker.discard();
            runner.discard();
            clearPad.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        helper.runAfterDelay(6, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, walkName, walkId, walker.getId(), true);
            String why2 = com.solegendary.reignofnether.hero.PossessionServer.claim(level, runName, runId, runner.getId(), true);
            if (why != null || why2 != null) {
                problems.add("could not possess the units: " + why + " / " + why2);
                finish.run();
                return;
            }
            double w0 = walker.getX(), r0 = runner.getX();
            final int ticks = 24;
            for (int t = 0; t < ticks; t += 5)
                helper.runAfterDelay(t + 1, () -> {
                    com.solegendary.reignofnether.hero.PossessionServer.applyInput(walkId, walker.getId(), 1, 0, -90, 0,
                        false, false, false);
                    com.solegendary.reignofnether.hero.PossessionServer.applyInput(runId, runner.getId(), 1, 0, -90, 0,
                        false, false, true);
                });
            helper.runAfterDelay(ticks / 2, () -> {
                if (!runner.isSprinting())
                    problems.add("the sprinting unit does not wear vanilla's sprinting flag");
                if (walker.isSprinting())
                    problems.add("the walking unit wears the sprinting flag");
            });
            helper.runAfterDelay(ticks + 1, () -> {
                double walked = walker.getX() - w0, ran = runner.getX() - r0;
                double speed = runner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                if (walked < 0.5)
                    problems.add(String.format("the walker barely moved (%.2f blocks)", walked));
                else if (ran < walked * 1.15 || ran > walked * 1.5)
                    problems.add(String.format("sprinting covered %.2f blocks against walking's %.2f (x%.2f, want ~%.1f; "
                        + "speed %.3f, onGround %b/%b)", ran, walked, ran / walked,
                        com.solegendary.reignofnether.hero.PossessionServer.SPRINT_MULT, speed, walker.onGround(),
                        runner.onGround()));
                finish.run();
            });
        });
    }

    /**
     * Letting go of the keys stops a possessed unit the way a player stops (ground friction, a few ticks): after a
     * walk, the unit is standing still within six ticks of the release and slid well under a block.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void possession_stops_when_the_keys_are_let_go(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "stop");
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(me.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 236;
        Runnable clearPad = possessionPad(level, base, y, 8, 2);
        Mob unit = padUnit(level, me, base.getX() - 7.5, y, base.getZ() + 0.5);
        if (unit == null) {
            clearPad.run();
            helper.fail("could not create a vindicator unit");
            return;
        }
        List<String> problems = new ArrayList<>();
        Runnable finish = () -> {
            com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            unit.discard();
            clearPad.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        helper.runAfterDelay(6, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, me, uuid, unit.getId(), true);
            if (why != null) {
                problems.add("could not possess the unit: " + why);
                finish.run();
                return;
            }
            double x0 = unit.getX();
            for (int t = 0; t < 16; t += 5)
                helper.runAfterDelay(t + 1, () -> com.solegendary.reignofnether.hero.PossessionServer.applyInput(uuid,
                    unit.getId(), 1, 0, -90, 0, false, false, true));
            double[] atRelease = new double[1];
            helper.runAfterDelay(20, () -> {
                atRelease[0] = unit.getX();
                if (atRelease[0] - x0 < 0.5)
                    problems.add(String.format("the unit did not get going (%.2f blocks)", atRelease[0] - x0));
                com.solegendary.reignofnether.hero.PossessionServer.applyInput(uuid, unit.getId(), 0, 0, -90, 0,
                    false, false, false);
            });
            double[] at6 = new double[1];
            helper.runAfterDelay(26, () -> at6[0] = unit.getX());
            helper.runAfterDelay(28, () -> {
                double slid = at6[0] - atRelease[0], still = Math.abs(unit.getX() - at6[0]);
                if (slid > 0.6)
                    problems.add(String.format("the unit slid %.2f blocks after the keys were let go", slid));
                if (still > 0.03)
                    problems.add(String.format("six ticks after the release the unit still moves (%.3f blocks in 2 ticks)",
                        still));
                if (unit.isSprinting())
                    problems.add("the sprinting flag outlived the forward key");
                finish.run();
            });
        });
    }

    /**
     * Free movement: possessing a unit drops everything that would walk it on its own (its move order - the goals'
     * reset - patrol, guard, queued orders), and the RTS per-tick systems leave it alone: an anchor far away does not
     * give it a retreat order (Unit.checkAndRetreatToAnchor runs every 50 ticks), nothing moves it while its player
     * stands still.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void possession_is_not_steered_by_rts_systems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "free");
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(me.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 240;
        Runnable clearPad = possessionPad(level, base, y, 8, 2);
        Mob unit = padUnit(level, me, base.getX() + 0.5, y, base.getZ() + 0.5);
        if (unit == null) {
            clearPad.run();
            helper.fail("could not create a vindicator unit");
            return;
        }
        Unit u = (Unit) unit;
        List<String> problems = new ArrayList<>();
        Runnable finish = () -> {
            com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            unit.discard();
            clearPad.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        helper.runAfterDelay(4, () -> {
            // an RTS life before possession: a move order across the pad and an anchor at its far end
            BlockPos far = new BlockPos(base.getX() + 7, y + 1, base.getZ());
            u.setMoveTarget(far);
            u.setAnchor(far);
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, me, uuid, unit.getId(), true);
            if (why != null) {
                problems.add("could not possess the unit: " + why);
                finish.run();
                return;
            }
            if (u.getMoveGoal().getMoveTarget() != null)
                problems.add("possession kept the unit's move order (" + u.getMoveGoal().getMoveTarget() + ")");
            double x0 = unit.getX(), z0 = unit.getZ();
            // the player stands still (keys up) through more than one anchor check
            for (int t = 0; t <= 60; t += 10)
                helper.runAfterDelay(t + 1, () -> com.solegendary.reignofnether.hero.PossessionServer.applyInput(uuid,
                    unit.getId(), 0, 0, -90, 0, false, false, false));
            helper.runAfterDelay(62, () -> {
                if (u.getMoveGoal().getMoveTarget() != null)
                    problems.add("an RTS system gave the possessed unit a move order (" + u.getMoveGoal().getMoveTarget()
                        + ") - the anchor retreat?");
                double moved = Math.hypot(unit.getX() - x0, unit.getZ() - z0);
                if (moved > 0.3)
                    problems.add(String.format("the possessed unit was moved %.2f blocks while its player stood still",
                        moved));
                u.setAnchor(null);
                finish.run();
            });
        });
    }

    /**
     * The server's move check knows sprint (PossessionServer.applyClaim): claims at a pace between walking and
     * sprinting are accepted from a sprinting possessor and corrected for a walking one, and a sprinting possessor's
     * teleport-sized claim is still refused: it moves the unit no further than a sprint covers in the ticks that really
     * passed (a sequence number 10 ahead banks nothing).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void possession_accepts_sprint_claims_and_refuses_a_teleport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String walkName = owner(helper, "cwalk"), runName = owner(helper, "crun");
        java.util.UUID walkId = java.util.UUID.nameUUIDFromBytes(walkName.getBytes());
        java.util.UUID runId = java.util.UUID.nameUUIDFromBytes(runName.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 244;
        Runnable clearPad = possessionPad(level, base, y, 8, 4);
        Mob walker = padUnit(level, walkName, base.getX() - 7.5, y, base.getZ() - 2 + 0.5);
        Mob runner = padUnit(level, runName, base.getX() - 7.5, y, base.getZ() + 2 + 0.5);
        if (walker == null || runner == null) {
            clearPad.run();
            helper.fail("could not create two vindicator units");
            return;
        }
        List<String> problems = new ArrayList<>();
        Runnable finish = () -> {
            com.solegendary.reignofnether.hero.PossessionServer.release(walkId, null);
            com.solegendary.reignofnether.hero.PossessionServer.release(runId, null);
            walker.discard();
            runner.discard();
            clearPad.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        helper.runAfterDelay(6, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, walkName, walkId, walker.getId(), true);
            String why2 = com.solegendary.reignofnether.hero.PossessionServer.claim(level, runName, runId, runner.getId(), true);
            if (why != null || why2 != null) {
                problems.add("could not possess the units: " + why + " / " + why2);
                finish.run();
                return;
            }
            var pw = com.solegendary.reignofnether.hero.PossessionServer.of(walkId);
            var pr = com.solegendary.reignofnether.hero.PossessionServer.of(runId);
            double speed = runner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            // halfway between the walking and the sprinting budget per tick
            double step = (com.solegendary.reignofnether.hero.PossessionServer.walkPerTick(speed, false)
                + com.solegendary.reignofnether.hero.PossessionServer.walkPerTick(speed, true)) / 2;
            double wx = walker.getX(), wy = walker.getY(), wz = walker.getZ();
            double rx = runner.getX(), ry = runner.getY(), rz = runner.getZ();
            final int claims = 8;
            long[] lastHonestAt = new long[1];
            for (int i = 1; i <= claims; i++) {
                final int seq = i;
                helper.runAfterDelay(i, () -> {
                    lastHonestAt[0] = level.getGameTime();
                    com.solegendary.reignofnether.hero.PossessionServer.applyMove(walkId, walker.getId(), 1, 0, -90, 0,
                        false, false, false, wx + seq * step, wy, wz, seq);
                    com.solegendary.reignofnether.hero.PossessionServer.applyMove(runId, runner.getId(), 1, 0, -90, 0,
                        false, false, true, rx + seq * step, ry, rz, seq);
                });
            }
            helper.runAfterDelay(claims + 3, () -> {
                if (pr.corrections > 0 || pr.acceptedClaims < claims - 2)
                    problems.add(String.format("sprint-pace claims (%.3f a tick, speed %.3f) were corrected %d times, "
                        + "accepted %d of %d", step, speed, pr.corrections, pr.acceptedClaims, claims));
                if (pw.corrections < 1)
                    problems.add(String.format("the same pace without sprint was never corrected (accepted %d) - the "
                        + "walk budget is not checked", pw.acceptedClaims));
                double r1 = runner.getX();
                int corr = pr.corrections;
                // a hacked client that also holds sprint: "40 blocks east", with a sequence number 10 ahead - the
                // movement bucket was full (an honest client claiming every tick keeps it so), but the server allows
                // no more than the ticks that really passed since the last claim, plus the jitter allowance
                long elapsed = level.getGameTime() - lastHonestAt[0] + 1;   // applied on this tick or the next
                double bound = com.solegendary.reignofnether.hero.PossessionServer.walkPerTick(speed, true)
                    * (elapsed + com.solegendary.reignofnether.hero.PossessionServer.CLAIM_JITTER_TICKS) + 0.05;
                com.solegendary.reignofnether.hero.PossessionServer.applyMove(runId, runner.getId(), 1, 0, -90, 0,
                    false, false, true, r1 + 40, runner.getY(), runner.getZ(), claims + 10);
                helper.runAfterDelay(3, () -> {
                    double moved = runner.getX() - r1;
                    if (moved > bound)
                        problems.add(String.format("a sprinting teleport claim moved the unit %.2f blocks, more than the "
                            + "%.2f a sprint covers in the %d ticks since the last claim (+%d jitter)", moved, bound,
                            elapsed, com.solegendary.reignofnether.hero.PossessionServer.CLAIM_JITTER_TICKS));
                    if (pr.corrections <= corr)
                        problems.add("the sprinting teleport claim was not corrected");
                    finish.run();
                });
            });
        });
    }

    /**
     * CR-17: the end of a Hero role (HeroRoles.revoke - what /rts-reset's HeroRoles.clearAll runs for every Hero) tells
     * the player's client "role off", so its Backspace and team fog stop with the server's. The sink is swapped and
     * restored inside this one callback (no other test can run in between); clearAll itself would clear every
     * parallel test's Heroes, so the per-player step is what is driven here.
     */
    @GameTest(template = ARENA)
    public static void hero_role_off_is_sent_on_reset(GameTestHelper helper) {
        String cmd = owner(helper, "rolecmd"), hero = owner(helper, "roleh");
        List<String> sent = new ArrayList<>();
        var old = com.solegendary.reignofnether.hero.HeroRoles.roleSink;
        com.solegendary.reignofnether.hero.HeroRoles.assign(hero, List.of(cmd));
        boolean revoked;
        try {
            com.solegendary.reignofnether.hero.HeroRoles.roleSink = (name, on) -> sent.add(name + "=" + on);
            revoked = com.solegendary.reignofnether.hero.HeroRoles.revoke(hero);
            // a second reset finds nothing to do and sends nothing
            com.solegendary.reignofnether.hero.HeroRoles.revoke(hero);
        } finally {
            com.solegendary.reignofnether.hero.HeroRoles.roleSink = old;
            com.solegendary.reignofnether.hero.HeroRoles.unassign(hero);
        }
        List<String> problems = new ArrayList<>();
        if (!revoked)
            problems.add("revoke did not find the Hero");
        if (!sent.equals(List.of(hero + "=false")))
            problems.add("expected exactly one role-off for the Hero, sent " + sent);
        if (com.solegendary.reignofnether.hero.HeroRoles.isHeroRole(hero))
            problems.add("still a Hero after the reset");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
