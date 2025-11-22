package com.patra.registry.domain.model.vo.expr;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ExprRenderRule 值对象单元测试。
///
/// 测试策略：
///
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 测试 record 的业务约束验证（正整数 ID、非空白字符串、必需字段等）
///   - 验证字符串字段自动 trim 处理
///   - 测试可选字段的 null 处理
///   - 验证归一化键的必需性验证
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///
/// 覆盖范围：
///
/// - ✅ record 构造函数验证测试
///   - ✅ 正整数 ID 验证（id, provenanceId）
///   - ✅ 非空白字符串验证（fieldKey, opCode, emitTypeCode）
///   - ✅ 必需字段非 null 验证（effectiveFrom）
///   - ✅ 归一化键验证（matchTypeKey, negatedKey, valueTypeKey）
///   - ✅ 字符串 trim 处理测试
///   - ✅ 可选字段处理
///   - ✅ record 的 equals/hashCode/toString 测试
///   - ✅ 不变性保证
///   - ✅ 业务场景测试（不同发射类型、操作符等）
///   - ✅ 边界条件处理
///
/// @author Patra Team
/// @since 0.1.0
@DisplayName("ExprRenderRule 单元测试")
class ExprRenderRuleTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的表达式渲染规则")
    void shouldCreateExprRenderRuleWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      String fieldKey = "publication_date";
      String opCode = "RANGE";
      String matchTypeCode = "PHRASE";
      Boolean negated = false;
      String valueTypeCode = "DATE";
      String emitTypeCode = "QUERY";
      String matchTypeKey = "PHRASE";
      String negatedKey = "F";
      String valueTypeKey = "DATE";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      String template = "{{fieldKey}}:[{{from}} TO {{to}}]";
      String itemTemplate = "{{fieldKey}}:{{q v}}";
      String joiner = " OR "; // 注意：joiner 会被 trim，所以实际存储为 "OR"
      boolean wrapGroup = true;
      String paramsJson = "{\"from\":\"from\",\"to\":\"to\"}";
      String functionCode = "PUBMED_DATETYPE";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              id,
              provenanceId,
              operationType,
              fieldKey,
              opCode,
              matchTypeCode,
              negated,
              valueTypeCode,
              emitTypeCode,
              matchTypeKey,
              negatedKey,
              valueTypeKey,
              effectiveFrom,
              effectiveTo,
              template,
              itemTemplate,
              joiner,
              wrapGroup,
              paramsJson,
              functionCode);

      // Then: 验证所有字段正确赋值
      assertThat(rule).isNotNull();
      assertThat(rule.id()).isEqualTo(id);
      assertThat(rule.provenanceId()).isEqualTo(provenanceId);
      assertThat(rule.operationType()).isEqualTo(operationType);
      assertThat(rule.fieldKey()).isEqualTo(fieldKey);
      assertThat(rule.opCode()).isEqualTo(opCode);
      assertThat(rule.matchTypeCode()).isEqualTo(matchTypeCode);
      assertThat(rule.negated()).isEqualTo(negated);
      assertThat(rule.valueTypeCode()).isEqualTo(valueTypeCode);
      assertThat(rule.emitTypeCode()).isEqualTo(emitTypeCode);
      assertThat(rule.matchTypeKey()).isEqualTo(matchTypeKey);
      assertThat(rule.negatedKey()).isEqualTo(negatedKey);
      assertThat(rule.valueTypeKey()).isEqualTo(valueTypeKey);
      assertThat(rule.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(rule.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(rule.template()).isEqualTo(template);
      assertThat(rule.itemTemplate()).isEqualTo(itemTemplate);
      assertThat(rule.joiner()).isEqualTo("OR"); // trim 后的值
      assertThat(rule.wrapGroup()).isEqualTo(wrapGroup);
      assertThat(rule.paramsJson()).isEqualTo(paramsJson);
      assertThat(rule.functionCode()).isEqualTo(functionCode);
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小配置")
    void shouldCreateMinimalExprRenderRule() {
      // Given: 只有必需字段
      Long id = 1001L;
      Long provenanceId = 2001L;
      String fieldKey = "title";
      String opCode = "TERM";
      String emitTypeCode = "QUERY";
      String matchTypeKey = "ANY";
      String negatedKey = "ANY";
      String valueTypeKey = "ANY";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              id,
              provenanceId,
              null, // operationType
              fieldKey,
              opCode,
              null, // matchTypeCode
              null, // negated
              null, // valueTypeCode
              emitTypeCode,
              matchTypeKey,
              negatedKey,
              valueTypeKey,
              effectiveFrom,
              null, // effectiveTo
              null, // template
              null, // itemTemplate
              null, // joiner
              false, // wrapGroup
              null, // paramsJson
              null); // functionCode

      // Then: 验证必需字段正确赋值，可选字段为 null
      assertThat(rule).isNotNull();
      assertThat(rule.id()).isEqualTo(id);
      assertThat(rule.provenanceId()).isEqualTo(provenanceId);
      assertThat(rule.operationType()).isNull();
      assertThat(rule.fieldKey()).isEqualTo(fieldKey);
      assertThat(rule.opCode()).isEqualTo(opCode);
      assertThat(rule.matchTypeCode()).isNull();
      assertThat(rule.negated()).isNull();
      assertThat(rule.valueTypeCode()).isNull();
      assertThat(rule.emitTypeCode()).isEqualTo(emitTypeCode);
      assertThat(rule.matchTypeKey()).isEqualTo(matchTypeKey);
      assertThat(rule.negatedKey()).isEqualTo(negatedKey);
      assertThat(rule.valueTypeKey()).isEqualTo(valueTypeKey);
      assertThat(rule.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(rule.effectiveTo()).isNull();
      assertThat(rule.template()).isNull();
      assertThat(rule.itemTemplate()).isNull();
      assertThat(rule.joiner()).isNull();
      assertThat(rule.wrapGroup()).isFalse();
      assertThat(rule.paramsJson()).isNull();
      assertThat(rule.functionCode()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 effectiveTo 为 null 的永久有效规则")
    void shouldCreatePermanentRule() {
      // Given: effectiveTo 为 null（表示永久有效）
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              "PHRASE",
              false,
              "STRING",
              "QUERY",
              "PHRASE",
              "F",
              "STRING",
              effectiveFrom,
              effectiveTo,
              "{{fieldKey}}:{{q v}}",
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 effectiveTo 为 null
      assertThat(rule.effectiveTo()).isNull();
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

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      id,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Render rule id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为 0")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given: id 为 0
      Long id = 0L;

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      id,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Render rule id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为负数")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given: id 为负数
      Long id = -1L;

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      id,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Render rule id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的规则")
    void shouldCreateRuleWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              id,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(rule.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的规则")
    void shouldCreateRuleWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              id,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(rule.id()).isEqualTo(Long.MAX_VALUE);
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

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
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

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
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

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
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
    @DisplayName("应该成功创建 provenanceId 为 1 的规则")
    void shouldCreateRuleWithProvenanceIdOne() {
      // Given: provenanceId 为 1
      Long provenanceId = 1L;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              provenanceId,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(rule.provenanceId()).isEqualTo(1L);
    }
  }

  // ========== 必需字段验证测试 ==========

  @Nested
  @DisplayName("必需字段验证")
  class RequiredFieldsValidationTests {

    @Test
    @DisplayName("应该抛出异常当 fieldKey 为 null")
    void shouldThrowExceptionWhenFieldKeyIsNull() {
      // Given: fieldKey 为 null
      String fieldKey = null;

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      fieldKey,
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
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

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      fieldKey,
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
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

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      fieldKey,
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
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
    @DisplayName("应该抛出异常当 opCode 为 null")
    void shouldThrowExceptionWhenOpCodeIsNull() {
      // Given: opCode 为 null
      String opCode = null;

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      opCode,
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Operation code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 opCode 为空字符串")
    void shouldThrowExceptionWhenOpCodeIsEmpty() {
      // Given: opCode 为空字符串
      String opCode = "";

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      opCode,
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Operation code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 emitTypeCode 为 null")
    void shouldThrowExceptionWhenEmitTypeCodeIsNull() {
      // Given: emitTypeCode 为 null
      String emitTypeCode = null;

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      emitTypeCode,
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Emit type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 emitTypeCode 为空字符串")
    void shouldThrowExceptionWhenEmitTypeCodeIsEmpty() {
      // Given: emitTypeCode 为空字符串
      String emitTypeCode = "";

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      emitTypeCode,
                      "ANY",
                      "ANY",
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Emit type code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 effectiveFrom 为 null")
    void shouldThrowExceptionWhenEffectiveFromIsNull() {
      // Given: effectiveFrom 为 null
      Instant effectiveFrom = null;

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      "ANY",
                      effectiveFrom,
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
  }

  // ========== 归一化键验证测试 ==========

  @Nested
  @DisplayName("归一化键验证")
  class NormalizedKeysValidationTests {

    @Test
    @DisplayName("应该抛出异常当 matchTypeKey 为 null")
    void shouldThrowExceptionWhenMatchTypeKeyIsNull() {
      // Given: matchTypeKey 为 null
      String matchTypeKey = null;

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      matchTypeKey,
                      "ANY",
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Match type key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 matchTypeKey 为空字符串")
    void shouldThrowExceptionWhenMatchTypeKeyIsEmpty() {
      // Given: matchTypeKey 为空字符串
      String matchTypeKey = "";

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      matchTypeKey,
                      "ANY",
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Match type key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 negatedKey 为 null")
    void shouldThrowExceptionWhenNegatedKeyIsNull() {
      // Given: negatedKey 为 null
      String negatedKey = null;

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      negatedKey,
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Negated key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 negatedKey 为空字符串")
    void shouldThrowExceptionWhenNegatedKeyIsEmpty() {
      // Given: negatedKey 为空字符串
      String negatedKey = "";

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      negatedKey,
                      "ANY",
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Negated key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 valueTypeKey 为 null")
    void shouldThrowExceptionWhenValueTypeKeyIsNull() {
      // Given: valueTypeKey 为 null
      String valueTypeKey = null;

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      valueTypeKey,
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Value type key")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 valueTypeKey 为空字符串")
    void shouldThrowExceptionWhenValueTypeKeyIsEmpty() {
      // Given: valueTypeKey 为空字符串
      String valueTypeKey = "";

      // When & Then: 创建规则应该失败
      assertThatThrownBy(
              () ->
                  new ExprRenderRule(
                      1001L,
                      2001L,
                      "HARVEST",
                      "title",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "ANY",
                      "ANY",
                      valueTypeKey,
                      Instant.now(),
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Value type key")
          .hasMessageContaining("不能为空白");
    }
  }

  // ========== 字符串 Trim 处理测试 ==========

  @Nested
  @DisplayName("字符串字段 Trim 处理")
  class StringTrimTests {

    @Test
    @DisplayName("应该自动 trim fieldKey 字段")
    void shouldTrimFieldKey() {
      // Given: fieldKey 包含首尾空白
      String fieldKey = "  publication_date  ";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              fieldKey,
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 fieldKey 已被 trim
      assertThat(rule.fieldKey()).isEqualTo("publication_date");
    }

    @Test
    @DisplayName("应该自动 trim opCode 字段")
    void shouldTrimOpCode() {
      // Given: opCode 包含首尾空白
      String opCode = "  RANGE  ";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              opCode,
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 opCode 已被 trim
      assertThat(rule.opCode()).isEqualTo("RANGE");
    }

    @Test
    @DisplayName("应该自动 trim emitTypeCode 字段")
    void shouldTrimEmitTypeCode() {
      // Given: emitTypeCode 包含首尾空白
      String emitTypeCode = "  PARAMS  ";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              emitTypeCode,
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 emitTypeCode 已被 trim
      assertThat(rule.emitTypeCode()).isEqualTo("PARAMS");
    }

    @Test
    @DisplayName("应该自动 trim 归一化键字段")
    void shouldTrimNormalizedKeys() {
      // Given: 归一化键包含首尾空白
      String matchTypeKey = "  PHRASE  ";
      String negatedKey = "  F  ";
      String valueTypeKey = "  DATE  ";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              matchTypeKey,
              negatedKey,
              valueTypeKey,
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证所有归一化键已被 trim
      assertThat(rule.matchTypeKey()).isEqualTo("PHRASE");
      assertThat(rule.negatedKey()).isEqualTo("F");
      assertThat(rule.valueTypeKey()).isEqualTo("DATE");
    }

    @Test
    @DisplayName("应该 trim 所有字符串字段")
    void shouldTrimAllStringFields() {
      // Given: 所有字符串字段包含首尾空白
      String operationType = "  UPDATE  ";
      String fieldKey = "  author  ";
      String opCode = "  IN  ";
      String matchTypeCode = "  EXACT  ";
      String valueTypeCode = "  STRING  ";
      String emitTypeCode = "  QUERY  ";
      String matchTypeKey = "  EXACT  ";
      String negatedKey = "  T  ";
      String valueTypeKey = "  STRING  ";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              operationType,
              fieldKey,
              opCode,
              matchTypeCode,
              true,
              valueTypeCode,
              emitTypeCode,
              matchTypeKey,
              negatedKey,
              valueTypeKey,
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证所有字段都已被 trim
      assertThat(rule.operationType()).isEqualTo("UPDATE");
      assertThat(rule.fieldKey()).isEqualTo("author");
      assertThat(rule.opCode()).isEqualTo("IN");
      assertThat(rule.matchTypeCode()).isEqualTo("EXACT");
      assertThat(rule.valueTypeCode()).isEqualTo("STRING");
      assertThat(rule.emitTypeCode()).isEqualTo("QUERY");
      assertThat(rule.matchTypeKey()).isEqualTo("EXACT");
      assertThat(rule.negatedKey()).isEqualTo("T");
      assertThat(rule.valueTypeKey()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String operationType = "\t\n  BACKFILL  \t\n";
      String fieldKey = " \t title \n ";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              operationType,
              fieldKey,
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证空白字符都被 trim
      assertThat(rule.operationType()).isEqualTo("BACKFILL");
      assertThat(rule.fieldKey()).isEqualTo("title");
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

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              operationType,
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 operationType 为 null
      assertThat(rule.operationType()).isNull();
    }

    @Test
    @DisplayName("matchTypeCode 为 null 时应保持 null")
    void matchTypeCodeCanBeNull() {
      // Given: matchTypeCode 为 null
      String matchTypeCode = null;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "EXISTS",
              matchTypeCode,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 matchTypeCode 为 null
      assertThat(rule.matchTypeCode()).isNull();
    }

    @Test
    @DisplayName("negated 为 null 时应保持 null")
    void negatedCanBeNull() {
      // Given: negated 为 null
      Boolean negated = null;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              negated,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 negated 为 null
      assertThat(rule.negated()).isNull();
    }

    @Test
    @DisplayName("valueTypeCode 为 null 时应保持 null")
    void valueTypeCodeCanBeNull() {
      // Given: valueTypeCode 为 null
      String valueTypeCode = null;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              valueTypeCode,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证 valueTypeCode 为 null
      assertThat(rule.valueTypeCode()).isNull();
    }

    @Test
    @DisplayName("应该处理所有可选字段都为 null 的情况")
    void shouldHandleAllOptionalFieldsBeingNull() {
      // Given: 所有可选字段为 null
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              null, // operationType
              "title",
              "TERM",
              null, // matchTypeCode
              null, // negated
              null, // valueTypeCode
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null, // effectiveTo
              null, // template
              null, // itemTemplate
              null, // joiner
              false,
              null, // paramsJson
              null); // functionCode

      // Then: 验证可选字段都为 null
      assertThat(rule.operationType()).isNull();
      assertThat(rule.matchTypeCode()).isNull();
      assertThat(rule.negated()).isNull();
      assertThat(rule.valueTypeCode()).isNull();
      assertThat(rule.effectiveTo()).isNull();
      assertThat(rule.template()).isNull();
      assertThat(rule.itemTemplate()).isNull();
      assertThat(rule.joiner()).isNull();
      assertThat(rule.paramsJson()).isNull();
      assertThat(rule.functionCode()).isNull();
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确实现 equals 方法（相同值对象相等）")
    void shouldImplementEqualsCorrectly() {
      // Given: 两个相同值的规则
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      String fieldKey = "title";
      String opCode = "TERM";
      String emitTypeCode = "QUERY";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      ExprRenderRule rule1 =
          new ExprRenderRule(
              id,
              provenanceId,
              operationType,
              fieldKey,
              opCode,
              "PHRASE",
              false,
              "STRING",
              emitTypeCode,
              "PHRASE",
              "F",
              "STRING",
              effectiveFrom,
              null,
              "{{fieldKey}}:{{q v}}",
              null,
              null,
              false,
              null,
              null);

      ExprRenderRule rule2 =
          new ExprRenderRule(
              id,
              provenanceId,
              operationType,
              fieldKey,
              opCode,
              "PHRASE",
              false,
              "STRING",
              emitTypeCode,
              "PHRASE",
              "F",
              "STRING",
              effectiveFrom,
              null,
              "{{fieldKey}}:{{q v}}",
              null,
              null,
              false,
              null,
              null);

      // When & Then: 应该相等
      assertThat(rule1).isEqualTo(rule2);
      assertThat(rule1).hasSameHashCodeAs(rule2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的规则
      ExprRenderRule rule1 =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      ExprRenderRule rule2 =
          new ExprRenderRule(
              1002L,
              2002L,
              "UPDATE",
              "author",
              "IN",
              null,
              null,
              null,
              "PARAMS",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              true,
              null,
              null);

      // When & Then: 不应该相等
      assertThat(rule1).isNotEqualTo(rule2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的规则
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      ExprRenderRule rule1 =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              effectiveFrom,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      ExprRenderRule rule2 =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              effectiveFrom,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When & Then: hashCode 应该相等
      assertThat(rule1.hashCode()).isEqualTo(rule2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建规则
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "publication_date",
              "RANGE",
              null,
              null,
              "DATE",
              "QUERY",
              "ANY",
              "ANY",
              "DATE",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              "{{fieldKey}}:[{{from}} TO {{to}}]",
              null,
              null,
              false,
              null,
              "PUBMED_DATETYPE");

      // When: 调用 toString
      String toString = rule.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("ExprRenderRule");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("2001");
      assertThat(toString).contains("HARVEST");
      assertThat(toString).contains("publication_date");
      assertThat(toString).contains("RANGE");
      assertThat(toString).contains("QUERY");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建规则
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When & Then: 对象应该等于自身
      assertThat(rule).isEqualTo(rule);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建规则
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When & Then: 与 null 比较应该返回 false
      assertThat(rule).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建规则
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(rule).isNotEqualTo("Not an ExprRenderRule");
      assertThat(rule).isNotEqualTo(1001L);
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 字段应该是不可变的")
    void recordFieldsShouldBeImmutable() {
      // Given: 创建规则
      Long originalId = 1001L;
      Long originalProvenanceId = 2001L;
      String originalFieldKey = "title";
      String originalOpCode = "TERM";
      String originalEmitTypeCode = "QUERY";
      Instant originalEffectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      ExprRenderRule rule =
          new ExprRenderRule(
              originalId,
              originalProvenanceId,
              "HARVEST",
              originalFieldKey,
              originalOpCode,
              null,
              null,
              null,
              originalEmitTypeCode,
              "ANY",
              "ANY",
              "ANY",
              originalEffectiveFrom,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When: 获取字段值
      Long retrievedId = rule.id();
      Long retrievedProvenanceId = rule.provenanceId();
      String retrievedFieldKey = rule.fieldKey();
      String retrievedOpCode = rule.opCode();
      String retrievedEmitTypeCode = rule.emitTypeCode();
      Instant retrievedEffectiveFrom = rule.effectiveFrom();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedProvenanceId).isEqualTo(originalProvenanceId);
      assertThat(retrievedFieldKey).isEqualTo(originalFieldKey);
      assertThat(retrievedOpCode).isEqualTo(originalOpCode);
      assertThat(retrievedEmitTypeCode).isEqualTo(originalEmitTypeCode);
      assertThat(retrievedEffectiveFrom).isEqualTo(originalEffectiveFrom);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建规则
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When: 多次获取字段值
      String fieldKey1 = rule.fieldKey();
      String fieldKey2 = rule.fieldKey();
      String opCode1 = rule.opCode();
      String opCode2 = rule.opCode();

      // Then: 字段值应该保持一致
      assertThat(fieldKey1).isEqualTo(fieldKey2);
      assertThat(opCode1).isEqualTo(opCode2);
      assertThat(fieldKey1).isSameAs(fieldKey2);
      assertThat(opCode1).isSameAs(opCode2);
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenariosTests {

    @Test
    @DisplayName("应该成功创建 TERM 操作的 QUERY 发射类型规则")
    void shouldCreateTermQueryRule() {
      // Given: TERM 操作，QUERY 发射类型
      String opCode = "TERM";
      String emitTypeCode = "QUERY";
      String template = "{{fieldKey}}:{{q v}}";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              opCode,
              "PHRASE",
              false,
              "STRING",
              emitTypeCode,
              "PHRASE",
              "F",
              "STRING",
              Instant.now(),
              null,
              template,
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(rule.opCode()).isEqualTo("TERM");
      assertThat(rule.emitTypeCode()).isEqualTo("QUERY");
      assertThat(rule.template()).isEqualTo(template);
    }

    @Test
    @DisplayName("应该成功创建 RANGE 操作的规则")
    void shouldCreateRangeRule() {
      // Given: RANGE 操作
      String opCode = "RANGE";
      String valueTypeCode = "DATE";
      String template = "{{fieldKey}}:[{{from}} TO {{to}}]";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "publication_date",
              opCode,
              null,
              null,
              valueTypeCode,
              "QUERY",
              "ANY",
              "ANY",
              "DATE",
              Instant.now(),
              null,
              template,
              null,
              null,
              false,
              null,
              "PUBMED_DATETYPE");

      // Then: 验证成功创建
      assertThat(rule.opCode()).isEqualTo("RANGE");
      assertThat(rule.valueTypeCode()).isEqualTo("DATE");
      assertThat(rule.template()).isEqualTo(template);
      assertThat(rule.functionCode()).isEqualTo("PUBMED_DATETYPE");
    }

    @Test
    @DisplayName("应该成功创建 IN 操作的规则")
    void shouldCreateInRule() {
      // Given: IN 操作
      String opCode = "IN";
      String itemTemplate = "{{fieldKey}}:{{q v}}";
      String joiner = " OR ";
      boolean wrapGroup = true;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "journal",
              opCode,
              null,
              null,
              "STRING",
              "QUERY",
              "ANY",
              "ANY",
              "STRING",
              Instant.now(),
              null,
              null,
              itemTemplate,
              joiner,
              wrapGroup,
              null,
              null);

      // Then: 验证成功创建
      assertThat(rule.opCode()).isEqualTo("IN");
      assertThat(rule.itemTemplate()).isEqualTo(itemTemplate);
      assertThat(rule.joiner()).isEqualTo("OR"); // trim 后的值
      assertThat(rule.wrapGroup()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建 PARAMS 发射类型规则")
    void shouldCreateParamsEmitTypeRule() {
      // Given: PARAMS 发射类型
      String emitTypeCode = "PARAMS";
      String paramsJson = "{\"from\":\"from\",\"to\":\"to\"}";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "publication_date",
              "RANGE",
              null,
              null,
              "DATE",
              emitTypeCode,
              "ANY",
              "ANY",
              "DATE",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              paramsJson,
              null);

      // Then: 验证成功创建
      assertThat(rule.emitTypeCode()).isEqualTo("PARAMS");
      assertThat(rule.paramsJson()).isEqualTo(paramsJson);
    }

    @Test
    @DisplayName("应该成功创建带否定标志的规则")
    void shouldCreateNegatedRule() {
      // Given: negated 为 true
      Boolean negated = true;
      String negatedKey = "T";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              negated,
              null,
              "QUERY",
              "ANY",
              negatedKey,
              "ANY",
              Instant.now(),
              null,
              "NOT {{fieldKey}}:{{q v}}",
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(rule.negated()).isTrue();
      assertThat(rule.negatedKey()).isEqualTo("T");
    }

    @Test
    @DisplayName("应该成功创建 EXISTS 操作的规则")
    void shouldCreateExistsRule() {
      // Given: EXISTS 操作
      String opCode = "EXISTS";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "doi",
              opCode,
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              "{{fieldKey}}:[* TO *]",
              null,
              null,
              false,
              null,
              null);

      // Then: 验证成功创建
      assertThat(rule.opCode()).isEqualTo("EXISTS");
    }

    @Test
    @DisplayName("应该成功创建完整的表达式渲染规则")
    void shouldCreateCompleteExprRenderRule() {
      // Given: 完整的表达式渲染规则
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "publication_date",
              "RANGE",
              null,
              false,
              "DATE",
              "QUERY",
              "ANY",
              "F",
              "DATE",
              Instant.parse("2025-01-01T00:00:00Z"),
              Instant.parse("2025-12-31T23:59:59Z"),
              "{{fieldKey}}:[{{from}} TO {{to}}]",
              null,
              null,
              false,
              null,
              "PUBMED_DATETYPE");

      // Then: 验证所有字段
      assertThat(rule.id()).isEqualTo(1001L);
      assertThat(rule.provenanceId()).isEqualTo(2001L);
      assertThat(rule.operationType()).isEqualTo("HARVEST");
      assertThat(rule.fieldKey()).isEqualTo("publication_date");
      assertThat(rule.opCode()).isEqualTo("RANGE");
      assertThat(rule.negated()).isFalse();
      assertThat(rule.valueTypeCode()).isEqualTo("DATE");
      assertThat(rule.emitTypeCode()).isEqualTo("QUERY");
      assertThat(rule.matchTypeKey()).isEqualTo("ANY");
      assertThat(rule.negatedKey()).isEqualTo("F");
      assertThat(rule.valueTypeKey()).isEqualTo("DATE");
      assertThat(rule.template()).isEqualTo("{{fieldKey}}:[{{from}} TO {{to}}]");
      assertThat(rule.functionCode()).isEqualTo("PUBMED_DATETYPE");
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

      ExprRenderRule rule1 =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              effectiveFrom,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      ExprRenderRule rule2 =
          new ExprRenderRule(
              1001L,
              2001L,
              "  HARVEST  ",
              "  title  ",
              "  TERM  ",
              null,
              null,
              null,
              "  QUERY  ",
              "  ANY  ",
              "  ANY  ",
              "  ANY  ",
              effectiveFrom,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // When & Then: trim 后应该相等
      assertThat(rule1).isEqualTo(rule2);
    }

    @Test
    @DisplayName("应该处理极短字符串")
    void shouldHandleMinimalStrings() {
      // Given: 极短字符串
      String fieldKey = "a";
      String opCode = "T";
      String emitTypeCode = "Q";

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "H",
              fieldKey,
              opCode,
              null,
              null,
              null,
              emitTypeCode,
              "A",
              "A",
              "A",
              Instant.now(),
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(rule.fieldKey()).isEqualTo("a");
      assertThat(rule.opCode()).isEqualTo("T");
      assertThat(rule.emitTypeCode()).isEqualTo("Q");
    }

    @Test
    @DisplayName("应该处理极长字符串")
    void shouldHandleLongStrings() {
      // Given: 极长字符串
      String longTemplate = "x".repeat(1000);

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              longTemplate,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(rule.template()).hasSize(1000);
    }

    @Test
    @DisplayName("应该处理 wrapGroup 为 true 的情况")
    void shouldHandleWrapGroupTrue() {
      // Given: wrapGroup 为 true
      boolean wrapGroup = true;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "journal",
              "IN",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              "{{fieldKey}}:{{q v}}",
              " OR ",
              wrapGroup,
              null,
              null);

      // Then: 应该成功创建
      assertThat(rule.wrapGroup()).isTrue();
    }

    @Test
    @DisplayName("应该处理 wrapGroup 为 false 的情况")
    void shouldHandleWrapGroupFalse() {
      // Given: wrapGroup 为 false
      boolean wrapGroup = false;

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "journal",
              "IN",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              Instant.now(),
              null,
              null,
              "{{fieldKey}}:{{q v}}",
              " OR ",
              wrapGroup,
              null,
              null);

      // Then: 应该成功创建
      assertThat(rule.wrapGroup()).isFalse();
    }

    @Test
    @DisplayName("应该处理过去时间的 effectiveFrom")
    void shouldHandlePastEffectiveFrom() {
      // Given: effectiveFrom 为过去时间
      Instant effectiveFrom = Instant.parse("2020-01-01T00:00:00Z");

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              effectiveFrom,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(rule.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该处理未来时间的 effectiveFrom")
    void shouldHandleFutureEffectiveFrom() {
      // Given: effectiveFrom 为未来时间
      Instant effectiveFrom = Instant.parse("2030-01-01T00:00:00Z");

      // When: 创建 ExprRenderRule
      ExprRenderRule rule =
          new ExprRenderRule(
              1001L,
              2001L,
              "HARVEST",
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              effectiveFrom,
              null,
              null,
              null,
              null,
              false,
              null,
              null);

      // Then: 应该成功创建
      assertThat(rule.effectiveFrom()).isEqualTo(effectiveFrom);
    }
  }
}
