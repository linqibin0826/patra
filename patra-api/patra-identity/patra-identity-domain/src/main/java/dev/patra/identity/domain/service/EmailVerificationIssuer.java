package dev.patra.identity.domain.service;

import dev.patra.identity.domain.model.aggregate.EmailToken;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.policy.MailLinkPolicy;
import dev.patra.identity.domain.port.mail.EmailVerificationMail;
import dev.patra.identity.domain.port.repository.EmailTokenRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/// 给用户签发验证邮箱的令牌并准备邮件：作废旧令牌 → 生成原文 → 存哈希 → 拼链接。
///
/// 必须在调用方的事务里执行；令牌原文只出现在返回的邮件链接里一次，不记日志。
/// 发邮件是调用方的事，要等事务提交之后再做。
public final class EmailVerificationIssuer {

  private final EmailTokenRepository tokens;
  private final MailLinkPolicy links;
  private final Clock clock;
  private final SecureRandom random;

  /// 创建领域服务。
  ///
  /// @param tokens 令牌仓储
  /// @param links 链接规则
  /// @param clock 时钟
  /// @param random 随机源
  public EmailVerificationIssuer(
      EmailTokenRepository tokens, MailLinkPolicy links, Clock clock, SecureRandom random) {
    this.tokens = Objects.requireNonNull(tokens, "tokens 不能为 null");
    this.links = Objects.requireNonNull(links, "links 不能为 null");
    this.clock = Objects.requireNonNull(clock, "clock 不能为 null");
    this.random = Objects.requireNonNull(random, "random 不能为 null");
  }

  /// 签发一枚验证令牌，返回已保存的令牌和要发的邮件。
  ///
  /// @param user 用户，必须还有邮箱
  /// @param displayName 用在邮件称呼里的昵称
  /// @param requestedIp 发起请求的 IP，可以为 `null`
  /// @return 已保存的令牌和邮件
  /// @throws IllegalStateException 用户没有邮箱（已注销）
  public PreparedVerificationMail prepare(User user, String displayName, String requestedIp) {
    Objects.requireNonNull(user, "user 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    EmailAddress email =
        user.getEmail().orElseThrow(() -> new IllegalStateException("没有邮箱的用户不能发验证邮件"));
    Instant now = clock.instant();
    tokens.revokeActive(user.getId(), EmailTokenPurpose.EMAIL_VERIFY, now);
    EmailTokenSecret secret = EmailTokenSecret.generate(random);
    EmailToken saved =
        tokens.save(
            EmailToken.issue(
                EmailTokenPurpose.EMAIL_VERIFY, user.getId(), email, secret, now, requestedIp));
    return PreparedVerificationMail.of(
        saved,
        EmailVerificationMail.of(
            email, displayName, links.verifyEmailUrl(secret), saved.getExpiresAt()));
  }
}
