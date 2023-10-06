### Default Platform for JVM

This module contains the default logger platform for a server-side 
Java environment.

`Platform` is mostly responsible for providing mandatory logging services 
and log site determination mechanism. 

This implementation uses `java.util.ServiceLoader` to find available service 
implementations, and stack trace analysis to determine a log site.
Every required service has a fall-back option if a user doesn't provide
a particular implementation.

The following services can be provided:

| Service               | Default implementation    |
|-----------------------|---------------------------|
| `BackendFactory`      | `JulBackendFactory`       |
| `ContextDataProvider` | `NoOpContextDataProvider` |
| `Clock`               | `SystemClock`             |
