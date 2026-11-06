package dev.patra.identity.domain.account.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 人机验证枚举的线上值：门户和第三方都按它核对，不能随枚举名变化。
@DisplayName("人机验证枚举的线上值")
class HumanVerificationEnumsTest {

  @Test
  @DisplayName("动作的线上值和接口约定一致")
  void action_wire_values_match_contract() {
    assertThat(HumanVerificationAction.LOGIN.wireValue()).isEqualTo("login");
    assertThat(HumanVerificationAction.REGISTER.wireValue()).isEqualTo("register");
    assertThat(HumanVerificationAction.PASSWORD_RESET.wireValue()).isEqualTo("password_reset");
  }

  @Test
  @DisplayName("厂商的线上值和接口约定一致")
  void provider_wire_values_match_contract() {
    assertThat(HumanVerificationProvider.TURNSTILE.wireValue()).isEqualTo("turnstile");
    assertThat(HumanVerificationProvider.TRUST_TURN.wireValue()).isEqualTo("trustturn");
  }
}
