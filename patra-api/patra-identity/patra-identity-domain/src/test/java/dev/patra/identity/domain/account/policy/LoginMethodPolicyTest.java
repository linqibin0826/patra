package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.exception.LastLoginMethodException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginMethodPolicy 单元测试。
@DisplayName("LoginMethodPolicy 单元测试")
class LoginMethodPolicyTest {

  @Test
  @DisplayName("没有密码且只剩这一种：拒绝")
  void should_reject_last_method_without_password() {
    assertThatThrownBy(() -> LoginMethodPolicy.requireCanUnlink(false, 1))
        .isInstanceOf(LastLoginMethodException.class);
  }

  @Test
  @DisplayName("有密码，或还绑着别的平台：放行")
  void should_allow_when_another_way_in_remains() {
    assertThatCode(() -> LoginMethodPolicy.requireCanUnlink(true, 1)).doesNotThrowAnyException();
    assertThatCode(() -> LoginMethodPolicy.requireCanUnlink(false, 2)).doesNotThrowAnyException();
  }
}
