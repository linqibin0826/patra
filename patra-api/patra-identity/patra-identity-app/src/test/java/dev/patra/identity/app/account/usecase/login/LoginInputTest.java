package dev.patra.identity.app.account.usecase.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.tool.core.security.ClientType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginInput 单元测试：解析成功得到合法的值对象，失败时字段错误一次报全。
@DisplayName("LoginInput 单元测试")
class LoginInputTest {

  private static final String DEVICE = "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";

  @Test
  @DisplayName("字段都合法：得到规范化之后的值对象，密码保留原文")
  void should_parse_valid_fields_into_value_objects() {
    LoginInput input = LoginInput.parse(command("Chen.Yu@Example.com", " pass ", "web", DEVICE));

    assertThat(input.email()).isEqualTo(EmailAddress.of("chen.yu@example.com"));
    assertThat(input.password().value()).isEqualTo(" pass ");
    assertThat(input.client().clientType()).isEqualTo(ClientType.WEB);
    assertThat(input.client().device()).map(DeviceId::value).contains(DEVICE);
  }

  @Test
  @DisplayName("客户端类型和设备标识没传：按网页端、没有设备")
  void should_default_client_when_absent() {
    LoginInput input = LoginInput.parse(command("chen.yu@example.com", "pass", null, null));

    assertThat(input.client().clientType()).isEqualTo(ClientType.WEB);
    assertThat(input.client().device()).isEmpty();
  }

  @Test
  @DisplayName("四个字段都错：一次报全，顺序固定，错误码不变")
  void should_report_all_violations_in_order() {
    assertThatThrownBy(() -> LoginInput.parse(command(null, "", "app", "x".repeat(129))))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly(
                        "email:REQUIRED",
                        "password:REQUIRED",
                        "clientType:INVALID_FORMAT",
                        "deviceId:INVALID_FORMAT"));
  }

  @Test
  @DisplayName("只有一个字段错：只报它")
  void should_report_only_invalid_field() {
    assertThatThrownBy(() -> LoginInput.parse(command("chen.yu@example.com", null, null, null)))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("password:REQUIRED"));
  }

  /// 只带四个基本字段的登录命令。
  ///
  /// @param email 邮箱
  /// @param password 密码
  /// @param clientType 客户端类型
  /// @param deviceId 设备标识
  /// @return 命令
  private static LoginUserCommand command(
      String email, String password, String clientType, String deviceId) {
    return LoginUserCommand.builder()
        .email(email)
        .password(password)
        .clientType(clientType)
        .deviceId(deviceId)
        .build();
  }
}
