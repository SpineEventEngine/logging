/*
 * Copyright 2020, The Flogger Authors; 2023, TeamDev. All rights reserved.
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

package io.spine.logging.flogger.backend;

import static io.spine.logging.flogger.util.Checks.checkArgument;
import static io.spine.logging.flogger.util.Checks.checkNotNull;

import io.spine.logging.flogger.FloggerMetadataKey;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Callback API for logger backend implementations to handle metadata keys/values. The API methods
 * will be called once for each distinct key, in encounter order. Different methods are called
 * depending on whether the key is repeatable or not.
 *
 * <p>It is expected that the most convenient way to construct a metadata handler is via the {@link
 * MetadataHandler.Builder Builder} class, which lets keys be individually mapped to callbacks,
 * however the class can also just be extended to implement alternate/custom behavior.
 *
 * @param <C> the arbitrary context type.
 *
 * @see <a href="https://github.com/google/flogger/blob/cb9e836a897d36a78309ee8badf5cad4e6a2d3d8/api/src/main/java/com/google/common/flogger/backend/MetadataHandler.java">
 *     Original Java code of Google Flogger</a>
 */
public abstract class MetadataHandler<C> {
  /**
   * Handles a single metadata key/value mapping. This method is called directly for singleton non
   * repeatable) keys, but may also be called for repeated keys by the default implementation of
   * {@link #handleRepeated}. It is up to the implementation to override that method if this
   * behaviour is unwanted.
   *
   * @param key the metadata key (not necessarily a "singleton" key).
   * @param value associated metadata value.
   * @param context an arbitrary context object supplied to the process method.
   * @param <T> the key/value type.
   */
  protected abstract <T> void handle(FloggerMetadataKey<T> key, T value, C context);

  /**
   * Handles values for a repeatable metadata key. The method is called for all repeatable keys
   * (even those with only one value). The default implementation makes repeated callbacks to the
   * {@link #handle} method, in order, for each value.
   *
   * @param key the repeatable metadata key.
   * @param values a lightweight iterator over all values associated with the key. Note that this
   *     instance is read-only and must not be held beyond the scope of this callback.
   * @param context an arbitrary context object supplied to the process method.
   * @param <T> the key/value type.
   */
  protected <T> void handleRepeated(FloggerMetadataKey<T> key, Iterator<T> values, C context) {
    while (values.hasNext()) {
      handle(key, values.next(), context);
    }
  }

  /**
   * Returns a builder for a handler with the specified default callback. The default handler will
   * receive all key/value pairs from the metadata individually, which can result in repeated keys
   * being seen more than once.
   *
   * <p>A default handler is required because no handler can know the complete set of keys which
   * might be available and it is very undesirable to drop unknown keys. If default repeated values
   * should be handled together, {@link Builder#setDefaultRepeatedHandler(RepeatedValueHandler)}
   * should be called as well.
   *
   * <p>Unknown keys/values can only be handled in a generic fashion unless a given key is matched
   * to a known constant. However the entire point of this map-based handler is to avoid any need to
   * do explicit matching, so the default handler should not need to know the value type.
   *
   * @param defaultHandler the default handler for unknown keys/values.
   * @param <C> the context type.
   */
  public static <C> Builder<C> builder(ValueHandler<Object, C> defaultHandler) {
    return new Builder<C>(defaultHandler);
  }

  /**
   * API for handling metadata key/value pairs individually.
   *
   * @param <T> the key/value type.
   * @param <C> the type of the context passed to the callbacks.
   */
  public interface ValueHandler<T, C> {
    /**
     * Handles metadata values individually.
     *
     * @param key the metadata key (not necessarily a "singleton" key).
     * @param value associated metadata value.
     * @param context an arbitrary context object supplied to the process method.
     */
    void handle(FloggerMetadataKey<T> key, T value, C context);
  }

  /**
   * API for handling repeated metadata key/values in a single callback.
   *
   * @param <T> the key/value type.
   * @param <C> the type of the context passed to the callbacks.
   */
  public interface RepeatedValueHandler<T, C> {
    /**
     * Handles all repeated metadata values for a given key.
     *
     * @param key the repeatable metadata key for which this handler was registered, or an unknown
     *     key if this is the default handler.
     * @param values a lightweight iterator over all values associated with the key. Note that this
     *     instance is read-only and must not be held beyond the scope of this callback.
     * @param context an arbitrary context object supplied to the process method.
     */
    void handle(FloggerMetadataKey<T> key, Iterator<T> values, C context);
  }

