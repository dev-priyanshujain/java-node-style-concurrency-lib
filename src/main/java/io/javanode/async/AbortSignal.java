package io.javanode.async;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A lightweight cancellation token analogous to Node.js's {@code AbortSignal}.
 * <p>
 * An {@link AbortSignal} is created and controlled by an {@link AbortController}.
 * Callers can:
 * <ul>
 *   <li>Observe whether the signal has been aborted via {@link #isAborted()}.</li>
 *   <li>Inspect the abort reason via {@link #getReason()}.</li>
 *   <li>Register callbacks that run when the signal is aborted via
 *       {@link #onAbort(Runnable)}.</li>
 * </ul>
 */
public final class AbortSignal {

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean aborted;
    private volatile Throwable reason;

    AbortSignal() {
    }

    /**
     * Whether this signal has been aborted.
     */
    public boolean isAborted() {
        return aborted;
    }

    /**
     * The reason associated with this abort, if any.
     */
    public Optional<Throwable> getReason() {
        return Optional.ofNullable(reason);
    }

    /**
     * Register a callback to run when this signal is aborted.
     * <p>
     * If the signal is already aborted, the callback runs immediately.
     */
    public void onAbort(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (aborted) {
            listener.run();
            return;
        }
        listeners.add(listener);
        // Handle the race where the signal is aborted between the initial
        // aborted check and the add.
        if (aborted && listeners.remove(listener)) {
            listener.run();
        }
    }

    /**
     * Internal hook used by {@link AbortController} to trigger this signal.
     */
    void abort(Throwable reason) {
        if (aborted) {
            return;
        }
        synchronized (this) {
            if (aborted) {
                return;
            }
            aborted = true;
            this.reason = reason != null ? reason : new AbortException("Aborted");
        }
        // Snapshot listeners at abort time; late registrations are handled by
        // {@link #onAbort(Runnable)}.
        Runnable[] snapshot = listeners.toArray(new Runnable[0]);
        for (Runnable listener : snapshot) {
            try {
                listener.run();
            } catch (Throwable ignored) {
                // Listener failures should not prevent other listeners from running.
            }
        }
        listeners.clear();
    }
}

