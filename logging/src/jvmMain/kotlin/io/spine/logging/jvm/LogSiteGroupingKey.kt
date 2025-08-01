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

package io.spine.logging.jvm

/**
 * The key associated with a sequence of log site "grouping keys".
 *
 * These serve to specialize the log site key to group the behaviour of stateful
 * operations like rate limiting.
 *
 * This is used by the `per()` methods and is only public so backends can
 * reference the key to control formatting.
 */
public open class LogSiteGroupingKey : MetadataKey<Any>("group_by", Any::class.java, true) {

    override fun emitRepeated(values: Iterator<Any>, kvh: KeyValueHandler) {
        if (values.hasNext()) {
            val first = values.next()
            if (!values.hasNext()) {
                kvh.handle(label, first)
            } else {
                // In the very unlikely case there is more than one aggregation key, emit a list.
                val value = buildString {
                    append('[')
                    append(first)
                    do {
                        append(',')
                        append(values.next())
                    } while (values.hasNext())
                    append(']')
                }
                kvh.handle(label, value)
            }
        }
    }
}
