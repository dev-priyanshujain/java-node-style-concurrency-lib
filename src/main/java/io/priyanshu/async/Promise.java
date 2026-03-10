package io.priyanshu.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A Node.js-style {@code Promise} abstraction for Java.
 */
public final class Promise<T> {

    public enum State {
        PENDING,
        FULFILLED,
        REJECTED
    }

    @FunctionalInterface
    public interface Resolver<T> {
        void resolve(T value);
    }

    @FunctionalInterface
    public interface Rejector {
        void reject(Throwable reason);
    }

    @FunctionalInterface
    public interface Executor<T> {
        void execute(Resolver<T> resolve, Rejector reject) throws Exception;
    }

    public static final class AllSettledResult<T> {
        public enum Status {
            FULFILLED,
            REJECTED
        }

        private final Status status;
        private final T value;
        private final Throwable reason;

        private AllSettledResult(Status status, T value, Throwable reason) {
            this.status = Objects.requireNonNull(status, "status");
            this.value = value;
            this.reason = reason;
        }

        public static <T> AllSettledResult<T> fulfilled(T value) {
            return new AllSettledResult<>(Status.FULFILLED, value, null);
        }

        public static <T> AllSettledResult<T> rejected(Throwable reason) {
            return new AllSettledResult<>(Status.REJECTED, null, reason);
        }

        public Status getStatus() { return status; }
        public Optional<T> getValue() { return Optional.ofNullable(value); }
        public Optional<Throwable> getReason() { return Optional.ofNullable(reason); }
    }

    public static final class AggregateException extends RuntimeException {
        private final List<Throwable> causes;
        public AggregateException(String message, List<? extends Throwable> causes) {
            super(message);
            this.causes = List.copyOf(causes);
        }
        public List<Throwable> getCauses() { return causes; }
    }

    private final CompletableFuture<T> delegate;
    private volatile boolean rejectionHandled;

    private Promise(CompletableFuture<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.delegate.whenComplete((value, error) -> {
            if (error != null) {
                Async.setTimeout(() -> {
                    Throwable unwrapped = unwrapCompletionException(error);
                    if (!rejectionHandled) {
                        PromiseDiagnostics.fireUnhandledRejection(this, unwrapped);
                    }
                }, 1L);
            }
        });
    }

