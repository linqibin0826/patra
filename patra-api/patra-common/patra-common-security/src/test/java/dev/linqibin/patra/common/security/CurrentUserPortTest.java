package dev.linqibin.patra.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// CurrentUserPort 单元测试：`require()` 的默认实现。
@DisplayName("CurrentUserPort 单元测试")
class CurrentUserPortTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

  @Test
  @DisplayName("有当前用户时 require 返回它")
  void should_return_user_when_require_and_user_present() {
    CurrentUserPort port = () -> Optional.of(USER);

    assertThat(port.require()).isEqualTo(USER);
  }

  @Test
  @DisplayName("没有当前用户时 require 抛出带 UNAUTHORIZED 特征的异常")
  void should_throw_authentication_required_when_require_and_no_user() {
    CurrentUserPort port = Optional::empty;

    assertThatThrownBy(port::require)
        .isInstanceOfSatisfying(
            AuthenticationRequiredException.class,
            exception -> {
              assertThat(exception.getErrorTraits())
                  .containsExactly(StandardErrorTrait.UNAUTHORIZED);
              assertThat(exception).hasMessage("Authentication required");
            });
  }
}
