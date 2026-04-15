# Usage Guide — Java Node-Style Concurrency Library

This guide explains how to use the library to write async, promise-based code in Java with APIs inspired by Node.js.

**Requirements:** Java 17+

---

## Table of Contents

1. [Setup](#setup)
2. [Promise basics](#promise-basics)
3. [Chaining and composition](#chaining-and-composition)
4. [Promise combinators](#promise-combinators)
5. [Timers and scheduling](#timers-and-scheduling)
6. [Cancellation (AbortController / AbortSignal)](#cancellation-abortcontroller--abortsignal)
7. [EventEmitter](#eventemitter)
8. [Worker pool](#worker-pool)
9. [Interop with CompletableFuture](#interop-with-completablefuture)
10. [Diagnostics and unhandled rejections](#diagnostics-and-unhandled-rejections)
11. [Common patterns](#common-patterns)

---

Add the library to your project:

```xml
<dependency>
    <groupId>io.github.dev-priyanshujain</groupId>
    <artifactId>java-node-style-concurrency-lib</artifactId>
    <version>0.1.0</version>
</dependency>
```

All public APIs live in the package **`io.javanode.async`**.

---

## Promise basics

### Creating a promise

**Already resolved or rejected:**

```java
import io.javanode.async.Promise;

Promise<String> resolved = Promise.resolve("hello");
Promise<String> rejected = Promise.reject(new RuntimeException("failed"));
```

**From an async executor** (like `new Promise((resolve, reject) => { ... })` in Node):

```java
Promise<Integer> p = Promise.create((resolve, reject) -> {
    try {
        int result = doSomeWork();
        resolve.resolve(result);
    } catch (Exception e) {
        reject.reject(e);
    }
});
```

Any exception thrown from the executor is treated as a rejection.

For tests or when you must block:

```java
String value = promise.join();  // throws if the promise rejected
```

**With Timeout (Recommended for tests):**

```java
import java.util.concurrent.TimeUnit;

// Blocks for up to 1 second, throws RuntimeException if it fails or times out
String value = promise.await(1, TimeUnit.SECONDS); 
```

### Promise state

```java
Promise.State state = promise.getState();  // PENDING, FULFILLED, or REJECTED
```

---

## Chaining and composition

### `then` — run when fulfilled

```java
Promise<Integer> next = promise
    .then(String::length)
    .then(n -> n * 2);
```

### `thenAsync` — return another promise (async step)

Like returning a promise from a `then` in JavaScript:

```java
Promise<Data> loaded = Promise.resolve(userId)
    .thenAsync(id -> fetchUser(id))
    .thenAsync(user -> fetchOrders(user));
```

### `catchError` — handle rejection

```java
Promise<String> safe = promise.catchError(e -> "default value");
```

### `finallyDo` — run after settle (success or failure)

```java
promise.finallyDo(() -> closeResource());
```

### `then` with both handlers

```java
promise.then(
    value -> process(value),
    error -> fallback(error)
);
```

---

## Promise combinators

### `Promise.all` — wait for all; fail fast on first rejection

```java
Promise<List<String>> all = Promise.all(
    fetchA(),
    fetchB(),
    fetchC()
);
List<String> results = all.join();  // [a, b, c] in order
```

### `Promise.allSettled` — wait for all; never rejects

```java
Promise<List<Promise.AllSettledResult<String>>> settled =
    Promise.allSettled(fetchA(), fetchB(), fetchC());

for (Promise.AllSettledResult<String> r : settled.join()) {
    switch (r.getStatus()) {
        case FULFILLED -> use(r.getValue().orElseThrow());
        case REJECTED  -> log(r.getReason().orElseThrow());
    }
}
```

### `Promise.race` — settle with the first result (fulfill or reject)

```java
Promise<String> first = Promise.race(slowRequest(), fastRequest());
```

### `Promise.any` — resolve with first fulfillment; reject if all reject

```java
Promise<String> one = Promise.any(primary(), backup1(), backup2());
// If all reject, you get Promise.AggregateException with getCauses()
```

---

## Timers and scheduling

Use **`Async`** for Node-style timers. Tasks run on a single-threaded scheduler (event-loop style).

### `setTimeout` — run once after a delay

```java
import io.javanode.async.Async;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

ScheduledFuture<?> handle = Async.setTimeout(() -> System.out.println("later"), 100);
// or with time unit:
Async.setTimeout(() -> {}, 5, TimeUnit.SECONDS);

// Cancel if needed:
handle.cancel(false);
```

### `setInterval` — run repeatedly

```java
ScheduledFuture<?> handle = Async.setInterval(() -> poll(), 1000);
// stop later:
handle.cancel(false);
```

### `setImmediate` and `nextTick` — run as soon as possible

```java
Async.setImmediate(() -> doSoon());
Async.nextTick(() -> doSoon());
```

Both queue work on the same scheduler; use them to defer execution without a delay.

### Promises and timers

You can wrap timers in promises:

```java
Promise<Void> delayed = Promise.create((resolve, reject) ->
    Async.setTimeout(() -> resolve.resolve(null), 200)
);
```

---

## Cancellation (AbortController / AbortSignal)

Cancel timeouts and promise observation using **`AbortController`** and **`AbortSignal`** (similar to the Web API / Node).

### Create and pass the signal

```java
import io.javanode.async.AbortController;
import io.javanode.async.AbortSignal;
import io.javanode.async.AbortException;

AbortController controller = new AbortController();
AbortSignal signal = controller.getSignal();
```

### Timers with abort

If the signal is aborted before the timer fires, the task is not run and the scheduled future is cancelled:

```java
ScheduledFuture<?> t = Async.setTimeout(() -> run(), 5000, signal);
controller.abort();  // cancels the timeout
```

### Promise with abort

Attach an abort signal to a promise so that if the signal is aborted before the promise settles, the *observed* promise rejects with `AbortException` (the underlying work is not automatically stopped):

```java
Promise<String> p = fetchUrl(url).withAbort(signal);
controller.abort(new AbortException("user cancelled"));
// p will reject with that exception (when observed)
```

### Create a promise that respects abort

```java
Promise<String> p = Promise.create((resolve, reject) -> {
    Async.setTimeout(() -> {
        if (signal.isAborted()) return;
        resolve.resolve("done");
    }, 100, signal);
}, signal);
```

---

## EventEmitter

**`EventEmitter`** provides Node-style named events: register listeners and emit with arguments.

### Register and remove listeners

```java
import io.javanode.async.EventEmitter;

EventEmitter bus = new EventEmitter();

EventEmitter.Listener onData = args -> {
    String id = (String) args[0];
    Object payload = args.length > 1 ? args[1] : null;
};
bus.on("data", onData);
bus.once("connect", args -> System.out.println("connected once"));
bus.off("data", onData);
bus.removeAllListeners("data");
```

### Emit events

`emit` runs listeners asynchronously on the library scheduler and returns a **`Promise<Void>`** that resolves when all listeners have run (or rejects if a listener throws):

```java
Promise<Void> done = bus.emit("data", "id-1", payload);
done.then(v -> System.out.println("all listeners ran"));
```

---

## Worker pool

**`WorkerPool`** runs blocking or CPU-bound work on a thread pool and returns **`Promise`s**, so you can compose with the rest of the API.

### Default pool

```java
import io.javanode.async.WorkerPool;

WorkerPool pool = new WorkerPool();

Promise<Integer> result = pool.submit(() -> heavyComputation());
Promise<Void> done = pool.run(() -> doSideEffect());
```

### Custom size or executor

```java
WorkerPool pool = new WorkerPool(8);
// or
WorkerPool pool = new WorkerPool(myExecutorService);
```

### Async tasks (returning a Promise)

`submitAsync` runs a task that returns a `Promise<T>` and flattens it so you get a single `Promise<T>`:

```java
Promise<Data> data = pool.submitAsync(() -> fetchFromNetwork());
```

### Shutdown

```java
pool.shutdown();
// or wait for tasks to finish:
boolean terminated = pool.shutdownAndAwait(10, TimeUnit.SECONDS);
```

---

## Interop with CompletableFuture

### From CompletableFuture to Promise

```java
CompletableFuture<String> future = someAsyncApi();
Promise<String> promise = Promise.fromFuture(future);
```

### From Promise to CompletableFuture

```java
CompletableFuture<String> future = promise.toFuture();
future.thenAccept(System.out::println);
```

This lets you mix the library with existing Java async code.

---

## Diagnostics and unhandled rejections

When a promise rejects and **no** `catchError`, `then(_, onRejected)`, or `finallyDo` was attached before it settled, the library can notify you (similar to Node’s unhandled rejection).

### Register a global handler

```java
import io.javanode.async.PromiseDiagnostics;

PromiseDiagnostics.onUnhandledRejection(info -> {
    System.err.println("Unhandled rejection: " + info.getReason().getMessage());
    info.getReason().printStackTrace();
});
```

### Remove a handler

```java
Consumer<PromiseDiagnostics.RejectionInfo> handler = info -> { ... };
PromiseDiagnostics.onUnhandledRejection(handler);
// later:
PromiseDiagnostics.removeUnhandledRejectionHandler(handler);
```

### Debug logging

```java
PromiseDiagnostics.setDebugLogging(true);
```

When enabled, unhandled rejections are logged and you can use `PromiseDiagnostics.debug("message")` (and overload with throwable) for tracing; they only log when debug logging is on.

---

## Common patterns

### Sequential async steps (like async/await)

Use `thenAsync` to chain steps that return promises:

```java
Promise<String> result = Promise.resolve(userId)
    .thenAsync(this::loadUser)
    .thenAsync(this::loadProfile)
    .then(Profile::getName)
    .catchError(e -> "unknown");
```

### Parallel work and combine

```java
Promise<List<Data>> all = Promise.all(
    pool.submit(() -> loadA()),
    pool.submit(() -> loadB()),
    pool.submit(() -> loadC())
);
```

### Timeout around a promise (concept)

Race a slow promise with a timeout promise:

```java
Promise<String> withTimeout = Promise.race(
    slowOperation(),
    Promise.create((resolve, reject) ->
        Async.setTimeout(() -> reject.reject(new TimeoutException()), 5000)
    )
);
```

### Fire-and-forget with logging

Attach `catchError` or a `finallyDo` so the rejection is “handled” and optionally log:

```java
doSomethingAsync()
    .catchError(e -> { log.error("Async failed", e); return null; });
```

Otherwise, register an `onUnhandledRejection` handler so no rejection is lost.

---

## Summary

| Need | Use |
|------|-----|
| Async value / chain steps | `Promise.create`, `then`, `thenAsync`, `catchError`, `finallyDo` |
| Multiple promises | `Promise.all`, `allSettled`, `race`, `any` |
| Delayed / periodic work | `Async.setTimeout`, `setInterval`, `setImmediate`, `nextTick` |
| Cancel timers or promise observation | `AbortController`, `AbortSignal`, `withAbort` |
| Named events | `EventEmitter` (`on`, `once`, `off`, `emit`) |
| Offload blocking work | `WorkerPool` (`submit`, `run`, `submitAsync`) |
| Use with existing futures | `Promise.fromFuture`, `promise.toFuture()` |
| See unhandled rejections | `PromiseDiagnostics.onUnhandledRejection`, `setDebugLogging` |

For more detail, see the Javadoc on each class in `io.javanode.async`.
