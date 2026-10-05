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
  @DisplayName("portal 对应门户用户")
  void should_return_portal_when_code_is_portal() {
    assertThat(AccountType.fromCode("portal")).contains(AccountType.PORTAL);
    assertThat(AccountType.PORTAL.getCode()).isEqualTo("portal");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "PORTAL", "admin", " portal"})
  @DisplayName("不认识的字符串返回空")
  void should_return_empty_when_code_is_unknown(String code) {
    assertThat(AccountType.fromCode(code)).isEmpty();
  }
}
