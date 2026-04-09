package com.patra.catalog.app.usecase.venue.scopus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.scopus.command.VenueScopusEnrichCommand;
import com.patra.catalog.app.usecase.venue.scopus.command.VenueScopusEnrichResult;
import com.patra.catalog.domain.port.batch.ScopusEnrichmentBatchPort;
import com.patra.common.error.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueScopusEnrichHandler 单元测试。
///
/// **测试策略**：
///
/// - Mock `ScopusEnrichmentBatchPort`，验证 Job 启动和结果返回
/// - 验证异常包装为 `ApplicationException`
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueScopusEnrichHandler 单元测试")
@Timeout(2)
@ExtendWith(MockitoExtension.class)
class VenueScopusEnrichHandlerTest {

  @Mock private ScopusEnrichmentBatchPort scopusEnrichmentBatchPort;

  private VenueScopusEnrichHandler handler;

  @BeforeEach
  void setUp() {
    handler = new VenueScopusEnrichHandler(scopusEnrichmentBatchPort);
  }

  @Test
  @DisplayName("应启动 Scopus 富化 Job 并返回 executionId")
  void shouldLaunchJobAndReturnExecutionId() {
    // Given
    short targetYear = (short) 2025;
    int minCitedByCount = 500;
    var command = new VenueScopusEnrichCommand(targetYear, minCitedByCount);
    when(scopusEnrichmentBatchPort.launchEnrichment(targetYear, minCitedByCount)).thenReturn(99L);

    // When
    VenueScopusEnrichResult result = handler.handle(command);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.executionId()).isEqualTo(99L);
    verify(scopusEnrichmentBatchPort).launchEnrichment(targetYear, minCitedByCount);
  }

  @Test
  @DisplayName("BatchPort 抛出 RuntimeException 时应包装为 ApplicationException")
  void shouldWrapRuntimeExceptionAsApplicationException() {
    // Given
    short targetYear = (short) 2025;
    int minCitedByCount = 0;
    var command = new VenueScopusEnrichCommand(targetYear, minCitedByCount);
    when(scopusEnrichmentBatchPort.launchEnrichment(targetYear, minCitedByCount))
        .thenThrow(new RuntimeException("Job 启动失败"));

    // When & Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("Scopus 期刊富化失败");
  }
}
