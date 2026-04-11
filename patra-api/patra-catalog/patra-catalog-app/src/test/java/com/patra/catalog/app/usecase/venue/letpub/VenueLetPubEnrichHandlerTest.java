package com.patra.catalog.app.usecase.venue.letpub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.app.usecase.venue.letpub.LetPubEnrichmentRunner.RunStats;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichCommand;
import com.patra.catalog.app.usecase.venue.letpub.command.VenueLetPubEnrichResult;
import com.patra.common.error.ApplicationException;
import com.patra.common.error.DomainException;
import com.patra.common.error.trait.StandardErrorTrait;
import java.util.concurrent.TimeUnit;
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
/// - Mock [LetPubEnrichmentRunner]，验证 Handler 把 RunStats 转成 Result 返回
/// - 验证 [DomainException] 直接传播
/// - 验证未知 [RuntimeException] 被包装成 [ApplicationException]
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueLetPubEnrichHandler 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class VenueLetPubEnrichHandlerTest {

  @Mock private LetPubEnrichmentRunner runner;

  private VenueLetPubEnrichHandler handler;

  @BeforeEach
  void setUp() {
    handler = new VenueLetPubEnrichHandler(runner);
  }

  @Test
  @DisplayName("正常调用 - Runner 返回 stats 被转成 Result")
  void shouldReturnResultFromRunnerStats() {
    short targetYear = (short) 2025;
    int minCitedByCount = 1000;
    var command = new VenueLetPubEnrichCommand(targetYear, minCitedByCount);
    when(runner.run(targetYear, minCitedByCount)).thenReturn(RunStats.of(100, 90, 5, 5));

    VenueLetPubEnrichResult result = handler.handle(command);

    assertThat(result.totalRead()).isEqualTo(100);
    assertThat(result.processed()).isEqualTo(90);
    assertThat(result.skipped()).isEqualTo(5);
    assertThat(result.failed()).isEqualTo(5);
    verify(runner).run(targetYear, minCitedByCount);
  }

  @Test
  @DisplayName("DomainException 直接传播，不被包装")
  void shouldPropagateDomainException() {
    short targetYear = (short) 2025;
    int minCitedByCount = 0;
    var command = new VenueLetPubEnrichCommand(targetYear, minCitedByCount);
    when(runner.run(targetYear, minCitedByCount))
        .thenThrow(
            new DomainException("domain rule violated", StandardErrorTrait.RULE_VIOLATION) {});

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(DomainException.class)
        .hasMessage("domain rule violated");
  }

  @Test
  @DisplayName("未知 RuntimeException 被包装为 ApplicationException(CAT_1302)")
  void shouldWrapRuntimeExceptionAsApplicationException() {
    short targetYear = (short) 2025;
    int minCitedByCount = 0;
    var command = new VenueLetPubEnrichCommand(targetYear, minCitedByCount);
    when(runner.run(targetYear, minCitedByCount)).thenThrow(new RuntimeException("Runner crashed"));

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("LetPub 期刊富化失败");
  }
}
