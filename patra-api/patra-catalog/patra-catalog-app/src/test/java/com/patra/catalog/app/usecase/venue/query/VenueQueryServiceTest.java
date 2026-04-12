package com.patra.catalog.app.usecase.venue.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.catalog.domain.model.read.venue.VenueFilter;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.port.read.VenueReadPort;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueQueryService 单元测试。
///
/// **测试目标**：
///
/// - 分页参数规范化（page/pageSize）
/// - 关键词参数规范化（trim 与空白归一化）
/// - 正确委托 VenueReadPort
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueQueryService 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueQueryServiceTest {

  private static final VenueFilter EMPTY_FILTER = VenueFilter.builder().build();

  @Mock private VenueReadPort venueReadPort;

  /// 默认参数场景应使用 page=1、pageSize=20，且空白字段归一化为 null。
  @Test
  @DisplayName("默认参数应归一化为 page=1, pageSize=20")
  void shouldNormalizeDefaults() {
    // Given
    VenueQueryService service = new VenueQueryService(venueReadPort);
    PageResult<VenueSummaryReadModel> expected =
        PageResult.of(
            List.of(
                VenueSummaryReadModel.builder()
                    .id(1L)
                    .title("Nature")
                    .countryCode("US")
                    .hIndex(412)
                    .build()),
            1,
            20,
            1);
    when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(EMPTY_FILTER)))
        .thenReturn(expected);

    // When
    PageResult<VenueSummaryReadModel> actual =
        service.listVenues(VenueListQuery.builder().q("   ").build());

    // Then
    assertThat(actual).isEqualTo(expected);
    verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), EMPTY_FILTER);
  }

  /// query 为 null 时应抛出 NullPointerException。
  @Test
  @DisplayName("query 为 null 应抛出 NPE")
  void shouldThrowNpeWhenQueryIsNull() {
    // Given
    VenueQueryService service = new VenueQueryService(venueReadPort);

    // When & Then
    assertThatNullPointerException()
        .isThrownBy(() -> service.listVenues(null))
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
      VenueQueryService service = new VenueQueryService(venueReadPort);
      VenueFilter filter = VenueFilter.builder().keyword("Nature").build();
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 100);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 100)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(
              VenueListQuery.builder().page(0).pageSize(1000).q("  Nature  ").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 100), filter);
    }

    /// 正常分页参数应保持原值传递。
    @Test
    @DisplayName("合法分页参数应原样传递")
    void shouldKeepValidPageAndPageSize() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      VenueFilter filter = VenueFilter.builder().keyword("NLM001").build();
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(3, 50);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(3, 50)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().page(3).pageSize(50).q("NLM001").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(3, 50), filter);
    }
  }

  @Nested
  @DisplayName("单个筛选条件生效")
  class SingleFilterTests {

    /// 只传 countryCode 时，仅该字段参与筛选，其余为 null。
    @Test
    @DisplayName("只传 countryCode 应正确构造 filter")
    void shouldFilterByCountryCodeOnly() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      VenueFilter filter = VenueFilter.builder().countryCode("US").build();
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().countryCode("US").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), filter);
    }

    /// 只传 issnL 时，仅该字段参与筛选，其余为 null。
    @Test
    @DisplayName("只传 issnL 应正确构造 filter")
    void shouldFilterByIssnLOnly() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      VenueFilter filter = VenueFilter.builder().issnL("0028-0836").build();
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().issnL("0028-0836").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), filter);
    }

    /// 只传 nlmId 时，仅该字段参与筛选，其余为 null。
    @Test
    @DisplayName("只传 nlmId 应正确构造 filter")
    void shouldFilterByNlmIdOnly() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      VenueFilter filter = VenueFilter.builder().nlmId("0410462").build();
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().nlmId("0410462").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), filter);
    }
  }

  @Nested
  @DisplayName("多个筛选条件组合")
  class CombinedFilterTests {

    /// q + countryCode 同时传入时，两者均参与筛选。
    @Test
    @DisplayName("q + countryCode 组合筛选")
    void shouldCombineKeywordAndCountryCode() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      VenueFilter filter = VenueFilter.builder().keyword("Nature").countryCode("GB").build();
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().q("Nature").countryCode("GB").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), filter);
    }
  }

  @Nested
  @DisplayName("关键词 LIKE 通配符转义")
  class KeywordLikeEscapeTests {

    /// 用户输入含 `%` 时应被转义为 `!%`，防止被 SQL LIKE 当通配符。
    @Test
    @DisplayName("% 应被转义为 !%")
    void shouldEscapePercentInKeyword() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      VenueFilter filter = VenueFilter.builder().keyword("Nat!%re").build();
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().q("Nat%re").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), filter);
    }

    /// 用户输入含 `_` 时应被转义为 `!_`。
    @Test
    @DisplayName("_ 应被转义为 !_")
    void shouldEscapeUnderscoreInKeyword() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      VenueFilter filter = VenueFilter.builder().keyword("Nat!_re").build();
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().q("Nat_re").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), filter);
    }

    /// 用户输入含转义字符 `!` 自身时应被转义为 `!!`。
    @Test
    @DisplayName("! 应被转义为 !!")
    void shouldEscapeBangInKeyword() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      VenueFilter filter = VenueFilter.builder().keyword("Nat!!re").build();
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(filter)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().q("Nat!re").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), filter);
    }

    /// 空白输入不应生成空字符串的 keyword（应归一化为 null）。
    @Test
    @DisplayName("空白输入转义后仍为 null（trim 在 escape 之前生效）")
    void shouldReturnNullWhenKeywordBlank() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(EMPTY_FILTER)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().q("  ").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), EMPTY_FILTER);
    }
  }

  @Nested
  @DisplayName("筛选字段空白归一化")
  class FilterFieldNormalizationTests {

    /// countryCode 为空白时应归一化为 null。
    @Test
    @DisplayName("countryCode 空白应归一化为 null")
    void shouldNormalizeBlankCountryCodeToNull() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(EMPTY_FILTER)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().countryCode("  ").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), EMPTY_FILTER);
    }

    /// issnL 为空白时应归一化为 null。
    @Test
    @DisplayName("issnL 空白应归一化为 null")
    void shouldNormalizeBlankIssnLToNull() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(EMPTY_FILTER)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().issnL("  \t ").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), EMPTY_FILTER);
    }

    /// nlmId 为空白时应归一化为 null。
    @Test
    @DisplayName("nlmId 空白应归一化为 null")
    void shouldNormalizeBlankNlmIdToNull() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 20);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(EMPTY_FILTER)))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(VenueListQuery.builder().nlmId("   ").build());

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), EMPTY_FILTER);
    }
  }
}
