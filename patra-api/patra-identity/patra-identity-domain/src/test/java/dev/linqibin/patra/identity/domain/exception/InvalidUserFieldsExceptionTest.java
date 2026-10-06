package dev.linqibin.patra.identity.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.commons.error.field.FieldViolation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// InvalidUserFieldsException 单元测试。
@DisplayName("InvalidUserFieldsException 单元测试")
class InvalidUserFieldsExceptionTest {

  @Test
  @DisplayName("带上全部字段错误，外部改原列表不影响异常")
  void should_carry_a_copy_of_all_violations() {
    List<FieldViolation> violations = new ArrayList<>();
    violations.add(FieldViolation.of("email", "REQUIRED", "请输入邮箱"));
    violations.add(FieldViolation.of("password", "TOO_SHORT", "密码至少 8 位"));

    InvalidUserFieldsException exception = new InvalidUserFieldsException(violations);
    violations.clear();

    assertThat(exception.getFieldViolations())
        .extracting("field")
        .containsExactly("email", "password");
  }

  @Test
  @DisplayName("至少要有一条字段错误")
  void should_require_at_least_one_violation() {
    assertThatThrownBy(() -> new InvalidUserFieldsException(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
