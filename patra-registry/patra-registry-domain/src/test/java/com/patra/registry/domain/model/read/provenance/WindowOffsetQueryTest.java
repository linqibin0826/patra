package com.patra.registry.domain.model.read.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link WindowOffsetQuery} 的单元测试。
/// 
/// 测试策略: 纯 Java Record 单元测试,验证 compact constructor 的校验逻辑、trim 行为和 record 语义。
/// 
/// @author linqibin
/// @since 0.1.0
@DisplayName("WindowOffsetQuery 单元测试")
class WindowOffsetQueryTest {

  private static final Long VALID_ID = 1L;
  private static final Long VALID_PROVENANCE_ID = 100L;
  private static final String VALID_OPERATION_TYPE = "FETCH";
  private static final Instant VALID_EFFECTIVE_FROM = Instant.parse("2025-01-01T00:00:00Z");
  private static final Instant VALID_EFFECTIVE_TO = Instant.parse("2025-12-31T23:59:59Z");
  private static final String VALID_WINDOW_MODE_CODE = "TUMBLING";
  private static final Integer VALID_WINDOW_SIZE_VALUE = 7;
  private static final String VALID_WINDOW_SIZE_UNIT_CODE = "DAYS";
  private static final String VALID_CALENDAR_ALIGN_TO = "START_OF_DAY";
  private static final Integer VALID_LOOKBACK_VALUE = 1;
  private static final String VALID_LOOKBACK_UNIT_CODE = "HOURS";
  private static final Integer VALID_OVERLAP_VALUE = 30;
  private static final String VALID_OVERLAP_UNIT_CODE = "MINUTES";
  private static final Integer VALID_WATERMARK_LAG_SECONDS = 300;
  private static final String VALID_OFFSET_TYPE_CODE = "DATE_FIELD";
  private static final String VALID_OFFSET_FIELD_KEY = "lastModifiedDate";
  private static final String VALID_OFFSET_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  private static final String VALID_WINDOW_DATE_FIELD_KEY = "publicationDate";
  private static final Integer VALID_MAX_IDS_PER_WINDOW = 10000;
  private static final Integer VALID_MAX_WINDOW_SPAN_SECONDS = 86400;

  @Nested
  @DisplayName("成功构造测试")
  class SuccessfulConstruction {

    @Test
    @DisplayName("所有字段有效时应成功创建")
    void shouldCreateWithAllFieldsValid() {
      // Given
      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isEqualTo(VALID_EFFECTIVE_TO);
      assertThat(query.windowModeCode()).isEqualTo(VALID_WINDOW_MODE_CODE);
      assertThat(query.windowSizeValue()).isEqualTo(VALID_WINDOW_SIZE_VALUE);
      assertThat(query.windowSizeUnitCode()).isEqualTo(VALID_WINDOW_SIZE_UNIT_CODE);
      assertThat(query.calendarAlignTo()).isEqualTo(VALID_CALENDAR_ALIGN_TO);
      assertThat(query.lookbackValue()).isEqualTo(VALID_LOOKBACK_VALUE);
      assertThat(query.lookbackUnitCode()).isEqualTo(VALID_LOOKBACK_UNIT_CODE);
      assertThat(query.overlapValue()).isEqualTo(VALID_OVERLAP_VALUE);
      assertThat(query.overlapUnitCode()).isEqualTo(VALID_OVERLAP_UNIT_CODE);
      assertThat(query.watermarkLagSeconds()).isEqualTo(VALID_WATERMARK_LAG_SECONDS);
      assertThat(query.offsetTypeCode()).isEqualTo(VALID_OFFSET_TYPE_CODE);
      assertThat(query.offsetFieldKey()).isEqualTo(VALID_OFFSET_FIELD_KEY);
      assertThat(query.offsetDateFormat()).isEqualTo(VALID_OFFSET_DATE_FORMAT);
      assertThat(query.windowDateFieldKey()).isEqualTo(VALID_WINDOW_DATE_FIELD_KEY);
      assertThat(query.maxIdsPerWindow()).isEqualTo(VALID_MAX_IDS_PER_WINDOW);
      assertThat(query.maxWindowSpanSeconds()).isEqualTo(VALID_MAX_WINDOW_SPAN_SECONDS);
    }

