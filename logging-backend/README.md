## Spine Logging Backend for JVM

This module defines basic mechanisms that handle log records produced by 
the logging facade.

### Default Platform

`DefaultPlatform` is mostly responsible for runtime discovery of injectable 
services and providing log site determination mechanism. It uses `java.util.ServiceLoader`
to find available service implementations, and stack trace analysis to determine
a log site.

The following services may be injected:

| Service               | Default |
|-----------------------|---|
| `BackendFactory`      | `StdBackendFactory` |
| `ContextDataProvider` | `NoOpContextDataProvider` |
| `Clock`               | `SystemClock` |

### Logger Backend and Factory

`LoggerBackend` is a base interface for all backends. The corresponding factory 
is used to create backend instances.

Do the following to create a backend implementation:

1. Add `spine-logging-backend` to `implementation` configuration.
2. Create your own backend implementation by extending `LoggerBackend`.
3. Create a factory for your backends by extending `BackendFactory`.
4. Expose the factory as a Java service, so that it can be loaded 
with `java.util.ServiceLoader`.

### Standard JUL-based backend

This module provides the default implementation of the logging backend that outputs
to the console, if not given an additional configuration. It uses a built-in 
Java logging framework (also known as JUL or `java.util.logging`). Take a look 
on `StdLoggerBackend` for details.

To use it, add `spine-logging-backend` to `runtimeOnly` configuration:

```kotlin
dependencies {
    runtimeOnly("io.spine:spine-logging-backend:$version")
}
```

Please note, this backend is default for JVM. `DefaultPlatform` uses it when 
no other backend implementation is passed. This backend is **not** exposed 
as a Java service.
