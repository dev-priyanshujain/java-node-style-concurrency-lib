package io.priyanshu.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PromiseDiagnosticsTest {

    private final AtomicReference<PromiseDiagnostics.RejectionInfo> captured = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        PromiseDiagnostics.removeUnhandledRejectionHandler(this::captureRejection);
    }

    private void captureRejection(PromiseDiagnostics.RejectionInfo info) {
        captured.set(info);
    }

    @Test
    void unhandledRejectionFiresHandler() throws InterruptedException {
        PromiseDiagnostics.onUnhandledRejection(this::captureRejection);

        Promise.resolve("ignored")
                .then(x -> { throw new RuntimeException("fail"); });
        // No catchError/finallyDo on the returned promise; it rejects unhandled.

        Thread.sleep(50);
        PromiseDiagnostics.RejectionInfo info = captured.get();
        assertNotNull(info, "Unhandled rejection handler should have been called");
        assertEquals("fail", info.getReason().getMessage());
        assertEquals(Promise.State.REJECTED, info.getPromise().getState());
    }

    @Test
    void handledRejectionDoesNotFireUnhandledHandler() throws InterruptedException {
        PromiseDiagnostics.onUnhandledRejection(this::captureRejection);

        Promise.resolve("ok")
                .then(x -> { throw new RuntimeException("caught"); })
                .catchError(e -> "recovered");
        // The chain has catchError, so the rejection is handled.

        Thread.sleep(50);
        assertEquals(null, captured.get(),
                "Handler should not be called when rejection is handled");
    }

    @Test
    void finallyDoMarksRejectionAsHandled() throws InterruptedException {
        PromiseDiagnostics.onUnhandledRejection(this::captureRejection);

        Promise.resolve("ok")
                .then(x -> { throw new RuntimeException("x"); })
                .finallyDo(() -> {});
        // finallyDo observes the promise, so rejection is considered handled.

        Thread.sleep(50);
        assertEquals(null, captured.get(),
                "Handler should not be called when finallyDo is attached");
    }
}
