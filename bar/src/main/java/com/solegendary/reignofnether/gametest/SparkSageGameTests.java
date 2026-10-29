package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoCaster;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.CapturedSpark;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.StaticStep;
import com.solegendary.reignofnether.ability.abilities.Thunderhead;
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
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.storm.SparkSageProd;
import com.solegendary.reignofnether.unit.units.storm.SparkSageUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The Stormcallers essence caster, the Spark Sage (design/essence_casters.md, "Last three casters", revision-scaled;
 * lovish approved draft 2 on Oct 29: "one caster per race"). Written before the code, from the design's numbers:
 * <ul>
 *   <li>trained at the Celestial Observatory (Stormcallers T2 lab) once Tier 2 is researched, for a cost; an essence
 *       carrier (gathers the wisps within 8 blocks) with three spells and the shared Empower;</li>
 *   <li>Static Step: 5 essence, 12 s - a blink of up to 8 blocks to a visible, standable spot (the Thunder Sage's Blink
 *       rules); refused with no such spot;</li>
 *   <li>Captured Spark: 10 essence, 25 s - chain lightning, 30 damage a strike, 4 strikes, each jump within 6 blocks,
 *       never a friend;</li>
 *   <li>Thunderhead: 20 essence, 60 s - a storm cloud for 10 s strikes one enemy within 6 of it every second for 25;</li>
 *   <li>every spell refuses - no essence spent, no cooldown - when it cannot act, and an order during the cooldown is
 *       refused too; the bots cast them (autocastEssence and an AutoRule on each).</li>
 * </ul>
 * Every unit stands still (no AI, autocast off unless a test turns it on) inside the test's own arena under owners
 * unique to the arena; the spells are cast in the arena's middle, well clear of the neighbouring arenas. Early callbacks
 * only record, never throw; the last callback judges and always cleans up (units, clouds, blocks, wisps) whatever the
 * verdict, as EssenceGameTests do.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SparkSageGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "caster_storm";
    /** A vindicator's health: 75, no magic resist, so a spell's damage is read straight off it. */
    static final float VINDICATOR_HP = 75f;

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_sparksage_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A unit that stands where it is put: no AI and no autocast, so it never walks off or casts by itself. */
    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null) {
            m.setNoAi(true);
            if (m instanceof Unit u)
                AutocastState.setAll(u, false);
        }
        return m;
    }

    /** A Spark Sage holding {@code essence}. */
    private static Mob sage(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = still(helper, EntityRegistrar.SPARK_SAGE_UNIT.get(), owner, x, z);
        if (m != null && essence > 0)
            Essence.add(m, essence);
        return m;
    }

    private static Mob vindicator(GameTestHelper helper, String owner, int x, int z) {
        return still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, x, z);
    }

    /** A unit with {@code hp} health, so ten strikes leave it standing. */
    private static void toughen(Mob m, float hp) {
        AttributeInstance max = m.getAttribute(Attributes.MAX_HEALTH);
        if (max != null)
            max.setBaseValue(hp);
        m.setHealth(hp);
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

    private static float cooldown(Mob caster, Ability a) {
        return a == null ? 0 : ((Unit) caster).getCooldown(a);
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void discardAll(List<? extends Entity> entities) {
        for (Entity e : entities)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    private static List<Entity> live(Entity... entities) {
        return new ArrayList<>(Arrays.stream(entities).filter(Objects::nonNull).toList());
    }

    /** Takes down every Thunderhead cloud these casters raised. */
    private static void dispelClouds(Entity... casters) {
        List<Entity> cs = Arrays.asList(casters);
        for (Thunderhead.Cloud c : new ArrayList<>(Thunderhead.clouds()))
            if (cs.contains(c.caster))
                Thunderhead.dispel(c);
    }

    private static Thunderhead.Cloud cloudOf(Entity caster) {
        for (Thunderhead.Cloud c : Thunderhead.clouds())
            if (c.caster == caster && c.isActive())
                return c;
        return null;
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ------------------------------------------------------------------------------------------- the unit

    /**
     * The Celestial Observatory trains the Spark Sage, for a real cost (metal, energy, population), behind the Tier 2
     * gate like the rest of the lab; the item makes a Spark Sage, a Stormcallers unit whose cost is the item's.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void spark_sage_trains_at_the_celestial_observatory_for_a_cost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        if (!Buildings.CELESTIAL_OBSERVATORY.productions.get().contains(ProductionItems.SPARK_SAGE))
            problems.add("the Celestial Observatory does not offer the Spark Sage");
        ResourceCost cost = ResourceCosts.SPARK_SAGE;
        if (cost.metal() <= 0 || cost.energy() <= 0 || cost.population <= 0 || cost.ticks <= 0)
            problems.add("the Spark Sage costs " + cost.metal() + " metal, " + cost.energy() + " energy, " + cost.population
                + " population, " + cost.ticks + " ticks: every part should be above 0");
        if (EntityRegistrar.getEntityType(SparkSageProd.itemName) != EntityRegistrar.SPARK_SAGE_UNIT.get())
            problems.add("the Spark Sage item does not make a Spark Sage");
        if (ProductionItems.getProductionItem(EntityRegistrar.SPARK_SAGE_UNIT.get()) != ProductionItems.SPARK_SAGE)
            problems.add("the Spark Sage type does not resolve to its production item");

        String me = owner(helper, "train");
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.CELESTIAL_OBSERVATORY, level,
            new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, me, false);
        Resources pool = new Resources(me, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        Mob made = null;
        try {
            if (!(bp instanceof ProductionPlacement pp)) {
                problems.add("no Celestial Observatory placement: " + bp);
            } else {
                pp.isBuilt = true;
                if (ProductionItems.SPARK_SAGE.getProduceErrorMsg(pp) == null)
                    problems.add("the Spark Sage was offered without Tier 2");
                ResearchServerEvents.addResearch(me, ProductionItems.RESEARCH_TIER_2);
                String err = ProductionItems.SPARK_SAGE.getProduceErrorMsg(pp);
                if (err != null)
                    problems.add("with Tier 2 the Spark Sage is refused: " + err);
                else if (!pp.startProductionItem(ProductionItems.SPARK_SAGE))
                    problems.add("the server refused to queue the Spark Sage");
                pp.productionQueue.clear();
            }
            made = EntityRegistrar.SPARK_SAGE_UNIT.get().create(level);
            if (!(made instanceof Unit u))
                problems.add("a Spark Sage is not a unit: " + made);
            else {
                if (u.getCost() != ResourceCosts.SPARK_SAGE)
                    problems.add("a Spark Sage's cost is not ResourceCosts.SPARK_SAGE");
                if (!Factions.STORMCALLERS.equals(Factions.getFaction(u)))
                    problems.add("the Spark Sage is not Stormcallers: " + Factions.getFaction(u));
                if (Math.abs(made.getMaxHealth() - SparkSageUnit.maxHealth) > 0.01f || made.getMaxHealth() <= 0)
                    problems.add("a Spark Sage has " + made.getMaxHealth() + " health, its class says " + SparkSageUnit.maxHealth);
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
     * The Spark Sage is an essence carrier: it pulls in a wisp 5 blocks away and holds its essence. It has its three
     * spells and the shared Empower.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = BATCH)
    public static void spark_sage_gathers_essence_and_has_its_four_spells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "gather");
        Mob caster = sage(helper, me, 5, 8, 0);
        if (caster == null) {
            helper.fail("could not spawn a Spark Sage");
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
                    problems.add("the Spark Sage is not an essence carrier");
                else if (Essence.get(caster) != 4)
                    problems.add("the Spark Sage holds " + Essence.get(caster) + " essence after 5 s beside a 4-essence wisp, expected 4");
                if (wisp != null && !wisp.isRemoved())
                    problems.add("the wisp 5 blocks from the Spark Sage was never gathered");
                List<String> missing = new ArrayList<>();
                if (ability(caster, StaticStep.class) == null) missing.add("Static Step");
                if (ability(caster, CapturedSpark.class) == null) missing.add("Captured Spark");
                if (ability(caster, Thunderhead.class) == null) missing.add("Thunderhead");
                if (ability(caster, Empower.class) == null) missing.add("Empower");
                if (!missing.isEmpty())
                    problems.add("the Spark Sage lacks " + String.join(", ", missing));
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** The shared Empower works from the Spark Sage: 15 essence, a friend 3 blocks away gains a rank. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void spark_sage_empowers_a_friend_for_15_essence(GameTestHelper helper) {
        String me = owner(helper, "emp");
        Mob caster = sage(helper, me, 5, 6, 15);
        Mob friend = vindicator(helper, me, 8, 6);
        List<Entity> all = live(caster, friend);
        if (caster == null || friend == null) {
            discardAll(all);
            helper.fail("could not spawn the Spark Sage and its friend");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Empower emp = ability(caster, Empower.class);
                if (Essence.get(caster) != 15)
                    problems.add("the Spark Sage holds " + Essence.get(caster) + " essence, the test gave it 15");
                if (emp == null)
                    problems.add("the Spark Sage has no Empower");
                else {
                    emp.use(helper.getLevel(), (Unit) caster, friend);
                    if (VeterancyServerEvents.getRank(friend) != 1)
                        problems.add("the friend is rank " + VeterancyServerEvents.getRank(friend) + " after Empower, expected 1");
                    if (Essence.get(caster) != 0)
                        problems.add("the Spark Sage holds " + Essence.get(caster) + " essence after Empower, expected 0");
                    if (cooldown(caster, emp) <= 0)
                        problems.add("Empower set no cooldown");
                }
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------ Static Step

    /**
     * Static Step: 5 essence, 12 s cooldown. Aimed 11 blocks away over open ground, the Sage blinks the full 8 blocks
     * along the line and no further; an order during the cooldown, through the click's own path, spends nothing and
     * moves it nowhere.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void spark_sage_static_step_blinks_up_to_8_blocks_for_5_essence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = owner(helper, "step");
        Mob caster = sage(helper, me, 3, 8, 30);
        List<Entity> all = live(caster);
        if (caster == null) {
            helper.fail("could not spawn a Spark Sage");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (StaticStep.ESSENCE_COST != 5 || StaticStep.CD_SECONDS != 12 || StaticStep.RANGE != 8)
                    problems.add("the design set 5 essence, 12 s, 8 blocks; Static Step has " + StaticStep.ESSENCE_COST
                        + ", " + StaticStep.CD_SECONDS + " s, " + StaticStep.RANGE);
                StaticStep step = ability(caster, StaticStep.class);
                if (Essence.get(caster) != 30)
                    problems.add("the Spark Sage holds " + Essence.get(caster) + " essence, the test gave it 30");
                if (step == null) {
                    problems.add("the Spark Sage has no Static Step");
                } else {
                    Vec3 before = caster.position();
                    step.use(level, (Unit) caster, ground(helper, 14, 8));
                    Vec3 after = caster.position();
                    double moved = flat(before, after);
                    if (moved < 7.5 || moved > 8.05)
                        problems.add("Static Step aimed 11 blocks away moved the Sage " + String.format("%.2f", moved) + " blocks, expected 8");
                    if (Math.abs(after.z - before.z) > 0.3)
                        problems.add("Static Step left the line of its aim (z " + before.z + " -> " + after.z + ")");
                    if (Essence.get(caster) != 25)
                        problems.add("the Spark Sage holds " + Essence.get(caster) + " essence after Static Step, expected 30 - 5 = 25");
                    float cd = cooldown(caster, step);
                    if (Math.abs(cd - StaticStep.CD_SECONDS * 20) > 1)
                        problems.add("Static Step set a " + cd + "-tick cooldown, expected " + StaticStep.CD_SECONDS * 20);
                    // the cooldown holds: the same order again, back the way it came, is refused
                    new UnitActionItem(me, UnitAction.STATIC_STEP, -1, new int[] { caster.getId() }, ground(helper, 1, 8), BlockPos.ZERO).action(level);
                    if (Essence.get(caster) != 25)
                        problems.add("an order during the cooldown spent essence (" + Essence.get(caster) + " left, expected 25)");
                    if (flat(after, caster.position()) > 0.5)
                        problems.add("an order during the cooldown moved the Sage " + String.format("%.2f", flat(after, caster.position())) + " blocks");
                }
            } catch (RuntimeException ex) {
                problems.add("the cast threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Static Step refuses - no essence spent, no cooldown, no move - when no standable spot along the line is in sight
     * (the Sage walled in by a 3-high stone ring) and when the Sage is 1 essence short. Given that 1 essence, the
     * short Sage then steps the 4 blocks it was aimed.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void spark_sage_static_step_refuses_without_a_visible_spot_or_when_short(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = owner(helper, "stepno");
        Mob walled = sage(helper, me, 4, 4, 30);
        Mob poor = sage(helper, me, 6, 11, 4);
        List<Entity> all = live(walled, poor);
        List<BlockPos> ring = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0)
                    for (int y = 2; y <= 4; y++)
                        ring.add(helper.absolutePos(new BlockPos(4 + dx, y, 4 + dz)));
        for (BlockPos p : ring)
            level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
        if (all.size() != 2) {
            discardAll(all);
            for (BlockPos p : ring)
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            helper.fail("could not spawn the two Spark Sages");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                StaticStep a = ability(walled, StaticStep.class), b = ability(poor, StaticStep.class);
                if (Essence.get(walled) != 30 || Essence.get(poor) != 4)
                    problems.add("the Sages hold " + Essence.get(walled) + " and " + Essence.get(poor) + " essence, the test gave them 30 and 4");
                if (a == null || b == null) {
                    problems.add("a Spark Sage has no Static Step");
                } else {
                    Vec3 w0 = walled.position(), p0 = poor.position();
                    a.use(level, (Unit) walled, ground(helper, 12, 4));
                    if (flat(w0, walled.position()) > 0.1)
                        problems.add("the walled-in Sage blinked " + String.format("%.2f", flat(w0, walled.position())) + " blocks through its wall");
                    if (Essence.get(walled) != 30)
                        problems.add("Static Step with no spot in sight spent essence: " + Essence.get(walled) + " left of 30");
                    if (cooldown(walled, a) > 0)
                        problems.add("Static Step with no spot in sight set a cooldown");
                    b.use(level, (Unit) poor, ground(helper, 10, 11));
                    if (flat(p0, poor.position()) > 0.1)
                        problems.add("a Sage 1 essence short blinked");
                    if (Essence.get(poor) != 4)
                        problems.add("a Sage 1 essence short spent essence: " + Essence.get(poor) + " left of 4");
                    if (cooldown(poor, b) > 0)
                        problems.add("a Sage 1 essence short got a cooldown");
                    // the control: with the essence it lacked, the same order steps the 4 blocks
                    Essence.add(poor, 1);
                    b.use(level, (Unit) poor, ground(helper, 10, 11));
                    double moved = flat(p0, poor.position());
                    if (moved < 3.7 || moved > 4.3)
                        problems.add("with 5 essence the Sage aimed 4 blocks away moved " + String.format("%.2f", moved) + " blocks");
                    if (Essence.get(poor) != 0)
                        problems.add("the step with 5 essence left " + Essence.get(poor) + ", expected 0");
                }
            } catch (RuntimeException ex) {
                problems.add("the cast threw " + ex);
            } finally {
                discardAll(all);
                for (BlockPos p : ring)
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            finish(helper, problems);
        });
    }

    // ---------------------------------------------------------------------------------------- Captured Spark

    /**
     * Captured Spark: 10 essence, 25 s cooldown. Aimed at the first of five enemies standing 4 blocks apart in a zig-zag,
     * it strikes four of them for 30 each - the nearest unstruck enemy within 6 of the last every time - and stops; the
     * fifth, 4 blocks from the fourth, is untouched, and so is a friend 2 blocks from the first. An order during the
     * cooldown, through the click's own path, strikes nobody and spends nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void spark_sage_captured_spark_chains_30_damage_over_4_enemies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = owner(helper, "spark"), foe = owner(helper, "sparkfoe");
        Mob caster = sage(helper, me, 1, 4, 30);
        Mob f1 = vindicator(helper, foe, 5, 4);
        Mob f2 = vindicator(helper, foe, 5, 8);
        Mob f3 = vindicator(helper, foe, 9, 8);
        Mob f4 = vindicator(helper, foe, 9, 12);
        Mob f5 = vindicator(helper, foe, 13, 12);
        Mob friend = vindicator(helper, me, 6, 6);
        List<Entity> all = live(caster, f1, f2, f3, f4, f5, friend);
        if (all.size() != 7) {
            discardAll(all);
            helper.fail("could not spawn the Spark Sage, the five enemies and the friend");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (CapturedSpark.ESSENCE_COST != 10 || CapturedSpark.CD_SECONDS != 25 || CapturedSpark.DAMAGE != 30f
                        || CapturedSpark.STRIKES != 4 || CapturedSpark.JUMP_RADIUS != 6)
                    problems.add("the design set 10 essence, 25 s, 30 damage, 4 strikes, 6-block jumps; Captured Spark has "
                        + CapturedSpark.ESSENCE_COST + ", " + CapturedSpark.CD_SECONDS + " s, " + CapturedSpark.DAMAGE + ", "
                        + CapturedSpark.STRIKES + ", " + CapturedSpark.JUMP_RADIUS);
                CapturedSpark spark = ability(caster, CapturedSpark.class);
                if (spark == null) {
                    problems.add("the Spark Sage has no Captured Spark");
                } else {
                    UnitGrid.invalidate();
                    spark.use(level, (Unit) caster, f1);
                    Mob[] struck = { f1, f2, f3, f4 };
                    for (int i = 0; i < struck.length; i++) {
                        float lost = VINDICATOR_HP - struck[i].getHealth();
                        if (Math.abs(lost - 30f) > 0.01f)
                            problems.add("enemy " + (i + 1) + " of the chain lost " + lost + " health, expected 30");
                    }
                    if (f5.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("the fifth enemy was struck: the chain is 4 strikes (it lost " + (VINDICATOR_HP - f5.getHealth()) + ")");
                    if (friend.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("the friend 2 blocks from the first enemy was struck");
                    if (Essence.get(caster) != 20)
                        problems.add("the Spark Sage holds " + Essence.get(caster) + " essence after Captured Spark, expected 30 - 10 = 20");
                    float cd = cooldown(caster, spark);
                    if (Math.abs(cd - CapturedSpark.CD_SECONDS * 20) > 1)
                        problems.add("Captured Spark set a " + cd + "-tick cooldown, expected " + CapturedSpark.CD_SECONDS * 20);
                    // the cooldown holds: the same order again at the untouched fifth enemy is refused
                    new UnitActionItem(me, UnitAction.CAPTURED_SPARK, f5.getId(), new int[] { caster.getId() }, f5.blockPosition(), BlockPos.ZERO).action(level);
                    if (f5.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("an order during the cooldown struck the fifth enemy");
                    if (Essence.get(caster) != 20)
                        problems.add("an order during the cooldown spent essence (" + Essence.get(caster) + " left, expected 20)");
                }
            } catch (RuntimeException ex) {
                problems.add("the cast threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Captured Spark refuses - no essence spent, no cooldown, nobody struck - when the Sage is 1 essence short, when
     * aimed at a friend and when the enemy is past its 10-block throw. Then, aimed at an enemy whose nearest fellow
     * stands 7 blocks off, it strikes that one alone: a jump reaches 6 blocks, no further.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void spark_sage_captured_spark_refuses_when_short_friendly_or_out_of_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = owner(helper, "sparkno"), foe = owner(helper, "sparknofoe");
        Mob poor = sage(helper, me, 1, 2, 9);
        Mob rich = sage(helper, me, 1, 8, 30);
        Mob a = vindicator(helper, foe, 4, 1);       // the short Sage's target
        Mob friend = vindicator(helper, me, 3, 8);
        Mob far = vindicator(helper, foe, 14, 14);   // 13.9 blocks from the rich Sage
        Mob d = vindicator(helper, foe, 5, 8);
        Mob e = vindicator(helper, foe, 12, 8);      // 7 blocks from d
        List<Entity> all = live(poor, rich, a, friend, far, d, e);
        if (all.size() != 7) {
            discardAll(all);
            helper.fail("could not spawn the two Spark Sages and the five vindicators");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                CapturedSpark sp = ability(poor, CapturedSpark.class), sr = ability(rich, CapturedSpark.class);
                if (sp == null || sr == null) {
                    problems.add("a Spark Sage has no Captured Spark");
                } else {
                    UnitGrid.invalidate();
                    sp.use(level, (Unit) poor, a);
                    if (a.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("a Sage 1 essence short struck its target");
                    if (Essence.get(poor) != 9)
                        problems.add("a Sage 1 essence short spent essence: " + Essence.get(poor) + " left of 9");
                    if (cooldown(poor, sp) > 0)
                        problems.add("a Sage 1 essence short got a cooldown");
                    sr.use(level, (Unit) rich, friend);
                    if (friend.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("Captured Spark aimed at a friend struck it");
                    sr.use(level, (Unit) rich, far);
                    if (far.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("Captured Spark struck an enemy 13.9 blocks away, past its " + CapturedSpark.RANGE + "-block throw");
                    if (Essence.get(rich) != 30)
                        problems.add("refused sparks (a friend, an enemy out of range) spent essence: " + Essence.get(rich) + " left of 30");
                    if (cooldown(rich, sr) > 0)
                        problems.add("a refused spark set a cooldown");
                    // the control, and the jump's reach: the enemy 7 blocks from d is out of the 6-block jump
                    sr.use(level, (Unit) rich, d);
                    if (Math.abs(VINDICATOR_HP - d.getHealth() - 30f) > 0.01f)
                        problems.add("the enemy 4 blocks from the full Sage lost " + (VINDICATOR_HP - d.getHealth()) + ", expected 30");
                    if (e.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("the spark jumped 7 blocks, past its 6-block reach");
                    if (friend.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("the spark jumped to the friend 2 blocks from its target");
                    if (Essence.get(rich) != 20)
                        problems.add("the full Sage holds " + Essence.get(rich) + " essence after its spark, expected 20");
                }
            } catch (RuntimeException ex) {
                problems.add("the cast threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------ Thunderhead

    /**
     * Thunderhead: 20 essence, 60 s cooldown. A cloud over a spot strikes the enemy nearest its centre for 25 once a
     * second for 10 s - 3 strikes in the first 3.5 s, 10 in all - then is gone. An enemy 4 blocks out (never the
     * nearest), an enemy 7 blocks out (outside the 6) and a friend beside the centre are never struck.
     */
    @GameTest(template = ARENA, timeoutTicks = 260, batch = BATCH)
    public static void spark_sage_thunderhead_strikes_once_a_second_for_25_over_10_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = owner(helper, "storm"), foe = owner(helper, "stormfoe");
        Mob caster = sage(helper, me, 1, 8, 30);
        Mob near = vindicator(helper, foe, 8, 9);    // 1 block from the centre
        Mob mid = vindicator(helper, foe, 8, 12);    // 4 blocks
        Mob out = vindicator(helper, foe, 8, 15);    // 7 blocks
        Mob friend = vindicator(helper, me, 7, 8);
        List<Entity> all = live(caster, near, mid, out, friend);
        if (all.size() != 5) {
            discardAll(all);
            helper.fail("could not spawn the Spark Sage and the four vindicators");
            return;
        }
        float tough = 400f;
        toughen(near, tough);
        List<String> early = new ArrayList<>();
        float[] lostAt70 = { -1 };
        int[] strikesAt70 = { -1 };
        helper.runAfterDelay(3, () -> {
            try {
                if (Thunderhead.ESSENCE_COST != 20 || Thunderhead.CD_SECONDS != 60 || Thunderhead.DAMAGE != 25f
                        || Thunderhead.RADIUS != 6 || Thunderhead.DURATION_TICKS != 200)
                    early.add("the design set 20 essence, 60 s, 25 damage, 6 blocks, 10 s; Thunderhead has " + Thunderhead.ESSENCE_COST
                        + ", " + Thunderhead.CD_SECONDS + " s, " + Thunderhead.DAMAGE + ", " + Thunderhead.RADIUS + ", "
                        + Thunderhead.DURATION_TICKS + " ticks");
                Thunderhead th = ability(caster, Thunderhead.class);
                if (th == null) {
                    early.add("the Spark Sage has no Thunderhead");
                    return;
                }
                th.use(level, (Unit) caster, ground(helper, 8, 8));
                if (cloudOf(caster) == null)
                    early.add("Thunderhead raised no cloud");
                if (Essence.get(caster) != 10)
                    early.add("the Spark Sage holds " + Essence.get(caster) + " essence after Thunderhead, expected 30 - 20 = 10");
                float cd = cooldown(caster, th);
                if (Math.abs(cd - Thunderhead.CD_SECONDS * 20) > 1)
                    early.add("Thunderhead set a " + cd + "-tick cooldown, expected " + Thunderhead.CD_SECONDS * 20);
                if (near.getHealth() < tough - 0.01f)
                    early.add("the cloud struck at once, before its first second");
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        helper.runAfterDelay(73, () -> {
            lostAt70[0] = tough - near.getHealth();
            Thunderhead.Cloud c = cloudOf(caster);
            strikesAt70[0] = c == null ? -1 : c.strikes;
        });
        helper.runAfterDelay(218, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (problems.isEmpty()) {
                    if (Math.abs(lostAt70[0] - 75f) > 0.01f)
                        problems.add("3.5 s after the cast the nearest enemy had lost " + lostAt70[0] + ", expected 3 strikes of 25 = 75");
                    if (strikesAt70[0] != 3)
                        problems.add("3.5 s after the cast the cloud had struck " + strikesAt70[0] + " times, expected 3");
                    float lost = tough - near.getHealth();
                    if (Math.abs(lost - 250f) > 0.01f)
                        problems.add("over the cloud's 10 s the nearest enemy lost " + lost + ", expected 10 strikes of 25 = 250");
                    if (cloudOf(caster) != null)
                        problems.add("the cloud was still up 10.75 s after the cast");
                    if (mid.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("the enemy 4 blocks out was struck, though one nearer stood under the cloud");
                    if (out.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("the enemy 7 blocks out, outside the cloud's 6, was struck");
                    if (friend.getHealth() < VINDICATOR_HP - 0.01f)
                        problems.add("a friend under the cloud was struck");
                    if (cooldown(caster, ability(caster, Thunderhead.class)) <= 0)
                        problems.add("the 60 s cooldown ended within 11 s");
                }
            } finally {
                dispelClouds(caster);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Thunderhead refuses - no essence spent, no cooldown, no cloud - over a spot with no enemy within 6 blocks and
     * when the Sage is 1 essence short. Given that 1 essence, the short Sage then raises its cloud over the enemy.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void spark_sage_thunderhead_refuses_with_no_enemy_under_it_or_when_short(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = owner(helper, "stormno"), foe = owner(helper, "stormnofoe");
        Mob rich = sage(helper, me, 1, 3, 30);
        Mob poor = sage(helper, me, 1, 12, 19);
        Mob enemy = vindicator(helper, foe, 8, 13);   // 9+ blocks from the rich Sage's spot
        List<Entity> all = live(rich, poor, enemy);
        if (all.size() != 3) {
            discardAll(all);
            helper.fail("could not spawn the two Spark Sages and the enemy");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Thunderhead tr = ability(rich, Thunderhead.class), tp = ability(poor, Thunderhead.class);
                if (tr == null || tp == null) {
                    problems.add("a Spark Sage has no Thunderhead");
                } else {
                    UnitGrid.invalidate();
                    tr.use(level, (Unit) rich, ground(helper, 10, 4));
                    if (cloudOf(rich) != null)
                        problems.add("Thunderhead over a spot with no enemy within 6 raised a cloud");
                    if (Essence.get(rich) != 30)
                        problems.add("Thunderhead with nobody to strike spent essence: " + Essence.get(rich) + " left of 30");
                    if (cooldown(rich, tr) > 0)
                        problems.add("Thunderhead with nobody to strike set a cooldown");
                    tp.use(level, (Unit) poor, ground(helper, 8, 12));
                    if (cloudOf(poor) != null)
                        problems.add("a Sage 1 essence short raised a cloud");
                    if (Essence.get(poor) != 19)
                        problems.add("a Sage 1 essence short spent essence: " + Essence.get(poor) + " left of 19");
                    if (cooldown(poor, tp) > 0)
                        problems.add("a Sage 1 essence short got a cooldown");
                    // the control: with the essence it lacked, the same order raises the cloud
                    Essence.add(poor, 1);
                    tp.use(level, (Unit) poor, ground(helper, 8, 12));
                    if (cloudOf(poor) == null)
                        problems.add("with 20 essence the Sage raised no cloud over the enemy");
                    if (Essence.get(poor) != 0)
                        problems.add("the cloud with 20 essence left " + Essence.get(poor) + ", expected 0");
                }
            } catch (RuntimeException ex) {
                problems.add("the cast threw " + ex);
            } finally {
                dispelClouds(rich, poor);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------- the bots

    /**
     * The bots cast the Sage's spells (design/essence_casters.md Revision 1): each spell's autocastEssence is its cost
     * and each has a rule (Static Step: SELF_IN_COMBAT, Captured Spark: ENEMY_IN_RANGE, Thunderhead: CROWD). One
     * AutoCaster look: a healthy Sage with 10 essence and an enemy 4 blocks off sparks it (30 damage, 10 essence); one
     * with 9 casts nothing; a Sage under half health with an enemy 2 blocks east steps 8 blocks west, away from it.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void spark_sage_bots_autocast_its_spells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String me = owner(helper, "auto"), foe = owner(helper, "autofoe");
        Mob sparker = sage(helper, me, 2, 3, 10);
        Mob target = vindicator(helper, foe, 6, 3);
        Mob dry = sage(helper, me, 2, 13, 9);
        Mob target2 = vindicator(helper, foe, 6, 13);
        Mob hurt = sage(helper, me, 12, 8, 5);
        Mob menace = vindicator(helper, foe, 14, 8);
        List<Entity> all = live(sparker, target, dry, target2, hurt, menace);
        if (all.size() != 6) {
            discardAll(all);
            helper.fail("could not spawn the three Spark Sages and their enemies");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                StaticStep step = ability(sparker, StaticStep.class);
                CapturedSpark spark = ability(sparker, CapturedSpark.class);
                Thunderhead storm = ability(sparker, Thunderhead.class);
                if (step == null || spark == null || storm == null) {
                    problems.add("the Spark Sage lacks a spell");
                    return;
                }
                if (step.autocastEssence != StaticStep.ESSENCE_COST || step.getAutoRule() != AutoRule.SELF_IN_COMBAT)
                    problems.add("Static Step autocasts with " + step.autocastEssence + " essence under " + step.getAutoRule()
                        + ", expected " + StaticStep.ESSENCE_COST + " and SELF_IN_COMBAT");
                if (spark.autocastEssence != CapturedSpark.ESSENCE_COST || spark.getAutoRule() != AutoRule.ENEMY_IN_RANGE)
                    problems.add("Captured Spark autocasts with " + spark.autocastEssence + " essence under " + spark.getAutoRule()
                        + ", expected " + CapturedSpark.ESSENCE_COST + " and ENEMY_IN_RANGE");
                if (storm.autocastEssence != Thunderhead.ESSENCE_COST || storm.getAutoRule() != AutoRule.CROWD)
                    problems.add("Thunderhead autocasts with " + storm.autocastEssence + " essence under " + storm.getAutoRule()
                        + ", expected " + Thunderhead.ESSENCE_COST + " and CROWD");

                for (Mob m : List.of(sparker, dry, hurt))
                    AutocastState.setAll((Unit) m, true);
                // the hurt Sage: hit by its menace (so it is in a fight), then left at 40% health
                hurt.hurt(level.damageSources().indirectMagic(menace, menace), 1f);
                hurt.setHealth(hurt.getMaxHealth() * 0.4f);
                UnitGrid.invalidate();

                AutoCaster.look(level, sparker, (Unit) sparker);
                if (Math.abs(VINDICATOR_HP - target.getHealth() - 30f) > 0.01f)
                    problems.add("the healthy Sage with 10 essence did not spark the enemy 4 blocks off (it lost " + (VINDICATOR_HP - target.getHealth()) + ")");
                if (Essence.get(sparker) != 0)
                    problems.add("the autocast spark left " + Essence.get(sparker) + " essence, expected 0");

                Vec3 d0 = dry.position();
                AutoCaster.look(level, dry, (Unit) dry);
                if (target2.getHealth() < VINDICATOR_HP - 0.01f || Essence.get(dry) != 9 || flat(d0, dry.position()) > 0.1)
                    problems.add("the healthy Sage with 9 essence cast anyway (enemy lost " + (VINDICATOR_HP - target2.getHealth())
                        + ", essence " + Essence.get(dry) + ")");

                Vec3 h0 = hurt.position();
                AutoCaster.look(level, hurt, (Unit) hurt);
                double movedWest = h0.x - hurt.position().x;
                if (movedWest < 7.5 || movedWest > 8.05)
                    problems.add("the hurt Sage did not step 8 blocks away from its menace (moved " + String.format("%.2f", movedWest) + " west)");
                if (Essence.get(hurt) != 0)
                    problems.add("the autocast step left " + Essence.get(hurt) + " essence, expected 0");
            } catch (RuntimeException ex) {
                problems.add("the autocast threw " + ex);
            } finally {
                discardAll(all);
                finish(helper, problems);
            }
        });
    }

    /**
     * lovish (Oct 29): the bots build and use all 7 casters. The Stormcallers bot kit trains the Spark Sage at its T2 lab
     * (the Observatory), and a Sage as the bot gets it - autocast switches untouched - casts by itself: its three spells
     * are on (Empower stays manual), and one AutoCaster look with 10 essence and an enemy 4 blocks off sparks it.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void spark_sage_bot_kit_trains_it_and_it_autocasts_by_default(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<String> problems = new ArrayList<>();
        com.solegendary.reignofnether.bot.BotPlayer.Kit kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.STORMCALLERS);
        if (kit == null || kit.t2Lab() != Buildings.CELESTIAL_OBSERVATORY || !kit.t2Army().contains(ProductionItems.SPARK_SAGE))
            problems.add("the Stormcallers bot kit does not train the Spark Sage at the Celestial Observatory");
        String me = owner(helper, "bot"), foe = owner(helper, "botfoe");
        // as trained: no AI switch-off, no autocast switch touched (HeroGameTests.spawnUnit, not still())
        Mob caster = HeroGameTests.spawnUnit(helper, EntityRegistrar.SPARK_SAGE_UNIT.get(), me, 4, 8);
        Mob target = vindicator(helper, foe, 8, 8);
        List<Entity> all = live(caster, target);
        if (all.size() != 2) {
            discardAll(all);
            helper.fail("could not spawn the Spark Sage and its enemy");
            return;
        }
        caster.setNoAi(true);   // stands still; AutoCaster does not read the AI flag
        helper.runAfterDelay(2, () -> {
            try {
                for (Ability a : ((Unit) caster).getAbilities().get()) {
                    boolean on = AutocastState.isOn((Unit) caster, a);
                    if (a instanceof Empower) {
                        if (a.getAutoRule() != AutoRule.MANUAL)
                            problems.add("Empower is no longer manual");
                    } else if (!on) {
                        problems.add(a.getClass().getSimpleName() + " does not autocast by default");
                    }
                }
                // the essence comes now: with essence and an enemy in reach since spawning, the world's own AutoCaster
                // pass could have cast first
                Essence.add(caster, 10);
                UnitGrid.invalidate();
                AutoCaster.look(level, caster, (Unit) caster);
                if (Math.abs(VINDICATOR_HP - target.getHealth() - 30f) > 0.01f)
                    problems.add("a Sage as the bot gets it did not spark the enemy 4 blocks off (it lost " + (VINDICATOR_HP - target.getHealth()) + ")");
                if (Essence.get(caster) != 0)
                    problems.add("the bot's autocast spark left " + Essence.get(caster) + " essence, expected 0");
            } catch (RuntimeException ex) {
                problems.add("the autocast threw " + ex);
            } finally {
                discardAll(all);
                finish(helper, problems);
            }
        });
    }
}
