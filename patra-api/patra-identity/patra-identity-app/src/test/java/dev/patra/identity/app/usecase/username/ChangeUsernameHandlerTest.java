package dev.patra.identity.app.usecase.username;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.exception.UserPendingDeletionException;
import dev.patra.identity.domain.exception.UsernameChangeCooldownException;
import dev.patra.identity.domain.model.aggregate.FormerUsername;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.policy.UsernameChangePolicy;
import dev.patra.identity.domain.policy.UsernamePolicy;
import dev.patra.identity.domain.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionOperations;

/// ChangeUsernameHandler 单元测试。
@DisplayName("ChangeUsernameHandler 单元测试")
class ChangeUsernameHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
  private static final UsernameChangePolicy POLICY =
      UsernameChangePolicy.of(Duration.ofDays(30), Duration.ofDays(90));
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final Username USERNAME = Username.of("chen-yu");

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final FormerUsernameRepository formerUsernames = mock(FormerUsernameRepository.class);
  private final ChangeUsernameHandler handler =
      new ChangeUsernameHandler(
          currentUserPort,
          new UsernamePolicy(),
          users,
          formerUsernames,
          POLICY,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 默认：当前用户 42，正常状态，每次加锁读都给一个新实例（聚合会被处理器改掉），保存原样返回。
  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findByIdForUpdate(42L)).thenAnswer(invocation -> Optional.of(activeUser(null)));
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(formerUsernames.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  /// 一个正常用户。
  ///
  /// @param usernameChangedAt 最近一次改名时间，可以为 `null`
  /// @return 用户
  private static User activeUser(Instant usernameChangedAt) {
    return User.restore()
        .id(42L)
        .email(EMAIL)
        .username(USERNAME)
        .usernameChangedAt(usernameChangedAt)
        .status(UserStatus.ACTIVE)
        .version(0L)
        .build();
  }

  @Test
  @DisplayName("改名成功：保存新名、记时间、旧名保留 90 天，返回新名和下次可改时间")
  void should_change_name_and_reserve_previous() {
    ChangeUsernameResult result = handler.handle(ChangeUsernameCommand.of(" Chen-Yu-2 "));

    assertThat(result.username()).isEqualTo("chen-yu-2");
    assertThat(result.nextUsernameChangeAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
    verify(users).save(user.capture());
    assertThat(user.getValue().getUsername().value()).isEqualTo("chen-yu-2");
    assertThat(user.getValue().getUsernameChangedAt()).isEqualTo(NOW);
    ArgumentCaptor<FormerUsername> former = ArgumentCaptor.forClass(FormerUsername.class);
    verify(formerUsernames).save(former.capture());
    assertThat(former.getValue().getUserId()).isEqualTo(42L);
    assertThat(former.getValue().getUsername()).isEqualTo(USERNAME);
    assertThat(former.getValue().getReplacedAt()).isEqualTo(NOW);
    assertThat(former.getValue().getReservedUntil()).isEqualTo(NOW.plus(Duration.ofDays(90)));
    InOrder order = inOrder(users, formerUsernames);
    order.verify(users).findByIdForUpdate(42L);
    order.verify(users).existsByUsername(Username.of("chen-yu-2"));
    order.verify(formerUsernames).existsReservedByOther(Username.of("chen-yu-2"), 42L, NOW);
    order.verify(users).save(any());
    order.verify(formerUsernames).save(any());
  }

  @Test
  @DisplayName("用户名不合法：422，不碰仓储")
  void should_reject_invalid_username_before_touching_repository() {
    assertThatThrownBy(() -> handler.handle(ChangeUsernameCommand.of("ab")))
        .isInstanceOf(InvalidUserFieldsException.class);
    assertThatThrownBy(() -> handler.handle(ChangeUsernameCommand.of("admin")))
        .isInstanceOf(InvalidUserFieldsException.class);
    verifyNoInteractions(users, formerUsernames);
  }

  @Test
  @DisplayName("被别人占用或保留：409 只有 username 一条；自己的旧名可以改回")
  void should_reject_taken_or_reserved_by_other() {
    when(users.existsByUsername(Username.of("chen-yu-2"))).thenReturn(true);
    assertThatThrownBy(() -> handler.handle(ChangeUsernameCommand.of("chen-yu-2")))
        .isInstanceOfSatisfying(
            UserIdentifierTakenException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .singleElement()
                    .satisfies(
                        v -> {
                          assertThat(v.field()).isEqualTo("username");
                          assertThat(v.code()).isEqualTo("ALREADY_TAKEN");
                        }));

    when(users.existsByUsername(Username.of("chen-yu-2"))).thenReturn(false);
    when(formerUsernames.existsReservedByOther(Username.of("chen-yu-2"), 42L, NOW))
        .thenReturn(true);
    assertThatThrownBy(() -> handler.handle(ChangeUsernameCommand.of("chen-yu-2")))
        .isInstanceOf(UserIdentifierTakenException.class);
    verify(users, never()).save(any());
    verify(formerUsernames, never()).save(any());

    when(formerUsernames.existsReservedByOther(Username.of("chen-yu-2"), 42L, NOW))
        .thenReturn(false);
    assertThat(handler.handle(ChangeUsernameCommand.of("chen-yu-2")).username())
        .isEqualTo("chen-yu-2");
  }

  @Test
  @DisplayName("冷却期内、冷静期内都是 409：聚合抛的异常原样传出，不保存")
  void should_propagate_cooldown_and_status_exceptions() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(Optional.of(activeUser(NOW.minus(Duration.ofDays(10)))));
    assertThatThrownBy(() -> handler.handle(ChangeUsernameCommand.of("chen-yu-2")))
        .isInstanceOf(UsernameChangeCooldownException.class);

    User pending = activeUser(null);
    pending.requestDeletion(NOW, Duration.ofDays(15));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(pending));
    assertThatThrownBy(() -> handler.handle(ChangeUsernameCommand.of("chen-yu-2")))
        .isInstanceOf(UserPendingDeletionException.class);

    verify(users, never()).save(any());
    verifyNoInteractions(formerUsernames);
  }

  @Test
  @DisplayName("会话指向的用户不存在：401")
  void should_return_401_when_user_row_missing() {
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(ChangeUsernameCommand.of("chen-yu-2")))
        .isInstanceOf(AuthenticationRequiredException.class);
  }
}
