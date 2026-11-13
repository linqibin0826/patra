package dev.patra.notification.domain.realtime.port.broadcast;

import java.util.Set;

/// 把「这些用户的未读数变了」告诉通知服务的其他实例（#406）。
///
/// 推送连接只活在建立它的实例里；变化发生的实例自己直推本机连接，再经这里广播，别的实例收到后推它们手上的连接。
///
/// 契约：尽力而为。调用不阻塞、不抛异常；发不出去就丢掉，下一次变化或下一次连上会用最新值补齐。
public interface UnreadCountBroadcastPort {

  /// 广播一次。
  ///
  /// @param userIds 未读数变了的用户；空集合不广播
  void broadcast(Set<Long> userIds);
}
