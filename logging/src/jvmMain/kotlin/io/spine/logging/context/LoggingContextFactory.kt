/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging.context

import com.google.errorprone.annotations.CheckReturnValue
import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.Level
import io.spine.logging.MetadataKey
import io.spine.logging.toJavaLogging
import io.spine.logging.toLevel
import io.spine.logging.toLoggerName
import kotlin.reflect.KClass
import io.spine.logging.jvm.context.ContextDataProvider as JContextDataProvider
import io.spine.logging.jvm.context.LogLevelMap as JLogLevelMap
import io.spine.logging.jvm.context.ScopedLoggingContext as JScopedLoggingContext

/**
 * A JVM implementation of `LoggingContextFactory`.
 *
 * The class is immutable and thread-safe.
 */
@Immutable
@ThreadSafe
@CheckReturnValue
internal actual object LoggingContextFactory {

    actual fun levelMapBuilder(): LogLevelMap.Builder = BuilderImpl()

    actual fun levelMap(map: Map<String, Level>, defaultLevel: Level): LogLevelMap =
        MapImpl(map, defaultLevel)

    actual fun newContext(): ScopedLoggingContext.Builder {
        val context = JContextDataProvider.getInstance().getContextApiSingleton().newContext()
        return DelegatingContextBuilder(context)
    }
}

/**
 * Adds the given packages at the specified log level.
 */
public fun LogLevelMap.Builder.add(level: Level, vararg packages: Package): LogLevelMap.Builder {
    packages.forEach {
        add(level, it.name)
    }
    return this
}

/**
 * Implements [LogLevelMap.Builder] by producing [Flogger-based implementation][MapImpl]
 * of [LogLevelMap].
 */
private class BuilderImpl : LogLevelMap.Builder {

    private val map: MutableMap<String, Level> = mutableMapOf()
    private var defaultLevel = Level.OFF

    private fun put(name: String, level: Level) {
        val prevEntry = map.put(name, level)
        check(prevEntry == null) {
            "The logging level for `$name` is already set to `$prevEntry`" +
                    " and could not be changed to `$level`." +
                    " Please check your log level map builder code."
        }
    }

    override fun add(level: Level, vararg classes: KClass<*>): LogLevelMap.Builder {
        classes.forEach { cls ->
            put(cls.toLoggerName(), level)
        }
        return this
    }

    override fun add(level: Level, vararg packageNames: String): LogLevelMap.Builder {
        packageNames.forEach {
            put(it, level)
        }
        return this
    }

    override fun setDefault(level: Level): LogLevelMap.Builder {
        defaultLevel = level
        return this
    }

    override fun build(): LogLevelMap {
        return MapImpl(map, defaultLevel)
    }
}

/**
 * Implements [LogLevelMap] by delegating to a log level map from Flogger.
 */
@Immutable
private class MapImpl(val delegate: JLogLevelMap): LogLevelMap {

    constructor(map: Map<String, Level>, defaultLevel: Level) :
            this(createDelegate(map, defaultLevel))

    override fun levelOf(loggerName: String): Level =
        delegate.getLevel(loggerName).toLevel()

    override fun merge(other: LogLevelMap): LogLevelMap {
        val otherDelegate = other.toFlogger()
        val merged = delegate.merge(otherDelegate)
        return MapImpl(merged)
    }

    companion object {
        private fun createDelegate(map: Map<String, Level>, defaultLevel: Level): JLogLevelMap {
            val convertedMap = map.mapValues { it.value.toJavaLogging() }
            return JLogLevelMap.create(convertedMap, defaultLevel.toJavaLogging())
        }
    }
}

/**
 * Converts a nullable instance of logging level map from Flogger to our wrapped implementation.
 */
public fun JLogLevelMap?.toMap(): LogLevelMap? {
    return this?.let { MapImpl(this) }
}

/**
 * Gets a Flogger log map instance, assuming that this map instance is a [MapImpl].
 * If not, an error is thrown with a diagnostic message.
 *
 * This function is a safety net to prevent an accidental introduction of other map implementations.
 * It is safe to assume that under a JVM, only [MapImpl] will be used because we
 * control the instantiation. This method ensures that the downcast is checked and documented.
 */
private fun LogLevelMap.toFlogger(): JLogLevelMap {
    if (this is MapImpl) {
        return this.delegate
    }
    error("Unsupported implementation of `${LogLevelMap::class.simpleName}`" +
            " encountered: `${this.javaClass.name}`.")
}

/**
 * Implements [ScopedLoggingContext.Builder] by using
 * a [Flogger counterpart][JScopedLoggingContext.Builder] as underlying implementation.
 */
private class DelegatingContextBuilder(
    private val delegate: JScopedLoggingContext.Builder
) : ScopedLoggingContext.Builder() {

    override fun withLogLevelMap(map: LogLevelMap): ScopedLoggingContext.Builder {
        delegate.withLogLevelMap(map.toFlogger())
        return this
    }

    override fun <T : Any> withMetadata(
        key: MetadataKey<T>,
        value: T
    ): ScopedLoggingContext.Builder {
        @Suppress("UNCHECKED_CAST")
        delegate.withMetadata((key as MetadataKey<T>), value)
        return this
    }

    override fun install(): AutoCloseable {
        val closeable = delegate.install()
        return AutoCloseable { closeable.close() }
    }
}
