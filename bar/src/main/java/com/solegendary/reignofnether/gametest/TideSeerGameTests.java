package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoCaster;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.BrineBolt;
import com.solegendary.reignofnether.ability.abilities.DrownedTithe;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.RisingTide;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.tide.TideSeerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.tide.TideSeerProd;
import com.solegendary.reignofnether.unit.units.tide.TideSeerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The Tidewrought essence caster, the Tide Seer (design/essence_casters.md; lovish approved draft 2 on Oct 29: "one
 * caster per race"). Written before the code, from the Revision 1 numbers (damage scaled to T1 health, 30-80):
 * <ul>
 *   <li>trained at the Drydock (Tidewrought T2 lab) once Tier 2 is researched, for a cost; an essence carrier (gathers
 *       the wisps within 8 blocks) with three spells and the shared Empower;</li>
 *   <li>Brine Bolt: 5 essence, 12 s - 25 damage to an enemy unit and a 3-block knockback;</li>
 *   <li>Drowned Tithe: 10 essence, 25 s - a whirlpool, radius 5, for 8 s; enemies in it -40% speed and 3 damage a
 *       second;</li>
 *   <li>Rising Tide: 20 essence, 60 s - enemies within 8 pushed 6 blocks back, fires on allies put out;</li>
 *   <li>every spell refuses - no essence spent, no cooldown - when it cannot act (short of essence, no target), an
 *       order during the cooldown is refused too, and the bots' AutoCaster casts them only while the caster holds the
 *       essence ({@code autocastEssence}).</li>
 * </ul>
 * Every unit stands still (no AI, its autocast switched off unless the test drives the AutoCaster itself) inside the
 * test's own arena under owners unique to the arena. Early callbacks only record, never throw; the last callback judges
 * and always cleans up (units, whirlpools, wisps) whatever the verdict, as EssenceGameTests do.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TideSeerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "caster_tidewrought";

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_tideseer_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A unit that stands where it is put: no AI, and no autocast of its own (the live AutoCaster runs every second). */
    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null) {
            m.setNoAi(true);
            if (m instanceof Unit u && u.getAbilities() != null)
                AutocastState.setAll(u, false);
        }
        return m;
    }

    /** A Tide Seer holding {@code essence}. */
    private static Mob seer(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = still(helper, EntityRegistrar.TIDE_SEER_UNIT.get(), owner, x, z);
        if (m != null && essence > 0)
            Essence.add(m, essence);
        return m;
    }

    private static Mob vindicator(GameTestHelper helper, String owner, int x, int z) {
        return still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, x, z);
    }

    /** The ground block under arena cell (x, z): units stand at y 2, so the floor is y 1. */
    private static BlockPos ground(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 1, z));
    }

    /** The unit's own copy of an ability of class {@code type}, or null. */
    @SuppressWarnings("unchecked")
    private static <A extends Ability> A ability(Mob caster, Class<A> type) {
        if (!(caster instanceof Unit u) || u.getAbilities() == null)
            return null;
        for (Ability a : u.getAbilities().get())
            if (type.isInstance(a))
                return (A) a;
        return null;
    }

    private static float cooldown(Mob caster, Class<? extends Ability> type) {
        Ability a = ability(caster, type);
        return a == null ? -1 : ((Unit) caster).getCooldown(a);
    }

    private static void discardAll(List<? extends Entity> entities) {
        for (Entity e : entities)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    private static List<Entity> live(Entity... entities) {
        return new ArrayList<>(Arrays.stream(entities).filter(Objects::nonNull).toList());
    }

    /** Units, whirlpools and wisps of the test, gone whatever the verdict. */
    private static void cleanUp(GameTestHelper helper, List<Entity> all) {
        discardAll(all);
        Vec3 middle = Vec3.atCenterOf(helper.absolutePos(new BlockPos(8, 2, 8)));
        TideSeerServerEvents.clearNear(helper.getLevel(), middle, 16);
        for (Entity w : EssenceServerEvents.getWisps())
            if (!w.isRemoved() && w.position().distanceTo(middle) <= 16)
                w.discard();
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    private static Vec3 flat(Entity e) {
        return new Vec3(e.getX(), 0, e.getZ());
    }

    private static long poolsOf(String owner) {
        return TideSeerServerEvents.getWhirlpools().stream().filter(w -> owner.equals(w.owner)).count();
    }

    // ------------------------------------------------------------------------------------------- the unit

    /**
     * The Drydock trains the Tide Seer (its own slot), for a real cost (metal, energy, population), behind the Tier 2
     * gate like the rest of the lab; the item makes a Tide Seer, a Tidewrought unit whose cost is the item's.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tide_seer_trains_at_the_drydock_for_a_cost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        if (!Buildings.DRYDOCK.productions.get().contains(ProductionItems.TIDE_SEER))
            problems.add("the Drydock does not offer the Tide Seer");
        ResourceCost cost = ResourceCosts.TIDE_SEER;
        if (cost.metal() <= 0 || cost.energy() <= 0 || cost.population <= 0 || cost.ticks <= 0)
            problems.add("the Tide Seer costs " + cost.metal() + " metal, " + cost.energy() + " energy, " + cost.population
                + " population, " + cost.ticks + " ticks: every part should be above 0");
        if (EntityRegistrar.getEntityType(TideSeerProd.itemName) != EntityRegistrar.TIDE_SEER_UNIT.get())
            problems.add("the Tide Seer item does not make a Tide Seer");
        if (ProductionItems.getProductionItem(EntityRegistrar.TIDE_SEER_UNIT.get()) != ProductionItems.TIDE_SEER)
            problems.add("the Tide Seer type does not resolve to its production item");

        String me = owner(helper, "train");
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.DRYDOCK, level,
            new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, me, false);
        Resources pool = new Resources(me, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        Mob made = null;
        try {
            if (!(bp instanceof ProductionPlacement pp)) {
                problems.add("no Drydock placement: " + bp);
            } else {
                pp.isBuilt = true;
                if (ProductionItems.TIDE_SEER.getProduceErrorMsg(pp) == null)
                    problems.add("the Tide Seer was offered without Tier 2");
                ResearchServerEvents.addResearch(me, ProductionItems.RESEARCH_TIER_2);
                String err = ProductionItems.TIDE_SEER.getProduceErrorMsg(pp);
                if (err != null)
                    problems.add("with Tier 2 the Tide Seer is refused: " + err);
                else if (!pp.startProductionItem(ProductionItems.TIDE_SEER))
                    problems.add("the server refused to queue the Tide Seer");
                pp.productionQueue.clear();
            }
            made = EntityRegistrar.TIDE_SEER_UNIT.get().create(level);
            if (!(made instanceof Unit u))
                problems.add("a Tide Seer is not a unit: " + made);
            else {
                if (u.getCost() != ResourceCosts.TIDE_SEER)
                    problems.add("a Tide Seer's cost is not ResourceCosts.TIDE_SEER");
                if (!Factions.TIDEWROUGHT.equals(Factions.getFaction(u)))
                    problems.add("the Tide Seer is not Tidewrought: " + Factions.getFaction(u));
                if (Math.abs(made.getMaxHealth() - TideSeerUnit.maxHealth) > 0.01f || made.getMaxHealth() <= 0)
                    problems.add("a Tide Seer has " + made.getMaxHealth() + " health, its class says " + TideSeerUnit.maxHealth);
            }
        } finally {
            if (made != null)
                made.discard();
            ResourcesServerEvents.resourcesList.remove(pool);
            ResearchServerEvents.removeAllResearchFor(me);
        }
        finish(helper, problems);
    }

    /**
     * The Tide Seer is an essence carrier: it pulls in a wisp 5 blocks away and holds its essence. It has its three
     * spells and the shared Empower, and each spell is one the bots cast (a rule other than MANUAL) gated on its own
     * essence cost.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = BATCH)
    public static void tide_seer_gathers_essence_and_has_its_four_spells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "gather");
        Mob caster = seer(helper, me, 3, 3, 0);
        if (caster == null) {
            helper.fail("could not spawn a Tide Seer");
            return;
        }
        Entity wisp = EssenceServerEvents.spawnWisp(level, caster.getX() + 5, caster.getY() + 1, caster.getZ(), 4);
        List<Entity> all = live(caster, wisp);
        helper.runAfterDelay(100, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (wisp == null)
                    problems.add("spawnWisp made no wisp");
                if (!Essence.isCarrier(caster))
                    problems.add("the Tide Seer is not an essence carrier");
                else if (Essence.get(caster) != 4)
                    problems.add("the Tide Seer holds " + Essence.get(caster) + " essence after 5 s beside a 4-essence wisp, expected 4");
                if (wisp != null && !wisp.isRemoved())
                    problems.add("the wisp 5 blocks from the Tide Seer was never gathered");
                BrineBolt bolt = ability(caster, BrineBolt.class);
                DrownedTithe tithe = ability(caster, DrownedTithe.class);
                RisingTide tide = ability(caster, RisingTide.class);
                List<String> missing = new ArrayList<>();
                if (bolt == null) missing.add("Brine Bolt");
                if (tithe == null) missing.add("Drowned Tithe");
                if (tide == null) missing.add("Rising Tide");
                if (ability(caster, Empower.class) == null) missing.add("Empower");
                if (!missing.isEmpty())
                    problems.add("the Tide Seer lacks " + String.join(", ", missing));
                if (bolt != null && (bolt.getAutoRule() != AutoRule.ENEMY_IN_RANGE || bolt.autocastEssence != BrineBolt.ESSENCE_COST))
                    problems.add("Brine Bolt's autocast is " + bolt.getAutoRule() + " gated on " + bolt.autocastEssence
                        + " essence, expected ENEMY_IN_RANGE on " + BrineBolt.ESSENCE_COST);
                if (tithe != null && (tithe.getAutoRule() != AutoRule.CROWD || tithe.autocastEssence != DrownedTithe.ESSENCE_COST))
                    problems.add("Drowned Tithe's autocast is " + tithe.getAutoRule() + " gated on " + tithe.autocastEssence
                        + " essence, expected CROWD on " + DrownedTithe.ESSENCE_COST);
                if (tide != null && (tide.getAutoRule() != AutoRule.CROWD || tide.autocastEssence != RisingTide.ESSENCE_COST))
                    problems.add("Rising Tide's autocast is " + tide.getAutoRule() + " gated on " + tide.autocastEssence
                        + " essence, expected CROWD on " + RisingTide.ESSENCE_COST);
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    /** The shared Empower works from the Tide Seer: 15 essence, a friend 3 blocks away gains a rank. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tide_seer_empowers_a_friend_for_15_essence(GameTestHelper helper) {
        String me = owner(helper, "emp");
        Mob caster = seer(helper, me, 4, 4, 15);
        Mob friend = vindicator(helper, me, 7, 4);
        List<Entity> all = live(caster, friend);
        if (caster == null || friend == null) {
            cleanUp(helper, all);
            helper.fail("could not spawn the Tide Seer and its friend");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Empower emp = ability(caster, Empower.class);
                if (Essence.get(caster) != 15)
                    problems.add("the Tide Seer holds " + Essence.get(caster) + " essence, the test gave it 15");
                if (emp == null)
                    problems.add("the Tide Seer has no Empower");
                else {
                    emp.use(helper.getLevel(), (Unit) caster, friend);
                    if (VeterancyServerEvents.getRank(friend) != 1)
                        problems.add("the friend is rank " + VeterancyServerEvents.getRank(friend) + " after Empower, expected 1");
                    if (Essence.get(caster) != 0)
                        problems.add("the Tide Seer holds " + Essence.get(caster) + " essence after Empower, expected 0");
                    if (((Unit) caster).getCooldown(emp) <= 0)
                        problems.add("Empower set no cooldown");
                }
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    // ----------------------------------------------------------------------------------------- Brine Bolt

    /**
     * Brine Bolt: 5 essence, 12 s cooldown. The enemy 4 blocks away takes 25 damage and slides 3 blocks straight away
     * from the caster. An order during the cooldown spends nothing and hits nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void tide_seer_brine_bolt_hits_for_25_and_knocks_back_3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "bolt"), foe = owner(helper, "boltfoe");
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        Mob caster = seer(helper, me, 2, 8, 30);
        Mob enemy = vindicator(helper, foe, 6, 8);
        List<Entity> all = live(caster, enemy);
        if (all.size() != 2) {
            cleanUp(helper, all);
            helper.fail("could not spawn the Tide Seer and the enemy");
            return;
        }
        List<String> early = new ArrayList<>();
        Vec3[] start = new Vec3[1];
        float[] hpAfter = { -1 };
        helper.runAfterDelay(3, () -> {
            try {
                if (BrineBolt.ESSENCE_COST != 5 || BrineBolt.CD_SECONDS != 12 || BrineBolt.DAMAGE != 25f || BrineBolt.KNOCKBACK_BLOCKS != 3)
                    early.add("Revision 1 set 5 essence, 12 s, 25 damage, 3 blocks; Brine Bolt has " + BrineBolt.ESSENCE_COST + ", "
                        + BrineBolt.CD_SECONDS + " s, " + BrineBolt.DAMAGE + ", " + BrineBolt.KNOCKBACK_BLOCKS);
                if (enemy.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) > 0)
                    early.add("the test's vindicator resists knockback (" + enemy.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) + ")");
                BrineBolt bolt = ability(caster, BrineBolt.class);
                if (Essence.get(caster) != 30)
                    early.add("the Tide Seer holds " + Essence.get(caster) + " essence, the test gave it 30");
                if (bolt == null) {
                    early.add("the Tide Seer has no Brine Bolt");
                    return;
                }
                start[0] = flat(enemy);
                float hp = enemy.getHealth();
                bolt.use(level, (Unit) caster, enemy);
                hpAfter[0] = enemy.getHealth();
                if (Math.abs((hp - hpAfter[0]) - 25f) > 0.01f)
                    early.add("Brine Bolt took " + (hp - hpAfter[0]) + " health off the enemy, expected 25");
                if (Essence.get(caster) != 25)
                    early.add("the Tide Seer holds " + Essence.get(caster) + " essence after Brine Bolt, expected 30 - 5 = 25");
                float cd = ((Unit) caster).getCooldown(bolt);
                if (Math.abs(cd - BrineBolt.CD_SECONDS * 20) > 1)
                    early.add("Brine Bolt set a " + cd + "-tick cooldown, expected " + BrineBolt.CD_SECONDS * 20);
                // the cooldown holds: the same order again, through the click's own path, is refused
                new UnitActionItem(me, UnitAction.BRINE_BOLT, enemy.getId(), new int[] { caster.getId() }, enemy.blockPosition(), BlockPos.ZERO).action(level);
                if (Essence.get(caster) != 25)
                    early.add("an order during the cooldown spent essence (" + Essence.get(caster) + " left, expected 25)");
                if (enemy.getHealth() < hpAfter[0] - 0.01f)
                    early.add("an order during the cooldown hit the enemy again");
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        helper.runAfterDelay(30, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (start[0] != null) {
                    Vec3 moved = flat(enemy).subtract(start[0]);
                    if (Math.abs(moved.x - 3) > 0.35 || Math.abs(moved.z) > 0.2)
                        problems.add("the enemy slid (" + String.format("%.2f", moved.x) + ", " + String.format("%.2f", moved.z)
                            + ") blocks, expected 3 straight away from the caster (+x)");
                }
                if (((Unit) caster).getCooldown(ability(caster, BrineBolt.class)) <= 0)
                    problems.add("the 12 s cooldown ended within 1.5 s");
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Brine Bolt refuses without spending or a cooldown: a Tide Seer with 4 essence (one short) aimed at an enemy, and a
     * full one aimed at a friend and at an enemy 12 blocks away (out of its 10-block reach).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tide_seer_brine_bolt_refuses_when_short_on_a_friend_or_out_of_reach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "boltno"), foe = owner(helper, "boltnofoe");
        Mob poor = seer(helper, me, 2, 2, 4);
        Mob rich = seer(helper, me, 2, 13, 30);
        Mob enemy = vindicator(helper, foe, 6, 2);
        Mob friend = vindicator(helper, me, 5, 13);
        Mob farEnemy = vindicator(helper, foe, 14, 13);
        List<Entity> all = live(poor, rich, enemy, friend, farEnemy);
        if (all.size() != 5) {
            cleanUp(helper, all);
            helper.fail("could not spawn the two Tide Seers and the three vindicators");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                BrineBolt a = ability(poor, BrineBolt.class), b = ability(rich, BrineBolt.class);
                if (Essence.get(poor) != 4 || Essence.get(rich) != 30)
                    problems.add("the Tide Seers hold " + Essence.get(poor) + " and " + Essence.get(rich) + " essence, the test gave them 4 and 30");
                if (a == null || b == null)
                    problems.add("a Tide Seer has no Brine Bolt");
                else {
                    float hp = enemy.getHealth();
                    a.use(level, (Unit) poor, enemy);
                    if (Essence.get(poor) != 4)
                        problems.add("a Tide Seer 1 essence short spent essence: " + Essence.get(poor) + " left of 4");
                    if (((Unit) poor).getCooldown(a) > 0)
                        problems.add("a Tide Seer 1 essence short got a cooldown");
                    if (enemy.getHealth() < hp - 0.01f)
                        problems.add("a Tide Seer 1 essence short hurt the enemy");
                    float friendHp = friend.getHealth();
                    b.use(level, (Unit) rich, friend);
                    if (Essence.get(rich) != 30 || ((Unit) rich).getCooldown(b) > 0)
                        problems.add("Brine Bolt on a friend was cast (" + Essence.get(rich) + " essence left of 30, cooldown "
                            + ((Unit) rich).getCooldown(b) + ")");
                    if (friend.getHealth() < friendHp - 0.01f)
                        problems.add("Brine Bolt hurt a friend");
                    float farHp = farEnemy.getHealth();
                    b.use(level, (Unit) rich, farEnemy);
                    if (Essence.get(rich) != 30 || ((Unit) rich).getCooldown(b) > 0)
                        problems.add("Brine Bolt on an enemy 12 blocks away was cast (" + Essence.get(rich) + " essence left of 30, cooldown "
                            + ((Unit) rich).getCooldown(b) + ")");
                    if (farEnemy.getHealth() < farHp - 0.01f)
                        problems.add("Brine Bolt hurt an enemy beyond its reach");
                }
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    // -------------------------------------------------------------------------------------- Drowned Tithe

    /**
     * Drowned Tithe: 10 essence, 25 s cooldown. A whirlpool opens on the spot; the enemy 1 block from it is 40% slower
     * at once and takes 3 damage a second - 12 by 4.5 s, 24 in all over 8 s - then is back to full speed when the pool
     * is gone. The enemy 6 blocks from it (outside radius 5) and a friend inside are untouched. An order during the
     * cooldown spends nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 220, batch = BATCH)
    public static void tide_seer_drowned_tithe_slows_40_and_deals_3_a_second_for_8_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "tithe"), foe = owner(helper, "tithefoe");
        Mob caster = seer(helper, me, 2, 2, 30);
        Mob near = vindicator(helper, foe, 9, 8);       // 1 block from the spot
        Mob far = vindicator(helper, foe, 8, 14);       // 6 blocks from it
        Mob friend = vindicator(helper, me, 7, 8);      // 1 block, the other side
        List<Entity> all = live(caster, near, far, friend);
        if (all.size() != 4) {
            cleanUp(helper, all);
            helper.fail("could not spawn the Tide Seer and the three vindicators");
            return;
        }
        BlockPos spot = ground(helper, 8, 8);
        double[] speed = new double[3];    // near, far, friend before the cast
        float[] hp = new float[3];
        float[] half = { -1 };
        List<String> early = new ArrayList<>();
        final int cast = 3;
        helper.runAfterDelay(cast, () -> {
            try {
                if (DrownedTithe.ESSENCE_COST != 10 || DrownedTithe.CD_SECONDS != 25 || DrownedTithe.RADIUS != 5
                        || DrownedTithe.DURATION_TICKS != 160 || DrownedTithe.SLOW != 0.4 || DrownedTithe.DAMAGE_PER_SECOND != 3f)
                    early.add("Revision 1 set 10 essence, 25 s, radius 5, 8 s, -40%, 3/s; Drowned Tithe has " + DrownedTithe.ESSENCE_COST
                        + ", " + DrownedTithe.CD_SECONDS + " s, " + DrownedTithe.RADIUS + ", " + DrownedTithe.DURATION_TICKS + " ticks, -"
                        + DrownedTithe.SLOW + ", " + DrownedTithe.DAMAGE_PER_SECOND);
                Mob[] three = { near, far, friend };
                for (int i = 0; i < 3; i++) {
                    speed[i] = three[i].getAttributeValue(Attributes.MOVEMENT_SPEED);
                    hp[i] = three[i].getHealth();
                }
                DrownedTithe tithe = ability(caster, DrownedTithe.class);
                if (Essence.get(caster) != 30)
                    early.add("the Tide Seer holds " + Essence.get(caster) + " essence, the test gave it 30");
                if (tithe == null) {
                    early.add("the Tide Seer has no Drowned Tithe");
                    return;
                }
                tithe.use(level, (Unit) caster, spot);
                if (poolsOf(me) != 1)
                    early.add("the cast opened " + poolsOf(me) + " whirlpools, expected 1");
                double slowed = near.getAttributeValue(Attributes.MOVEMENT_SPEED);
                if (Math.abs(slowed - speed[0] * 0.6) > 1e-4)
                    early.add("the enemy in the pool moves at " + slowed + ", expected 60% of " + speed[0]);
                if (Math.abs(far.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed[1]) > 1e-6)
                    early.add("the enemy 6 blocks from the spot was slowed: outside the radius-5 pool");
                if (Math.abs(friend.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed[2]) > 1e-6)
                    early.add("a friend inside the pool was slowed");
                if (Essence.get(caster) != 20)
                    early.add("the Tide Seer holds " + Essence.get(caster) + " essence after Drowned Tithe, expected 30 - 10 = 20");
                float cd = ((Unit) caster).getCooldown(tithe);
                if (Math.abs(cd - DrownedTithe.CD_SECONDS * 20) > 1)
                    early.add("Drowned Tithe set a " + cd + "-tick cooldown, expected " + DrownedTithe.CD_SECONDS * 20);
                new UnitActionItem(me, UnitAction.DROWNED_TITHE, -1, new int[] { caster.getId() }, spot, BlockPos.ZERO).action(level);
                if (Essence.get(caster) != 20 || poolsOf(me) != 1)
                    early.add("an order during the cooldown was cast (" + Essence.get(caster) + " essence left of 20, "
                        + poolsOf(me) + " pools)");
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        // the pulses fall on the pool's seconds (cast + 20, + 40 ... + 160): sample between them
        helper.runAfterDelay(cast + 90, () -> half[0] = near.getHealth());
        helper.runAfterDelay(cast + 175, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (problems.isEmpty()) {
                    float midLoss = hp[0] - half[0], loss = hp[0] - near.getHealth();
                    if (Math.abs(midLoss - 12f) > 0.1f)
                        problems.add("the enemy in the pool lost " + midLoss + " health in its first 4.5 s, expected 4 x 3 = 12");
                    if (Math.abs(loss - 24f) > 0.1f)
                        problems.add("the enemy in the pool lost " + loss + " health over the pool's 8 s, expected 8 x 3 = 24");
                    if (far.getHealth() < hp[1] - 0.01f)
                        problems.add("the enemy outside the pool was hurt");
                    if (friend.getHealth() < hp[2] - 0.01f)
                        problems.add("a friend inside the pool was hurt");
                    if (Math.abs(near.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed[0]) > 1e-6)
                        problems.add("the enemy is still at " + near.getAttributeValue(Attributes.MOVEMENT_SPEED)
                            + " speed after the pool, expected its own " + speed[0]);
                    if (poolsOf(me) != 0)
                        problems.add("the whirlpool was still open 8.75 s after the cast, expected 8 s");
                }
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Drowned Tithe refuses without spending, a cooldown or a pool: a Tide Seer with 9 essence (one short) aimed at an
     * enemy, and a full one aimed at a spot with no enemy within 5 blocks.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tide_seer_drowned_tithe_refuses_when_short_or_with_no_enemy_in_the_pool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "titheno"), foe = owner(helper, "tithenofoe");
        Mob poor = seer(helper, me, 2, 2, 9);
        Mob rich = seer(helper, me, 13, 2, 30);
        Mob enemy = vindicator(helper, foe, 6, 6);
        List<Entity> all = live(poor, rich, enemy);
        if (all.size() != 3) {
            cleanUp(helper, all);
            helper.fail("could not spawn the two Tide Seers and the enemy");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                DrownedTithe a = ability(poor, DrownedTithe.class), b = ability(rich, DrownedTithe.class);
                if (Essence.get(poor) != 9 || Essence.get(rich) != 30)
                    problems.add("the Tide Seers hold " + Essence.get(poor) + " and " + Essence.get(rich) + " essence, the test gave them 9 and 30");
                if (a == null || b == null)
                    problems.add("a Tide Seer has no Drowned Tithe");
                else {
                    double speed = enemy.getAttributeValue(Attributes.MOVEMENT_SPEED);
                    a.use(level, (Unit) poor, ground(helper, 6, 6));
                    if (Essence.get(poor) != 9)
                        problems.add("a Tide Seer 1 essence short spent essence: " + Essence.get(poor) + " left of 9");
                    if (((Unit) poor).getCooldown(a) > 0)
                        problems.add("a Tide Seer 1 essence short got a cooldown");
                    if (Math.abs(enemy.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) > 1e-6)
                        problems.add("a Tide Seer 1 essence short slowed the enemy");
                    b.use(level, (Unit) rich, ground(helper, 13, 12));   // 9+ blocks from the enemy
                    if (Essence.get(rich) != 30)
                        problems.add("a whirlpool with no enemy in it spent essence: " + Essence.get(rich) + " left of 30");
                    if (((Unit) rich).getCooldown(b) > 0)
                        problems.add("a whirlpool with no enemy in it set a cooldown");
                    if (poolsOf(me) != 0)
                        problems.add("a refused Drowned Tithe opened " + poolsOf(me) + " whirlpools");
                }
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    // ---------------------------------------------------------------------------------------- Rising Tide

    /**
     * Rising Tide: 20 essence, 60 s cooldown. The enemy 3 blocks from the caster slides 6 blocks straight away from it;
     * the enemy 10.8 blocks off (outside 8) and the friends do not move; the burning friend 3 blocks off is put out.
     * An order during the cooldown spends nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void tide_seer_rising_tide_pushes_enemies_6_back_and_soaks_burning_allies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "tide"), foe = owner(helper, "tidefoe");
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        Mob caster = seer(helper, me, 2, 8, 30);
        Mob near = vindicator(helper, foe, 5, 8);        // 3 blocks east of the caster
        Mob far = vindicator(helper, foe, 11, 14);       // 10.8 blocks off
        Mob burning = vindicator(helper, me, 2, 5);      // 3 blocks north, on fire
        Mob calm = vindicator(helper, me, 2, 11);        // 3 blocks south
        List<Entity> all = live(caster, near, far, burning, calm);
        if (all.size() != 5) {
            cleanUp(helper, all);
            helper.fail("could not spawn the Tide Seer and the four vindicators");
            return;
        }
        Vec3[] start = new Vec3[4];
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(3, () -> {
            try {
                if (RisingTide.ESSENCE_COST != 20 || RisingTide.CD_SECONDS != 60 || RisingTide.RADIUS != 8 || RisingTide.PUSH_BLOCKS != 6)
                    early.add("lovish set 20 essence, 60 s, 8 blocks, 6 blocks back; Rising Tide has " + RisingTide.ESSENCE_COST + ", "
                        + RisingTide.CD_SECONDS + " s, " + RisingTide.RADIUS + ", " + RisingTide.PUSH_BLOCKS);
                if (near.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) > 0)
                    early.add("the test's vindicator resists knockback (" + near.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) + ")");
                burning.setSecondsOnFire(10);
                if (!burning.isOnFire())
                    early.add("the test could not set its friend on fire");
                Mob[] four = { near, far, burning, calm };
                for (int i = 0; i < 4; i++)
                    start[i] = flat(four[i]);
                RisingTide tide = ability(caster, RisingTide.class);
                if (Essence.get(caster) != 30)
                    early.add("the Tide Seer holds " + Essence.get(caster) + " essence, the test gave it 30");
                if (tide == null) {
                    early.add("the Tide Seer has no Rising Tide");
                    return;
                }
                tide.use(level, (Unit) caster, caster.blockPosition());
                if (burning.isOnFire())
                    early.add("the burning friend 3 blocks away is still on fire after Rising Tide");
                if (Essence.get(caster) != 10)
                    early.add("the Tide Seer holds " + Essence.get(caster) + " essence after Rising Tide, expected 30 - 20 = 10");
                float cd = ((Unit) caster).getCooldown(tide);
                if (Math.abs(cd - RisingTide.CD_SECONDS * 20) > 1)
                    early.add("Rising Tide set a " + cd + "-tick cooldown, expected " + RisingTide.CD_SECONDS * 20);
                new UnitActionItem(me, UnitAction.RISING_TIDE, -1, new int[] { caster.getId() }, caster.blockPosition(), BlockPos.ZERO).action(level);
                if (Essence.get(caster) != 10)
                    early.add("an order during the cooldown spent essence (" + Essence.get(caster) + " left, expected 10)");
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        helper.runAfterDelay(35, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (start[0] != null) {
                    Vec3 moved = flat(near).subtract(start[0]);
                    if (Math.abs(moved.x - 6) > 0.35 || Math.abs(moved.z) > 0.2)
                        problems.add("the enemy 3 blocks off slid (" + String.format("%.2f", moved.x) + ", " + String.format("%.2f", moved.z)
                            + ") blocks, expected 6 straight away from the caster (+x)");
                    String[] names = { "", "the enemy outside the wave", "the burning friend", "the other friend" };
                    Mob[] others = { null, far, burning, calm };
                    for (int i = 1; i < 4; i++)
                        if (flat(others[i]).distanceTo(start[i]) > 0.05)
                            problems.add(names[i] + " moved " + String.format("%.2f", flat(others[i]).distanceTo(start[i])) + " blocks");
                }
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Rising Tide refuses without spending or a cooldown: a Tide Seer with 19 essence (one short) beside an enemy, and a
     * full one with no enemy within 8 blocks and no ally on fire.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tide_seer_rising_tide_refuses_when_short_or_with_nothing_to_push(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "tideno"), foe = owner(helper, "tidenofoe");
        Mob poor = seer(helper, me, 2, 2, 19);
        Mob rich = seer(helper, me, 2, 13, 30);
        Mob enemy = vindicator(helper, foe, 5, 2);     // 3 from the poor one, 11.4 from the rich one
        List<Entity> all = live(poor, rich, enemy);
        if (all.size() != 3) {
            cleanUp(helper, all);
            helper.fail("could not spawn the two Tide Seers and the enemy");
            return;
        }
        Vec3[] start = new Vec3[1];
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(3, () -> {
            try {
                RisingTide a = ability(poor, RisingTide.class), b = ability(rich, RisingTide.class);
                if (Essence.get(poor) != 19 || Essence.get(rich) != 30)
                    early.add("the Tide Seers hold " + Essence.get(poor) + " and " + Essence.get(rich) + " essence, the test gave them 19 and 30");
                if (a == null || b == null) {
                    early.add("a Tide Seer has no Rising Tide");
                    return;
                }
                start[0] = flat(enemy);
                a.use(level, (Unit) poor, poor.blockPosition());
                if (Essence.get(poor) != 19)
                    early.add("a Tide Seer 1 essence short spent essence: " + Essence.get(poor) + " left of 19");
                if (((Unit) poor).getCooldown(a) > 0)
                    early.add("a Tide Seer 1 essence short got a cooldown");
                b.use(level, (Unit) rich, rich.blockPosition());
                if (Essence.get(rich) != 30)
                    early.add("a wave with nothing to push spent essence: " + Essence.get(rich) + " left of 30");
                if (((Unit) rich).getCooldown(b) > 0)
                    early.add("a wave with nothing to push set a cooldown");
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        helper.runAfterDelay(20, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (start[0] != null && flat(enemy).distanceTo(start[0]) > 0.05)
                    problems.add("a refused Rising Tide moved the enemy " + String.format("%.2f", flat(enemy).distanceTo(start[0])) + " blocks");
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------ the bots

    /**
     * A bot's Tide Seer is not an AttackerUnit, so the army list never holds it; the bot's "healers follow the army"
     * block (BotPlayer.verdantHabits, run for every faction) orders a lagging essence caster to the army's centre, as
     * it does the Tide Priest (shared edit, briefs/essence-caster-bot-follow-line.md). The Seer 14 blocks from the
     * army is sent there; the one 3 blocks from it is left alone. verdantHabits is package-private and its signature is
     * the same in every caster PR, so the test reaches it by reflection.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tide_seer_bots_send_a_lagging_seer_to_the_army(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "follow");
        Mob lagging = seer(helper, me, 2, 2, 0);
        Mob close = seer(helper, me, 10, 10, 0);
        Mob f1 = vindicator(helper, me, 12, 12), f2 = vindicator(helper, me, 13, 12);
        List<Entity> all = live(lagging, close, f1, f2);
        if (all.size() != 4) {
            cleanUp(helper, all);
            helper.fail("could not spawn the two Tide Seers and the army");
            return;
        }
        List<String> early = new ArrayList<>();
        BlockPos[] moveTo = new BlockPos[1];
        helper.runAfterDelay(3, () -> {
            try {
                var brain = new com.solegendary.reignofnether.bot.BotPlayer(me, Factions.TIDEWROUGHT,
                    com.solegendary.reignofnether.bot.BotPlayer.Difficulty.MEDIUM, helper.absolutePos(new BlockPos(1, 2, 1)));
                brain.orderListener = (action, target) -> {
                    if (action == UnitAction.MOVE)
                        moveTo[0] = target;
                };
                List<net.minecraft.world.entity.LivingEntity> mine = new ArrayList<>(List.of(lagging, close, f1, f2));
                List<net.minecraft.world.entity.LivingEntity> army = new ArrayList<>(List.of(f1, f2));
                var habits = com.solegendary.reignofnether.bot.BotPlayer.class.getDeclaredMethod("verdantHabits",
                    ServerLevel.class, List.class, List.class, List.class, List.class, long.class, long.class);
                habits.setAccessible(true);
                habits.invoke(brain, level, mine, army, new ArrayList<>(), new ArrayList<>(), level.getGameTime(), 0L);
                if (moveTo[0] == null)
                    early.add("the bot gave no move order: its lagging Tide Seer stays at the lab");
            } catch (ReflectiveOperationException | RuntimeException ex) {
                early.add("calling the bot's follow-the-army block threw " + ex);
            }
        });
        helper.runAfterDelay(8, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (moveTo[0] != null) {
                    BlockPos t = ((Unit) lagging).getMoveGoal() == null ? null : ((Unit) lagging).getMoveGoal().getMoveTarget();
                    if (t == null || Math.abs(t.getX() - moveTo[0].getX()) > 1 || Math.abs(t.getZ() - moveTo[0].getZ()) > 1)
                        problems.add("the lagging Tide Seer is headed for " + t + ", not the army's centre " + moveTo[0]);
                    BlockPos c = ((Unit) close).getMoveGoal() == null ? null : ((Unit) close).getMoveGoal().getMoveTarget();
                    if (c != null)
                        problems.add("the Tide Seer 3 blocks from the army was ordered to " + c + ": only a lagging one should be");
                }
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    /**
     * The Tidewrought bots train the Tide Seer (it is in their Drydock army) and spend its Empower (MANUAL for players)
     * through BotPlayer.tideSeers: a Seer holding Empower's 15 plus a Drowned Tithe's 10 ranks up the priciest friend in
     * reach (an Iron Golem over a vindicator) and keeps 10; one holding 24 keeps it all for its spells.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tide_seer_bots_train_it_and_empower_with_spare_essence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "bot");
        Mob poor = seer(helper, me, 2, 2, 24);
        Mob poorFriend = vindicator(helper, me, 4, 2);
        Mob rich = seer(helper, me, 2, 12, 25);
        Mob cheap = vindicator(helper, me, 4, 12);
        Mob dear = still(helper, EntityRegistrar.IRON_GOLEM_UNIT.get(), me, 5, 14);
        List<Entity> all = live(poor, poorFriend, rich, cheap, dear);
        if (all.size() != 5) {
            cleanUp(helper, all);
            helper.fail("could not spawn the two Tide Seers and their three friends");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.TIDEWROUGHT);
                if (kit == null || !kit.t2Army().contains(ProductionItems.TIDE_SEER))
                    problems.add("the Tidewrought bot's T2 army has no Tide Seer");
                var brain = new com.solegendary.reignofnether.bot.BotPlayer(me, Factions.TIDEWROUGHT,
                    com.solegendary.reignofnether.bot.BotPlayer.Difficulty.MEDIUM, helper.absolutePos(new BlockPos(8, 2, 8)));
                brain.tideSeers(level, new ArrayList<>(List.of(poor, poorFriend, rich, cheap, dear)));
                if (Essence.get(poor) != 24 || VeterancyServerEvents.getRank(poorFriend) != 0)
                    problems.add("a bot Seer with 24 essence empowered (" + Essence.get(poor) + " left, friend rank "
                        + VeterancyServerEvents.getRank(poorFriend) + "): it should keep 10 after Empower's 15");
                if (VeterancyServerEvents.getRank(dear) != 1)
                    problems.add("the Iron Golem beside a bot Seer with 25 essence is rank " + VeterancyServerEvents.getRank(dear)
                        + ", expected 1 (the priciest friend in reach)");
                if (VeterancyServerEvents.getRank(cheap) != 0)
                    problems.add("the vindicator was empowered too (rank " + VeterancyServerEvents.getRank(cheap) + ")");
                if (Essence.get(rich) != 10)
                    problems.add("the bot Seer holds " + Essence.get(rich) + " essence after Empower, expected 25 - 15 = 10");
                if (cooldown(rich, Empower.class) <= 0)
                    problems.add("the bot's Empower set no cooldown");
            } catch (RuntimeException ex) {
                problems.add("the bot habit threw " + ex);
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }

    /**
     * The bots' AutoCaster uses the Tide Seer (design/essence_casters.md Revision 1: bots use essence spells and never
     * order one that would be refused). A Tide Seer with 30 essence 5 blocks from a knot of 3 enemies casts Brine Bolt
     * on its first look, Drowned Tithe on the crowd on its second (the bolt now cooling down), and nothing on its third:
     * Rising Tide's 20 is more than the 15 left. A Tide Seer with 4 essence beside the same enemies casts nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tide_seer_autocasts_its_spells_only_with_the_essence_for_them(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "auto"), foe = owner(helper, "autofoe");
        Mob rich = seer(helper, me, 2, 8, 30);
        Mob poor = seer(helper, me, 2, 4, 4);
        Mob e1 = vindicator(helper, foe, 7, 7), e2 = vindicator(helper, foe, 7, 8), e3 = vindicator(helper, foe, 7, 9);
        List<Entity> all = live(rich, poor, e1, e2, e3);
        if (all.size() != 5) {
            cleanUp(helper, all);
            helper.fail("could not spawn the two Tide Seers and the three enemies");
            return;
        }
        helper.runAfterDelay(5, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Unit r = (Unit) rich, p = (Unit) poor;
                AutocastState.setAll(r, true);
                AutocastState.setAll(p, true);
                boolean first = AutoCaster.look(level, rich, r);
                if (!first || Essence.get(rich) != 25 || cooldown(rich, BrineBolt.class) <= 0)
                    problems.add("first look: cast " + first + ", " + Essence.get(rich) + " essence left, Brine Bolt cooldown "
                        + cooldown(rich, BrineBolt.class) + " - expected Brine Bolt (30 - 5 = 25)");
                boolean second = AutoCaster.look(level, rich, r);
                if (!second || Essence.get(rich) != 15 || cooldown(rich, DrownedTithe.class) <= 0 || poolsOf(me) != 1)
                    problems.add("second look: cast " + second + ", " + Essence.get(rich) + " essence left, Drowned Tithe cooldown "
                        + cooldown(rich, DrownedTithe.class) + ", " + poolsOf(me) + " pools - expected a whirlpool on the crowd (25 - 10 = 15)");
                boolean third = AutoCaster.look(level, rich, r);
                if (third || Essence.get(rich) != 15 || cooldown(rich, RisingTide.class) > 0)
                    problems.add("third look: cast " + third + ", " + Essence.get(rich) + " essence left, Rising Tide cooldown "
                        + cooldown(rich, RisingTide.class) + " - expected nothing (Rising Tide's 20 is more than 15)");
                boolean poorCast = AutoCaster.look(level, poor, p);
                if (poorCast || Essence.get(poor) != 4 || cooldown(poor, BrineBolt.class) > 0)
                    problems.add("a Tide Seer with 4 essence cast " + poorCast + " (" + Essence.get(poor) + " essence left, Brine Bolt cooldown "
                        + cooldown(poor, BrineBolt.class) + "), expected nothing");
                AutocastState.setAll(r, false);
                AutocastState.setAll(p, false);
            } catch (RuntimeException ex) {
                problems.add("the autocast threw " + ex);
            } finally {
                cleanUp(helper, all);
            }
            finish(helper, problems);
        });
    }
}
