## Spine Logging Smoke Test

This module contains `AbstractLoggingSmokeTest` that is meant to be executed in concrete
Spine modules to verify that the logging is actually happening. Only the most basic,
top-level functionality is tested. The main goal is to make sure that the logged messages
don't go to some `/dev/null`.

As for now, it is only applicable to JVM modules. It is because testing involves usage 
of Java Logging backend. This backend is default and allows to easier intercept the logged
text for further assertion.

Take a look on `AbstractLoggingSmokeTest` for usage example.
