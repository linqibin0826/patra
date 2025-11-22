package com.patra.registry.domain.model.vo.expr;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ExprCapability 值对象单元测试。
/// 
/// 测试策略：
/// 
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 测试 record 的业务约束验证（正整数 ID、非空白字符串、必需字段等）
///   - 验证字符串字段自动 trim 处理和 trimOrNull 处理
///   - 测试可选字段的 null 处理
///   - 验证表达式能力的各种配置场景
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
/// 
/// 覆盖范围：
/// 
/// - ✅ record 构造函数验证测试
///   - ✅ 正整数 ID 验证（id, provenanceId）
///   - ✅ 非空白字符串验证（fieldKey, rangeKindCode）
///   - ✅ 必需字段非 null 验证（effectiveFrom）
///   - ✅ 字符串 trim 处理测试
///   - ✅ trimOrNull 处理测试
///   - ✅ 可选字段处理
///   - ✅ record 的 equals/hashCode/toString 测试
///   - ✅ 不变性保证
///   - ✅ 业务场景测试（TERM/IN/RANGE/TOKEN 配置）
///   - ✅ 边界条件处理
/// 
/// @author Patra Team
/// @since 2.0
@DisplayName("ExprCapability 单元测试")
class ExprCapabilityTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的表达式能力配置")
    void shouldCreateExprCapabilityWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      String fieldKey = "author_name";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      String opsJson = "[\"TERM\",\"IN\",\"RANGE\",\"EXISTS\"]";
      String negatableOpsJson = "[\"TERM\",\"IN\"]";
      boolean supportsNot = true;
      String termMatchesJson = "[\"PHRASE\",\"EXACT\",\"ANY\"]";
      boolean termCaseSensitiveAllowed = true;
      boolean termAllowBlank = false;
      int termMinLength = 2;
      int termMaxLength = 100;
      String termPattern = "^[a-zA-Z0-9\\s]+$";
      int inMaxSize = 50;
      boolean inCaseSensitiveAllowed = true;
      String rangeKindCode = "DATE";
      boolean rangeAllowOpenStart = true;
      boolean rangeAllowOpenEnd = true;
      boolean rangeAllowClosedAtInfinity = false;
      LocalDate dateMin = LocalDate.parse("2000-01-01");
      LocalDate dateMax = LocalDate.parse("2025-12-31");
      Instant datetimeMin = Instant.parse("2000-01-01T00:00:00Z");
      Instant datetimeMax = Instant.parse("2025-12-31T23:59:59Z");
      BigDecimal numberMin = new BigDecimal("0.0");
      BigDecimal numberMax = new BigDecimal("9999.99");
      boolean existsSupported = true;
      String tokenKindsJson = "[\"owner\",\"pmcid\"]";
      String tokenValuePattern = "^PMC[0-9]+$";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              id,
              provenanceId,
              operationType,
              fieldKey,
              effectiveFrom,
              effectiveTo,
              opsJson,
              negatableOpsJson,
              supportsNot,
              termMatchesJson,
              termCaseSensitiveAllowed,
              termAllowBlank,
              termMinLength,
              termMaxLength,
              termPattern,
              inMaxSize,
              inCaseSensitiveAllowed,
              rangeKindCode,
              rangeAllowOpenStart,
              rangeAllowOpenEnd,
              rangeAllowClosedAtInfinity,
              dateMin,
              dateMax,
              datetimeMin,
              datetimeMax,
              numberMin,
              numberMax,
              existsSupported,
              tokenKindsJson,
              tokenValuePattern);

      // Then: 验证所有字段正确赋值
      assertThat(capability).isNotNull();
      assertThat(capability.id()).isEqualTo(id);
      assertThat(capability.provenanceId()).isEqualTo(provenanceId);
      assertThat(capability.operationType()).isEqualTo(operationType);
      assertThat(capability.fieldKey()).isEqualTo(fieldKey);
      assertThat(capability.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(capability.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(capability.opsJson()).isEqualTo(opsJson);
      assertThat(capability.negatableOpsJson()).isEqualTo(negatableOpsJson);
      assertThat(capability.supportsNot()).isTrue();
      assertThat(capability.termMatchesJson()).isEqualTo(termMatchesJson);
      assertThat(capability.termCaseSensitiveAllowed()).isTrue();
      assertThat(capability.termAllowBlank()).isFalse();
      assertThat(capability.termMinLength()).isEqualTo(termMinLength);
      assertThat(capability.termMaxLength()).isEqualTo(termMaxLength);
      assertThat(capability.termPattern()).isEqualTo(termPattern);
      assertThat(capability.inMaxSize()).isEqualTo(inMaxSize);
      assertThat(capability.inCaseSensitiveAllowed()).isTrue();
      assertThat(capability.rangeKindCode()).isEqualTo(rangeKindCode);
      assertThat(capability.rangeAllowOpenStart()).isTrue();
      assertThat(capability.rangeAllowOpenEnd()).isTrue();
      assertThat(capability.rangeAllowClosedAtInfinity()).isFalse();
      assertThat(capability.dateMin()).isEqualTo(dateMin);
      assertThat(capability.dateMax()).isEqualTo(dateMax);
      assertThat(capability.datetimeMin()).isEqualTo(datetimeMin);
      assertThat(capability.datetimeMax()).isEqualTo(datetimeMax);
      assertThat(capability.numberMin()).isEqualTo(numberMin);
      assertThat(capability.numberMax()).isEqualTo(numberMax);
      assertThat(capability.existsSupported()).isTrue();
      assertThat(capability.tokenKindsJson()).isEqualTo(tokenKindsJson);
      assertThat(capability.tokenValuePattern()).isEqualTo(tokenValuePattern);
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小配置")
    void shouldCreateMinimalExprCapability() {
      // Given: 只有必需字段
      Long id = 1001L;
      Long provenanceId = 2001L;
      String fieldKey = "author_name";
      String rangeKindCode = "NONE";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              id,
              provenanceId,
              null, // operationType
              fieldKey,
              effectiveFrom,
              null, // effectiveTo
              null, // opsJson
              null, // negatableOpsJson
              false, // supportsNot
              null, // termMatchesJson
              false, // termCaseSensitiveAllowed
              false, // termAllowBlank
              0, // termMinLength
              0, // termMaxLength
              null, // termPattern
              0, // inMaxSize
              false, // inCaseSensitiveAllowed
              rangeKindCode,
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
              null); // tokenValuePattern

      // Then: 验证必需字段正确赋值，可选字段为 null 或默认值
      assertThat(capability).isNotNull();
      assertThat(capability.id()).isEqualTo(id);
      assertThat(capability.provenanceId()).isEqualTo(provenanceId);
      assertThat(capability.operationType()).isNull();
      assertThat(capability.fieldKey()).isEqualTo(fieldKey);
      assertThat(capability.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(capability.effectiveTo()).isNull();
      assertThat(capability.rangeKindCode()).isEqualTo(rangeKindCode);
    }

    @Test
    @DisplayName("应该成功创建 effectiveTo 为 null 的永久有效配置")
    void shouldCreatePermanentCapability() {
      // Given: effectiveTo 为 null（表示永久有效）
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null;

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              effectiveFrom,
              effectiveTo,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 effectiveTo 为 null
      assertThat(capability.effectiveTo()).isNull();
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

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      id,
                      2001L,
                      "HARVEST",
                      "author_name",
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Capability id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为 0")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given: id 为 0
      Long id = 0L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      id,
                      2001L,
                      "HARVEST",
                      "author_name",
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Capability id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为负数")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given: id 为负数
      Long id = -1L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      id,
                      2001L,
                      "HARVEST",
                      "author_name",
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Capability id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的配置")
    void shouldCreateCapabilityWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              id,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(capability.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的配置")
    void shouldCreateCapabilityWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              id,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(capability.id()).isEqualTo(Long.MAX_VALUE);
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

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      "author_name",
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
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

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      "author_name",
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
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

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      "author_name",
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 provenanceId 为 1 的配置")
    void shouldCreateCapabilityWithProvenanceIdOne() {
      // Given: provenanceId 为 1
      Long provenanceId = 1L;

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              provenanceId,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(capability.provenanceId()).isEqualTo(1L);
    }
  }

  // ========== FieldKey 验证测试 ==========

  @Nested
  @DisplayName("FieldKey 非空白验证")
  class FieldKeyValidationTests {

    @Test
    @DisplayName("应该抛出异常当 fieldKey 为 null")
    void shouldThrowExceptionWhenFieldKeyIsNull() {
      // Given: fieldKey 为 null
      String fieldKey = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      2001L,
                      "HARVEST",
                      fieldKey,
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Field key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 fieldKey 为空字符串")
    void shouldThrowExceptionWhenFieldKeyIsEmpty() {
      // Given: fieldKey 为空字符串
      String fieldKey = "";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      2001L,
                      "HARVEST",
                      fieldKey,
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Field key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 fieldKey 仅包含空白字符")
    void shouldThrowExceptionWhenFieldKeyIsBlank() {
      // Given: fieldKey 仅包含空白字符
      String fieldKey = "   ";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      2001L,
                      "HARVEST",
                      fieldKey,
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Field key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim fieldKey 字段")
    void shouldTrimFieldKey() {
      // Given: fieldKey 包含首尾空白
      String fieldKey = "  author_name  ";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              fieldKey,
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 fieldKey 已被 trim
      assertThat(capability.fieldKey()).isEqualTo("author_name");
    }
  }

  // ========== RangeKindCode 验证测试 ==========

  @Nested
  @DisplayName("RangeKindCode 非空白验证")
  class RangeKindCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 rangeKindCode 为 null")
    void shouldThrowExceptionWhenRangeKindCodeIsNull() {
      // Given: rangeKindCode 为 null
      String rangeKindCode = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      2001L,
                      "HARVEST",
                      "author_name",
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      rangeKindCode,
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Range kind code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 rangeKindCode 为空字符串")
    void shouldThrowExceptionWhenRangeKindCodeIsEmpty() {
      // Given: rangeKindCode 为空字符串
      String rangeKindCode = "";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      2001L,
                      "HARVEST",
                      "author_name",
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      rangeKindCode,
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Range kind code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 rangeKindCode 仅包含空白字符")
    void shouldThrowExceptionWhenRangeKindCodeIsBlank() {
      // Given: rangeKindCode 仅包含空白字符
      String rangeKindCode = "   ";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      2001L,
                      "HARVEST",
                      "author_name",
                      Instant.now(),
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      rangeKindCode,
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Range kind code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim rangeKindCode 字段")
    void shouldTrimRangeKindCode() {
      // Given: rangeKindCode 包含首尾空白
      String rangeKindCode = "  DATE  ";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              rangeKindCode,
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 rangeKindCode 已被 trim
      assertThat(capability.rangeKindCode()).isEqualTo("DATE");
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

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new ExprCapability(
                      1001L,
                      2001L,
                      "HARVEST",
                      "author_name",
                      effectiveFrom,
                      null,
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Effective from")
          .hasMessageContaining("不能为 null");
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为当前时间的配置")
    void shouldCreateCapabilityWithCurrentEffectiveFrom() {
      // Given: effectiveFrom 为当前时间
      Instant effectiveFrom = Instant.now();

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              effectiveFrom,
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(capability.effectiveFrom()).isEqualTo(effectiveFrom);
    }
  }

  // ========== 字符串 Trim 和 TrimOrNull 处理测试 ==========

  @Nested
  @DisplayName("字符串字段 Trim 和 TrimOrNull 处理")
  class StringProcessingTests {

    @Test
    @DisplayName("应该自动 trim 必需字符串字段")
    void shouldTrimRequiredStringFields() {
      // Given: 必需字段包含首尾空白
      String fieldKey = "  author_name  ";
      String rangeKindCode = "  NONE  ";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              fieldKey,
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              rangeKindCode,
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证字段已被 trim
      assertThat(capability.fieldKey()).isEqualTo("author_name");
      assertThat(capability.rangeKindCode()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("应该使用 trimOrNull 处理 operationType 字段")
    void shouldTrimOrNullOperationType() {
      // Given: operationType 包含首尾空白
      String operationType = "  HARVEST  ";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              operationType,
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 operationType 已被 trim
      assertThat(capability.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该 trim 空白的 operationType 为空字符串")
    void shouldTrimBlankOperationTypeToEmpty() {
      // Given: operationType 仅包含空白字符
      String operationType = "   ";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              operationType,
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 operationType 为空字符串（trimOrNull 只是 trim，不会转换为 null）
      assertThat(capability.operationType()).isEmpty();
    }

    @Test
    @DisplayName("应该使用 trimOrNull 处理 termPattern 字段")
    void shouldTrimOrNullTermPattern() {
      // Given: termPattern 包含首尾空白
      String termPattern = "  ^[a-zA-Z]+$  ";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              termPattern,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 termPattern 已被 trim
      assertThat(capability.termPattern()).isEqualTo("^[a-zA-Z]+$");
    }

    @Test
    @DisplayName("应该 trim 空白的 termPattern 为空字符串")
    void shouldTrimBlankTermPatternToEmpty() {
      // Given: termPattern 仅包含空白字符
      String termPattern = "   ";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              termPattern,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 termPattern 为空字符串（trimOrNull 只是 trim，不会转换为 null）
      assertThat(capability.termPattern()).isEmpty();
    }

    @Test
    @DisplayName("应该使用 trimOrNull 处理 tokenValuePattern 字段")
    void shouldTrimOrNullTokenValuePattern() {
      // Given: tokenValuePattern 包含首尾空白
      String tokenValuePattern = "  ^PMC[0-9]+$  ";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              tokenValuePattern);

      // Then: 验证 tokenValuePattern 已被 trim
      assertThat(capability.tokenValuePattern()).isEqualTo("^PMC[0-9]+$");
    }

    @Test
    @DisplayName("应该 trim 空白的 tokenValuePattern 为空字符串")
    void shouldTrimBlankTokenValuePatternToEmpty() {
      // Given: tokenValuePattern 仅包含空白字符
      String tokenValuePattern = "   ";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              tokenValuePattern);

      // Then: 验证 tokenValuePattern 为空字符串（trimOrNull 只是 trim，不会转换为 null）
      assertThat(capability.tokenValuePattern()).isEmpty();
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenariosTests {

    @Test
    @DisplayName("应该成功创建 TERM 操作配置")
    void shouldCreateTermOperationCapability() {
      // Given: TERM 操作配置
      String opsJson = "[\"TERM\"]";
      String termMatchesJson = "[\"PHRASE\",\"EXACT\"]";
      int termMinLength = 2;
      int termMaxLength = 100;
      String termPattern = "^[a-zA-Z0-9\\s]+$";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              opsJson,
              null,
              false,
              termMatchesJson,
              true,
              false,
              termMinLength,
              termMaxLength,
              termPattern,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 TERM 配置正确赋值
      assertThat(capability.opsJson()).isEqualTo(opsJson);
      assertThat(capability.termMatchesJson()).isEqualTo(termMatchesJson);
      assertThat(capability.termMinLength()).isEqualTo(termMinLength);
      assertThat(capability.termMaxLength()).isEqualTo(termMaxLength);
      assertThat(capability.termPattern()).isEqualTo(termPattern);
      assertThat(capability.termCaseSensitiveAllowed()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建 IN 操作配置")
    void shouldCreateInOperationCapability() {
      // Given: IN 操作配置
      String opsJson = "[\"IN\"]";
      int inMaxSize = 50;
      boolean inCaseSensitiveAllowed = true;

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "status",
              Instant.now(),
              null,
              opsJson,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              inMaxSize,
              inCaseSensitiveAllowed,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 IN 配置正确赋值
      assertThat(capability.opsJson()).isEqualTo(opsJson);
      assertThat(capability.inMaxSize()).isEqualTo(inMaxSize);
      assertThat(capability.inCaseSensitiveAllowed()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建 DATE 类型 RANGE 配置")
    void shouldCreateDateRangeCapability() {
      // Given: DATE 类型 RANGE 配置
      String opsJson = "[\"RANGE\"]";
      String rangeKindCode = "DATE";
      LocalDate dateMin = LocalDate.parse("2000-01-01");
      LocalDate dateMax = LocalDate.parse("2025-12-31");

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "publication_date",
              Instant.now(),
              null,
              opsJson,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              rangeKindCode,
              true,
              true,
              false,
              dateMin,
              dateMax,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 DATE RANGE 配置正确赋值
      assertThat(capability.rangeKindCode()).isEqualTo(rangeKindCode);
      assertThat(capability.dateMin()).isEqualTo(dateMin);
      assertThat(capability.dateMax()).isEqualTo(dateMax);
      assertThat(capability.rangeAllowOpenStart()).isTrue();
      assertThat(capability.rangeAllowOpenEnd()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建 DATETIME 类型 RANGE 配置")
    void shouldCreateDatetimeRangeCapability() {
      // Given: DATETIME 类型 RANGE 配置
      String rangeKindCode = "DATETIME";
      Instant datetimeMin = Instant.parse("2000-01-01T00:00:00Z");
      Instant datetimeMax = Instant.parse("2025-12-31T23:59:59Z");

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "created_at",
              Instant.now(),
              null,
              "[\"RANGE\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              rangeKindCode,
              true,
              true,
              false,
              null,
              null,
              datetimeMin,
              datetimeMax,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 DATETIME RANGE 配置正确赋值
      assertThat(capability.rangeKindCode()).isEqualTo(rangeKindCode);
      assertThat(capability.datetimeMin()).isEqualTo(datetimeMin);
      assertThat(capability.datetimeMax()).isEqualTo(datetimeMax);
    }

    @Test
    @DisplayName("应该成功创建 NUMBER 类型 RANGE 配置")
    void shouldCreateNumberRangeCapability() {
      // Given: NUMBER 类型 RANGE 配置
      String rangeKindCode = "NUMBER";
      BigDecimal numberMin = new BigDecimal("0.0");
      BigDecimal numberMax = new BigDecimal("9999.99");

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "score",
              Instant.now(),
              null,
              "[\"RANGE\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              rangeKindCode,
              true,
              true,
              false,
              null,
              null,
              null,
              null,
              numberMin,
              numberMax,
              false,
              null,
              null);

      // Then: 验证 NUMBER RANGE 配置正确赋值
      assertThat(capability.rangeKindCode()).isEqualTo(rangeKindCode);
      assertThat(capability.numberMin()).isEqualTo(numberMin);
      assertThat(capability.numberMax()).isEqualTo(numberMax);
    }

    @Test
    @DisplayName("应该成功创建 EXISTS 操作配置")
    void shouldCreateExistsOperationCapability() {
      // Given: EXISTS 操作配置
      String opsJson = "[\"EXISTS\"]";
      boolean existsSupported = true;

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "optional_field",
              Instant.now(),
              null,
              opsJson,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              existsSupported,
              null,
              null);

      // Then: 验证 EXISTS 配置正确赋值
      assertThat(capability.opsJson()).isEqualTo(opsJson);
      assertThat(capability.existsSupported()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建 TOKEN 操作配置")
    void shouldCreateTokenOperationCapability() {
      // Given: TOKEN 操作配置
      String opsJson = "[\"TOKEN\"]";
      String tokenKindsJson = "[\"owner\",\"pmcid\"]";
      String tokenValuePattern = "^PMC[0-9]+$";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "identifier",
              Instant.now(),
              null,
              opsJson,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              tokenKindsJson,
              tokenValuePattern);

      // Then: 验证 TOKEN 配置正确赋值
      assertThat(capability.opsJson()).isEqualTo(opsJson);
      assertThat(capability.tokenKindsJson()).isEqualTo(tokenKindsJson);
      assertThat(capability.tokenValuePattern()).isEqualTo(tokenValuePattern);
    }

    @Test
    @DisplayName("应该成功创建支持 NOT 操作的配置")
    void shouldCreateCapabilityWithNotSupport() {
      // Given: 支持 NOT 操作
      boolean supportsNot = true;
      String opsJson = "[\"TERM\",\"IN\"]";
      String negatableOpsJson = "[\"TERM\"]";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              opsJson,
              negatableOpsJson,
              supportsNot,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 NOT 配置正确赋值
      assertThat(capability.supportsNot()).isTrue();
      assertThat(capability.negatableOpsJson()).isEqualTo(negatableOpsJson);
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确实现 equals 方法（相同值对象相等）")
    void shouldImplementEqualsCorrectly() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      ExprCapability capability1 =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              effectiveFrom,
              null,
              "[\"TERM\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      ExprCapability capability2 =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              effectiveFrom,
              null,
              "[\"TERM\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When & Then: 应该相等
      assertThat(capability1).isEqualTo(capability2);
      assertThat(capability1).hasSameHashCodeAs(capability2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的配置
      ExprCapability capability1 =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              "[\"TERM\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      ExprCapability capability2 =
          new ExprCapability(
              1002L,
              2002L,
              "UPDATE",
              "title",
              Instant.now(),
              null,
              "[\"IN\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "DATE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When & Then: 不应该相等
      assertThat(capability1).isNotEqualTo(capability2);
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建配置
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              "[\"TERM\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When: 调用 toString
      String toString = capability.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("ExprCapability");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("2001");
      assertThat(toString).contains("author_name");
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件处理")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 termMinLength 为 0 的情况（无限制）")
    void shouldHandleZeroTermMinLength() {
      // Given: termMinLength 为 0
      int termMinLength = 0;

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              "[\"TERM\"]",
              null,
              false,
              null,
              false,
              true,
              termMinLength,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(capability.termMinLength()).isEqualTo(0);
      assertThat(capability.termAllowBlank()).isTrue();
    }

    @Test
    @DisplayName("应该处理 inMaxSize 为 0 的情况（无限制）")
    void shouldHandleZeroInMaxSize() {
      // Given: inMaxSize 为 0
      int inMaxSize = 0;

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "status",
              Instant.now(),
              null,
              "[\"IN\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              inMaxSize,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(capability.inMaxSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理 BigDecimal 极值")
    void shouldHandleBigDecimalExtremeValues() {
      // Given: BigDecimal 极值
      BigDecimal numberMin = new BigDecimal("-999999999999.999999");
      BigDecimal numberMax = new BigDecimal("999999999999.999999");

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "score",
              Instant.now(),
              null,
              "[\"RANGE\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NUMBER",
              true,
              true,
              false,
              null,
              null,
              null,
              null,
              numberMin,
              numberMax,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(capability.numberMin()).isEqualTo(numberMin);
      assertThat(capability.numberMax()).isEqualTo(numberMax);
    }

    @Test
    @DisplayName("应该处理复杂的正则表达式模式")
    void shouldHandleComplexRegexPattern() {
      // Given: 复杂的正则表达式
      String termPattern = "^[\\p{L}\\p{N}\\s.,!?;:()\\[\\]{}\"'@#$%^&*+=_-]+$";

      // When: 创建 ExprCapability
      ExprCapability capability =
          new ExprCapability(
              1001L,
              2001L,
              "HARVEST",
              "author_name",
              Instant.now(),
              null,
              "[\"TERM\"]",
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              termPattern,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(capability.termPattern()).isEqualTo(termPattern);
    }
  }
}
