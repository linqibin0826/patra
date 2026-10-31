package dev.patra.notification.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.subject.event.InteractionExchange;
import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.support.NotificationFlowITSupport;
import dev.patra.notification.support.SseTestConnection;
import dev.patra.tool.security.test.TestIdentity;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/// 未读数推送（PAP-145，设计 `docs/patra/specs/2026-10-31-notification-realtime-push-design.md` 第 5、6 节）：
/// 别人回复后 3 秒内推到、连上先推当前值、只在提交后推、心跳与在站、到寿命正常结束、未登录 401。
///
/// 心跳压到 200 毫秒、寿命压到 5 秒，用例才跑得快（寿命要长过「3 秒内推到」的窗口）；真实的 25 秒与 30 分钟由
/// `ApplicationConfigTest` 守着。
@TestPropertySource(
    properties = {
      "patra.notification.realtime.heartbeat-interval=200ms",
      "patra.notification.realtime.max-connection-age=5s"
    })
class UnreadCountStreamFlowIT extends NotificationFlowITSupport {

  private static final String STREAM = "/me/notifications/stream";
  private static final long USER = 7L;
  private static final long OTHER = 8L;
  private static final long AUTHOR = 9L;

  @Autowired private Environment environment;
  @Autowired private TransactionTemplate transactions;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private RabbitTemplate rabbit;

  /// 别人回复了他（AC）：interaction 的 `comment.published` 经 RabbitMQ 到信箱、提交后推，3 秒内到达他开着的
  /// 每条连接，`replies` 变成 1；别人的连接收不到。
  @Test
  void should_push_reply_to_recipient_within_three_seconds() throws Exception {
    try (SseTestConnection firstTab = open(USER);
        SseTestConnection secondTab = open(USER);
        SseTestConnection someoneElse = open(OTHER)) {
      for (SseTestConnection stream : List.of(firstTab, secondTab, someoneElse)) {
        stream.nextEvent(Duration.ofSeconds(2)).orElseThrow();
      }

      rabbit.convertAndSend(InteractionExchange.NAME, CommentPublished.ROUTING_KEY, replyTo(USER));

      for (SseTestConnection tab : List.of(firstTab, secondTab)) {
        List<String> pushed = tab.nextEvent(Duration.ofSeconds(3)).orElseThrow();
        JsonNode data = JSON.readTree(SseTestConnection.data(pushed));
        assertThat(data.get("replies").asInt()).isEqualTo(1);
        assertThat(data.get("total").asInt()).isEqualTo(1);
      }
      assertThat(someoneElse.nextEvent(Duration.ofSeconds(1))).isEmpty();
    }
  }

  /// 连上立刻收到一条当前值：带 3 秒重连间隔、事件名 `unread-count`、数据与未读数接口同形。
  @Test
  void should_send_current_counts_with_retry_first() throws Exception {
    try (SseTestConnection stream = open(USER)) {
      assertThat(stream.status()).isEqualTo(200);
      assertThat(stream.headers().firstValue(HttpHeaders.CONTENT_TYPE))
          .hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));
      assertThat(stream.headers().firstValue(HttpHeaders.CACHE_CONTROL))
          .hasValueSatisfying(value -> assertThat(value).contains("no-transform"));

