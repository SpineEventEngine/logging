## Google Flogger

This directory contains Flogger [modules][flogger-github] built with Gradle.

Original Flogger sources have been repackaged from `com.google.common.*` 
to `io.spine.logging.jvm.*`. It prevents a runtime clash in situations 
when a user has both `spine-logging` and `flogger` on the classpath. 
However, a user is not meant to use two logging libraries simultaneously.
`flogger` may appear on the classpath as a transitive dependency.

Further, Flogger sources should be migrated to Kotlin and merged with
our own code.

### Middleware

Contains Flogger Logging API along with classes that handle log statements 
before they are passed to a backend. 

Handling of a log statement includes controlling of rate limiting, attachment 
of context and scope metadata, tracking log level restrictions, etc.

From the standpoint of `spine-logging`, this module is middleware for JVM target.

### Platform Generator

API declares `Platform`. It is a configuration point of the logging framework.
Implementations of this class should handle runtime discovery of injectable services
(backends, clocks and contexts), provide log site determination mechanism,
create backend instances, etc. 

Flogger is designed to be portable to different Java platforms. 

For example: 

- Server backends.
- [GWT apps][google-gwt].
- Android.

Potentially, we can have different `Platform` implementations for different
Java platforms. And this module was meant to generate different `PlatformProvider`
classes on different platforms. But as of now, it always generates a provider
that creates instances of `DefaultPlatform`.

In the future, this module may be replaced with a single hard class. 

Take a look at issue [#67](https://github.com/SpineEventEngine/logging/issues/67)
for details.

[flogger-github]: https://google.github.io/flogger
[google-gwt]: https://en.wikipedia.org/wiki/Google_Web_Toolkit
