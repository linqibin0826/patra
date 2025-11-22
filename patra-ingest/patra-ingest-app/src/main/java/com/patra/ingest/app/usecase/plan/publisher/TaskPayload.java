package com.patra.ingest.app.usecase.plan.publisher;

import com.patra.ingest.domain.outbox.OutboxPayload;
import java.util.Objects;

/// Task Outbox 消息载荷(简化版)
///
/// 仅包含下游消费者所需的基本数据。所有其他业务数据(溯源、操作、参数等)应使用 taskId 从数据库查询。
///
/// @param taskId 任务标识符(上下文加载和租约获取所需)
/// @param idempotentKey 幂等键(去重和幂等性检查所需)
/// @author linqibin
/// @since 0.1.0
public record TaskPayload(Long taskId, String idempotentKey) implements OutboxPayload {

  /// 紧凑构造函数,带验证
  ///
  /// @param taskId 任务 ID(不能为 null)
  /// @param idempotentKey 幂等键(不能为 null)
  public TaskPayload {
    Objects.requireNonNull(taskId, "taskId 不能为 null");
    Objects.requireNonNull(idempotentKey, "idempotentKey 不能为 null");
  }
}
