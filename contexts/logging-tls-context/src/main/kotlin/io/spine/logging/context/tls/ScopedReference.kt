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

package io.spine.logging.context.tls

import java.util.concurrent.atomic.AtomicReference
import java.util.function.BinaryOperator

/**
 * A reference to a value kept as [AtomicReference] which provides
 * a [merging][mergeFrom] operation.
 */
internal abstract class ScopedReference<T>(initialValue: T?) {

    private val value: AtomicReference<T?>

    init {
        value = AtomicReference(initialValue)
    }

    /**
     * Obtains the current value.
     */
    fun get(): T? = value.get()

    /**
     * Merges the given [delta] into the referenced value.
     *
     * If the current value is `null` the [delta] becomes the new value.
     * Otherwise, it is [merged][merge] with the current one.
     */
    fun mergeFrom(delta: T?) {
        if (delta != null) {
            val operator = BinaryOperator<T?> { t, u ->
                t?.let { merge(it, u) } ?: u
            }
            value.accumulateAndGet(delta, operator)
        }
    }

    /**
     * Merges the [current] value with the [delta].
     *
     * The implementing functions must have no side effects.
     */
    abstract fun merge(current: T, delta: T): T
}
