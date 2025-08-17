/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.logging.backend

import io.spine.logging.jvm.AbstractLogger
import io.spine.logging.jvm.JvmLogSite
import io.spine.logging.jvm.context.ContextDataProvider
import io.spine.logging.jvm.context.Tags
import io.spine.logging.jvm.util.RecursionDepth
import java.util.logging.Level
import java.util.concurrent.TimeUnit.MILLISECONDS
import com.google.common.base.Preconditions.checkNotNull
import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import java.lang.reflect.InvocationTargetException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Platform abstraction layer required to allow fluent logger implementations
 * to work on differing Java platforms (such as Android or GWT).
 *
 * The `Platform` class is responsible for providing any platform-specific APIs,
 * including the mechanism by which logging backends are created.
 *
 * To enable an additional logging platform implementation, the class name
 * should be added to the list of available platforms before the default platform
 * (which must always be at the end).
 *
 * Platform implementation classes must subclass `Platform` and have a public,
 * no-argument constructor.
 *
 * Platform instances are created on first-use of a fluent logger, and
 * platform implementors must take care to avoid cycles during initialization
 * and re-entrant behaviour.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/Platform.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
public abstract class Platform {

    /**
     * Returns the current depth of recursion for logging in the current thread.
     *
     * This method is intended only for use by logging backends or the core of the Logging library.
     * It needs to be called by code which is invoking user code that might trigger
     * reentrant logging.
     *
     * - A value of 1 means that this thread is in a normal log statement.
     *   This is the expected state, and the caller should behave normally.
     *
     * - A value greater than 1 means that this thread is performing reentrant logging.
     *   The caller may choose to change behaviour depending on the value if
     *   there is a risk that reentrant logging is being caused by the caller's code.
     *
     * - A value of zero means that this thread is not logging.
     *   (This is expected to never occur as this method should only be called as
     *   part of a logging library). It should be ignored.
     *
     * When the core Logging library detects the depth exceeding a preset threshold,
     * it may start to modify behaviour to mitigate the risk of unbounded reentrant logging.
     * For example, some or all metadata may be removed from log sites since processing
     * user-provided metadata can itself trigger reentrant logging.
     */
    public companion object {

        @JvmStatic
        public fun getCurrentRecursionDepth(): Int =
            RecursionDepth.getCurrentDepth()

        /**
         * Returns the API for obtaining caller information about loggers and logging classes.
         */
        @JvmStatic
        public fun getCallerFinder(): LogCallerFinder =
            LazyHolder.INSTANCE.getCallerFinderImpl()

        /**
         * Returns a logger backend of the given class name for use by a logger.
         *
         * Note that the returned backend need not be unique; one backend could
         * be used by multiple loggers.
         *
         * The given class name must be in the normal dot-separated form
         * (e.g., `"com.example.Foo$Bar"`) rather than the internal binary format
         * (e.g., `"com/example/Foo$Bar"`).
         *
         * @param className The fully qualified name of the class to which the logger is associated.
         * The logger name is derived from this string in a platform-specific way.
         */
        @JvmStatic
        public fun getBackend(className: String): LoggerBackend =
            LazyHolder.INSTANCE.getBackendImpl(className)

        /**
         * Returns the singleton ContextDataProvider from which
         * a [ScopedLoggingContext][io.spine.logging.jvm.context.ScopedLoggingContext]
         * can be obtained.
         *
         * Platform implementations are required to always provide the same instance here,
         * as this can be cached by callers.
         */
        @JvmStatic
        public fun getContextDataProvider(): ContextDataProvider =
            LazyHolder.INSTANCE.getContextDataProviderImpl()

        /**
         * Returns whether the given logger should have logging forced at the specified level.
         *
         * When logging is forced for a log statement, it will be emitted regardless
         * of the normal log level configuration of the logger and ignoring rate limiting
         * or other filtering.
         *
         * This method is intended to be invoked unconditionally from a fluent logger's
         * `at(Level)` method to permit overriding of default logging behavior.
         *
         * @param loggerName The fully qualified logger name (e.g., "com.example.SomeClass").
         * @param level The level of the log statement being invoked.
         * @param isEnabled Whether the logger is enabled at the given level
         * (i.e., the result of calling `isLoggable()` on the backend instance).
         */
        @JvmStatic
        public fun shouldForceLogging(
            loggerName: String,
            level: Level,
            isEnabled: Boolean
        ): Boolean = getContextDataProvider().shouldForceLogging(loggerName, level, isEnabled)

        /**
         * Obtains a custom logging level set for the logger with the given name via
         * a [io.spine.logging.jvm.context.LogLevelMap] set in the current logging context.
         *
         * The method returns `null` if:
         * - There is no current logging context installed.
         * - The context does not have a log level map.
         * - The log level map does not have a custom level for the given logger.
         *
         * @param loggerName The name of the logger.
         * @return the custom level or `null`.
         */
        @JvmStatic
        public fun getMappedLevel(loggerName: String): Level? {
            checkNotNull(loggerName)
            val provider = getContextDataProvider()
            val result = provider.getMappedLevel(loggerName)
            if (result == null) {
                return null
            }
            return result
        }

        /**
         * Returns [Tags] from the current context to be included in log statements.
         */
        @JvmStatic
        public fun getInjectedTags(): Tags = getContextDataProvider().getTags()

        /**
         * Returns [Metadata] from the current context to be included in log statements.
         */
        @JvmStatic
        public fun getInjectedMetadata(): Metadata =
            // TODO(dbeaumont): Make this return either an extensible MetadataProcessor or
            //  ScopeMetadata.
            getContextDataProvider().getMetadata()

        /**
         * Returns the current time from the epoch (`00:00 1st Jan, 1970`)
         * with nanosecond granularity.
         *
         * This is a non-negative signed 64-bit value in the range
         * `0 <= timestamp < 2^63`. This ensures that the difference between
         * any two timestamps will always yield a valid signed value.
         *
         * **Warning:** Not all [Platform] implementations will deliver nanosecond precision.
         * The code should avoid relying on any implied precision.
         */
        @JvmStatic
        public fun getCurrentTimeNanos(): Long =
            LazyHolder.INSTANCE.getCurrentTimeNanosImpl()

        /**
         * Returns a human-readable string describing the platform and its configuration.
         *
         * This should contain everything a human would need to see to check that the Platform
         * was configured as expected.
         * It should contain the platform name along with any configurable elements
         * (e.g., plugin services) and their settings. It is recommended (though not required)
         * that this string is formatted with one piece of configuration per line in a tabular
         * format, such as:
         * ```
         * platform: <human readable name>
         * formatter: com.example.logging.FormatterPlugin
         * formatter.foo: <"foo" settings for the formatter plugin>
         * formatter.bar: <"bar" settings for the formatter plugin>
         * ```
         * It is not required that this string be machine parseable (though it should be stable).
         */
        @JvmStatic
        public fun getConfigInfo(): String =
            LazyHolder.INSTANCE.getConfigInfoImpl()
    }

