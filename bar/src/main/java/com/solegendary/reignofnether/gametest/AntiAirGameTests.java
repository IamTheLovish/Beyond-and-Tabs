package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.T2Workers;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Oct 21 towers (design/defences.md; lovish 02:47: "flying units ran through everything... anti air defenses should
 * be added"; 02:51: "tier 1 constructors can make enhanced version of tier 1 defenses - kinda like in bar"): the Sky
 * Watch and its enhanced twin, the flak and the enhanced anti-raid tower of all seven factions. Their own holder (the CI
 * guard keeps new tests out of SkirmishGameTests); placements are made with getNewBuildingPlacement and never
 * registered in the world's building list, so no other test can see them; every weapon is ticked synchronously with
 * i-frames cleared between shots, as DefenceGameTests does.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AntiAirGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @Nullable
    static DefenceTower.DefenceTowerPlacement place(GameTestHelper helper, DefenceTower tower, String owner, String foe) {
        var bp = BuildingUtils.getNewBuildingPlacement(tower, helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 6)),
            Rotation.NONE, owner, false);
        if (!(bp instanceof DefenceTower.DefenceTowerPlacement p))
            return null;
        p.isBuilt = true;
        p.onlyFoe = foe;
        return p;
    }

    @Nullable
    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z, String owner, List<Mob> spawned) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(x, y, z, 0, 0);
        m.setNoGravity(true);   // a flyer stays where the test put it; a ground unit is put on the ground anyway
        ((Unit) m).setOwnerName(owner);
        level.addFreshEntity(m);
        spawned.add(m);
        return m;
    }

    /** Ticks the weapon up to {@code ticks} times or until {@code done}; clears the targets' i-frames between ticks. */
    static int tickUntil(ServerLevel level, DefenceTower.DefenceTowerPlacement p, List<Mob> targets, int ticks,
                         java.util.function.BooleanSupplier done) {
        for (int t = 0; t < ticks; t++) {
            if (done.getAsBoolean())
                return t;
            for (Mob m : targets)
                m.invulnerableTime = 0;
            p.tickWeapon(level);
        }
        return ticks;
    }

    /**
     * Every faction's Sky Watch, enhanced Sky Watch and flak shoot an enemy T2 flyer (a Gyrocopter Gunship, 90 hp - not
     * the Phoenix, whose rebirth cancels its first death) down - the Sky Watch and the flak within 10 s, the enhanced
     * Sky Watch within 5 s - and never touch an enemy ground raider standing just as close. The flak stays dormant
     * without Tier 2 research.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void anti_air_towers_shoot_flyers_down_and_ignore_ground_units(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_aa_" + helper.absolutePos(BlockPos.ZERO).asLong(), foe = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        List<Mob> spawned = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            ResearchServerEvents.removeAllResearchFor(owner);
            for (Defences.TowerSet set : Defences.towerSets()) {
                for (DefenceTower tower : List.of(set.skyWatch(), set.skyWatchHeavy(), set.flak())) {
                    var p = place(helper, tower, owner, foe);
                    if (p == null) {
                        problems.add(tower.name + " is not a DefenceTowerPlacement");
                        continue;
                    }
                    double cx = p.centrePos.getX() + 0.5, cz = p.centrePos.getZ() + 0.5;
                    Mob flyer = spawn(level, EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), cx + 4, gy + 6, cz, foe, spawned);
                    Mob raider = spawn(level, EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), cx - 3, gy, cz, foe, spawned);
                    if (flyer == null || raider == null || !((Unit) flyer).isFlyingUnit()) {
                        problems.add("could not make a Gyrocopter Gunship and a Cutlass Raider");
                        break;
                    }
                    UnitGrid.invalidate();
                    float raiderHp = raider.getHealth();
                    if (tower.kind == DefenceTower.Kind.FLAK) {
                        if (!p.dormant() || p.fire(level) != null)
                            problems.add(tower.name + " fires without Tier 2");
                        ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
                        if (p.dormant())
                            problems.add(tower.name + " stays dormant with Tier 2");
                    }
                    int limit = tower.kind == DefenceTower.Kind.SKY_WATCH_HEAVY ? 100 : 200;
                    int took = tickUntil(level, p, List.of(flyer, raider), limit, () -> !flyer.isAlive());
                    if (flyer.isAlive())
                        problems.add(tower.name + " left a Gunship at " + Math.round(flyer.getHealth()) + " hp after "
                            + limit / 20 + " s (" + tower.kind.dps() + " dps, range " + tower.kind.range + ")");
                    else
                        ReignOfNether.LOGGER.info("[AntiAir] {} downed a Gyrocopter Gunship in {} s", tower.name, took / 20f);
                    if (raider.getHealth() < raiderHp)
                        problems.add(tower.name + " shot a ground unit (anti-air only)");
                    ResearchServerEvents.removeAllResearchFor(owner);
                    flyer.discard();
                    raider.discard();
                }
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

    /** One flak burst hits every enemy flyer within its splash of the target - a clump of three takes damage at once. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void flak_burst_hits_every_flyer_in_a_clump(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_flak_" + helper.absolutePos(BlockPos.ZERO).asLong(), foe = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        List<Mob> spawned = new ArrayList<>();
        try {
            ResearchServerEvents.addResearch(owner, ProductionItems.RESEARCH_TIER_2);
            var p = place(helper, Defences.VILLAGERS_TOWERS.flak(), owner, foe);
            if (p == null) {
                helper.fail("the flak is not a DefenceTowerPlacement");
                return;
            }
            double cx = p.centrePos.getX() + 0.5, cz = p.centrePos.getZ() + 0.5;
            List<Mob> clump = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Mob m = spawn(level, EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), cx + 5 + i * 0.9, gy + 6, cz + (i % 2) * 0.9, foe, spawned);
                if (m == null) {
                    helper.fail("could not make a Gyrocopter Gunship");
                    return;
                }
                clump.add(m);
            }
            UnitGrid.invalidate();
            float full = clump.get(0).getMaxHealth();
            if (p.fire(level) == null) {
                helper.fail("the flak did not fire at three flyers in range");
                return;
            }
            int hit = 0;
            for (Mob m : clump)
                if (m.getHealth() < full)
                    hit++;
            if (hit < 3)
                helper.fail("one flak burst hit " + hit + " of a clump of 3 flyers (splash " + p.tower.kind.splash + ")");
            else
                helper.succeed();
        } finally {
            ResearchServerEvents.removeAllResearchFor(owner);
            for (Mob m : spawned)
                m.discard();
        }
    }

    /**
     * The rules round the new towers, for every live faction: each has its four, registered to it; the enhanced towers
     * wait for a T1 lab and cost about twice their base tower for more than twice its damage; only a T2 builder may
     * place the flak; combat engineers may raise the Sky Watch in the field but not the enhanced towers or the flak.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void enhanced_and_anti_air_towers_follow_their_gates_and_prices(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (var rl : Factions.PLAYABLE_FACTIONS) {
            Faction f = Factions.getFaction(rl);
            if (!Factions.isLive(f))
                continue;
            Defences.Set base = Defences.of(f);
            Defences.TowerSet set = Defences.towersOf(f);
            if (base == null || set == null) {
                problems.add(f.getName() + " has no " + (base == null ? "defences" : "Oct 21 towers"));
                continue;
            }
            for (var b : set.all())
                if (!f.equals(b.getFaction()))
                    problems.add(b.name + " is not registered to " + f.getName());
            check(problems, set.raidTowerHeavy(), base.raidTower());
            check(problems, set.skyWatchHeavy(), set.skyWatch());
            if (!T2Workers.needsT2Builder(set.flak()))
                problems.add(set.flak().name + " should need a T2 builder");
            for (DefenceTower t : List.of(set.raidTowerHeavy(), set.skyWatch(), set.skyWatchHeavy()))
                if (T2Workers.needsT2Builder(t))
                    problems.add(t.name + " should be a T1 builder's job");
            if (!CombatEngineers.mayStart(set.skyWatch()))
                problems.add("combat engineers should raise the " + set.skyWatch().name);
            for (DefenceTower t : List.of(set.raidTowerHeavy(), set.skyWatchHeavy(), set.flak()))
                if (CombatEngineers.mayStart(t))
                    problems.add("combat engineers should not raise the " + t.name);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** An enhanced tower: gated by a T1 lab, 1.8-2.6x its base tower's metal, at least twice its damage a second. */
    static void check(List<String> problems, DefenceTower enhanced, DefenceTower base) {
        if (enhanced.kind.gate != DefenceTower.Gate.T1_LAB)
            problems.add(enhanced.name + " should wait for a T1 lab");
        double cost = (double) enhanced.cost.metal() / Math.max(1, base.cost.metal());
        if (cost < 1.8 || cost > 2.6)
            problems.add(enhanced.name + " costs " + Math.round(cost * 100) / 100.0 + "x its base tower (want ~2.2x)");
        if (enhanced.kind.dps() < 2 * base.kind.dps())
            problems.add(enhanced.name + " does " + enhanced.kind.dps() + " dps, under twice " + base.name + "'s " + base.kind.dps());
        if (enhanced.kind.airOnly != base.kind.airOnly)
            problems.add(enhanced.name + " should shoot what " + base.name + " shoots");
    }
}
