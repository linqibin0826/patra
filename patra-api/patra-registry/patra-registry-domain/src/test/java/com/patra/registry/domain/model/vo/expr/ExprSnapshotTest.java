package com.patra.registry.domain.model.vo.expr;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ExprSnapshot 值对象单元测试。
/// 
/// 测试策略：
/// 
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 测试 record 的非空验证（所有字段永不为 null）
///   - 验证空列表是合法的（不为 null 但可以为空）
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
/// 
/// 覆盖范围：
/// 
/// - ✅ record 构造函数创建与验证
///   - ✅ 非空验证测试（fields, capabilities, renderRules, apiParamMappings）
///   - ✅ 所有参数为 null 时抛出 NullPointerException
///   - ✅ 空列表是合法的（List.of()）
///   - ✅ record 的 equals/hashCode/toString 测试
///   - ✅ 不变性保证
/// 
/// @author Patra Team
/// @since 2.0
@DisplayName("ExprSnapshot 单元测试")
class ExprSnapshotTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有非空列表的快照")
    void shouldCreateSnapshotWithAllNonEmptyLists() {
      // Given: 准备测试数据
      List<ExprField> fields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> capabilities =
          List.of(ExprSnapshotTestDataBuilder.buildExprCapability());
      List<ExprRenderRule> renderRules = List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule());
      List<ApiParamMapping> apiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      // When: 创建 ExprSnapshot
      ExprSnapshot snapshot = new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // Then: 验证所有字段正确赋值
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.fields()).isEqualTo(fields);
      assertThat(snapshot.capabilities()).isEqualTo(capabilities);
      assertThat(snapshot.renderRules()).isEqualTo(renderRules);
      assertThat(snapshot.apiParamMappings()).isEqualTo(apiParamMappings);
    }

    @Test
    @DisplayName("应该成功创建包含所有空列表的快照")
    void shouldCreateSnapshotWithAllEmptyLists() {
      // Given: 所有列表为空但非 null
      List<ExprField> fields = List.of();
      List<ExprCapability> capabilities = List.of();
      List<ExprRenderRule> renderRules = List.of();
      List<ApiParamMapping> apiParamMappings = List.of();

      // When: 创建 ExprSnapshot
      ExprSnapshot snapshot = new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // Then: 验证所有字段正确赋值（空列表是合法的）
      assertThat(snapshot).isNotNull();
      assertThat(snapshot.fields()).isEmpty();
      assertThat(snapshot.capabilities()).isEmpty();
      assertThat(snapshot.renderRules()).isEmpty();
      assertThat(snapshot.apiParamMappings()).isEmpty();
    }

    @Test
    @DisplayName("应该成功创建包含多个元素的列表快照")
    void shouldCreateSnapshotWithMultipleElements() {
      // Given: 准备包含多个元素的列表
      List<ExprField> fields =
          List.of(
              ExprSnapshotTestDataBuilder.buildExprField(1L, "title"),
              ExprSnapshotTestDataBuilder.buildExprField(2L, "abstract"));
      List<ExprCapability> capabilities =
          List.of(
              ExprSnapshotTestDataBuilder.buildExprCapability(1L, "title"),
              ExprSnapshotTestDataBuilder.buildExprCapability(2L, "abstract"));
      List<ExprRenderRule> renderRules =
          List.of(
              ExprSnapshotTestDataBuilder.buildExprRenderRule(1L, "title"),
              ExprSnapshotTestDataBuilder.buildExprRenderRule(2L, "abstract"));
      List<ApiParamMapping> apiParamMappings =
          List.of(
              ExprSnapshotTestDataBuilder.buildApiParamMapping(1L, "from"),
              ExprSnapshotTestDataBuilder.buildApiParamMapping(2L, "to"));

      // When: 创建 ExprSnapshot
      ExprSnapshot snapshot = new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // Then: 验证所有字段包含正确数量的元素
      assertThat(snapshot.fields()).hasSize(2);
      assertThat(snapshot.capabilities()).hasSize(2);
      assertThat(snapshot.renderRules()).hasSize(2);
      assertThat(snapshot.apiParamMappings()).hasSize(2);
    }
  }

  // ========== 非空验证测试 ==========

  @Nested
  @DisplayName("非空验证")
  class NonNullValidationTests {

    @Test
    @DisplayName("应该抛出异常当 fields 为 null")
    void shouldThrowExceptionWhenFieldsIsNull() {
      // Given: fields 为 null
      List<ExprField> fields = null;
      List<ExprCapability> capabilities =
          List.of(ExprSnapshotTestDataBuilder.buildExprCapability());
      List<ExprRenderRule> renderRules = List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule());
      List<ApiParamMapping> apiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      // When & Then: 创建快照应该失败
      assertThatThrownBy(
              () -> new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fields");
    }

    @Test
    @DisplayName("应该抛出异常当 capabilities 为 null")
    void shouldThrowExceptionWhenCapabilitiesIsNull() {
      // Given: capabilities 为 null
      List<ExprField> fields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> capabilities = null;
      List<ExprRenderRule> renderRules = List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule());
      List<ApiParamMapping> apiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      // When & Then: 创建快照应该失败
      assertThatThrownBy(
              () -> new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("capabilities");
    }

    @Test
    @DisplayName("应该抛出异常当 renderRules 为 null")
    void shouldThrowExceptionWhenRenderRulesIsNull() {
      // Given: renderRules 为 null
      List<ExprField> fields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> capabilities =
          List.of(ExprSnapshotTestDataBuilder.buildExprCapability());
      List<ExprRenderRule> renderRules = null;
      List<ApiParamMapping> apiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      // When & Then: 创建快照应该失败
      assertThatThrownBy(
              () -> new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("renderRules");
    }

    @Test
    @DisplayName("应该抛出异常当 apiParamMappings 为 null")
    void shouldThrowExceptionWhenApiParamMappingsIsNull() {
      // Given: apiParamMappings 为 null
      List<ExprField> fields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> capabilities =
          List.of(ExprSnapshotTestDataBuilder.buildExprCapability());
      List<ExprRenderRule> renderRules = List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule());
      List<ApiParamMapping> apiParamMappings = null;

      // When & Then: 创建快照应该失败
      assertThatThrownBy(
              () -> new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("apiParamMappings");
    }

    @Test
    @DisplayName("应该抛出异常当所有参数为 null")
    void shouldThrowExceptionWhenAllParametersAreNull() {
      // Given: 所有参数为 null
      List<ExprField> fields = null;
      List<ExprCapability> capabilities = null;
      List<ExprRenderRule> renderRules = null;
      List<ApiParamMapping> apiParamMappings = null;

      // When & Then: 创建快照应该失败（至少一个参数检查会抛出异常）
      assertThatThrownBy(
              () -> new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("应该抛出异常当前三个参数为 null")
    void shouldThrowExceptionWhenFirstThreeParametersAreNull() {
      // Given: 前三个参数为 null
      List<ExprField> fields = null;
      List<ExprCapability> capabilities = null;
      List<ExprRenderRule> renderRules = null;
      List<ApiParamMapping> apiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      // When & Then: 创建快照应该失败
      assertThatThrownBy(
              () -> new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 字段应该是不可变的")
    void recordFieldsShouldBeImmutable() {
      // Given: 创建快照
      List<ExprField> originalFields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> originalCapabilities =
          List.of(ExprSnapshotTestDataBuilder.buildExprCapability());
      List<ExprRenderRule> originalRenderRules =
          List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule());
      List<ApiParamMapping> originalApiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      ExprSnapshot snapshot =
          new ExprSnapshot(
              originalFields, originalCapabilities, originalRenderRules, originalApiParamMappings);

      // When: 获取字段引用
      List<ExprField> retrievedFields = snapshot.fields();
      List<ExprCapability> retrievedCapabilities = snapshot.capabilities();
      List<ExprRenderRule> retrievedRenderRules = snapshot.renderRules();
      List<ApiParamMapping> retrievedApiParamMappings = snapshot.apiParamMappings();

      // Then: 引用应该保持不变（值对象不变性）
      assertThat(retrievedFields).isSameAs(originalFields);
      assertThat(retrievedCapabilities).isSameAs(originalCapabilities);
      assertThat(retrievedRenderRules).isSameAs(originalRenderRules);
      assertThat(retrievedApiParamMappings).isSameAs(originalApiParamMappings);
    }

    @Test
    @DisplayName("列表内容应该在创建后保持不变")
    void listContentsShouldRemainUnchangedAfterCreation() {
      // Given: 创建包含多个元素的快照
      List<ExprField> fields =
          List.of(
              ExprSnapshotTestDataBuilder.buildExprField(1L, "title"),
              ExprSnapshotTestDataBuilder.buildExprField(2L, "abstract"));

      ExprSnapshot snapshot =
          new ExprSnapshot(
              fields,
              List.of(ExprSnapshotTestDataBuilder.buildExprCapability()),
              List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule()),
              List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping()));

      // When: 获取列表大小
      int originalSize = snapshot.fields().size();

      // Then: 列表大小应该保持不变
      assertThat(snapshot.fields()).hasSize(originalSize);
      assertThat(snapshot.fields()).hasSize(2);
      assertThat(snapshot.fields().getFirst().fieldKey()).isEqualTo("title");
      assertThat(snapshot.fields().get(1).fieldKey()).isEqualTo("abstract");
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确实现 equals 方法（相同值对象相等）")
    void shouldImplementEqualsCorrectly() {
      // Given: 两个相同值的快照
      List<ExprField> fields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> capabilities =
          List.of(ExprSnapshotTestDataBuilder.buildExprCapability());
      List<ExprRenderRule> renderRules = List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule());
      List<ApiParamMapping> apiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      ExprSnapshot snapshot1 =
          new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);
      ExprSnapshot snapshot2 =
          new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // When & Then: 应该相等
      assertThat(snapshot1).isEqualTo(snapshot2);
      assertThat(snapshot1).hasSameHashCodeAs(snapshot2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的快照
      List<ExprField> fields1 = List.of(ExprSnapshotTestDataBuilder.buildExprField(1L, "title"));
      List<ExprField> fields2 = List.of(ExprSnapshotTestDataBuilder.buildExprField(2L, "abstract"));

      ExprSnapshot snapshot1 =
          new ExprSnapshot(
              fields1,
              List.of(ExprSnapshotTestDataBuilder.buildExprCapability()),
              List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule()),
              List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping()));

      ExprSnapshot snapshot2 =
          new ExprSnapshot(
              fields2,
              List.of(ExprSnapshotTestDataBuilder.buildExprCapability()),
              List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule()),
              List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping()));

      // When & Then: 不应该相等
      assertThat(snapshot1).isNotEqualTo(snapshot2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的快照
      List<ExprField> fields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> capabilities =
          List.of(ExprSnapshotTestDataBuilder.buildExprCapability());
      List<ExprRenderRule> renderRules = List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule());
      List<ApiParamMapping> apiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      ExprSnapshot snapshot1 =
          new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);
      ExprSnapshot snapshot2 =
          new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // When & Then: hashCode 应该相等
      assertThat(snapshot1.hashCode()).isEqualTo(snapshot2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建快照
      ExprSnapshot snapshot =
          new ExprSnapshot(
              List.of(ExprSnapshotTestDataBuilder.buildExprField()),
              List.of(ExprSnapshotTestDataBuilder.buildExprCapability()),
              List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule()),
              List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping()));

      // When: 调用 toString
      String toString = snapshot.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("ExprSnapshot");
      assertThat(toString).contains("fields");
      assertThat(toString).contains("capabilities");
      assertThat(toString).contains("renderRules");
      assertThat(toString).contains("apiParamMappings");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建快照
      ExprSnapshot snapshot =
          new ExprSnapshot(
              List.of(ExprSnapshotTestDataBuilder.buildExprField()),
              List.of(ExprSnapshotTestDataBuilder.buildExprCapability()),
              List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule()),
              List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping()));

      // When & Then: 对象应该等于自身
      assertThat(snapshot).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("应该支持 equals 对称性")
    void shouldSupportEqualsSymmetry() {
      // Given: 两个相同值的快照
      List<ExprField> fields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> capabilities =
          List.of(ExprSnapshotTestDataBuilder.buildExprCapability());
      List<ExprRenderRule> renderRules = List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule());
      List<ApiParamMapping> apiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      ExprSnapshot snapshot1 =
          new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);
      ExprSnapshot snapshot2 =
          new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // When & Then: 对称性（a.equals(b) == b.equals(a)）
      assertThat(snapshot1.equals(snapshot2)).isEqualTo(snapshot2.equals(snapshot1));
      assertThat(snapshot1).isEqualTo(snapshot2);
      assertThat(snapshot2).isEqualTo(snapshot1);
    }

    @Test
    @DisplayName("应该支持 equals 传递性")
    void shouldSupportEqualsTransitivity() {
      // Given: 三个相同值的快照
      List<ExprField> fields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> capabilities =
          List.of(ExprSnapshotTestDataBuilder.buildExprCapability());
      List<ExprRenderRule> renderRules = List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule());
      List<ApiParamMapping> apiParamMappings =
          List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping());

      ExprSnapshot snapshot1 =
          new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);
      ExprSnapshot snapshot2 =
          new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);
      ExprSnapshot snapshot3 =
          new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // When & Then: 传递性（a.equals(b) && b.equals(c) => a.equals(c)）
      assertThat(snapshot1).isEqualTo(snapshot2);
      assertThat(snapshot2).isEqualTo(snapshot3);
      assertThat(snapshot1).isEqualTo(snapshot3);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件处理")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理所有列表为空的情况")
    void shouldHandleAllEmptyLists() {
      // Given: 所有列表为空
      List<ExprField> fields = List.of();
      List<ExprCapability> capabilities = List.of();
      List<ExprRenderRule> renderRules = List.of();
      List<ApiParamMapping> apiParamMappings = List.of();

      // When: 创建快照
      ExprSnapshot snapshot = new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // Then: 应该成功创建
      assertThat(snapshot.fields()).isEmpty();
      assertThat(snapshot.capabilities()).isEmpty();
      assertThat(snapshot.renderRules()).isEmpty();
      assertThat(snapshot.apiParamMappings()).isEmpty();
    }

    @Test
    @DisplayName("应该处理仅 fields 非空的情况")
    void shouldHandleOnlyFieldsNonEmpty() {
      // Given: 仅 fields 非空
      List<ExprField> fields = List.of(ExprSnapshotTestDataBuilder.buildExprField());
      List<ExprCapability> capabilities = List.of();
      List<ExprRenderRule> renderRules = List.of();
      List<ApiParamMapping> apiParamMappings = List.of();

      // When: 创建快照
      ExprSnapshot snapshot = new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // Then: 应该成功创建
      assertThat(snapshot.fields()).hasSize(1);
      assertThat(snapshot.capabilities()).isEmpty();
      assertThat(snapshot.renderRules()).isEmpty();
      assertThat(snapshot.apiParamMappings()).isEmpty();
    }

    @Test
    @DisplayName("应该处理大量元素的列表")
    void shouldHandleLargeListOfElements() {
      // Given: 准备包含大量元素的列表（模拟大规模配置）
      List<ExprField> fields =
          List.of(
              ExprSnapshotTestDataBuilder.buildExprField(1L, "field1"),
              ExprSnapshotTestDataBuilder.buildExprField(2L, "field2"),
              ExprSnapshotTestDataBuilder.buildExprField(3L, "field3"),
              ExprSnapshotTestDataBuilder.buildExprField(4L, "field4"),
              ExprSnapshotTestDataBuilder.buildExprField(5L, "field5"));

      // When: 创建快照
      ExprSnapshot snapshot =
          new ExprSnapshot(
              fields,
              List.of(ExprSnapshotTestDataBuilder.buildExprCapability()),
              List.of(ExprSnapshotTestDataBuilder.buildExprRenderRule()),
              List.of(ExprSnapshotTestDataBuilder.buildApiParamMapping()));

      // Then: 应该成功创建并保留所有元素
      assertThat(snapshot.fields()).hasSize(5);
      assertThat(snapshot.fields().getFirst().fieldKey()).isEqualTo("field1");
      assertThat(snapshot.fields().get(4).fieldKey()).isEqualTo("field5");
    }
  }

  // ========== TestDataBuilder (辅助类) ==========

  /// ExprSnapshot 测试数据构建器。
