package dev.patra.notification.domain.preference.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// 落地页上的脱敏邮箱：本地部分只留首字符，写法同 identity 的 `EmailAddress.masked()`。
class MaskedEmailTest {

  /// `chen.yu@example.com` → `c***@example.com`。
  @Test
  void should_keep_first_char_and_domain() {
    assertThat(MaskedEmail.of("chen.yu@example.com")).isEqualTo("c***@example.com");
  }

  /// 本地部分只有一个字符时同样补三个星号。
  @Test
  void should_mask_single_char_local_part() {
    assertThat(MaskedEmail.of("a@b.cn")).isEqualTo("a***@b.cn");
  }
}