    @Test
    @DisplayName("所有可选字段为 null 时应成功创建")
    void shouldCreateWithOptionalFieldsNull() {
      // Given
      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null, // operationType
              VALID_EFFECTIVE_FROM,
              null, // effectiveTo
              VALID_WINDOW_MODE_CODE,
              null, // windowSizeValue
              VALID_WINDOW_SIZE_UNIT_CODE,
              null, // calendarAlignTo
              null, // lookbackValue
              null, // lookbackUnitCode
              null, // overlapValue
              null, // overlapUnitCode
              null, // watermarkLagSeconds
              VALID_OFFSET_TYPE_CODE,
              null, // offsetFieldKey
              null, // offsetDateFormat
              null, // windowDateFieldKey
              null, // maxIdsPerWindow
              null // maxWindowSpanSeconds
              );

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isNull();
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isNull();
      assertThat(query.windowModeCode()).isEqualTo(VALID_WINDOW_MODE_CODE);
      assertThat(query.windowSizeValue()).isNull();
      assertThat(query.windowSizeUnitCode()).isEqualTo(VALID_WINDOW_SIZE_UNIT_CODE);
      assertThat(query.calendarAlignTo()).isNull();
      assertThat(query.lookbackValue()).isNull();
      assertThat(query.lookbackUnitCode()).isNull();
      assertThat(query.overlapValue()).isNull();
      assertThat(query.overlapUnitCode()).isNull();
      assertThat(query.watermarkLagSeconds()).isNull();
      assertThat(query.offsetTypeCode()).isEqualTo(VALID_OFFSET_TYPE_CODE);
      assertThat(query.offsetFieldKey()).isNull();
      assertThat(query.offsetDateFormat()).isNull();
      assertThat(query.windowDateFieldKey()).isNull();
      assertThat(query.maxIdsPerWindow()).isNull();
      assertThat(query.maxWindowSpanSeconds()).isNull();
    }

