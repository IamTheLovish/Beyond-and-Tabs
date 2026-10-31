package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.pathfinding.WidePathMemo;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A wide unit chasing a target it cannot reach must not run the full-range vanilla search at every repath
 * (WidePathMemo; lovish's Oct 29 game: a Sun Colossus's search was 12-33 ms each on the server thread, 8 colossi
 * 6 ms of every tick). A Colossus is ordered onto an enemy standing on a 5 x 5 plateau seven blocks high: the ring
 * slots round it are good ground (so the chase searches for a way up rather than holding), but there is no way up, and
 * from the foot of the plateau the enemy is out of reach.
 * Over {@link #WINDOW} ticks of that chase it may search the whole range at most {@link #MAX_FULL} times (once per
 * {@link WidePathMemo#TICKS}, plus a StuckNudge sidestep's worth); before the memo it searched at every repath, about
 * every 20 ticks. It must have searched at least once, or the chase never reached the fallback and proves nothing.
 * Vanilla pathfinding (the rtsPathfinding gamerule off), as in every GameTest and in every chase in a real game.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WidePathMemoGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int WINDOW = 300;
    static final int MAX_FULL = 5;
    /** The cleared pocket: template x/z 0..15, y 2..TOP (a Colossus is ~6 blocks tall). */
    static final int TOP = 16;
    /** The plateau: x 10..14, z 6..10, stone from y 2 to PLATEAU_TOP; the enemy stands on it at x 12.5, z 8.5. */
    static final int PLATEAU_TOP = 8;

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_widememo_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /**
     * SkirmishGameTests.openGround and ArenaReady.sealed for a tall pocket: a stone floor at y 1, air up to {@link #TOP},
     * a stone lid over it, stone in every rim cell beside the pocket that is not a full block and on every fluid, a
     * falling block over the lid made stone. Every block goes through {@code undo}, put back when the test ends.
     */
    static void tallPocket(GameTestHelper helper, TestCleanup.BlockUndo undo) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -1; x <= 16; x++)
            for (int z = -1; z <= 16; z++) {
                boolean inside = x >= 0 && x <= 15 && z >= 0 && z <= 15;
                for (int y = 1; y <= TOP + 1; y++) {
                    BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
                    BlockState was = level.getBlockState(abs);
                    boolean pocket = y >= 2 && y <= TOP;
                    BlockState want;
                    if (inside)
                        want = pocket ? air : stone;
                    else
                        want = !was.getFluidState().isEmpty() || (pocket && !was.isCollisionShapeFullBlock(level, abs)) ? stone : null;
                    if (want != null && was != want)
                        undo.set(abs, want);
                }
                if (!inside)
                    continue;
                for (int y = TOP + 2; y <= 64; y++) {
                    BlockPos above = helper.absolutePos(new BlockPos(x, y, z));
                    BlockState state = level.getBlockState(above);
                    if (state.isAir())
                        continue;
                    if (state.getBlock() instanceof FallingBlock || !state.getFluidState().isEmpty())
                        undo.set(above, stone);
                    break;
                }
            }
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void wide_unit_chasing_an_unreachable_target_does_not_repeat_the_full_search(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestCleanup.BlockUndo undo = TestCleanup.blocks(helper);
        tallPocket(helper, undo);
        for (int x = 10; x <= 14; x++)
            for (int z = 6; z <= 10; z++)
                for (int y = 2; y <= PLATEAU_TOP; y++)
                    undo.set(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState());
        String mine = owner(helper, "colossus"), theirs = owner(helper, "colossus_foe");
        Runnable daylight = ArenaReady.noSunlight(mine, theirs);
        TestCleanup.atEnd(helper, daylight);
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        List<Entity> made = new ArrayList<>();
        TestCleanup.entities(helper, made);
        Mob colossus = PathingBudgetGameTests.unit(level, EntityRegistrar.SUN_COLOSSUS_UNIT.get(), mine,
            o.getX() + 3.5, o.getY() + 2, o.getZ() + 8.5, made);
        Mob foe = TerrainGameTests.dummy(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs,
            o.getX() + 12.5, o.getY() + PLATEAU_TOP + 1, o.getZ() + 8.5, made);
        if (colossus == null || foe == null) {
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the units");
            return;
        }
        WidePathMemo memo = ((WidePathMemo.Holder) colossus.getNavigation()).reignofnether$wideMemo();
        int[] ordered = { -1 };
        int[] before = { 0 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            ordered[0] = tick;
            before[0] = memo.fullSearches;
            PathingBudgetGameTests.attack(level, mine, colossus, foe);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0] || helper.getTick() - ordered[0] < WINDOW)
                return;
            done[0] = true;
            int full = memo.fullSearches - before[0];
            List<String> problems = new ArrayList<>();
            if (full < 1)
                problems.add("the Colossus never ran a full-range search on its unreachable target, so this proves "
                    + "nothing (target " + (colossus.getTarget() == foe ? "the dummy" : String.valueOf(colossus.getTarget()))
                    + ", nav done " + colossus.getNavigation().isDone() + "; " + ArenaReady.describe(level, made) + ")");
            if (full > MAX_FULL)
                problems.add("the Colossus ran the full-range search " + full + " times in " + WINDOW
                    + " ticks of chasing a target it cannot reach (at most " + MAX_FULL + "); "
                    + ArenaReady.around(colossus, foe));
            try {
                TerrainGameTests.finish(helper, problems);
            } finally {
                TerrainGameTests.cleanUp(made);
            }
        });
    }

    // Oct 31, lovish's game 03:14 (42 headhunters 18% of the server tick, ~5 ms vanilla searches, ticks to 184 ms):
    // the memo is every unit's, not only the wide ones'. A ranged unit (moveTo(target) every tick out of range or out
    // of sight) and a melee unit chase the same foe on the plateau; each may search at most MAX_SEARCHES times in
    // WINDOW ticks (once a second, WidePathMemo.REPEAT_TICKS, plus StuckNudge's sidesteps) and the whole range at most
    // MAX_FULL times. Before, the headhunter searched on every tick: ~300 searches, nearly all with the fallback.
    static final int MAX_SEARCHES = 30;

    static void narrowChase(GameTestHelper helper, net.minecraft.world.entity.EntityType<? extends Mob> type, String tag) {
        ServerLevel level = helper.getLevel();
        TestCleanup.BlockUndo undo = TestCleanup.blocks(helper);
        tallPocket(helper, undo);
        for (int x = 10; x <= 14; x++)
            for (int z = 6; z <= 10; z++)
                for (int y = 2; y <= PLATEAU_TOP; y++)
                    undo.set(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState());
        String mine = owner(helper, tag), theirs = owner(helper, tag + "_foe");
        Runnable daylight = ArenaReady.noSunlight(mine, theirs);
        TestCleanup.atEnd(helper, daylight);
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        List<Entity> made = new ArrayList<>();
        TestCleanup.entities(helper, made);
        Mob unit = PathingBudgetGameTests.unit(level, type, mine, o.getX() + 3.5, o.getY() + 2, o.getZ() + 8.5, made);
        Mob foe = TerrainGameTests.dummy(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs,
            o.getX() + 12.5, o.getY() + PLATEAU_TOP + 1, o.getZ() + 8.5, made);
        if (unit == null || foe == null) {
            TerrainGameTests.cleanUp(made);
            helper.fail("could not create the units");
            return;
        }
        WidePathMemo memo = ((WidePathMemo.Holder) unit.getNavigation()).reignofnether$wideMemo();
        int[] ordered = { -1 };
        int[] before = { 0, 0 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            ordered[0] = tick;
            before[0] = memo.searches;
            before[1] = memo.fullSearches;
            PathingBudgetGameTests.attack(level, mine, unit, foe);
        });
        helper.onEachTick(() -> {
            if (ordered[0] < 0 || done[0] || helper.getTick() - ordered[0] < WINDOW)
                return;
            done[0] = true;
            int searches = memo.searches - before[0], full = memo.fullSearches - before[1];
            List<String> problems = new ArrayList<>();
            if (searches < 1)
                problems.add("the " + tag + " never searched for its unreachable target, so this proves nothing (target "
                    + (unit.getTarget() == foe ? "the dummy" : String.valueOf(unit.getTarget())) + ", nav done "
                    + unit.getNavigation().isDone() + "; " + ArenaReady.describe(level, made) + ")");
            if (searches > MAX_SEARCHES)
                problems.add("the " + tag + " searched " + searches + " times in " + WINDOW + " ticks of chasing a target "
                    + "it cannot reach (at most " + MAX_SEARCHES + ")");
            if (full > MAX_FULL)
                problems.add("the " + tag + " ran the full-range search " + full + " times in " + WINDOW + " ticks (at most "
                    + MAX_FULL + "); " + ArenaReady.around(unit, foe));
            try {
                TerrainGameTests.finish(helper, problems);
            } finally {
                TerrainGameTests.cleanUp(made);
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void ranged_unit_chasing_an_unreachable_target_searches_once_a_second(GameTestHelper helper) {
        narrowChase(helper, EntityRegistrar.HEADHUNTER_UNIT.get(), "headhunter");
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void melee_unit_chasing_an_unreachable_target_searches_once_a_second(GameTestHelper helper) {
        narrowChase(helper, EntityRegistrar.VINDICATOR_UNIT.get(), "vindicator");
    }
}
