[![Ubuntu build][ubuntu-build-badge]][gh-actions]
[![Windows build][windows-build-badge]][gh-actions]
[![codecov][codecov-badge]][codecov] &nbsp;
[![license][license-badge]][license]

# Spine Logging

Spine Logging is a versatile library designed for Kotlin and Java projects, 
with a potential for multi-platform use. 

As for now, only JVM target is supported, with a JavaScript implementation 
being our priority for future development.

API and implementation are largely inspired by [Google Flogger][flogger],
and the introduction of fluent logging API in [SLF4J in v2.0.0][fluent-slf4j].

## Current status: Experimental

Please note that this library is still in the experimental phase of development, 
and hence, its API may undergo significant changes. As such, we advise using 
this library cautiously in your projects until it has reached a stable 
release stage.

## Simple example

To start logging, only a dependency on `spine-logging` is needed:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
}
```

In this case, the default logging backend will be used. It outputs log records
to the console using `java.util.logging` (JUL) framework.

All logging operations are done with an instance of `io.spine.logging.Logger`.
To get a logger, one can use the following:

1. Make a logging class implement `WithLogging` interface.
2. Get a logger from `LoggingFactory`.

The interface provides a default property `logger` that returns a logger 
for the implementing class or object:

```kotlin
import io.spine.logging.WithLogging

class Example : WithLogging {
    fun doSomething() {
        logger.atWarning() // Call to the default property of `WithLogging`.
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

## Logging backends

A logging backend handles an actual output of the logged statement. 
`LogRecord` may be printed to the console, be written to a file or sent by 
the network to some log-aggregating service. It all is up to a chosen backend 
and its configuration.

The following backends are available:

* `io.spine:spine-logging-jul-backend` – the default JUL-based backend.
* `io.spine:spine-logging-log4j2-backend` – Log4j2 backend.

The default backend is supplied along the logging library itself. To use it,
one just needs to not supply any other backend. Then JUL backend will be 
used automatically.

For other backends, put a chosen one to `runtimeOnly` configuration, 
and the logging library will discover it in the runtime.

An example usage of Log4j2 backend:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
    runtimeOnly("io.spine:spine-logging-log4j2-backend:$version")
}
```

Please note, only one backend implementation should be present in the runtime.
Two or more backends will cause an exception because the logging framework 
will not be able to understand, which one should be used. The default backend
doesn't count here.

## Logging contexts

A logging context refers to a set of attributes that are attached to all log 
records while a context is installed. For example, rate limit counters are 
always attached to the context.

Here is an example of rate limiter context metadata:

```kotlin
import io.spine.logging.WithLogging

class Example : WithLogging {
    fun action() = repeat(12) {
        logger.atInfo()
            .every(7) // Should be emitted once per N calls.
            .log { "Call #$it" } // Rate limiter metadata will be included here.
    }
}

// Produces the following output (without timestamps):
// INFO: Call #0 [CONTEXT ratelimit_count=7 ]
// INFO: Call #7 [CONTEXT ratelimit_count=7 skipped=6 ]
```

Also, a user can attach its own metadata. For instance, you can attach 
a user ID to all log records for the current request, or force logging 
level if the requested URL contains a debug parameter.

Here is an example of how to attach a user ID:

```kotlin
import io.spine.logging.LoggingFactory.singleMetadataKey
import io.spine.logging.WithLogging
import io.spine.logging.context.ScopedLoggingContext

typealias Action = String
typealias User = String

data class Request(
    val action: Action,
    val user: User,
)

class RequestHandler : WithLogging {

    companion object {
        // Metadata is represented with key-value entries.
        private val USER_KEY = singleMetadataKey("user", User::class)
    }

    fun handle(request: Request) = withinUserContext(request.user) {
        logger.atInfo().log { "Handling `${request.action}` request." }
        // All log statements within this block will include `user` info.
    }

    private fun withinUserContext(user: User, action: () -> Unit) = ScopedLoggingContext.newContext()
        .withMetadata(USER_KEY, user)
        .execute(action)
}
```

Let's send several requests to the created handler:

```kotlin
val handler = RequestHandler()
with(handler) {
    handle(Request(action = "create", user = "Jack"))
    handle(Request(action = "update", user = "Jack"))
    handle(Request(action = "remove", user = "Bill"))
}

// Produces the following output (without timestamps):
// INFO: Handling `create` request. [CONTEXT user="Jack" ]
// INFO: Handling `update` request. [CONTEXT user="Jack" ]
// INFO: Handling `remove` request. [CONTEXT user="Bill" ]
```

### Gradle configuration

The default implementation provides a no-op context. Context metadata is
ignored in this case.

To use a logging context, a `runtimeOnly` dependency for a context 
implementation should be added.

If your project does not use gRPC, use the following dependency:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
    runtimeOnly("io.spine:spine-logging-std-context:$version")
}
```

If your project does use gRPC, add the following dependency:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
    runtimeOnly("io.spine:spine-logging-grpc-context:$version")
}
```

[codecov]: https://codecov.io/gh/SpineEventEngine/logging
[codecov-badge]: https://codecov.io/gh/SpineEventEngine/logging/branch/master/graph/badge.svg
[license-badge]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat
[license]: https://www.apache.org/licenses/LICENSE-2.0
[gh-actions]: https://github.com/SpineEventEngine/logging/actions
[ubuntu-build-badge]: https://github.com/SpineEventEngine/logging/actions/workflows/build-on-ubuntu.yml/badge.svg
[windows-build-badge]: https://github.com/SpineEventEngine/logging/actions/workflows/build-on-windows.yml/badge.svg
[flogger]: https://google.github.io/flogger
[fluent-slf4j]: https://www.slf4j.org/manual.html#fluent
