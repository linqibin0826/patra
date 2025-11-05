package com.patra.ingest.domain.model.vo.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TaskParams 值对象单元测试")
class TaskParamsTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用非空 Map 创建有效的任务参数")
    void shouldCreateValidTaskParamsWithNonEmptyMap() {
      // Given
      Map<String, Object> values = Map.of("key1", "value1", "key2", 42);

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).containsEntry("key1", "value1").containsEntry("key2", 42);
    }

    @Test
    @DisplayName("应该将 null Map 规范化为空 Map")
    void shouldNormalizeNullMapToEmptyMap() {
      // When
      TaskParams params = new TaskParams(null);

      // Then
      assertThat(params.values()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("应该使用空 Map 创建任务参数")
    void shouldCreateTaskParamsWithEmptyMap() {
      // Given
      Map<String, Object> values = Map.of();

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).isEmpty();
    }

    @Test
    @DisplayName("应该创建内部 Map 的不可变副本")
    void shouldCreateImmutableCopyOfInternalMap() {
      // Given
      Map<String, Object> mutableMap = new HashMap<>();
      mutableMap.put("key", "value");

      // When
      TaskParams params = new TaskParams(mutableMap);
      mutableMap.put("newKey", "newValue"); // 修改原始 Map

      // Then
      assertThat(params.values()).hasSize(1).containsOnlyKeys("key"); // 不受原始 Map 修改影响
    }

    @Test
    @DisplayName("返回的 Map 应该是不可变的")
    void returnedMapShouldBeImmutable() {
      // Given
      Map<String, Object> values = Map.of("key", "value");
      TaskParams params = new TaskParams(values);

      // When & Then
      assertThatThrownBy(() -> params.values().put("newKey", "newValue"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("应该支持各种类型的值")
    void shouldSupportVariousValueTypes() {
      // Given
      Map<String, Object> values =
          Map.of(
              "string", "text",
              "integer", 42,
              "long", 100L,
              "boolean", true,
              "double", 3.14);

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).hasSize(5).containsEntry("string", "text").containsEntry("integer", 42).containsEntry("long", 100L).containsEntry("boolean", true).containsEntry("double", 3.14);
    }

    @Test
    @DisplayName("应该支持复杂对象作为值")
    void shouldSupportComplexObjectsAsValues() {
      // Given
      Map<String, Object> nestedMap = Map.of("nested", "value");
      Map<String, Object> values = Map.of("map", nestedMap, "list", java.util.List.of(1, 2, 3));

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).containsEntry("map", nestedMap).containsKey("list");
    }

  }

  @Nested
  @DisplayName("isEmpty() 方法测试")
  class IsEmptyMethodTests {

    @Test
    @DisplayName("当没有参数时应该返回 true")
    void shouldReturnTrueWhenNoParameters() {
      // Given
      TaskParams params = new TaskParams(Map.of());

      // When
      boolean empty = params.isEmpty();

      // Then
      assertThat(empty).isTrue();
    }

    @Test
    @DisplayName("当构造时传入 null 应该返回 true")
    void shouldReturnTrueWhenConstructedWithNull() {
      // Given
      TaskParams params = new TaskParams(null);

      // When
      boolean empty = params.isEmpty();

      // Then
      assertThat(empty).isTrue();
    }

    @Test
    @DisplayName("当有参数时应该返回 false")
    void shouldReturnFalseWhenHasParameters() {
      // Given
      TaskParams params = new TaskParams(Map.of("key", "value"));

      // When
      boolean empty = params.isEmpty();

      // Then
      assertThat(empty).isFalse();
    }

    @Test
    @DisplayName("当只有一个参数时应该返回 false")
    void shouldReturnFalseWhenHasSingleParameter() {
      // Given
      TaskParams params = new TaskParams(Map.of("key", "value"));

      // When
      boolean empty = params.isEmpty();

      // Then
      assertThat(empty).isFalse();
    }

    @Test
    @DisplayName("当有多个参数时应该返回 false")
    void shouldReturnFalseWhenHasMultipleParameters() {
      // Given
      TaskParams params = new TaskParams(Map.of("key1", "value1", "key2", "value2"));

      // When
      boolean empty = params.isEmpty();

      // Then
      assertThat(empty).isFalse();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同参数的实例应该相等")
    void instancesWithSameParametersShouldBeEqual() {
      // Given
      Map<String, Object> values = Map.of("key", "value");
      TaskParams params1 = new TaskParams(values);
      TaskParams params2 = new TaskParams(values);

      // Then
      assertThat(params1).isEqualTo(params2);
      assertThat(params1.hashCode()).isEqualTo(params2.hashCode());
    }

    @Test
    @DisplayName("不同参数的实例应该不相等")
    void instancesWithDifferentParametersShouldNotBeEqual() {
      // Given
      TaskParams params1 = new TaskParams(Map.of("key1", "value1"));
      TaskParams params2 = new TaskParams(Map.of("key2", "value2"));

      // Then
      assertThat(params1).isNotEqualTo(params2);
    }

    @Test
    @DisplayName("两个空参数对象应该相等")
    void twoEmptyParamsShouldBeEqual() {
      // Given
      TaskParams empty1 = new TaskParams(Map.of());
      TaskParams empty2 = new TaskParams(null);

      // Then
      assertThat(empty1).isEqualTo(empty2);
      assertThat(empty1.hashCode()).isEqualTo(empty2.hashCode());
    }

    @Test
    @DisplayName("toString() 应该包含参数信息")
    void toStringShouldContainParameterInformation() {
      // Given
      TaskParams params = new TaskParams(Map.of("key", "value"));

      // When
      String result = params.toString();

      // Then
      assertThat(result).contains("TaskParams").contains("key").contains("value");
    }

    @Test
    @DisplayName("应该支持作为 Map 的键")
    void shouldWorkAsMapKey() {
      // Given
      var map = new java.util.HashMap<TaskParams, String>();
      TaskParams key1 = new TaskParams(Map.of("key", "value"));
      TaskParams key2 = new TaskParams(Map.of("key", "value"));

      // When
      map.put(key1, "metadata");

      // Then
      assertThat(map.get(key2)).isEqualTo("metadata"); // 相同值可以检索
      assertThat(map).containsKey(key1);
      assertThat(map).containsKey(key2);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理大量参数")
    void shouldHandleManyParameters() {
      // Given
      Map<String, Object> manyParams = new HashMap<>();
      for (int i = 0; i < 1000; i++) {
        manyParams.put("key" + i, "value" + i);
      }

      // When
      TaskParams params = new TaskParams(manyParams);

      // Then
      assertThat(params.values()).hasSize(1000);
      assertThat(params.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("应该处理非常长的键名")
    void shouldHandleVeryLongKeys() {
      // Given
      String longKey = "key-" + "x".repeat(1000);
      Map<String, Object> values = Map.of(longKey, "value");

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).containsKey(longKey);
    }

    @Test
    @DisplayName("应该处理非常长的值")
    void shouldHandleVeryLongValues() {
      // Given
      String longValue = "value-" + "x".repeat(10000);
      Map<String, Object> values = Map.of("key", longValue);

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).containsEntry("key", longValue);
    }

    @Test
    @DisplayName("应该处理特殊字符的键名")
    void shouldHandleKeysWithSpecialCharacters() {
      // Given
      Map<String, Object> values = Map.of("key.with.dots", "value1", "key-with-dashes", "value2", "key_with_underscores", "value3");

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).hasSize(3).containsKey("key.with.dots").containsKey("key-with-dashes").containsKey("key_with_underscores");
    }

    @Test
    @DisplayName("应该处理空字符串作为键")
    void shouldHandleEmptyStringAsKey() {
      // Given
      Map<String, Object> values = Map.of("", "emptyKeyValue");

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).containsEntry("", "emptyKeyValue");
    }

    @Test
    @DisplayName("应该处理空字符串作为值")
    void shouldHandleEmptyStringAsValue() {
      // Given
      Map<String, Object> values = Map.of("key", "");

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).containsEntry("key", "");
    }

    @Test
    @DisplayName("应该处理包含 Unicode 字符的参数")
    void shouldHandleUnicodeParameters() {
      // Given
      Map<String, Object> values = Map.of("键", "值", "clé", "valeur", "キー", "値");

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).hasSize(3).containsEntry("键", "值").containsEntry("clé", "valeur").containsEntry("キー", "値");
    }
  }

  @Nested
  @DisplayName("实际场景测试")
  class RealWorldScenarioTests {

    @Test
    @DisplayName("应该支持任务切片编号参数")
    void shouldSupportSliceNumberParameter() {
      // Given
      Map<String, Object> values = Map.of("sliceNo", 5);

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).containsEntry("sliceNo", 5);
      assertThat(params.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("应该支持配置参数")
    void shouldSupportConfigurationParameters() {
      // Given
      Map<String, Object> values =
          Map.of(
              "batchSize", 100,
              "timeout", 3000L,
              "retryEnabled", true,
              "endpoint", "https://api.example.com");

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).hasSize(4).containsEntry("batchSize", 100).containsEntry("timeout", 3000L).containsEntry("retryEnabled", true).containsEntry("endpoint", "https://api.example.com");
    }

    @Test
    @DisplayName("应该支持过滤器参数")
    void shouldSupportFilterParameters() {
      // Given
      Map<String, Object> values =
          Map.of("dateFrom", "2025-01-01", "dateTo", "2025-01-31", "tags", java.util.List.of("tag1", "tag2"));

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).hasSize(3).containsKey("dateFrom").containsKey("dateTo").containsKey("tags");
    }

    @Test
    @DisplayName("应该支持重试上下文参数")
    void shouldSupportRetryContextParameters() {
      // Given
      Map<String, Object> values =
          Map.of(
              "retryCount", 3,
              "lastError", "Connection timeout",
              "nextRetryAt", "2025-01-05T10:30:00Z");

      // When
      TaskParams params = new TaskParams(values);

      // Then
      assertThat(params.values()).hasSize(3).containsEntry("retryCount", 3).containsKey("lastError").containsKey("nextRetryAt");
    }
  }
}
