package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.addon.NightSourceAddon;
import com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.time.NightUtils;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The late-game perf quick wins (Oct 26 audit of an 8v8 with ~1,000 units and ~1,700 buildings) must not change what
 * a player sees: written before the fixes, against the code they replace.
 * <ul>
 *   <li>NightUtils.isInRangeOfNightSource walked every building and every unit per call; it now checks a per-tick list
 *       of night sources. A source still covers its range, a point outside stays outside, and a source that goes away
 *       stops counting.</li>
 *   <li>UnitServerEvents.knockbackIgnoreIds: an id added on a hit that no knockback follows stayed forever (and every
 *       knockback paid a removeIf over the leak). After a tick with no knockback the id is gone; a real arrow hit
 *       still has its knockback cancelled.</li>
 *   <li>Unit.tick's item-pickup query is skipped while the level holds no item: an item dropped after quiet ticks is
 *       still picked up.</li>
 *   <li>getCurrentPopulation's production-queue part is cached per owner per tick: queuing, unqueuing and removing a
 *       building in the same tick still count exactly as before.</li>
 * </ul>
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerfQuickWinsGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_perfqw_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    // ------------------------------------------------------------------------------------------------ night sources

    /**
     * A Shadow Shifter's NIGHT_WARPING (range = amplifier + 1) still puts a point inside its range in the night and
     * leaves a point outside it as it was; once the effect is gone the point inside is back to what it was before.
     * Judged against the "before" readings so a neighbour arena's own night source cannot fail it.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void night_warping_unit_still_covers_its_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ZombieUnit zombie = EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        if (zombie == null) {
            helper.fail("could not create a zombie unit");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        zombie.setOwnerName(owner(helper, "night"));
        level.addFreshEntity(zombie);
        final int range = 10;   // amplifier 9
        int[] phase = { 0 };
        long[] t = { 0 };
        boolean[] before = new boolean[2];   // near, far
        helper.onEachTick(() -> {
            if (phase[0] == 3)
                return;
            Vec3 p = zombie.position();
            Vec3 near = p.add(range - 4, 1, 0), far = p.add(range + 6, 1, 0);
            if (phase[0] == 0 && helper.getTick() >= 3) {
                before[0] = NightUtils.isInRangeOfNightSource(near, false);
                before[1] = NightUtils.isInRangeOfNightSource(far, false);
                zombie.addEffect(new MobEffectInstance(MobEffectRegistrar.NIGHT_WARPING.get(), 400, range - 1));
                t[0] = helper.getTick();
                phase[0] = 1;
            } else if (phase[0] == 1 && helper.getTick() >= t[0] + 3) {
                if (!NightUtils.isInRangeOfNightSource(near, false))
                    fail(helper, zombie, "a point " + (range - 4) + " blocks from a range-" + range + " NIGHT_WARPING unit is not in night");
                else if (NightUtils.isInRangeOfNightSource(far, false) != before[1])
                    fail(helper, zombie, "a point " + (range + 6) + " blocks away changed with the unit's NIGHT_WARPING (was " + before[1] + ")");
                else {
                    zombie.removeEffect(MobEffectRegistrar.NIGHT_WARPING.get());
                    t[0] = helper.getTick();
                    phase[0] = 2;
                }
            } else if (phase[0] == 2 && helper.getTick() >= t[0] + 3) {
                phase[0] = 3;
                boolean nearNow = NightUtils.isInRangeOfNightSource(near, false);
                zombie.discard();
                if (nearNow != before[0])
                    helper.fail("the point stayed in night after the NIGHT_WARPING effect ended (before: " + before[0] + ")");
                else
                    helper.succeed();
            }
        });
    }

    private static void fail(GameTestHelper helper, ZombieUnit zombie, String why) {
        zombie.discard();
        helper.fail(why);
    }

    /**
     * A built Stronghold (a night source) still covers its night range round its centre, a point beyond the range is
     * unchanged by it, and taking it out of the building list ends its night - all in one tick, as a building placed or
     * destroyed mid-tick must count at once. Its blocks are never placed, so nothing ticks it into the world.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void night_source_building_still_covers_its_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.STRONGHOLD, level,
            helper.absolutePos(new BlockPos(4, 1, 4)), Rotation.NONE, owner(helper, "stronghold"), false);
        if (bp == null) {
            helper.fail("could not make a Stronghold placement");
            return;
        }
        NightSourceAddon nsa = bp.getBuilding().getActiveAddon(NightSourceAddon.class);
        if (nsa == null) {
            helper.fail("the Stronghold has no active NightSourceAddon");
            return;
        }
        bp.isBuilt = true;
        int range = nsa.getNightRange(bp);
        if (range <= 0) {
            helper.fail("a built Stronghold has night range " + range);
            return;
        }
        BlockPos c = BuildingUtils.getCentrePos(bp.getBlocks());
        Vec3 near = new Vec3(c.getX() + range - 3, c.getY(), c.getZ() + 0.5);
        Vec3 far = new Vec3(c.getX() + range + 3, c.getY(), c.getZ() + 0.5);
        boolean nearBefore = NightUtils.isInRangeOfNightSource(near, false);
        boolean farBefore = NightUtils.isInRangeOfNightSource(far, false);
        List<String> problems = new ArrayList<>();
        BuildingServerEvents.getBuildings().add(bp);
        try {
            if (!NightUtils.isInRangeOfNightSource(near, false))
                problems.add("a point " + (range - 3) + " blocks from a range-" + range + " Stronghold is not in night");
            if (NightUtils.isInRangeOfNightSource(far, false) != farBefore)
                problems.add("a point " + (range + 3) + " blocks away changed with the Stronghold (was " + farBefore + ")");
        } finally {
            BuildingServerEvents.getBuildings().remove(bp);
        }
        if (NightUtils.isInRangeOfNightSource(near, false) != nearBefore)
            problems.add("the point stayed in night after the Stronghold left the building list (before: " + nearBefore + ")");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ------------------------------------------------------------------------------------------------ knockback ids

    private static VillagerUnit spawnVillager(GameTestHelper helper, String owner, int x, int z) {
        VillagerUnit v = EntityRegistrar.VILLAGER_UNIT.get().create(helper.getLevel());
        if (v == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        v.setOwnerName(owner);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /**
     * An arrow with no shooter marks the target to ignore knockback (shouldIgnoreKnockback), but vanilla only knocks
     * back from a source entity, so no knockback event ever consumes the mark. It must not outlive the tick.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void knockback_ignore_list_is_empty_after_a_tick_without_knockback(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillagerUnit target = spawnVillager(helper, owner(helper, "kbleak"), 3, 3);
        if (target == null) {
            helper.fail("could not create a villager unit");
            return;
        }
        Arrow arrow = new Arrow(level, target.getX(), target.getY() + 3, target.getZ());
        boolean hurt = target.hurt(level.damageSources().arrow(arrow, null), 1f);
        int id = target.getId();
        if (!UnitServerEvents.knockbackIgnoreIds.contains(id)) {
            target.discard();
            helper.fail("set-up: an arrow hit (hurt returned " + hurt + ") did not mark the target to ignore knockback");
            return;
        }
        helper.runAfterDelay(2, () -> {
            boolean leaked = UnitServerEvents.knockbackIgnoreIds.contains(id);
            target.discard();
            if (leaked)
                helper.fail("the knockback-ignore mark of a hit with no knockback was still there 2 ticks later");
            else
                helper.succeed();
        });
    }

    /** A shot arrow's hit still has its knockback cancelled, in the same tick, and the mark is consumed by it. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void arrow_hit_still_ignores_knockback(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillagerUnit target = spawnVillager(helper, owner(helper, "kbtarget"), 3, 3);
        VillagerUnit shooter = spawnVillager(helper, owner(helper, "kbshooter"), 3, 8);
        if (target == null || shooter == null) {
            if (target != null) target.discard();
            if (shooter != null) shooter.discard();
            helper.fail("could not create the villager units");
            return;
        }
        Arrow arrow = new Arrow(level, shooter);
        target.setDeltaMovement(Vec3.ZERO);
        boolean hurt = target.hurt(level.damageSources().arrow(arrow, shooter), 1f);
        Vec3 v = target.getDeltaMovement();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        boolean marked = UnitServerEvents.knockbackIgnoreIds.contains(target.getId());
        target.discard();
        shooter.discard();
        if (!hurt)
            helper.fail("set-up: the arrow hit did no damage");
        else if (horizontal > 0.05)
            helper.fail("an arrow hit knocked the target back (horizontal speed " + horizontal + ")");
        else if (marked)
            helper.fail("the knockback that followed the arrow hit did not consume the target's ignore mark");
        else
            helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ item pickup

    /** A worker that has stood around for a few ticks still picks up a resource item dropped at its feet. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void item_dropped_after_quiet_ticks_is_still_picked_up(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillagerUnit worker = spawnVillager(helper, owner(helper, "pickup"), 4, 4);
        if (worker == null) {
            helper.fail("could not create a worker");
            return;
        }
        ItemEntity[] logs = { null };
        long[] dropped = { -1 };
        helper.onEachTick(() -> {
            if (dropped[0] == -2)
                return;
            if (logs[0] == null) {
                if (helper.getTick() < 10)
                    return;
                ItemEntity item = new ItemEntity(level, worker.getX(), worker.getY(), worker.getZ(), new ItemStack(Items.OAK_LOG, 1));
                item.setPickUpDelay(0);
                item.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(item);
                logs[0] = item;
                dropped[0] = helper.getTick();
            } else if (logs[0].isRemoved() && !worker.getItems().isEmpty()) {
                dropped[0] = -2;
                worker.discard();
                helper.succeed();
            } else if (helper.getTick() > dropped[0] + 80) {
                dropped[0] = -2;
                logs[0].discard();
                worker.discard();
                helper.fail("the worker did not pick up a log dropped at its feet within 80 ticks");
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ population

    /**
     * Queued production counts toward population exactly as before, inside one tick: each Zombie queued adds its
     * population at once, a removed queue entry and a cleared queue take it away at once, and a production building
     * that leaves the building list stops counting at once.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void population_counts_queued_production_as_before(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "pop");
        Resources pool = new Resources(owner, 0, 100000, 100000);
        ResourcesServerEvents.resourcesList.add(pool);
        GraveyardPlacement gy = null;
        List<String> problems = new ArrayList<>();
        try {
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.GRAVEYARD, level,
                helper.absolutePos(new BlockPos(4, 1, 4)), Rotation.NONE, owner, false);
            if (!(bp instanceof GraveyardPlacement g)) {
                helper.fail("the Graveyard placement is " + bp);
                return;
            }
            gy = g;
            for (int x = gy.minCorner.getX() - 1; x <= gy.maxCorner.getX() + 1; x++)
                for (int z = gy.minCorner.getZ() - 1; z <= gy.maxCorner.getZ() + 1; z++)
                    level.setBlock(new BlockPos(x, gy.minCorner.getY() - 1, z), Blocks.STONE.defaultBlockState(), 2);
            for (var blk : gy.getBlocks())
                level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
            gy.initPlacedBlocks();
            gy.isBuilt = true;
            gy.autoRelease = false;
            int base = UnitServerEvents.getCurrentPopulation(owner);
            BuildingServerEvents.getBuildings().add(gy);
            expect(problems, "a graveyard with an empty queue", base, UnitServerEvents.getCurrentPopulation(owner));
            int cost = ProductionItems.ZOMBIE.getCost(false, owner).population;
            if (cost <= 0)
                problems.add("set-up: a Zombie costs " + cost + " population");
            for (int i = 1; i <= 3; i++) {
                if (!gy.startProductionItem(ProductionItems.ZOMBIE))
                    problems.add("queuing Zombie " + i + " was refused");
                expect(problems, i + " Zombies queued", base + i * cost, UnitServerEvents.getCurrentPopulation(owner));
            }
            gy.productionQueue.remove(gy.productionQueue.size() - 1);
            expect(problems, "one queued Zombie removed", base + 2 * cost, UnitServerEvents.getCurrentPopulation(owner));
            gy.productionQueue.clear();
            expect(problems, "the queue cleared", base, UnitServerEvents.getCurrentPopulation(owner));
            gy.startProductionItem(ProductionItems.ZOMBIE);
            expect(problems, "a Zombie queued again", base + cost, UnitServerEvents.getCurrentPopulation(owner));
            BuildingServerEvents.getBuildings().remove(gy);
            expect(problems, "the graveyard gone from the building list", base, UnitServerEvents.getCurrentPopulation(owner));
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            if (gy != null) {
                gy.productionQueue.clear();
                BuildingServerEvents.getBuildings().remove(gy);
                for (var blk : gy.getBlocks())
                    level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
            }
            ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    private static void expect(List<String> problems, String when, int want, int got) {
        if (want != got)
            problems.add(when + ": population " + got + ", want " + want);
    }
}
