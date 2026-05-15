package com.patra.ingest.app.usecase.execution.command;

import dev.linqibin.commons.cqrs.Command;
import java.util.Map;

/// 任务就绪命令(简化版)
///
/// 在六边形架构+DDD中的角色:应用层命令对象,由适配器层在消费INGEST_TASK_READY MQ消息后组装。
///
/// 设计理念:仅包含任务执行准备所需的核心字段。其他业务数据(来源、操作、参数等)应从数据库查询。
///
/// 字段说明:
///
/// - taskId: 任务ID(必需,用于租约获取和上下文加载)
///   - idempotentKey: 幂等键(必需,用于去重检查)
///   - headers: MQ消息头(用于链路追踪: ROCKET_MQ_MESSAGE_ID, traceId, partitionKey等)
///
/// @param taskId 任务ID
/// @param idempotentKey 幂等键
/// @param headers MQ消息头(用于链路追踪和审计)
/// @author linqibin
/// @since 0.1.0
public record TaskReadyCommand(long taskId, String idempotentKey, Map<String, Object> headers)
    implements Command<Void> {
  public TaskReadyCommand {
    if (idempotentKey == null || idempotentKey.isBlank()) {
      throw new IllegalArgumentException("幂等键不能为空");
    }
  }

  /// 从消息头中提取追踪字段(辅助方法)
  ///
  /// @return RocketMQ消息ID
  public String getMessageId() {
    return resolveHeaderAsString("ROCKET_MQ_MESSAGE_ID");
  }

  /// 获取关联ID
  ///
  /// @return 关联ID
  public String getCorrelationId() {
    return resolveHeaderAsString("correlationId");
  }

  /// 从消息头中解析字符串值
  ///
  /// @param key 消息头键名
  /// @return 字符串值,不存在时返回null
  private String resolveHeaderAsString(String key) {
    if (headers == null) {
      return null;
    }
    Object value = headers.get(key);
    if (value == null) {
      return null;
    }
    // RocketMQ消息头可能是UUID或其他非字符串类型,统一转换为字符串
    return value instanceof String ? (String) value : value.toString();
  }
}
