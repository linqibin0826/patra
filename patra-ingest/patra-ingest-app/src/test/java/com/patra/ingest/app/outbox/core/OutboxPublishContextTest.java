package com.patra.ingest.app.outbox.core;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OutboxPublishContext}.
 *
 * <p>Tests builder pattern, type-safe retrieval, and defensive copying.
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("OutboxPublishContext unit tests")
class OutboxPublishContextTest {

  @Test
  @DisplayName("should build context with put() and retrieve with get()")
  void shouldBuildAndRetrieve() {
    // given
    String planKey = "test-plan";
    Integer priority = 10;

    // when
    OutboxPublishContext ctx =
        OutboxPublishContext.builder().put("plan", planKey).put("priority", priority).build();

    // then
    assertThat(ctx.get("plan", String.class)).isEqualTo(planKey);
    assertThat(ctx.get("priority", Integer.class)).isEqualTo(priority);
  }

  @Test
  @DisplayName("should return null when key does not exist")
  void shouldReturnNullWhenKeyNotFound() {
    // given
    OutboxPublishContext ctx = OutboxPublishContext.builder().put("existingKey", "value").build();

    // when
    String result = ctx.get("nonExistentKey", String.class);

    // then
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("should throw ClassCastException when type mismatch")
  void shouldThrowClassCastExceptionWhenTypeMismatch() {
    // given
    OutboxPublishContext ctx = OutboxPublishContext.builder().put("value", "stringValue").build();

    // when & then
    assertThatThrownBy(() -> ctx.get("value", Integer.class))
        .isInstanceOf(ClassCastException.class)
        .hasMessageContaining("is not of type java.lang.Integer");
  }

  @Test
  @DisplayName("should return default value when key not found using getOrDefault()")
  void shouldReturnDefaultValueWhenKeyNotFound() {
    // given
    OutboxPublishContext ctx = OutboxPublishContext.builder().build();
    String defaultValue = "default";

    // when
    String result = ctx.getOrDefault("missingKey", String.class, defaultValue);

    // then
    assertThat(result).isEqualTo(defaultValue);
  }

  @Test
  @DisplayName("should return actual value when key exists using getOrDefault()")
  void shouldReturnActualValueWhenKeyExists() {
    // given
    String actualValue = "actual";
    OutboxPublishContext ctx = OutboxPublishContext.builder().put("key", actualValue).build();

    // when
    String result = ctx.getOrDefault("key", String.class, "default");

    // then
    assertThat(result).isEqualTo(actualValue);
  }

  @Test
  @DisplayName("should throw NullPointerException when key is null")
  void shouldThrowNullPointerExceptionWhenKeyIsNull() {
    // given
    OutboxPublishContext.Builder builder = OutboxPublishContext.builder();

    // when & then
    assertThatThrownBy(() -> builder.put(null, "value"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Context key must not be null");
  }

  @Test
  @DisplayName("should allow null values in context")
  void shouldAllowNullValues() {
    // given
    OutboxPublishContext ctx = OutboxPublishContext.builder().put("nullableKey", null).build();

    // when
    String result = ctx.get("nullableKey", String.class);

    // then
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("should build immutable context (defensive copy)")
  void shouldBuildImmutableContext() {
    // given
    OutboxPublishContext.Builder builder = OutboxPublishContext.builder().put("key1", "value1");

    OutboxPublishContext ctx1 = builder.build();

    // Modify builder after first build
    builder.put("key2", "value2");
    OutboxPublishContext ctx2 = builder.build();

    // then - ctx1 should not be affected by builder changes
    assertThat(ctx1.get("key1", String.class)).isEqualTo("value1");
    assertThat(ctx1.get("key2", String.class)).isNull();

    assertThat(ctx2.get("key1", String.class)).isEqualTo("value1");
    assertThat(ctx2.get("key2", String.class)).isEqualTo("value2");
  }

  @Test
  @DisplayName("should handle complex object types")
  void shouldHandleComplexObjectTypes() {
    // given
    record TestAggregate(Long id, String name) {}
    TestAggregate aggregate = new TestAggregate(1L, "test");

    OutboxPublishContext ctx = OutboxPublishContext.builder().put("aggregate", aggregate).build();

    // when
    TestAggregate retrieved = ctx.get("aggregate", TestAggregate.class);

    // then
    assertThat(retrieved).isEqualTo(aggregate);
    assertThat(retrieved.id()).isEqualTo(1L);
    assertThat(retrieved.name()).isEqualTo("test");
  }

  @Test
  @DisplayName("should build empty context")
  void shouldBuildEmptyContext() {
    // when
    OutboxPublishContext ctx = OutboxPublishContext.builder().build();

    // then
    assertThat(ctx.get("anyKey", String.class)).isNull();
  }
}
