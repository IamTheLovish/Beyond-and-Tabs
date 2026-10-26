package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A partly built building can be destroyed (the user's bug: "a metal extractor at 20% constructed or so wouldn't be
 * destroyed no matter what"). Enemy Vindicators get a forced attack-building order - the same damage path units use
 * (BuildingPlacement.destroyRandomBlocks with an attacker) - and the server's building tick must take the site out of
 * the building list, leaving its footprint free. A fully built control goes down the same way.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PartialDestroyGameTests {

    static final String BATCH = "partial_destroy";
    static final int ATTACK_TICKS = 500;

    /** A placement registered with the server; fraction < 1 stands only the lowest fraction of its blocks, unbuilt. */
    static BuildingPlacement site(GameTestHelper helper, Building type, String owner, int x, int z, float fraction) {
        ServerLevel level = helper.getLevel();
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(type, level,
            helper.absolutePos(new BlockPos(x, 1, z)), Rotation.NONE, owner, false);
        List<BuildingBlock> solid = new ArrayList<>();
        for (BuildingBlock b : bp.getBlocks())
            if (!b.getBlockState().isAir())
                solid.add(b);
        solid.sort(Comparator.comparingInt(b -> b.getBlockPos().getY()));
        int n = fraction >= 1f ? solid.size() : Math.max(1, Math.round(solid.size() * fraction));
        for (int i = 0; i < n; i++)
            level.setBlock(solid.get(i).getBlockPos(), solid.get(i).getBlockState(), 3);
        bp.initPlacedBlocks();
        bp.isBuilt = fraction >= 1f;
        if (!bp.isBuilt)
            bp.buildProgress = (float) n / solid.size();   // the paid progress a real site at this point would have
        BuildingServerEvents.getBuildings().add(bp);
        return bp;
    }

    static List<VindicatorUnit> attackers(GameTestHelper helper, String owner, BuildingPlacement target, int count) {
        List<VindicatorUnit> made = new ArrayList<>();
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        for (int i = 0; i < count; i++) {
            VindicatorUnit v = EntityRegistrar.VINDICATOR_UNIT.get().create(helper.getLevel());
            if (v == null)
                continue;
            v.moveTo(target.minCorner.getX() - 1.5 + i * 2, o.getY() + 2, target.maxCorner.getZ() + 2.5, 0, 0);
            v.setOwnerName(owner);
            helper.getLevel().addFreshEntity(v);
            made.add(v);
        }
        return made;
    }

    static final int HIT_EVERY = 2;
    // a light unit's hit: one block's worth of damage (getHealthPerBlock() is per two blocks)
    static final int HIT_BLOCKS = 1;

    /**
     * ORDER: three enemy Vindicators get a forced attack-building order (big hits). HITS: one enemy unit lands a steady
     * one-block hit every HIT_EVERY ticks through BuildingPlacement.destroyRandomBlocks(amount, attacker) - the call
     * every unit attack ends in - the way a squad of light units chips at a site.
     */
    static void run(GameTestHelper helper, Building type, String tag, float fraction, boolean order) {
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        String owner = "gametest_pd_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BuildingPlacement bp = site(helper, type, owner, 5, 5, fraction);
        int startBlocks = bp.getBlocksPlaced(), startHealth = bp.getHealth();
        List<VindicatorUnit> foes = attackers(helper, owner + "_foe", bp, order ? 3 : 1);
        if (foes.isEmpty() || startBlocks <= 0) {
            BuildingServerEvents.getBuildings().remove(bp);
            foes.forEach(VindicatorUnit::discard);
            helper.fail("setup: " + foes.size() + " attackers, " + startBlocks + " blocks standing");
            return;
        }
        // blocks a hit knocked off must stay off: nobody is building the site, so it may not grow back between hits
        int[] lastAfterHit = { -1 }, regrown = { 0 };
        if (order) {
            helper.runAfterDelay(3, () -> foes.forEach(v -> v.setAttackBuildingTarget(bp.centrePos, true)));
        } else {
            VindicatorUnit striker = foes.get(0);
            striker.setNoAi(true);   // only the attacker of record; the hits below are the test's
            for (int t = 5; t < ATTACK_TICKS - 10; t += HIT_EVERY)
                helper.runAfterDelay(t, () -> {
                    if (!BuildingServerEvents.getBuildings().contains(bp))
                        return;
                    if (lastAfterHit[0] >= 0 && bp.getBlocksPlaced() > lastAfterHit[0])
                        regrown[0] += bp.getBlocksPlaced() - lastAfterHit[0];
                    bp.destroyRandomBlocks(bp.getHealthPerBlock() / 2 * HIT_BLOCKS, striker);
                    lastAfterHit[0] = bp.getBlocksPlaced();
                });
        }
        helper.runAfterDelay(ATTACK_TICKS, () -> {
            boolean listed = BuildingServerEvents.getBuildings().contains(bp);
            int blocksNow = bp.getBlocksPlaced(), healthNow = bp.getHealth();
            String state = bp.constructionState();
            BuildingPlacement onSpot = BuildingUtils.findBuilding(false, bp.centrePos);
            foes.forEach(VindicatorUnit::discard);
            BuildingServerEvents.getBuildings().remove(bp);
            String what = tag + " at " + Math.round(fraction * 100) + "% (" + (order ? "attack orders" : "steady hits") + ")";
            if (regrown[0] > 0)
                helper.fail(what + ": " + regrown[0] + " knocked-off blocks grew back with no builder on the site"
                    + (listed ? " and it was never destroyed (" + blocksNow + " of " + startBlocks + " blocks left)" : ""));
            else if (listed)
                helper.fail(what + " was not destroyed after " + ATTACK_TICKS + " ticks of attacks: blocks "
                    + startBlocks + " -> " + blocksNow + " of " + bp.getBlocksTotal() + ", health " + startHealth + " -> " + healthNow + ", " + state);
            else if (onSpot != null)
                helper.fail(what + " destroyed but its spot is still held by " + onSpot.getBuilding().name);
            else
                helper.succeed();
        });
    }

    /** AC-1: a metal extractor at ~20% construction is destroyed by steady enemy hits and its spot is free again. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = ATTACK_TICKS + 20, batch = BATCH)
    public static void partial_mex_at_20_percent_is_destroyed_by_steady_hits(GameTestHelper helper) {
        run(helper, Buildings.METAL_EXTRACTOR_VILLAGERS, "mex", 0.2f, false);
    }

    /** AC-1: the same site under enemy Vindicators' attack orders. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = ATTACK_TICKS + 20, batch = BATCH)
    public static void partial_mex_at_20_percent_is_destroyed_by_attack_orders(GameTestHelper helper) {
        run(helper, Buildings.METAL_EXTRACTOR_VILLAGERS, "mex_order", 0.2f, true);
    }

    /** AC-2: a Barracks at ~15% construction is destroyed the same way (is the bug general?). */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = ATTACK_TICKS + 20, batch = BATCH)
    public static void partial_barracks_at_15_percent_is_destroyed_by_steady_hits(GameTestHelper helper) {
        run(helper, Buildings.BARRACKS, "barracks", 0.15f, false);
    }

    /** AC-3: a fully built metal extractor still goes down under the same hits. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = ATTACK_TICKS + 20, batch = BATCH)
    public static void built_mex_is_still_destroyed_by_steady_hits(GameTestHelper helper) {
        run(helper, Buildings.METAL_EXTRACTOR_VILLAGERS, "built_mex", 1f, false);
    }

    /** AC-3: a fully built Barracks still goes down under the same hits. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = ATTACK_TICKS + 20, batch = BATCH)
    public static void built_barracks_is_still_destroyed_by_steady_hits(GameTestHelper helper) {
        run(helper, Buildings.BARRACKS, "built_barracks", 1f, false);
    }
}
