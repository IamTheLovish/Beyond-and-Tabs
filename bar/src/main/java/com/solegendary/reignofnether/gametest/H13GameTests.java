package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.HeroProd;
import com.solegendary.reignofnether.hero.HeroProgression;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.hero.HeroTypes;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests of H13a: the T2-lab hero frame (a hero trained at a T2 lab that counts against allowedHeroes but skips
 * the castle clamp, spawning at level 3 with its points) and the Horde's Ash Shaman on the Flame Sanctuary. Every test
 * works inside its own arena with unique owners; enemy dummies have no AI so they stay where the test put them.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class H13GameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String what) {
        return "gametest_h13_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A Flame Sanctuary placement for {@code owner} inside this test's arena (never registered with the server). */
    static ProductionPlacement sanctuary(GameTestHelper helper, String owner) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.FLAME_SANCTUARY, helper.getLevel(),
            new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, owner, false);
        if (bp instanceof ProductionPlacement pp) {
            pp.isBuilt = true;
            return pp;
        }
        return null;
    }

    static Mob dummy(GameTestHelper helper, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, x, z);
        if (m != null)
            m.setNoAi(true);
        return m;
    }

    /**
     * A Shaman owned by {@code owner} with every ability at rank 1, ready to cast - by the test alone: its owner's Hero
     * AI is switched off, since with PR 418 the AI casts the Shaman's spells at the dummies by itself and a test that
     * checks who its own cast hurt must be the only caster.
     */
    static FactionHero readyShaman(GameTestHelper helper, String owner, int x, int z) {
        com.solegendary.reignofnether.hero.HeroBot.setAiEnabled(owner, false);
        Mob m = HeroGameTests.spawnHero(helper, HeroKits.ASH_SHAMAN, owner, x, z);
        if (!(m instanceof FactionHero fh))
            return null;
        for (int i = 0; i < 4; i++) {
            FactionHeroAbility a = HeroKits.ability(fh, i);
            a.setRank(fh, 1);
            a.updateStatsForRank(fh);
        }
        return fh;
    }

    /**
     * AC-1: the Flame Sanctuary offers the Shaman and its revive (slot 9); the server refuses a second Shaman while one
     * is alive or fallen, and then the revive is what the lab accepts.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void h13_flame_sanctuary_offers_the_shaman(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ProductionBuilding pb = Buildings.FLAME_SANCTUARY;
        if (!pb.productions.get().contains(ProductionItems.ASH_SHAMAN)
            || !pb.productions.get().contains(ProductionItems.ASH_SHAMAN_REVIVE)) {
            helper.fail("the Flame Sanctuary does not list the Ash Shaman and its revive");
            return;
        }
        var type = HeroTypes.typeOf(HeroKits.ASH_SHAMAN);
        if (type == null || ProductionItems.getProductionItem(type) != ProductionItems.ASH_SHAMAN)
            problems.add("the Shaman type does not resolve to its production item");
        String me = owner(helper, "offer");
        com.solegendary.reignofnether.hero.HeroBot.setAiEnabled(me, false);   // no Shaman AI casting at a neighbouring pad (PR 418; see readyShaman)
        ProductionPlacement pp = sanctuary(helper, me);
        if (pp == null) {
            helper.fail("no Flame Sanctuary placement");
            return;
        }
        Resources pool = new Resources(me, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(me, ProductionItems.RESEARCH_TIER_2);   // the T2 lab needs Tier 2 (TierGate)
        Mob shaman = null;
        HeroUnitSave fallen = null;
        try {
            if (ProductionItems.ASH_SHAMAN.getProduceErrorMsg(pp) != null)
                problems.add("a fresh player is refused the Shaman: " + ProductionItems.ASH_SHAMAN.getProduceErrorMsg(pp));
            if (!pp.startProductionItem(ProductionItems.ASH_SHAMAN))
                problems.add("the server refused to queue the Shaman");
            pp.productionQueue.clear();
            if (ProductionItems.ASH_SHAMAN_REVIVE.getProduceErrorMsg(pp) == null)
                problems.add("a revive was offered with no fallen Shaman");

            shaman = HeroGameTests.spawnHero(helper, HeroKits.ASH_SHAMAN, me, 6, 6);
            if (ProductionItems.ASH_SHAMAN.getProduceErrorMsg(pp) == null)
                problems.add("a second Shaman was accepted while one is alive");
            shaman.discard();
            shaman = null;

            String heroName = type.getDescriptionId();
            fallen = new HeroUnitSave("00000000-0000-0000-0000-000000000013", heroName, me,
                HeroProgression.xpForLevel(3), 1, 0, 1, 0, 0, 0, null);
            HeroServerEvents.fallenHeroes.add(fallen);
            if (ProductionItems.ASH_SHAMAN.getProduceErrorMsg(pp) == null)
                problems.add("a second Shaman was accepted while one is fallen");
            if (ProductionItems.ASH_SHAMAN_REVIVE.getProduceErrorMsg(pp) != null)
                problems.add("the revive is refused for a fallen Shaman: " + ProductionItems.ASH_SHAMAN_REVIVE.getProduceErrorMsg(pp));
        } finally {
            if (fallen != null)
                HeroServerEvents.fallenHeroes.remove(fallen);
            if (shaman != null)
                shaman.discard();
            ResourcesServerEvents.resourcesList.remove(pool);
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(me);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * AC-2: with no castle, a player holding a live T1-lab hero can still train the Shaman (cap 2); a third hero is
     * refused; and the T1 labs keep the old clamp (a second T1 hero with no castle is refused).
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void h13_shaman_skips_the_castle_clamp_but_counts_against_the_cap(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String me = owner(helper, "cap");
        com.solegendary.reignofnether.hero.HeroBot.setAiEnabled(me, false);   // no Shaman AI casting at a neighbouring pad (PR 418; see readyShaman)
        ProductionPlacement pp = sanctuary(helper, me);
        if (pp == null) {
            helper.fail("no Flame Sanctuary placement");
            return;
        }
        if (BuildingUtils.castleOwned(false, me)) {
            helper.fail("test owner unexpectedly owns a castle");
            return;
        }
        Mob warchief = HeroGameTests.spawnHero(helper, HeroKits.HORDE_WARCHIEF, me, 4, 4);
        Mob shaman = null;
        try {
            int cap = helper.getLevel().getGameRules().getInt(com.solegendary.reignofnether.registrars.GameRuleRegistrar.ALLOWED_HEROES);
            if (cap < 2)
                problems.add("allowedHeroes is " + cap + ", the test needs the default 2");
            String err = ProductionItems.ASH_SHAMAN.getProduceErrorMsg(pp);
            if (err != null)
                problems.add("the Shaman was refused beside a T1 hero with no castle: " + err);
            String t1 = HeroProd.forKit(HeroKits.GRAVECALLER).getProduceErrorMsg(pp);
            if (t1 == null)
                problems.add("a second T1-lab hero was accepted with no castle (the clamp must stay for T1 labs)");
            shaman = HeroGameTests.spawnHero(helper, HeroKits.ASH_SHAMAN, me, 7, 4);
            String third = HeroProd.forKit(HeroKits.GRAVECALLER).getProduceErrorMsg(pp);
            if (third == null)
                problems.add("a third hero was accepted (cap 2)");
            else if (!third.equals("hud.hero.reignofnether.error.too_many_heroes"))
                problems.add("the third hero was refused for the wrong reason: " + third);
        } finally {
            if (warchief != null)
                warchief.discard();
            if (shaman != null)
                shaman.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * AC-2 (other order): with no castle, a player whose only hero is the Shaman can still train a T1-lab hero, since
     * the T2-lab hero does not count toward the one-T1-hero clamp; a third hero is still refused.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void h13_shaman_first_then_t1_hero_without_castle(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        String me = owner(helper, "order");
        com.solegendary.reignofnether.hero.HeroBot.setAiEnabled(me, false);   // no Shaman AI casting at a neighbouring pad (PR 418; see readyShaman)
        ProductionPlacement pp = sanctuary(helper, me);
        if (pp == null) {
            helper.fail("no Flame Sanctuary placement");
            return;
        }
        if (BuildingUtils.castleOwned(false, me)) {
            helper.fail("test owner unexpectedly owns a castle");
            return;
        }
        Mob shaman = HeroGameTests.spawnHero(helper, HeroKits.ASH_SHAMAN, me, 4, 4);
        Mob warchief = null;
        try {
            String t1 = HeroProd.forKit(HeroKits.HORDE_WARCHIEF).getProduceErrorMsg(pp);
            if (t1 != null)
                problems.add("a T1-lab hero was refused when the only hero is the Shaman (no castle): " + t1);
            warchief = HeroGameTests.spawnHero(helper, HeroKits.HORDE_WARCHIEF, me, 7, 4);
            String third = HeroProd.forKit(HeroKits.GRAVECALLER).getProduceErrorMsg(pp);
            if (third == null)
                problems.add("a third hero was accepted (cap 2)");
            else if (!third.equals("hud.hero.reignofnether.error.too_many_heroes"))
                problems.add("the third hero was refused for the wrong reason: " + third);
        } finally {
            if (shaman != null)
                shaman.discard();
            if (warchief != null)
                warchief.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** AC-3: a Shaman trained the real way (item completion) spawns at level 3 holding exactly 3 ability points. */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void h13_shaman_spawns_at_level_three_with_three_points(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "level");
        com.solegendary.reignofnether.hero.HeroBot.setAiEnabled(me, false);   // no Shaman AI casting at a neighbouring pad (PR 418; see readyShaman)
        ProductionPlacement pp = sanctuary(helper, me);
        if (pp == null) {
            helper.fail("no Flame Sanctuary placement");
            return;
        }
        Resources pool = new Resources(me, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        pp.productionQueue.clear();
        ProductionItems.ASH_SHAMAN.onComplete.accept(level, pp);
        Mob found = null;
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof Mob m && m.getType() == HeroTypes.typeOf(HeroKits.ASH_SHAMAN) && m instanceof Unit u && me.equals(u.getOwnerName()))
                found = m;
        if (!(found instanceof FactionHero fh)) {
            ResourcesServerEvents.resourcesList.remove(pool);
            helper.fail("completing the Shaman's training produced no Shaman");
            return;
        }
        Mob shaman = found;
        helper.runAfterDelay(5, () -> {   // past the first tick, when the level's stats are applied
            List<String> problems = new ArrayList<>();
            if (fh.getHeroLevel() != 3)
                problems.add("level " + fh.getHeroLevel() + ", not 3");
            if (fh.getExperience() != HeroProgression.xpForLevel(3))
                problems.add("experience " + fh.getExperience() + ", not " + HeroProgression.xpForLevel(3));
            if (fh.getSkillPoints() != 3)
                problems.add(fh.getSkillPoints() + " ability points, not exactly 3");
            float wantHp = HeroKits.ASH_SHAMAN.health * HeroProgression.statMultiplier(3);
            if (Math.abs(shaman.getMaxHealth() - wantHp) > 0.5f)
                problems.add("max health " + shaman.getMaxHealth() + ", not the level-3 " + wantHp);
            if (shaman.getHealth() < shaman.getMaxHealth() - 0.5f)
                problems.add("spawned hurt: " + shaman.getHealth() + "/" + shaman.getMaxHealth());
            shaman.discard();
            ResourcesServerEvents.resourcesList.remove(pool);
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /** AC-4a: Firewall burns the enemy standing on its line and not the one beside it. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void h13_firewall_damages_only_its_line(GameTestHelper helper) {
        String me = owner(helper, "fw"), them = owner(helper, "fw_enemy");
        FactionHero shaman = readyShaman(helper, me, 3, 10);
        Mob onLine = dummy(helper, them, 9, 10), beside = dummy(helper, them, 9, 17);
        if (shaman == null || onLine == null || beside == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        helper.runAfterDelay(3, () -> {
            Mob hero = (Mob) shaman;
            Vec3Aim aim = new Vec3Aim(hero, 10, 10, helper);
            if (!HeroKits.ability(shaman, 0).castNow(shaman, null, aim.at))
                helper.fail("Firewall did not cast");
            else
                helper.runAfterDelay(14, () -> {
                    List<String> problems = new ArrayList<>();
                    if (onLine.getHealth() >= onLine.getMaxHealth())
                        problems.add("the enemy on the line took no damage");
                    if (beside.getHealth() < beside.getMaxHealth())
                        problems.add("the enemy off the line was hurt");
                    for (Mob m : List.of(hero, onLine, beside))
                        m.discard();
                    if (problems.isEmpty())
                        helper.succeed();
                    else
                        helper.fail(String.join("; ", problems));
                });
        });
    }

    /** AC-4b: Lava Geyser deals nothing before its telegraph ends, then hurts inside its radius only. */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void h13_lava_geyser_waits_for_its_telegraph(GameTestHelper helper) {
        String me = owner(helper, "geyser"), them = owner(helper, "geyser_enemy");
        FactionHero shaman = readyShaman(helper, me, 3, 10);
        Mob inside = dummy(helper, them, 12, 10), outside = dummy(helper, them, 12, 17);
        if (shaman == null || inside == null || outside == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        helper.runAfterDelay(3, () -> {
            Mob hero = (Mob) shaman;
            Vec3Aim aim = new Vec3Aim(hero, 12, 10, helper);
            if (!HeroKits.ability(shaman, 1).castNow(shaman, null, aim.at)) {
                helper.fail("Lava Geyser did not cast");
                return;
            }
            // the telegraph is 1.5 s = 30 ticks: nothing may hurt before it ends
            helper.runAfterDelay(20, () -> {
                if (inside.getHealth() < inside.getMaxHealth()) {
                    helper.fail("Lava Geyser hurt an enemy 20 ticks in, before its 30-tick telegraph ended");
                    return;
                }
                helper.runAfterDelay(25, () -> {
                    List<String> problems = new ArrayList<>();
                    if (inside.getHealth() >= inside.getMaxHealth())
                        problems.add("the enemy in the circle was not hurt after the eruption");
                    if (outside.getHealth() < outside.getMaxHealth())
                        problems.add("the enemy outside the circle was hurt");
                    for (Mob m : List.of(hero, inside, outside))
                        m.discard();
                    if (problems.isEmpty())
                        helper.succeed();
                    else
                        helper.fail(String.join("; ", problems));
                });
            });
        });
    }

    /** AC-4c: Ember Totem burns an enemy in range and not one outside it. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void h13_ember_totem_burns_only_in_range(GameTestHelper helper) {
        String me = owner(helper, "totem"), them = owner(helper, "totem_enemy");
        FactionHero shaman = readyShaman(helper, me, 3, 10);
        Mob near = dummy(helper, them, 11, 11), far = dummy(helper, them, 11, 19);
        if (shaman == null || near == null || far == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        helper.runAfterDelay(3, () -> {
            Mob hero = (Mob) shaman;
            Vec3Aim aim = new Vec3Aim(hero, 10, 10, helper);
            if (!HeroKits.ability(shaman, 2).castNow(shaman, null, aim.at)) {
                helper.fail("Ember Totem did not cast");
                return;
            }
            helper.runAfterDelay(30, () -> {
                List<String> problems = new ArrayList<>();
                if (near.getHealth() >= near.getMaxHealth())
                    problems.add("the enemy by the totem took no damage");
                if (!near.isOnFire())
                    problems.add("the enemy by the totem is not burning");
                if (far.getHealth() < far.getMaxHealth() || far.isOnFire())
                    problems.add("the enemy outside the totem's radius was burned");
                for (Mob m : List.of(hero, near, far))
                    m.discard();
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            });
        });
    }

    /** AC-4d: Kindle (passive) sets a target the Shaman hits alight; an untouched bystander stays cold. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void h13_kindle_sets_a_hit_target_alight(GameTestHelper helper) {
        String me = owner(helper, "kindle"), them = owner(helper, "kindle_enemy");
        FactionHero shaman = readyShaman(helper, me, 4, 4);
        Mob target = dummy(helper, them, 5, 4), bystander = dummy(helper, them, 8, 10);
        if (shaman == null || target == null || bystander == null) {
            helper.fail("could not spawn the test units");
            return;
        }
        helper.runAfterDelay(3, () -> {
            Mob hero = (Mob) shaman;
            List<String> problems = new ArrayList<>();
            if (target.isOnFire())
                problems.add("the target was already burning");
            boolean hit = hero.doHurtTarget(target);
            if (!hit)
                problems.add("the Shaman's attack did not land");
            if (!target.isOnFire())
                problems.add("the hit target is not on fire");
            if (bystander.isOnFire())
                problems.add("a bystander is on fire");
            for (Mob m : List.of(hero, target, bystander))
                m.discard();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /** The aimed point at (x, z) of the test arena, on the hero's floor. */
    static final class Vec3Aim {
        final net.minecraft.world.phys.Vec3 at;

        Vec3Aim(Mob hero, int x, int z, GameTestHelper helper) {
            BlockPos p = helper.absolutePos(new BlockPos(x, 2, z));
            this.at = new net.minecraft.world.phys.Vec3(p.getX() + 0.5, hero.getY(), p.getZ() + 0.5);
        }
    }
}
