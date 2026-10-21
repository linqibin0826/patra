package dev.patra.identity.domain.service;

import dev.patra.identity.domain.model.aggregate.EmailToken;
import dev.patra.identity.domain.port.mail.EmailVerificationMail;
import java.util.Objects;

/// 签发好的验证令牌和对应的邮件：令牌已落库（只有哈希），邮件里的链接带着原文，等事务提交后发出去。
///
/// @param token 已保存的令牌
/// @param mail 要发的验证邮件
public record PreparedVerificationMail(EmailToken token, EmailVerificationMail mail) {

  /// 两个都不能为 `null`。
  public PreparedVerificationMail {
    Objects.requireNonNull(token, "token 不能为 null");
    Objects.requireNonNull(mail, "mail 不能为 null");
  }

  /// 创建。
  ///
  /// @param token 已保存的令牌
  /// @param mail 要发的验证邮件
  /// @return 结果
  public static PreparedVerificationMail of(EmailToken token, EmailVerificationMail mail) {
    return new PreparedVerificationMail(token, mail);
  }

  /// 只输出令牌，不输出邮件内容。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "PreparedVerificationMail[token=" + token + "]";
  }
}
