package dev.patra.notification.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.rabbitmq.client.AMQP;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.subject.event.InteractionExchange;
import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.notification.infra.realtime.broadcast.UnreadCountBroadcastAdapter;
import dev.patra.notification.support.NotificationFlowITSupport;
import dev.patra.notification.support.SecondNotificationInstance;
import dev.patra.notification.support.SseTestConnection;
import dev.patra.tool.security.test.TestIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// 多实例推送未读数（#406，spec `docs/patra/specs/2026-11-13-notification-unread-broadcast-design.md`）：同一个
// JVM 里
/// 起第二个通知服务实例 B，和测试类自己的上下文 A 共用数据库与 RabbitMQ。
///
/// 信箱队列是单活跃消费者，最先订阅的实例在收。B 在 A 之后启动、最后订阅，永远不是信箱的消费者，所以「连接在 B」
/// 就是「连接所在的实例没消费这条消息」。
class UnreadCountBroadcastFlowIT extends NotificationFlowITSupport {

  private static final String STREAM = "/me/notifications/stream";
  private static final long USER = 7L;
  private static final long AUTHOR = 9L;

  /// 第二个实例：第一个用例开始前启动，整个测试类共用。
  private static SecondNotificationInstance b;

  @Autowired private Environment environment;
  @Autowired private RabbitTemplate rabbit;
  @Autowired private AmqpAdmin admin;
  @Autowired private RabbitListenerEndpointRegistry listeners;
  @Autowired private ApplicationContext context;
  @Autowired private MeterRegistry meters;

  @BeforeEach
  void startSecondInstance() {
    if (b == null) {
      b = SecondNotificationInstance.start(identity.baseUrl(), interaction.baseUrl());
    }
  }

  @AfterAll
  static void stopSecondInstance() {
    if (b != null) {
      b.close();
      b = null;
    }
  }

