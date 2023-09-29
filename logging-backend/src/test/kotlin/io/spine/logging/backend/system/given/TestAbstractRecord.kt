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
import io.spine.logging.flogger.backend.LogMessageFormatter
import io.spine.logging.flogger.backend.Metadata
import io.spine.logging.flogger.backend.MetadataProcessor
import io.spine.logging.flogger.backend.SimpleMessageFormatter
import io.spine.logging.backend.system.AbstractLogRecord
import io.spine.logging.flogger.backend.given.FakeLogData

/**
 * An instantiatable [AbstractLogRecord].
 *
 * It uses its own formatter to make sure the abstract methods
 * are indeed called when expected.
 */
@Suppress("serial") // Serial number is not needed.
internal class TestAbstractRecord(message: String, vararg args: Any?) :
    AbstractLogRecord(FakeLogData.withPrintfStyle(message, *args), Metadata.empty()) {

    private val formatter = TestLogMessageFormatter()

    override fun getLogMessageFormatter(): LogMessageFormatter = formatter
}

private class TestLogMessageFormatter : LogMessageFormatter() {

    private val defaultFormatter = SimpleMessageFormatter.getDefaultFormatter()

    override fun append(
        logData: LogData,
        metadata: MetadataProcessor,
        out: StringBuilder
    ): StringBuilder {
        out.append("Appended: ")
        defaultFormatter.append(logData, metadata, out)
        return out
    }

    override fun format(logData: LogData, metadata: MetadataProcessor): String =
        "Copied: " + defaultFormatter.format(logData, metadata)
}
