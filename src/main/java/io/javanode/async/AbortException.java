package io.javanode.async;

/**
 * Exception type used to represent an aborted operation triggered via
 * {@link AbortController}/{@link AbortSignal}.
 * <p>
 * This is analogous to Node.js's {@code AbortError} and can be used as a
 * rejection reason for promises or a cause for cancelled tasks.
 */
public final class AbortException extends RuntimeException {

    public AbortException(String message) {
        super(message);
    }

    public AbortException(String message, Throwable cause) {
        super(message, cause);
    }
}

