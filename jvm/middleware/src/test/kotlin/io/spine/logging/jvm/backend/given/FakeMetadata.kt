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

package io.spine.logging.jvm.backend.given

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.logging.jvm.MetadataKey
import io.spine.logging.jvm.backend.Metadata

/**
 * A mutable [Metadata] implementation for testing logging backends
 * and other log handling code.
 *
 * @see <a href="http://rb.gy/h75mb">Original Java code of Google Flogger</a> for historical context.
 */
class FakeMetadata : Metadata() {

    private class KeyValuePair<T : Any>(val key: MetadataKey<T>, val value: T)

    private val entries = mutableListOf<KeyValuePair<*>>()

    /**
     * Adds a key/value pair to this [Metadata].
     */
    @CanIgnoreReturnValue
    fun <T : Any> add(key: MetadataKey<T>, value: T): FakeMetadata {
        entries.add(KeyValuePair(key, value))
        return this
    }

    override fun size(): Int = entries.size

    override fun getKey(n: Int): MetadataKey<*> = entries[n].key

    override fun getValue(n: Int): Any = entries[n].value

    override fun <T : Any> findValue(key: MetadataKey<T>): T? {
        val entry = entries.firstOrNull { it.key == key }
        val casted = key.cast(entry?.value) // It is safe to pass `null` here.
        return casted
    }
}
