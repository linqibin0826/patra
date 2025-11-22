package com.patra.ingest.app.usecase.relay;

import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;
import com.patra.ingest.app.usecase.relay.executor.OutboxRelayExecutor;
import com.patra.ingest.app.usecase.relay.planner.RelayPlanBuilder;
import com.patra.ingest.app.usecase.relay.publisher.RelayEventPublisher;
import com.patra.ingest.domain.model.vo.relay.RelayBatchResult;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Outbox 中继用例的应用服务: 提供单批次发布的编排入口
/// 
/// 执行流程:
/// 
/// 事务语义: 方法使用 `@Transactional` 注解 (单数据库写入原子性), 执行器在相同边界内更新消息状态。
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayOrchestrator implements OutboxRelayUseCase {

  private final OutboxRelayProperties properties;
  private final RelayPlanBuilder planBuilder;
  private final OutboxRelayExecutor relayExecutor;
  private final RelayEventPublisher eventPublisher;

  /// 执行一轮中继任务。当功能开关关闭时,返回空报告以保持调度器健康。
/// 
/// 支持针对特定通道或在 `instruction.channel()` 为 `null` 时处理所有通道。
/// 
/// @param instruction 指令负载,允许可选的覆盖配置
/// @return 执行报告
  @Override
  @Transactional
  public RelayReport relay(OutboxRelayCommand instruction) {
    if (!properties.isEnabled()) {
      var channelKey = instruction.channel() != null ? instruction.channel() : null; // null 表示所有通道
      String channelDesc = channelKey != null ? channelKey.channel() : "ALL_CHANNELS";
      log.info("Outbox 中继已禁用,跳过通道={}", channelDesc);
      return RelayReport.empty(channelKey);
    }
    // 记录开始时间戳以计算延迟指标
    long start = System.currentTimeMillis();

    RelayPlan plan = planBuilder.build(instruction);
    if (log.isDebugEnabled()) {
      String channelDesc = plan.channel() != null ? plan.channel().channel() : "ALL_CHANNELS";
      log.debug(
          "中继计划已构建 通道={} 批大小={} 租约持有者={} 租约过期时间={}",
          channelDesc,
          plan.batchSize(),
          plan.leaseOwner(),
          plan.leaseExpireAt());
    }

    RelayBatchResult result = relayExecutor.execute(plan);
    eventPublisher.publish(result.events());

    long elapsed = System.currentTimeMillis() - start;
    String channelDesc = result.channel() != null ? result.channel().channel() : "ALL_CHANNELS";
    log.info(
        "中继完成 通道={} 获取={} 已发布={} 已重试={} 失败={} 租约丢失={} 耗时毫秒={}",
        channelDesc,
        result.fetched(),
        result.published(),
        result.retried(),
        result.failed(),
        result.leaseMissed(),
        elapsed);
    return new RelayReport(
        result.channel(),
        result.fetched(),
        result.published(),
        result.retried(),
        result.failed(),
        result.leaseMissed());
  }
}
