package dev.patra.identity.domain.account.service;

import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/// 签发一次性邮件令牌，四种用途共用：作废同用途的旧令牌 → 生成原文 → 存哈希。
///
/// 改邮箱的撤销令牌（`EMAIL_CHANGE_REVERT`）不作废旧的：连续改两次邮箱时两个旧邮箱里的「不是我」链接
/// 要各自活满 7 天。必须在调用方的事务里执行，且调用方已经锁了用户行（先锁用户再动令牌）；
/// 拼链接、发邮件是调用方的事，发信要等事务提交之后。
public final class EmailTokenIssuer {

  private final EmailTokenRepository tokens;
  private final Clock clock;
  private final SecureRandom random;

  /// 创建领域服务。
  ///
  /// @param tokens 令牌仓储
  /// @param clock 时钟
  /// @param random 随机源
  public EmailTokenIssuer(EmailTokenRepository tokens, Clock clock, SecureRandom random) {
    this.tokens = Objects.requireNonNull(tokens, "tokens 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
    this.random = Objects.requireNonNull(random, "random 不能为 null");
  }

  /// 签发一枚令牌。
  ///
  /// @param purpose 用途
  /// @param userId 用户 ID
  /// @param email 发往的邮箱（验证、重设是当前邮箱；改邮箱确认是新邮箱；撤销是要改回的旧邮箱）
  /// @param requestedIp 发起请求的 IP，可以为 `null`
  /// @return 已保存的令牌和原文
  public IssuedEmailToken issue(
      EmailTokenPurpose purpose, long userId, EmailAddress email, String requestedIp) {
    Objects.requireNonNull(purpose, "purpose 不能为 null");
    Objects.requireNonNull(email, "email 不能为 null");
    Instant now = clock.instant();
    if (purpose != EmailTokenPurpose.EMAIL_CHANGE_REVERT) {
      tokens.revokeActive(userId, purpose, now);
    }
    EmailTokenSecret secret = EmailTokenSecret.generate(random);
    EmailToken saved =
        tokens.save(EmailToken.issue(purpose, userId, email, secret, now, requestedIp));
    return IssuedEmailToken.of(saved, secret);
  }
}
