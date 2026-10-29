package dev.patra.identity.domain.account.service;

import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import java.util.Objects;

/// 签发好的令牌：已落库的聚合（只有哈希）和原文。原文只在这里出现一次，调用方用它拼邮件里的链接。
///
/// @param token 已保存的令牌
/// @param secret 令牌原文
public record IssuedEmailToken(EmailToken token, EmailTokenSecret secret) {

  /// 两个都不能为 `null`。
  public IssuedEmailToken {
    Objects.requireNonNull(token, "token 不能为 null");
    Objects.requireNonNull(secret, "secret 不能为 null");
  }

  /// 创建。
  ///
  /// @param token 已保存的令牌
  /// @param secret 令牌原文
  /// @return 结果
  public static IssuedEmailToken of(EmailToken token, EmailTokenSecret secret) {
    return new IssuedEmailToken(token, secret);
  }

  /// 只输出令牌，不输出原文。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "IssuedEmailToken[token=" + token + "]";
  }
}
