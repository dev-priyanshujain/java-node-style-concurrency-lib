package io.javanode.async;

import java.util.Objects;

/**
 * Controller counterpart to {@link AbortSignal}, analogous to Node.js's
 * {@code AbortController}.
 * <p>
 * A controller owns a single {@link AbortSignal} instance exposed via
 * {@link #getSignal()}. Calling {@link #abort()} (or {@link #abort(Throwable)})
 * marks the signal as aborted and notifies any registered listeners.
 */
public final class AbortController {

    private final AbortSignal signal = new AbortSignal();

    /**
     * Create a new controller.
     */
    public AbortController() {
    }

    /**
     * Convenience factory for a new controller.
     */
    public static AbortController create() {
        return new AbortController();
    }

    /**
     * The {@link AbortSignal} associated with this controller.
     */
    public AbortSignal getSignal() {
        return signal;
    }

    /**
     * Abort with a default {@link AbortException} reason.
     */
    public void abort() {
        abort(new AbortException("Aborted"));
    }

    /**
     * Abort with a specific reason.
     */
    public void abort(Throwable reason) {
        Objects.requireNonNull(reason, "reason");
        signal.abort(reason);
    }
}

