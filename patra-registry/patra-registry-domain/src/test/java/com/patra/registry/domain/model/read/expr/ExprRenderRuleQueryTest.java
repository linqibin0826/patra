package com.patra.registry.domain.model.read.expr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link ExprRenderRuleQuery} 的单元测试。
/// 
/// @author linqibin
/// @since 0.1.0
@DisplayName("ExprRenderRuleQuery 单元测试")
class ExprRenderRuleQueryTest {

  @Nested
  @DisplayName("构造器验证规则")
  class ConstructorValidation {

    @Test
    @DisplayName("应该拒绝 null 的 provenanceId")
    void shouldRejectNullProvenanceId() {
      // Given: provenanceId 为 null
      Long nullProvenanceId = null;

      // When & Then: 构造时应该抛出异常
      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      nullProvenanceId,
                      "QUERY",
                      "author",
                      "EQ",
                      "EXACT",
                      false,
                      "STRING",
                      "PARAM",
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该拒绝零或负数的 provenanceId")
    void shouldRejectZeroOrNegativeProvenanceId() {
      // Given: provenanceId 为零或负数
      Long zeroProvenanceId = 0L;
      Long negativeProvenanceId = -1L;

      // When & Then: 构造时应该抛出异常
      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      zeroProvenanceId,
                      "QUERY",
                      "author",
                      "EQ",
                      "EXACT",
                      false,
                      "STRING",
                      "PARAM",
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id");

      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      negativeProvenanceId,
                      "QUERY",
                      "author",
                      "EQ",
                      "EXACT",
                      false,
                      "STRING",
                      "PARAM",
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id");
    }

    @Test
    @DisplayName("应该拒绝 null 或空白的 fieldKey")
    void shouldRejectNullOrBlankFieldKey() {
      // Given: fieldKey 为 null 或空白
      String nullFieldKey = null;
      String blankFieldKey = "   ";

      // When & Then: 构造时应该抛出异常
      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      1L,
                      "QUERY",
                      nullFieldKey,
                      "EQ",
                      "EXACT",
                      false,
                      "STRING",
                      "PARAM",
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Field key")
          .hasMessageContaining("不能为空白");

      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      1L,
                      "QUERY",
                      blankFieldKey,
                      "EQ",
                      "EXACT",
                      false,
                      "STRING",
                      "PARAM",
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Field key");
    }

    @Test
    @DisplayName("应该拒绝 null 或空白的 opCode")
    void shouldRejectNullOrBlankOpCode() {
      // Given: opCode 为 null 或空白
      String nullOpCode = null;
      String blankOpCode = "   ";

      // When & Then: 构造时应该抛出异常
      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      1L,
                      "QUERY",
                      "author",
                      nullOpCode,
                      "EXACT",
                      false,
                      "STRING",
                      "PARAM",
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Operation code")
          .hasMessageContaining("不能为空白");

      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      1L,
                      "QUERY",
                      "author",
                      blankOpCode,
                      "EXACT",
                      false,
                      "STRING",
                      "PARAM",
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Operation code");
    }

    @Test
    @DisplayName("应该拒绝 null 或空白的 emitTypeCode")
    void shouldRejectNullOrBlankEmitTypeCode() {
      // Given: emitTypeCode 为 null 或空白
      String nullEmitTypeCode = null;
      String blankEmitTypeCode = "   ";

      // When & Then: 构造时应该抛出异常
      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      1L,
                      "QUERY",
                      "author",
                      "EQ",
                      "EXACT",
                      false,
                      "STRING",
                      nullEmitTypeCode,
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Emit type code")
          .hasMessageContaining("不能为空白");

      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      1L,
                      "QUERY",
                      "author",
                      "EQ",
                      "EXACT",
                      false,
                      "STRING",
                      blankEmitTypeCode,
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      Instant.now(),
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Emit type code");
    }

    @Test
    @DisplayName("应该拒绝 null 的 effectiveFrom")
    void shouldRejectNullEffectiveFrom() {
      // Given: effectiveFrom 为 null
      Instant nullEffectiveFrom = null;

      // When & Then: 构造时应该抛出异常
      assertThatThrownBy(
              () ->
                  new ExprRenderRuleQuery(
                      1L,
                      "QUERY",
                      "author",
                      "EQ",
                      "EXACT",
                      false,
                      "STRING",
                      "PARAM",
                      "author={value}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      nullEffectiveFrom,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Effective from")
          .hasMessageContaining("不能为 null");
    }
  }

  @Nested
  @DisplayName("字段 trim 逻辑")
  class FieldTrimming {

    @Test
    @DisplayName("应该 trim fieldKey 两端空白")
    void shouldTrimFieldKey() {
      // Given: fieldKey 包含前后空白
      String fieldKeyWithSpaces = "  author  ";
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              fieldKeyWithSpaces,
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: fieldKey 应该被 trim
      assertThat(query.fieldKey()).isEqualTo("author");
    }

    @Test
    @DisplayName("应该 trim opCode 两端空白")
    void shouldTrimOpCode() {
      // Given: opCode 包含前后空白
      String opCodeWithSpaces = "  EQ  ";
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              opCodeWithSpaces,
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: opCode 应该被 trim
      assertThat(query.opCode()).isEqualTo("EQ");
    }

    @Test
    @DisplayName("应该 trim emitTypeCode 两端空白")
    void shouldTrimEmitTypeCode() {
      // Given: emitTypeCode 包含前后空白
      String emitTypeCodeWithSpaces = "  PARAM  ";
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              emitTypeCodeWithSpaces,
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: emitTypeCode 应该被 trim
      assertThat(query.emitTypeCode()).isEqualTo("PARAM");
    }

    @Test
    @DisplayName("应该 trim 可选字段 operationType 的两端空白")
    void shouldTrimOptionalOperationType() {
      // Given: operationType 包含前后空白
      String operationTypeWithSpaces = "  QUERY  ";
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              operationTypeWithSpaces,
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: operationType 应该被 trim
      assertThat(query.operationType()).isEqualTo("QUERY");
    }

    @Test
    @DisplayName("应该 trim 可选字段 matchTypeCode 的两端空白")
    void shouldTrimOptionalMatchTypeCode() {
      // Given: matchTypeCode 包含前后空白
      String matchTypeCodeWithSpaces = "  EXACT  ";
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              matchTypeCodeWithSpaces,
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: matchTypeCode 应该被 trim
      assertThat(query.matchTypeCode()).isEqualTo("EXACT");
    }

    @Test
    @DisplayName("应该 trim 可选字段 valueTypeCode 的两端空白")
    void shouldTrimOptionalValueTypeCode() {
      // Given: valueTypeCode 包含前后空白
      String valueTypeCodeWithSpaces = "  STRING  ";
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              valueTypeCodeWithSpaces,
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: valueTypeCode 应该被 trim
      assertThat(query.valueTypeCode()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("可选字段为 null 时应保持 null")
    void shouldKeepNullForOptionalFields() {
      // Given: 可选字段为 null
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              null,
              "author",
              "EQ",
              null,
              false,
              null,
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: 可选字段应保持 null
      assertThat(query.operationType()).isNull();
      assertThat(query.matchTypeCode()).isNull();
      assertThat(query.valueTypeCode()).isNull();
    }
  }

  @Nested
  @DisplayName("合法对象构造")
  class ValidConstruction {

    @Test
    @DisplayName("应该成功构造包含所有必需字段的对象")
    void shouldConstructWithAllRequiredFields() {
      // Given: 所有必需字段
      Long provenanceId = 1L;
      String fieldKey = "author";
      String opCode = "EQ";
      String emitTypeCode = "PARAM";
      Instant effectiveFrom = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              provenanceId,
              null,
              fieldKey,
              opCode,
              null,
              false,
              null,
              emitTypeCode,
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              effectiveFrom,
              null);

      // Then: 所有字段应正确设置
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.fieldKey()).isEqualTo(fieldKey);
      assertThat(query.opCode()).isEqualTo(opCode);
      assertThat(query.emitTypeCode()).isEqualTo(emitTypeCode);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功构造包含所有字段的对象")
    void shouldConstructWithAllFields() {
      // Given: 所有字段
      Long provenanceId = 1L;
      String operationType = "QUERY";
      String fieldKey = "author";
      String opCode = "EQ";
      String matchTypeCode = "EXACT";
      Boolean negated = true;
      String valueTypeCode = "STRING";
      String emitTypeCode = "PARAM";
      String template = "author={value}";
      String itemTemplate = "{item}";
      String joiner = " AND ";
      boolean wrapGroup = true;
      String paramsJson = "{\"key\":\"value\"}";
      String functionCode = "TRANSFORM";
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              provenanceId,
              operationType,
              fieldKey,
              opCode,
              matchTypeCode,
              negated,
              valueTypeCode,
              emitTypeCode,
              template,
              itemTemplate,
              joiner,
              wrapGroup,
              paramsJson,
              functionCode,
              effectiveFrom,
              effectiveTo);

      // Then: 所有字段应正确设置
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.operationType()).isEqualTo(operationType);
      assertThat(query.fieldKey()).isEqualTo(fieldKey);
      assertThat(query.opCode()).isEqualTo(opCode);
      assertThat(query.matchTypeCode()).isEqualTo(matchTypeCode);
      assertThat(query.negated()).isEqualTo(negated);
      assertThat(query.valueTypeCode()).isEqualTo(valueTypeCode);
      assertThat(query.emitTypeCode()).isEqualTo(emitTypeCode);
      assertThat(query.template()).isEqualTo(template);
      assertThat(query.itemTemplate()).isEqualTo(itemTemplate);
      assertThat(query.joiner()).isEqualTo(joiner);
      assertThat(query.wrapGroup()).isEqualTo(wrapGroup);
      assertThat(query.paramsJson()).isEqualTo(paramsJson);
      assertThat(query.functionCode()).isEqualTo(functionCode);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isEqualTo(effectiveTo);
    }

    @Test
    @DisplayName("应该正确处理 negated 为 null 的情况")
    void shouldHandleNullNegated() {
      // Given: negated 为 null
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              null,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: negated 应为 null
      assertThat(query.negated()).isNull();
    }

    @Test
    @DisplayName("应该正确处理 wrapGroup 的 true 和 false")
    void shouldHandleWrapGroupBoolean() {
      // Given: wrapGroup 分别为 true 和 false
      Instant now = Instant.now();

      // When: 构造两个对象
      var queryWithTrue =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              true,
              null,
              null,
              now,
              null);

      var queryWithFalse =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: wrapGroup 应正确设置
      assertThat(queryWithTrue.wrapGroup()).isTrue();
      assertThat(queryWithFalse.wrapGroup()).isFalse();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemantics {

    @Test
    @DisplayName("相同字段的对象应该相等")
    void shouldBeEqualWithSameFields() {
      // Given: 两个字段完全相同的对象
      Instant now = Instant.now();
      var query1 =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      var query2 =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // When & Then: 应该相等且 hashCode 相同
      assertThat(query1).isEqualTo(query2);
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("不同字段的对象应该不相等")
    void shouldNotBeEqualWithDifferentFields() {
      // Given: 两个字段不同的对象
      Instant now = Instant.now();
      var query1 =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      var query2 =
          new ExprRenderRuleQuery(
              2L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // When & Then: 应该不相等
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("toString 应该包含所有字段")
    void toStringShouldContainAllFields() {
      // Given: 一个包含多个字段的对象
      Instant now = Instant.now();
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // When: 调用 toString
      String result = query.toString();

      // Then: 应该包含关键字段
      assertThat(result)
          .contains("provenanceId=1")
          .contains("operationType=QUERY")
          .contains("fieldKey=author")
          .contains("opCode=EQ")
          .contains("emitTypeCode=PARAM");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditions {

    @Test
    @DisplayName("应该接受最小的正数 provenanceId")
    void shouldAcceptMinimumPositiveProvenanceId() {
      // Given: provenanceId 为 1
      Long minProvenanceId = 1L;
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              minProvenanceId,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: 应该成功构造
      assertThat(query.provenanceId()).isEqualTo(minProvenanceId);
    }

    @Test
    @DisplayName("应该接受非常大的 provenanceId")
    void shouldAcceptVeryLargeProvenanceId() {
      // Given: provenanceId 为 Long.MAX_VALUE
      Long largeProvenanceId = Long.MAX_VALUE;
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              largeProvenanceId,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: 应该成功构造
      assertThat(query.provenanceId()).isEqualTo(largeProvenanceId);
    }

    @Test
    @DisplayName("应该接受仅包含一个字符的 fieldKey")
    void shouldAcceptSingleCharacterFieldKey() {
      // Given: fieldKey 为单字符
      String singleCharFieldKey = "a";
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              singleCharFieldKey,
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: 应该成功构造
      assertThat(query.fieldKey()).isEqualTo(singleCharFieldKey);
    }

    @Test
    @DisplayName("应该接受非常长的字符串字段")
    void shouldAcceptVeryLongStringFields() {
      // Given: 非常长的字符串
      String longString = "a".repeat(1000);
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              longString,
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              longString,
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: 应该成功构造
      assertThat(query.fieldKey()).isEqualTo(longString);
      assertThat(query.template()).isEqualTo(longString);
    }

    @Test
    @DisplayName("应该接受 effectiveFrom 等于 effectiveTo")
    void shouldAcceptEffectiveFromEqualsEffectiveTo() {
      // Given: effectiveFrom 等于 effectiveTo
      Instant sameInstant = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              sameInstant,
              sameInstant);

      // Then: 应该成功构造
      assertThat(query.effectiveFrom()).isEqualTo(sameInstant);
      assertThat(query.effectiveTo()).isEqualTo(sameInstant);
    }

    @Test
    @DisplayName("应该接受 effectiveTo 早于 effectiveFrom（无业务逻辑验证）")
    void shouldAcceptEffectiveToBeforeEffectiveFrom() {
      // Given: effectiveTo 早于 effectiveFrom
      Instant later = Instant.parse("2025-01-01T00:00:00Z");
      Instant earlier = Instant.parse("2024-01-01T00:00:00Z");

      // When: 构造对象（record 不验证业务逻辑）
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              "author={value}",
              null,
              null,
              false,
              null,
              null,
              later,
              earlier);

      // Then: 应该成功构造（业务逻辑验证在其他层处理）
      assertThat(query.effectiveFrom()).isEqualTo(later);
      assertThat(query.effectiveTo()).isEqualTo(earlier);
    }

    @Test
    @DisplayName("应该接受特殊字符的字符串字段")
    void shouldAcceptSpecialCharactersInStringFields() {
      // Given: 包含特殊字符的字符串
      String specialChars = "author=\"{value}\" AND type='article'";
      Instant now = Instant.now();

      // When: 构造对象
      var query =
          new ExprRenderRuleQuery(
              1L,
              "QUERY",
              "author",
              "EQ",
              "EXACT",
              false,
              "STRING",
              "PARAM",
              specialChars,
              null,
              null,
              false,
              null,
              null,
              now,
              null);

      // Then: 应该成功构造
      assertThat(query.template()).isEqualTo(specialChars);
    }
  }
}
