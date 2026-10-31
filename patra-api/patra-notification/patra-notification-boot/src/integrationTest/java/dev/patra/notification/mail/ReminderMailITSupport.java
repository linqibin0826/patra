package dev.patra.notification.mail;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;

import com.icegreen.greenmail.util.GreenMail;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.subject.event.InteractionExchange;
import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.notification.app.mail.usecase.flush.FlushDueRemindersCommand;
import dev.patra.notification.app.mail.usecase.send.SendPendingDeliveriesCommand;
import dev.patra.notification.app.mail.usecase.send.SendResult;
import dev.patra.notification.domain.mail.exception.MailDeliveryFailedException;
import dev.patra.notification.domain.mail.model.enums.MailDeliveryFailure;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.notification.domain.mail.port.delivery.MailDeliveryPort;
import dev.patra.notification.support.NotificationFlowITSupport;
import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.test.clock.MutableClock;
import dev.patra.tool.test.mail.GreenMailSmtpInitializer;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ContextConfiguration;

/// 提醒邮件端到端测试的共用基座：真实的 PG、RabbitMQ 与 GreenMail；identity、interaction 由 WireMock 扮演；
/// 时钟可拨动；发信端口外面包一层可以让它先失败几次的替身（GreenMail 各上下文共用，不能停掉它）。
///
/// 提醒经真实路径产生：向 `interaction.events` 发评论事件 → 信箱消费写提醒 → 同事务入队。任务不走调度中心，直接经
/// 命令总线跑一次 `reminderMailJob` 的两步。
@ContextConfiguration(initializers = GreenMailSmtpInitializer.class)
@Import(ReminderMailITSupport.ReminderMailITSpringConfig.class)
abstract class ReminderMailITSupport extends NotificationFlowITSupport {

  /// 起始时刻：北京时间 10 月 30 日 15:02。
  static final Instant T0 = Instant.parse("2026-10-30T07:02:00Z");

  /// 收件人。
  static final long ME = 2L;

  /// 评论作者（王立）。
  static final long AUTHOR = 9L;

  /// 收件地址。
  static final String MAILBOX = "chen@example.com";

  /// 文献对象。
  static final long PAPER = 40L;

  @Autowired MutableClock clock;
  @Autowired RabbitTemplate rabbit;
  @Autowired CommandBus commandBus;
  @Autowired FlakyMailDeliveryPort flaky;

  /// GreenMail（各上下文共用一个）。
  final GreenMail greenMail = GreenMailSmtpInitializer.getGreenMail();

  /// 每例：时钟回到起点、清空信箱与替身；identity 认识收件人与作者，interaction 认识那篇文献。
  @BeforeEach
  void resetMailWorld() {
    clock.set(T0);
    GreenMailSmtpInitializer.purge();
    flaky.reset();
    stubMailbox(ME, MAILBOX, true, "ACTIVE");
    stubSummaries(
        "{\"userId\":9,\"username\":\"wangli\",\"displayName\":\"王立\",\"avatarPalette\":1,"
            + "\"accountStatus\":\"ACTIVE\",\"emailVerified\":true,\"profileVisible\":true},"
            + "{\"userId\":2,\"username\":\"chenyu\",\"displayName\":\"陈宇\",\"avatarPalette\":2,"
            + "\"accountStatus\":\"ACTIVE\",\"emailVerified\":true,\"profileVisible\":true}");
    stubSubjectViews(
        "{\"subjectId\":40,\"type\":\"PUBLICATION\",\"key\":\"18\",\"available\":true,"
            + "\"title\":\"Hydrocortisone, Vitamin C, and Thiamine for the Treatment of Severe Sepsis\","
            + "\"journal\":\"Chest\",\"ownerAnonymous\":false}");
  }

  /// identity 对这个人的收件信息回这样一条（可指定是否已验证）。
  ///
  /// @param userId 用户
  /// @param email 邮箱
  /// @param verified 是否已验证
  /// @param status 账号状态
  static void stubMailbox(long userId, String email, boolean verified, String status) {
    identity.stubFor(
        post(urlPathEqualTo("/_internal/users/recipients"))
            .withRequestBody(equalToJson("{\"userIds\":[" + userId + "]}"))
            .willReturn(
                okJson(
                    "{\"recipients\":[{\"userId\":\""
                        + userId
                        + "\",\"email\":\""
                        + email
                        + "\",\"emailVerified\":"
                        + verified
                        + ",\"accountStatus\":\""
                        + status
                        + "\",\"username\":\"chenyu\",\"displayName\":\"陈宇\"}]}")));
  }

  /// 王立回复了收件人的评论；等信箱写好提醒。
  ///
  /// @param commentId 评论
  /// @param plainText 正文
  void reply(long commentId, String plainText) {
    publish(commentId, ME, List.of(), plainText);
  }

  /// 王立发了一条根评论 @ 了收件人；等信箱写好提醒。
  ///
  /// @param commentId 评论
  /// @param plainText 正文，含 `<@2>`
  void mention(long commentId, String plainText) {
    publish(commentId, null, List.of(ME), plainText);
  }

