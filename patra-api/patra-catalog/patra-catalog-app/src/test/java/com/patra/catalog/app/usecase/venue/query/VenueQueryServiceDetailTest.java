package com.patra.catalog.app.usecase.venue.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.query.dto.VenueDetailQuery;
import com.patra.catalog.domain.exception.VenueNotFoundException;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.port.read.VenueReadPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueQueryService 详情查询单元测试。
///
/// **测试目标**：
///
/// - 正常查询：传入有效 ID，返回 VenueDetailReadModel
/// - 不存在：传入无效 ID，抛出 VenueNotFoundException
/// - 空查询：query 为 null 时抛出 NullPointerException
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueQueryService 详情查询单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueQueryServiceDetailTest {

  @Mock private VenueReadPort venueReadPort;

  /// 传入有效 ID 应返回 VenueDetailReadModel。
  @Test
  @DisplayName("传入有效 ID 应返回详情")
  void shouldReturnDetailWhenIdExists() {
    // Given
    VenueQueryService service = new VenueQueryService(venueReadPort);
    Long validId = 1L;
    VenueDetailReadModel expected =
        VenueDetailReadModel.builder()
            .id(validId)
            .venueType("JOURNAL")
            .title("Nature")
            .issnL("0028-0836")
            .nlmId("0410462")
            .openalexId("S12345")
            .abbreviatedTitle("Nature")
            .primaryLanguage("eng")
            .countryCode("US")
            .affiliatedSocieties(List.of())
            .lastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .createdAt(Instant.parse("2026-02-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .build();
    when(venueReadPort.findVenueDetail(eq(validId))).thenReturn(Optional.of(expected));

    // When
    VenueDetailReadModel actual = service.getVenueDetail(VenueDetailQuery.of(validId));

    // Then
    assertThat(actual).isEqualTo(expected);
    verify(venueReadPort).findVenueDetail(validId);
  }

  /// 传入不存在的 ID 应抛出 VenueNotFoundException。
  @Test
  @DisplayName("传入不存在的 ID 应抛出 VenueNotFoundException")
  void shouldThrowVenueNotFoundExceptionWhenIdNotExists() {
    // Given
    VenueQueryService service = new VenueQueryService(venueReadPort);
    Long invalidId = 999L;
    when(venueReadPort.findVenueDetail(eq(invalidId))).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> service.getVenueDetail(VenueDetailQuery.of(invalidId)))
        .isInstanceOf(VenueNotFoundException.class)
        .hasMessageContaining("Venue not found with id: 999");
    verify(venueReadPort).findVenueDetail(invalidId);
  }

  /// query 为 null 时应抛出 NullPointerException。
  @Test
  @DisplayName("query 为 null 应抛出 NPE")
  void shouldThrowNpeWhenQueryIsNull() {
    // Given
    VenueQueryService service = new VenueQueryService(venueReadPort);

    // When & Then
    assertThatNullPointerException()
        .isThrownBy(() -> service.getVenueDetail(null))
        .withMessageContaining("query");
  }
}
