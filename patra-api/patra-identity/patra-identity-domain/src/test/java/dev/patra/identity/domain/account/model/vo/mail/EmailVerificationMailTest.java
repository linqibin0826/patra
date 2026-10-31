package dev.patra.identity.domain.account.model.vo.mail;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// EmailVerificationMail 单元测试：字段都不能为 null（事件 ID 与 toString 的规则见 OutgoingMailTest）。
@DisplayName("EmailVerificationMail 单元测试")
class EmailVerificationMailTest {

  @Test
  @DisplayName("收件人、昵称、链接、过期时间都不能为 null")
  void should_reject_null_fields() {
    EmailAddress to = EmailAddress.of("a@example.com");
    assertThatThrownBy(() -> build(null, "a", "u", Instant.EPOCH))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> build(to, null, "u", Instant.EPOCH))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> build(to, "a", null, Instant.EPOCH))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> build(to, "a", "u", null)).isInstanceOf(NullPointerException.class);
  }

  /// 用 builder 建一封验证邮件。
  ///
  /// @param to 收件人
  /// @param displayName 昵称
  /// @param verifyUrl 链接
  /// @param linkExpiresAt 过期时间
  /// @return 邮件
  private static EmailVerificationMail build(
      EmailAddress to, String displayName, String verifyUrl, Instant linkExpiresAt) {
    return EmailVerificationMail.builder()
        .to(to)
        .displayName(displayName)
        .verifyUrl(verifyUrl)
        .linkExpiresAt(linkExpiresAt)
        .build();
  }
}
