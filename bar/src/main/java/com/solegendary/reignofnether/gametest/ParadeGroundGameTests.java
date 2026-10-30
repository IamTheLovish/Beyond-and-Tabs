package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.modelling.motion.ParadeGround;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The living army's parade ground (design/living_army.md, section 4): `/parade` spawns a block of every unit type and
 * runs its scripted loop (stand, march, wheel, march back, halt, take hits from every side) - here headless, one unit
 * a type, for one whole loop: every type spawns, the parade's blows kill nobody, the loop completes and stop clears
 * the field. (Timed summons - totems, scout pets, a deployed turret, drones - expire on their own during a loop; that is
 * their design, not the parade, so they are listed, not failed.)
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ParadeGroundGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = ParadeGround.LOOP_TICKS + 200, batch = "parade_ground")
    public static void parade_spawns_every_unit_type_and_runs_a_loop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<EntityType<? extends Mob>> types = ParadeGround.unitTypes(level);
        helper.assertTrue(types.size() > 50, "the parade knows the roster: " + types.size() + " unit types");
        ParadeGround parade = ParadeGround.start(level, helper.absolutePos(new BlockPos(2, 2, 2)), "parade_test", 1,
            false);
        // the parade (one of every unit type, still looping) goes when the test ends, however it ends
        TestCleanup.atEnd(helper, () -> {
            if (ParadeGround.active() == parade)
                ParadeGround.stop();
        });
        helper.assertTrue(parade.units().size() == types.size(), "one of every type: " + parade.units().size()
            + " spawned of " + types.size());
        helper.runAfterDelay(ParadeGround.LOOP_TICKS + 20, () -> {
            List<String> gone = new ArrayList<>();
            for (Mob m : parade.units())
                if (!m.isAlive())
                    gone.add(EntityType.getKey(m.getType()).getPath());
            int loops = parade.loopsCompleted(), kills = parade.kills();
            ParadeGround.stop();
            ReignOfNether.LOGGER.info("[Parade] one loop: " + parade.units().size() + " units, expired on their own: "
                + (gone.isEmpty() ? "none" : String.join(", ", gone)));
            helper.assertTrue(loops >= 1, "a whole loop ran: " + loops);
            helper.assertTrue(kills == 0, "the parade's blows killed " + kills + " units");
            helper.assertTrue(ParadeGround.active() == null, "stop clears the parade");
            helper.succeed();
        });
    }
}
