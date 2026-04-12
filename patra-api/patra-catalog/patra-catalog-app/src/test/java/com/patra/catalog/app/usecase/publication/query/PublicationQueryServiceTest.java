package com.patra.catalog.app.usecase.publication.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.publication.query.dto.PublicationListQuery;
import com.patra.catalog.domain.model.read.publication.PublicationFilter;
import com.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import com.patra.catalog.domain.port.read.PublicationReadPort;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PublicationQueryService 单元测试。
///
/// **测试目标**：
///
/// - 分页参数规范化（page/pageSize）
/// - 关键词及筛选参数规范化（trim 与空白归一化）
/// - 正确委托 PublicationReadPort
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("PublicationQueryService 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PublicationQueryServiceTest {

  private static final PublicationFilter EMPTY_FILTER = PublicationFilter.builder().build();

  @Mock private PublicationReadPort publicationReadPort;

  /// 默认参数场景应使用 page=1、pageSize=20，且空白字段归一化为 null。
  @Test
  @DisplayName("默认参数应归一化为 page=1, pageSize=20")
  void shouldNormalizeDefaults() {
    // Given
    PublicationQueryService service = new PublicationQueryService(publicationReadPort);
    PageResult<PublicationSummaryReadModel> expected =
        PageResult.of(
            List.of(
                new PublicationSummaryReadModel(
                    1L,
                    "Test Article",
                    "12345678",
                    "10.1234/test",
                    2024,
                    "en",
                    true,
                    "gold",
                    100L,
                    "Nature",
                    10,
                    Instant.parse("2026-02-13T00:00:00Z"))),
            1,
            20,
            1);
    when(publicationReadPort.findPublicationPage(eq(PagingParams.of(1, 20)), eq(EMPTY_FILTER)))
        .thenReturn(expected);

    // When
    PageResult<PublicationSummaryReadModel> actual =
        service.listPublications(PublicationListQuery.builder().q("   ").build());

    // Then
    assertThat(actual).isEqualTo(expected);
    verify(publicationReadPort).findPublicationPage(PagingParams.of(1, 20), EMPTY_FILTER);
  }

  /// query 为 null 时应抛出 NullPointerException。
  @Test
  @DisplayName("query 为 null 应抛出 NPE")
  void shouldThrowNpeWhenQueryIsNull() {
    // Given
    PublicationQueryService service = new PublicationQueryService(publicationReadPort);

    // When & Then
    assertThatNullPointerException()
        .isThrownBy(() -> service.listPublications(null))
        .withMessage("query must not be null");
  }

  @Nested
  @DisplayName("参数边界归一化")
  class ParameterNormalizeTests {

    /// 非法页码和超大 pageSize 应被夹紧到合法范围。
    @Test
    @DisplayName("非法分页参数应被归一化")
    void shouldNormalizeInvalidPageAndPageSize() {
      // Given
      PublicationQueryService service = new PublicationQueryService(publicationReadPort);
      PublicationFilter filter = PublicationFilter.builder().keyword("cancer").build();
      PageResult<PublicationSummaryReadModel> expected = PageResult.empty(1, 100);
      when(publicationReadPort.findPublicationPage(eq(PagingParams.of(1, 100)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<PublicationSummaryReadModel> actual =
          service.listPublications(
              PublicationListQuery.builder().page(0).pageSize(1000).q("  cancer  ").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(publicationReadPort).findPublicationPage(PagingParams.of(1, 100), filter);
    }

    /// 正常分页参数应保持原值传递。
    @Test
    @DisplayName("合法分页参数应原样传递")
    void shouldKeepValidPageAndPageSize() {
      // Given
      PublicationQueryService service = new PublicationQueryService(publicationReadPort);
      PublicationFilter filter = PublicationFilter.builder().keyword("apoptosis").build();
      PageResult<PublicationSummaryReadModel> expected = PageResult.empty(3, 50);
      when(publicationReadPort.findPublicationPage(eq(PagingParams.of(3, 50)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<PublicationSummaryReadModel> actual =
          service.listPublications(
              PublicationListQuery.builder().page(3).pageSize(50).q("apoptosis").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(publicationReadPort).findPublicationPage(PagingParams.of(3, 50), filter);
    }
  }

  @Nested
  @DisplayName("单个筛选条件生效")
  class SingleFilterTests {

    /// 只传 yearFrom 时，仅该字段参与筛选。
    @Test
    @DisplayName("只传 yearFrom 应正确构造 filter")
    void shouldFilterByYearFromOnly() {
      // Given
      PublicationQueryService service = new PublicationQueryService(publicationReadPort);
      PublicationFilter filter = PublicationFilter.builder().yearFrom(2020).build();
      PageResult<PublicationSummaryReadModel> expected = PageResult.empty(1, 20);
      when(publicationReadPort.findPublicationPage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<PublicationSummaryReadModel> actual =
          service.listPublications(PublicationListQuery.builder().yearFrom(2020).build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(publicationReadPort).findPublicationPage(PagingParams.of(1, 20), filter);
    }

    /// 只传 venueId 时，仅该字段参与筛选。
    @Test
    @DisplayName("只传 venueId 应正确构造 filter")
    void shouldFilterByVenueIdOnly() {
      // Given
      PublicationQueryService service = new PublicationQueryService(publicationReadPort);
      PublicationFilter filter = PublicationFilter.builder().venueId(100L).build();
      PageResult<PublicationSummaryReadModel> expected = PageResult.empty(1, 20);
      when(publicationReadPort.findPublicationPage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<PublicationSummaryReadModel> actual =
          service.listPublications(PublicationListQuery.builder().venueId(100L).build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(publicationReadPort).findPublicationPage(PagingParams.of(1, 20), filter);
    }

    /// 只传 isOa 时，仅该字段参与筛选。
    @Test
    @DisplayName("只传 isOa 应正确构造 filter")
    void shouldFilterByIsOaOnly() {
      // Given
      PublicationQueryService service = new PublicationQueryService(publicationReadPort);
      PublicationFilter filter = PublicationFilter.builder().isOa(true).build();
      PageResult<PublicationSummaryReadModel> expected = PageResult.empty(1, 20);
      when(publicationReadPort.findPublicationPage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<PublicationSummaryReadModel> actual =
          service.listPublications(PublicationListQuery.builder().isOa(true).build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(publicationReadPort).findPublicationPage(PagingParams.of(1, 20), filter);
    }
  }

  @Nested
  @DisplayName("多个筛选条件组合")
  class CombinedFilterTests {

    /// q + yearFrom + yearTo + isOa 同时传入时，全部参与筛选。
    @Test
    @DisplayName("多条件组合筛选")
    void shouldCombineMultipleFilters() {
      // Given
      PublicationQueryService service = new PublicationQueryService(publicationReadPort);
      PublicationFilter filter =
          PublicationFilter.builder()
              .keyword("cancer")
              .yearFrom(2020)
              .yearTo(2024)
              .isOa(true)
              .build();
      PageResult<PublicationSummaryReadModel> expected = PageResult.empty(1, 20);
      when(publicationReadPort.findPublicationPage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<PublicationSummaryReadModel> actual =
          service.listPublications(
              PublicationListQuery.builder()
                  .q("cancer")
                  .yearFrom(2020)
                  .yearTo(2024)
                  .isOa(true)
                  .build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(publicationReadPort).findPublicationPage(PagingParams.of(1, 20), filter);
    }
  }

  @Nested
  @DisplayName("筛选字段空白归一化")
  class FilterFieldNormalizationTests {

    /// languageBase 为空白时应归一化为 null。
    @Test
    @DisplayName("languageBase 空白应归一化为 null")
    void shouldNormalizeBlankLanguageBaseToNull() {
      // Given
      PublicationQueryService service = new PublicationQueryService(publicationReadPort);
      PageResult<PublicationSummaryReadModel> expected = PageResult.empty(1, 20);
      when(publicationReadPort.findPublicationPage(eq(PagingParams.of(1, 20)), eq(EMPTY_FILTER)))
          .thenReturn(expected);

      // When
      PageResult<PublicationSummaryReadModel> actual =
          service.listPublications(PublicationListQuery.builder().languageBase("  ").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(publicationReadPort).findPublicationPage(PagingParams.of(1, 20), EMPTY_FILTER);
    }

    /// oaStatus 为空白时应归一化为 null。
    @Test
    @DisplayName("oaStatus 空白应归一化为 null")
    void shouldNormalizeBlankOaStatusToNull() {
      // Given
      PublicationQueryService service = new PublicationQueryService(publicationReadPort);
      PageResult<PublicationSummaryReadModel> expected = PageResult.empty(1, 20);
      when(publicationReadPort.findPublicationPage(eq(PagingParams.of(1, 20)), eq(EMPTY_FILTER)))
          .thenReturn(expected);

      // When
      PageResult<PublicationSummaryReadModel> actual =
          service.listPublications(PublicationListQuery.builder().oaStatus("  \t ").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(publicationReadPort).findPublicationPage(PagingParams.of(1, 20), EMPTY_FILTER);
    }

    /// pmid 为空白时应归一化为 null。
    @Test
    @DisplayName("pmid 空白应归一化为 null")
    void shouldNormalizeBlankPmidToNull() {
      // Given
      PublicationQueryService service = new PublicationQueryService(publicationReadPort);
      PageResult<PublicationSummaryReadModel> expected = PageResult.empty(1, 20);
      when(publicationReadPort.findPublicationPage(eq(PagingParams.of(1, 20)), eq(EMPTY_FILTER)))
          .thenReturn(expected);

      // When
      PageResult<PublicationSummaryReadModel> actual =
          service.listPublications(PublicationListQuery.builder().pmid("   ").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(publicationReadPort).findPublicationPage(PagingParams.of(1, 20), EMPTY_FILTER);
    }
  }
}