  /// 验收 1：连接在 B，回复 A 的消息被别的实例消费，B 上 3 秒内收到新的未读数。
  @Test
  void should_push_to_stream_on_instance_that_did_not_consume_the_message() throws Exception {
    try (SseTestConnection onB = openOnB(USER)) {
      onB.nextEvent(Duration.ofSeconds(2)).orElseThrow();

      rabbit.convertAndSend(InteractionExchange.NAME, CommentPublished.ROUTING_KEY, replyTo(USER));

      assertThat(replies(onB.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isEqualTo(1);
    }
  }

  /// 验收 2：同一用户在 A、B 上各开一条连接，两边都收到。
  @Test
  void should_push_to_streams_on_both_instances() throws Exception {
    try (SseTestConnection onA = openOnA(USER);
        SseTestConnection onB = openOnB(USER)) {
      onA.nextEvent(Duration.ofSeconds(2)).orElseThrow();
      onB.nextEvent(Duration.ofSeconds(2)).orElseThrow();

      rabbit.convertAndSend(InteractionExchange.NAME, CommentPublished.ROUTING_KEY, replyTo(USER));

      assertThat(replies(onA.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isEqualTo(1);
      assertThat(replies(onB.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isEqualTo(1);
    }
  }

  /// 请求线程触发的路径：在 B 上全部标已读，A 上的连接收到清零后的数。
  @Test
  void should_push_across_instances_when_marked_read_on_the_other_one() throws Exception {
    try (SseTestConnection onA = openOnA(USER)) {
      onA.nextEvent(Duration.ofSeconds(2)).orElseThrow();
      rabbit.convertAndSend(InteractionExchange.NAME, CommentPublished.ROUTING_KEY, replyTo(USER));
      assertThat(replies(onA.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isEqualTo(1);

      assertThat(markAllRead(b.port(), USER)).isEqualTo(200);

      assertThat(replies(onA.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isZero();
    }
  }

  /// 发起变化的实例只推一次：本机直推一次，自己发的广播回到自己队列时跳过。
  @Test
  void should_push_once_on_the_instance_that_made_the_change() throws Exception {
    try (SseTestConnection onA = openOnA(USER)) {
      onA.nextEvent(Duration.ofSeconds(2)).orElseThrow();
      rabbit.convertAndSend(InteractionExchange.NAME, CommentPublished.ROUTING_KEY, replyTo(USER));
      assertThat(replies(onA.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isEqualTo(1);

      assertThat(markAllRead(aPort(), USER)).isEqualTo(200);

      assertThat(replies(onA.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isZero();
      assertThat(onA.nextEvent(Duration.ofSeconds(1))).isEmpty();
    }
  }

  /// 通知服务确实把信箱队列声明成了单活跃消费者：用同样的参数再声明一次，参数不一致时 RabbitMQ 会拒绝；两个实例都
  /// 订阅着（只有一个在收）。
  @Test
  void should_declare_inbox_as_single_active_consumer_with_both_instances_subscribed() {
    AMQP.Queue.DeclareOk ok =
        rabbit.execute(
            channel ->
                channel.queueDeclare(
                    "notification.inbox",
                    true,
                    false,
                    false,
                    Map.of(
                        "x-dead-letter-exchange", "notification.inbox.dlx",
                        "x-dead-letter-routing-key", "notification.inbox",
                        "x-single-active-consumer", true)));

    assertThat(ok.getConsumerCount()).isGreaterThanOrEqualTo(2);
  }

  /// 广播队列里来了一条坏消息（不是合法 JSON）：这条被丢掉，监听照常工作，后面的广播照常推。
  @Test
  void should_keep_listening_after_malformed_broadcast() throws Exception {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    rabbit.send(
        UnreadCountBroadcastAdapter.EXCHANGE,
        "",
        new Message("{oops".getBytes(StandardCharsets.UTF_8), properties));

    assertCrossInstancePushStillWorks();
  }

  /// 广播监听重启（断线重连时也是这样）：临时队列随消费者取消被删，重启时重建，照常收到。
  @Test
  void should_receive_broadcast_again_after_listener_restart() throws Exception {
    MessageListenerContainer container = listeners.getListenerContainer("unreadBroadcast");
    container.stop();
    container.start();

    assertCrossInstancePushStillWorks();
  }

  /// 没有队列可投的广播被 broker 退回（事件工具库让所有发送都带 mandatory）：发送方不抛异常。
  @Test
  void should_not_throw_when_broadcast_is_returned() {
    FanoutExchange nowhere = new FanoutExchange("notification.it.nowhere", false, true);
    admin.declareExchange(nowhere);
    try {
      assertThatCode(() -> rabbit.convertAndSend(nowhere.getName(), "", "hello"))
          .doesNotThrowAnyException();
    } finally {
      admin.deleteExchange(nowhere.getName());
    }
  }

  /// 两个线程池都在：Boot 的推送线程池没有因为多了广播线程池而让位；广播线程池带监控指标，能看出有没有堆积。
  @Test
  void should_keep_boot_executor_and_expose_broadcast_executor_metrics() {
    assertThat(context.getBean("applicationTaskExecutor"))
        .isInstanceOf(ThreadPoolTaskExecutor.class);
    assertThat(context.getBean("unreadBroadcastExecutor"))
        .isInstanceOf(ThreadPoolTaskExecutor.class);
    assertThat(meters.find("executor.queued").tag("name", "unreadBroadcastExecutor").gauge())
        .isNotNull();
  }

  /// 跨实例推送仍然正常：在 B 上标已读，A 上的连接收到。
  private void assertCrossInstancePushStillWorks() throws Exception {
    try (SseTestConnection onA = openOnA(USER)) {
      onA.nextEvent(Duration.ofSeconds(2)).orElseThrow();
      rabbit.convertAndSend(InteractionExchange.NAME, CommentPublished.ROUTING_KEY, replyTo(USER));
      assertThat(replies(onA.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isEqualTo(1);

      assertThat(markAllRead(b.port(), USER)).isEqualTo(200);

      assertThat(replies(onA.nextEvent(Duration.ofSeconds(3)).orElseThrow())).isZero();
    }
  }

  /// 在某个实例上把这个人的提醒全部标已读。
  ///
  /// @param port 实例端口
  /// @param userId 用户
  /// @return 响应状态码
  private static int markAllRead(int port, long userId) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me/notifications/read"))
            .header(
                HttpHeaders.AUTHORIZATION,
                TestIdentity.headers(userId).getFirst(HttpHeaders.AUTHORIZATION))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
  }

  /// A 的 HTTP 端口。
  ///
  /// @return 端口
  private int aPort() {
    return environment.getRequiredProperty("local.server.port", Integer.class);
  }

  /// 在 B 上开一条推送连接。
  ///
  /// @param userId 用户
  /// @return 连接
  private SseTestConnection openOnB(long userId) throws Exception {
    return open(b.port(), userId);
  }

  /// 在 A 上开一条推送连接。
  ///
  /// @param userId 用户
  /// @return 连接
  private SseTestConnection openOnA(long userId) throws Exception {
    return open(aPort(), userId);
  }

  /// 在某个端口的实例上开推送连接。
  ///
  /// @param port 实例端口
  /// @param userId 用户
  /// @return 连接
  private static SseTestConnection open(int port, long userId) throws Exception {
    return SseTestConnection.open(
        "http://localhost:" + port + STREAM,
        Map.of(
            HttpHeaders.AUTHORIZATION,
            TestIdentity.headers(userId).getFirst(HttpHeaders.AUTHORIZATION)));
  }

  /// 事件里的 `replies`。
  ///
  /// @param event 事件的各行
  /// @return 回复我的未读数
  private static int replies(List<String> event) {
    return JSON.readTree(SseTestConnection.data(event)).get("replies").asInt();
  }

  /// 作者 9 回复了这个人的评论（不 @ 任何人），写法同 `UnreadCountStreamFlowIT`。
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
        .publishedAt(Instant.parse("2026-11-13T06:00:00Z"))
        .build();
  }
}
