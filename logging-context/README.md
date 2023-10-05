## Standard logging context for JVM

This module provides the basic implementation of the logging context. It is very 
similar to that provided by gRPC logging context from Flogger.

To use it, add `spine-logging-context` to `runtimeOnly` configuration:

```kotlin
dependencies {
    runtimeOnly("io.spine:spine-logging-context:$version")
}
```

Please note, this context is **not** used by default when no other contexts
are found on the classpath. `DefaultPlatform` uses a no-op implementation when
the context is not passed explicitly.
