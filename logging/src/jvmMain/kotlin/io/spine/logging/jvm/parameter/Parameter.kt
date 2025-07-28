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

package io.spine.logging.jvm.parameter

import com.google.errorprone.annotations.Immutable
import com.google.errorprone.annotations.ThreadSafe
import io.spine.logging.jvm.backend.FormatOptions

/**
 * An abstract representation of a parameter for a message template.
 *
 * Note that this is implemented as a class (rather than via an interface) because it is very
 * helpful to have explicit checks for the index values and count to ensure we can calculate
 * reliable low bounds for the number of arguments a template can accept.
 *
 * Note that all subclasses of Parameter must be immutable and thread-safe.
 *
 * @property formatOptions The format options for this parameter.
 * @property index The index of the argument processed by this parameter.
 * @constructor Constructs a parameter to format an argument using specified formatting options.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/parameter/Parameter.java">
 *   Original Java code of Google Flogger</a> for historical context.
 */
@Immutable
@ThreadSafe
public abstract class Parameter protected constructor(
    public val formatOptions: FormatOptions,
    public val index: Int
) {
    init {
        require(index >= 0) { "Invalid index: $index" }
    }

    /**
     * Accepts a visitor and processes the argument at the parameter's index.
     *
     * This method handles three cases:
     *  1. If the argument exists and is not `null`, processes it using [accept].
     *  2. If the argument exists but is `null`, calls [ArgumentVisitor.visitNull].
     *  3. If no argument exists at the index, calls [ArgumentVisitor.visitMissing].
     *
     * @param visitor The visitor that will process the argument
     * @param args The array of arguments to process
     */
    public fun accept(visitor: ArgumentVisitor, args: Array<out Any?>) {
        if (index < args.size) {
            val value = args[index]
            if (value != null) {
                accept(visitor, value)
            } else {
                visitor.visitNull()
            }
        } else {
            visitor.visitMissing()
        }
    }

    /**
     * Processes a non-null argument value using the given visitor.
     *
     * The implementing classes implement this function to define how specific parameter types
     * process their arguments. The implementation should use appropriate visitor methods
     * based on the parameter type and formatting requirements.
     *
     * @param visitor The visitor that will process the argument value
     * @param value The argument value to be processed
     */
    protected abstract fun accept(visitor: ArgumentVisitor, value: Any)

    /**
     * The `printf` format string specified for this parameter (e.g., `"%d"` or `"%tc"`).
     */
    public abstract val format: String
}
