package io.javanode.async;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A worker-pool abstraction inspired by Node.js {@code worker_threads}.
 * <p>
 * Submits tasks to a shared executor and returns {@link Promise}s, so callers
 * can run CPU-bound or blocking work off the "main" flow and compose results
 * with the rest of the promise-based API.
 * <p>
 * By default a fixed-size thread pool is used. Callers can supply a custom
 * {@link ExecutorService} for dedicated or sized pools (e.g. for CPU-heavy
 * workloads).
 */
public final class WorkerPool {

    private final ExecutorService executor;

    /**
     * Creates a worker pool with a default fixed thread pool (number of threads
     * equal to the number of available processors).
     */
    public WorkerPool() {
        this(Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                defaultThreadFactory()));
    }

    /**
     * Creates a worker pool with the given number of threads.
     *
     * @param nThreads number of worker threads
     */
    public WorkerPool(int nThreads) {
        this(Executors.newFixedThreadPool(nThreads, defaultThreadFactory()));
    }

    /**
     * Creates a worker pool backed by the given executor.
     *
     * @param executor the executor to use for submitted tasks
     */
    public WorkerPool(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    private static ThreadFactory defaultThreadFactory() {
        AtomicInteger counter = new AtomicInteger(0);
        return r -> {
            Thread t = new Thread(r, "worker-pool-" + counter.incrementAndGet());
            t.setDaemon(false);
            return t;
        };
    }

    /**
     * Submits a task that returns a value. The returned promise resolves with
     * that value or rejects with the exception thrown by the task.
     *
     * @param task the task to run on the pool
     * @param <T>  the result type
     * @return a promise that settles when the task completes
     */
    public <T> Promise<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
            }
        }, executor);
        return Promise.fromFuture(future);
    }

    /**
     * Submits a runnable task. The returned promise resolves to {@code null}
     * when the task completes, or rejects if the task throws.
     *
     * @param task the task to run on the pool
     * @return a promise that settles when the task completes
     */
    public Promise<Void> run(Runnable task) {
        Objects.requireNonNull(task, "task");
        return submit(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Submits a task that returns a {@link Promise}. The returned promise
     * flattens the inner promise: it resolves or rejects when the inner
     * promise settles.
     *
     * @param task a task that returns a promise (e.g. async work)
     * @param <T>  the result type
     * @return a promise that settles when the inner promise settles
     */
    public <T> Promise<T> submitAsync(Callable<Promise<T>> task) {
        Objects.requireNonNull(task, "task");
        return submit(task).thenAsync(p -> p);
    }

    /**
     * Shuts down the underlying executor. Pending and future submissions may
     * be rejected after shutdown. Does not wait for already running tasks.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Initiates shutdown and blocks until all tasks have completed or the
     * timeout elapses.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit
     * @return {@code true} if the pool terminated, {@code false} if the timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean shutdownAndAwait(long timeout, java.util.concurrent.TimeUnit unit)
            throws InterruptedException {
        executor.shutdown();
        return executor.awaitTermination(timeout, unit);
    }
}
