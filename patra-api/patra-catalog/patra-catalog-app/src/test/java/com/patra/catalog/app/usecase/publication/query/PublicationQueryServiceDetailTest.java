package com.patra.catalog.app.usecase.publication.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.publication.query.dto.PublicationDetailQuery;
import com.patra.catalog.domain.exception.PublicationNotFoundException;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel;
import com.patra.catalog.domain.port.read.PublicationReadPort;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// PublicationQueryService 详情查询单元测试。
///
/// **测试目标**：
///
/// - 正常查询：传入有效 ID，返回 PublicationDetailReadModel
/// - 不存在：传入无效 ID，抛出 PublicationNotFoundException
/// - 空查询：query 为 null 时抛出 NullPointerException
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("PublicationQueryService 详情查询单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class PublicationQueryServiceDetailTest {

  @Mock private PublicationReadPort publicationReadPort;

  /// 传入有效 ID 应返回 PublicationDetailReadModel。
  @Test
  @DisplayName("传入有效 ID 应返回详情")
  void shouldReturnDetailWhenIdExists() {
    // Given
    PublicationQueryService service = new PublicationQueryService(publicationReadPort);
    Long validId = 1L;
    PublicationDetailReadModel expected =
        PublicationDetailReadModel.builder()
            .id(validId)
            .provenanceCode("PUBMED")
            .title("Test Article")
            .pmid("12345678")
            .doi("10.1234/test")
            .publicationYear(2024)
            .languageCode("en")
            .isOa(true)
            .oaStatus("gold")
            .venueId(100L)
            .venueName("Nature")
            .citationCount(42)
            .lastSyncedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .createdAt(Instant.parse("2026-02-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-02-13T00:00:00Z"))
            .build();
    when(publicationReadPort.findPublicationDetail(eq(validId))).thenReturn(Optional.of(expected));

    // When
    PublicationDetailReadModel actual =
        service.getPublicationDetail(PublicationDetailQuery.of(validId));

    // Then
    assertThat(actual).isEqualTo(expected);
    verify(publicationReadPort).findPublicationDetail(validId);
  }

  /// 传入不存在的 ID 应抛出 PublicationNotFoundException。
  @Test
  @DisplayName("传入不存在的 ID 应抛出 PublicationNotFoundException")
  void shouldThrowPublicationNotFoundExceptionWhenIdNotExists() {
    // Given
    PublicationQueryService service = new PublicationQueryService(publicationReadPort);
    Long invalidId = 999L;
    when(publicationReadPort.findPublicationDetail(eq(invalidId))).thenReturn(Optional.empty());

    // When & Then
    assertThatThrownBy(() -> service.getPublicationDetail(PublicationDetailQuery.of(invalidId)))
        .isInstanceOf(PublicationNotFoundException.class)
        .hasMessageContaining("Publication not found with id: 999");
    verify(publicationReadPort).findPublicationDetail(invalidId);
  }

  /// query 为 null 时应抛出 NullPointerException。
  @Test
  @DisplayName("query 为 null 应抛出 NPE")
  void shouldThrowNpeWhenQueryIsNull() {
    // Given
    PublicationQueryService service = new PublicationQueryService(publicationReadPort);

    // When & Then
    assertThatNullPointerException()
        .isThrownBy(() -> service.getPublicationDetail(null))
        .withMessageContaining("query");
  }
}
