package dev.patra.tool.core.event;

import java.util.UUID;

/// 跨服务的集成事件：发布方 `-api` 模块里对外公布的事件 record 实现它。
///
/// `eventId` 必须在领域事件创建时生成、映射时原样带过来，不能在映射里新建：
/// 事件发布登记表存的是领域事件，补投时会重新映射一遍，ID 变了消费端就去不了重。
/// 外送时它被写进 AMQP `messageId`，消费端用它去重（见 `ProcessedEventPort`）。
public interface IntegrationEvent {

  /// 事件 ID，同一个事件的每次投递都相同。
  ///
  /// @return 事件 ID
  UUID eventId();
}
