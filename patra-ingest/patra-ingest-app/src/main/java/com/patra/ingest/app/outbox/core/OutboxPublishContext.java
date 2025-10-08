package com.patra.ingest.app.outbox.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Outbox publishing context encapsulating aggregates and metadata.
 * <p>Replaces variable-length arguments with type-safe context map.</p>
 * <p>This class is immutable and thread-safe. All context data is defensively copied.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * OutboxPublishContext ctx = OutboxPublishContext.builder()
 *     .put("plan", planAggregate)
 *     .put("schedule", scheduleAggregate)
 *     .put("traceId", traceContext.getTraceId())
 *     .build();
 *
 * publisher.publish(events, ctx);
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class OutboxPublishContext {

    private final Map<String, Object> contextData;

    /**
     * Private constructor to enforce builder usage.
     *
     * @param contextData Context data map (defensively copied)
     */
    private OutboxPublishContext(Map<String, Object> contextData) {
        this.contextData = new HashMap<>(contextData);
    }

    /**
     * Retrieves a context value by key with type casting.
     *
     * @param key  Context key (must not be null)
     * @param type Expected value type (must not be null)
     * @param <T>  Type parameter
     * @return Value cast to the specified type, or null if key not found
     * @throws ClassCastException if value exists but cannot be cast to the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = contextData.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                    String.format("Context value for key '%s' is not of type %s, actual: %s",
                            key, type.getName(), value.getClass().getName()));
        }
        return (T) value;
    }

    /**
     * Retrieves a context value by key with type casting, with default fallback.
     *
     * @param key          Context key
     * @param type         Expected type
     * @param defaultValue Default value if key not found
     * @param <T>          Type parameter
     * @return Value or default
     */
    public <T> T getOrDefault(String key, Class<T> type, T defaultValue) {
        T value = get(key, type);
        return value != null ? value : defaultValue;
    }

    /**
     * Creates a builder for constructing context.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link OutboxPublishContext}.
     */
    public static final class Builder {
        private final Map<String, Object> data = new HashMap<>();

        private Builder() {
        }

        /**
         * Adds a key-value pair to the context.
         *
         * @param key   Context key (must not be null)
         * @param value Context value
         * @return This builder
         */
        public Builder put(String key, Object value) {
            Objects.requireNonNull(key, "Context key must not be null");
            data.put(key, value);
            return this;
        }

        /**
         * Builds the immutable context.
         *
         * @return OutboxPublishContext instance
         */
        public OutboxPublishContext build() {
            return new OutboxPublishContext(data);
        }
    }
}
