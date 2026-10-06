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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// BanUserHandler 单元测试。
@DisplayName("BanUserHandler 单元测试")
class BanUserHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-06T08:00:00Z");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");

  private final UserRepository users = mock(UserRepository.class);
  private final BanUserHandler handler =
      new BanUserHandler(users, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("封禁正常用户：状态改为封禁，封禁时间取时钟")
  void should_ban_active_user() {
    when(users.findById(42L))
        .thenReturn(Optional.of(User.restore(42L, EMAIL, UserStatus.ACTIVE, null, 0L, null, null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(BanUserCommand.of(42L));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(saved.getValue().getBannedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("重复封禁保留原来的封禁时间")
  void should_keep_original_ban_time() {
    Instant earlier = Instant.parse("2026-10-01T00:00:00Z");
    when(users.findById(42L))
        .thenReturn(
            Optional.of(User.restore(42L, EMAIL, UserStatus.BANNED, earlier, 1L, null, null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    handler.handle(BanUserCommand.of(42L));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getBannedAt()).isEqualTo(earlier);
  }

  @Test
  @DisplayName("用户不存在返回 404")
  void should_throw_when_user_not_found() {
    when(users.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(BanUserCommand.of(404L)))
        .isInstanceOf(UserNotFoundException.class);
  }
}
