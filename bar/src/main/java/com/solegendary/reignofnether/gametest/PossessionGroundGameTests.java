package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.PossessionServer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PossessionGroundGameTests {

    static final String ARENA = HeroGameTests.ARENA;
    /** Ticks of standing claims: past the hover rule's 40 (PossessionServer.HOVER_TICKS) with room to spare. */
    static final int STAND_TICKS = 70;

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
        Runnable finish = () -> {
            PossessionServer.release(uuid, null);
            unit.discard();
            clearPad.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        helper.runAfterDelay(6, () -> {
            String why = PossessionServer.claim(level, me, uuid, unit.getId(), true);
            if (why != null) {
                problems.add("could not possess the unit: " + why);
                finish.run();
                return;
            }
            double y0 = unit.getY();
            int[] seq = { 1 };
            int[] airborneTicks = { 0 };
            // an honest client standing still: every tick it claims exactly where the unit is
            for (int t = 1; t <= STAND_TICKS; t++)
                helper.runAfterDelay(t, () -> {
                    if (!unit.onGround())
                        airborneTicks[0]++;
                    PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, false, false,
                        unit.getX(), unit.getY(), unit.getZ(), seq[0]++);
                });
            helper.runAfterDelay(STAND_TICKS + 1, () -> {
                var p = PossessionServer.of(uuid);
                if (p == null) {
                    problems.add("the possession was lost");
                    finish.run();
                    return;
                }
                if (!unit.onGround())
                    problems.add("a unit standing on the pad under claims does not read on-ground");
                if (airborneTicks[0] > 2)
                    problems.add("it read airborne " + airborneTicks[0] + " of " + STAND_TICKS + " standing ticks");
                if (p.corrections > 0)
                    problems.add(p.corrections + " standing claims were corrected (the hover rule fires on solid ground)");
                if (Math.abs(unit.getY() - y0) > 0.01)
                    problems.add(String.format(Locale.ROOT, "the standing unit moved %.3f blocks up or down", unit.getY() - y0));
                // an honest sprint-jump: the first tick of a jump from the ground, a little past a sprint step's
                // reach - inside the 0.2 boost vanilla gives a sprint-jump (PossessionServer.SPRINT_JUMP_BOOST)
                double speed = unit.getAttributeValue(Attributes.MOVEMENT_SPEED);
                double reach = PossessionServer.walkPerTick(speed, true) + PossessionServer.SPRINT_JUMP_BOOST * 0.75;
                int corrected = p.corrections, accepted = p.acceptedClaims;
                double x1 = unit.getX();
                PossessionServer.applyMove(uuid, unit.getId(), 1, 0, -90, 0, true, false, true,
                    x1 + reach, unit.getY() + 0.4, unit.getZ(), seq[0]++);
                helper.runAfterDelay(2, () -> {
                    if (p.corrections > corrected || p.acceptedClaims <= accepted)
                        problems.add(String.format(Locale.ROOT,
                            "an honest sprint-jump claim (%.3f blocks, sprint reach %.3f + boost) was corrected "
                                + "(corrections %d -> %d, accepted %d -> %d)", reach,
                            PossessionServer.walkPerTick(speed, true), corrected, p.corrections, accepted, p.acceptedClaims));
                    finish.run();
                });
            });
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
        Runnable finish = () -> {
            PossessionServer.release(uuid, null);
            unit.discard();
            clearPad.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        helper.runAfterDelay(6, () -> {
            String why = PossessionServer.claim(level, me, uuid, unit.getId(), true);
            if (why != null) {
                problems.add("could not possess the unit: " + why);
                finish.run();
                return;
            }
            double floor = unit.getY();
            int[] seq = { 1 };
            double[] highest = { 0 };
            for (int t = 1; t <= 80; t++)
                helper.runAfterDelay(t, () -> {
                    highest[0] = Math.max(highest[0], unit.getY() - floor);
                    PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, false, false,
                        unit.getX(), floor + 1.5, unit.getZ(), seq[0]++);
                });
            helper.runAfterDelay(81, () -> {
                var p = PossessionServer.of(uuid);
                if (p == null) {
                    problems.add("the possession was lost");
                    finish.run();
                    return;
                }
                if (p.corrections < 1)
                    problems.add("80 ticks of hovering claims were never corrected");
                if (unit.getY() - floor > 0.3)
                    problems.add(String.format(Locale.ROOT, "the hovering unit was not pulled down: %.2f above the pad "
                        + "after 80 ticks (highest %.2f)", unit.getY() - floor, highest[0]));
                if (highest[0] > 1.6)
                    problems.add(String.format(Locale.ROOT, "the hovering claims lifted the unit %.2f blocks", highest[0]));
                finish.run();
            });
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
        Runnable finish = () -> {
            PossessionServer.release(uuid, null);
            unit.discard();
            clearPad.run();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        };
        helper.runAfterDelay(6, () -> {
            String why = PossessionServer.claim(level, me, uuid, unit.getId(), true);
            if (why != null) {
                problems.add("could not possess the unit: " + why);
                finish.run();
                return;
            }
            double floor = unit.getY();   // the pad is flat: the last ground the unit stood on is always the floor
            int[] seq = { 1 };
            double[] highest = { 0 };
            int[] highestAt = { 0 };
            for (int t = 1; t <= 100; t++) {
                int tick = t;
                helper.runAfterDelay(t, () -> {
                    if (unit.getY() - floor > highest[0]) {
                        highest[0] = unit.getY() - floor;
                        highestAt[0] = tick;
                    }
                    PossessionServer.applyMove(uuid, unit.getId(), 0, 0, -90, 0, true, false,
                        unit.getX(), unit.getY() + 1.2, unit.getZ(), seq[0]++);
                });
            }
            helper.runAfterDelay(101, () -> {
                var p = PossessionServer.of(uuid);
                if (p == null) {
                    problems.add("the possession was lost");
                    finish.run();
                    return;
                }
                highest[0] = Math.max(highest[0], unit.getY() - floor);
                if (highest[0] > MOST_RISE)
                    problems.add(String.format(Locale.ROOT, "jump claims of +1.2 every tick lifted the unit %.2f blocks "
                        + "above its ground (tick %d), more than a jump's apex plus a step (%.2f)", highest[0],
                        highestAt[0], MOST_RISE));
                if (p.corrections < 1)
                    problems.add("100 ticks of impossible jump claims were never corrected");
                finish.run();
            });
        });
    }
}
