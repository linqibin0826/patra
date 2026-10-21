package dev.patra.identity.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.retry.HasRetryAfter;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// identity 领域异常的文案和语义特征。文案会原样出现在响应的 `detail` 里，必须是固定的。
@DisplayName("identity 领域异常的文案和特征")
class DomainExceptionTraitsTest {

  /// 各领域异常及其预期的文案和（唯一的）标准特征。
  ///
  /// @return 异常、文案、特征
  static Stream<Arguments> exceptions() {
    return Stream.of(
        Arguments.of(
            new InvalidUserFieldsException(
                List.of(FieldViolation.of("email", "REQUIRED", "请输入邮箱"))),
            "请求参数不合法",
            StandardErrorTrait.RULE_VIOLATION),
        Arguments.of(new InvalidCredentialsException(), "邮箱或密码错误", StandardErrorTrait.UNAUTHORIZED),
        Arguments.of(
            new LoginTemporarilyLockedException(Duration.ofMinutes(15)),
            "尝试次数过多，请稍后再试",
            StandardErrorTrait.QUOTA_EXCEEDED),
        Arguments.of(new UserBannedException(), "该账号已被封禁", StandardErrorTrait.FORBIDDEN),
        Arguments.of(new UserNotFoundException(), "用户不存在", StandardErrorTrait.NOT_FOUND),
        Arguments.of(
            new UserModifiedConcurrentlyException(), "用户正被其他操作修改，请重试", StandardErrorTrait.CONFLICT),
        Arguments.of(
            new TemporarilyUnavailableException(), "服务暂时不可用", StandardErrorTrait.DEP_UNAVAILABLE),
        Arguments.of(
            new UserIdentifierTakenException(List.of(UserFieldViolations.emailAlreadyRegistered())),
            "邮箱或用户名已被使用",
            StandardErrorTrait.CONFLICT),
        Arguments.of(
            new InvalidUserStateTransitionException(UserStatus.DELETED, "unban"),
            "账号当前状态不允许此操作",
            StandardErrorTrait.CONFLICT),
        Arguments.of(
            new EmailResendLimitedException(Duration.ofSeconds(60)),
            "发送过于频繁，请稍后再试",
            StandardErrorTrait.QUOTA_EXCEEDED));
  }

  @Test
  @DisplayName("邮箱已验证还重发是 409：CONFLICT 之外带 EMAIL_ALREADY_VERIFIED")
  void should_carry_already_verified_trait_beside_conflict() {
    EmailAlreadyVerifiedException exception = new EmailAlreadyVerifiedException();

    assertThat(exception.getMessage()).isEqualTo("邮箱已经验证过了");
    assertThat(exception.getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.CONFLICT, IdentityErrorTrait.EMAIL_ALREADY_VERIFIED);
  }

  @Test
  @DisplayName("重发限流带 Retry-After（毫秒向上取整到秒）；令牌字段错误的字段名和原因码固定")
  void should_expose_retry_after_and_token_field_codes() {
    EmailResendLimitedException limited =
        new EmailResendLimitedException(Duration.ofMillis(36_500));

    assertThat(limited.getRetryAfter()).isEqualTo(Duration.ofMillis(36_500));
    assertThat(limited.getRetryAfterSeconds()).isEqualTo(37);
    assertThatThrownBy(() -> new EmailResendLimitedException(null))
        .isInstanceOf(NullPointerException.class);
    assertThat(UserFieldViolations.tokenRequired().field()).isEqualTo("token");
    assertThat(UserFieldViolations.tokenRequired().code()).isEqualTo("REQUIRED");
    assertThat(UserFieldViolations.tokenInvalid().field()).isEqualTo("token");
    assertThat(UserFieldViolations.tokenInvalid().code()).isEqualTo("INVALID");
    assertThat(UserFieldViolations.tokenInvalid().message()).isEqualTo("验证链接无效或已过期");
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("exceptions")
  @DisplayName("文案固定，特征决定状态码")
  void should_have_fixed_message_and_trait(
      DomainException exception, String message, StandardErrorTrait trait) {
    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getErrorTraits()).containsExactly(trait);
  }

  @Test
  @DisplayName("注销中的异常在 CONFLICT 之外多带 identity 自己的 PENDING_DELETION 特征")
  void should_carry_pending_deletion_trait_beside_conflict() {
    UserPendingDeletionException exception = new UserPendingDeletionException();

    assertThat(exception.getMessage()).isEqualTo("账号正在注销中，取消注销后再操作");
    assertThat(exception.getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.CONFLICT, IdentityErrorTrait.PENDING_DELETION);
    assertThat(IdentityErrorTrait.PENDING_DELETION.name()).isEqualTo("PENDING_DELETION");
  }

  @Test
  @DisplayName("改名冷却是 409：CONFLICT 之外带 USERNAME_CHANGE_COOLDOWN，不带 Retry-After")
  void should_carry_cooldown_trait_beside_conflict_without_retry_after() {
    UsernameChangeCooldownException exception = new UsernameChangeCooldownException();

    assertThat(exception.getMessage()).isEqualTo("用户名修改过于频繁，请稍后再试");
    assertThat(exception.getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.CONFLICT, IdentityErrorTrait.USERNAME_CHANGE_COOLDOWN);
    assertThat(exception).isNotInstanceOf(HasRetryAfter.class);
  }

  @Test
  @DisplayName("占用冲突带字段错误（至少一条）")
  void should_expose_field_violations() {
    UserIdentifierTakenException taken =
        new UserIdentifierTakenException(
            List.of(
                UserFieldViolations.emailAlreadyRegistered(),
                UserFieldViolations.usernameAlreadyTaken()));

    assertThat(taken.getFieldViolations())
        .extracting(FieldViolation::field)
        .containsExactly("email", "username");
    assertThatThrownBy(() -> new UserIdentifierTakenException(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
