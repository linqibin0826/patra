package com.patra.registry.domain.model.read.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PaginationConfigQuery} 的单元测试。
 *
 * <p>测试策略: 纯 Java Record 单元测试,验证 compact constructor 的校验逻辑、trim 行为和 record 语义。
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("PaginationConfigQuery 单元测试")
class PaginationConfigQueryTest {

  private static final Long VALID_ID = 1L;
  private static final Long VALID_PROVENANCE_ID = 100L;
  private static final String VALID_OPERATION_TYPE = "SEARCH";
  private static final Instant VALID_EFFECTIVE_FROM = Instant.parse("2025-01-01T00:00:00Z");
  private static final Instant VALID_EFFECTIVE_TO = Instant.parse("2025-12-31T23:59:59Z");
  private static final String VALID_PAGINATION_MODE = "OFFSET";
  private static final Integer VALID_PAGE_SIZE = 100;
  private static final Integer VALID_MAX_PAGES = 10;
  private static final String VALID_SORT_FIELD = "publicationDate";
  private static final Integer VALID_SORT_DIRECTION = 1;

  @Nested
  @DisplayName("成功构造测试")
  class SuccessfulConstruction {

    @Test
    @DisplayName("所有字段有效时应成功创建")
    void shouldCreateWithAllFieldsValid() {
      // Given
      // When
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_PAGINATION_MODE,
              VALID_PAGE_SIZE,
              VALID_MAX_PAGES,
              VALID_SORT_FIELD,
              VALID_SORT_DIRECTION);

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isEqualTo(VALID_EFFECTIVE_TO);
      assertThat(query.paginationModeCode()).isEqualTo(VALID_PAGINATION_MODE);
      assertThat(query.pageSizeValue()).isEqualTo(VALID_PAGE_SIZE);
      assertThat(query.maxPagesPerExecution()).isEqualTo(VALID_MAX_PAGES);
      assertThat(query.sortFieldParamName()).isEqualTo(VALID_SORT_FIELD);
      assertThat(query.sortingDirection()).isEqualTo(VALID_SORT_DIRECTION);
    }

