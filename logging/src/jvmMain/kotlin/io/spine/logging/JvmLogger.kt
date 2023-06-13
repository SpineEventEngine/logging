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

import com.google.common.flogger.FluentLogger
import com.google.common.flogger.LogSites.callerOf
import com.google.errorprone.annotations.CheckReturnValue
import kotlin.reflect.KClass
import java.util.logging.Level as JLevel

/**
 * Implements [Logger] using [FluentLogger] as the underlying implementation.
 */
@CheckReturnValue
public class JvmLogger(
    cls: KClass<*>,
    internal val delegate: FluentLogger
) : Logger<JvmLogger.Api>(cls) {

    /**
     * The non-wildcard logging API for the [JvmLogger].
     */
    public interface Api: LoggingApi<Api>

    override fun createApi(level: Level): Api {
        val floggerApi = delegate.at(level.toJavaLogging()).let {
            if (it.isEnabled) {
                it.withInjectedLogSite(callerOf(Logger::class.java))
            } else {
                it
            }
        }
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
 * Implements [LoggingApi] wrapping [FluentLogger.Api].
 */
private class ApiImpl(private val delegate: FluentLogger.Api): JvmLogger.Api {

    private var loggingDomain: LoggingDomain? = null

    override fun withLoggingDomain(domain: LoggingDomain): JvmLogger.Api {
        this.loggingDomain = domain
        return this
    }

    override fun withCause(cause: Throwable): JvmLogger.Api {
        delegate.withCause(cause)
        return this
    }

    override fun every(n: Int): JvmLogger.Api {
        delegate.every(n)
        return this
    }

    override fun isEnabled(): Boolean = delegate.isEnabled

    override fun log() = delegate.log()

    override fun log(message: () -> String) {
        if (isEnabled()) {
            val prefix = loggingDomain.messagePrefix
            delegate.log(prefix + message.invoke())
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