  /// 发一条评论发表事件，等它的提醒写进信箱。
  ///
  /// @param commentId 评论
  /// @param parentAuthorId 被回复的人，根评论为空
  /// @param mentioned @ 到的人
  /// @param plainText 正文
  private void publish(
      long commentId, Long parentAuthorId, List<Long> mentioned, String plainText) {
    rabbit.convertAndSend(
        InteractionExchange.NAME,
        CommentPublished.ROUTING_KEY,
        CommentPublished.builder()
            .eventId(UUID.randomUUID())
            .commentId(commentId)
            .version(1)
            .subject(SubjectContext.of(PAPER, "PUBLICATION", null, false))
            .rootCommentId(parentAuthorId == null ? commentId : 100L)
            .parentCommentId(parentAuthorId == null ? null : 100L)
            .parentAuthorId(parentAuthorId)
            .authorId(AUTHOR)
            .authorAnonymous(false)
            .mentionedUserIds(mentioned)
            .plainText(plainText)
            .publishedAt(clock.instant())
            .build());
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM mail.reminder_queue q JOIN inbox.notification n"
                            + " ON n.notification_id = q.notification_id WHERE n.comment_id = ?",
                        Long.class,
                        commentId)
                    == 1);
  }

  /// 收件人拉一次未读数（在站信号）。
  void pollUnreadCount() {
    call(HttpMethod.GET, ME, "/me/notifications/unread-count", null);
  }

  /// 收件人把全部提醒标为已读。
  void readAll() {
    call(HttpMethod.PUT, ME, "/me/notifications/read", null);
  }

  /// 把收件人某一类的档位改掉。
  ///
  /// @param category `REPLY` 或 `MENTION`
  /// @param mode 档位
  void setMode(String category, String mode) {
    call(
        HttpMethod.PUT,
        ME,
        "/me/notification-preferences/" + category,
        "{\"mode\":\"" + mode + "\"}");
  }

  /// 拨时钟。
  ///
  /// @param duration 往后拨多久
  void advance(Duration duration) {
    clock.advance(duration);
  }

  /// 跑一次 `reminderMailJob`：先到点处理，再发送。
  ///
  /// @return 发送这一步的计数
  SendResult runReminderJob() {
    commandBus.handle(FlushDueRemindersCommand.of());
    return commandBus.handle(SendPendingDeliveriesCommand.of());
  }

  /// 邮件的 HTML 部分。
  ///
  /// @param mail 邮件
  /// @return HTML；没有时为空串
  /// @throws Exception 读取失败
  static String html(MimeMessage mail) throws Exception {
    return htmlPart(mail.getContent());
  }

  /// 在（可能嵌套的）多部分里找 HTML。
  ///
  /// @param content 内容
  /// @return HTML；没有时为空串
  /// @throws Exception 读取失败
  private static String htmlPart(Object content) throws Exception {
    if (content instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart part = multipart.getBodyPart(i);
        String html =
            part.isMimeType("text/html")
                ? part.getContent().toString()
                : htmlPart(part.getContent());
        if (!html.isEmpty()) {
          return html;
        }
      }
    }
    return "";
  }

  /// GreenMail 收到的信。
  ///
  /// @return 信
  MimeMessage[] mails() {
    return greenMail.getReceivedMessages();
  }

  /// 测试用的装配：可拨动的时钟、能先失败几次的发信端口。
  @TestConfiguration(proxyBeanMethods = false)
  static class ReminderMailITSpringConfig {

    /// 从 {@link #T0} 开始的时钟（启动器的 `Clock` 让位给它）。
    ///
    /// @return 时钟
    @Bean
    @Primary
    MutableClock clock() {
      return MutableClock.startingAt(T0);
    }

    /// 包着真实发信端口的替身。
    ///
    /// @param real 真实的 SMTP 发信端口
    /// @return 替身
    @Bean
    @Primary
    FlakyMailDeliveryPort flakyMailDeliveryPort(
        @Qualifier("mailDeliveryPort") MailDeliveryPort real) {
      return new FlakyMailDeliveryPort(real);
    }
  }

  /// 能先失败几次的发信端口：其余时候原样交给真实端口（经 GreenMail 发出）。
  static final class FlakyMailDeliveryPort implements MailDeliveryPort {

    private final MailDeliveryPort real;
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private volatile MailDeliveryFailure failure = MailDeliveryFailure.TRANSIENT;

    /// 创建。
    ///
    /// @param real 真实端口
    FlakyMailDeliveryPort(MailDeliveryPort real) {
      this.real = real;
    }

    /// 接下来的 n 次都按这一类失败。
    ///
    /// @param n 次数
    /// @param kind 失败类别
    void failNext(int n, MailDeliveryFailure kind) {
      failure = kind;
      failuresLeft.set(n);
    }

    /// 恢复正常。
    void reset() {
      failuresLeft.set(0);
    }

    /// 发一封信：还有失败次数就抛出，否则交给真实端口。
    ///
    /// @param mail 信
    @Override
    public void deliver(OutgoingMail mail) {
      if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
        throw new MailDeliveryFailedException(mail, failure, "测试替身模拟的失败");
      }
      real.deliver(mail);
    }
  }
}
