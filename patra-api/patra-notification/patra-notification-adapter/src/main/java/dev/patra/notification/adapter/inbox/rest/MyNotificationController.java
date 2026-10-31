package dev.patra.notification.adapter.inbox.rest;

import dev.patra.notification.adapter.inbox.rest.response.NotificationPageResponse;
import dev.patra.notification.adapter.inbox.rest.response.UnreadCountsResponse;
import dev.patra.notification.app.inbox.usecase.poll.PollUnreadCountsCommand;
import dev.patra.notification.app.inbox.usecase.query.InboxQueryService;
import dev.patra.notification.app.inbox.usecase.read.MarkAllNotificationsReadCommand;
import dev.patra.notification.app.inbox.usecase.read.MarkNotificationReadCommand;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户的提醒（都要登录，未登录 401）：未读数（条件请求，没变化 304，顺带记在站）、按标签分页的列表、单条已读、
/// 全部已读。路径与 v0.15 interaction 的同名接口一致，只换了网关前缀。
@Tag(name = "MyNotifications", description = "我的提醒")
@RestController
@RequestMapping("/me/notifications")
@RequiredArgsConstructor
public class MyNotificationController {

  /// 浏览器每次都来问，按 ETag 省下响应体；只给本人缓存。
  private static final CacheControl REVALIDATE = CacheControl.noCache().cachePrivate();

  private final CommandBus commandBus;
  private final InboxQueryService query;

  /// 未读数。ETag 由三个标签的数算出；请求带的 `If-None-Match` 命中时 304、不带响应体。每次请求都记一次在站。
  ///
  /// 200 与 304 都显式带 `ETag` 与 `Cache-Control`：304 上不能被安全过滤器补成 `no-store`，否则浏览器丢掉缓存条目。
  ///
  /// @param ifNoneMatch 请求的 `If-None-Match`，可空
  /// @return 未读数或 304
  @GetMapping("/unread-count")
  public ResponseEntity<UnreadCountsResponse> unreadCount(
      @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
    UnreadCounts counts = commandBus.handle(PollUnreadCountsCommand.of());
    String etag = counts.etag();
    if (matches(ifNoneMatch, etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
          .eTag(etag)
          .cacheControl(REVALIDATE)
          .build();
    }
    return ResponseEntity.ok()
        .eTag(etag)
        .cacheControl(REVALIDATE)
        .body(UnreadCountsResponse.from(counts));
  }

  /// 一个标签下的一页（每页 20 条）。
  ///
  /// @param tab `replies`（默认）/ `mentions` / `helpful`
  /// @param cursor 上一页的 `nextCursor`，可空
  /// @return 一页
  @GetMapping
  public NotificationPageResponse page(
      @RequestParam(required = false) String tab, @RequestParam(required = false) String cursor) {
    return NotificationPageResponse.from(query.page(tab, cursor));
  }

  /// 单条已读（已读的再标不报错）。404 不存在或不是自己的。
  ///
  /// @param notificationId 提醒 ID
  /// @return 新的未读数
  @PutMapping("/{notificationId}/read")
  public UnreadCountsResponse markRead(@PathVariable long notificationId) {
    return UnreadCountsResponse.from(
        commandBus.handle(MarkNotificationReadCommand.of(notificationId)));
  }

  /// 全部已读（三个标签一起）。
  ///
  /// @return 新的未读数
  @PutMapping("/read")
  public UnreadCountsResponse markAllRead() {
    return UnreadCountsResponse.from(commandBus.handle(MarkAllNotificationsReadCommand.of()));
  }

  /// `If-None-Match` 里有没有这个 ETag（逗号分隔、忽略弱标记 `W/`、`*` 都算）。
  ///
  /// @param ifNoneMatch 请求头，可空
  /// @param etag 当前 ETag
  /// @return 命中
  private static boolean matches(String ifNoneMatch, String etag) {
    if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
      return false;
    }
    return Arrays.stream(ifNoneMatch.split(","))
        .map(String::trim)
        .map(tag -> tag.startsWith("W/") ? tag.substring(2) : tag)
        .anyMatch(tag -> tag.equals("*") || tag.equals(etag));
  }
}
