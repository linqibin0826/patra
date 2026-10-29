package dev.patra.identity.domain.account.model.vo.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// RorId 单元测试：9 位裸 ID，先去空白转小写再判格式。
@DisplayName("RorId 单元测试")
class RorIdTest {

  @Test
  void should_fold_to_lower_case_and_strip() {
    assertThat(RorId.of(" 04JZTAG35 ").value()).isEqualTo("04jztag35");
  }

  @Test
  void should_reject_non_bare_or_malformed_ids() {
    assertThat(RorId.isValid("https://ror.org/04jztag35")).isFalse();
    assertThat(RorId.isValid("14jztag35")).isFalse();
    assertThat(RorId.isValid("04jztag3")).isFalse();
    assertThat(RorId.isValid("04jztag355")).isFalse();
    assertThat(RorId.isValid("04jztag35")).isTrue();
  }

  @Test
  void should_throw_on_invalid_raw() {
    assertThatThrownBy(() -> RorId.of("x")).isInstanceOf(IllegalArgumentException.class);
  }
}
