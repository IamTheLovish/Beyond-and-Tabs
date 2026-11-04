package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The units' own vanilla push (LivingEntity.pushEntities, the Oct 22 server profile: ~104 world entity queries per
 * pillager per 10 s window) looks its neighbours up in the shared UnitGrid instead of the world. The spacing it
 * produces must not change, and the lookup is counted as the {@code pushScan} section.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerfPushGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int UNITS = 40;

    private static List<VindicatorUnit> spawnPile(GameTestHelper helper, String owner, int n) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(0, 2, 0));
        List<VindicatorUnit> made = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            VindicatorUnit v = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
            if (v == null) {
                made.forEach(VindicatorUnit::discard);
                return null;
            }
            // a 0.3-block lattice: every body overlaps its neighbours, so the push has work to do
            v.moveTo(at.getX() + 6.5 + (i % 8) * 0.3, at.getY(), at.getZ() + 6.5 + (i / 8) * 0.3, 0, 0);
            v.setOwnerName(owner);
            // hold fire: the pile is about the push alone. Idle units auto-acquire enemies in range, and a shard runs
            // this beside combat tests' units of other owners (the two piles of this class differ in owner too); a pile
            // chasing one melee ring stands closer than it spawned. That is the best-supported cause of the Oct 27 flake
            // - "0.29 apart" after 400 ticks, only ever in shard 1 (PR #289's build 40572488789) - while alone a pile
            // spreads in 102-138 of 400 ticks with no target (flake-stress 40606579207). Hold fire also stops
            // retaliation, so a neighbour's stray hit does not start a chase either.
            EngagementStance.set(v, EngagementStance.HOLD_FIRE);
            level.addFreshEntity(v);
            made.add(v);
        }
        return made;
    }

    /** A pile of overlapping units still spreads out: no two bodies are left on top of each other. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void a_pile_of_units_still_spreads_out(GameTestHelper helper) {
        List<VindicatorUnit> pile = spawnPile(helper, "gametest_pushspread_" + helper.absolutePos(BlockPos.ZERO).asLong(), UNITS);
        if (pile == null) {
            helper.fail("could not create the units");
            return;
        }
        // the best moment so far (largest closest-pair distance) and when it was: a pile that spread and was pushed back
        // together reads differently from one that never parted
        double[] best = { 0 };
        long[] bestAt = { 0 };
        // the most pile units that had a target at once, and what it was: a pile that is idle by itself must not have
        // any; one that does was pulled by a neighbour test's units (the suspected cause of the Oct 27 flake)
        int[] mostTargeting = { 0 };
        String[] targetSeen = { "-" };
        helper.succeedWhen(() -> {
            int targeting = 0;
            for (VindicatorUnit v : pile)
                if (v.getTarget() != null) {
                    if (targeting++ == 0 && mostTargeting[0] == 0)
                        targetSeen[0] = v.getTarget().getType().toShortString()
                            + (v.getTarget() instanceof Unit u ? "(" + u.getOwnerName() + ")" : "") + " at tick " + helper.getTick();
                }
            mostTargeting[0] = Math.max(mostTargeting[0], targeting);
            double closest = Double.MAX_VALUE;
            int ci = 0, cj = 1, close = 0;
            for (int i = 0; i < pile.size(); i++)
                for (int j = i + 1; j < pile.size(); j++) {
                    double dx = pile.get(i).getX() - pile.get(j).getX(), dz = pile.get(i).getZ() - pile.get(j).getZ();
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d < MIN_GAP)
                        close++;
                    if (d < closest) {
                        closest = d;
                        ci = i;
                        cj = j;
                    }
                }
            if (closest > best[0]) {
                best[0] = closest;
                bestAt[0] = helper.getTick();
            }
            if (closest < MIN_GAP)
                // the Oct 27 flake said only "0.29 apart": the message now carries what would explain a pair that
                // stays together (an order or target pulling them, off the ground, at rest, a foreign unit in the arena)
                throw new GameTestAssertException(String.format(java.util.Locale.ROOT,
                    "two units are still %.2f blocks apart (a body is 0.6 wide); %d pairs under %.2f at tick %d, best %.2f at tick %d; at most %d had a target at once (first: %s); %s | %s | %s",
                    closest, close, MIN_GAP, helper.getTick(), best[0], bestAt[0], mostTargeting[0], targetSeen[0],
                    describe(pile.get(ci), ci), describe(pile.get(cj), cj), surroundings(helper, pile)));
            // the isolation itself is checked: a hold-fire pile that still took a target means the stance leaked, and its
            // spacing proves nothing
            if (mostTargeting[0] > 0)
                throw new GameTestAssertException("a hold-fire pile took a target (" + mostTargeting[0] + " units at once, first: "
                    + targetSeen[0] + ") - EngagementStance.HOLD_FIRE no longer keeps an idle unit from acquiring");
            // the margin to the timeout on a pass, read by flake-stress runs (tools/ci_gametests.sh: [Push spread])
            ReignOfNether.LOGGER.info("[PushSpread] {} units spread to {} in {} of 400 ticks; at most {} had a target at once (first: {})",
                UNITS, String.format(java.util.Locale.ROOT, "%.2f", closest), helper.getTick(), mostTargeting[0], targetSeen[0]);
            pile.forEach(VindicatorUnit::discard);
        });
    }

    static final double MIN_GAP = 0.45;

    /** One pile unit's state, for a failure message. */
    static String describe(VindicatorUnit v, int index) {
        Vec3 dm = v.getDeltaMovement();
        var goal = v.getMoveGoal();
        return String.format(java.util.Locale.ROOT,
            "#%d id %d at %.2f %.2f %.2f alive %b ground %b dm %.4f %.4f %.4f target %s move %s hold %b idle %b nav-done %b pushable %b",
            index, v.getId(), v.getX(), v.getY(), v.getZ(), v.isAlive(), v.onGround(), dm.x, dm.y, dm.z,
            v.getTarget() == null ? "-" : v.getTarget().getType().toShortString() + "@" + String.format(java.util.Locale.ROOT, "%.1f", v.distanceTo(v.getTarget())),
            goal == null || goal.getMoveTarget() == null ? "-" : goal.getMoveTarget().toShortString(),
            v.getHoldPosition(), v.isIdle(), v.getNavigation().isDone(), v.isPushable());
    }

    /** The other living bodies in or beside the arena (a neighbour test's units, mobs), and the pile's own spread. */
    static String surroundings(GameTestHelper helper, List<VindicatorUnit> pile) {
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(o.getX() - 8, o.getY() - 2, o.getZ() - 8,
            o.getX() + 24, o.getY() + 12, o.getZ() + 24);
        List<String> others = new ArrayList<>();
        for (net.minecraft.world.entity.LivingEntity le : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            if (pile.contains(le))
                continue;
            if (others.size() < 6)
                others.add(le.getType().toShortString() + (le instanceof Unit u ? "(" + u.getOwnerName() + ")" : "")
                    + String.format(java.util.Locale.ROOT, "@%.1f,%.1f", le.getX() - o.getX(), le.getZ() - o.getZ()));
            else if (others.size() == 6)
                others.add("...");
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int alive = 0;
        for (VindicatorUnit v : pile) {
            if (v.isAlive())
                alive++;
            minX = Math.min(minX, v.getX()); maxX = Math.max(maxX, v.getX());
            minZ = Math.min(minZ, v.getZ()); maxZ = Math.max(maxZ, v.getZ());
        }
        return String.format(java.util.Locale.ROOT, "pile %d alive, spread %.1f x %.1f; others near: %s",
            alive, maxX - minX, maxZ - minZ, others.isEmpty() ? "none" : others);
    }

    /** Units in a pile each look their neighbours up once a tick through the counted pushScan section. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void unit_push_neighbour_lookups_are_counted(GameTestHelper helper) {
        List<VindicatorUnit> pile = spawnPile(helper, "gametest_pushscan_" + helper.absolutePos(BlockPos.ZERO).asLong(), UNITS);
        if (pile == null) {
            helper.fail("could not create the units");
            return;
        }
        // the lifetime counter, never reset: the shared window (Sys.calls) is restarted by the 10 s [Sys] report and by
        // any other test's snapshot, so a 40-tick window that crossed one counted only its tail (174 of 1600, Oct 26);
        // the hold is this test's own, so another test ending cannot switch measuring off under it
        Object hold = new Object();
        Sys.force(hold, true);
        long before = Sys.lifetime(Sys.PUSH_SCAN)[0];
        helper.runAfterDelay(40, () -> {
            long calls = Sys.lifetime(Sys.PUSH_SCAN)[0] - before;
            String line = Sys.lastLine;
            Sys.force(hold, false);
            pile.forEach(VindicatorUnit::discard);
            ReignOfNether.LOGGER.info("[PushScan] {} units, 40 ticks: {} neighbour lookups; {}", UNITS, calls, line);
            if (calls < UNITS * 40 / 2)
                helper.fail("only " + calls + " push neighbour lookups were counted for " + UNITS + " units over 40 ticks");
            else
                helper.succeed();
        });
    }

    /**
     * A unit's own doPush override still runs in its push pass: a standing War Mammoth shoves the small bodies it
     * overlaps at TitanMass.SHOVE (0.12 a tick), where vanilla's push is at most 0.05. The pass is the mammoth's real
     * tick; each Brute starts at rest, so its velocity afterwards is the push alone.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void a_titan_still_shoves_small_bodies_in_the_push_pass(GameTestHelper helper) {
        String owner = BigUnitFeelGameTests.owner(helper, "pushtitan");
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        Mob mammoth = BigUnitGameTests.spawn(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), owner, 8.5, 8.5);
        double[][] offsets = { { 0.8, 0.0 }, { -0.7, 0.5 }, { 0.2, -0.9 } };
        List<Mob> brutes = new ArrayList<>();
        for (double[] o : offsets) {
            Mob b = BigUnitGameTests.spawn(helper, EntityRegistrar.BRUTE_UNIT.get(), owner, 8.5 + o[0], 8.5 + o[1]);
            if (b != null) {
                ((Unit) b).setHoldPosition(true);
                brutes.add(b);
            }
        }
        if (mammoth == null || brutes.size() != offsets.length) {
            if (mammoth != null)
                mammoth.discard();
            brutes.forEach(Mob::discard);
            helper.fail("could not create the units");
            return;
        }
        helper.runAfterDelay(10, () -> {
            // ten ticks of pushing have parted them: put each back inside the mammoth's body, at rest
            for (int i = 0; i < brutes.size(); i++) {
                brutes.get(i).moveTo(mammoth.getX() + offsets[i][0], mammoth.getY(), mammoth.getZ() + offsets[i][1], 0, 0);
                brutes.get(i).setDeltaMovement(Vec3.ZERO);
            }
            mammoth.setDeltaMovement(Vec3.ZERO);
            StringBuilder why = new StringBuilder();
            for (Mob b : brutes)
                why.append(String.format(java.util.Locale.ROOT, "[small %b pushable %b overlap %b y %.2f/%.2f w %.2f/%.2f] ",
                    TitanMass.isSmallBody(mammoth, b), b.isPushable(), b.getBoundingBox().intersects(mammoth.getBoundingBox()),
                    b.getY(), mammoth.getY(), b.getBbWidth(), mammoth.getBbWidth()));
            mammoth.tick();   // its push pass runs inside the tick
            List<String> weak = new ArrayList<>();
            for (Mob b : brutes) {
                Vec3 v = b.getDeltaMovement();
                double push = Math.sqrt(v.x * v.x + v.z * v.z);
                if (push < 0.1)
                    weak.add(String.format(java.util.Locale.ROOT, "%.3f", push));
            }
            mammoth.discard();
            brutes.forEach(Mob::discard);
            if (weak.isEmpty())
                helper.succeed();
            else
                helper.fail("the mammoth did not shove " + weak.size() + " of 3 Brutes by its 0.12 (pushed only " + weak + "; vanilla is at most 0.05) " + why);
        });
    }
}
