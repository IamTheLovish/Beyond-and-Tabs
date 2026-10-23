package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
}
