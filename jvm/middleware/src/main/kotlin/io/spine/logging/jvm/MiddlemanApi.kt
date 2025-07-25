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

@file:Suppress("LongParameterList")

package io.spine.logging.jvm

import io.spine.logging.jvm.util.Checks.checkNotNull
import java.util.concurrent.TimeUnit

/**
 * The basic logging API. An implementation of this API (or an extension of it) will be
 * returned by any logger and forms the basis of the fluent call chain.
 */
@Suppress("TooManyFunctions", "ComplexInterface")
public interface MiddlemanApi<API : MiddlemanApi<API>> {

    public fun withCause(cause: Throwable?): API

    public fun every(n: Int): API

    public fun onAverageEvery(n: Int): API

    public fun atMostEvery(n: Int, unit: TimeUnit): API

    public fun isEnabled(): Boolean

    public fun log()

    public fun log(msg: String?)

    public fun <T> per(key: T?, strategy: LogPerBucketingStrategy<in T>): API

    public fun per(key: Enum<*>?): API

    public fun per(scopeProvider: LoggingScopeProvider): API

    public fun withStackTrace(size: StackSize): API

    public fun <T : Any> with(key: MetadataKey<T>, value: T?): API

    public fun with(key: MetadataKey<Boolean>): API

    public fun withInjectedLogSite(logSite: JvmLogSite?): API

    public fun withInjectedLogSite(
        internalClassName: String,
        methodName: String,
        encodedLineNumber: Int,
        sourceFileName: String?
    ): API

    public fun logVarargs(message: String, params: Array<Any?>?)

    public fun log(message: String?, p1: Any?)

    public fun log(message: String?, p1: Any?, p2: Any?)

    public fun log(message: String?, p1: Any?, p2: Any?, p3: Any?)

    public fun log(message: String?, p1: Any?, p2: Any?, p3: Any?, p4: Any?)

    public fun log(message: String?, p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?)

    public fun log(
        message: String?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?
    )

    public fun log(
        message: String?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?,
        p7: Any?
    )

    public fun log(
        message: String?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?,
        p7: Any?,
        p8: Any?
    )

    public fun log(
        message: String?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?,
        p7: Any?,
        p8: Any?,
        p9: Any?
    )

    public fun log(
        message: String?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?,
        p7: Any?,
        p8: Any?,
        p9: Any?,
        p10: Any?
    )

    public fun log(
        message: String?,
        p1: Any?,
        p2: Any?,
        p3: Any?,
        p4: Any?,
        p5: Any?,
        p6: Any?,
        p7: Any?,
        p8: Any?,
        p9: Any?,
        p10: Any?,
        vararg rest: Any?
    )

    // Single primitive parameter methods
    public fun log(message: String?, p1: Char)
    public fun log(message: String?, p1: Byte)
    public fun log(message: String?, p1: Short)
    public fun log(message: String?, p1: Int)
    public fun log(message: String?, p1: Long)

    // Two parameter methods with primitives
    public fun log(message: String?, p1: Any?, p2: Boolean)
    public fun log(message: String?, p1: Any?, p2: Char)
    public fun log(message: String?, p1: Any?, p2: Byte)
    public fun log(message: String?, p1: Any?, p2: Short)
    public fun log(message: String?, p1: Any?, p2: Int)
    public fun log(message: String?, p1: Any?, p2: Long)
    public fun log(message: String?, p1: Any?, p2: Float)
    public fun log(message: String?, p1: Any?, p2: Double)

    public fun log(message: String?, p1: Boolean, p2: Any?)
    public fun log(message: String?, p1: Char, p2: Any?)
    public fun log(message: String?, p1: Byte, p2: Any?)
    public fun log(message: String?, p1: Short, p2: Any?)
    public fun log(message: String?, p1: Int, p2: Any?)
    public fun log(message: String?, p1: Long, p2: Any?)
    public fun log(message: String?, p1: Float, p2: Any?)
    public fun log(message: String?, p1: Double, p2: Any?)

    // Primitive-primitive combinations
    public fun log(message: String?, p1: Boolean, p2: Boolean)
    public fun log(message: String?, p1: Char, p2: Boolean)
    public fun log(message: String?, p1: Byte, p2: Boolean)
    public fun log(message: String?, p1: Short, p2: Boolean)
    public fun log(message: String?, p1: Int, p2: Boolean)
    public fun log(message: String?, p1: Long, p2: Boolean)
    public fun log(message: String?, p1: Float, p2: Boolean)
    public fun log(message: String?, p1: Double, p2: Boolean)

