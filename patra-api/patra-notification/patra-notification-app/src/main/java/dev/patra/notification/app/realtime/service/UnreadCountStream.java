package dev.patra.notification.app.realtime.service;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;

/// 一条已打开的推送连接（PAP-145）。连接表只认这个接口，不认识 SSE；实现在 adapter（`SseEmitter`）。
///
/// 发送失败（浏览器已走、写不进去）抛 `RuntimeException`，连接表据此剔除这条连接。
public interface UnreadCountStream {

  /// 推一次三个未读数。
  ///
  /// @param counts 未读数
  void sendCounts(UnreadCounts counts);

  /// 推一次心跳：让中间各层代理看到「有数据在流」。
  void sendHeartbeat();

  /// 服务端结束这条连接（正常结束，浏览器随后重连）。
  void close();
}
