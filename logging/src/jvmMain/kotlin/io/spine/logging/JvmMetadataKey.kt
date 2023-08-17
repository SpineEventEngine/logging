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

import kotlin.reflect.KClass
import kotlin.reflect.cast
import com.google.common.flogger.MetadataKey as FMetadataKey

/**
 * Flogger-based implementation of [MetadataKey].
 */
internal class JvmMetadataKey<T: Any>(
    override val label: String,
    private val clazz: KClass<out T>,
    override val canRepeat: Boolean
) :  MetadataKey<T> {

    internal val delegate: FMetadataKey<T> = KeyDelegate(label, clazz.java, canRepeat)

    companion object {

        /**
         * Creates a new single metadata key with the given label and type.
         */
        fun <T: Any> single(label: String, clazz: KClass<out T>): MetadataKey<T> =
            JvmMetadataKey(label, clazz, canRepeat = false)

        /**
         * Creates a new repeated metadata key with the given label and type.
         */
        fun <T: Any> repeated(label: String, clazz: KClass<out T>): MetadataKey<T> =
            JvmMetadataKey(label, clazz, canRepeat = true)
    }

    override fun cast(value: Any): T {
        return clazz.cast(value)
    }
}

private class KeyDelegate<T: Any>(label: String, clazz: Class<out T>, canRepeat: Boolean) :
    FMetadataKey<T>(label, clazz, canRepeat)