    public fun log(message: String?, p1: Boolean, p2: Char)
    public fun log(message: String?, p1: Char, p2: Char)
    public fun log(message: String?, p1: Byte, p2: Char)
    public fun log(message: String?, p1: Short, p2: Char)
    public fun log(message: String?, p1: Int, p2: Char)
    public fun log(message: String?, p1: Long, p2: Char)
    public fun log(message: String?, p1: Float, p2: Char)
    public fun log(message: String?, p1: Double, p2: Char)

    public fun log(message: String?, p1: Boolean, p2: Byte)
    public fun log(message: String?, p1: Char, p2: Byte)
    public fun log(message: String?, p1: Byte, p2: Byte)
    public fun log(message: String?, p1: Short, p2: Byte)
    public fun log(message: String?, p1: Int, p2: Byte)
    public fun log(message: String?, p1: Long, p2: Byte)
    public fun log(message: String?, p1: Float, p2: Byte)
    public fun log(message: String?, p1: Double, p2: Byte)

    public fun log(message: String?, p1: Boolean, p2: Short)
    public fun log(message: String?, p1: Char, p2: Short)
    public fun log(message: String?, p1: Byte, p2: Short)
    public fun log(message: String?, p1: Short, p2: Short)
    public fun log(message: String?, p1: Int, p2: Short)
    public fun log(message: String?, p1: Long, p2: Short)
    public fun log(message: String?, p1: Float, p2: Short)
    public fun log(message: String?, p1: Double, p2: Short)

    public fun log(message: String?, p1: Boolean, p2: Int)
    public fun log(message: String?, p1: Char, p2: Int)
    public fun log(message: String?, p1: Byte, p2: Int)
    public fun log(message: String?, p1: Short, p2: Int)
    public fun log(message: String?, p1: Int, p2: Int)
    public fun log(message: String?, p1: Long, p2: Int)
    public fun log(message: String?, p1: Float, p2: Int)
    public fun log(message: String?, p1: Double, p2: Int)

    public fun log(message: String?, p1: Boolean, p2: Long)
    public fun log(message: String?, p1: Char, p2: Long)
    public fun log(message: String?, p1: Byte, p2: Long)
    public fun log(message: String?, p1: Short, p2: Long)
    public fun log(message: String?, p1: Int, p2: Long)
    public fun log(message: String?, p1: Long, p2: Long)
    public fun log(message: String?, p1: Float, p2: Long)
    public fun log(message: String?, p1: Double, p2: Long)

    public fun log(message: String?, p1: Boolean, p2: Float)
    public fun log(message: String?, p1: Char, p2: Float)
    public fun log(message: String?, p1: Byte, p2: Float)
    public fun log(message: String?, p1: Short, p2: Float)
    public fun log(message: String?, p1: Int, p2: Float)
    public fun log(message: String?, p1: Long, p2: Float)
    public fun log(message: String?, p1: Float, p2: Float)
    public fun log(message: String?, p1: Double, p2: Float)

    public fun log(message: String?, p1: Boolean, p2: Double)
    public fun log(message: String?, p1: Char, p2: Double)
    public fun log(message: String?, p1: Byte, p2: Double)
    public fun log(message: String?, p1: Short, p2: Double)
    public fun log(message: String?, p1: Int, p2: Double)
    public fun log(message: String?, p1: Long, p2: Double)
    public fun log(message: String?, p1: Float, p2: Double)
    public fun log(message: String?, p1: Double, p2: Double)

    /**
     * An implementation of [MiddlemanApi] which does nothing and discards all parameters.
     *
     * This class (or a subclass in the case of an extended API) should be returned whenever logging
     * is definitely disabled (e.g. when the log level is too low).
     */
    public open class NoOp<API : MiddlemanApi<API>> : MiddlemanApi<API> {

        @Suppress("UNCHECKED_CAST", "MemberNameEqualsClassName")
        protected fun noOp(): API = this as API

        override fun withInjectedLogSite(logSite: JvmLogSite?): API = noOp()

        override fun withInjectedLogSite(
            internalClassName: String,
            methodName: String,
            encodedLineNumber: Int,
            sourceFileName: String?
        ): API = noOp()

        override fun isEnabled(): Boolean = false

        override fun <T : Any> with(key: MetadataKey<T>, value: T?): API {
            checkNotNull(key, "metadata key")
            return noOp()
        }

        override fun with(key: MetadataKey<Boolean>): API {
            checkNotNull(key, "metadata key")
            return noOp()
        }

        override fun <T> per(key: T?, strategy: LogPerBucketingStrategy<in T>): API = noOp()

        override fun per(key: Enum<*>?): API = noOp()

        override fun per(scopeProvider: LoggingScopeProvider): API = noOp()

        override fun withCause(cause: Throwable?): API = noOp()

        override fun every(n: Int): API = noOp()