/// 
/// 提供便捷的测试数据构建方法以简化测试用例编写。
  static class ExprSnapshotTestDataBuilder {

    /// 构建默认的 ExprField
    static ExprField buildExprField() {
      return buildExprField(1L, "title");
    }

    /// 构建指定 ID 和 fieldKey 的 ExprField
    static ExprField buildExprField(Long id, String fieldKey) {
      return new ExprField(
          id, // id
          fieldKey, // fieldKey
          "Title", // displayName
          "Article title", // description
          "TEXT", // dataTypeCode
          "SINGLE", // cardinalityCode
          true, // exposable
          false // dateField
          );
    }

    /// 构建默认的 ExprCapability
    static ExprCapability buildExprCapability() {
      return buildExprCapability(1L, "title");
    }

    /// 构建指定 ID 和 fieldKey 的 ExprCapability
    static ExprCapability buildExprCapability(Long id, String fieldKey) {
      return new ExprCapability(
          id, // id
          1001L, // provenanceId
          "HARVEST", // operationType
          fieldKey, // fieldKey
          Instant.parse("2025-01-01T00:00:00Z"), // effectiveFrom
          null, // effectiveTo (永久有效)
          "[\"TERM\",\"IN\"]", // opsJson
          "[\"TERM\"]", // negatableOpsJson
          true, // supportsNot
          "[\"PHRASE\",\"EXACT\"]", // termMatchesJson
          false, // termCaseSensitiveAllowed
          false, // termAllowBlank
          1, // termMinLength
          500, // termMaxLength
          null, // termPattern
          100, // inMaxSize
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
          true, // existsSupported
          null, // tokenKindsJson
          null // tokenValuePattern
          );
    }

    /// 构建默认的 ExprRenderRule
    static ExprRenderRule buildExprRenderRule() {
      return buildExprRenderRule(1L, "title");
    }

    /// 构建指定 ID 和 fieldKey 的 ExprRenderRule
    static ExprRenderRule buildExprRenderRule(Long id, String fieldKey) {
      return new ExprRenderRule(
          id, // id
          1001L, // provenanceId
          "HARVEST", // operationType
          fieldKey, // fieldKey
          "TERM", // opCode
          "PHRASE", // matchTypeCode
          false, // negated
          "STRING", // valueTypeCode
          "QUERY", // emitTypeCode
          "PHRASE", // matchTypeKey
          "F", // negatedKey
          "STRING", // valueTypeKey
          Instant.parse("2025-01-01T00:00:00Z"), // effectiveFrom
          null, // effectiveTo (永久有效)
          "{{fieldKey}}={{value}}", // template
          null, // itemTemplate
          null, // joiner
          false, // wrapGroup
          null, // paramsJson
          null // functionCode
          );
    }

    /// 构建默认的 ApiParamMapping
    static ApiParamMapping buildApiParamMapping() {
      return buildApiParamMapping(1L, "from");
    }

    /// 构建指定 ID 和 stdKey 的 ApiParamMapping
    static ApiParamMapping buildApiParamMapping(Long id, String stdKey) {
      return new ApiParamMapping(
          id, // id
          1001L, // provenanceId
          "HARVEST", // operationType
          "search", // endpointName
          stdKey, // stdKey
          "mindate", // providerParamName
          null, // transformCode
          null, // notesJson
          Instant.parse("2025-01-01T00:00:00Z"), // effectiveFrom
          null // effectiveTo (永久有效)
          );
    }
  }
}
