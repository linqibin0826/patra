package dev.patra.identity.adapter.account.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ProviderErrorAwareTokenResponseConverter 单元测试。
@DisplayName("ProviderErrorAwareTokenResponseConverter 单元测试")
class ProviderErrorAwareTokenResponseConverterTest {

  private final ProviderErrorAwareTokenResponseConverter converter =
      new ProviderErrorAwareTokenResponseConverter();

  @Test
  @DisplayName("JSON 里有 error：拒绝，错误码进消息，描述不进")
  void should_refuse_when_error_present() {
    assertThatThrownBy(
            () ->
                converter.convert(
                    Map.of("error", "bad_verification_code", "error_description", "secret-detail")))
        .isInstanceOf(ProviderTokenRefusedException.class)
        .hasMessageContaining("bad_verification_code")
        .hasMessageNotContaining("secret-detail");
  }

  @Test
  @DisplayName("正常的令牌响应：交给框架的默认实现")
  void should_delegate_normal_response() {
    assertThat(
            converter
                .convert(Map.of("access_token", "t", "token_type", "bearer"))
                .getAccessToken()
                .getTokenValue())
        .isEqualTo("t");
  }
}
