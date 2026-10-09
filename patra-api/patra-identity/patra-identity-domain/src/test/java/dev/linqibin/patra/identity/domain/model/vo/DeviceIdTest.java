package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// DeviceId 单元测试。
@DisplayName("DeviceId 单元测试")
class DeviceIdTest {

  @Test
  @DisplayName("null 和空白按没有设备标识：不算错，也不建对象")
  void should_treat_blank_as_absent() {
    assertThat(DeviceId.validate(null)).isEmpty();
    assertThat(DeviceId.validate("   ")).isEmpty();
    assertThat(DeviceId.of(null)).isEmpty();
    assertThat(DeviceId.of("  ")).isEmpty();
  }

  @Test
  @DisplayName("去掉首尾空白后保存")
  void should_strip_surrounding_whitespace() {
    assertThat(DeviceId.of("  mac-safari ")).map(DeviceId::value).contains("mac-safari");
  }

  @Test
  @DisplayName("长度按码点数：128 个表情通过，129 个报 TOO_LONG")
  void should_count_device_id_length_by_code_points() {
    String okay = "😀".repeat(128);
    String tooLong = "😀".repeat(129);

    assertThat(DeviceId.validate(okay)).isEmpty();
    assertThat(DeviceId.of(okay)).map(DeviceId::value).contains(okay);
    assertThat(DeviceId.validate(tooLong))
        .get()
        .satisfies(
            violation -> {
              assertThat(violation.field()).isEqualTo("deviceId");
              assertThat(violation.code()).isEqualTo("TOO_LONG");
            });
    assertThatThrownBy(() -> DeviceId.of(tooLong)).isInstanceOf(InvalidUserFieldsException.class);
  }

  @Test
  @DisplayName("直接构造只接受规范化之后的值")
  void should_reject_unnormalized_value_in_constructor() {
    assertThatThrownBy(() -> new DeviceId(" x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DeviceId("")).isInstanceOf(IllegalArgumentException.class);
  }
}
