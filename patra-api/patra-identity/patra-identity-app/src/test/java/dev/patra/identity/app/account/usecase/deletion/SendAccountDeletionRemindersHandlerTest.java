package dev.patra.identity.app.account.usecase.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionReminderMail;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.policy.AccountDeletionPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/// SendAccountDeletionRemindersHandler 单元测试：标记后发信、锁内复核、拒收退回标记、单个失败不影响其他。
@DisplayName("SendAccountDeletionRemindersHandler 单元测试")
class SendAccountDeletionRemindersHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-01T08:15:00Z");
  private static final Instant DUE = Instant.parse("2026-11-04T08:00:00Z");
  private static final Duration LEAD = Duration.ofDays(3);

  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final List<Instant> savedReminders = new ArrayList<>();
  private final SendAccountDeletionRemindersHandler handler =
      new SendAccountDeletionRemindersHandler(
          users,
          userReadPort,
          AccountDeletionPolicy.of(Duration.ofDays(15), LEAD),
          emailDelivery,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(users.findIdsDueForDeletionReminder(eq(NOW), eq(NOW.plus(LEAD)), anyLong(), eq(100)))
        .thenReturn(List.of());
    when(users.save(any()))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              savedReminders.add(user.getDeletionRemindedAt());
              return user;
            });
    when(emailDelivery.send(any())).thenReturn(true);
  }

  @Test
  @DisplayName("该提醒的用户：记下提醒时间，提交后发「将在 3 天后删除」")
  void should_mark_and_send_reminder() {
    pendingUsers(42L);

    ReminderRunResult result = handler.handle(SendAccountDeletionRemindersCommand.of());

    assertThat(savedReminders).containsExactly(NOW);
    ArgumentCaptor<OutgoingMail> mail = ArgumentCaptor.forClass(OutgoingMail.class);
    verify(emailDelivery).send(mail.capture());
    AccountDeletionReminderMail reminder = (AccountDeletionReminderMail) mail.getValue();
    assertThat(reminder.to()).isEqualTo(email(42L));
    assertThat(reminder.deletionDueAt()).isEqualTo(DUE);
    assertThat(reminder.daysLeft()).isEqualTo(3);
    assertThat(result).isEqualTo(ReminderRunResult.of(1, 0));
  }

  @Test
  @DisplayName("锁内复核已经提醒过：不保存、不发信")
  void should_skip_when_already_reminded_after_lock() {
    when(users.findIdsDueForDeletionReminder(NOW, NOW.plus(LEAD), 0L, 100))
        .thenReturn(List.of(42L));
    User reminded = pending(42L);
    reminded.markDeletionReminded(NOW.minusSeconds(60));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(reminded));

    ReminderRunResult result = handler.handle(SendAccountDeletionRemindersCommand.of());

    assertThat(result).isEqualTo(ReminderRunResult.of(0, 0));
    verify(users, never()).save(any());
    verifyNoInteractions(emailDelivery);
  }

  @Test
  @DisplayName("发信被拒收：把提醒标记清回去，下次重发，计入失败")
  void should_unmark_when_mail_rejected() {
    pendingUsers(42L);
    when(emailDelivery.send(any())).thenReturn(false);

    ReminderRunResult result = handler.handle(SendAccountDeletionRemindersCommand.of());

    assertThat(savedReminders).containsExactly(NOW, null);
    assertThat(result).isEqualTo(ReminderRunResult.of(0, 1));
  }

  @Test
  @DisplayName("一个用户失败，另一个照样提醒")
  void should_continue_after_one_user_fails() {
    pendingUsers(41L, 42L);
    when(users.findByIdForUpdate(41L)).thenThrow(new IllegalStateException("boom"));

    ReminderRunResult result = handler.handle(SendAccountDeletionRemindersCommand.of());

    assertThat(result).isEqualTo(ReminderRunResult.of(1, 1));
    verify(emailDelivery, times(1)).send(any());
  }

  /// 这些 ID 是第一页该提醒的用户。
  ///
  /// @param ids 用户 ID
  private void pendingUsers(Long... ids) {
    when(users.findIdsDueForDeletionReminder(NOW, NOW.plus(LEAD), 0L, 100))
        .thenReturn(List.of(ids));
    for (long id : ids) {
      when(users.findByIdForUpdate(id)).thenAnswer(invocation -> Optional.of(pending(id)));
      when(userReadPort.findAccount(id))
          .thenReturn(
              Optional.of(
                  UserAccountReadModel.builder()
                      .userId(id)
                      .email(email(id).value())
                      .username("u-" + id)
                      .displayName("陈宇")
                      .status(UserStatus.PENDING_DELETION)
                      .build()));
    }
  }

  /// 冷静期内、还没提醒过的用户。
  ///
  /// @param id 用户 ID
  /// @return 用户
  private static User pending(long id) {
    return User.restore()
        .id(id)
        .email(email(id))
        .username(Username.of("u-" + id))
        .status(UserStatus.PENDING_DELETION)
        .deletionDueAt(DUE)
        .version(1L)
        .build();
  }

  /// 用户的邮箱。
  ///
  /// @param id 用户 ID
  /// @return 邮箱
  private static EmailAddress email(long id) {
    return EmailAddress.of("u" + id + "@example.com");
  }
}
