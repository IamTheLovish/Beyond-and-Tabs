package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.bot.BotRoles;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.building.buildings.defence.WallSegment;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.gametest.RaidGameTests.Cell;
import com.solegendary.reignofnether.gametest.RaidGameTests.Pads;
import com.solegendary.reignofnether.gametest.RaidGameTests.Sim;
import com.solegendary.reignofnether.raid.RaidTuning;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.FieldArtillery;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The T1 siege lobber, one per faction (design/siege_t1.md, lovish Oct 29: BAR's Rocko). Every faction trains it at its
 * T1 lab; it breaks the first-stage tower (the anti-raid tower) from outside that tower's reach, loses in pairs to the
 * enhanced tower and wins in fives, takes a wall down at the defence rate (no wall bonus: walls stay strong), barely
 * scratches a unit that stands still and misses one that keeps walking, and sees less far than it shoots.
 * <p>
 * The units are looked up by registry id, so the table below names each one once and nothing here depends on their
 * classes. The fights run on RaidGameTests' far pads (slots 10-13), one pad per faction and case, timed from the order.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SiegeLobberGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PAD_Y = DuelGameTests.PAD_Y;

    // the shared base (design/siege_t1.md, "The lobber - shared base")
    static final int METAL = 95, ENERGY = 130, POP = 2;
    static final float HEALTH = 80f, RANGE = 15f, SIGHT = 12f, MIN_RANGE = 3f, BUILDING_BONUS = 3.5f;
    static final int SET_UP_TICKS = 20;

    /** One faction's lobber: its id (the production item; the entity is id_unit), lab and defences. */
    record Lob(String faction, String id, Supplier<Faction> fac, Building lab, Defences.Set set, Defences.TowerSet towers) { }

    static List<Lob> lobs() {
        return List.of(
            new Lob("Sunforged", "sunfire_mangonel", () -> Factions.VILLAGERS, Buildings.BARRACKS, Defences.VILLAGERS, Defences.VILLAGERS_TOWERS),
            new Lob("Gravebound", "skull_hurler", () -> Factions.MONSTERS, Buildings.GRAVEYARD, Defences.MONSTERS, Defences.MONSTERS_TOWERS),
            new Lob("Horde", "boulder_chucker", () -> Factions.PIGLINS, Buildings.PORTAL_MILITARY, Defences.PIGLINS, Defences.PIGLINS_TOWERS),
            new Lob("Verdant", "burl_slinger", () -> Factions.VERDANT_COURT, Buildings.GROVE, Defences.VERDANT, Defences.VERDANT_TOWERS),
            new Lob("Tidewrought", "powder_monkey", () -> Factions.TIDEWROUGHT, Buildings.SLIPWAY, Defences.TIDE, Defences.TIDE_TOWERS),
            new Lob("Stormcallers", "thunderstone_monk", () -> Factions.STORMCALLERS, Buildings.LOTUS_ACADEMY, Defences.STORM, Defences.STORM_TOWERS),
            new Lob("Brassworks", "steam_lobber", () -> Factions.BRASSWORKS, Buildings.MACHINE_SHOP, Defences.BRASS, Defences.BRASS_TOWERS));
    }

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, path);
    }

    /** The lobber's entity type, or null while it is not registered. */
    static EntityType<?> type(Lob l) {
        ResourceLocation key = rl(l.id() + "_unit");
        return ForgeRegistries.ENTITY_TYPES.containsKey(key) ? ForgeRegistries.ENTITY_TYPES.getValue(key) : null;
    }

    static ProductionItem item(Lob l) {
        return ReignOfNetherRegistries.PRODUCTION_ITEM.get(rl(l.id()));
    }

    static double attr(Mob m, Attribute a) {
        var inst = m.getAttribute(a);
        return inst == null ? -1 : inst.getValue();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 1. Trained at every T1 lab, with the shared stats
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Each faction's T1 lab trains its lobber (the real completion path: onComplete -> produceUnit), the entity belongs
     * to that faction, and the unit has the shared base: 95 metal / 130 energy / 2 pop, 80 health, range 15 with a 3
     * dead zone, sight 12 (below its range: it needs spotters), a 1 s set-up, no shots at air, the +3.5 building bonus
     * (and no wall bonus, though it still picks walls), the bots' T1 siege, ships its skin, portrait and lang.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void siege_lobber_trains_at_every_t1_lab_with_the_shared_stats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        List<Mob> made = new ArrayList<>();
        var lang = SkirmishGameTests.enUsLang();
        int i = 0;
        try {
            for (Lob l : lobs()) {
                i++;
                String tag = l.faction() + " " + l.id();
                EntityType<?> type = type(l);
                ProductionItem item = item(l);
                if (type == null || item == null) {
                    problems.add(tag + ": not registered (entity " + (type != null) + ", item " + (item != null) + ")");
                    continue;
                }
                Faction fac = l.fac().get();
                if (!(l.lab() instanceof ProductionBuilding lab) || !lab.productions.get().contains(item))
                    problems.add(tag + ": " + l.lab().name + " does not train it");
                if (fac == null || !fac.key.equals(Factions.ENTITY_FACTION.get(EntityType.getKey(type))))
                    problems.add(tag + ": its entity is not registered to " + l.faction());
                if (EntityRegistrar.getEntityType(item.getItemName()) != type)
                    problems.add(tag + ": its item name does not resolve to its entity type");
                if (BotRoles.siegeFor(fac) != item || BotRoles.siegeIsT2(fac))
                    problems.add(tag + ": the bots' T1 siege is " + BotRoles.siegeFor(fac) + (BotRoles.siegeIsT2(fac) ? " (T2)" : ""));
                String owner = "gametest_lobber_train" + i + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
                BlockPos corner = helper.absolutePos(BlockPos.ZERO);
                BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(l.lab(), level,
                    new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, owner, false);
                if (!(bp instanceof ProductionPlacement pp)) {
                    problems.add(tag + ": " + l.lab().name + " is not a production placement");
                    continue;
                }
                pp.isBuilt = true;
                item.onComplete.accept(level, pp);
                Mob unit = CombatEngineerGameTests.findProduced(level, pp, type, owner);
                if (unit == null) {
                    problems.add(tag + ": completing the item produced no unit");
                    continue;
                }
                made.add(unit);
                if (!(unit instanceof Unit u) || !(unit instanceof AttackerUnit au) || !(unit instanceof FieldArtillery fa)) {
                    problems.add(tag + ": not a field-artillery attacker unit");
                    continue;
                }
                var c = u.getCost();
                if (c == null || c.metal() != METAL || c.energy() != ENERGY || c.population != POP)
                    problems.add(tag + ": costs " + (c == null ? "nothing" : c.metal() + " / " + c.energy() + " / " + c.population + " pop")
                        + ", not " + METAL + " / " + ENERGY + " / " + POP);
                if (Math.abs(unit.getMaxHealth() - HEALTH) > 0.01f)
                    problems.add(tag + ": " + unit.getMaxHealth() + " health, not " + HEALTH);
                double range = attr(unit, AttributeRegistrar.ATTACK_RANGE.get()), sight = attr(unit, AttributeRegistrar.SIGHT_RANGE.get());
                if (Math.abs(range - RANGE) > 0.01)
                    problems.add(tag + ": range " + range + ", not " + RANGE);
                if (Math.abs(sight - SIGHT) > 0.01)
                    problems.add(tag + ": sight " + sight + ", not " + SIGHT);
                if (u.getSightRange() >= au.getAttackRange())
                    problems.add(tag + ": sees " + u.getSightRange() + ", not below its range " + au.getAttackRange());
                if (Math.abs(fa.getMinRange() - MIN_RANGE) > 0.01f)
                    problems.add(tag + ": dead zone " + fa.getMinRange() + ", not " + MIN_RANGE);
                if (fa.getSetUpTicks() != SET_UP_TICKS)
                    problems.add(tag + ": sets up in " + fa.getSetUpTicks() + " ticks, not " + SET_UP_TICKS);
                if (au.canTargetAir())
                    problems.add(tag + ": can shoot at air");
                if (Math.abs(au.getBuildingDamageBonus() - BUILDING_BONUS) > 0.01f)
                    problems.add(tag + ": building bonus " + au.getBuildingDamageBonus() + ", not " + BUILDING_BONUS);
                if (!WallSegment.drawsAggroFrom(unit))
                    problems.add(tag + ": does not pick walls as targets");
                if (WallSegment.damageMultFrom(unit) != 1f)
                    problems.add(tag + ": hits walls x" + WallSegment.damageMultFrom(unit) + " (walls take the defence rate)");
                if (!BotRoles.isSiege(unit) || !RaidTuning.isT1Vehicle(unit))
                    problems.add(tag + ": not in the bots' siege role and T1 vehicles");
                String id = l.id();
                for (String file : List.of(id + "_unit.png", id + "_unit_team.png"))
                    if (!SkirmishGameTests.modFileHas("assets", ReignOfNether.MOD_ID, "textures", "entities", file))
                        problems.add(tag + ": no textures/entities/" + file);
                if (!SkirmishGameTests.modFileHas("assets", ReignOfNether.MOD_ID, "textures", "mobheads", id + ".png"))
                    problems.add(tag + ": no textures/mobheads/" + id + ".png");
                String key = "entity.reignofnether." + id + "_unit";
                for (String k : List.of(key, key + ".tooltip1", key + ".tooltip2", key + ".tooltip3"))
                    if (!lang.has(k))
                        problems.add(tag + ": no lang " + k);
            }
        } finally {
            for (Mob m : made)
                m.discard();
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The pad fights
    // ---------------------------------------------------------------------------------------------------------------

    /** n lobbers in rows at the pad's west end, owned by the sim's attacker (each given the "bow" its attack goal needs). */
    static void lobbers(ServerLevel level, Sim s, Lob l, int n) {
        EntityType<?> type = type(l);
        if (type == null) {
            s.errors.add(l.id() + "_unit is not registered");
            return;
        }
        for (int k = 0; k < n; k++) {
            Mob m = RaidGameTests.spawn(level, (EntityType<? extends Mob>) type, s.atk, s.cell.x0() + 4 + (k % 3) * 1.3,
                s.cell.z0() + 6 + (k / 3) * 1.5);
            if (m == null) {
                s.errors.add("could not make a " + l.id());
                continue;
            }
            if (m instanceof Unit u)
                u.setupEquipmentAndUpgradesServer();
            s.attackers.add(m);
        }
    }

    /** A built enemy defence on the pad (RaidGameTests.siege): a tower shoots back at this sim's lobbers only. */
    static void defence(ServerLevel level, Sim s, Building b) {
        RaidGameTests.siege(level, s, b, false);
        if (s.target instanceof DefenceTower.DefenceTowerPlacement tower)
            tower.onlyFoe = s.atk;
    }

    static boolean allDead(List<Mob> mobs) {
        for (Mob m : mobs)
            if (m.isAlive())
                return false;
        return !mobs.isEmpty();
    }

    static int alive(List<Mob> mobs) {
        int n = 0;
        for (Mob m : mobs)
            if (m.isAlive())
                n++;
        return n;
    }

    /**
     * RaidGameTests.drive with an end for a lost fight (every lobber dead) and a per-tick hook: holds the pads, builds
     * the sims, gives the orders three ticks later, calls {@code each} with the ticks since the order, and hands the
     * sims to the verdict once all are over or {@code maxSim} ticks have passed.
     */
    static void fight(GameTestHelper helper, Pads pads, int maxSim, Supplier<List<Sim>> setup,
                      BiConsumer<List<Sim>, Integer> each, Consumer<List<Sim>> verdict) {
        ServerLevel level = helper.getLevel();
        List<Sim> sims = new ArrayList<>();
        int[] phase = { 0, 0, 0 };
        GameTestChunks.whenEntitiesReady(helper, level, pads.chunks, 5, 400, 1300, "siege pad", () -> {
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
                    return;
                for (Sim s : sims)
                    if (s.order != null)
                        s.order.run();
                phase[0] = 2;
                phase[2] = now;
                return;
            }
            int t = now - phase[2];
            if (each != null)
                each.accept(sims, t);
            boolean over = true;
            for (Sim s : sims) {
                s.observe(t);
                if (s.done < 0 && s.errors.isEmpty() && !allDead(s.attackers))
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

    static String fmt(float s) {
        return RaidGameTests.fmt(s);
    }

    static float hpLeft(BuildingPlacement b) {
        return b == null ? -1 : (float) b.getHealth();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 2. The anti-raid tower (BAR's LLT): one lobber breaks it from outside its reach
    // ---------------------------------------------------------------------------------------------------------------

    /** The window, order -> razed: a step in, the set-up, the flight and five shells 2.5 s apart (~14 s). */
    static final float LLT_TOTAL_MAX = 25f;

    /**
     * One lobber ordered onto each faction's anti-raid tower (range 14) razes it within {@link #LLT_TOTAL_MAX} s and is
     * never shot: its 15 out-reaches the tower (lovish's Rocko: "useful at sniping defenses").
     */
    @GameTest(template = ARENA, timeoutTicks = 600 + 1500)
    public static void siege_lobber_razes_a_raid_tower_from_outside_its_reach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Lob> lobs = lobs();
        Pads pads = new Pads(level, 10, lobs.size());
        fight(helper, pads, 600, () -> {
            List<Sim> sims = new ArrayList<>();
            for (int f = 0; f < lobs.size(); f++) {
                Lob l = lobs.get(f);
                Sim s = new Sim(l.faction(), "llt", pads.cells.get(f), "gametest_lobber_llt" + f + "_a", "gametest_lobber_llt" + f + "_b");
                lobbers(level, s, l, 1);
                defence(level, s, l.set().raidTower());
                sims.add(s);
            }
            return sims;
        }, null, sims -> {
            List<String> problems = new ArrayList<>();
            for (Sim s : sims) {
                Mob lob = s.attackers.isEmpty() ? null : s.attackers.get(0);
                float hurt = lob == null ? -1 : lob.getMaxHealth() - lob.getHealth();
                ReignOfNether.LOGGER.info("[Siege] {} one lobber on the anti-raid tower: razed in {} s (first block {} s), lobber lost {} health{}",
                    s.group, fmt(s.total()), fmt(s.contact < 0 ? -1 : s.contact / 20f), lob == null ? "-" : String.format(Locale.ROOT, "%.0f", hurt),
                    s.done < 0 ? ", tower at " + String.format(Locale.ROOT, "%.0f", hpLeft(s.target)) : "");
                if (s.done < 0 || s.total() > LLT_TOTAL_MAX)
                    problems.add(s.group + ": the anti-raid tower " + (s.done < 0 ? "still stands (" + String.format(Locale.ROOT, "%.0f", hpLeft(s.target)) + " health)"
                        : "fell only after " + fmt(s.total()) + " s") + ", window " + LLT_TOTAL_MAX + " s");
                if (lob == null || !lob.isAlive() || hurt > 0.5f)
                    problems.add(s.group + ": the tower hit the lobber (" + (lob == null || !lob.isAlive() ? "dead" : String.format(Locale.ROOT, "%.0f", hurt) + " health")
                        + ") - it should stand outside the tower's 14");
            }
            if (!problems.isEmpty())
                throw new GameTestAssertException(String.join("; ", problems));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 3. The enhanced tower (BAR's Beamer): two lose, five win
    // ---------------------------------------------------------------------------------------------------------------

    static final int BEAMER_LOSE = 2, BEAMER_WIN = 5;
    static final float BEAMER_TOTAL_MAX = 40f;

    /**
     * Each faction's enhanced anti-raid tower (600 health, range 16: it out-reaches the lobber) kills two lobbers and
     * still stands; five break it within {@link #BEAMER_TOTAL_MAX} s (lovish: "Beamer is good but loses to a lot of
     * rockos").
     */
    @GameTest(template = ARENA, timeoutTicks = 900 + 1500)
    public static void siege_lobber_pair_loses_to_the_enhanced_tower_and_five_break_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Lob> lobs = lobs();
        Pads pads = new Pads(level, 11, lobs.size() * 2);
        fight(helper, pads, 900, () -> {
            List<Sim> sims = new ArrayList<>();
            int n = 0;
            for (int f = 0; f < lobs.size(); f++) {
                Lob l = lobs.get(f);
                for (int k = 0; k < 2; k++) {
                    int count = k == 0 ? BEAMER_LOSE : BEAMER_WIN;
                    Sim s = new Sim(l.faction(), "beamer" + count, pads.cells.get(n++), "gametest_lobber_bm" + f + k + "_a", "gametest_lobber_bm" + f + k + "_b");
                    lobbers(level, s, l, count);
                    defence(level, s, l.towers().raidTowerHeavy());
                    sims.add(s);
                }
            }
            return sims;
        }, null, sims -> {
            List<String> problems = new ArrayList<>();
            for (Sim s : sims) {
                boolean win = s.kind.equals("beamer" + BEAMER_WIN);
                ReignOfNether.LOGGER.info("[Siege] {} {} lobbers on the enhanced tower: {} after {} s, {} lobbers left, tower at {}",
                    s.group, s.attackers.size(), s.done >= 0 ? "razed" : "standing", fmt(s.done >= 0 ? s.total() : -1),
                    alive(s.attackers), String.format(Locale.ROOT, "%.0f", hpLeft(s.target)));
                if (win && (s.done < 0 || s.total() > BEAMER_TOTAL_MAX))
                    problems.add(s.group + ": " + BEAMER_WIN + " lobbers did not break the enhanced tower within " + BEAMER_TOTAL_MAX + " s ("
                        + (s.done < 0 ? String.format(Locale.ROOT, "%.0f", hpLeft(s.target)) + " health left, " + alive(s.attackers) + " lobbers alive" : fmt(s.total()) + " s") + ")");
                if (!win && (s.done >= 0 || !allDead(s.attackers)))
                    problems.add(s.group + ": " + BEAMER_LOSE + " lobbers " + (s.done >= 0 ? "broke the enhanced tower in " + fmt(s.total()) + " s"
                        : "were not all killed (" + alive(s.attackers) + " alive)"));
            }
            if (!problems.isEmpty())
                throw new GameTestAssertException(String.join("; ", problems));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 4. Walls and gates at the defence rate (lovish Oct 29: "walls should be strong")
    // ---------------------------------------------------------------------------------------------------------------

    /** Order -> razed for one lobber (design: a wall in ~20.5 s, a gate in ~15.5 s; the x3 wall bonus made it 8 s). */
    static final float WALL_MIN = 14f, WALL_MAX = 32f, GATE_MIN = 10f, GATE_MAX = 26f;

    /** One lobber takes each faction's wall segment and gate down, no faster than the defence rate and within a window. */
    @GameTest(template = ARENA, timeoutTicks = 900 + 1500)
    public static void siege_lobber_breaks_walls_and_gates_at_the_defence_rate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Lob> lobs = lobs();
        Pads pads = new Pads(level, 12, lobs.size() * 2);
        fight(helper, pads, 900, () -> {
            List<Sim> sims = new ArrayList<>();
            int n = 0;
            for (int f = 0; f < lobs.size(); f++) {
                Lob l = lobs.get(f);
                for (int k = 0; k < 2; k++) {
                    Sim s = new Sim(l.faction(), k == 0 ? "wall" : "gate", pads.cells.get(n++), "gametest_lobber_wl" + f + k + "_a", "gametest_lobber_wl" + f + k + "_b");
                    lobbers(level, s, l, 1);
                    defence(level, s, k == 0 ? l.set().wall() : l.set().gate());
                    sims.add(s);
                }
            }
            return sims;
        }, null, sims -> {
            List<String> problems = new ArrayList<>();
            for (Sim s : sims) {
                boolean wall = s.kind.equals("wall");
                float min = wall ? WALL_MIN : GATE_MIN, max = wall ? WALL_MAX : GATE_MAX;
                ReignOfNether.LOGGER.info("[Siege] {} one lobber on the {}: razed in {} s{}", s.group, s.kind, fmt(s.total()),
                    s.done < 0 ? ", " + String.format(Locale.ROOT, "%.0f", hpLeft(s.target)) + " health left" : "");
                if (s.done < 0 || s.total() < min || s.total() > max)
                    problems.add(s.group + " " + s.kind + ": " + (s.done < 0 ? "still stands (" + String.format(Locale.ROOT, "%.0f", hpLeft(s.target)) + " health)"
                        : "razed in " + fmt(s.total()) + " s") + ", window " + min + "-" + max + " s");
            }
            if (!problems.isEmpty())
                throw new GameTestAssertException(String.join("; ", problems));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // 5. Weak against units: little to one that stands, nothing to one that walks
    // ---------------------------------------------------------------------------------------------------------------

    static final int UNIT_TICKS = 300;
    /** 15 s on a standing Vindicator: a few 4-damage shells. */
    static final float STANDING_MAX = 30f;
    /** A unit walking a loop never stands where a shell lands 2 s later (no lead): at most one glancing hit. */
    static final float WALKER_MAX = 4f;

    /** The loop a walker follows: a rectangle 5-13 blocks in front of the lobber (all of it inside its 15, outside its 3). */
    static BlockPos corner(Cell c, int i) {
        int[][] at = { { 8, 2 }, { 16, 2 }, { 16, 14 }, { 8, 14 } };
        int[] p = at[i & 3];
        return new BlockPos(c.x0() + p[0], PAD_Y + 1, c.z0() + p[1]);
    }

    /**
     * A lobber ordered onto a Vindicator that holds position does it a little damage in 15 s (some, but at most
     * {@link #STANDING_MAX}); one ordered onto a Vindicator that keeps walking a loop barely touches it.
     */
    @GameTest(template = ARENA, timeoutTicks = UNIT_TICKS + 1500)
    public static void siege_lobber_barely_scratches_a_standing_unit_and_misses_a_walking_one(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Lob> lobs = lobs();
        Pads pads = new Pads(level, 13, lobs.size() * 2);
        int[] legs = new int[lobs.size() * 2];
        fight(helper, pads, UNIT_TICKS, () -> {
            List<Sim> sims = new ArrayList<>();
            int n = 0;
            for (int f = 0; f < lobs.size(); f++) {
                Lob l = lobs.get(f);
                for (int k = 0; k < 2; k++) {
                    Cell c = pads.cells.get(n++);
                    Sim s = new Sim(l.faction(), k == 0 ? "standing" : "walking", c, "gametest_lobber_un" + f + k + "_a", "gametest_lobber_un" + f + k + "_b");
                    lobbers(level, s, l, 1);
                    BlockPos at = k == 0 ? new BlockPos(c.x0() + 13, PAD_Y + 1, c.z0() + 6) : corner(c, 0);
                    Mob v = RaidGameTests.spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), s.foe, at.getX() + 0.5, at.getZ() + 0.5);
                    if (v == null) {
                        s.errors.add("could not make a Vindicator");
                    } else {
                        s.victims.add(v);
                        if (k == 0)
                            ((Unit) v).setHoldPosition(true);
                    }
                    s.order = () -> {
                        for (Mob m : s.attackers)
                            if (m instanceof AttackerUnit au && !s.victims.isEmpty())
                                au.setUnitAttackTarget(s.victims.get(0));
                    };
                    sims.add(s);
                }
            }
            return sims;
        }, (sims, t) -> {
            for (int i = 0; i < sims.size(); i++) {
                Sim s = sims.get(i);
                if (!s.kind.equals("walking") || s.victims.isEmpty() || !(s.victims.get(0) instanceof Unit u))
                    continue;
                Mob v = s.victims.get(0);
                BlockPos next = corner(s.cell, legs[i] + 1);
                if (t == 1 || Math.abs(v.getX() - (next.getX() + 0.5)) + Math.abs(v.getZ() - (next.getZ() + 0.5)) < 1.2) {
                    if (t != 1)
                        legs[i]++;
                    u.getMoveGoal().setMoveTarget(corner(s.cell, legs[i] + 1));
                }
            }
        }, sims -> {
            List<String> problems = new ArrayList<>();
            for (Sim s : sims) {
                Mob v = s.victims.isEmpty() ? null : s.victims.get(0);
                float dmg = v == null ? -1 : v.getMaxHealth() - (v.isAlive() ? v.getHealth() : 0);
                ReignOfNether.LOGGER.info("[Siege] {} one lobber on a {} Vindicator for {} s: {} damage", s.group, s.kind,
                    UNIT_TICKS / 20, String.format(Locale.ROOT, "%.1f", dmg));
                if (v == null)
                    continue;
                if (s.kind.equals("standing") && (dmg <= 0 || dmg > STANDING_MAX))
                    problems.add(s.group + ": a standing Vindicator took " + String.format(Locale.ROOT, "%.1f", dmg) + " in 15 s, window (0, " + STANDING_MAX + "]");
                if (s.kind.equals("walking") && dmg > WALKER_MAX)
                    problems.add(s.group + ": a walking Vindicator took " + String.format(Locale.ROOT, "%.1f", dmg) + " in 15 s, at most " + WALKER_MAX);
            }
            if (!problems.isEmpty())
                throw new GameTestAssertException(String.join("; ", problems));
            helper.succeed();
        });
    }
}
