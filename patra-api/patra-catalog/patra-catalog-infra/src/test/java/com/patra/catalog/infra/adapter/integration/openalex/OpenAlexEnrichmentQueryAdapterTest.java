package com.patra.catalog.infra.adapter.integration.openalex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.VenueOpenAlexEnrichment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// OpenAlexEnrichmentQueryAdapter 单元测试。
///
/// **测试策略**：
///
/// - 空集短路：null 和空集合直接返回空 Map，不调用 Client
/// - 正常委托：验证请求和响应正确传递
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("OpenAlexEnrichmentQueryAdapter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class OpenAlexEnrichmentQueryAdapterTest {

  @Mock private OpenAlexSourcesClient openAlexSourcesClient;
  @InjectMocks private OpenAlexEnrichmentQueryAdapter adapter;

  @Test
  @DisplayName("null 输入应返回空 Map，不调用 Client")
  void shouldReturnEmptyMapForNullInput() {
    // When
    Map<String, VenueOpenAlexEnrichment> result = adapter.findEnrichmentData(null);

    // Then
    assertThat(result).isEmpty();
    verify(openAlexSourcesClient, never()).queryEnrichmentData(any());
  }

  @Test
  @DisplayName("空集合应返回空 Map，不调用 Client")
  void shouldReturnEmptyMapForEmptySet() {
    // When
    Map<String, VenueOpenAlexEnrichment> result = adapter.findEnrichmentData(Set.of());

    // Then
    assertThat(result).isEmpty();
    verify(openAlexSourcesClient, never()).queryEnrichmentData(any());
  }

  @Test
  @DisplayName("正常输入应委托给 Client 并返回结果")
  void shouldDelegateToClientAndReturnResults() {
    // Given
    Set<String> issnLs = Set.of("0028-0836");
    VenueOpenAlexEnrichment enrichment =
        VenueOpenAlexEnrichment.of(
            "S137773608",
            CitationMetrics.of(150000, 2500000, 285, 1200, new BigDecimal("3.45")),
            List.of(),
            null);
    when(openAlexSourcesClient.queryEnrichmentData(issnLs))
        .thenReturn(Map.of("0028-0836", enrichment));

    // When
    Map<String, VenueOpenAlexEnrichment> result = adapter.findEnrichmentData(issnLs);

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get("0028-0836").openAlexId()).isEqualTo("S137773608");
    verify(openAlexSourcesClient).queryEnrichmentData(issnLs);
  }
}
