package io.priyanshu.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromiseCancellationTest {

    @Test
    void withAbortOnAlreadyAbortedSignalRejectsImmediately() {
        AbortController controller = new AbortController();
        controller.abort(new AbortException("stop"));

        Promise<String> promise = Promise.resolve("value").withAbort(controller.getSignal());

        CompletionException ex = assertThrows(CompletionException.class, promise::join);
        Throwable cause = ex.getCause();
        assertTrue(cause instanceof AbortException, "Cause should be AbortException");
        assertEquals("stop", cause.getMessage());
    }

    @Test
    void createWithAbortRespectsAbortSignal() {
        AbortController controller = new AbortController();
        AbortSignal signal = controller.getSignal();

        Promise<String> promise = Promise.create((resolve, reject) -> {
            // Simulate async work that would resolve later.
            Async.setTimeout(() -> resolve.resolve("ok"), 50);
        }, signal);

        controller.abort(new AbortException("cancelled"));

        CompletionException ex = assertThrows(CompletionException.class, promise::join);
        Throwable cause = ex.getCause();
        assertTrue(cause instanceof AbortException, "Cause should be AbortException");
        assertEquals("cancelled", cause.getMessage());
    }
}

