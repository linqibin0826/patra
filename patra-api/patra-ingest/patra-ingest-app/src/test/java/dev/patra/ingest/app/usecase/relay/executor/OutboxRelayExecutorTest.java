package dev.patra.ingest.app.usecase.relay.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.ingest.app.usecase.relay.coordinator.RelayLeaseCoordinator;
import dev.patra.ingest.app.usecase.relay.coordinator.RelayLogCoordinator;
import dev.patra.ingest.app.usecase.relay.coordinator.RelayLogCoordinator.LogAccumulator;
import dev.patra.ingest.app.usecase.relay.coordinator.RelayPublishCoordinator;
import dev.patra.ingest.app.usecase.relay.coordinator.RelayPublishCoordinator.RelayResult;
import dev.patra.ingest.domain.model.entity.OutboxMessage;
import dev.patra.ingest.domain.model.vo.relay.RelayPlan;
import dev.patra.ingest.domain.port.OutboxRelayRepository;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// OutboxRelayExecutor 单元测试：中继分三段，领取租约、标记状态各用短事务，发到 MQ 在事务外。
@DisplayName("OutboxRelayExecutor 单元测试")
class OutboxRelayExecutorTest {

  private static final Instant NOW = Instant.parse("2026-10-29T08:00:00Z");

  private final List<String> events = new ArrayList<>();
  private final OutboxRelayRepository relayStore = mock(OutboxRelayRepository.class);
  private final RelayLeaseCoordinator leaseCoordinator = mock(RelayLeaseCoordinator.class);
  private final RelayPublishCoordinator publishCoordinator = mock(RelayPublishCoordinator.class);
  private final RelayLogCoordinator logCoordinator = mock(RelayLogCoordinator.class);
  private final OutboxRelayExecutor executor =
      new OutboxRelayExecutor(
          relayStore,
          leaseCoordinator,
          publishCoordinator,
          logCoordinator,
          new RecordingTransactions(events));

  /// 租约在发布前单独提交，中继日志在事务里批量写入。
  @Test
  @DisplayName("租约在发布前单独提交，中继日志在事务里批量写入")
  void should_commit_lease_before_publishing() {
    RelayPlan plan =
        new RelayPlan(
            null,
            NOW,
            10,
            Duration.ofMinutes(5),
            3,
            Duration.ofSeconds(1),
            2.0,
            Duration.ofMinutes(10),
            "owner");
    OutboxMessage message =
        OutboxMessage.builder()
            .id(1L)
            .version(1L)
            .aggregateType("Task")
            .aggregateId(100L)
            .channel("TASK_READY")
            .opType("TASK_READY")
            .dedupKey("task-001")
            .partitionKey("")
            .payloadJson("{}")
            .statusCode("PENDING")
            .retryCount(0)
            .build();
    when(relayStore.fetchPending(null, NOW, 10)).thenReturn(List.of(message));
    when(logCoordinator.createAccumulator(any())).thenReturn(mock(LogAccumulator.class));
    when(leaseCoordinator.tryAcquire(message, plan))
        .thenAnswer(
            inv -> {
              events.add("lease");
              return true;
            });
    when(publishCoordinator.publish(any(), any()))
        .thenAnswer(
            inv -> {
              events.add("publish");
              return RelayResult.success(1L, 1);
            });
    doAnswer(inv -> events.add("persistLogs")).when(logCoordinator).persistBatch(any());

    executor.execute(plan);

    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN,
            "lease",
            RecordingTransactions.END,
            "publish",
            RecordingTransactions.BEGIN,
            "persistLogs",
            RecordingTransactions.END);
  }
}