    /**
     * API for determining the logging class and log statement sites, return from [getCallerFinder].
     *
     * These classes are immutable and thread-safe.
     *
     * This functionality is not provided directly by the `Platform` API because doing so would
     * require several additional levels to be added to the stack before the implementation was
     * reached. This is problematic for Android, which has only limited stack analysis. By allowing
     * callers to resolve the implementation early and then call an instance directly (this is not
     * an interface), we reduce the number of elements in the stack before the caller is found.
     *
     * ## Essential Implementation Restrictions
     *
     * Any implementation of this API *MUST* follow the rules listed below to avoid any risk of
     * re-entrant code calling during logger initialization. Failure to do so risks creating complex,
     * hard to debug, issues with Flogger configuration.
     *
     * 1. Implementations *MUST NOT* attempt any logging in static methods or constructors.
     * 2. Implementations *MUST NOT* statically depend on any unknown code.
     * 3. Implementations *MUST NOT* depend on any unknown code in constructors.
     *
     * Note that logging and calling arbitrary unknown code (which might log) are permitted inside
     * the instance methods of this API, since they are not called during platform initialization.
     * The
     * easiest way to achieve this is to simply avoid having any non-trivial static fields or any
     * instance fields at all in the implementation.
     *
     * While this sounds onerous it is not difficult to achieve because this API is a singleton, and
     * can delay any actual work until its methods are called. For example, if any additional state is
     * required in the implementation, it can be held via a "lazy holder" to defer initialization.
     */
    @Immutable
    @ThreadSafe
    public abstract class LogCallerFinder {

        /**
         * Returns the name of the immediate caller of the given logger class.
         *
         * This is useful when determining the class name with which to create a logger backend.
         *
         * @param loggerClass The class containing the log() methods whose caller we need to find.
         * @return The name of the class called the specified logger.
         * @throws IllegalStateException If there was no caller of the specified logged passed
         *         on the stack (which may occur if the logger class was invoked directly by JNI).
         */
        public abstract fun findLoggingClass(loggerClass: Class<out AbstractLogger<*>>): String

