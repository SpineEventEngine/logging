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

package io.spine.logging.backend.system;

import io.spine.annotation.VisibleForTesting;
import io.spine.logging.backend.jul.JulBackendFactory;
import io.spine.logging.backend.BackendFactory;
import io.spine.logging.backend.Clock;
import io.spine.logging.backend.LoggerBackend;
import io.spine.logging.backend.Platform;
import io.spine.logging.backend.LogCallerFinder;
import io.spine.logging.jvm.context.ContextDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static io.spine.logging.util.StaticMethodCaller.getInstanceFromSystemProperty;

/**
 * The default logger platform for a server-side Java environment.
 *
 * <p>This class allows configuration via a number of service types. A single instance of each
 * service type may be provided, either via the classpath using <i>service providers</i> (see {@link
 * ServiceLoader}) or by system property. For most users, configuring one of these should just
 * require including the appropriate dependency.
 *
 * <p>If set, the system property for each service type takes precedence over any implementations
 * that may be found on the classpath. The value of the system property is expected to be of one of
 * two forms:
 *
 * <ul>
 *   <li><b>A fully-qualified class name:</b> In this case, the platform will attempt to get an
 *       instance of that class by invoking the public no-arg constructor. If the class defines a
 *       public static no-arg {@code getInstance} method, the platform will call that instead.
 *       <b>Note:</b> Support for {@code getInstance} is only provided to facilitate transition
 *       from older service implementations that include a {@code getInstance} method and will
 *       likely be removed in the future.
 *   <li><b>A fully-qualified class name followed by "#" and the name of a static method:</b>
 *       In this case, the platform will attempt to get an instance of that class by invoking
 *       either the named no-arg static method or the public no-arg constructor.
 *       <b>Note:</b> This option exists only for compatibility with previous Flogger behavior and
 *       may be removed in the future; service implementations should prefer providing a no-arg
 *       public constructor rather than a static method, and system properties should prefer
 *       only including the class name.
 * </ul>
 *
 * <p>The services used by this platform are the following:
 *
 * <pre>
 * | Service Type            | System Property            | Default                                    |
 * |------------------------|---------------------------|---------------------------------------------|
 * | {@link BackendFactory} | {@code flogger.backend_factory} | {@link JulBackendFactory}                   |
 * | {@link ContextDataProvider} | {@code flogger.logging_context} | A no-op {@code ContextDataProvider}         |
 * | {@link Clock}          | {@code flogger.clock}           | {@link SystemClock}, a millisecond-precision clock |
 * </pre>
 *
 * @see <a href="http://rb.gy/nnjac">Original Java code of Google Flogger</a> for historical context.
 */
// Non-final for testing.
public class DefaultPlatform extends Platform {

    // System property names for properties expected to define "getters" for platform attributes.
    private static final String BACKEND_FACTORY = "flogger.backend_factory";
    private static final String CONTEXT_DATA_PROVIDER = "flogger.logging_context";
    private static final String CLOCK = "flogger.clock";

    private final BackendFactory backendFactory;
    private final ContextDataProvider context;
    private final Clock clock;
    private final LogCallerFinder callerFinder;

    public DefaultPlatform() {
        // To avoid eagerly loading the default implementations of each service when they might not
        // be required, we return null from the loadService() method rather than accepting a default
        // instance. This avoids a bunch of potentially unnecessary static initialization.
        var backendFactory = loadService(BackendFactory.class, BACKEND_FACTORY);
        this.backendFactory = backendFactory != null ? backendFactory : new JulBackendFactory();

        var contextDataProvider =
                loadService(ContextDataProvider.class, CONTEXT_DATA_PROVIDER);
        this.context =
                contextDataProvider !=
                        null ? contextDataProvider : ContextDataProvider.getNoOpProvider();

        var clock = loadService(Clock.class, CLOCK);
        this.clock = clock != null ? clock : SystemClock.getInstance();

        this.callerFinder = StackBasedCallerFinder.getInstance();
    }

    /**
     * Attempts to load an implementation of the given {@code serviceType}:
     *
     * <ol>
     *   <li>First looks for an implementation specified by the value of the given {@code
     *       systemProperty}, if that system property is set correctly. If the property is set but
     *       can't be used to get an instance of the service type, prints an error and returns {@code
     *       null}.
     *   <li>Then attempts to load an implementation from the classpath via {@code ServiceLoader}, if
     *       there is exactly one. If there is more than one, prints an error and returns {@code
     *       null}.
     *   <li>If neither is present, returns {@code null}.
     * </ol>
     */
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    @Nullable
    private static <S> S loadService(Class<S> serviceType, String systemProperty) {
        // TODO(cgdecker): Throw an exception if configuration is present but invalid?
        // - If the system property is set but using it to get the service fails.
        // - If the system property is not set and more than one service is loaded by ServiceLoader
        // If no configuration is present, falling back to the default makes sense, but when invalid
        // configuration is present it may be best to attempt to fail fast.
        var service = getInstanceFromSystemProperty(systemProperty, serviceType);
        if (service != null) {
            // Service was loaded successfully via an explicitly overridden system property.
            return service;
        }

        List<S> loadedServices = new ArrayList<>();
        for (var loaded : ServiceLoader.load(serviceType)) {
            loadedServices.add(loaded);
        }

        return switch (loadedServices.size()) {
            // Normal use of the default service when nothing else exists.
            case 0 -> null;
            // A single service implementation was found and loaded automatically.
            case 1 -> loadedServices.get(0);
            default -> { System.err.printf(
                    "Multiple implementations of service %s found on the classpath: %s%n"
                    + "Ensure only the service implementation you want to use is included on the "
                    + "classpath or else specify the service class at startup with the '%s' system "
                    + "property. The default implementation will be used instead.%n",
                    serviceType.getName(), loadedServices, systemProperty
                );
                yield null;
            }
        };
    }

    @VisibleForTesting
    DefaultPlatform(
            BackendFactory factory,
            ContextDataProvider context,
            Clock clock,
            LogCallerFinder callerFinder) {
        this.backendFactory = factory;
        this.context = context;
        this.clock = clock;
        this.callerFinder = callerFinder;
    }

    @Override
    protected LogCallerFinder getCallerFinderImpl() {
        return callerFinder;
    }

    @Override
    protected LoggerBackend getBackendImpl(String className) {
        return backendFactory.create(className);
    }

    @Override
    protected ContextDataProvider getContextDataProviderImpl() {
        return context;
    }

    @Override
    protected long getCurrentTimeNanosImpl() {
        return clock.getCurrentTimeNanos();
    }

    @SuppressWarnings("HardcodedLineSeparator")
    @Override
    protected String getConfigInfoImpl() {
        return "Platform: " + getClass().getName() + '\n'
                + "BackendFactory: " + backendFactory + '\n'
                + "Clock: " + clock + '\n'
                + "ContextDataProvider: " + context + '\n'
                + "LogCallerFinder: " + callerFinder + '\n';
    }
}
