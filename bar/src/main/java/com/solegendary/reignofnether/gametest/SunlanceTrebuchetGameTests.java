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
 * On its own far pad an enemy anti-siege tower stands against the pad's west rim; a Sunfire
 * Mangonel (Sunforged's T1 lobber) holds 18 blocks
 * east of its centre and a Trebuchet 21 east, in one row, both ordered onto the tower through the order path. The
 * lobber is the control that the tower is live: it is inside the tower's reach (and outside its own 15), so the tower,
 * which shoots siege units first, kills it. The Trebuchet must take health off the tower and never be hurt. Every
 * unit must end on the pad (it is 32 x 16 and floats at PAD_Y: an earlier layout dropped them off it).
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
            // all on the pad (DuelGameTests.buildPad: 32 x 16 of glass at PAD_Y, a 3-high rim; off it a unit falls ~150 blocks):
            // the tower against the west rim, the lobber 18 east of its centre, the Trebuchet 21 east, one row
            BlockPos origin = new BlockPos(c.x0() + 1, RaidGameTests.PAD_Y + 1, c.z0() + 6);
            BuildingPlacement bp = tower == null ? null : RaidGameTests.build(level, tower, origin, "gametest_st_foe", false);
            double cx = bp == null ? 0 : bp.centrePos.getX() + 0.5, cz = bp == null ? 0 : bp.centrePos.getZ() + 0.5;
            Mob treb = bp == null ? null : RaidGameTests.spawn(level, EntityRegistrar.SUNLANCE_TREBUCHET_UNIT.get(), "gametest_st_siege", cx + 21, cz);
            Mob lobber = bp == null ? null : RaidGameTests.spawn(level, EntityRegistrar.SUNFIRE_MANGONEL_UNIT.get(), "gametest_st_siege", cx + 18, cz);
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
            // the state trail a failure prints: ticks with a building target, the lowest shot cooldown seen, the last
            // set-up ticks left, (unused), shells seen in flight from this unit
            int[] trail = { 0, Integer.MAX_VALUE, -1, 0, 0 };
            java.util.Set<Integer> shells = new java.util.HashSet<>();
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
                if (((AttackerUnit) treb).getAttackBuildingGoal() instanceof com.solegendary.reignofnether.unit.goals.RangedAttackBuildingGoal<?> g
                        && g.getBuildingTarget() != null)
                    trail[0]++;
                if (((AttackerUnit) treb).getAttackGoal() instanceof com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal<?> bow)
                    trail[1] = Math.min(trail[1], (int) bow.getAttackCooldown());
                trail[2] = com.solegendary.reignofnether.unit.SetUp.ticksLeft(treb);
                for (var e : level.getEntitiesOfClass(com.solegendary.reignofnether.unit.units.tide.BombardShell.class,
                        treb.getBoundingBox().inflate(30)))
                    if (e.getOwner() == treb)
                        shells.add(e.getId());
                trail[4] = shells.size();
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
                double nearest = Double.MAX_VALUE;
                for (var b : bp.getBlocks())
                    if (b.isPlaced(level))
                        nearest = Math.min(nearest, Math.sqrt(treb.distanceToSqr(b.getBlockPos().getX() + 0.5, b.getBlockPos().getY() + 1.0,
                            b.getBlockPos().getZ() + 0.5)));
                var hurtBy = treb.getLastHurtByMob();
                String state = String.format(Locale.ROOT, "tower %d of %d health, trebuchet %.0f/%.0f hp %.1f blocks from the tower (moved %.1f), lobber %s;"
                    + " trail: building target %d ticks, lowest cooldown %s, set-up left %d, %d shells thrown,"
                    + " nearest tower block %.1f (range %.0f), canAttackBuildings %s, last hurt by %s",
                    health, before, treb.getHealth(), trebMax, dist, drift, lobber.isAlive() ? "alive at " + Math.round(lobber.getHealth()) + " hp" : "dead",
                    trail[0], trail[1] == Integer.MAX_VALUE ? "none" : String.valueOf(trail[1]), trail[2], trail[4], nearest,
                    ((AttackerUnit) treb).getAttackRange(), ((AttackerUnit) treb).canAttackBuildings(),
                    hurtBy == null ? "nothing" : hurtBy.getType().toShortString() + " " + Math.round(Math.sqrt(hurtBy.distanceToSqr(treb))) + " blocks away");
                ReignOfNether.LOGGER.info("[SunlanceTrebuchet] GameTest: first lance after {} ticks, {} health lost; {}",
                    firstHit[0] < 0 ? -1 : firstHit[0] - start, lost, state);
                String verdict = null;
                if (treb.getY() < RaidGameTests.PAD_Y || lobber.getY() < RaidGameTests.PAD_Y)
                    verdict = "setup: a unit fell off the pad (treb y " + Math.round(treb.getY()) + ", lobber y " + Math.round(lobber.getY())
                        + ", pad " + RaidGameTests.PAD_Y + "); " + state;
                else if (lobber.isAlive())
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
