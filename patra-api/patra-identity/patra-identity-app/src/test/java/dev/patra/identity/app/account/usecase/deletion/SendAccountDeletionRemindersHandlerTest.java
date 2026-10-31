package dev.patra.identity.app.account.usecase.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import dev.patra.tool.test.transaction.RecordingTransactions;
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

/// SendAccountDeletionRemindersHandler 单元测试：标记与登记提醒同一事务、锁内复核、登记失败计入失败、单个失败不影响其他。
@DisplayName("SendAccountDeletionRemindersHandler 单元测试")
class SendAccountDeletionRemindersHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-01T08:15:00Z");
  private static final Instant DUE = Instant.parse("2026-11-04T08:00:00Z");
  private static final Duration LEAD = Duration.ofDays(3);

  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final List<Instant> savedReminders = new ArrayList<>();
  private final List<String> events = new ArrayList<>();
  private final SendAccountDeletionRemindersHandler handler =
      new SendAccountDeletionRemindersHandler(
          users,
          userReadPort,
          AccountDeletionPolicy.of(Duration.ofDays(15), LEAD),
          emailDelivery,
          new RecordingTransactions(events),
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
              events.add("save");
              return user;
            });
    doAnswer(
            invocation -> {
              events.add("send");
              return null;
            })
        .when(emailDelivery)
        .send(any());
  }

  @Test
  @DisplayName("该提醒的用户：记下提醒时间与登记「将在 3 天后删除」在同一个事务里")
  void should_mark_reminder_and_register_mail_in_one_transaction() {
    pendingUsers(42L);

    ReminderRunResult result = handler.handle(SendAccountDeletionRemindersCommand.of());

    assertThat(savedReminders).containsExactly(NOW);
    assertThat(events)
        .containsExactly(RecordingTransactions.BEGIN, "save", "send", RecordingTransactions.END);
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
  @DisplayName("登记失败：抛出让标记随事务回滚（不另开事务清标记），计入失败，下次运行重试")
  void should_count_failure_and_keep_unmarked_when_registration_fails() {
    pendingUsers(42L);
    doThrow(new IllegalStateException("登记表写入失败")).when(emailDelivery).send(any());

    ReminderRunResult result = handler.handle(SendAccountDeletionRemindersCommand.of());

    assertThat(savedReminders).containsExactly(NOW);
    verify(emailDelivery).send(any());
    assertThat(events).as("事务没有提交，也没有另开事务清标记").containsExactly(RecordingTransactions.BEGIN, "save");
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
