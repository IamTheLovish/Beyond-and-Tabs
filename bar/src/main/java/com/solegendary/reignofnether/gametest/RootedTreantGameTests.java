package com.solegendary.reignofnether.gametest;

import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.TakeRoot;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.units.verdant.RootboundEffect;
import com.solegendary.reignofnether.unit.units.verdant.SentinelTreantUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.gametest.BigUnitGameTests.owner;
import static com.solegendary.reignofnether.gametest.BigUnitGameTests.spawn;

/**
 * The Verdant <b>Rooted Treant</b> (vehicles-design 2026-10-28, section 3.4): the Sentinel Treant's Take Root mode. It
 * roots in {@link SentinelTreantUnit#ROOT_TIMES} (root and uproot ticks, lovish's open question 5), can't move from
 * the first tick of rooting until it has fully uprooted, a move order uproots it and it then walks there (BAR's
 * deployables), and rooted it bursts a line of root spikes along the ground that hits only enemies. The mode is server
 * state saved with the unit.
 * <p>
 * Every test that waits schedules ALL its tick callbacks from its own body ({@link #everyTick}) and runs as a little
 * state machine on the tick number: vanilla's GameTestInfo iterates its tick-time map while it runs them, so a
 * runAfterDelay from inside a callback corrupts the iteration (CI 40630152099 crashed shard 1 that way, #300).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class RootedTreantGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** One step per tick, 1..ticks, all scheduled now; step returns true once the test is over (no more steps). */
    interface Step {
        boolean at(int tick);
    }

    private static void everyTick(GameTestHelper helper, int ticks, Step step) {
        final boolean[] over = { false };
        for (int t = 1; t <= ticks; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (!over[0])
                    over[0] = step.at(tick);
            });
        }
    }

    /** Ends the test (succeed when no problems, else fail with them) after discarding the units; true = steps over. */
    private static boolean finish(GameTestHelper helper, List<String> problems, List<Mob> units) {
        for (Mob m : units)
            if (m != null)
                m.discard();
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
        return true;
    }

    static double speed(SentinelTreantUnit t) {
        return t.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    static SentinelTreantUnit treant(GameTestHelper helper, String owner, double x, double z) {
        return (SentinelTreantUnit) spawn(helper, EntityRegistrar.SENTINEL_TREANT_UNIT.get(), owner, x, z);
    }

    /** A worker that never fights back, with a lot of health so it outlives every hit and the loss can be measured. */
    static Mob sturdyWorker(GameTestHelper helper, String owner, double x, double z) {
        Mob m = spawn(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, x, z);
        if (m != null) {
            AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null)
                hp.setBaseValue(500);
            m.setHealth(500);
        }
        return m;
    }

    static void rootNow(SentinelTreantUnit t, ServerLevel level) {
        t.toggleRoot();
        for (int i = 0; i < SentinelTreantUnit.ROOT_TIMES.rootTicks(); i++)
            t.tickRoot(level);
    }

    static boolean allMade(GameTestHelper helper, List<Mob> units, int expected) {
        long made = units.stream().filter(m -> m != null).count();
        if (made == expected)
            return true;
        for (Mob m : units)
            if (m != null)
                m.discard();
        helper.fail("could not create the Rooted Treant test units");
        return false;
    }

    /** What UnitUnstick reads, for a failure message. */
    static String unstickState(SentinelTreantUnit t) {
        return String.format("{pos %.2f %.2f %.2f, encased %s, standable %s, onGround %s, idle %s, target %s, nav done %s, noPhysics %s, rooted %s}",
            t.getX(), t.getY(), t.getZ(), com.solegendary.reignofnether.unit.UnitUnstick.isEncased(t),
            com.solegendary.reignofnether.unit.UnitUnstick.isOnStandableGround(t), t.onGround(), t.isIdle(),
            t.getTarget() == null ? "none" : t.getTarget().getType().toShortString(), t.getNavigation().isDone(), t.noPhysics, t.isRooted());
    }

    static double movedFrom(Mob m, double x0, double z0) {
        return Math.hypot(m.getX() - x0, m.getZ() - z0);
    }

    /**
     * The Take Root order (the generic ability order a button click sends) roots the Treant in exactly the tuned root
     * time, immobile from the first tick; fully rooted it takes ROOTED_TAKEN_MULT damage, but not while uprooting; the
     * order again uproots it in exactly the tuned uproot time, and only then can it move.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void rooted_treant_roots_and_uproots_on_the_tuned_times(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_times");
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        SentinelTreantUnit treant = treant(helper, owner, 7.5, 7.5);
        List<Mob> units = new ArrayList<>();
        units.add(treant);
        if (!allMade(helper, units, 1))
            return;
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
        treant.tickRoot(level);
        treant.invulnerableTime = 0;
        float u0 = treant.getHealth();
        treant.hurt(level.damageSources().generic(), 20f);
        float uLost = u0 - treant.getHealth();
        if (Math.abs(uLost - 20f) > 0.5f)
            problems.add("an uprooting Treant should take full damage (lost " + uLost + " of 20)");
        for (int t = 1; t < uproot - 1; t++)
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
        finish(helper, problems, units);
    }

    /**
     * In the live world a rooted Treant ordered to attack an enemy 9 blocks away (its melee goal pulls it) stays where
     * it is and stays rooted; a walking Treant given the same order beside it does walk (the control).
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void rooted_treant_cannot_move_while_rooted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_move"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        SentinelTreantUnit rooted = treant(helper, owner, 2.5, 10.5);
        SentinelTreantUnit walker = treant(helper, owner, 2.5, 3.5);
        Mob foeA = sturdyWorker(helper, foeOwner, 11.5, 10.5);
        Mob foeB = sturdyWorker(helper, foeOwner, 11.5, 3.5);
        List<Mob> units = new ArrayList<>();
        for (Mob m : new Mob[] { rooted, walker, foeA, foeB })
            units.add(m);
        if (!allMade(helper, units, 4))
            return;
        List<String> problems = new ArrayList<>();
        double[] start = new double[4];
        double[] walkedMost = new double[1];
        double[] last = new double[3];
        String[] jump = new String[1];
        everyTick(helper, 85, tick -> {
            if (tick > 1) {
                walkedMost[0] = Math.max(walkedMost[0], movedFrom(walker, start[2], start[3]));
                // the first tick the rooted Treant shifts: from where to where, and what UnitUnstick read then
                double step = Math.hypot(rooted.getX() - last[0], rooted.getZ() - last[2]);
                if (jump[0] == null && step > 0.05)
                    jump[0] = String.format("tick %d: %.2f %.2f %.2f -> state %s", tick, last[0], last[1], last[2], unstickState(rooted));
            }
            last[0] = rooted.getX();
            last[1] = rooted.getY();
            last[2] = rooted.getZ();
            if (tick == 1) {
                rootNow(rooted, level);
                start[0] = rooted.getX();
                start[1] = rooted.getZ();
                start[2] = walker.getX();
                start[3] = walker.getZ();
                rooted.setUnitAttackTargetForced(foeA);
                walker.setUnitAttackTargetForced(foeB);
            } else if (tick == 80) {
                double moved = movedFrom(rooted, start[0], start[1]);
                if (moved > 0.3 || !rooted.isRooted())
                    problems.add("a rooted Treant moved " + moved + " blocks (rooted " + rooted.isRooted() + "; first shift "
                        + (jump[0] == null ? "none seen" : jump[0]) + ")");
                // the control chases its foe 9 blocks off: the most it got from its start at any tick, so a chase that
                // reached the foe early and stood there still counts; on a miss, say where it was and why
                if (walkedMost[0] < 1.0)
                    problems.add(String.format("the walking control Treant only got %.2f blocks from its start: the check "
                        + "can't see movement (at %.2f, %.2f from %.2f, %.2f; navigation done %s; target %s; foe at %.2f, %.2f)",
                        walkedMost[0], walker.getX(), walker.getZ(), start[2], start[3], walker.getNavigation().isDone(),
                        walker.getTargetGoal().getTarget() == null ? "none" : walker.getTargetGoal().getTarget() == foeB ? "the foe" : "another",
                        foeB.getX(), foeB.getZ()));
                return finish(helper, problems, units);
            }
            return false;
        });
    }

    /**
     * A move order to a rooted Treant uproots it (the tuned uproot time, still immobile meanwhile) and it then walks to
     * the spot with no second order; a move order while still rooting cancels the root and it walks at once.
     */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void rooted_treant_move_order_uproots_then_walks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_order");
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        SentinelTreantUnit rooted = treant(helper, owner, 2.5, 10.5);
        SentinelTreantUnit rooting = treant(helper, owner, 2.5, 3.5);
        List<Mob> units = new ArrayList<>();
        units.add(rooted);
        units.add(rooting);
        if (!allMade(helper, units, 2))
            return;
        List<String> problems = new ArrayList<>();
        int uproot = SentinelTreantUnit.ROOT_TIMES.uprootTicks();
        double[] start = new double[4];
        BlockPos goalA = helper.absolutePos(new BlockPos(12, 2, 10)), goalB = helper.absolutePos(new BlockPos(12, 2, 3));
        everyTick(helper, 120, tick -> {
            if (tick == 1) {
                rootNow(rooted, level);
                rooting.toggleRoot();
                for (int i = 0; i < 10; i++)
                    rooting.tickRoot(level);
                start[0] = rooted.getX();
                start[1] = rooted.getZ();
                start[2] = rooting.getX();
                start[3] = rooting.getZ();
                new UnitActionItem(owner, UnitAction.MOVE, -1, new int[] { rooted.getId() }, goalA, BlockPos.ZERO).action(level);
                new UnitActionItem(owner, UnitAction.MOVE, -1, new int[] { rooting.getId() }, goalB, BlockPos.ZERO).action(level);
            } else if (tick == 3) {
                if (rooted.isRootWanted() || !rooted.isImmobile())
                    problems.add("a move order should start uprooting a rooted Treant (wanted " + rooted.isRootWanted() + ", immobile " + rooted.isImmobile() + ")");
                if (rooting.isImmobile())
                    problems.add("a move order while rooting should cancel the root at once");
            } else if (tick == uproot - 2) {
                double moved = movedFrom(rooted, start[0], start[1]);
                if (moved > 0.3)
                    problems.add("an uprooting Treant moved " + moved + " blocks before the uproot time was over");
            } else if (tick == uproot + 80) {
                double walkedA = movedFrom(rooted, start[0], start[1]), walkedB = movedFrom(rooting, start[2], start[3]);
                if (rooted.isImmobile() || walkedA < 1.5)
                    problems.add("the uprooted Treant should walk to the ordered spot without a second order (moved " + walkedA + ")");
                if (walkedB < 1.5)
                    problems.add("the Treant whose root was cancelled should walk to the ordered spot (moved " + walkedB + ")");
                return finish(helper, problems, units);
            }
            return false;
        });
    }

    /**
     * A stop order after a move order to a rooted Treant drops the held move: it still uproots, but stays where it is.
     */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void rooted_treant_stop_order_drops_the_held_move(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_stop");
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        SentinelTreantUnit treant = treant(helper, owner, 2.5, 7.5);
        List<Mob> units = new ArrayList<>();
        units.add(treant);
        if (!allMade(helper, units, 1))
            return;
        List<String> problems = new ArrayList<>();
        int uproot = SentinelTreantUnit.ROOT_TIMES.uprootTicks();
        double[] start = new double[2];
        BlockPos goal = helper.absolutePos(new BlockPos(12, 2, 7));
        everyTick(helper, 120, tick -> {
            if (tick == 1) {
                rootNow(treant, level);
                start[0] = treant.getX();
                start[1] = treant.getZ();
                new UnitActionItem(owner, UnitAction.MOVE, -1, new int[] { treant.getId() }, goal, BlockPos.ZERO).action(level);
            } else if (tick == 3) {
                if (treant.isRootWanted())
                    problems.add("the move order should have started uprooting the Treant");
                new UnitActionItem(owner, UnitAction.STOP, -1, new int[] { treant.getId() }).action(level);
            } else if (tick == uproot + 60) {
                double moved = movedFrom(treant, start[0], start[1]);
                if (treant.isImmobile())
                    problems.add("the Treant should have finished uprooting");
                if (moved > 0.5)
                    problems.add("a stop order should drop the held move, but the Treant walked " + moved + " blocks");
                return finish(helper, problems, units);
            }
            return false;
        });
    }

    /**
     * Things that are not orders keep a held move: an anchored Treant's idle walk back to its anchor (Unit's 50-tick
     * retreat check) firing during the uproot, and a Take Root press during the uproot (ignored there). Both Treants
     * still walk to the ordered spot once free.
     */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void rooted_treant_held_move_survives_the_walk_back_and_a_take_root_press(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_keep");
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        SentinelTreantUnit anchored = treant(helper, owner, 2.5, 10.5);
        SentinelTreantUnit pressed = treant(helper, owner, 2.5, 3.5);
        List<Mob> units = new ArrayList<>();
        units.add(anchored);
        units.add(pressed);
        if (!allMade(helper, units, 2))
            return;
        List<String> problems = new ArrayList<>();
        int uproot = SentinelTreantUnit.ROOT_TIMES.uprootTicks();
        double[] startX = new double[2];
        BlockPos goalA = helper.absolutePos(new BlockPos(12, 2, 10)), goalB = helper.absolutePos(new BlockPos(12, 2, 3));
        everyTick(helper, 120, tick -> {
            if (tick == 1) {
                rootNow(anchored, level);
                rootNow(pressed, level);
                // an anchor three blocks off, so the idle walk-back fires: due on the Treant's tick 3 from now
                anchored.setAnchor(helper.absolutePos(new BlockPos(2, 2, 13)));
                anchored.tickCount = 100 - 3;
                startX[0] = anchored.getX();
                startX[1] = pressed.getX();
                new UnitActionItem(owner, UnitAction.MOVE, -1, new int[] { anchored.getId() }, goalA, BlockPos.ZERO).action(level);
                new UnitActionItem(owner, UnitAction.MOVE, -1, new int[] { pressed.getId() }, goalB, BlockPos.ZERO).action(level);
            } else if (tick == 3) {
                if (anchored.isRootWanted() || pressed.isRootWanted())
                    problems.add("the move orders should have started uprooting both Treants");
                new UnitActionItem(owner, UnitAction.TAKE_ROOT, -1, new int[] { pressed.getId() }).action(level);
            } else if (tick == 10) {
                anchored.setAnchor(null);   // one walk-back is the case; no more pulling it home after that
            } else if (tick == uproot + 80) {
                double walkedA = anchored.getX() - startX[0], walkedB = pressed.getX() - startX[1];
                if (walkedA < 1.5)
                    problems.add("the anchor walk-back during the uproot dropped the held move (walked " + walkedA + " toward it)");
                if (walkedB < 1.5)
                    problems.add("a Take Root press during the uproot dropped the held move (walked " + walkedB + " toward it)");
                return finish(helper, problems, units);
            }
            return false;
        });
    }

    /**
     * A Take Root press is a new order: rooting drops a group move still waiting in the formation dispatch queue (else
     * it lands later and uproots the Treant), and uprooting drops the attack target it held while rooted.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void rooted_treant_take_root_press_drops_a_queued_move_and_the_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_press"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        SentinelTreantUnit walking = treant(helper, owner, 2.5, 3.5);
        SentinelTreantUnit rooted = treant(helper, owner, 2.5, 10.5);
        Mob foe = sturdyWorker(helper, foeOwner, 8.5, 10.5);
        List<Mob> units = new ArrayList<>();
        for (Mob m : new Mob[] { walking, rooted, foe })
            units.add(m);
        if (!allMade(helper, units, 3))
            return;
        List<String> problems = new ArrayList<>();
        everyTick(helper, 5, tick -> {
            if (tick == 1) {
                // a 20+ unit move's slot for the walking Treant, still waiting to dispatch, then the root press
                UnitServerEvents.queueFormationMove(List.of(Pair.<net.minecraft.world.entity.LivingEntity, BlockPos>of(walking, helper.absolutePos(new BlockPos(12, 2, 3)))));
                new UnitActionItem(owner, UnitAction.TAKE_ROOT, -1, new int[] { walking.getId() }).action(level);
                if (!walking.isRootWanted())
                    problems.add("the press should start rooting the walking Treant");
                if (UnitServerEvents.hasQueuedFormationMove(walking.getId()))
                    problems.add("rooting should drop the Treant's queued formation move");
                rootNow(rooted, level);
                rooted.setUnitAttackTargetForced(foe);
                if (rooted.getTargetGoal().getTarget() != foe)
                    problems.add("the rooted Treant should hold its attack target");
                new UnitActionItem(owner, UnitAction.TAKE_ROOT, -1, new int[] { rooted.getId() }).action(level);
                if (rooted.isRootWanted())
                    problems.add("the press should start uprooting the rooted Treant");
                if (rooted.getTargetGoal().getTarget() != null)
                    problems.add("uprooting should drop the attack target it held while rooted");
                return finish(helper, problems, units);
            }
            return false;
        });
    }

    /**
     * The stuck-unit rescue (UnitUnstick) leaves a rooted Treant where it is: in a small walled pit, with a target it
     * can't reach, an unrooted Treant (the control) is lifted out, a rooted one stays put and rooted. A rooted Treant
     * caught inside blocks is still freed.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void rooted_treant_is_not_lifted_out_of_a_pit_but_is_freed_from_blocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_pit"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        // two 3x3 pits walled three high (no step-up out): nine cells, under UnitUnstick.POCKET_MAX_CELLS
        for (int cz : new int[] { 4, 10 })
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2)
                        for (int y = 2; y <= 4; y++)
                            level.setBlock(helper.absolutePos(new BlockPos(4 + dx, y, cz + dz)), Blocks.STONE.defaultBlockState(), 3);
        SentinelTreantUnit rooted = treant(helper, owner, 4.5, 4.5);
        SentinelTreantUnit control = treant(helper, owner, 4.5, 10.5);
        SentinelTreantUnit buried = treant(helper, owner, 11.5, 12.5);
        Mob foe = sturdyWorker(helper, foeOwner, 11.5, 6.5);
        List<Mob> units = new ArrayList<>();
        for (Mob m : new Mob[] { rooted, control, buried, foe })
            units.add(m);
        if (!allMade(helper, units, 4))
            return;
        List<String> problems = new ArrayList<>();
        double[] start = new double[7];
        double[] mostOff = new double[3];
        String[] seen = new String[2];
        everyTick(helper, 250, tick -> {
            if (tick == 2) {
                seen[0] = unstickState(rooted);
                seen[1] = unstickState(buried);
            }
            if (tick > 1) {
                mostOff[0] = Math.max(mostOff[0], movedFrom(rooted, start[0], start[1]));
                mostOff[1] = Math.max(mostOff[1], movedFrom(control, start[2], start[3]));
                mostOff[2] = Math.max(mostOff[2], Math.sqrt(buried.distanceToSqr(start[4], start[5], start[6])));
            }
            if (tick == 1) {
                rootNow(rooted, level);
                rootNow(buried, level);
                rooted.setUnitAttackTargetForced(foe);   // a target it can't reach: not idle, so a pocket suspect
                control.setUnitAttackTargetForced(foe);
                BlockPos feet = buried.blockPosition();
                level.setBlock(feet, Blocks.OAK_LOG.defaultBlockState(), 3);
                level.setBlock(feet.above(), Blocks.OAK_LOG.defaultBlockState(), 3);
                start[0] = rooted.getX();
                start[1] = rooted.getZ();
                start[2] = control.getX();
                start[3] = control.getZ();
                start[4] = buried.getX();
                start[5] = buried.getY();
                start[6] = buried.getZ();
            } else if (tick == 240) {
                if (mostOff[1] < 1.5)
                    problems.add("the unrooted control was not lifted out of its pit (got " + mostOff[1] + " blocks off): the pit is no pocket");
                if (mostOff[0] > 0.3 || !rooted.isRooted())
                    problems.add("the stuck-unit rescue moved a rooted Treant " + mostOff[0] + " blocks (rooted " + rooted.isRooted() + ")");
                // the rescue lifts it onto the nearest cell it fits, here the top of the logs it was caught in
                if (mostOff[2] < 0.5 || com.solegendary.reignofnether.unit.UnitUnstick.isEncased(buried))
                    problems.add("a rooted Treant inside blocks should still be freed (got " + mostOff[2] + " blocks off; at tick 2 "
                        + seen[1] + "; now " + unstickState(buried) + ")");
                if (!problems.isEmpty())
                    problems.add("pit-rooted Treant at tick 2 " + seen[0] + ", now " + unstickState(rooted));
                return finish(helper, problems, units);
            }
            return false;
        });
    }

    /**
     * One root-spike line toward +x: hurts the enemies on the line within LINE_LENGTH, and not a friend or an ally of
     * another owner on the line, an enemy beside it, or an enemy past its end.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void rooted_treant_spike_line_hits_only_enemies_on_the_line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_line"), foeOwner = owner + "_foe", allyOwner = owner + "_ally";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var vill = EntityRegistrar.VILLAGER_UNIT.get();
        SentinelTreantUnit treant = treant(helper, owner, 2.5, 7.5);
        Mob foeNear = spawn(helper, vill, foeOwner, 5.5, 7.5);     // 3 blocks along the line
        Mob friend = spawn(helper, vill, owner, 7.0, 7.5);         // on the line, but ours
        Mob ally = spawn(helper, vill, allyOwner, 8.3, 7.5);       // on the line, an ally's
        Mob foeFar = spawn(helper, vill, foeOwner, 9.5, 7.5);      // 7 blocks along the line
        Mob foeBeside = spawn(helper, vill, foeOwner, 6.5, 11.5);  // 4 blocks off the line
        Mob foePast = spawn(helper, vill, foeOwner, 13.0, 7.5);    // 10.5 blocks: past the line's end
        List<Mob> units = new ArrayList<>();
        for (Mob m : new Mob[] { treant, foeNear, friend, ally, foeFar, foeBeside, foePast })
            units.add(m);
        if (!allMade(helper, units, 7))
            return;
        AlliancesServerEvents.addAlliance(owner, allyOwner);
        try {
            if (SentinelTreantUnit.LINE_LENGTH < 8 || SentinelTreantUnit.LINE_LENGTH >= 10.5f)
                problems.add("the spike line should reach 8 blocks (design 3.4), is " + SentinelTreantUnit.LINE_LENGTH);
            float n0 = foeNear.getHealth(), fr0 = friend.getHealth(), a0 = ally.getHealth(), f0 = foeFar.getHealth(),
                b0 = foeBeside.getHealth(), p0 = foePast.getHealth();
            int hits = treant.strikeRootLine(level, 1, 0);
            if (foeNear.getHealth() >= n0 || foeFar.getHealth() >= f0)
                problems.add("the spike line should hurt both enemies on it (near " + (n0 - foeNear.getHealth()) + ", far " + (f0 - foeFar.getHealth()) + ")");
            if (friend.getHealth() < fr0)
                problems.add("the spike line hurt a friend");
            if (ally.getHealth() < a0)
                problems.add("the spike line hurt an ally");
            if (foeBeside.getHealth() < b0 || foePast.getHealth() < p0)
                problems.add("the spike line hurt an enemy off the line or past its end");
            if (hits != 2)
                problems.add("the spike line should report 2 hits, reported " + hits);
        } finally {
            AlliancesServerEvents.removeAlliance(owner, allyOwner);
        }
        finish(helper, problems, units);
    }

    /**
     * In the live world a walking Treant beside an enemy fires no spike line in a whole LINE_EVERY plus the telegraph;
     * once rooted it fires its line on its own and hurts the enemy (its slam is off while rooted, so all of that loss
     * is the line's).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void rooted_treant_fires_its_spike_line_by_itself_when_rooted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_auto"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        SentinelTreantUnit treant = treant(helper, owner, 3.5, 7.5);
        Mob foe = sturdyWorker(helper, foeOwner, 9.5, 7.5);   // 6 blocks: in the line's reach
        List<Mob> units = new ArrayList<>();
        units.add(treant);
        units.add(foe);
        if (!allMade(helper, units, 2))
            return;
        List<String> problems = new ArrayList<>();
        int window = SentinelTreantUnit.LINE_EVERY + SentinelTreantUnit.LINE_TELEGRAPH_TICKS + 10;
        float[] h0 = new float[1];
        everyTick(helper, 2 * window + 2, tick -> {
            if (tick == window) {
                if (treant.linesFired != 0)
                    problems.add("a walking Treant fired " + treant.linesFired + " spike lines");
                rootNow(treant, level);
                treant.linesFired = 0;
                h0[0] = foe.getHealth();
            } else if (tick == 2 * window) {
                float lost = h0[0] - foe.getHealth();
                if (treant.linesFired < 1 || lost <= 0)
                    problems.add("a rooted Treant should fire its spike line at an enemy in reach (fired " + treant.linesFired + ", foe lost " + lost + ")");
                return finish(helper, problems, units);
            }
            return false;
        });
    }

    /**
     * The mode is saved with the unit: a rooted Treant reloads rooted and immobile and keeps its spike-line cooldown
     * (no free line right after a reload); one half-way through rooting reloads half-way and finishes on the
     * remaining ticks.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void rooted_treant_state_survives_save_and_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_save"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        var type = EntityRegistrar.SENTINEL_TREANT_UNIT.get();
        SentinelTreantUnit treant = treant(helper, owner, 7.5, 7.5);
        Mob foe = sturdyWorker(helper, foeOwner, 12.5, 7.5);   // 5 blocks: in the line's reach
        SentinelTreantUnit loaded = type.create(level), half = type.create(level);
        List<Mob> units = new ArrayList<>();
        for (Mob m : new Mob[] { treant, foe, loaded, half })
            units.add(m);
        if (!allMade(helper, units, 4))
            return;
        int root = SentinelTreantUnit.ROOT_TIMES.rootTicks();
        rootNow(treant, level);
        // one full line (telegraph and strike), then save with most of the cooldown still to run
        for (int t = 0; t < SentinelTreantUnit.LINE_TELEGRAPH_TICKS + 2 && treant.linesFired == 0; t++)
            treant.tickRoot(level);
        if (treant.linesFired != 1)
            problems.add("the rooted Treant should have struck one line before the save (struck " + treant.linesFired + ")");
        CompoundTag tag = treant.saveWithoutId(new CompoundTag());
        loaded.load(tag);
        if (!loaded.isRooted() || !loaded.isRootWanted())
            problems.add("a rooted Treant should reload rooted (rooted " + loaded.isRooted() + ")");
        if (!loaded.isImmobile() || speed(loaded) > 0.0001)
            problems.add("a reloaded rooted Treant must not be able to move (speed " + speed(loaded) + ")");
        for (int t = 0; t < 20; t++)
            loaded.tickRoot(level);
        if (loaded.linesFired != 0)
            problems.add("a reloaded Treant struck a new line before its saved cooldown ran out");

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
        finish(helper, problems, units);
    }

    /**
     * lovish (Oct 28): enemies the spike line roots are slowed and take damage over time, stacking the more they are
     * rooted. Each hit adds a Rootbound stack up to MAX_STACKS (the slow deepens with each, then holds at the cap); the
     * stacks deal damage over time; a friend on the line gets none; it wears off after SECONDS.
     */
    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void rooted_treant_spike_line_stacks_a_slow_and_damage_over_time(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "treant_bound"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 1, 1, 14, 14);
        SentinelTreantUnit treant = treant(helper, owner, 2.5, 7.5);
        Mob friend = sturdyWorker(helper, owner, 4.5, 7.5);
        Mob foe = sturdyWorker(helper, foeOwner, 6.5, 7.5);
        List<Mob> units = new ArrayList<>();
        for (Mob m : new Mob[] { treant, friend, foe })
            units.add(m);
        if (!allMade(helper, units, 3))
            return;
        List<String> problems = new ArrayList<>();
        int max = RootboundEffect.MAX_STACKS, wearsOff = RootboundEffect.SECONDS * 20;
        double[] base = new double[1];
        float[] h1 = new float[1];
        everyTick(helper, wearsOff + 30, tick -> {
            if (tick == 1) {
                base[0] = foe.getAttributeValue(Attributes.MOVEMENT_SPEED);
                double last = base[0];
                for (int i = 1; i <= max + 1; i++) {
                    foe.invulnerableTime = 0;
                    friend.invulnerableTime = 0;
                    int hits = treant.strikeRootLine(level, 1, 0);
                    if (hits != 1)
                        problems.add("strike " + i + " should hit exactly the foe, hit " + hits);
                    int stacks = RootboundEffect.stacks(foe);
                    double sp = foe.getAttributeValue(Attributes.MOVEMENT_SPEED);
                    if (stacks != Math.min(i, max))
                        problems.add("after " + i + " hits the foe should have " + Math.min(i, max) + " Rootbound stacks, has " + stacks);
                    if (i <= max && sp >= last - 1e-6)
                        problems.add("hit " + i + " should slow the foe further (" + sp + " vs " + last + ")");
                    if (i > max && Math.abs(sp - last) > 1e-6)
                        problems.add("the slow should hold at the cap after " + max + " stacks");
                    last = sp;
                }
                double want = base[0] * (1 - RootboundEffect.SLOW_PER_STACK * max);
                if (Math.abs(last - want) > base[0] * 0.01)
                    problems.add("at the cap the foe should walk at " + want + ", walks at " + last);
                if (RootboundEffect.stacks(friend) != 0 || friend.getHealth() < friend.getMaxHealth())
                    problems.add("the spike line hurt or rootbound a friend standing on it");
                treant.setNoAi(true);   // not rooted, it would slam the foe: the loss below can only be Rootbound
                h1[0] = foe.getHealth();
            } else if (tick == 72) {
                // the ticks on durations 60, 40 and 20 land by now (the one at application is inside the line hit's
                // frames): all three, and nothing beyond what four could deal
                float lost = h1[0] - foe.getHealth(), perTick = RootboundEffect.DAMAGE_PER_STACK * max;
                if (lost < 3 * perTick - 0.01f || lost > 4 * perTick + 0.01f)
                    problems.add("Rootbound at " + max + " stacks should deal " + perTick + " a second and nothing else hurt the foe (lost " + lost + " in 3.5 s)");
            } else if (tick == wearsOff + 20) {
                if (RootboundEffect.stacks(foe) != 0)
                    problems.add("Rootbound should wear off after " + RootboundEffect.SECONDS + " s");
                if (Math.abs(foe.getAttributeValue(Attributes.MOVEMENT_SPEED) - base[0]) > 1e-6)
                    problems.add("the foe should walk at its own speed once Rootbound wears off");
                return finish(helper, problems, units);
            }
            return false;
        });
    }
}
