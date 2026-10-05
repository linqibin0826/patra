package dev.linqibin.patra.starter.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// PatraSecurityProperties 单元测试。
@DisplayName("PatraSecurityProperties 单元测试")
class PatraSecurityPropertiesTest {

  private static final String TOKEN = "test-gateway-token";

  @Test
  @DisplayName("合法的令牌原样保存")
  void should_keep_token_when_valid() {
    assertThat(new PatraSecurityProperties(TOKEN).gatewayToken()).isEqualTo(TOKEN);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\t"})
  @DisplayName("令牌为空或只有空白时拒绝，并指明配置项")
  void should_reject_blank_token(String token) {
    assertThatThrownBy(() -> new PatraSecurityProperties(token))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("patra.security.gateway-token");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {" test-gateway-token", "test-gateway-token\n", "test gateway token", "令牌"})
  @DisplayName("令牌带空白、换行或非 ASCII 字符时拒绝，并指明配置项")
  void should_reject_token_with_whitespace_or_non_ascii(String token) {
    assertThatThrownBy(() -> new PatraSecurityProperties(token))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("patra.security.gateway-token");
  }

  @Test
  @DisplayName("toString 不输出令牌的值")
  void should_mask_token_in_to_string() {
    assertThat(new PatraSecurityProperties(TOKEN).toString()).doesNotContain(TOKEN);
  }
}
