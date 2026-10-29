package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.defence.DefenceCosts;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.building.buildings.defence.Trap;
import com.solegendary.reignofnether.building.buildings.defence.WallGate;
import com.solegendary.reignofnether.building.buildings.defence.WallSegment;
import com.solegendary.reignofnether.building.buildings.placements.RailBatteryPlacement;
import com.solegendary.reignofnether.building.buildings.placements.StormOakPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless tests of the static defences (design/defences.md): walls and gates, traps, the T1 anti-raid towers and the
 * anti-T3 emplacements of all seven factions. Their own holder next to SkirmishGameTests (same arena template, unique
 * owners, no global counts); placements are built with getNewBuildingPlacement and never registered in the world's
 * building list, so no other test can see them.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DefenceGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Stamps a placement's blocks into the world (flag 2: no neighbour updates, nothing pops off). */
    static void stamp(ServerLevel level, BuildingPlacement bp) {
        for (BuildingBlock b : bp.getBlocks())
            if (!b.getBlockState().isAir())
                level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
    }

    /**
     * Whether a 1-wide, 2-tall ground unit can walk from {@code from} to {@code to} over the cells of the box, by the
     * pathfinder's own cell rule (WalkabilityBuilder.classify) and the grid's moves (4 directions, a 1-block step up,
     * drops of up to 3). Read straight from the world, so no walkability cache can be stale.
     */
    static boolean reachable(ServerLevel level, BlockPos from, BlockPos to, BlockPos min, BlockPos max) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        open.add(from);
        seen.add(from.asLong());
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!open.isEmpty()) {
            BlockPos c = open.poll();
            if (c.getX() == to.getX() && c.getZ() == to.getZ() && Math.abs(c.getY() - to.getY()) <= 1)
                return true;
            for (int[] d : dirs) {
                int nx = c.getX() + d[0], nz = c.getZ() + d[1];
                if (nx < min.getX() || nx > max.getX() || nz < min.getZ() || nz > max.getZ())
                    continue;
                for (int dy = 1; dy >= -3; dy--) {
                    int ny = c.getY() + dy;
                    if (ny < min.getY() || ny > max.getY())
                        continue;
                    if (WalkabilityBuilder.classify(level, nx, ny, nz) != WalkabilityBuilder.KIND_LAND)
                        continue;
                    // stepping up needs head room over the current cell; dropping needs the column above the landing open
                    BlockPos n = new BlockPos(nx, ny, nz);
                    if (seen.add(n.asLong()))
                        open.add(n);
                    break;
                }
            }
        }
        return false;
    }

    /**
     * A wall line with a gate across a floating platform (y=210, so the only way across is through the line): an enemy
     * near the gate shuts it and nothing can walk across; with no enemy near it opens and a ground unit walks through.
     * Plain units never auto-target a wall, a T3 does.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void defence_wall_blocks_pathing_and_the_gate_opens_only_for_its_side(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_wall_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        final int y0 = 210;
        int bx = base.getX(), bz = base.getZ();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                level.setBlock(new BlockPos(bx + x, y0, bz + z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 1; y <= 6; y++)
                    level.setBlock(new BlockPos(bx + x, y0 + y, bz + z), Blocks.AIR.defaultBlockState(), 2);
            }
        Defences.Set set = Defences.VILLAGERS;
        List<BuildingPlacement> parts = new ArrayList<>();
        WallGate.GatePlacement gate = null;
        for (int x = 0; x < 16; x += 2) {
            var building = x == 6 ? set.gate() : set.wall();
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(building, level, new BlockPos(bx + x, y0, bz + 7),
                Rotation.NONE, owner, false);
            stamp(level, bp);
            parts.add(bp);
            if (bp instanceof WallGate.GatePlacement gp)
                gate = gp;
            else if (!(bp instanceof WallSegment.WallPlacement))
                helper.fail("a wall segment is not a WallPlacement: " + bp);
        }
        if (gate == null) {
            helper.fail("the gate is not a GatePlacement");
            return;
        }
        gate.isBuilt = true;
        gate.onlyFoe = foeOwner;
        BlockPos min = new BlockPos(bx, y0 + 1, bz), max = new BlockPos(bx + 15, y0 + 5, bz + 15);
        BlockPos a = new BlockPos(bx + 2, y0 + 1, bz + 2), b = new BlockPos(bx + 2, y0 + 1, bz + 13);
        var foe = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var colossus = EntityRegistrar.SUN_COLOSSUS_UNIT.get().create(level);
        if (foe == null || colossus == null) {
            helper.fail("could not create the wall test units");
            return;
        }
        try {
            // shut (as built): nobody crosses
            if (reachable(level, a, b, min, max))
                helper.fail("a unit can walk across a wall line whose gate is shut");
            // an enemy at the gate keeps it shut
            foe.moveTo(gate.centrePos.getX() + 0.5, y0 + 1, gate.centrePos.getZ() - 3.5, 0, 0);
            foe.setOwnerName(foeOwner);
            foe.setHoldPosition(true);
            level.addFreshEntity(foe);
            UnitGrid.invalidate();
            if (gate.updateGate(level))
                helper.fail("the gate opened with an enemy " + 3 + " blocks away");
            if (reachable(level, a, b, min, max))
                helper.fail("the enemy can path through a shut gate");
            // the enemy leaves: the gate opens and the owner's units walk through the gap
            foe.discard();
            UnitGrid.invalidate();
            if (!gate.updateGate(level))
                helper.fail("the gate stayed shut with no enemy near");
            int open = 0;
            for (BuildingBlock bb : gate.getBlocks())
                if (level.getBlockState(bb.getBlockPos()).getBlock() instanceof FenceGateBlock
                        && level.getBlockState(bb.getBlockPos()).getValue(FenceGateBlock.OPEN))
                    open++;
            if (open != 4)
                helper.fail("an open gate should show its four fence gates open, found " + open);
            if (!reachable(level, a, b, min, max))
                helper.fail("no way through the open gate");
            // swinging is not damage: the gate counts every block placed whether open or shut
            gate.initPlacedBlocks();
            if (gate.getBlocksPlaced() < gate.getBlocksTotal())
                helper.fail("an open gate reads as damaged: " + gate.getBlocksPlaced() + "/" + gate.getBlocksTotal());
            // walls are no target for a plain unit, but they are for an experimental (and it hits them far harder)
            if (WallSegment.drawsAggroFrom(foe))
                helper.fail("a worker should walk round a wall, not attack it");
            if (!WallSegment.drawsAggroFrom(colossus) || WallSegment.damageMultFrom(colossus) < WallSegment.T3_MULT)
                helper.fail("a Sun Colossus should break walls (x" + WallSegment.T3_MULT + ")");
            helper.succeed();
        } finally {
            foe.discard();
            colossus.discard();
        }
    }

    /**
     * A trap is hidden from the enemy (Concealment, never attackable) but not from its owner; a friend walking over it
     * sets nothing off; an enemy stepping on it springs it - hurt, revealed for good, plate knocked out (a rearmable
     * trap waits for a repair; putting the plate back rearms it). A mine is gone once it fires.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void defence_trap_is_hidden_then_springs_on_an_enemy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_trap_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        Trap spikesB = Defences.VILLAGERS.trapA();
        Trap mineB = Defences.TIDE.trapA();
        if (spikesB.kind != Trap.Kind.SPIKES || mineB.kind != Trap.Kind.MINE) {
            helper.fail("expected the Kingdom's first trap to be spikes and the Tidewrought's a mine");
            return;
        }
        var bp = BuildingUtils.getNewBuildingPlacement(spikesB, level, helper.absolutePos(new BlockPos(3, 1, 3)), Rotation.NONE, owner, false);
        var mp = BuildingUtils.getNewBuildingPlacement(mineB, level, helper.absolutePos(new BlockPos(10, 1, 10)), Rotation.NONE, owner, false);
        if (!(bp instanceof Trap.TrapPlacement trap) || !(mp instanceof Trap.TrapPlacement mine)) {
            helper.fail("a trap is not a TrapPlacement");
            return;
        }
        var foe = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var friend = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var foe2 = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (foe == null || friend == null || foe2 == null) {
            helper.fail("could not create the trap test units");
            return;
        }
        List<Mob> all = List.of(foe, friend, foe2);
        try {
            for (Trap.TrapPlacement t : List.of(trap, mine)) {
                stamp(level, t);
                t.initPlacedBlocks();
                t.isBuilt = true;
                t.onlyFoe = foeOwner;
                if (!t.isArmed())
                    helper.fail("a freshly built " + t.trap.kind + " trap is not armed: " + t.getBlocksPlaced() + "/" + t.getBlocksTotal());
                if (!com.solegendary.reignofnether.unit.Concealment.isBuildingHiddenFrom(t, foeOwner))
                    helper.fail("an unsprung trap should be hidden from the enemy");
                if (com.solegendary.reignofnether.unit.Concealment.isBuildingHiddenFrom(t, owner))
                    helper.fail("a trap should never be hidden from its owner");
                if (t.isAttackable())
                    helper.fail("a hidden trap should not be attackable");
            }
            int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
            friend.moveTo(trap.centrePos.getX() + 0.5, gy, trap.centrePos.getZ() + 0.5, 0, 0);
            friend.setOwnerName(owner);
            friend.setHoldPosition(true);
            level.addFreshEntity(friend);
            UnitGrid.invalidate();
            if (trap.checkTrigger(level) != 0 || !trap.isArmed() || trap.revealed)
                helper.fail("the owner's own unit set the trap off");
            foe.moveTo(trap.centrePos.getX() + 0.5, gy, trap.centrePos.getZ() + 0.5, 0, 0);
            foe.setOwnerName(foeOwner);
            foe.setHoldPosition(true);
            level.addFreshEntity(foe);
            float before = foe.getHealth(), friendBefore = friend.getHealth();
            UnitGrid.invalidate();
            if (trap.checkTrigger(level) < 1)
                helper.fail("an enemy on the trap did not spring it");
            if (foe.isAlive() && foe.getHealth() >= before)
                helper.fail("the sprung trap did not hurt the enemy");
            if (friend.getHealth() < friendBefore)
                helper.fail("the sprung trap hurt its owner's unit");
            if (!trap.revealed || com.solegendary.reignofnether.unit.Concealment.isBuildingHiddenFrom(trap, foeOwner) || !trap.isAttackable())
                helper.fail("a sprung trap should be revealed (and attackable)");
            if (trap.isArmed() || trap.checkTrigger(level) != 0)
                helper.fail("a sprung trap should wait for a rearm");
            // rearm = the repair putting the plate back
            for (BuildingBlock b : trap.getBlocks())
                if (b.getBlockState().getBlock() == trap.trap.trigger)
                    level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
            trap.initPlacedBlocks();
            if (!trap.isArmed())
                helper.fail("putting the plate back should rearm the trap");
            // the mine: one shot, every block gone
            foe2.moveTo(mine.centrePos.getX() + 0.5, gy, mine.centrePos.getZ() + 0.5, 0, 0);
            foe2.setOwnerName(foeOwner);
            foe2.setHoldPosition(true);
            level.addFreshEntity(foe2);
            UnitGrid.invalidate();
            if (mine.checkTrigger(level) < 1)
                helper.fail("an enemy on the mine did not set it off");
            if (mine.getBlocksPlaced() != 0)
                helper.fail("a spent mine should be gone, " + mine.getBlocksPlaced() + " blocks left");
            helper.succeed();
        } finally {
            for (Mob m : all)
                m.discard();
        }
    }

    /**
     * Every faction's anti-raid tower out-trades its price against T1 raiders: given raiders worth at least its metal
     * standing in range, it kills raiders worth at least its metal before they could have razed it (its health over
     * their combined building damage per second). Ticks the weapon synchronously; i-frames are cleared between shots
     * as the game's own ticks would. Ground only: it ignores a flyer.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void defence_anti_raid_tower_out_trades_its_cost_vs_raiders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_raidtower_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        List<Mob> spawned = new ArrayList<>();
        try {
            for (Defences.Set set : Defences.sets()) {
                var bp = BuildingUtils.getNewBuildingPlacement(set.raidTower(), level, helper.absolutePos(new BlockPos(6, 1, 6)),
                    Rotation.NONE, owner, false);
                if (!(bp instanceof DefenceTower.DefenceTowerPlacement tower)) {
                    helper.fail(set.factionName() + ": the raid tower is not a DefenceTowerPlacement");
                    return;
                }
                tower.isBuilt = true;
                tower.onlyFoe = foeOwner;
                int metal = set.raidTower().cost.metal();
                List<Mob> raiders = new ArrayList<>();
                int raiderMetal = 0;
                float buildingDps = 0;
                while (raiderMetal < metal) {
                    var r = EntityRegistrar.CUTLASS_RAIDER_UNIT.get().create(level);
                    if (r == null) {
                        helper.fail("could not create a Cutlass Raider");
                        return;
                    }
                    r.moveTo(tower.centrePos.getX() + 0.5 + raiders.size() * 2, gy, tower.centrePos.getZ() + 5.5, 0, 0);
                    r.setOwnerName(foeOwner);
                    r.setHoldPosition(true);
                    level.addFreshEntity(r);
                    raiders.add(r);
                    spawned.add(r);
                    raiderMetal += ((Unit) r).getCost().metal();
                    var au = (com.solegendary.reignofnether.unit.interfaces.AttackerUnit) r;
                    buildingDps += au.getUnitAttackDamage() * tower.getMeleeDamageMult() * au.getBuildingDamageMultiplier()
                        * (20f / au.getAttackCooldown());
                }
                // a flyer overhead in range: never shot at by the anti-raid tower
                var flyer = EntityRegistrar.GULL_SPOTTER_UNIT.get().create(level);
                if (flyer != null) {
                    flyer.moveTo(tower.centrePos.getX() + 0.5, gy + 2, tower.centrePos.getZ() - 3.5, 0, 0);
                    flyer.setOwnerName(foeOwner);
                    level.addFreshEntity(flyer);
                    spawned.add(flyer);
                }
                UnitGrid.invalidate();
                int surviveTicks = (int) (set.raidTower().maxHealth / Math.max(0.01f, buildingDps) * 20);
                int killedMetal = 0;
                float flyerHp = flyer == null ? 0 : flyer.getHealth();
                for (int t = 0; t < surviveTicks && killedMetal < raiderMetal; t++) {
                    for (Mob r : raiders)
                        r.invulnerableTime = 0;
                    tower.tickWeapon(level);
                    killedMetal = 0;
                    for (Mob r : raiders)
                        if (!r.isAlive())
                            killedMetal += ((Unit) r).getCost().metal();
                }
                if (killedMetal < metal)
                    helper.fail(set.factionName() + ": the raid tower (" + metal + " metal) killed only " + killedMetal
                        + " metal of raiders in the " + surviveTicks / 20 + " s they need to raze it");
                if (flyer != null && flyer.isAlive() && ((Unit) flyer).isFlyingUnit() && flyer.getHealth() < flyerHp)
                    helper.fail(set.factionName() + ": the anti-raid tower shot a flyer");
                for (Mob r : raiders)
                    r.discard();
                if (flyer != null)
                    flyer.discard();
            }
            helper.succeed();
        } finally {
            for (Mob m : spawned)
                m.discard();
        }
    }

    /**
     * Every tower kind fires at its configured rate with the game's own target scan - no onlyFoe shortcut, tickAge
     * counted up before the weapon as BuildingPlacement.tick does: against a target that stays in range the shots come
     * exactly cooldownTicks apart. A ready tower used to wait a tick more and then for the next SCAN_TICKS boundary, so a
     * 1 s tower fired every 1.5 s and the anti-T3 every 3.5 s instead of 3 s. The tower and its target float at y = 380,
     * more than a tower's reach above every other test's pad (ArenaReady.pad: 301 is the highest), so a tower with no
     * onlyFoe sees nothing but its own target; any shot that misses it is reported.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void defence_towers_fire_at_their_configured_rate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_towerrate_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        final int y = 380, ticks = 400;
        BlockPos at = helper.absolutePos(new BlockPos(6, 0, 6)).atY(y);
        // one registered tower of every Kind, so a kind added later is checked too (or reported if none is registered)
        java.util.Map<DefenceTower.Kind, DefenceTower> byKind = new java.util.EnumMap<>(DefenceTower.Kind.class);
        for (var b : com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING)
            if (b instanceof DefenceTower t)
                byKind.putIfAbsent(t.kind, t);
        List<String> problems = new ArrayList<>();
        for (DefenceTower.Kind k : DefenceTower.Kind.values())
            if (!byKind.containsKey(k))
                problems.add("no tower of kind " + k + " is registered");
        List<Mob> spawned = new ArrayList<>();
        try {
            ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
            ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_3);
            for (DefenceTower t : byKind.values()) {
                var bp = BuildingUtils.getNewBuildingPlacement(t, level, at, Rotation.NONE, owner, false);
                if (!(bp instanceof DefenceTower.DefenceTowerPlacement tower)) {
                    problems.add(t.name + " is not a DefenceTowerPlacement");
                    continue;
                }
                tower.isBuilt = true;
                Mob target = (t.kind.airOnly ? EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get() : EntityRegistrar.CUTLASS_RAIDER_UNIT.get())
                    .create(level);
                if (target == null) {
                    problems.add("could not make a target for " + t.name);
                    continue;
                }
                target.moveTo(tower.centrePos.getX() + 4.5, y + (t.kind.airOnly ? 6 : 0), tower.centrePos.getZ() + 0.5, 0, 0);
                target.setNoGravity(true);
                ((Unit) target).setOwnerName(foeOwner);
                level.addFreshEntity(target);
                spawned.add(target);
                UnitGrid.invalidate();
                List<Integer> shots = new ArrayList<>();
                int strays = 0;
                for (int tick = 0; tick < ticks; tick++) {
                    target.invulnerableTime = 0;
                    target.setHealth(target.getMaxHealth());
                    tower.tickAge++;
                    tower.tickWeapon(level);
                    if (tower.cooldown == t.kind.cooldownTicks) {
                        shots.add(tick);
                        if (target.getHealth() >= target.getMaxHealth())
                            strays++;
                    }
                }
                target.discard();
                int cd = t.kind.cooldownTicks;
                if (strays > 0)
                    problems.add(t.name + ": " + strays + " shots missed its own target (something else in reach)");
                if (shots.size() < 3) {
                    problems.add(t.name + " fired " + shots.size() + " shots in " + ticks / 20 + " s (set to every " + cd / 20f + " s)");
                    continue;
                }
                Set<Integer> gaps = new java.util.TreeSet<>();
                for (int i = 1; i < shots.size(); i++)
                    gaps.add(shots.get(i) - shots.get(i - 1));
                double mean = (double) (shots.get(shots.size() - 1) - shots.get(0)) / (shots.size() - 1);
                ReignOfNether.LOGGER.info("[TowerRate] {} ({}) fired {} shots, every {} ticks (set to {})", t.name, t.kind,
                    shots.size(), mean, cd);
                if (!gaps.equals(Set.of(cd)))
                    problems.add(String.format("%s (%s) fired every %.2f s (gaps %s ticks), set to every %.2f s: %.1f dps instead of %.1f",
                        t.name, t.kind, mean / 20, gaps, cd / 20f, t.kind.damage * 20 / mean, t.kind.dps()));
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            ResearchServerEvents.removeAllResearchFor(owner);
            for (Mob m : spawned)
                m.discard();
        }
    }

    /**
     * The Rail Battery and the Storm Oak keep their configured rate with the game's own target scan, as the towers do
     * (defence_towers_fire_at_their_configured_rate): against a target that stays in range the rail fires every
     * CHARGE_TICKS + COOLDOWN_TICKS and the oak strikes every COOLDOWN_TICKS. Both used to skip the tick the cooldown
     * ran out and then wait for the next SCAN_TICKS: 140 and 130 ticks instead of 130 and 120. A shot is read off the
     * target's health alone; the same floating spot as the tower test (y = 380, no onlyFoe).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void defence_rail_battery_and_storm_oak_fire_at_their_configured_rate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_weaponrate_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        final int y = 380, ticks = 800;
        BlockPos at = helper.absolutePos(new BlockPos(6, 0, 6)).atY(y);
        record Weapon(Building building, int period) { }
        List<Weapon> weapons = List.of(
            new Weapon(Buildings.RAIL_BATTERY, RailBatteryPlacement.CHARGE_TICKS + RailBatteryPlacement.COOLDOWN_TICKS),
            new Weapon(Buildings.STORM_OAK, StormOakPlacement.COOLDOWN_TICKS));
        List<String> problems = new ArrayList<>();
        List<Mob> spawned = new ArrayList<>();
        try {
            ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
            for (Weapon w : weapons) {
                var bp = BuildingUtils.getNewBuildingPlacement(w.building(), level, at, Rotation.NONE, owner, false);
                Runnable tickWeapon;
                if (bp instanceof RailBatteryPlacement rail)
                    tickWeapon = () -> rail.tickRail(level);
                else if (bp instanceof StormOakPlacement oak)
                    tickWeapon = () -> oak.tickStrike(level);
                else {
                    problems.add(w.building().name + " has no weapon placement: " + bp);
                    continue;
                }
                bp.isBuilt = true;
                Mob target = EntityRegistrar.SENTINEL_TREANT_UNIT.get().create(level);
                if (target == null) {
                    problems.add("could not make a Sentinel Treant");
                    break;
                }
                target.moveTo(bp.centrePos.getX() + 0.5, y, bp.centrePos.getZ() + 6.5, 0, 0);
                target.setNoGravity(true);
                ((Unit) target).setOwnerName(foeOwner);
                level.addFreshEntity(target);
                spawned.add(target);
                UnitGrid.invalidate();
                List<Integer> shots = new ArrayList<>();
                for (int tick = 0; tick < ticks && target.isAlive(); tick++) {
                    target.invulnerableTime = 0;
                    target.setHealth(target.getMaxHealth());
                    bp.tickAge++;
                    tickWeapon.run();
                    if (target.getHealth() < target.getMaxHealth())
                        shots.add(tick);
                }
                boolean died = !target.isAlive();
                target.discard();
                if (died)
                    problems.add(w.building().name + " killed its full-health target in one shot: pick a sturdier one");
                if (shots.size() < 3) {
                    problems.add(w.building().name + " hit " + shots.size() + " times in " + ticks / 20 + " s (set to every "
                        + w.period() / 20f + " s)");
                    continue;
                }
                Set<Integer> gaps = new java.util.TreeSet<>();
                for (int i = 1; i < shots.size(); i++)
                    gaps.add(shots.get(i) - shots.get(i - 1));
                double mean = (double) (shots.get(shots.size() - 1) - shots.get(0)) / (shots.size() - 1);
                ReignOfNether.LOGGER.info("[WeaponRate] {} hit {} times, every {} ticks (set to {})", w.building().name,
                    shots.size(), mean, w.period());
                if (!gaps.equals(Set.of(w.period())))
                    problems.add(String.format("%s fired every %.2f s (gaps %s ticks), set to every %.2f s",
                        w.building().name, mean / 20, gaps, w.period() / 20f));
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            ResearchServerEvents.removeAllResearchFor(owner);
            for (Mob m : spawned)
                m.discard();
        }
    }

    /**
     * Every faction's anti-T3 emplacement costs 10-20% of the priciest experimental (metal and energy, against the
     * DefenceCosts list and every bot kit's T3 unit), is big (5x5 and up), short-ranged, dormant without Tier 3, and hits
     * an experimental at least three times as hard as a raider. Every live faction has its full defence set.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void defence_anti_t3_costs_ten_to_twenty_percent_of_a_t3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_antit3_" + helper.absolutePos(BlockPos.ZERO).asLong();
        int maxMetal = 0, maxEnergy = 0;
        for (var c : DefenceCosts.T3_UNITS) {
            maxMetal = Math.max(maxMetal, c.metal());
            maxEnergy = Math.max(maxEnergy, c.energy());
        }
        var colossus = EntityRegistrar.SUN_COLOSSUS_UNIT.get().create(level);
        var raider = EntityRegistrar.CUTLASS_RAIDER_UNIT.get().create(level);
        List<net.minecraft.world.entity.Entity> made = new ArrayList<>();
        try {
            for (var rl : Factions.PLAYABLE_FACTIONS) {
                Faction f = Factions.getFaction(rl);
                if (!Factions.isLive(f))
                    continue;
                Defences.Set set = Defences.of(f);
                if (set == null) {
                    helper.fail(f.getName() + " has no static defences");
                    continue;
                }
                for (var b : set.all())
                    if (!f.equals(b.getFaction()))
                        helper.fail(b.name + " is not registered to " + f.getName());
                var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(f);
                if (kit != null && kit.t3Unit() != null) {
                    var type = EntityRegistrar.getEntityType(kit.t3Unit().getItemName());
                    net.minecraft.world.entity.Entity e = type == null ? null : type.create(level);
                    if (e != null) {
                        made.add(e);
                        if (e instanceof Unit u && u.getCost() != null) {
                            maxMetal = Math.max(maxMetal, u.getCost().metal());
                            maxEnergy = Math.max(maxEnergy, u.getCost().energy());
                        }
                    }
                }
            }
            if (maxMetal <= 0)
                helper.fail("no T3 unit has a cost");
            for (Defences.Set set : Defences.sets()) {
                DefenceTower anti = set.antiT3();
                double rm = (double) anti.cost.metal() / maxMetal, re = (double) anti.cost.energy() / Math.max(1, maxEnergy);
                if (rm < 0.10 || rm > 0.20 || re < 0.10 || re > 0.20)
                    helper.fail(anti.name + " costs " + anti.cost.metal() + "m/" + anti.cost.energy() + "e = "
                        + Math.round(rm * 100) + "%/" + Math.round(re * 100) + "% of the priciest T3 (" + maxMetal + "m/" + maxEnergy + "e)");
                if (anti.kind != DefenceTower.Kind.ANTI_T3 || anti.kind.range > 20)
                    helper.fail(anti.name + " should be a short-ranged anti-T3 emplacement");
                var bp = BuildingUtils.getNewBuildingPlacement(anti, level, helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
                if (!(bp instanceof DefenceTower.DefenceTowerPlacement p)) {
                    helper.fail(anti.name + " is not a DefenceTowerPlacement");
                    continue;
                }
                if (p.maxCorner.getX() - p.minCorner.getX() + 1 < 5 || p.maxCorner.getZ() - p.minCorner.getZ() + 1 < 5)
                    helper.fail(anti.name + " should take a big (5x5) footprint");
                p.isBuilt = true;
                com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
                if (!p.dormant())
                    helper.fail(anti.name + " fires without Tier 3");
                if (colossus != null && raider != null && anti.damageAgainst(colossus) < 3 * anti.damageAgainst(raider))
                    helper.fail(anti.name + " should hit an experimental at least 3x as hard as a raider");
                if (set.raidTower().cost.metal() >= anti.cost.metal())
                    helper.fail(set.raidTower().name + " should be far cheaper than the anti-T3 emplacement");
            }
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
            var bp = BuildingUtils.getNewBuildingPlacement(Defences.VILLAGERS.antiT3(), level, helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
            if (bp instanceof DefenceTower.DefenceTowerPlacement p) {
                p.isBuilt = true;
                if (p.dormant())
                    helper.fail("the anti-T3 emplacement stays dormant with Tier 3");
            }
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
            if (colossus != null)
                colossus.discard();
            if (raider != null)
                raider.discard();
            for (var e : made)
                e.discard();
        }
    }
}
