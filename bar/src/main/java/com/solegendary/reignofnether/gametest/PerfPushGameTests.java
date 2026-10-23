package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
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
        helper.succeedWhen(() -> {
            double closest = Double.MAX_VALUE;
            for (int i = 0; i < pile.size(); i++)
                for (int j = i + 1; j < pile.size(); j++) {
                    double dx = pile.get(i).getX() - pile.get(j).getX(), dz = pile.get(i).getZ() - pile.get(j).getZ();
                    closest = Math.min(closest, Math.sqrt(dx * dx + dz * dz));
                }
            if (closest < 0.45)
                throw new GameTestAssertException("two units are still " + String.format("%.2f", closest) + " blocks apart (a body is 0.6 wide)");
            pile.forEach(VindicatorUnit::discard);
        });
    }

    /** Units in a pile each look their neighbours up once a tick through the counted pushScan section. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void unit_push_neighbour_lookups_are_counted(GameTestHelper helper) {
        List<VindicatorUnit> pile = spawnPile(helper, "gametest_pushscan_" + helper.absolutePos(BlockPos.ZERO).asLong(), UNITS);
        if (pile == null) {
            helper.fail("could not create the units");
            return;
        }
        Sys.force(true);
        Sys.snapshot();   // start a fresh window
        helper.runAfterDelay(40, () -> {
            long calls = Sys.calls(Sys.PUSH_SCAN);
            String line = Sys.snapshot();
            Sys.force(false);
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
