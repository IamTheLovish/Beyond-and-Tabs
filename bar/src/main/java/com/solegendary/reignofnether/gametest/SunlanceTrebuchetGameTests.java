package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Locale;

/**
 * The Sunlance Trebuchet, Sunforged's T2 siege unit (design/defences_tiers.md), does the job the counter audit found
 * missing for Sunforged: it shells an anti-siege tower (range 20) from where the tower cannot answer.
 * <p>
 * On its own far pad an enemy anti-siege tower stands between a Trebuchet 21 blocks east of its centre and a Sunfire
 * Mangonel (Sunforged's T1 lobber) 18 blocks west, both holding position and both ordered onto the tower through the
 * order path. The lobber is the control that the tower is live: it is inside the tower's reach (and outside its own 15),
 * so the tower, which shoots siege units first, kills it. The Trebuchet must take health off the tower - lances, not
 * chips - and never be hurt.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SunlanceTrebuchetGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** Ticks for the first lance to land (set-up, cooldown, a 2 s flight), then the window the rest is read over. */
    static final int FIRST_HIT_TICKS = 400, WINDOW_TICKS = 300;

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void a_sunlance_trebuchet_shells_an_anti_siege_tower_from_beyond_its_reach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RaidGameTests.Pads pads = new RaidGameTests.Pads(level, 21, 1);
        // registered here, once, idling until the pad is ready (BuildingDamageGameTests: never from inside the callback)
        Runnable[] watch = { null };
        helper.onEachTick(() -> {
            if (watch[0] != null)
                watch[0].run();
        });
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "sunlance trebuchet pad", () -> {
            RaidGameTests.Cell c = pads.cells.get(0);
            DefenceTower tower = Defences.siegeGuardOf(Factions.MONSTERS);
            BlockPos origin = new BlockPos(c.x0() + 24, RaidGameTests.PAD_Y + 1, c.z0() + 20);
            BuildingPlacement bp = tower == null ? null : RaidGameTests.build(level, tower, origin, "gametest_st_foe", false);
            double cx = bp == null ? 0 : bp.centrePos.getX() + 0.5, cz = bp == null ? 0 : bp.centrePos.getZ() + 0.5;
            Mob treb = bp == null ? null : RaidGameTests.spawn(level, EntityRegistrar.SUNLANCE_TREBUCHET_UNIT.get(), "gametest_st_siege", cx + 21, cz);
            Mob lobber = bp == null ? null : RaidGameTests.spawn(level, EntityRegistrar.SUNFIRE_MANGONEL_UNIT.get(), "gametest_st_siege", cx - 18, cz);
            if (bp == null || treb == null || lobber == null) {
                if (treb != null)
                    treb.discard();
                if (lobber != null)
                    lobber.discard();
                if (bp != null)
                    BuildingServerEvents.getBuildings().remove(bp);
                pads.release();
                helper.fail("setup: tower " + tower + ", placement " + bp + ", treb " + treb + ", lobber " + lobber);
                return;
            }
            ((Unit) treb).setHoldPosition(true);
            ((Unit) lobber).setHoldPosition(true);
            int start = (int) helper.getTick();
            int before = bp.getHealth();
            float trebMax = treb.getMaxHealth();
            double trebX = treb.getX(), trebZ = treb.getZ();
            int[] firstHit = { -1, 0 };
            boolean[] ordered = { false }, over = { false };
            watch[0] = () -> {
                if (over[0])
                    return;
                int now = (int) helper.getTick();
                if (!ordered[0]) {
                    if (now < start + 2)
                        return;
                    ((AttackerUnit) treb).setAttackBuildingTarget(bp.originPos);
                    ((AttackerUnit) lobber).setAttackBuildingTarget(bp.originPos);
                    ordered[0] = true;
                    return;
                }
                int health = bp.getHealth();
                if (firstHit[0] < 0 && health < before) {
                    firstHit[0] = now;
                    firstHit[1] = health;
                }
                boolean done = firstHit[0] >= 0 ? now >= firstHit[0] + WINDOW_TICKS : now - start > FIRST_HIT_TICKS;
                if (!done)
                    return;
                over[0] = true;
                double dist = Math.sqrt(treb.distanceToSqr(cx, treb.getY(), cz));
                double drift = Math.sqrt((treb.getX() - trebX) * (treb.getX() - trebX) + (treb.getZ() - trebZ) * (treb.getZ() - trebZ));
                int lost = before - health;
                String state = String.format(Locale.ROOT, "tower %d of %d health, trebuchet %.0f/%.0f hp %.1f blocks from the tower (moved %.1f), lobber %s",
                    health, before, treb.getHealth(), trebMax, dist, drift, lobber.isAlive() ? "alive at " + Math.round(lobber.getHealth()) + " hp" : "dead");
                ReignOfNether.LOGGER.info("[SunlanceTrebuchet] GameTest: first lance after {} ticks, {} health lost; {}",
                    firstHit[0] < 0 ? -1 : firstHit[0] - start, lost, state);
                String verdict = null;
                if (lobber.isAlive())
                    verdict = "control: the anti-siege tower never killed the lobber 18 blocks away (is it firing?); " + state;
                else if (firstHit[0] < 0)
                    verdict = "the Sunlance Trebuchet never hurt the anti-siege tower in " + FIRST_HIT_TICKS / 20 + " s; " + state;
                else if (lost < 60)
                    verdict = "the Sunlance Trebuchet took only " + lost + " health off the tower: a lance takes about 90 off a defence; " + state;
                else if (treb.getHealth() < trebMax)
                    verdict = "the anti-siege tower (range " + tower.kind.range + ") hurt the Sunlance Trebuchet standing beyond its reach; " + state;
                else if (dist <= tower.kind.range)
                    verdict = "the Sunlance Trebuchet walked into the tower's reach instead of holding at 21; " + state;
                treb.discard();
                lobber.discard();
                BuildingServerEvents.getBuildings().remove(bp);
                pads.release();
                if (verdict != null)
                    helper.fail(verdict);
                else
                    helper.succeed();
            };
        });
    }
}