    @Test
    @DisplayName("所有可选字段为 null 时应成功创建")
    void shouldCreateWithOptionalFieldsNull() {
      // Given
      // When
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null, // operationType
              VALID_EFFECTIVE_FROM,
              null, // effectiveTo
              VALID_PAGINATION_MODE,
              null, // pageSizeValue
              null, // maxPagesPerExecution
              null, // sortFieldParamName
              null // sortingDirection
              );

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isNull();
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isNull();
      assertThat(query.paginationModeCode()).isEqualTo(VALID_PAGINATION_MODE);
      assertThat(query.pageSizeValue()).isNull();
      assertThat(query.maxPagesPerExecution()).isNull();
      assertThat(query.sortFieldParamName()).isNull();
      assertThat(query.sortingDirection()).isNull();
    }

    @Test
    @DisplayName("最小必填字段应成功创建")
    void shouldCreateWithMinimalRequiredFields() {
      // Given
      // When
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_PAGINATION_MODE,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.paginationModeCode()).isEqualTo(VALID_PAGINATION_MODE);
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
                  new PaginationConfigQuery(
                      null,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_PAGINATION_MODE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("分页配置ID必须为正数");
    }

    @Test
    @DisplayName("id 为 0 时应抛出异常")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new PaginationConfigQuery(
                      0L,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_PAGINATION_MODE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("分页配置ID必须为正数");
    }

    @Test
    @DisplayName("id 为负数时应抛出异常")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new PaginationConfigQuery(
                      -1L,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_PAGINATION_MODE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("分页配置ID必须为正数");
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
                  new PaginationConfigQuery(
                      VALID_ID,
                      null,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_PAGINATION_MODE,
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
                  new PaginationConfigQuery(
                      VALID_ID,
                      0L,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_PAGINATION_MODE,
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
                  new PaginationConfigQuery(
                      VALID_ID,
                      -1L,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      VALID_PAGINATION_MODE,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("来源ID必须为正数");
    }
  }

  @Nested
  @DisplayName("分页模式代码验证测试")
  class PaginationModeCodeValidation {

    @Test
    @DisplayName("paginationModeCode 为 null 时应抛出异常")
    void shouldThrowExceptionWhenPaginationModeCodeIsNull() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new PaginationConfigQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("分页模式代码不能为空");
    }

    @Test
    @DisplayName("paginationModeCode 为空字符串时应抛出异常")
    void shouldThrowExceptionWhenPaginationModeCodeIsEmpty() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new PaginationConfigQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      "",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("分页模式代码不能为空");
    }

    @Test
    @DisplayName("paginationModeCode 为空白字符串时应抛出异常")
    void shouldThrowExceptionWhenPaginationModeCodeIsBlank() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new PaginationConfigQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      "   ",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("分页模式代码不能为空");
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
                  new PaginationConfigQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      null,
                      null,
                      VALID_PAGINATION_MODE,
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
      String operationTypeWithSpaces = "  SEARCH  ";

      // When
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              operationTypeWithSpaces,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_PAGINATION_MODE,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.operationType()).isEqualTo("SEARCH");
    }

    @Test
    @DisplayName("paginationModeCode 前后空格应被 trim")
    void shouldTrimPaginationModeCode() {
      // Given
      String paginationModeWithSpaces = "  OFFSET  ";

      // When
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              paginationModeWithSpaces,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.paginationModeCode()).isEqualTo("OFFSET");
    }

    @Test
    @DisplayName("sortFieldParamName 前后空格应被 trim")
    void shouldTrimSortFieldParamName() {
      // Given
      String sortFieldWithSpaces = "  publicationDate  ";

      // When
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_PAGINATION_MODE,
              null,
              null,
              sortFieldWithSpaces,
              null);

      // Then
      assertThat(query.sortFieldParamName()).isEqualTo("publicationDate");
    }

    @Test
    @DisplayName("operationType 为 null 时 trim 不应导致异常")
    void shouldNotThrowExceptionWhenOperationTypeIsNullDuringTrim() {
      // Given
      // When
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_PAGINATION_MODE,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.operationType()).isNull();
    }

    @Test
    @DisplayName("sortFieldParamName 为 null 时 trim 不应导致异常")
    void shouldNotThrowExceptionWhenSortFieldParamNameIsNullDuringTrim() {
      // Given
      // When
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_PAGINATION_MODE,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.sortFieldParamName()).isNull();
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
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_PAGINATION_MODE,
              VALID_PAGE_SIZE,
              VALID_MAX_PAGES,
              VALID_SORT_FIELD,
              VALID_SORT_DIRECTION);

      var query2 =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_PAGINATION_MODE,
              VALID_PAGE_SIZE,
              VALID_MAX_PAGES,
              VALID_SORT_FIELD,
              VALID_SORT_DIRECTION);

      // When & Then
      assertThat(query1).isEqualTo(query2);
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("不同字段值的实例应该不相等")
    void shouldNotBeEqualWhenFieldsAreDifferent() {
      // Given
      var query1 =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_PAGINATION_MODE,
              VALID_PAGE_SIZE,
              VALID_MAX_PAGES,
              VALID_SORT_FIELD,
              VALID_SORT_DIRECTION);

      var query2 =
          new PaginationConfigQuery(
              2L, // 不同的 ID
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_PAGINATION_MODE,
              VALID_PAGE_SIZE,
              VALID_MAX_PAGES,
              VALID_SORT_FIELD,
              VALID_SORT_DIRECTION);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("toString 应该返回可读的字符串表示")
    void shouldHaveReadableToString() {
      // Given
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_PAGINATION_MODE,
              VALID_PAGE_SIZE,
              VALID_MAX_PAGES,
              VALID_SORT_FIELD,
              VALID_SORT_DIRECTION);

      // When
      String toString = query.toString();

      // Then
      assertThat(toString)
          .contains("PaginationConfigQuery")
          .contains("id=" + VALID_ID)
          .contains("provenanceId=" + VALID_PROVENANCE_ID)
          .contains("operationType=" + VALID_OPERATION_TYPE)
          .contains("paginationModeCode=" + VALID_PAGINATION_MODE);
    }

    @Test
    @DisplayName("组件访问器应该返回正确的字段值")
    void shouldAccessComponentsCorrectly() {
      // Given
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_PAGINATION_MODE,
              VALID_PAGE_SIZE,
              VALID_MAX_PAGES,
              VALID_SORT_FIELD,
              VALID_SORT_DIRECTION);

      // When & Then
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isEqualTo(VALID_EFFECTIVE_TO);
      assertThat(query.paginationModeCode()).isEqualTo(VALID_PAGINATION_MODE);
      assertThat(query.pageSizeValue()).isEqualTo(VALID_PAGE_SIZE);
      assertThat(query.maxPagesPerExecution()).isEqualTo(VALID_MAX_PAGES);
      assertThat(query.sortFieldParamName()).isEqualTo(VALID_SORT_FIELD);
      assertThat(query.sortingDirection()).isEqualTo(VALID_SORT_DIRECTION);
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
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              originalInstant,
              VALID_EFFECTIVE_TO,
              VALID_PAGINATION_MODE,
              VALID_PAGE_SIZE,
              VALID_MAX_PAGES,
              VALID_SORT_FIELD,
              VALID_SORT_DIRECTION);

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
          new PaginationConfigQuery(
              maxId,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_PAGINATION_MODE,
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
          new PaginationConfigQuery(
              VALID_ID,
              maxProvenanceId,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              VALID_PAGINATION_MODE,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.provenanceId()).isEqualTo(maxProvenanceId);
    }

    @Test
    @DisplayName("paginationModeCode 仅包含空格时 trim 后为空应抛出异常")
    void shouldThrowExceptionWhenPaginationModeCodeIsOnlySpaces() {
      // Given
      // When & Then
      assertThatThrownBy(
              () ->
                  new PaginationConfigQuery(
                      VALID_ID,
                      VALID_PROVENANCE_ID,
                      null,
                      VALID_EFFECTIVE_FROM,
                      null,
                      "     ",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessage("分页模式代码不能为空");
    }

    @Test
    @DisplayName("effectiveFrom 和 effectiveTo 时间相同时应成功创建")
    void shouldCreateWhenEffectiveFromAndToAreEqual() {
      // Given
      Instant sameTime = Instant.parse("2025-06-01T00:00:00Z");

      // When
      var query =
          new PaginationConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              sameTime,
              sameTime,
              VALID_PAGINATION_MODE,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.effectiveFrom()).isEqualTo(sameTime);
      assertThat(query.effectiveTo()).isEqualTo(sameTime);
    }
  }
}
