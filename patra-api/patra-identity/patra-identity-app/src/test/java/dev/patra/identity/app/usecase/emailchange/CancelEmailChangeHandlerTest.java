package dev.patra.identity.app.usecase.emailchange;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.exception.UserPendingDeletionException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/// CancelEmailChangeHandler 单元测试：作废待确认的令牌，没有也成功；注销中 409。
@DisplayName("CancelEmailChangeHandler 单元测试")
class CancelEmailChangeHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final EmailTokenRepository tokens = mock(EmailTokenRepository.class);
  private final CancelEmailChangeHandler handler =
      new CancelEmailChangeHandler(
          currentUserPort,
          users,
          tokens,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("作废 EMAIL_CHANGE_CONFIRM；0 行也不抛")
  void should_revoke_pending_confirm_token_even_when_none() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
    when(tokens.revokeActive(42L, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW)).thenReturn(0);

    assertThatCode(() -> handler.handle(CancelEmailChangeCommand.of())).doesNotThrowAnyException();
    verify(tokens).revokeActive(42L, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, NOW);
  }

  @Test
  @DisplayName("注销冷静期内：409，不碰令牌")
  void should_reject_pending_deletion() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user(UserStatus.PENDING_DELETION)));

    assertThatThrownBy(() -> handler.handle(CancelEmailChangeCommand.of()))
        .isInstanceOf(UserPendingDeletionException.class);
    verifyNoInteractions(tokens);
  }

  /// 指定状态的用户 42。
  ///
  /// @param status 状态
  /// @return 用户
  private static User user(UserStatus status) {
    User.UserBuilder builder =
        User.restore()
            .id(42L)
            .email(EmailAddress.of("chen.yu@example.com"))
            .username(Username.of("chen-yu"))
            .status(status)
            .version(0L);
    return status == UserStatus.PENDING_DELETION
        ? builder.deletionDueAt(Instant.EPOCH).build()
        : builder.build();
  }
}
