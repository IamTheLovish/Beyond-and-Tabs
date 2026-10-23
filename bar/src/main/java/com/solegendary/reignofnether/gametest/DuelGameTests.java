package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Headless duel simulator: real fights on the GameTest server, as a balance MEASUREMENT next to the paper numbers of
 * tools/balance_audit.py. Equal-metal groups of unit A and unit B fight on their own glass pad with their real goals
 * (attack-move into each other, idle aggro, retaliation) and their activated abilities cast by the bot's own in-fight
 * logic (BotPlayer.castFightAbilities), for at most {@link #MAX_TICKS}. Each duel records the winner, the time and
 * each side's remaining metal value (cost x health fraction of its surviving units).
 *
 * Results go to run-gametest/duel_results.json and as one "[Duel]" log line per duel; CI turns the file into a few
 * ::notice:: annotations (tools/duel_summary.py), the one channel readable from here. The test never fails on what
 * the fights show - only when a duel could not run (a unit that can't be created, has no cost or isn't an attacker,
 * or a duel where nobody ever took damage, i.e. the units never ticked or never met).
 *
 * Isolation: every duel stands on its own walled glass pad 3000 blocks south of the test grid (north holds the
 * extractor pads, west the stress benchmark, east the bot soak), chunk-aligned 2x1 chunks with 32 blocks of air
 * between pads - more than any aggro range in the matrix (Mortar Galley 30) - all at one height, because UnitGrid
 * buckets by x/z only and stacked pads would leak into each other's ability scans. Glass: nothing spawns on it, so no
 * wildlife or night mob ever joins a fight. Owners are unique per duel and in no alliance.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class DuelGameTests {

    static final String ARENA = "gametest_arena";

    /**
     * A fight that is still going after this many ticks is called on remaining value. 55 s: the CI time budget is ~60 s
     * (all duels run at once, beside the 4-minute bot soak); at 40 s most T2 heavy and T3 fights were still going.
     */
    static final int MAX_TICKS = 1100;
    static final int START_DELAY = 20;     // let the forced chunks reach entity-ticking before anything spawns
    static final int SAMPLE_EVERY = 10;    // ticks between value samples / end checks
    static final int REPS = 2;             // the second run swaps the sides (west/east), to cancel spawn-order bias
    static final int PAD_Y = 210;          // above any ordinary terrain; the interior is cleared regardless
    static final int COLUMNS = 6;          // pads per grid row
    static final int BUDGET_T1 = 480, BUDGET_T2 = 1000;

    /** One side of a duel: unit types and how many of each. */
    record Group(String label, List<RegistryObject<? extends EntityType<? extends Mob>>> types, List<Integer> counts) { }

    /**
     * A duel to run: tier label, the two groups, which repetition. {@code open}: the sides start at the far ends of the
     * pad (front ranks ~22 blocks apart instead of ~13), so ranged units and artillery get their range before contact,
     * as when two armies meet in the field - the counter (rock-paper-scissors) rows use it. {@code heroLevel} > 0: any
     * faction hero in the duel is raised to that level and casts its abilities as a bot's hero would.
     */
    record Spec(String tier, Group a, Group b, int rep, boolean open, int heroLevel) {
        Spec(String tier, Group a, Group b, int rep) {
            this(tier, a, b, rep, false, 0);
        }
    }

    /** One running duel's state. */
    static final class Duel {
        final Spec spec;
        final int index;
        final int x0, z0;   // pad corner (min x, min z); the pad is 32 x 16
        final List<List<Mob>> units = List.of(new ArrayList<>(), new ArrayList<>());
        final float[] startMetal = new float[2];
        final float[] value = new float[2];
        com.solegendary.reignofnether.bot.BotPlayer[] bots;
        boolean done, engaged;
        int ticks;
        // ids of the risen skeletons each side had (Bone Dragon kills), for the result line
        final List<java.util.Set<Integer>> risen = List.<java.util.Set<Integer>>of(new java.util.HashSet<Integer>(), new java.util.HashSet<Integer>());
        String winner = "draw";
        boolean timeout;

        Duel(Spec spec, int index, int x0, int z0) {
            this.spec = spec;
            this.index = index;
            this.x0 = x0;
            this.z0 = z0;
        }

        String owner(int side) {
            return "gametest_duel_" + index + (side == 0 ? "_a" : "_b");
        }

        AABB bounds() {
            return new AABB(x0 - 6, PAD_Y - 6, z0 - 6, x0 + 38, PAD_Y + 48, z0 + 22);
        }
    }

    static final Map<EntityType<?>, Integer> METAL = new HashMap<>();

    /** Metal cost of a unit type, read from a throwaway instance (never added to the world); 0 if unknown. */
    static int metalOf(ServerLevel level, EntityType<? extends Mob> type) {
        return METAL.computeIfAbsent(type, t -> {
            var e = type.create(level);
            int m = e instanceof Unit u && u.getCost() != null ? u.getCost().metal() : 0;
            if (e != null)
                e.discard();
            return m;
        });
    }

    /**
     * Counts for an A-vs-B duel at about {@code budget} metal a side, as close to equal metal as whole units allow:
     * scores the metal mismatch between the sides first and the distance from the budget second.
     */
    static int[] equalMetalCounts(int costA, int costB, int budget) {
        int bestA = 1, bestB = 1;
        double best = Double.MAX_VALUE;
        for (int na = 1; na * costA <= budget * 1.6; na++) {
            int nb = Math.max(1, Math.round((float) na * costA / costB));
            double ma = na * costA, mb = nb * costB;
            double score = Math.abs(ma - mb) / Math.max(ma, mb) + 0.3 * Math.abs((ma + mb) / 2 - budget) / budget;
            if (score < best) {
                best = score;
                bestA = na;
                bestB = nb;
            }
        }
        return new int[] { bestA, bestB };
    }

    static String name(RegistryObject<? extends EntityType<? extends Mob>> type) {
        String path = type.getId().getPath().replace("_unit", "");
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_"))
            if (!w.isEmpty())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return sb.toString();
    }

    @SafeVarargs
    static List<RegistryObject<? extends EntityType<? extends Mob>>> types(RegistryObject<? extends EntityType<? extends Mob>>... t) {
        return List.of(t);
    }

    /** The matrix: T1 line units pairwise, T2 heavies pairwise, every T3 against an equal-metal counter mix. */
    static List<Spec> matrix(ServerLevel level) {
        List<Spec> specs = new ArrayList<>();
        // each faction's main T1 line unit (the Court has no "line" role: its Leafblade is the bread-and-butter melee),
        // plus the Tidewrought's Cutlass Raider and the Stormcallers' Jade Guard (line) and Fire Adept (a mana-paid
        // skirmisher - its owner here has no pool, so it fires at its full rate: the matrix measures the unit), and the
        // Brassworks' Iron Warden (line), Rifle Squad (skirmisher) and Steam Runner (raider) - the machines among them
        // vent at 100 heat mid-duel, which is what the matrix is here to measure
        var t1 = types(EntityRegistrar.VINDICATOR_UNIT, EntityRegistrar.ZOMBIE_UNIT, EntityRegistrar.BRUTE_UNIT,
            EntityRegistrar.LEAFBLADE_UNIT, EntityRegistrar.REEF_GUARD_UNIT, EntityRegistrar.CUTLASS_RAIDER_UNIT,
            EntityRegistrar.JADE_GUARD_UNIT, EntityRegistrar.FIRE_ADEPT_UNIT,
            EntityRegistrar.IRON_WARDEN_UNIT, EntityRegistrar.RIFLE_SQUAD_UNIT, EntityRegistrar.STEAM_RUNNER_UNIT);
        // each faction's T2 heavy (the Tidewrought's Drydock has no front-liner: its Mortar Galley is the big body), plus
        // the Stormcallers' Phoenix (slice 4: a flyer that rises once - the matrix shows what the rebirth is worth), and
        // the Brassworks' Steam Juggernaut (their T2 heavy) and Tesla Walker (a chain that needs a crowd: a duel is the
        // hardest case for it) - both vent at 100 heat mid-duel
        var t2 = types(EntityRegistrar.RAVAGER_UNIT, EntityRegistrar.WARDEN_UNIT, EntityRegistrar.WITHER_SKELETON_UNIT,
            EntityRegistrar.ELDER_TREANT_UNIT, EntityRegistrar.MORTAR_GALLEY_UNIT, EntityRegistrar.PHOENIX_UNIT,
            EntityRegistrar.STEAM_JUGGERNAUT_UNIT, EntityRegistrar.TESLA_WALKER_UNIT);
        pairs(level, specs, "T1", t1, BUDGET_T1);
        pairs(level, specs, "T2", t2, BUDGET_T2);
        // T3 rule (c): an experimental loses to an equal-cost focused T2 army. The army: each faction's best answer to a
        // big ground body (Golem, Warden, Wither Skeleton's stacking wither, Stag Lancer, Mortar Galley) - against the
        // flying Bone Dragon each faction's anti-air instead (Gravebound has no T2 anti-air: Skeletons; the
        // Tidewrought's is the T1 Flak Swivel)
        var ground = types(EntityRegistrar.IRON_GOLEM_UNIT, EntityRegistrar.WARDEN_UNIT, EntityRegistrar.WITHER_SKELETON_UNIT,
            EntityRegistrar.STAG_LANCER_UNIT, EntityRegistrar.MORTAR_GALLEY_UNIT);
        var air = types(EntityRegistrar.WINDCALLER_UNIT, EntityRegistrar.SKELETON_UNIT, EntityRegistrar.BLAZE_UNIT,
            EntityRegistrar.WISP_CHOIR_UNIT, EntityRegistrar.FLAK_SWIVEL_UNIT);
        // the Celestial Dragon (Stormcallers T3, slice 4) is an air unit too: it meets the anti-air mix, whose Windcaller,
        // Wisp Choir and Flak Swivel are wind-type and ground it. The Iron Leviathan Engine (Brassworks T3, slice 4) is a
        // ground body: it meets the T2 mix, and its drones (launched every 30 s, so at most one wave in a duel) fight for it
        var t3 = types(EntityRegistrar.SUN_COLOSSUS_UNIT, EntityRegistrar.BONE_DRAGON_UNIT, EntityRegistrar.WAR_MAMMOTH_UNIT,
            EntityRegistrar.PLAGUE_ABOMINATION_UNIT,   // second T3 (T3b-3): a slow melee rotter on the ground mix, an equal-metal duel row
            EntityRegistrar.MAGMA_HURLER_UNIT,   // second T3 (T3b-2): artillery on the ground mix, an equal-metal duel row
            EntityRegistrar.WORLDBREAKER_UNIT,   // Tier 4 (H5a): a melee siege giant on the ground mix, an equal-metal duel row
            EntityRegistrar.JADE_SENTINEL_UNIT,   // second T3 (T3b-4): a slow melee wall on the ground mix, an equal-metal duel row
            EntityRegistrar.CATHEDRAL_WARDEN_UNIT,   // second T3 (T3b-5): a slow ranged support on the ground mix, an equal-metal duel row
            EntityRegistrar.ABYSSAL_HYDRA_UNIT,   // second T3 (T3b-6): a ranged three-headed shooter on the ground mix, an equal-metal duel row
            EntityRegistrar.THORNWING_ROC_UNIT,   // second T3 (T3b-7): a fast flyer, meets the AA mix, an equal-metal duel row
            EntityRegistrar.BRASS_ZEPPELIN_UNIT,   // second T3 (T3b-8): a slow air bomber, meets the AA mix, an equal-metal duel row
            EntityRegistrar.WORLD_TREE_WALKER_UNIT, EntityRegistrar.CELESTIAL_DRAGON_UNIT, EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT);
        for (var big : t3) {
            int cost = metalOf(level, big.get());
            boolean flyer = big == EntityRegistrar.BONE_DRAGON_UNIT || big == EntityRegistrar.CELESTIAL_DRAGON_UNIT || big == EntityRegistrar.THORNWING_ROC_UNIT || big == EntityRegistrar.BRASS_ZEPPELIN_UNIT;
            var mixTypes = flyer ? air : ground;
            Group mix = mix(level, mixTypes, cost, flyer ? "AA mix" : "T2 mix");
            Group one = new Group(name(big), types(big), List.of(1));
            for (int rep = 0; rep < REPS; rep++)
                specs.add(new Spec("T3", one, mix, rep));
        }
        // big-units (design/big_units.md): each T2 beast against the same equal-metal T2 counter mix as the ground T3s -
        // the beasts are meant to win only through their signature (aura, lane charge, wrecks, riders), most of which a
        // bare duel can't show, so a loss here is expected for the elephant and the bison and a draw-ish fight for the
        // rhino and the behemoth (no wrecks on a fresh pad until something dies)
        var beasts = types(EntityRegistrar.ORIFLAMME_ELEPHANT_UNIT, EntityRegistrar.GOREHORN_RHINO_UNIT,
            EntityRegistrar.OSSUARY_BEHEMOTH_UNIT, EntityRegistrar.MOSSBACK_BISON_UNIT,
            // beasts-2: the turtle fights unmoored here (a duel never anchors it) and the qilin/hauler's signatures
            // (spells, machines to mend) have nothing to work on - expect losses, the rows are for the record
            EntityRegistrar.SHELLBACK_TURTLE_UNIT, EntityRegistrar.JADE_QILIN_UNIT, EntityRegistrar.STEAM_HAULER_UNIT);
        for (var beast : beasts) {
            Group mix = mix(level, ground, metalOf(level, beast.get()), "T2 mix");
            Group one = new Group(name(beast), types(beast), List.of(1));
            for (int rep = 0; rep < REPS; rep++)
                specs.add(new Spec("Beast", one, mix, rep));
        }
        return specs;
    }

    static void pairs(ServerLevel level, List<Spec> specs, String tier,
                      List<RegistryObject<? extends EntityType<? extends Mob>>> roster, int budget) {
        for (int i = 0; i < roster.size(); i++)
            for (int j = i + 1; j < roster.size(); j++) {
                var a = roster.get(i);
                var b = roster.get(j);
                int[] n = equalMetalCounts(Math.max(1, metalOf(level, a.get())), Math.max(1, metalOf(level, b.get())), budget);
                Group ga = new Group(name(a), types(a), List.of(n[0]));
                Group gb = new Group(name(b), types(b), List.of(n[1]));
                for (int rep = 0; rep < REPS; rep++)
                    specs.add(new Spec(tier, ga, gb, rep));
            }
    }

    /** Round-robin one of each type until the next would overshoot the budget by more than 4%. */
    static Group mix(ServerLevel level, List<RegistryObject<? extends EntityType<? extends Mob>>> roster, int budget, String label) {
        int[] counts = new int[roster.size()];
        int total = 0;
        boolean added = true;
        while (added) {
            added = false;
            for (int i = 0; i < roster.size(); i++) {
                int c = Math.max(1, metalOf(level, roster.get(i).get()));
                if (total + c <= budget * 1.04) {
                    counts[i]++;
                    total += c;
                    added = true;
                }
            }
        }
        List<Integer> list = new ArrayList<>();
        for (int c : counts)
            list.add(c);
        return new Group(label, roster, list);
    }

    /** Lays the pad: glass floor, a 3-high glass rim (knockback must not throw anyone off), cleared air above. */
    static void buildPad(ServerLevel level, int x0, int z0) {
        BlockState glass = Blocks.GLASS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 32; dx++)
            for (int dz = 0; dz < 16; dz++) {
                boolean rim = dx == 0 || dz == 0 || dx == 31 || dz == 15;
                level.setBlock(p.set(x0 + dx, PAD_Y, z0 + dz), glass, 2);
                for (int dy = 1; dy <= 16; dy++) {
                    p.set(x0 + dx, PAD_Y + dy, z0 + dz);
                    if (rim && dy <= 3)
                        level.setBlock(p, glass, 2);
                    else if (!level.getBlockState(p).isAir())
                        level.setBlock(p, air, 2);
                }
            }
    }

    /**
     * Spawns one side in ranks facing the other: side 0 on the west half in rep 0 (east in rep 1). Returns an error
     * message, or null.
     */
    static String spawnSide(ServerLevel level, Duel d, int side) {
        Group g = side == 0 ? d.spec.a() : d.spec.b();
        boolean west = (side == 0) == (d.spec.rep() == 0);
        List<EntityType<? extends Mob>> order = new ArrayList<>();
        for (int t = 0; t < g.types().size(); t++)
            for (int k = 0; k < g.counts().get(t); k++)
                order.add(g.types().get(t).get());
        double spacing = 1.3;
        for (var type : order)
            spacing = Math.max(spacing, type.getWidth() + 0.5);
        int perRow = Math.max(1, (int) (13 / spacing));
        double centreX = d.x0 + 16, centreZ = d.z0 + 8;
        double gap = d.spec.open() ? 11 : 6;   // centre to the front rank
        for (int k = 0; k < order.size(); k++) {
            var type = order.get(k);
            Mob e = type.create(level);
            if (!(e instanceof AttackerUnit) || !(e instanceof Unit u)) {
                if (e != null)
                    e.discard();
                return "could not create " + EntityType.getKey(type) + " as an attacker";
            }
            if (u.getCost() == null || u.getCost().metal() <= 0) {
                e.discard();
                return EntityType.getKey(type) + " has no metal cost";
            }
            int row = k / perRow, col = k % perRow;
            int inRow = Math.min(perRow, order.size() - row * perRow);
            double x = centreX + (west ? -1 : 1) * (gap + spacing / 2 + row * spacing);
            double z = centreZ - (inRow - 1) * spacing / 2 + col * spacing;
            x = Math.max(d.x0 + 1.5, Math.min(d.x0 + 30.5, x));   // a huge rank would reach the rim: keep it inside
            e.moveTo(x, PAD_Y + 1, z, west ? -90 : 90, 0);
            u.setOwnerName(d.owner(side));
            if (!level.addFreshEntity(e)) {
                e.discard();
                return "the level refused " + EntityType.getKey(type);
            }
            d.units.get(side).add(e);
            d.startMetal[side] += u.getCost().metal();
            if (d.spec.heroLevel() > 1 && e instanceof com.solegendary.reignofnether.hero.FactionHero fh) {
                // mid-game hero: the level's stats and skill points (one per level, as Reign of Nether grants them),
                // spent as a bot spends them, full health and mana
                fh.setExperience(com.solegendary.reignofnether.hero.HeroProgression.xpForLevel(d.spec.heroLevel()));
                fh.setSkillPoints(d.spec.heroLevel());
                com.solegendary.reignofnether.hero.HeroBot.spendPoints(fh);
                e.setHealth(e.getMaxHealth());
                fh.setMana(fh.getMaxMana());
            }
        }
        d.value[side] = d.startMetal[side];
        return null;
    }

    /** Remaining metal value of a side: cost x health fraction of each living unit. */
    static float sideValue(List<Mob> units) {
        float v = 0;
        for (Mob m : units)
            if (m.isAlive() && !m.isRemoved() && m instanceof Unit u && u.getCost() != null)
                v += u.getCost().metal() * Math.max(0f, Math.min(1f, m.getHealth() / Math.max(1f, m.getMaxHealth())));
        return v;
    }

    static BlockPos centroid(List<Mob> units, int fallbackX, int fallbackZ) {
        double x = 0, z = 0;
        int n = 0;
        for (Mob m : units)
            if (m.isAlive()) {
                x += m.getX();
                z += m.getZ();
                n++;
            }
        return n == 0 ? new BlockPos(fallbackX, PAD_Y + 1, fallbackZ)
            : BlockPos.containing(x / n, PAD_Y + 1, z / n);
    }

    /** Removes everything a duel left on its pad (units, wrecks, summons, projectiles). */
    static void clearDuel(ServerLevel level, Duel d) {
        for (var side : d.units)
            for (Mob m : side)
                if (!m.isRemoved())
                    m.discard();
        for (var e : level.getEntities((net.minecraft.world.entity.Entity) null, d.bounds(), e -> !(e instanceof Player)))
            e.discard();
    }

    // its own batch, like the counter matrix: its ~2,000 units at once make every block change in the same batch walk
    // them all (ServerLevel.sendBlockUpdated checks each navigating mob's path), and whichever 100-test chunk of the
    // default batch it landed in with the production pad tests ran at under 1 tps (CI, command-queue branch)
    @GameTest(template = ARENA, timeoutTicks = START_DELAY + MAX_TICKS + 200, batch = "duel_matrix")
    public static void duel_simulator_measures_equal_metal_fights(GameTestHelper helper) {
        runDuels(helper, matrix(helper.getLevel()), 0, "duel_results.json");
    }

    /**
     * The counter check (design/balance.md "Rock-paper-scissors"), equal-metal best-T1-mix fights between the factions
     * and mid-game heroes against T2 units - its own batch, so its ~190 duels never share the server with the main
     * matrix and the bot soak, and its own pads east of the main matrix and the melee diagnostics.
     */
    @GameTest(template = ARENA, timeoutTicks = START_DELAY + MAX_TICKS + 200, batch = "duel_counters")
    public static void duel_counters_mixes_and_heroes(GameTestHelper helper) {
        runDuels(helper, counterMatrix(helper.getLevel()), RPS_OFFSET_X, "duel_results_counters.json");
    }

    /** Block offsets east of the test grid: main matrix 0-384, melee diagnostics from 448, counter rows from 896. */
    static final int MELEE_OFFSET_X = 448, RPS_OFFSET_X = 896;

    // ---------------------------------------------------------------------------------------------------------------
    // Rock-paper-scissors: each faction's T1 roles (null = the faction has none at T1). Support units (Moonwell Bearer,
    // Tide Priest, Ink Scribe, Shield Projector) are left out: what they do needs a real owner's energy and allies to
    // buff, which a duel owner has not, and none of them is an attacker.
    // ---------------------------------------------------------------------------------------------------------------

    static final String[] FACTION = { "Sunforged", "Gravebound", "Horde", "Verdant", "Tidewrought", "Stormcallers", "Brassworks" };

    @SuppressWarnings("unchecked")
    static List<RegistryObject<? extends EntityType<? extends Mob>>> role(String role) {
        RegistryObject<? extends EntityType<? extends Mob>>[] r = switch (role) {
            // fast melee that dives the back line
            case "raider" -> new RegistryObject[] { null, EntityRegistrar.SPIDER_UNIT, EntityRegistrar.MARAUDER_UNIT,
                EntityRegistrar.LEAFBLADE_UNIT, EntityRegistrar.CUTLASS_RAIDER_UNIT, EntityRegistrar.BLADE_DANCER_UNIT,
                EntityRegistrar.STEAM_RUNNER_UNIT };
            // the durable front (the Court's is the Sentinel Treant: its Leafblade is the raider)
            case "line" -> new RegistryObject[] { EntityRegistrar.VINDICATOR_UNIT, EntityRegistrar.ZOMBIE_UNIT,
                EntityRegistrar.BRUTE_UNIT, EntityRegistrar.SENTINEL_TREANT_UNIT, EntityRegistrar.REEF_GUARD_UNIT,
                EntityRegistrar.JADE_GUARD_UNIT, EntityRegistrar.IRON_WARDEN_UNIT };
            // ranged infantry, 12-16 blocks
            case "skirmisher" -> new RegistryObject[] { EntityRegistrar.PILLAGER_UNIT, EntityRegistrar.SKELETON_UNIT,
                EntityRegistrar.HEADHUNTER_UNIT, EntityRegistrar.THORNBOW_UNIT, EntityRegistrar.HARPOONER_UNIT,
                EntityRegistrar.FIRE_ADEPT_UNIT, EntityRegistrar.RIFLE_SQUAD_UNIT };
            // 22-24 block splash
            case "artillery" -> new RegistryObject[] { null, null, null, null, EntityRegistrar.BOMBARD_CREW_UNIT,
                EntityRegistrar.ROCKET_BATTERY_UNIT, EntityRegistrar.MORTAR_CRAWLER_UNIT };
            case "aa" -> new RegistryObject[] { null, null, null, null, EntityRegistrar.FLAK_SWIVEL_UNIT,
                EntityRegistrar.WIND_MONK_UNIT, EntityRegistrar.FLAK_CART_UNIT };
            default -> throw new IllegalArgumentException(role);
        };
        return java.util.Arrays.asList(r);
    }

    /**
     * The intended counters, winner first: line beats raider (it outlasts the dive), raider beats skirmisher (it closes
     * the gap faster than they shoot it down), skirmisher beats line (free volleys while the line walks in), raider beats
     * artillery (too fast to be shelled), artillery beats a line (a clumped slow front eats the splash all the way in).
     */
    static final String[][] COUNTERS = { { "line", "raider" }, { "raider", "skirmisher" }, { "skirmisher", "line" },
        { "raider", "artillery" }, { "artillery", "line" } };

    /** A faction's best T1 army, in spawn order (front first), repeated as often as the budget allows. */
    static List<RegistryObject<? extends EntityType<? extends Mob>>> bestMix(int faction) {
        return switch (faction) {
            case 0 -> types(EntityRegistrar.VINDICATOR_UNIT, EntityRegistrar.PILLAGER_UNIT, EntityRegistrar.VINDICATOR_UNIT,
                EntityRegistrar.PILLAGER_UNIT);
            case 1 -> types(EntityRegistrar.ZOMBIE_UNIT, EntityRegistrar.SKELETON_UNIT, EntityRegistrar.ZOMBIE_UNIT,
                EntityRegistrar.SKELETON_UNIT, EntityRegistrar.SPIDER_UNIT);
            case 2 -> types(EntityRegistrar.BRUTE_UNIT, EntityRegistrar.HEADHUNTER_UNIT, EntityRegistrar.BRUTE_UNIT,
                EntityRegistrar.HEADHUNTER_UNIT, EntityRegistrar.MARAUDER_UNIT);
            case 3 -> types(EntityRegistrar.SENTINEL_TREANT_UNIT, EntityRegistrar.THORNBOW_UNIT, EntityRegistrar.LEAFBLADE_UNIT,
                EntityRegistrar.THORNBOW_UNIT, EntityRegistrar.LEAFBLADE_UNIT);
            case 4 -> types(EntityRegistrar.REEF_GUARD_UNIT, EntityRegistrar.HARPOONER_UNIT, EntityRegistrar.CUTLASS_RAIDER_UNIT,
                EntityRegistrar.HARPOONER_UNIT, EntityRegistrar.BOMBARD_CREW_UNIT);
            case 5 -> types(EntityRegistrar.JADE_GUARD_UNIT, EntityRegistrar.FIRE_ADEPT_UNIT, EntityRegistrar.BLADE_DANCER_UNIT,
                EntityRegistrar.FIRE_ADEPT_UNIT, EntityRegistrar.ROCKET_BATTERY_UNIT);
            default -> types(EntityRegistrar.IRON_WARDEN_UNIT, EntityRegistrar.RIFLE_SQUAD_UNIT, EntityRegistrar.STEAM_RUNNER_UNIT,
                EntityRegistrar.RIFLE_SQUAD_UNIT, EntityRegistrar.MORTAR_CRAWLER_UNIT);
        };
    }

    static final int BUDGET_MIX = 720;

    /**
     * Walks the pattern round and round, adding each unit that brings the total closer to the budget, until none does:
     * every faction's army lands within half its cheapest unit of the budget ("fits under budget" left the Stormcallers
     * and the Horde ~10% short of the others).
     */
    static Group patternMix(ServerLevel level, List<RegistryObject<? extends EntityType<? extends Mob>>> pattern, int budget, String label) {
        List<RegistryObject<? extends EntityType<? extends Mob>>> distinct = new ArrayList<>();
        for (var t : pattern)
            if (!distinct.contains(t))
                distinct.add(t);
        int[] counts = new int[distinct.size()];
        int total = 0;
        boolean added = true;
        while (added) {
            added = false;
            for (var t : pattern) {
                int c = Math.max(1, metalOf(level, t.get()));
                if (Math.abs(total + c - budget) < Math.abs(total - budget)) {
                    counts[distinct.indexOf(t)]++;
                    total += c;
                    added = true;
                }
            }
        }
        List<Integer> list = new ArrayList<>();
        for (int c : counts)
            list.add(c);
        return new Group(label, distinct, list);
    }

    /** The counter matrix: rock-paper-scissors rows, best-T1-mix rows, hero rows. */
    static List<Spec> counterMatrix(ServerLevel level) {
        List<Spec> specs = new ArrayList<>();
        // each counter relation within every faction that has both roles, and across factions: each faction's winner
        // role against the next faction (in FACTION order, wrapping) that fields the loser role
        for (String[] rel : COUNTERS) {
            var win = role(rel[0]);
            var lose = role(rel[1]);
            String tier = "RPS " + rel[0] + ">" + rel[1];
            for (int f = 0; f < FACTION.length; f++) {
                if (win.get(f) == null)
                    continue;
                if (lose.get(f) != null)
                    counterPair(level, specs, tier, win.get(f), lose.get(f));
                for (int k = 1; k < FACTION.length; k++) {
                    var other = lose.get((f + k) % FACTION.length);
                    if (other != null) {
                        counterPair(level, specs, tier, win.get(f), other);
                        break;
                    }
                }
            }
        }
        // anti-air beats air: each T1 anti-air against the T2 gunships it exists to stop
        for (var aa : role("aa"))
            if (aa != null)
                for (var flyer : types(EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT, EntityRegistrar.PHOENIX_UNIT))
                    counterPair(level, specs, "RPS aa>air", aa, flyer);
        // cross-faction: every pair of factions' best T1 armies at equal metal; none should win by more than 1.5x
        List<Group> mixes = new ArrayList<>();
        for (int f = 0; f < FACTION.length; f++)
            mixes.add(patternMix(level, bestMix(f), BUDGET_MIX, FACTION[f]));
        for (int i = 0; i < mixes.size(); i++)
            for (int j = i + 1; j < mixes.size(); j++)
                for (int rep = 0; rep < REPS; rep++)
                    specs.add(new Spec("Mix", mixes.get(i), mixes.get(j), rep, true, 0));
        // heroes (design/hero_mode.md section 6): a level-5 hero should beat two T2 units and lose to three; the
        // yardstick is the Iron Golem (a plain T2 body, 120 HP, 4 DPS, nothing a duel can't show)
        var heroes = types(EntityRegistrar.DAWN_PALADIN_UNIT, EntityRegistrar.GRAVECALLER_UNIT, EntityRegistrar.HORDE_WARCHIEF_UNIT,
            EntityRegistrar.THORNHEART_DRUID_UNIT, EntityRegistrar.CORSAIR_CAPTAIN_UNIT, EntityRegistrar.STORM_WITCH_UNIT,
            EntityRegistrar.ARCH_ENGINEER_UNIT);
        for (var hero : heroes)
            for (int n = 2; n <= 3; n++) {
                Group h = new Group(name(hero), types(hero), List.of(1));
                // the label carries the count: rows are aggregated by tier and labels, and 2 and 3 Golems are two rows
                Group golems = new Group(n + "xIronGolem", types(EntityRegistrar.IRON_GOLEM_UNIT), List.of(n));
                for (int rep = 0; rep < REPS; rep++)
                    specs.add(new Spec("Hero", h, golems, rep, false, HERO_LEVEL));
            }
        return specs;
    }

    static final int HERO_LEVEL = 5;

    /** One counter row: the intended winner is always side A, equal metal at the T1 budget, open field. */
    static void counterPair(ServerLevel level, List<Spec> specs, String tier,
                            RegistryObject<? extends EntityType<? extends Mob>> winner,
                            RegistryObject<? extends EntityType<? extends Mob>> loser) {
        int[] n = equalMetalCounts(Math.max(1, metalOf(level, winner.get())), Math.max(1, metalOf(level, loser.get())), BUDGET_T1);
        Group ga = new Group(name(winner), types(winner), List.of(n[0]));
        Group gb = new Group(name(loser), types(loser), List.of(n[1]));
        for (int rep = 0; rep < REPS; rep++)
            specs.add(new Spec(tier, ga, gb, rep, true, 0));
    }

    /** Hero AI hook: HeroBot.useAbilities is package-private (it runs for bot-owned heroes only); a duel owner is no bot. */
    static java.lang.reflect.Method heroAbilities;

    static void castHeroAbilities(List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.hero.FactionHero fh) || !(le instanceof Mob mob))
                continue;
            try {
                if (heroAbilities == null) {
                    heroAbilities = com.solegendary.reignofnether.hero.HeroBot.class.getDeclaredMethod("useAbilities",
                        Mob.class, com.solegendary.reignofnether.hero.FactionHero.class);
                    heroAbilities.setAccessible(true);
                }
                heroAbilities.invoke(null, mob, fh);
            } catch (ReflectiveOperationException ex) {
                ReignOfNether.LOGGER.warn("[Duel] could not drive hero abilities: " + ex);
                return;
            }
        }
    }

    static void runDuels(GameTestHelper helper, List<Spec> specs, int offsetX, String resultFile) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        int baseChunkX = (origin.getX() + offsetX) >> 4, baseChunkZ = (origin.getZ() + 3000) >> 4;
        List<Duel> duels = new ArrayList<>();
        List<long[]> chunks = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            // pads every 4 chunks along x and 3 along z: 32 x 16 pad, then 32 blocks of air to the next
            int cx = baseChunkX + (i % COLUMNS) * 4, cz = baseChunkZ + (i / COLUMNS) * 3;
            duels.add(new Duel(specs.get(i), i, cx << 4, cz << 4));
            chunks.add(new long[] { cx, cz });
            chunks.add(new long[] { cx + 1, cz });
        }
        // async FORCED tickets and a bounded wait: setChunkForced generated each chunk synchronously on the server
        // thread with no way out (CI hangs, see GameTestChunks)
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "duel pad");
        for (Duel d : duels)
            buildPad(level, d.x0, d.z0);
        List<String> errors = new ArrayList<>();
        long[] wallStart = { System.nanoTime() };   // CI cost of the whole simulator, pads included

        helper.runAfterDelay(START_DELAY, () -> {
            for (Duel d : duels) {
                for (int side = 0; side < 2 && !d.done; side++) {
                    String err = spawnSide(level, d, side);
                    if (err != null) {
                        errors.add("duel " + d.index + " (" + d.spec.a().label() + " v " + d.spec.b().label() + "): " + err);
                        d.done = true;
                        clearDuel(level, d);
                    }
                }
                if (d.done)
                    continue;
                d.bots = new com.solegendary.reignofnether.bot.BotPlayer[2];
                for (int side = 0; side < 2; side++)
                    d.bots[side] = new com.solegendary.reignofnether.bot.BotPlayer(d.owner(side), null,
                        com.solegendary.reignofnether.bot.BotPlayer.Difficulty.MEDIUM, new BlockPos(d.x0 + 16, PAD_Y + 1, d.z0 + 8));
            }
        });
        // a few ticks later (the units have joined the unit lists): everyone attack-moves into the other side
        helper.runAfterDelay(START_DELAY + 3, () -> {
            for (Duel d : duels)
                if (!d.done)
                    for (int side = 0; side < 2; side++) {
                        BlockPos at = centroid(d.units.get(1 - side), d.x0 + 16, d.z0 + 8);
                        for (Mob m : d.units.get(side))
                            ((AttackerUnit) m).setAttackMoveTarget(at);
                    }
        });
        for (int t = SAMPLE_EVERY; t <= MAX_TICKS; t += SAMPLE_EVERY) {
            final int tick = t;
            helper.runAfterDelay(START_DELAY + t, () -> {
                List<LivingEntity> mine = new ArrayList<>();
                for (Duel d : duels) {
                    if (d.done)
                        continue;
                    d.ticks = tick;
                    for (int side = 0; side < 2; side++) {
                        d.value[side] = sideValue(d.units.get(side));
                        if (d.value[side] < d.startMetal[side] - 0.01f)
                            d.engaged = true;
                    }
                    boolean deadA = d.value[0] <= 0, deadB = d.value[1] <= 0;
                    if (deadA || deadB || tick >= MAX_TICKS) {
                        d.done = true;
                        d.timeout = !deadA && !deadB;
                        float keepA = d.value[0] / d.startMetal[0], keepB = d.value[1] / d.startMetal[1];
                        if (deadB && !deadA) d.winner = "A";
                        else if (deadA && !deadB) d.winner = "B";
                        else if (Math.abs(keepA - keepB) >= 0.05f) d.winner = keepA > keepB ? "A" : "B";
                        if (!d.engaged)
                            errors.add("duel " + d.index + " (" + d.spec.a().label() + " v " + d.spec.b().label()
                                + "): nobody took damage in " + MAX_TICKS / 20 + " s - the units never fought");
                        clearDuel(level, d);
                        continue;
                    }
                    if (tick % 20 != 0)
                        continue;
                    // once a second, as a bot thinks: activated abilities, and idle units sent back into the fight
                    for (int side = 0; side < 2; side++) {
                        mine.clear();
                        for (Mob m : d.units.get(side))
                            if (m.isAlive())
                                mine.add(m);
                        // a Bone Dragon's kills rise as skeletons (DragonRaiseServerEvents): they fight and take orders
                        // like the rest of the side, but are not in its metal value (they cost nothing and crumble)
                        int before = mine.size();
                        com.solegendary.reignofnether.unit.DragonRaiseServerEvents.risenOf(d.owner(side), mine);
                        for (int k = before; k < mine.size(); k++)
                            d.risen.get(side).add(mine.get(k).getId());
                        d.bots[side].castFightAbilities(level, mine);
                        if (d.spec.heroLevel() > 0)
                            castHeroAbilities(mine);
                        BlockPos at = null;
                        for (LivingEntity le : mine)
                            if (((Unit) le).isIdle()) {
                                if (at == null)
                                    at = centroid(d.units.get(1 - side), d.x0 + 16, d.z0 + 8);
                                ((AttackerUnit) le).setAttackMoveTarget(at);
                            }
                    }
                }
            });
        }
        helper.runAfterDelay(START_DELAY + MAX_TICKS + 5, () -> {
            for (Duel d : duels)
                if (!d.done)
                    clearDuel(level, d);
            GameTestChunks.releaseAll(level, chunks);
            writeResults(duels, errors, (System.nanoTime() - wallStart[0]) / 1e9, resultFile);
            if (!errors.isEmpty())
                helper.fail(errors.size() + " duel(s) could not run: " + String.join("; ", errors));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Melee diagnostics: WHY a melee unit wins or loses its duels. Per T1 line unit, three small fights on their own
    // pads (3000 south and MELEE_OFFSET_X east of the grid: they used to sit at 3600 south, inside the duel matrix once
    // it grew past 12 rows, where duel units could aggro the 5000-HP dummies): one attacker on a passive dummy (does it
    // swing at its nominal rate and hit for its nominal damage?), three on one dummy (are focus-fire hits swallowed?)
    // and five against five live Halberdiers (does the scrum's retargeting cost it swings?). Every body is healed
    // to full each tick, so nobody dies and the window stays a steady state. Measured from the
    // outside only (health deltas, swing animation, getTarget), so it sees exactly what a duel sees. One "[Melee]" log
    // line per fight, which CI turns into a notice. Never fails on the numbers - only if a body could not be made.
    // ---------------------------------------------------------------------------------------------------------------

    static final int MELEE_TICKS = 300;     // 15 s per fight: approach + a dozen swings at 0.5/s
    static final float DUMMY_HP = 5000f;

    /** One diagnostic fight: attackers (side 0) and targets (side 1), and what was seen of side 0. */
    static final class MeleeFight {
        final String unit, scenario;
        final int x0, z0;
        final List<Mob> attackers = new ArrayList<>(), targets = new ArrayList<>();
        int swings, hits, retargets, firstHit = -1, outOfReachTicks, targetedTicks;
        float damage;
        final Map<Mob, LivingEntity> lastTarget = new HashMap<>();
        final Map<Mob, Integer> lastSwing = new HashMap<>();
        long intervalSum;
        int intervals;

        MeleeFight(String unit, String scenario, int x0, int z0) {
            this.unit = unit;
            this.scenario = scenario;
            this.x0 = x0;
            this.z0 = z0;
        }
    }

    static Mob meleeBody(ServerLevel level, EntityType<? extends Mob> type, String owner, double x, double z, float yaw) {
        Mob e = type.create(level);
        if (!(e instanceof Unit u) || !(e instanceof AttackerUnit)) {
            if (e != null)
                e.discard();
            return null;
        }
        e.moveTo(x, PAD_Y + 1, z, yaw, 0);
        u.setOwnerName(owner);
        var hp = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null)
            hp.setBaseValue(DUMMY_HP);
        e.setHealth(DUMMY_HP);
        if (!level.addFreshEntity(e)) {
            e.discard();
            return null;
        }
        return e;
    }

    @GameTest(template = ARENA, timeoutTicks = START_DELAY + MELEE_TICKS + 200)
    public static void melee_diagnostics_swing_rate_damage_and_retargets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        int baseChunkX = (origin.getX() + MELEE_OFFSET_X) >> 4, baseChunkZ = (origin.getZ() + 3000) >> 4;
        var t1 = types(EntityRegistrar.VINDICATOR_UNIT, EntityRegistrar.ZOMBIE_UNIT, EntityRegistrar.BRUTE_UNIT,
            EntityRegistrar.LEAFBLADE_UNIT, EntityRegistrar.REEF_GUARD_UNIT, EntityRegistrar.CUTLASS_RAIDER_UNIT);
        String[] scenarios = { "1 on dummy", "3 on dummy", "5v5 scrum" };
        List<MeleeFight> fights = new ArrayList<>();
        List<long[]> chunks = new ArrayList<>();
        int i = 0;
        for (var type : t1)
            for (String sc : scenarios) {
                int cx = baseChunkX + (i % COLUMNS) * 4, cz = baseChunkZ + (i / COLUMNS) * 3;
                fights.add(new MeleeFight(name(type), sc, cx << 4, cz << 4));
                chunks.add(new long[] { cx, cz });
                chunks.add(new long[] { cx + 1, cz });
                i++;
            }
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "melee pad");   // bounded, see runDuels
        for (MeleeFight f : fights)
            buildPad(level, f.x0, f.z0);
        List<String> errors = new ArrayList<>();

        helper.runAfterDelay(START_DELAY, () -> {
            int k = 0;
            for (var type : t1)
                for (int s = 0; s < scenarios.length; s++, k++) {
                    MeleeFight f = fights.get(k);
                    String own = "gametest_melee_" + k + "_a", foe = "gametest_melee_" + k + "_b";
                    double cx = f.x0 + 16, cz = f.z0 + 8;
                    int nAtk = s == 0 ? 1 : s == 1 ? 3 : 5, nTgt = s == 2 ? 5 : 1;
                    for (int n = 0; n < nAtk; n++) {
                        Mob m = meleeBody(level, type.get(), own, cx - 3, cz - (nAtk - 1) * 0.65 + n * 1.3, -90);
                        if (m == null) errors.add("could not make " + f.unit);
                        else f.attackers.add(m);
                    }
                    for (int n = 0; n < nTgt; n++) {
                        Mob m = meleeBody(level, EntityRegistrar.VINDICATOR_UNIT.get(), foe, cx + 3, cz - (nTgt - 1) * 0.65 + n * 1.3, 90);
                        if (m == null) { errors.add("could not make a dummy"); continue; }
                        if (s < 2)
                            m.setNoAi(true);   // a passive dummy: never moves, never strikes back
                        f.targets.add(m);
                    }
                }
        });
        helper.runAfterDelay(START_DELAY + 2, () -> {
            for (MeleeFight f : fights) {
                if (f.targets.isEmpty())
                    continue;
                for (Mob m : f.attackers)
                    if (f.scenario.startsWith("5v5")) ((AttackerUnit) m).setAttackMoveTarget(centroid(f.targets, f.x0 + 16, f.z0 + 8));
                    else ((AttackerUnit) m).setUnitAttackTargetForced(f.targets.get(0));
                if (f.scenario.startsWith("5v5"))
                    for (Mob m : f.targets)
                        ((AttackerUnit) m).setAttackMoveTarget(centroid(f.attackers, f.x0 + 16, f.z0 + 8));
            }
        });
        for (int t = 3; t <= MELEE_TICKS; t++) {
            final int tick = t;
            helper.runAfterDelay(START_DELAY + t, () -> {
                for (MeleeFight f : fights) {
                    for (Mob m : f.targets) {
                        // the max-health attribute does not survive the unit's spawn setup (it falls back to the
                        // unit's own), so measure against whatever the max is - a top-up every tick still keeps it alive
                        float lost = m.getMaxHealth() - m.getHealth();
                        if (lost > 0.001f) {
                            f.damage += lost;
                            f.hits++;
                            if (f.firstHit < 0)
                                f.firstHit = tick;
                        }
                        m.setHealth(m.getMaxHealth());
                    }
                    for (Mob m : f.attackers) {
                        m.setHealth(m.getMaxHealth());
                        // LivingEntity.swing sets swingTime to -1 on the server; the next aiStep moves it on, so -1
                        // here (the test ticks after the level) means a swing started this tick
                        if (m.swinging && m.swingTime == -1) {
                            f.swings++;
                            Integer prev = f.lastSwing.put(m, tick);
                            if (prev != null) {
                                f.intervalSum += tick - prev;
                                f.intervals++;
                            }
                        }
                        LivingEntity tgt = m.getTarget();
                        LivingEntity was = f.lastTarget.get(m);
                        if (tgt != null && was != null && tgt != was)
                            f.retargets++;
                        if (tgt != null) {
                            f.lastTarget.put(m, tgt);
                            f.targetedTicks++;
                            double reach = m.getBbWidth() * 2.0 * m.getBbWidth() * 2.0 + tgt.getBbWidth();
                            if (m.distanceToSqr(tgt) > reach)
                                f.outOfReachTicks++;
                        }
                    }
                    // the scrum: keep both sides in the fight, as the duel's bot does once a second
                    if (f.scenario.startsWith("5v5") && tick % 20 == 0) {
                        for (Mob m : f.attackers)
                            if (((Unit) m).isIdle()) ((AttackerUnit) m).setAttackMoveTarget(centroid(f.targets, f.x0 + 16, f.z0 + 8));
                        for (Mob m : f.targets)
                            if (((Unit) m).isIdle()) ((AttackerUnit) m).setAttackMoveTarget(centroid(f.attackers, f.x0 + 16, f.z0 + 8));
                    }
                }
            });
        }
        helper.runAfterDelay(START_DELAY + MELEE_TICKS + 5, () -> {
            for (MeleeFight f : fights) {
                if (f.attackers.isEmpty())
                    continue;
                AttackerUnit au = (AttackerUnit) f.attackers.get(0);
                int n = f.attackers.size();
                float secs = (MELEE_TICKS - Math.max(0, f.firstHit)) / 20f;   // rates over the window after first contact
                float nominal = au.getUnitAttackDamage();
                ReignOfNether.LOGGER.info(String.format(Locale.ROOT,
                    "[Melee] %s %s: nominal %.1f dmg every %.0ft; per unit %.2f swings/s, %.2f dmg/s; %.2f dmg/swing (%.0f%% of nominal); "
                        + "hit ticks %d; swing gap %.1ft; first hit t%d; retargets %d; out of reach %.0f%% of targeted ticks",
                    f.unit, f.scenario, nominal, au.getAttackCooldown(), f.swings / (float) n / Math.max(1f, secs),
                    f.damage / n / Math.max(1f, secs), f.damage / Math.max(1, f.swings),
                    100f * f.damage / Math.max(1, f.swings) / Math.max(0.01f, nominal), f.hits,
                    f.intervals == 0 ? 0f : f.intervalSum / (float) f.intervals, f.firstHit, f.retargets,
                    100f * f.outOfReachTicks / Math.max(1, f.targetedTicks)));
                for (Mob m : f.attackers) m.discard();
                for (Mob m : f.targets) m.discard();
            }
            GameTestChunks.releaseAll(level, chunks);
            if (!errors.isEmpty())
                helper.fail(String.join("; ", errors));
            helper.succeed();
        });
    }

    /**
     * A unit's held weapon must not add melee damage its stat sheet doesn't show (AttackerUnit.cosmeticWeapon). Fails
     * for the units whose "visual only" weapon used to add +5/+8 per hit; every other attacker whose default mainhand
     * still adds melee damage is listed in a "[Melee]" line for review (some may mean it).
     */
    @GameTest(template = ARENA)
    public static void cosmetic_weapons_add_no_melee_damage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> listed = new ArrayList<>(), failed = new ArrayList<>();
        var mustBeZero = List.of(EntityRegistrar.LEAFBLADE_UNIT.getId(), EntityRegistrar.REEF_GUARD_UNIT.getId(),
            EntityRegistrar.CUTLASS_RAIDER_UNIT.getId(), EntityRegistrar.VINDICATOR_UNIT.getId());
        for (var ro : EntityRegistrar.ENTITIES.getEntries()) {
            var e = ro.get().create(level);
            if (e == null)
                continue;
            if (e instanceof AttackerUnit au && e instanceof Unit u
                && !(e instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit)) {
                try {
                    u.setupEquipmentAndUpgradesServer();   // outside the world, owner "": the no-research loadout
                } catch (RuntimeException ex) {
                    listed.add(ro.getId().getPath() + " (setup threw " + ex.getClass().getSimpleName() + ")");
                }
                double bonus = AttackerUnit.getWeaponDamageModifier(au);
                if (bonus != 0) {
                    listed.add(ro.getId().getPath() + " +" + bonus);
                    if (mustBeZero.contains(ro.getId()))
                        failed.add(ro.getId().getPath() + " +" + bonus);
                }
            }
            e.discard();
        }
        ReignOfNether.LOGGER.info("[Melee] default weapons that add melee damage: " + (listed.isEmpty() ? "none" : String.join(", ", listed)));
        if (!failed.isEmpty())
            helper.fail("a visual-only weapon adds melee damage per hit: " + String.join(", ", failed));
        helper.succeed();
    }

    /**
     * Focus fire is additive (LivingEntityMixin.unitHitsStack): three units hitting one target in the same tick each
     * land their full hit, one unit can't hit twice in one tick, and non-unit damage (fire, fall) keeps vanilla's hurt
     * window. Everything happens inside this one tick, with no-AI bodies discarded at the end, so no neighbouring test
     * ever sees them.
     */
    @GameTest(template = ARENA)
    public static void focus_fire_hits_stack_but_one_attacker_cannot_double_hit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        List<Mob> all = new ArrayList<>();
        try {
            Mob[] atk = new Mob[4];
            for (int n = 0; n < atk.length; n++) {
                atk[n] = EntityRegistrar.ZOMBIE_UNIT.get().create(level);
                if (atk[n] == null) { helper.fail("could not create a Zombie"); return; }
                atk[n].moveTo(at.getX() + 0.5 - 2, at.getY(), at.getZ() + 0.5 + n - 1.5, -90, 0);
                ((Unit) atk[n]).setOwnerName("gametest_focus_a");
                atk[n].setNoAi(true);
                level.addFreshEntity(atk[n]);
                all.add(atk[n]);
            }
            Mob[] dummy = new Mob[2];
            for (int n = 0; n < dummy.length; n++) {
                dummy[n] = EntityRegistrar.VINDICATOR_UNIT.get().create(level);
                if (dummy[n] == null) { helper.fail("could not create a dummy"); return; }
                dummy[n].moveTo(at.getX() + 0.5 + 1, at.getY(), at.getZ() + 0.5 + n * 3 - 1.5, 90, 0);
                ((Unit) dummy[n]).setOwnerName("gametest_focus_b");
                dummy[n].setNoAi(true);
                level.addFreshEntity(dummy[n]);
                all.add(dummy[n]);
            }
            var src = level.damageSources();
            List<String> errors = new ArrayList<>();

            // three different attackers, one tick: every hit lands, the total is the sum
            Mob d = dummy[0];
            d.invulnerableTime = 0;
            float hp0 = d.getHealth();
            d.hurt(src.mobAttack(atk[0]), 4f);
            float one = hp0 - d.getHealth();
            if (one <= 0)
                errors.add("the first hit did no damage");
            float hp1 = d.getHealth();
            d.hurt(src.mobAttack(atk[0]), 4f);
            if (d.getHealth() < hp1)
                errors.add("one attacker hit twice in one tick (" + (hp1 - d.getHealth()) + " more)");
            d.hurt(src.mobAttack(atk[1]), 4f);
            d.hurt(src.mobAttack(atk[2]), 4f);
            float total = hp0 - d.getHealth();
            if (Math.abs(total - 3 * one) > 0.01f)
                errors.add("three attackers in one tick dealt " + total + ", expected 3 x " + one);
            // a unit-owned projectile in the same tick lands too
            float hp2 = d.getHealth();
            d.hurt(src.thrown(atk[3], atk[3]), 4f);
            if (d.getHealth() >= hp2)
                errors.add("a fourth attacker's projectile was swallowed by the hurt window");

            // non-unit damage: vanilla immunity unchanged
            Mob e = dummy[1];
            e.invulnerableTime = 0;
            float f0 = e.getHealth();
            e.hurt(src.onFire(), 1f);
            float f1 = e.getHealth();
            if (f1 >= f0)
                errors.add("fire did no damage on an open window");
            e.hurt(src.onFire(), 1f);
            if (e.getHealth() < f1)
                errors.add("fire hit twice inside the hurt window");
            float f2 = e.getHealth();
            e.hurt(src.mobAttack(atk[0]), 4f);   // a unit's hit still lands after fire...
            float f3 = e.getHealth();
            if (f3 >= f2)
                errors.add("a unit's hit was swallowed by fire's hurt window");
            e.hurt(src.fall(), 1f);              // ...and fall damage right after it is still blocked
            if (e.getHealth() < f3)
                errors.add("fall damage landed inside the hurt window");
            e.hurt(src.onFire(), 1f);
            if (e.getHealth() < f3)
                errors.add("fire landed inside the hurt window after a unit's hit");

            if (!errors.isEmpty())
                helper.fail(String.join("; ", errors));
            else
                helper.succeed();
        } finally {
            for (Mob m : all)
                m.discard();
        }
    }

    static String groupText(Group g) {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < g.types().size(); t++) {
            if (g.counts().get(t) == 0)
                continue;
            if (sb.length() > 0)
                sb.append('+');
            sb.append(g.counts().get(t)).append(' ').append(name(g.types().get(t)));
        }
        return sb.toString();
    }

    /** run-gametest/duel_results.json (read by tools/duel_summary.py in CI) and one "[Duel]" log line per duel. */
    static void writeResults(List<Duel> duels, List<String> errors, double wallSeconds, String resultFile) {
        JsonArray arr = new JsonArray();
        for (Duel d : duels) {
            JsonObject o = new JsonObject();
            o.addProperty("tier", d.spec.tier());
            o.addProperty("a", d.spec.a().label());
            o.addProperty("b", d.spec.b().label());
            o.addProperty("groupA", groupText(d.spec.a()));
            o.addProperty("groupB", groupText(d.spec.b()));
            o.addProperty("rep", d.spec.rep());
            o.addProperty("metalA", Math.round(d.startMetal[0]));
            o.addProperty("metalB", Math.round(d.startMetal[1]));
            o.addProperty("valueA", Math.round(d.value[0]));
            o.addProperty("valueB", Math.round(d.value[1]));
            o.addProperty("winner", d.winner);
            o.addProperty("timeout", d.timeout);
            o.addProperty("seconds", d.ticks / 20f);
            o.addProperty("ran", d.engaged);
            o.addProperty("risenA", d.risen.get(0).size());
            o.addProperty("risenB", d.risen.get(1).size());
            arr.add(o);
            ReignOfNether.LOGGER.info(String.format(Locale.ROOT, "[Duel] %s %s (%s, %.0fm) v %s (%s, %.0fm) rep %d: winner %s%s, keep %.0f/%.0f metal, %.1fs, risen %d/%d",
                d.spec.tier(), d.spec.a().label(), groupText(d.spec.a()), d.startMetal[0], d.spec.b().label(), groupText(d.spec.b()),
                d.startMetal[1], d.spec.rep(), d.winner, d.timeout ? " (time)" : "", d.value[0], d.value[1], d.ticks / 20f,
                d.risen.get(0).size(), d.risen.get(1).size()));
        }
        JsonObject root = new JsonObject();
        root.addProperty("maxSeconds", MAX_TICKS / 20);
        root.addProperty("wallSeconds", Math.round(wallSeconds * 10) / 10.0);
        root.add("duels", arr);
        JsonArray errs = new JsonArray();
        for (String e : errors)
            errs.add(e);
        root.add("errors", errs);
        try {
            Files.writeString(Path.of(resultFile), new GsonBuilder().setPrettyPrinting().create().toJson(root),
                StandardCharsets.UTF_8);
        } catch (Exception ex) {
            ReignOfNether.LOGGER.error("[Duel] could not write " + resultFile, ex);
        }
    }
}
