package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginClient 单元测试。
@DisplayName("LoginClient 单元测试")
class LoginClientTest {

  @Test
  @DisplayName("客户端类型不传或为空按 web；传了 web（允许首尾空白）也是 web")
  void should_default_client_type_to_web() {
    assertThat(LoginClient.validate(null, null)).isEmpty();
    assertThat(LoginClient.of(null, null).clientType()).isEqualTo(ClientType.WEB);
    assertThat(LoginClient.of("", null).clientType()).isEqualTo(ClientType.WEB);
    assertThat(LoginClient.of(" web ", null).clientType()).isEqualTo(ClientType.WEB);
    assertThat(LoginClient.of(null, null).device()).isEmpty();
    assertThat(LoginClient.web()).isEqualTo(LoginClient.of(null, null));
  }

  @Test
  @DisplayName("不认识的客户端类型报 INVALID_FORMAT；大小写不同也不认识")
  void should_reject_unknown_client_type() {
    assertThat(LoginClient.validate("app", null))
        .singleElement()
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly("clientType", "INVALID_FORMAT");
    assertThat(LoginClient.validate("WEB", null)).hasSize(1);
  }

  @Test
  @DisplayName("两个字段的错误一次报全；of 抛 InvalidUserFieldsException")
  void should_report_both_violations_at_once() {
    String tooLong = "x".repeat(129);

    assertThat(LoginClient.validate("app", tooLong))
        .extracting(FieldViolation::field)
        .containsExactly("clientType", "deviceId");
    assertThatThrownBy(() -> LoginClient.of("app", tooLong))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e -> assertThat(((InvalidUserFieldsException) e).getFieldViolations()).hasSize(2));
  }

  @Test
  @DisplayName("设备标识原样带过去")
  void should_carry_device_id() {
    LoginClient client = LoginClient.of("web", " mac-safari ");

    assertThat(client.device()).map(DeviceId::value).contains("mac-safari");
  }
}
