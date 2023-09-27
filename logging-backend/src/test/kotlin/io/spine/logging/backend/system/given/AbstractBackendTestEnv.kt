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

package io.spine.logging.backend.system.given

import io.spine.logging.flogger.backend.LogData
import io.spine.logging.backend.system.AbstractBackend
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * An instantiatable [AbstractBackend] that remembers the fact
 * of usage of [ForcingLogger].
 */
internal class MemoizingBackend(logger: Logger) : AbstractBackend(logger) {

    /**
     * Tells whether the last call to [AbstractBackend.log]
     * used a forcing logger.
     */
    internal var wasForcingLoggerUsed = false
        private set

    /**
     * This method is not used by [AbstractBackend].
     *
     * [LogData] tests are in `SimpleBackendLoggerSpec`.
     */
    override fun log(data: LogData) {
        // no-op
    }

    /**
     * This class never tries to format anything in [MemoizingLogger.log],
     * so this method is not expected to be ever called.
     */
    override fun handleError(error: RuntimeException, badData: LogData) {
        throw UnsupportedOperationException()
    }

    /**
     * Explicitly creates a forcing child logger.
     *
     * Normally, the forcing logger is obtained from [LogManager][java.util.logging.LogManager].
     * But in tests, the used [MemoizingLogger] is an explicit subclass of [Logger],
     * that is not a part of log manager's hierarchy. So, we have to create
     * an explicit forcing child logger too.
     */
    override fun getForcingLogger(parent: Logger): Logger {
        return ForcingLogger(parent)
    }

    /**
     * An explicit forcing logger that notifies the outer [MemoizingBackend]
     * that it was used.
     */
    private inner class ForcingLogger(parent: Logger) :
        Logger(parent.name + ".__forced__", null) {

        init {
            setParent(parent)
        }

        override fun log(record: LogRecord) {
            wasForcingLoggerUsed = true
            super.log(record)
        }
    }
}

/**
 * A Java logger that remembers the captured and published log messages.
 *
 * A message is [captured] when it arrives to [Logger.log] method.
 * Then, if it passes [Logger.log] and arrives to handlers, it is [published].
 */
internal class MemoizingLogger(name: String, level: Level) : Logger(name, null) {

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