        override fun onAverageEvery(n: Int): API = noOp()

        override fun atMostEvery(n: Int, unit: TimeUnit): API {
            checkNotNull(unit, "time unit")
            return noOp()
        }

        override fun withStackTrace(size: StackSize): API {
            checkNotNull(size, "stack size")
            return noOp()
        }

        override fun logVarargs(message: String, params: Array<Any?>?): Unit = Unit

        override fun log(): Unit = Unit

        override fun log(msg: String?): Unit = Unit

        override fun log(message: String?, p1: Any?): Unit = Unit

        override fun log(message: String?, p1: Any?, p2: Any?): Unit = Unit

        override fun log(message: String?, p1: Any?, p2: Any?, p3: Any?): Unit = Unit

        override fun log(message: String?, p1: Any?, p2: Any?, p3: Any?, p4: Any?): Unit = Unit

        override fun log(message: String?, p1: Any?, p2: Any?, p3: Any?, p4: Any?, p5: Any?): Unit =
            Unit

        override fun log(
            message: String?,
            p1: Any?,
            p2: Any?,
            p3: Any?,
            p4: Any?,
            p5: Any?,
            p6: Any?
        ): Unit = Unit

        override fun log(
            message: String?,
            p1: Any?,
            p2: Any?,
            p3: Any?,
            p4: Any?,
            p5: Any?,
            p6: Any?,
            p7: Any?
        ): Unit = Unit

        override fun log(
            message: String?,
            p1: Any?,
            p2: Any?,
            p3: Any?,
            p4: Any?,
            p5: Any?,
            p6: Any?,
            p7: Any?,
            p8: Any?
        ): Unit = Unit

        override fun log(
            message: String?,
            p1: Any?,
            p2: Any?,
            p3: Any?,
            p4: Any?,
            p5: Any?,
            p6: Any?,
            p7: Any?,
            p8: Any?,
            p9: Any?
        ): Unit = Unit

        override fun log(
            message: String?,
            p1: Any?,
            p2: Any?,
            p3: Any?,
            p4: Any?,
            p5: Any?,
            p6: Any?,
            p7: Any?,
            p8: Any?,
            p9: Any?,
            p10: Any?
        ): Unit = Unit

        override fun log(
            message: String?,
            p1: Any?,
            p2: Any?,
            p3: Any?,
            p4: Any?,
            p5: Any?,
            p6: Any?,
            p7: Any?,
            p8: Any?,
            p9: Any?,
            p10: Any?,
            vararg rest: Any?
        ): Unit = Unit

        // Single primitive parameter methods
        override fun log(message: String?, p1: Char): Unit = Unit
        override fun log(message: String?, p1: Byte): Unit = Unit
        override fun log(message: String?, p1: Short): Unit = Unit
        override fun log(message: String?, p1: Int): Unit = Unit
        override fun log(message: String?, p1: Long): Unit = Unit

        // Two parameter methods with primitives
        override fun log(message: String?, p1: Any?, p2: Boolean): Unit = Unit
        override fun log(message: String?, p1: Any?, p2: Char): Unit = Unit
        override fun log(message: String?, p1: Any?, p2: Byte): Unit = Unit
        override fun log(message: String?, p1: Any?, p2: Short): Unit = Unit
        override fun log(message: String?, p1: Any?, p2: Int): Unit = Unit
        override fun log(message: String?, p1: Any?, p2: Long): Unit = Unit
        override fun log(message: String?, p1: Any?, p2: Float): Unit = Unit
        override fun log(message: String?, p1: Any?, p2: Double): Unit = Unit

        override fun log(message: String?, p1: Boolean, p2: Any?): Unit = Unit
        override fun log(message: String?, p1: Char, p2: Any?): Unit = Unit
        override fun log(message: String?, p1: Byte, p2: Any?): Unit = Unit
        override fun log(message: String?, p1: Short, p2: Any?): Unit = Unit
        override fun log(message: String?, p1: Int, p2: Any?): Unit = Unit
        override fun log(message: String?, p1: Long, p2: Any?): Unit = Unit
        override fun log(message: String?, p1: Float, p2: Any?): Unit = Unit
        override fun log(message: String?, p1: Double, p2: Any?): Unit = Unit

        // Primitive-primitive combinations
        override fun log(message: String?, p1: Boolean, p2: Boolean): Unit = Unit
        override fun log(message: String?, p1: Char, p2: Boolean): Unit = Unit
        override fun log(message: String?, p1: Byte, p2: Boolean): Unit = Unit
        override fun log(message: String?, p1: Short, p2: Boolean): Unit = Unit
        override fun log(message: String?, p1: Int, p2: Boolean): Unit = Unit
        override fun log(message: String?, p1: Long, p2: Boolean): Unit = Unit
        override fun log(message: String?, p1: Float, p2: Boolean): Unit = Unit
        override fun log(message: String?, p1: Double, p2: Boolean): Unit = Unit

