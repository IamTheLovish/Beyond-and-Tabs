package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.SoulChains;
import com.solegendary.reignofnether.ability.abilities.SoulHarvest;
import com.solegendary.reignofnether.ability.abilities.Wail;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.essence.SoulbinderServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import com.solegendary.reignofnether.unit.units.monsters.SoulbinderUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The Gravebound essence caster, the Soulbinder (design/essence_casters.md, lovish approved draft 2 on Oct 29 2026).
 * Written before the code, from his numbers:
 * <ul>
 *   <li>trained at the Dungeon (the Gravebound T2 lab) for a cost, an essence carrier that gathers the wisps near it;</li>
 *   <li>Wail (5 essence, 12 s): the enemies in a cone in front of it flee for 2 s;</li>
 *   <li>Soul Harvest (10 essence, 25 s): 2 spectral skeletons fight for 20 s, then vanish; summons, so no essence;</li>
 *   <li>Soul Chains (20 essence, 60 s): up to 4 enemies linked for 10 s; 50% of the damage one takes is dealt to the
 *       others;</li>
 *   <li>the shared Empower; every ability refuses - nothing spent, no cooldown - when it cannot act, and its cooldown
 *       holds after a cast.</li>
 * </ul>
 * Every unit stands still (no AI) with its autocast off, so only the test's own casts happen. Each test records what it
 * sees in its early callbacks (which never throw) and judges in its last, which always cleans up whatever the verdict.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SoulbinderGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "soulbinder";

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_soulbinder_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    private static void discardAll(List<? extends Entity> entities) {
        for (Entity e : entities)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** A unit that stands where it is put, never casting by itself. */
    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null) {
            m.setNoAi(true);
            AutocastState.setAll((Unit) m, false);
        }
        return m;
    }

    /**
     * A Soulbinder holding {@code essence}. Flagged a carrier as well, so the ability tests judge the abilities alone
     * (that the unit is a carrier by itself is the training and gathering tests' business).
     */
    private static Mob binder(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = still(helper, EntityRegistrar.SOULBINDER_UNIT.get(), owner, x, z);
        if (m == null)
            return null;
        Essence.setCarrier(m, true);
        Essence.add(m, essence);
        return m;
    }

    private static <T extends Ability> T ability(Mob binder, Class<T> type) {
        for (Ability a : ((Unit) binder).getAbilities().get())
            if (type.isInstance(a))
                return type.cast(a);
        return null;
    }

    private static boolean scared(LivingEntity le) {
        return le.hasEffect(MobEffectRegistrar.FEARFUL.get());
    }

    private static List<Mob> spawned(List<Mob> units) {
        return units.stream().filter(Objects::nonNull).toList();
    }

    // ------------------------------------------------------------------------------------------- the unit itself

    /**
     * The Dungeon trains the Soulbinder: its item names the unit registered to Gravebound, completing it (the real
     * onComplete -> produceUnit path) makes an owned Soulbinder that is an essence carrier, costs its ResourceCosts entry
     * (metal and population above 0), has its 40 health, and carries Wail, Soul Harvest, Soul Chains and Empower with
     * lovish's essence costs and cooldowns. The placement is never registered; the unit is removed at the end.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void soulbinder_trains_at_the_dungeon_with_its_cost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "train");
        List<String> problems = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        try {
            Building dungeon = Buildings.DUNGEON;
            if (!(dungeon instanceof ProductionBuilding lab) || !lab.productions.get().contains(ProductionItems.SOULBINDER))
                problems.add("the Dungeon (the Gravebound T2 lab) does not train the Soulbinder");
            if (!Factions.MONSTERS.key.equals(Factions.ENTITY_FACTION.get(EntityType.getKey(EntityRegistrar.SOULBINDER_UNIT.get()))))
                problems.add("the Soulbinder is not registered to Gravebound");
            if (EntityRegistrar.getEntityType(ProductionItems.SOULBINDER.getItemName()) != EntityRegistrar.SOULBINDER_UNIT.get())
                problems.add("the Soulbinder's item name does not resolve to its entity type");
            ResourceCost cost = ResourceCosts.SOULBINDER;
            if (cost.metal() <= 0 || cost.population <= 0)
                problems.add("the Soulbinder costs " + cost.metal() + " metal and " + cost.population + " population: it must cost something");
            BlockPos corner = helper.absolutePos(BlockPos.ZERO);
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(dungeon, level,
                new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, owner, false);
            if (!(bp instanceof ProductionPlacement pp)) {
                problems.add("the Dungeon is not a production placement");
            } else {
                pp.isBuilt = true;
                ProductionItems.SOULBINDER.onComplete.accept(level, pp);
                Mob unit = CombatEngineerGameTests.findProduced(level, pp, EntityRegistrar.SOULBINDER_UNIT.get(), owner);
                if (unit == null) {
                    problems.add("completing the Soulbinder item produced no unit");
                } else {
                    made.add(unit);
                    if (!(unit instanceof SoulbinderUnit))
                        problems.add("the produced unit is a " + unit.getClass().getSimpleName());
                    if (!Essence.isCarrier(unit))
                        problems.add("the Soulbinder is not an essence carrier");
                    if (((Unit) unit).getCost() != cost)
                        problems.add("its cost is not ResourceCosts.SOULBINDER");
                    if (Math.abs(unit.getMaxHealth() - 40f) > 0.5f || SoulbinderUnit.maxHealth != 40f)
                        problems.add("max health " + unit.getMaxHealth() + " (maxHealth " + SoulbinderUnit.maxHealth + "), expected 40");
                    List<UnitAction> actions = new ArrayList<>();
                    for (Ability a : ((Unit) unit).getAbilities().get())
                        actions.add(a.action);
                    List<UnitAction> want = List.of(UnitAction.WAIL, UnitAction.SOUL_HARVEST, UnitAction.SOUL_CHAINS, UnitAction.EMPOWER);
                    if (!actions.equals(want))
                        problems.add("its abilities are " + actions + ", expected " + want);
                    if (ability(unit, Empower.class) == null)
                        problems.add("it has no Empower");
                    int[][] costs = {
                        { Wail.ESSENCE_COST, 5, Wail.CD_SECONDS, 12 },
                        { SoulHarvest.ESSENCE_COST, 10, SoulHarvest.CD_SECONDS, 25 },
                        { SoulChains.ESSENCE_COST, 20, SoulChains.CD_SECONDS, 60 } };
                    String[] names = { "Wail", "Soul Harvest", "Soul Chains" };
                    for (int i = 0; i < costs.length; i++)
                        if (costs[i][0] != costs[i][1] || costs[i][2] != costs[i][3])
                            problems.add(names[i] + " costs " + costs[i][0] + " essence with " + costs[i][2] + " s, lovish set "
                                + costs[i][1] + " and " + costs[i][3] + " s");
                    Wail wail = ability(unit, Wail.class);
                    SoulHarvest harvest = ability(unit, SoulHarvest.class);
                    SoulChains chains = ability(unit, SoulChains.class);
                    if (wail == null || harvest == null || chains == null)
                        problems.add("an ability is missing");
                    else if (wail.cooldownMax != 12 * 20 || harvest.cooldownMax != 25 * 20 || chains.cooldownMax != 60 * 20)
                        problems.add("cooldowns in ticks " + wail.cooldownMax + " / " + harvest.cooldownMax + " / " + chains.cooldownMax
                            + ", expected 240 / 500 / 1200");
                }
            }
        } catch (RuntimeException ex) {
            problems.add("threw " + ex);
        } finally {
            discardAll(made);
        }
        finish(helper, problems);
    }

    /** A Soulbinder is a carrier by itself: a 4-essence wisp 5 blocks from it flies in and its essence is 4. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void soulbinder_gathers_essence_wisps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "gather");
        // in a corner, so no neighbouring arena's wisp is within its 8 blocks
        Mob binder = still(helper, EntityRegistrar.SOULBINDER_UNIT.get(), owner, 2, 2);
        if (binder == null) {
            helper.fail("could not spawn the Soulbinder");
            return;
        }
        Entity wisp = EssenceServerEvents.spawnWisp(level, binder.getX() + 5, binder.getY() + 1, binder.getZ(), 4);
        if (wisp == null) {
            discardAll(spawned(List.of(binder)));
            helper.fail("spawnWisp made no wisp");
            return;
        }
        helper.runAfterDelay(80, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (!Essence.isCarrier(binder))
                    problems.add("the Soulbinder is not an essence carrier");
                if (Essence.get(binder) != 4)
                    problems.add("the Soulbinder holds " + Essence.get(binder) + " essence, expected the wisp's 4");
                if (!wisp.isRemoved())
                    problems.add("the wisp 5 blocks away was never gathered");
            } finally {
                discardAll(spawned(List.of(binder)));
                if (!wisp.isRemoved())
                    wisp.discard();
            }
            finish(helper, problems);
        });
    }

    /** The Soulbinder's Empower: 15 essence, a friend 3 blocks away gains a rank. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soulbinder_empowers_a_friend(GameTestHelper helper) {
        String owner = owner(helper, "emp");
        Mob binder = binder(helper, owner, 4, 4, 30);
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 7, 4);
        List<Mob> all = new ArrayList<>(Arrays.asList(binder, friend));
        if (binder == null || friend == null) {
            discardAll(spawned(all));
            helper.fail("could not spawn the Soulbinder and its friend");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Empower empower = ability(binder, Empower.class);
                if (empower == null) {
                    problems.add("the Soulbinder has no Empower");
                } else {
                    empower.use(helper.getLevel(), (Unit) binder, friend);
                    if (VeterancyServerEvents.getRank(friend) != 1)
                        problems.add("the friend is rank " + VeterancyServerEvents.getRank(friend) + " after Empower, expected 1");
                    if (Essence.get(binder) != 15)
                        problems.add("the Soulbinder holds " + Essence.get(binder) + " essence after Empower, expected 15");
                }
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------------------ Wail

    /**
     * Wail aimed east: the two enemies in the cone (straight ahead 3 blocks, and 4.5 out at 27 degrees) flee for 2 s -
     * scared and uncontrollable for 40 ticks, sent further away from the Soulbinder. An enemy 56 degrees off the aim, one
     * behind, one 8 blocks out, and a friend inside the cone are untouched. 5 of 30 essence spent, the 12 s cooldown
     * starts, and a second Wail at once (aimed at the enemy behind) is refused by it.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soulbinder_wail_scares_the_enemies_in_its_cone_for_2_seconds(GameTestHelper helper) {
        String owner = owner(helper, "wail"), foe = owner(helper, "wailfoe");
        Mob binder = binder(helper, owner, 3, 8, 30);
        Mob ahead = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 8);
        Mob wide = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 7, 6);
        Mob offAngle = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 5, 11);
        Mob behind = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 1, 8);
        Mob far = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 11, 8);
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 5, 7);
        List<Mob> all = new ArrayList<>(Arrays.asList(binder, ahead, wide, offAngle, behind, far, friend));
        if (all.contains(null)) {
            discardAll(spawned(all));
            helper.fail("could not spawn the Soulbinder and the seven units");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Wail wail = ability(binder, Wail.class);
                if (wail == null)
                    throw new IllegalStateException("the Soulbinder has no Wail");
                if (Wail.FLEE_SECONDS != 2)
                    problems.add("lovish set a 2 s flee, FLEE_SECONDS is " + Wail.FLEE_SECONDS);
                double aheadWas = ahead.distanceTo(binder);
                wail.use(helper.getLevel(), (Unit) binder, ahead);
                for (Mob m : List.of(ahead, wide)) {
                    String what = m == ahead ? "the enemy straight ahead" : "the enemy 27 degrees off the aim";
                    MobEffectInstance fear = m.getEffect(MobEffectRegistrar.FEARFUL.get());
                    if (fear == null)
                        problems.add(what + " is not scared");
                    else if (fear.getDuration() < 2 * 20 - 3 || fear.getDuration() > 2 * 20)
                        problems.add(what + " is scared for " + fear.getDuration() + " ticks, expected 40");
                    if (!m.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get()))
                        problems.add(what + " can still be ordered while it flees");
                }
                BlockPos fleeTo = ((Unit) ahead).getMoveGoal() == null ? null : ((Unit) ahead).getMoveGoal().getMoveTarget();
                if (fleeTo == null)
                    problems.add("the enemy ahead was given nowhere to flee to");
                else if (Vec3.atBottomCenterOf(fleeTo).distanceTo(binder.position()) < aheadWas + 2)
                    problems.add("the enemy ahead flees to " + fleeTo.toShortString() + ", not away from the Soulbinder");
                if (scared(offAngle))
                    problems.add("an enemy 56 degrees off the aim (outside the cone) is scared");
                if (scared(behind))
                    problems.add("an enemy behind the Soulbinder is scared");
                if (scared(far))
                    problems.add("an enemy 8 blocks out (beyond the " + Wail.RANGE + "-block cone) is scared");
                if (scared(friend))
                    problems.add("a friend inside the cone is scared");
                if (Essence.get(binder) != 25)
                    problems.add("the Soulbinder holds " + Essence.get(binder) + " essence after Wail, expected 30 - 5 = 25");
                float cd = ((Unit) binder).getCooldown(wail);
                if (cd < 12 * 20 - 2)
                    problems.add("Wail's cooldown is " + cd + " ticks after the cast, expected 240");
                // the cooldown holds: a second Wail now, toward the enemy behind, is refused
                wail.use(helper.getLevel(), (Unit) binder, behind);
                if (scared(behind) || Essence.get(binder) != 25)
                    problems.add("a second Wail during the cooldown went off (behind scared " + scared(behind)
                        + ", essence " + Essence.get(binder) + ")");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Wail refuses without spending or starting its cooldown: short of essence (4 of 5) with an enemy in the cone, and
     * with 30 essence but no enemy in the cone (only one behind it and a friend in front). Nobody is scared.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soulbinder_wail_refuses_when_short_or_with_no_enemy_in_front(GameTestHelper helper) {
        String owner = owner(helper, "wailno"), foe = owner(helper, "wailnofoe");
        Mob poor = binder(helper, owner, 3, 3, 4);
        Mob target = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 3);
        Mob rich = binder(helper, owner, 3, 12, 30);
        Mob behind = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 1, 12);
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 6, 12);
        List<Mob> all = new ArrayList<>(Arrays.asList(poor, target, rich, behind, friend));
        if (all.contains(null)) {
            discardAll(spawned(all));
            helper.fail("could not spawn the two Soulbinders and their targets");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Wail wail = ability(poor, Wail.class);
                if (wail == null)
                    throw new IllegalStateException("the Soulbinder has no Wail");
                wail.use(helper.getLevel(), (Unit) poor, target);
                if (Essence.get(poor) != 4)
                    problems.add("a Wail short of essence changed it to " + Essence.get(poor));
                if (((Unit) poor).getCooldown(wail) > 0)
                    problems.add("a Wail short of essence set the cooldown");
                if (scared(target))
                    problems.add("a Wail short of essence scared the enemy");
                wail.use(helper.getLevel(), (Unit) rich, helper.absolutePos(new BlockPos(9, 2, 12)));
                if (Essence.get(rich) != 30)
                    problems.add("a Wail at no enemy spent essence (" + Essence.get(rich) + " left of 30)");
                if (((Unit) rich).getCooldown(wail) > 0)
                    problems.add("a Wail at no enemy set the cooldown");
                if (scared(behind) || scared(friend))
                    problems.add("a Wail at no enemy scared the unit behind it or the friend in front");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ---------------------------------------------------------------------------------------------- Soul Harvest

    /**
     * Soul Harvest: 2 skeletons rise beside the Soulbinder for its owner, marked summoned, set to crumble 20 s later; 10
     * of 30 essence spent, the 25 s cooldown starts and a second cast at once is refused. One skeleton whose time is up
     * vanishes; one killed leaves no essence wisp.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void soulbinder_soul_harvest_raises_2_skeletons_for_20_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "harvest");
        Mob binder = binder(helper, owner, 4, 4, 30);
        if (binder == null) {
            helper.fail("could not spawn the Soulbinder");
            return;
        }
        List<Entity> all = new ArrayList<>(List.of(binder));
        List<String> early = new ArrayList<>();
        List<Mob> raised = new ArrayList<>();
        Vec3[] killedAt = { null };
        helper.runAfterDelay(2, () -> {
            try {
                SoulHarvest harvest = ability(binder, SoulHarvest.class);
                if (harvest == null) {
                    early.add("the Soulbinder has no Soul Harvest");
                    return;
                }
                long now = level.getGameTime();
                harvest.use(level, (Unit) binder, binder.blockPosition());
                raised.addAll(SoulbinderServerEvents.summonsOf(binder));
                // anything the cast made, found or not by summonsOf, is cleaned up
                for (SkeletonUnit sk : level.getEntitiesOfClass(SkeletonUnit.class, binder.getBoundingBox().inflate(5)))
                    if (owner.equals(sk.getOwnerName()))
                        all.add(sk);
                if (raised.size() != 2)
                    early.add("Soul Harvest raised " + raised.size() + " skeletons, expected 2");
                for (Mob m : raised) {
                    if (!(m instanceof SkeletonUnit) || !owner.equals(((Unit) m).getOwnerName()))
                        early.add("a raised unit is a " + m.getClass().getSimpleName() + " of " + ((Unit) m).getOwnerName());
                    if (!Essence.isSummoned(m))
                        early.add("a raised skeleton is not marked summoned: its death would drop essence");
                    if (!SoulbinderServerEvents.isHarvestSummon(m))
                        early.add("a raised skeleton is not a Soul Harvest summon");
                    long end = SoulbinderServerEvents.summonEnd(m);
                    if (Math.abs(end - (now + 20 * 20)) > 1)
                        early.add("a skeleton crumbles at " + end + ", expected 20 s after the cast (" + (now + 400) + ")");
                    if (m.distanceTo(binder) > 3)
                        early.add("a skeleton rose " + String.format("%.1f", m.distanceTo(binder)) + " blocks from the Soulbinder");
                }
                if (Essence.get(binder) != 20)
                    early.add("the Soulbinder holds " + Essence.get(binder) + " essence after Soul Harvest, expected 20");
                float cd = ((Unit) binder).getCooldown(harvest);
                if (cd < 25 * 20 - 2)
                    early.add("Soul Harvest's cooldown is " + cd + " ticks after the cast, expected 500");
                harvest.use(level, (Unit) binder, binder.blockPosition());
                if (Essence.get(binder) != 20 || SoulbinderServerEvents.summonsOf(binder).size() != raised.size())
                    early.add("a second Soul Harvest during the cooldown went off (essence " + Essence.get(binder) + ")");
            } catch (RuntimeException ex) {
                early.add("casting threw " + ex);
            }
        });
        helper.runAfterDelay(4, () -> {
            try {
                if (raised.size() >= 2) {
                    SoulbinderServerEvents.setSummonEnd(raised.get(0), level.getGameTime());
                    killedAt[0] = raised.get(1).position();
                    raised.get(1).kill();
                }
            } catch (RuntimeException ex) {
                early.add("ending the skeletons threw " + ex);
            }
        });
        helper.runAfterDelay(10, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (raised.size() >= 2) {
                    if (!raised.get(0).isRemoved())
                        problems.add("a skeleton whose 20 s were up is still standing");
                    if (raised.get(1).isAlive())
                        problems.add("the killed skeleton is alive");
                    if (killedAt[0] != null)
                        for (Entity w : EssenceServerEvents.getWisps())
                            if (!w.isRemoved() && w.position().distanceTo(killedAt[0]) < 2) {
                                problems.add("the killed skeleton dropped an essence wisp: summons must not farm essence");
                                w.discard();
                            }
                    if (!SoulbinderServerEvents.summonsOf(binder).isEmpty())
                        problems.add("summonsOf still lists " + SoulbinderServerEvents.summonsOf(binder).size() + " skeletons");
                }
            } finally {
                all.addAll(raised);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** Short of essence (9 of 10) Soul Harvest is refused: nothing spent, no cooldown, no skeleton. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soulbinder_soul_harvest_refuses_when_short(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "harvestno");
        Mob binder = binder(helper, owner, 8, 8, 9);
        if (binder == null) {
            helper.fail("could not spawn the Soulbinder");
            return;
        }
        List<Entity> all = new ArrayList<>(List.of(binder));
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                SoulHarvest harvest = ability(binder, SoulHarvest.class);
                if (harvest == null)
                    throw new IllegalStateException("the Soulbinder has no Soul Harvest");
                harvest.use(level, (Unit) binder, binder.blockPosition());
                for (SkeletonUnit sk : level.getEntitiesOfClass(SkeletonUnit.class, new AABB(binder.blockPosition()).inflate(6)))
                    if (owner.equals(sk.getOwnerName())) {
                        all.add(sk);
                        problems.add("a skeleton rose without the essence");
                    }
                if (Essence.get(binder) != 9)
                    problems.add("a refused Soul Harvest changed the essence to " + Essence.get(binder));
                if (((Unit) binder).getCooldown(harvest) > 0)
                    problems.add("a refused Soul Harvest set the cooldown");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ----------------------------------------------------------------------------------------------- Soul Chains

    /**
     * Soul Chains on an enemy with four more round it: it and its three nearest (2, 2 and 2.8 blocks off) are chained for
     * 10 s, the fifth (4 off) and one far away are not; 20 of 30 essence spent, the 60 s cooldown starts and a second
     * cast at once is refused. 8 magic damage to the aimed enemy also takes half of what it lost from each of the other
     * three, and nothing from the unchained or the Soulbinder (the shared damage is not shared again). Once the chain's
     * time is up, a hit on the first is its own again.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void soulbinder_soul_chains_share_half_the_damage_among_up_to_4(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "chains"), foe = owner(helper, "chainsfoe");
        Mob binder = binder(helper, owner, 2, 2, 30);
        Mob a = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 6);
        Mob b = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 8, 6);
        Mob c = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 8);
        Mob d = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 8, 8);
        Mob fifth = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 10, 6);
        Mob far = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 13, 13);
        List<Mob> all = new ArrayList<>(Arrays.asList(binder, a, b, c, d, fifth, far));
        if (all.contains(null)) {
            discardAll(spawned(all));
            helper.fail("could not spawn the Soulbinder and the six enemies");
            return;
        }
        List<Mob> others = List.of(b, c, d), unchained = List.of(fifth, far, binder);
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                SoulChains chains = ability(binder, SoulChains.class);
                if (chains == null) {
                    early.add("the Soulbinder has no Soul Chains");
                    return;
                }
                if (SoulChains.MAX_LINKED != 4 || SoulChains.DURATION_SECONDS != 10 || SoulChains.SHARE != 0.5f)
                    early.add("lovish set up to 4 linked for 10 s sharing 50%, Soul Chains has " + SoulChains.MAX_LINKED
                        + " for " + SoulChains.DURATION_SECONDS + " s sharing " + SoulChains.SHARE);
                long now = level.getGameTime();
                chains.use(level, (Unit) binder, a);
                Set<LivingEntity> linked = new HashSet<>(SoulbinderServerEvents.chainOf(a));
                Set<LivingEntity> want = new HashSet<>(List.of(a, b, c, d));
                if (!linked.equals(want))
                    early.add("the chain holds " + linked.size() + " units" + (linked.containsAll(want) ? "" : ", not the aimed enemy and its three nearest"));
                if (!SoulbinderServerEvents.chainOf(fifth).isEmpty() || !SoulbinderServerEvents.chainOf(far).isEmpty())
                    early.add("the fifth enemy or the far one is chained (at most 4)");
                long end = SoulbinderServerEvents.chainEnd(a);
                if (Math.abs(end - (now + 10 * 20)) > 1)
                    early.add("the chain breaks at " + end + ", expected 10 s after the cast (" + (now + 200) + ")");
                if (Essence.get(binder) != 10)
                    early.add("the Soulbinder holds " + Essence.get(binder) + " essence after Soul Chains, expected 10");
                float cd = ((Unit) binder).getCooldown(chains);
                if (cd < 60 * 20 - 2)
                    early.add("Soul Chains' cooldown is " + cd + " ticks after the cast, expected 1200");
                chains.use(level, (Unit) binder, a);
                if (Essence.get(binder) != 10)
                    early.add("a second Soul Chains during the cooldown spent essence (" + Essence.get(binder) + ")");
            } catch (RuntimeException ex) {
                early.add("casting threw " + ex);
            }
        });
        helper.runAfterDelay(4, () -> {
            try {
                float[] before = new float[all.size()];
                for (int i = 0; i < all.size(); i++)
                    before[i] = all.get(i).getHealth();
                a.invulnerableTime = 0;
                a.hurt(level.damageSources().magic(), 8f);
                float lostA = before[1] - a.getHealth();
                if (lostA <= 0.5f)
                    early.add("the aimed enemy lost " + lostA + " of 8 magic damage: the test lost its point");
                for (Mob m : others) {
                    float lost = before[all.indexOf(m)] - m.getHealth();
                    if (Math.abs(lost - lostA * 0.5f) > 0.05f)
                        early.add("a chained enemy lost " + lost + " when the aimed one lost " + lostA + ", expected half: " + lostA * 0.5f);
                }
                for (Mob m : unchained) {
                    float lost = before[all.indexOf(m)] - m.getHealth();
                    if (lost > 0.01f)
                        early.add((m == binder ? "the Soulbinder" : "an unchained enemy") + " lost " + lost + " health");
                }
            } catch (RuntimeException ex) {
                early.add("the shared hit threw " + ex);
            }
        });
        helper.runAfterDelay(6, () -> SoulbinderServerEvents.setChainEnd(a, level.getGameTime()));
        helper.runAfterDelay(8, () -> {
            try {
                float[] before = new float[others.size()];
                for (int i = 0; i < others.size(); i++)
                    before[i] = others.get(i).getHealth();
                a.invulnerableTime = 0;
                a.hurt(level.damageSources().magic(), 4f);
                for (int i = 0; i < others.size(); i++)
                    if (before[i] - others.get(i).getHealth() > 0.01f)
                        early.add("after the chain's time was up a hit on the aimed enemy still took "
                            + (before[i] - others.get(i).getHealth()) + " from another");
                if (!SoulbinderServerEvents.chainOf(a).isEmpty())
                    early.add("the chain still lists " + SoulbinderServerEvents.chainOf(a).size() + " units after its time was up");
            } catch (RuntimeException ex) {
                early.add("the hit after the chain threw " + ex);
            }
        });
        helper.runAfterDelay(10, () -> {
            List<String> problems = new ArrayList<>(early);
            discardAll(all);
            finish(helper, problems);
        });
    }

    /**
     * Soul Chains refuses without spending or starting its cooldown: short of essence (19 of 20) on a pair of enemies;
     * with 30 essence on a lone enemy (nobody to link it to), on a friend, and on a pair of enemies 11 blocks off (past
     * its 8-block throw). Nobody is chained.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soulbinder_soul_chains_refuse_without_spending(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "chainsno"), foe = owner(helper, "chainsnofoe");
        Mob poor = binder(helper, owner, 2, 2, 19);
        Mob pair1 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 5, 4);
        Mob pair2 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 7, 4);
        Mob rich = binder(helper, owner, 2, 12, 30);
        Mob lone = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 12);
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 4, 10);
        Mob farA = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 13, 13);
        Mob farB = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 13, 11);
        List<Mob> all = new ArrayList<>(Arrays.asList(poor, pair1, pair2, rich, lone, friend, farA, farB));
        if (all.contains(null)) {
            discardAll(spawned(all));
            helper.fail("could not spawn the two Soulbinders and their targets");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                SoulChains chains = ability(poor, SoulChains.class);
                if (chains == null)
                    throw new IllegalStateException("the Soulbinder has no Soul Chains");
                chains.use(level, (Unit) poor, pair1);
                if (Essence.get(poor) != 19 || ((Unit) poor).getCooldown(chains) > 0)
                    problems.add("Soul Chains short of essence was not refused cleanly (essence " + Essence.get(poor)
                        + ", cooldown " + ((Unit) poor).getCooldown(chains) + ")");
                String[] what = { "a lone enemy", "a friend", "enemies 11 blocks off" };
                Mob[] at = { lone, friend, farA };
                for (int i = 0; i < at.length; i++) {
                    chains.use(level, (Unit) rich, at[i]);
                    if (Essence.get(rich) != 30 || ((Unit) rich).getCooldown(chains) > 0)
                        problems.add("Soul Chains on " + what[i] + " was not refused cleanly (essence " + Essence.get(rich)
                            + ", cooldown " + ((Unit) rich).getCooldown(chains) + ")");
                }
                for (Mob m : all)
                    if (!SoulbinderServerEvents.chainOf(m).isEmpty())
                        problems.add("a refused Soul Chains chained a unit");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }
}
