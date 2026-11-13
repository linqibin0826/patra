package dev.patra.notification.domain.realtime.model.vo;

import java.util.Collection;
import java.util.Set;

/// 一条未读数广播：某个实例告诉其他实例「这些用户的未读数变了」。
///
/// 推送连接只活在建立它的实例里，变化却可能在另一个实例上发生，所以变化发生的实例把用户 ID 广播出去，每个实例
/// 推自己手上的连接。只带用户 ID，不带数字：收到的实例现读数据库，丢一条也不会推错数。
///
/// 这个 record 就是 RabbitMQ 上的消息体（JSON），发送方和接收方共用。
///
/// @param origin 发送实例的 {@link InstanceId} 标识；接收方据此跳过自己发的
/// @param userIds 未读数变了的用户
public record UnreadCountBroadcast(String origin, Set<Long> userIds) {

  /// 防御性拷贝。
  public UnreadCountBroadcast {
    userIds = Set.copyOf(userIds);
  }

  /// 构造。
  ///
  /// @param origin 发送实例
  /// @param userIds 未读数变了的用户
  /// @return 广播
  public static UnreadCountBroadcast of(InstanceId origin, Collection<Long> userIds) {
    return new UnreadCountBroadcast(origin.value(), Set.copyOf(userIds));
  }

  /// 是不是这个实例发的。
  ///
  /// @param instance 实例
  /// @return 发送实例就是它时为 `true`
  public boolean sentBy(InstanceId instance) {
    return origin.equals(instance.value());
  }
}
