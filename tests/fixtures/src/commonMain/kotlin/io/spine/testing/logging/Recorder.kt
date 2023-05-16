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

package io.spine.testing.logging

import io.spine.logging.Level

public fun checkLogging(recorder: Recorder, block: Recorder.() -> Unit) {
    try {
        recorder.start()
        recorder.block()
    } finally {
        recorder.stop()
    }
}

/**
 * Records [LogData] after the method [start] called.
 *
 * Implementing classes should tap into an underlying logging framework to
 * intercept logging instructions with the [minLevel] or above.
 */
public abstract class Recorder(protected val minLevel: Level) {

    private val mutableRecords: MutableList<LogData> = mutableListOf()

    /**
     * Contains log data collected so far.
     *
     * Is always empty before [start] and after [stop].
     */
    public val records: List<LogData>
        get() = mutableRecords

    protected fun append(data: LogData) {
        mutableRecords.add(data)
    }

    /**
     * Removes records accumulated by the time of the call.
     */
    protected fun clear(): Unit = mutableRecords.clear()

    /**
     * Starts the recording.
     *
     * @see [stop]
     */
    public abstract fun start()

    /**
     * Stops the recording.
     *
     * @see [start]
     */
    public abstract fun stop()
}
