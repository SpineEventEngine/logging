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

/*
 * Copyright 2022, TeamDev. All rights reserved.
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

import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Intercepts logging records of the associated class.
 */
open class JulInterceptor(
    loggerName: String,
    public val level: Level
) {

    /** The `java.util.logging` logger.  */
    private val logger: Logger = Logger.getLogger(loggerName)

    /** The handler which remembers log records and performs assertions.  */
    private var handler: JulRecordingHandler? = null

    private var publishingLogger: Logger? = null

    public val records: List<LogRecord>
        get() = handler?.records ?: emptyList()

    /**
     * Installs the handler for intercepting the records.
     *
     * Current handlers are removed and remembered.
     * The logger will also not use parent handlers.
     *
     * @see .release
     */
    fun intercept() {
        handler = JulRecordingHandler(level)

        publishingLogger = findPublishingLogger(logger)
        publishingLogger?.addHandler(handler)
    }

    private fun findPublishingLogger(logger: Logger): Logger {
        if (!logger.useParentHandlers) {
            return logger
        }
        val parent = logger.parent ?: return logger
        if (parent.useParentHandlers) {
            return findPublishingLogger(parent)
        }
        return parent
    }

    /**
     * Returns the logger configuration to the previous state.
     */
    fun release() {
        if (handler == null) {
            return
        }
        publishingLogger?.removeHandler(handler)
        handler = null
        publishingLogger = null
    }

    /**
     * Obtains assertions for the accumulated log.
     *
     * @throws IllegalStateException
     *          if the interceptor is not yet [installed][intercept] or already
     *          [released][release].
     * @see [intercept]
     * @see [release]
     */
    fun recordingHandler(): JulRecordingHandler {
        check(handler != null) {
            "The handler is not available. Please call `intercept(Level)`."
        }
        return handler!!
    }
}
