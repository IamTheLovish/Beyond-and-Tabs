package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Splits the headless test suite across CI jobs that run side by side ("shards").
 *
 * The game-test server runs its batches one after another and a batch lasts as long as its slowest test, so the
 * suite's wall time is the sum over batches (28m53s of build ed041bf, Oct 15). Two environment variables, read once
 * when the game-test server is about to start, pick this shard's part:
 * <ul>
 *   <li>{@code BAT_GAMETEST_ONLY=a,b,c} - run only what the list names;</li>
 *   <li>{@code BAT_GAMETEST_SKIP=a,b,c} - run everything the list does not name.</li>
 * </ul>
 * An entry names a whole batch - vanilla's "defaultBatch:1", "duel_counters:1", ... by its full name or by the part
 * before the ':' ("duel_matrix" takes every chunk of that batch) - or, as {@code test:<prefix>}, every test whose name
 * starts with the prefix, wherever it is ("test:production_buildings_place_every_unit" takes those eight tests out of
 * whichever default chunk Forge's HashSet order put them in; the rest of that chunk stays a batch of its own here).
 * Prefer base names and test: prefixes - a chunk number ("defaultBatch:4") names whatever 100 tests that order put
 * there, which moves as tests are added; such an entry is flagged in the log. A set but EMPTY ONLY list runs nothing
 * (an emptied shard list must not make that shard run the whole suite again).
 * CI (.github/workflows/build.yml) gives shards 2 and 3 their own lists as ONLY and shard 1 both lists joined as
 * SKIP, so every test runs exactly once whatever new tests are added (anything unnamed lands in shard 1), as long as
 * the two lists name no test in common. With neither variable set nothing changes: a local
 * {@code ./gradlew runGameTestServer} runs everything.
 * <p>
 * The server's own batch collection is rewritten before its first tick (ServerAboutToStartEvent fires in
 * GameTestServer.initServer; the tests start on the first tick): a batch keeps its name and its before / after batch
 * functions, with only this shard's tests. If the collection cannot be found while a filter is set, this throws: a
 * shard that silently ran everything (or nothing) would hide tests or double the time.
 * <p>
 * It also logs every passed test's wall time ("[TestTime] name ms" - vanilla's log reporter says nothing on a pass),
 * so tools/gametest_timing.py can name the slow tests behind a slow batch; failures are reported exactly as before.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class GameTestShards {
    private GameTestShards() { }

    static final String ONLY_ENV = "BAT_GAMETEST_ONLY";
    static final String SKIP_ENV = "BAT_GAMETEST_SKIP";

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent evt) {
        if (!(evt.getServer() instanceof GameTestServer server))
            return;
        GlobalTestReporter.replaceWith(new TimingReporter());
        String onlyRaw = System.getenv(ONLY_ENV);
        Set<String> only = onlyRaw == null ? null : parse(onlyRaw);   // null = no ONLY filter; empty = run nothing
        Set<String> skip = parse(System.getenv(SKIP_ENV));
        if (only == null && skip.isEmpty())
            return;
        Collection<GameTestBatch> batches = batchList(server);
        List<GameTestBatch> mine = new ArrayList<>();
        List<String> kept = new ArrayList<>(), dropped = new ArrayList<>();
        int tests = 0, total = 0;
        Set<String> unmatched = allEntries(only, skip);
        for (GameTestBatch b : batches) {
            List<TestFunction> run = new ArrayList<>();
            for (TestFunction f : b.getTestFunctions()) {
                if (keep(b.getName(), f.getTestName(), only, skip))
                    run.add(f);
                unmatched.removeIf(e -> named(b.getName(), f.getTestName(), Set.of(e)));
            }
            total += b.getTestFunctions().size();
            tests += run.size();
            if (run.size() == b.getTestFunctions().size()) {
                mine.add(b);
                kept.add(b.getName());
            } else if (run.isEmpty()) {
                dropped.add(b.getName());
            } else {
                mine.add(new GameTestBatch(b.getName(), run, b::runBeforeBatchFunction, b::runAfterBatchFunction));
                kept.add(b.getName() + " (" + run.size() + " of " + b.getTestFunctions().size() + ")");
            }
        }
        batches.clear();
        batches.addAll(mine);
        List<String> chunkNamed = new ArrayList<>();
        for (String e : allEntries(only, skip))
            if (e.matches(".*:\\d+"))
                chunkNamed.add(e);
        ReignOfNether.LOGGER.info("[Shards] this server runs {} of {} tests in {} batches: {} - left to the other "
                + "shards: {}{}{}", tests, total, kept.size(), kept, dropped,
                unmatched.isEmpty() ? "" : " - LIST ENTRIES MATCHING NO TEST (typo?): " + unmatched,
                chunkNamed.isEmpty() ? "" : " - entries naming a chunk number (its tests move as tests are added): "
                        + chunkNamed);
        if (tests == 0)
            ReignOfNether.LOGGER.warn("[Shards] nothing matched {}={} {}={} - this shard runs no tests", ONLY_ENV, only,
                    SKIP_ENV, skip);
    }

    /**
     * Whether a test runs in this shard: named by ONLY (null = no ONLY filter; empty = nothing) and not by SKIP - its
     * batch by full or base name, or the test by a "test:" prefix (its name with or without the class prefix).
     */
    static boolean keep(String batchName, String testName, Set<String> only, Set<String> skip) {
        return (only == null || named(batchName, testName, only)) && !named(batchName, testName, skip);
    }

    private static Set<String> allEntries(Set<String> only, Set<String> skip) {
        Set<String> all = new java.util.TreeSet<>(skip);
        if (only != null)
            all.addAll(only);
        return all;
    }

    private static boolean named(String batchName, String testName, Set<String> list) {
        String batch = batchName.toLowerCase(Locale.ROOT);
        int colon = batch.lastIndexOf(':');
        if (list.contains(batch) || list.contains(colon < 0 ? batch : batch.substring(0, colon)))
            return true;
        String test = testName.toLowerCase(Locale.ROOT);
        String bare = test.substring(test.lastIndexOf('.') + 1);
        for (String entry : list)
            if (entry.startsWith("test:") && entry.length() > 5
                    && (test.startsWith(entry.substring(5)) || bare.startsWith(entry.substring(5))))
                return true;
        return false;
    }

    static Set<String> parse(String list) {
        if (list == null)
            return Set.of();
        return Arrays.stream(list.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The server's own (mutable) collection of batches, found by its element type rather than by name, so it does not
     * depend on the field's name in this mapping.
     */
    @SuppressWarnings("unchecked")
    private static Collection<GameTestBatch> batchList(GameTestServer server) {
        for (Field f : GameTestServer.class.getDeclaredFields()) {
            if (!Collection.class.isAssignableFrom(f.getType()))
                continue;
            try {
                f.setAccessible(true);
                Object value = f.get(server);
                if (value instanceof Collection<?> c && !c.isEmpty() && c.iterator().next() instanceof GameTestBatch)
                    return (Collection<GameTestBatch>) c;
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException("[Shards] cannot read GameTestServer." + f.getName(), e);
            }
        }
        throw new IllegalStateException("[Shards] " + ONLY_ENV + " / " + SKIP_ENV + " are set but GameTestServer has no"
                + " collection of test batches to filter - refusing to run every test in every shard");
    }

    /** Vanilla's log reporter plus one "[TestTime]" line per passed test (its wall time since it started). */
    private static final class TimingReporter implements TestReporter {
        private final LogTestReporter log = new LogTestReporter();

        @Override
        public void onTestFailed(GameTestInfo info) {
            log.onTestFailed(info);
        }

        @Override
        public void onTestSuccess(GameTestInfo info) {
            ReignOfNether.LOGGER.info("[TestTime] {} {} ms", info.getTestName(), info.getRunTime());
            log.onTestSuccess(info);
        }
    }
}
