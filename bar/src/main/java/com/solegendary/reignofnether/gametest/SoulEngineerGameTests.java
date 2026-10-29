package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoCaster;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.GhostFurnace;
import com.solegendary.reignofnether.ability.abilities.PatchKit;
import com.solegendary.reignofnether.ability.abilities.SoulBoiler;
import com.solegendary.reignofnether.brass.Machine;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.Summons;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit;
import com.solegendary.reignofnether.unit.units.brass.GhostFurnaceUnit;
import com.solegendary.reignofnether.unit.units.brass.SoulEngineerProd;
import com.solegendary.reignofnether.unit.units.brass.SoulEngineerUnit;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The Brassworks essence caster, the Soul Engineer (design/essence_casters.md; lovish approved draft 2 on Oct 29: "one
 * caster per race"; the numbers are the revision-scaled ones of the design's last section). Written before the code:
 * <ul>
 *   <li>trained at the Assembly Works (Brassworks T2 lab) once Tier 2 is researched, for a cost; an essence carrier with
 *       three spells and the shared Empower, each spell gated for the bots on its essence ({@code autocastEssence});</li>
 *   <li>Patch Kit: 5 essence, 12 s - a friendly machine or building within 8 is mended by 120 HP; refused on a target at
 *       full health (and on an enemy's, out of reach, or short); autocast at a hurt building (AutoRule.BUILDING_HURT);</li>
 *   <li>Ghost Furnace: 10 essence, 25 s - a 150 HP summoned spirit-steam furnace for 20 s that scalds every enemy ground
 *       unit within 5 for 4 a second; its death drops no essence; siege shells hit it at the defence rate (54 for a
 *       lobber's 4); not the Deploy Turret (no gun, no heat);</li>
 *   <li>Soul Boiler: 20 essence, 60 s - a friendly building within 24 blocks of the caster builds and produces 50% faster
 *       for 20 s; refused on an idle, far, enemy or already boiling building; the unit casts it by itself at a busy one;</li>
 *   <li>every refusal spends nothing and sets no cooldown, and an order during a cooldown is refused.</li>
 * </ul>
 * Every unit stands still (no AI) inside the test's own arena under owners unique to the arena. Early callbacks only
 * record, never throw; the last callback judges and always cleans up (units, furnaces, buildings, wisps) in a finally,
 * as EssenceGameTests do.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SoulEngineerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "caster_brassworks";

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_soulengineer_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A unit that stands where it is put: no AI, so it never walks off its spot. */
    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null)
            m.setNoAi(true);
        return m;
    }

    /** A Soul Engineer holding {@code essence}. */
    private static Mob engineer(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = still(helper, EntityRegistrar.SOUL_ENGINEER_UNIT.get(), owner, x, z);
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

    private static List<GhostFurnaceUnit> furnacesNear(ServerLevel level, Vec3 at, double r) {
        return level.getEntitiesOfClass(GhostFurnaceUnit.class, new AABB(BlockPos.containing(at)).inflate(r), e -> !e.isRemoved());
    }

    private static List<Entity> wispsNear(Vec3 at, double r) {
        List<Entity> found = new ArrayList<>();
        for (Entity w : EssenceServerEvents.getWisps())
            if (!w.isRemoved() && w.position().distanceTo(at) <= r)
                found.add(w);
        return found;
    }

    /** Puts the building's blocks in the world and counts it built (FactoryGuardGameTests' site), owned by {@code owner}. */
    private static BuildingPlacement site(GameTestHelper helper, com.solegendary.reignofnether.building.Building building,
                                          String owner, int x, int z, boolean built) {
        ServerLevel level = helper.getLevel();
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(building, level,
            helper.absolutePos(new BlockPos(x, 1, z)), Rotation.NONE, owner, false);
        if (bp == null)
            return null;
        if (built) {
            for (var blk : bp.getBlocks())
                if (!blk.getBlockState().isAir())
                    level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
            bp.initPlacedBlocks();
        }
        bp.isBuilt = built;
        BuildingServerEvents.getBuildings().add(bp);
        return bp;
    }

    private static void unsite(ServerLevel level, BuildingPlacement bp) {
        if (bp == null)
            return;
        if (bp instanceof ProductionPlacement pp)
            pp.productionQueue.clear();
        BuildingServerEvents.getBuildings().remove(bp);
        for (var blk : bp.getBlocks())
            level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
    }

    /**
     * Takes the building's blocks away, top first, until its health is at most {@code hp} - straight out of the world, so
     * no hit's explosion roll goes off beside the test's units. True when it got there with the building still standing.
     */
    private static boolean hurtTo(ServerLevel level, BuildingPlacement bp, int hp) {
        List<com.solegendary.reignofnether.building.BuildingBlock> blocks = new ArrayList<>(bp.getBlocks());
        blocks.sort(Comparator.comparingInt((com.solegendary.reignofnether.building.BuildingBlock b) -> b.getBlockPos().getY()).reversed());
        int n = 0;
        for (var blk : blocks) {
            if (bp.getHealth() <= hp)
                break;
            if (blk.getBlockState().isAir() || !blk.isPlaced(level))
                continue;
            level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
            if (++n % 10 == 0)
                bp.initPlacedBlocks();
        }
        bp.initPlacedBlocks();
        return bp.getHealth() <= hp && bp.getHealth() > 0;
    }

    /** One placed block's share of the building's health. */
    private static double healthPerBlock(BuildingPlacement bp) {
        return bp.getHealthPerBlock() / 2 / bp.minBlocksPercent;
    }

    /** Stands the caster {@code gap} blocks east of the building's east edge, on the building's middle row. */
    private static void beside(Mob caster, BuildingPlacement bp, double gap) {
        caster.moveTo(bp.maxCorner.getX() + 1 + gap, caster.getY(), bp.centrePos.getZ() + 0.5, 0, 0);
    }

    // ------------------------------------------------------------------------------------------- the unit

    /**
     * The Assembly Works trains the Soul Engineer (its own slot), for a real cost (metal, energy, population), behind the
     * Tier 2 gate like the rest of the lab; the item makes a Soul Engineer, a Brassworks unit whose cost is the item's.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_trains_at_the_assembly_works_for_a_cost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        if (!Buildings.ASSEMBLY_WORKS.productions.get().contains(ProductionItems.SOUL_ENGINEER))
            problems.add("the Assembly Works does not offer the Soul Engineer");
        ResourceCost cost = ResourceCosts.SOUL_ENGINEER;
        if (cost.metal() <= 0 || cost.energy() <= 0 || cost.population <= 0 || cost.ticks <= 0)
            problems.add("the Soul Engineer costs " + cost.metal() + " metal, " + cost.energy() + " energy, " + cost.population
                + " population, " + cost.ticks + " ticks: every part should be above 0");
        if (EntityRegistrar.getEntityType(SoulEngineerProd.itemName) != EntityRegistrar.SOUL_ENGINEER_UNIT.get())
            problems.add("the Soul Engineer item does not make a Soul Engineer");
        if (ProductionItems.getProductionItem(EntityRegistrar.SOUL_ENGINEER_UNIT.get()) != ProductionItems.SOUL_ENGINEER)
            problems.add("the Soul Engineer type does not resolve to its production item");

        String me = owner(helper, "train");
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.ASSEMBLY_WORKS, level,
            new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, me, false);
        Resources pool = new Resources(me, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        Mob made = null;
        try {
            if (!(bp instanceof ProductionPlacement pp)) {
                problems.add("no Assembly Works placement: " + bp);
            } else {
                pp.isBuilt = true;
                if (ProductionItems.SOUL_ENGINEER.getProduceErrorMsg(pp) == null)
                    problems.add("the Soul Engineer was offered without Tier 2");
                ResearchServerEvents.addResearch(me, ProductionItems.RESEARCH_TIER_2);
                String err = ProductionItems.SOUL_ENGINEER.getProduceErrorMsg(pp);
                if (err != null)
                    problems.add("with Tier 2 the Soul Engineer is refused: " + err);
                else if (!pp.startProductionItem(ProductionItems.SOUL_ENGINEER))
                    problems.add("the server refused to queue the Soul Engineer");
                pp.productionQueue.clear();
            }
            made = EntityRegistrar.SOUL_ENGINEER_UNIT.get().create(level);
            if (!(made instanceof Unit u))
                problems.add("a Soul Engineer is not a unit: " + made);
            else {
                if (u.getCost() != ResourceCosts.SOUL_ENGINEER)
                    problems.add("a Soul Engineer's cost is not ResourceCosts.SOUL_ENGINEER");
                if (!Factions.BRASSWORKS.equals(Factions.getFaction(u)))
                    problems.add("the Soul Engineer is not Brassworks: " + Factions.getFaction(u));
                if (Math.abs(made.getMaxHealth() - SoulEngineerUnit.maxHealth) > 0.01f || made.getMaxHealth() <= 0)
                    problems.add("a Soul Engineer has " + made.getMaxHealth() + " health, its class says " + SoulEngineerUnit.maxHealth);
                if (made instanceof Machine)
                    problems.add("the Soul Engineer is a machine: it is a person (crews are people)");
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
     * The Soul Engineer is an essence carrier: it pulls in a wisp 5 blocks away and holds its essence. It has its three
     * spells and the shared Empower, and the bots can use the spells: each one's autocastEssence is its cost, Patch Kit
     * autocasts at hurt buildings, Ghost Furnace on a crowd, Soul Boiler by the unit's own tick.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = BATCH)
    public static void soul_engineer_gathers_essence_and_has_its_four_spells_for_bots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "gather");
        Mob caster = engineer(helper, me, 3, 3, 0);
        if (caster == null) {
            helper.fail("could not spawn a Soul Engineer");
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
                    problems.add("the Soul Engineer is not an essence carrier");
                else if (Essence.get(caster) != 4)
                    problems.add("the Soul Engineer holds " + Essence.get(caster) + " essence after 5 s beside a 4-essence wisp, expected 4");
                if (wisp != null && !wisp.isRemoved())
                    problems.add("the wisp 5 blocks from the Soul Engineer was never gathered");
                PatchKit kit = ability(caster, PatchKit.class);
                GhostFurnace furnace = ability(caster, GhostFurnace.class);
                SoulBoiler boiler = ability(caster, SoulBoiler.class);
                List<String> missing = new ArrayList<>();
                if (kit == null) missing.add("Patch Kit");
                if (furnace == null) missing.add("Ghost Furnace");
                if (boiler == null) missing.add("Soul Boiler");
                if (ability(caster, Empower.class) == null) missing.add("Empower");
                if (!missing.isEmpty())
                    problems.add("the Soul Engineer lacks " + String.join(", ", missing));
                if (PatchKit.ESSENCE_COST != 5 || PatchKit.CD_SECONDS != 12 || PatchKit.REPAIR_HP != 120)
                    problems.add("lovish set Patch Kit at 5 essence, 12 s, 120 HP; it has " + PatchKit.ESSENCE_COST + ", "
                        + PatchKit.CD_SECONDS + " s, " + PatchKit.REPAIR_HP);
                if (GhostFurnace.ESSENCE_COST != 10 || GhostFurnace.CD_SECONDS != 25 || GhostFurnaceUnit.LIFETIME_TICKS != 400)
                    problems.add("lovish set Ghost Furnace at 10 essence, 25 s, 20 s alive; it has " + GhostFurnace.ESSENCE_COST + ", "
                        + GhostFurnace.CD_SECONDS + " s, " + GhostFurnaceUnit.LIFETIME_TICKS + " ticks");
                if (SoulBoiler.ESSENCE_COST != 20 || SoulBoiler.CD_SECONDS != 60 || SoulBoiler.RANGE != 24
                        || SoulBoiler.BOIL_TICKS != 400 || Math.abs(SoulBoiler.SPEED_MULT - 1.5f) > 1e-6)
                    problems.add("lovish set Soul Boiler at 20 essence, 60 s, 24 blocks, +50% for 20 s; it has " + SoulBoiler.ESSENCE_COST
                        + ", " + SoulBoiler.CD_SECONDS + " s, " + SoulBoiler.RANGE + ", x" + SoulBoiler.SPEED_MULT + " for " + SoulBoiler.BOIL_TICKS + " ticks");
                if (kit != null && (kit.autocastEssence != PatchKit.ESSENCE_COST || kit.getAutoRule() != AutoRule.BUILDING_HURT))
                    problems.add("Patch Kit's bot gate is " + kit.autocastEssence + " essence, rule " + kit.getAutoRule()
                        + "; expected 5 and BUILDING_HURT");
                if (furnace != null && (furnace.autocastEssence != GhostFurnace.ESSENCE_COST || furnace.getAutoRule() != AutoRule.CROWD))
                    problems.add("Ghost Furnace's bot gate is " + furnace.autocastEssence + " essence, rule " + furnace.getAutoRule()
                        + "; expected 10 and CROWD");
                if (boiler != null && (boiler.autocastEssence != SoulBoiler.ESSENCE_COST || !boiler.autocastByUnit || !boiler.canAutocast()))
                    problems.add("Soul Boiler's bot gate is " + boiler.autocastEssence + " essence, by unit " + boiler.autocastByUnit
                        + ", autocastable " + (boiler != null && boiler.canAutocast()) + "; expected 20, true, true");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** The shared Empower works from the Soul Engineer: 15 essence, a friend 3 blocks away gains a rank. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_empowers_a_friend_for_15_essence(GameTestHelper helper) {
        String me = owner(helper, "emp");
        Mob caster = engineer(helper, me, 4, 4, 15);
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 7, 4);
        List<Entity> all = live(caster, friend);
        if (caster == null || friend == null) {
            discardAll(all);
            helper.fail("could not spawn the Soul Engineer and its friend");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Empower emp = ability(caster, Empower.class);
                if (emp == null)
                    problems.add("the Soul Engineer has no Empower");
                else {
                    emp.use(helper.getLevel(), (Unit) caster, friend);
                    if (VeterancyServerEvents.getRank(friend) != 1)
                        problems.add("the friend is rank " + VeterancyServerEvents.getRank(friend) + " after Empower, expected 1");
                    if (Essence.get(caster) != 0)
                        problems.add("the Soul Engineer holds " + Essence.get(caster) + " essence after Empower, expected 0");
                    if (((Unit) caster).getCooldown(emp) <= 0)
                        problems.add("Empower set no cooldown");
                }
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------- Patch Kit

    /**
     * Patch Kit on a hurt friendly building 3 blocks away: 120 health back at once (within one block's share), 5 essence
     * spent, a 12 s cooldown; the same order again during the cooldown spends nothing and mends nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_patch_kit_mends_a_building_by_120(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "patchb");
        Mob caster = engineer(helper, me, 1, 1, 30);
        BuildingPlacement bp = site(helper, Buildings.MACHINE_SHOP, me, 4, 4, true);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (caster == null || bp == null) {
                    problems.add("could not set up the Soul Engineer and the Machine Shop");
                    return;
                }
                beside(caster, bp, 3);
                int max = bp.getMaxHealth();
                if (!hurtTo(level, bp, Math.max(1, max - 135))) {
                    problems.add("setup: could not bring the Machine Shop down to " + (max - 135) + " of " + max + " (" + bp.getHealth() + ")");
                    return;
                }
                PatchKit kit = ability(caster, PatchKit.class);
                if (kit == null) {
                    problems.add("the Soul Engineer has no Patch Kit");
                    return;
                }
                int before = bp.getHealth();
                kit.use(level, (Unit) caster, bp.centrePos);
                int gained = bp.getHealth() - before;
                double slack = healthPerBlock(bp) + 1;
                if (gained < PatchKit.REPAIR_HP - 0.5 || gained > PatchKit.REPAIR_HP + slack)
                    problems.add("Patch Kit gave the building " + gained + " health (" + before + " -> " + bp.getHealth() + " of " + max
                        + "), expected 120 (up to " + Math.round(slack) + " more for the last block)");
                if (Essence.get(caster) != 25)
                    problems.add("the Soul Engineer holds " + Essence.get(caster) + " essence after Patch Kit, expected 30 - 5 = 25");
                float cd = ((Unit) caster).getCooldown(kit);
                if (Math.abs(cd - PatchKit.CD_SECONDS * 20) > 1)
                    problems.add("Patch Kit set a " + cd + "-tick cooldown, expected " + PatchKit.CD_SECONDS * 20);
                int mid = bp.getHealth();
                new UnitActionItem(me, UnitAction.PATCH_KIT, -1, new int[] { caster.getId() }, bp.centrePos, BlockPos.ZERO).action(level);
                if (Essence.get(caster) != 25 || bp.getHealth() != mid)
                    problems.add("an order during the cooldown spent essence or mended (" + Essence.get(caster) + " essence, health "
                        + mid + " -> " + bp.getHealth() + ")");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                unsite(level, bp);
                discardAll(live(caster));
                finish(helper, problems);
            }
        });
    }

    /** Patch Kit on a friendly Steam Juggernaut (a machine) at 50 of 200: 120 HP back, 5 essence spent, a cooldown. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_patch_kit_mends_a_machine_by_120(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "patchm");
        Mob caster = engineer(helper, me, 4, 4, 30);
        Mob machine = still(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), me, 8, 4);
        List<Entity> all = live(caster, machine);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (all.size() != 2) {
                    problems.add("could not spawn the Soul Engineer and the Juggernaut");
                    return;
                }
                PatchKit kit = ability(caster, PatchKit.class);
                if (kit == null) {
                    problems.add("the Soul Engineer has no Patch Kit");
                    return;
                }
                machine.setHealth(50);
                kit.use(level, (Unit) caster, machine);
                if (Math.abs(machine.getHealth() - 170) > 0.01f)
                    problems.add("the Juggernaut is at " + machine.getHealth() + " after Patch Kit from 50, expected 170");
                if (Essence.get(caster) != 25)
                    problems.add("the Soul Engineer holds " + Essence.get(caster) + " essence after Patch Kit, expected 25");
                if (((Unit) caster).getCooldown(kit) <= 0)
                    problems.add("Patch Kit on a machine set no cooldown");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
                finish(helper, problems);
            }
        });
    }

    /**
     * Patch Kit refuses - no essence, no cooldown, nothing mended - on a friendly building and a friendly machine at full
     * health, on an enemy's hurt building and machine, on a person (a crew), on a hurt building 20 blocks away, and with
     * 4 essence (one short) at a hurt building in reach.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_patch_kit_refuses_full_health_enemies_people_far_and_short(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "patchno"), foe = owner(helper, "patchnofoe");
        Mob rich = engineer(helper, me, 1, 1, 30);
        Mob poor = engineer(helper, me, 1, 2, 4);
        Mob mine = still(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), me, 14, 2);
        Mob theirs = still(helper, EntityRegistrar.STEAM_JUGGERNAUT_UNIT.get(), foe, 14, 6);
        Mob crew = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 14, 10);
        List<Entity> all = live(rich, poor, mine, theirs, crew);
        BuildingPlacement bp = site(helper, Buildings.MACHINE_SHOP, me, 3, 4, true);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (all.size() != 5 || bp == null) {
                    problems.add("could not set up the casters, units and building");
                    return;
                }
                PatchKit kitR = ability(rich, PatchKit.class), kitP = ability(poor, PatchKit.class);
                if (kitR == null || kitP == null) {
                    problems.add("a Soul Engineer has no Patch Kit");
                    return;
                }
                beside(rich, bp, 2);
                beside(poor, bp, 2);
                rich.moveTo(rich.getX(), rich.getY(), rich.getZ() + 1, 0, 0);
                // full health: the building, then a machine beside the caster
                if (bp.getHealth() < bp.getMaxHealth()) {
                    problems.add("setup: the Machine Shop stands at " + bp.getHealth() + " of " + bp.getMaxHealth() + ", not full");
                    return;
                }
                kitR.use(level, (Unit) rich, bp.centrePos);
                mine.moveTo(rich.getX() + 2, rich.getY(), rich.getZ(), 0, 0);
                kitR.use(level, (Unit) rich, mine);
                if (Essence.get(rich) != 30 || ((Unit) rich).getCooldown(kitR) > 0)
                    problems.add("Patch Kit at full health spent essence or set a cooldown (" + Essence.get(rich) + " essence, cooldown "
                        + ((Unit) rich).getCooldown(kitR) + ")");
                // a person, at 10 of its health: a crew is no machine
                crew.moveTo(rich.getX() + 1, rich.getY(), rich.getZ() + 2, 0, 0);
                crew.setHealth(10);
                kitR.use(level, (Unit) rich, crew);
                if (Essence.get(rich) != 30 || crew.getHealth() > 10.01f)
                    problems.add("Patch Kit mended a person (" + crew.getHealth() + " HP, " + Essence.get(rich) + " essence)");
                // an enemy's hurt machine
                theirs.moveTo(rich.getX() + 2, rich.getY(), rich.getZ() - 2, 0, 0);
                theirs.setHealth(50);
                kitR.use(level, (Unit) rich, theirs);
                if (Essence.get(rich) != 30 || theirs.getHealth() > 50.01f)
                    problems.add("Patch Kit mended an enemy's machine (" + theirs.getHealth() + " HP, " + Essence.get(rich) + " essence)");
                // short of essence at a hurt friendly building in reach
                if (!hurtTo(level, bp, Math.max(1, bp.getMaxHealth() / 2))) {
                    problems.add("setup: could not hurt the Machine Shop");
                    return;
                }
                int hurt = bp.getHealth();
                kitP.use(level, (Unit) poor, bp.centrePos);
                if (Essence.get(poor) != 4 || ((Unit) poor).getCooldown(kitP) > 0 || bp.getHealth() != hurt)
                    problems.add("a Soul Engineer 1 essence short spent, cooled down or mended (" + Essence.get(poor) + " essence, health "
                        + hurt + " -> " + bp.getHealth() + ")");
                // the same hurt building from 20 blocks
                beside(rich, bp, 20);
                kitR.use(level, (Unit) rich, bp.centrePos);
                if (Essence.get(rich) != 30 || bp.getHealth() != hurt)
                    problems.add("Patch Kit mended a building 20 blocks away (" + Essence.get(rich) + " essence, health " + hurt + " -> " + bp.getHealth() + ")");
                // the same hurt building, now the enemy's, in reach
                beside(rich, bp, 2);
                bp.ownerName = foe;
                kitR.use(level, (Unit) rich, bp.centrePos);
                if (Essence.get(rich) != 30 || bp.getHealth() != hurt)
                    problems.add("Patch Kit mended an enemy's building (" + Essence.get(rich) + " essence, health " + hurt + " -> " + bp.getHealth() + ")");
                if (((Unit) rich).getCooldown(kitR) > 0)
                    problems.add("a refused Patch Kit set a cooldown");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                unsite(level, bp);
                discardAll(all);
                finish(helper, problems);
            }
        });
    }

    /**
     * The bots' Patch Kit: the AutoCaster casts it at a friendly building under 70% health in reach once the Soul
     * Engineer holds 5 essence (with 4 it orders nothing), and the building gains health.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_patch_kit_autocasts_at_a_hurt_building(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "patchauto");
        Mob caster = engineer(helper, me, 1, 1, 4);
        BuildingPlacement bp = site(helper, Buildings.MACHINE_SHOP, me, 4, 4, true);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (caster == null || bp == null) {
                    problems.add("could not set up the Soul Engineer and the Machine Shop");
                    return;
                }
                beside(caster, bp, 3);
                if (!hurtTo(level, bp, (int) (bp.getMaxHealth() * 0.4))) {
                    problems.add("setup: could not bring the Machine Shop under 70%");
                    return;
                }
                int hurt = bp.getHealth();
                if (AutoCaster.look(level, caster, (Unit) caster) || bp.getHealth() != hurt || Essence.get(caster) != 4)
                    problems.add("with 4 of 5 essence the AutoCaster still cast (health " + hurt + " -> " + bp.getHealth() + ")");
                Essence.add(caster, 1);
                if (!AutoCaster.look(level, caster, (Unit) caster))
                    problems.add("with 5 essence and a building at " + hurt + " of " + bp.getMaxHealth() + " in reach the AutoCaster did not cast");
                if (bp.getHealth() <= hurt)
                    problems.add("the autocast Patch Kit mended nothing (" + hurt + " -> " + bp.getHealth() + ")");
                if (Essence.get(caster) != 0)
                    problems.add("the autocast left " + Essence.get(caster) + " essence, expected 0");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                unsite(level, bp);
                discardAll(live(caster));
                finish(helper, problems);
            }
        });
    }

    // ------------------------------------------------------------------------------------------- Ghost Furnace

    /**
     * Ghost Furnace: 10 essence, 25 s. A 150 HP furnace stands at the aimed spot, marked summoned, not a Deploy Turret and
     * not a machine; once a second it scalds the enemy 3 blocks off for 4 while an enemy 7 blocks off and a friend 2
     * blocks off are untouched. An order during the cooldown spends nothing. Killed, it drops no essence.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void soul_engineer_ghost_furnace_scalds_enemies_within_5_and_is_a_summon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "furnace"), foe = owner(helper, "furnacefoe");
        Mob caster = engineer(helper, me, 3, 3, 20);
        Mob near = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 11, 8);    // 3 blocks east of the spot
        Mob far = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 8, 15);     // 7 blocks south
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 6, 8);    // 2 blocks west
        List<Entity> all = live(caster, near, far, friend);
        BlockPos spot = ground(helper, 8, 8);
        Vec3 spotTop = Vec3.atBottomCenterOf(spot.above());
        List<String> early = new ArrayList<>();
        GhostFurnaceUnit[] made = { null };
        float[] hp = { 0, 0, 0 };
        helper.runAfterDelay(3, () -> {
            try {
                if (all.size() != 4) {
                    early.add("could not spawn the Soul Engineer and the three vindicators");
                    return;
                }
                GhostFurnace gf = ability(caster, GhostFurnace.class);
                if (gf == null) {
                    early.add("the Soul Engineer has no Ghost Furnace");
                    return;
                }
                hp[0] = near.getHealth(); hp[1] = far.getHealth(); hp[2] = friend.getHealth();
                gf.use(level, (Unit) caster, spot);
                List<GhostFurnaceUnit> found = furnacesNear(level, spotTop, 2);
                if (found.size() != 1) {
                    early.add("expected one Ghost Furnace at the spot, found " + found.size());
                    return;
                }
                GhostFurnaceUnit f = made[0] = found.get(0);
                if (!me.equals(f.getOwnerName()))
                    early.add("the furnace belongs to " + f.getOwnerName() + ", not the caster's owner");
                if (Math.abs(f.getMaxHealth() - 150f) > 0.01f || Math.abs(f.getHealth() - 150f) > 0.01f)
                    early.add("the furnace has " + f.getHealth() + "/" + f.getMaxHealth() + " HP, expected 150");
                if (!Summons.isSummoned(f))
                    early.add("the furnace is not marked summoned");
                if (f instanceof DeployedTurretUnit || f instanceof Machine)
                    early.add("the furnace is a Deploy Turret or a machine: it should be its own thing (no gun, no heat)");
                if (Essence.get(caster) != 10)
                    early.add("the Soul Engineer holds " + Essence.get(caster) + " essence after Ghost Furnace, expected 20 - 10 = 10");
                float cd = ((Unit) caster).getCooldown(gf);
                if (Math.abs(cd - GhostFurnace.CD_SECONDS * 20) > 1)
                    early.add("Ghost Furnace set a " + cd + "-tick cooldown, expected " + GhostFurnace.CD_SECONDS * 20);
                new UnitActionItem(me, UnitAction.GHOST_FURNACE, -1, new int[] { caster.getId() }, ground(helper, 4, 12), BlockPos.ZERO).action(level);
                if (Essence.get(caster) != 10 || furnacesNear(level, Vec3.atCenterOf(ground(helper, 4, 12)), 2).size() > 0)
                    early.add("an order during the cooldown spent essence or set a furnace");
            } catch (RuntimeException ex) {
                early.add("the cast threw " + ex);
            }
        });
        helper.runAfterDelay(50, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                GhostFurnaceUnit f = made[0];
                if (problems.isEmpty() && f != null) {
                    int pulses = f.getAge() / GhostFurnace.SCALD_TICKS;
                    float lost = hp[0] - near.getHealth();
                    if (pulses < 2)
                        problems.add("the furnace is only " + f.getAge() + " ticks old after 47 ticks");
                    else if (Math.abs(lost - pulses * GhostFurnace.SCALD_DAMAGE) > 0.6f)
                        problems.add("the enemy 3 blocks off lost " + lost + " HP over " + pulses + " scalds, expected " + pulses * 4);
                    if (far.getHealth() < hp[1] - 0.01f)
                        problems.add("the enemy 7 blocks off was scalded (" + hp[1] + " -> " + far.getHealth() + ")");
                    if (friend.getHealth() < hp[2] - 0.01f)
                        problems.add("the friend 2 blocks off was scalded (" + hp[2] + " -> " + friend.getHealth() + ")");
                    // killed, the summon drops no essence
                    Vec3 at = f.position();
                    wispsNear(at, 3).forEach(Entity::discard);
                    f.kill();
                    if (!wispsNear(at, 3).isEmpty())
                        problems.add("the killed furnace dropped essence");
                }
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                if (made[0] != null) {
                    wispsNear(made[0].position(), 3).forEach(Entity::discard);
                    if (!made[0].isRemoved())
                        made[0].discard();
                }
                discardAll(furnacesNear(level, spotTop, 12));
                discardAll(all);
                finish(helper, problems);
            }
        });
    }

    /** The furnace cools after 20 s: a furnace 5 ticks short of its lifetime is gone 10 ticks later. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_ghost_furnace_lasts_20_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "furnacelife");
        Mob caster = engineer(helper, me, 2, 2, 10);
        GhostFurnaceUnit[] made = { null };
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            if (caster == null) {
                early.add("could not spawn the Soul Engineer");
                return;
            }
            made[0] = GhostFurnace.place(level, caster, me, ground(helper, 6, 6), true);
            if (made[0] == null)
                early.add("Ghost Furnace.place set no furnace");
            else
                made[0].setAge(GhostFurnaceUnit.LIFETIME_TICKS - 5);
        });
        helper.runAfterDelay(14, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (made[0] != null && !made[0].isRemoved())
                    problems.add("the furnace was still standing " + made[0].getAge() + " ticks after it was set (lifetime "
                        + GhostFurnaceUnit.LIFETIME_TICKS + ")");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                if (made[0] != null && !made[0].isRemoved())
                    made[0].discard();
                discardAll(live(caster));
                finish(helper, problems);
            }
        });
    }

    /**
     * Revision 1: siege lobbers hit the furnace at the defence rate. A shell of 4 from artillery with the lobber's +3.5
     * building bonus takes 54 off it (the defence rate, 4 x 1.5 x 4.5 x 2); the same shell from artillery with no bonus
     * takes 4 (the unit rate).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_ghost_furnace_takes_the_defence_rate_from_siege_shells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "furnacesiege"), foe = owner(helper, "furnacesiegefoe");
        Mob caster = engineer(helper, me, 1, 1, 20);
        Mob siege = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14, 14);
        Mob plain = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 14, 1);
        List<Entity> all = live(caster, siege, plain);
        GhostFurnaceUnit[] made = { null, null };
        helper.runAfterDelay(2, () -> {
            if (all.size() == 3) {
                made[0] = GhostFurnace.place(level, caster, me, ground(helper, 4, 4), true);
                made[1] = GhostFurnace.place(level, caster, me, ground(helper, 4, 12), true);
            }
        });
        helper.runAfterDelay(8, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (all.size() != 3 || made[0] == null || made[1] == null) {
                    problems.add("could not set up the Soul Engineer, the two shooters and the two furnaces");
                    return;
                }
                AttributeInstance bonus = siege.getAttribute(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get());
                if (bonus == null) {
                    problems.add("setup: the shooter has no building damage bonus attribute");
                    return;
                }
                bonus.setBaseValue(3.5);
                float a0 = made[0].getHealth(), b0 = made[1].getHealth();
                BombardShell s1 = new BombardShell(level, siege, 4f);
                s1.setPos(made[0].getX(), made[0].getY() + 0.5, made[0].getZ());
                s1.burst();
                BombardShell s2 = new BombardShell(level, plain, 4f);
                s2.setPos(made[1].getX(), made[1].getY() + 0.5, made[1].getZ());
                s2.burst();
                float lostA = a0 - made[0].getHealth(), lostB = b0 - made[1].getHealth();
                if (Math.abs(lostA - 54f) > 1f)
                    problems.add("a siege shell of 4 took " + lostA + " off the furnace, expected the defence rate's 54");
                if (Math.abs(lostB - 4f) > 0.5f)
                    problems.add("a plain shell of 4 took " + lostB + " off the furnace, expected the unit rate's 4");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                for (GhostFurnaceUnit f : made)
                    if (f != null && !f.isRemoved())
                        f.discard();
                discardAll(all);
                finish(helper, problems);
            }
        });
    }

    /** Ghost Furnace refuses with 9 essence (one short): nothing spent, no cooldown, no furnace. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_ghost_furnace_refuses_when_short(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "furnaceno");
        Mob poor = engineer(helper, me, 2, 2, 9);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                GhostFurnace gf = poor == null ? null : ability(poor, GhostFurnace.class);
                if (gf == null) {
                    problems.add("could not spawn a Soul Engineer with a Ghost Furnace");
                    return;
                }
                gf.use(level, (Unit) poor, ground(helper, 6, 6));
                if (Essence.get(poor) != 9)
                    problems.add("a Soul Engineer 1 essence short spent essence: " + Essence.get(poor) + " left of 9");
                if (((Unit) poor).getCooldown(gf) > 0)
                    problems.add("a Soul Engineer 1 essence short got a cooldown");
                if (!furnacesNear(level, Vec3.atCenterOf(ground(helper, 6, 6)), 3).isEmpty())
                    problems.add("a Soul Engineer 1 essence short set a furnace");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(furnacesNear(level, Vec3.atCenterOf(ground(helper, 6, 6)), 6));
                discardAll(live(poor));
                finish(helper, problems);
            }
        });
    }

    // ------------------------------------------------------------------------------------------- Soul Boiler

    /**
     * Soul Boiler: 20 essence, 60 s. A friendly Machine Shop with a unit queued produces 1.5x as fast for 20 s; a
     * friendly site still going up builds 1.5x as fast (a second caster). An order during the cooldown spends nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_soul_boiler_speeds_a_building_by_half_for_20_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "boil");
        Mob caster = engineer(helper, me, 1, 1, 30);
        Mob second = engineer(helper, me, 1, 2, 20);
        BuildingPlacement shop = site(helper, Buildings.MACHINE_SHOP, me, 3, 3, true);
        BuildingPlacement rising = site(helper, Buildings.MACHINE_SHOP, me, 3, 3, false);
        if (rising != null)
            BuildingServerEvents.getBuildings().remove(rising);   // judged on its own below, never ticked
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (caster == null || second == null || !(shop instanceof ProductionPlacement pp) || rising == null) {
                    problems.add("could not set up the Soul Engineers and the Machine Shops");
                    return;
                }
                SoulBoiler sb = ability(caster, SoulBoiler.class), sb2 = ability(second, SoulBoiler.class);
                if (sb == null || sb2 == null) {
                    problems.add("a Soul Engineer has no Soul Boiler");
                    return;
                }
                beside(caster, shop, 10);
                pp.productionQueue.add(new ActiveProduction(ProductionItems.STEAM_RUNNER, false, me));
                float p0 = pp.getProductionBuildPower();
                long now = level.getGameTime();
                sb.use(level, (Unit) caster, shop.centrePos);
                float p1 = pp.getProductionBuildPower();
                if (Math.abs(p1 - p0 * 1.5f) > 1e-3f)
                    problems.add("the boiled Machine Shop's production power is " + p1 + ", expected 1.5 x " + p0);
                if (SoulBoiler.speedMult(shop) != 1.5f)
                    problems.add("the boiled Machine Shop's speed factor is " + SoulBoiler.speedMult(shop) + ", expected 1.5");
                long left = shop.boilUntilTick - now;
                if (left < SoulBoiler.BOIL_TICKS - 1 || left > SoulBoiler.BOIL_TICKS + 1)
                    problems.add("the boil lasts " + left + " ticks, expected 400");
                if (Essence.get(caster) != 10)
                    problems.add("the Soul Engineer holds " + Essence.get(caster) + " essence after Soul Boiler, expected 30 - 20 = 10");
                float cd = ((Unit) caster).getCooldown(sb);
                if (Math.abs(cd - SoulBoiler.CD_SECONDS * 20) > 1)
                    problems.add("Soul Boiler set a " + cd + "-tick cooldown, expected " + SoulBoiler.CD_SECONDS * 20);
                Essence.add(caster, 20);
                new UnitActionItem(me, UnitAction.SOUL_BOILER, -1, new int[] { caster.getId() }, shop.centrePos, BlockPos.ZERO).action(level);
                if (Essence.get(caster) != 30)
                    problems.add("an order during the cooldown spent essence (" + Essence.get(caster) + " left, expected 30)");
                // a site still going up: the second caster, its own cooldown
                BuildingServerEvents.getBuildings().remove(shop);
                BuildingServerEvents.getBuildings().add(rising);
                beside(second, rising, 4);
                sb2.use(level, (Unit) second, rising.centrePos);
                if (SoulBoiler.speedMult(rising) != 1.5f)
                    problems.add("a friendly site still going up was not boiled (factor " + SoulBoiler.speedMult(rising) + ")");
                if (Essence.get(second) != 0)
                    problems.add("boiling the site left " + Essence.get(second) + " essence, expected 0");
                BuildingServerEvents.getBuildings().remove(rising);
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                BuildingServerEvents.getBuildings().remove(rising);
                unsite(level, shop);
                discardAll(live(caster, second));
                finish(helper, problems);
            }
        });
    }

    /**
     * Soul Boiler refuses - no essence, no cooldown, no boil - on a built building with nothing queued, on a busy one 25
     * blocks away, on an enemy's busy one, with 19 essence (one short), and on one already boiling.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_soul_boiler_refuses_idle_far_enemy_short_and_boiling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "boilno"), foe = owner(helper, "boilnofoe");
        Mob rich = engineer(helper, me, 1, 1, 30);
        Mob poor = engineer(helper, me, 1, 2, 19);
        BuildingPlacement shop = site(helper, Buildings.MACHINE_SHOP, me, 3, 3, true);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (rich == null || poor == null || !(shop instanceof ProductionPlacement pp)) {
                    problems.add("could not set up the Soul Engineers and the Machine Shop");
                    return;
                }
                SoulBoiler sbR = ability(rich, SoulBoiler.class), sbP = ability(poor, SoulBoiler.class);
                if (sbR == null || sbP == null) {
                    problems.add("a Soul Engineer has no Soul Boiler");
                    return;
                }
                beside(rich, shop, 4);
                beside(poor, shop, 4);
                // idle: built, nothing queued
                sbR.use(level, (Unit) rich, shop.centrePos);
                if (SoulBoiler.isBoiling(shop) || Essence.get(rich) != 30)
                    problems.add("Soul Boiler boiled an idle building (" + Essence.get(rich) + " essence)");
                pp.productionQueue.add(new ActiveProduction(ProductionItems.STEAM_RUNNER, false, me));
                // short
                sbP.use(level, (Unit) poor, shop.centrePos);
                if (SoulBoiler.isBoiling(shop) || Essence.get(poor) != 19 || ((Unit) poor).getCooldown(sbP) > 0)
                    problems.add("a Soul Engineer 1 essence short boiled, spent or cooled down (" + Essence.get(poor) + " essence)");
                // 25 blocks off
                beside(rich, shop, 25);
                sbR.use(level, (Unit) rich, shop.centrePos);
                if (SoulBoiler.isBoiling(shop) || Essence.get(rich) != 30)
                    problems.add("Soul Boiler boiled a building 25 blocks away (" + Essence.get(rich) + " essence)");
                // the enemy's
                beside(rich, shop, 4);
                shop.ownerName = foe;
                sbR.use(level, (Unit) rich, shop.centrePos);
                if (SoulBoiler.isBoiling(shop) || Essence.get(rich) != 30)
                    problems.add("Soul Boiler boiled an enemy's building (" + Essence.get(rich) + " essence)");
                shop.ownerName = me;
                // already boiling
                shop.boilUntilTick = level.getGameTime() + 100;
                sbR.use(level, (Unit) rich, shop.centrePos);
                if (Essence.get(rich) != 30 || shop.boilUntilTick != level.getGameTime() + 100)
                    problems.add("Soul Boiler boiled a building already boiling (" + Essence.get(rich) + " essence)");
                if (((Unit) rich).getCooldown(sbR) > 0)
                    problems.add("a refused Soul Boiler set a cooldown");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                unsite(level, shop);
                discardAll(live(rich, poor));
                finish(helper, problems);
            }
        });
    }

    /**
     * The bots' Soul Boiler: the unit casts it by itself (autoBoil) at a friendly building in reach with work queued -
     * never at an idle one - and only with 20 essence.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void soul_engineer_soul_boiler_autocasts_at_a_busy_building(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = owner(helper, "boilauto");
        Mob caster = engineer(helper, me, 1, 1, 19);
        BuildingPlacement shop = site(helper, Buildings.MACHINE_SHOP, me, 3, 3, true);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (!(caster instanceof SoulEngineerUnit se) || !(shop instanceof ProductionPlacement pp)) {
                    problems.add("could not set up the Soul Engineer and the Machine Shop");
                    return;
                }
                beside(caster, shop, 6);
                Essence.add(caster, 1);
                if (se.autoBoil() || SoulBoiler.isBoiling(shop))
                    problems.add("the Soul Engineer boiled an idle building by itself");
                pp.productionQueue.add(new ActiveProduction(ProductionItems.STEAM_RUNNER, false, me));
                Essence.trySpend(caster, 1);
                if (se.autoBoil() || SoulBoiler.isBoiling(shop))
                    problems.add("with 19 essence the Soul Engineer boiled by itself");
                Essence.add(caster, 1);
                if (!se.autoBoil() || !SoulBoiler.isBoiling(shop))
                    problems.add("with 20 essence and a busy building in reach the Soul Engineer did not boil it by itself");
                if (Essence.get(caster) != 0)
                    problems.add("the autocast left " + Essence.get(caster) + " essence, expected 0");
                SoulBoiler sb = ability(caster, SoulBoiler.class);
                if (sb != null && ((Unit) caster).getCooldown(sb) <= 0)
                    problems.add("the autocast set no cooldown");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                unsite(level, shop);
                discardAll(live(caster));
                finish(helper, problems);
            }
        });
    }
}
