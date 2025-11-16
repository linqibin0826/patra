package com.patra.objectstorage.domain.model.vo;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BusinessContext 单元测试
 *
 * <p>测试覆盖:
 *
 * <ul>
 *   <li>成功构造测试 - 验证正常创建场景
 *   <li>验证失败测试 - 验证参数校验逻辑
 *   <li>Sanitize 逻辑测试 - 验证 correlationData 清理行为
 *   <li>不可变性测试 - 验证 record 不可变特性
 *   <li>Record 语义测试 - 验证 equals/hashCode/toString
 * </ul>
 */
@DisplayName("BusinessContext 测试")
class BusinessContextTest {

  @Nested
  @DisplayName("成功构造测试")
  class ConstructionSuccess {

    @Test
    @DisplayName("应该成功创建 - 所有字段有效且 correlationData 非空")
    void shouldCreateSuccessfully_withNonEmptyCorrelationData() {
      // Given
      String serviceName = "patra-ingest";
      String businessType = "publication_batch";
      String businessId = "batch-12345";
      Map<String, Object> correlationData = new HashMap<>();
      correlationData.put("batchSize", 100);
      correlationData.put("source", "pubmed");

      // When
      BusinessContext context =
          new BusinessContext(serviceName, businessType, businessId, correlationData);

      // Then
      assertThat(context).isNotNull();
      assertThat(context.serviceName()).isEqualTo(serviceName);
      assertThat(context.businessType()).isEqualTo(businessType);
      assertThat(context.businessId()).isEqualTo(businessId);
      assertThat(context.correlationData())
          .containsEntry("batchSize", 100)
          .containsEntry("source", "pubmed");
    }

    @Test
    @DisplayName("应该成功创建 - correlationData 为 null")
    void shouldCreateSuccessfully_withNullCorrelationData() {
      // Given
      String serviceName = "patra-ingest";
      String businessType = "publication_batch";
      String businessId = "batch-12345";

      // When
      BusinessContext context = new BusinessContext(serviceName, businessType, businessId, null);

      // Then
      assertThat(context).isNotNull();
      assertThat(context.correlationData()).isEmpty();
    }

    @Test
    @DisplayName("应该成功创建 - correlationData 为空 Map")
    void shouldCreateSuccessfully_withEmptyCorrelationData() {
      // Given
      String serviceName = "patra-ingest";
      String businessType = "publication_batch";
      String businessId = "batch-12345";
      Map<String, Object> emptyMap = new HashMap<>();

      // When
      BusinessContext context =
          new BusinessContext(serviceName, businessType, businessId, emptyMap);

      // Then
      assertThat(context).isNotNull();
      assertThat(context.correlationData()).isEmpty();
    }

    @Test
    @DisplayName("应该成功创建 - correlationData 包含多个条目")
    void shouldCreateSuccessfully_withMultipleCorrelationDataEntries() {
      // Given
      String serviceName = "patra-registry";
      String businessType = "provenance_config";
      String businessId = "config-789";
      Map<String, Object> correlationData = new LinkedHashMap<>();
      correlationData.put("version", "1.0.0");
      correlationData.put("author", "admin");
      correlationData.put("timestamp", 1734825600000L);
      correlationData.put("tags", java.util.List.of("prod", "active"));

      // When
      BusinessContext context =
          new BusinessContext(serviceName, businessType, businessId, correlationData);

      // Then
      assertThat(context.correlationData())
          .hasSize(4)
          .containsKeys("version", "author", "timestamp", "tags");
    }

    @Test
    @DisplayName("应该成功创建 - serviceName/businessType/businessId 包含空格但不是空白")
    void shouldCreateSuccessfully_withWhitespaceInMiddle() {
      // Given
      String serviceName = "patra ingest";
      String businessType = "publication batch";
      String businessId = "batch 12345";

      // When
      BusinessContext context = new BusinessContext(serviceName, businessType, businessId, null);

      // Then
      assertThat(context.serviceName()).isEqualTo(serviceName);
      assertThat(context.businessType()).isEqualTo(businessType);
      assertThat(context.businessId()).isEqualTo(businessId);
    }
  }

  @Nested
  @DisplayName("验证失败测试")
  class ValidationFailure {

