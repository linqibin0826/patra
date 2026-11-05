package dev.patra.identity.app.account.service;

import dev.patra.identity.domain.account.model.enums.RegistrationCodePurpose;
import dev.patra.identity.domain.account.model.vo.CodeCheck;
import dev.patra.identity.domain.account.model.vo.PendingCode;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.RegistrationCode;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 第三方首次登录验证码的发放与校验：注册和就地绑定共用同一套有效期、错误次数和字段错误。
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingRegistrationCodes {

  private final PendingRegistrationPort pendingRegistrations;
  private final ProviderRegistrationPolicy policy;
  private final SecureRandom random = new SecureRandom();

  /// 生成一个验证码并写进存储，覆盖旧的。
  ///
  /// @param ticket 凭证原文
  /// @param registration 待补填记录
  /// @param purpose 用途
  /// @param username 绑定的用户名，已规范化；绑定用途为 `null`
  /// @param email 绑定的邮箱，已规范化
  /// @param now 当前时间
  /// @return 验证码和它的公开部分
  /// @throws InvalidUserFieldsException 待补填记录已经到期
  public IssuedCode issue(
      String ticket,
      PendingRegistration registration,
      RegistrationCodePurpose purpose,
      String username,
      String email,
      Instant now) {
    Duration ttl = ttlWithin(registration, now);
    PendingCode pendingCode =
        PendingCode.builder()
            .purpose(purpose)
            .username(username)
            .email(email)
            .codeExpiresAt(now.plus(ttl))
            .nextResendAt(now.plus(policy.resendCooldown()))
            .build();
    RegistrationCode code = RegistrationCode.generate(random);
    pendingRegistrations.storeCode(ticket, pendingCode, code, ttl);
    return IssuedCode.of(code, pendingCode);
  }

  /// 解析验证码。
  ///
  /// @param rawCode 原始输入
  /// @param violations 字段错误，不合法时往里加
  /// @return 验证码；不合法时为空
  public Optional<RegistrationCode> parse(String rawCode, List<FieldViolation> violations) {
    if (rawCode == null || rawCode.isBlank()) {
      violations.add(UserFieldViolations.codeRequired());
      return Optional.empty();
    }
    Optional<RegistrationCode> parsed = RegistrationCode.parse(rawCode);
    if (parsed.isEmpty()) {
      violations.add(UserFieldViolations.codeInvalidFormat());
    }
    return parsed;
  }

  /// 校验验证码，对了就用掉。
  ///
  /// @param ticket 凭证原文
  /// @param code 提交的验证码
  /// @param purpose 期望的用途
  /// @return 发码时绑定的值
  /// @throws InvalidUserFieldsException 验证码不对（`INVALID`），或没有可用的验证码（`EXPIRED`）
  public CodeCheck.Matched verify(
      String ticket, RegistrationCode code, RegistrationCodePurpose purpose) {
    return switch (pendingRegistrations.verifyCode(
        ticket, code, purpose, policy.maxCodeAttempts())) {
      case CodeCheck.Matched matched -> matched;
      case CodeCheck.Mismatch _ ->
          throw new InvalidUserFieldsException(List.of(UserFieldViolations.codeInvalid()));
      case CodeCheck.NoActiveCode _ ->
          throw new InvalidUserFieldsException(List.of(UserFieldViolations.codeExpired()));
    };
  }

  /// 删待补填记录。失败只记日志：账号或绑定已经提交，记录会自己过期，重复操作由唯一约束挡住。
  ///
  /// @param ticket 凭证原文
  public void deletePendingQuietly(String ticket) {
    try {
      pendingRegistrations.delete(ticket);
    } catch (RuntimeException e) {
      log.warn("完成后删待补填记录失败，等它自己过期: {}", e.getMessage());
    }
  }

  /// 验证码的有效期，不超过待补填记录的余量：记录没了，码也没用了。
  ///
  /// @param registration 待补填记录
  /// @param now 当前时间
  /// @return 有效期
  /// @throws InvalidUserFieldsException 记录已经到期
  private Duration ttlWithin(PendingRegistration registration, Instant now) {
    Duration remaining = Duration.between(now, registration.expiresAt());
    if (remaining.isZero() || remaining.isNegative()) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.ticketInvalid()));
    }
    return remaining.compareTo(policy.codeTtl()) < 0 ? remaining : policy.codeTtl();
  }

  /// 刚写进存储的验证码。
  ///
  /// @param code 验证码原文，只进邮件
  /// @param pendingCode 公开部分
  public record IssuedCode(RegistrationCode code, PendingCode pendingCode) {

    /// 创建。
    ///
    /// @param code 验证码原文
    /// @param pendingCode 公开部分
    /// @return 结果
    public static IssuedCode of(RegistrationCode code, PendingCode pendingCode) {
      return new IssuedCode(code, pendingCode);
    }
  }
}
