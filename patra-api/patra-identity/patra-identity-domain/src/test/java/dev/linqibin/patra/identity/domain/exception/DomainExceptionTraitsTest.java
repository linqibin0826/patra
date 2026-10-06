package dev.linqibin.patra.identity.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// identity 领域异常的文案和语义特征。文案会原样出现在响应的 `detail` 里，必须是固定的。
@DisplayName("identity 领域异常的文案和特征")
class DomainExceptionTraitsTest {

  /// 七个异常及其预期的文案和特征。
  ///
  /// @return 异常、文案、特征
  static Stream<Arguments> exceptions() {
    return Stream.of(
        Arguments.of(
            new InvalidUserFieldsException(
                List.of(FieldViolation.of("email", "REQUIRED", "请输入邮箱"))),
            "请求参数不合法",
            StandardErrorTrait.RULE_VIOLATION),
        Arguments.of(new EmailAlreadyRegisteredException(), "该邮箱已注册", StandardErrorTrait.CONFLICT),
        Arguments.of(new InvalidCredentialsException(), "邮箱或密码错误", StandardErrorTrait.UNAUTHORIZED),
        Arguments.of(
            new LoginTemporarilyLockedException(Duration.ofMinutes(15)),
            "尝试次数过多，请稍后再试",
            StandardErrorTrait.QUOTA_EXCEEDED),
        Arguments.of(new UserBannedException(), "该账号已被封禁", StandardErrorTrait.FORBIDDEN),
        Arguments.of(new UserNotFoundException(), "用户不存在", StandardErrorTrait.NOT_FOUND),
        Arguments.of(
            new TemporarilyUnavailableException(), "服务暂时不可用", StandardErrorTrait.DEP_UNAVAILABLE));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("exceptions")
  @DisplayName("文案固定，特征决定状态码")
  void should_have_fixed_message_and_trait(
      DomainException exception, String message, StandardErrorTrait trait) {
    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getErrorTraits()).containsExactly(trait);
  }
}
