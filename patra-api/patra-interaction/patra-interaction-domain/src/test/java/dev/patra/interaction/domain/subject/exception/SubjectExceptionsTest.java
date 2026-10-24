package dev.patra.interaction.domain.subject.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import org.junit.jupiter.api.Test;

/// 四个领域异常的语义特征决定 HTTP 状态（503 / 404 / 422 / 422）。
class SubjectExceptionsTest {

  @Test
  void should_mark_source_unavailable_as_dependency_unavailable() {
    assertTraitAndMessage(
        new SubjectSourceUnavailableException(new RuntimeException("down")),
        StandardErrorTrait.DEP_UNAVAILABLE,
        "文献信息暂时无法获取，请稍后再试");
  }

  @Test
  void should_mark_not_found() {
    assertTraitAndMessage(
        new SubjectNotFoundException(SubjectType.PUBLICATION, "100"),
        StandardErrorTrait.NOT_FOUND,
        "文献不存在或已被删除");
  }

  @Test
  void should_mark_invalid_key_as_rule_violation() {
    assertTraitAndMessage(
        new InvalidSubjectKeyException(SubjectType.PUBLICATION, "abc"),
        StandardErrorTrait.RULE_VIOLATION,
        "对象标识格式不正确");
  }

  @Test
  void should_mark_unsupported_capability_as_rule_violation() {
    assertTraitAndMessage(
        new SubjectCapabilityNotSupportedException(
            SubjectType.PUBLICATION, SubjectCapability.COLLECT),
        StandardErrorTrait.RULE_VIOLATION,
        "该对象不支持此操作");
  }

  /// 只有一个特征，消息是给用户看的中文原文。
  ///
  /// @param ex 异常
  /// @param trait 期望的特征
  /// @param message 期望的消息
  private static void assertTraitAndMessage(
      DomainException ex, StandardErrorTrait trait, String message) {
    assertThat(ex.getErrorTraits()).containsExactly(trait);
    assertThat(ex.getMessage()).isEqualTo(message);
  }
}
