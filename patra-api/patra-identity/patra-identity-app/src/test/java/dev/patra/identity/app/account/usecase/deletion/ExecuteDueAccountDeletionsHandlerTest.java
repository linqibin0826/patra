package dev.patra.identity.app.account.usecase.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.AvatarStorageException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionCompletedMail;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.model.vo.profile.Bio;
import dev.patra.identity.domain.account.model.vo.profile.ProfileContent;
import dev.patra.identity.domain.account.port.avatar.AvatarStoragePort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/// ExecuteDueAccountDeletionsHandler 单元测试：匿名化清单、锁内复核、单个失败不影响其他、键集翻页、「已注销」与匿名化同事务登记。
@DisplayName("ExecuteDueAccountDeletionsHandler 单元测试")
class ExecuteDueAccountDeletionsHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-04T08:05:00Z");
  private static final Instant DUE = Instant.parse("2026-11-04T08:00:00Z");

  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final UserProfileRepository profiles = mock(UserProfileRepository.class);
  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final AvatarStoragePort avatarStorage = mock(AvatarStoragePort.class);
  private final SessionTerminator sessionTerminator = mock(SessionTerminator.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final List<String> events = new ArrayList<>();
  private final ExecuteDueAccountDeletionsHandler handler =
      new ExecuteDueAccountDeletionsHandler(
          users,
          userReadPort,
          credentials,
          profiles,
          tokens,
          avatarStorage,
          sessionTerminator,
          emailDelivery,
          new RecordingTransactions(events),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(users.findIdsDueForDeletion(eq(NOW), anyLong(), eq(100))).thenReturn(List.of());
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessionTerminator.terminateAll(anyLong(), any(), any())).thenReturn(List.of());
    doAnswer(
            invocation -> {
              events.add("send");
              return null;
            })
        .when(emailDelivery)
        .send(any());
  }

  @Test
  @DisplayName("到期用户：转已注销、删凭据、清资料、删令牌、撤会话，同一事务里给原邮箱登记「已注销」")
  void should_register_completion_mail_inside_anonymizing_transaction() {
    dueUsers(42L);
    UserProfile profile = UserProfile.create(42L);
    profile.edit(ProfileContent.builder().bio(new Bio("心内科医生")).build());
    when(profiles.findByUserId(42L)).thenReturn(Optional.of(profile));

    DeletionRunResult result = handler.handle(ExecuteDueAccountDeletionsCommand.of());

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(saved.getValue().getEmail()).isEmpty();
    verify(credentials).deleteByUserId(42L);
    verify(profiles).save(profile);
    assertThat(profile.getBio()).isEmpty();
    verify(tokens).deleteAllByUserId(42L);
    verify(sessionTerminator).terminateAll(42L, LoginEndReason.DELETION_COMPLETED, NOW);
    ArgumentCaptor<OutgoingMail> mail = ArgumentCaptor.forClass(OutgoingMail.class);
    verify(emailDelivery).send(mail.capture());
    AccountDeletionCompletedMail completed = (AccountDeletionCompletedMail) mail.getValue();
    assertThat(completed.to()).isEqualTo(email(42L));
    assertThat(completed.displayName()).isEqualTo("陈宇");
    assertThat(completed.username()).isEqualTo("u-42");
    assertThat(events)
        .containsExactly(RecordingTransactions.BEGIN, "send", RecordingTransactions.END);
    assertThat(result).isEqualTo(DeletionRunResult.of(1, 0));
  }

  @Test
  @DisplayName("扫描之后用户刚取消：锁内复核不到期，跳过、不发信、不计失败")
  void should_skip_user_no_longer_due_after_lock() {
    when(users.findIdsDueForDeletion(NOW, 0L, 100)).thenReturn(List.of(42L));
    when(users.findByIdForUpdate(42L))
        .thenReturn(
            Optional.of(
                User.restore()
                    .id(42L)
                    .email(email(42L))
                    .username(Username.of("u-42"))
                    .status(UserStatus.ACTIVE)
                    .version(1L)
                    .build()));

    DeletionRunResult result = handler.handle(ExecuteDueAccountDeletionsCommand.of());

    assertThat(result).isEqualTo(DeletionRunResult.of(0, 0));
    verify(users, never()).save(any());
    verifyNoInteractions(credentials, tokens, sessionTerminator, emailDelivery);
  }

  @Test
  @DisplayName("头像对象在加锁事务之前按前缀删光")
  void should_delete_avatar_objects_before_locking() {
    dueUsers(42L);

    handler.handle(ExecuteDueAccountDeletionsCommand.of());

    InOrder order = inOrder(avatarStorage, users);
    order.verify(avatarStorage).deleteAll(42L);
    order.verify(users).findByIdForUpdate(42L);
  }

  @Test
  @DisplayName("扫描之后刚取消的用户：不删头像对象")
  void should_not_delete_avatar_objects_when_user_cancelled_after_scan() {
    when(users.findIdsDueForDeletion(NOW, 0L, 100)).thenReturn(List.of(42L));
    User active =
        User.restore()
            .id(42L)
            .email(email(42L))
            .username(Username.of("u-42"))
            .status(UserStatus.ACTIVE)
            .version(1L)
            .build();
    when(users.findById(42L)).thenReturn(Optional.of(active));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(active));

    handler.handle(ExecuteDueAccountDeletionsCommand.of());

    verify(avatarStorage, never()).deleteAll(anyLong());
  }

  @Test
  @DisplayName("删头像对象失败：该用户计失败、不进事务，下一个照常处理")
  void should_count_failure_when_avatar_deletion_fails() {
    dueUsers(41L, 42L);
    doThrow(new AvatarStorageException("boom", new RuntimeException()))
        .when(avatarStorage)
        .deleteAll(41L);

    DeletionRunResult result = handler.handle(ExecuteDueAccountDeletionsCommand.of());

    assertThat(result).isEqualTo(DeletionRunResult.of(1, 1));
    verify(users, never()).findByIdForUpdate(41L);
    verify(tokens).deleteAllByUserId(42L);
  }

  @Test
  @DisplayName("一个用户失败，另一个照样完成")
  void should_continue_after_one_user_fails() {
    dueUsers(41L, 42L);
    doThrow(new IllegalStateException("boom")).when(credentials).deleteByUserId(41L);

    DeletionRunResult result = handler.handle(ExecuteDueAccountDeletionsCommand.of());

    assertThat(result).isEqualTo(DeletionRunResult.of(1, 1));
    verify(tokens).deleteAllByUserId(42L);
    verify(tokens, never()).deleteAllByUserId(41L);
  }

  @Test
  @DisplayName("按 ID 游标翻页：一页满 100 个就以最后一个 ID 继续，不满就停")
  void should_page_by_id_cursor() {
    List<Long> firstPage = LongStream.rangeClosed(1, 100).boxed().toList();
    when(users.findIdsDueForDeletion(NOW, 0L, 100)).thenReturn(firstPage);
    when(users.findIdsDueForDeletion(NOW, 100L, 100)).thenReturn(List.of(101L));
    LongStream.rangeClosed(1, 101).forEach(this::stubPending);

    DeletionRunResult result = handler.handle(ExecuteDueAccountDeletionsCommand.of());

    assertThat(result).isEqualTo(DeletionRunResult.of(101, 0));
    verify(users).findIdsDueForDeletion(NOW, 100L, 100);
    verify(users, never()).findIdsDueForDeletion(NOW, 101L, 100);
  }

  @Test
  @DisplayName("「已注销」登记失败：异常抛出让匿名化一起回滚，计入失败，用户仍在冷静期等下次重试")
  void should_roll_back_and_count_failure_when_registration_fails() {
    dueUsers(42L);
    doThrow(new IllegalStateException("登记表写入失败")).when(emailDelivery).send(any());

    DeletionRunResult result = handler.handle(ExecuteDueAccountDeletionsCommand.of());

    assertThat(result).isEqualTo(DeletionRunResult.of(0, 1));
    verify(emailDelivery).send(any());
    assertThat(events).as("事务没有提交").containsExactly(RecordingTransactions.BEGIN);
  }

  /// 这些 ID 是第一页的到期用户，各自在冷静期且已到期。
  ///
  /// @param ids 用户 ID
  private void dueUsers(Long... ids) {
    when(users.findIdsDueForDeletion(NOW, 0L, 100)).thenReturn(List.of(ids));
    for (long id : ids) {
      stubPending(id);
    }
  }

  /// 冷静期且已到期的用户。
  ///
  /// @param id 用户 ID
  /// @return 用户
  private static User pendingUser(long id) {
    return User.restore()
        .id(id)
        .email(email(id))
        .username(Username.of("u-" + id))
        .status(UserStatus.PENDING_DELETION)
        .deletionDueAt(DUE)
        .version(1L)
        .build();
  }

  /// 给一个冷静期且已到期的用户打桩：加锁读、账号信息。
  ///
  /// @param id 用户 ID
  private void stubPending(long id) {
    when(users.findById(id)).thenAnswer(invocation -> Optional.of(pendingUser(id)));
    when(users.findByIdForUpdate(id)).thenAnswer(invocation -> Optional.of(pendingUser(id)));
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

  /// 用户的邮箱。
  ///
  /// @param id 用户 ID
  /// @return 邮箱
  private static EmailAddress email(long id) {
    return EmailAddress.of("u" + id + "@example.com");
  }
}
