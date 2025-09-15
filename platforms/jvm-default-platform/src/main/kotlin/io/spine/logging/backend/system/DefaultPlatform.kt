/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.logging.backend.system

import io.spine.annotation.TestOnly
import io.spine.annotation.VisibleForTesting
import io.spine.logging.backend.BackendFactory
import io.spine.logging.backend.Clock
import io.spine.logging.backend.LogCallerFinder
import io.spine.logging.backend.LoggerBackend
import io.spine.logging.backend.Platform
import io.spine.logging.backend.jul.JulBackendFactory
import io.spine.logging.context.ContextDataProvider
import java.util.ServiceLoader

/**
 * The default logger platform for a server-side Java environment.
 *
 * This class allows configuration via a number of service types.
 * A single instance of each service type may be provided, either via the classpath
 * using service providers (see [ServiceLoader]) or by system property. For most users,
 * configuring one of these should just require including the appropriate dependency.
 *
 * If set, the system property for each service type takes precedence over any
 * implementations that may be found on the classpath.
 * The value of the system property is expected to be of one of two forms:
 *
 *  - A fully-qualified class name: In this case, the platform will attempt to get
 *    an instance of that class by invoking the public no-arg constructor.
 *    If the class defines a public static no-arg `getInstance` method,
 *    the platform will call that instead.
 *
 *  - A fully-qualified class name followed by "#" and the name of a static method:
 *    In this case, the platform will attempt to get an instance of that class by
 *    invoking either the named no-arg static method or the public no-arg constructor.
 *
 * The services used by this platform are the following:
 *  - [BackendFactory] (system property `flogger.backend_factory`) — [JulBackendFactory] by default.
 *  - [ContextDataProvider] (system property `flogger.logging_context`) — No-op provider by default.
 *  - [Clock] (system property `flogger.clock`) — [SystemClock],
 *    a millisecond-precision clock by default.
 */
public open class DefaultPlatform : Platform {

    private val backendFactory: BackendFactory
    private val context: ContextDataProvider
    private val clock: Clock
    private val callerFinder: LogCallerFinder

    public constructor() {
        // Avoid eager loading of default implementations when not required.
        val backendFactory = loadService(BackendFactory::class.java, BACKEND_FACTORY)
        this.backendFactory = backendFactory ?: JulBackendFactory()

        val contextDataProvider =
            loadService(ContextDataProvider::class.java, CONTEXT_DATA_PROVIDER)
        this.context = contextDataProvider ?: ContextDataProvider.getNoOpProvider()

        val clock = loadService(Clock::class.java, CLOCK)
        this.clock = clock ?: SystemClock.getInstance()

        this.callerFinder = StackBasedCallerFinder.getInstance()
    }

    @VisibleForTesting
    public constructor(
        factory: BackendFactory,
        context: ContextDataProvider,
        clock: Clock,
        callerFinder: LogCallerFinder
    ) {
        this.backendFactory = factory
        this.context = context
        this.clock = clock
        this.callerFinder = callerFinder
    }

    override fun getCallerFinderImpl(): LogCallerFinder = callerFinder

    @TestOnly
    internal fun doGetCallerFinderImpl(): LogCallerFinder =
        getCallerFinderImpl()

    override fun getBackendImpl(className: String): LoggerBackend = backendFactory.create(className)

    @TestOnly
    internal fun doGetBackendImpl(className: String): LoggerBackend =
        getBackendImpl(className)

    override fun getContextDataProviderImpl(): ContextDataProvider = context

    @TestOnly
    internal fun doGetContextDataProviderImpl(): ContextDataProvider =
        getContextDataProviderImpl()

    override fun getCurrentTimeNanosImpl(): Long = clock.getCurrentTimeNanos()

    @TestOnly
    internal fun doGetCurrentTimeNanosImpl(): Long =
        getCurrentTimeNanosImpl()

    override fun getConfigInfoImpl(): String = buildString {
        append("Platform: ").append(this@DefaultPlatform.javaClass.name).append('\n')
        append("BackendFactory: ").append(backendFactory).append('\n')
        append("Clock: ").append(clock).append('\n')
        append("ContextDataProvider: ").append(context).append('\n')
        append("LogCallerFinder: ").append(callerFinder).append('\n')
    }

    @TestOnly
    internal fun doGetConfigInfoImpl(): String =
        getConfigInfoImpl()

    public companion object {
        private const val BACKEND_FACTORY = "flogger.backend_factory"
        private const val CONTEXT_DATA_PROVIDER = "flogger.logging_context"
        private const val CLOCK = "flogger.clock"

        @JvmStatic
        private fun <S> loadService(serviceType: Class<S>, systemProperty: String): S? {
            // First, try system property-specified implementation.
            val fromProp =
                StaticMethodCaller.getInstanceFromSystemProperty(systemProperty, serviceType)
            if (fromProp != null) return fromProp

            // Next, attempt to load via ServiceLoader. If exactly one, return it;
            // if many, warn; else null.
            val loaded = ServiceLoader.load(serviceType).toList()
            return when (loaded.size) {
                0 -> null
                1 -> loaded[0]
                else -> {
                    System.err.printf(
                        "Multiple implementations of service %s found on the classpath: %s%n" +
                        "Ensure only the service implementation you want to use is included" +
                        " on the classpath or else specify the service class at startup" +
                        " with the '%s' system property." +
                        " The default implementation will be used instead.%n",
                        serviceType.name, loaded, systemProperty
                    )
                    null
                }
            }
        }
    }
}
