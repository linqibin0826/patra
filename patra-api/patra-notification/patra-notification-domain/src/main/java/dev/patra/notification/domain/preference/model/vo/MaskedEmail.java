package dev.patra.notification.domain.preference.model.vo;

import java.util.Objects;

/// 退订落地页显示的脱敏邮箱：本地部分只留首字符，`c***@example.com`（同 identity 的 `EmailAddress.masked()`）。
public final class MaskedEmail {

  private MaskedEmail() {}

  /// 脱敏。
  ///
  /// @param email identity 给的规范化邮箱
  /// @return 脱敏后的邮箱
  public static String of(String email) {
    Objects.requireNonNull(email, "email 不能为 null");
    int at = email.indexOf('@');
    return email.substring(0, 1) + "***" + email.substring(at);
  }
}
