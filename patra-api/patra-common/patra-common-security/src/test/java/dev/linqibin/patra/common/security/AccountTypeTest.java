package dev.linqibin.patra.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// AccountType 单元测试。
@DisplayName("AccountType 单元测试")
class AccountTypeTest {

  @Test
  @DisplayName("user 对应前台用户")
  void should_return_user_when_code_is_user() {
    assertThat(AccountType.fromCode("user")).contains(AccountType.USER);
    assertThat(AccountType.USER.getCode()).isEqualTo("user");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "USER", "portal", "staff", " user"})
  @DisplayName("不认识的字符串返回空")
  void should_return_empty_when_code_is_unknown(String code) {
    assertThat(AccountType.fromCode(code)).isEmpty();
  }
}
