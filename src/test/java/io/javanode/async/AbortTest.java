package io.javanode.async;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class AbortTest {

    @Test
    public void testAbortSignalInitialState() {
        AbortController controller = new AbortController();
        AbortSignal signal = controller.getSignal();
        assertFalse(signal.isAborted());
        assertFalse(signal.getReason().isPresent());
    }

    @Test
    public void testAbort() {
        AbortController controller = new AbortController();
        AbortSignal signal = controller.getSignal();
        controller.abort();
        assertTrue(signal.isAborted());
        assertTrue(signal.getReason().isPresent());
        assertTrue(signal.getReason().get() instanceof AbortException);
    }

    @Test
    public void testAbortWithReason() {
        AbortController controller = new AbortController();
        AbortSignal signal = controller.getSignal();
        RuntimeException reason = new RuntimeException("custom reason");
        controller.abort(reason);
        assertTrue(signal.isAborted());
        assertEquals(reason, signal.getReason().orElse(null));
    }

    @Test
    public void testOnAbortListener() {
        AbortController controller = new AbortController();
        AbortSignal signal = controller.getSignal();
        AtomicInteger count = new AtomicInteger(0);
        
        signal.onAbort(count::incrementAndGet);
        assertEquals(0, count.get());
        
        controller.abort();
        assertEquals(1, count.get());
        
        // Listener should be cleared and not run again if abort is called again
        controller.abort();
        assertEquals(1, count.get());
    }

    @Test
    public void testOnAbortLateRegistration() {
        AbortController controller = new AbortController();
        AbortSignal signal = controller.getSignal();
        controller.abort();
        
        AtomicInteger count = new AtomicInteger(0);
        signal.onAbort(count::incrementAndGet);
        
        // Should run immediately because already aborted
        assertEquals(1, count.get());
    }

    @Test
    public void testOnAbortMultipleListeners() {
        AbortController controller = new AbortController();
        AbortSignal signal = controller.getSignal();
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);
        
        signal.onAbort(count1::incrementAndGet);
        signal.onAbort(count2::incrementAndGet);
        
        controller.abort();
        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
    }
}
