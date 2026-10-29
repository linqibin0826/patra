package dev.patra.identity.app.account.usecase.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.DeviceProfile;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionCancelledMail;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/// CancelAccountDeletionHandler 单元测试：冷静期回到正常并发信；本来正常时幂等；封禁、已注销 401。
@DisplayName("CancelAccountDeletionHandler 单元测试")
class CancelAccountDeletionHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-25T08:00:00Z");
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7002L, AccountType.USER, ClientType.WEB);
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final UserAccountReadModel ACCOUNT =
      UserAccountReadModel.builder()
          .userId(42L)
          .email("chen.yu@example.com")
          .emailVerified(true)
          .username("chen-yu")
          .displayName("陈宇")
          .status(UserStatus.ACTIVE)
          .build();

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final CancelAccountDeletionHandler handler =
      new CancelAccountDeletionHandler(
          currentUserPort,
          users,
          userReadPort,
          new ClientOriginResolver(
              userAgent -> DeviceProfile.of("Chrome 141 · macOS", DeviceClass.DESKTOP),
              ip -> Optional.empty()),
          emailDelivery,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.PENDING_DELETION)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(userReadPort.findAccount(42L)).thenReturn(Optional.of(ACCOUNT));
    when(emailDelivery.send(any())).thenReturn(true);
  }

  @Test
  @DisplayName("冷静期内取消：回到正常、清到期时间，提交后发「已取消」，返回账号信息")
  void should_cancel_pending_deletion_and_send_mail() {
    UserAccountReadModel result = handler.handle(command());

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.getValue().getDeletionDueAt()).isNull();
    ArgumentCaptor<OutgoingMail> mail = ArgumentCaptor.forClass(OutgoingMail.class);
    verify(emailDelivery).send(mail.capture());
    assertThat(mail.getValue()).isInstanceOf(AccountDeletionCancelledMail.class);
    assertThat(mail.getValue().to()).isEqualTo(EMAIL);
    assertThat(result).isEqualTo(ACCOUNT);
  }

  @Test
  @DisplayName("本来就正常（重复取消）：不发信，照常返回账号信息")
  void should_be_idempotent_for_active_user_without_mail() {
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.ACTIVE)));

    UserAccountReadModel result = handler.handle(command());

    assertThat(result).isEqualTo(ACCOUNT);
    verifyNoInteractions(emailDelivery);
  }

  @Test
  @DisplayName("封禁、已注销：401，不发信")
  void should_require_authentication_for_banned_or_deleted() {
    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.BANNED)));
    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOf(AuthenticationRequiredException.class);

    when(users.findByIdForUpdate(42L))
        .thenAnswer(invocation -> Optional.of(user(UserStatus.DELETED)));
    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOf(AuthenticationRequiredException.class);

    verifyNoInteractions(emailDelivery);
  }

  /// 带客户端信息的命令。
  ///
  /// @return 命令
  private static CancelAccountDeletionCommand command() {
    return CancelAccountDeletionCommand.of("203.0.113.9", "Mozilla/5.0");
  }

  /// 指定状态的用户，各时间列按状态配好。
  ///
  /// @param status 状态
  /// @return 用户
  private static User user(UserStatus status) {
    User.UserBuilder builder =
        User.restore().id(42L).username(Username.of("chen-yu")).status(status).version(0L);
    return switch (status) {
      case ACTIVE -> builder.email(EMAIL).build();
      case PENDING_DELETION -> builder.email(EMAIL).deletionDueAt(NOW.plusSeconds(60)).build();
      case BANNED -> builder.email(EMAIL).bannedAt(NOW).build();
      case DELETED -> builder.deletedAt(NOW).build();
    };
  }
}
