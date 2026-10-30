package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingAction;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingProductionServerboundPacket;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.hud.ProductionHudLogic;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.GuardServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BAR's factoryguard (Ctrl+G, tester request): a factory with the toggle on gives every builder it produces a guard
 * order on itself, and a builder guarding a factory lends its build power to the factory's production.
 * <ul>
 *   <li>AC-1: the toggle flips through the real packet path for the owner only, and is kept in the save data;</li>
 *   <li>AC-2: with the toggle on, a produced builder guards the factory; a combat unit, or a builder with it off, not;</li>
 *   <li>AC-3: a builder guarding a factory with production queued raises its production build power;</li>
 *   <li>AC-4: a new order from the player ends the guard, and a mixed group's press turns every factory on.</li>
 * </ul>
 * Each test stamps its own finished Barracks in its own arena, with a unique owner, all undone in the same callback.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FactoryGuardGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** One test's factory, owner, pool and units, undone together. */
    static final class Site {
        final ServerLevel level;
        final String owner;
        final List<Entity> made = new ArrayList<>();
        final Resources pool;
        ProductionPlacement pp;

        Site(GameTestHelper helper, String tag) {
            level = helper.getLevel();
            owner = "gametest_facguard_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
            pool = new Resources(owner, 0, 100000, 100000);
            ResourcesServerEvents.resourcesList.add(pool);
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
                helper.absolutePos(new BlockPos(4, 1, 4)), Rotation.NONE, owner, false);
            if (!(bp instanceof ProductionPlacement p))
                return;
            pp = p;
            for (int x = pp.minCorner.getX() - 8; x <= pp.maxCorner.getX() + 8; x++)
                for (int z = pp.minCorner.getZ() - 8; z <= pp.maxCorner.getZ() + 8; z++)
                    level.setBlock(new BlockPos(x, pp.minCorner.getY() - 1, z), Blocks.STONE.defaultBlockState(), 2);
            for (var blk : pp.getBlocks())
                level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
            pp.initPlacedBlocks();
            pp.isBuilt = true;
            BuildingServerEvents.getBuildings().add(pp);
        }

        Entity produce(net.minecraft.world.entity.EntityType<? extends com.solegendary.reignofnether.unit.interfaces.Unit> type) {
            Entity e = pp.produceUnit(level, type, owner, false);
            if (e != null)
                made.add(e);
            return e;
        }

        void undo() {
            for (Entity e : made) {
                GuardServerEvents.clear(e.getId());
                if (!e.isRemoved())
                    e.discard();
            }
            if (pp != null) {
                pp.productionQueue.clear();
                BuildingServerEvents.getBuildings().remove(pp);
                for (var blk : pp.getBlocks())
                    level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
            }
            ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    static void finish(GameTestHelper helper, Site s, List<String> problems) {
        try {
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            s.undo();
        }
    }

    static ServerPlayer fake(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
    }

    /** AC-1: the owner's packet flips the toggle and writes it to the save data; anyone else's is refused. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void ac1_factory_guard_toggles_for_the_owner_and_is_saved(GameTestHelper helper) {
        Site s = new Site(helper, "toggle");
        List<String> problems = new ArrayList<>();
        if (s.pp == null) {
            s.undo();
            helper.fail("(setup) the Barracks is not a production placement");
            return;
        }
        if (s.pp.factoryGuard)
            problems.add("a new factory starts with factory guard on");
        var packet = new BuildingProductionServerboundPacket(BuildingAction.TOGGLE_FACTORY_GUARD, "", s.pp.originPos);
        if (packet.accept(fake(s.level, s.owner + "_stranger")) || s.pp.factoryGuard)
            problems.add("a stranger's packet turned factory guard on");
        if (!packet.accept(fake(s.level, s.owner)) || !s.pp.factoryGuard)
            problems.add("the owner's packet did not turn factory guard on");
        if (!Boolean.TRUE.equals(s.pp.getDataStorage().getData(ProductionPlacement.FACTORY_GUARD)))
            problems.add("factory guard on was not written to the save data");
        s.pp.factoryGuard = false;
        s.pp.restoreFactoryGuard();
        if (!s.pp.factoryGuard)
            problems.add("factory guard did not come back from the save data");
        if (!packet.accept(fake(s.level, s.owner)) || s.pp.factoryGuard)
            problems.add("a second press did not turn factory guard off");
        if (!Boolean.FALSE.equals(s.pp.getDataStorage().getData(ProductionPlacement.FACTORY_GUARD)))
            problems.add("factory guard off was not written to the save data");
        finish(helper, s, problems);
    }

    /** AC-2: on, a produced builder guards the factory; a combat unit does not; off, a builder does not either. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void ac2_factory_guard_makes_produced_builders_guard_the_factory(GameTestHelper helper) {
        Site s = new Site(helper, "produce");
        List<String> problems = new ArrayList<>();
        if (s.pp == null) {
            s.undo();
            helper.fail("(setup) the Barracks is not a production placement");
            return;
        }
        Entity offBuilder = s.produce(EntityRegistrar.VILLAGER_UNIT.get());
        s.pp.setFactoryGuard(true);
        Entity builder = s.produce(EntityRegistrar.VILLAGER_UNIT.get());
        Entity fighter = s.produce(EntityRegistrar.VINDICATOR_UNIT.get());
        if (offBuilder == null || builder == null || fighter == null) {
            s.undo();
            helper.fail("(setup) the factory could not produce the units");
            return;
        }
        if (!s.pp.originPos.equals(GuardServerEvents.guardedBuilding(builder.getId())))
            problems.add("a builder made with factory guard on guards " + GuardServerEvents.guardedBuilding(builder.getId()));
        if (GuardServerEvents.isGuarding(fighter.getId()))
            problems.add("a combat unit made with factory guard on was put on guard");
        if (GuardServerEvents.isGuarding(offBuilder.getId()))
            problems.add("a builder made with factory guard off was put on guard");
        finish(helper, s, problems);
    }

    /** AC-3: a builder guarding the factory, beside it, raises the build power of the factory's production. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void ac3_a_builder_guarding_a_factory_speeds_up_its_production(GameTestHelper helper) {
        Site s = new Site(helper, "assist");
        List<String> problems = new ArrayList<>();
        if (s.pp == null) {
            s.undo();
            helper.fail("(setup) the Barracks is not a production placement");
            return;
        }
        float base = s.pp.getProductionBuildPower();
        s.pp.productionQueue.add(new ActiveProduction(ProductionItems.VINDICATOR, false, s.owner));
        s.pp.setFactoryGuard(true);
        Entity builder = s.produce(EntityRegistrar.VILLAGER_UNIT.get());
        if (builder == null) {
            s.undo();
            helper.fail("(setup) the factory could not produce the builder");
            return;
        }
        BlueprintGameTests.poll(helper, 0, 260,
            () -> s.pp.getProductionBuildPower() > base + 0.5f,
            () -> finish(helper, s, problems),
            () -> {
                problems.add("production build power stayed " + s.pp.getProductionBuildPower() + " (base " + base
                    + "); builder at " + builder.blockPosition().toShortString() + ", gap "
                    + GuardServerEvents.footprintGap(s.pp, builder.blockPosition()) + ", guarding "
                    + GuardServerEvents.guardedBuilding(builder.getId()));
                finish(helper, s, problems);
            });
    }

    /** AC-4: the player's next order ends a factory guard; one press on a mixed group turns every factory on. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void ac4_a_new_order_ends_the_guard_and_a_mixed_group_goes_all_on(GameTestHelper helper) {
        Site s = new Site(helper, "order");
        List<String> problems = new ArrayList<>();
        if (s.pp == null) {
            s.undo();
            helper.fail("(setup) the Barracks is not a production placement");
            return;
        }
        s.pp.setFactoryGuard(true);
        Entity builder = s.produce(EntityRegistrar.VILLAGER_UNIT.get());
        if (builder == null) {
            s.undo();
            helper.fail("(setup) the factory could not produce the builder");
            return;
        }
        if (!GuardServerEvents.isGuarding(builder.getId()))
            problems.add("the produced builder was not guarding");
        UnitServerEvents.addActionItem(s.owner, UnitAction.MOVE, -1, new int[] { builder.getId() },
            builder.blockPosition().offset(3, 0, 0), new BlockPos(0, 0, 0));
        helper.runAfterDelay(5, () -> {
            if (GuardServerEvents.isGuarding(builder.getId()))
                problems.add("a move order did not end the factory guard");
            if (!ProductionHudLogic.repeatTarget(List.of(true, false)) || ProductionHudLogic.repeatTarget(List.of(true, true)))
                problems.add("a group press does not turn a mixed group on and an all-on group off");
            if (ProductionHudLogic.allGuarding(List.of()) || !ProductionHudLogic.allGuarding(List.of(s.pp)))
                problems.add("the button's lit state is wrong for " + s.pp.factoryGuard);
            finish(helper, s, problems);
        });
    }
}
