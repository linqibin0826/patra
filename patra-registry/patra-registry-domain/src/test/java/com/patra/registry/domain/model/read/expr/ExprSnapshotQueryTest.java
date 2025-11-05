package com.patra.registry.domain.model.read.expr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * {@link ExprSnapshotQuery} 单元测试。
 *
 * <p>测试聚合表达式快照查询视图的构造、验证和 record 语义。
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("ExprSnapshotQuery 单元测试")
class ExprSnapshotQueryTest {

  @Nested
  @DisplayName("构造器验证")
  class ConstructorValidation {

    @Test
    @DisplayName("所有字段非空时应成功创建实例")
    void shouldCreateInstance_whenAllFieldsAreNonNull() {
      // Given: 准备所有必需的列表
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());

      // When: 创建快照查询
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(fields, capabilities, renderRules, apiParamMappings);

      // Then: 所有字段都被正确设置
      assertThat(snapshot.fields()).isEqualTo(fields);
      assertThat(snapshot.capabilities()).isEqualTo(capabilities);
      assertThat(snapshot.renderRules()).isEqualTo(renderRules);
      assertThat(snapshot.apiParamMappings()).isEqualTo(apiParamMappings);
    }

    @Test
    @DisplayName("所有字段为空列表时应成功创建实例")
    void shouldCreateInstance_whenAllFieldsAreEmptyLists() {
      // Given: 准备空列表
      List<ExprFieldQuery> emptyFields = Collections.emptyList();
      List<ExprCapabilityQuery> emptyCapabilities = Collections.emptyList();
      List<ExprRenderRuleQuery> emptyRenderRules = Collections.emptyList();
      List<ApiParamMappingQuery> emptyApiParamMappings = Collections.emptyList();

      // When: 创建快照查询
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(
              emptyFields, emptyCapabilities, emptyRenderRules, emptyApiParamMappings);

      // Then: 所有字段都是空列表
      assertThat(snapshot.fields()).isEmpty();
      assertThat(snapshot.capabilities()).isEmpty();
      assertThat(snapshot.renderRules()).isEmpty();
      assertThat(snapshot.apiParamMappings()).isEmpty();
    }

    @Test
    @DisplayName("fields 为 null 时应抛出 NullPointerException")
    void shouldThrowException_whenFieldsIsNull() {
      // Given: fields 为 null
      List<ExprFieldQuery> nullFields = null;
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());

