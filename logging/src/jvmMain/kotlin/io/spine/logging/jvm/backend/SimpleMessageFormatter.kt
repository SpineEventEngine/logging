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

package io.spine.logging.jvm.backend

import io.spine.logging.jvm.MetadataKey

/**
 * A simple message formatter that handles only literal strings without complex formatting.
 *
 * This formatter replaces the previous complex formatting system and only supports
 * lazily evaluated messages through `() -> String?` functions.
 */
public class SimpleMessageFormatter private constructor() : LogMessageFormatter() {

    override fun append(
        logData: LogData,
        metadata: MetadataProcessor,
        buffer: StringBuilder
    ): StringBuilder {
        // Append the literal message
        buffer.append(logData.literalArgument ?: "")
        
        // Append metadata if present
        if (metadata.keyCount() > 0) {
            buffer.append(" [CONTEXT ")
            val metadataEntries = mutableListOf<String>()
            metadata.process(METADATA_HANDLER, metadataEntries)
            buffer.append(metadataEntries.joinToString(" "))
            buffer.append(" ]")
        }
        
        return buffer
    }

    public companion object {

        private val DEFAULT_FORMATTER = SimpleMessageFormatter()

        /**
         * Metadata handler for formatting key-value pairs.
         */
        private val METADATA_HANDLER = object : MetadataHandler<MutableList<String>>() {
            override fun <T : Any> handle(
                key: MetadataKey<T>, 
                value: T, 
                context: MutableList<String>
            ) {
                val stringified = "${key.label}=${formatValue(value)}"
                context.add(stringified)
            }

            override fun <T : Any> handleRepeated(
                key: MetadataKey<T>, 
                values: Iterator<T>, 
                context: MutableList<String>
            ) {
                // Convert to list to handle single vs multiple values
                val valueList = values.asSequence().map(::formatValue).toList()
                val formattedValue = when (valueList.size) {
                    0 -> "[]"  // Empty list
                    1 -> valueList[0]  // Single value without brackets
                    else -> valueList.toString()  // Multiple values as [value1, value2, ...]
                }
                val stringified = "${key.label}=$formattedValue"
                context.add(stringified)
            }
        }

        /**
         * Formats a value for metadata output, adding quotes around strings if needed.
         */
        private fun formatValue(value: Any): String {
            return when (value) {
                is String -> "\"$value\""
                else -> value.toString()
            }
        }

        /**
         * Returns the default formatter instance.
         */
        @JvmStatic
        public fun getDefaultFormatter(): LogMessageFormatter = DEFAULT_FORMATTER
    }
}
