package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.gameender.NecropolisPlacement;
import com.solegendary.reignofnether.unit.UnitUnstick;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Branch art-pass-3 (design/art-pass-3-progress.md): the Necropolis's host rises on the ground round its target - not
 * on the target's roof - and the art this pass ships is in the mod file (beast portraits, game-ender icons, the
 * villager workers' skins and profession overlays). Its own holder next to SkirmishGameTests (same arena template,
 * unique owners, no global counts); the game-ender placement is never put in the world and gets its own building
 * pool, so no other test's buildings can be aimed at or stood on.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArtPassThreeGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /**
     * The Necropolis raises its host round a roofed target on the ground it stands on. The target is a Machine Shop
     * stamped into the arena (a 9x9 copper roof over an open floor, 3 blocks up): the first version took each risen's
     * height from the heightmap, so every ring point under that roof stood its risen on the roof. One ring point is
     * turned into a lava pool. Every risen must stand at the arena's ground level, outside the shop's footprint (not on
     * it, not under it), with room for its body and no lava in or beside it (UnitUnstick.bodyFits), inside the arena,
     * one per cell, for the Necropolis's owner and tagged risen.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void necropolis_host_rises_on_the_ground_round_a_roofed_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "necro_roof"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        // the shop's floor layer replaces the arena's grass (y 1); its roof is at y 5, the ridge at y 6
        BuildingPlacement shop = CombatEngineerGameTests.stampedBuilt(level, Buildings.MACHINE_SHOP,
            helper.absolutePos(new BlockPos(3, 0, 3)), foeOwner);
        var necro = (NecropolisPlacement) BuildingUtils.getNewBuildingPlacement(Buildings.NECROPOLIS, level,
            helper.absolutePos(new BlockPos(0, 2, 0)), Rotation.NONE, owner, false);
        necro.pool = new ArrayList<>(List.of(shop));
        int ground = helper.absolutePos(new BlockPos(0, 2, 0)).getY();   // where a unit stands on the arena's grass
        // the test has to cover the bug: the old ring (radius 5 round the centre) put points under the roof
        int underRoof = 0;
        for (int i = 0; i < NecropolisPlacement.BASE_RISEN; i++) {
            double a = i * Math.PI * 2 / NecropolisPlacement.BASE_RISEN;
            BlockPos col = new BlockPos((int) Math.floor(shop.centrePos.getX() + 0.5 + Math.cos(a) * 5), ground,
                (int) Math.floor(shop.centrePos.getZ() + 0.5 + Math.sin(a) * 5));
            if (shop.isPosInsideBuilding(col) && !level.getBlockState(new BlockPos(col.getX(), shop.maxCorner.getY() - 1, col.getZ())).isAir())
                underRoof++;
        }
        if (underRoof < 2)
            problems.add("test geometry: only " + underRoof + " old ring points are under the shop's roof");
        // lava where the new ring's east point would stand (the floor block under it)
        double[] east = NecropolisPlacement.ringPoint(shop.centrePos, shop, 0);
        BlockPos lava = new BlockPos((int) Math.floor(east[0]), ground - 1, (int) Math.floor(east[1]));
        BlockState under = level.getBlockState(lava);
        level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 2);
        BlockPos min = helper.absolutePos(BlockPos.ZERO), max = helper.absolutePos(new BlockPos(15, 11, 15));
        try {
            necro.aimAt(level, shop.centrePos, foeOwner);
            necro.resolveStrike(level);
            int n = necro.lastRisen.size();
            if (necro.strikesLanded != 1 || n < NecropolisPlacement.BASE_RISEN)
                problems.add("expected " + NecropolisPlacement.BASE_RISEN + "+ risen, got " + n);
            Set<Long> cells = new HashSet<>();
            for (Entity e : necro.lastRisen) {
                BlockPos feet = BlockPos.containing(e.getX(), e.getY() + 0.01, e.getZ());
                String at = helper.relativePos(feet).toShortString();
                if (!(e instanceof Unit u) || !owner.equals(u.getOwnerName()))
                    problems.add("a risen does not belong to the Necropolis's owner");
                if (!NecropolisPlacement.isRisen(e))
                    problems.add("a risen is not tagged to crumble");
                if (feet.getY() != ground)
                    problems.add("a risen stands at y " + feet.getY() + ", not on the ground (y " + ground + ") at " + at);
                if (shop.isPosInsideBuilding(feet) || shop.isPosInsideBuilding(feet.below()))
                    problems.add("a risen stands in or on the target's footprint at " + at);
                if (!UnitUnstick.bodyFits(level, feet, e.getBbWidth(), e.getBbHeight()))
                    problems.add("a risen's body does not fit (or lava is beside it) at " + at);
                if (lavaNear(level, feet))
                    problems.add("a risen stands next to lava at " + at);
                if (feet.getX() < min.getX() || feet.getX() > max.getX() || feet.getZ() < min.getZ() || feet.getZ() > max.getZ())
                    problems.add("a risen rose outside the arena at " + at);
                if (!cells.add(feet.asLong()))
                    problems.add("two risen share the cell " + at);
            }
        } finally {
            for (Entity e : necro.lastRisen)
                e.discard();
            level.setBlock(lava, under, 2);   // the same tick it was poured: it never flowed
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** Lava in the feet cell's 3x3 at feet or floor level. */
    static boolean lavaNear(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = -1; dy <= 0; dy++)
                    if (level.getFluidState(feet.offset(dx, dy, dz)).is(FluidTags.LAVA))
                        return true;
        return false;
    }
}
