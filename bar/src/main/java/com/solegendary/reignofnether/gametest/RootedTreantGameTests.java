package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.TakeRoot;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.units.verdant.SentinelTreantUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.gametest.BigUnitGameTests.finish;
import static com.solegendary.reignofnether.gametest.BigUnitGameTests.owner;
import static com.solegendary.reignofnether.gametest.BigUnitGameTests.spawn;

/**
 * The Verdant <b>Rooted Treant</b> (vehicles-design 2026-10-28, section 3.4): the Sentinel Treant's Take Root mode. It
 * roots in {@link SentinelTreantUnit#ROOT_TIMES} (root and uproot ticks, lovish's open question 5), can't move from
 * the first tick of rooting until it has fully uprooted, and rooted it bursts a line of root spikes along the ground
 * that hits only enemies. The mode is server state saved with the unit.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class RootedTreantGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static double speed(SentinelTreantUnit t) {
        return t.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /**
     * The Take Root order (the generic ability order a button click sends) roots the Treant in exactly the tuned root
     * time, immobile from the first tick; rooted it takes ROOTED_TAKEN_MULT damage; the order again uproots it in exactly
     * the tuned uproot time, and only then can it move.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void rooted_treant_roots_and_uproots_on_the_tuned_times(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_times");
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var treant = (SentinelTreantUnit) spawn(helper, EntityRegistrar.SENTINEL_TREANT_UNIT.get(), owner, 7.5, 7.5);
        if (treant == null) {
            helper.fail("could not create the Sentinel Treant");
            return;
        }
        try {
            boolean hasButton = false;
            for (Ability a : treant.getAbilities().get())
                if (a instanceof TakeRoot)
                    hasButton = true;
            if (!hasButton)
                problems.add("the Sentinel Treant has no Take Root ability");
            double walk = speed(treant);
            int root = SentinelTreantUnit.ROOT_TIMES.rootTicks(), uproot = SentinelTreantUnit.ROOT_TIMES.uprootTicks();
            if (root != 30 || uproot != 20)
                problems.add("the tuned times should start at 1.5 s / 1 s (lovish's Q5 option B), got " + root + " / " + uproot + " ticks");

            new UnitActionItem(owner, UnitAction.TAKE_ROOT, -1, new int[] { treant.getId() }).action(level);
            if (!treant.isRootWanted())
                problems.add("the TAKE_ROOT order did not start rooting");
            for (int t = 0; t < root - 1; t++)
                treant.tickRoot(level);
            if (treant.isRooted())
                problems.add("rooting finished before the tuned root time");
            if (!treant.isImmobile() || speed(treant) > 0.0001)
                problems.add("a rooting Treant must not be able to move (speed " + speed(treant) + ")");
            treant.tickRoot(level);
            if (!treant.isRooted())
                problems.add("the Treant should be rooted after the tuned root time");

            float h0 = treant.getHealth();
            treant.hurt(level.damageSources().generic(), 20f);
            float lost = h0 - treant.getHealth();
            if (Math.abs(lost - 20f * SentinelTreantUnit.ROOTED_TAKEN_MULT) > 0.5f)
                problems.add("a rooted Treant should take " + SentinelTreantUnit.ROOTED_TAKEN_MULT + "x damage (lost " + lost + " of 20)");

            new UnitActionItem(owner, UnitAction.TAKE_ROOT, -1, new int[] { treant.getId() }).action(level);
            for (int t = 0; t < uproot - 1; t++)
                treant.tickRoot(level);
            if (!treant.isImmobile() || speed(treant) > 0.0001)
                problems.add("an uprooting Treant must stay put until the tuned uproot time is over");
            treant.tickRoot(level);
            if (treant.isImmobile() || treant.isRooted())
                problems.add("the Treant should be free after the tuned uproot time");
            if (Math.abs(speed(treant) - walk) > 0.0001)
                problems.add("an uprooted Treant should walk at its old speed (" + speed(treant) + " vs " + walk + ")");
            if (Math.abs(walk - SentinelTreantUnit.movementSpeed) > 0.0001)
                problems.add("the Sentinel Treant's walking speed changed");
        } finally {
            treant.discard();
        }
        finish(helper, problems);
    }

    /**
     * In the live world: a rooted Treant ordered to walk 8 blocks stays where it is; once uprooted the same order moves it.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void rooted_treant_cannot_move_while_rooted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_move");
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var treant = (SentinelTreantUnit) spawn(helper, EntityRegistrar.SENTINEL_TREANT_UNIT.get(), owner, 3.5, 7.5);
        if (treant == null) {
            helper.fail("could not create the Sentinel Treant");
            return;
        }
        treant.toggleRoot();
        for (int t = 0; t < SentinelTreantUnit.ROOT_TIMES.rootTicks(); t++)
            treant.tickRoot(level);
        double x0 = treant.getX(), z0 = treant.getZ();
        BlockPos goal = helper.absolutePos(new BlockPos(11, 2, 7));
        treant.getMoveGoal().setMoveTarget(goal);
        helper.runAfterDelay(40, () -> {
            double moved = Math.hypot(treant.getX() - x0, treant.getZ() - z0);
            if (!treant.isRooted() || moved > 0.3) {
                treant.discard();
                helper.fail("a rooted Treant walked " + moved + " blocks on a move order (rooted " + treant.isRooted() + ")");
                return;
            }
            treant.toggleRoot();
            helper.runAfterDelay(SentinelTreantUnit.ROOT_TIMES.uprootTicks() + 2, () -> {
                treant.getMoveGoal().setMoveTarget(goal);
                helper.runAfterDelay(60, () -> {
                    double after = Math.hypot(treant.getX() - x0, treant.getZ() - z0);
                    treant.discard();
                    if (treant.isImmobile() || after < 1.0)
                        helper.fail("an uprooted Treant should walk again (moved " + after + ", immobile " + treant.isImmobile() + ")");
                    else
                        helper.succeed();
                });
            });
        });
    }

    /**
     * One root-spike line toward +x: hurts the enemies on the line within LINE_LENGTH, and not a friend on the line, an
     * enemy beside it, or an enemy past its end.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void rooted_treant_spike_line_hits_only_enemies_on_the_line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_line"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var vill = EntityRegistrar.VILLAGER_UNIT.get();
        var treant = (SentinelTreantUnit) spawn(helper, EntityRegistrar.SENTINEL_TREANT_UNIT.get(), owner, 2.5, 7.5);
        Mob foeNear = spawn(helper, vill, foeOwner, 5.5, 7.5);     // 3 blocks along the line
        Mob friend = spawn(helper, vill, owner, 7.0, 7.5);         // on the line, but ours
        Mob foeFar = spawn(helper, vill, foeOwner, 9.5, 7.5);      // 7 blocks along the line
        Mob foeBeside = spawn(helper, vill, foeOwner, 6.5, 11.5);  // 4 blocks off the line
        Mob foePast = spawn(helper, vill, foeOwner, 13.0, 7.5);    // 10.5 blocks: past the line's end
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { treant, foeNear, friend, foeFar, foeBeside, foePast })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 6) {
                helper.fail("could not create the Rooted Treant test units");
                return;
            }
            if (SentinelTreantUnit.LINE_LENGTH < 8 || SentinelTreantUnit.LINE_LENGTH >= 10.5f)
                problems.add("the spike line should reach 8 blocks (design 3.4), is " + SentinelTreantUnit.LINE_LENGTH);
            float n0 = foeNear.getHealth(), fr0 = friend.getHealth(), f0 = foeFar.getHealth(), b0 = foeBeside.getHealth(), p0 = foePast.getHealth();
            int hits = treant.strikeRootLine(level, 1, 0);
            if (foeNear.getHealth() >= n0 || foeFar.getHealth() >= f0)
                problems.add("the spike line should hurt both enemies on it (near " + (n0 - foeNear.getHealth()) + ", far " + (f0 - foeFar.getHealth()) + ")");
            if (friend.getHealth() < fr0)
                problems.add("the spike line hurt a friend");
            if (foeBeside.getHealth() < b0 || foePast.getHealth() < p0)
                problems.add("the spike line hurt an enemy off the line or past its end");
            if (hits != 2)
                problems.add("the spike line should report 2 hits, reported " + hits);
        } finally {
            for (Mob m : all)
                m.discard();
        }
        finish(helper, problems);
    }

    /**
     * In the live world a rooted Treant fires its spike line on its own at an enemy in reach, within one LINE_EVERY
     * plus the telegraph; a walking Treant fires none.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void rooted_treant_fires_its_spike_line_by_itself_when_rooted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_auto"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var treant = (SentinelTreantUnit) spawn(helper, EntityRegistrar.SENTINEL_TREANT_UNIT.get(), owner, 3.5, 7.5);
        Mob foe = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), foeOwner, 9.5, 7.5);   // 6 blocks: out of melee, in the line
        if (treant == null || foe == null) {
            if (treant != null) treant.discard();
            if (foe != null) foe.discard();
            helper.fail("could not create the Rooted Treant test units");
            return;
        }
        if (treant.linesFired != 0) {
            treant.discard();
            foe.discard();
            helper.fail("a walking Treant fired a spike line");
            return;
        }
        treant.toggleRoot();
        for (int t = 0; t < SentinelTreantUnit.ROOT_TIMES.rootTicks(); t++)
            treant.tickRoot(level);
        float h0 = foe.getHealth();
        helper.runAfterDelay(SentinelTreantUnit.LINE_EVERY + SentinelTreantUnit.LINE_TELEGRAPH_TICKS + 10, () -> {
            float lost = h0 - foe.getHealth();
            int fired = treant.linesFired;
            treant.discard();
            foe.discard();
            if (fired < 1 || lost <= 0)
                helper.fail("a rooted Treant should fire its spike line at an enemy 6 blocks away (fired " + fired + ", foe lost " + lost + ")");
            else
                helper.succeed();
        });
    }

    /**
     * The mode is saved with the unit: a rooted Treant reloads rooted and immobile, and one half-way through rooting
     * reloads half-way and finishes on the remaining ticks.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void rooted_treant_state_survives_save_and_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_save");
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var type = EntityRegistrar.SENTINEL_TREANT_UNIT.get();
        var treant = (SentinelTreantUnit) spawn(helper, type, owner, 7.5, 7.5);
        SentinelTreantUnit loaded = type.create(level), half = type.create(level);
        try {
            if (treant == null || loaded == null || half == null) {
                helper.fail("could not create the Sentinel Treants");
                return;
            }
            int root = SentinelTreantUnit.ROOT_TIMES.rootTicks();
            treant.toggleRoot();
            for (int t = 0; t < root; t++)
                treant.tickRoot(level);
            CompoundTag tag = treant.saveWithoutId(new CompoundTag());
            loaded.load(tag);
            if (!loaded.isRooted() || !loaded.isRootWanted())
                problems.add("a rooted Treant should reload rooted (rooted " + loaded.isRooted() + ")");
            if (!loaded.isImmobile() || speed(loaded) > 0.0001)
                problems.add("a reloaded rooted Treant must not be able to move (speed " + speed(loaded) + ")");

            // half-way through rooting
            treant.toggleRoot();
            for (int t = 0; t < SentinelTreantUnit.ROOT_TIMES.uprootTicks(); t++)
                treant.tickRoot(level);
            if (treant.isImmobile())
                problems.add("the Treant did not uproot before the half-way check");
            treant.toggleRoot();
            int done = root / 2;
            for (int t = 0; t < done; t++)
                treant.tickRoot(level);
            half.load(treant.saveWithoutId(new CompoundTag()));
            if (!half.isRootWanted() || half.isRooted() || !half.isImmobile())
                problems.add("a half-rooted Treant should reload still rooting and immobile");
            for (int t = 0; t < root - done - 1; t++)
                half.tickRoot(level);
            if (half.isRooted())
                problems.add("a reloaded half-rooted Treant finished early");
            half.tickRoot(level);
            if (!half.isRooted())
                problems.add("a reloaded half-rooted Treant should finish on the remaining ticks");
        } finally {
            if (treant != null) treant.discard();
            if (loaded != null) loaded.discard();
            if (half != null) half.discard();
        }
        finish(helper, problems);
    }
}
