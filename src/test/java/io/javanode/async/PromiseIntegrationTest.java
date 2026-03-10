package io.javanode.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PromiseIntegrationTest {

    @Test
    void promiseChainWithAsyncDelayAndErrorRecovery() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> finalResult = new AtomicReference<>("UNSET");
        AtomicReference<Throwable> caughtError = new AtomicReference<>();

        Promise.resolve(10)
                // Step 1: Async step that simulates work taking longer than the usual instant resolution
                .thenAsync(val -> {
                    // Use Promise.create to wrap the timed execution
                    return Promise.<Integer>create((resolver, reject) -> {
                        Async.setTimeout(() -> {
                            if (val == 10) {
                                resolver.resolve(val + 5); // Resolves to 15
                            } else {
                                reject.reject(new RuntimeException("Unexpected initial value"));
                            }
                        }, 50); // 50ms delay
                    });
                })
                // Step 2: A synchronous step that throws an error
                .then(val -> {
                    if (val == 15) {
                        throw new RuntimeException("Simulated Rejection");
                    }
                    return val; // Should not be reached
                })
                // Step 3: Catch the error and recover
                .catchError(throwable -> {
                    caughtError.set(throwable);
                    return 100; // Recovery value (Integer)
                })
                // Step 4: Final synchronous step
                .then(result -> {
                    finalResult.set("SUCCESS:" + result);
                    return result;
                })
                // Step 5: Finally block to signal completion
                .finallyDo(() -> {
                    latch.countDown();
                });

        // Wait for the final state
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS),
                "The promise chain should complete within the timeout");

        // Assertions
        assertEquals("SUCCESS:100", finalResult.get(),
                "The final result should be the recovered value.");
        assertNotNull(caughtError.get(),
                "An error should have been caught in catchError.");
        assertEquals(RuntimeException.class.getName() + ": Simulated Rejection", caughtError.get().toString(),
                "The caught error string should match the rejection reason.");
    }
}
