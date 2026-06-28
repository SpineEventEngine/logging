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

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.spine.logging.Level
import io.spine.logging.LogContext
import io.spine.logging.MetadataKey
import io.spine.logging.context.Tags
import io.spine.logging.WithLogging
import io.spine.logging.backend.otel.given.NoOpSpanProcessor
import io.spine.logging.backend.otel.given.RecordingLogRecordProcessor
import io.spine.logging.backend.otel.given.StubLogData
import io.spine.logging.backend.otel.given.StubLogSite
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`OtelLoggerBackend` should")
internal class OtelLoggerBackendSpec {

    private lateinit var processor: RecordingLogRecordProcessor
    private lateinit var backend: OtelLoggerBackend
    private val lastRecord get() = processor.records.last()

    @BeforeEach
    fun setUp() {
        processor = RecordingLogRecordProcessor()
        val otel = createOpenTelemetry {
            loggerProvider {
                export { processor }
            }
        }
        OtelBackendSettings.use(otel)
        backend = OtelLoggerBackend(LOGGER_NAME)
    }

    @AfterEach
    fun tearDown() {
        // The settings holder is a global singleton; restore the default so a test
        // that injects an instance does not leak it into later tests.
        OtelBackendSettings.use(NoopOpenTelemetry)
    }

    @Test
    fun `emit a literal message as the record body`() {
        backend.log(StubLogData(LITERAL))
        lastRecord.body shouldBe LITERAL
    }

    @Test
    fun `not append the context block to the body`() {
        backend.log(StubLogData(LITERAL).addMetadata(STR_KEY, "value"))
        val body = lastRecord.body
        body shouldBe LITERAL
        (body as String) shouldNotContain "[CONTEXT"
    }

    @Test
    fun `set the severity text from the level name`() {
        backend.log(StubLogData(LITERAL).setLevel(Level.WARNING))
        lastRecord.severityText shouldBe "WARNING"
    }

    @Test
    fun `map a level to a severity number`() {
        val expected = mapOf(
            Level.FINEST to SeverityNumber.TRACE,
            Level.FINER to SeverityNumber.TRACE2,
            Level.DEBUG to SeverityNumber.DEBUG,
            Level.CONFIG to SeverityNumber.DEBUG4,
            Level.INFO to SeverityNumber.INFO,
            Level.WARNING to SeverityNumber.WARN,
            Level.ERROR to SeverityNumber.ERROR,
            Level.FATAL to SeverityNumber.FATAL,
        )
        expected.forEach { (level, severity) ->
            backend.log(StubLogData(level.name).setLevel(level))
            lastRecord.severityNumber shouldBe severity
        }
    }

    @Test
    fun `record the log site as code attributes`() {
        val site = StubLogSite("com.acme.Service", "handle", 42, "Service.kt")
        backend.log(StubLogData(LITERAL).setLogSite(site))
        with(lastRecord.attributes) {
            this["code.namespace"] shouldBe "com.acme.Service"
            this["code.function"] shouldBe "handle"
            this["code.filepath"] shouldBe "Service.kt"
            this["code.lineno"] shouldBe 42L
        }
    }

    @Test
    fun `record custom metadata as an attribute named after its label`() {
        backend.log(StubLogData(LITERAL).addMetadata(STR_KEY, "value"))
        lastRecord.attributes["str"] shouldBe "value"
    }

    @Test
    fun `collect repeated metadata into a list`() {
        backend.log(
            StubLogData(LITERAL)
                .addMetadata(INT_KEY, 1)
                .addMetadata(INT_KEY, 2)
        )
        lastRecord.attributes["int"] shouldBe listOf(1L, 2L)
    }

    @Test
    fun `record the cause as an exception, not an attribute`() {
        val cause = RuntimeException("Boom")
        backend.log(StubLogData(LITERAL).addMetadata(LogContext.Key.LOG_CAUSE, cause))
        with(lastRecord.attributes) {
            this shouldContainKey "exception.message"
            this["exception.message"] shouldBe "Boom"
            this shouldNotContainKey "cause"
        }
    }

    @Test
    fun `pass the timestamp through unchanged`() {
        backend.log(StubLogData(LITERAL).setTimestampNanos(TIMESTAMP_NANOS))
        lastRecord.timestamp shouldBe TIMESTAMP_NANOS
    }

    @Test
    fun `map boolean and double attribute values`() {
        backend.log(
            StubLogData(LITERAL)
                .addMetadata(BOOL_KEY, true)
                .addMetadata(DOUBLE_KEY, 1.5)
        )
        with(lastRecord.attributes) {
            this["enabled"] shouldBe true
            this["ratio"] shouldBe 1.5
        }
    }

    @Test
    fun `expand tags into namespaced attributes`() {
        val tags = Tags.builder()
            .addTag("flag")             // Label-only → recorded as presence.
            .addTag("user", "alice")    // Single value.
            .addTag("ids", "a")         // Multiple values → list.
            .addTag("ids", "b")
            .build()
        backend.log(StubLogData(LITERAL).addMetadata(LogContext.Key.TAGS, tags))
        with(lastRecord.attributes) {
            this["tag.flag"] shouldBe true
            this["tag.user"] shouldBe "alice"
            (this["tag.ids"] as List<*>) shouldContainExactlyInAnyOrder listOf("a", "b")
        }
    }

