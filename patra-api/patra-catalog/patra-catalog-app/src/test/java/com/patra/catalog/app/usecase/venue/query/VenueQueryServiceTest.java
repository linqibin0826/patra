package com.patra.catalog.app.usecase.venue.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.port.read.VenueReadPort;
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

  @Mock private VenueReadPort venueReadPort;

  /// 默认参数场景应使用 page=1、pageSize=20，且 q 为空时传 null。
  @Test
  @DisplayName("默认参数应归一化为 page=1, pageSize=20")
  void shouldNormalizeDefaults() {
    // Given
    VenueQueryService service = new VenueQueryService(venueReadPort);
    PageResult<VenueSummaryReadModel> expected =
        PageResult.of(
            List.of(
                new VenueSummaryReadModel(
                    1L,
                    "Nature",
                    "0028-0836",
                    "0410462",
                    "OPENALEX",
                    "US",
                    Instant.parse("2026-02-13T00:00:00Z"))),
            1,
            20,
            1);
    when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 20)), eq(null))).thenReturn(expected);

    // When
    PageResult<VenueSummaryReadModel> actual =
        service.listVenues(new VenueListQuery(null, null, "   "));

    // Then
    assertThat(actual).isEqualTo(expected);
    verify(venueReadPort).findVenuePage(PagingParams.of(1, 20), null);
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
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(1, 100);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(1, 100)), eq("Nature")))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(new VenueListQuery(0, 1000, "  Nature  "));

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(1, 100), "Nature");
    }

    /// 正常分页参数应保持原值传递。
    @Test
    @DisplayName("合法分页参数应原样传递")
    void shouldKeepValidPageAndPageSize() {
      // Given
      VenueQueryService service = new VenueQueryService(venueReadPort);
      PageResult<VenueSummaryReadModel> expected = PageResult.empty(3, 50);
      when(venueReadPort.findVenuePage(eq(PagingParams.of(3, 50)), eq("NLM001")))
          .thenReturn(expected);

      // When
      PageResult<VenueSummaryReadModel> actual =
          service.listVenues(new VenueListQuery(3, 50, "NLM001"));

      // Then
      assertThat(actual).isEqualTo(expected);
      verify(venueReadPort).findVenuePage(PagingParams.of(3, 50), "NLM001");
    }
  }
}