        /**
         * Returns a LogSite found from the current stack trace for the caller of the log() method
         * on the given logging class.
         *
         * @param loggerApi The class containing the log() methods whose caller we need to find.
         * @param stackFramesToSkip The number of method calls which exist on the stack between the
         *        `log()` method, and the point at which this method is invoked.
         * @return A log site inferred from the stack, or [JvmLogSite.invalid] if no log site
         *         can be determined.
         */
        public abstract fun findLogSite(loggerApi: Class<*>, stackFramesToSkip: Int): JvmLogSite
    }

    /**
     * Returns the implementation of [LogCallerFinder] for this platform.
     *
     * Platform implementations must provide their own [LogCallerFinder]
     * which handles stack trace analysis in a platform-specific way.
     */
    protected abstract fun getCallerFinderImpl(): LogCallerFinder

    /**
     * Creates a platform-specific [LoggerBackend] implementation for the given class.
     *
     * @param className The fully-qualified name of the Java class to create a backend for.
     *        Must be in dot-separated form (e.g., `"com.example.Foo$Bar"`).
     */
    protected abstract fun getBackendImpl(className: String): LoggerBackend

    /**
     * Returns the [ContextDataProvider] implementation for this platform.
     *
     * This method provides a default no-op implementation, but platform implementations
     * are expected to override it with their own context data provider.
     * In the future, this method may become abstract, requiring all platforms to provide
     * their own implementation.
     */
    protected open fun getContextDataProviderImpl(): ContextDataProvider =
        ContextDataProvider.getNoOpProvider()

    /**
     * Returns the current time in nanoseconds for this platform implementation.
     *
     * This default implementation uses [kotlin.time.Clock.System.now] to
     * provide the nanoseconds precision.
     */
    @OptIn(ExperimentalTime::class)
    protected open fun getCurrentTimeNanosImpl(): Long {
        return Clock.System.now().let {
            it.epochSeconds * MILLISECONDS.toNanos(1) + it.nanosecondsOfSecond
        }
    }

    /**
     * Returns configuration information about this platform implementation.
     *
     * Platform implementations must provide details about their configuration
     * in a human-readable format.
     */
    protected abstract fun getConfigInfoImpl(): String
}

/**
 * Use the lazy holder idiom here to avoid class loading issues. Loading the Platform subclass
 * will trigger static initialization of the Platform class first, which would not be possible if
 * the [INSTANCE] field were a static field in Platform.
 *
 * This means that any errors in platform loading are deferred until the first time one of the
 * [Platform]'s static methods is invoked.
 */
private object LazyHolder {

    /**
     * Non-final to prevent javac inlining.
     */
    @Suppress("ConstantField")
    private var defaultPlatform: String =
        "io.spine.logging.backend.system.DefaultPlatform"

    /**
     * The first available platform from this list is used. Each platform is defined separately
     * outside of this array so that the IdentifierNameString annotation can be applied to each.
     * This annotation tells Proguard that these strings refer to class names.
     * If Proguard decides to obfuscate those classes, it will also obfuscate these strings so
     * that reflection can still be used.
     */
    private val availablePlatforms: Array<String> = arrayOf(
        // The fallback/default platform gives a workable, logging backend.
        defaultPlatform
    )

    val INSTANCE: Platform = loadFirstAvailablePlatform(availablePlatforms)

    @Suppress("TooGenericExceptionCaught", "InstanceOfCheckForException")
    private fun loadFirstAvailablePlatform(platformClass: Array<String>): Platform {
        val platform = viaHolder()
        if (platform != null) {
            return platform
        }

        val errorMessage = StringBuilder()
        // Try the reflection-based approach as a backup, if the provider isn't available.
        for (clazz in platformClass) {
            try {
                return Class.forName(clazz)
                    .getConstructor()
                    .newInstance() as Platform
            } catch (e: Throwable) {
                // Catch errors so if we can't find _any_ implementations,
                // we can report something useful.
                // Unwrap any generic wrapper exceptions for readability here
                // (extend this as needed).
                val th = if (e is InvocationTargetException) { e.cause } else { e }
                errorMessage.append('\n')
                    .append(clazz)
                    .append(": ")
                    .append(th)
            }
        }
        error(errorMessage.insert(0, "No logging platforms found:").toString())
    }

    @Suppress("SwallowedException")
    private fun viaHolder(): Platform? {
        var platform: Platform? = null
        // Try the platform provider first if it is available.
        try {
            platform = PlatformProvider.getPlatform()
        } catch (_: NoClassDefFoundError) {
            // May be an expected error:
            // The `PlatformProvider` is an optional dependency that can be provided at runtime.
            // Inside Google we use a generator to create the class file for
            // it programmatically, but for third-party use cases the provider
            // could be made available through custom classpath management.
            // The exception is swallowed intentionally as the provider is optional.
        }
        return platform
    }
}
