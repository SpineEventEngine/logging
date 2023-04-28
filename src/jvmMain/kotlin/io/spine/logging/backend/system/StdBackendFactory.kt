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

package io.spine.logging.backend.system

import com.google.common.flogger.backend.LogData
import com.google.common.flogger.backend.LoggerBackend
import com.google.common.flogger.backend.Platform
import com.google.common.flogger.backend.system.AbstractBackend
import com.google.common.flogger.backend.system.BackendFactory
import com.google.common.flogger.backend.system.SimpleLogRecord
import io.spine.logging.compareTo
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * A [BackendFactory] producing [LoggerBackend] which support publishing
 * of logging records according to configured [LogLevelMap][io.spine.logging.context.LogLevelMap].
 */
public class StdBackendFactory: BackendFactory() {

    public override fun create(loggingClass: String): LoggerBackend {
        return StdLoggerBackend(loggingClass)
    }

    /**
     * Returns a fully-qualified name of this class.
     */
    override fun toString(): String {
        return javaClass.name
    }
}

/**
 * A [LoggerBackend] which allows forced publishing of logging records.
 *
 * @param loggingClass
 *          a name of the logger created for this backend. A better name for the parameter
 *          would be `loggerName`, but we keep the naming consistent with the API
 *          we extend. Please also see the constructor of `AbstractBackend` which accepts
 *          `String` for the operation with the given class name.
 * @see AbstractBackend
 *
 */
internal class StdLoggerBackend(loggingClass: String): AbstractBackend(loggingClass) {

    private val logger: Logger by lazy {
        Logger.getLogger(loggerName)
    }

    override fun log(data: LogData) {
        doLog(SimpleLogRecord.create(data, Platform.getInjectedMetadata()), data.wasForced())
    }

    override fun handleError(error: RuntimeException, badData: LogData) {
        doLog(
            SimpleLogRecord.error(error, badData, Platform.getInjectedMetadata()),
            badData.wasForced()
        )
    }

    /**
     * Logs the given record using the [logger] associated with this backend.
     *
     * If [wasForced] is set, forces the publishing by increasing the level
     * of handlers of the [logger].
     *
     * ## Implementation Note
     *
     * This method is a replacement of [AbstractBackend.log], which fails to
     * handle the forcing in cases when [parent handlers][Logger.getUseParentHandlers]
     * are involved.
     *
     * When a [parent logger][Logger.getParent] has a higher level
     * (which is [Level.INFO][java.util.logging.Level.INFO] by default) its handler has
     * this level too. Because of this the method [Handler.isLoggable] filters
     * out the record.
     *
     * Therefore, a reliable method is to temporarily force the level of
     * the [Handler] when publishing. A side effect of this operation may be
     * unwanted publication of other logging records produced for the same logger
     * by other threads during the "window" of temporarily raised level.
     * But the chance of this is not very high and the downside is low.
     *
     * @see publishForced
     */
    private fun doLog(record: LogRecord, wasForced: Boolean) {
        // If not forced, do work as usually.
        if (!wasForced || isLoggable(record.level)) {
            logger.log(record)
            return
        }
        // If a filter is set, let it do its work.
        if (logger.filter?.isLoggable(record) == true) {
            return
        }
        publishForced(logger, record)
    }
}

/**
 * Publishes the given [record] using the handlers of the [logger].
 *
 * If the logger is configured to [use parent handlers][Logger.getUseParentHandlers]
 * propagates publishing to the [parent logger][Logger.getParent] too.
 */
private fun publishForced(logger: Logger, record: LogRecord) {
    for (handler in logger.handlers) {
        if (handler.level > record.level) {
            publishForced(handler, record)
        } else {
            handler.publish(record)
        }
    }
    if (logger.useParentHandlers) {
        logger.parent?.let {
            publishForced(it, record)
        }
    }
}

/**
 * Publishes the [record] temporarily forcing the level of the [handler] to
 * that of the [record].
 */
private fun publishForced(handler: Handler, record: LogRecord) {
    val prevLevel = handler.level
    try {
        handler.level = record.level
        handler.publish(record)
    } finally {
        handler.level = prevLevel
    }
}
