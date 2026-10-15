package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
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
 * Splits the headless test suite across CI jobs that run side by side ("shards"), by whole test batches.
 *
 * The game-test server runs its batches one after another and a batch lasts as long as its slowest test, so the
 * suite's wall time is the sum over batches (~26 of a build's ~32 minutes, Oct 15). Two shards that each run part of
 * the batches finish in about half that. Selection is by batch name - vanilla's "defaultBatch:0", "stress_8v8:0", ...;
 * a list entry matches a batch by its full name or by the part before the ':' (so "duel_matrix" takes every chunk of
 * that batch). Two environment variables, read once when the game-test server is about to start:
 * <ul>
 *   <li>{@code BAT_GAMETEST_ONLY=a,b,c} - run only these batches;</li>
 *   <li>{@code BAT_GAMETEST_SKIP=a,b,c} - run every batch except these.</li>
 * </ul>
 * CI gives one shard the ONLY list and the other the SKIP list with the SAME value, so every batch runs exactly once
 * whatever new tests are added (a new batch falls into the SKIP shard). With neither set nothing changes: a local
 * {@code ./gradlew runGameTestServer} runs everything.
 *
 * Batches are filtered in place in the server's own batch list before its first tick (ServerAboutToStartEvent fires
 * in GameTestServer.initServer; the tests start on the first tick). If the list cannot be found while a filter is set,
 * this throws: a shard that silently ran everything (or nothing) would hide tests or double the time.
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
        Set<String> only = parse(System.getenv(ONLY_ENV));
        Set<String> skip = parse(System.getenv(SKIP_ENV));
        if (only.isEmpty() && skip.isEmpty())
            return;
        Collection<GameTestBatch> batches = batchList(server);
        List<String> kept = new ArrayList<>(), dropped = new ArrayList<>();
        int tests = 0;
        for (GameTestBatch b : batches) {
            if (keep(b.getName(), only, skip)) {
                kept.add(b.getName());
                tests += b.getTestFunctions().size();
            } else {
                dropped.add(b.getName());
            }
        }
        batches.removeIf(b -> !keep(b.getName(), only, skip));
        ReignOfNether.LOGGER.info("[Shards] this server runs {} of {} test batches ({} tests): {} - left to the other "
                + "shard: {}", kept.size(), kept.size() + dropped.size(), tests, kept, dropped);
        if (kept.isEmpty())
            ReignOfNether.LOGGER.warn("[Shards] no batch matched {}={} {}={} - this shard runs no tests", ONLY_ENV, only,
                    SKIP_ENV, skip);
    }

    /** Whether a batch runs in this shard: in ONLY (if given) and not in SKIP; by full name or by its base name. */
    static boolean keep(String batchName, Set<String> only, Set<String> skip) {
        String name = batchName.toLowerCase(Locale.ROOT);
        int colon = name.lastIndexOf(':');
        String base = colon < 0 ? name : name.substring(0, colon);
        boolean inOnly = only.contains(name) || only.contains(base);
        boolean inSkip = skip.contains(name) || skip.contains(base);
        return (only.isEmpty() || inOnly) && !inSkip;
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
