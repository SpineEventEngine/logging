/*
 * Copyright 2023, The Flogger Authors; 2025, TeamDev. All rights reserved.
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

/**
 * Used by [LoggingScope] and [LogSiteMap] and in response to
 * "per()" or "perUnique()" (which is an implicitly unbounded scope).
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/SpecializedLogSiteKey.java">
 *     Original Java code of Google Flogger</a> for historical context.
 */
internal class SpecializedLogSiteKey private constructor(
    private val delegate: LogSiteKey,
    private val qualifier: Any
) : LogSiteKey {

    companion object {

        @JvmStatic
        public fun of(key: LogSiteKey, qualifier: Any): LogSiteKey =
            SpecializedLogSiteKey(key, qualifier)
    }

    // Equals is dependent on the order in which specialization occurred, even though
    // conceptually it needn't be.
    override fun equals(other: Any?): Boolean {
        if (other !is SpecializedLogSiteKey) {
            return false
        }
        return delegate == other.delegate && qualifier == other.qualifier
    }

    override fun hashCode(): Int {
        // Use XOR (which is symmetric) so hash codes are not dependent on specialization order.
        return delegate.hashCode() xor qualifier.hashCode()
    }

    override fun toString(): String =
        "SpecializedLogSiteKey{ delegate='$delegate', qualifier='$qualifier' }"
}
