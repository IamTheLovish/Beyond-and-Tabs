package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.bot.BotArmy;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.raid.RaidTuning;
import com.solegendary.reignofnether.raid.WorkerFlee;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The T1 raid package (lovish, Oct 22: "Tier 1 units should be able to raid bases early" - infantry hunt workers, vehicles
 * break buildings, converters are the fragile eco building, extractors are softer than the rest). Every number lives in
 * {@link RaidTuning}; these tests hold the line on the outcomes and print the measured times as "[Raid]" lines (CI folds
 * them into one notice), so the PR's tables are what the game did, not what the arithmetic says.
 * <p>
 * The live fights run on their own glass pads far from the test grid (like the duel simulator: neighbouring arenas sit
 * ~21 blocks apart, inside a raider's aggro range). Each scenario owns a 32x16 pad, its own owner names, and is timed
 * from the first point of damage to the death (the walk-in is not the raid's merit), with the whole time from the order
 * logged too. Nobody is healed and nothing is cheated: real units, real placements, real server-tick destruction.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class RaidGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PAD_Y = DuelGameTests.PAD_Y;
    static final int BASE = 60000;       // chunk aligned; far from the duel grids (3000-ish) and the 30000/36000 columns
    static final int COLUMNS = 6;

    // ---------------------------------------------------------------------------------------------------------------
    // The windows (seconds). Time is first damage -> death unless the name says total (order -> death).
    // ---------------------------------------------------------------------------------------------------------------

    /** A party of 6 raiders on a lone worker (design: ~2-4 s) and on a group of 4 (design: ~8-12 s). */
    static final float LONE_PURE_MAX = 5f, LONE_TOTAL_MAX = 11f, GROUP_PURE_MAX = 13f, GROUP_TOTAL_MAX = 24f;
    /** 6 infantry raiders on a converter (design: 4-6 s for 4-6 units). */
    static final float CONV_MIN = 2.5f, CONV_MAX = 8f;
    /** 6 infantry raiders on a windmill, and on a T1 extractor (design: 12-18 s for 4-6 units). */
    static final float WIND_INF_MIN = 5f, WIND_INF_MAX = 19f, EXT_INF_MIN = 7f, EXT_INF_MAX = 28f;
    /** 4 melee T1 vehicles on a windmill (design 5-8 s), a T1 extractor (design 6-10 s) and a T2 extractor (~2x). */
    static final float WIND_VEH_MAX = 12f, EXT_VEH_MAX = 14f, EXT2_VEH_MAX = 28f;
    /** Artillery lands its shells from range and starts later: looser. */
    static final float WIND_ART_MAX = 25f, EXT_ART_MAX = 32f, EXT2_ART_MAX = 50f;

    // ---------------------------------------------------------------------------------------------------------------
    // The factions' pieces
    // ---------------------------------------------------------------------------------------------------------------

    record Fac(String name, Supplier<? extends EntityType<? extends Mob>> worker, Supplier<? extends EntityType<? extends Mob>> raider,
               String raiderName, Building wind, Building ext, Building conv) { }

    /** A T1 vehicle: the faction it belongs to (index into facs()), its name and type, and whether it fights in melee. */
    record Veh(int fac, String name, Supplier<? extends EntityType<? extends Mob>> type, boolean melee) { }

    static List<Fac> facs() {
        return List.of(
            new Fac("Sunforged", EntityRegistrar.VILLAGER_UNIT, EntityRegistrar.VINDICATOR_UNIT, "Vindicator(no raider)",
                Buildings.WIND_GENERATOR_VILLAGERS, Buildings.METAL_EXTRACTOR_VILLAGERS, Buildings.ENERGY_CONVERTER_VILLAGERS),
            new Fac("Gravebound", EntityRegistrar.ZOMBIE_VILLAGER_UNIT, EntityRegistrar.SPIDER_UNIT, "Spider",
                Buildings.WIND_GENERATOR_MONSTERS, Buildings.METAL_EXTRACTOR_MONSTERS, Buildings.ENERGY_CONVERTER_MONSTERS),
            new Fac("Horde", EntityRegistrar.GRUNT_UNIT, EntityRegistrar.MARAUDER_UNIT, "Marauder",
                Buildings.WIND_GENERATOR_PIGLINS, Buildings.METAL_EXTRACTOR_PIGLINS, Buildings.ENERGY_CONVERTER_PIGLINS),
            new Fac("Verdant", EntityRegistrar.SEEDSHAPER_UNIT, EntityRegistrar.LEAFBLADE_UNIT, "Leafblade",
                Buildings.WIND_GENERATOR_VERDANT, Buildings.METAL_EXTRACTOR_VERDANT, Buildings.ENERGY_CONVERTER_VERDANT),
            new Fac("Tidewrought", EntityRegistrar.SHIPWRIGHT_UNIT, EntityRegistrar.CUTLASS_RAIDER_UNIT, "Cutlass",
                Buildings.WIND_GENERATOR_TIDE, Buildings.METAL_EXTRACTOR_TIDE, Buildings.ENERGY_CONVERTER_TIDE),
            new Fac("Stormcallers", EntityRegistrar.GEOMANCER_UNIT, EntityRegistrar.BLADE_DANCER_UNIT, "BladeDancer",
                Buildings.WIND_GENERATOR_STORM, Buildings.METAL_EXTRACTOR_STORM, Buildings.ENERGY_CONVERTER_STORM),
            new Fac("Brassworks", EntityRegistrar.TINKER_UNIT, EntityRegistrar.STEAM_RUNNER_UNIT, "SteamRunner",
                Buildings.WIND_GENERATOR_BRASS, Buildings.METAL_EXTRACTOR_BRASS, Buildings.ENERGY_CONVERTER_BRASS));
    }

    static List<Veh> vehicles() {
        return List.of(
            new Veh(2, "Hoglin", EntityRegistrar.HOGLIN_UNIT, true),
            new Veh(3, "SentinelTreant", EntityRegistrar.SENTINEL_TREANT_UNIT, true),
            new Veh(4, "MudskipperSloop", EntityRegistrar.MUDSKIPPER_SLOOP_UNIT, true),
            new Veh(4, "BombardCrew", EntityRegistrar.BOMBARD_CREW_UNIT, false),
            new Veh(5, "RocketBattery", EntityRegistrar.ROCKET_BATTERY_UNIT, false),
            new Veh(6, "MortarCrawler", EntityRegistrar.MORTAR_CRAWLER_UNIT, false));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Pads, spawning, building
    // ---------------------------------------------------------------------------------------------------------------

    record Cell(int x0, int z0) { }

    /** A block of far pads (one per scenario) held loaded for the test: slot picks the column of pads, n how many. */
    static final class Pads {
        final ServerLevel level;
        final List<Cell> cells = new ArrayList<>();
        final List<long[]> chunks = new ArrayList<>();

        Pads(ServerLevel level, int slot, int n) {
            this.level = level;
            for (int i = 0; i < n; i++) {
                int x0 = BASE + slot * 1024 + (i % COLUMNS) * 64, z0 = BASE + (i / COLUMNS) * 48;
                cells.add(new Cell(x0, z0));
                chunks.add(new long[] { x0 >> 4, z0 >> 4 });
                chunks.add(new long[] { (x0 >> 4) + 1, z0 >> 4 });
            }
            GameTestChunks.holdAndAwaitOrFail(level, chunks, "raid pad");
            for (Cell c : cells)
                DuelGameTests.buildPad(level, c.x0(), c.z0());
        }

        void release() {
            GameTestChunks.releaseAll(level, chunks);
        }
    }

    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, String owner, double x, double z) {
        Mob e = type.create(level);
        if (e == null)
            return null;
        e.moveTo(x, PAD_Y + 1, z, 0, 0);
        if (e instanceof Unit u)
            u.setOwnerName(owner);
        if (!level.addFreshEntity(e)) {
            e.discard();
            return null;
        }
        return e;
    }

    /** A built, standing placement of {@code b} with its blocks in the world; t2 puts the extractor's copper tower on it. */
    static BuildingPlacement build(ServerLevel level, Building b, BlockPos origin, String owner, boolean t2) {
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(b, level, origin, Rotation.NONE, owner, false);
        if (bp == null)
            return null;
        if (t2 && b instanceof MetalExtractor mex)
            bp.changeStructure(mex.getUpgradedStructureName(1));
        for (BuildingBlock bb : bp.getBlocks())
            if (!bb.getBlockState().isAir())
                level.setBlock(bb.getBlockPos(), bb.getBlockState(), 2);
        bp.initPlacedBlocks();
        bp.isBuilt = true;
        BuildingServerEvents.getBuildings().add(bp);
        return bp;
    }

    static float secs(int ticks) {
        return ticks / 20f;
    }

    static String fmt(float s) {
        return s < 0 ? "n/a" : String.format(Locale.ROOT, "%.1f", s);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The driver: scenarios on pads, timed from the order
    // ---------------------------------------------------------------------------------------------------------------

    /** One timed fight: attackers against workers (victims) or a building (target). */
    static final class Sim {
        final String group, kind;
        final Cell cell;
        final String atk, foe;
        final List<Mob> attackers = new ArrayList<>(), victims = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        BuildingPlacement target;
        Runnable order;
        int contact = -1, done = -1, nearAfter3s = -1, startBlocks = -1;

        Sim(String group, String kind, Cell cell, String atk, String foe) {
            this.group = group;
            this.kind = kind;
            this.cell = cell;
            this.atk = atk;
            this.foe = foe;
        }

        void observe(int t) {
            if (target != null) {
                if (contact < 0 && startBlocks >= 0 && target.getBlocksPlaced() < startBlocks)
                    contact = t;
                if (done < 0 && (target.shouldBeDestroyed() || !BuildingServerEvents.getBuildings().contains(target)))
                    done = t;
            } else {
                boolean allDead = !victims.isEmpty();
                for (Mob v : victims) {
                    if (contact < 0 && (!v.isAlive() || v.getHealth() < v.getMaxHealth()))
                        contact = t;
                    if (v.isAlive())
                        allDead = false;
                }
                if (done < 0 && allDead)
                    done = t;
            }
            if (done >= 0 && contact < 0)
                contact = done;
            if (target != null && nearAfter3s < 0 && contact >= 0 && t >= contact + 60) {
                nearAfter3s = 0;   // how many of the party are at the building three seconds into the fight (stragglers explain slow times)
                for (Mob m : attackers)
                    if (m.isAlive() && Math.sqrt(m.distanceToSqr(target.centrePos.getX() + 0.5, m.getY(), target.centrePos.getZ() + 0.5)) <= 7)
                        nearAfter3s++;
            }
        }

        /** First damage -> death in seconds, or -1 if it never finished. */
        float pure() {
            return contact < 0 || done < 0 ? -1f : secs(done - contact);
        }

        /** Order -> death in seconds, or -1. */
        float total() {
            return done < 0 ? -1f : secs(done);
        }

        void cleanup() {
            for (Mob m : attackers)
                m.discard();
            for (Mob m : victims)
                m.discard();
            if (target != null)
                BuildingServerEvents.getBuildings().remove(target);
        }
    }

    /** n units of a type, in two rows at the pad's west end, owned by the sim's attacker. */
    static void raiders(ServerLevel level, Sim s, Supplier<? extends EntityType<? extends Mob>> type, int n) {
        for (int k = 0; k < n; k++) {
            Mob m = spawn(level, type.get(), s.atk, s.cell.x0() + 4 + (k % 3) * 1.3, s.cell.z0() + 6 + (k / 3) * 1.5);
            if (m == null)
                s.errors.add("could not make a " + type.get().getDescriptionId());
            else
                s.attackers.add(m);
        }
    }

    /** A building target for the sim, and the order that sends the party at it. */
    static void siege(ServerLevel level, Sim s, Building b, boolean t2) {
        BlockPos origin = new BlockPos(s.cell.x0() + 19, PAD_Y + 1, s.cell.z0() + 5);
        s.target = build(level, b, origin, s.foe, t2);
        if (s.target == null) {
            s.errors.add("could not place " + b.structureName);
            return;
        }
        s.startBlocks = s.target.getBlocksPlaced();
        BuildingPlacement t = s.target;
        s.order = () -> {
            for (Mob m : s.attackers)
                if (m instanceof AttackerUnit au)
                    au.setAttackBuildingTarget(t.originPos, true);
        };
    }

    /**
     * Holds the pads, waits for their entities to tick, builds the sims (setup), gives the orders three ticks later and
     * watches every sim until all are over or {@code maxSim} ticks have passed; then releases the pads, cleans up and
     * hands the sims to the verdict (which fails the test or not; setup errors fail first).
     */
    static void drive(GameTestHelper helper, Pads pads, int maxSim, Supplier<List<Sim>> setup, Consumer<List<Sim>> verdict) {
        ServerLevel level = helper.getLevel();
        List<Sim> sims = new ArrayList<>();
        int[] phase = { 0, 0, 0 };   // 0 waiting, 1 spawned (orders due), 2 running, 3 finished; spawn tick; order tick
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "raid pad", () -> {
            sims.addAll(setup.get());
            phase[0] = 1;
            phase[1] = (int) helper.getTick();
        });
        helper.onEachTick(() -> {
            if (phase[0] == 0 || phase[0] == 3)
                return;
            int now = (int) helper.getTick();
            if (phase[0] == 1) {
                if (now - phase[1] < 3)
                    return;   // let the units join the unit lists
                for (Sim s : sims)
                    if (s.order != null)
                        s.order.run();
                phase[0] = 2;
                phase[2] = now;
                return;
            }
            int t = now - phase[2];
            boolean over = true;
            for (Sim s : sims) {
                s.observe(t);
                if (s.done < 0 && s.errors.isEmpty())
                    over = false;
            }
            if (!over && t < maxSim)
                return;
            phase[0] = 3;
            pads.release();
            List<String> setupErrors = new ArrayList<>();
            for (Sim s : sims)
                for (String e : s.errors)
                    setupErrors.add(s.group + " " + s.kind + ": " + e);
            try {
                if (!setupErrors.isEmpty())
                    helper.fail("setup: " + String.join("; ", setupErrors));
                verdict.accept(sims);
            } finally {
                for (Sim s : sims)
                    s.cleanup();
            }
        });
    }

    static Sim find(List<Sim> sims, String group, String kind) {
        for (Sim s : sims)
            if (s.group.equals(group) && s.kind.equals(kind))
                return s;
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 1. Raiders hunt workers
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * A party of six of each faction's fast T1 raider (the Sunforged have none: their best fast fighter is the
     * Vindicator, measured for reference and flagged) catches a lone worker and a group of four on open ground, attack
     * moving onto them like the bot's raid does. Lone: dead within {@link #LONE_PURE_MAX} s of the first blow; four:
     * all dead within {@link #GROUP_PURE_MAX} s.
     */
    @GameTest(template = ARENA, timeoutTicks = 2100)
    public static void raid_party_of_six_hunts_a_lone_worker_and_a_group_of_four(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Fac> facs = facs();
        Pads pads = new Pads(level, 0, facs.size() * 2);
        drive(helper, pads, 700, () -> {
            List<Sim> sims = new ArrayList<>();
            for (int i = 0; i < facs.size(); i++)
                for (int k = 0; k < 2; k++) {
                    Fac f = facs.get(i);
                    Cell c = pads.cells.get(i * 2 + k);
                    Sim s = new Sim(f.name(), k == 0 ? "lone" : "group", c, "gametest_raid_w" + i + k + "_a", "gametest_raid_w" + i + k + "_b");
                    raiders(level, s, f.raider(), 6);
                    int workers = k == 0 ? 1 : 4;
                    for (int w = 0; w < workers; w++) {
                        Mob m = spawn(level, f.worker().get(), s.foe, c.x0() + 20 + (w % 2) * 1.5, c.z0() + 7.5 + (w / 2) * 1.5);
                        if (m == null)
                            s.errors.add("could not make a worker");
                        else
                            s.victims.add(m);
                    }
                    BlockPos to = new BlockPos(c.x0() + 20, PAD_Y + 1, c.z0() + 8);
                    s.order = () -> {
                        for (Mob m : s.attackers)
                            if (m instanceof AttackerUnit au)
                                au.setAttackMoveTarget(to);
                    };
                    sims.add(s);
                }
            return sims;
        }, sims -> {
            List<String> problems = new ArrayList<>();
            for (Fac f : facs) {
                Sim lone = find(sims, f.name(), "lone"), group = find(sims, f.name(), "group");
                ReignOfNether.LOGGER.info("[Raid] workers {} {} x6: lone {}s (total {}s), four {}s (total {}s)", f.name(), f.raiderName(),
                    fmt(lone.pure()), fmt(lone.total()), fmt(group.pure()), fmt(group.total()));
                if (lone.pure() < 0 || lone.pure() > LONE_PURE_MAX || lone.total() > LONE_TOTAL_MAX)
                    problems.add(f.name() + " " + f.raiderName() + " lone worker: " + fmt(lone.pure()) + " s (total " + fmt(lone.total())
                        + "), wanted <= " + LONE_PURE_MAX + " (total " + LONE_TOTAL_MAX + ")");
                if (group.pure() < 0 || group.pure() > GROUP_PURE_MAX || group.total() > GROUP_TOTAL_MAX)
                    problems.add(f.name() + " " + f.raiderName() + " group of four: " + fmt(group.pure()) + " s (total " + fmt(group.total())
                        + "), wanted <= " + GROUP_PURE_MAX + " (total " + GROUP_TOTAL_MAX + ")");
            }
            if (!problems.isEmpty())
                throw new net.minecraft.gametest.framework.GameTestAssertException(String.join("; ", problems));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 2. Infantry against eco buildings (converter, windmill, T1 and T2 extractor)
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Six of each faction's raider on a converter (the fragile one), a windmill, a T1 extractor and a T2 extractor, each on
     * its own pad. The times are the PR's infantry table; the windows keep the converter fragile, the windmill and
     * extractor softer-but-real, and the T2 extractor about twice the T1.
     */
    @GameTest(template = ARENA, timeoutTicks = 2550)
    public static void raid_infantry_party_times_against_eco_buildings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Fac> facs = facs();
        Pads pads = new Pads(level, 1, facs.size() * 4);
        drive(helper, pads, 1150, () -> {
            List<Sim> sims = new ArrayList<>();
            for (int i = 0; i < facs.size(); i++) {
                Fac f = facs.get(i);
                for (int k = 0; k < 4; k++) {
                    Cell c = pads.cells.get(i * 4 + k);
                    String kind = k == 0 ? "converter" : k == 1 ? "windmill" : k == 2 ? "extractor" : "extractor2";
                    Sim s = new Sim(f.name(), kind, c, "gametest_raid_i" + i + k + "_a", "gametest_raid_i" + i + k + "_b");
                    raiders(level, s, f.raider(), 6);
                    siege(level, s, k == 0 ? f.conv() : k == 1 ? f.wind() : f.ext(), k == 3);
                    sims.add(s);
                }
            }
            return sims;
        }, sims -> {
            List<String> problems = new ArrayList<>();
            for (Fac f : facs) {
                Sim conv = find(sims, f.name(), "converter"), wind = find(sims, f.name(), "windmill"),
                    ext = find(sims, f.name(), "extractor"), ext2 = find(sims, f.name(), "extractor2");
                ReignOfNether.LOGGER.info("[Raid] infantry {} {} x6: converter {}s windmill {}s extractor {}s extractor2 {}s (HP {}/{}/{}/{})",
                    f.name(), f.raiderName(), fmt(conv.pure()), fmt(wind.pure()), fmt(ext.pure()), fmt(ext2.pure()),
                    conv.target.getMaxHealth(), wind.target.getMaxHealth(), ext.target.getMaxHealth(), ext2.target.getMaxHealth());
                window(problems, f.name() + " converter", conv.pure(), CONV_MIN, CONV_MAX);
                window(problems, f.name() + " windmill", wind.pure(), WIND_INF_MIN, WIND_INF_MAX);
                window(problems, f.name() + " T1 extractor", ext.pure(), EXT_INF_MIN, EXT_INF_MAX);
                if (ext.pure() > 0 && ext2.pure() > 0 && (ext2.pure() < ext.pure() * 1.5f || ext2.pure() > ext.pure() * 2.7f))
                    problems.add(f.name() + " T2 extractor " + fmt(ext2.pure()) + " s should be about twice the T1's " + fmt(ext.pure()) + " s");
                if (ext2.target.getUpgradeLevel() != 1)
                    problems.add(f.name() + " T2 extractor placement is not read as upgrade level 1");
            }
            if (!problems.isEmpty())
                throw new net.minecraft.gametest.framework.GameTestAssertException(String.join("; ", problems));
            helper.succeed();
        });
    }

    static void window(List<String> problems, String what, float s, float min, float max) {
        if (s < 0)
            problems.add(what + ": never finished");
        else if (s < min || s > max)
            problems.add(what + ": " + fmt(s) + " s outside " + min + "-" + max + " s");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 3. Vehicles against windmill and extractors, and infantry slower than vehicles
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Four of each T1 vehicle on a windmill, a T1 extractor and a T2 extractor; four of the same factions' infantry raider on
     * the windmill and T1 extractor as the baseline. Melee vehicles must beat their windows and be clearly faster
     * than the infantry (they are the building-breakers); the artillery is timed and held to loose windows.
     */
    @GameTest(template = ARENA, timeoutTicks = 2550)
    public static void raid_t1_vehicles_break_windmills_and_extractors_faster_than_infantry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Fac> facs = facs();
        List<Veh> vehs = vehicles();
        int[] baselineFacs = { 2, 3, 4 };   // the factions whose vehicles are melee beasts: Horde, Verdant, Tidewrought
        Pads pads = new Pads(level, 2, vehs.size() * 3 + baselineFacs.length * 2);
        drive(helper, pads, 1150, () -> {
            List<Sim> sims = new ArrayList<>();
            int n = 0;
            for (int v = 0; v < vehs.size(); v++) {
                Veh veh = vehs.get(v);
                Fac f = facs.get(veh.fac());
                for (int k = 0; k < 3; k++) {
                    Cell c = pads.cells.get(n++);
                    String kind = k == 0 ? "windmill" : k == 1 ? "extractor" : "extractor2";
                    Sim s = new Sim(veh.name(), kind, c, "gametest_raid_v" + v + k + "_a", "gametest_raid_v" + v + k + "_b");
                    raiders(level, s, veh.type(), 4);
                    siege(level, s, k == 0 ? f.wind() : f.ext(), k == 2);
                    sims.add(s);
                }
            }
            for (int b = 0; b < baselineFacs.length; b++) {
                Fac f = facs.get(baselineFacs[b]);
                for (int k = 0; k < 2; k++) {
                    Cell c = pads.cells.get(n++);
                    String kind = k == 0 ? "windmill" : "extractor";
                    Sim s = new Sim(f.name() + "-" + f.raiderName(), kind, c, "gametest_raid_b" + b + k + "_a", "gametest_raid_b" + b + k + "_b");
                    raiders(level, s, f.raider(), 4);
                    siege(level, s, k == 0 ? f.wind() : f.ext(), false);
                    sims.add(s);
                }
            }
            return sims;
        }, sims -> {
            List<String> problems = new ArrayList<>();
            for (Veh veh : vehs) {
                Fac f = facs.get(veh.fac());
                Sim wind = find(sims, veh.name(), "windmill"), ext = find(sims, veh.name(), "extractor"), ext2 = find(sims, veh.name(), "extractor2");
                Sim iw = find(sims, f.name() + "-" + f.raiderName(), "windmill"), ie = find(sims, f.name() + "-" + f.raiderName(), "extractor");
                ReignOfNether.LOGGER.info("[Raid] vehicles {} {} x4: windmill {}s (total {}s, {} near) extractor {}s ({}s, {}) extractor2 {}s ({}s, {}); {} x4 infantry: windmill {}s extractor {}s",
                    f.name(), veh.name(), fmt(wind.pure()), fmt(wind.total()), wind.nearAfter3s, fmt(ext.pure()), fmt(ext.total()), ext.nearAfter3s,
                    fmt(ext2.pure()), fmt(ext2.total()), ext2.nearAfter3s, f.raiderName(),
                    iw == null ? "-" : fmt(iw.pure()), ie == null ? "-" : fmt(ie.pure()));
                // the Treant is slow and big (0.2, an iron golem) and Verdant's extractor is the slowest to raze for every unit: its extractor windows are looser
                float extScale = veh.name().equals("SentinelTreant") ? 1.8f : 1f;
                // melee vehicles are held to first-damage -> death; artillery lands whole volleys, so it is held to order -> death
                window(problems, veh.name() + " windmill", veh.melee() ? wind.pure() : wind.total(), 0f, veh.melee() ? WIND_VEH_MAX : WIND_ART_MAX);
                window(problems, veh.name() + " T1 extractor", veh.melee() ? ext.pure() : ext.total(), 0f, (veh.melee() ? EXT_VEH_MAX : EXT_ART_MAX) * extScale);
                window(problems, veh.name() + " T2 extractor", veh.melee() ? ext2.pure() : ext2.total(), 0f, (veh.melee() ? EXT2_VEH_MAX : EXT2_ART_MAX) * extScale);
                if (veh.melee() && iw != null && ie != null) {
                    if (wind.pure() > 0 && iw.pure() > 0 && wind.pure() > iw.pure() * 0.9f)
                        problems.add(veh.name() + " is not clearly faster than 4 " + f.raiderName() + " on a windmill: " + fmt(wind.pure()) + " vs " + fmt(iw.pure()) + " s");
                    if (!veh.name().equals("SentinelTreant") && ext.pure() > 0 && ie.pure() > 0 && ext.pure() > ie.pure() * 0.9f)
                        problems.add(veh.name() + " is not clearly faster than 4 " + f.raiderName() + " on a T1 extractor: " + fmt(ext.pure()) + " vs " + fmt(ie.pure()) + " s");
                }
            }
            if (!problems.isEmpty())
                throw new net.minecraft.gametest.framework.GameTestAssertException(String.join("; ", problems));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 4. The table from the numbers: defences keep a minimum TTK, roles split, T2 extractor doubles
    // ---------------------------------------------------------------------------------------------------------------

    /** Health per second one unit takes off a placement with its normal melee attack (the goal's own arithmetic, x2 for health). */
    static float hps(Mob m, BuildingPlacement p) {
        AttackerUnit au = (AttackerUnit) m;
        float hitsPerSecond = 20f / Math.max(1, (int) au.getAttackCooldown());
        return au.getUnitAttackDamage() * p.getMeleeDamageMult() * au.getBuildingDamageMultiplier() * hitsPerSecond * 2f;
    }

    static BuildingPlacement place(ServerLevel level, Building b, boolean t2) {
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(b, level, new BlockPos(0, 100, 0), Rotation.NONE, "gametest_raid_table", false);
        if (bp != null && t2 && b instanceof MetalExtractor mex)
            bp.changeStructure(mex.getUpgradedStructureName(1));
        return bp;
    }

    /**
     * The infantry and vehicle building-damage rates and the buildings' health, straight from the registered units and
     * placements: the converter is the fragile building, the T1 extractor the next softest, the T2 extractor twice it,
     * the towers and the capitol keep a minimum time to kill for a whole party, the melee vehicles do about
     * {@link RaidTuning#VEHICLE_BUILDING_HPS} to a building (at least twice an infantry raider) and beat the infantry
     * against the towers and the capitol too. Prints the rows the PR quotes ("[Raid] table ...").
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void raid_ttk_table_keeps_roles_and_defences_in_line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Fac> facs = facs();
        List<String> problems = new ArrayList<>();
        BuildingPlacement tower = place(level, Defences.sets().get(0).raidTower(), false), capitol = place(level, Buildings.TOWN_CENTRE, false);
        if (tower == null || capitol == null) {
            helper.fail("could not make the tower or the capitol");
            return;
        }
        BuildingPlacement[] refs = { place(level, facs.get(0).conv(), false), place(level, facs.get(0).wind(), false),
            place(level, facs.get(0).ext(), false), place(level, facs.get(0).ext(), true) };
        for (BuildingPlacement r : refs)
            if (r == null) {
                helper.fail("could not make a reference placement");
                return;
            }
        ReignOfNether.LOGGER.info("[Raid] table health: converter {} windmill {} extractor {} extractor2 {} tower {} capitol {}", refs[0].getMaxHealth(),
            refs[1].getMaxHealth(), refs[2].getMaxHealth(), refs[3].getMaxHealth(), tower.getMaxHealth(), capitol.getMaxHealth());
        if (refs[0].getMaxHealth() > refs[1].getMaxHealth() || refs[2].getMaxHealth() < refs[0].getMaxHealth())
            problems.add("the converter must be the most fragile eco building");
        if (refs[3].getUpgradeLevel() != 1)
            problems.add("the T2 extractor structure is not read as upgrade level 1");
        double ratio = refs[3].getMaxHealth() / (double) Math.max(1, refs[2].getMaxHealth());
        if (ratio < 1.9 || ratio > 2.1)
            problems.add("the T2 extractor should have about twice the T1's health, got " + refs[3].getMaxHealth() + " vs " + refs[2].getMaxHealth());
        float minInfantry = Float.MAX_VALUE, maxInfantry = 0;
        for (Fac f : facs) {
            Mob raider = f.raider().get().create(level);
            if (raider == null) {
                problems.add("could not make a " + f.raiderName());
                continue;
            }
            float inf = hps(raider, refs[1]);
            minInfantry = Math.min(minInfantry, inf);
            maxInfantry = Math.max(maxInfantry, inf);
            ReignOfNether.LOGGER.info("[Raid] table infantry {} {}: {}/s per unit; x6 converter {}s windmill {}s extractor {}s extractor2 {}s tower {}s capitol {}s; x4 converter {}s windmill {}s extractor {}s",
                f.name(), f.raiderName(), String.format(Locale.ROOT, "%.2f", inf),
                fmt(refs[0].getMaxHealth() / (6 * inf) / 1f), fmt(refs[1].getMaxHealth() / (6 * inf)), fmt(refs[2].getMaxHealth() / (6 * inf)),
                fmt(refs[3].getMaxHealth() / (6 * inf)), fmt(tower.getMaxHealth() / (6 * inf)), fmt(capitol.getMaxHealth() / (6 * inf)),
                fmt(refs[0].getMaxHealth() / (4 * inf)), fmt(refs[1].getMaxHealth() / (4 * inf)), fmt(refs[2].getMaxHealth() / (4 * inf)));
            if (tower.getMaxHealth() / (6 * inf) < 20f)
                problems.add(f.name() + ": 6 " + f.raiderName() + " raze a tower in " + fmt(tower.getMaxHealth() / (6 * inf)) + " s (minimum 20)");
            if (capitol.getMaxHealth() / (6 * inf) < 30f)
                problems.add(f.name() + ": 6 " + f.raiderName() + " raze the capitol in " + fmt(capitol.getMaxHealth() / (6 * inf)) + " s (minimum 30)");
            raider.discard();
        }
        for (Veh veh : vehicles()) {
            if (!veh.melee())
                continue;
            Mob m = veh.type().get().create(level);
            if (m == null) {
                problems.add("could not make a " + veh.name());
                continue;
            }
            float v = hps(m, refs[1]);
            ReignOfNether.LOGGER.info("[Raid] table vehicle {}: {}/s per unit (target {}); x4 windmill {}s extractor {}s extractor2 {}s tower {}s capitol {}s; x3 windmill {}s extractor {}s",
                veh.name(), String.format(Locale.ROOT, "%.2f", v), RaidTuning.VEHICLE_BUILDING_HPS,
                fmt(refs[1].getMaxHealth() / (4 * v)), fmt(refs[2].getMaxHealth() / (4 * v)), fmt(refs[3].getMaxHealth() / (4 * v)),
                fmt(tower.getMaxHealth() / (4 * v)), fmt(capitol.getMaxHealth() / (4 * v)),
                fmt(refs[1].getMaxHealth() / (3 * v)), fmt(refs[2].getMaxHealth() / (3 * v)));
            if (Math.abs(v - RaidTuning.VEHICLE_BUILDING_HPS) > RaidTuning.VEHICLE_BUILDING_HPS * 0.12f)
                problems.add(veh.name() + " does " + v + " health/s to a building, not about " + RaidTuning.VEHICLE_BUILDING_HPS);
            if (v < maxInfantry * 1.9f)
                problems.add(veh.name() + " (" + v + "/s) is not nearly twice the best infantry raider (" + maxInfantry + "/s) against buildings");
            // faster than the infantry on the towers and the capitol too, but those stay tough
            if (tower.getMaxHealth() / (4 * v) < 10f)
                problems.add("4 " + veh.name() + " raze a tower in " + fmt(tower.getMaxHealth() / (4 * v)) + " s (minimum 10)");
            if (capitol.getMaxHealth() / (4 * v) < 18f)
                problems.add("4 " + veh.name() + " raze the capitol in " + fmt(capitol.getMaxHealth() / (4 * v)) + " s (minimum 18)");
            m.discard();
        }
        ReignOfNether.LOGGER.info("[Raid] table gaps: Sunforged has no fast T1 raider and no T1 vehicle; Gravebound has no T1 vehicle");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 5. Converter chain blast
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * A converter that falls to damage blasts the converters within {@link RaidTuning#CONVERTER_BLAST_RADIUS} and nothing
     * else: A falls, B (next to A) goes with it, C (next to B only) goes with B, D (a gap away) lives, and the windmill
     * and the tower beside the row are untouched. Real placements, real server-tick destruction.
     */
    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void raid_converter_blast_chains_through_neighbouring_converters_only(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pads pads = new Pads(level, 4, 1);
        Cell c = pads.cells.get(0);
        String owner = "gametest_raid_chain";
        int y = PAD_Y + 1;
        BuildingPlacement a = build(level, Buildings.ENERGY_CONVERTER_VILLAGERS, new BlockPos(c.x0() + 4, y, c.z0() + 4), owner, false),
            b = build(level, Buildings.ENERGY_CONVERTER_VILLAGERS, new BlockPos(c.x0() + 8, y, c.z0() + 4), owner, false),
            cc = build(level, Buildings.ENERGY_CONVERTER_VILLAGERS, new BlockPos(c.x0() + 12, y, c.z0() + 4), owner, false),
            d = build(level, Buildings.ENERGY_CONVERTER_VILLAGERS, new BlockPos(c.x0() + 22, y, c.z0() + 4), owner, false),
            wind = build(level, Buildings.WIND_GENERATOR_VILLAGERS, new BlockPos(c.x0() + 4, y, c.z0() + 9), owner, false),
            tower = build(level, Defences.sets().get(0).raidTower(), new BlockPos(c.x0() + 9, y, c.z0() + 9), owner, false);
        List<BuildingPlacement> all = new ArrayList<>();
        for (BuildingPlacement p : new BuildingPlacement[] { a, b, cc, d, wind, tower })
            if (p != null)
                all.add(p);
        Runnable cleanup = () -> {
            for (BuildingPlacement p : all)
                BuildingServerEvents.getBuildings().remove(p);
            pads.release();
        };
        if (all.size() != 6) {
            cleanup.run();
            helper.fail("could not place the converters, the windmill and the tower");
            return;
        }
        double ab = flat(a, b), bc = flat(b, cc), ac = flat(a, cc), cd = flat(cc, d), bd = flat(b, d);
        double r = RaidTuning.CONVERTER_BLAST_RADIUS;
        if (ab > r || bc > r || ac <= r || cd <= r || bd <= r) {
            cleanup.run();
            helper.fail("the test layout is off: A-B " + ab + " B-C " + bc + " A-C " + ac + " C-D " + cd + " B-D " + bd + " against radius " + r);
            return;
        }
        int windHp = wind.getHealth(), towerHp = tower.getHealth(), dHp = d.getHealth();
        helper.runAfterDelay(5, () -> a.destroyRandomBlocks(10000));   // the raiders' last blow, whoever struck it
        helper.runAfterDelay(100, () -> {
            try {
                List<String> problems = new ArrayList<>();
                if (BuildingServerEvents.getBuildings().contains(a))
                    problems.add("A did not fall");
                if (BuildingServerEvents.getBuildings().contains(b))
                    problems.add("B (next to A) did not go with it");
                if (BuildingServerEvents.getBuildings().contains(cc))
                    problems.add("C (next to B only) did not chain");
                if (!BuildingServerEvents.getBuildings().contains(d) || d.getHealth() != dHp)
                    problems.add("D (a gap away) was hurt: " + dHp + " -> " + d.getHealth());
                if (!BuildingServerEvents.getBuildings().contains(wind) || wind.getHealth() != windHp)
                    problems.add("the windmill beside the row was hurt: " + windHp + " -> " + wind.getHealth());
                if (!BuildingServerEvents.getBuildings().contains(tower) || tower.getHealth() != towerHp)
                    problems.add("the tower beside the row was hurt: " + towerHp + " -> " + tower.getHealth());
                if (problems.isEmpty())
                    ReignOfNether.LOGGER.info("[Raid] other chain blast: A, B, C fell; D (gap {}), windmill and tower untouched", (int) Math.min(cd, bd));
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    static double flat(BuildingPlacement p, BuildingPlacement q) {
        double dx = p.centrePos.getX() - q.centrePos.getX(), dz = p.centrePos.getZ() - q.centrePos.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 6. A destroyed extractor's patch is rebuildable at once
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * An extractor on a real metal patch (a raw iron cross flush in grass over stone) is razed by damage - a T1 on one pad, a T2
     * on another - and afterwards the ground under it is exactly what it was (no crater from the collapse blasts, no rubble
     * above), the patch cross stands, no placement is left behind, and the validator accepts a new extractor on the same
     * origin straight away. Fails with the first block that differs.
     */
    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void raid_destroyed_extractor_leaves_its_patch_rebuildable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pads pads = new Pads(level, 5, 2);
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
        String owner = "gametest_raid_patch";
        Building ext = Buildings.METAL_EXTRACTOR_VILLAGERS;
        BlockPos[] origins = new BlockPos[2];
        List<List<BlockState>> befores = new ArrayList<>();
        BuildingPlacement[] cur = new BuildingPlacement[2];
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            Cell c = pads.cells.get(round);
            int cx = c.x0() + 16, cz = c.z0() + 8;
            for (int dx = -5; dx <= 5; dx++)
                for (int dz = -5; dz <= 5; dz++) {
                    level.setBlock(new BlockPos(cx + dx, PAD_Y - 1, cz + dz), stone, 2);
                    level.setBlock(new BlockPos(cx + dx, PAD_Y, cz + dz), grass, 2);
                }
            for (int[] o : new int[][] { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
                level.setBlock(new BlockPos(cx + o[0], PAD_Y, cz + o[1]), MetalPatches.PATCH_BLOCK.defaultBlockState(), 2);
            origins[round] = new BlockPos(cx - 2, PAD_Y + 1, cz - 2);   // the 5x5 footprint centred on the patch
            List<BlockState> before = new ArrayList<>();
            scan(level, origins[round], before);
            befores.add(before);
            String firstError = BuildingValidators.getPlacementValidityError(level, ext, origins[round], owner, Rotation.NONE, false, false, true);
            if (firstError != null) {
                pads.release();
                helper.fail("the test patch is not placeable to begin with: " + firstError);
                return;
            }
        }
        helper.runAfterDelay(5, () -> {
            for (int round = 0; round < 2; round++) {
                cur[round] = build(level, ext, origins[round], owner, round == 1);
                if (cur[round] == null) {
                    problems.add("could not place the " + (round == 1 ? "T2" : "T1") + " extractor");
                    continue;
                }
            }
        });
        helper.onEachTick(() -> {
            // the raiders' blows, six damage a tick on each, until the building is down (a 10000 blow in one call razed only the T1)
            if (helper.getTick() < 6 || helper.getTick() > 110)
                return;
            for (BuildingPlacement p : cur)
                if (p != null && BuildingServerEvents.getBuildings().contains(p) && !p.shouldBeDestroyed())
                    p.destroyRandomBlocks(6);
        });
        helper.runAfterDelay(120, () -> {
            try {
                for (int round = 0; round < 2; round++) {
                    String what = round == 1 ? "T2 extractor" : "T1 extractor";
                    BuildingPlacement p = cur[round];
                    if (p == null)
                        continue;
                    if (BuildingServerEvents.getBuildings().contains(p)) {
                        problems.add("the razed " + what + " is still a placement (blocks placed " + p.getBlocksPlaced() + ", health " + p.getHealth()
                            + "/" + p.getMaxHealth() + ", should be destroyed " + p.shouldBeDestroyed() + ", built " + p.isBuilt + ")");
                        continue;
                    }
                    List<BlockState> after = new ArrayList<>();
                    scan(level, origins[round], after);
                    List<BlockState> before = befores.get(round);
                    boolean same = true;
                    for (int i = 0; i < before.size() && same; i++)
                        if (!before.get(i).equals(after.get(i))) {
                            same = false;
                            problems.add("after the " + what + " fell, the block at scan index " + i + " is " + after.get(i) + ", it was "
                                + before.get(i) + " (" + describe(origins[round], i) + ")");
                        }
                    String err = BuildingValidators.getPlacementValidityError(level, ext, origins[round], owner, Rotation.NONE, false, false, true);
                    if (err != null)
                        problems.add("after the " + what + " fell the patch cannot be built on: " + err);
                    else if (same)
                        ReignOfNether.LOGGER.info("[Raid] other patch rebuildable after a {} fell: ground and cross intact, validator accepts", what);
                }
            } finally {
                for (BuildingPlacement p : cur)
                    if (p != null)
                        BuildingServerEvents.getBuildings().remove(p);
                pads.release();
            }
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    /** The footprint and its surroundings: x/z -1..5 round the 5x5, from two below the patch to ten above the origin. */
    static void scan(ServerLevel level, BlockPos origin, List<BlockState> out) {
        for (int x = -1; x <= 5; x++)
            for (int z = -1; z <= 5; z++)
                for (int y = -2; y <= 10; y++)
                    out.add(level.getBlockState(origin.offset(x, y - 1, z)));
    }

    static String describe(BlockPos origin, int index) {
        int perColumn = 13, perRow = 7 * perColumn;
        int x = index / perRow - 1, z = (index % perRow) / perColumn - 1, y = index % perColumn - 2 - 1;
        return "offset " + x + "," + y + "," + z + " from the extractor origin";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 7. Workers flee to a tower or the capitol; hold position stays put
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * A worker (made unkillable so the chase can be watched) with a raider on it, 20 blocks from its own tower, runs to the
     * tower; the same with the capitol; and a worker on hold position stays where it is. Arrival is within the refuge's box
     * plus {@link RaidTuning#FLEE_ARRIVED}; the flight is counted by WorkerFlee.
     */
    @GameTest(template = ARENA, timeoutTicks = 2100)
    public static void raid_hurt_workers_run_to_a_tower_or_the_capitol_and_hold_stays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pads pads = new Pads(level, 3, 3);
        String[] kinds = { "tower", "capitol", "hold" };
        Building[] refuges = { Defences.sets().get(0).raidTower(), Buildings.TOWN_CENTRE, Defences.sets().get(0).raidTower() };
        List<Sim> sims = new ArrayList<>();
        List<BuildingPlacement> refugeBp = new ArrayList<>();
        List<BlockPos> starts = new ArrayList<>();
        List<Mob> workers = new ArrayList<>();
        int[] spawnedAt = { -1 };
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "raid pad", () -> {
            for (int i = 0; i < 3; i++) {
                Cell c = pads.cells.get(i);
                String own = "gametest_raid_f" + i + "_w", foe = "gametest_raid_f" + i + "_x";
                Sim s = new Sim(kinds[i], "flee", c, foe, own);
                BuildingPlacement bp = build(level, refuges[i], new BlockPos(c.x0() + 3, PAD_Y + 1, c.z0() + 4), own, false);
                refugeBp.add(bp);
                // the capitol is big (its box plus the arrival margin reaches past x0+24), so the worker starts at the pad's far edge
                double wx = i == 1 ? 29.5 : 24, rx = i == 1 ? 27.5 : 27;
                Mob w = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), own, c.x0() + wx, c.z0() + 8);
                Mob r = spawn(level, EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), foe, c.x0() + rx, c.z0() + 8);
                if (bp == null || w == null || r == null) {
                    s.errors.add("could not make the refuge, the worker and the raider");
                    sims.add(s);
                    continue;
                }
                if (i == 2)
                    ((Unit) w).setHoldPosition(true);
                s.attackers.add(r);
                s.victims.add(w);
                workers.add(w);
                starts.add(w.blockPosition());
                sims.add(s);
            }
            spawnedAt[0] = (int) helper.getTick();
        });
        boolean[] ordered = { false };
        helper.onEachTick(() -> {
            if (spawnedAt[0] < 0)
                return;
            int t = (int) helper.getTick() - spawnedAt[0];
            for (Sim s : sims) {
                for (Mob m : s.attackers)
                    unkillable(m);
                for (Mob m : s.victims)
                    unkillable(m);
            }
            if (t == 3 && !ordered[0]) {
                ordered[0] = true;
                for (Sim s : sims)
                    if (!s.attackers.isEmpty() && !s.victims.isEmpty() && s.attackers.get(0) instanceof AttackerUnit au)
                        au.setUnitAttackTargetForced(s.victims.get(0));
            }
            if (t < 3)
                return;
            if (t == 700 || t >= 3 && allArrived(sims, refugeBp, workers, t)) {
                // done: judge and clean up once
                spawnedAt[0] = -1;
                pads.release();
                List<String> problems = new ArrayList<>();
                for (int i = 0; i < sims.size(); i++) {
                    Sim s = sims.get(i);
                    if (!s.errors.isEmpty()) {
                        problems.addAll(s.errors);
                        continue;
                    }
                    Mob w = s.victims.get(0);
                    BuildingPlacement bp = refugeBp.get(i);
                    boolean arrived = bp.getAABB().inflate(RaidTuning.FLEE_ARRIVED).contains(w.position());
                    int flights = WorkerFlee.flights(w);
                    ReignOfNether.LOGGER.info("[Raid] other flee {}: flights {} arrived {} after {}s (distance to refuge {})", kinds[i], flights, arrived,
                        fmt(secs(t)), (int) Math.sqrt(bp.centrePos.distSqr(w.blockPosition())));
                    if (i < 2 && (flights < 1 || !arrived))
                        problems.add("the hurt worker did not reach its " + kinds[i] + ": flights " + flights + ", arrived " + arrived);
                    // (hold position is judged on the flight alone: the raider's blows still knock the worker about)
                    if (i == 2 && flights != 0)
                        problems.add("a worker on hold position fled: flights " + flights);
                    if (i == 1 && arrived && bp.getAABB().inflate(RaidTuning.FLEE_ARRIVED).contains(starts.get(1).getCenter()))
                        problems.add("the capitol test started the worker inside the arrival box");
                }
                for (Sim s : sims)
                    s.cleanup();
                for (BuildingPlacement bp : refugeBp)
                    BuildingServerEvents.getBuildings().remove(bp);
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            }
        });
    }

    /** True once both fleeing workers are at their refuge and the hold worker has had 300 ticks to (not) move. */
    static boolean allArrived(List<Sim> sims, List<BuildingPlacement> refuges, List<Mob> workers, int t) {
        if (sims.size() < 3 || refuges.size() < 3 || workers.size() < 3 || t < 300)
            return false;
        for (int i = 0; i < 2; i++) {
            BuildingPlacement bp = refuges.get(i);
            if (bp == null || !bp.getAABB().inflate(RaidTuning.FLEE_ARRIVED).contains(workers.get(i).position()))
                return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 8. Bots: worker raids, target order, vehicle raids, defence of hurt workers, converters spread
    // ---------------------------------------------------------------------------------------------------------------

    static List<LivingEntity> infantry(ServerLevel level, String owner, Cell c, double dx, double dz, int n) {
        List<LivingEntity> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Mob m = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), owner, c.x0() + dx + (i % 3) * 1.4, c.z0() + dz + (i / 3) * 1.4);
            if (m != null)
                out.add(m);
        }
        return out;
    }

    static List<LivingEntity> hoglins(ServerLevel level, String owner, Cell c, double dx, double dz, int n) {
        List<LivingEntity> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Mob m = spawn(level, EntityRegistrar.HOGLIN_UNIT.get(), owner, c.x0() + dx + i * 2.0, c.z0() + dz);
            if (m != null)
                out.add(m);
        }
        return out;
    }

    static BlockPos at(Cell c, int dx, int dz) {
        return new BlockPos(c.x0() + dx, PAD_Y + 1, c.z0() + dz);
    }

    static boolean near(BlockPos a, BlockPos b, double d) {
        return a != null && b != null && Math.sqrt(a.distSqr(b)) <= d;
    }

    /**
     * Bots raid workers: a hard bot (minute 5) and a medium bot (minute 7 - both before minute 8) with six idle fighters
     * and an enemy worker in sight launch a worker raid at once, the squad attack-moves onto the worker and hurts it;
     * an easy bot launches none. Real BotArmy steps, real units.
     */
    @GameTest(template = ARENA, timeoutTicks = 2300)
    public static void raid_hard_and_medium_bots_launch_a_worker_raid_before_minute_eight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pads pads = new Pads(level, 6, 6);   // per bot: the pad of its army and worker, and the pad 64 blocks away that is its home (beyond the home scan)
        BotPlayer[] brains = new BotPlayer[3];
        List<List<LivingEntity>> armies = new ArrayList<>();
        List<Mob> workers = new ArrayList<>();
        BotPlayer.Difficulty[] diffs = { BotPlayer.Difficulty.HARD, BotPlayer.Difficulty.MEDIUM, BotPlayer.Difficulty.EASY };
        long[] minutes = { 5, 7, 5 };
        int[] spawnedAt = { -1 };
        List<String> errors = new ArrayList<>();
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "raid pad", () -> {
            for (int i = 0; i < 3; i++) {
                Cell c = pads.cells.get(i * 2), homePad = pads.cells.get(i * 2 + 1);
                String own = "gametest_raid_b" + i + "_bot", foe = "gametest_raid_b" + i + "_foe";
                brains[i] = new BotPlayer(own, Factions.VILLAGERS, diffs[i], at(homePad, 16, 8));
                brains[i].enemyFilter = foe::equals;
                armies.add(infantry(level, own, c, 4, 5, 6));
                Mob w = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), foe, c.x0() + 24, c.z0() + 8);
                if (w == null || armies.get(i).size() != 6)
                    errors.add("could not make the army and the worker for " + diffs[i]);
                workers.add(w);
            }
            spawnedAt[0] = (int) helper.getTick();
        });
        boolean[] stepped = { false };
        helper.onEachTick(() -> {
            if (spawnedAt[0] < 0)
                return;
            int t = (int) helper.getTick() - spawnedAt[0];
            if (t < 10)
                return;
            if (!stepped[0]) {
                stepped[0] = true;
                if (!errors.isEmpty()) {
                    finishBots(helper, pads, armies, workers, String.join("; ", errors));
                    spawnedAt[0] = -1;
                    return;
                }
                for (int i = 0; i < 3; i++)
                    brains[i].army.run(level, level.getGameTime(), armies.get(i), armies.get(i), List.of(), minutes[i]);
                List<String> problems = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    BotArmy a = brains[i].army;
                    int ordered = 0;
                    for (LivingEntity le : armies.get(i))
                        if (le instanceof AttackerUnit au && au.getAttackMoveTarget() != null)
                            ordered++;
                    ReignOfNether.LOGGER.info("[Raid] other bot {} minute {}: raids {} (workers {}), kind '{}', target {}, {} fighters ordered", diffs[i], minutes[i],
                        a.raidsLaunched, a.workerRaidsLaunched, a.raidKind, a.raidTarget, ordered);
                    if (diffs[i] == BotPlayer.Difficulty.EASY) {
                        if (a.raidsLaunched != 0)
                            problems.add("an easy bot raided");
                    } else {
                        if (a.workerRaidsLaunched != 1 || !a.raidKind.equals("workers"))
                            problems.add(diffs[i] + " bot did not launch a worker raid at minute " + minutes[i] + ": raids " + a.raidsLaunched
                                + ", kind '" + a.raidKind + "'");
                        else if (!near(a.raidTarget, workers.get(i).blockPosition(), 6))
                            problems.add(diffs[i] + " raid target " + a.raidTarget + " is not the worker at " + workers.get(i).blockPosition());
                        int want = diffs[i] == BotPlayer.Difficulty.HARD ? 4 : 3;   // BotTuning.raidSize
                        if (ordered < want)
                            problems.add(diffs[i] + " raid sent only " + ordered + " fighters, wanted " + want);
                    }
                }
                if (!problems.isEmpty()) {
                    finishBots(helper, pads, armies, workers, String.join("; ", problems));
                    spawnedAt[0] = -1;
                }
                return;
            }
            // the squad then actually catches the worker (hard and medium); the easy bot's worker is left alone
            boolean hardHurt = workers.get(0) == null || !workers.get(0).isAlive() || workers.get(0).getHealth() < workers.get(0).getMaxHealth();
            boolean medHurt = workers.get(1) == null || !workers.get(1).isAlive() || workers.get(1).getHealth() < workers.get(1).getMaxHealth();
            if ((hardHurt && medHurt) || t > 900) {
                spawnedAt[0] = -1;
                String err = null;
                if (!hardHurt || !medHurt)
                    err = "the raid squad never hurt the worker (hard " + hardHurt + ", medium " + medHurt + ")";
                finishBots(helper, pads, armies, workers, err);
            }
        });
    }

    static void finishBots(GameTestHelper helper, Pads pads, List<List<LivingEntity>> armies, List<Mob> extras, String error) {
        pads.release();
        for (List<LivingEntity> army : armies)
            for (LivingEntity le : army)
                le.discard();
        for (Mob m : extras)
            if (m != null)
                m.discard();
        if (error != null)
            helper.fail(error);
        else
            helper.succeed();
    }

    /**
     * What a bot raids, in order: workers it can see that no known defence covers, else the nearest uncovered converter, else
     * an uncovered extractor, else nothing; infantry only (vehicles have their own raid). Four scenarios, each with its
     * army on one pad and the sites it knows split over that pad (the covered ones, round a tower) and the next pad
     * (the bare ones, 64 blocks from any tower - the sites are seeded, nothing is built): a bare converter beside a covered
     * one takes the bare one, a bare extractor beside a covered one likewise, only covered sites launch nothing, and a
     * converter is chosen before an extractor.
     */
    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void raid_bot_hunts_converters_then_bare_extractors_and_skips_covered_ones(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pads pads = new Pads(level, 7, 8);
        List<List<LivingEntity>> armies = new ArrayList<>();
        int[] spawnedAt = { -1 };
        BotPlayer[] brains = new BotPlayer[4];
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "raid pad", () -> {
            long now = level.getGameTime();
            for (int i = 0; i < 4; i++) {
                Cell c = pads.cells.get(i * 2);
                String own = "gametest_raid_t" + i + "_bot";
                brains[i] = new BotPlayer(own, Factions.VILLAGERS, BotPlayer.Difficulty.HARD, at(c, 4, 6));
                brains[i].enemyFilter = o -> false;
                armies.add(infantry(level, own, c, 4, 5, 6));
            }
            Cell[] near = new Cell[4], far = new Cell[4];
            for (int i = 0; i < 4; i++) {
                near[i] = pads.cells.get(i * 2);
                far[i] = pads.cells.get(i * 2 + 1);
            }
            // 0: a converter beside a tower and a bare one -> the bare one
            brains[0].army.seedSite(at(near[0], 14, 8), Buildings.WATCHTOWER, now);
            brains[0].army.seedSite(at(near[0], 16, 10), Buildings.ENERGY_CONVERTER_VILLAGERS, now);
            brains[0].army.seedSite(at(far[0], 8, 4), Buildings.ENERGY_CONVERTER_VILLAGERS, now);
            // 1: an extractor beside a tower and a bare one -> the bare one
            brains[1].army.seedSite(at(near[1], 12, 4), Buildings.WATCHTOWER, now);
            brains[1].army.seedSite(at(near[1], 14, 4), Buildings.METAL_EXTRACTOR_VILLAGERS, now);
            brains[1].army.seedSite(at(far[1], 8, 4), Buildings.METAL_EXTRACTOR_VILLAGERS, now);
            // 2: only covered sites -> nothing
            brains[2].army.seedSite(at(near[2], 12, 4), Buildings.WATCHTOWER, now);
            brains[2].army.seedSite(at(near[2], 14, 4), Buildings.METAL_EXTRACTOR_VILLAGERS, now);
            brains[2].army.seedSite(at(near[2], 10, 12), Buildings.ENERGY_CONVERTER_VILLAGERS, now);
            // 3: a bare extractor and a bare converter -> the converter first
            brains[3].army.seedSite(at(near[3], 26, 4), Buildings.METAL_EXTRACTOR_VILLAGERS, now);
            brains[3].army.seedSite(at(near[3], 28, 12), Buildings.ENERGY_CONVERTER_VILLAGERS, now);
            spawnedAt[0] = (int) helper.getTick();
        });
        helper.onEachTick(() -> {
            if (spawnedAt[0] < 0 || (int) helper.getTick() - spawnedAt[0] < 8)
                return;
            spawnedAt[0] = -1;
            for (int i = 0; i < 4; i++)
                brains[i].army.run(level, level.getGameTime(), armies.get(i), armies.get(i), List.of(), 5);
            List<String> problems = new ArrayList<>();
            expectRaid(problems, "covered converter", brains[0].army, "converter", at(pads.cells.get(1), 8, 4));
            expectRaid(problems, "covered extractor", brains[1].army, "extractor", at(pads.cells.get(3), 8, 4));
            if (brains[2].army.raidsLaunched != 0)
                problems.add("a bot raided only covered sites: kind '" + brains[2].army.raidKind + "' at " + brains[2].army.raidTarget);
            expectRaid(problems, "converter before extractor", brains[3].army, "converter", at(pads.cells.get(6), 28, 12));
            ReignOfNether.LOGGER.info("[Raid] other bot targets: covered converter -> {} {}, covered extractor -> {} {}, only covered -> {} raids, converter first -> {} {}",
                brains[0].army.raidKind, brains[0].army.raidTarget, brains[1].army.raidKind, brains[1].army.raidTarget,
                brains[2].army.raidsLaunched, brains[3].army.raidKind, brains[3].army.raidTarget);
            finishBots(helper, pads, armies, List.of(), problems.isEmpty() ? null : String.join("; ", problems));
        });
    }

    static void expectRaid(List<String> problems, String what, BotArmy army, String kind, BlockPos target) {
        if (army.raidsLaunched != 1 || !army.raidKind.equals(kind) || !near(army.raidTarget, target, 3))
            problems.add(what + ": wanted a '" + kind + "' raid at " + target + ", got " + army.raidsLaunched + " raids, kind '" + army.raidKind
                + "' at " + army.raidTarget);
    }

    /**
     * The vehicle raid: a hard bot with three idle Hoglins and six Vindicators sends only the Hoglins at the nearest bare
     * converter (the Vindicators are the infantry raid's), a medium bot at a bare extractor when there is no converter,
     * a bot with two vehicles (under the squad size) and an easy bot send none.
     */
    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void raid_bot_sends_idle_t1_vehicles_at_converters_and_extractors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pads pads = new Pads(level, 8, 4);
        List<List<LivingEntity>> armies = new ArrayList<>();
        List<List<LivingEntity>> vehicles = new ArrayList<>();
        int[] spawnedAt = { -1 };
        BotPlayer[] brains = new BotPlayer[4];
        BotPlayer.Difficulty[] diffs = { BotPlayer.Difficulty.HARD, BotPlayer.Difficulty.MEDIUM, BotPlayer.Difficulty.HARD, BotPlayer.Difficulty.EASY };
        int[] counts = { 3, 3, 2, 3 };
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "raid pad", () -> {
            long now = level.getGameTime();
            for (int i = 0; i < 4; i++) {
                Cell c = pads.cells.get(i);
                String own = "gametest_raid_h" + i + "_bot";
                brains[i] = new BotPlayer(own, Factions.VILLAGERS, diffs[i], at(c, 4, 6));
                brains[i].enemyFilter = o -> false;
                armies.add(infantry(level, own, c, 4, 5, 6));
                vehicles.add(hoglins(level, own, c, 4, 10, counts[i]));
                brains[i].army.seedSite(at(c, 28, 12), Buildings.METAL_EXTRACTOR_VILLAGERS, now);
                if (i != 1)
                    brains[i].army.seedSite(at(c, 28, 2), Buildings.ENERGY_CONVERTER_VILLAGERS, now);
            }
            spawnedAt[0] = (int) helper.getTick();
        });
        helper.onEachTick(() -> {
            if (spawnedAt[0] < 0 || (int) helper.getTick() - spawnedAt[0] < 8)
                return;
            spawnedAt[0] = -1;
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                List<LivingEntity> all = new ArrayList<>(armies.get(i));
                all.addAll(vehicles.get(i));
                brains[i].army.run(level, level.getGameTime(), all, all, List.of(), 5);
            }
            Cell c0 = pads.cells.get(0), c1 = pads.cells.get(1);
            BotArmy hard = brains[0].army, medium = brains[1].army;
            if (hard.vehicleRaidsLaunched != 1 || !hard.vehicleRaidKind.equals("converter") || !near(hard.vehicleRaidTarget, at(c0, 28, 2), 3))
                problems.add("hard vehicle raid: wanted a converter raid at " + at(c0, 28, 2) + ", got " + hard.vehicleRaidsLaunched + " raids, kind '"
                    + hard.vehicleRaidKind + "' at " + hard.vehicleRaidTarget);
            if (medium.vehicleRaidsLaunched != 1 || !medium.vehicleRaidKind.equals("extractor") || !near(medium.vehicleRaidTarget, at(c1, 28, 12), 3))
                problems.add("medium vehicle raid: wanted an extractor raid at " + at(c1, 28, 12) + ", got " + medium.vehicleRaidsLaunched + " raids, kind '"
                    + medium.vehicleRaidKind + "' at " + medium.vehicleRaidTarget);
            if (brains[2].army.vehicleRaidsLaunched != 0)
                problems.add("a bot with only two vehicles sent a vehicle raid");
            if (brains[3].army.vehicleRaidsLaunched != 0)
                problems.add("an easy bot sent a vehicle raid");
            for (LivingEntity h : vehicles.get(0)) {
                if (!hard.inVehicleRaid(h.getId()) || hard.inInfantryRaid(h.getId()))
                    problems.add("a Hoglin is not (only) in the vehicle raid squad");
                else if (!(h instanceof AttackerUnit au) || !near(au.getAttackMoveTarget(), at(c0, 28, 2), 3))
                    problems.add("a Hoglin of the raid squad was not sent at the converter");
            }
            for (LivingEntity v : armies.get(0))
                if (hard.inVehicleRaid(v.getId()))
                    problems.add("a Vindicator was sent on the vehicle raid");
            ReignOfNether.LOGGER.info("[Raid] other bot vehicles: hard -> {} {}, medium -> {} {}, two vehicles -> {} raids, easy -> {} raids",
                hard.vehicleRaidKind, hard.vehicleRaidTarget, medium.vehicleRaidKind, medium.vehicleRaidTarget,
                brains[2].army.vehicleRaidsLaunched, brains[3].army.vehicleRaidsLaunched);
            List<Mob> extras = new ArrayList<>();
            for (List<LivingEntity> vs : vehicles)
                for (LivingEntity v : vs)
                    extras.add((Mob) v);
            finishBots(helper, pads, armies, extras, problems.isEmpty() ? null : String.join("; ", problems));
        });
    }

    /**
     * A worker hurt far from home (the bot's army is on the pad beside it, home is on the pad 64 blocks away, beyond the home
     * scan's reach): the worker runs to its tower (WorkerFlee) and the bot sends its nearest fighters at the raider - the
     * alarm that a hurt worker raises where the home scan sees nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void raid_bot_answers_a_hurt_worker_with_defenders_while_it_flees(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pads pads = new Pads(level, 9, 2);
        Cell home = pads.cells.get(0), c = pads.cells.get(1);
        String own = "gametest_raid_d_bot", foe = "gametest_raid_d_foe";
        List<LivingEntity> army = new ArrayList<>();
        Mob[] cast = new Mob[2];   // worker, raider
        BuildingPlacement[] tower = { null };
        BotPlayer[] brain = { null };
        int[] spawnedAt = { -1 };
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "raid pad", () -> {
            brain[0] = new BotPlayer(own, Factions.VILLAGERS, BotPlayer.Difficulty.HARD, at(home, 16, 8));
            brain[0].enemyFilter = foe::equals;
            tower[0] = build(level, Defences.sets().get(0).raidTower(), new BlockPos(c.x0() + 3, PAD_Y + 1, c.z0() + 4), own, false);
            army.addAll(infantry(level, own, c, 8, 10, 6));
            for (LivingEntity le : army)
                EngagementStance.set(le, EngagementStance.HOLD_FIRE);   // the defenders are only ordered; the fight is not the point
            cast[0] = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), own, c.x0() + 24, c.z0() + 8);
            cast[1] = spawn(level, EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), foe, c.x0() + 27, c.z0() + 8);
            spawnedAt[0] = (int) helper.getTick();
        });
        boolean[] ordered = { false };
        helper.onEachTick(() -> {
            if (spawnedAt[0] < 0)
                return;
            int t = (int) helper.getTick() - spawnedAt[0];
            for (Mob m : cast)
                if (m != null)
                    unkillable(m);
            if (t == 3 && !ordered[0]) {
                ordered[0] = true;
                if (cast[1] instanceof AttackerUnit au && cast[0] != null)
                    au.setUnitAttackTargetForced(cast[0]);
            }
            if (t != 120)
                return;
            spawnedAt[0] = -1;
            pads.release();
            List<LivingEntity> mine = new ArrayList<>(army);
            if (cast[0] != null)
                mine.add(cast[0]);
            List<BuildingPlacement> buildings = new ArrayList<>();
            if (tower[0] != null)
                buildings.add(tower[0]);
            String error = null;
            try {
                if (cast[0] == null || cast[1] == null || tower[0] == null || army.size() != 6)
                    error = "could not make the tower, the army, the worker and the raider";
                else if (!WorkerFlee.recentlyHurt(cast[0]))
                    error = "the raider never hurt the worker";
                else {
                    brain[0].army.run(level, level.getGameTime(), mine, army, buildings, 5);
                    int sent = 0;
                    for (LivingEntity le : army)
                        if (le instanceof AttackerUnit au && au.getAttackMoveTarget() != null && near(au.getAttackMoveTarget(), cast[1].blockPosition(), 12))
                            sent++;
                    int flights = WorkerFlee.flights(cast[0]);
                    ReignOfNether.LOGGER.info("[Raid] other bot defence: intruder {}, {} defenders sent, worker flights {}", brain[0].army.lastIntruder, sent, flights);
                    if (brain[0].army.lastIntruder != cast[1])
                        error = "the bot did not name the raider as the intruder: " + brain[0].army.lastIntruder;
                    else if (sent < 3)
                        error = "the bot sent only " + sent + " defenders at the raider hunting its worker";
                    else if (flights < 1)
                        error = "the hurt worker did not flee";
                }
            } finally {
                for (LivingEntity le : army)
                    le.discard();
                for (Mob m : cast)
                    if (m != null)
                        m.discard();
                if (tower[0] != null)
                    BuildingServerEvents.getBuildings().remove(tower[0]);
            }
            if (error != null)
                helper.fail(error);
            else
                helper.succeed();
        });
    }

    /** Keeps a body alive for the chase: the max-health attribute does not always survive spawn setup, so top it up every tick. */
    static void unkillable(Mob m) {
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getBaseValue() < 5000)
            hp.setBaseValue(5000);
        if (m.getHealth() < m.getMaxHealth())
            m.setHealth(m.getMaxHealth());
    }

    /** A bot spreads its converters out of each other's death blast: slots 8 apart or more, every pair beyond the radius. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void raid_bot_spreads_its_own_converters_beyond_the_blast(GameTestHelper helper) {
        BotPlayer brain = new BotPlayer("gametest_raid_spread", Factions.VILLAGERS, BotPlayer.Difficulty.HARD, new BlockPos(0, 100, 0));
        List<BlockPos> slots = new ArrayList<>();
        for (int i = 0; i < 8; i++)
            slots.add(brain.converterSlot(i));
        double min = Double.MAX_VALUE;
        for (int i = 0; i < slots.size(); i++)
            for (int j = i + 1; j < slots.size(); j++) {
                double dx = slots.get(i).getX() - slots.get(j).getX(), dz = slots.get(i).getZ() - slots.get(j).getZ();
                min = Math.min(min, Math.sqrt(dx * dx + dz * dz));
            }
        ReignOfNether.LOGGER.info("[Raid] other converter slots: closest pair {} apart (blast radius {})", String.format(Locale.ROOT, "%.1f", min),
            RaidTuning.CONVERTER_BLAST_RADIUS);
        if (min <= RaidTuning.CONVERTER_BLAST_RADIUS)
            helper.fail("two bot converter slots are only " + min + " apart, inside the " + RaidTuning.CONVERTER_BLAST_RADIUS + " blast radius");
        else
            helper.succeed();
    }
}
