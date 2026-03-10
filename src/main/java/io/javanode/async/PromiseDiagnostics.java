package io.javanode.async;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diagnostics and hooks for the promise library, analogous to Node.js
 * unhandled rejection handling and optional debug tracing.
 * <p>
 * Register one or more handlers via {@link #onUnhandledRejection(Consumer)}.
 * When a promise rejects and no {@code catchError}, {@code then(_, onRejected)},
 * or {@code finallyDo} was attached before settlement, the handlers are
 * invoked with a {@link RejectionInfo} describing the rejection.
 * <p>
 * Enable {@link #setDebugLogging(boolean)} to log promise lifecycle events
 * (e.g. creation, settlement, unhandled rejection) to a logger.
 */
public final class PromiseDiagnostics {

    private static final Logger LOG = Logger.getLogger(PromiseDiagnostics.class.getName());

    /**
     * Immutable info passed to unhandled-rejection handlers.
     */
    public static final class RejectionInfo {
        private final Promise<?> promise;
        private final Throwable reason;

        RejectionInfo(Promise<?> promise, Throwable reason) {
            this.promise = promise;
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        /** The promise that rejected (for inspection, e.g. {@link Promise#getState()}). */
        public Promise<?> getPromise() {
            return promise;
        }

        /** The rejection reason. */
        public Throwable getReason() {
            return reason;
        }
    }

    private static final CopyOnWriteArrayList<Consumer<RejectionInfo>> unhandledHandlers =
            new CopyOnWriteArrayList<>();
    private static volatile boolean debugLogging;

    private PromiseDiagnostics() {
        throw new AssertionError("PromiseDiagnostics should not be instantiated");
    }

    /**
     * Registers a handler that is invoked when a promise rejects without
     * having a rejection handler (catchError / then(_, onRejected) / finallyDo)
     * attached before it settles.
     *
     * @param handler the consumer to call with rejection info
     */
    public static void onUnhandledRejection(Consumer<RejectionInfo> handler) {
        Objects.requireNonNull(handler, "handler");
        unhandledHandlers.add(handler);
    }

    /**
     * Removes a previously registered unhandled-rejection handler.
     *
     * @param handler the handler to remove
     */
    public static void removeUnhandledRejectionHandler(Consumer<RejectionInfo> handler) {
        Objects.requireNonNull(handler, "handler");
        unhandledHandlers.remove(handler);
    }

    /**
     * Enables or disables debug logging of promise lifecycle and unhandled
     * rejections. When enabled, messages are logged at {@link Level#FINE}
     * (or similar) to the logger for this class.
     *
     * @param enabled {@code true} to enable debug logging
     */
    public static void setDebugLogging(boolean enabled) {
        debugLogging = enabled;
    }

    /**
     * Returns whether debug logging is currently enabled.
     */
    public static boolean isDebugLoggingEnabled() {
        return debugLogging;
    }

    /**
     * Invoked by the library when a promise settles rejected and was not
     * handled. Not intended for direct use by callers.
     */
    public static void fireUnhandledRejection(Promise<?> promise, Throwable reason) {
        Throwable unwrapped = reason;
        if (unwrapped instanceof CompletionException || unwrapped instanceof ExecutionException) {
            if (unwrapped.getCause() != null) {
                unwrapped = unwrapped.getCause();
            }
        }
        RejectionInfo info = new RejectionInfo(promise, unwrapped);
        if (debugLogging) {
            LOG.log(Level.WARNING, "Unhandled promise rejection: {0}", reason.getMessage());
            LOG.log(Level.FINE, "Rejection stack", reason);
        }
        for (Consumer<RejectionInfo> handler : unhandledHandlers) {
            try {
                handler.accept(info);
            } catch (Throwable t) {
                if (debugLogging) {
                    LOG.log(Level.SEVERE, "Unhandled rejection handler threw", t);
                }
            }
        }
    }

    /**
     * Logs a debug message only when {@link #isDebugLoggingEnabled()} is true.
     * Useful for tracing promise chain creation and settlement.
     */
    public static void debug(String message) {
        if (debugLogging && LOG.isLoggable(Level.FINE)) {
            LOG.fine(message);
        }
    }

    /**
     * Logs a debug message with throwable only when debug logging is enabled.
     */
    public static void debug(String message, Throwable thrown) {
        if (debugLogging && LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, message, thrown);
        }
    }
}
