package dev.patra.identity.domain.account.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.identity.domain.account.model.vo.avatar.AvatarRejection;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import org.junit.jupiter.api.Test;

class InvalidAvatarExceptionTest {

  @Test
  void should_expose_file_field_violation_with_reason_code() {
    InvalidAvatarException exception = new InvalidAvatarException(AvatarRejection.TOO_SMALL);

    assertThat(exception.getRejection()).isEqualTo(AvatarRejection.TOO_SMALL);
    assertThat(exception.getFieldViolations())
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(tuple("file", "TOO_SMALL"));
    assertThat(exception.getErrorTraits()).contains(StandardErrorTrait.RULE_VIOLATION);
  }
}
