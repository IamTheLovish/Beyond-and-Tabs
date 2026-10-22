package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Felt like the units weren't attacking each other" (lovish, Oct 22, a 1v1 against a bot with hundreds of units a
 * side: green and red blobs pressed into each other at the front with little fighting). These check the rules that
 * decide whether a unit in contact with the enemy fights, at the size of that match:
 * <ul>
 *   <li>two idle groups placed in contact both deal damage;</li>
 *   <li>an attack-moving unit takes an enemy in range on its way;</li>
 *   <li>an idle unit hit from outside its aggro range answers its attacker;</li>
 *   <li>a unit whose plain move order is blocked (it stands, not getting any closer) fights the enemy it touches
 *       instead of standing there until the move gives up;</li>
 *   <li>150 a side in contact: every unit with an enemy in its aggro range picks a target within seconds (no scan
 *       budget or throttle starves the crowd);</li>
 *   <li>150 a side, one attack-moving (the bot) and one on a formation move straight through it (a player's
 *       right-click): both sides fight.</li>
 * </ul>
 * Everything happens on glass pads far from the test grid (their own chunks held), so no other test's units are in
 * reach. The big ones log an "[Engage]" line with the measured acquisition times and damage.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CombatEngageGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PAD_Y = 205;
    static final int SETUP = 20;   // ticks between laying the pad and spawning (chunk sends, entity sections)

    // ------------------------------------------------------------------------------------------------ the pads

    /** A glass pad of {@code w} x {@code d} with a 3-high rim, its chunks held; {@code slot} keeps each test's pad apart. */
    record Pad(int x0, int z0, int w, int d, List<long[]> chunks) {
        double x(double dx) { return x0 + dx; }
        double z(double dz) { return z0 + dz; }
        BlockPos at(double dx, double dz) { return BlockPos.containing(x0 + dx, PAD_Y + 1, z0 + dz); }
    }

    static Pad pad(GameTestHelper helper, int slot, int w, int d) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        // 9000 west and north of the grid (the 8v8 stress sits 3000 west, the duels 3000 south, the soak east)
        int x0 = (((base.getX() - 9000) >> 4) << 4) + 2, z0 = (((base.getZ() - 9000 - slot * 400) >> 4) << 4) + 2;
        List<long[]> chunks = new ArrayList<>();
        for (int cx = (x0 - 16) >> 4; cx <= (x0 + w + 16) >> 4; cx++)
            for (int cz = (z0 - 16) >> 4; cz <= (z0 + d + 16) >> 4; cz++)
                chunks.add(new long[] { cx, cz });
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "combat engage pad");
        BlockState glass = Blocks.GLASS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < w; dx++)
            for (int dz = 0; dz < d; dz++) {
                boolean rim = dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1;
                level.setBlock(p.set(x0 + dx, PAD_Y, z0 + dz), glass, 2);
                for (int dy = 1; dy <= 6; dy++) {
                    p.set(x0 + dx, PAD_Y + dy, z0 + dz);
                    if (rim && dy <= 3)
                        level.setBlock(p, glass, 2);
                    else if (!level.getBlockState(p).isAir())
                        level.setBlock(p, air, 2);
                }
            }
        return new Pad(x0, z0, w, d, chunks);
    }

    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, String owner, double x, double z, float yaw,
                     List<Mob> made) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(x, PAD_Y + 1, z, yaw, 0);
        ((Unit) m).setOwnerName(owner);
        level.addFreshEntity(m);
        made.add(m);
        return m;
    }

    /**
     * A block of {@code count} units in rows of {@code perRow} along z, rank after rank going away from the enemy
     * ({@code facingEast}: the front rank is the largest x). Every third unit a Pillager when {@code mixed}.
     */
    static List<Mob> block(ServerLevel level, Pad pad, String owner, int count, int perRow, double frontX,
                           boolean facingEast, boolean mixed, List<Mob> made) {
        List<Mob> side = new ArrayList<>();
        double spacing = 1.3;
        double zStart = pad.d() / 2.0 - (perRow - 1) * spacing / 2.0;
        for (int i = 0; i < count; i++) {
            int rank = i / perRow, file = i % perRow;
            double x = frontX + (facingEast ? -rank * spacing : rank * spacing);
            double z = zStart + file * spacing;
            // melee in front, shooters behind them (a mixed army's usual line)
            boolean shooter = mixed && rank >= (count / perRow) * 2 / 3;
            Mob m = spawn(level, shooter ? EntityRegistrar.PILLAGER_UNIT.get() : EntityRegistrar.VINDICATOR_UNIT.get(),
                owner, pad.x(x), pad.z(z), facingEast ? -90 : 90, made);
            if (m != null)
                side.add(m);
        }
        return side;
    }

    /**
     * Runs {@code body} once all {@code expected} units are made and each has ticked a few ticks of its own
     * (ArenaReady's rule): the first CI run gave the orders 2 ticks after spawning on a freshly held far pad and the
     * movers never left their spawn ("move none, idle true" at the timeout) while units that needed no order fought.
     */
    static void whenReady(GameTestHelper helper, List<Mob> made, int expected, Runnable body) {
        // registered from the test body itself: onEachTick schedules one callback per remaining tick, which must not
        // happen from inside another scheduled callback (the schedule is being iterated then)
        boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0] || made.size() < expected)
                return;
            for (Mob m : made)
                if (m.isRemoved() || m.tickCount < ArenaReady.TICKS) {
                    if (helper.getTick() > SETUP + 160)
                        throw new GameTestAssertException("the units were not ticking after " + helper.getTick()
                            + " test ticks: " + ArenaReady.describe(helper.getLevel(), List.of(m)));
                    return;
                }
            done[0] = true;
            body.run();
        });
    }

    static void cleanUp(List<Mob> made, ServerLevel level, Pad pad) {
        for (Mob m : made) {
            UnitServerEvents.clearQueuedActions(m.getId());
            m.discard();
        }
        if (pad != null)
            GameTestChunks.releaseAll(level, pad.chunks());
    }

    static void notYet(String why) {
        throw new GameTestAssertException(why);
    }

    static float lost(List<Mob> side) {
        float lost = 0;
        for (Mob m : side)
            lost += m.getMaxHealth() - (m.isAlive() ? m.getHealth() : 0);
        return lost;
    }

    static int withTarget(List<Mob> side) {
        int n = 0;
        for (Mob m : side)
            if (m.isAlive() && m.getTarget() != null && m.getTarget().isAlive())
                n++;
        return n;
    }

    static boolean enemyWithin(Mob m, List<Mob> enemies, double range) {
        for (Mob e : enemies)
            if (e.isAlive() && e.distanceToSqr(m) <= range * range)
                return true;
        return false;
    }

    static String moveState(Mob m) {
        Unit u = (Unit) m;
        BlockPos mt = u.getMoveGoal() == null ? null : u.getMoveGoal().getMoveTarget();
        return "move " + (mt == null ? "none" : mt.toShortString()) + ", idle " + u.isIdle() + ", target "
            + (m.getTarget() == null ? "none" : m.getTarget().getType().toShortString());
    }

    // ------------------------------------------------------------------------------------------------ small fights

    /** Two idle groups of eight placed face to face: both sides land hits within a few seconds. */
    @GameTest(template = ARENA, timeoutTicks = SETUP + 260, batch = "combat_engage")
    public static void idle_groups_in_contact_both_deal_damage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pad pad = pad(helper, 0, 24, 16);
        String a = "gametest_engage_idle_a_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String b = "gametest_engage_idle_b_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Mob> made = new ArrayList<>();
        List<List<Mob>> sides = new ArrayList<>();
        helper.runAfterDelay(SETUP, () -> {
            sides.add(block(level, pad, a, 8, 4, 11.4, true, false, made));
            sides.add(block(level, pad, b, 8, 4, 12.6, false, false, made));
        });
        helper.succeedWhen(() -> {
            if (sides.size() < 2 || sides.get(0).size() < 8 || sides.get(1).size() < 8) {
                notYet("units not (all) created yet");
            }
            float lostA = lost(sides.get(0)), lostB = lost(sides.get(1));
            // two hits a side (6 damage each): both sides are fighting, not only one
            if (lostA < 12 || lostB < 12)
                notYet("damage taken: A " + lostA + ", B " + lostB + "; with a target: A " + withTarget(sides.get(0))
                    + "/8, B " + withTarget(sides.get(1)) + "/8 (front unit A: " + moveState(sides.get(0).get(0)) + ")");
            cleanUp(made, level, pad);
        });
    }

    /** Attack-moving past an enemy that stands within its aggro range, a unit takes it on. */
    @GameTest(template = ARENA, timeoutTicks = SETUP + 360, batch = "combat_engage")
    public static void attack_move_unit_engages_an_enemy_in_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pad pad = pad(helper, 1, 32, 16);
        String a = "gametest_engage_amove_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Mob> made = new ArrayList<>();
        Mob[] units = new Mob[2];
        helper.runAfterDelay(SETUP, () -> {
            units[0] = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), a, pad.x(3.5), pad.z(5.5), -90, made);
            units[1] = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), a + "_foe", pad.x(16.5), pad.z(10.5), 90, made);
            if (units[1] != null)
                EngagementStance.set(units[1], EngagementStance.HOLD_FIRE);   // the attacker's choice alone
        });
        whenReady(helper, made, 2, () -> {
            if (units[0] != null)
                UnitServerEvents.addActionItem(a, UnitAction.ATTACK_MOVE, -1, new int[] { units[0].getId() },
                    pad.at(29.5, 5.5), BlockPos.ZERO);
        });
        helper.succeedWhen(() -> {
            if (units[0] == null || units[1] == null)
                notYet("units not created yet");
            if (units[0].getTarget() != units[1])
                notYet("the attack-moving unit has not taken the enemy " + Math.round(units[0].distanceTo(units[1]))
                    + " blocks away (" + moveState(units[0]) + ")");
            cleanUp(made, level, pad);
        });
    }

    /**
     * An idle unit hit by an enemy standing outside its aggro range turns on it (retaliation), the way an idle BAR
     * unit answers fire it cannot see coming.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + 300, batch = "combat_engage")
    public static void idle_unit_hit_from_outside_its_aggro_range_retaliates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pad pad = pad(helper, 2, 24, 16);
        String a = "gametest_engage_ret_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Mob> made = new ArrayList<>();
        Mob[] units = new Mob[2];
        helper.runAfterDelay(SETUP, () -> {
            units[0] = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), a, pad.x(4.5), pad.z(8.5), -90, made);
            units[1] = spawn(level, EntityRegistrar.PILLAGER_UNIT.get(), a + "_foe", pad.x(14.5), pad.z(8.5), 90, made);
            if (units[0] != null) {
                AttributeInstance aggro = units[0].getAttribute(AttributeRegistrar.AGGRO_RANGE.get());
                if (aggro != null)
                    aggro.setBaseValue(1);   // its own scan cannot see the shooter 10 blocks off
            }
            if (units[1] != null)
                EngagementStance.set(units[1], EngagementStance.HOLD_FIRE);
        });
        whenReady(helper, made, 2, () -> {
            if (units[0] != null && units[1] != null)
                units[0].hurt(level.damageSources().mobAttack(units[1]), 2f);
        });
        helper.succeedWhen(() -> {
            if (units[0] == null || units[1] == null)
                notYet("units not created yet");
            if (units[0].getTarget() != units[1])
                notYet("the unit that was shot has not turned on the shooter (" + moveState(units[0]) + ", aggro "
                    + ((AttackerUnit) units[0]).getAggroRange() + ")");
            cleanUp(made, level, pad);
        });
    }

    /**
     * A plain move order that is blocked - the unit stands at a wall, not getting any closer - with an enemy in arm's
     * reach: it fights that enemy within a couple of seconds of stalling, instead of standing beside it until the
     * move gives up (three repaths, 7 s or more; in a crowd much longer). BAR's move does not chase, but its units
     * still fire at what is in range; for a melee unit that is what it touches.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + 520, batch = "combat_engage")
    public static void unit_blocked_on_a_move_order_fights_the_enemy_it_touches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pad pad = pad(helper, 3, 32, 16);
        String a = "gametest_engage_block_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Mob> made = new ArrayList<>();
        Mob[] units = new Mob[2];
        long[] contactAt = { -1 };
        String[] failed = { null };
        // a wall across the whole pad at x = 16: the far side cannot be reached
        BlockState glass = Blocks.GLASS.defaultBlockState();
        for (int dz = 1; dz < pad.d() - 1; dz++)
            for (int dy = 1; dy <= 3; dy++)
                level.setBlock(new BlockPos(pad.x0() + 16, PAD_Y + dy, pad.z0() + dz), glass, 2);
        helper.runAfterDelay(SETUP, () -> {
            units[0] = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), a, pad.x(6.5), pad.z(8.5), -90, made);
            // the enemy stands against the wall, on the mover's line, and does not fight (the mover's choice alone)
            units[1] = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), a + "_foe", pad.x(15.5), pad.z(9.3), 90, made);
            if (units[1] != null) {
                EngagementStance.set(units[1], EngagementStance.HOLD_FIRE);
                ((Unit) units[1]).setHoldPosition(true);
                units[1].setHealth(units[1].getMaxHealth());
            }
            if (units[0] != null) {
                // its own scan reaches only arm's length, so only the contact rule (not idle aggro from afar) can start it
                AttributeInstance aggro = units[0].getAttribute(AttributeRegistrar.AGGRO_RANGE.get());
                if (aggro != null)
                    aggro.setBaseValue(1);
            }
        });
        whenReady(helper, made, 2, () -> {
            if (units[0] != null)
                UnitServerEvents.addActionItem(a, UnitAction.MOVE, -1, new int[] { units[0].getId() },
                    pad.at(24.5, 8.5), BlockPos.ZERO);
        });
        helper.succeedWhen(() -> {
            if (units[0] == null || units[1] == null)
                notYet("units not created yet");
            if (failed[0] != null)
                notYet(failed[0]);   // sticky: a hit after the move gave up is not the rule under test
            long now = level.getGameTime();
            if (contactAt[0] < 0) {
                if (units[0].distanceTo(units[1]) > 2.2)
                    notYet("the mover has not reached the enemy at the wall yet (" + String.format(Locale.ROOT, "%.1f",
                        units[0].distanceTo(units[1])) + " blocks, " + moveState(units[0]) + ")");
                contactAt[0] = now;
            }
            boolean hit = units[1].getHealth() < units[1].getMaxHealth();
            if (!hit) {
                // 25 ticks to read as blocked (MoveToTargetBlockGoal.PROGRESS_WINDOW) + the 4-tick scan + a swing
                if (now - contactAt[0] > 100 && failed[0] == null)
                    failed[0] = "the blocked mover stood beside the enemy for " + (now - contactAt[0])
                        + " ticks without hitting it (" + moveState(units[0]) + ")";
                notYet("in contact for " + (now - contactAt[0]) + " ticks, no hit yet (" + moveState(units[0]) + ")");
            }
            cleanUp(made, level, pad);
        });
    }

    // ------------------------------------------------------------------------------------------------ at scale

    static final int PER_SIDE = 150;
    static final int PER_ROW = 15;

    /**
     * 150 a side (two thirds melee in front, one third Pillagers behind) placed with the fronts 1.2 blocks apart, all
     * idle. Every unit that has an enemy inside its aggro range at the start must pick a target within 5 s (at least
     * 95% of them: a starved scan - a per-tick budget, a throttle, a visibility check - would leave whole ranks
     * idle), and both sides must take damage.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + 420, batch = "combat_engage_big")
    public static void hundreds_in_contact_all_in_range_acquire_targets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pad pad = pad(helper, 4, 44, 24);
        String a = "gametest_engage_big_a_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String b = "gametest_engage_big_b_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Mob> made = new ArrayList<>();
        List<List<Mob>> sides = new ArrayList<>();
        Map<Mob, Long> firstTarget = new HashMap<>();
        List<Mob> inRange = new ArrayList<>();
        long[] start = { -1 };
        helper.runAfterDelay(SETUP, () -> {
            sides.add(block(level, pad, a, PER_SIDE, PER_ROW, 21.4, true, true, made));
            sides.add(block(level, pad, b, PER_SIDE, PER_ROW, 22.6, false, true, made));
        });
        // the clock starts once every unit is ticking (ArenaReady); who has an enemy in its aggro range then is judged
        whenReady(helper, made, 2 * PER_SIDE, () -> {
            start[0] = level.getGameTime();
            for (int s = 0; s < sides.size(); s++)
                for (Mob m : sides.get(s))
                    if (enemyWithin(m, sides.get(1 - s), ((AttackerUnit) m).getAggroRange()))
                        inRange.add(m);
        });
        boolean[] judged = { false };
        helper.onEachTick(() -> {
            if (start[0] < 0 || judged[0])
                return;
            long t = level.getGameTime() - start[0];
            for (List<Mob> side : sides)
                for (Mob m : side)
                    if (!firstTarget.containsKey(m) && m.isAlive() && m.getTarget() != null)
                        firstTarget.put(m, t);
            if (t < 200)
                return;
            judged[0] = true;
            if (sides.size() < 2 || sides.get(0).size() < PER_SIDE || sides.get(1).size() < PER_SIDE) {
                cleanUp(made, level, pad);
                helper.fail("could not create the units");
                return;
            }
            int got = 0, by100 = 0;
            List<Long> times = new ArrayList<>();
            for (Mob m : inRange) {
                Long first = firstTarget.get(m);
                if (first != null) {
                    got++;
                    times.add(first);
                    if (first <= 100)
                        by100++;
                }
            }
            times.sort(Long::compare);
            String p50 = times.isEmpty() ? "-" : String.valueOf(times.get(times.size() / 2));
            String p90 = times.isEmpty() ? "-" : String.valueOf(times.get(Math.min(times.size() - 1, times.size() * 9 / 10)));
            float lostA = lost(sides.get(0)), lostB = lost(sides.get(1));
            String line = String.format(Locale.ROOT, "[Engage] %d v %d idle in contact: %d of %d units with an enemy in "
                    + "aggro range took a target within 5 s (%d within 10 s), first-target tick p50 %s p90 %s; damage "
                    + "taken A %.0f B %.0f; with a target now A %d B %d", PER_SIDE, PER_SIDE, by100, inRange.size(), got,
                p50, p90, lostA, lostB, withTarget(sides.get(0)), withTarget(sides.get(1)));
            ReignOfNether.LOGGER.info(line);
            List<String> idle = new ArrayList<>();
            for (Mob m : inRange)
                if (!firstTarget.containsKey(m) && idle.size() < 3)
                    idle.add(m.getType().toShortString() + " " + moveState(m));
            cleanUp(made, level, pad);
            if (inRange.isEmpty())
                helper.fail("no unit had an enemy in aggro range - the blocks were not placed face to face");
            else if (by100 < inRange.size() * 0.95)
                helper.fail(line + "; never targeted e.g. " + idle);
            else if (lostA <= 0 || lostB <= 0)
                helper.fail(line + " - one side never hurt the other");
            else
                helper.succeed();
        });
    }

    /**
     * lovish's match, both orders at once: side A (as the bot sends its waves) attack-moves at side B, side B (a
     * player's right-click) is sent on a formation move straight through side A. Where they meet B's units are
     * blocked by A's bodies; they must fight there - not stand in the blob until their moves give up. Both sides
     * must deal real damage in 15 s, and B (the movers) at least a third of what A deals.
     */
    @GameTest(template = ARENA, timeoutTicks = SETUP + 520, batch = "combat_engage_big2")
    public static void hundreds_moving_into_an_attack_moving_army_both_fight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pad pad = pad(helper, 5, 60, 24);
        String a = "gametest_engage_wave_a_" + helper.absolutePos(BlockPos.ZERO).asLong();
        String b = "gametest_engage_wave_b_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<Mob> made = new ArrayList<>();
        List<List<Mob>> sides = new ArrayList<>();
        Map<Mob, Long> firstTarget = new HashMap<>();
        long[] start = { -1 };
        helper.runAfterDelay(SETUP, () -> {
            sides.add(block(level, pad, a, PER_SIDE, PER_ROW, 20.0, true, true, made));
            sides.add(block(level, pad, b, PER_SIDE, PER_ROW, 40.0, false, true, made));
        });
        whenReady(helper, made, 2 * PER_SIDE, () -> {
            int[] idsA = sides.get(0).stream().mapToInt(Mob::getId).toArray();
            int[] idsB = sides.get(1).stream().mapToInt(Mob::getId).toArray();
            // each side is sent to the other's starting spot: A fights its way there, B just moves
            UnitServerEvents.addActionItem(a, UnitAction.ATTACK_MOVE, -1, idsA, pad.at(46, pad.d() / 2.0), BlockPos.ZERO);
            UnitServerEvents.addActionItem(b, UnitAction.MOVE, -1, idsB, pad.at(14, pad.d() / 2.0), BlockPos.ZERO);
            start[0] = level.getGameTime();
        });
        boolean[] judged = { false };
        helper.onEachTick(() -> {
            if (start[0] < 0 || judged[0])
                return;
            long t = level.getGameTime() - start[0];
            for (List<Mob> side : sides)
                for (Mob m : side)
                    if (!firstTarget.containsKey(m) && m.isAlive() && m.getTarget() != null)
                        firstTarget.put(m, t);
            if (t < 300)
                return;
            judged[0] = true;
            if (sides.get(0).size() < PER_SIDE || sides.get(1).size() < PER_SIDE) {
                cleanUp(made, level, pad);
                helper.fail("could not create the units");
                return;
            }
            int[] fought = new int[2];
            for (int s = 0; s < 2; s++)
                for (Mob m : sides.get(s))
                    if (firstTarget.containsKey(m))
                        fought[s]++;
            float lostA = lost(sides.get(0)), lostB = lost(sides.get(1));
            // damage dealt by a side = what the other side lost
            float dealtA = lostB, dealtB = lostA;
            int movingB = 0;
            for (Mob m : sides.get(1))
                if (m.isAlive() && ((Unit) m).getMoveGoal() != null && ((Unit) m).getMoveGoal().getMoveTarget() != null
                        && m.getTarget() == null && enemyWithin(m, sides.get(0), 2.5))
                    movingB++;
            String line = String.format(Locale.ROOT, "[Engage] %d attack-moving v %d moving through them, 15 s: units "
                    + "that took a target A %d B %d; damage dealt A %.0f B %.0f; B units still on their move in contact "
                    + "with no target %d", PER_SIDE, PER_SIDE, fought[0], fought[1], dealtA, dealtB, movingB);
            ReignOfNether.LOGGER.info(line);
            cleanUp(made, level, pad);
            if (dealtA <= 0 && dealtB <= 0)
                helper.fail(line + " - the armies never fought (did they meet?)");
            else if (dealtA <= 0 || dealtB < dealtA / 3f)
                helper.fail(line + " - the side on a move order barely fought back");
            else
                helper.succeed();
        });
    }
}
