package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.CapturedSpark;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
                    + " from the target, mode " + s.mode() + ", danger steps " + s.kites + ", an enemy hurt " + hurt
                    + ", alive " + medic.isAlive();
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
        // on hold from its first tick: the vindicator stands within 4 from the start, so a HOLD order given once the
        // units tick came after the step (CI run 39718993567: "the holder 2.75 -> 2.88 (1 steps)")
        ((Unit) holder).setHoldPosition(true);
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

    // ------------------------------------------------------------------ cast before kiting

    /**
     * Cast before kiting (lovish, Oct 29): a support caster an enemy comes for gets one autocast look before its danger
     * step - the step is a move order, and the AutoCaster leaves a unit on a move order alone until it ends. A Spark
     * Sage with 10 essence, an enemy put 2 blocks off and coming for it: it sparks the enemy (30) before it has moved a
     * block, then still steps away. The enemy comes 3 ticks after the Sage's AutoCaster slot, so the once-a-second
     * look is 17 ticks off and the Sage's next think (at most 4 ticks) decides.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 240)
    public static void chased_spark_sage_casts_before_its_danger_step(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "chased"), theirs = mine + "_foe";
        Mob sage = HeroGameTests.spawnUnit(helper, EntityRegistrar.SPARK_SAGE_UNIT.get(), mine, 10, 8);
        Mob foe = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, 1, 1);   // 11.4 off: no danger yet
        for (Mob m : new Mob[] { sage, foe })
            if (m != null)
                made.add(m);
        if (made.size() != 2) {
            cleanUp(made);
            helper.fail("could not spawn the Spark Sage and the vindicator");
            return;
        }
        foe.setNoAi(true);
        AutocastState.setAll((Unit) foe, false);
        int[] came = { -1 }, sparkAt = { -1 };
        double[] spot = new double[2];
        double[] movedAtSpark = { -1 };
        float[] hp0 = { 0 };
        boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            int t = (int) helper.getTick();
            if (came[0] < 0) {
                boolean ticking = sage.tickCount >= ArenaReady.TICKS && foe.tickCount >= ArenaReady.TICKS;
                if (!ticking || Math.floorMod(level.getGameTime() - sage.getId(), 20) != 3) {
                    if (t < 120)
                        return;
                    done[0] = true;
                    try {
                        helper.fail("the units were not ready after " + t + " ticks: " + ArenaReady.describe(level, made));
                    } finally {
                        cleanUp(made);
                    }
                    return;
                }
                foe.teleportTo(sage.getX() - 2, sage.getY(), sage.getZ());
                ReactionGameTests.aim(foe, sage);
                Essence.add(sage, CapturedSpark.ESSENCE_COST);
                UnitGrid.invalidate();
                spot[0] = sage.getX();
                spot[1] = sage.getZ();
                hp0[0] = foe.getHealth();
                came[0] = t;
                return;
            }
            if (sparkAt[0] < 0 && hp0[0] - foe.getHealth() > 1f) {
                sparkAt[0] = t;
                movedAtSpark[0] = flat(sage, spot[0], spot[1]);
            }
            if ((sparkAt[0] < 0 || state(sage).kites < 1) && t < came[0] + 60)
                return;
            done[0] = true;
            try {
                List<String> problems = new ArrayList<>();
                if (sparkAt[0] < 0)
                    problems.add("the chased Sage never sparked the enemy (" + state(sage).kites + " danger steps)");
                else if (sparkAt[0] - came[0] > 8 || movedAtSpark[0] > 1.0)
                    problems.add("the Sage sparked only after its step: " + (sparkAt[0] - came[0]) + " ticks after the enemy came, "
                        + String.format("%.2f", movedAtSpark[0]) + " blocks from its spot");
                else if (Math.abs(hp0[0] - foe.getHealth() - CapturedSpark.DAMAGE) > 0.01f)
                    problems.add("the spark took " + (hp0[0] - foe.getHealth()) + ", expected " + CapturedSpark.DAMAGE);
                if (state(sage).kites < 1)
                    problems.add("the Sage did not step away after its cast");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                cleanUp(made);
            }
        });
    }

    // ------------------------------------------------------------------ E: the smarter healer (lovish, Oct 30)

    /**
     * lovish, Oct 30: "heal low hp units not full hp units". A medic with three friends in reach, each on its own (no
     * splash reaches two of them): an Iron Golem at 65% (42 of 120 missing, and a costly unit), a vindicator at 50%
     * (37.5 of 75 missing) and a full vindicator. Her first heal goes to the vindicator, the lowest on health; the old
     * score (missing health x cost) sent it to the golem - more health missing, whatever the golem's cost.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
    public static void healer_heals_the_lowest_hp_fraction_first(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 272);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "lowest");
        ResearchServerEvents.addResearch(mine, ProductionItems.RESEARCH_HEALING_POTIONS);
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 8.5, 8.5, made);
        Mob golem = spawn(level, EntityRegistrar.IRON_GOLEM_UNIT.get(), mine, p, 8.5, 2.5, made);
        Mob low = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 8.5, 14.5, made);
        Mob full = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 14.5, 8.5, made);
        if (medic == null || golem == null || low == null || full == null) {
            cleanUp(made, mine);
            helper.fail("could not create the medic and her three friends");
            return;
        }
        for (Mob m : List.of(golem, low, full)) {
            m.setNoAi(true);
            AutocastState.setAll((Unit) m, false);
        }
        int[] start = { -1 }, firstAt = { -1 };
        float[] hp = new float[3];
        double[] first = new double[2];
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            golem.setHealth(golem.getMaxHealth() * 0.65f);
            low.setHealth(low.getMaxHealth() * 0.5f);
            hp[0] = golem.getHealth();
            hp[1] = low.getHealth();
            hp[2] = full.getHealth();
            start[0] = tick;
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            SupportState s = state(medic);
            if (firstAt[0] < 0 && s.heals + s.areaHeals > 0) {
                firstAt[0] = t;
                first[0] = s.lastCastX;
                first[1] = s.lastCastZ;
            }
            if (firstAt[0] < 0 && t < 300)
                return;
            if (firstAt[0] >= 0 && t < firstAt[0] + 40 && t < 300)
                return;   // let the first splash land
            done[0] = true;
            try {
                String where = "after " + t + " ticks: heals " + s.heals + " + area " + s.areaHeals + ", first at tick "
                    + firstAt[0] + " aimed " + f(first[0] - p.getX()) + " / " + f(first[1] - p.getZ())
                    + ", hp: golem " + f(hp[0]) + " -> " + f(golem.getHealth()) + ", low vindicator " + f(hp[1]) + " -> "
                    + f(low.getHealth()) + ", full vindicator " + f(hp[2]) + " -> " + f(full.getHealth());
                ReignOfNether.LOGGER.info("[SupportAI gametest] lowest first: {}", where);
                List<String> problems = new ArrayList<>();
                if (firstAt[0] < 0) {
                    problems.add("the medic threw no heal at all");
                } else {
                    double toLow = Math.hypot(first[0] - low.getX(), first[1] - low.getZ());
                    double toGolem = Math.hypot(first[0] - golem.getX(), first[1] - golem.getZ());
                    if (toLow > 2.0)
                        problems.add("her first heal was not aimed at the vindicator at 50% (" + f(toLow) + " off it, "
                            + f(toGolem) + " off the golem at 65%)");
                }
                if (low.getHealth() <= hp[1])
                    problems.add("the vindicator at 50% was not healed");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems) + " (" + where + ")");
            } finally {
                cleanUp(made, mine);
            }
        });
    }

    /** A glass floor {@code lenX} blocks long and 16 across at height y, put back when the test ends: room for a march. */
    static BlockPos longPad(GameTestHelper helper, int y, int lenX) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        TestCleanup.BlockUndo pad = TestCleanup.blocks(helper);
        for (int dx = 0; dx < lenX; dx++)
            for (int dz = 0; dz < 16; dz++) {
                pad.set(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState());
                for (int dy = 1; dy <= 6; dy++)
                    pad.set(new BlockPos(corner.getX() + dx, y + dy, corner.getZ() + dz), Blocks.AIR.defaultBlockState());
            }
        return new BlockPos(corner.getX(), y + 1, corner.getZ());
    }

    /**
     * lovish, Oct 30: "stay behind units". Four slow vindicators and a medic 4 blocks ahead of them get one Fight order
     * at an enemy line 18 blocks off (three vindicators, standing, unkillable so the fight lasts), and the click is past
     * it. While the army marches up she must keep behind it: at least 2 blocks further from the enemy than our nearest
     * vindicator in 80% of the looks, never within the danger radius of an enemy, and still behind the front once it
     * fights. The old Fight walked her straight to her rear-rank spot by the click, ahead of the army and into the
     * enemy, because "the front" only counted once a vindicator was within 6 of an enemy.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 1200)
    public static void healer_stays_behind_a_marching_front(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = longPad(helper, 284, 40);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "march"), theirs = mine + "_foe";
        List<Mob> line = new ArrayList<>(), foes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Mob v = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 10.5, 5.5 + i * 2, made);
            if (v != null)
                line.add(v);
        }
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 14.5, 8.5, made);
        for (int i = 0; i < 3; i++) {
            Mob foe = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 28.5, 6.5 + i * 2, made);
            if (foe != null)
                foes.add(foe);
        }
        if (line.size() != 4 || foes.size() != 3 || medic == null) {
            cleanUp(made);
            helper.fail("could not create the army, the medic and the enemy line");
            return;
        }
        for (Mob v : line)
            ReactionGameTests.speedTo(v, 0.1);   // a slow march: the test watches the medic, not a race
        for (Mob foe : foes) {
            foe.setNoAi(true);
            AutocastState.setAll((Unit) foe, false);
            foe.addEffect(new MobEffectInstance(MobEffectRegistrar.INVINCIBLE.get(), 1200));
        }
        double startX = p.getX() + 10.5, foeX = p.getX() + 28.5;
        int[] start = { -1 }, samples = { 0 }, behind = { 0 }, engagedAt = { -1 };
        double[] closest = { Double.MAX_VALUE };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            start[0] = tick;
            Mob[] all = new Mob[line.size() + 1];
            for (int i = 0; i < line.size(); i++)
                all[i] = line.get(i);
            all[line.size()] = medic;
            order(level, mine, UnitAction.ATTACK_MOVE, -1, p.offset(34, -1, 8), all);
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            double dMe = Double.MAX_VALUE, dFront = Double.MAX_VALUE, headX = -Double.MAX_VALUE;
            for (Mob foe : foes) {
                dMe = Math.min(dMe, flat(medic, foe));
                for (Mob v : line)
                    dFront = Math.min(dFront, flat(v, foe));
            }
            for (Mob v : line)
                headX = Math.max(headX, v.getX());
            closest[0] = Math.min(closest[0], dMe);
            // the march: the army is on its way (5 blocks on) and not yet at the enemy (5 off)
            if (t % 2 == 0 && headX >= startX + 5 && headX <= foeX - 5) {
                samples[0]++;
                if (dMe >= dFront + 2.0)
                    behind[0]++;
            }
            if (engagedAt[0] < 0 && dFront <= 2.5)
                engagedAt[0] = t;
            if ((engagedAt[0] < 0 || t < engagedAt[0] + 60) && t < 900)
                return;
            done[0] = true;
            try {
                String where = "after " + t + " ticks: behind the front in " + behind[0] + " of " + samples[0]
                    + " looks on the march, closest to an enemy " + f(closest[0]) + ", now " + f(dMe) + " from the enemy (front "
                    + f(dFront) + "), medic at x " + f(medic.getX() - p.getX()) + ", army head at x " + f(headX - p.getX())
                    + ", fight from tick " + engagedAt[0] + ", mode " + state(medic).mode() + ", danger steps " + state(medic).kites;
                // "gametest" in the line: ci_gametests.sh prints only lines matching GameTest|gametest|fail|error, and
                // the margin over the 80% bar is what a green run should show
                ReignOfNether.LOGGER.info("[SupportAI gametest] marching front: {} - {}% behind, the bar is 80%", where,
                    samples[0] == 0 ? "-" : f(100.0 * behind[0] / samples[0]));
                List<String> problems = new ArrayList<>();
                if (engagedAt[0] < 0)
                    problems.add("the army never reached the enemy line");
                if (samples[0] < 8)
                    problems.add("the march gave only " + samples[0] + " looks to judge");
                else if (behind[0] < samples[0] * 0.8)
                    problems.add("the medic was behind the front in only " + behind[0] + " of " + samples[0] + " looks on the march");
                if (closest[0] < SupportBrain.DANGER_RADIUS)
                    problems.add("the medic came within " + f(closest[0]) + " blocks of an enemy");
                if (engagedAt[0] >= 0 && dMe < dFront + 2.0)
                    problems.add("the medic is not behind the front in the fight");
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
     * Bots walk every support unit with the army (the Field Medic was missing from the old hand list, and its medics
     * idled at the lab). The set comes from the registry, so a support unit added later (the Soul Engineer, the Mender
     * Angel) is checked without a list here to keep up; a fighter must not be in it.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 300)
    public static void bot_support_units_follow_the_army(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // every unit type the mod registers, made (never added to the world) and asked: the ones that are no fighter
        // and walk with a bot's army are each support unit, the Seer, the Lantern Fortress and each essence caster
        List<String> problems = new ArrayList<>();
        Set<String> follow = new TreeSet<>();
        int supports = 0;
        for (var entry : EntityRegistrar.ENTITIES.getEntries()) {
            Entity e;
            try {
                e = entry.get().create(level);
            } catch (RuntimeException ex) {
                continue;   // a type that can't be made outside the world is no unit a bot trains
            }
            if (e == null)
                continue;
            try {
                if (!(e instanceof LivingEntity le) || !(e instanceof Unit))
                    continue;
                String name = entry.getId().getPath();
                boolean want = e instanceof SupportUnit || e instanceof com.solegendary.reignofnether.unit.units.storm.SeerUnit
                    || e instanceof com.solegendary.reignofnether.unit.units.storm.LanternFortressUnit
                    || e instanceof com.solegendary.reignofnether.essence.EssenceCarrier;
                if (e instanceof SupportUnit)
                    supports++;
                if (want)
                    follow.add(name);
                if (BotPlayer.followsArmy(le) != want)
                    problems.add(name + (want ? " does not follow a bot's army"
                        : " follows a bot's army but is no support unit, Seer, Lantern Fortress or essence caster"));
            } finally {
                e.discard();
            }
        }
        for (String must : List.of("witch_unit", "soul_engineer_unit", "mender_angel_unit", "spark_sage_unit"))
            if (!follow.contains(must))
                problems.add(must + " is not among the units that follow a bot's army " + follow);
        if (supports < 8)
            problems.add("only " + supports + " support unit types found: the registry walk missed some");
        ReignOfNether.LOGGER.info("[SupportAI gametest] bot follow set: {} support types, {}", supports, follow);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ------------------------------------------------------------------ E2: review round 1 (Oct 30)

    /**
     * A lone Field Medic Fight-ordered across the field walks past friends standing about (an idle vindicator 10 blocks
     * on along her way, a villager beside it) to her spot, and the order ends there. Review r1: the march waited at
     * "the head less the standoff" for any friendly fighter in the look, so she stopped for good behind units that were
     * not marching, with her order still on (queued orders waited too).
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 900)
    public static void fight_order_alone_walks_past_idle_friends_to_its_spot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = longPad(helper, 216, 40);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "alone");
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 2.5, 8.5, made);
        Mob idle = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 12.5, 9.5, made);
        Mob worker = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), mine, p, 14.5, 11.5, made);
        if (medic == null || idle == null || worker == null) {
            cleanUp(made, mine);
            helper.fail("could not create the medic and the idle friends");
            return;
        }
        for (Mob m : List.of(idle, worker)) {
            m.setNoAi(true);
            AutocastState.setAll((Unit) m, false);
        }
        int[] start = { -1 }, endedAt = { -1 };
        BlockPos[] dest = { null };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            start[0] = tick;
            order(level, mine, UnitAction.ATTACK_MOVE, -1, p.offset(30, -1, 8), medic);
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            SupportState s = state(medic);
            if (s.mode() == SupportState.Mode.SUPPORT_MOVE)
                dest[0] = s.destination();
            else if (dest[0] != null && endedAt[0] < 0)
                endedAt[0] = t;
            if (endedAt[0] < 0 && t < 700)
                return;
            done[0] = true;
            try {
                double off = dest[0] == null ? -1 : flat(medic, dest[0].getX() + 0.5, dest[0].getZ() + 0.5);
                String where = "after " + t + " ticks: mode " + s.mode() + ", order ended at tick " + endedAt[0] + ", medic at x "
                    + f(medic.getX() - p.getX()) + " z " + f(medic.getZ() - p.getZ()) + ", spot "
                    + (dest[0] == null ? "none" : (dest[0].getX() - p.getX()) + " / " + (dest[0].getZ() - p.getZ())) + " (" + f(off)
                    + " off it), idle vindicator at x " + f(idle.getX() - p.getX());
                ReignOfNether.LOGGER.info("[SupportAI gametest] alone: {}", where);
                List<String> problems = new ArrayList<>();
                if (dest[0] == null)
                    problems.add("the medic never took the Fight order");
                else if (endedAt[0] < 0)
                    problems.add("the medic's Fight order never ended: she stopped on the way");
                else if (off > SupportBrain.ARRIVE + 1.5)
                    problems.add("the order ended " + f(off) + " blocks short of her spot");
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
     * lovish, Oct 30 (r1 answer: "Add Follow"): a Field Medic on Follow behind a vindicator that fights holds her standoff
     * behind it, out of reach of the enemy, and is still on Follow afterwards. The old follow walked her to the
     * vindicator's block every second, into the fight. The enemy stands still and can't die; the medic's autocast is off
     * (this watches where she stands, not what she throws).
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 900)
    public static void healer_on_follow_holds_behind_a_fighting_front(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = longPad(helper, 242, 30);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "followfront"), theirs = mine + "_foe";
        Mob leader = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 8.5, 8.5, made);
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 4.5, 8.5, made);
        Mob foe = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 18.5, 8.5, made);
        if (leader == null || medic == null || foe == null) {
            cleanUp(made, mine);
            helper.fail("could not create the vindicator, its medic and the enemy");
            return;
        }
        foe.setNoAi(true);
        AutocastState.setAll((Unit) foe, false);
        AutocastState.setAll((Unit) medic, false);
        foe.addEffect(new MobEffectInstance(MobEffectRegistrar.INVINCIBLE.get(), 1200));
        int[] start = { -1 }, engagedAt = { -1 }, samples = { 0 }, behind = { 0 };
        double[] closest = { Double.MAX_VALUE };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            start[0] = tick;
            order(level, mine, UnitAction.FOLLOW, leader.getId(), leader.blockPosition(), medic);
            order(level, mine, UnitAction.ATTACK, foe.getId(), foe.blockPosition(), leader);
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            double dMe = flat(medic, foe), dFront = flat(leader, foe);
            if (engagedAt[0] < 0 && dFront <= 2.5)
                engagedAt[0] = t;
            // from 40 ticks into the fight (time to take her spot) on
            if (engagedAt[0] >= 0 && t >= engagedAt[0] + 40 && t % 2 == 0) {
                samples[0]++;
                closest[0] = Math.min(closest[0], dMe);
                if (dMe >= dFront + 4.5)
                    behind[0]++;
            }
            if ((engagedAt[0] < 0 || t < engagedAt[0] + 200) && t < 700)
                return;
            done[0] = true;
            try {
                String where = "after " + t + " ticks: fight from tick " + engagedAt[0] + ", behind the vindicator in " + behind[0]
                    + " of " + samples[0] + " looks (" + (samples[0] == 0 ? "-" : f(100.0 * behind[0] / samples[0]))
                    + "%, the bar is 80%), closest to the enemy " + f(closest[0]) + ", now " + f(dMe) + " (vindicator " + f(dFront)
                    + "), following " + (((Unit) medic).getFollowTarget() == leader) + ", danger steps " + state(medic).kites;
                ReignOfNether.LOGGER.info("[SupportAI gametest] follow front: {}", where);
                List<String> problems = new ArrayList<>();
                if (engagedAt[0] < 0)
                    problems.add("the vindicator never reached the enemy");
                else if (samples[0] < 40)
                    problems.add("the fight gave only " + samples[0] + " looks to judge");
                else if (behind[0] < samples[0] * 0.8)
                    problems.add("the medic was her standoff behind the vindicator in only " + behind[0] + " of " + samples[0] + " looks");
                if (samples[0] > 0 && closest[0] < SupportBrain.DANGER_RADIUS + 0.5)
                    problems.add("the medic came within " + f(closest[0]) + " blocks of the enemy");
                if (((Unit) medic).getFollowTarget() != leader)
                    problems.add("the medic is no longer on Follow");
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
     * lovish, Oct 30 (r1 answer: "Only full health"): a friend just below full is healed - an Iron Golem at 95% (6 of 120
     * missing), with a full vindicator beside the medic that is not. The 90% cut of the first fix left it untouched.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 500)
    public static void healer_heals_a_friend_just_below_full(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = ArenaReady.pad(helper, 313);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "nearfull");
        ResearchServerEvents.addResearch(mine, ProductionItems.RESEARCH_HEALING_POTIONS);
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 8.5, 8.5, made);
        Mob golem = spawn(level, EntityRegistrar.IRON_GOLEM_UNIT.get(), mine, p, 8.5, 3.5, made);
        Mob full = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 8.5, 13.5, made);
        if (medic == null || golem == null || full == null) {
            cleanUp(made, mine);
            helper.fail("could not create the medic and her two friends");
            return;
        }
        for (Mob m : List.of(golem, full)) {
            m.setNoAi(true);
            AutocastState.setAll((Unit) m, false);
        }
        int[] start = { -1 }, firstAt = { -1 };
        float[] hp = new float[1];
        double[] first = new double[2];
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            golem.setHealth(golem.getMaxHealth() * 0.95f);
            hp[0] = golem.getHealth();
            start[0] = tick;
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            SupportState s = state(medic);
            if (firstAt[0] < 0 && s.heals + s.areaHeals > 0) {
                firstAt[0] = t;
                first[0] = s.lastCastX;
                first[1] = s.lastCastZ;
            }
            if (firstAt[0] < 0 && t < 300)
                return;
            if (firstAt[0] >= 0 && t < firstAt[0] + 40 && t < 300)
                return;   // let the splash land
            done[0] = true;
            try {
                String where = "after " + t + " ticks: heals " + s.heals + " + area " + s.areaHeals + ", first at tick " + firstAt[0]
                    + ", golem hp " + f(hp[0]) + " -> " + f(golem.getHealth()) + " of " + f(golem.getMaxHealth());
                ReignOfNether.LOGGER.info("[SupportAI gametest] near full: {}", where);
                List<String> problems = new ArrayList<>();
                if (firstAt[0] < 0)
                    problems.add("the medic threw no heal at the golem at 95%");
                else if (Math.hypot(first[0] - golem.getX(), first[1] - golem.getZ()) > 2.0)
                    problems.add("her first heal was not aimed at the golem at 95% ("
                        + f(Math.hypot(first[0] - full.getX(), first[1] - full.getZ())) + " off the full vindicator)");
                if (golem.getHealth() <= hp[0])
                    problems.add("the golem at 95% was not healed");
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
     * Review r2: on Follow the medic holds behind her leader's fight, not anyone's. Her vindicator and a second one fight
     * an enemy that can't die; once she holds behind them, her leader is moved 28 blocks back while the second fights
     * on. She follows him out (main's follow did; r2's first hold kept her behind the other's fight until it ended):
     * once he has reached his spot (or 160 ticks on) she is within 6 of him, well off her standoff spot by the fight.
     * Review r3: he walks back through that spot, so "near him" alone also passed while she still held there.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 1200)
    public static void healer_on_follow_leaves_a_fight_her_leader_left(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = longPad(helper, 296, 40);
        List<Entity> made = new ArrayList<>();
        String mine = owner(helper, "followleave"), theirs = mine + "_foe";
        Mob leader = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 24.5, 7.5, made);
        Mob other = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), mine, p, 24.5, 9.5, made);
        Mob medic = spawn(level, EntityRegistrar.WITCH_UNIT.get(), mine, p, 20.5, 8.5, made);
        Mob foe = spawn(level, EntityRegistrar.VINDICATOR_UNIT.get(), theirs, p, 34.5, 8.5, made);
        if (leader == null || other == null || medic == null || foe == null) {
            cleanUp(made, mine);
            helper.fail("could not create the two vindicators, the medic and the enemy");
            return;
        }
        foe.setNoAi(true);
        AutocastState.setAll((Unit) foe, false);
        AutocastState.setAll((Unit) medic, false);
        foe.addEffect(new MobEffectInstance(MobEffectRegistrar.INVINCIBLE.get(), 2400));
        int[] start = { -1 }, engagedAt = { -1 }, heldFor = { 0 }, leftAt = { -1 }, rejoinedAt = { -1 };
        double[] otherGap = { 0 };
        boolean[] done = { false };
        ArenaReady.whenTicking(helper, made, 150, tick -> {
            start[0] = tick;
            order(level, mine, UnitAction.FOLLOW, leader.getId(), leader.blockPosition(), medic);
            order(level, mine, UnitAction.ATTACK, foe.getId(), foe.blockPosition(), leader, other);
        });
        helper.onEachTick(() -> {
            if (start[0] < 0 || done[0])
                return;
            int t = (int) (helper.getTick() - start[0]);
            double dFront = Math.min(flat(leader, foe), flat(other, foe));
            if (engagedAt[0] < 0 && dFront <= 2.5)
                engagedAt[0] = t;
            if (leftAt[0] < 0) {
                // she holds behind the fight: 20 looks in a row her standoff behind it
                if (engagedAt[0] >= 0 && flat(medic, foe) >= dFront + 4.5)
                    heldFor[0]++;
                else
                    heldFor[0] = 0;
                if (heldFor[0] >= 20 || (engagedAt[0] >= 0 && t >= engagedAt[0] + 200)) {
                    leftAt[0] = t;
                    order(level, mine, UnitAction.MOVE, -1, p.offset(4, -1, 8), leader);
                }
            } else if (rejoinedAt[0] < 0 && t >= leftAt[0] + 40
                    && (flat(leader, p.getX() + 4.5, p.getZ() + 8.5) <= 2.5 || t >= leftAt[0] + 160)
                    && medic.getX() < p.getX() + 12 && flat(medic, leader) <= 6.0) {
                rejoinedAt[0] = t;
                otherGap[0] = flat(other, foe);
            }
            if ((leftAt[0] < 0 || (rejoinedAt[0] < 0 && t < leftAt[0] + 300)) && t < 900)
                return;
            done[0] = true;
            try {
                String where = "after " + t + " ticks: fight from tick " + engagedAt[0] + ", held behind it " + heldFor[0]
                    + " looks, leader moved off at tick " + leftAt[0] + ", medic back by him at tick " + rejoinedAt[0]
                    + ", medic now " + f(flat(medic, leader)) + " from him (leader at x " + f(leader.getX() - p.getX())
                    + ", medic at x " + f(medic.getX() - p.getX()) + "), the other vindicator " + f(rejoinedAt[0] >= 0 ? otherGap[0]
                    : flat(other, foe)) + " from the enemy, following " + (((Unit) medic).getFollowTarget() == leader);
                ReignOfNether.LOGGER.info("[SupportAI gametest] follow leaves: {}", where);
                List<String> problems = new ArrayList<>();
                if (engagedAt[0] < 0)
                    problems.add("the vindicators never reached the enemy");
                else if (leftAt[0] < 0)
                    problems.add("the leader was never moved off");
                else if (rejoinedAt[0] < 0)
                    problems.add("the medic did not follow her leader out of the fight within 300 ticks (once he was"
                        + " at his spot, within 6 of him and short of x 12)");
                else if (otherGap[0] > 3.0)
                    problems.add("the other vindicator was no longer fighting (" + f(otherGap[0]) + " from the enemy)");
                if (((Unit) medic).getFollowTarget() != leader)
                    problems.add("the medic is no longer on Follow");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems) + " (" + where + ")");
            } finally {
                cleanUp(made, mine);
            }
        });
    }
}
