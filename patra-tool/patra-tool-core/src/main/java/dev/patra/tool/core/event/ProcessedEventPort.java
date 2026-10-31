package dev.patra.tool.core.event;

import java.util.UUID;

/// 消费端去重：记下某个消费方处理过某个事件。
///
/// 事件是「至少一次」投递，同一个事件可能到达多次。消费方在**业务事务里**先调本方法，返回 `false`
/// 就直接结束；返回 `true` 再写业务数据。记录和业务写入同成同败，业务回滚时记录跟着回滚，重试不会被当成重复。
///
/// 实现由 `patra-tool-events` 注册，需要服务库里有 `processed_event` 表。
public interface ProcessedEventPort {

  /// 记下处理过；必须在已有事务里调用。
  ///
  /// @param consumer 消费方，填队列名
  /// @param eventId 事件 ID（{@link IntegrationEvent#eventId()}）
  /// @return 第一次处理返回 `true`，处理过返回 `false`
  boolean markProcessed(String consumer, UUID eventId);
}
