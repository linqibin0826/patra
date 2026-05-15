package dev.linqibin.patra.ingest.app.usecase.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.linqibin.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import dev.linqibin.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import dev.linqibin.patra.ingest.app.usecase.relay.dto.RelayReport;
import dev.linqibin.patra.ingest.app.usecase.relay.executor.OutboxRelayExecutor;
import dev.linqibin.patra.ingest.app.usecase.relay.planner.RelayPlanBuilder;
import dev.linqibin.patra.ingest.app.usecase.relay.publisher.RelayEventPublisher;
import dev.linqibin.patra.ingest.domain.event.OutboxRelayDomainEvent;
import dev.linqibin.patra.ingest.domain.model.vo.relay.RelayBatchResult;
import dev.linqibin.patra.ingest.domain.model.vo.relay.RelayPlan;
import dev.linqibin.commons.messaging.ChannelKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// OutboxRelayHandler 单元测试
///
/// 测试覆盖:
///
/// - ✅ 功能开关禁用场景
/// - ✅ 正常中继流程 (规划 → 执行 → 发布事件)
/// - ✅ 调用顺序验证 (planBuilder → executor → eventPublisher)
/// - ✅ 单通道和全通道场景
/// - ✅ 空结果和批量结果场景
/// - ✅ 报告生成验证
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayHandler 单元测试")
class OutboxRelayHandlerTest {

  @Mock private OutboxRelayProperties properties;

  @Mock private RelayPlanBuilder planBuilder;

  @Mock private OutboxRelayExecutor relayExecutor;

  @Mock private RelayEventPublisher eventPublisher;

  @InjectMocks private OutboxRelayHandler handler;

  private OutboxRelayCommand testCommand;
  private RelayPlan testPlan;

  @BeforeEach
  void setUp() {
    testCommand = new OutboxRelayCommand(null, null, null, null, null, null, null);
    testPlan =
        new RelayPlan(
            null,
            Instant.now(),
            100,
            Duration.ofMinutes(5),
            3,
            Duration.ofSeconds(1),
            2.0,
            Duration.ofMinutes(10),
            "test-owner");
  }

  // 辅助方法：创建空的 RelayBatchResult
  private RelayBatchResult createBatchResult(
      ChannelKey channel, int fetched, int published, int retried, int failed, int leaseMissed) {
    return new RelayBatchResult(
        channel, fetched, published, retried, failed, leaseMissed, Collections.emptyList());
  }

  @Nested
  @DisplayName("功能开关控制场景")
  class FeatureToggleTests {

    @Test
    @DisplayName("功能禁用时应返回空报告且不执行中继逻辑")
    void shouldReturnEmptyReportWhenFeatureDisabled() {
      // Given: 功能开关关闭
      when(properties.isEnabled()).thenReturn(false);

      // When: 执行中继
      RelayReport report = handler.handle(testCommand);

      // Then: 返回空报告
      assertThat(report).isNotNull();
      assertThat(report.channel()).isNull();
      assertThat(report.fetched()).isZero();
      assertThat(report.published()).isZero();
      assertThat(report.retried()).isZero();
      assertThat(report.failed()).isZero();
      assertThat(report.leaseMissed()).isZero();

      // 验证没有执行中继逻辑
      verify(planBuilder, never()).build(any());
      verify(relayExecutor, never()).execute(any());
      verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("功能禁用时针对特定通道也应返回空报告")
    void shouldReturnEmptyReportForSpecificChannelWhenDisabled() {
      // Given: 功能开关关闭,创建 Mock ChannelKey
      ChannelKey channel = mock(ChannelKey.class);
      when(channel.channel()).thenReturn("TASK_READY");
      OutboxRelayCommand command =
          new OutboxRelayCommand(channel, null, null, null, null, null, null);
      when(properties.isEnabled()).thenReturn(false);

      // When
      RelayReport report = handler.handle(command);

      // Then: 返回带通道的空报告
      assertThat(report.channel()).isEqualTo(channel);
      assertThat(report.fetched()).isZero();

      // 验证没有执行中继逻辑
      verifyNoInteractions(planBuilder, relayExecutor, eventPublisher);
    }
  }

