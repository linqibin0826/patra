package dev.patra.notification.adapter.realtime.rest;

import dev.patra.notification.adapter.realtime.config.RealtimeProperties;
import dev.patra.notification.app.realtime.service.UnreadCountStreamRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/// 未读数推送（PAP-145）：要登录，只推三个未读数。
///
/// 设计：`docs/patra/specs/2026-10-31-notification-realtime-push-design.md`。
///
/// 直接用连接表、不走命令总线：它管的是长连接的状态，不是一个用例；每 25 秒的心跳过拦截器链只会刷日志。
@Tag(name = "MyNotifications", description = "我的提醒")
@RestController
@RequiredArgsConstructor
public class UnreadCountStreamController {

  /// 不缓存，也不许中间层改写：`no-transform` 让 Next.js 的压缩中间件与 Cloudflare 不 gzip 事件流
  /// （压缩会攒满一块才发，事件就卡住了）。
  private static final String CACHE_CONTROL = "no-cache, no-transform";

  private final UnreadCountStreamRegistry registry;
  private final RealtimeProperties properties;

  /// 开一条推送连接：立刻推一次当前值，之后每次变化推一次，每 25 秒一行心跳，30 分钟后正常结束。
  ///
  /// `SseEmitter` 的超时就是连接寿命，同时也是显式设的异步超时（不设是 Tomcat 默认的 30 秒）：到点正常结束，
  /// 不当错误；结束、出错、超时都从连接表注销。
  ///
  /// @return 事件流
  @GetMapping(path = "/me/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> stream() {
    SseEmitter emitter = new SseEmitter(properties.maxConnectionAge().toMillis());
    SseUnreadCountStream stream = new SseUnreadCountStream(emitter);
    long userId = registry.openForCurrentUser(stream);
    emitter.onTimeout(emitter::complete);
    emitter.onCompletion(() -> registry.close(userId, stream));
    emitter.onError(error -> registry.close(userId, stream));
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL).body(emitter);
  }
}
