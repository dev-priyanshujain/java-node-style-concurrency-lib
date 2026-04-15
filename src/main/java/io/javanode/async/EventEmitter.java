package io.javanode.async;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A lightweight, Node.js-style {@code EventEmitter} for Java.
 * <p>
 * Listeners are registered per named event via {@link #on(String, Listener)}
 * and {@link #once(String, Listener)}, and removed via
 * {@link #off(String, Listener)}. Emission is always asynchronous: calls to
 * {@link #emit(String, Object...)} schedule listener execution onto the same
 * single-threaded scheduler used by {@link Async}, providing an
 * event-loop-like execution model.
 */
public final class EventEmitter {

    /**
     * Functional interface representing an event listener. The listener receives
     * the event arguments as an {@code Object...} array, mirroring the flexible
     * signature used in Node.js.
     */
    @FunctionalInterface
    public interface Listener {
        void onEvent(Object... args);
    }

    private static final class Registration {
        final Listener listener;
        final boolean once;

        Registration(Listener listener, boolean once) {
            this.listener = listener;
            this.once = once;
        }
    }

    private final ConcurrentMap<String, CopyOnWriteArrayList<Registration>> listeners =
            new ConcurrentHashMap<>();

    /**
     * Register a listener for the given event name.
     *
     * @param event    the event name
     * @param listener the listener to invoke
     * @return this emitter for chaining
     */
    public EventEmitter on(String event, Listener listener) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(listener, "listener");
        listeners
                .computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
                .add(new Registration(listener, false));
        return this;
    }

    /**
     * Register a one-time listener for the given event name. The listener is
     * automatically removed after it has been invoked once.
     *
     * @param event    the event name
     * @param listener the listener to invoke
     * @return this emitter for chaining
     */
    public EventEmitter once(String event, Listener listener) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(listener, "listener");
        listeners
                .computeIfAbsent(event, k -> new CopyOnWriteArrayList<>())
                .add(new Registration(listener, true));
        return this;
    }

    /**
     * Remove a previously registered listener for the given event name.
     *
     * @param event    the event name
     * @param listener the listener to remove
     * @return this emitter for chaining
     */
    public EventEmitter off(String event, Listener listener) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(listener, "listener");
        CopyOnWriteArrayList<Registration> regs = listeners.get(event);
        if (regs != null) {
            regs.removeIf(reg -> reg.listener.equals(listener));
            if (regs.isEmpty()) {
                listeners.remove(event, regs);
            }
        }
        return this;
    }

    /**
     * Asynchronously emit an event with the given arguments.
     * <p>
     * Listener invocation is scheduled using {@link Async#nextTick(Runnable)},
     * so handlers run on the library's single-threaded scheduler rather than
     * the caller's thread. The returned {@link Promise} resolves once all
     * listeners for the event have run. If any listener throws, the promise is
     * rejected with that exception.
     *
     * @param event the event name
     * @param args  event arguments passed to listeners
     * @return a promise that settles when listener execution completes
     */
    public Promise<Void> emit(String event, Object... args) {
        Objects.requireNonNull(event, "event");

        // Snapshot registrations at emit time to provide predictable iteration
        // while still allowing concurrent modification of the listener lists.
        CopyOnWriteArrayList<Registration> regs =
                listeners.getOrDefault(event, new CopyOnWriteArrayList<>());

        return Promise.create((resolve, reject) -> {
            Async.nextTick(() -> {
                try {
                    if (regs.isEmpty()) {
                        resolve.resolve(null);
                        return;
                    }
                    for (Registration reg : regs) {
                        try {
                            reg.listener.onEvent(args);
                        } catch (Throwable listenerError) {
                            reject.reject(listenerError);
                            return;
                        }
                        if (reg.once) {
                            off(event, reg.listener);
                        }
                    }
                    resolve.resolve(null);
                } catch (Throwable t) {
                    reject.reject(t);
                }
            });
        });
    }

    /**
     * Remove all listeners for the given event.
     *
     * @param event the event name
     * @return this emitter for chaining
     */
    public EventEmitter removeAllListeners(String event) {
        Objects.requireNonNull(event, "event");
        listeners.remove(event);
        return this;
    }
}