  @Nested
  @DisplayName("正常中继流程场景")
  class NormalRelayFlowTests {

    @Test
    @DisplayName("应按顺序执行: 构建计划 → 执行中继 → 发布事件")
    void shouldExecuteRelayInCorrectOrder() {
      // Given
      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(testCommand)).thenReturn(testPlan);

      RelayBatchResult batchResult = createBatchResult(null, 10, 8, 1, 1, 0);
      when(relayExecutor.execute(testPlan)).thenReturn(batchResult);

      // When
      handler.handle(testCommand);

      // Then: 验证调用顺序
      InOrder inOrder = inOrder(planBuilder, relayExecutor, eventPublisher);
      inOrder.verify(planBuilder).build(testCommand);
      inOrder.verify(relayExecutor).execute(testPlan);
      inOrder.verify(eventPublisher).publish(Collections.emptyList());
    }

    @Test
    @DisplayName("应正确生成包含所有指标的中继报告")
    void shouldGenerateCompleteRelayReport() {
      // Given
      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(testCommand)).thenReturn(testPlan);

      ChannelKey channel = mock(ChannelKey.class);
      when(channel.channel()).thenReturn("TASK_READY");
      RelayBatchResult batchResult = createBatchResult(channel, 15, 12, 2, 1, 0);
      when(relayExecutor.execute(testPlan)).thenReturn(batchResult);

      // When
      RelayReport report = handler.handle(testCommand);

      // Then: 验证报告包含所有指标
      assertThat(report.channel()).isEqualTo(channel);
      assertThat(report.fetched()).isEqualTo(15);
      assertThat(report.published()).isEqualTo(12);
      assertThat(report.retried()).isEqualTo(2);
      assertThat(report.failed()).isEqualTo(1);
      assertThat(report.leaseMissed()).isEqualTo(0);
    }

    @Test
    @DisplayName("全通道模式应正确处理并发布事件")
    void shouldHandleAllChannelsMode() {
      // Given: 全通道模式 (channel = null)
      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(testCommand)).thenReturn(testPlan);

      RelayBatchResult batchResult = createBatchResult(null, 20, 18, 1, 1, 0);
      when(relayExecutor.execute(testPlan)).thenReturn(batchResult);

      // When
      RelayReport report = handler.handle(testCommand);