  /**
   * Builder for a map-based {@link MetadataHandler} which allows handlers to be associated with
   * individual callbacks.
   *
   * @param <C> the context type.
   */
  public static final class Builder<C> {
    // Since the context is ignored, this can safely be cast to ValueHandler<Object, C>
    private static final ValueHandler<Object, Object> IGNORE_VALUE =
        new ValueHandler<Object, Object>() {
          @Override
          public void handle(FloggerMetadataKey<Object> key, Object value, Object context) {}
        };

    // Since the context is ignored, this can safely be cast to RepeatedValueHandler<Object, C>.
    private static final RepeatedValueHandler<Object, Object> IGNORE_REPEATED_VALUE =
            (key, value, context) -> { /* No op. */ };

    private final Map<FloggerMetadataKey<?>, ValueHandler<?, ? super C>> singleValueHandlers =
            new HashMap<>();
    private final Map<FloggerMetadataKey<?>, RepeatedValueHandler<?, ? super C>> repeatedValueHandlers =
            new HashMap<>();
    private final ValueHandler<Object, ? super C> defaultHandler;
    private RepeatedValueHandler<Object, ? super C> defaultRepeatedHandler = null;

    private Builder(ValueHandler<Object, ? super C> defaultHandler) {
      this.defaultHandler = checkNotNull(defaultHandler, "default handler");
    }

    /**
     * Sets a handler for any unknown repeated keys which allows values to be processed via a
     * generic {@link Iterator}. To handle repeated values against a known key with their expected
     * type, register a handler via {@link #addRepeatedHandler(FloggerMetadataKey,RepeatedValueHandler)}.
     *
     * <p>Note that if a repeated key is associated with an individual value handler (i.e. via
     * {@link #addHandler(FloggerMetadataKey,ValueHandler)}), then that will be used in preference
     * to the default handler set here.
     *
     * @param defaultHandler the default handler for unknown repeated keys/values.
     * @return the builder instance for chaining.
     */
    @CanIgnoreReturnValue
    public Builder<C> setDefaultRepeatedHandler(
        RepeatedValueHandler<Object, ? super C> defaultHandler) {
      this.defaultRepeatedHandler = checkNotNull(defaultHandler, "handler");
      return this;
    }

    /**
     * Registers a value handler for the specified key, replacing any previously registered value.
     *
     * @param key the key for which the handler should be invoked (can be a repeated key).
     * @param handler the value handler to be invoked for every value associated with the key.
     * @param <T> the key/value type.
     * @return the builder instance for chaining.
     */
    @CanIgnoreReturnValue
    public <T> Builder<C> addHandler(
            FloggerMetadataKey<T> key, ValueHandler<? super T, ? super C> handler) {
      checkNotNull(key, "key");
      checkNotNull(handler, "handler");
      repeatedValueHandlers.remove(key);
      singleValueHandlers.put(key, handler);
      return this;
    }

    /**
     * Registers a repeated value handler for the specified key, replacing any previously
     * registered value.
     *
     * @param key the repeated key for which the handler should be invoked.
     * @param handler the repeated value handler to be invoked once for all associated values.
     * @param <T> the key/value type.
     * @return the builder instance for chaining.
     */
    @CanIgnoreReturnValue
    public <T> Builder<C> addRepeatedHandler(
            FloggerMetadataKey<? extends T> key, RepeatedValueHandler<T, ? super C> handler) {
      checkNotNull(key, "key");
      checkNotNull(handler, "handler");
      checkArgument(key.canRepeat(), "key must be repeating");
      singleValueHandlers.remove(key);
      repeatedValueHandlers.put(key, handler);
      return this;
    }

    /**
     * Registers "no op" handlers for the given keys, resulting in their values being ignored.
     *
     * @param key a key to ignore in the builder.
     * @param rest additional keys to ignore in the builder.
     * @return the builder instance for chaining.
     */
    @CanIgnoreReturnValue
    public Builder<C> ignoring(FloggerMetadataKey<?> key, FloggerMetadataKey<?>... rest) {
      checkAndIgnore(key);
      for (FloggerMetadataKey<?> k : rest) {
        checkAndIgnore(k);
      }
      return this;
    }

