package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.T2Workers;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The anti-siege tower (BAR's HLT; lovish Oct 29, design/siege_t1.md "Separate step: the HLT"): every faction's heavy
 * T1-lab tower that stops the T1 siege lobber (range 15). 1000 hp, range 20, 45 damage every 1.5 s, ground only, 450
 * metal / 650 energy, 3x7x3. Its own holder (the CI guard keeps new tests out of SkirmishGameTests). The towers are looked
 * up by registry id ({@link #ID_PREFIX} + the faction's defence suffix), so each test fails with a message, not a
 * compile error, while a faction has none. Placements are made with getNewBuildingPlacement and never registered in the
 * world's building list, and the weapon is ticked synchronously with i-frames cleared, as AntiAirGameTests does.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AntiSiegeTowerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    /** The anti-siege tower's id is this plus the faction's defence suffix ("siege_guard_villagers", ...). */
    static final String ID_PREFIX = "siege_guard_";
    /** The T1 siege lobber's reach (design/siege_t1.md, Rocko model): the tower must outrange it. */
    static final int LOBBER_RANGE = 15;
    /** The lobber's health (design/siege_t1.md): two shots must kill one. */
    static final float LOBBER_HP = 80f;
    /** The lobber's armour. */
    static final double LOBBER_ARMOUR = 2;

    /** The faction's anti-siege tower, or null while it has none. */
    @Nullable
    static DefenceTower towerOf(Defences.Set base) {
        Building b = ReignOfNetherRegistries.BUILDING.get(
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, ID_PREFIX + base.raidTower().style.suffix()));
        return b instanceof DefenceTower t ? t : null;
    }

    @Nullable
    static DefenceTower.DefenceTowerPlacement place(GameTestHelper helper, DefenceTower tower, BlockPos at, String owner, String foe) {
        var bp = BuildingUtils.getNewBuildingPlacement(tower, helper.getLevel(), helper.absolutePos(at), Rotation.NONE, owner, false);
        if (!(bp instanceof DefenceTower.DefenceTowerPlacement p))
            return null;
        p.isBuilt = true;
        p.onlyFoe = foe;
        return p;
    }

    /**
     * For every live faction: an anti-siege tower registered to it with the HLT's numbers - 1000 hp, range 20, 45 damage
     * every 1.5 s (two shots kill a lobber), no light bonus, ground only, 450 metal / 650 energy, a 3x7x3 footprint,
     * build time x1.2 - waiting for the same T1 labs as the enhanced anti-raid tower (a T1 builder's job, not the combat
     * engineers'). It outranges the lobber (15, shooting at the tower's near face) and the enhanced anti-raid tower, and
     * is itself outranged by the long artillery (the Bombard Crew's 22): artillery is the HLT's answer, as in BAR.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void anti_siege_tower_has_the_hlt_numbers_for_every_faction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_hlt_stats_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<String> problems = new ArrayList<>();
        for (var rl : Factions.PLAYABLE_FACTIONS) {
            Faction f = Factions.getFaction(rl);
            if (!Factions.isLive(f))
                continue;
            Defences.Set base = Defences.of(f);
            Defences.TowerSet towers = Defences.towersOf(f);
            if (base == null || towers == null) {
                problems.add(f.getName() + " has no defences");
                continue;
            }
            DefenceTower t = towerOf(base);
            if (t == null) {
                problems.add(f.getName() + " has no anti-siege tower (" + ID_PREFIX + base.raidTower().style.suffix() + ")");
                continue;
            }
            if (!f.equals(t.getFaction()))
                problems.add(t.name + " is not registered to " + f.getName());
            DefenceTower.Kind k = t.kind;
            if (t.maxHealth != 1000 || k.hp != 1000)
                problems.add(t.name + " has " + t.maxHealth + " hp (want 1000)");
            if (k.range != 20)
                problems.add(t.name + " has range " + k.range + " (want 20)");
            if (k.damage != 45f || k.cooldownTicks != 30)
                problems.add(t.name + " deals " + k.damage + " every " + k.cooldownTicks + " ticks (want 45 every 30)");
            if (k.bonusMult != 1f)
                problems.add(t.name + " should have no bonus (x" + k.bonusMult + ")");
            if (k.hitsAir || k.airOnly || k.splash != 0f)
                problems.add(t.name + " should shoot single ground targets only");
            if (t.cost.metal() != 450 || t.cost.energy() != 650)
                problems.add(t.name + " costs " + t.cost.metal() + " M / " + t.cost.energy() + " E (want 450 / 650)");
            if (Math.abs(t.getBuildTimeModifier() - 1.2f) > 1e-4)
                problems.add(t.name + " builds at x" + t.getBuildTimeModifier() + " (want x1.2)");
            if (k.gate != DefenceTower.Gate.T1_LAB || t.requiredLabs() == null
                    || !t.requiredLabs().get().equals(towers.raidTowerHeavy().requiredLabs().get()))
                problems.add(t.name + " should wait for the T1 labs that unlock " + towers.raidTowerHeavy().name);
            if (T2Workers.needsT2Builder(t))
                problems.add(t.name + " should be a T1 builder's job");
            if (CombatEngineers.mayStart(t))
                problems.add("combat engineers should not raise the " + t.name);
            if (2 * k.damage * (1 - LOBBER_ARMOUR * 0.04) < LOBBER_HP)
                problems.add(t.name + " needs more than two shots for a lobber (" + LOBBER_HP + " hp)");
            // the lobber shells the tower's nearest block: it stands its range plus half the footprint from the centre
            if (k.range < LOBBER_RANGE + 2 || k.range <= towers.raidTowerHeavy().kind.range)
                problems.add(t.name + " (range " + k.range + ") should outrange the lobber (" + LOBBER_RANGE
                    + " from its face) and " + towers.raidTowerHeavy().name + " (" + towers.raidTowerHeavy().kind.range + ")");
            if (k.range >= BombardCrewUnit.attackRange)
                problems.add(t.name + " (range " + k.range + ") should be outranged by the Bombard Crew (" + BombardCrewUnit.attackRange + ")");
            var p = BuildingUtils.getNewBuildingPlacement(t, level, helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
            if (p == null) {
                problems.add(t.name + " has no structure");
                continue;
            }
            int w = p.maxCorner.getX() - p.minCorner.getX() + 1, h = p.maxCorner.getY() - p.minCorner.getY() + 1,
                d = p.maxCorner.getZ() - p.minCorner.getZ() + 1;
            if (w != 3 || h != 7 || d != 3)
                problems.add(t.name + " is " + w + "x" + h + "x" + d + " (want 3x7x3)");
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * Every faction's anti-siege tower hits an enemy standing 17 blocks from its centre - where a range-15 lobber stands
     * to shell its near face - and two shots kill a lobber-weight target there (80 hp, armour 2), while the enhanced
     * anti-raid tower (range 16) cannot touch it from the same spot.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void anti_siege_tower_outranges_a_range_15_attacker_and_kills_it_in_two_shots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_hlt_reach_" + helper.absolutePos(BlockPos.ZERO).asLong(), foe = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        List<Mob> spawned = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            for (Defences.Set base : Defences.sets()) {
                DefenceTower tower = towerOf(base);
                if (tower == null) {
                    problems.add(base.factionName() + " has no anti-siege tower");
                    continue;
                }
                Defences.TowerSet towers = null;
                for (Defences.TowerSet ts : Defences.towerSets())
                    if (ts.factionName().equals(base.factionName()))
                        towers = ts;
                // the tower in the arena's corner, the target 17 blocks away on the diagonal (12, 12): inside the arena
                var p = place(helper, tower, new BlockPos(0, 1, 0), owner, foe);
                var beamer = towers == null ? null : place(helper, towers.raidTowerHeavy(), new BlockPos(0, 1, 0), owner, foe);
                if (p == null || beamer == null) {
                    problems.add(base.factionName() + ": could not place the towers");
                    continue;
                }
                double cx = p.centrePos.getX() + 0.5, cz = p.centrePos.getZ() + 0.5;
                Mob target = EntityRegistrar.CUTLASS_RAIDER_UNIT.get().create(level);
                if (target == null) {
                    problems.add("could not create a Cutlass Raider");
                    break;
                }
                target.moveTo(cx + 12, gy, cz + 12, 0, 0);
                ((Unit) target).setOwnerName(foe);
                ((Unit) target).setHoldPosition(true);
                level.addFreshEntity(target);
                spawned.add(target);
                var maxHp = target.getAttribute(Attributes.MAX_HEALTH);
                var armour = target.getAttribute(Attributes.ARMOR);
                if (maxHp != null)
                    maxHp.setBaseValue(LOBBER_HP);
                if (armour != null)
                    armour.setBaseValue(LOBBER_ARMOUR);
                target.setHealth(LOBBER_HP);
                UnitGrid.invalidate();
                double dist = Math.sqrt(target.distanceToSqr(cx, target.getY(), cz));

                // the enhanced anti-raid tower: out of its reach
                for (int t = 0; t < 40; t++) {
                    target.invulnerableTime = 0;
                    beamer.tickWeapon(level);
                }
                if (target.getHealth() < LOBBER_HP)
                    problems.add(towers.raidTowerHeavy().name + " (range " + towers.raidTowerHeavy().kind.range
                        + ") hit a target " + Math.round(dist * 10) / 10.0 + " blocks away");
                target.setHealth(LOBBER_HP);

                // the anti-siege tower: the first shot at once, the second one cooldown later, and it is dead
                int shots = 0, ticks = 0, limit = 2 * tower.kind.cooldownTicks + 5;
                float before = target.getHealth();
                for (; ticks < limit && target.isAlive(); ticks++) {
                    target.invulnerableTime = 0;
                    p.tickWeapon(level);
                    if (!target.isAlive() || target.getHealth() < before)
                        shots++;
                    before = target.getHealth();
                }
                if (target.isAlive())
                    problems.add(tower.name + " left a lobber-weight target " + Math.round(dist * 10) / 10.0 + " blocks away at "
                        + Math.round(target.getHealth()) + "/" + Math.round(LOBBER_HP) + " hp after " + shots + " shots in "
                        + ticks + " ticks (range " + tower.kind.range + ", " + tower.kind.damage + " a shot, armour "
                        + target.getArmorValue() + ")");
                else if (shots > 2)
                    problems.add(tower.name + " needed " + shots + " shots for a lobber-weight target (want 2)");
                else
                    ReignOfNether.LOGGER.info("[AntiSiege] {} killed an 80 hp target {} blocks away in {} shots ({} ticks)",
                        tower.name, Math.round(dist * 10) / 10.0, shots, ticks);
                target.discard();
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
     * Every faction's anti-siege tower shoots a ground unit and never an enemy flyer - even one nearer than the ground
     * unit (the Sky Watch is the anti-air answer).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void anti_siege_tower_shoots_ground_units_only(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_hlt_ground_" + helper.absolutePos(BlockPos.ZERO).asLong(), foe = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        List<Mob> spawned = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            for (Defences.Set base : Defences.sets()) {
                DefenceTower tower = towerOf(base);
                if (tower == null) {
                    problems.add(base.factionName() + " has no anti-siege tower");
                    continue;
                }
                var p = place(helper, tower, new BlockPos(6, 1, 6), owner, foe);
                if (p == null) {
                    problems.add(tower.name + " is not a DefenceTowerPlacement");
                    continue;
                }
                double cx = p.centrePos.getX() + 0.5, cz = p.centrePos.getZ() + 0.5;
                Mob flyer = AntiAirGameTests.spawn(level, EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), cx + 3, gy + 5, cz, foe, spawned);
                Mob raider = AntiAirGameTests.spawn(level, EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), cx - 6, gy, cz, foe, spawned);
                if (flyer == null || raider == null || !((Unit) flyer).isFlyingUnit()) {
                    problems.add("could not make a Gyrocopter Gunship and a Cutlass Raider");
                    break;
                }
                ((Unit) raider).setHoldPosition(true);
                UnitGrid.invalidate();
                float flyerHp = flyer.getHealth(), raiderHp = raider.getHealth();
                AntiAirGameTests.tickUntil(level, p, List.of(flyer, raider), 2 * tower.kind.cooldownTicks + 5, () -> false);
                if (flyer.getHealth() < flyerHp)
                    problems.add(tower.name + " shot a flyer (ground only)");
                if (raider.isAlive() && raider.getHealth() >= raiderHp)
                    problems.add(tower.name + " never shot a ground raider 6 blocks away");
                flyer.discard();
                raider.discard();
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
}
