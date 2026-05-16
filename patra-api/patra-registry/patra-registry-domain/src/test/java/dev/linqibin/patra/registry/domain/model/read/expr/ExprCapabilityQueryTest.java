package dev.linqibin.patra.registry.domain.model.read.expr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link ExprCapabilityQuery} 单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ExprCapabilityQuery 表达式能力查询视图")
class ExprCapabilityQueryTest {

  @Nested
  @DisplayName("构造器验证测试")
  class ConstructorValidationTest {

    @Test
    @DisplayName("使用有效参数创建实例应成功")
    void shouldCreateInstanceWithValidParameters() {
      // Given
      Long provenanceId = 1L;
      String fieldKey = "author.name";
      String rangeKindCode = "DATE";
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              provenanceId,
              "SEARCH",
              fieldKey,
              "[\"eq\", \"ne\"]",
              "[\"not_eq\"]",
              true,
              "[\"exact\", \"prefix\"]",
              true,
              false,
              1,
              100,
              "^[a-zA-Z]+$",
              50,
              true,
              rangeKindCode,
              true,
              true,
              false,
              LocalDate.of(2020, 1, 1),
              LocalDate.of(2030, 12, 31),
              Instant.parse("2020-01-01T00:00:00Z"),
              Instant.parse("2030-12-31T23:59:59Z"),
              new BigDecimal("0.01"),
              new BigDecimal("999.99"),
              true,
              "[\"keyword\", \"phrase\"]",
              "[a-z]+",
              effectiveFrom,
              Instant.parse("2025-12-31T23:59:59Z"));

