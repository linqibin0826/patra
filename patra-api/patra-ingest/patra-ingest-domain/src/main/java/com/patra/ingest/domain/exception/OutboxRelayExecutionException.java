package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.EnumSet;
import java.util.Set;

/// Outbox Relay 执行异常。
/// 
/// 触发场景:在 Outbox Relay 管道执行过程中发生非持久化类失败,具体包括:
/// 
/// - 获取待发布消息失败
///   - 获取租约(lease)失败
///   - 发布到消息中间件失败(网络中断、SDK 错误)
///   - 序列化/反序列化错误
/// 
/// 与 {@link OutboxPersistenceException} 的区别:本异常强调外部依赖或发布失败,而 {@link OutboxPersistenceException}
/// 强调数据库更新失败。
/// 
/// 处理策略:
/// 
/// - **可恢复错误**(如临时性网络问题):标记为重试。
///   - **不可恢复错误**(如不支持的格式、目标拒绝):根据策略路由到死信队列。
/// 
/// @author linqibin
/// @since 0.1.0
public class OutboxRelayExecutionException extends IngestException implements HasErrorTraits {

  /// 构造 Outbox Relay 执行异常。
/// 
/// @param message 描述性消息
/// @param cause 底层异常
  public OutboxRelayExecutionException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
  }
}
