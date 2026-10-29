package dev.patra.identity.domain.account.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.policy.DisplayNamePolicy;
import org.junit.jupiter.api.Test;

/// DisplayName 单元测试。不用 JUnit 的 `@DisplayName`：它和被测的值对象同名。
class DisplayNameTest {

  @Test
  void should_accept_normalized_value() {
    assertThat(new DisplayName("陈 宇").value()).isEqualTo("陈 宇");
  }

  @Test
  void should_reject_unnormalized_or_empty_value() {
    assertThatThrownBy(() -> new DisplayName(" a ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DisplayName("")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_not_treat_every_username_as_valid_display_name() {
    assertThat(new DisplayNamePolicy().validate("weixin123"))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("INVALID_FORMAT"));
  }
}
