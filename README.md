# Java Node-Style Concurrency Library

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)

A lightweight, zero-dependency Java library that brings **Node.js-style async patterns** to the JVM — promises, timers, event emitters, cancellation, and more.

## Features

- **Promise\<T\>** — `then`, `catchError`, `finallyDo`, `thenAsync`; static `resolve`, `reject`, `all`, `allSettled`, `race`, `any`
- **Async** — `setTimeout`, `setInterval`, `setImmediate`, `nextTick`
- **AbortController / AbortSignal** — cancellation for timers and promises
- **EventEmitter** — named events with `on`, `once`, `off`, `emit`
- **WorkerPool** — run blocking or async tasks on a thread pool, get `Promise`s back
- **PromiseDiagnostics** — unhandled rejection handlers and debug logging
- **CompletableFuture interop** — `fromFuture` / `toFuture` for seamless integration

## Documentation

**[Usage Guide](docs/USAGE.md)** — setup, examples, and how to use each part of the library.

## Building

**Requirements:** Java 17+, Maven 3.8+

```bash
# Run tests
mvn test

# Build the JAR
mvn package
```

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
