# Java Node-Style Concurrency Library

[![Maven Central](https://img.shields.io/maven-central/v/io.github.dev-priyanshujain/java-node-style-concurrency-lib.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.dev-priyanshujain/java-node-style-concurrency-lib)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)

A lightweight, zero-dependency Java library that brings **Node.js-style async patterns** to the JVM — promises, timers, event emitters, cancellation, and more.

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.dev-priyanshujain</groupId>
    <artifactId>java-node-style-concurrency-lib</artifactId>
    <version>0.1.0</version>
</dependency>
```

For Gradle (Groovy):
```groovy
implementation 'io.github.dev-priyanshujain:java-node-style-concurrency-lib:0.1.0'
```

## Features

- **Promise\<T\>** — `then`, `catchError`, `finallyDo`, `thenAsync`; static `resolve`, `reject`, `all`, `allSettled`, `race`, `any`
- **Async** — `setTimeout`, `setInterval`, `setImmediate`, `nextTick`
- **AbortController / AbortSignal** — cancellation for timers and promises
- **EventEmitter** — named events with `on`, `once`, `off`, `emit`
- **WorkerPool** — run blocking or async tasks on a thread pool, get `Promise`s back
- **PromiseDiagnostics** — unhandled rejection handlers and debug logging
- **CompletableFuture interop** — `fromFuture` / `toFuture` for seamless integration

## Documentation

**[Usage Guide](docs/USAGE.md)** — detailed examples and API reference.

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
