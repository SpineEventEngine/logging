[![Ubuntu build][ubuntu-build-badge]][gh-actions]
[![Windows build][windows-build-badge]][gh-actions]
[![codecov][codecov-badge]][codecov] &nbsp;
[![license][license-badge]][license]

# Spine Logging

Spine Logging is a library for (potentially multi-platform) Kotlin and Java projects.
Only JMV implementation for Kotlin is currently provided, with JS implementation being
of priority.

The library is largely inspired by [Google Flogger][flogger] logging API, and introduction of
fluent logging API in [SLF4J in v2.0.0][fluent-slf4j].

## Experimental status

This library is in its early stages of development and is not yet stable. 
Its API may change without notice in the near future. Therefore, you should use it with
caution in your projects until it is no longer considered experimental.

## Logging backends

The JVM implementation of the library uses Google Flogger as a backend, at least, for 
the time being. Flogger, in turn, is also a logging facade, which means that it needs to
be configured with a backend which does actual logging operations. 

There are following logging Flogger backends available at the time of writing:
  * `com.google.flogger:flogger-system-backend:$floggerVersion` — uses `java.util.logging`.
  * `com.google.flogger:flogger-log4j-backend:$floggerVersion` — uses Log4.
  * `com.google.flogger:flogger-log4j2-backend:$floggerVersion` — uses Log4j2.
  * `com.google.flogger:flogger-slf4j-backend:$floggerVersion` — uses SLF4J (which is facade itself!).

### Using `java.util.logging` as a backend
If you want to use `java.util.logging` in your project, you need to add the following dependency:
```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
    runtimeOnly("io.spine:spine-logging-backend:$version")
}
```
The second dependency replaces the default Flogger backend with the one which fixes the issues
with using `LogLevelMap` in the project.

### Other backends
If you want a backend other than `java.util.logging`, you need to add dependencies that include
`rutimeOnly` dependency for a logging backend of choice. For example:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
    runtimeOnly("com.google.flogger:flogger-log4j2-backend:$floggerVersion")
}
```

If you need to use SLF4J as a backend, your dependencies should also include a backend for SLF4J.
For example:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
    runtimeOnly("com.google.flogger:flogger-slf4j-backend:$floggerVersion")
    runtimeOnly("org.slf4j:slf4j-reload4j:$slf4jVersion")
}
```

## Logging context

A logging context is a set of attributes that are attached to all log records while 
a context is installed. For example, you can attach a user ID to all log records for 
the current request.

Default implementation provides a no-op context, which does nothing. To use logging
context a `rutimeOnly` dependency for a context implementation should be added in addition
to the dependencies described above.

If your project does not use gRPC, you need to use the following dependency:

```kotlin
dependencies {
    //...
    rutimeOnly("io.spine:spine-logging-context:$version")
}
```

If your project does use gRPC, you need to add the following dependency instead:

```kotlin
dependencies {
    //...
    rutimeOnly("com.google.flogger:flogger-grpc-context:$floggerVersion")
}
```

[codecov]: https://codecov.io/gh/SpineEventEngine/logging
[codecov-badge]: https://codecov.io/gh/SpineEventEngine/logging/branch/master/graph/badge.svg
[license-badge]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat
[license]: http://www.apache.org/licenses/LICENSE-2.0
[gh-actions]: https://github.com/SpineEventEngine/logging/actions
[ubuntu-build-badge]: https://github.com/SpineEventEngine/logging/actions/workflows/build-on-ubuntu.yml/badge.svg
[windows-build-badge]: https://github.com/SpineEventEngine/logging/actions/workflows/build-on-windows.yml/badge.svg
[flogger]: https://google.github.io/flogger
[fluent-slf4j]: https://www.slf4j.org/manual.html#fluent
