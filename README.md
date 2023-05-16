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

## Gradle dependency
To use Spine Logging in your Gradle project:

```kotlin
dependencies {
    implementation("io.spine:spine-logging:${version}")
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
