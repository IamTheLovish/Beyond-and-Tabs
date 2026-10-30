package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoCaster;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.AwakenThicket;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.EntanglingRoots;
import com.solegendary.reignofnether.ability.abilities.SeedOfRenewal;
import com.solegendary.reignofnether.ability.abilities.SproutGuardian;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.bot.BotPlayer;
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
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.RenewalSaplingUnit;
import com.solegendary.reignofnether.unit.units.verdant.SentinelTreantUnit;
import com.solegendary.reignofnether.unit.units.verdant.ThornweaverProd;
import com.solegendary.reignofnether.unit.units.verdant.ThornweaverUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The Verdant essence caster, the Thornweaver (design/essence_casters.md; lovish approved draft 2 on Oct 29: "one
 * caster per race"). Written before the code, from his numbers:
 * <ul>
 *   <li>trained at the Grove (Verdant Tier 1 army building; lovish, Oct 29: casters are tier 1), for a cost, and by the Verdant bot
 *       (lovish, Oct 29: bots build and use all seven casters); an essence carrier
 *       (gathers the wisps within 8 blocks) with three spells and the shared Empower;</li>
 *   <li>Entangling Roots: 5 essence, 12 s - enemies within 3 blocks of the aimed spot are rooted (cannot move, can
 *       still attack) for 3 s;</li>
 *   <li>Seed of Renewal: 10 essence, 25 s - a sapling heals allies within 6 for 3 HP/s for 10 s (Revision 1, scaled to
 *       the Bloom Priestess); the sapling is a 100 HP unit the enemy can target, marked summoned (no essence);</li>
 *   <li>Sprout Guardian: 20 essence, 60 s - a sapling treant fights for the owner for 25 s, then withers, marked
 *       summoned (its death drops no essence);</li>
 *   <li>every spell refuses - no essence spent, no cooldown - when it cannot act (short of essence, nothing to root),
 *       and an order during the cooldown is refused too; the autocast never orders a cast its spell would refuse.</li>
 * </ul>
 * Revision 1 (coordinator, Oct 29): Seed of Renewal at 3 HP/s for 10 s, its sapling a 100 HP unit that can be
 * targeted, every summon marked summoned. Every unit stands still (no AI) inside the test's own arena under owners unique to the arena. Early callbacks only
 * record, never throw; the last callback judges and always cleans up (units, saplings, guardians, wisps) whatever the
 * verdict, as EssenceGameTests do.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ThornweaverGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "caster_verdant";

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_thornweaver_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A unit that stands where it is put: no AI, so it never walks off its spot. */
    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null)
            m.setNoAi(true);
        return m;
    }

    /** A Thornweaver holding {@code essence}. */
    private static Mob weaver(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = still(helper, EntityRegistrar.THORNWEAVER_UNIT.get(), owner, x, z);
        if (m != null && essence > 0)
            Essence.add(m, essence);
        return m;
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

    private static void discardAll(List<? extends Entity> entities) {
        for (Entity e : entities)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    private static List<Entity> live(Entity... entities) {
        return new ArrayList<>(Arrays.stream(entities).filter(Objects::nonNull).toList());
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    private static boolean rooted(LivingEntity le) {
        MobEffectInstance e = le.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return e != null && e.getAmplifier() >= EntanglingRoots.ROOT_AMPLIFIER;
    }

    /** The live Seed of Renewal saplings within {@code r} of the cell above the ground block {@code at}. */
    /**
     * Saplings within {@code r} of the cell above {@code at}, over the whole height the spell may plant at (it stands
     * the sapling up to MAX_DY from the click): sand or gravel still falling from the terrain over an arena can land in
     * the cleared pocket and lift it (run 40828975818, 2 runners of 10: "planted 0 sapling units").
     */
    private static List<RenewalSaplingUnit> saplingsNear(ServerLevel level, BlockPos at, double r) {
        return level.getEntitiesOfClass(RenewalSaplingUnit.class, new AABB(at.above()).inflate(r, SeedOfRenewal.MAX_DY + 1, r),
            e -> !e.isRemoved());
    }

    /**
     * Guardians within {@code r} of {@code at}, over the whole height the spell may raise one at (it stands the treant
     * up to SeedOfRenewal.MAX_DY from the click, like the sapling): falling sand or gravel can lift it (run 40969304023,
     * 2 runners of 8: "the cast raised 0 guardians").
     */
    private static List<LivingEntity> guardiansNear(ServerLevel level, BlockPos at, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(r, SeedOfRenewal.MAX_DY + 1, r), SproutGuardian::isGuardian);
    }

    /**
     * The guardians {@code caster} raised, from the spell's own list (SproutGuardian.guardiansOf): a treant raised in a
     * chunk whose entities are still loading is hidden from a level query for a while, so a box search right after the
     * cast can miss it (run 40986497550). Logs when the box search would have missed one, as evidence.
     */
    private static List<LivingEntity> guardiansOf(ServerLevel level, LivingEntity caster, BlockPos spot) {
        List<LivingEntity> mine = new ArrayList<>();
        for (Entity e : SproutGuardian.guardiansOf(caster))
            if (e instanceof LivingEntity le)
                mine.add(le);
        if (mine.size() != guardiansNear(level, spot, 2).size())
            ReignOfNether.LOGGER.info("[ThornweaverGameTests] {} guardian(s) on the spell's list, {} seen by a level query at {}",
                mine.size(), guardiansNear(level, spot, 2).size(), spot);
        return mine;
    }

    private static void clearWispsNear(Vec3 at, double r) {
        for (Entity w : EssenceServerEvents.getWisps())
            if (!w.isRemoved() && w.position().distanceTo(at) <= r)
                w.discard();
    }

    // ------------------------------------------------------------------------------------------- the unit

    /**
     * The Grove, the Court's Tier 1 army building, trains the Thornweaver (lovish, Oct 29: "essence casters should be
     * tier 1"); the Circle of Elders, the T2 lab, no longer does. It costs something real (metal, energy, population),
     * a built Grove queues it with no research at all, and the item makes a Thornweaver, a Verdant Court unit whose
     * cost is the item's.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void thornweaver_trains_at_the_grove_from_tier_1_for_a_cost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        if (!Buildings.GROVE.productions.get().contains(ProductionItems.THORNWEAVER))
            problems.add("the Grove (Tier 1) does not offer the Thornweaver");
        if (Buildings.CIRCLE_OF_ELDERS.productions.get().contains(ProductionItems.THORNWEAVER))
            problems.add("the Circle of Elders (the T2 lab) still offers the Thornweaver");
        ResourceCost cost = ResourceCosts.THORNWEAVER;
        if (cost.metal() <= 0 || cost.energy() <= 0 || cost.population <= 0 || cost.ticks <= 0)
            problems.add("the Thornweaver costs " + cost.metal() + " metal, " + cost.energy() + " energy, " + cost.population
                + " population, " + cost.ticks + " ticks: every part should be above 0");
        if (EntityRegistrar.getEntityType(ThornweaverProd.itemName) != EntityRegistrar.THORNWEAVER_UNIT.get())
            problems.add("the Thornweaver item does not make a Thornweaver");
        if (ProductionItems.getProductionItem(EntityRegistrar.THORNWEAVER_UNIT.get()) != ProductionItems.THORNWEAVER)
            problems.add("the Thornweaver type does not resolve to its production item");

        String me = owner(helper, "train");
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.GROVE, level,
            new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, me, false);
        Resources pool = new Resources(me, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        Mob made = null;
        try {
            if (!(bp instanceof ProductionPlacement pp)) {
                problems.add("no Grove placement: " + bp);
            } else {
                pp.isBuilt = true;
                String err = ProductionItems.THORNWEAVER.getProduceErrorMsg(pp);   // no research: Tier 1
                if (err != null)
                    problems.add("a built Grove without Tier 2 refuses the Thornweaver: " + err);
                else if (!pp.startProductionItem(ProductionItems.THORNWEAVER))
                    problems.add("the server refused to queue the Thornweaver");
                pp.productionQueue.clear();
            }
            made = EntityRegistrar.THORNWEAVER_UNIT.get().create(level);
            if (!(made instanceof Unit u))
                problems.add("a Thornweaver is not a unit: " + made);
            else {
                if (u.getCost() != ResourceCosts.THORNWEAVER)
                    problems.add("a Thornweaver's cost is not ResourceCosts.THORNWEAVER");
                if (!Factions.VERDANT_COURT.equals(Factions.getFaction(u)))
                    problems.add("the Thornweaver is not Verdant Court: " + Factions.getFaction(u));
                if (Math.abs(made.getMaxHealth() - ThornweaverUnit.maxHealth) > 0.01f || made.getMaxHealth() <= 0)
                    problems.add("a Thornweaver has " + made.getMaxHealth() + " health, its class says " + ThornweaverUnit.maxHealth);
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
     * The Thornweaver is an essence carrier: it pulls in a wisp 5 blocks away and holds its essence. It has its three
     * spells and the shared Empower.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = BATCH)
    public static void thornweaver_gathers_essence_and_has_its_four_spells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "gather");
        Mob caster = weaver(helper, me, 3, 3, 0);
        if (caster == null) {
            helper.fail("could not spawn a Thornweaver");
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
                    problems.add("the Thornweaver is not an essence carrier");
                else if (Essence.get(caster) != 4)
                    problems.add("the Thornweaver holds " + Essence.get(caster) + " essence after 5 s beside a 4-essence wisp, expected 4");
                if (wisp != null && !wisp.isRemoved())
                    problems.add("the wisp 5 blocks from the Thornweaver was never gathered");
                List<String> missing = new ArrayList<>();
                if (ability(caster, EntanglingRoots.class) == null) missing.add("Entangling Roots");
                if (ability(caster, SeedOfRenewal.class) == null) missing.add("Seed of Renewal");
                if (ability(caster, SproutGuardian.class) == null) missing.add("Sprout Guardian");
                if (ability(caster, Empower.class) == null) missing.add("Empower");
                if (!missing.isEmpty())
                    problems.add("the Thornweaver lacks " + String.join(", ", missing));
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** The shared Empower works from the Thornweaver: 15 essence, a friend 3 blocks away gains a rank. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void thornweaver_empowers_a_friend_for_15_essence(GameTestHelper helper) {
        String me = owner(helper, "emp");
        Mob caster = weaver(helper, me, 4, 4, 15);
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 7, 4);
        List<Entity> all = live(caster, friend);
        if (caster == null || friend == null) {
            discardAll(all);
            helper.fail("could not spawn the Thornweaver and its friend");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Empower emp = ability(caster, Empower.class);
                if (Essence.get(caster) != 15)
                    problems.add("the Thornweaver holds " + Essence.get(caster) + " essence, the test gave it 15");
                if (emp == null)
                    problems.add("the Thornweaver has no Empower");
                else {
                    emp.use(helper.getLevel(), (Unit) caster, friend);
                    if (VeterancyServerEvents.getRank(friend) != 1)
                        problems.add("the friend is rank " + VeterancyServerEvents.getRank(friend) + " after Empower, expected 1");
                    if (Essence.get(caster) != 0)
                        problems.add("the Thornweaver holds " + Essence.get(caster) + " essence after Empower, expected 0");
                    if (((Unit) caster).getCooldown(emp) <= 0)
                        problems.add("Empower set no cooldown");
                }
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------ Entangling Roots

    /**
     * Entangling Roots: 5 essence, 12 s cooldown. The enemy within 3 blocks of the aimed spot is rooted for 3 s - its
     * speed is 0, its attack untouched - while an enemy 4 blocks away and a friend inside the circle are not. An order
     * during the cooldown spends nothing; the root is gone after 3 s.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void thornweaver_entangling_roots_roots_enemies_within_3_for_3_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "roots"), foe = owner(helper, "rootsfoe");
        Mob caster = weaver(helper, me, 2, 2, 30);
        Mob near = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 9, 8);     // 1 block from the spot
        Mob far = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 8, 12);     // 4 blocks from it
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 7, 8);    // 1 block, the other side
        List<Entity> all = live(caster, near, far, friend);
        if (all.size() != 4) {
            discardAll(all);
            helper.fail("could not spawn the Thornweaver and the three vindicators");
            return;
        }
        BlockPos spot = ground(helper, 8, 8);
        double attackBefore = near.getAttributeValue(Attributes.ATTACK_DAMAGE);
        List<String> early = new ArrayList<>();
        boolean[] rootedAfter = { false };
        helper.runAfterDelay(3, () -> {
            try {
                if (EntanglingRoots.ESSENCE_COST != 5 || EntanglingRoots.CD_SECONDS != 12 || EntanglingRoots.RADIUS != 3
                        || EntanglingRoots.ROOT_TICKS != 60)
                    early.add("lovish set 5 essence, 12 s, 3 blocks, 3 s; Entangling Roots has " + EntanglingRoots.ESSENCE_COST
                        + ", " + EntanglingRoots.CD_SECONDS + " s, " + EntanglingRoots.RADIUS + ", " + EntanglingRoots.ROOT_TICKS + " ticks");
                EntanglingRoots roots = ability(caster, EntanglingRoots.class);
                if (Essence.get(caster) != 30)
                    early.add("the Thornweaver holds " + Essence.get(caster) + " essence, the test gave it 30");
                if (roots == null) {
                    early.add("the Thornweaver has no Entangling Roots");
                    return;
                }
                roots.use(level, (Unit) caster, spot);
                MobEffectInstance root = near.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                if (!rooted(near))
                    early.add("the enemy 1 block from the spot was not rooted");
                else {
                    if (root.getDuration() < 55 || root.getDuration() > 60)
                        early.add("the root lasts " + root.getDuration() + " ticks, expected 60");
                    if (near.getAttributeValue(Attributes.MOVEMENT_SPEED) > 1e-3)
                        early.add("the rooted enemy's speed is " + near.getAttributeValue(Attributes.MOVEMENT_SPEED) + ", expected 0");
                }
                if (Math.abs(near.getAttributeValue(Attributes.ATTACK_DAMAGE) - attackBefore) > 1e-6)
                    early.add("the root changed the enemy's attack (" + attackBefore + " -> " + near.getAttributeValue(Attributes.ATTACK_DAMAGE) + "): it should still fight");
                if (rooted(far))
                    early.add("the enemy 4 blocks from the spot was rooted: beyond the 3-block circle");
                if (friend.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
                    early.add("a friendly unit inside the circle was rooted");
                if (Essence.get(caster) != 25)
                    early.add("the Thornweaver holds " + Essence.get(caster) + " essence after Entangling Roots, expected 30 - 5 = 25");
                float cd = ((Unit) caster).getCooldown(roots);
                if (Math.abs(cd - EntanglingRoots.CD_SECONDS * 20) > 1)
                    early.add("Entangling Roots set a " + cd + "-tick cooldown, expected " + EntanglingRoots.CD_SECONDS * 20);
                // the cooldown holds: the same order again, through the click's own path, is refused
                new UnitActionItem(me, UnitAction.ENTANGLING_ROOTS, -1, new int[] { caster.getId() }, spot, BlockPos.ZERO).action(level);
                if (Essence.get(caster) != 25)
                    early.add("an order during the cooldown spent essence (" + Essence.get(caster) + " left, expected 25)");
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        helper.runAfterDelay(40, () -> rootedAfter[0] = rooted(near));
        helper.runAfterDelay(70, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (problems.isEmpty() && !rootedAfter[0])
                    problems.add("the root was gone after under 2 s, expected 3 s");
                if (rooted(near))
                    problems.add("the enemy was still rooted 3.35 s after the cast, expected 3 s");
                if (((Unit) caster).getCooldown(ability(caster, EntanglingRoots.class)) <= 0)
                    problems.add("the 12 s cooldown ended within 3.35 s");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Entangling Roots refuses without spending or a cooldown: a Thornweaver with 4 essence (one short) aimed at an
     * enemy, and a full one aimed at a spot with no enemy within 3 blocks.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void thornweaver_entangling_roots_refuses_when_short_or_with_nothing_to_root(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "rootsno"), foe = owner(helper, "rootsnofoe");
        Mob poor = weaver(helper, me, 2, 2, 4);
        Mob rich = weaver(helper, me, 13, 2, 30);
        Mob enemy = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 5, 8);
        List<Entity> all = live(poor, rich, enemy);
        if (all.size() != 3) {
            discardAll(all);
            helper.fail("could not spawn the two Thornweavers and the enemy");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                EntanglingRoots a = ability(poor, EntanglingRoots.class), b = ability(rich, EntanglingRoots.class);
                if (Essence.get(poor) != 4 || Essence.get(rich) != 30)
                    problems.add("the Thornweavers hold " + Essence.get(poor) + " and " + Essence.get(rich) + " essence, the test gave them 4 and 30");
                if (a == null || b == null)
                    problems.add("a Thornweaver has no Entangling Roots");
                else {
                    a.use(level, (Unit) poor, ground(helper, 5, 8));
                    if (Essence.get(poor) != 4)
                        problems.add("a Thornweaver 1 essence short spent essence: " + Essence.get(poor) + " left of 4");
                    if (((Unit) poor).getCooldown(a) > 0)
                        problems.add("a Thornweaver 1 essence short got a cooldown");
                    if (rooted(enemy))
                        problems.add("a Thornweaver 1 essence short rooted the enemy");
                    b.use(level, (Unit) rich, ground(helper, 12, 12));   // 7+ blocks from the enemy
                    if (Essence.get(rich) != 30)
                        problems.add("roots with nothing to root spent essence: " + Essence.get(rich) + " left of 30");
                    if (((Unit) rich).getCooldown(b) > 0)
                        problems.add("roots with nothing to root set a cooldown");
                }
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------- Seed of Renewal

    /**
     * Seed of Renewal: 10 essence, 25 s cooldown (Revision 1: 3 HP a second for 10 s). The sapling is a 100 HP unit of
     * the caster's owner, marked summoned, that the enemy beside it counts as hostile and can damage, and it never mends
     * itself. It heals the hurt friend 3 blocks away 3 HP a second - 15 by 5.5 s, 30 in all, nothing after it withers
     * - and never the friend 9.9 blocks away or the enemy beside it.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = BATCH)
    public static void thornweaver_seed_of_renewal_heals_3_a_second_within_6_for_10_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "seed"), foe = owner(helper, "seedfoe");
        SkirmishGameTests.openGround(helper, 2, 2, 14, 14);
        Mob caster = weaver(helper, me, 2, 2, 30);
        Mob near = still(helper, EntityRegistrar.ELDER_TREANT_UNIT.get(), me, 9, 6);     // 3 blocks from the spot
        Mob far = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 13, 13);      // 9.9 blocks from it
        Mob enemy = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 4, 6);     // 2 blocks from it
        List<Entity> all = live(caster, near, far, enemy);
        if (all.size() != 4) {
            discardAll(all);
            helper.fail("could not spawn the Thornweaver and the three units");
            return;
        }
        BlockPos spot = ground(helper, 6, 6);
        float[] start = new float[3];       // near, far, enemy health right after the cast
        float[] half = { -1 }, end = { -1 }, after = { -1 }, saplingHalf = { -1 };
        boolean[] witheredAtEnd = { false };
        List<Entity> made = new ArrayList<>();
        List<String> early = new ArrayList<>();
        final int cast = 2;
        helper.runAfterDelay(cast, () -> {
            try {
                if (SeedOfRenewal.ESSENCE_COST != 10 || SeedOfRenewal.CD_SECONDS != 25 || SeedOfRenewal.RADIUS != 6
                        || SeedOfRenewal.HEAL_PER_SECOND != 3f || SeedOfRenewal.LIFETIME_TICKS != 200)
                    early.add("lovish set 10 essence, 25 s, 6 blocks, 3 HP/s for 10 s; Seed of Renewal has " + SeedOfRenewal.ESSENCE_COST
                        + ", " + SeedOfRenewal.CD_SECONDS + " s, " + SeedOfRenewal.RADIUS + ", " + SeedOfRenewal.HEAL_PER_SECOND
                        + " for " + SeedOfRenewal.LIFETIME_TICKS + " ticks");
                if (near.getMaxHealth() < 50)
                    early.add("the Elder Treant has " + near.getMaxHealth() + " health: too little to see 30 healed");
                near.setHealth(near.getMaxHealth() - 45);
                far.setHealth(far.getMaxHealth() - 10);
                enemy.setHealth(enemy.getMaxHealth() - 10);
                // the enemy takes hits in the arena (run 40828030752 saw 65 -> 29), which hid any heal: with no damage
                // landing, only a heal moves its health
                enemy.setInvulnerable(true);
                start[0] = near.getHealth();
                start[1] = far.getHealth();
                start[2] = enemy.getHealth();
                SeedOfRenewal seed = ability(caster, SeedOfRenewal.class);
                if (Essence.get(caster) != 30)
                    early.add("the Thornweaver holds " + Essence.get(caster) + " essence, the test gave it 30");
                if (seed == null) {
                    early.add("the Thornweaver has no Seed of Renewal");
                    return;
                }
                seed.use(level, (Unit) caster, spot);
                List<RenewalSaplingUnit> saplings = saplingsNear(level, spot, 1.5);
                made.addAll(saplings);
                if (saplings.size() != 1) {
                    early.add("the cast planted " + saplings.size() + " sapling units by the spot, expected 1");
                } else {
                    RenewalSaplingUnit sapling = saplings.get(0);
                    if (RenewalSaplingUnit.HEALTH != 100f || Math.abs(sapling.getMaxHealth() - 100f) > 0.01f)
                        early.add("the sapling has " + sapling.getMaxHealth() + " health, Revision 1 set 100");
                    if (!me.equals(sapling.getOwnerName()))
                        early.add("the sapling belongs to " + sapling.getOwnerName() + ", not to the caster's owner");
                    if (!Essence.isSummoned(sapling) || !SeedOfRenewal.isSapling(sapling))
                        early.add("the sapling is not marked summoned: its death would drop essence");
                    if (UnitServerEvents.getUnitToEntityRelationship((Unit) enemy, sapling) != Relationship.HOSTILE)
                        early.add("the enemy does not see the sapling as hostile: it cannot be targeted");
                    if (!sapling.isAttackable() || sapling.isInvulnerable())
                        early.add("the sapling cannot be attacked");
                    sapling.hurt(level.damageSources().generic(), 10f);
                    if (sapling.getHealth() > 90.01f)
                        early.add("10 damage left the sapling at " + sapling.getHealth() + " health");
                }
                if (Essence.get(caster) != 20)
                    early.add("the Thornweaver holds " + Essence.get(caster) + " essence after Seed of Renewal, expected 30 - 10 = 20");
                float cd = ((Unit) caster).getCooldown(seed);
                if (Math.abs(cd - SeedOfRenewal.CD_SECONDS * 20) > 1)
                    early.add("Seed of Renewal set a " + cd + "-tick cooldown, expected " + SeedOfRenewal.CD_SECONDS * 20);
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        // pulses fall on the sapling's seconds (about cast + 20, + 40 ... + 200): sample between them
        helper.runAfterDelay(cast + 110, () -> {
            half[0] = near.getHealth();
            saplingHalf[0] = made.isEmpty() ? -1 : ((LivingEntity) made.get(0)).getHealth();
        });
        helper.runAfterDelay(cast + 230, () -> {
            end[0] = near.getHealth();
            witheredAtEnd[0] = made.stream().allMatch(Entity::isRemoved) && saplingsNear(level, spot, 1.5).isEmpty();
        });
        helper.runAfterDelay(cast + 270, () -> {
            after[0] = near.getHealth();
            List<String> problems = new ArrayList<>(early);
            try {
                if (problems.isEmpty()) {
                    if (Math.abs(half[0] - start[0] - 15) > 0.01f)
                        problems.add("the friend 3 blocks away gained " + (half[0] - start[0]) + " HP in 5.5 s, expected 5 pulses x 3 = 15");
                    if (Math.abs(end[0] - start[0] - 30) > 0.01f)
                        problems.add("the friend 3 blocks away gained " + (end[0] - start[0]) + " HP over the sapling's 10 s, expected 30");
                    if (!witheredAtEnd[0])
                        problems.add("the sapling still stood 11.5 s after it was planted");
                    if (Math.abs(after[0] - end[0]) > 0.01f)
                        problems.add("the friend gained " + (after[0] - end[0]) + " HP after the sapling withered");
                    if (saplingHalf[0] > 90.01f)
                        problems.add("the sapling mended itself: " + saplingHalf[0] + " health 5.5 s after taking 10 damage");
                    if (Math.abs(far.getHealth() - start[1]) > 0.01f)
                        problems.add("the friend 9.9 blocks away was healed (" + start[1] + " -> " + far.getHealth() + ")");
                    if (Math.abs(enemy.getHealth() - start[2]) > 0.01f)
                        problems.add("the enemy beside the sapling was healed (" + start[2] + " -> " + enemy.getHealth() + ")");
                }
            } finally {
                discardAll(made);
                discardAll(saplingsNear(level, spot, 1.5));
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * Seed of Renewal refuses a Thornweaver 1 essence short (no sapling, nothing spent, no cooldown); a full one plants
     * a sapling, and the sapling cut down drops no essence wisp (a summon must not feed the caster that made it).
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void thornweaver_seed_of_renewal_refuses_when_short_and_its_sapling_drops_no_essence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "seedno");
        SkirmishGameTests.openGround(helper, 2, 2, 13, 13);
        Mob poor = weaver(helper, me, 2, 2, 9);
        Mob rich = weaver(helper, me, 13, 2, 30);
        List<Entity> all = live(poor, rich);
        if (all.size() != 2) {
            discardAll(all);
            helper.fail("could not spawn the two Thornweavers");
            return;
        }
        BlockPos poorSpot = ground(helper, 4, 8), richSpot = ground(helper, 11, 8);
        List<Entity> made = new ArrayList<>();
        List<String> early = new ArrayList<>();
        Vec3[] deathAt = { null };
        helper.runAfterDelay(3, () -> {
            try {
                SeedOfRenewal a = ability(poor, SeedOfRenewal.class), b = ability(rich, SeedOfRenewal.class);
                if (Essence.get(poor) != 9 || Essence.get(rich) != 30)
                    early.add("the Thornweavers hold " + Essence.get(poor) + " and " + Essence.get(rich) + " essence, the test gave them 9 and 30");
                if (a == null || b == null) {
                    early.add("a Thornweaver has no Seed of Renewal");
                    return;
                }
                a.use(level, (Unit) poor, poorSpot);
                List<RenewalSaplingUnit> none = saplingsNear(level, poorSpot, 3);
                made.addAll(none);
                if (!none.isEmpty())
                    early.add("a Thornweaver 1 essence short planted a sapling");
                if (Essence.get(poor) != 9)
                    early.add("a Thornweaver 1 essence short spent essence: " + Essence.get(poor) + " left of 9");
                if (((Unit) poor).getCooldown(a) > 0)
                    early.add("a Thornweaver 1 essence short got a cooldown");
                b.use(level, (Unit) rich, richSpot);
                List<RenewalSaplingUnit> planted = saplingsNear(level, richSpot, 1.5);
                made.addAll(planted);
                if (planted.size() != 1)
                    early.add("the cast planted " + planted.size() + " sapling units, expected 1 (essence left " + Essence.get(rich)
                        + ", cooldown " + ((Unit) rich).getCooldown(b) + ", standing cell " + SeedOfRenewal.standingCell(level,
                        richSpot.getX(), richSpot.getZ(), richSpot.getY() + 1) + " for the spot " + richSpot + ")");
                else {
                    deathAt[0] = planted.get(0).position();
                    planted.get(0).kill();
                }
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        helper.runAfterDelay(15, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (problems.isEmpty() && deathAt[0] != null) {
                    long wisps = EssenceServerEvents.getWisps().stream()
                        .filter(w -> !w.isRemoved() && w.position().distanceTo(deathAt[0]) <= 3).count();
                    if (wisps > 0)
                        problems.add("the cut-down sapling left " + wisps + " essence wisps: summons must drop none");
                }
            } finally {
                if (deathAt[0] != null)
                    clearWispsNear(deathAt[0], 3);
                discardAll(made);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------- Sprout Guardian

    /**
     * Sprout Guardian: 20 essence, 60 s cooldown. A sapling treant (the Court's Sentinel Treant body) wakes on the
     * aimed ground for the caster's owner, marked summoned, still stands after 23.5 s and has withered by 27 s (its 25 s
     * plus the expiry pass's 1 s step). The guardian is frozen (no AI) so it cannot wander into another test's arena.
     */
    @GameTest(template = ARENA, timeoutTicks = 560, batch = BATCH)
    public static void thornweaver_sprout_guardian_fights_for_25_seconds_then_withers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "sprout");
        SkirmishGameTests.openGround(helper, 2, 2, 12, 12);
        Mob caster = weaver(helper, me, 2, 2, 30);
        if (caster == null) {
            helper.fail("could not spawn a Thornweaver");
            return;
        }
        BlockPos spot = ground(helper, 7, 7);
        List<Entity> made = new ArrayList<>();
        List<String> early = new ArrayList<>();
        boolean[] aliveLate = { false }, inWorldLate = { false };
        final int cast = 2;
        helper.runAfterDelay(cast, () -> {
            try {
                if (SproutGuardian.ESSENCE_COST != 20 || SproutGuardian.CD_SECONDS != 60 || SproutGuardian.LIFETIME_TICKS != 500)
                    early.add("lovish set 20 essence, 60 s, 25 s; Sprout Guardian has " + SproutGuardian.ESSENCE_COST + ", "
                        + SproutGuardian.CD_SECONDS + " s, " + SproutGuardian.LIFETIME_TICKS + " ticks");
                SproutGuardian sprout = ability(caster, SproutGuardian.class);
                if (Essence.get(caster) != 30)
                    early.add("the Thornweaver holds " + Essence.get(caster) + " essence, the test gave it 30");
                if (sprout == null) {
                    early.add("the Thornweaver has no Sprout Guardian");
                    return;
                }
                sprout.use(level, (Unit) caster, spot);
                List<LivingEntity> guardians = guardiansOf(level, caster, spot);
                made.addAll(guardians);
                for (LivingEntity g : guardians)
                    if (g instanceof Mob m)
                        m.setNoAi(true);
                if (guardians.size() != 1) {
                    early.add("the cast raised " + guardians.size() + " guardians by the spot, expected 1");
                } else {
                    LivingEntity g = guardians.get(0);
                    if (!(g instanceof SentinelTreantUnit st) || !me.equals(st.getOwnerName()))
                        early.add("the guardian is not a Sentinel Treant of the caster's owner: " + g);
                    if (!Essence.isSummoned(g))
                        early.add("the guardian is not marked summoned: its death would drop essence");
                    if (!AwakenThicket.isAwakened(g))
                        early.add("the guardian is not on the awakened-treant timer");
                    if (Math.abs(g.getY() - (spot.getY() + 1)) > 0.5)
                        early.add("the guardian stands at y " + g.getY() + ", not on the aimed ground (" + (spot.getY() + 1) + ")");
                    if (Math.hypot(g.getX() - (spot.getX() + 0.5), g.getZ() - (spot.getZ() + 0.5)) > 1)
                        early.add("the guardian woke at " + g.blockPosition() + ", not by the aimed spot " + spot);
                }
                if (Essence.get(caster) != 10)
                    early.add("the Thornweaver holds " + Essence.get(caster) + " essence after Sprout Guardian, expected 30 - 20 = 10");
                float cd = ((Unit) caster).getCooldown(sprout);
                if (Math.abs(cd - SproutGuardian.CD_SECONDS * 20) > 1)
                    early.add("Sprout Guardian set a " + cd + "-tick cooldown, expected " + SproutGuardian.CD_SECONDS * 20);
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        helper.runAfterDelay(cast + 470, () -> {
            aliveLate[0] = !made.isEmpty() && made.stream().allMatch(e -> !e.isRemoved() && e.isAlive());
            inWorldLate[0] = !made.isEmpty() && made.stream().allMatch(e -> level.getEntity(e.getUUID()) == e);   // not only on the spell's list
        });
        helper.runAfterDelay(cast + 540, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (problems.isEmpty()) {
                    if (!aliveLate[0])
                        problems.add("the guardian was gone before 23.5 s, expected 25 s");
                    else if (!inWorldLate[0])
                        problems.add("the guardian on the spell's list was not in the world at 23.5 s (not found by its UUID)");
                    if (made.stream().anyMatch(e -> !e.isRemoved()))
                        problems.add("the guardian still stood 27 s after it woke, expected it to wither at 25 s");
                }
            } finally {
                discardAll(made);
                discardAll(guardiansNear(level, spot, 3));
                caster.discard();
            }
            finish(helper, problems);
        });
    }

    /**
     * Sprout Guardian refuses a Thornweaver 1 essence short (no treant, nothing spent, no cooldown); a guardian that
     * is killed drops no essence wisp. The guardian is read from the spell's own list at the cast and must be in the
     * world (found by its UUID) 20 ticks into the test, before it is killed: the list alone can't pass the test.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void thornweaver_sprout_guardian_refuses_when_short_and_drops_no_essence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "sproutno");
        SkirmishGameTests.openGround(helper, 2, 2, 13, 13);
        Mob poor = weaver(helper, me, 2, 2, 19);
        Mob rich = weaver(helper, me, 13, 2, 30);
        List<Entity> all = live(poor, rich);
        if (all.size() != 2) {
            discardAll(all);
            helper.fail("could not spawn the two Thornweavers");
            return;
        }
        BlockPos poorSpot = ground(helper, 4, 8), richSpot = ground(helper, 11, 8);
        List<Entity> made = new ArrayList<>();
        List<String> early = new ArrayList<>();
        Vec3[] deathAt = { null };
        LivingEntity[] guardian = { null };
        final int inWorld = 20;
        helper.runAfterDelay(3, () -> {
            try {
                SproutGuardian a = ability(poor, SproutGuardian.class), b = ability(rich, SproutGuardian.class);
                if (Essence.get(poor) != 19 || Essence.get(rich) != 30)
                    early.add("the Thornweavers hold " + Essence.get(poor) + " and " + Essence.get(rich) + " essence, the test gave them 19 and 30");
                if (a == null || b == null) {
                    early.add("a Thornweaver has no Sprout Guardian");
                    return;
                }
                a.use(level, (Unit) poor, poorSpot);
                List<LivingEntity> none = guardiansOf(level, poor, poorSpot);
                made.addAll(none);
                if (!none.isEmpty())
                    early.add("a Thornweaver 1 essence short raised a guardian");
                if (Essence.get(poor) != 19)
                    early.add("a Thornweaver 1 essence short spent essence: " + Essence.get(poor) + " left of 19");
                if (((Unit) poor).getCooldown(a) > 0)
                    early.add("a Thornweaver 1 essence short got a cooldown");
                b.use(level, (Unit) rich, richSpot);
                List<LivingEntity> raised = guardiansOf(level, rich, richSpot);
                made.addAll(raised);
                if (raised.size() == 1 && Math.hypot(raised.get(0).getX() - (richSpot.getX() + 0.5), raised.get(0).getZ() - (richSpot.getZ() + 0.5)) > 1)
                    early.add("the guardian woke at " + raised.get(0).blockPosition() + ", not by the aimed spot " + richSpot);
                else if (raised.size() != 1)
                    early.add("the cast raised " + raised.size() + " guardians, expected 1 (essence left " + Essence.get(rich)
                        + ", cooldown " + ((Unit) rich).getCooldown(b) + ", standing cell " + SeedOfRenewal.standingCell(level,
                        richSpot.getX(), richSpot.getZ(), richSpot.getY() + 1) + " for the spot " + richSpot + ")");
                else {
                    guardian[0] = raised.get(0);
                    if (guardian[0] instanceof Mob m)
                        m.setNoAi(true);
                }
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        helper.runAfterDelay(inWorld, () -> {
            LivingEntity g = guardian[0];
            if (g == null)
                return;
            if (g.isRemoved() || level.getEntity(g.getUUID()) != g) {
                early.add("the guardian on the spell's list is not in the world " + (inWorld - 3) + " ticks after the cast (removed "
                    + g.isRemoved() + ", found by UUID " + (level.getEntity(g.getUUID()) != null) + ", at " + g.blockPosition() + ")");
                return;
            }
            deathAt[0] = g.position();
            g.kill();
        });
        helper.runAfterDelay(inWorld + 12, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (problems.isEmpty() && deathAt[0] != null) {
                    long wisps = EssenceServerEvents.getWisps().stream()
                        .filter(w -> !w.isRemoved() && w.position().distanceTo(deathAt[0]) <= 3).count();
                    if (wisps > 0)
                        problems.add("the killed guardian left " + wisps + " essence wisps: summons must drop none");
                }
            } finally {
                if (deathAt[0] != null)
                    clearWispsNear(deathAt[0], 3);
                discardAll(made);
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ---------------------------------------------------------------------------------------------- autocast

    /**
     * The autocast never orders a spell it would refuse (PR 362 review round 1): an order resets the unit's behaviour,
     * which clears hold position, and a refused one also shows the owner a refusal every second. A Thornweaver on hold
     * with 30 essence, autocast on, beside three enemy flyers (Roots holds only ground units) and a hurt friendly
     * sapling (a pulse never heals a sapling): a full AutoCaster look gives no order, so it is still on hold, still
     * holds 30 essence, and no spell is on cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void thornweaver_autocast_skips_targets_its_spells_refuse_and_keeps_hold_position(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "auto"), foe = owner(helper, "autofoe");
        SkirmishGameTests.openGround(helper, 2, 2, 12, 12);
        Mob caster = weaver(helper, me, 3, 3, 30);
        Mob sapling = still(helper, EntityRegistrar.RENEWAL_SAPLING_UNIT.get(), me, 5, 3);
        List<Entity> all = live(caster, sapling);
        for (int i = 0; i < 3; i++)
            all.add(still(helper, EntityRegistrar.OWL_WATCHER_UNIT.get(), foe, 8 + i, 8));
        if (all.size() != 5 || all.contains(null)) {
            discardAll(all.stream().filter(Objects::nonNull).toList());
            helper.fail("could not spawn the Thornweaver, the sapling and the three owls");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Unit u = (Unit) caster;
                for (int i = 2; i < 5; i++)
                    if (!(all.get(i) instanceof Unit owl) || !owl.isFlyingUnit())
                        problems.add("the Owl Watcher is not a flying unit: the test lost its point");
                sapling.setHealth(sapling.getMaxHealth() * 0.5f);
                // a SupportUnit's brain kites it, and the AutoCaster skips a unit with a move target: stand it idle first
                Unit.fullResetBehaviours(u);
                u.setHoldPosition(true);
                boolean cast = AutoCaster.look(level, caster, u);
                if (cast)
                    problems.add("the AutoCaster cast a spell with only flyers and a sapling around");
                if (!u.getHoldPosition())
                    problems.add("an autocast order cleared the Thornweaver's hold position");
                if (Essence.get(caster) != 30)
                    problems.add("the autocast spent essence: " + Essence.get(caster) + " left of 30");
                for (Ability a : u.getAbilities().get())
                    if (u.getCooldown(a) > 0)
                        problems.add(a.getClass().getSimpleName() + " went on cooldown from the autocast");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // --------------------------------------------------------------------------------------------------- bots

    /**
     * Bots build and use the Thornweaver (lovish, Oct 29: "bots build and use all 7 casters"). It is in the Verdant
     * kit's Tier 1 army, its three spells autocast by default (the AutoCaster casts them for a bot as for a player), and in
     * a bot's fight pass a Thornweaver with essence to spare (30) empowers the dearest friend in reach - the Elder Treant,
     * not the Vindicator - while one holding 20 keeps it for its spells.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void thornweaver_verdant_bot_trains_it_and_empowers_with_spare_essence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "bot");
        Mob full = weaver(helper, me, 3, 3, 30);
        Mob low = weaver(helper, me, 13, 13, 20);
        Mob cheap = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 5, 3);     // 2 blocks from the full one
        Mob dear = still(helper, EntityRegistrar.ELDER_TREANT_UNIT.get(), me, 3, 6);    // 3 blocks from it
        Mob lowFriend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 11, 13);   // 2 blocks from the low one
        List<Entity> all = live(full, low, cheap, dear, lowFriend);
        if (all.size() != 5) {
            discardAll(all);
            helper.fail("could not spawn the two Thornweavers and their friends");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                BotPlayer.Kit kit = BotPlayer.kitFor(Factions.VERDANT_COURT);
                if (kit == null || !kit.army().contains(ProductionItems.THORNWEAVER) || kit.t2Army().contains(ProductionItems.THORNWEAVER))
                    problems.add("the Verdant bot does not train the Thornweaver from its Tier 1 army list (it is a T1 unit now)");
                for (Ability a : Arrays.asList(ability(full, EntanglingRoots.class), ability(full, SeedOfRenewal.class), ability(full, SproutGuardian.class)))
                    if (a == null || !AutocastState.isOn((Unit) full, a))
                        problems.add((a == null ? "a spell" : a.getClass().getSimpleName()) + " is not autocast by default");
                BotPlayer brain = new BotPlayer(me, Factions.VERDANT_COURT, BotPlayer.Difficulty.MEDIUM, helper.absolutePos(BlockPos.ZERO));
                List<LivingEntity> mine = new ArrayList<>();
                for (Entity e : all)
                    mine.add((LivingEntity) e);
                brain.castFightAbilities(level, mine);
                if (VeterancyServerEvents.getRank(dear) != 1)
                    problems.add("the bot's full Thornweaver did not empower the Elder Treant (rank " + VeterancyServerEvents.getRank(dear) + ")");
                if (VeterancyServerEvents.getRank(cheap) != 0)
                    problems.add("the bot empowered the Vindicator over the dearer Elder Treant");
                if (Essence.get(full) != 15)
                    problems.add("the full Thornweaver holds " + Essence.get(full) + " essence after the bot's Empower, expected 15");
                Empower emp = ability(full, Empower.class);
                if (emp == null || ((Unit) full).getCooldown(emp) <= 0)
                    problems.add("the bot's Empower set no cooldown");
                if (Essence.get(low) != 20 || VeterancyServerEvents.getRank(lowFriend) != 0)
                    problems.add("a bot Thornweaver holding 20 essence spent it on Empower (" + Essence.get(low) + " left, friend rank "
                        + VeterancyServerEvents.getRank(lowFriend) + "): it keeps that for its spells");
            } catch (RuntimeException ex) {
                problems.add("the bot pass threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * A bot's Thornweaver goes with the army (PR 364 round 3 review: the bot's army list holds only attackers, so a
     * caster idled at its lab with 0 essence). One standing 14 blocks from the bot's army is lagging: the bot's
     * Verdant habits - the "healers follow the army" block, which now takes every EssenceCarrier - order it to the
     * army's centre. The block is package-private bot code, so the test calls it as think() does, by reflection.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void thornweaver_bot_orders_a_lagging_thornweaver_to_the_army(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "follow");
        Mob caster = weaver(helper, me, 2, 2, 0);
        Mob a = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 12, 12);
        Mob b = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 13, 12);
        List<Entity> all = live(caster, a, b);
        if (all.size() != 3) {
            discardAll(all);
            helper.fail("could not spawn the Thornweaver and the bot's army");
            return;
        }
        List<BlockPos> moves = new ArrayList<>();
        double cx = (a.getX() + b.getX()) / 2, cz = (a.getZ() + b.getZ()) / 2;
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                BotPlayer brain = new BotPlayer(me, Factions.VERDANT_COURT, BotPlayer.Difficulty.MEDIUM, null);
                brain.orderListener = (action, target) -> {
                    if (action == UnitAction.MOVE)
                        moves.add(target);
                };
                List<LivingEntity> army = new ArrayList<>(List.of((LivingEntity) a, (LivingEntity) b));
                // the Thornweaver is the only unit here the block may send: a move to the centre is its order
                List<LivingEntity> mine = new ArrayList<>(List.of((LivingEntity) caster, (LivingEntity) a, (LivingEntity) b));
                java.lang.reflect.Method habits = BotPlayer.class.getDeclaredMethod("verdantHabits", ServerLevel.class,
                    List.class, List.class, List.class, List.class, long.class, long.class);
                habits.setAccessible(true);
                habits.invoke(brain, level, mine, army, new ArrayList<LivingEntity>(), new ArrayList<BuildingPlacement>(), level.getGameTime(), 0L);
                if (moves.isEmpty())
                    problems.add("the bot gave no move order: its Thornweaver 14 blocks from the army stays where it is");
                else if (moves.stream().noneMatch(t -> Math.hypot(t.getX() + 0.5 - cx, t.getZ() + 0.5 - cz) <= 2))
                    problems.add("the bot's move orders " + moves + " do not go to the army's centre (" + cx + ", " + cz + ")");
            } catch (ReflectiveOperationException | RuntimeException ex) {
                problems.add("the bot's Verdant habits could not be run: " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }
}