    @Test
    @DisplayName("最小必填字段应成功创建")
    void shouldCreateWithMinimalRequiredFields() {
      // Given
      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.windowModeCode()).isEqualTo(VALID_WINDOW_MODE_CODE);
      assertThat(query.windowSizeUnitCode()).isEqualTo(VALID_WINDOW_SIZE_UNIT_CODE);
      assertThat(query.offsetTypeCode()).isEqualTo(VALID_OFFSET_TYPE_CODE);
    }
  }

  @Nested
  @DisplayName("ID 验证测试")
  class IdValidation {

    @Test
    @DisplayName("id 为 null 时应抛出异常")
    void shouldThrowExceptionWhenIdIsNull() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      null,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("窗口偏移配置ID必须为正数");
    }

    @Test
    @DisplayName("id 为 0 时应抛出异常")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      0L,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("窗口偏移配置ID必须为正数");
    }

    @Test
    @DisplayName("id 为负数时应抛出异常")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      -1L,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("窗口偏移配置ID必须为正数");
    }
  }

  @Nested
  @DisplayName("来源 ID 验证测试")
  class ProvenanceIdValidation {

    @Test
    @DisplayName("provenanceId 为 null 时应抛出异常")
    void shouldThrowExceptionWhenProvenanceIdIsNull() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      null,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("来源ID必须为正数");
    }

    @Test
    @DisplayName("provenanceId 为 0 时应抛出异常")
    void shouldThrowExceptionWhenProvenanceIdIsZero() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      0L,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("来源ID必须为正数");
    }

    @Test
    @DisplayName("provenanceId 为负数时应抛出异常")
    void shouldThrowExceptionWhenProvenanceIdIsNegative() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      -1L,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("来源ID必须为正数");
    }
  }

  @Nested
  @DisplayName("窗口模式代码验证测试")
  class WindowModeCodeValidation {

    @Test
    @DisplayName("windowModeCode 为 null 时应抛出异常")
    void shouldThrowExceptionWhenWindowModeCodeIsNull() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      null,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("窗口模式代码不能为空");
    }

    @Test
    @DisplayName("windowModeCode 为空字符串时应抛出异常")
    void shouldThrowExceptionWhenWindowModeCodeIsEmpty() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      "",
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("窗口模式代码不能为空");
    }

    @Test
    @DisplayName("windowModeCode 为空白字符串时应抛出异常")
    void shouldThrowExceptionWhenWindowModeCodeIsBlank() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      "   ",
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("窗口模式代码不能为空");
    }
  }

  @Nested
  @DisplayName("窗口大小单位代码验证测试")
  class WindowSizeUnitCodeValidation {

    @Test
    @DisplayName("windowSizeUnitCode 为 null 时应抛出异常")
    void shouldThrowExceptionWhenWindowSizeUnitCodeIsNull() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("窗口大小单位代码不能为空");
    }

    @Test
    @DisplayName("windowSizeUnitCode 为空字符串时应抛出异常")
    void shouldThrowExceptionWhenWindowSizeUnitCodeIsEmpty() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      "",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("窗口大小单位代码不能为空");
    }

    @Test
    @DisplayName("windowSizeUnitCode 为空白字符串时应抛出异常")
    void shouldThrowExceptionWhenWindowSizeUnitCodeIsBlank() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      "   ",
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("窗口大小单位代码不能为空");
    }
  }

  @Nested
  @DisplayName("偏移类型代码验证测试")
  class OffsetTypeCodeValidation {

    @Test
    @DisplayName("offsetTypeCode 为 null 时应抛出异常")
    void shouldThrowExceptionWhenOffsetTypeCodeIsNull() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("偏移类型代码不能为空");
    }

    @Test
    @DisplayName("offsetTypeCode 为空字符串时应抛出异常")
    void shouldThrowExceptionWhenOffsetTypeCodeIsEmpty() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("偏移类型代码不能为空");
    }

    @Test
    @DisplayName("offsetTypeCode 为空白字符串时应抛出异常")
    void shouldThrowExceptionWhenOffsetTypeCodeIsBlank() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      "   ",
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("偏移类型代码不能为空");
    }
  }

  @Nested
  @DisplayName("生效时间验证测试")
  class EffectiveFromValidation {

    @Test
    @DisplayName("effectiveFrom 为 null 时应抛出异常")
    void shouldThrowExceptionWhenEffectiveFromIsNull() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new WindowOffsetQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      null,
                      null,
                      VALID_WINDOW_MODE_CODE,
                      null,
                      VALID_WINDOW_SIZE_UNIT_CODE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      VALID_OFFSET_TYPE_CODE,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("生效时间不能为null");
    }
  }

  @Nested
  @DisplayName("字符串 Trim 逻辑测试")
  class TrimBehavior {

    @Test
    @DisplayName("operationType 前后空格应被 trim")
    void shouldTrimOperationType() {
      // Given
      String operationTypeWithSpaces = "  FETCH  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              operationTypeWithSpaces,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.operationType()).isEqualTo("FETCH");
    }

    @Test
    @DisplayName("windowModeCode 前后空格应被 trim")
    void shouldTrimWindowModeCode() {
      // Given
      String windowModeCodeWithSpaces = "  TUMBLING  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              windowModeCodeWithSpaces,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.windowModeCode()).isEqualTo("TUMBLING");
    }

    @Test
    @DisplayName("windowSizeUnitCode 前后空格应被 trim")
    void shouldTrimWindowSizeUnitCode() {
      // Given
      String windowSizeUnitCodeWithSpaces = "  DAYS  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              windowSizeUnitCodeWithSpaces,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.windowSizeUnitCode()).isEqualTo("DAYS");
    }

    @Test
    @DisplayName("calendarAlignTo 前后空格应被 trim")
    void shouldTrimCalendarAlignTo() {
      // Given
      String calendarAlignToWithSpaces = "  START_OF_DAY  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              calendarAlignToWithSpaces,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.calendarAlignTo()).isEqualTo("START_OF_DAY");
    }

    @Test
    @DisplayName("lookbackUnitCode 前后空格应被 trim")
    void shouldTrimLookbackUnitCode() {
      // Given
      String lookbackUnitCodeWithSpaces = "  HOURS  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              lookbackUnitCodeWithSpaces,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.lookbackUnitCode()).isEqualTo("HOURS");
    }

    @Test
    @DisplayName("overlapUnitCode 前后空格应被 trim")
    void shouldTrimOverlapUnitCode() {
      // Given
      String overlapUnitCodeWithSpaces = "  MINUTES  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              overlapUnitCodeWithSpaces,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.overlapUnitCode()).isEqualTo("MINUTES");
    }

    @Test
    @DisplayName("offsetTypeCode 前后空格应被 trim")
    void shouldTrimOffsetTypeCode() {
      // Given
      String offsetTypeCodeWithSpaces = "  DATE_FIELD  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              offsetTypeCodeWithSpaces,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.offsetTypeCode()).isEqualTo("DATE_FIELD");
    }

    @Test
    @DisplayName("offsetFieldKey 前后空格应被 trim")
    void shouldTrimOffsetFieldKey() {
      // Given
      String offsetFieldKeyWithSpaces = "  lastModifiedDate  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              offsetFieldKeyWithSpaces,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.offsetFieldKey()).isEqualTo("lastModifiedDate");
    }

    @Test
    @DisplayName("offsetFieldKey trim 后为空字符串时应转换为 null")
    void shouldConvertEmptyOffsetFieldKeyToNull() {
      // Given
      String emptyOffsetFieldKey = "   ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              emptyOffsetFieldKey,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.offsetFieldKey()).isNull();
    }

    @Test
    @DisplayName("offsetDateFormat 前后空格应被 trim")
    void shouldTrimOffsetDateFormat() {
      // Given
      String offsetDateFormatWithSpaces = "  yyyy-MM-dd  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              offsetDateFormatWithSpaces,
              null,
              null,
              null);

      // Then
      assertThat(query.offsetDateFormat()).isEqualTo("yyyy-MM-dd");
    }

    @Test
    @DisplayName("windowDateFieldKey 前后空格应被 trim")
    void shouldTrimWindowDateFieldKey() {
      // Given
      String windowDateFieldKeyWithSpaces = "  publicationDate  ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              windowDateFieldKeyWithSpaces,
              null,
              null);

      // Then
      assertThat(query.windowDateFieldKey()).isEqualTo("publicationDate");
    }

    @Test
    @DisplayName("windowDateFieldKey trim 后为空字符串时应转换为 null")
    void shouldConvertEmptyWindowDateFieldKeyToNull() {
      // Given
      String emptyWindowDateFieldKey = "   ";

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              emptyWindowDateFieldKey,
              null,
              null);

      // Then
      assertThat(query.windowDateFieldKey()).isNull();
    }

    @Test
    @DisplayName("所有可 trim 字段为 null 时不应抛出异常")
    void shouldNotThrowExceptionWhenTrimmableFieldsAreNull() {
      // Given
      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.operationType()).isNull();
      assertThat(query.calendarAlignTo()).isNull();
      assertThat(query.lookbackUnitCode()).isNull();
      assertThat(query.overlapUnitCode()).isNull();
      assertThat(query.offsetFieldKey()).isNull();
      assertThat(query.offsetDateFormat()).isNull();
      assertThat(query.windowDateFieldKey()).isNull();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemantics {

    @Test
    @DisplayName("相同字段值的实例应该相等")
    void shouldBeEqualWhenFieldsAreIdentical() {
      // Given
      var query1 =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      var query2 =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      // When & Then
      assertThat(query1).isEqualTo(query2);
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("不同字段值的实例应该不相等")
    void shouldNotBeEqualWhenFieldsAreDifferent() {
      // Given
      var query1 =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      var query2 =
          new WindowOffsetQuery(
              2L, // 不同的 ID
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("toString 应该返回可读的字符串表示")
    void shouldHaveReadableToString() {
      // Given
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      // When
      String toString = query.toString();

      // Then
      assertThat(toString)
          .contains("WindowOffsetQuery")
          .contains("id=" + VALID_ID)
          .contains("provenanceId=" + VALID_PROVENANCE_ID)
          .contains("windowModeCode=" + VALID_WINDOW_MODE_CODE)
          .contains("offsetTypeCode=" + VALID_OFFSET_TYPE_CODE);
    }

    @Test
    @DisplayName("组件访问器应该返回正确的字段值")
    void shouldAccessComponentsCorrectly() {
      // Given
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      // When & Then
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isEqualTo(VALID_EFFECTIVE_TO);
      assertThat(query.windowModeCode()).isEqualTo(VALID_WINDOW_MODE_CODE);
      assertThat(query.windowSizeValue()).isEqualTo(VALID_WINDOW_SIZE_VALUE);
      assertThat(query.windowSizeUnitCode()).isEqualTo(VALID_WINDOW_SIZE_UNIT_CODE);
      assertThat(query.calendarAlignTo()).isEqualTo(VALID_CALENDAR_ALIGN_TO);
      assertThat(query.lookbackValue()).isEqualTo(VALID_LOOKBACK_VALUE);
      assertThat(query.lookbackUnitCode()).isEqualTo(VALID_LOOKBACK_UNIT_CODE);
      assertThat(query.overlapValue()).isEqualTo(VALID_OVERLAP_VALUE);
      assertThat(query.overlapUnitCode()).isEqualTo(VALID_OVERLAP_UNIT_CODE);
      assertThat(query.watermarkLagSeconds()).isEqualTo(VALID_WATERMARK_LAG_SECONDS);
      assertThat(query.offsetTypeCode()).isEqualTo(VALID_OFFSET_TYPE_CODE);
      assertThat(query.offsetFieldKey()).isEqualTo(VALID_OFFSET_FIELD_KEY);
      assertThat(query.offsetDateFormat()).isEqualTo(VALID_OFFSET_DATE_FORMAT);
      assertThat(query.windowDateFieldKey()).isEqualTo(VALID_WINDOW_DATE_FIELD_KEY);
      assertThat(query.maxIdsPerWindow()).isEqualTo(VALID_MAX_IDS_PER_WINDOW);
      assertThat(query.maxWindowSpanSeconds()).isEqualTo(VALID_MAX_WINDOW_SPAN_SECONDS);
    }

    @Test
    @DisplayName("与自身比较应相等")
    void shouldBeEqualToItself() {
      // Given
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      // When & Then
      assertThat(query).isEqualTo(query);
    }

    @Test
    @DisplayName("与 null 比较应不相等")
    void shouldNotBeEqualToNull() {
      // Given
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // When & Then
      assertThat(query).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与不同类型比较应不相等")
    void shouldNotBeEqualToDifferentType() {
      // Given
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // When & Then
      assertThat(query).isNotEqualTo("not a WindowOffsetQuery");
    }

    @Test
    @DisplayName("hashCode 应对相等对象保持一致")
    void shouldHaveConsistentHashCodeForEqualObjects() {
      // Given
      var query1 =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      var query2 =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      // When & Then
      int hashCode1 = query1.hashCode();
      int hashCode2 = query1.hashCode();
      int hashCode3 = query2.hashCode();

      assertThat(hashCode1).isEqualTo(hashCode2);
      assertThat(hashCode1).isEqualTo(hashCode3);
    }
  }

  @Nested
  @DisplayName("不可变性测试")
  class Immutability {

    @Test
    @DisplayName("Record 应该是不可变的")
    void shouldBeImmutable() {
      // Given
      var originalInstant = Instant.parse("2025-01-01T00:00:00Z");

      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              originalInstant,
              VALID_EFFECTIVE_TO,
              VALID_WINDOW_MODE_CODE,
              VALID_WINDOW_SIZE_VALUE,
              VALID_WINDOW_SIZE_UNIT_CODE,
              VALID_CALENDAR_ALIGN_TO,
              VALID_LOOKBACK_VALUE,
              VALID_LOOKBACK_UNIT_CODE,
              VALID_OVERLAP_VALUE,
              VALID_OVERLAP_UNIT_CODE,
              VALID_WATERMARK_LAG_SECONDS,
              VALID_OFFSET_TYPE_CODE,
              VALID_OFFSET_FIELD_KEY,
              VALID_OFFSET_DATE_FORMAT,
              VALID_WINDOW_DATE_FIELD_KEY,
              VALID_MAX_IDS_PER_WINDOW,
              VALID_MAX_WINDOW_SPAN_SECONDS);

      // When
      Instant retrievedInstant = query.effectiveFrom();

      // Then
      // Instant 本身是不可变的,验证返回的是相同实例
      assertThat(retrievedInstant).isSameAs(originalInstant);
      // 验证所有字段都可以访问且值正确
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
    }
  }

  @Nested
  @DisplayName("边界情况测试")
  class EdgeCases {

    @Test
    @DisplayName("id 为 Long.MAX_VALUE 时应成功创建")
    void shouldCreateWithMaxLongId() {
      // Given
      Long maxId = Long.MAX_VALUE;

      // When
      var query =
          new WindowOffsetQuery(
              maxId,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.id()).isEqualTo(maxId);
    }

    @Test
    @DisplayName("provenanceId 为 Long.MAX_VALUE 时应成功创建")
    void shouldCreateWithMaxLongProvenanceId() {
      // Given
      Long maxProvenanceId = Long.MAX_VALUE;

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              maxProvenanceId,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.provenanceId()).isEqualTo(maxProvenanceId);
    }

    @Test
    @DisplayName("effectiveFrom 为 Instant.MIN 时应成功创建")
    void shouldCreateWithMinInstant() {
      // Given
      Instant minInstant = Instant.MIN;

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              minInstant,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.effectiveFrom()).isEqualTo(minInstant);
    }

    @Test
    @DisplayName("effectiveFrom 为 Instant.MAX 时应成功创建")
    void shouldCreateWithMaxInstant() {
      // Given
      Instant maxInstant = Instant.MAX;

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              maxInstant,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.effectiveFrom()).isEqualTo(maxInstant);
    }

    @Test
    @DisplayName("effectiveFrom 和 effectiveTo 时间相同时应成功创建")
    void shouldCreateWhenEffectiveFromAndToAreEqual() {
      // Given
      Instant sameTime = Instant.parse("2025-06-01T00:00:00Z");

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              sameTime,
              sameTime,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.effectiveFrom()).isEqualTo(sameTime);
      assertThat(query.effectiveTo()).isEqualTo(sameTime);
    }

    @Test
    @DisplayName("effectiveTo 早于 effectiveFrom 时应成功创建")
    void shouldCreateWhenEffectiveToBeforeEffectiveFrom() {
      // Given
      Instant effectiveFrom = Instant.parse("2025-12-31T23:59:59Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              effectiveFrom,
              effectiveTo,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(query.effectiveTo()).isBefore(query.effectiveFrom());
    }

    @Test
    @DisplayName("窗口大小值为 0 时应成功创建")
    void shouldCreateWithZeroWindowSizeValue() {
      // Given
      Integer zeroWindowSize = 0;

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              zeroWindowSize,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.windowSizeValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("窗口大小值为负数时应成功创建")
    void shouldCreateWithNegativeWindowSizeValue() {
      // Given
      Integer negativeWindowSize = -1;

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              negativeWindowSize,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              null,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.windowSizeValue()).isEqualTo(-1);
    }

    @Test
    @DisplayName("水位延迟秒数为 Integer.MAX_VALUE 时应成功创建")
    void shouldCreateWithMaxWatermarkLagSeconds() {
      // Given
      Integer maxWatermarkLag = Integer.MAX_VALUE;

      // When
      var query =
          new WindowOffsetQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_WINDOW_MODE_CODE,
              null,
              VALID_WINDOW_SIZE_UNIT_CODE,
              null,
              null,
              null,
              null,
              null,
              maxWatermarkLag,
              VALID_OFFSET_TYPE_CODE,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.watermarkLagSeconds()).isEqualTo(Integer.MAX_VALUE);
    }
  }
}