        override fun log(message: String?, p1: Boolean, p2: Char): Unit = Unit
        override fun log(message: String?, p1: Char, p2: Char): Unit = Unit
        override fun log(message: String?, p1: Byte, p2: Char): Unit = Unit
        override fun log(message: String?, p1: Short, p2: Char): Unit = Unit
        override fun log(message: String?, p1: Int, p2: Char): Unit = Unit
        override fun log(message: String?, p1: Long, p2: Char): Unit = Unit
        override fun log(message: String?, p1: Float, p2: Char): Unit = Unit
        override fun log(message: String?, p1: Double, p2: Char): Unit = Unit

        override fun log(message: String?, p1: Boolean, p2: Byte): Unit = Unit
        override fun log(message: String?, p1: Char, p2: Byte): Unit = Unit
        override fun log(message: String?, p1: Byte, p2: Byte): Unit = Unit
        override fun log(message: String?, p1: Short, p2: Byte): Unit = Unit
        override fun log(message: String?, p1: Int, p2: Byte): Unit = Unit
        override fun log(message: String?, p1: Long, p2: Byte): Unit = Unit
        override fun log(message: String?, p1: Float, p2: Byte): Unit = Unit
        override fun log(message: String?, p1: Double, p2: Byte): Unit = Unit
        override fun log(message: String?, p1: Boolean, p2: Short): Unit = Unit
        override fun log(message: String?, p1: Char, p2: Short): Unit = Unit
        override fun log(message: String?, p1: Byte, p2: Short): Unit = Unit
        override fun log(message: String?, p1: Short, p2: Short): Unit = Unit
        override fun log(message: String?, p1: Int, p2: Short): Unit = Unit
        override fun log(message: String?, p1: Long, p2: Short): Unit = Unit
        override fun log(message: String?, p1: Float, p2: Short): Unit = Unit
        override fun log(message: String?, p1: Double, p2: Short): Unit = Unit
        override fun log(message: String?, p1: Boolean, p2: Int): Unit = Unit
        override fun log(message: String?, p1: Char, p2: Int): Unit = Unit
        override fun log(message: String?, p1: Byte, p2: Int): Unit = Unit
        override fun log(message: String?, p1: Short, p2: Int): Unit = Unit
        override fun log(message: String?, p1: Int, p2: Int): Unit = Unit
        override fun log(message: String?, p1: Long, p2: Int): Unit = Unit
        override fun log(message: String?, p1: Float, p2: Int): Unit = Unit
        override fun log(message: String?, p1: Double, p2: Int): Unit = Unit
        override fun log(message: String?, p1: Boolean, p2: Long): Unit = Unit
        override fun log(message: String?, p1: Char, p2: Long): Unit = Unit
        override fun log(message: String?, p1: Byte, p2: Long): Unit = Unit
        override fun log(message: String?, p1: Short, p2: Long): Unit = Unit
        override fun log(message: String?, p1: Int, p2: Long): Unit = Unit
        override fun log(message: String?, p1: Long, p2: Long): Unit = Unit
        override fun log(message: String?, p1: Float, p2: Long): Unit = Unit
        override fun log(message: String?, p1: Double, p2: Long): Unit = Unit
        override fun log(message: String?, p1: Boolean, p2: Float): Unit = Unit
        override fun log(message: String?, p1: Char, p2: Float): Unit = Unit
        override fun log(message: String?, p1: Byte, p2: Float): Unit = Unit
        override fun log(message: String?, p1: Short, p2: Float): Unit = Unit
        override fun log(message: String?, p1: Int, p2: Float): Unit = Unit
        override fun log(message: String?, p1: Long, p2: Float): Unit = Unit
        override fun log(message: String?, p1: Float, p2: Float): Unit = Unit
        override fun log(message: String?, p1: Double, p2: Float): Unit = Unit
        override fun log(message: String?, p1: Boolean, p2: Double): Unit = Unit
        override fun log(message: String?, p1: Char, p2: Double): Unit = Unit
        override fun log(message: String?, p1: Byte, p2: Double): Unit = Unit
        override fun log(message: String?, p1: Short, p2: Double): Unit = Unit
        override fun log(message: String?, p1: Int, p2: Double): Unit = Unit
        override fun log(message: String?, p1: Long, p2: Double): Unit = Unit
        override fun log(message: String?, p1: Float, p2: Double): Unit = Unit
        override fun log(message: String?, p1: Double, p2: Double): Unit = Unit
    }
}
