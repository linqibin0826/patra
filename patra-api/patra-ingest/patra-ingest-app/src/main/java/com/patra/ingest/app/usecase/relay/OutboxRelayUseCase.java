package com.patra.ingest.app.usecase.relay;

import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;

/// Outbox 中继用例契约 (应用层编排入口)
///
/// 职责: 接收由调度器或运维人员触发的 {@link OutboxRelayCommand},执行一轮批量消息发布, 并返回聚合统计指标。
///
/// 语义: 该调用不会执行无限重试;失败或待处理的消息通过领域状态和后续调度周期恢复。
public interface OutboxRelayUseCase {

  /// 执行一轮 Outbox 中继任务
  ///
  /// 实现必须保证:
  ///
  /// - 幂等性: 相同的消息不会被发布两次 (通过租约和状态转换强制实现)
  ///   - 可观测性: 关键计数器会记录到日志
  ///
  /// @param instruction 调度指令,允许覆盖默认配置
  /// @return 包含聚合统计信息的执行报告
  RelayReport relay(OutboxRelayCommand instruction);
}
