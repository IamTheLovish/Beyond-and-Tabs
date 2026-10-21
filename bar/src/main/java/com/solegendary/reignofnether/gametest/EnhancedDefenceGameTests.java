package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.building.buildings.defence.Trap;
import com.solegendary.reignofnether.building.buildings.defence.WallSegment;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.UnitGrid;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The enhanced walls and traps (design/defences.md; lovish Oct 21 02:51: "tier 1 constructors can make enhanced version
 * of tier 1 defenses - kinda like in bar"): the fortified wall and the enhanced twin of every faction's two traps. Their
 * own holder; placements made with getNewBuildingPlacement and stamped like DefenceGameTests' trap test, never
 * registered in the world's building list.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class EnhancedDefenceGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @Nullable
    static Mob foeOn(ServerLevel level, Trap.TrapPlacement t, double dx, int gy, String foe, List<Mob> spawned) {
        Mob m = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (m == null)
            return null;
        m.moveTo(t.centrePos.getX() + 0.5 + dx, gy, t.centrePos.getZ() + 0.5, 0, 0);
        ((com.solegendary.reignofnether.unit.interfaces.Unit) m).setOwnerName(foe);
        ((com.solegendary.reignofnether.unit.interfaces.Unit) m).setHoldPosition(true);
        level.addFreshEntity(m);
        spawned.add(m);
        return m;
    }

    /**
     * An enhanced trap (the Kingdom's spikes, the Tidewrought's mine) reaches further than its base trap, keeps its
     * blocks on the first spring and rearms itself after Trap.SPARE_REARM_TICKS; the second spring spends it like a
     * base trap (the spikes' plate goes, the mine is gone).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void enhanced_trap_reaches_further_and_springs_twice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_enhtrap_" + helper.absolutePos(BlockPos.ZERO).asLong(), foe = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        List<Mob> spawned = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            Trap spikes = Defences.VILLAGERS_FORT.trapAEnhanced(), mine = Defences.TIDE_FORT.trapAEnhanced();
            if (spikes.kind != Trap.Kind.SPIKES || mine.kind != Trap.Kind.MINE || !spikes.enhanced || !mine.enhanced) {
                helper.fail("expected the Kingdom's enhanced spikes and the Tidewrought's enhanced mine");
                return;
            }
            int i = 0;
            for (Trap trap : List.of(spikes, mine)) {
                var bp = BuildingUtils.getNewBuildingPlacement(trap, level, helper.absolutePos(new BlockPos(3 + 7 * i, 1, 3 + 7 * i)),
                    Rotation.NONE, owner, false);
                i++;
                if (!(bp instanceof Trap.TrapPlacement t)) {
                    problems.add(trap.name + " is not a TrapPlacement");
                    continue;
                }
                DefenceGameTests.stamp(level, t);
                t.initPlacedBlocks();
                t.isBuilt = true;
                t.onlyFoe = foe;
                if (!t.isArmed()) {
                    problems.add(trap.name + " is not armed when built");
                    continue;
                }
                if (trap.radius() <= trap.kind.radius || trap.damage() <= trap.kind.damage)
                    problems.add(trap.name + " should reach further and hit harder than its base trap");
                // first spring: one foe on the plate, one beyond the base radius but inside the enhanced one
                Mob onPlate = foeOn(level, t, 0, gy, foe, spawned);
                Mob outer = foeOn(level, t, (trap.kind.radius + trap.radius()) / 2, gy, foe, spawned);
                if (onPlate == null || outer == null) {
                    problems.add("could not make the test units");
                    break;
                }
                float outerHp = outer.getHealth();
                UnitGrid.invalidate();
                if (t.checkTrigger(level) < 1)
                    problems.add(trap.name + " did not spring on an enemy on its plate");
                if (outer.isAlive() && outer.getHealth() >= outerHp)
                    problems.add(trap.name + " missed an enemy inside its wider radius (" + Math.round(trap.radius() * 10) / 10.0 + ")");
                int placed = t.getBlocksPlaced();
                if (placed < t.getBlocksTotal())
                    problems.add(trap.name + " lost blocks on its spare charge (" + placed + "/" + t.getBlocksTotal() + ")");
                if (t.isArmed() || t.rearmTicks != Trap.SPARE_REARM_TICKS || t.spareCharges != 0)
                    problems.add(trap.name + " should be rearming its spare charge (" + t.rearmTicks + " ticks, " + t.spareCharges + " spare)");
                for (Mob m : List.of(onPlate, outer))
                    m.discard();
                // the rearm runs out (TrapPlacement.tick counts it down); then the second spring spends it
                t.rearmTicks = 0;
                if (!t.isArmed())
                    problems.add(trap.name + " did not rearm after its spare charge");
                Mob second = foeOn(level, t, 0, gy, foe, spawned);
                UnitGrid.invalidate();
                if (second == null || t.checkTrigger(level) < 1)
                    problems.add(trap.name + " did not spring a second time");
                if (trap.kind == Trap.Kind.MINE ? t.getBlocksPlaced() != 0 : t.isArmed())
                    problems.add(trap.name + " should be spent after its second spring (" + t.getBlocksPlaced() + " blocks left)");
                if (trap.kind != Trap.Kind.MINE) {
                    // a repair puts the plate back, like a base trap
                    for (BuildingBlock b : t.getBlocks())
                        if (b.getBlockState().getBlock() == trap.trigger)
                            level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
                    t.initPlacedBlocks();
                    if (!t.isArmed())
                        problems.add(trap.name + " should rearm when the plate is put back");
                }
                if (second != null)
                    second.discard();
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            for (Mob m : spawned)
                m.discard();
        }
    }

    /**
     * For every live faction: a fortified wall and two enhanced traps, registered to it; each waits for a T1 lab,
     * combat engineers can't start it, it costs about twice its base defence (2-3x) and the fortified wall has at least
     * 2.5x a wall's health on the same footprint (so it mixes into a wall line).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void fortified_walls_and_enhanced_traps_follow_their_gates_and_prices(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_fort_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<String> problems = new ArrayList<>();
        for (var rl : Factions.PLAYABLE_FACTIONS) {
            Faction f = Factions.getFaction(rl);
            if (!Factions.isLive(f))
                continue;
            Defences.Set base = Defences.of(f);
            Defences.FortSet fort = Defences.fortOf(f);
            if (base == null || fort == null) {
                problems.add(f.getName() + " has no " + (base == null ? "defences" : "enhanced walls and traps"));
                continue;
            }
            for (var b : fort.all()) {
                if (!f.equals(b.getFaction()))
                    problems.add(b.name + " is not registered to " + f.getName());
                if (!((com.solegendary.reignofnether.building.buildings.defence.DefenceBuilding) b).requiresLab())
                    problems.add(b.name + " should wait for a T1 lab");
                if (CombatEngineers.mayStart(b))
                    problems.add("combat engineers should not start the " + b.name);
            }
            WallSegment wall = fort.fortifiedWall();
            if (!wall.fortified || wall.maxHealth < 2.5 * base.wall().maxHealth)
                problems.add(wall.name + " should have at least 2.5x a wall's health (" + wall.maxHealth + " vs " + base.wall().maxHealth + ")");
            ratio(problems, wall.name, wall.cost.metal(), base.wall().cost.metal());
            ratio(problems, fort.trapAEnhanced().name, fort.trapAEnhanced().cost.metal(), base.trapA().cost.metal());
            ratio(problems, fort.trapBEnhanced().name, fort.trapBEnhanced().cost.metal(), base.trapB().cost.metal());
            if (fort.trapAEnhanced().kind != base.trapA().kind || fort.trapBEnhanced().kind != base.trapB().kind)
                problems.add(f.getName() + "'s enhanced traps should be the same kinds as its traps");
            var wp = BuildingUtils.getNewBuildingPlacement(wall, level, helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
            var bp = BuildingUtils.getNewBuildingPlacement(base.wall(), level, helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
            if (wp == null || bp == null || wp.maxCorner.getX() - wp.minCorner.getX() != bp.maxCorner.getX() - bp.minCorner.getX()
                    || wp.maxCorner.getZ() - wp.minCorner.getZ() != bp.maxCorner.getZ() - bp.minCorner.getZ())
                problems.add(wall.name + " should share the wall's footprint");
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    static void ratio(List<String> problems, String name, int metal, int baseMetal) {
        double r = (double) metal / Math.max(1, baseMetal);
        if (r < 2 || r > 3)
            problems.add(name + " costs " + Math.round(r * 100) / 100.0 + "x its base defence's metal (want 2-3x)");
    }
}
