package dev.patra.identity.domain.account.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.core.error.field.FieldViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Username 单元测试。
@DisplayName("Username 单元测试")
class UsernameTest {

  @Test
  @DisplayName("去掉首尾空白、转成小写")
  void should_normalize_input() {
    assertThat(Username.of(" Chen_Yu ").value()).isEqualTo("chen_yu");
    assertThat(Username.normalize("  ABC ")).isEqualTo("abc");
  }

  @Test
  @DisplayName("语法不合法时抛字段错误，只有一条")
  void should_throw_single_violation_for_invalid_input() {
    assertThatThrownBy(() -> Username.of("ab"))
        .isInstanceOfSatisfying(
            InvalidUserFieldsException.class,
            e ->
                assertThat(e.getFieldViolations())
                    .singleElement()
                    .extracting(FieldViolation::code)
                    .isEqualTo("TOO_SHORT"));
  }

  @Test
  @DisplayName("构造器只接受已规范化且语法合法的值")
  void should_reject_unnormalized_or_invalid_value_in_constructor() {
    assertThatThrownBy(() -> new Username("Chen")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Username("ab")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("保留字不在值对象里判")
  void should_not_check_reserved_words() {
    assertThat(Username.of("admin").value()).isEqualTo("admin");
  }
}