      // Then
      assertThat(report.channel()).isNull();
      assertThat(report.fetched()).isEqualTo(20);
      verify(eventPublisher).publish(Collections.emptyList());
    }

    @Test
    @DisplayName("单通道模式应正确处理并发布事件")
    void shouldHandleSingleChannelMode() {
      // Given: 单通道模式
      ChannelKey channel = mock(ChannelKey.class);
      when(channel.channel()).thenReturn("PUBLICATION_READY");
      OutboxRelayCommand command =
          new OutboxRelayCommand(channel, null, null, null, null, null, null);

      RelayPlan channelPlan =
          new RelayPlan(
              channel,
              Instant.now(),
              50,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "test-owner");

      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(command)).thenReturn(channelPlan);

      RelayBatchResult batchResult = createBatchResult(channel, 5, 5, 0, 0, 0);
      when(relayExecutor.execute(channelPlan)).thenReturn(batchResult);

      // When
      RelayReport report = handler.handle(command);

      // Then
      assertThat(report.channel()).isEqualTo(channel);
      assertThat(report.fetched()).isEqualTo(5);
      assertThat(report.published()).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("边界条件场景")
  class BoundaryConditionTests {

    @Test
    @DisplayName("空批次应返回零指标的报告")
    void shouldHandleEmptyBatch() {
      // Given: 执行器返回空结果
      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(testCommand)).thenReturn(testPlan);

      RelayBatchResult emptyResult = createBatchResult(null, 0, 0, 0, 0, 0);
      when(relayExecutor.execute(testPlan)).thenReturn(emptyResult);

      // When
      RelayReport report = handler.handle(testCommand);

      // Then: 所有指标为零
      assertThat(report.fetched()).isZero();
      assertThat(report.published()).isZero();
      assertThat(report.retried()).isZero();
      assertThat(report.failed()).isZero();
      assertThat(report.leaseMissed()).isZero();

      // 仍然应该发布事件 (即使是空列表)
      verify(eventPublisher).publish(Collections.emptyList());
    }

    @Test
    @DisplayName("所有消息租约丢失应正确记录")
    void shouldRecordAllLeaseMissed() {
      // Given: 所有消息租约丢失
      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(testCommand)).thenReturn(testPlan);

      RelayBatchResult result = createBatchResult(null, 10, 0, 0, 0, 10);
      when(relayExecutor.execute(testPlan)).thenReturn(result);

      // When
      RelayReport report = handler.handle(testCommand);

      // Then
      assertThat(report.fetched()).isEqualTo(10);
      assertThat(report.leaseMissed()).isEqualTo(10);
      assertThat(report.published()).isZero();
    }

    @Test
    @DisplayName("所有消息失败应正确记录")
    void shouldRecordAllFailed() {
      // Given: 所有消息失败
      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(testCommand)).thenReturn(testPlan);

      RelayBatchResult result = createBatchResult(null, 5, 0, 0, 5, 0);
      when(relayExecutor.execute(testPlan)).thenReturn(result);

      // When
      RelayReport report = handler.handle(testCommand);

      // Then
      assertThat(report.fetched()).isEqualTo(5);
      assertThat(report.failed()).isEqualTo(5);
      assertThat(report.published()).isZero();
    }

    @Test
    @DisplayName("混合结果应正确统计所有指标")
    void shouldHandleMixedResults() {
      // Given: 混合结果 (成功 + 重试 + 失败 + 租约丢失)
      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(testCommand)).thenReturn(testPlan);

      ChannelKey channel = mock(ChannelKey.class);
      when(channel.channel()).thenReturn("TASK_READY");
      RelayBatchResult result = createBatchResult(channel, 100, 80, 10, 5, 5);
      when(relayExecutor.execute(testPlan)).thenReturn(result);

      // When
      RelayReport report = handler.handle(testCommand);

      // Then: 验证指标加总正确
      assertThat(report.fetched()).isEqualTo(100);
      assertThat(report.published()).isEqualTo(80);
      assertThat(report.retried()).isEqualTo(10);
      assertThat(report.failed()).isEqualTo(5);
      assertThat(report.leaseMissed()).isEqualTo(5);
      // 验证: published + retried + failed + leaseMissed = fetched
      assertThat(report.published() + report.retried() + report.failed() + report.leaseMissed())
          .isEqualTo(report.fetched());
    }
  }

  @Nested
  @DisplayName("事件发布场景")
  class EventPublishingTests {

    @Test
    @DisplayName("应发布所有领域事件")
    void shouldPublishAllDomainEvents() {
      // Given
      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(testCommand)).thenReturn(testPlan);

      // 模拟有领域事件的结果 (具体事件对象由执行器生成)
      OutboxRelayDomainEvent mockEvent = mock(OutboxRelayDomainEvent.class);
      RelayBatchResult result =
          new RelayBatchResult(null, 10, 8, 1, 1, 0, Collections.singletonList(mockEvent));
      when(relayExecutor.execute(testPlan)).thenReturn(result);

      // When
      handler.handle(testCommand);

      // Then: 验证事件发布器被调用
      verify(eventPublisher).publish(Collections.singletonList(mockEvent));
    }

    @Test
    @DisplayName("即使没有事件也应调用事件发布器")
    void shouldCallEventPublisherEvenWithNoEvents() {
      // Given
      when(properties.isEnabled()).thenReturn(true);
      when(planBuilder.build(testCommand)).thenReturn(testPlan);

      RelayBatchResult result = createBatchResult(null, 5, 5, 0, 0, 0);
      when(relayExecutor.execute(testPlan)).thenReturn(result);

      // When
      handler.handle(testCommand);

      // Then: 仍然调用,但传入空列表
      verify(eventPublisher).publish(Collections.emptyList());
    }
  }
}