      // Then
      assertThat(query).isNotNull();
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.fieldKey()).isEqualTo(fieldKey);
      assertThat(query.rangeKindCode()).isEqualTo(rangeKindCode);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("provenanceId 为 null 应抛出异常")
    void shouldThrowExceptionWhenProvenanceIdIsNull() {
      // Given
      Long provenanceId = null;

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(provenanceId, "field.key", "DATE", Instant.now()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id");
    }

    @Test
    @DisplayName("provenanceId 为负数应抛出异常")
    void shouldThrowExceptionWhenProvenanceIdIsNegative() {
      // Given
      Long provenanceId = -1L;

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(provenanceId, "field.key", "DATE", Instant.now()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id");
    }

    @Test
    @DisplayName("provenanceId 为零应抛出异常")
    void shouldThrowExceptionWhenProvenanceIdIsZero() {
      // Given
      Long provenanceId = 0L;

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(provenanceId, "field.key", "DATE", Instant.now()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id");
    }

    @Test
    @DisplayName("fieldKey 为 null 应抛出异常")
    void shouldThrowExceptionWhenFieldKeyIsNull() {
      // Given
      String fieldKey = null;

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(1L, fieldKey, "DATE", Instant.now()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Field key");
    }

    @Test
    @DisplayName("fieldKey 为空字符串应抛出异常")
    void shouldThrowExceptionWhenFieldKeyIsEmpty() {
      // Given
      String fieldKey = "";

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(1L, fieldKey, "DATE", Instant.now()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Field key");
    }

    @Test
    @DisplayName("fieldKey 仅包含空格应抛出异常")
    void shouldThrowExceptionWhenFieldKeyIsBlank() {
      // Given
      String fieldKey = "   ";

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(1L, fieldKey, "DATE", Instant.now()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Field key");
    }

    @Test
    @DisplayName("rangeKindCode 为 null 应抛出异常")
    void shouldThrowExceptionWhenRangeKindCodeIsNull() {
      // Given
      String rangeKindCode = null;

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(1L, "field.key", rangeKindCode, Instant.now()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Range kind code");
    }

    @Test
    @DisplayName("rangeKindCode 为空字符串应抛出异常")
    void shouldThrowExceptionWhenRangeKindCodeIsEmpty() {
      // Given
      String rangeKindCode = "";

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(1L, "field.key", rangeKindCode, Instant.now()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Range kind code");
    }

    @Test
    @DisplayName("rangeKindCode 仅包含空格应抛出异常")
    void shouldThrowExceptionWhenRangeKindCodeIsBlank() {
      // Given
      String rangeKindCode = "  ";

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(1L, "field.key", rangeKindCode, Instant.now()))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Range kind code");
    }

    @Test
    @DisplayName("effectiveFrom 为 null 应抛出异常")
    void shouldThrowExceptionWhenEffectiveFromIsNull() {
      // Given
      Instant effectiveFrom = null;

      // When & Then
      assertThatThrownBy(() -> createMinimalQuery(1L, "field.key", "DATE", effectiveFrom))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Effective from");
    }
  }

  @Nested
  @DisplayName("字段 Trim 处理测试")
  class FieldTrimTest {

    @Test
    @DisplayName("fieldKey 应被自动 trim")
    void shouldTrimFieldKey() {
      // Given
      String fieldKeyWithSpaces = "  author.name  ";

      // When
      ExprCapabilityQuery query = createMinimalQuery(1L, fieldKeyWithSpaces, "DATE", Instant.now());

      // Then
      assertThat(query.fieldKey()).isEqualTo("author.name");
    }

    @Test
    @DisplayName("rangeKindCode 应被自动 trim")
    void shouldTrimRangeKindCode() {
      // Given
      String rangeKindCodeWithSpaces = "  NUMBER  ";

      // When
      ExprCapabilityQuery query =
          createMinimalQuery(1L, "field.key", rangeKindCodeWithSpaces, Instant.now());

      // Then
      assertThat(query.rangeKindCode()).isEqualTo("NUMBER");
    }

    @Test
    @DisplayName("operationType 为非 null 时应被 trim")
    void shouldTrimOperationTypeWhenNotNull() {
      // Given
      String operationTypeWithSpaces = "  SEARCH  ";

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              operationTypeWithSpaces,
              "field.key",
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
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.operationType()).isEqualTo("SEARCH");
    }

    @Test
    @DisplayName("operationType 为 null 时应保持 null")
    void shouldKeepOperationTypeNullWhenNull() {
      // Given
      String operationType = null;

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              operationType,
              "field.key",
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
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.operationType()).isNull();
    }

    @Test
    @DisplayName("termPattern 为非 null 时应被 trim")
    void shouldTrimTermPatternWhenNotNull() {
      // Given
      String termPatternWithSpaces = "  ^[a-z]+$  ";

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              termPatternWithSpaces,
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
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.termPattern()).isEqualTo("^[a-z]+$");
    }

    @Test
    @DisplayName("termPattern 为 null 时应保持 null")
    void shouldKeepTermPatternNullWhenNull() {
      // Given
      String termPattern = null;

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
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
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.termPattern()).isNull();
    }

    @Test
    @DisplayName("tokenValuePattern 为非 null 时应被 trim")
    void shouldTrimTokenValuePatternWhenNotNull() {
      // Given
      String tokenValuePatternWithSpaces = "  [a-z]+  ";

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
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
              tokenValuePatternWithSpaces,
              Instant.now(),
              null);

      // Then
      assertThat(query.tokenValuePattern()).isEqualTo("[a-z]+");
    }

    @Test
    @DisplayName("tokenValuePattern 为 null 时应保持 null")
    void shouldKeepTokenValuePatternNullWhenNull() {
      // Given
      String tokenValuePattern = null;

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
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
              tokenValuePattern,
              Instant.now(),
              null);

      // Then
      assertThat(query.tokenValuePattern()).isNull();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTest {

    @Test
    @DisplayName("相同值的实例应相等")
    void shouldBeEqualForSameValues() {
      // Given
      Instant now = Instant.now();
      ExprCapabilityQuery query1 = createMinimalQuery(1L, "field.key", "DATE", now);
      ExprCapabilityQuery query2 = createMinimalQuery(1L, "field.key", "DATE", now);

      // When & Then
      assertThat(query1).isEqualTo(query2);
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("不同 provenanceId 的实例应不相等")
    void shouldNotBeEqualForDifferentProvenanceId() {
      // Given
      Instant now = Instant.now();
      ExprCapabilityQuery query1 = createMinimalQuery(1L, "field.key", "DATE", now);
      ExprCapabilityQuery query2 = createMinimalQuery(2L, "field.key", "DATE", now);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("不同 fieldKey 的实例应不相等")
    void shouldNotBeEqualForDifferentFieldKey() {
      // Given
      Instant now = Instant.now();
      ExprCapabilityQuery query1 = createMinimalQuery(1L, "field.key1", "DATE", now);
      ExprCapabilityQuery query2 = createMinimalQuery(1L, "field.key2", "DATE", now);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("不同 rangeKindCode 的实例应不相等")
    void shouldNotBeEqualForDifferentRangeKindCode() {
      // Given
      Instant now = Instant.now();
      ExprCapabilityQuery query1 = createMinimalQuery(1L, "field.key", "DATE", now);
      ExprCapabilityQuery query2 = createMinimalQuery(1L, "field.key", "NUMBER", now);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("不同 effectiveFrom 的实例应不相等")
    void shouldNotBeEqualForDifferentEffectiveFrom() {
      // Given
      ExprCapabilityQuery query1 =
          createMinimalQuery(1L, "field.key", "DATE", Instant.parse("2024-01-01T00:00:00Z"));
      ExprCapabilityQuery query2 =
          createMinimalQuery(1L, "field.key", "DATE", Instant.parse("2024-01-02T00:00:00Z"));

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("toString 应包含所有字段值")
    void shouldIncludeAllFieldsInToString() {
      // Given
      ExprCapabilityQuery query =
          createMinimalQuery(1L, "author.name", "DATE", Instant.parse("2024-01-01T00:00:00Z"));

      // When
      String result = query.toString();

      // Then
      assertThat(result)
          .contains("provenanceId=1")
          .contains("fieldKey=author.name")
          .contains("rangeKindCode=DATE")
          .contains("effectiveFrom=2024-01-01T00:00:00Z");
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTest {

    @Test
    @DisplayName("provenanceId 为 Long.MAX_VALUE 应成功")
    void shouldAcceptMaxLongAsProvenanceId() {
      // Given
      Long maxProvenanceId = Long.MAX_VALUE;

      // When
      ExprCapabilityQuery query =
          createMinimalQuery(maxProvenanceId, "field.key", "DATE", Instant.now());

      // Then
      assertThat(query.provenanceId()).isEqualTo(maxProvenanceId);
    }

    @Test
    @DisplayName("effectiveFrom 为 Instant.MIN 应成功")
    void shouldAcceptMinInstantAsEffectiveFrom() {
      // Given
      Instant minInstant = Instant.MIN;

      // When
      ExprCapabilityQuery query = createMinimalQuery(1L, "field.key", "DATE", minInstant);

      // Then
      assertThat(query.effectiveFrom()).isEqualTo(minInstant);
    }

    @Test
    @DisplayName("effectiveFrom 为 Instant.MAX 应成功")
    void shouldAcceptMaxInstantAsEffectiveFrom() {
      // Given
      Instant maxInstant = Instant.MAX;

      // When
      ExprCapabilityQuery query = createMinimalQuery(1L, "field.key", "DATE", maxInstant);

      // Then
      assertThat(query.effectiveFrom()).isEqualTo(maxInstant);
    }

    @Test
    @DisplayName("effectiveTo 为 null 应成功")
    void shouldAcceptNullEffectiveTo() {
      // Given
      Instant effectiveTo = null;

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
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
              null,
              Instant.now(),
              effectiveTo);

      // Then
      assertThat(query.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("termMinLength 为负数应成功（无验证）")
    void shouldAcceptNegativeTermMinLength() {
      // Given
      int termMinLength = -1;

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
              null,
              null,
              false,
              null,
              false,
              false,
              termMinLength,
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
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.termMinLength()).isEqualTo(termMinLength);
    }

    @Test
    @DisplayName("termMaxLength 为 Integer.MAX_VALUE 应成功")
    void shouldAcceptMaxIntegerAsTermMaxLength() {
      // Given
      int termMaxLength = Integer.MAX_VALUE;

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              termMaxLength,
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
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.termMaxLength()).isEqualTo(termMaxLength);
    }

    @Test
    @DisplayName("inMaxSize 为零应成功")
    void shouldAcceptZeroAsInMaxSize() {
      // Given
      int inMaxSize = 0;

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
              null,
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
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.inMaxSize()).isZero();
    }

    @Test
    @DisplayName("dateMin 为 null 应成功")
    void shouldAcceptNullDateMin() {
      // Given
      LocalDate dateMin = null;

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
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
              "DATE",
              false,
              false,
              false,
              dateMin,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.dateMin()).isNull();
    }

    @Test
    @DisplayName("numberMin 为 BigDecimal.ZERO 应成功")
    void shouldAcceptZeroAsBigDecimalNumberMin() {
      // Given
      BigDecimal numberMin = BigDecimal.ZERO;

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
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
              "DATE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              numberMin,
              null,
              false,
              null,
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.numberMin()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("所有布尔标志为 true 应成功")
    void shouldAcceptAllBooleanFlagsAsTrue() {
      // Given & When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
              null,
              null,
              true, // supportsNot
              null,
              true, // termCaseSensitiveAllowed
              true, // termAllowBlank
              0,
              0,
              null,
              0,
              true, // inCaseSensitiveAllowed
              "DATE",
              true, // rangeAllowOpenStart
              true, // rangeAllowOpenEnd
              true, // rangeAllowClosedAtInfinity
              null,
              null,
              null,
              null,
              null,
              null,
              true, // existsSupported
              null,
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.supportsNot()).isTrue();
      assertThat(query.termCaseSensitiveAllowed()).isTrue();
      assertThat(query.termAllowBlank()).isTrue();
      assertThat(query.inCaseSensitiveAllowed()).isTrue();
      assertThat(query.rangeAllowOpenStart()).isTrue();
      assertThat(query.rangeAllowOpenEnd()).isTrue();
      assertThat(query.rangeAllowClosedAtInfinity()).isTrue();
      assertThat(query.existsSupported()).isTrue();
    }

    @Test
    @DisplayName("所有 JSON 字符串字段为 null 应成功")
    void shouldAcceptAllJsonFieldsAsNull() {
      // Given & When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              null,
              "field.key",
              null, // opsJson
              null, // negatableOpsJson
              false,
              null, // termMatchesJson
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
              null, // tokenKindsJson
              null,
              Instant.now(),
              null);

      // Then
      assertThat(query.opsJson()).isNull();
      assertThat(query.negatableOpsJson()).isNull();
      assertThat(query.termMatchesJson()).isNull();
      assertThat(query.tokenKindsJson()).isNull();
    }
  }

  @Nested
  @DisplayName("完整数据填充测试")
  class CompleteDataTest {

    @Test
    @DisplayName("填充所有字段应成功")
    void shouldAcceptAllFieldsPopulated() {
      // Given
      Long provenanceId = 100L;
      String operationType = "ADVANCED_SEARCH";
      String fieldKey = "publication.date";
      String opsJson = "[\"eq\", \"ne\", \"gt\", \"lt\"]";
      String negatableOpsJson = "[\"not_eq\"]";
      boolean supportsNot = true;
      String termMatchesJson = "[\"exact\", \"prefix\", \"fuzzy\"]";
      boolean termCaseSensitiveAllowed = true;
      boolean termAllowBlank = false;
      int termMinLength = 3;
      int termMaxLength = 200;
      String termPattern = "^[a-zA-Z0-9]+$";
      int inMaxSize = 100;
      boolean inCaseSensitiveAllowed = false;
      String rangeKindCode = "DATE";
      boolean rangeAllowOpenStart = true;
      boolean rangeAllowOpenEnd = true;
      boolean rangeAllowClosedAtInfinity = false;
      LocalDate dateMin = LocalDate.of(1900, 1, 1);
      LocalDate dateMax = LocalDate.of(2100, 12, 31);
      Instant datetimeMin = Instant.parse("1900-01-01T00:00:00Z");
      Instant datetimeMax = Instant.parse("2100-12-31T23:59:59Z");
      BigDecimal numberMin = new BigDecimal("-999999.99");
      BigDecimal numberMax = new BigDecimal("999999.99");
      boolean existsSupported = true;
      String tokenKindsJson = "[\"keyword\", \"phrase\", \"wildcard\"]";
      String tokenValuePattern = "[a-z0-9]+";
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");

      // When
      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              provenanceId,
              operationType,
              fieldKey,
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
              tokenValuePattern,
              effectiveFrom,
              effectiveTo);

      // Then
      assertThat(query.provenanceId()).isEqualTo(provenanceId);
      assertThat(query.operationType()).isEqualTo(operationType);
      assertThat(query.fieldKey()).isEqualTo(fieldKey);
      assertThat(query.opsJson()).isEqualTo(opsJson);
      assertThat(query.negatableOpsJson()).isEqualTo(negatableOpsJson);
      assertThat(query.supportsNot()).isEqualTo(supportsNot);
      assertThat(query.termMatchesJson()).isEqualTo(termMatchesJson);
      assertThat(query.termCaseSensitiveAllowed()).isEqualTo(termCaseSensitiveAllowed);
      assertThat(query.termAllowBlank()).isEqualTo(termAllowBlank);
      assertThat(query.termMinLength()).isEqualTo(termMinLength);
      assertThat(query.termMaxLength()).isEqualTo(termMaxLength);
      assertThat(query.termPattern()).isEqualTo(termPattern);
      assertThat(query.inMaxSize()).isEqualTo(inMaxSize);
      assertThat(query.inCaseSensitiveAllowed()).isEqualTo(inCaseSensitiveAllowed);
      assertThat(query.rangeKindCode()).isEqualTo(rangeKindCode);
      assertThat(query.rangeAllowOpenStart()).isEqualTo(rangeAllowOpenStart);
      assertThat(query.rangeAllowOpenEnd()).isEqualTo(rangeAllowOpenEnd);
      assertThat(query.rangeAllowClosedAtInfinity()).isEqualTo(rangeAllowClosedAtInfinity);
      assertThat(query.dateMin()).isEqualTo(dateMin);
      assertThat(query.dateMax()).isEqualTo(dateMax);
      assertThat(query.datetimeMin()).isEqualTo(datetimeMin);
      assertThat(query.datetimeMax()).isEqualTo(datetimeMax);
      assertThat(query.numberMin()).isEqualByComparingTo(numberMin);
      assertThat(query.numberMax()).isEqualByComparingTo(numberMax);
      assertThat(query.existsSupported()).isEqualTo(existsSupported);
      assertThat(query.tokenKindsJson()).isEqualTo(tokenKindsJson);
      assertThat(query.tokenValuePattern()).isEqualTo(tokenValuePattern);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isEqualTo(effectiveTo);
    }
  }

  // ==================== 辅助方法 ====================

  /// 创建最小化的查询对象（仅包含必需字段）。
  private ExprCapabilityQuery createMinimalQuery(
      Long provenanceId, String fieldKey, String rangeKindCode, Instant effectiveFrom) {
    return new ExprCapabilityQuery(
        provenanceId,
        null, // operationType
        fieldKey,
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
        null, // tokenValuePattern
        effectiveFrom,
        null // effectiveTo
        );
  }
}
