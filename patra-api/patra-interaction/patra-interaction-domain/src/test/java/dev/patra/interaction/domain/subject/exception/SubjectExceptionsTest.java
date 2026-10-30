package dev.patra.interaction.domain.subject.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/// 底座领域异常的语义特征决定 HTTP 状态（503 / 404 / 422 / 422），原因码进 `traits[]`。
class SubjectExceptionsTest {

  @Test
  void should_mark_source_unavailable_as_dependency_unavailable() {
    SubjectSourceUnavailableException ex =
        new SubjectSourceUnavailableException(
            SubjectType.PUBLICATION, new RuntimeException("down"));

    assertTraitAndMessage(ex, StandardErrorTrait.DEP_UNAVAILABLE, "文献信息暂时无法获取，请稍后再试");
    assertThat(ex.getType()).isEqualTo(SubjectType.PUBLICATION);
  }

  /// 从未登记、来源说不存在：只有 `NOT_FOUND`。
  @Test
  void should_mark_not_found_without_reason() {
    SubjectNotFoundException ex = SubjectNotFoundException.notFound(SubjectType.PUBLICATION, "100");

    assertTraitAndMessage(ex, StandardErrorTrait.NOT_FOUND, "文献不存在或已被删除");
    assertThat(ex.getKey()).isEqualTo("100");
  }

  /// 已登记但失效、来源已软删：另带原因码 `SUBJECT_UNAVAILABLE`，门户据此显示「已删除」。
  @Test
  void should_mark_unavailable_with_reason() {
    SubjectNotFoundException ex =
        SubjectNotFoundException.unavailable(SubjectType.PUBLICATION, "100");

    assertThat(ex.getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.NOT_FOUND, SubjectErrorTrait.SUBJECT_UNAVAILABLE);
    assertThat(ex.getMessage()).isEqualTo("文献不存在或已被删除");
  }

  @Test
  void should_mark_invalid_key_as_rule_violation() {
    assertTraitAndMessage(
        new InvalidSubjectKeyException(SubjectType.PUBLICATION, "abc"),
        StandardErrorTrait.RULE_VIOLATION,
        "对象标识格式不正确");
  }

  /// 批量对象统计的参数不合法：422，说明原样带出。
  @Test
  void should_mark_invalid_request_as_rule_violation() {
    assertTraitAndMessage(
        new InvalidSubjectRequestException("一次 1 ~ 100 个 key"),
        StandardErrorTrait.RULE_VIOLATION,
        "一次 1 ~ 100 个 key");
  }

  @Test
  void should_mark_unsupported_capability_as_rule_violation() {
    assertTraitAndMessage(
        new SubjectCapabilityNotSupportedException(
            SubjectType.PUBLICATION, SubjectCapability.COLLECT),
        StandardErrorTrait.RULE_VIOLATION,
        "该对象不支持此操作");
  }

  /// 名片取不到是依赖不可用（503）。
  @Test
  void should_mark_user_directory_unavailable_as_dependency_unavailable() {
    assertTraitAndMessage(
        new UserDirectoryUnavailableException(new RuntimeException("down")),
        StandardErrorTrait.DEP_UNAVAILABLE,
        "用户信息暂时无法获取，请稍后再试");
  }

  /// 发表资格：账号状态异常、邮箱未验证都是 409，各带原因码（与 collection 设公开同名）。
  @Test
  void should_mark_ineligible_as_conflict_with_reasons() {
    PublishIneligibleException restricted = PublishIneligibleException.accountRestricted();
    PublishIneligibleException unverified = PublishIneligibleException.emailUnverified();

    assertThat(restricted.getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.CONFLICT, SubjectErrorTrait.ACCOUNT_RESTRICTED);
    assertThat(restricted.getMessage()).isEqualTo("账号状态异常，暂时不能发表");
    assertThat(unverified.getErrorTraits())
        .containsExactlyInAnyOrder(StandardErrorTrait.CONFLICT, SubjectErrorTrait.EMAIL_UNVERIFIED);
    assertThat(unverified.getMessage()).isEqualTo("验证邮箱后才能发表");
  }

  /// 限流：429，带原因码与还要等多久（`Retry-After`）。
  @Test
  void should_mark_rate_limited_with_retry_after() {
    PublishRateLimitedException ex =
        PublishRateLimitedException.tooFrequent(PublishKind.COMMENT, Duration.ofMillis(4_500));

    assertThat(ex.getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.QUOTA_EXCEEDED, SubjectErrorTrait.TOO_FREQUENT);
    assertThat(ex.getRetryAfterSeconds()).isEqualTo(5L);
    assertThat(ex.getMessage()).isEqualTo("发得太快了，请隔 10 秒再发");
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
