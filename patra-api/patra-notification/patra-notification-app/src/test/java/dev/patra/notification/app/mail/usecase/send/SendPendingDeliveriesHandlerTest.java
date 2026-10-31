package dev.patra.notification.app.mail.usecase.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.notification.app.mail.service.AssembledReminder;
import dev.patra.notification.app.mail.service.ReminderDeliveryAssembler;
import dev.patra.notification.domain.kernel.exception.IdentityUnavailableException;
import dev.patra.notification.domain.kernel.exception.InteractionUnavailableException;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.exception.MailDeliveryFailedException;
import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.enums.DeliveryStatus;
import dev.patra.notification.domain.mail.model.enums.MailDeliveryFailure;
import dev.patra.notification.domain.mail.model.enums.SkipReason;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent;
import dev.patra.notification.domain.mail.model.vo.content.ReminderMailContent.Layout;
import dev.patra.notification.domain.mail.port.delivery.MailDeliveryPort;
import dev.patra.notification.domain.mail.port.repository.ReminderDeliveryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/// 逐条装配并发出待发的投递：四种发信结果、依赖不可用推迟并停下、意外异常继续、24 小时过期（spec 5.4、7.2、7.3）。
class SendPendingDeliveriesHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-30T07:12:00Z");
  private static final String ONE_CLICK = "https://p/unsubscribe/one-click?token=T&category=REPLY";

  private final ReminderDeliveryRepository deliveries = mock(ReminderDeliveryRepository.class);
  private final ReminderDeliveryAssembler assembler = mock(ReminderDeliveryAssembler.class);
  private final MailDeliveryPort mail = mock(MailDeliveryPort.class);
  private final SendPendingDeliveriesHandler handler =
      new SendPendingDeliveriesHandler(
          deliveries, assembler, mail, Clock.fixed(NOW, ZoneOffset.UTC));

  /// 发出：模板名、主题、变量、两个退订头、投递 ID 都对；先记下第一次发送再调端口。
  @Test
  void ready_is_delivered_with_template_model_headers_and_delivery_id() {
    ReminderDelivery delivery = due();
    ready(delivery, Layout.BATCH);

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    ArgumentCaptor<OutgoingMail> sent = ArgumentCaptor.captor();
    InOrder order = inOrder(deliveries, mail);
    order.verify(deliveries).update(delivery);
    order.verify(mail).deliver(sent.capture());
    OutgoingMail outgoing = sent.getValue();
    assertThat(outgoing.deliveryId()).isEqualTo(delivery.getDeliveryId());
    assertThat(outgoing.to()).isEqualTo("chen@example.com");
    assertThat(outgoing.template()).isEqualTo("notification-batch");
    assertThat(outgoing.subject()).isEqualTo("3 条新回复：王立");
    assertThat(outgoing.model()).containsKey("mail");
    assertThat(outgoing.headers())
        .containsEntry("List-Unsubscribe", "<" + ONE_CLICK + ">")
        .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(delivery.getFirstSentAt()).isEqualTo(NOW);
    assertThat(result).isEqualTo(SendResult.of(1, 0, 0, false));
  }

  /// 装配说不该发：记跳过原因，不调端口。
  @Test
  void skip_is_recorded_with_reason() {
    ReminderDelivery delivery = due();
    when(assembler.assemble(delivery, NOW))
        .thenReturn(AssembledReminder.Skip.of(SkipReason.RECIPIENT));

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
    assertThat(delivery.getSkipReason()).isEqualTo(SkipReason.RECIPIENT);
    verify(deliveries).update(delivery);
    verify(mail, never()).deliver(any());
    assertThat(result).isEqualTo(SendResult.of(0, 1, 0, false));
  }

  /// identity 挂了：这一条推迟，本轮停下，后面的不碰。
  @Test
  void identity_down_defers_current_and_stops_run() {
    ReminderDelivery first = delivery();
    ReminderDelivery second = delivery();
    page(first, second);
    when(assembler.assemble(first, NOW))
        .thenThrow(new IdentityUnavailableException(new RuntimeException("503")));

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(first.getAttempts()).isEqualTo(1);
    assertThat(first.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    verify(assembler, never()).assemble(second, NOW);
    assertThat(result).isEqualTo(SendResult.of(0, 0, 1, true));
  }

  /// interaction 挂了：同样推迟并停下。
  @Test
  void interaction_down_defers_and_stops() {
    ReminderDelivery delivery = due();
    when(assembler.assemble(delivery, NOW))
        .thenThrow(new InteractionUnavailableException(new RuntimeException("503")));

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(result.stoppedEarly()).isTrue();
  }

  /// 临时发信故障：推迟并停下。
  @Test
  void transient_failure_defers_and_stops() {
    ReminderDelivery first = delivery();
    ReminderDelivery second = delivery();
    page(first, second);
    ready(first, Layout.SINGLE);
    fail(MailDeliveryFailure.TRANSIENT);

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(first.getStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(first.getAttempts()).isEqualTo(1);
    assertThat(first.getFirstSentAt()).isEqualTo(NOW);
    verify(assembler, never()).assemble(second, NOW);
    assertThat(result).isEqualTo(SendResult.of(0, 0, 1, true));
  }

  /// 同一个幂等键已被接受过：当作已发。
  @Test
  void already_accepted_counts_as_sent() {
    ReminderDelivery delivery = due();
    ready(delivery, Layout.SINGLE);
    fail(MailDeliveryFailure.ALREADY_ACCEPTED);

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(result).isEqualTo(SendResult.of(1, 0, 0, false));
  }

  /// 永久拒绝：跳过这一封，继续下一封。
  @Test
  void rejected_skips_and_continues() {
    ReminderDelivery first = delivery();
    ReminderDelivery second = delivery();
    page(first, second);
    ready(first, Layout.SINGLE);
    ready(second, Layout.SINGLE);
    doThrow(failure(MailDeliveryFailure.REJECTED)).doNothing().when(mail).deliver(any());

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(first.getSkipReason()).isEqualTo(SkipReason.REJECTED);
    assertThat(second.getStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(result).isEqualTo(SendResult.of(1, 1, 0, false));
  }

  /// 意外异常：推迟这一条、继续下一条。
  @Test
  void unexpected_error_defers_and_continues() {
    ReminderDelivery first = delivery();
    ReminderDelivery second = delivery();
    page(first, second);
    when(assembler.assemble(first, NOW)).thenThrow(new IllegalStateException("坏数据"));
    ready(second, Layout.SINGLE);

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(first.getAttempts()).isEqualTo(1);
    assertThat(second.getStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(result).isEqualTo(SendResult.of(1, 0, 1, false));
  }

  /// 重试用同一个投递 ID。
  @Test
  void retry_reuses_delivery_id() {
    ReminderDelivery delivery = due();
    ready(delivery, Layout.SINGLE);
    fail(MailDeliveryFailure.TRANSIENT);
    handler.handle(SendPendingDeliveriesCommand.of());
    UUID firstId = delivery.getDeliveryId();

    handler.handle(SendPendingDeliveriesCommand.of());

    ArgumentCaptor<OutgoingMail> sent = ArgumentCaptor.captor();
    verify(mail, times(2)).deliver(sent.capture());
    assertThat(sent.getAllValues()).extracting(OutgoingMail::deliveryId).containsOnly(firstId);
  }

  /// 调用过发信端口、满 24 小时仍失败：过期。
  @Test
  void expires_24_hours_after_first_send() {
    ReminderDelivery delivery = delivery();
    delivery.recordSendAttempt(NOW.minus(Duration.ofHours(24)));
    page(delivery);
    ready(delivery, Layout.SINGLE);
    fail(MailDeliveryFailure.TRANSIENT);

    handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.EXPIRED);
  }

  /// 调用过发信端口、已满 24 小时：哪怕依赖都恢复了、装配得出来，也不再调发信端口（幂等键已失效），直接过期。
  @Test
  void send_window_closed_expires_without_delivering() {
    ReminderDelivery delivery = delivery();
    delivery.recordSendAttempt(NOW.minus(Duration.ofHours(24)));
    page(delivery);
    ready(delivery, Layout.SINGLE);

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.EXPIRED);
    verify(mail, never()).deliver(any());
    verify(deliveries).update(delivery);
    assertThat(result.sent()).isZero();
  }

  /// 从没调用过发信端口：identity 挂了 30 天也只推迟、不过期、不丢（AC「identity 不可用时推迟重试，恢复后补发」）。
  @Test
  void never_sent_delivery_survives_long_identity_outage() {
    ReminderDelivery delivery =
        ReminderDelivery.instant(
            UUID.randomUUID(),
            7L,
            NotificationCategory.REPLY,
            List.of(1L),
            NOW.minus(Duration.ofDays(30)));
    page(delivery);
    when(assembler.assemble(delivery, NOW))
        .thenThrow(new IdentityUnavailableException(new RuntimeException("503")));

    SendResult result = handler.handle(SendPendingDeliveriesCommand.of());

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(delivery.getFirstSentAt()).isNull();
    verify(mail, never()).deliver(any());
    assertThat(result.stoppedEarly()).isTrue();
  }

  /// 写回本身失败、一直取到同一条：一轮里每条只处理一次，不死循环。
  @Test
  void same_delivery_is_processed_once_per_run() {
    ReminderDelivery delivery = due();
    when(assembler.assemble(delivery, NOW)).thenReturn(AssembledReminder.Skip.of(SkipReason.MODE));
    doThrow(new IllegalStateException("库写不进")).doAnswer(i -> null).when(deliveries).update(any());

    handler.handle(SendPendingDeliveriesCommand.of());

    verify(assembler, times(1)).assemble(delivery, NOW);
  }

  /// 一条到点的逐条投递。
  ///
  /// @return 结果
  private ReminderDelivery due() {
    ReminderDelivery delivery = delivery();
    page(delivery);
    return delivery;
  }

  /// 取到点投递时回这一页（取两次后为空）。
  ///
  /// @param page page
  private void page(ReminderDelivery... page) {
    when(deliveries.findDue(NOW, 50))
        .thenReturn(List.of(page))
        .thenReturn(List.of(page))
        .thenReturn(List.of());
  }

  /// 这条投递装配得出来。
  ///
  /// @param delivery delivery
  /// @param layout layout
  private void ready(ReminderDelivery delivery, Layout layout) {
    when(assembler.assemble(delivery, NOW))
        .thenReturn(
            AssembledReminder.Ready.of(
                "chen@example.com",
                ReminderMailContent.builder()
                    .layout(layout)
                    .subject("3 条新回复：王立")
                    .oneClickUrl(ONE_CLICK)
                    .unsubscribeLabel("退订这类邮件")
                    .unsubscribeUrl("https://p/unsubscribe#token=T&category=REPLY")
                    .preferencesUrl("https://p/settings/notifications")
                    .build()));
  }

  /// 发信端口下一次按这一类失败。
  ///
  /// @param failure failure
  private void fail(MailDeliveryFailure failure) {
    doThrow(failure(failure)).doAnswer(i -> null).when(mail).deliver(any());
  }

  /// 发信端口的失败。
  ///
  /// @param failure failure
  /// @return 结果
  private static MailDeliveryFailedException failure(MailDeliveryFailure failure) {
    return new MailDeliveryFailedException(
        OutgoingMail.builder()
            .deliveryId(UUID.randomUUID())
            .to("x@example.com")
            .template("notification-single")
            .subject("s")
            .model(Map.of())
            .build(),
        failure,
        "原因");
  }

  /// 一条逐条投递。
  ///
  /// @return 结果
  private static ReminderDelivery delivery() {
    return ReminderDelivery.instant(
        UUID.randomUUID(), 7L, NotificationCategory.REPLY, List.of(1L), NOW);
  }
}