    private static Throwable unwrapCompletionException(Throwable t) {
        if (t instanceof CompletionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    public static <T> Promise<T> create(Executor<T> executor) {
        Objects.requireNonNull(executor, "executor");
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(future::complete, future::completeExceptionally);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return new Promise<>(future);
    }

    public static <T> Promise<T> create(Executor<T> executor, AbortSignal signal) {
        return create(executor).withAbort(signal);
    }

    public static <T> Promise<T> resolve(T value) {
        return new Promise<>(CompletableFuture.completedFuture(value));
    }

    public static <T> Promise<T> reject(Throwable reason) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(Objects.requireNonNull(reason, "reason"));
        return new Promise<>(future);
    }

    public static <T> Promise<T> fromFuture(CompletableFuture<T> future) {
        return new Promise<>(future);
    }

    public static <T> Promise<T> fromFuture(CompletableFuture<T> future, AbortSignal signal) {
        return fromFuture(future).withAbort(signal);
    }

    public CompletableFuture<T> toFuture() { return delegate; }

    public State getState() {
        if (!delegate.isDone()) return State.PENDING;
        return delegate.isCompletedExceptionally() ? State.REJECTED : State.FULFILLED;
    }

    public <U> Promise<U> then(Function<? super T, ? extends U> onFulfilled) {
        return new Promise<>(delegate.thenApply(onFulfilled));
    }

    public <U> Promise<U> then(Function<? super T, ? extends U> onFulfilled, Function<? super Throwable, ? extends U> onRejected) {
        rejectionHandled = true;
        return new Promise<>(delegate.handle((value, error) -> {
            if (error == null) {
                return onFulfilled == null ? (U) value : onFulfilled.apply(value);
            } else {
                Throwable unwrapped = unwrapCompletionException(error);
                if (onRejected == null) {
                    throw (unwrapped instanceof RuntimeException e) ? e : new RuntimeException(unwrapped);
                }
                return onRejected.apply(unwrapped);
            }
        }));
    }

    public <U> Promise<U> thenAsync(Function<? super T, Promise<U>> onFulfilled) {
        return new Promise<>(delegate.thenCompose(value -> onFulfilled.apply(value).toFuture()));
    }

    public Promise<T> catchError(Function<? super Throwable, ? extends T> onRejected) {
        rejectionHandled = true;
        return new Promise<>(delegate.handle((value, error) -> {
            if (error == null) return value;
            return onRejected.apply(unwrapCompletionException(error));
        }));
    }

    public Promise<T> finallyDo(Runnable action) {
        this.rejectionHandled = true;
        CompletableFuture<T> next = delegate.whenComplete((v, e) -> action.run());
        Promise<T> res = new Promise<>(next);
        res.rejectionHandled = true;
        return res;
    }

    public T join() {
        return delegate.join();
    }

    public static <T> Promise<List<T>> all(Collection<? extends Promise<? extends T>> promises) {
        if (promises.isEmpty()) return Promise.resolve(List.of());
        List<? extends Promise<? extends T>> list = List.copyOf(promises);
        CompletableFuture<?>[] futures = list.stream().map(Promise::toFuture).toArray(CompletableFuture[]::new);
        return new Promise<>(CompletableFuture.allOf(futures).thenApply(v -> {
            List<T> res = new ArrayList<>(list.size());
            for (Promise<? extends T> p : list) res.add(p.join());
            return res;
        }));
    }

    @SafeVarargs
    public static <T> Promise<List<T>> all(Promise<? extends T>... promises) {
        return all(List.of(promises));
    }

    public static <T> Promise<List<AllSettledResult<T>>> allSettled(Collection<? extends Promise<? extends T>> promises) {
        if (promises.isEmpty()) return Promise.resolve(List.of());
        List<? extends Promise<? extends T>> list = List.copyOf(promises);
        CompletableFuture<?>[] futures = list.stream().map(Promise::toFuture).toArray(CompletableFuture[]::new);
        return new Promise<>(CompletableFuture.allOf(futures).handle((v, e) -> {
            List<AllSettledResult<T>> res = new ArrayList<>(list.size());
            for (Promise<? extends T> p : list) {
                try { res.add(AllSettledResult.fulfilled(p.join())); }
                catch (Throwable t) { res.add(AllSettledResult.rejected(t.getCause() != null ? t.getCause() : t)); }
            }
            return res;
        }));
    }

    @SafeVarargs
    public static <T> Promise<List<AllSettledResult<T>>> allSettled(Promise<? extends T>... promises) {
        return allSettled(List.of(promises));
    }

    public static <T> Promise<T> race(Collection<? extends Promise<? extends T>> promises) {
        if (promises.isEmpty()) return Promise.resolve(null);
        CompletableFuture<T> future = new CompletableFuture<>();
        for (Promise<? extends T> p : promises) {
            p.toFuture().whenComplete((v, e) -> {
                if (!future.isDone()) {
                    if (e == null) future.complete(v);
                    else future.completeExceptionally(e);
                }
            });
        }
        return new Promise<>(future);
    }

    @SafeVarargs
    public static <T> Promise<T> race(Promise<? extends T>... promises) {
        return race(List.of(promises));
    }

    public static <T> Promise<T> any(Collection<? extends Promise<? extends T>> promises) {
        if (promises.isEmpty()) return Promise.reject(new AggregateException("Empty", List.of()));
        CompletableFuture<T> future = new CompletableFuture<>();
        List<? extends Promise<? extends T>> list = List.copyOf(promises);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (Promise<? extends T> p : list) {
            p.toFuture().whenComplete((v, e) -> {
                if (e == null) future.complete(v);
                else {
                    errors.add(e);
                    if (errors.size() == list.size()) future.completeExceptionally(new AggregateException("All failed", errors));
                }
            });
        }
        return new Promise<>(future);
    }

    @SafeVarargs
    public static <T> Promise<T> any(Promise<? extends T>... promises) {
        return any(List.of(promises));
    }

    public <U> Promise<U> fold(Function<? super T, ? extends U> onFulfilled, Function<? super Throwable, ? extends U> onRejected) {
        return new Promise<>(delegate.handle((v, e) -> e == null ? onFulfilled.apply(v) : onRejected.apply(unwrapCompletionException(e))));
    }

    public Promise<T> tap(Consumer<? super T> onFulfilled, Consumer<? super Throwable> onRejected) {
        return new Promise<>(delegate.whenComplete((v, e) -> {
            if (e == null) onFulfilled.accept(v);
            else onRejected.accept(unwrapCompletionException(e));
        }));
    }

    public Promise<T> withAbort(AbortSignal signal) {
        if (signal.isAborted()) return Promise.reject(signal.getReason().orElse(new AbortException("Aborted")));
        CompletableFuture<T> res = new CompletableFuture<>();
        delegate.whenComplete((v, e) -> {
            if (signal.isAborted()) res.completeExceptionally(signal.getReason().orElse(new AbortException("Aborted")));
            else if (e != null) res.completeExceptionally(e);
            else res.complete(v);
        });
        signal.onAbort(() -> res.completeExceptionally(signal.getReason().orElse(new AbortException("Aborted"))));
        return new Promise<>(res);
    }
}
