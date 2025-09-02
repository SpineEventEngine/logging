## Platform Generator

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
