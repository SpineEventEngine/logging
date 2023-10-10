## Thread local based logging context for JVM

This module provides the basic implementation of the logging context that
uses thread local storage (TLS) to store the current context data.

To use it, add `spine-logging-thread-local-context` to `runtimeOnly` configuration:

```kotlin
dependencies {
    runtimeOnly("io.spine:spine-logging-tls-context:$version")
}
```
