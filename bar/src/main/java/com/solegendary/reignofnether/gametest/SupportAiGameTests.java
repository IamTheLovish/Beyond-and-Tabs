package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.command.CommandQueues;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.interfaces.SupportUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.support.PotionDelivery;
import com.solegendary.reignofnether.unit.support.SupportBrain;
import com.solegendary.reignofnether.unit.support.SupportState;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The healer AI (claude/healer-ai-oct21.md, F; lovish Oct 21 20:52: healers "don't work well with the attack command
 * and end up killing themselves; they also throw all their potions, including the harmful ones, onto allies"). Each
 * test stands on its own glass floor in its own arena column (ArenaReady.pad) at a height no other test of this batch
 * uses, 12 apart, in its own batch so no other test's units are its neighbours; units that must stay put have no AI.
 * Orders go through CommandQueues like a player's, once every unit really ticks (ArenaReady.whenTicking). The
 * support counters (SupportState) say what a healer threw; diagnostics go out as "[SupportAI]" lines. Everything is
 * discarded and every research granted is taken back in a finally. The maths behind the choices is unit-tested
 * without a game in bar/src/test/.../unit/support/SupportMathTest.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SupportAiGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "support_ai";

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_support_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, String owner, BlockPos p, double x, double z, List<Entity> made) {
        return ReactionGameTests.spawn(level, type, owner, p.getX() + x, p.getY(), p.getZ() + z, made);
    }

    static void order(ServerLevel level, String owner, UnitAction action, int targetId, BlockPos pos, Mob... units) {
        int[] ids = new int[units.length];
        for (int i = 0; i < units.length; i++)
            ids[i] = units[i].getId();
        CommandQueues.issue(level, owner, action, targetId, ids, pos, BlockPos.ZERO, QueueMode.REPLACE);
    }

    static SupportState state(Mob m) {
        return ((SupportUnit) m).getSupportState();
    }

    static double flat(Entity a, Entity b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    static double flat(Entity a, double x, double z) {
        return Math.hypot(a.getX() - x, a.getZ() - z);
    }

    static void cleanUp(List<Entity> made, String... owners) {
        for (Entity e : made) {
            if (e instanceof Mob m)
                CommandQueues.clear(m.getId());
            if (!e.isRemoved())
                e.discard();
        }
        for (String o : owners)
            ResearchServerEvents.removeAllResearchFor(o);
    }

    static String f(double d) {
        return ReactionGameTests.f(d);
    }

    // ------------------------------------------------------------------ A: orders

    /**
     * Attack on an enemy 10 blocks off with two more beside it (all three standing still): the Field Medic throws
     * harming at it from range and never comes within 4 blocks of any of them - the old order walked her onto it.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
    public static void attack_order_keeps_medic_at_standoff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 140);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "atk"), theirs = mine + "_foe";
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 2.5, 8.5, made);
        Mob a = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 12.5, 8.5, made);
        Mob b = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 13.5, 6.5, made);
        Mob c = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 13.5, 10.5, made);
        if (medic == null || a == null || b == null || c == null) {
            cleanUp(made);
            helper.fail("could not create the medic and the three vindicators");
            return;
        }
        List<Mob> foes = List.of(a, b, c);
        for (Mob foe : foes)
            foe.setNoAi(true);
        int[] start = { -1 };
        double[] closest = { Double.MAX_VALUE };
        boolean[] held = { false }, done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            start[0] = tick;
            order(level, mine, UnitAction.ATTACK, a.getId(), a.blockPosition(), medic);
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            for (Mob foe : foes)
                closest[0] = Math.min(closest[0], flat(medic, foe));
            if (state(medic).mode() == SupportState.Mode.STANDOFF_UNIT)
                held[0] = true;
            if (helper.getTick() < start[0] + 200)
                return;
            done[0] = true;
            try {
                SupportState s = state(medic);
                boolean hurt = false;
                for (Mob foe : foes)
                    hurt |= foe.getHealth() < foe.getMaxHealth();
                String where = "harming throws " + s.harms + ", closest " + f(closest[0]) + " blocks, now " + f(flat(medic, a))
                    + " from the target, mode " + s.mode() + ", an enemy hurt " + hurt + ", alive " + medic.isAlive();
                ReignOfNether.LOGGER.info("[SupportAI] attack order: {}", where);
                List<String> problems = new ArrayList<>();
                if (!held[0])
                    problems.add("the attack order did not become a standoff");
                if (s.harms < 1)
                    problems.add("the medic cast no harm at the target");
                if (closest[0] < 4.0)
                    problems.add("the medic came within 4 blocks of an enemy melee unit");
                if (!hurt)
                    problems.add("no enemy was hurt: the cloud did not land on them");
                if (!medic.isAlive())
                    problems.add("the medic died");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems) + " (" + where + ")");
            } finally {
                cleanUp(made);
            }
        });
    }

    /**
     * Fight with six hurt vindicators and two medics: the medics heal on the way, and they end in the rear rank, behind
     * every vindicator of the order - the old Fight walked them to the click with the front line.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 700)
    public static void fight_order_puts_medics_in_rear_rank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 152);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "fight");
        ResearchServerEvents.addResearch(mine, ProductionItems.RESEARCH_HEALING_POTIONS);
        List<Mob> line = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Mob v = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 2.5 + (i % 3), 6.5 + (i / 3) * 3, made);
            if (v != null)
                line.add(v);
        }
        Mob m1 = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 3.5, 4.5, made);
        Mob m2 = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 3.5, 11.5, made);
        if (line.size() != 6 || m1 == null || m2 == null) {
            cleanUp(made, mine);
            helper.fail("could not create the vindicators and medics");
            return;
        }
        BlockPos click = p.offset(13, -1, 8);
        int[] start = { -1 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            for (Mob v : line)
                v.setHealth(v.getMaxHealth() * 0.5f);
            start[0] = tick;
            Mob[] all = new Mob[8];
            for (int i = 0; i < 6; i++)
                all[i] = line.get(i);
            all[6] = m1;
            all[7] = m2;
            order(level, mine, UnitAction.ATTACK_MOVE, -1, click, all);
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            boolean arrived = state(m1).isIdle() && state(m2).isIdle() && m1.getNavigation().isDone() && m2.getNavigation().isDone();
            double frontX = Double.MAX_VALUE;
            for (Mob v : line)
                frontX = Math.min(frontX, v.getX());
            boolean behind = m1.getX() < frontX - 2.0 && m2.getX() < frontX - 2.0;
            int heals = state(m1).healsWhileOrdered + state(m2).healsWhileOrdered;
            if ((t < 120 || !arrived || !behind || heals < 1) && t < 500)
                return;
            done[0] = true;
            try {
                String where = "after " + t + " ticks: medics at x " + f(m1.getX() - p.getX()) + " / " + f(m2.getX() - p.getX())
                    + ", rearmost vindicator at x " + f(frontX - p.getX()) + ", heals on the way " + heals
                    + " (all throws " + (state(m1).heals + state(m1).areaHeals) + " + " + (state(m2).heals + state(m2).areaHeals)
                    + "), medic modes " + state(m1).mode() + " / " + state(m2).mode() + ", spread " + f(flat(m1, m2));
                ReignOfNether.LOGGER.info("[SupportAI] fight order: {}", where);
                List<String> problems = new ArrayList<>();
                if (!behind)
                    problems.add("the medics did not end behind every vindicator");
                if (heals < 1)
                    problems.add("no medic healed while it moved under the Fight order");
                if (flat(m1, m2) < 1.0)
                    problems.add("the medics stacked on one spot instead of a rank");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems) + " (" + where + ")");
            } finally {
                cleanUp(made, mine);
            }
        });
    }

    /**
     * Fight and attack orders for every other support unit (Moonwell Bearer, Bloom Priestess, Tide Priest, Ink Scribe):
     * each, sent with a fighter at an enemy 7 blocks off, escorts the fighter from behind and never comes within 4 blocks
     * of the enemy - the old order made it follow the enemy into melee.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
    public static void support_units_escort_from_behind_on_attack_orders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 164);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "escort"), theirs = mine + "_foe";
        List<EntityType<? extends Mob>> kinds = new ArrayList<>();
        kinds.add(EntityRegistrar.MOONWELL_BEARER_UNIT.get());
        kinds.add(EntityRegistrar.BLOOM_PRIESTESS_UNIT.get());
        kinds.add(EntityRegistrar.TIDE_PRIEST_UNIT.get());
        kinds.add(EntityRegistrar.INK_SCRIBE_UNIT.get());
        List<Mob> supports = new ArrayList<>(), fighters = new ArrayList<>(), foes = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            // the fighter starts 7 off its target, already within reach of a fight: the escort's spot behind it
            // (reach - 1.5, at most 6.5) is on this 16-block floor from the first look
            double z = 1.5 + i * 4;
            Mob s = spawn(level, kinds.get(i), mine, p, 4.5, z, made);
            Mob fighter = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 6.5, z, made);
            Mob foe = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 13.5, z, made);
            if (s == null || fighter == null || foe == null)
                continue;
            supports.add(s);
            fighters.add(fighter);
            foes.add(foe);
        }
        if (supports.size() != kinds.size()) {
            cleanUp(made);
            helper.fail("could not create the support units, fighters and targets");
            return;
        }
        for (Mob foe : foes) {
            foe.setNoAi(true);
            foe.addEffect(new MobEffectInstance(MobEffectRegistrar.INVINCIBLE.get(), 1200));   // the fight lasts the test
        }
        for (Mob s : supports)
            AutocastState.setAll((Unit) s, false);   // no tidepool under the fight, no seal: only the order moves them
        int n = supports.size();
        double[] closest = new double[n];
        boolean[] escorted = new boolean[n];
        java.util.Arrays.fill(closest, Double.MAX_VALUE);
        int[] start = { -1 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            start[0] = tick;
            for (int i = 0; i < n; i++)
                order(level, mine, UnitAction.ATTACK, foes.get(i).getId(), foes.get(i).blockPosition(), fighters.get(i), supports.get(i));
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            for (int i = 0; i < n; i++) {
                closest[i] = Math.min(closest[i], flat(supports.get(i), foes.get(i)));
                if (state(supports.get(i)).mode() == SupportState.Mode.ESCORT)
                    escorted[i] = true;
            }
            if (helper.getTick() < start[0] + 180)
                return;
            done[0] = true;
            try {
                List<String> problems = new ArrayList<>();
                StringBuilder where = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    Mob s = supports.get(i), fighter = fighters.get(i), foe = foes.get(i);
                    String name = EntityType.getKey(s.getType()).getPath();
                    where.append(name).append(": closest ").append(f(closest[i])).append(", now ").append(f(flat(s, foe)))
                        .append(" (fighter ").append(f(flat(fighter, foe))).append("), escorted ").append(escorted[i]).append("; ");
                    if (!escorted[i])
                        problems.add(name + " never escorted its fighter");
                    if (closest[i] < 4.0)
                        problems.add(name + " came within 4 blocks of the enemy");
                    if (flat(s, foe) < flat(fighter, foe) + 2.0)
                        problems.add(name + " is not behind its fighter");
                }
                ReignOfNether.LOGGER.info("[SupportAI] escorts: {}", where);
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems) + " (" + where + ")");
            } finally {
                cleanUp(made);
            }
        });
    }

    // ------------------------------------------------------------------ B: heal while moving, step out of danger

    /** A medic following a hurt fighter on the march heals it - the old autocast waited for her to be fully idle. */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
    public static void healers_heal_while_following(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 176);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "follow");
        ResearchServerEvents.addResearch(mine, ProductionItems.RESEARCH_HEALING_POTIONS);
        Mob leader = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 2.5, 8.5, made);
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 1.5, 5.5, made);
        if (leader == null || medic == null) {
            cleanUp(made, mine);
            helper.fail("could not create the vindicator and the medic");
            return;
        }
        ReactionGameTests.speedTo(leader, 0.15);   // a slow march, so the test watches the heal and not a race
        int[] start = { -1 };
        float[] hp = { 0 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            leader.setHealth(leader.getMaxHealth() * 0.4f);
            hp[0] = leader.getHealth();
            start[0] = tick;
            order(level, mine, UnitAction.FOLLOW, leader.getId(), leader.blockPosition(), medic);
            order(level, mine, UnitAction.MOVE, -1, p.offset(13, -1, 8), leader);
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            SupportState s = state(medic);
            boolean healed = leader.getHealth() > hp[0] + 0.5f;
            if ((s.healsWhileOrdered < 1 || !healed) && t < 300)
                return;
            done[0] = true;
            try {
                String where = "after " + t + " ticks: heals while following " + s.healsWhileOrdered + " (all " + s.heals
                    + "), leader hp " + f(hp[0]) + " -> " + f(leader.getHealth()) + ", following " + (((Unit) medic).getFollowTarget() == leader);
                ReignOfNether.LOGGER.info("[SupportAI] follow: {}", where);
                if (s.healsWhileOrdered < 1)
                    helper.fail("the medic threw no heal while following: " + where);
                else if (!healed)
                    helper.fail("the heal did not reach the marching leader: " + where);
                else
                    helper.succeed();
            } finally {
                cleanUp(made, mine);
            }
        });
    }

    /**
     * A melee enemy closing on an idle medic (2.5 blocks, coming for her): she steps back, out of its reach. A second
     * medic on hold position in the same spot holds her ground (the step is off under hold).
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 300)
    public static void medic_steps_back_from_melee_but_not_on_hold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 188);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "kite"), theirs = mine + "_foe";
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 8.5, 12.5, made);
        Mob foe = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 6.0, 12.5, made);
        Mob holder = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 8.5, 3.5, made);
        Mob foe2 = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 6.0, 3.5, made);
        if (medic == null || foe == null || holder == null || foe2 == null) {
            cleanUp(made);
            helper.fail("could not create the medics and the vindicators");
            return;
        }
        foe.setNoAi(true);
        foe2.setNoAi(true);
        int[] start = { -1 };
        double[] from = new double[2];
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            order(level, mine, UnitAction.HOLD, -1, BlockPos.ZERO, holder);
            ReactionGameTests.aim(foe, medic);
            ReactionGameTests.aim(foe2, holder);
            from[0] = flat(medic, foe);
            from[1] = flat(holder, foe2);
            start[0] = tick;
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            if ((state(medic).kites < 1 || flat(medic, foe) < 4.5) && t < 80)
                return;
            done[0] = true;
            try {
                String where = "after " + t + " ticks: the medic " + f(from[0]) + " -> " + f(flat(medic, foe)) + " blocks off ("
                    + state(medic).kites + " steps), the holder " + f(from[1]) + " -> " + f(flat(holder, foe2)) + " ("
                    + state(holder).kites + " steps)";
                ReignOfNether.LOGGER.info("[SupportAI] danger step: {}", where);
                List<String> problems = new ArrayList<>();
                if (state(medic).kites < 1 || flat(medic, foe) < 4.5)
                    problems.add("the medic did not step out of the vindicator's reach");
                if (state(holder).kites > 0 || Math.abs(flat(holder, foe2) - from[1]) > 1.0)
                    problems.add("the medic on hold position stepped");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems) + " (" + where + ")");
            } finally {
                cleanUp(made);
            }
        });
    }

    // ------------------------------------------------------------------ C: friendly-fire-safe target scoring

    /**
     * An enemy in melee with our vindicator and a lone enemy 9 blocks from it, both within the medic's reach: her
     * autocast area harm goes to the lone enemy and hurts it, is never aimed within its radius of a friend, and the
     * friend in the brawl is never hurt.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 400)
    public static void harming_never_lands_on_allies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 248);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "harm"), theirs = mine + "_foe";
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 4.5, 8.5, made);
        Mob ally = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 10.5, 8.5, made);
        Mob brawler = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 11.5, 8.5, made);
        Mob lone = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 2.5, 3.5, made);
        if (medic == null || ally == null || brawler == null || lone == null) {
            cleanUp(made);
            helper.fail("could not create the medic, the ally and the two enemies");
            return;
        }
        for (Mob m : List.of(ally, brawler, lone))
            m.setNoAi(true);
        int[] start = { -1 }, casts = { 0 };
        boolean[] done = { false };
        List<String> problems = new ArrayList<>();
        double[] nearLone = { Double.MAX_VALUE };
        double radius = PotionDelivery.CLOUD_RADIUS;
        ArenaReady.whenTicking(helper, made, 150, tick -> start[0] = tick);
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            SupportState s = state(medic);
            if (s.harms > casts[0]) {   // a harm went off: where was it aimed?
                casts[0] = s.harms;
                double toAlly = flat(ally, s.lastCastX, s.lastCastZ), toMedic = flat(medic, s.lastCastX, s.lastCastZ);
                nearLone[0] = Math.min(nearLone[0], flat(lone, s.lastCastX, s.lastCastZ));
                ReignOfNether.LOGGER.info("[SupportAI] harm aimed at {} / {}: {} from the ally, {} from the medic, {} from the lone enemy",
                    f(s.lastCastX - p.getX()), f(s.lastCastZ - p.getZ()), f(toAlly), f(toMedic), f(flat(lone, s.lastCastX, s.lastCastZ)));
                if (toAlly < radius + 0.5)
                    problems.add("a harm was aimed " + f(toAlly) + " blocks from the ally");
                if (toMedic < radius + 0.5)
                    problems.add("a harm was aimed " + f(toMedic) + " blocks from the medic herself");
            }
            int t = (int) (helper.getTick() - start[0]);
            boolean landed = lone.getHealth() < lone.getMaxHealth();
            if ((casts[0] == 0 || !landed) && t < 200)
                return;
            if (t < 80)
                return;   // a little longer: a second harm, if any, must not go to the brawl either
            done[0] = true;
            try {
                String where = "after " + t + " ticks: harms " + casts[0] + ", nearest aim to the lone enemy " + f(nearLone[0])
                    + ", lone hp " + f(lone.getHealth()) + ", brawler hp " + f(brawler.getHealth())
                    + ", ally hp " + f(ally.getHealth()) + " / " + f(ally.getMaxHealth());
                ReignOfNether.LOGGER.info("[SupportAI] harming: {}", where);
                if (casts[0] == 0)
                    problems.add("no harm at all: the lone enemy should have had one");
                else if (nearLone[0] > radius)
                    problems.add("no harm was aimed on the lone enemy");
                if (!landed)
                    problems.add("the lone enemy was not hurt: the harm did not land");
                if (ally.getHealth() < ally.getMaxHealth())
                    problems.add("the ally in melee with the brawler was hurt");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems) + " (" + where + ")");
            } finally {
                cleanUp(made);
            }
        });
    }

    /**
     * Two hurt friends: the more hurt stands next to an undead friend (a Wither Skeleton - healing hurts it), the other
     * alone. The healing splash goes to the lone one, never next to the undead friend, and the undead friend is unhurt.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 400)
    public static void no_heal_splash_on_undead_ally(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 260);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "undead");
        ResearchServerEvents.addResearch(mine, ProductionItems.RESEARCH_HEALING_POTIONS);
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 4.5, 8.5, made);
        Mob byUndead = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 10.5, 8.5, made);
        Mob undead = spawn(level, EntityRegistrar.WITHER_SKELETON_UNIT.get(), mine, p, 11.5, 8.5, made);
        Mob alone = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 4.5, 2.5, made);
        if (medic == null || byUndead == null || undead == null || alone == null) {
            cleanUp(made, mine);
            helper.fail("could not create the medic and her three friends");
            return;
        }
        for (Mob m : List.of(byUndead, undead, alone)) {
            m.setNoAi(true);
            AutocastState.setAll((Unit) m, false);
        }
        int[] start = { -1 };
        float[] hp = new float[3];
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            byUndead.setHealth(byUndead.getMaxHealth() * 0.3f);
            alone.setHealth(alone.getMaxHealth() * 0.6f);
            hp[0] = byUndead.getHealth();
            hp[1] = alone.getHealth();
            hp[2] = undead.getHealth();
            start[0] = tick;
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            SupportState s = state(medic);
            if ((s.heals < 1 || alone.getHealth() <= hp[1]) && t < 200)
                return;
            if (t < 60)
                return;   // let a splash land fully before judging the undead friend
            done[0] = true;
            try {
                String where = "after " + t + " ticks: heals " + s.heals + ", last cast at " + f(s.lastCastX - p.getX())
                    + " / " + f(s.lastCastZ - p.getZ()) + ", hp: by the undead " + f(hp[0]) + " -> " + f(byUndead.getHealth())
                    + ", alone " + f(hp[1]) + " -> " + f(alone.getHealth()) + ", undead " + f(hp[2]) + " -> " + f(undead.getHealth());
                ReignOfNether.LOGGER.info("[SupportAI] undead ally: {}", where);
                List<String> problems = new ArrayList<>();
                if (s.heals < 1)
                    problems.add("the medic threw no heal at all");
                if (alone.getHealth() <= hp[1])
                    problems.add("the lone hurt friend was not healed");
                if (undead.getHealth() < hp[2])
                    problems.add("the undead friend was hurt by a healing splash");
                if (s.heals > 0 && Math.hypot(s.lastCastX - undead.getX(), s.lastCastZ - undead.getZ()) < PotionDelivery.SPLASH_RADIUS)
                    problems.add("a heal was aimed within its splash of the undead friend");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems) + " (" + where + ")");
            } finally {
                cleanUp(made, mine);
            }
        });
    }

    /** Guard against a refactor dropping the shared brain from a support unit (the five that have it). */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 20)
    public static void support_units_share_the_brain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        List<EntityType<? extends Mob>> kinds = new ArrayList<>();
        kinds.add(EntityRegistrar.WITCH_UNIT.get());
        kinds.add(EntityRegistrar.MOONWELL_BEARER_UNIT.get());
        kinds.add(EntityRegistrar.BLOOM_PRIESTESS_UNIT.get());
        kinds.add(EntityRegistrar.TIDE_PRIEST_UNIT.get());
        kinds.add(EntityRegistrar.INK_SCRIBE_UNIT.get());
        for (EntityType<? extends Mob> type : kinds) {
            Mob m = type.create(level);
            // a reach no bigger than the standoff margin + 1 would put the standoff on the front itself
            if (!(m instanceof SupportUnit su))
                problems.add(EntityType.getKey(type) + " is not a SupportUnit");
            else if (su.getSupportState() == null || su.getSupportReach() <= SupportBrain.STANDOFF_MARGIN + 1.0)
                problems.add(EntityType.getKey(type) + " has no support state or too short a reach");
            if (m != null)
                m.discard();
        }
        Mob medic = EntityRegistrar.WITCH_UNIT.get().create(level);
        if (!(medic instanceof WitchUnit w) || !w.hasSupportDamage())
            problems.add("the Field Medic does not count as a support unit with a harming throw");
        if (medic != null)
            medic.discard();
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

}
