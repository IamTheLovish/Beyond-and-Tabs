package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The exit arrow (tester request 2026-10-26: "all structures should have an arrow that shows where the units they
 * produce comes out of"):
 * <ul>
 *   <li>AC-1: for every production building of every faction, at two rotations, the arrow's spot
 *       (ProductionPlacement.exitArrowPos) is where a unit produced through the building's real production item
 *       actually appears, within 1.5 blocks;</li>
 *   <li>AC-2: a building that produces no units has no arrow;</li>
 *   <li>AC-4: setting a rally point neither moves the arrow nor is touched by it.</li>
 * </ul>
 * Each building is stamped finished on a stone pad high above the arena (clear of other tests' ground), produced from,
 * measured and removed again in the same tick.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ExitArrowGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final double TOLERANCE = 1.5;
    static final int LIFT = 48;

    /** A stamped, finished building and everything to undo. */
    static final class Stamp {
        final ServerLevel level;
        final ProductionPlacement pp;
        final List<BlockPos> pad = new ArrayList<>();

        Stamp(ServerLevel level, ProductionPlacement pp) {
            this.level = level;
            this.pp = pp;
        }

        void undo() {
            pp.productionQueue.clear();
            BuildingServerEvents.getBuildings().remove(pp);
            for (var blk : pp.getBlocks())
                level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
            for (BlockPos p : pad)
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    @javax.annotation.Nullable
    static Stamp stamp(GameTestHelper helper, Building building, Rotation rotation, String owner) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(16, LIFT, 16));
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(building, level, origin, rotation, owner, false);
        if (!(bp instanceof ProductionPlacement pp))
            return null;
        Stamp s = new Stamp(level, pp);
        int y = pp.minCorner.getY() - 1;
        for (int x = pp.minCorner.getX() - 12; x <= pp.maxCorner.getX() + 12; x++)
            for (int z = pp.minCorner.getZ() - 12; z <= pp.maxCorner.getZ() + 12; z++) {
                BlockPos p = new BlockPos(x, y, z);
                level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
                s.pad.add(p);
            }
        for (var blk : pp.getBlocks())
            level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
        pp.initPlacedBlocks();
        pp.isBuilt = true;
        BuildingServerEvents.getBuildings().add(pp);
        return s;
    }

    static List<ProductionBuilding> productionBuildings() {
        List<ProductionBuilding> out = new ArrayList<>();
        for (Building b : ReignOfNetherRegistries.BUILDING)
            if (b instanceof ProductionBuilding pb)
                out.add(pb);
        return out;
    }

    /** AC-1 at one rotation: arrow spot vs the real spawn, for every production building that produces units. */
    static void arrowMatchesRealSpawn(GameTestHelper helper, Rotation rotation, String tag) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_exitarrow_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = new Resources(owner, 0, 100000, 100000);
        ResourcesServerEvents.resourcesList.add(pool);
        List<String> problems = new ArrayList<>();
        int checked = 0;
        try {
            for (ProductionBuilding building : productionBuildings()) {
                Stamp s = stamp(helper, building, rotation, owner);
                if (s == null)
                    continue;   // custom / special placements that are not production placements
                List<Entity> made = new ArrayList<>();
                try {
                    ProductionPlacement.ExitSpec spec = s.pp.exitSpec(level);
                    if (spec == null)
                        continue;   // produces no units: AC-2 covers it
                    BlockPos arrow = s.pp.exitArrowPos(level, spec, true);
                    // the real thing: the primary item's own completion, as ProductionItem.tick runs it
                    AABB around = new AABB(s.pp.minCorner, s.pp.maxCorner).inflate(24);
                    List<Entity> before = level.getEntities((Entity) null, around, e -> e.getType() == spec.type());
                    spec.item().onComplete.accept(level, s.pp);
                    Entity unit = null;
                    for (Entity e : level.getEntities((Entity) null, around, e -> e.getType() == spec.type()))
                        if (!before.contains(e)) {
                            made.add(e);
                            unit = e;
                        }
                    String name = building.name + " (" + EntityType.getKey(spec.type()).getPath() + ", " + rotation + ")";
                    checked++;
                    if (unit == null) {
                        problems.add(name + ": production spawned nothing");
                        continue;
                    }
                    if (arrow == null) {
                        problems.add(name + ": no arrow, unit at " + unit.blockPosition().toShortString());
                        continue;
                    }
                    double dx = unit.getX() - (arrow.getX() + 0.5), dy = unit.getY() - arrow.getY(), dz = unit.getZ() - (arrow.getZ() + 0.5);
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > TOLERANCE)
                        problems.add(String.format("%s: arrow %s, unit %.1f %.1f %.1f (%.1f off)", name,
                                arrow.toShortString(), unit.getX(), unit.getY(), unit.getZ(), d));
                } finally {
                    for (Entity e : made)
                        if (!e.isRemoved())
                            e.discard();
                    s.undo();
                }
            }
            if (checked < 20)
                problems.add("only " + checked + " production buildings with units were checked");
            ReignOfNether.LOGGER.info("exit arrow {}: {} production buildings checked, {} problems", rotation, checked, problems.size());
        } finally {
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void exit_arrow_ac1_matches_the_real_spawn_rotation_none(GameTestHelper helper) {
        arrowMatchesRealSpawn(helper, Rotation.NONE, "r0");
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void exit_arrow_ac1_matches_the_real_spawn_rotation_90(GameTestHelper helper) {
        arrowMatchesRealSpawn(helper, Rotation.CLOCKWISE_90, "r90");
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void exit_arrow_ac1_matches_the_real_spawn_rotation_180(GameTestHelper helper) {
        arrowMatchesRealSpawn(helper, Rotation.CLOCKWISE_180, "r180");
    }

    /** AC-2: a production building with no unit items (research only) has no arrow; a unit lab has one. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void exit_arrow_ac2_no_arrow_without_unit_production(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_exitarrow_ac2_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<String> problems = new ArrayList<>();
        int none = 0, some = 0;
        for (ProductionBuilding building : productionBuildings()) {
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(building, level,
                    helper.absolutePos(new BlockPos(16, LIFT, 16)), Rotation.NONE, owner, false);
            if (!(bp instanceof ProductionPlacement pp))
                continue;
            boolean unitItem = false;
            for (var item : building.productions.get())
                unitItem |= item instanceof com.solegendary.reignofnether.building.production.IUnitProductionItem;
            ProductionPlacement.ExitSpec spec = pp.exitSpec(level);
            if (spec == null) {
                none++;
                if (unitItem)
                    ReignOfNether.LOGGER.info("exit arrow: {} has unit items but none spawns through produceUnit", building.name);
            } else {
                some++;
                if (!unitItem)
                    problems.add(building.name + " has an arrow but no unit item");
            }
        }
        if (some == 0)
            problems.add("no production building has an arrow");
        if (none == 0)
            problems.add("(setup) no production building without unit production to check");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** AC-4: a rally point does not move the arrow, and finding the arrow leaves the rally point alone. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void exit_arrow_ac4_rally_point_unaffected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_exitarrow_ac4_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Stamp s = stamp(helper, Buildings.BARRACKS, Rotation.NONE, owner);
        if (s == null) {
            helper.fail("(setup) the Barracks is not a production placement");
            return;
        }
        List<String> problems = new ArrayList<>();
        try {
            ProductionPlacement.ExitSpec spec = s.pp.exitSpec(level);
            if (spec == null) {
                helper.fail("the Barracks has no exit arrow");
                return;
            }
            BlockPos before = s.pp.exitArrowPos(level, spec, true);
            BlockPos rally = s.pp.maxCorner.offset(8, 0, 8).atY(s.pp.minCorner.getY());
            s.pp.setRallyPoint(rally);
            BlockPos after = s.pp.exitArrowPos(level, spec, true);
            if (before == null || !before.equals(after))
                problems.add("the arrow moved with the rally point: " + before + " -> " + after);
            if (s.pp.getRallyPoints().size() != 1 || !s.pp.getRallyPoints().get(0).equals(rally))
                problems.add("the rally point changed: " + s.pp.getRallyPoints());
        } finally {
            s.undo();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
