package dev.patra.identity.app.account.service;

import dev.patra.identity.domain.account.exception.HumanVerificationUnavailableException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.model.enums.HumanVerificationAction;
import dev.patra.identity.domain.account.model.enums.HumanVerificationResult;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.policy.HumanVerificationPolicy;
import dev.patra.identity.domain.account.port.throttle.LoginIpFailurePort;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.account.port.throttle.LoginThrottleStatus;
import dev.patra.identity.domain.account.port.verification.HumanVerificationPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.security.AccountType;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 决定“要不要验证、结果怎么处理”的唯一地方。用哪家由配置决定，这里不依赖具体厂商。
///
/// 调用方要在事务外、其他检查之前调用：校验要调第三方（`transaction.md` 第 2 节），而且校验失败的请求不能占
/// 失败限制的名额，也不能做密码哈希。验证不了一律拒绝，不放行。
@Component
@RequiredArgsConstructor
public class HumanVerificationGuard {

  private final LoginThrottlePort loginThrottle;
  private final LoginIpFailurePort ipFailures;
  private final List<HumanVerificationPort> ports;
  private final Supplier<HumanVerificationPolicy> policy;

  /// 登录前的检查：已锁定直接提示锁定；同一邮箱或同一 IP 的失败次数到门槛时要先过验证。
  ///
  /// 不存在的邮箱也会计失败，所以存在和不存在的邮箱都在相同次数后要求验证，看不出邮箱是否存在。
  ///
  /// @param email 邮箱
  /// @param clientIp 客户端 IP，可以为 `null`，此时只按邮箱触发
  /// @param token 令牌，可以没有
  /// @throws LoginTemporarilyLockedException 账号处于锁定期
  /// @throws InvalidUserFieldsException 要验证但没带令牌，或令牌没通过
  /// @throws HumanVerificationUnavailableException 验证不了
  public void requireForLogin(EmailAddress email, String clientIp, String token) {
    LoginThrottleStatus status = loginThrottle.status(AccountType.USER, email);
    status
        .lockRemaining()
        .ifPresent(
            remaining -> {
              throw new LoginTemporarilyLockedException(remaining);
            });
    HumanVerificationPolicy current = policy.get();
    if (current.requiresVerification(status.failures(), ipFailures.count(clientIp))) {
      verify(current, HumanVerificationAction.LOGIN, token);
    }
  }

  /// 每次都要验证的操作：注册、申请找回密码。
  ///
  /// @param action 这次操作
  /// @param token 令牌，可以没有
  /// @throws InvalidUserFieldsException 没带令牌，或令牌没通过
  /// @throws HumanVerificationUnavailableException 验证不了
  public void require(HumanVerificationAction action, String token) {
    verify(policy.get(), action, token);
  }

  /// 校验令牌。空白的令牌等同没带，不调第三方。
  ///
  /// @param current 当前参数
  /// @param action 这次操作
  /// @param token 令牌
  private void verify(
      HumanVerificationPolicy current, HumanVerificationAction action, String token) {
    if (token == null || token.isBlank()) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.verificationRequired()));
    }
    HumanVerificationResult result =
        ports.stream()
            .filter(port -> port.provider() == current.provider())
            .findFirst()
            .map(port -> port.verify(token, action))
            .orElse(HumanVerificationResult.UNAVAILABLE);
    switch (result) {
      case PASSED -> {}
      case REJECTED ->
          throw new InvalidUserFieldsException(List.of(UserFieldViolations.verificationFailed()));
      case UNAVAILABLE -> throw new HumanVerificationUnavailableException();
    }
  }
}
