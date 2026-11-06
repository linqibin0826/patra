package dev.patra.identity.app.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.HumanVerificationUnavailableException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.model.enums.HumanVerificationAction;
import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import dev.patra.identity.domain.account.model.enums.HumanVerificationResult;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.policy.HumanVerificationPolicy;
import dev.patra.identity.domain.account.port.throttle.LoginIpFailurePort;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.account.port.throttle.LoginThrottleStatus;
import dev.patra.identity.domain.account.port.verification.HumanVerificationPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.core.security.AccountType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 决定“要不要验证、结果怎么处理”的守卫。
@DisplayName("HumanVerificationGuard")
class HumanVerificationGuardTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final String IP = "113.92.157.29";
  private static final String VERIFICATION = "V".repeat(64);

  private final LoginThrottlePort throttle = mock(LoginThrottlePort.class);
  private final LoginIpFailurePort ipFailures = mock(LoginIpFailurePort.class);
  private final HumanVerificationPort turnstile = mock(HumanVerificationPort.class);
  private final HumanVerificationPort trustTurn = mock(HumanVerificationPort.class);
  private final AtomicReference<HumanVerificationProvider> provider =
      new AtomicReference<>(HumanVerificationProvider.TURNSTILE);
  private HumanVerificationGuard guard;

  /// 默认没有失败记录，两家都放行。
  @BeforeEach
  void setUp() {
    when(turnstile.provider()).thenReturn(HumanVerificationProvider.TURNSTILE);
    when(trustTurn.provider()).thenReturn(HumanVerificationProvider.TRUST_TURN);
    when(turnstile.verify(any(), any())).thenReturn(HumanVerificationResult.PASSED);
    when(trustTurn.verify(any(), any())).thenReturn(HumanVerificationResult.PASSED);
    givenFailures(0, 0);
    guard =
        new HumanVerificationGuard(
            throttle,
            ipFailures,
            List.of(turnstile, trustTurn),
            () -> HumanVerificationPolicy.of(provider.get(), 3, 5, Duration.ofMinutes(15)));
  }

  @Test
  @DisplayName("账号已锁定：直接提示锁定，不要求验证，也不调第三方")
  void throws_locked_without_calling_port_when_account_locked() {
    when(throttle.status(AccountType.USER, EMAIL))
        .thenReturn(LoginThrottleStatus.of(0, Optional.of(Duration.ofMinutes(9))));

    assertThatThrownBy(() -> guard.requireForLogin(EMAIL, IP, null))
        .isInstanceOf(LoginTemporarilyLockedException.class);
    verify(turnstile, never()).verify(any(), any());
  }

  @Test
  @DisplayName("没到门槛：不校验令牌，带了也忽略")
  void does_nothing_below_thresholds() {
    givenFailures(2, 4);

    assertThatCode(() -> guard.requireForLogin(EMAIL, IP, VERIFICATION)).doesNotThrowAnyException();
    verify(turnstile, never()).verify(any(), any());
  }

  @Test
  @DisplayName("邮箱到门槛、没带令牌：要求验证")
  void requires_token_when_email_threshold_reached() {
    givenFailures(3, 0);

    assertViolation(() -> guard.requireForLogin(EMAIL, IP, null), "VERIFICATION_REQUIRED");
  }

  @Test
  @DisplayName("IP 到门槛、没带令牌：要求验证")
  void requires_token_when_ip_threshold_reached() {
    givenFailures(0, 5);

    assertViolation(() -> guard.requireForLogin(EMAIL, IP, null), "VERIFICATION_REQUIRED");
  }

  @Test
  @DisplayName("空字符串或全空白的令牌等同没带，不调第三方")
  void treats_blank_token_as_missing() {
    givenFailures(3, 0);

    assertViolation(() -> guard.requireForLogin(EMAIL, IP, ""), "VERIFICATION_REQUIRED");
    assertViolation(() -> guard.requireForLogin(EMAIL, IP, "   "), "VERIFICATION_REQUIRED");
    verify(turnstile, never()).verify(any(), any());
  }

  @Test
  @DisplayName("到门槛且令牌通过：放行")
  void passes_when_port_passes() {
    givenFailures(3, 0);

    assertThatCode(() -> guard.requireForLogin(EMAIL, IP, VERIFICATION)).doesNotThrowAnyException();
    verify(turnstile).verify(VERIFICATION, HumanVerificationAction.LOGIN);
  }

  @Test
  @DisplayName("令牌没通过：验证失败")
  void failed_when_port_rejects() {
    givenFailures(3, 0);
    when(turnstile.verify(any(), any())).thenReturn(HumanVerificationResult.REJECTED);

    assertViolation(() -> guard.requireForLogin(EMAIL, IP, VERIFICATION), "VERIFICATION_FAILED");
  }

  @Test
  @DisplayName("第三方不可用：拒绝，不放行")
  void unavailable_when_port_unavailable() {
    givenFailures(3, 0);
    when(turnstile.verify(any(), any())).thenReturn(HumanVerificationResult.UNAVAILABLE);

    assertThatThrownBy(() -> guard.requireForLogin(EMAIL, IP, VERIFICATION))
        .isInstanceOf(HumanVerificationUnavailableException.class);
  }

  @Test
  @DisplayName("按当前配置选厂商，切换后用另一家")
  void selects_port_by_current_provider() {
    guard.require(HumanVerificationAction.REGISTER, VERIFICATION);
    verify(turnstile).verify(VERIFICATION, HumanVerificationAction.REGISTER);
    verify(trustTurn, never()).verify(any(), any());

    provider.set(HumanVerificationProvider.TRUST_TURN);
    guard.require(HumanVerificationAction.REGISTER, VERIFICATION);
    verify(trustTurn).verify(VERIFICATION, HumanVerificationAction.REGISTER);
  }

  @Test
  @DisplayName("当前厂商没有对应实现：拒绝，不放行")
  void unavailable_when_no_port_for_provider() {
    HumanVerificationGuard onlyTurnstile =
        new HumanVerificationGuard(
            throttle,
            ipFailures,
            List.of(turnstile),
            () ->
                HumanVerificationPolicy.of(
                    HumanVerificationProvider.TRUST_TURN, 3, 5, Duration.ofMinutes(15)));

    assertThatThrownBy(() -> onlyTurnstile.require(HumanVerificationAction.LOGIN, VERIFICATION))
        .isInstanceOf(HumanVerificationUnavailableException.class);
  }

  @Test
  @DisplayName("拿不到 IP：只按邮箱触发")
  void null_client_ip_uses_email_trigger_only() {
    when(ipFailures.count(null)).thenReturn(0);
    when(throttle.status(AccountType.USER, EMAIL))
        .thenReturn(LoginThrottleStatus.of(2, Optional.empty()));

    assertThatCode(() -> guard.requireForLogin(EMAIL, null, null)).doesNotThrowAnyException();

    when(throttle.status(AccountType.USER, EMAIL))
        .thenReturn(LoginThrottleStatus.of(3, Optional.empty()));
    assertViolation(() -> guard.requireForLogin(EMAIL, null, null), "VERIFICATION_REQUIRED");
  }

  @Test
  @DisplayName("注册和找回密码每次都校验，不看门槛")
  void require_always_verifies() {
    assertViolation(
        () -> guard.require(HumanVerificationAction.PASSWORD_RESET, null), "VERIFICATION_REQUIRED");

    guard.require(HumanVerificationAction.PASSWORD_RESET, VERIFICATION);

    verify(turnstile).verify(VERIFICATION, HumanVerificationAction.PASSWORD_RESET);
  }

  /// 设定两个计数。
  ///
  /// @param email 邮箱失败次数
  /// @param ip IP 失败次数
  private void givenFailures(int email, int ip) {
    when(throttle.status(AccountType.USER, EMAIL))
        .thenReturn(LoginThrottleStatus.of(email, Optional.empty()));
    when(ipFailures.count(IP)).thenReturn(ip);
  }

  /// 断言抛出 `verificationToken` 字段错误。
  ///
  /// @param call 调用
  /// @param code 原因码
  private static void assertViolation(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("verificationToken:" + code));
  }
}
