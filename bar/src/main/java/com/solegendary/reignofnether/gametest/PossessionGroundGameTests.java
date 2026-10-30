package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.PossessionServer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A possessed unit driven by its client's position claims (PossessionServer.applyClaim) must read on-ground while it
 * stands on the ground. It did not (Oct 27 investigation, claude/inbox-possession-onground-oct27.md): the claims path
 * zeroes the velocity before vanilla travel, travel's move is then a zero move - the last write to onGround each tick -
 * and leaves it false, so the server counted a standing unit as airborne: after 40 ticks the hover rule pushed it into
 * the floor and corrected the client, and the sprint-jump boost (only on the ground) was lost, so an honest sprint-jump
 * claim was corrected too. A unit hovering in the air must still be pulled down.
 *
 * Every test schedules ALL its tick callbacks from its own body ({@link #everyTick}) and runs as a little state machine
 * on the tick number, as #300's DragonDiveGameTests does: vanilla's GameTestInfo iterates its tick-time map while it
 * runs them, so a runAfterDelay from inside a callback corrupts the iteration (fastutil's "this.wrapped is null" in
 * GameTestInfo.tickInternal - CI 40646416597 crashed the whole shard-8 server that way, #308 review round 2).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PossessionGroundGameTests {

    static final String ARENA = HeroGameTests.ARENA;
    /** Ticks of standing claims: past the hover rule's 40 (PossessionServer.HOVER_TICKS) with room to spare. */
    static final int STAND_TICKS = 70;
    /** The tick each test possesses its unit on (it has stood on the pad a few ticks first). */
    static final int CLAIM_AT = 6;

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

    /** Releases the unit, discards it, clears the test's blocks, then succeeds or fails; says the steps are over. */
    private static boolean finish(GameTestHelper helper, UUID uuid, Mob unit, Runnable clear, List<String> problems) {
        PossessionServer.release(uuid, null);
        unit.discard();
        clear.run();
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
        return true;
    }

    /** Possesses {@code unit} headless; false (with the problem noted) when it can't. */
    private static boolean possess(ServerLevel level, String me, UUID uuid, Mob unit, List<String> problems) {
        String why = PossessionServer.claim(level, me, uuid, unit.getId(), true);
        if (why != null)
            problems.add("could not possess the unit: " + why);
        return why == null;
    }

    /**
     * Claims at the unit's own standing spot every tick for 70 ticks: it reads on-ground, never sinks, no claim is
     * corrected - and then an honest sprint-jump claim (just past a sprint's reach, within the jump's boost) is accepted.
     */
    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void possession_standing_unit_reads_on_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = HeroGameTests.owner(helper, "standground");
        UUID uuid = UUID.nameUUIDFromBytes(me.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 248;
        Runnable clearPad = HeroGameTests.possessionPad(level, base, y, 8, 2);
        Mob unit = HeroGameTests.padUnit(level, me, base.getX() - 3.5, y, base.getZ() + 0.5);
        if (unit == null) {
            clearPad.run();
            helper.fail("could not create a vindicator unit");
            return;
        }
        List<String> problems = new ArrayList<>();
        double[] y0 = { 0 };
        int[] seq = { 1 };
        int[] airborneTicks = { 0 };
        int[] corrected = { 0 }, accepted = { 0 };
        double[] reach = { 0 };
        final int check = CLAIM_AT + STAND_TICKS + 1, jumpChecked = check + 2;
        everyTick(helper, jumpChecked, tick -> {
            if (tick < CLAIM_AT)
                return false;
            if (tick == CLAIM_AT) {
                if (!possess(level, me, uuid, unit, problems))
                    return finish(helper, uuid, unit, clearPad, problems);
                y0[0] = unit.getY();
                return false;
            }
            if (tick < check) {
                // an honest client standing still: every tick it claims exactly where the unit is
                if (!unit.onGround())
                    airborneTicks[0]++;
                PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, false, false,
                    unit.getX(), unit.getY(), unit.getZ(), seq[0]++);
                return false;
            }
            var p = PossessionServer.of(uuid);
            if (p == null) {
                problems.add("the possession was lost");
                return finish(helper, uuid, unit, clearPad, problems);
            }
            double speed = unit.getAttributeValue(Attributes.MOVEMENT_SPEED);
            if (tick == check) {
                if (!unit.onGround())
                    problems.add("a unit standing on the pad under claims does not read on-ground");
                if (airborneTicks[0] > 2)
                    problems.add("it read airborne " + airborneTicks[0] + " of " + STAND_TICKS + " standing ticks");
                if (p.corrections > 0)
                    problems.add(p.corrections + " standing claims were corrected (the hover rule fires on solid ground)");
                if (Math.abs(unit.getY() - y0[0]) > 0.01)
                    problems.add(String.format(Locale.ROOT, "the standing unit moved %.3f blocks up or down", unit.getY() - y0[0]));
                // an honest sprint-jump: the first tick of a jump from the ground, a little past a sprint step's
                // reach - inside the 0.2 boost vanilla gives a sprint-jump (PossessionServer.SPRINT_JUMP_BOOST)
                reach[0] = PossessionServer.walkPerTick(speed, true) + PossessionServer.SPRINT_JUMP_BOOST * 0.75;
                corrected[0] = p.corrections;
                accepted[0] = p.acceptedClaims;
                PossessionServer.applyMove(uuid, unit.getId(), 1, 0, -90, 0, true, false, true,
                    unit.getX() + reach[0], unit.getY() + 0.4, unit.getZ(), seq[0]++);
                return false;
            }
            if (tick < jumpChecked)
                return false;
            if (p.corrections > corrected[0] || p.acceptedClaims <= accepted[0])
                problems.add(String.format(Locale.ROOT,
                    "an honest sprint-jump claim (%.3f blocks, sprint reach %.3f + boost) was corrected "
                        + "(corrections %d -> %d, accepted %d -> %d)", reach[0],
                    PossessionServer.walkPerTick(speed, true), corrected[0], p.corrections, accepted[0], p.acceptedClaims));
            return finish(helper, uuid, unit, clearPad, problems);
        });
    }

    /**
     * The other side of the rule: a client claiming its unit hovers 1.5 blocks above the pad, tick after tick, is
     * corrected and the unit is pulled down to the ground - on-ground comes from the server's world, not the claim.
     */
    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void possession_hovering_claims_are_still_pulled_down(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = HeroGameTests.owner(helper, "hover");
        UUID uuid = UUID.nameUUIDFromBytes(me.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 254;
        Runnable clearPad = HeroGameTests.possessionPad(level, base, y, 8, 2);
        Mob unit = HeroGameTests.padUnit(level, me, base.getX() - 3.5, y, base.getZ() + 0.5);
        if (unit == null) {
            clearPad.run();
            helper.fail("could not create a vindicator unit");
            return;
        }
        List<String> problems = new ArrayList<>();
        double[] floor = { 0 };
        int[] seq = { 1 };
        double[] highest = { 0 };
        final int check = CLAIM_AT + 81;
        everyTick(helper, check, tick -> {
            if (tick < CLAIM_AT)
                return false;
            if (tick == CLAIM_AT) {
                if (!possess(level, me, uuid, unit, problems))
                    return finish(helper, uuid, unit, clearPad, problems);
                floor[0] = unit.getY();
                return false;
            }
            if (tick < check) {
                highest[0] = Math.max(highest[0], unit.getY() - floor[0]);
                PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, false, false,
                    unit.getX(), floor[0] + 1.5, unit.getZ(), seq[0]++);
                return false;
            }
            var p = PossessionServer.of(uuid);
            if (p == null) {
                problems.add("the possession was lost");
                return finish(helper, uuid, unit, clearPad, problems);
            }
            if (p.corrections < 1)
                problems.add("80 ticks of hovering claims were never corrected");
            if (unit.getY() - floor[0] > 0.3)
                problems.add(String.format(Locale.ROOT, "the hovering unit was not pulled down: %.2f above the pad "
                    + "after 80 ticks (highest %.2f)", unit.getY() - floor[0], highest[0]));
            if (highest[0] > 1.6)
                problems.add(String.format(Locale.ROOT, "the hovering claims lifted the unit %.2f blocks", highest[0]));
            return finish(helper, uuid, unit, clearPad, problems);
        });
    }

    /** A real jump's apex (vanilla: 0.42 up, then -0.08 and x0.98 a tick) plus a step's 0.6, with a little float slack. */
    static final double MOST_RISE = 1.2522 + 0.6 + 0.05;

    /**
     * A lying client that holds jump and claims 1.2 blocks up every tick, on the ground or not: once a standing unit
     * reads on-ground (this PR), each landing must not hand it a fresh climb budget. It must never rise more than a real
     * jump's apex plus a step (about 1.85) above the ground it last stood on, and the lie is corrected. Before the cap,
     * the grounded allowance (+1.2 on air ticks 0 and 1, then 0.45 a tick) let it rise ~4.6 blocks in 7 ticks and
     * refilled on every landing (Mode A review of #308, round 1).
     */
    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void possession_repeated_jump_claims_never_rise_past_a_jump(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = HeroGameTests.owner(helper, "jumpclimb");
        UUID uuid = UUID.nameUUIDFromBytes(me.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 236;
        Runnable clearPad = HeroGameTests.possessionPad(level, base, y, 8, 2);
        Mob unit = HeroGameTests.padUnit(level, me, base.getX() - 3.5, y, base.getZ() + 0.5);
        if (unit == null) {
            clearPad.run();
            helper.fail("could not create a vindicator unit");
            return;
        }
        List<String> problems = new ArrayList<>();
        double[] floor = { 0 };   // the pad is flat: the last ground the unit stood on is always the floor
        int[] seq = { 1 };
        double[] highest = { 0 };
        int[] highestAt = { 0 };
        final int check = CLAIM_AT + 101;
        everyTick(helper, check, tick -> {
            if (tick < CLAIM_AT)
                return false;
            if (tick == CLAIM_AT) {
                if (!possess(level, me, uuid, unit, problems))
                    return finish(helper, uuid, unit, clearPad, problems);
                floor[0] = unit.getY();
                return false;
            }
            if (tick < check) {
                if (unit.getY() - floor[0] > highest[0]) {
                    highest[0] = unit.getY() - floor[0];
                    highestAt[0] = tick - CLAIM_AT;
                }
                PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, true, false,
                    unit.getX(), unit.getY() + 1.2, unit.getZ(), seq[0]++);
                return false;
            }
            var p = PossessionServer.of(uuid);
            if (p == null) {
                problems.add("the possession was lost");
                return finish(helper, uuid, unit, clearPad, problems);
            }
            highest[0] = Math.max(highest[0], unit.getY() - floor[0]);
            if (highest[0] > MOST_RISE)
                problems.add(String.format(Locale.ROOT, "jump claims of +1.2 every tick lifted the unit %.2f blocks "
                    + "above its ground (tick %d), more than a jump's apex plus a step (%.2f)", highest[0],
                    highestAt[0], MOST_RISE));
            if (p.corrections < 1)
                problems.add("100 ticks of impossible jump claims were never corrected");
            return finish(helper, uuid, unit, clearPad, problems);
        });
    }

    /** A real jump's height after its first two ticks: 0.42, then (0.42 - 0.08) x 0.98 more. */
    static final double TAP_JUMP_TWO_TICKS = 0.42 + (0.42 - 0.08) * 0.98;

    /**
     * Network jitter: a tap-jump's two packets - jump held on the first, released on the second - reach the server in
     * one tick. Only the newest claim is applied, and its jump flag is off; the jump still happened on the ground, so the
     * claim must count as launched and be accepted, not put back on the floor. Before the fix only the last packet's
     * jump flag counted (#308 review round 2, non-blocking): the honest tap-jump was capped at a step and corrected.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void possession_tap_jump_bunched_by_jitter_still_counts_as_a_jump(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = HeroGameTests.owner(helper, "tapjump");
        UUID uuid = UUID.nameUUIDFromBytes(me.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 242;
        Runnable clearPad = HeroGameTests.possessionPad(level, base, y, 8, 2);
        Mob unit = HeroGameTests.padUnit(level, me, base.getX() - 3.5, y, base.getZ() + 0.5);
        if (unit == null) {
            clearPad.run();
            helper.fail("could not create a vindicator unit");
            return;
        }
        List<String> problems = new ArrayList<>();
        double[] floor = { 0 };
        int[] seq = { 1 };
        int[] corrected = { 0 }, accepted = { 0 };
        final int jumpAt = CLAIM_AT + 11, check = jumpAt + 2;
        everyTick(helper, check, tick -> {
            if (tick < CLAIM_AT)
                return false;
            if (tick == CLAIM_AT) {
                if (!possess(level, me, uuid, unit, problems))
                    return finish(helper, uuid, unit, clearPad, problems);
                floor[0] = unit.getY();
                return false;
            }
            if (tick < jumpAt) {
                // stand still a while (honest claims at its own spot) so it reads on-ground
                PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, false, false,
                    unit.getX(), unit.getY(), unit.getZ(), seq[0]++);
                return false;
            }
            var p = PossessionServer.of(uuid);
            if (p == null) {
                problems.add("the possession was lost");
                return finish(helper, uuid, unit, clearPad, problems);
            }
            if (tick == jumpAt) {
                if (!unit.onGround())
                    problems.add("set-up: the standing unit does not read on-ground before the jump");
                corrected[0] = p.corrections;
                accepted[0] = p.acceptedClaims;
                // both of the tap-jump's client ticks arrive before the server's next tick
                PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, true, false,
                    unit.getX(), floor[0] + 0.42, unit.getZ(), seq[0]++);
                PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, false, false,
                    unit.getX(), floor[0] + TAP_JUMP_TWO_TICKS, unit.getZ(), seq[0]++);
                return false;
            }
            if (tick < check)
                return false;
            if (p.corrections > corrected[0] || p.acceptedClaims <= accepted[0])
                problems.add(String.format(Locale.ROOT, "an honest tap-jump whose two packets came in one tick was "
                    + "corrected (corrections %d -> %d, accepted %d -> %d)", corrected[0], p.corrections, accepted[0],
                    p.acceptedClaims));
            if (unit.getY() - floor[0] < TAP_JUMP_TWO_TICKS - 0.1)
                problems.add(String.format(Locale.ROOT, "the tap-jump was put back down: %.2f above the pad, the claim "
                    + "was %.2f", unit.getY() - floor[0], TAP_JUMP_TWO_TICKS));
            return finish(helper, uuid, unit, clearPad, problems);
        });
    }

    /**
     * Leaving a ladder at its top: an honest client climbs a two-block ladder against a stone wall, its feet pass the
     * ladder's top, and it steps over onto the wall. For a tick it is above the ladder with nothing under it - neither
     * on the ground nor climbing - and it never jumped; climbing must count as launched (as a jump does), so the claim
     * is accepted and not put back down to the last ladder rung. Before the fix it was put back and corrected (#308
     * review round 2, non-blocking; the same holds for climbing out of water).
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void possession_leaving_a_ladder_top_is_not_put_back_down(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = HeroGameTests.owner(helper, "laddertop");
        UUID uuid = UUID.nameUUIDFromBytes(me.getBytes());
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 264;
        Runnable clearPad = HeroGameTests.possessionPad(level, base, y, 8, 2);
        double ux = base.getX() - 3.5, uz = base.getZ() + 0.5;
        // the unit's own block column: a ladder two high (feet at y + 1), on a stone wall two high just east of it
        BlockPos rung = BlockPos.containing(ux, y + 1, uz);
        List<BlockPos> placed = new ArrayList<>();
        for (int h = 0; h < 2; h++) {
            BlockPos wall = rung.above(h).east();
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(rung.above(h), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), 2);
            placed.add(wall);
            placed.add(rung.above(h));
        }
        Runnable clear = () -> {
            for (BlockPos b : placed)
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 2);
            clearPad.run();
        };
        Mob unit = HeroGameTests.padUnit(level, me, ux, y, uz);
        if (unit == null) {
            clear.run();
            helper.fail("could not create a vindicator unit");
            return;
        }
        List<String> problems = new ArrayList<>();
        double[] x0 = { 0 }, y0 = { 0 };
        int[] seq = { 1 };
        boolean[] climbed = { false };
        final int climbFrom = CLAIM_AT + 4, climbSteps = 11, over = climbFrom + climbSteps, check = over + 8;
        everyTick(helper, check, tick -> {
            if (tick < CLAIM_AT)
                return false;
            if (tick == CLAIM_AT) {
                if (!possess(level, me, uuid, unit, problems))
                    return finish(helper, uuid, unit, clear, problems);
                x0[0] = unit.getX();
                y0[0] = unit.getY();
                return false;
            }
            double cx = x0[0], cy;
            if (tick < climbFrom) {
                cy = y0[0];                                     // stand at the foot of the ladder
            } else if (tick < over) {
                climbed[0] |= unit.onClimbable();
                cy = y0[0] + 0.2 * (tick - climbFrom + 1);      // vanilla's climb: 0.2 a tick, up to 2.2 above the foot
            } else if (tick < check) {
                // feet over the top: step out toward the wall and onto it at walking speed - a vindicator walks 0.26 a
                // tick (PossessionServer.walkPerTick at speed 0.25), so 0.2 a tick; round 3's 0.3 a tick was more than
                // an honest client can claim and the server rightly clipped both steps (CI 40650756519 attempt 2)
                int k = tick - over;
                cx = x0[0] + Math.min(0.6, 0.2 * (k + 1));
                cy = y0[0] + (k == 0 ? 2.15 : k == 1 ? 2.05 : 2.0);
            } else {
                var p = PossessionServer.of(uuid);
                if (p == null) {
                    problems.add("the possession was lost");
                    return finish(helper, uuid, unit, clear, problems);
                }
                if (!climbed[0])
                    problems.add("set-up: the unit never read as climbing the ladder");
                if (p.corrections > 0)
                    problems.add(p.corrections + " claims of an honest climb out over the ladder's top were corrected");
                if (Math.abs(unit.getY() - (y0[0] + 2.0)) > 0.05 || unit.getX() - x0[0] < 0.5)
                    problems.add(String.format(Locale.ROOT, "the unit did not end up on the wall top: %.2f up, %.2f "
                        + "across (expected 2.00 up, 0.60 across)", unit.getY() - y0[0], unit.getX() - x0[0]));
                return finish(helper, uuid, unit, clear, problems);
            }
            PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, false, false, cx, cy, uz, seq[0]++);
            return false;
        });
    }
}
