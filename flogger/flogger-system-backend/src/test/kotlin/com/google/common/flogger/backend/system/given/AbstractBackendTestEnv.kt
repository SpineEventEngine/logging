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

package com.google.common.flogger.backend.system.given

import com.google.common.flogger.backend.LogData
import com.google.common.flogger.backend.system.AbstractBackend
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * An instantiatable [AbstractBackend].
 */
internal class TestBackend(logger: Logger) : AbstractBackend(logger) {

    // Faked forcing logger (because we don't get our test loggers from the LogManager).
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

    internal var wasForcingLoggerUsed = false
        private set

    override fun log(data: LogData) {
        // LogData tests are in sub-class tests.
    }

    override fun handleError(error: RuntimeException, badData: LogData) {
        // Because log() never tries to format anything, this can never be called.
        throw UnsupportedOperationException()
    }

    // Normally the forcing logger is obtained from the LogManager (so we get the sub-class
    // implementation), but in tests the Logger used is an explicit subclass that's not in the
    // LogManager hierarchy, so we have to make an explicit child logger here too.
    override fun getForcingLogger(parent: Logger): Logger {
        return ForcingLogger(parent)
    }
}


internal class TestLogger(name: String?, level: Level?) : Logger(name, null) {

    internal var logged: String? = null
        private set

    internal var published: String? = null
        private set

    init {
        setLevel(level)
        addHandler(object : Handler() {
            override fun publish(record: LogRecord) {
                published = record.message
            }

            @Suppress("EmptyFunctionBlock") // xc.
            override fun flush() {
            }

            @Suppress("EmptyFunctionBlock") // Detekt's false-positive.
            override fun close() {
            }
        })
    }

    override fun log(record: LogRecord) {
        logged = record.message
        published = null // On this stage, it is unclear whether it will be published.
        super.log(record)
    }
}
