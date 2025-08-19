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

package io.spine.logging

import com.google.errorprone.annotations.CheckReturnValue
import io.spine.logging.jvm.JvmLogSite
import io.spine.logging.jvm.JvmLogSite.Companion.injectedLogSite
import io.spine.logging.jvm.Middleman
import kotlin.reflect.KClass
import kotlin.time.DurationUnit
import java.util.logging.Level as JLevel

/**
 * Implements [Middleman] using [Middleman] as the underlying implementation.
 */
@CheckReturnValue
public class JvmLogger(
    cls: KClass<*>,
    internal val delegate: Middleman
) : Logger<JvmLogger.Api>(cls) {

    /**
     * The non-wildcard logging API for the [JvmLogger].
     */
    public interface Api: LoggingApi<Api>

    override fun createApi(level: Level): Api {
        val floggerApi = delegate.at(level)
        return if (floggerApi.isEnabled()) {
            ApiImpl(floggerApi)
        } else {
            NoOp
        }
    }

    /**
     * A no-op singleton implementation of [Api].
     */
    private object NoOp: LoggingApi.NoOp<Api>(), Api
}

/**
 * Implements [LoggingApi] wrapping [Middleman.Api].
 */
@Suppress("TooManyFunctions")
private class ApiImpl(private val delegate: Middleman.Api): JvmLogger.Api {

    private var loggingDomain: LoggingDomain? = null

    override fun withLoggingDomain(domain: LoggingDomain): JvmLogger.Api {
        this.loggingDomain = domain
        return this
    }

    override fun withCause(cause: Throwable?): JvmLogger.Api {
        if (cause != null) {
            delegate.withCause(cause)
        }
        return this
    }

    override fun withInjectedLogSite(logSite: LogSite): JvmLogger.Api {
        val floggerLogSite = logSite.toFloggerSite()
        delegate.withInjectedLogSite(floggerLogSite)
        return this
    }

    override fun withInjectedLogSite(
        internalClassName: String,
        methodName: String,
        encodedLineNumber: Int,
        sourceFileName: String?
    ): JvmLogger.Api {
        val logSite = injectedLogSite(
            internalClassName, methodName, encodedLineNumber, sourceFileName
        )
        return withInjectedLogSite(logSite)
    }

    override fun <T : Any> with(key: MetadataKey<T>, value: T?): JvmLogger.Api {
        delegate.with(key, value)
        return this
    }

    override fun with(key: MetadataKey<Boolean>): JvmLogger.Api {
        delegate.with(key)
        return this
    }

    override fun withStackTrace(size: StackSize): JvmLogger.Api {
        delegate.withStackTrace(size)
        return this
    }

    override fun every(n: Int): JvmLogger.Api {
        delegate.every(n)
        return this
    }

    override fun onAverageEvery(n: Int): JvmLogger.Api {
        delegate.onAverageEvery(n)
        return this
    }

    override fun atMostEvery(n: Int, unit: DurationUnit): JvmLogger.Api {
        delegate.atMostEvery(n, unit)
        return this
    }

    override fun per(key: Enum<*>?): JvmLogger.Api {
        if (key != null) {
            delegate.per(key)
        }
        return this
    }

    override fun <T> per(key: T?, strategy: LogPerBucketingStrategy<in T>): JvmLogger.Api {
        if (key != null) {
            delegate.per(key, strategy)
        }
        return this
    }

    override fun per(scopeProvider: LoggingScopeProvider): JvmLogger.Api {
        delegate.per(scopeProvider)
        return this
    }

    override fun isEnabled(): Boolean = delegate.isEnabled()

    override fun log() =
        delegate
            .withInjectedLogSite(JvmLogSite.callerOf(ApiImpl::class.java))
            .log()

    override fun log(message: () -> String?) {
        if (isEnabled()) {
            val prefix = loggingDomain.messagePrefix
            delegate
                .withInjectedLogSite(JvmLogSite.callerOf(ApiImpl::class.java))
                .log { prefix + message.invoke() }
        }
    }
}

/**
 * Maps [Level] values to its Java logging counterparts.
 *
 * @see [JLevel.toLevel]
 */
public fun Level.toJavaLogging(): JLevel = when (this) {
    Level.DEBUG -> JLevel.FINE
    Level.INFO -> JLevel.INFO
    Level.WARNING -> JLevel.WARNING
    Level.ERROR -> JLevel.SEVERE
    Level.ALL -> JLevel.ALL
    else -> ConvertedLevel(this)
}

/**
 * Opens the constructor of [JLevel] for creating converting instance.
 */
@Suppress("serial")
private class ConvertedLevel(level: Level): JLevel(level.name, level.value)

/**
 * Converts Java logging level to [Level].
 *
 * @see [Level.toJavaLogging]
 */
public fun JLevel.toLevel(): Level = when (this) {
    JLevel.FINE -> Level.DEBUG
    JLevel.INFO -> Level.INFO
    JLevel.WARNING -> Level.WARNING
    JLevel.SEVERE -> Level.ERROR
    else -> Level(name, intValue())
}

/**
 * Compares Java logging levels using their [values][JLevel.intValue].
 */
public operator fun JLevel.compareTo(other: JLevel): Int =
    intValue().compareTo(other.intValue())

/**
 * Converts this [LogSite] to the JVM logging counterpart.
 */
private fun LogSite.toFloggerSite(): JvmLogSite {
    if (this == LogSite.Invalid) {
        return JvmLogSite.invalid
    }
    return object : JvmLogSite() {
        override val className: String = this@toFloggerSite.className
        override val methodName: String = this@toFloggerSite.methodName
        override val lineNumber: Int = this@toFloggerSite.lineNumber
        override val fileName: String? = null
        override fun hashCode(): Int = this@toFloggerSite.hashCode()
        override fun equals(other: Any?): Boolean {
            if (other !is JvmLogSite) {
                return false
            }
            return lineNumber == other.lineNumber &&
                    methodName == other.methodName &&
                    className == other.className
        }
    }
}
