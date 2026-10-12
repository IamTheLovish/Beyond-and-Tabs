package com.solegendary.reignofnether.taskscheduler;

import com.solegendary.reignofnether.ReignOfNether;

import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs tasks a whole number of game ticks from now, on the thread that drives {@link #tick()} (the server thread for
 * {@link TaskSchedulerServerEvents}, the client thread for {@link TaskSchedulerClientEvents}). design/ron_rewrite_plan.md
 * explains why it replaced Reign of Nether's scheduler and its wall-clock {@code CompletableFuture.delayedExecutor}
 * callbacks; in short, what this guarantees:
 * <ul>
 *   <li><b>On the game thread.</b> {@link #schedule} may be called from any thread; the task always runs inside
 *   {@link #tick()}. (The delayed executor ran game code - order queues, building lists, block changes, chunk resends -
 *   on a ForkJoin pool thread while the server thread was mutating the same state.)</li>
 *   <li><b>In game time.</b> Delays are ticks, so a paused singleplayer game or a lagging server delays the task with
 *   the game instead of firing it into a frozen or stopped world.</li>
 *   <li><b>Exactly once, in order.</b> Due order, first-scheduled first within a tick. A task that throws is logged
 *   and skipped; it neither re-runs nor takes the other tasks down with it (RoN's scheduler re-ran every task of the
 *   tick on the next tick after any exception).</li>
 *   <li><b>Re-entrant.</b> A task may schedule another; it runs on a later tick, never in the same pass (RoN's threw a
 *   ConcurrentModificationException).</li>
 *   <li><b>Cheap.</b> A binary heap keyed by due tick: O(log n) to add, a tick with nothing due costs one poll and one
 *   peek, and nothing is allocated per tick (RoN's rebuilt its whole list, one record per task, every tick).</li>
 * </ul>
 * Semantics kept from Reign of Nether's scheduler: {@code schedule(0, t)} from inside a tick runs at the end of that
 * same tick, {@code schedule(n, t)} at the end of the n-th tick after it.
 */
public final class TickScheduler {

    private record Task(long due, long seq, Runnable run) { }

    private static final Comparator<Task> ORDER = Comparator.comparingLong(Task::due).thenComparingLong(Task::seq);

    private final String name;
    /** Scheduled since the last tick, from any thread; moved into {@link #queue} by the ticking thread. */
    private final ConcurrentLinkedQueue<Task> inbox = new ConcurrentLinkedQueue<>();
    /** Ticking thread only. */
    private final PriorityQueue<Task> queue = new PriorityQueue<>(ORDER);
    private final AtomicLong seq = new AtomicLong();
    /** Ticks completed. Written by the ticking thread only; read by schedule() from any thread. */
    private volatile long now = 0;

    public TickScheduler(String name) {
        this.name = name;
    }

    /** Runs {@code task} at the end of the {@code delayTicks}-th tick from now (0 = the end of this one). */
    public void schedule(int delayTicks, Runnable task) {
        Objects.requireNonNull(task, "task");
        inbox.add(new Task(now + Math.max(0, delayTicks), seq.getAndIncrement(), task));
    }

    /** Runs every task now due, then advances the clock one tick. Returns how many ran. Ticking thread only. */
    public int tick() {
        for (Task t; (t = inbox.poll()) != null; )
            queue.add(t);
        int ran = 0;
        long current = now;
        for (Task t = queue.peek(); t != null && t.due <= current; t = queue.peek()) {
            queue.poll();
            ran++;
            try {
                t.run.run();
            } catch (Exception e) {
                ReignOfNether.LOGGER.error("[{}] scheduled task threw; skipped", name, e);
            }
        }
        now = current + 1;
        return ran;
    }

    /** Tasks waiting (scheduled and not yet run). */
    public int pending() {
        return inbox.size() + queue.size();
    }

    /** Drops every waiting task (the world they were scheduled against is going away) and restarts the clock. */
    public void clear() {
        inbox.clear();
        queue.clear();
        now = 0;
    }
}
