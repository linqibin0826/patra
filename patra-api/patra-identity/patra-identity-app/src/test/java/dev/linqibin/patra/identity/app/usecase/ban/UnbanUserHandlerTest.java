package dev.linqibin.patra.identity.app.usecase.ban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.identity.domain.exception.UserNotFoundException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// UnbanUserHandler 单元测试。
@DisplayName("UnbanUserHandler 单元测试")
class UnbanUserHandlerTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");

  private final UserRepository users = mock(UserRepository.class);
  private final UnbanUserHandler handler = new UnbanUserHandler(users);

  @Test
  @DisplayName("解封已封禁的用户：回到正常，清掉封禁时间")
  void should_unban_banned_user() {
    when(users.findById(42L))
        .thenReturn(
            Optional.of(
                User.restore(
                    42L,
                    EMAIL,
                    UserStatus.BANNED,
                    Instant.parse("2026-10-06T08:00:00Z"),
                    1L,
                    null,
                    null)));
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
