package io.priyanshu.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncTest {

    @Test
    void setTimeoutRunsTaskAfterDelay() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Async.setTimeout(latch::countDown, 10);

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS),
                "setTimeout should execute the task within a reasonable time window");
    }

    @Test
    void setIntervalRunsRepeatedlyAndCanBeCancelled() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger counter = new AtomicInteger();

        ScheduledFuture<?> future = Async.setInterval(() -> {
            counter.incrementAndGet();
            latch.countDown();
        }, 10);

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS),
                "setInterval should execute the task multiple times");

        int beforeCancel = counter.get();
        future.cancel(false);

        Thread.sleep(100);
        assertEquals(beforeCancel, counter.get(),
                "Cancelling the interval should stop further executions");
    }

    @Test
    void setImmediateAndNextTickRunSoon() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);

        Async.setImmediate(latch::countDown);
        Async.nextTick(latch::countDown);

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS),
                "setImmediate and nextTick should both run promptly");
    }

    @Test
    void abortSignalCancelsTimeoutBeforeExecution() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AbortController controller = new AbortController();
        AbortSignal signal = controller.getSignal();

        ScheduledFuture<?> future = Async.setTimeout(latch::countDown, 50, signal);
        controller.abort();

        // Allow time for the timeout that should have been cancelled.
        assertFalse(latch.await(200, TimeUnit.MILLISECONDS),
                "Aborted timeout should not execute the task");
        assertTrue(future.isCancelled() || future.isDone(),
                "Scheduled future should be cancelled or completed");
    }
}

