## Standard context implementation for JVM

This module provides the basic implementation of the logging context that
uses thread local to store the current context data.

To use it, add `spine-logging-std-context` to `runtimeOnly` configuration:

```kotlin
dependencies {
    runtimeOnly("io.spine:spine-logging-std-context:$version")
}
```
