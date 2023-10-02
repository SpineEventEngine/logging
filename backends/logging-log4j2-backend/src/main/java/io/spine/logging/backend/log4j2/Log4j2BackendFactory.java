/*
 * Copyright 2019, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.backend.log4j2;

import io.spine.logging.flogger.backend.LoggerBackend;
import io.spine.logging.backend.system.BackendFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

/**
 * Backend factory for Log4j2.
 *
 * <p>When using {@link io.spine.logging.backend.system.DefaultPlatform DefaultPlatform},
 * this factory will automatically be used if it is included on the classpath,
 * and no other implementation of {@code BackendFactory} (other than the default
 * implementation) is present.
 *
 * <p>To specify it more explicitly or to work around an issue where multiple
 * backend implementations are on the classpath, you can set {@code flogger.backend_factory}
 * system property to {@code io.spine.logging.backend.log4j2.Log4j2BackendFactory}.
 */
public final class Log4j2BackendFactory extends BackendFactory {

  // Must be public for ServiceLoader
  public Log4j2BackendFactory() {}

  @Override
  public LoggerBackend create(String loggingClassName) {

    // Compute the logger name the same way as in `SimpleBackendFactory`.
    var name = loggingClassName.replace('$', '.');

    // There is `log4j.Logger` interface and `log4j.core.Logger` implementation.
    // Implementation exposes more methods that are needed by the backend.
    // So, we have to cast an interface back to its implementation.
    var logger = (Logger) LogManager.getLogger(name);

    return new Log4j2LoggerBackend(logger);
  }

  /**
   * Returns a fully-qualified name of this class.
   */
  @Override
  public String toString() {
    return getClass().getName();
  }
}