    /**
     * Registers "no op" handlers for the given keys, resulting in their values being ignored.
     *
     * @param keys the keys to ignore in the builder.
     * @return the builder instance for chaining.
     */
    @CanIgnoreReturnValue
    public Builder<C> ignoring(Iterable<FloggerMetadataKey<?>> keys) {
      for (FloggerMetadataKey<?> k : keys) {
        checkAndIgnore(k);
      }
      return this;
    }

    <T> void checkAndIgnore(FloggerMetadataKey<T> key) {
      checkNotNull(key, "key");
      // It is more efficient to ignore a repeated key explicitly.
      if (key.canRepeat()) {
        addRepeatedHandler(key, IGNORE_REPEATED_VALUE);
      } else {
        addHandler(key, IGNORE_VALUE);
      }
    }

    /**
     * Removes any existing handlers for the given keys, returning them to the default handler(s).
     * This method is useful when making several handlers with different mappings from a single
     * builder.
     *
     * @param key a key to remove from the builder.
     * @param rest additional keys to remove from the builder.
     * @return the builder instance for chaining.
     */
    @CanIgnoreReturnValue
    public Builder<C> removeHandlers(FloggerMetadataKey<?> key, FloggerMetadataKey<?>... rest) {
      checkAndRemove(key);
      for (FloggerMetadataKey<?> k : rest) {
        checkAndRemove(k);
      }
      return this;
    }

    void checkAndRemove(FloggerMetadataKey<?> key) {
      checkNotNull(key, "key");
      singleValueHandlers.remove(key);
      repeatedValueHandlers.remove(key);
    }

    /** Returns the immutable, map-based metadata handler. */
    public MetadataHandler<C> build() {
      return new MapBasedhandler<C>(this);
    }
  }

  private static final class MapBasedhandler<C> extends MetadataHandler<C> {
    private final Map<FloggerMetadataKey<?>, ValueHandler<?, ? super C>> singleValueHandlers =
            new HashMap<>();
    private final Map<FloggerMetadataKey<?>, RepeatedValueHandler<?, ? super C>> repeatedValueHandlers =
            new HashMap<>();
    private final ValueHandler<Object, ? super C> defaultHandler;
    private final RepeatedValueHandler<Object, ? super C> defaultRepeatedHandler;

    private MapBasedhandler(Builder<C> builder) {
      this.singleValueHandlers.putAll(builder.singleValueHandlers);
      this.repeatedValueHandlers.putAll(builder.repeatedValueHandlers);
      this.defaultHandler = builder.defaultHandler;
      this.defaultRepeatedHandler = builder.defaultRepeatedHandler;
    }

    @SuppressWarnings("unchecked") // See comments for why casting is safe.
    @Override
    protected <T> void handle(FloggerMetadataKey<T> key, T value, C context) {
      // Safe cast because of how our private map is managed.
      ValueHandler<T, ? super C> handler =
          (ValueHandler<T, ? super C>) singleValueHandlers.get(key);
      if (handler != null) {
        handler.handle(key, value, context);
      } else {
        // Casting MetadataKey<T> to "<? super T>" is safe since it only produces elements of 'T'.
        defaultHandler.handle((FloggerMetadataKey<Object>) key, value, context);
      }
    }

    @SuppressWarnings("unchecked") // See comments for why casting is safe.
    @Override
    protected <T> void handleRepeated(FloggerMetadataKey<T> key, Iterator<T> values, C context) {
      // Safe cast because of how our private map is managed.
      RepeatedValueHandler<T, ? super C> handler =
          (RepeatedValueHandler<T, ? super C>) repeatedValueHandlers.get(key);
      if (handler != null) {
        handler.handle(key, values, context);
      } else if (defaultRepeatedHandler != null && !singleValueHandlers.containsKey(key)) {
        // Casting MetadataKey<T> to "<? super T>" is safe since it only produces elements of 'T'.
        // Casting the iterator is safe since it also only produces elements of 'T'.
        defaultRepeatedHandler.handle(
                (FloggerMetadataKey<Object>) key, (Iterator<Object>) values, context);
      } else {
        // Dispatches keys individually.
        super.handleRepeated(key, values, context);
      }
    }
  }
}
