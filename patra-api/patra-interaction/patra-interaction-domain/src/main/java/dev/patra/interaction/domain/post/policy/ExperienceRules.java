package dev.patra.interaction.domain.post.policy;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/// 投稿经验的常量规则：日期按北京时间算「今天」、日期下界、轮数与评分上限、编辑不记版本的宽限期。
///
/// 时区与底座发表守卫的「每天」相同；那份常量在底座的私有包里，这里另定义一份。
public final class ExperienceRules {

  /// 「今天」所在的时区：北京时间。
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  /// 最早能填的日期。
  public static final LocalDate EARLIEST = LocalDate.of(1990, 1, 1);

  /// 审稿轮数上限。
  public static final int MAX_REVIEW_ROUNDS = 10;

  /// 评分上限（下限是 1）。
  public static final int MAX_RATING = 5;

  /// 发布后这段时间内的修改不记版本（spec 2.1.1「5 分钟内的修改不记版本」）。
  public static final Duration EDIT_GRACE = Duration.ofMinutes(5);

  /// 只有静态成员。
  private ExperienceRules() {}

  /// 北京时间的今天。
  ///
  /// @param clock 时钟
  /// @return 日期
  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(ZONE));
  }
}
