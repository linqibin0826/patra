package dev.linqibin.patra.identity.adapter.rest.auth.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginRequest 单元测试。
@DisplayName("LoginRequest 单元测试")
class LoginRequestTest {

  @Test
  @DisplayName("toString 不输出密码")
  void should_hide_password_in_to_string() {
    assertThat(new LoginRequest("chen.yu@example.com", "Secret-Value-1").toString())
        .contains("chen.yu@example.com")
        .doesNotContain("Secret-Value-1");
  }
}