    @Nested
    @DisplayName("serviceName 验证")
    class ServiceNameValidation {

      @Test
      @DisplayName("应该抛出异常 - serviceName 为 null")
      void shouldThrowException_whenServiceNameIsNull() {
        // When & Then
        assertThatThrownBy(() -> new BusinessContext(null, "publication_batch", "batch-123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("服务名称不能为空");
      }

      @Test
      @DisplayName("应该抛出异常 - serviceName 为空字符串")
      void shouldThrowException_whenServiceNameIsEmpty() {
        // When & Then
        assertThatThrownBy(() -> new BusinessContext("", "publication_batch", "batch-123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("服务名称不能为空");
      }

      @Test
      @DisplayName("应该抛出异常 - serviceName 为空白字符串")
      void shouldThrowException_whenServiceNameIsBlank() {
        // When & Then
        assertThatThrownBy(() -> new BusinessContext("   ", "publication_batch", "batch-123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("服务名称不能为空");
      }

      @Test
      @DisplayName("应该抛出异常 - serviceName 为制表符")
      void shouldThrowException_whenServiceNameIsTab() {
        // When & Then
        assertThatThrownBy(() -> new BusinessContext("\t", "publication_batch", "batch-123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("服务名称不能为空");
      }
    }

    @Nested
    @DisplayName("businessType 验证")
    class BusinessTypeValidation {

      @Test
      @DisplayName("应该抛出异常 - businessType 为 null")
      void shouldThrowException_whenBusinessTypeIsNull() {
        // When & Then
        assertThatThrownBy(() -> new BusinessContext("patra-ingest", null, "batch-123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("业务类型不能为空");
      }

      @Test
      @DisplayName("应该抛出异常 - businessType 为空字符串")
      void shouldThrowException_whenBusinessTypeIsEmpty() {
        // When & Then
        assertThatThrownBy(() -> new BusinessContext("patra-ingest", "", "batch-123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("业务类型不能为空");
      }

      @Test
      @DisplayName("应该抛出异常 - businessType 为空白字符串")
      void shouldThrowException_whenBusinessTypeIsBlank() {
        // When & Then
        assertThatThrownBy(() -> new BusinessContext("patra-ingest", "  \n  ", "batch-123", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("业务类型不能为空");
      }
    }

    @Nested
    @DisplayName("businessId 验证")
    class BusinessIdValidation {

      @Test
      @DisplayName("应该抛出异常 - businessId 为 null")
      void shouldThrowException_whenBusinessIdIsNull() {
        // When & Then
        assertThatThrownBy(
                () -> new BusinessContext("patra-ingest", "publication_batch", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("业务ID不能为空");
      }

      @Test
      @DisplayName("应该抛出异常 - businessId 为空字符串")
      void shouldThrowException_whenBusinessIdIsEmpty() {
        // When & Then
        assertThatThrownBy(() -> new BusinessContext("patra-ingest", "publication_batch", "", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("业务ID不能为空");
      }

      @Test
      @DisplayName("应该抛出异常 - businessId 为空白字符串")
      void shouldThrowException_whenBusinessIdIsBlank() {
        // When & Then
        assertThatThrownBy(
                () -> new BusinessContext("patra-ingest", "publication_batch", "\r\n", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("业务ID不能为空");
      }
    }

    @Nested
    @DisplayName("correlationData 验证")
    class CorrelationDataValidation {

      @Test
      @DisplayName("应该抛出异常 - correlationData 的 key 为 null")
      void shouldThrowException_whenCorrelationDataKeyIsNull() {
        // Given
        Map<String, Object> correlationData = new HashMap<>();
        correlationData.put(null, "value");

        // When & Then
        assertThatThrownBy(
                () ->
                    new BusinessContext(
                        "patra-ingest", "publication_batch", "batch-123", correlationData))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("关联数据键不能为null");
      }

      @Test
      @DisplayName("应该成功创建 - correlationData 的 value 为 null (允许)")
      void shouldCreateSuccessfully_whenCorrelationDataValueIsNull() {
        // Given
        Map<String, Object> correlationData = new HashMap<>();
        correlationData.put("key1", null);
        correlationData.put("key2", "value2");

        // When
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", correlationData);

        // Then
        assertThat(context.correlationData())
            .containsEntry("key1", null)
            .containsEntry("key2", "value2");
      }
    }
  }

  @Nested
  @DisplayName("Sanitize 逻辑测试")
  class SanitizeLogic {

    @Test
    @DisplayName("应该返回空 Map - correlationData 为 null")
    void shouldReturnEmptyMap_whenCorrelationDataIsNull() {
      // When
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

      // Then
      assertThat(context.correlationData()).isNotNull().isEqualTo(Map.of());
    }

    @Test
    @DisplayName("应该返回空 Map - correlationData 为空 Map")
    void shouldReturnEmptyMap_whenCorrelationDataIsEmpty() {
      // Given
      Map<String, Object> emptyMap = new HashMap<>();

      // When
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", emptyMap);

      // Then
      assertThat(context.correlationData()).isNotNull().isEqualTo(Map.of());
    }

    @Test
    @DisplayName("应该创建不可变副本 - correlationData 非空")
    void shouldCreateUnmodifiableCopy_whenCorrelationDataIsNonEmpty() {
      // Given
      Map<String, Object> originalMap = new HashMap<>();
      originalMap.put("key1", "value1");
      originalMap.put("key2", 42);

      // When
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", originalMap);

      // Then
      assertThat(context.correlationData())
          .containsEntry("key1", "value1")
          .containsEntry("key2", 42);

      // 验证是不可变的
      assertThatThrownBy(() -> context.correlationData().put("key3", "value3"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("应该允许 null value - correlationData 包含 null")
    void shouldAllowNullValue_whenCorrelationDataContainsNull() {
      // Given
      Map<String, Object> mapWithNull = new HashMap<>();
      mapWithNull.put("key1", "value1");
      mapWithNull.put("key2", null);
      mapWithNull.put("key3", "value3");

      // When
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", mapWithNull);

      // Then
      assertThat(context.correlationData())
          .containsEntry("key1", "value1")
          .containsEntry("key2", null)
          .containsEntry("key3", "value3");
    }

    @Test
    @DisplayName("应该保持插入顺序 - correlationData 使用 LinkedHashMap")
    void shouldPreserveInsertionOrder_whenUsingLinkedHashMap() {
      // Given
      Map<String, Object> orderedMap = new LinkedHashMap<>();
      orderedMap.put("first", 1);
      orderedMap.put("second", 2);
      orderedMap.put("third", 3);

      // When
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", orderedMap);

      // Then
      assertThat(context.correlationData().keySet()).containsExactly("first", "second", "third");
    }
  }

  @Nested
  @DisplayName("不可变性测试")
  class Immutability {

    @Test
    @DisplayName("correlationData 返回的 Map 不可修改 - put 操作")
    void correlationDataMapShouldBeImmutable_put() {
      // Given
      Map<String, Object> originalMap = new HashMap<>();
      originalMap.put("key1", "value1");
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", originalMap);

      // When & Then
      assertThatThrownBy(() -> context.correlationData().put("key2", "value2"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("correlationData 返回的 Map 不可修改 - remove 操作")
    void correlationDataMapShouldBeImmutable_remove() {
      // Given
      Map<String, Object> originalMap = new HashMap<>();
      originalMap.put("key1", "value1");
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", originalMap);

      // When & Then
      assertThatThrownBy(() -> context.correlationData().remove("key1"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("correlationData 返回的 Map 不可修改 - clear 操作")
    void correlationDataMapShouldBeImmutable_clear() {
      // Given
      Map<String, Object> originalMap = new HashMap<>();
      originalMap.put("key1", "value1");
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", originalMap);

      // When & Then
      assertThatThrownBy(() -> context.correlationData().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("原始 Map 修改不影响 record 中的 Map")
    void originalMapChangesShouldNotAffectRecord() {
      // Given
      Map<String, Object> originalMap = new HashMap<>();
      originalMap.put("key1", "value1");
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", originalMap);

      // When
      originalMap.put("key2", "value2");
      originalMap.remove("key1");

      // Then
      assertThat(context.correlationData()).containsOnlyKeys("key1").doesNotContainKey("key2");
    }

    @Test
    @DisplayName("Record 本身不可变 - 组件访问器返回相同引用")
    void recordShouldBeImmutable_sameReferences() {
      // Given
      Map<String, Object> correlationData = Map.of("key", "value");
      BusinessContext context =
          new BusinessContext("patra-ingest", "publication_batch", "batch-123", correlationData);

      // When & Then
      assertThat(context.serviceName()).isSameAs(context.serviceName());
      assertThat(context.businessType()).isSameAs(context.businessType());
      assertThat(context.businessId()).isSameAs(context.businessId());
      assertThat(context.correlationData()).isSameAs(context.correlationData());
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemantics {

    @Nested
    @DisplayName("equals() 测试")
    class EqualsTest {

      @Test
      @DisplayName("应该相等 - 所有字段相同")
      void shouldBeEqual_whenAllFieldsAreSame() {
        // Given
        Map<String, Object> data1 = Map.of("key", "value");
        Map<String, Object> data2 = Map.of("key", "value");

        BusinessContext context1 =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", data1);
        BusinessContext context2 =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", data2);

        // When & Then
        assertThat(context1).isEqualTo(context2);
        assertThat(context2).isEqualTo(context1);
      }

      @Test
      @DisplayName("应该相等 - correlationData 都为空")
      void shouldBeEqual_whenBothCorrelationDataAreEmpty() {
        // Given
        BusinessContext context1 =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);
        BusinessContext context2 =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", Map.of());

        // When & Then
        assertThat(context1).isEqualTo(context2);
      }

      @Test
      @DisplayName("应该不相等 - serviceName 不同")
      void shouldNotBeEqual_whenServiceNameDiffers() {
        // Given
        BusinessContext context1 =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);
        BusinessContext context2 =
            new BusinessContext("patra-registry", "publication_batch", "batch-123", null);

        // When & Then
        assertThat(context1).isNotEqualTo(context2);
      }

      @Test
      @DisplayName("应该不相等 - businessType 不同")
      void shouldNotBeEqual_whenBusinessTypeDiffers() {
        // Given
        BusinessContext context1 =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);
        BusinessContext context2 =
            new BusinessContext("patra-ingest", "provenance_config", "batch-123", null);

        // When & Then
        assertThat(context1).isNotEqualTo(context2);
      }

      @Test
      @DisplayName("应该不相等 - businessId 不同")
      void shouldNotBeEqual_whenBusinessIdDiffers() {
        // Given
        BusinessContext context1 =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);
        BusinessContext context2 =
            new BusinessContext("patra-ingest", "publication_batch", "batch-456", null);

        // When & Then
        assertThat(context1).isNotEqualTo(context2);
      }

      @Test
      @DisplayName("应该不相等 - correlationData 不同")
      void shouldNotBeEqual_whenCorrelationDataDiffers() {
        // Given
        BusinessContext context1 =
            new BusinessContext(
                "patra-ingest", "publication_batch", "batch-123", Map.of("key", "value1"));
        BusinessContext context2 =
            new BusinessContext(
                "patra-ingest", "publication_batch", "batch-123", Map.of("key", "value2"));

        // When & Then
        assertThat(context1).isNotEqualTo(context2);
      }

      @Test
      @DisplayName("应该不相等 - 与 null 比较")
      void shouldNotBeEqual_whenComparedToNull() {
        // Given
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

        // When & Then
        assertThat(context).isNotEqualTo(null);
      }

      @Test
      @DisplayName("应该不相等 - 与不同类型比较")
      void shouldNotBeEqual_whenComparedToDifferentType() {
        // Given
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

        // When & Then
        assertThat(context).isNotEqualTo("not a BusinessContext");
      }

      @Test
      @DisplayName("应该相等 - 与自身比较")
      void shouldBeEqual_whenComparedToSelf() {
        // Given
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

        // When & Then
        assertThat(context).isEqualTo(context);
      }
    }

    @Nested
    @DisplayName("hashCode() 测试")
    class HashCodeTest {

      @Test
      @DisplayName("hashCode 应该一致 - 相同对象多次调用")
      void hashCodeShouldBeConsistent_whenCalledMultipleTimes() {
        // Given
        BusinessContext context =
            new BusinessContext(
                "patra-ingest", "publication_batch", "batch-123", Map.of("key", "value"));

        // When
        int hashCode1 = context.hashCode();
        int hashCode2 = context.hashCode();

        // Then
        assertThat(hashCode1).isEqualTo(hashCode2);
      }

      @Test
      @DisplayName("hashCode 应该相同 - equals 相等的对象")
      void hashCodeShouldBeEqual_whenObjectsAreEqual() {
        // Given
        BusinessContext context1 =
            new BusinessContext(
                "patra-ingest", "publication_batch", "batch-123", Map.of("key", "value"));
        BusinessContext context2 =
            new BusinessContext(
                "patra-ingest", "publication_batch", "batch-123", Map.of("key", "value"));

        // When & Then
        assertThat(context1).isEqualTo(context2);
        assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
      }

      @Test
      @DisplayName("hashCode 可能不同 - equals 不相等的对象 (不保证但期望)")
      void hashCodeMayDiffer_whenObjectsAreNotEqual() {
        // Given
        BusinessContext context1 =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);
        BusinessContext context2 =
            new BusinessContext("patra-registry", "provenance_config", "config-456", null);

        // When & Then
        assertThat(context1).isNotEqualTo(context2);
        // 注意: hashCode 不同不是强制要求,但这里期望它们不同
        assertThat(context1.hashCode()).isNotEqualTo(context2.hashCode());
      }
    }

    @Nested
    @DisplayName("toString() 测试")
    class ToStringTest {

      @Test
      @DisplayName("toString 应该包含类名")
      void toStringShouldContainClassName() {
        // Given
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

        // When
        String result = context.toString();

        // Then
        assertThat(result).contains("BusinessContext");
      }

      @Test
      @DisplayName("toString 应该包含所有字段")
      void toStringShouldContainAllFields() {
        // Given
        BusinessContext context =
            new BusinessContext(
                "patra-ingest", "publication_batch", "batch-123", Map.of("key", "value"));

        // When
        String result = context.toString();

        // Then
        assertThat(result)
            .contains("patra-ingest")
            .contains("publication_batch")
            .contains("batch-123")
            .contains("key")
            .contains("value");
      }

      @Test
      @DisplayName("toString 应该一致 - 相同对象多次调用")
      void toStringShouldBeConsistent() {
        // Given
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

        // When
        String result1 = context.toString();
        String result2 = context.toString();

        // Then
        assertThat(result1).isEqualTo(result2);
      }
    }

    @Nested
    @DisplayName("组件访问器测试")
    class ComponentAccessors {

      @Test
      @DisplayName("serviceName() 应该返回正确的值")
      void serviceNameAccessorShouldReturnCorrectValue() {
        // Given
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

        // When
        String result = context.serviceName();

        // Then
        assertThat(result).isEqualTo("patra-ingest");
      }

      @Test
      @DisplayName("businessType() 应该返回正确的值")
      void businessTypeAccessorShouldReturnCorrectValue() {
        // Given
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

        // When
        String result = context.businessType();

        // Then
        assertThat(result).isEqualTo("publication_batch");
      }

      @Test
      @DisplayName("businessId() 应该返回正确的值")
      void businessIdAccessorShouldReturnCorrectValue() {
        // Given
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

        // When
        String result = context.businessId();

        // Then
        assertThat(result).isEqualTo("batch-123");
      }

      @Test
      @DisplayName("correlationData() 应该返回正确的值")
      void correlationDataAccessorShouldReturnCorrectValue() {
        // Given
        Map<String, Object> expectedData = Map.of("key", "value");
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", expectedData);

        // When
        Map<String, Object> result = context.correlationData();

        // Then
        assertThat(result).isEqualTo(expectedData);
      }

      @Test
      @DisplayName("所有访问器应该返回非 null 值")
      void allAccessorsShouldReturnNonNullValues() {
        // Given
        BusinessContext context =
            new BusinessContext("patra-ingest", "publication_batch", "batch-123", null);

        // When & Then
        assertThat(context.serviceName()).isNotNull();
        assertThat(context.businessType()).isNotNull();
        assertThat(context.businessId()).isNotNull();
        assertThat(context.correlationData()).isNotNull();
      }
    }
  }
}
