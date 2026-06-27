/*
 * Copyright 2026, TeamDev. All rights reserved.
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

@file:OptIn(ExperimentalApi::class)

package io.spine.logging.backend.otel

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.spine.logging.LogContext
import io.spine.logging.backend.LogData
import io.spine.logging.backend.MetadataHandler
import io.spine.logging.backend.MetadataProcessor
import io.spine.logging.context.Tags

/**
 * The prefix applied to Spine metadata keys so they never collide with
 * OpenTelemetry semantic-convention attribute names.
 */
private const val SPINE_PREFIX = "spine."

/**
 * The prefix applied to individual [Tags] entries.
 */
private const val TAG_PREFIX = "spine.tag."

/**
 * Records the log site of the given [data] as OpenTelemetry `code.*`
 * semantic-convention attributes.
 */
internal fun AttributesMutator.putLogSite(data: LogData) {
    val site = data.logSite
    setStringAttribute("code.namespace", site.className)
    setStringAttribute("code.function", site.methodName)
    site.fileName?.let { setStringAttribute("code.filepath", it) }
    if (site.lineNumber > 0) {
        setLongAttribute("code.lineno", site.lineNumber.toLong())
    }
}

/**
 * Records the scope and log-site [metadata] as attributes.
 *
 * The throwable carried under [LogContext.Key.LOG_CAUSE] is ignored here,
 * as it is emitted as the log record's exception. [Tags] are expanded into
 * individual `spine.tag.<name>` attributes; all other keys are namespaced
 * under `spine.<label>`.
 */
internal fun AttributesMutator.putMetadata(metadata: MetadataProcessor) {
    metadata.process(attributeHandler, this)
}

private val attributeHandler: MetadataHandler<AttributesMutator> =
    MetadataHandler.builder<AttributesMutator> { key, value, mutator ->
        mutator.putValue(SPINE_PREFIX + key.label, value)
    }.setDefaultRepeatedHandler { key, values, mutator ->
        mutator.putValues(SPINE_PREFIX + key.label, values)
    }.addHandler(LogContext.Key.TAGS) { _, tags, mutator ->
        mutator.putTags(tags)
    }.ignoring(
        // The cause is emitted as the record's exception, not as an attribute.
        LogContext.Key.LOG_CAUSE
    ).build()

private fun AttributesMutator.putValue(name: String, value: Any) {
    when (value) {
        is Boolean -> setBooleanAttribute(name, value)
        is Byte, is Short, is Int, is Long -> setLongAttribute(name, (value as Number).toLong())
        is Float, is Double -> setDoubleAttribute(name, (value as Number).toDouble())
        is String -> setStringAttribute(name, value)
        else -> setStringAttribute(name, value.toString())
    }
}

private fun AttributesMutator.putValues(name: String, values: Iterator<Any>) {
    val list = values.asSequence().toList()
    if (list.isEmpty()) {
        return
    }
    when (list.first()) {
        is Boolean -> setBooleanListAttribute(name, list.map { it as Boolean })
        is Byte, is Short, is Int, is Long ->
            setLongListAttribute(name, list.map { (it as Number).toLong() })
        is Float, is Double ->
            setDoubleListAttribute(name, list.map { (it as Number).toDouble() })
        is String -> setStringListAttribute(name, list.map { it as String })
        else -> setStringListAttribute(name, list.map { it.toString() })
    }
}

private fun AttributesMutator.putTags(tags: Tags) {
    for ((name, values) in tags.asMap()) {
        val attributeKey = TAG_PREFIX + name
        val present = values.filterNotNull()
        when {
            // A label-only tag carries no value; record its presence.
            present.isEmpty() -> setBooleanAttribute(attributeKey, true)
            present.size == 1 -> putValue(attributeKey, present.first())
            // A tag may hold values of mixed types; render them as strings.
            else -> setStringListAttribute(attributeKey, present.map { it.toString() })
        }
    }
}
