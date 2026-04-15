package io.javanode.async;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class EventEmitterTest {

    @Test
    public void testOnAndEmit() throws Exception {
        EventEmitter emitter = new EventEmitter();
        AtomicInteger count = new AtomicInteger(0);
        
        emitter.on("test", args -> {
            count.incrementAndGet();
            assertEquals("hello", args[0]);
        });

        emitter.emit("test", "hello").await(1, TimeUnit.SECONDS);
        assertEquals(1, count.get());

        emitter.emit("test", "hello").await(1, TimeUnit.SECONDS);
        assertEquals(2, count.get());
    }

    @Test
    public void testOnce() throws Exception {
        EventEmitter emitter = new EventEmitter();
        AtomicInteger count = new AtomicInteger(0);
        
        emitter.once("test", args -> count.incrementAndGet());

        emitter.emit("test").await(1, TimeUnit.SECONDS);
        assertEquals(1, count.get());

        emitter.emit("test").await(1, TimeUnit.SECONDS);
        assertEquals(1, count.get());
    }

    @Test
    public void testOff() throws Exception {
        EventEmitter emitter = new EventEmitter();
        AtomicInteger count = new AtomicInteger(0);
        EventEmitter.Listener listener = args -> count.incrementAndGet();
        
        emitter.on("test", listener);
        emitter.emit("test").await(1, TimeUnit.SECONDS);
        assertEquals(1, count.get());

        emitter.off("test", listener);
        emitter.emit("test").await(1, TimeUnit.SECONDS);
        assertEquals(1, count.get());
    }

    @Test
    public void testMultipleListeners() throws Exception {
        EventEmitter emitter = new EventEmitter();
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);
        
        emitter.on("test", args -> count1.incrementAndGet());
        emitter.on("test", args -> count2.incrementAndGet());

        emitter.emit("test").await(1, TimeUnit.SECONDS);
        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
    }

    @Test
    public void testRemoveAllListeners() throws Exception {
        EventEmitter emitter = new EventEmitter();
        AtomicInteger count = new AtomicInteger(0);
        
        emitter.on("test", args -> count.incrementAndGet());
        emitter.on("test", args -> count.incrementAndGet());

        emitter.removeAllListeners("test");
        emitter.emit("test").await(1, TimeUnit.SECONDS);
        assertEquals(0, count.get());
    }

    @Test
    public void testEmitRejection() {
        EventEmitter emitter = new EventEmitter();
        RuntimeException error = new RuntimeException("oops");
        
        emitter.on("error", args -> {
            throw error;
        });

        Promise<Void> result = emitter.emit("error");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> result.await(1, TimeUnit.SECONDS));
        assertEquals("oops", ex.getMessage());
    }

    @Test
    public void testAsyncExecution() throws Exception {
        EventEmitter emitter = new EventEmitter();
        AtomicInteger count = new AtomicInteger(0);
        Thread mainThread = Thread.currentThread();
        CompletableFuture<Thread> listenerThread = new CompletableFuture<>();

        emitter.on("test", args -> {
            count.incrementAndGet();
            listenerThread.complete(Thread.currentThread());
        });

        Promise<Void> p = emitter.emit("test");
        // Emission should be async, so count might still be 0 here if scheduler hasn't run it yet.
        // But the promise ensures it completes.
        p.await(1, TimeUnit.SECONDS);
        
        assertEquals(1, count.get());
        assertNotSame(mainThread, listenerThread.get(1, TimeUnit.SECONDS));
    }
}
