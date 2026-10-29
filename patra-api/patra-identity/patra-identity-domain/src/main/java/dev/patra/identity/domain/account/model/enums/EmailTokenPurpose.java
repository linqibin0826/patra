package dev.patra.identity.domain.account.model.enums;

import java.time.Duration;

/// 一次性邮件令牌的用途。每种用途有自己的有效期，有效期是产品定死的数，不进配置。
public enum EmailTokenPurpose {
  /// 验证邮箱，发给用户当前的邮箱。
  EMAIL_VERIFY(Duration.ofHours(24)),
  /// 重设密码，发给用户当前的邮箱。
  PASSWORD_RESET(Duration.ofHours(1)),
  /// 确认改邮箱，发给待确认的新邮箱。
  EMAIL_CHANGE_CONFIRM(Duration.ofHours(1)),
  /// 撤销改邮箱，发给改之前的邮箱；邮件里的「不是我」链接把邮箱改回去，所以窗口最长。
  EMAIL_CHANGE_REVERT(Duration.ofDays(7));

  private final Duration validity;

  /// 每种用途绑定自己的有效期。
  ///
  /// @param validity 令牌从签发起的有效期
  EmailTokenPurpose(Duration validity) {
    this.validity = validity;
  }

  /// 令牌从签发起的有效期。
  ///
  /// @return 有效期
  public Duration validity() {
    return validity;
  }
}
