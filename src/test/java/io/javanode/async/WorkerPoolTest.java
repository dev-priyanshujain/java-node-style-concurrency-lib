package io.javanode.async;

import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

public class WorkerPoolTest {

    @Test
    public void testSubmit() throws Exception {
        WorkerPool pool = new WorkerPool(2);
        try {
            Promise<String> p = pool.submit(() -> {
                Thread.sleep(100);
                return "done";
            });
            assertEquals("done", p.await(1, TimeUnit.SECONDS));
        } finally {
            pool.shutdownAndAwait(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testRun() throws Exception {
        WorkerPool pool = new WorkerPool(2);
        AtomicBoolean ran = new AtomicBoolean(false);
        try {
            Promise<Void> p = pool.run(() -> {
                ran.set(true);
            });
            p.await(1, TimeUnit.SECONDS);
            assertTrue(ran.get());
        } finally {
            pool.shutdownAndAwait(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testSubmitAsync() throws Exception {
        WorkerPool pool = new WorkerPool(2);
        try {
            Promise<Integer> p = pool.submitAsync(() -> {
                return Promise.resolve(42);
            });
            assertEquals(42, p.await(1, TimeUnit.SECONDS));
        } finally {
            pool.shutdownAndAwait(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testRejection() {
        WorkerPool pool = new WorkerPool(1);
        try {
            Promise<Object> p = pool.submit(() -> {
                throw new RuntimeException("worker error");
            });
            RuntimeException ex = assertThrows(RuntimeException.class, () -> p.await(1, TimeUnit.SECONDS));
            assertEquals("worker error", ex.getMessage());
        } finally {
            try {
                pool.shutdownAndAwait(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    public void testThreadNaming() throws Exception {
        WorkerPool pool = new WorkerPool(1);
        try {
            Promise<String> p = pool.submit(() -> Thread.currentThread().getName());
            String threadName = p.await(1, TimeUnit.SECONDS);
            assertTrue(threadName.startsWith("worker-pool-"));
        } finally {
            pool.shutdownAndAwait(1, TimeUnit.SECONDS);
        }
    }
}
