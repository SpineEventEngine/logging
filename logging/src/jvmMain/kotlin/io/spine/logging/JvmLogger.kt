/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import io.spine.logging.flogger.FluentLogger2
import io.spine.logging.flogger.LogSites.callerOf
import com.google.errorprone.annotations.CheckReturnValue
import kotlin.reflect.KClass
import kotlin.time.DurationUnit
import kotlin.time.toTimeUnit
import java.util.logging.Level as JLevel
import io.spine.logging.flogger.FloggerLogSite as FloggerLogSite

/**
 * Implements [Logger] using [FluentLogger2] as the underlying implementation.
 */
@CheckReturnValue
public class JvmLogger(
    cls: KClass<*>,
    internal val delegate: FluentLogger2
) : Logger<JvmLogger.Api>(cls) {

    /**
     * The non-wildcard logging API for the [JvmLogger].
     */
    public interface Api: LoggingApi<Api>

    override fun createApi(level: Level): Api {
        val floggerApi = delegate.at(level.toJavaLogging())
        return if (floggerApi.isEnabled) {
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
 * Implements [LoggingApi] wrapping [FluentLogger2.Api].
 */
private class ApiImpl(private val delegate: FluentLogger2.Api): JvmLogger.Api {

    private var loggingDomain: LoggingDomain? = null

    override fun withLoggingDomain(domain: LoggingDomain): JvmLogger.Api {
        this.loggingDomain = domain
        return this
    }

    override fun withCause(cause: Throwable): JvmLogger.Api {
        delegate.withCause(cause)
        return this
    }

    override fun withInjectedLogSite(logSite: LogSite): JvmLogger.Api {
        val floggerLogSite = logSite.toFloggerSite()
        delegate.withInjectedLogSite(floggerLogSite)
        return this
    }

    override fun every(n: Int): JvmLogger.Api {
        delegate.every(n)
        return this
    }

    override fun atMostEvery(n: Int, unit: DurationUnit): JvmLogger.Api {
        val javaTimeUnit = unit.toTimeUnit()
        delegate.atMostEvery(n, javaTimeUnit)
        return this
    }

    override fun per(key: Enum<*>): JvmLogger.Api {
        delegate.per(key)
        return this
    }

    override fun isEnabled(): Boolean = delegate.isEnabled

    override fun log() {
        delegate.withInjectedLogSite(callerOf(ApiImpl::class.java))
            .log()
    }

    override fun log(message: () -> String) {
        if (isEnabled()) {
            val prefix = loggingDomain.messagePrefix
            delegate.withInjectedLogSite(callerOf(ApiImpl::class.java))
                .log(prefix + message.invoke())
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
 * Converts this [LogSite] to Flogger's counterpart.
 */
private fun LogSite.toFloggerSite(): FloggerLogSite {
    if (this == LogSite.INVALID) {
        return FloggerLogSite.INVALID
    }
    return object : FloggerLogSite() {
        override fun getClassName(): String = this@toFloggerSite.className
        override fun getMethodName(): String = this@toFloggerSite.methodName
        override fun getLineNumber(): Int = this@toFloggerSite.lineNumber
        override fun getFileName(): String? = null
        override fun hashCode(): Int = this@toFloggerSite.hashCode()
        override fun equals(other: Any?): Boolean {
            if (other !is FloggerLogSite) {
                return false
            }
            return lineNumber == other.lineNumber &&
                    methodName == other.methodName &&
                    className == other.className
        }
    }
}
