package com.patra.catalog.app.usecase.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichCommand;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichResult;
import com.patra.catalog.domain.port.batch.LetPubEnrichmentBatchPort;
import com.patra.common.error.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueLetPubEnrichHandler 单元测试。
///
/// **测试策略**：
///
/// - Mock `LetPubEnrichmentBatchPort`，验证 Job 启动和结果返回
/// - 验证异常包装为 `ApplicationException`
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueLetPubEnrichHandler 单元测试")
@Timeout(2)
@ExtendWith(MockitoExtension.class)
class VenueLetPubEnrichHandlerTest {

  @Mock private LetPubEnrichmentBatchPort letPubEnrichmentBatchPort;

  private VenueLetPubEnrichHandler handler;

  @BeforeEach
  void setUp() {
    handler = new VenueLetPubEnrichHandler(letPubEnrichmentBatchPort);
  }

  @Test
  @DisplayName("应启动 LetPub 富化 Job 并返回 executionId")
  void shouldLaunchJobAndReturnExecutionId() {
    // Given
    var command = new VenueLetPubEnrichCommand();
    when(letPubEnrichmentBatchPort.launchEnrichment()).thenReturn(42L);

    // When
    VenueLetPubEnrichResult result = handler.handle(command);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.executionId()).isEqualTo(42L);
    verify(letPubEnrichmentBatchPort).launchEnrichment();
  }

  @Test
  @DisplayName("BatchPort 抛出 RuntimeException 时应包装为 ApplicationException")
  void shouldWrapRuntimeExceptionAsApplicationException() {
    // Given
    var command = new VenueLetPubEnrichCommand();
    when(letPubEnrichmentBatchPort.launchEnrichment()).thenThrow(new RuntimeException("Job 启动失败"));

    // When & Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("LetPub 期刊富化失败");
  }
}
