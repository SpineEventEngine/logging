[![Ubuntu build][ubuntu-build-badge]][gh-actions]
[![Windows build][windows-build-badge]][gh-actions]
[![codecov][codecov-badge]][codecov] &nbsp;
[![license][license-badge]][license]

# Spine Logging

Spine Logging is a versatile library designed for Kotlin and Java projects, with a potential
for multi-platform use. At present, we only provide a JVM implementation for Kotlin,
with a JavaScript implementation being our priority for future development.

This library draws inspiration from the logging API of [Google Flogger][flogger], and
the introduction of a fluent logging API in [SLF4J v2.0.0][fluent-slf4j].

## Current status: Experimental

Please note that this library is still in the experimental phase of development and hence,
its API may undergo significant changes. As such, we advise using this library cautiously in
your projects until it has reached a stable release stage.

## Logging backends

Our JVM implementation currently employs Google Flogger.
Since Flogger is a logging facade, it requires a backend to perform the actual logging operations.
At the time of writing, the following Flogger backends are available:

* `com.google.flogger:flogger-system-backend:$floggerVersion` — utilizing `java.util.logging`.
* `com.google.flogger:flogger-log4j-backend:$floggerVersion` — utilizing Log4j.
* `com.google.flogger:flogger-log4j2-backend:$floggerVersion` — utilizing Log4j2.
* `com.google.flogger:flogger-slf4j-backend:$floggerVersion` — utilizing SLF4J (which is a facade itself!).

### How to Use `java.util.logging` as a backend

To use `java.util.logging` in your project, add the following dependency:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
    runtimeOnly("io.spine:spine-logging-backend:$version")
}
```
The second dependency replaces the default Flogger backend with a backend that resolves issues
with using LogLevelMap in the project.

### Utilizing other backends

If you prefer a backend other than java.util.logging, add dependencies that include a `runtimeOnly`
dependency for your chosen logging backend. For instance:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
    runtimeOnly("com.google.flogger:flogger-log4j2-backend:$floggerVersion")
}
```

For SLF4J as a backend, your dependencies should also include a backend for SLF4J. For example:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:$version")
    runtimeOnly("com.google.flogger:flogger-slf4j-backend:$floggerVersion")
    runtimeOnly("org.slf4j:slf4j-reload4j:$slf4jVersion")
}
```

## Logging context

A logging context refers to a set of attributes that are attached to all log records while
a context is installed. For instance, you can attach a user ID to all log records for
the current request.

The default implementation provides a no-op context. To use a logging context, a `runtimeOnly`
dependency for a context implementation should be added along with the above dependencies.

If your project does not use gRPC, use the following dependency:

```kotlin
dependencies {
    //...
    rutimeOnly("io.spine:spine-logging-context:$version")
}
```

If your project does use gRPC, add the following dependency:

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
