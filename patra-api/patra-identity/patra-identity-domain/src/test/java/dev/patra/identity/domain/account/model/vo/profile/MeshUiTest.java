package dev.patra.identity.domain.account.model.vo.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// MeshUi 单元测试：`D` + 6 位或 9 位数字，不改大小写。
@DisplayName("MeshUi 单元测试")
class MeshUiTest {

  @Test
  void should_accept_six_and_nine_digit_uis() {
    assertThat(MeshUi.isValid("D003920")).isTrue();
    assertThat(MeshUi.isValid("D000072716")).isTrue();
  }

  @Test
  void should_reject_malformed_uis() {
    assertThat(MeshUi.isValid("d003920")).isFalse();
    assertThat(MeshUi.isValid("D0039201")).isFalse();
    assertThat(MeshUi.isValid("C000001")).isFalse();
  }

  @Test
  void should_strip_raw_value() {
    assertThat(MeshUi.of(" D003920 ").value()).isEqualTo("D003920");
  }

  @Test
  void should_throw_on_invalid_raw() {
    assertThatThrownBy(() -> MeshUi.of("d003920")).isInstanceOf(IllegalArgumentException.class);
  }
}
