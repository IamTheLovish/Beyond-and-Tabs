package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hero.wager.NeutralYap;
import com.solegendary.reignofnether.hero.wager.Phenomena;
import com.solegendary.reignofnether.hero.wager.Wager;
import com.solegendary.reignofnether.hero.wager.WagerClock;
import com.solegendary.reignofnether.hero.wager.WagerIntents;
import com.solegendary.reignofnether.hero.wager.WagerParser;
import com.solegendary.reignofnether.hero.wager.WagerState;
import com.solegendary.reignofnether.hero.wager.WagerBlocks;
import com.solegendary.reignofnether.hero.wager.WagerVerdict;
import com.solegendary.reignofnether.hero.wager.WagerWeather;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Lovish's Wager Book (hero-abilities-overhaul, "First prototype: the Wager Book"): the keyword parser maps every
 * intent, the verdict's odds follow level and ask size and replay from a seed, the fairness caps hold (one legendary
 * per hero a minute and per intent two minutes, damage / radius / duration ceilings), the cooldown, pages and ink are
 * paid as the design says, a refusal fizzles with a joke and costs only a page, and Lovish's yapping casts only
 * neutral effects. Every test name starts with "wager_" (flake-stress: test:wager_). The pure ones need no world; the
 * world ones own a 16x16 arena each with unique owners and assert inside one callback (no ticks pass mid-check).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WagerBookGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** One sample line per intent: lovish edits the keyword table, this keeps every intent reachable. */
    static final Map<String, String> SAMPLES = new LinkedHashMap<>();
    static {
        SAMPLES.put("rain", "make it rain on them");
        SAMPLES.put("sun", "clear sky");
        SAMPLES.put("night", "make it dark");
        SAMPLES.put("wind", "a gust of wind");
        SAMPLES.put("earth", "build a stone wall here");
        SAMPLES.put("fire", "set them on fire");
        SAMPLES.put("ice", "freeze them");
        SAMPLES.put("heal", "heal my units");
        SAMPLES.put("shield", "protect my guys");
        SAMPLES.put("speed", "make us faster");
        SAMPLES.put("strength", "hit harder");
        SAMPLES.put("summon", "summon some friends");
        SAMPLES.put("animal", "give me a wolf");
        SAMPLES.put("light", "reveal the enemy");
        SAMPLES.put("hide", "hide us in the fog");
        SAMPLES.put("teleport", "teleport me there");
        SAMPLES.put("money", "i want more gold");
        SAMPLES.put("trap", "set a trap");
        SAMPLES.put("fear", "scare them");
        SAMPLES.put("party", "lets dance");
        SAMPLES.put("nonsense", "asdf qwerty");
    }

    /** A Random whose nextDouble() plays a script (then 0.5), and nextInt() is always 0: forces a verdict in a test. */
    static final class Scripted extends Random {
        private final double[] script;
        private int next = 0;

        Scripted(double... script) {
            super(0);
            this.script = script;
        }

        @Override
        public double nextDouble() { return next < script.length ? script[next++] : 0.5; }

        @Override
        public int nextInt(int bound) { return 0; }
    }

    static String owner(GameTestHelper helper, String what) {
        return "gametest_wager_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ------------------------------------------------------------------------------------------------ parser (pure)

    /** Every intent in the JSON is reachable: its sample line parses to it, and every intent has a sample. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void wager_parser_maps_every_intent(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        WagerIntents table = WagerIntents.get();
        if (table.intents.size() < 20)
            problems.add("only " + table.intents.size() + " intents in the table (the design has about 20)");
        for (WagerIntents.Intent i : table.intents) {
            String sample = SAMPLES.get(i.id());
            if (sample == null) {
                problems.add("intent " + i.id() + " has no sample line in this test");
                continue;
            }
            WagerParser.Parsed p = WagerParser.parse(sample);
            if (p.intent() == null || !p.intent().id().equals(i.id()))
                problems.add("'" + sample + "' -> " + (p.intent() == null ? "null" : p.intent().id()) + ", expected " + i.id());
            if (!i.id().equals(WagerIntents.NONSENSE) && i.keywords().isEmpty())
                problems.add("intent " + i.id() + " has no keywords");
        }
        finish(helper, problems);
    }

    /**
     * The reading rules: the last non-empty line is the request, punctuation and case do not matter, size words move
     * the asked tier (clamped 1-4), a number of 10+ raises it and is kept as the quantity, a phrase beats a single word,
     * a tie goes to the smaller ask, and an empty or unreadable page is Nonsense.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void wager_parser_reads_the_last_line_size_words_and_ties(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int base = WagerIntents.get().defaultTier;
        if (!WagerParser.requestLine("my notes\nmore notes\n\nmake it rain\n  \n").equals("make it rain"))
            problems.add("requestLine took '" + WagerParser.requestLine("my notes\nmore notes\n\nmake it rain\n  \n") + "'");
        if (!WagerParser.normalise("  Make it RAIN!!! ...now?").equals("make it rain now"))
            problems.add("normalise gave '" + WagerParser.normalise("  Make it RAIN!!! ...now?") + "'");
        expect(problems, "notes about fire\nmake it RAIN!", "rain", base);
        expect(problems, "a little rain", "rain", Math.max(1, base - 1));
        expect(problems, "a huge storm", "rain", Math.min(4, base + 1));
        expect(problems, "a huge massive enormous storm", "rain", 4);
        expect(problems, "just a tiny little bit of rain", "rain", 1);
        expect(problems, "summon 12 soldiers", "summon", Math.min(4, base + 1));
        expect(problems, "heal my army", "heal", base);       // heal 1, summon 1: the smaller ask
        expect(problems, "run away", "fear", base);           // the phrase (2) beats speed's "run" (1)
        expect(problems, "it is raining", "rain", base);      // a 4+ letter keyword takes longer words
        expect(problems, "", WagerIntents.NONSENSE, base);
        expect(problems, "!!! ???", WagerIntents.NONSENSE, base);
        WagerParser.Parsed q = WagerParser.parse("summon 12 soldiers");
        if (q.quantity() != 12)
            problems.add("'summon 12 soldiers' kept quantity " + q.quantity());
        finish(helper, problems);
    }

    static void expect(List<String> problems, String page, String intent, int tier) {
        WagerParser.Parsed p = WagerParser.parse(page);
        String got = p.intent() == null ? "null" : p.intent().id();
        if (!got.equals(intent) || p.askedTier() != tier)
            problems.add("'" + page + "' -> " + got + " T" + p.askedTier() + ", expected " + intent + " T" + tier);
    }

    // ------------------------------------------------------------------------------------------------ verdict (pure)

    /**
     * The odds: the agree chance follows the formula (base + 5% a level - 10% a tier asked above T1 - 10% a tier above
     * the unlocked one - 15% if asked recently, clamped 5-95%), rises with level and falls with size; the twist chance
     * goes 15% -> 5% over ten levels (+10% above the unlocked tier); a seeded roll replays exactly; 20000 seeded rolls
     * agree as often as the chance says; the granted tier never passes the asked or the unlocked tier; the same intent
     * is never twisted twice in a row; cooldown, ink and pages follow the design's table.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void wager_verdict_odds_are_seeded_and_scale_with_level_and_size(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        WagerIntents.Intent rain = WagerIntents.get().byId("rain");
        if (rain == null) {
            helper.fail("no rain intent");
            return;
        }
        int[][] unlocked = { {1, 1}, {2, 2}, {3, 2}, {4, 3}, {6, 3}, {7, 4}, {10, 4} };
        for (int[] u : unlocked)
            if (WagerVerdict.unlockedTier(u[0]) != u[1])
                problems.add("unlockedTier(" + u[0] + ") = " + WagerVerdict.unlockedTier(u[0]) + ", expected " + u[1]);
        double b = rain.base();
        near(problems, "agree L2 T1", WagerVerdict.agreeChance(rain, 2, 1, false), b + 0.10);
        near(problems, "agree L2 T3", WagerVerdict.agreeChance(rain, 2, 3, false), b + 0.10 - 0.20 - 0.10);
        near(problems, "agree L10 T4", WagerVerdict.agreeChance(rain, 10, 4, false), Math.min(0.95, b + 0.50 - 0.30));
        near(problems, "agree L1 T4 (clamped)", WagerVerdict.agreeChance(rain, 1, 4, false), 0.05);
        near(problems, "agree L5 T2 asked recently", WagerVerdict.agreeChance(rain, 5, 2, true), b + 0.25 - 0.10 - 0.15);
        for (int tier = 1; tier <= 4; tier++)
            for (int level = 1; level < 10; level++)
                if (WagerVerdict.agreeChance(rain, level + 1, tier, false) < WagerVerdict.agreeChance(rain, level, tier, false))
                    problems.add("agree chance falls from level " + level + " to " + (level + 1) + " at T" + tier);
        for (int level = 1; level <= 10; level++)
            for (int tier = 1; tier < 4; tier++)
                if (WagerVerdict.agreeChance(rain, level, tier + 1, false) > WagerVerdict.agreeChance(rain, level, tier, false))
                    problems.add("a bigger ask (T" + (tier + 1) + ") is likelier at level " + level);
        near(problems, "twist L1 T1", WagerVerdict.twistChance(1, 1), 0.15);
        near(problems, "twist L10 T1", WagerVerdict.twistChance(10, 1), 0.05);
        near(problems, "twist L2 T3 (above unlocked)", WagerVerdict.twistChance(2, 3), 0.15 - 0.10 / 9 + 0.10);

        // seeded: the same seed gives the same verdicts
        Random a = new Random(42), c = new Random(42);
        for (int i = 0; i < 500; i++) {
            WagerVerdict.Result x = WagerVerdict.roll(rain, 5, 2, false, null, a);
            WagerVerdict.Result y = WagerVerdict.roll(rain, 5, 2, false, null, c);
            if (x.kind() != y.kind() || x.tier() != y.tier()) {
                problems.add("seed 42 diverged at roll " + i + ": " + x + " vs " + y);
                break;
            }
        }
        // the rate matches the chance; tiers stay under the caps
        Random r = new Random(7);
        int n = 20000, granted = 0;
        for (int i = 0; i < n; i++)
            if (WagerVerdict.roll(rain, 5, 2, false, null, r).kind() != WagerVerdict.Kind.REFUSE)
                granted++;
        double want = WagerVerdict.agreeChance(rain, 5, 2, false);
        if (want <= 0 || Math.abs(granted / (double) n - want) > 0.02)
            problems.add("L5 T2: " + granted + "/" + n + " granted, chance " + want);
        for (int level = 1; level <= 10; level++)
            for (int asked = 1; asked <= 4; asked++)
                for (int i = 0; i < 200; i++) {
                    WagerVerdict.Result v = WagerVerdict.roll(rain, level, asked, false, null, r);
                    if (v.kind() == WagerVerdict.Kind.REFUSE ? v.tier() != 0
                            : v.tier() < 1 || v.tier() > Math.min(asked, WagerVerdict.unlockedTier(level)))
                        problems.add("L" + level + " asked T" + asked + " got " + v);
                }
        // never the same twist twice in a row: a roll that would twist agrees instead
        WagerVerdict.Result again = WagerVerdict.roll(rain, 1, 1, false, "rain", new Scripted(0.0, 0.0, 0.0));
        if (again.kind() != WagerVerdict.Kind.AGREE)
            problems.add("rain was twisted twice in a row: " + again);
        WagerVerdict.Result twist = WagerVerdict.roll(rain, 1, 1, false, "fire", new Scripted(0.0, 0.0, 0.0));
        if (twist.kind() != WagerVerdict.Kind.TWIST)
            problems.add("a forced twist after another intent's twist did not twist: " + twist);
        // the design's table
        if (WagerVerdict.cooldownTicks(1) != 400 || WagerVerdict.cooldownTicks(10) != 130)
            problems.add("cooldown " + WagerVerdict.cooldownTicks(1) + " / " + WagerVerdict.cooldownTicks(10) + " ticks, expected 400 / 130");
        if (WagerVerdict.inkCost(1) != 10 || WagerVerdict.inkCost(2) != 25 || WagerVerdict.inkCost(3) != 40 || WagerVerdict.inkCost(4) != 60)
            problems.add("ink costs are not 10/25/40/60");
        if (WagerVerdict.maxPages(1) != 20 || WagerVerdict.maxPages(10) != 56)
            problems.add("pages " + WagerVerdict.maxPages(1) + " / " + WagerVerdict.maxPages(10) + ", expected 20 / 56");
        finish(helper, problems);
    }

    static void near(List<String> problems, String what, double got, double want) {
        if (Math.abs(got - want) > 1e-6)
            problems.add(what + " = " + got + ", expected " + want);
    }

    // ------------------------------------------------------------------------------------------------ fairness (pure)

    /**
     * No one-shotting a base: a legendary at most once a minute per hero and once per two minutes per intent across the
     * match (a refused T4 becomes a T3), and every phenomenon and twist in the table stays under the damage, radius and
     * duration ceilings (a damaging one within radius 6, a buff or reveal within 16).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void wager_fairness_caps_legendaries_damage_radius_and_duration(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        WagerClock clock = new WagerClock();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        if (clock.fairTier(a, "rain", 4, 0) != 4)
            problems.add("the first legendary was refused");
        clock.recordLegendary(a, "rain", 0);
        if (clock.fairTier(a, "fire", 4, 100) != 3)
            problems.add("hero A got a second legendary within a minute");
        if (clock.fairTier(b, "rain", 4, 100) != 3)
            problems.add("hero B chained the same intent's legendary within two minutes");
        if (clock.fairTier(b, "fire", 4, 100) != 4)
            problems.add("hero B's first legendary of another intent was refused");
        if (clock.fairTier(a, "fire", 4, WagerClock.HERO_T4_TICKS - 1) != 3 || clock.fairTier(a, "fire", 4, WagerClock.HERO_T4_TICKS) != 4)
            problems.add("the per-hero clock is not exactly " + WagerClock.HERO_T4_TICKS + " ticks");
        if (clock.fairTier(b, "rain", 4, WagerClock.INTENT_T4_TICKS - 1) != 3 || clock.fairTier(b, "rain", 4, WagerClock.INTENT_T4_TICKS) != 4)
            problems.add("the per-intent clock is not exactly " + WagerClock.INTENT_T4_TICKS + " ticks");
        if (clock.fairTier(a, "rain", 2, 1) != 2)
            problems.add("the clock touched a T2");
        if (WagerClock.HERO_T4_TICKS != 1200 || WagerClock.INTENT_T4_TICKS != 2400)
            problems.add("the legendary clocks are not 60 s / 120 s");

        int seen = 0;
        for (WagerIntents.Intent i : WagerIntents.get().intents) {
            if (i.id().equals(WagerIntents.NONSENSE))
                continue;
            for (int tier = 1; tier <= 4; tier++)
                for (boolean twist : new boolean[] { false, true }) {
                    Phenomena.Spec s = Phenomena.spec(i.id(), tier, twist);
                    if (s == null) {
                        problems.add("no phenomenon for " + i.id() + " T" + tier + (twist ? " twist" : ""));
                        continue;
                    }
                    seen++;
                    // damage phenomena obey the hero caps (50, radius 6); a buff or a reveal may reach MAX_AREA
                    if (s.damage() > Phenomena.MAX_DAMAGE || s.ticks() > Phenomena.MAX_TICKS || s.radius() > Phenomena.MAX_AREA
                            || (s.damage() > 0 && s.radius() > Phenomena.MAX_RADIUS))
                        problems.add(s + " passes a cap");
                    if (s.name() == null || s.name().isBlank())
                        problems.add(i.id() + " T" + tier + " has no name for the reply");
                }
        }
        if (Phenomena.MAX_DAMAGE != 50 || Phenomena.MAX_RADIUS != 6 || Phenomena.MAX_AREA > 16 || Phenomena.MAX_TICKS != 1800)
            problems.add("the caps are not 50 damage / radius 6 (16 for a buff) / 90 s");
        if (seen < 20 * 8)
            problems.add("only " + seen + " phenomena checked");
        finish(helper, problems);
    }

    // ------------------------------------------------------------------------------------------------ world

    /** A Dawn Paladin carrying the book, a friend and an enemy (no AI: nothing moves or fights during the check). */
    static Mob[] scene(GameTestHelper helper, String me, String them) {
        Mob hero = HeroGameTests.spawnHero(helper, HeroKits.DAWN_PALADIN, me, 2, 2);
        Mob friend = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), me, 3, 2);
        Mob enemy = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), them, 2, 6);
        if (friend != null)
            friend.setNoAi(true);
        if (enemy != null)
            enemy.setNoAi(true);
        return new Mob[] { hero, friend, enemy };
    }

    static void cleanup(Mob[] mobs, Resources pool) {
        for (Mob m : mobs)
            if (m != null)
                m.discard();
        if (pool != null)
            ResourcesServerEvents.resourcesList.remove(pool);
    }

    /**
     * The costs: a granted T1 wager spends a page and 10 ink (the hero's mana) and starts the 20-s cooldown; a second
     * wager inside the cooldown is turned away with nothing spent; a refused wager spends a page but no ink; an empty
     * book or an empty inkpot turns the wager away.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void wager_cooldown_pages_and_ink(GameTestHelper helper) {
        String me = owner(helper, "cost"), them = owner(helper, "cost_enemy");
        Mob[] s = scene(helper, me, them);
        if (!(s[0] instanceof FactionHero fh) || s[1] == null || s[2] == null) {
            helper.fail("could not spawn the scene");
            return;
        }
        Mob hero = s[0];
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            ServerLevel level = helper.getLevel();
            WagerClock clock = new WagerClock();
            s[1].setHealth(s[1].getMaxHealth() - 20);
            int pages = WagerState.of(hero).pages();
            if (pages != WagerVerdict.maxPages(1))
                problems.add("a fresh book has " + pages + " pages, expected " + WagerVerdict.maxPages(1));
            float ink = fh.getMana();
            long now = level.getGameTime();
            Wager.Result r = Wager.cast(level, hero, "a little heal", null, new Scripted(0.0, 0.99, 0.0), clock);
            if (r.outcome() != Wager.Outcome.CAST || r.verdict() == null || r.verdict().kind() != WagerVerdict.Kind.AGREE)
                problems.add("a forced agree did not cast: " + r);
            if (WagerState.of(hero).pages() != pages - 1)
                problems.add("pages " + pages + " -> " + WagerState.of(hero).pages() + " after a wager");
            if (Math.abs((ink - fh.getMana()) - WagerVerdict.inkCost(1)) > 1.5f)
                problems.add("a T1 wager cost " + (ink - fh.getMana()) + " ink, expected " + WagerVerdict.inkCost(1));
            if (WagerState.of(hero).cooldownUntil() - now != WagerVerdict.cooldownTicks(1))
                problems.add("cooldown " + (WagerState.of(hero).cooldownUntil() - now) + " ticks, expected " + WagerVerdict.cooldownTicks(1));

            float ink2 = fh.getMana();
            Wager.Result early = Wager.cast(level, hero, "a little heal", null, new Scripted(0.0, 0.99, 0.0), clock);
            if (early.outcome() != Wager.Outcome.COOLDOWN)
                problems.add("a wager inside the cooldown was " + early.outcome());
            if (WagerState.of(hero).pages() != pages - 1 || fh.getMana() < ink2 - 0.5f)
                problems.add("the turned-away wager spent a page or ink");

            WagerState.of(hero).setCooldownUntil(now);
            float ink3 = fh.getMana();
            Wager.Result no = Wager.cast(level, hero, "make it rain", null, new Scripted(0.999, 0.99), clock);
            if (no.outcome() != Wager.Outcome.CAST || no.verdict() == null || no.verdict().kind() != WagerVerdict.Kind.REFUSE)
                problems.add("a forced refusal did not come back as a refusal: " + no);
            if (WagerState.of(hero).pages() != pages - 2)
                problems.add("a refusal did not spend its page");
            if (fh.getMana() < ink3 - 0.5f)
                problems.add("a refusal spent ink: " + ink3 + " -> " + fh.getMana());

            WagerState.of(hero).setCooldownUntil(now);
            WagerState.of(hero).setPages(0);
            if (Wager.cast(level, hero, "a little heal", null, new Scripted(0.0, 0.99, 0.0), clock).outcome() != Wager.Outcome.NO_PAGES)
                problems.add("an empty book still cast");
            WagerState.of(hero).setPages(5);
            fh.setMana(0);
            if (Wager.cast(level, hero, "a little heal", null, new Scripted(0.0, 0.99, 0.0), clock).outcome() != Wager.Outcome.NO_INK)
                problems.add("an empty inkpot still cast");
            if (Wager.cast(level, s[1], "a little heal").outcome() != Wager.Outcome.NOT_A_HERO)
                problems.add("a plain unit could wager");
            cleanup(s, null);
            finish(helper, problems);
        });
    }

    /**
     * A refusal fizzles: nothing in the world changes (no one's health, no resources), the page holds the request and a
     * refusal line from the book's table, the tally counts it.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void wager_refusal_fizzles_with_a_joke_and_spends_only_a_page(GameTestHelper helper) {
        String me = owner(helper, "refuse"), them = owner(helper, "refuse_enemy");
        Mob[] s = scene(helper, me, them);
        if (!(s[0] instanceof FactionHero) || s[1] == null || s[2] == null) {
            helper.fail("could not spawn the scene");
            return;
        }
        Resources pool = new Resources(me, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        Mob hero = s[0];
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            ServerLevel level = helper.getLevel();
            float[] hp = { s[0].getHealth(), s[1].getHealth(), s[2].getHealth() };
            int metal = pool.ore, energy = pool.wood;
            Wager.Result r = Wager.cast(level, hero, "a huge storm of fire", null, new Scripted(0.999, 0.99), new WagerClock());
            if (r.verdict() == null || r.verdict().kind() != WagerVerdict.Kind.REFUSE)
                problems.add("the forced refusal came back as " + r);
            if (r.yapped())
                problems.add("Lovish yapped on a 0.99 yap roll (chance " + Wager.YAP_CHANCE + ")");
            for (int i = 0; i < 3; i++)
                if (s[i].getHealth() != hp[i])
                    problems.add("a refusal changed " + s[i].getName().getString() + "'s health " + hp[i] + " -> " + s[i].getHealth());
            if (pool.ore != metal || pool.wood != energy)
                problems.add("a refusal changed the owner's resources");
            List<String> jokes = new ArrayList<>(WagerIntents.get().lines("refuse"));
            jokes.addAll(WagerIntents.get().lines("refuse_low"));
            boolean joked = false;
            for (String j : jokes)
                if (r.reply().contains(j.replace("{level}", "1")))
                    joked = true;
            if (!joked)
                problems.add("the reply '" + r.reply() + "' has no refusal line");
            WagerState st = WagerState.of(hero);
            List<WagerState.Entry> pages = st.entries();
            if (pages.isEmpty() || !pages.get(pages.size() - 1).request().equals("a huge storm of fire")
                    || !pages.get(pages.size() - 1).reply().equals(r.reply()))
                problems.add("the page was not written back into the book: " + pages);
            if (st.asks() != 1 || st.refused() != 1 || st.granted() != 0)
                problems.add("tally asks " + st.asks() + " refused " + st.refused() + " granted " + st.granted());
            if (!st.tally().contains("Asks: 1"))
                problems.add("the margin tally reads '" + st.tally() + "'");
            cleanup(s, pool);
            finish(helper, problems);
        });
    }

    /**
     * Lovish yapping: a refusal with a low yap roll writes a monologue line and casts one neutral effect; every neutral
     * effect leaves everyone's health, the owner's resources and the number of entities exactly as they were.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void wager_yapping_casts_only_neutral_effects(GameTestHelper helper) {
        String me = owner(helper, "yap"), them = owner(helper, "yap_enemy");
        Mob[] s = scene(helper, me, them);
        if (!(s[0] instanceof FactionHero) || s[1] == null || s[2] == null) {
            helper.fail("could not spawn the scene");
            return;
        }
        Resources pool = new Resources(me, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        Mob hero = s[0];
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            ServerLevel level = helper.getLevel();
            AABB box = new AABB(helper.absolutePos(new BlockPos(-4, -2, -4)), helper.absolutePos(new BlockPos(20, 10, 20)));
            for (int i = 0; i < NeutralYap.EFFECTS.size(); i++) {
                float[] hp = { s[0].getHealth(), s[1].getHealth(), s[2].getHealth() };
                int metal = pool.ore, energy = pool.wood;
                int entities = level.getEntitiesOfClass(Entity.class, box).size();
                String id = NeutralYap.cast(level, hero, i);
                if (!NeutralYap.EFFECTS.get(i).equals(id))
                    problems.add("neutral effect " + i + " ran as '" + id + "'");
                for (int k = 0; k < 3; k++)
                    if (s[k].getHealth() != hp[k])
                        problems.add(id + " changed a unit's health");
                if (pool.ore != metal || pool.wood != energy)
                    problems.add(id + " changed the owner's resources");
                int after = level.getEntitiesOfClass(Entity.class, box).size();
                if (after != entities)
                    problems.add(id + " changed the entity count " + entities + " -> " + after);
            }
            Wager.Result r = Wager.cast(level, hero, "make it rain", null, new Scripted(0.999, 0.0), new WagerClock());
            if (!r.yapped() || !NeutralYap.EFFECTS.contains(r.neutralEffect()))
                problems.add("a refusal with a 0.0 yap roll did not yap: " + r);
            boolean monologue = false;
            for (String line : WagerIntents.get().lines("yap"))
                if (r.reply().contains(line))
                    monologue = true;
            if (!monologue)
                problems.add("the yapping reply '" + r.reply() + "' has no monologue line");
            cleanup(s, pool);
            finish(helper, problems);
        });
    }

    /**
     * A granted wager happens and is written down: "a little heal" heals the hurt friend and the reply names the
     * phenomenon; twisted ("granted, mostly"), the heal goes to the nearest enemy instead and the friend gets nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void wager_granted_heal_heals_and_its_twist_heals_the_enemy(GameTestHelper helper) {
        String me = owner(helper, "heal"), them = owner(helper, "heal_enemy");
        Mob[] s = scene(helper, me, them);
        if (!(s[0] instanceof FactionHero) || s[1] == null || s[2] == null) {
            helper.fail("could not spawn the scene");
            return;
        }
        Mob hero = s[0];
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            ServerLevel level = helper.getLevel();
            WagerClock clock = new WagerClock();
            s[1].setHealth(s[1].getMaxHealth() - 20);
            s[2].setHealth(s[2].getMaxHealth() - 20);
            float friend = s[1].getHealth(), enemy = s[2].getHealth();
            Wager.Result r = Wager.cast(level, hero, "a little heal", null, new Scripted(0.0, 0.99, 0.0), clock);
            if (r.verdict() == null || r.verdict().kind() != WagerVerdict.Kind.AGREE || r.verdict().tier() != 1)
                problems.add("the forced T1 agree came back as " + r);
            if (s[1].getHealth() <= friend)
                problems.add("the granted heal did not heal the friend (" + friend + " -> " + s[1].getHealth() + ")");
            if (s[2].getHealth() != enemy)
                problems.add("the granted heal healed the enemy");
            Phenomena.Spec spec = Phenomena.spec("heal", 1, false);
            if (spec == null || !r.phenomenon().equals(spec.name()) || !r.reply().contains(spec.name()))
                problems.add("the reply '" + r.reply() + "' does not name the phenomenon " + (spec == null ? "?" : spec.name()));

            WagerState.of(hero).setCooldownUntil(level.getGameTime());
            friend = s[1].getHealth();
            Wager.Result t = Wager.cast(level, hero, "a little heal", null, new Scripted(0.0, 0.0, 0.0), clock);
            if (t.verdict() == null || t.verdict().kind() != WagerVerdict.Kind.TWIST)
                problems.add("the forced twist came back as " + t);
            if (s[2].getHealth() <= enemy)
                problems.add("the twisted heal did not heal the nearest enemy");
            if (s[1].getHealth() != friend)
                problems.add("the twisted heal still healed the friend");
            if (WagerState.of(hero).twisted() != 1 || !WagerState.of(hero).tally().contains("Regretted: 1"))
                problems.add("the tally did not count the twist: " + WagerState.of(hero).tally());
            cleanup(s, null);
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------------ Mode A round 1

    /**
     * B1: wager weather ends on our own clock. The mod's load function turns doWeatherCycle off, so vanilla's rain timer
     * never runs out in a match: a 2-s rain from a wager must stop by itself (with the cycle off) and leave the sky as it
     * was, and it never brings vanilla thunder (whose lightning strikes across the map).
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void wager_weather_ends_on_its_own_clock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameRules.BooleanValue cycle = level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE);
        boolean cycleWas = cycle.get();
        cycle.set(false, level.getServer());
        // the level data's flags, not isRaining(): the visible rain level ramps up over ~20 ticks
        boolean rainWas = level.getLevelData().isRaining();
        long start = level.getGameTime();
        WagerWeather.start(level, true, 40);
        if (!level.getLevelData().isRaining() || level.getLevelData().isThundering()) {
            cycle.set(cycleWas, level.getServer());
            helper.fail("the wager rain did not start, or it brought thunder (raining " + level.getLevelData().isRaining()
                + ", thundering " + level.getLevelData().isThundering() + ")");
            return;
        }
        if (WagerWeather.until(level) != start + 40) {
            cycle.set(cycleWas, level.getServer());
            helper.fail("the wager weather's clock reads " + WagerWeather.until(level) + ", expected " + (start + 40));
            return;
        }
        helper.succeedWhen(() -> {
            long now = level.getGameTime();
            if (now < start + 45)
                helper.fail("waiting for the rain to end (" + (now - start) + " ticks)");
            boolean done = level.getLevelData().isRaining() == rainWas && !level.getLevelData().isThundering()
                && WagerWeather.until(level) < 0;
            if (!done && now < start + 150)
                helper.fail("still raining " + (now - start) + " ticks after a 40-tick wager");
            cycle.set(cycleWas, level.getServer());
            if (!done)
                helper.fail("the wager rain never ended with doWeatherCycle off (raining " + level.getLevelData().isRaining()
                    + ", clock " + WagerWeather.until(level) + ")");
        });
    }

    /**
     * B2: a temporary block (wall, mound, campfire) is only forgotten once it is actually gone: at its time with its chunk
     * unloaded it waits (block and entry stay) and goes when the chunk is back; a server stop keeps unloaded ones; the
     * list is saved (SavedData) so a restart still takes the block down.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void wager_temporary_blocks_wait_for_their_chunk_and_survive_a_restart(GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            List<String> problems = new ArrayList<>();
            ServerLevel level = helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(9, 2, 9));
            long until = level.getGameTime() + 100000;   // the real server sweep never reaches it during the test
            if (!WagerBlocks.place(level, pos, Blocks.COBBLESTONE.defaultBlockState(), until)) {
                helper.fail("could not place the test block at " + pos);
                return;
            }
            CompoundTag saved = WagerBlocks.saveTag();
            boolean listed = false;
            for (Tag t : saved.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) t;
                if (e.getInt("x") == pos.getX() && e.getInt("y") == pos.getY() && e.getInt("z") == pos.getZ()
                        && e.getLong("until") == until)
                    listed = true;
            }
            if (!listed)
                problems.add("the saved list has no entry for the block (a restart would leave it): " + saved);

            WagerBlocks.stop(level, p -> false);
            if (WagerBlocks.pendingAt(level, pos) != 1 || !level.getBlockState(pos).is(Blocks.COBBLESTONE))
                problems.add("a server stop with the chunk unloaded dropped the entry");
            WagerBlocks.sweep(level, until, p -> false);
            if (WagerBlocks.pendingAt(level, pos) != 1)
                problems.add("the entry was dropped at its time while its chunk was unloaded");
            WagerBlocks.sweep(level, until, p -> true);
            if (!level.getBlockState(pos).isAir())
                problems.add("the block is still there once its chunk is loaded");
            if (WagerBlocks.pendingAt(level, pos) != 0)
                problems.add("the entry stayed after the block came down");

            // a restart: the saved list comes back; the block is already gone, so the entry is simply dropped
            WagerBlocks.loadTag(level.getServer(), saved);
            if (WagerBlocks.pendingAt(level, pos) != 1)
                problems.add("the saved entry did not come back on load");
            WagerBlocks.sweep(level, until, p -> true);
            if (WagerBlocks.pendingAt(level, pos) != 0 || !level.getBlockState(pos).isAir())
                problems.add("the reloaded entry was not cleared");
            if (!level.getBlockState(pos).isAir())
                level.removeBlock(pos, false);
            finish(helper, problems);
        });
    }

    /**
     * Every phenomenon and twist casts without an error and answers with its name (Mode A note 9: only heal was cast
     * before). The weather ones are left out (global; B1 has its own test). Whatever they spawned is removed after.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void wager_every_phenomenon_casts_once(GameTestHelper helper) {
        String me = owner(helper, "smoke"), them = owner(helper, "smoke_enemy");
        Mob[] s = scene(helper, me, them);
        if (!(s[0] instanceof FactionHero) || s[1] == null || s[2] == null) {
            helper.fail("could not spawn the scene");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            ServerLevel level = helper.getLevel();
            AABB box = new AABB(helper.absolutePos(new BlockPos(-8, -2, -8)), helper.absolutePos(new BlockPos(24, 20, 24)));
            Set<Integer> before = new HashSet<>();
            for (Entity e : level.getEntitiesOfClass(Entity.class, box))
                before.add(e.getId());
            int cast = 0;
            for (Phenomena.Spec spec : Phenomena.specs()) {
                if (Phenomena.changesWeather(spec))
                    continue;
                String what = spec.intent() + " T" + spec.tier() + (spec.twist() ? " twist" : "");
                try {
                    String name = Phenomena.cast(level, s[0], spec.intent(), Math.max(1, spec.tier()), spec.twist(),
                        s[2].position(), 0);
                    if (!spec.name().equals(name))
                        problems.add(what + " answered '" + name + "'");
                    cast++;
                } catch (RuntimeException ex) {
                    problems.add(what + " threw " + ex);
                }
            }
            if (cast < 90)
                problems.add("only " + cast + " phenomena cast");
            for (Entity e : level.getEntitiesOfClass(Entity.class, box))
                if (!before.contains(e.getId()) && !(e instanceof net.minecraft.world.entity.player.Player))
                    e.discard();
            cleanup(s, null);
            finish(helper, problems);
        });
    }
}
