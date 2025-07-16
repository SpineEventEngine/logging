## Spine Logging

This module contains Spine Logging API.

API and implementation are largely inspired by [Google Flogger][flogger] and
the introduction of fluent logging API in [SLF4J v2.0.0][fluent-slf4j].

The library aims to be multiplatform, but as of now, only the JVM target is supported.

### API stability level
The library is used internally by the Spine SDK modules.
Consider this library being in the `@Experimental` status for the usage outside the Spine SDK.

[flogger]: https://google.github.io/flogger
[fluent-slf4j]: https://www.slf4j.org/manual.html#fluent
