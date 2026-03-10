package io.priyanshu.async;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Scheduling helpers that mirror Node.js-style timer APIs such as
 * {@code setTimeout}, {@code setInterval}, {@code setImmediate}, and
 * {@code process.nextTick}.
 * <p>
 * All tasks are executed on a dedicated single-threaded scheduler to provide a
 * predictable, event-loop-like execution model similar to Node.js.
 */
public final class Async {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "async-scheduler");
                t.setDaemon(true);
                return t;
            });

    private Async() {
        throw new AssertionError("Async should not be instantiated");
    }

    /**
     * Node-style {@code setTimeout(handler, delay)}.
     * <p>
     * Schedules {@code task} to run once after the given delay.
     *
     * @param task  the runnable to execute
     * @param delay the delay amount
     * @param unit  the time unit of the delay
     * @return a {@link ScheduledFuture} that can be used to cancel the timer
     */
    public static ScheduledFuture<?> setTimeout(Runnable task, long delay, TimeUnit unit) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        return SCHEDULER.schedule(task, delay, unit);
    }

    /**
     * Variant of {@link #setTimeout(Runnable, long, TimeUnit)} that is tied to
     * an {@link AbortSignal}. If the signal is aborted before the task runs,
     * the scheduled timer is cancelled and the task is not executed.
     */
    public static ScheduledFuture<?> setTimeout(
            Runnable task,
            long delay,
            TimeUnit unit,
            AbortSignal signal
    ) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(signal, "signal");

        Runnable wrapped = () -> {
            if (signal.isAborted()) {
                return;
            }
            task.run();
        };

        ScheduledFuture<?> future = SCHEDULER.schedule(wrapped, delay, unit);
        signal.onAbort(() -> future.cancel(false));
        return future;
    }

    /**
     * Convenience overload for {@link #setTimeout(Runnable, long, TimeUnit)}
     * that interprets {@code delayMillis} as milliseconds.
     */
    public static ScheduledFuture<?> setTimeout(Runnable task, long delayMillis) {
        return setTimeout(task, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Millisecond-based overload of {@link #setTimeout(Runnable, long, TimeUnit, AbortSignal)}.
     */
    public static ScheduledFuture<?> setTimeout(Runnable task, long delayMillis, AbortSignal signal) {
        return setTimeout(task, delayMillis, TimeUnit.MILLISECONDS, signal);
    }

    /**
     * Node-style {@code setInterval(handler, interval)}.
     * <p>
     * Schedules {@code task} to run repeatedly with a fixed period between
     * consecutive executions. The first execution occurs after the given period.
     *
     * @param task   the runnable to execute
     * @param period the interval between executions
     * @param unit   the time unit of the period
     * @return a {@link ScheduledFuture} that can be used to cancel the interval
     */
    public static ScheduledFuture<?> setInterval(Runnable task, long period, TimeUnit unit) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        return SCHEDULER.scheduleAtFixedRate(task, period, period, unit);
    }

    /**
     * Variant of {@link #setInterval(Runnable, long, TimeUnit)} that is tied
     * to an {@link AbortSignal}. When the signal is aborted, the repeating
     * interval is cancelled and further executions are suppressed.
     */
    public static ScheduledFuture<?> setInterval(
            Runnable task,
            long period,
            TimeUnit unit,
            AbortSignal signal
    ) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(signal, "signal");

        Runnable wrapped = () -> {
            if (signal.isAborted()) {
                return;
            }
            task.run();
        };

        ScheduledFuture<?> future = SCHEDULER.scheduleAtFixedRate(wrapped, period, period, unit);
        signal.onAbort(() -> future.cancel(false));
        return future;
    }

    /**
     * Convenience overload for {@link #setInterval(Runnable, long, TimeUnit)}
     * that interprets {@code periodMillis} as milliseconds.
     */
    public static ScheduledFuture<?> setInterval(Runnable task, long periodMillis) {
        return setInterval(task, periodMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Millisecond-based overload of {@link #setInterval(Runnable, long, TimeUnit, AbortSignal)}.
     */
    public static ScheduledFuture<?> setInterval(Runnable task, long periodMillis, AbortSignal signal) {
        return setInterval(task, periodMillis, TimeUnit.MILLISECONDS, signal);
    }

    /**
     * Node-style {@code setImmediate(handler)}.
     * <p>
     * Queues {@code task} to run as soon as possible on the scheduler.
     *
     * @param task the runnable to execute
     * @return a {@link ScheduledFuture} that can be used to cancel the task
     */
    public static ScheduledFuture<?> setImmediate(Runnable task) {
        Objects.requireNonNull(task, "task");
        return SCHEDULER.schedule(task, 0L, TimeUnit.MILLISECONDS);
    }

    /**
     * Abort-aware variant of {@link #setImmediate(Runnable)}.
     */
    public static ScheduledFuture<?> setImmediate(Runnable task, AbortSignal signal) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(signal, "signal");

        Runnable wrapped = () -> {
            if (signal.isAborted()) {
                return;
            }
            task.run();
        };

        ScheduledFuture<?> future = SCHEDULER.schedule(wrapped, 0L, TimeUnit.MILLISECONDS);
        signal.onAbort(() -> future.cancel(false));
        return future;
    }

    /**
     * Node-style {@code process.nextTick(handler)} approximation.
     * <p>
     * Schedules {@code task} to run on the scheduler "soon", before or alongside
     * other queued timers. Exact microtask vs macrotask ordering semantics from
     * Node.js are not guaranteed, but this provides a convenient API for
     * deferring work.
     *
     * @param task the runnable to execute
     * @return a {@link ScheduledFuture} that can be used to cancel the task
     */
    public static ScheduledFuture<?> nextTick(Runnable task) {
        Objects.requireNonNull(task, "task");
        return SCHEDULER.schedule(task, 0L, TimeUnit.MILLISECONDS);
    }

    /**
     * Abort-aware variant of {@link #nextTick(Runnable)}.
     */
    public static ScheduledFuture<?> nextTick(Runnable task, AbortSignal signal) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(signal, "signal");

        Runnable wrapped = () -> {
            if (signal.isAborted()) {
                return;
            }
            task.run();
        };

        ScheduledFuture<?> future = SCHEDULER.schedule(wrapped, 0L, TimeUnit.MILLISECONDS);
        signal.onAbort(() -> future.cancel(false));
        return future;
    }
}

