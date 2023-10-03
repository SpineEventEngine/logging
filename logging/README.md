## Spine Logging

This module contains multiplatform Spine Logging API.

As for now, only JVM target is supported.

API and implementation are largely inspired by [Google Flogger][flogger],
and the introduction of fluent logging API in [SLF4J in v2.0.0][fluent-slf4j].

### Entry points

All logging operations are done with an instance of `io.spine.logging.Logger`.
To get a logger, one can use the following:

1. Make the class that needs logging implement `WithLogging` interface.
2. Get a logger from `LoggingFactory`.

The interface provides a default method `logger()` that returns a logger
for the implementing class or object:

```kotlin
import io.spine.logging.WithLogging

class Example : WithLogging {
    fun doSomething() {
        logger() // Call to the default method of `WithLogging`.
            .atWarning()
            .log { "..." }
    }
}
```

`LoggingFactory` has two methods that return a logger for the enclosing class
and for the given `KClass`:

```kotlin
import io.spine.logging.LoggingFactory

class App {
    private val logger1 = LoggingFactory.forEnclosingClass()
    private val logger2 = LoggingFactory.loggerFor(this::class)

    fun doSomething() {
        check(logger1 === logger2) // There is always one logger per class.
        logger1.atWarning()
            .log { "..." }
    }
}
```

[flogger]: https://google.github.io/flogger
[fluent-slf4j]: https://www.slf4j.org/manual.html#fluent
