/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.jul.given

import io.spine.logging.Level
import io.spine.logging.toJavaLogging
import java.util.logging.Handler
import java.util.logging.Level as JLevel
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * A Java logger that remembers the captured and published log messages.
 *
 * A message is [captured] when it arrives to [Logger.log] method.
 * Then, if it passes [Logger.log] and arrives to handlers, it is [published].
 */
internal class MemoizingLogger(name: String, level: JLevel) : Logger(name, null) {

    /**
     * Creates a new instance with the given [name] and [level].
     */
    constructor(name: String, level: Level) : this(name, level.toJavaLogging())

    /**
     * Contains the message from the last call to [MemoizingLogger.log].
     */
    var captured: String? = null
        private set

    /**
     * Contains the message from the last call to handlers of this logger.
     *
     * This property would have a message only if it has been passed down
     * to handlers by [Logger.log] method.
     */
    var published: String? = null
        private set

    init {
        val handler = TestHandler()
        setLevel(level)
        addHandler(handler)
    }

    override fun log(record: LogRecord) {
        captured = record.message
        super.log(record)
    }

    /**
     * A handler that returns the message back to the outer [MemoizingLogger].
     */
    private inner class TestHandler : Handler() {

        override fun publish(record: LogRecord) {
            published = record.message
        }

        override fun flush() {
            // no-op
        }

        override fun close() {
            // no-op
        }
    }
}
