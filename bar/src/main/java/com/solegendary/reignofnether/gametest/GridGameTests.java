package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.blocks.ThicketCover;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.tide.FogBankServerEvents;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;
import com.solegendary.reignofnether.tide.TidesServerEvents;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitGrid;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Slice 1a of the AI faction (design/ai_faction.md section 6, lovish Oct 29: "Light Grid"): the Tides core's pools
 * play by the Light Grid's rules. Only a grid tile counts (real water gives nothing), a tile never douses fire, and an
 * enemy standing on a player's grid loses its cover (the grid is a sensor). Units are created but only the cover tests
 * add them to the level (cover is computed from the unit lists); a direct pass is limited to the test's owners, and
 * the real 1 Hz passes the keep test waits on only find its units on its own grid.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class GridGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /**
     * An enemy hidden in its own Fog Bank on the owner's grid is stripped of its cover; the owner's own hidden unit on
     * the same grid, and an enemy hidden on its own side's grid, keep theirs.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void grid_strips_cover_from_an_enemy_on_it_not_a_friend(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_grid_cover_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 1, 1, 14, 10);
        // a radius-4 grid, the foe and the friend 6 apart on it and the foe's home grid 5 past the friend: nobody is
        // within ThicketCover.REVEAL_RADIUS (4) of an enemy, so all three can hide
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos theirs = at.offset(8, 0, 0);
        var villager = EntityRegistrar.VILLAGER_UNIT.get();
        var foe = villager.create(level);
        var friend = villager.create(level);
        var foeAtHome = villager.create(level);
        try {
            var grid = TidepoolServerEvents.raise(level, owner, at, 4, 400);
            var foeGrid = TidepoolServerEvents.raise(level, foeOwner, theirs, 1, 400);
            if (grid == null || foeGrid == null || foe == null || friend == null || foeAtHome == null) {
                helper.fail("could not set up the grid cover test: " + TidepoolServerEvents.lastRefusal());
                return;
            }
            foe.moveTo(at.getX() - 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
            friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
            foeAtHome.moveTo(theirs.getX() + 0.5, theirs.getY(), theirs.getZ() + 0.5, 0, 0);
            foe.setOwnerName(foeOwner);
            friend.setOwnerName(owner);
            foeAtHome.setOwnerName(foeOwner);
            for (var u : List.of(foe, friend, foeAtHome))
                level.addFreshEntity(u);
            if (!TidesServerEvents.isWet(foe) || !TidesServerEvents.isWet(friend) || !TidesServerEvents.isWet(foeAtHome)) {
                helper.fail("test setup: a unit is not standing on a grid tile");
                return;
            }
            FogBankServerEvents.raise(level, foeOwner, foe.getId(), foe.getX(), foe.getY(), foe.getZ());
            FogBankServerEvents.raise(level, owner, friend.getId(), friend.getX(), friend.getY(), friend.getZ());
            FogBankServerEvents.raise(level, foeOwner, foeAtHome.getId(), foeAtHome.getX(), foeAtHome.getY(), foeAtHome.getZ());
            ThicketCover.refresh(level.getServer());
            if (!ThicketCover.isHidden(foe) || !ThicketCover.isHidden(friend) || !ThicketCover.isHidden(foeAtHome)) {
                helper.fail("precondition: the three units are not hidden in their Fog Banks");
                return;
            }
            UnitGrid.invalidate();   // the units joined this tick: the unit grid must see them
            int lit = TidepoolServerEvents.revealOnGrid(level, owner);
            ThicketCover.refresh(level.getServer());
            if (lit != 1)
                helper.fail("the grid should strip cover from exactly the one enemy on it, stripped " + lit);
            if (ThicketCover.isHidden(foe) || Concealment.isHiddenFrom(foe, owner))
                helper.fail("the enemy on the owner's grid is still hidden");
            if (!ThicketCover.isHidden(friend))
                helper.fail("the owner's own unit on its grid lost its cover");
            if (!ThicketCover.isHidden(foeAtHome))
                helper.fail("an enemy on its own side's grid lost its cover to the owner's grid");
            helper.succeed();
        } finally {
            TidepoolServerEvents.clearOwner(owner);
            TidepoolServerEvents.clearOwner(foeOwner);
            FogBankServerEvents.clearOwner(owner);
            FogBankServerEvents.clearOwner(foeOwner);
            for (var u : new Object[] {foe, friend, foeAtHome})
                if (u instanceof net.minecraft.world.entity.Entity e)
                    e.discard();
            ThicketCover.refresh(level.getServer());
        }
    }

    /**
     * The grid keeps sensing (PR 380 review r1): an enemy hidden in its Fog Bank on the owner's grid stays seen across
     * three real 1 Hz passes, so an attacker 8 blocks off keeps its target. A reveal that ran only on hidden units let
     * the cover take the enemy back every 2 s, and going under clears every attacker's target (ThicketCover.onHide,
     * the target sync in Unit.tick). Both units have no AI, so neither walks into the other's reveal radius, and both
     * hold fire: the attacker (aggro range 10, 8 off) would otherwise pick the enemy up again as soon as the grid
     * revealed it, and an enemy that targets the attacker is engaged and never hides - either hid the lost order, and
     * the test passed on the r1 code (PR 380 review r2). The order is a forced one, a player's attack order.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void grid_keeps_an_enemy_seen_across_passes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_grid_keep_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 1, 1, 14, 10);
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 5));
        var foe = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var attacker = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        Runnable cleanup = () -> {
            TidepoolServerEvents.clearOwner(owner);
            FogBankServerEvents.clearOwner(foeOwner);
            for (var u : new Object[] {foe, attacker})
                if (u instanceof net.minecraft.world.entity.Entity e)
                    e.discard();
            ThicketCover.refresh(level.getServer());
        };
        boolean scheduled = false;
        try {
            var grid = TidepoolServerEvents.raise(level, owner, at, 2, 400);
            if (grid == null || foe == null || attacker == null) {
                helper.fail("could not set up the grid keep test: " + TidepoolServerEvents.lastRefusal());
                return;
            }
            foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            attacker.moveTo(at.getX() + 8.5, at.getY(), at.getZ() + 0.5, 0, 0);
            foe.setOwnerName(foeOwner);
            attacker.setOwnerName(owner);
            foe.setNoAi(true);
            attacker.setNoAi(true);
            EngagementStance.set(foe, EngagementStance.HOLD_FIRE);
            EngagementStance.set(attacker, EngagementStance.HOLD_FIRE);
            level.addFreshEntity(foe);
            level.addFreshEntity(attacker);
            FogBankServerEvents.raise(level, foeOwner, foe.getId(), foe.getX(), foe.getY(), foe.getZ());
            ThicketCover.refresh(level.getServer());
            if (!ThicketCover.isHidden(foe)) {
                helper.fail("precondition: the enemy is not hidden in its Fog Bank");
                return;
            }
            UnitGrid.invalidate();   // the units joined this tick: the unit grid must see them
            if (TidepoolServerEvents.revealOnGrid(level, owner) != 1 || Concealment.isConcealed(foe)) {
                helper.fail("precondition: the grid did not strip the enemy's cover");
                return;
            }
            attacker.setUnitAttackTargetForced(foe);
            if (attacker.getTargetGoal().getTarget() != foe) {
                helper.fail("precondition: the attacker did not take the enemy as its target");
                return;
            }
            long start = level.getGameTime();
            // 70 ticks: three real grid passes (one every EXPIRY_PASS_TICKS) and past the first reveal's end (40)
            helper.runAfterDelay(70, () -> {
                try {
                    if (ThicketCover.isHidden(foe) || Concealment.isHiddenFrom(foe, owner))
                        helper.fail("the enemy on the grid is hidden again after " + (level.getGameTime() - start)
                            + " ticks: " + ThicketCover.diagnose(level, foe));
                    if (attacker.getTargetGoal().getTarget() != foe)
                        helper.fail("the attacker lost its target on the grid after " + (level.getGameTime() - start)
                            + " ticks (the enemy went back under cover between passes): " + ThicketCover.diagnose(level, foe));
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
            scheduled = true;
        } finally {
            if (!scheduled)
                cleanup.run();
        }
    }

    /**
     * The maps are dry and the faction's bonus is the grid's: a tidal unit standing in real water gets no speed
     * modifier and no regen; the same unit on a grid tile gets both. The unit is never added to the level.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void grid_real_water_gives_no_bonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_grid_water_" + helper.absolutePos(BlockPos.ZERO).asLong();
        SkirmishGameTests.openGround(helper, 1, 1, 12, 6);
        BlockPos water = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos tile = water.offset(6, 0, 0);
        var tidal = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        // a still source with no neighbour updates; removed again below, in this tick, before it can spread
        level.setBlock(water, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        try {
            var grid = TidepoolServerEvents.raise(level, owner, tile, 1, 400);
            if (grid == null || tidal == null) {
                helper.fail("could not set up the water test: " + TidepoolServerEvents.lastRefusal());
                return;
            }
            tidal.addTag(TidesServerEvents.TIDAL_TAG);
            tidal.setOwnerName(owner);
            tidal.moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 0, 0);
            tidal.setHealth(tidal.getMaxHealth() - 6);
            float hp = tidal.getHealth();
            TidesServerEvents.update(tidal);
            if (TidesServerEvents.isWet(tidal) || TidesServerEvents.hasSpeedBonus(tidal))
                helper.fail("real water gave a tidal unit the grid speed bonus");
            if (tidal.getHealth() != hp)
                helper.fail("real water healed a tidal unit");
            tidal.moveTo(tile.getX() + 0.5, tile.getY(), tile.getZ() + 0.5, 0, 0);
            TidesServerEvents.update(tidal);
            if (!TidesServerEvents.hasSpeedBonus(tidal) || tidal.getHealth() <= hp)
                helper.fail("a tidal unit on a grid tile got no speed bonus or no regen");
            helper.succeed();
        } finally {
            level.setBlock(water, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            TidepoolServerEvents.clearOwner(owner);
            if (tidal != null)
                tidal.discard();
        }
    }

    /**
     * Light doesn't put out fire: a burning unit inside a grid tile keeps burning (the tidepool used to clear it).
     * Fire next to a cell still evaporates it - tidepool_fire_evaporates_and_drain_dries covers that half.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void grid_does_not_put_out_fire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_grid_fire_" + helper.absolutePos(BlockPos.ZERO).asLong();
        SkirmishGameTests.openGround(helper, 1, 1, 8, 8);
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        var burning = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        try {
            var grid = TidepoolServerEvents.raise(level, owner, at, 1, 400);
            if (grid == null || burning == null || !TidepoolServerEvents.isTidepool(level, at)) {
                helper.fail("could not set up the fire test: " + TidepoolServerEvents.lastRefusal());
                return;
            }
            burning.setOwnerName(owner + "_foe");
            burning.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            burning.setSecondsOnFire(10);
            if (!burning.isOnFire()) {
                helper.fail("precondition: the unit is not burning");
                return;
            }
            var tidepool = BlockRegistrar.TIDEPOOL.get();
            tidepool.entityInside(level.getBlockState(at), level, at, burning);
            if (!burning.isOnFire())
                helper.fail("a grid tile put out a burning unit");
            helper.succeed();
        } finally {
            TidepoolServerEvents.clearOwner(owner);
            if (burning != null)
                burning.discard();
        }
    }
}
