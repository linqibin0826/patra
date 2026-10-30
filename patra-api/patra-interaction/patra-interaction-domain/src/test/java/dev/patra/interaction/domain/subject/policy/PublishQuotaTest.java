package dev.patra.interaction.domain.subject.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.subject.exception.PublishRateLimitedException;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// 发表频率：先判当天（北京时间自然日）上限，再判间隔；放行时计数加一。
class PublishQuotaTest {

  /// 北京时间 2026-10-27 16:00。
  private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

  @Test
  void should_admit_first_publish() {
    assertThat(admit(Optional.empty(), PublishKind.COMMENT, NOW))
        .isEqualTo(new PublishQuota(TODAY, 1, NOW));
  }

  /// 当天第 100 条放行，第 101 条拒绝，要等到北京时间明天 0 点。
  @Test
  void should_admit_hundredth_comment_and_reject_next() {
    Instant hourAgo = NOW.minusSeconds(3600);

    assertThat(admit(quota(TODAY, 99, hourAgo), PublishKind.COMMENT, NOW).dayCount())
        .isEqualTo(100);
    assertThatThrownBy(() -> admit(quota(TODAY, 100, hourAgo), PublishKind.COMMENT, NOW))
        .isInstanceOfSatisfying(
            PublishRateLimitedException.class,
            ex -> {
              assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.DAILY_LIMIT_REACHED);
              assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofHours(8));
              assertThat(ex.getMessage()).isEqualTo("今天已经发表了 100 条评论，明天再来吧");
            });
  }

  /// 过了北京时间 0 点（UTC 16:00）就是新的一天，计数从 1 重新开始；0 点前一秒还算前一天。
  @Test
  void should_reset_count_after_beijing_midnight() {
    Instant lastEvening = Instant.parse("2026-10-27T15:00:00Z");
    Instant beforeMidnight = Instant.parse("2026-10-27T15:59:59Z");
    Instant midnight = Instant.parse("2026-10-27T16:00:00Z");

    assertThat(admit(quota(TODAY, 100, lastEvening), PublishKind.COMMENT, midnight))
        .isEqualTo(new PublishQuota(LocalDate.of(2026, 10, 28), 1, midnight));
    assertThatThrownBy(
            () -> admit(quota(TODAY, 100, lastEvening), PublishKind.COMMENT, beforeMidnight))
        .isInstanceOf(PublishRateLimitedException.class);
  }

  /// 间隔跨天同样算：0 点前 1 秒发过，0 点整不能再发。
  @Test
  void should_apply_interval_across_midnight() {
    Instant beforeMidnight = Instant.parse("2026-10-27T15:59:59Z");
    Instant midnight = Instant.parse("2026-10-27T16:00:00Z");

    assertThatThrownBy(() -> admit(quota(TODAY, 3, beforeMidnight), PublishKind.COMMENT, midnight))
        .isInstanceOfSatisfying(
            PublishRateLimitedException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.TOO_FREQUENT));
  }

  /// 评论间隔不足 10 秒拒绝，正好 10 秒放行。
  @Test
  void should_reject_comment_within_ten_seconds() {
    Instant almost = NOW.minusMillis(9_999);

    assertThatThrownBy(() -> admit(quota(TODAY, 1, almost), PublishKind.COMMENT, NOW))
        .isInstanceOfSatisfying(
            PublishRateLimitedException.class,
            ex -> {
              assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.TOO_FREQUENT);
              assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofMillis(1));
              assertThat(ex.getMessage()).isEqualTo("发得太快了，请隔 10 秒再发");
            });
    assertThat(admit(quota(TODAY, 1, NOW.minusSeconds(10)), PublishKind.COMMENT, NOW).dayCount())
        .isEqualTo(2);
  }

  /// 投稿经验没有间隔。
  @Test
  void should_ignore_interval_for_posts() {
    assertThat(admit(quota(TODAY, 1, NOW.minusSeconds(1)), PublishKind.POST, NOW).dayCount())
        .isEqualTo(2);
  }

  /// 没有间隔的种类不判间隔：即使系统时钟回拨、上次发表时刻在「现在」之后，也不报「请隔 0 秒再发」。
  @Test
  void should_not_check_interval_when_kind_has_none() {
    assertThat(admit(quota(TODAY, 1, NOW.plusSeconds(5)), PublishKind.POST, NOW).dayCount())
        .isEqualTo(2);
  }

  /// 已经到上限的人，等 10 秒也没用：先报每日上限。
  @Test
  void should_check_daily_limit_before_interval() {
    assertThatThrownBy(
            () -> admit(quota(TODAY, 100, NOW.minusSeconds(1)), PublishKind.COMMENT, NOW))
        .isInstanceOfSatisfying(
            PublishRateLimitedException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.DAILY_LIMIT_REACHED));
  }

  /// 投稿经验每天最多 10 条。
  @Test
  void should_cap_posts_at_ten_per_day() {
    assertThatThrownBy(() -> admit(quota(TODAY, 10, NOW.minusSeconds(3600)), PublishKind.POST, NOW))
        .isInstanceOfSatisfying(
            PublishRateLimitedException.class,
            ex -> {
              assertThat(ex.getKind()).isEqualTo(PublishKind.POST);
              assertThat(ex.getMessage()).isEqualTo("今天已经发表了 10 条投稿经验，明天再来吧");
            });
  }

  /// 有帮助每天最多 300 次、不限间隔：第 300 次紧挨着上一次也放行，第 301 次拒绝。
  @Test
  void should_admit_three_hundredth_helpful_without_interval_and_reject_next() {
    assertThat(admit(quota(TODAY, 299, NOW.minusSeconds(1)), PublishKind.HELPFUL, NOW).dayCount())
        .isEqualTo(300);
    assertThatThrownBy(
            () -> admit(quota(TODAY, 300, NOW.minusSeconds(1)), PublishKind.HELPFUL, NOW))
        .isInstanceOfSatisfying(
            PublishRateLimitedException.class,
            ex -> {
              assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.DAILY_LIMIT_REACHED);
              assertThat(ex.getMessage()).isEqualTo("今天已经点了 300 次有帮助，明天再来吧");
            });
  }

  /// 按北京时间放行一次。
  ///
  /// @param current 现有计数
  /// @param kind 种类
  /// @param now 现在
  /// @return 放行后的计数
  private static PublishQuota admit(Optional<PublishQuota> current, PublishKind kind, Instant now) {
    return PublishQuota.admit(current, kind, now, PublishQuota.ZONE);
  }

  /// 现有计数。
  ///
  /// @param day 自然日
  /// @param count 当天条数
  /// @param last 上次发表
  /// @return 计数
  private static Optional<PublishQuota> quota(LocalDate day, int count, Instant last) {
    return Optional.of(new PublishQuota(day, count, last));
  }
}
