package dev.linqibin.patra.starter.security.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

/// CurrentUserRunner 单元测试。
@DisplayName("CurrentUserRunner 单元测试")
class CurrentUserRunnerTest {

  private static final CurrentUser ALICE =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);
  private static final CurrentUser BOB =
      CurrentUser.of(1002L, 2002L, AccountType.USER, ClientType.WEB);

  private final CurrentUserPort currentUserPort = new SecurityContextCurrentUserAdapter();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("执行期间能取到指定的用户")
  void should_expose_user_inside_run_as() {
    AtomicReference<Optional<CurrentUser>> seen = new AtomicReference<>();

    CurrentUserRunner.runAs(ALICE, () -> seen.set(currentUserPort.current()));

    assertThat(seen.get()).contains(ALICE);
  }

  @Test
  @DisplayName("callAs 返回动作的结果")
  void should_return_action_result_when_call_as() {
    long userId = CurrentUserRunner.callAs(ALICE, () -> currentUserPort.require().userId());

    assertThat(userId).isEqualTo(1001L);
  }

  @Test
  @DisplayName("原来没有身份时，执行结束后清空")
  void should_clear_context_after_run_when_no_previous_identity() {
    CurrentUserRunner.runAs(ALICE, () -> {});

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("原来有身份时，执行结束后恢复原来的身份")
  void should_restore_previous_identity_after_run() {
    SecurityContextHolder.getContext().setAuthentication(new CurrentUserAuthentication(BOB));

    CurrentUserRunner.runAs(ALICE, () -> {});

    assertThat(currentUserPort.current()).contains(BOB);
  }

  @Test
  @DisplayName("动作抛异常时也恢复原状，异常原样抛出")
  void should_restore_context_when_action_throws() {
    IllegalStateException failure = new IllegalStateException("boom");

    assertThatThrownBy(
            () ->
                CurrentUserRunner.runAs(
                    ALICE,
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);

    assertThat(currentUserPort.current()).isEmpty();
  }

  @Test
  @DisplayName("嵌套调用时，内层结束（包括抛异常）后回到外层的身份")
  void should_restore_outer_identity_after_nested_run() {
    List<Optional<CurrentUser>> seen = new ArrayList<>();

    CurrentUserRunner.runAs(
        ALICE,
        () -> {
          CurrentUserRunner.runAs(BOB, () -> seen.add(currentUserPort.current()));
          seen.add(currentUserPort.current());
          try {
            CurrentUserRunner.runAs(
                BOB,
                () -> {
                  throw new IllegalStateException("inner");
                });
          } catch (IllegalStateException expected) {
            seen.add(currentUserPort.current());
          }
        });

    assertThat(seen).containsExactly(Optional.of(BOB), Optional.of(ALICE), Optional.of(ALICE));
    assertThat(currentUserPort.current()).isEmpty();
  }
}