      // When & Then: 应抛出异常且消息包含 "fields"
      assertThatNullPointerException()
          .isThrownBy(
              () -> new ExprSnapshotQuery(nullFields, capabilities, renderRules, apiParamMappings))
          .withMessageContaining("fields");
    }

    @Test
    @DisplayName("capabilities 为 null 时应抛出 NullPointerException")
    void shouldThrowException_whenCapabilitiesIsNull() {
      // Given: capabilities 为 null
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> nullCapabilities = null;
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());

      // When & Then: 应抛出异常且消息包含 "capabilities"
      assertThatNullPointerException()
          .isThrownBy(
              () -> new ExprSnapshotQuery(fields, nullCapabilities, renderRules, apiParamMappings))
          .withMessageContaining("capabilities");
    }

    @Test
    @DisplayName("renderRules 为 null 时应抛出 NullPointerException")
    void shouldThrowException_whenRenderRulesIsNull() {
      // Given: renderRules 为 null
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> nullRenderRules = null;
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());

      // When & Then: 应抛出异常且消息包含 "renderRules"
      assertThatNullPointerException()
          .isThrownBy(
              () -> new ExprSnapshotQuery(fields, capabilities, nullRenderRules, apiParamMappings))
          .withMessageContaining("renderRules");
    }

    @Test
    @DisplayName("apiParamMappings 为 null 时应抛出 NullPointerException")
    void shouldThrowException_whenApiParamMappingsIsNull() {
      // Given: apiParamMappings 为 null
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> nullApiParamMappings = null;

      // When & Then: 应抛出异常且消息包含 "apiParamMappings"
      assertThatNullPointerException()
          .isThrownBy(
              () -> new ExprSnapshotQuery(fields, capabilities, renderRules, nullApiParamMappings))
          .withMessageContaining("apiParamMappings");
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemantics {

    @Test
    @DisplayName("相同字段值的实例应相等")
    void shouldBeEqual_whenFieldsAreSame() {
      // Given: 相同的字段值
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());

      // When: 创建两个相同的实例
      ExprSnapshotQuery snapshot1 =
          new ExprSnapshotQuery(fields, capabilities, renderRules, apiParamMappings);
      ExprSnapshotQuery snapshot2 =
          new ExprSnapshotQuery(fields, capabilities, renderRules, apiParamMappings);

      // Then: 两个实例应相等且 hashCode 相同
      assertThat(snapshot1)
          .isEqualTo(snapshot2)
          .hasSameHashCodeAs(snapshot2)
          .isNotSameAs(snapshot2);
    }

    @Test
    @DisplayName("不同 fields 的实例应不相等")
    void shouldNotBeEqual_whenFieldsAreDifferent() {
      // Given: 不同的 fields
      List<ExprFieldQuery> fields1 = List.of(createExprFieldQuery("author"));
      List<ExprFieldQuery> fields2 = List.of(createExprFieldQuery("title"));
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());

      // When: 创建两个不同的实例
      ExprSnapshotQuery snapshot1 =
          new ExprSnapshotQuery(fields1, capabilities, renderRules, apiParamMappings);
      ExprSnapshotQuery snapshot2 =
          new ExprSnapshotQuery(fields2, capabilities, renderRules, apiParamMappings);

      // Then: 两个实例应不相等
      assertThat(snapshot1).isNotEqualTo(snapshot2);
    }

    @Test
    @DisplayName("不同 capabilities 的实例应不相等")
    void shouldNotBeEqual_whenCapabilitiesAreDifferent() {
      // Given: 不同的 capabilities
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> capabilities1 = List.of(createExprCapabilityQuery());
      List<ExprCapabilityQuery> capabilities2 = Collections.emptyList();
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());

      // When: 创建两个不同的实例
      ExprSnapshotQuery snapshot1 =
          new ExprSnapshotQuery(fields, capabilities1, renderRules, apiParamMappings);
      ExprSnapshotQuery snapshot2 =
          new ExprSnapshotQuery(fields, capabilities2, renderRules, apiParamMappings);

      // Then: 两个实例应不相等
      assertThat(snapshot1).isNotEqualTo(snapshot2);
    }

    @Test
    @DisplayName("不同 renderRules 的实例应不相等")
    void shouldNotBeEqual_whenRenderRulesAreDifferent() {
      // Given: 不同的 renderRules
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> renderRules1 = List.of(createExprRenderRuleQuery());
      List<ExprRenderRuleQuery> renderRules2 = Collections.emptyList();
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());

      // When: 创建两个不同的实例
      ExprSnapshotQuery snapshot1 =
          new ExprSnapshotQuery(fields, capabilities, renderRules1, apiParamMappings);
      ExprSnapshotQuery snapshot2 =
          new ExprSnapshotQuery(fields, capabilities, renderRules2, apiParamMappings);

      // Then: 两个实例应不相等
      assertThat(snapshot1).isNotEqualTo(snapshot2);
    }

    @Test
    @DisplayName("不同 apiParamMappings 的实例应不相等")
    void shouldNotBeEqual_whenApiParamMappingsAreDifferent() {
      // Given: 不同的 apiParamMappings
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> apiParamMappings1 = List.of(createApiParamMappingQuery());
      List<ApiParamMappingQuery> apiParamMappings2 = Collections.emptyList();

      // When: 创建两个不同的实例
      ExprSnapshotQuery snapshot1 =
          new ExprSnapshotQuery(fields, capabilities, renderRules, apiParamMappings1);
      ExprSnapshotQuery snapshot2 =
          new ExprSnapshotQuery(fields, capabilities, renderRules, apiParamMappings2);

      // Then: 两个实例应不相等
      assertThat(snapshot1).isNotEqualTo(snapshot2);
    }

    @Test
    @DisplayName("toString 应包含所有字段信息")
    void shouldContainAllFields_inToString() {
      // Given: 创建一个实例
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(fields, capabilities, renderRules, apiParamMappings);

      // When: 调用 toString
      String result = snapshot.toString();

      // Then: 应包含所有字段名称
      assertThat(result)
          .contains("fields", "capabilities", "renderRules", "apiParamMappings")
          .contains("ExprSnapshotQuery");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditions {

    @Test
    @DisplayName("包含多个元素的列表应正确存储")
    void shouldStoreCorrectly_whenListsHaveMultipleElements() {
      // Given: 包含多个元素的列表
      List<ExprFieldQuery> fields =
          List.of(createExprFieldQuery("author"), createExprFieldQuery("title"));
      List<ExprCapabilityQuery> capabilities =
          List.of(createExprCapabilityQuery(), createExprCapabilityQuery());
      List<ExprRenderRuleQuery> renderRules =
          List.of(createExprRenderRuleQuery(), createExprRenderRuleQuery());
      List<ApiParamMappingQuery> apiParamMappings =
          List.of(createApiParamMappingQuery(), createApiParamMappingQuery());

      // When: 创建快照查询
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(fields, capabilities, renderRules, apiParamMappings);

      // Then: 所有列表元素数量正确
      assertThat(snapshot.fields()).hasSize(2);
      assertThat(snapshot.capabilities()).hasSize(2);
      assertThat(snapshot.renderRules()).hasSize(2);
      assertThat(snapshot.apiParamMappings()).hasSize(2);
    }

    @Test
    @DisplayName("空列表混合非空列表应正确处理")
    void shouldHandleCorrectly_whenMixingEmptyAndNonEmptyLists() {
      // Given: 混合空列表和非空列表
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> emptyCapabilities = Collections.emptyList();
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> emptyApiParamMappings = Collections.emptyList();

      // When: 创建快照查询
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(fields, emptyCapabilities, renderRules, emptyApiParamMappings);

      // Then: 非空列表有元素，空列表为空
      assertThat(snapshot.fields()).hasSize(1);
      assertThat(snapshot.capabilities()).isEmpty();
      assertThat(snapshot.renderRules()).hasSize(1);
      assertThat(snapshot.apiParamMappings()).isEmpty();
    }

    @Test
    @DisplayName("List.of() 创建的不可变列表应正确工作")
    void shouldWorkCorrectly_withImmutableLists() {
      // Given: 使用 List.of() 创建不可变列表
      List<ExprFieldQuery> immutableFields = List.of(createExprFieldQuery("author"));
      List<ExprCapabilityQuery> immutableCapabilities = List.of(createExprCapabilityQuery());
      List<ExprRenderRuleQuery> immutableRenderRules = List.of(createExprRenderRuleQuery());
      List<ApiParamMappingQuery> immutableApiParamMappings = List.of(createApiParamMappingQuery());

      // When: 创建快照查询
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(
              immutableFields,
              immutableCapabilities,
              immutableRenderRules,
              immutableApiParamMappings);

      // Then: 所有字段都正确存储
      assertThat(snapshot.fields()).isEqualTo(immutableFields);
      assertThat(snapshot.capabilities()).isEqualTo(immutableCapabilities);
      assertThat(snapshot.renderRules()).isEqualTo(immutableRenderRules);
      assertThat(snapshot.apiParamMappings()).isEqualTo(immutableApiParamMappings);
    }
  }

  @Nested
  @DisplayName("访问器方法测试")
  class AccessorMethods {

    @Test
    @DisplayName("fields() 应返回构造时传入的列表")
    void fields_shouldReturnConstructedList() {
      // Given: 准备列表
      List<ExprFieldQuery> fields = List.of(createExprFieldQuery("author"));
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(
              fields, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

      // When: 调用 fields()
      List<ExprFieldQuery> result = snapshot.fields();

      // Then: 返回相同的列表
      assertThat(result).isEqualTo(fields);
    }

    @Test
    @DisplayName("capabilities() 应返回构造时传入的列表")
    void capabilities_shouldReturnConstructedList() {
      // Given: 准备列表
      List<ExprCapabilityQuery> capabilities = List.of(createExprCapabilityQuery());
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(
              Collections.emptyList(), capabilities, Collections.emptyList(), Collections.emptyList());

      // When: 调用 capabilities()
      List<ExprCapabilityQuery> result = snapshot.capabilities();

      // Then: 返回相同的列表
      assertThat(result).isEqualTo(capabilities);
    }

    @Test
    @DisplayName("renderRules() 应返回构造时传入的列表")
    void renderRules_shouldReturnConstructedList() {
      // Given: 准备列表
      List<ExprRenderRuleQuery> renderRules = List.of(createExprRenderRuleQuery());
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(
              Collections.emptyList(), Collections.emptyList(), renderRules, Collections.emptyList());

      // When: 调用 renderRules()
      List<ExprRenderRuleQuery> result = snapshot.renderRules();

      // Then: 返回相同的列表
      assertThat(result).isEqualTo(renderRules);
    }

    @Test
    @DisplayName("apiParamMappings() 应返回构造时传入的列表")
    void apiParamMappings_shouldReturnConstructedList() {
      // Given: 准备列表
      List<ApiParamMappingQuery> apiParamMappings = List.of(createApiParamMappingQuery());
      ExprSnapshotQuery snapshot =
          new ExprSnapshotQuery(
              Collections.emptyList(),
              Collections.emptyList(),
              Collections.emptyList(),
              apiParamMappings);

      // When: 调用 apiParamMappings()
      List<ApiParamMappingQuery> result = snapshot.apiParamMappings();

      // Then: 返回相同的列表
      assertThat(result).isEqualTo(apiParamMappings);
    }
  }

  // ==================== 测试数据工厂方法 ====================

  /**
   * 创建测试用的 ExprFieldQuery 实例。
   *
   * @param fieldKey 字段键
   * @return ExprFieldQuery 实例
   */
  private static ExprFieldQuery createExprFieldQuery(String fieldKey) {
    return new ExprFieldQuery(fieldKey, "显示名称", "描述", "STRING", "SINGLE", true, false);
  }

  /**
   * 创建测试用的 ExprCapabilityQuery 实例。
   *
   * @return ExprCapabilityQuery 实例
   */
  private static ExprCapabilityQuery createExprCapabilityQuery() {
    return new ExprCapabilityQuery(
        1L, // provenanceId
        "SEARCH", // operationType
        "author", // fieldKey
        null, // opsJson
        null, // negatableOpsJson
        false, // supportsNot
        null, // termMatchesJson
        false, // termCaseSensitiveAllowed
        false, // termAllowBlank
        1, // termMinLength
        100, // termMaxLength
        null, // termPattern
        10, // inMaxSize
        false, // inCaseSensitiveAllowed
        "NONE", // rangeKindCode
        false, // rangeAllowOpenStart
        false, // rangeAllowOpenEnd
        false, // rangeAllowClosedAtInfinity
        null, // dateMin
        null, // dateMax
        null, // datetimeMin
        null, // datetimeMax
        null, // numberMin
        null, // numberMax
        false, // existsSupported
        null, // tokenKindsJson
        null, // tokenValuePattern
        Instant.parse("2025-01-01T00:00:00Z"), // effectiveFrom
        null // effectiveTo
        );
  }

  /**
   * 创建测试用的 ExprRenderRuleQuery 实例。
   *
   * @return ExprRenderRuleQuery 实例
   */
  private static ExprRenderRuleQuery createExprRenderRuleQuery() {
    return new ExprRenderRuleQuery(
        1L, // provenanceId
        "SEARCH", // operationType
        "author", // fieldKey
        "EQ", // opCode
        null, // matchTypeCode
        null, // negated
        null, // valueTypeCode
        "PARAM", // emitTypeCode
        "author={value}", // template
        null, // itemTemplate
        null, // joiner
        false, // wrapGroup
        null, // paramsJson
        null, // functionCode
        Instant.parse("2025-01-01T00:00:00Z"), // effectiveFrom
        null // effectiveTo
        );
  }

  /**
   * 创建测试用的 ApiParamMappingQuery 实例。
   *
   * @return ApiParamMappingQuery 实例
   */
  private static ApiParamMappingQuery createApiParamMappingQuery() {
    return new ApiParamMappingQuery(
        1L, // provenanceId
        "SEARCH", // operationType
        "search", // endpointName
        "author", // stdKey
        "AUTH", // providerParamName
        null, // transformCode
        null, // notesJson
        Instant.parse("2025-01-01T00:00:00Z"), // effectiveFrom
        null // effectiveTo
        );
  }
}
