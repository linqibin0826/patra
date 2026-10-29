package dev.patra.identity.domain.session.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// DeviceId 单元测试：门户生成的 32 字节随机数的 base64url，恰好 43 个字符（PAP-99 设计第 7 节）。
@DisplayName("DeviceId 单元测试")
class DeviceIdTest {

  /// 一个合规的设备标识。
  static final String VALID = "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";

  @Test
  @DisplayName("null 和空白按没有设备标识：不算错，也不建对象")
  void should_treat_blank_as_absent() {
    assertThat(DeviceId.validate(null)).isEmpty();
    assertThat(DeviceId.validate("   ")).isEmpty();
    assertThat(DeviceId.of(null)).isEmpty();
    assertThat(DeviceId.of("  ")).isEmpty();
  }

  @Test
  @DisplayName("43 个 base64url 字符通过")
  void should_accept_43_base64url_characters() {
    assertThat(VALID).hasSize(43);
    assertThat(DeviceId.validate(VALID)).isEmpty();
    assertThat(DeviceId.of(VALID)).map(DeviceId::value).contains(VALID);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wX",
        "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXyz",
        "Kq3+z/0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy",
        "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wX=",
        " Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy",
        "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN\n7oP8qR9sT0uV1w",
        "mac-safari"
      })
  @DisplayName("长度不是 43、含 base64url 以外的字符、带空白或控制字符：INVALID_FORMAT")
  void should_reject_values_outside_the_format(String raw) {
    assertThat(DeviceId.validate(raw))
        .get()
        .satisfies(
            violation -> {
              assertThat(violation.field()).isEqualTo("deviceId");
              assertThat(violation.code()).isEqualTo("INVALID_FORMAT");
            });
    assertThatThrownBy(() -> DeviceId.of(raw)).isInstanceOf(InvalidUserFieldsException.class);
  }

  @Test
  @DisplayName("直接构造只接受合规的值")
  void should_reject_invalid_value_in_constructor() {
    assertThatThrownBy(() -> new DeviceId(" " + VALID.substring(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DeviceId("")).isInstanceOf(IllegalArgumentException.class);
  }
}
