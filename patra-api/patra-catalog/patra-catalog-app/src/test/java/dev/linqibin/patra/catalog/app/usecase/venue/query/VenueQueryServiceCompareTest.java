package dev.linqibin.patra.catalog.app.usecase.venue.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.catalog.app.usecase.venue.query.dto.VenueCompareQuery;
import dev.linqibin.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import dev.linqibin.patra.catalog.domain.port.read.VenueReadPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueQueryService 期刊对比查询单元测试。
///
/// **测试目标**：
///
/// - 正常查询：传入 2~5 个有效 ID，返回对应的 VenueDetailReadModel 列表
/// - 空查询：query 为 null 时抛出 NullPointerException
/// - 委托验证：正确委托给 VenueReadPort.findVenuesForCompare
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueQueryService 期刊对比查询单元测试")
class VenueQueryServiceCompareTest {

  @Mock private VenueReadPort venueReadPort;

  /// 传入有效 ID 列表应返回对应的详情列表。
  @Test
  @DisplayName("传入有效 ID 列表应返回详情列表")
  void shouldReturnDetailListWhenIdsProvided() {
    // Given
    VenueQueryService service = new VenueQueryService(venueReadPort);
    List<Long> ids = List.of(1L, 2L, 3L);
    var now = Instant.now();

    var venue1 = buildDetailReadModel(1L, "Nature", now);
    var venue2 = buildDetailReadModel(2L, "Science", now);
    var venue3 = buildDetailReadModel(3L, "Cell", now);
    List<VenueDetailReadModel> expected = List.of(venue1, venue2, venue3);

    when(venueReadPort.findVenuesForCompare(eq(ids))).thenReturn(expected);

    // When
    List<VenueDetailReadModel> actual = service.compareVenues(VenueCompareQuery.of(ids));

    // Then
    assertThat(actual).hasSize(3);
    assertThat(actual).isEqualTo(expected);
    verify(venueReadPort).findVenuesForCompare(ids);
  }

  /// query 为 null 时应抛出 NullPointerException。
  @Test
  @DisplayName("query 为 null 应抛出 NPE")
  void shouldThrowNpeWhenQueryIsNull() {
    // Given
    VenueQueryService service = new VenueQueryService(venueReadPort);

    // When & Then
    assertThatNullPointerException()
        .isThrownBy(() -> service.compareVenues(null))
        .withMessageContaining("query");
  }

  /// 构建测试用 VenueDetailReadModel。
  private VenueDetailReadModel buildDetailReadModel(Long id, String title, Instant now) {
    return VenueDetailReadModel.builder()
        .id(id)
        .venueType("JOURNAL")
        .title(title)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
