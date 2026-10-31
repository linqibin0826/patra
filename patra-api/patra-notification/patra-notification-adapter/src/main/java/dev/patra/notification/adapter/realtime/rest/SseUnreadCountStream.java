package dev.patra.notification.adapter.realtime.rest;

import dev.patra.notification.adapter.realtime.rest.response.UnreadCountsEventResponse;
import dev.patra.notification.app.realtime.service.UnreadCountStream;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/// 用 `SseEmitter` 实现的推送连接（PAP-145，契约见设计第 5 节）。
///
/// 每条连接的第一条事件带 `retry: 3000`（浏览器断线后 3 秒重连）；事件名 `unread-count`，数据是三个未读数；
/// 心跳是一行注释 `:ping`，浏览器不触发任何事件。
public class SseUnreadCountStream implements UnreadCountStream {

  /// 事件名。
  static final String EVENT = "unread-count";

  /// 浏览器断线后隔多久重连。
  private static final Duration RECONNECT = Duration.ofSeconds(3);

  private final SseEmitter emitter;

  /// 还没推过事件（第一条带重连间隔）。
  private boolean first = true;

  /// 创建。
  ///
  /// @param emitter 这条连接的 emitter
  public SseUnreadCountStream(SseEmitter emitter) {
    this.emitter = emitter;
  }

  /// 推一条 `unread-count` 事件。连接表在每个用户的锁里调用，推送之间不会并发；心跳可能同时在写，同一条连接上的写
  /// 由 `SseEmitter` 自己串行化。
  ///
  /// @param counts 未读数
  /// @throws UncheckedIOException 写不进去（浏览器已走）
  @Override
  public void sendCounts(UnreadCounts counts) {
    SseEmitter.SseEventBuilder event =
        SseEmitter.event()
            .name(EVENT)
            .data(UnreadCountsEventResponse.from(counts), MediaType.APPLICATION_JSON);
    if (first) {
      event.reconnectTime(RECONNECT.toMillis());
      first = false;
    }
    send(event);
  }

  /// 推一行注释当心跳。
  ///
  /// @throws UncheckedIOException 写不进去（浏览器已走）
  @Override
  public void sendHeartbeat() {
    send(SseEmitter.event().comment("ping"));
  }

  /// 正常结束响应，浏览器按重连间隔重连。
  @Override
  public void close() {
    emitter.complete();
  }

  /// 发送一条。
  ///
  /// @param event 事件
  private void send(SseEmitter.SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