      List<String> first = stream.nextEvent(Duration.ofSeconds(2)).orElseThrow();
      assertThat(SseTestConnection.field(first, "retry")).isEqualTo("3000");
      assertThat(SseTestConnection.field(first, "event")).isEqualTo("unread-count");
      JsonNode data = JSON.readTree(SseTestConnection.data(first));
      assertThat(data.get("total").asInt()).isZero();
      assertThat(data.get("replies").asInt()).isZero();
      assertThat(data.get("mentions").asInt()).isZero();
      assertThat(data.get("helpful").asInt()).isZero();
    }
  }

  /// 没有身份断言（正常经网关时网关已先拦住，这里是第二道）：401 ProblemDetail，不是事件流；
  /// 请求带的 `Accept: text/event-stream` 不影响错误的输出。
  @Test
  void should_reject_anonymous_stream_with_problem_detail() throws Exception {
    try (SseTestConnection stream = SseTestConnection.open(url(STREAM), Map.of())) {
      assertThat(stream.status()).isEqualTo(401);
      assertThat(stream.headers().firstValue(HttpHeaders.CONTENT_TYPE))
          .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
      assertThat(stream.body()).contains("\"code\":\"NOTIFICATION-0401\"");
    }
  }

  /// 没有提醒时连接也不安静：按心跳间隔收到 `:ping`，连接一直开着；在站时刻随心跳前进（决策 H）。
  @Test
  void should_keep_idle_stream_open_with_heartbeats_and_mark_presence() throws Exception {
    try (SseTestConnection stream = open(USER)) {
      stream.nextEvent(Duration.ofSeconds(2)).orElseThrow();
      assertThat(stream.awaitComment(Duration.ofSeconds(1))).isTrue();
      Instant seenAtFirstHeartbeat = lastSeen(USER);

      assertThat(stream.awaitComment(Duration.ofSeconds(1))).isTrue();
      assertThat(stream.awaitComment(Duration.ofSeconds(1))).isTrue();

      // 一轮心跳先发完全部连接、再记在站，所以刚收到心跳时读到的可能还是上一轮的时刻
      await()
          .atMost(Duration.ofSeconds(1))
          .untilAsserted(() -> assertThat(lastSeen(USER)).isAfter(seenAtFirstHeartbeat));
    }
  }

  /// 浏览器悄悄走了（关标签页、断网）：之后的心跳写不进去，连接被剔除，在站时刻不再前进。
  @Test
  void should_stop_marking_presence_after_client_disconnects() throws Exception {
    SseTestConnection stream = open(USER);
    stream.nextEvent(Duration.ofSeconds(2)).orElseThrow();
    stream.close();

    // 关掉后的头一两次写可能还进得了内核缓冲；5 轮心跳之后一定已经剔除
    Thread.sleep(Duration.ofSeconds(1));
    Instant seenAfterDrop = lastSeen(USER);
    Thread.sleep(Duration.ofSeconds(1));

    assertThat(lastSeen(USER)).isEqualTo(seenAfterDrop);
  }

  /// 到寿命（测试里 5 秒）服务端正常结束响应：读到结尾，不是 I/O 错误；浏览器随后按 `retry` 重连。
  @Test
  void should_end_stream_normally_at_max_age() throws Exception {
    long started = System.nanoTime();
    try (SseTestConnection stream = open(USER)) {
      assertThat(stream.awaitEnd(Duration.ofSeconds(8))).isTrue();
    }
    assertThat(Duration.ofNanos(System.nanoTime() - started))
        .isGreaterThanOrEqualTo(Duration.ofSeconds(5));
  }

  /// 只在信箱写入的事务提交后推：回滚了不推（AC）；推送不进 Modulith 事件登记表（登记表只认
  /// `@ApplicationModuleListener`），重启后不会补推过期的数。
  @Test
  void should_push_only_after_commit_and_not_record_publication() throws Exception {
    try (SseTestConnection stream = open(USER)) {
      stream.nextEvent(Duration.ofSeconds(2)).orElseThrow();

      transactions.executeWithoutResult(
          status -> {
            events.publishEvent(UnreadCountsChangedEvent.of(Set.of(USER)));
            status.setRollbackOnly();
          });
      assertThat(stream.nextEvent(Duration.ofSeconds(1))).isEmpty();

      transactions.executeWithoutResult(
          status -> events.publishEvent(UnreadCountsChangedEvent.of(Set.of(USER))));
      assertThat(stream.nextEvent(Duration.ofSeconds(1))).isPresent();
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_publication WHERE event_type LIKE ?",
                Long.class,
                "%UnreadCountsChangedEvent"))
        .isZero();
  }

  /// 以某个用户开一条推送连接（直连通知服务，带测试私钥签的身份断言）。
  ///
  /// @param userId 用户
  /// @return 连接
  /// @throws Exception 连不上时
  private SseTestConnection open(long userId) throws Exception {
    return SseTestConnection.open(
        url(STREAM),
        Map.of(
            HttpHeaders.AUTHORIZATION,
            TestIdentity.headers(userId).getFirst(HttpHeaders.AUTHORIZATION)));
  }

  /// 作者 9 回复了这个人的评论（不 @ 任何人）。
  ///
  /// @param parentAuthorId 被回复的人
  /// @return 事件
  private static CommentPublished replyTo(long parentAuthorId) {
    return CommentPublished.builder()
        .eventId(UUID.randomUUID())
        .commentId(500L)
        .version(1)
        .subject(SubjectContext.of(40L, "PUBLICATION", null, false))
        .rootCommentId(100L)
        .parentCommentId(100L)
        .parentAuthorId(parentAuthorId)
        .authorId(AUTHOR)
        .authorAnonymous(false)
        .mentionedUserIds(List.of())
        .plainText("谢谢分享")
        .publishedAt(Instant.parse("2026-10-31T08:00:00Z"))
        .build();
  }

  /// 这个用户最后一次在站的时刻（`kernel.presence`）。
  ///
  /// @param userId 用户
  /// @return 时刻
  private Instant lastSeen(long userId) {
    return jdbcTemplate
        .queryForObject(
            "SELECT last_seen_at FROM kernel.presence WHERE user_id = ?", Timestamp.class, userId)
        .toInstant();
  }

  /// 通知服务上某个路径的完整地址。
  ///
  /// @param path 路径
  /// @return 地址
  private String url(String path) {
    return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
  }
}