    @Test
    fun `record a backend error via handleError`() {
        backend.handleError(RuntimeException("boom"), StubLogData(LITERAL))
        with(lastRecord) {
            severityNumber shouldBe SeverityNumber.ERROR
            severityText shouldBe SeverityNumber.ERROR.name
            attributes shouldContainKey "spine.logging.bad_data"
            attributes["exception.message"] shouldBe "boom"
        }
    }

    @Test
    fun `correlate the record with the active span`() {
        val otel = createOpenTelemetry {
            tracerProvider { export { NoOpSpanProcessor() } }
            loggerProvider { export { processor } }
        }
        OtelBackendSettings.use(otel)
        val correlated = OtelLoggerBackend(LOGGER_NAME)
        val span = otel.tracerProvider.getTracer(LOGGER_NAME).startSpan("unit")
        val scope = otel.context.implicit().storeSpan(span).attach()
        try {
            correlated.log(StubLogData(LITERAL))
        } finally {
            scope.detach()
            span.end()
        }
        with(processor.records.last().spanContext) {
            isValid shouldBe true
            traceId shouldBe span.spanContext.traceId
            spanId shouldBe span.spanContext.spanId
        }
    }

    @Test
    fun `emit a named event when the event-name metadata is set`() {
        backend.log(StubLogData(LITERAL).addMetadata(EVENT_NAME, "checkout.completed"))
        lastRecord.eventName shouldBe "checkout.completed"
        lastRecord.attributes shouldNotContainKey "otelEventName"
    }

    @Test
    fun `emit a named event through the 'logEvent' API end-to-end`() {
        OtelBackendSettings.use(
            createOpenTelemetry { loggerProvider { export { processor } } }
        )
        EventSource().logEvent("checkout.completed")
        processor.records.last().eventName shouldBe "checkout.completed"
    }

    @Test
    fun `resolve the injected OpenTelemetry instance via the factory`() {
        OtelBackendSettings.use(
            createOpenTelemetry { loggerProvider { export { processor } } }
        )
        val created = OtelBackendFactory().create("io.spine.Foo\$Bar")
        created.loggerName shouldBe "io.spine.Foo.Bar"
        created.log(StubLogData(LITERAL))
        processor.records shouldHaveSize 1
    }

    @Test
    fun `map single numeric and non-primitive attribute values`() {
        backend.log(
            StubLogData(LITERAL)
                .addMetadata(LONG_KEY, 7L)
                .addMetadata(CHAR_KEY, 'x')
        )
        with(lastRecord.attributes) {
            this["count"] shouldBe 7L
            this["symbol"] shouldBe "x"
        }
    }

    @Test
    fun `collect repeated attribute values by type`() {
        backend.log(
            StubLogData(LITERAL)
                .addMetadata(BOOL_LIST_KEY, true).addMetadata(BOOL_LIST_KEY, false)
                .addMetadata(DOUBLE_LIST_KEY, 1.5).addMetadata(DOUBLE_LIST_KEY, 2.5)
                .addMetadata(STRING_LIST_KEY, "a").addMetadata(STRING_LIST_KEY, "b")
                .addMetadata(CHAR_LIST_KEY, 'a').addMetadata(CHAR_LIST_KEY, 'b')
        )
        with(lastRecord.attributes) {
            (this["flags"] as List<*>) shouldContainExactlyInAnyOrder listOf(true, false)
            (this["ratios"] as List<*>) shouldContainExactlyInAnyOrder listOf(1.5, 2.5)
            (this["names"] as List<*>) shouldContainExactlyInAnyOrder listOf("a", "b")
            (this["symbols"] as List<*>) shouldContainExactlyInAnyOrder listOf("a", "b")
        }
    }

    @Test
    fun `omit the line number and source file when unknown`() {
        backend.log(StubLogData(LITERAL).setLogSite(StubLogSite("C", "m", 0, null)))
        with(lastRecord.attributes) {
            this shouldNotContainKey "code.lineno"
            this shouldNotContainKey "code.filepath"
        }
    }

    @Test
    fun `report its fully-qualified class name from the factory's 'toString'`() {
        OtelBackendFactory().toString() shouldBe
            "io.spine.logging.backend.otel.OtelBackendFactory"
    }

    private class EventSource : WithLogging

    companion object {
        private const val LOGGER_NAME = "io.spine.LoggerName"
        private const val LITERAL = "Hello world"
        private const val TIMESTAMP_NANOS = 1_234_567_890L
        private val STR_KEY = MetadataKey.single<String>("str")
        private val INT_KEY = MetadataKey.repeated<Int>("int")
        private val BOOL_KEY = MetadataKey.single<Boolean>("enabled")
        private val DOUBLE_KEY = MetadataKey.single<Double>("ratio")
        private val LONG_KEY = MetadataKey.single<Long>("count")
        private val CHAR_KEY = MetadataKey.single<Char>("symbol")
        private val BOOL_LIST_KEY = MetadataKey.repeated<Boolean>("flags")
        private val DOUBLE_LIST_KEY = MetadataKey.repeated<Double>("ratios")
        private val STRING_LIST_KEY = MetadataKey.repeated<String>("names")
        private val CHAR_LIST_KEY = MetadataKey.repeated<Char>("symbols")
    }
}
