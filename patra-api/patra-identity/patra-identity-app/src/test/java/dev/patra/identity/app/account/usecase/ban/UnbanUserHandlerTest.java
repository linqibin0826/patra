package dev.patra.identity.app.account.usecase.ban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.UserNotFoundException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/// UnbanUserHandler 单元测试。
@DisplayName("UnbanUserHandler 单元测试")
class UnbanUserHandlerTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");

  private final UserRepository users = mock(UserRepository.class);
  private final UnbanUserHandler handler =
      new UnbanUserHandler(users, TransactionOperations.withoutTransaction());

  @Test
  @DisplayName("解封已封禁的用户：回到正常，清掉封禁时间")
  void should_unban_banned_user() {
    when(users.findById(42L))
        .thenReturn(
            Optional.of(
                User.restore()
                    .id(42L)
                    .email(EMAIL)
                    .username(Username.of("chen-yu"))
                    .status(UserStatus.BANNED)
                    .bannedAt(Instant.parse("2026-10-06T08:00:00Z"))
                    .version(1L)
                    .build()));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(UnbanUserCommand.of(42L));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.getValue().getBannedAt()).isNull();
  }

  @Test
  @DisplayName("用户不存在返回 404")
  void should_throw_when_user_not_found() {
    when(users.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(UnbanUserCommand.of(404L)))
        .isInstanceOf(UserNotFoundException.class);
  }
}
