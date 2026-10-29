package dev.patra.identity.domain.account.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// EmailTokenPurpose 单元测试。
@DisplayName("EmailTokenPurpose 单元测试")
class EmailTokenPurposeTest {

  @Test
  @DisplayName("每种用途有自己的有效期")
  void should_define_validity_per_purpose() {
    assertThat(EmailTokenPurpose.EMAIL_VERIFY.validity()).isEqualTo(Duration.ofHours(24));
    assertThat(EmailTokenPurpose.PASSWORD_RESET.validity()).isEqualTo(Duration.ofHours(1));
    assertThat(EmailTokenPurpose.EMAIL_CHANGE_CONFIRM.validity()).isEqualTo(Duration.ofHours(1));
    assertThat(EmailTokenPurpose.EMAIL_CHANGE_REVERT.validity()).isEqualTo(Duration.ofDays(7));
  }
}
