package dev.linqibin.patra.catalog.app.usecase.venue.scopus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import dev.linqibin.patra.catalog.app.usecase.venue.VenueEnrichRunStats;
import dev.linqibin.patra.catalog.app.usecase.venue.scopus.command.VenueScopusEnrichCommand;
import java.util.concurrent.TimeUnit;
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
/// - Mock [ScopusEnrichmentRunner]，验证 Handler 透传 [VenueEnrichRunStats]
/// - 验证 [DomainException] 直接传播
/// - 验证未知 [RuntimeException] 被包装成 [ApplicationException]
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueScopusEnrichHandler 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@ExtendWith(MockitoExtension.class)
class VenueScopusEnrichHandlerTest {

  @Mock private ScopusEnrichmentRunner runner;

  private VenueScopusEnrichHandler handler;

  @BeforeEach
  void setUp() {
    handler = new VenueScopusEnrichHandler(runner);
  }

  @Test
  @DisplayName("正常调用 - Runner 的 stats 被透传")
  void shouldReturnStatsFromRunner() {
    short targetYear = (short) 2025;
    int minCitedByCount = 1000;
    var command = new VenueScopusEnrichCommand(targetYear, minCitedByCount);
    when(runner.run(targetYear, minCitedByCount)).thenReturn(VenueEnrichRunStats.of(50, 45, 3, 2));

    VenueEnrichRunStats result = handler.handle(command);

    assertThat(result.totalRead()).isEqualTo(50);
    assertThat(result.processed()).isEqualTo(45);
    assertThat(result.skipped()).isEqualTo(3);
    assertThat(result.failed()).isEqualTo(2);
    verify(runner).run(targetYear, minCitedByCount);
  }

  @Test
  @DisplayName("DomainException 直接传播，不被包装")
  void shouldPropagateDomainException() {
    short targetYear = (short) 2025;
    int minCitedByCount = 100;
    var command = new VenueScopusEnrichCommand(targetYear, minCitedByCount);
    when(runner.run(targetYear, minCitedByCount))
        .thenThrow(
            new DomainException("domain rule violated", StandardErrorTrait.RULE_VIOLATION) {});

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(DomainException.class)
        .hasMessage("domain rule violated");
  }

  @Test
  @DisplayName("未知 RuntimeException 被包装为 ApplicationException")
  void shouldWrapRuntimeExceptionAsApplicationException() {
    short targetYear = (short) 2025;
    int minCitedByCount = 100;
    var command = new VenueScopusEnrichCommand(targetYear, minCitedByCount);
    when(runner.run(targetYear, minCitedByCount)).thenThrow(new RuntimeException("Runner crashed"));

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ApplicationException.class)
        .hasMessageContaining("Scopus 期刊富化失败");
  }
}
