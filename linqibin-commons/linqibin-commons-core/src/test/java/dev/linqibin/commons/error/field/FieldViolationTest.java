package dev.linqibin.commons.error.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// FieldViolation 单元测试。
@DisplayName("FieldViolation 单元测试")
class FieldViolationTest {

  @Test
  @DisplayName("按原样保存字段名、原因码和文案")
  void should_keep_field_code_and_message() {
    FieldViolation violation = FieldViolation.of("password", "TOO_SHORT", "密码至少 8 位");

    assertThat(violation.field()).isEqualTo("password");
    assertThat(violation.code()).isEqualTo("TOO_SHORT");
    assertThat(violation.message()).isEqualTo("密码至少 8 位");
  }

  @Test
  @DisplayName("字段名和原因码不能为 null")
  void should_reject_null_field_or_code() {
    assertThatThrownBy(() -> FieldViolation.of(null, "REQUIRED", "请输入邮箱"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> FieldViolation.of("email", null, "请输入邮箱"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("文案可以为 null")
  void should_allow_null_message() {
    assertThat(FieldViolation.of("email", "REQUIRED", null).message()).isNull();
  }
}
