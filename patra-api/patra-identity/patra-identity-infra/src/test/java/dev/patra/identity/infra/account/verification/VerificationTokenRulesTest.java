package dev.patra.identity.infra.account.verification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 发给第三方之前对令牌做的检查。
@DisplayName("VerificationTokenRules")
class VerificationTokenRulesTest {

  @Test
  @DisplayName("普通令牌可以发")
  void accepts_ordinary_token() {
    assertThat(VerificationTokenRules.isSendable("abc.DEF-123_x", 2048)).isTrue();
  }

  @Test
  @DisplayName("空的令牌不发")
  void rejects_null_or_empty() {
    assertThat(VerificationTokenRules.isSendable(null, 2048)).isFalse();
    assertThat(VerificationTokenRules.isSendable("", 2048)).isFalse();
  }

  @Test
  @DisplayName("超过上限不发，正好等于上限可以发")
  void respects_max_length() {
    assertThat(VerificationTokenRules.isSendable("a".repeat(2048), 2048)).isTrue();
    assertThat(VerificationTokenRules.isSendable("a".repeat(2049), 2048)).isFalse();
  }

  @Test
  @DisplayName("含空白或控制字符不发")
  void rejects_whitespace_and_control_characters() {
    assertThat(VerificationTokenRules.isSendable("a b", 2048)).isFalse();
    assertThat(VerificationTokenRules.isSendable("a\nb", 2048)).isFalse();
    assertThat(VerificationTokenRules.isSendable("a\u0000b", 2048)).isFalse();
  }
}
