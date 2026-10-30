package dev.patra.notification.app.mail.usecase.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.mail.exception.MailDeliveryFailedException;
import dev.patra.notification.domain.mail.model.aggregate.MailDelivery;
import dev.patra.notification.domain.mail.model.enums.MailDeliveryOutcome;
import dev.patra.notification.domain.mail.model.enums.SecurityMailKind;
import dev.patra.notification.domain.mail.model.vo.OperationContext;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.notification.domain.mail.model.vo.SecurityMail;
import dev.patra.notification.domain.mail.port.delivery.MailDeliveryPort;
import dev.patra.notification.domain.mail.port.repository.MailDeliveryRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/// SendSecurityMailHandler 单元测试：查重、链接过期、事务外发信、短事务里记已处理与发送记录。
@DisplayName("SendSecurityMailHandler 单元测试")
@ExtendWith(OutputCaptureExtension.class)
class SendSecurityMailHandlerTest {

  private static final String QUEUE = "notification.security-mail";
  private static final UUID EVENT_ID = UUID.fromString("1e2d3c4b-5a69-4788-9a0b-c1d2e3f4a5b6");
  private static final String TO = "chen.yu@example.com";
  private static final String RESET_URL = "https://patra.example/reset-password#token=rst";
  private static final Instant NOW = Instant.parse("2026-10-31T02:00:00Z");

  private final List<String> events = new ArrayList<>();
  private final MailDeliveryRepository deliveries = mock(MailDeliveryRepository.class);
  private final MailDeliveryPort mailDelivery = mock(MailDeliveryPort.class);
  private final ProcessedEventPort processedEvents = mock(ProcessedEventPort.class);
  private final List<MailDelivery> added = new ArrayList<>();
  private final List<OutgoingMail> delivered = new ArrayList<>();

