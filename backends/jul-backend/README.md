## `java.util.logging` (JUL) backend

This module provides the implementation of the logging backend that outputs
log records to the console, if not given any additional configuration. It uses 
a built-in Java logging framework (also known as JUL or `java.util.logging`).

### Usage notice

This backend is default for JVM. `DefaultPlatform` uses it when no other backend
implementation is passed. It means that an end user never needs to put this 
backend to `runtimeOnly` configuration to use it. It is always supplied along
with the logging facade itself.

Hence, this backend is **not** exposed as a Java service.
