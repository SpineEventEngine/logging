## Spine Logging Smoke Test

This module contains `AbstractLoggingSmokeTest` that is meant to be executed in concrete
Spine modules to verify that the logging is actually happening. Only the most basic,
top-level functionality is tested. The main goal is to make sure that the logged messages
don't go to some `/dev/null`.

Functionality of `spine-logging` is already covered with tests directly in the repository.
But due to the library complexity (i.e., KMP, runtime binding, classpath scan), it is still
better to have some real-life check-ups outside the development repository.

### Applicability

As of now, it is only applicable to JVM modules. It is because testing involves usage 
of JUL-based backend. This backend is default and allows easier interception of the logged
text for further assertion.

Take a look at `AbstractLoggingSmokeTest` for usage example.

Please note, the presence of `spine-logging` and `spine-logging-backend` is expected due to 
the Gradle configuration of `jvm-module` script plugin, which is applied to Spine JVM modules.

`spine-logging` should be added manually if this smoke test is used in a module without
`jvm-module` configuration:

```
implementation("io.spine:spine-logging:$version")
runtimeOnly("io.spine:spine-logging-backend:$version")
```
