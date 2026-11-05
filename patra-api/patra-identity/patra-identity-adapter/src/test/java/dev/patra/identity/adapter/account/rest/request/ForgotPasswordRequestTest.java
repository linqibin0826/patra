package dev.patra.identity.adapter.account.rest.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 找回密码申请的请求体。
@DisplayName("ForgotPasswordRequest")
class ForgotPasswordRequestTest {

  @Test
  @DisplayName("toString 不输出人机验证令牌")
  void to_string_hides_verification_token() {
    assertThat(new ForgotPasswordRequest("chen.yu@example.com", "Verify-Value-1").toString())
        .doesNotContain("Verify-Value-1");
  }
}
