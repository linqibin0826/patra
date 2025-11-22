package com.patra.registry.domain.model.vo.expr;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ApiParamMapping 值对象单元测试。
///
/// 测试策略：
///
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 测试 record 的业务约束验证（正整数 ID、非空白字符串、必需字段等）
///   - 验证字符串字段自动 trim 处理
///   - 测试可选字段的 null 处理
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///
/// 覆盖范围：
///
/// - ✅ record 构造函数验证测试
///   - ✅ 正整数 ID 验证（id, provenanceId）
///   - ✅ 非空白字符串验证（stdKey, providerParamName）
///   - ✅ 必需字段非 null 验证（effectiveFrom）
///   - ✅ 字符串 trim 处理测试
///   - ✅ 可选字段处理（operationType, endpointName, transformCode, notesJson, effectiveTo）
///   - ✅ record 的 equals/hashCode/toString 测试
///   - ✅ 不变性保证
///   - ✅ 业务场景测试（API 参数映射、时间有效性等）
///   - ✅ 边界条件处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ApiParamMapping 单元测试")
class ApiParamMappingTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的 API 参数映射")
    void shouldCreateApiParamMappingWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      String endpointName = "search";
      String stdKey = "from";
      String providerParamName = "mindate";
      String transformCode = "TO_EXCLUSIVE_MINUS_1D";
      String notesJson = "{\"note\":\"PubMed date format\"}";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              id,
              provenanceId,
              operationType,
              endpointName,
              stdKey,
              providerParamName,
              transformCode,
              notesJson,
              effectiveFrom,
              effectiveTo);

      // Then: 验证所有字段正确赋值
      assertThat(mapping).isNotNull();
      assertThat(mapping.id()).isEqualTo(id);
      assertThat(mapping.provenanceId()).isEqualTo(provenanceId);
      assertThat(mapping.operationType()).isEqualTo(operationType);
      assertThat(mapping.endpointName()).isEqualTo(endpointName);
      assertThat(mapping.stdKey()).isEqualTo(stdKey);
      assertThat(mapping.providerParamName()).isEqualTo(providerParamName);
      assertThat(mapping.transformCode()).isEqualTo(transformCode);
      assertThat(mapping.notesJson()).isEqualTo(notesJson);
      assertThat(mapping.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(mapping.effectiveTo()).isEqualTo(effectiveTo);
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小 API 参数映射")
    void shouldCreateMinimalApiParamMapping() {
      // Given: 只有必需字段
      Long id = 1001L;
      Long provenanceId = 2001L;
      String stdKey = "term";
      String providerParamName = "q";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              id,
              provenanceId,
              null, // operationType
              null, // endpointName
              stdKey,
              providerParamName,
              null, // transformCode
              null, // notesJson
              effectiveFrom,
              null); // effectiveTo

      // Then: 验证必需字段正确赋值，可选字段为 null
      assertThat(mapping).isNotNull();
      assertThat(mapping.id()).isEqualTo(id);
      assertThat(mapping.provenanceId()).isEqualTo(provenanceId);
      assertThat(mapping.operationType()).isNull();
      assertThat(mapping.endpointName()).isNull();
      assertThat(mapping.stdKey()).isEqualTo(stdKey);
      assertThat(mapping.providerParamName()).isEqualTo(providerParamName);
      assertThat(mapping.transformCode()).isNull();
      assertThat(mapping.notesJson()).isNull();
      assertThat(mapping.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(mapping.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 effectiveTo 为 null 的永久有效映射")
    void shouldCreatePermanentMapping() {
      // Given: effectiveTo 为 null（表示永久有效）
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              effectiveFrom,
              effectiveTo);

      // Then: 验证 effectiveTo 为 null
      assertThat(mapping.effectiveTo()).isNull();
    }
  }

  // ========== ID 验证测试 ==========

  @Nested
  @DisplayName("ID 正整数验证")
  class IdValidationTests {

    @Test
    @DisplayName("应该抛出异常当 id 为 null")
    void shouldThrowExceptionWhenIdIsNull() {
      // Given: id 为 null
      Long id = null;

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      id,
                      2001L,
                      "HARVEST",
                      "search",
                      "from",
                      "mindate",
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Mapping id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为 0")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given: id 为 0
      Long id = 0L;

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      id,
                      2001L,
                      "HARVEST",
                      "search",
                      "from",
                      "mindate",
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Mapping id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为负数")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given: id 为负数
      Long id = -1L;

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      id,
                      2001L,
                      "HARVEST",
                      "search",
                      "from",
                      "mindate",
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Mapping id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的映射")
    void shouldCreateMappingWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              id, 2001L, "HARVEST", "search", "from", "mindate", null, null, Instant.now(), null);

      // Then: 验证成功创建
      assertThat(mapping.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的映射")
    void shouldCreateMappingWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              id, 2001L, "HARVEST", "search", "from", "mindate", null, null, Instant.now(), null);

      // Then: 验证成功创建
      assertThat(mapping.id()).isEqualTo(Long.MAX_VALUE);
    }
  }

  @Nested
  @DisplayName("ProvenanceId 正整数验证")
  class ProvenanceIdValidationTests {

    @Test
    @DisplayName("应该抛出异常当 provenanceId 为 null")
    void shouldThrowExceptionWhenProvenanceIdIsNull() {
      // Given: provenanceId 为 null
      Long provenanceId = null;

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      "search",
                      "from",
                      "mindate",
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 provenanceId 为 0")
    void shouldThrowExceptionWhenProvenanceIdIsZero() {
      // Given: provenanceId 为 0
      Long provenanceId = 0L;

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      "search",
                      "from",
                      "mindate",
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 provenanceId 为负数")
    void shouldThrowExceptionWhenProvenanceIdIsNegative() {
      // Given: provenanceId 为负数
      Long provenanceId = -1L;

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      "search",
                      "from",
                      "mindate",
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 provenanceId 为 1 的映射")
    void shouldCreateMappingWithProvenanceIdOne() {
      // Given: provenanceId 为 1
      Long provenanceId = 1L;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              provenanceId,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // Then: 验证成功创建
      assertThat(mapping.provenanceId()).isEqualTo(1L);
    }
  }

  // ========== StdKey 验证测试 ==========

  @Nested
  @DisplayName("StdKey 非空白验证")
  class StdKeyValidationTests {

    @Test
    @DisplayName("应该抛出异常当 stdKey 为 null")
    void shouldThrowExceptionWhenStdKeyIsNull() {
      // Given: stdKey 为 null
      String stdKey = null;

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      2001L,
                      "HARVEST",
                      "search",
                      stdKey,
                      "mindate",
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Standard key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 stdKey 为空字符串")
    void shouldThrowExceptionWhenStdKeyIsEmpty() {
      // Given: stdKey 为空字符串
      String stdKey = "";

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      2001L,
                      "HARVEST",
                      "search",
                      stdKey,
                      "mindate",
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Standard key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 stdKey 仅包含空白字符")
    void shouldThrowExceptionWhenStdKeyIsBlank() {
      // Given: stdKey 仅包含空白字符
      String stdKey = "   ";

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      2001L,
                      "HARVEST",
                      "search",
                      stdKey,
                      "mindate",
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Standard key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim stdKey 字段")
    void shouldTrimStdKey() {
      // Given: stdKey 包含首尾空白
      String stdKey = "  from  ";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              stdKey,
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // Then: 验证 stdKey 已被 trim
      assertThat(mapping.stdKey()).isEqualTo("from");
    }
  }

  // ========== ProviderParamName 验证测试 ==========

  @Nested
  @DisplayName("ProviderParamName 非空白验证")
  class ProviderParamNameValidationTests {

    @Test
    @DisplayName("应该抛出异常当 providerParamName 为 null")
    void shouldThrowExceptionWhenProviderParamNameIsNull() {
      // Given: providerParamName 为 null
      String providerParamName = null;

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      2001L,
                      "HARVEST",
                      "search",
                      "from",
                      providerParamName,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provider param name")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 providerParamName 为空字符串")
    void shouldThrowExceptionWhenProviderParamNameIsEmpty() {
      // Given: providerParamName 为空字符串
      String providerParamName = "";

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      2001L,
                      "HARVEST",
                      "search",
                      "from",
                      providerParamName,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provider param name")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 providerParamName 仅包含空白字符")
    void shouldThrowExceptionWhenProviderParamNameIsBlank() {
      // Given: providerParamName 仅包含空白字符
      String providerParamName = "   ";

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      2001L,
                      "HARVEST",
                      "search",
                      "from",
                      providerParamName,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provider param name")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim providerParamName 字段")
    void shouldTrimProviderParamName() {
      // Given: providerParamName 包含首尾空白
      String providerParamName = "  mindate  ";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              providerParamName,
              null,
              null,
              Instant.now(),
              null);

      // Then: 验证 providerParamName 已被 trim
      assertThat(mapping.providerParamName()).isEqualTo("mindate");
    }
  }

  // ========== EffectiveFrom 验证测试 ==========

  @Nested
  @DisplayName("EffectiveFrom 非 null 验证")
  class EffectiveFromValidationTests {

    @Test
    @DisplayName("应该抛出异常当 effectiveFrom 为 null")
    void shouldThrowExceptionWhenEffectiveFromIsNull() {
      // Given: effectiveFrom 为 null
      Instant effectiveFrom = null;

      // When & Then: 创建映射应该失败
      assertThatThrownBy(
              () ->
                  new ApiParamMapping(
                      1001L,
                      2001L,
                      "HARVEST",
                      "search",
                      "from",
                      "mindate",
                      null,
                      null,
                      effectiveFrom,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Effective from")
          .hasMessageContaining("不能为 null");
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为当前时间的映射")
    void shouldCreateMappingWithCurrentEffectiveFrom() {
      // Given: effectiveFrom 为当前时间
      Instant effectiveFrom = Instant.now();

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              effectiveFrom,
              null);

      // Then: 验证成功创建
      assertThat(mapping.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为过去时间的映射")
    void shouldCreateMappingWithPastEffectiveFrom() {
      // Given: effectiveFrom 为过去时间
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              effectiveFrom,
              null);

      // Then: 验证成功创建
      assertThat(mapping.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为未来时间的映射")
    void shouldCreateMappingWithFutureEffectiveFrom() {
      // Given: effectiveFrom 为未来时间
      Instant effectiveFrom = Instant.parse("2026-01-01T00:00:00Z");

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              effectiveFrom,
              null);

      // Then: 验证成功创建
      assertThat(mapping.effectiveFrom()).isEqualTo(effectiveFrom);
    }
  }

  // ========== 字符串 Trim 处理测试 ==========

  @Nested
  @DisplayName("字符串字段 Trim 处理")
  class StringTrimTests {

    @Test
    @DisplayName("应该自动 trim operationType 字段")
    void shouldTrimOperationType() {
      // Given: operationType 包含首尾空白
      String operationType = "  HARVEST  ";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              operationType,
              "search",
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // Then: 验证 operationType 已被 trim
      assertThat(mapping.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该自动 trim endpointName 字段")
    void shouldTrimEndpointName() {
      // Given: endpointName 包含首尾空白
      String endpointName = "  search  ";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              endpointName,
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // Then: 验证 endpointName 已被 trim
      assertThat(mapping.endpointName()).isEqualTo("search");
    }

    @Test
    @DisplayName("应该自动 trim transformCode 字段")
    void shouldTrimTransformCode() {
      // Given: transformCode 包含首尾空白
      String transformCode = "  TO_EXCLUSIVE_MINUS_1D  ";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              transformCode,
              null,
              Instant.now(),
              null);

      // Then: 验证 transformCode 已被 trim
      assertThat(mapping.transformCode()).isEqualTo("TO_EXCLUSIVE_MINUS_1D");
    }

    @Test
    @DisplayName("应该 trim 所有可 trim 字符串字段")
    void shouldTrimAllTrimmableStringFields() {
      // Given: 所有可 trim 字符串字段包含首尾空白
      String operationType = "  UPDATE  ";
      String endpointName = "  export  ";
      String stdKey = "  to  ";
      String providerParamName = "  maxdate  ";
      String transformCode = "  ISO_FORMAT  ";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              operationType,
              endpointName,
              stdKey,
              providerParamName,
              transformCode,
              null,
              Instant.now(),
              null);

      // Then: 验证所有字段都已被 trim
      assertThat(mapping.operationType()).isEqualTo("UPDATE");
      assertThat(mapping.endpointName()).isEqualTo("export");
      assertThat(mapping.stdKey()).isEqualTo("to");
      assertThat(mapping.providerParamName()).isEqualTo("maxdate");
      assertThat(mapping.transformCode()).isEqualTo("ISO_FORMAT");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String operationType = "\t\n  BACKFILL  \t\n";
      String stdKey = " \t from \n ";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              operationType,
              "search",
              stdKey,
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // Then: 验证空白字符都被 trim
      assertThat(mapping.operationType()).isEqualTo("BACKFILL");
      assertThat(mapping.stdKey()).isEqualTo("from");
    }
  }

  // ========== Null 处理测试 ==========

  @Nested
  @DisplayName("可选字段 Null 处理")
  class NullHandlingTests {

    @Test
    @DisplayName("operationType 为 null 时应保持 null")
    void operationTypeCanBeNull() {
      // Given: operationType 为 null
      String operationType = null;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              operationType,
              "search",
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // Then: 验证 operationType 为 null
      assertThat(mapping.operationType()).isNull();
    }

    @Test
    @DisplayName("endpointName 为 null 时应保持 null")
    void endpointNameCanBeNull() {
      // Given: endpointName 为 null
      String endpointName = null;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              endpointName,
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // Then: 验证 endpointName 为 null
      assertThat(mapping.endpointName()).isNull();
    }

    @Test
    @DisplayName("transformCode 为 null 时应保持 null")
    void transformCodeCanBeNull() {
      // Given: transformCode 为 null
      String transformCode = null;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              transformCode,
              null,
              Instant.now(),
              null);

      // Then: 验证 transformCode 为 null
      assertThat(mapping.transformCode()).isNull();
    }

    @Test
    @DisplayName("notesJson 为 null 时应保持 null")
    void notesJsonCanBeNull() {
      // Given: notesJson 为 null
      String notesJson = null;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              notesJson,
              Instant.now(),
              null);

      // Then: 验证 notesJson 为 null
      assertThat(mapping.notesJson()).isNull();
    }

    @Test
    @DisplayName("effectiveTo 为 null 时应保持 null")
    void effectiveToCanBeNull() {
      // Given: effectiveTo 为 null
      Instant effectiveTo = null;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              effectiveTo);

      // Then: 验证 effectiveTo 为 null
      assertThat(mapping.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("应该处理所有可选字段都为 null 的情况")
    void shouldHandleAllOptionalFieldsBeingNull() {
      // Given: 所有可选字段为 null
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              null, // operationType
              null, // endpointName
              "from",
              "mindate",
              null, // transformCode
              null, // notesJson
              Instant.now(),
              null); // effectiveTo

      // Then: 验证可选字段都为 null
      assertThat(mapping.operationType()).isNull();
      assertThat(mapping.endpointName()).isNull();
      assertThat(mapping.transformCode()).isNull();
      assertThat(mapping.notesJson()).isNull();
      assertThat(mapping.effectiveTo()).isNull();
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确实现 equals 方法（相同值对象相等）")
    void shouldImplementEqualsCorrectly() {
      // Given: 两个相同值的映射
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      String endpointName = "search";
      String stdKey = "from";
      String providerParamName = "mindate";
      String transformCode = "TO_EXCLUSIVE_MINUS_1D";
      String notesJson = "{\"note\":\"test\"}";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");

      ApiParamMapping mapping1 =
          new ApiParamMapping(
              id,
              provenanceId,
              operationType,
              endpointName,
              stdKey,
              providerParamName,
              transformCode,
              notesJson,
              effectiveFrom,
              effectiveTo);

      ApiParamMapping mapping2 =
          new ApiParamMapping(
              id,
              provenanceId,
              operationType,
              endpointName,
              stdKey,
              providerParamName,
              transformCode,
              notesJson,
              effectiveFrom,
              effectiveTo);

      // When & Then: 应该相等
      assertThat(mapping1).isEqualTo(mapping2);
      assertThat(mapping1).hasSameHashCodeAs(mapping2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的映射
      ApiParamMapping mapping1 =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      ApiParamMapping mapping2 =
          new ApiParamMapping(
              1002L, 2002L, "UPDATE", "export", "to", "maxdate", null, null, Instant.now(), null);

      // When & Then: 不应该相等
      assertThat(mapping1).isNotEqualTo(mapping2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的映射
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      ApiParamMapping mapping1 =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              effectiveFrom,
              null);

      ApiParamMapping mapping2 =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              effectiveFrom,
              null);

      // When & Then: hashCode 应该相等
      assertThat(mapping1.hashCode()).isEqualTo(mapping2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建映射
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              "TO_EXCLUSIVE_MINUS_1D",
              "{\"note\":\"test\"}",
              Instant.parse("2025-01-01T00:00:00Z"),
              null);

      // When: 调用 toString
      String toString = mapping.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("ApiParamMapping");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("2001");
      assertThat(toString).contains("HARVEST");
      assertThat(toString).contains("from");
      assertThat(toString).contains("mindate");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建映射
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // When & Then: 对象应该等于自身
      assertThat(mapping).isEqualTo(mapping);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建映射
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // When & Then: 与 null 比较应该返回 false
      assertThat(mapping).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建映射
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(mapping).isNotEqualTo("Not an ApiParamMapping");
      assertThat(mapping).isNotEqualTo(1001L);
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 字段应该是不可变的")
    void recordFieldsShouldBeImmutable() {
      // Given: 创建映射
      Long originalId = 1001L;
      Long originalProvenanceId = 2001L;
      String originalOperationType = "HARVEST";
      String originalEndpointName = "search";
      String originalStdKey = "from";
      String originalProviderParamName = "mindate";
      Instant originalEffectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      ApiParamMapping mapping =
          new ApiParamMapping(
              originalId,
              originalProvenanceId,
              originalOperationType,
              originalEndpointName,
              originalStdKey,
              originalProviderParamName,
              null,
              null,
              originalEffectiveFrom,
              null);

      // When: 获取字段值
      Long retrievedId = mapping.id();
      Long retrievedProvenanceId = mapping.provenanceId();
      String retrievedOperationType = mapping.operationType();
      String retrievedEndpointName = mapping.endpointName();
      String retrievedStdKey = mapping.stdKey();
      String retrievedProviderParamName = mapping.providerParamName();
      Instant retrievedEffectiveFrom = mapping.effectiveFrom();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedProvenanceId).isEqualTo(originalProvenanceId);
      assertThat(retrievedOperationType).isEqualTo(originalOperationType);
      assertThat(retrievedEndpointName).isEqualTo(originalEndpointName);
      assertThat(retrievedStdKey).isEqualTo(originalStdKey);
      assertThat(retrievedProviderParamName).isEqualTo(originalProviderParamName);
      assertThat(retrievedEffectiveFrom).isEqualTo(originalEffectiveFrom);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建映射
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // When: 多次获取字段值
      String operationType1 = mapping.operationType();
      String operationType2 = mapping.operationType();
      String stdKey1 = mapping.stdKey();
      String stdKey2 = mapping.stdKey();

      // Then: 字段值应该保持一致
      assertThat(operationType1).isEqualTo(operationType2);
      assertThat(stdKey1).isEqualTo(stdKey2);
      assertThat(operationType1).isSameAs(operationType2);
      assertThat(stdKey1).isSameAs(stdKey2);
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenariosTests {

    @Test
    @DisplayName("应该成功创建 PubMed 日期参数映射")
    void shouldCreatePubMedDateParamMapping() {
      // Given: PubMed 日期参数映射
      String stdKey = "from";
      String providerParamName = "mindate";
      String transformCode = "TO_EXCLUSIVE_MINUS_1D";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "esearch",
              stdKey,
              providerParamName,
              transformCode,
              null,
              Instant.now(),
              null);

      // Then: 验证映射正确创建
      assertThat(mapping.stdKey()).isEqualTo("from");
      assertThat(mapping.providerParamName()).isEqualTo("mindate");
      assertThat(mapping.transformCode()).isEqualTo("TO_EXCLUSIVE_MINUS_1D");
    }

    @Test
    @DisplayName("应该成功创建通用于所有操作类型的映射")
    void shouldCreateMappingForAllOperationTypes() {
      // Given: operationType 为 null（表示应用于所有操作类型）
      String operationType = null;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L, 2001L, operationType, "search", "term", "q", null, null, Instant.now(), null);

      // Then: 验证 operationType 为 null
      assertThat(mapping.operationType()).isNull();
    }

    @Test
    @DisplayName("应该成功创建通用于所有端点的映射")
    void shouldCreateMappingForAllEndpoints() {
      // Given: endpointName 为 null（表示应用于所有端点）
      String endpointName = null;

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              endpointName,
              "apiKey",
              "api_key",
              null,
              null,
              Instant.now(),
              null);

      // Then: 验证 endpointName 为 null
      assertThat(mapping.endpointName()).isNull();
    }

    @Test
    @DisplayName("应该成功创建包含平台差异说明的映射")
    void shouldCreateMappingWithNotesJson() {
      // Given: 包含 JSON 格式的说明
      String notesJson =
          "{\"note\":\"PubMed requires date format YYYY/MM/DD\",\"boundary\":\"exclusive\"}";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "esearch",
              "from",
              "mindate",
              null,
              notesJson,
              Instant.now(),
              null);

      // Then: 验证 notesJson 正确赋值
      assertThat(mapping.notesJson()).isEqualTo(notesJson);
    }

    @Test
    @DisplayName("应该成功创建带时间有效期的映射")
    void shouldCreateMappingWithTemporalValidity() {
      // Given: 带时间有效期的映射
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              effectiveFrom,
              effectiveTo);

      // Then: 验证时间有效期正确赋值
      assertThat(mapping.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(mapping.effectiveTo()).isEqualTo(effectiveTo);
    }

    @Test
    @DisplayName("应该成功创建完整的 API 参数映射")
    void shouldCreateCompleteApiParamMapping() {
      // Given: 完整的 API 参数映射
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "esearch",
              "from",
              "mindate",
              "TO_EXCLUSIVE_MINUS_1D",
              "{\"note\":\"PubMed date format\"}",
              Instant.parse("2025-01-01T00:00:00Z"),
              Instant.parse("2025-12-31T23:59:59Z"));

      // Then: 验证所有字段
      assertThat(mapping.id()).isEqualTo(1001L);
      assertThat(mapping.provenanceId()).isEqualTo(2001L);
      assertThat(mapping.operationType()).isEqualTo("HARVEST");
      assertThat(mapping.endpointName()).isEqualTo("esearch");
      assertThat(mapping.stdKey()).isEqualTo("from");
      assertThat(mapping.providerParamName()).isEqualTo("mindate");
      assertThat(mapping.transformCode()).isEqualTo("TO_EXCLUSIVE_MINUS_1D");
      assertThat(mapping.notesJson()).isEqualTo("{\"note\":\"PubMed date format\"}");
      assertThat(mapping.effectiveFrom()).isNotNull();
      assertThat(mapping.effectiveTo()).isNotNull();
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件处理")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 trim 后相同的不同输入")
    void shouldHandleDifferentInputsWithSameTrimmedValue() {
      // Given: trim 后相同的不同输入
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      ApiParamMapping mapping1 =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              effectiveFrom,
              null);

      ApiParamMapping mapping2 =
          new ApiParamMapping(
              1001L,
              2001L,
              "  HARVEST  ",
              "  search  ",
              "  from  ",
              "  mindate  ",
              null,
              null,
              effectiveFrom,
              null);

      // When & Then: trim 后应该相等
      assertThat(mapping1).isEqualTo(mapping2);
    }

    @Test
    @DisplayName("应该处理 stdKey 为极短字符串的情况")
    void shouldHandleMinimalStdKey() {
      // Given: stdKey 为单字符
      String stdKey = "q";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L, 2001L, "HARVEST", "search", stdKey, "query", null, null, Instant.now(), null);

      // Then: 应该成功创建
      assertThat(mapping.stdKey()).isEqualTo("q");
    }

    @Test
    @DisplayName("应该处理 providerParamName 为极短字符串的情况")
    void shouldHandleMinimalProviderParamName() {
      // Given: providerParamName 为单字符
      String providerParamName = "q";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "term",
              providerParamName,
              null,
              null,
              Instant.now(),
              null);

      // Then: 应该成功创建
      assertThat(mapping.providerParamName()).isEqualTo("q");
    }

    @Test
    @DisplayName("应该处理包含特殊字符的 stdKey")
    void shouldHandleStdKeyWithSpecialCharacters() {
      // Given: stdKey 包含下划线等特殊字符
      String stdKey = "date_from";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              stdKey,
              "mindate",
              null,
              null,
              Instant.now(),
              null);

      // Then: 应该成功创建
      assertThat(mapping.stdKey()).isEqualTo("date_from");
    }

    @Test
    @DisplayName("应该处理包含特殊字符的 providerParamName")
    void shouldHandleProviderParamNameWithSpecialCharacters() {
      // Given: providerParamName 包含点号等特殊字符
      String providerParamName = "date.from";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              providerParamName,
              null,
              null,
              Instant.now(),
              null);

      // Then: 应该成功创建
      assertThat(mapping.providerParamName()).isEqualTo("date.from");
    }

    @Test
    @DisplayName("应该处理大型 JSON 格式的 notesJson")
    void shouldHandleLargeNotesJson() {
      // Given: 大型 JSON 格式的 notesJson
      String notesJson =
          "{\"note\":\"Complex mapping rules\",\"platform\":\"PubMed\",\"version\":\"2.0\","
              + "\"boundaries\":{\"from\":\"exclusive\",\"to\":\"inclusive\"},"
              + "\"transformations\":[\"ISO_FORMAT\",\"MINUS_1D\"]}";

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              notesJson,
              Instant.now(),
              null);

      // Then: 应该成功创建
      assertThat(mapping.notesJson()).isEqualTo(notesJson);
    }

    @Test
    @DisplayName("应该处理 effectiveFrom 和 effectiveTo 相同的情况")
    void shouldHandleSameEffectiveFromAndTo() {
      // Given: effectiveFrom 和 effectiveTo 相同
      Instant sameInstant = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建 ApiParamMapping
      ApiParamMapping mapping =
          new ApiParamMapping(
              1001L,
              2001L,
              "HARVEST",
              "search",
              "from",
              "mindate",
              null,
              null,
              sameInstant,
              sameInstant);

      // Then: 应该成功创建
      assertThat(mapping.effectiveFrom()).isEqualTo(sameInstant);
      assertThat(mapping.effectiveTo()).isEqualTo(sameInstant);
    }
  }
}