  private final SendSecurityMailHandler handler =
      new SendSecurityMailHandler(
          deliveries,
          mailDelivery,
          processedEvents,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 各协作者往同一个事件列表里记，用来断言先后与事务内外。
  @BeforeEach
  void recordCalls() {
    when(deliveries.exists(any()))
        .thenAnswer(
            invocation -> {
              events.add("exists");
              return false;
            });
    doAnswer(
            invocation -> {
              events.add("deliver");
              delivered.add(invocation.getArgument(0));
              return null;
            })
        .when(mailDelivery)
        .deliver(any());
    when(processedEvents.markProcessed(anyString(), any()))
        .thenAnswer(
            invocation -> {
              events.add("markProcessed");
              return true;
            });
    doAnswer(
            invocation -> {
              events.add("add");
              added.add(invocation.getArgument(0));
              return null;
            })
        .when(deliveries)
        .add(any());
  }

  /// 先查重，在事务外发信，再在一个短事务里记「处理过了」与发送记录。
  @Test
  void should_send_outside_transaction_then_record_and_mark_processed() {
    SendSecurityMailResult result =
        handler.handle(SendSecurityMailCommand.of(QUEUE, reset(NOW.plusSeconds(1_800))));

    assertThat(result).isEqualTo(SendSecurityMailResult.SENT);
    assertThat(events)
        .containsExactly(
            "exists",
            "deliver",
            RecordingTransactions.BEGIN,
            "markProcessed",
            "add",
            RecordingTransactions.END);
    assertThat(delivered)
        .singleElement()
        .satisfies(mail -> assertThat(mail.deliveryId()).isEqualTo(EVENT_ID));
    assertThat(added).containsExactly(MailDelivery.sent(EVENT_ID, "password-reset", NOW));
    verify(processedEvents).markProcessed(QUEUE, EVENT_ID);
  }

  /// 只为链接而发的信，链接已过期：不发，短事务里记「处理过了」与 `LINK_EXPIRED`。
  @Test
  void should_record_link_expired_without_sending() {
    SendSecurityMailResult result = handler.handle(SendSecurityMailCommand.of(QUEUE, reset(NOW)));

    assertThat(result).isEqualTo(SendSecurityMailResult.LINK_EXPIRED);
    assertThat(events)
        .containsExactly(
            "exists",
            RecordingTransactions.BEGIN,
            "markProcessed",
            "add",
            RecordingTransactions.END);
    assertThat(added).containsExactly(MailDelivery.linkExpired(EVENT_ID, "password-reset", NOW));
    assertThat(added.getFirst().outcome()).isEqualTo(MailDeliveryOutcome.LINK_EXPIRED);
  }

  /// 通知类（邮箱已更改）的链接过期了也照发：通知本身才是重点。
  @Test
  void should_send_expired_notice_kind() {
    SecurityMail notice =
        SecurityMail.builder()
            .eventId(EVENT_ID)
            .kind(SecurityMailKind.EMAIL_CHANGED_NOTICE)
            .to(TO)
            .displayName("陈宇")
            .context(OperationContext.of(NOW, null, null, null))
            .linkExpiresAt(NOW.minusSeconds(60))
            .build();

    assertThat(handler.handle(SendSecurityMailCommand.of(QUEUE, notice)))
        .isEqualTo(SendSecurityMailResult.SENT);
    assertThat(delivered).hasSize(1);
  }

  /// 已有发送记录：重复投递，不发、不开事务。
  @Test
  void should_skip_when_already_recorded() {
    when(deliveries.exists(EVENT_ID)).thenReturn(true);

    SendSecurityMailResult result =
        handler.handle(SendSecurityMailCommand.of(QUEUE, reset(NOW.plusSeconds(1_800))));

    assertThat(result).isEqualTo(SendSecurityMailResult.DUPLICATE);
    assertThat(events).doesNotContain("deliver", RecordingTransactions.BEGIN);
  }

  /// 发信失败：异常原样抛给监听容器（由它重试、最后进死信），不开事务、不记已处理。
  @Test
  void should_not_record_when_delivery_fails() {
    MailDeliveryFailedException failure =
        new MailDeliveryFailedException(
            reset(NOW.plusSeconds(1_800)).compose(), "connection refused");
    doThrow(failure).when(mailDelivery).deliver(any());

    assertThatThrownBy(
            () -> handler.handle(SendSecurityMailCommand.of(QUEUE, reset(NOW.plusSeconds(1_800)))))
        .isSameAs(failure);
    assertThat(events).doesNotContain(RecordingTransactions.BEGIN, "markProcessed", "add");
  }

  /// 「处理过了」已经记过（同一事件的另一份投递抢先提交）：不再追加发送记录，避免主键冲突。
  @Test
  void should_not_add_record_when_event_already_marked() {
    when(processedEvents.markProcessed(anyString(), any())).thenReturn(false);

    SendSecurityMailResult result =
        handler.handle(SendSecurityMailCommand.of(QUEUE, reset(NOW.plusSeconds(1_800))));

    assertThat(result).isEqualTo(SendSecurityMailResult.SENT);
    assertThat(added).isEmpty();
  }

  /// 发出与失败的日志里有事件 ID 与类别，没有收件地址与链接。
  @Test
  void should_log_without_recipient_or_link(CapturedOutput output) {
    handler.handle(SendSecurityMailCommand.of(QUEUE, reset(NOW.plusSeconds(1_800))));
    doThrow(new MailDeliveryFailedException(reset(NOW.plusSeconds(1_800)).compose(), "SMTP 连接被拒绝"))
        .when(mailDelivery)
        .deliver(any());
    when(deliveries.exists(any())).thenReturn(false);

    assertThatThrownBy(
            () -> handler.handle(SendSecurityMailCommand.of(QUEUE, reset(NOW.plusSeconds(1_800)))))
        .isInstanceOf(MailDeliveryFailedException.class);

    assertThat(output.getAll())
        .contains("安全邮件已发送")
        .contains("安全邮件发送失败")
        .contains(EVENT_ID.toString())
        .contains("PASSWORD_RESET")
        .doesNotContain(TO)
        .doesNotContain("token=");
  }

  /// 链接 `expiresAt` 过期的找回密码信。
  ///
  /// @param expiresAt 链接过期时刻
  /// @return 邮件
  private static SecurityMail reset(Instant expiresAt) {
    return SecurityMail.builder()
        .eventId(EVENT_ID)
        .kind(SecurityMailKind.PASSWORD_RESET)
        .to(TO)
        .displayName("陈宇")
        .facts(Map.of("resetUrl", RESET_URL))
        .linkExpiresAt(expiresAt)
        .build();
  }
}
