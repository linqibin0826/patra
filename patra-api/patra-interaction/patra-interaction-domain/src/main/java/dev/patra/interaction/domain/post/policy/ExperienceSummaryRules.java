package dev.patra.interaction.domain.post.policy;

import dev.patra.interaction.domain.post.model.read.ExperienceStats;
import dev.patra.interaction.domain.post.model.read.ExperienceSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;

/// 期刊汇总的口径（spec 第 7 节、决策 G）：窗口内不到 5 条只给条数；分位数由 PG 的 `percentile_cont`
/// 线性插值算出，这里半进位到整天（与 Excel 一致；PG 的 `round(double)` 对 .5 是银行家舍入，所以不在 SQL 里取整）；
/// 录用率只算录用与拒稿。
public final class ExperienceSummaryRules {

  /// 满这么多条才给统计。
  public static final int MIN_SAMPLES = 5;

  /// 只有静态方法。
  private ExperienceSummaryRules() {}

  /// 把原始统计整理成汇总。
  ///
  /// @param stats 原始统计
  /// @return 汇总
  public static ExperienceSummary summarize(ExperienceStats stats) {
    ExperienceSummary.ExperienceSummaryBuilder summary =
        ExperienceSummary.builder()
            .count(Math.toIntExact(stats.windowCount()))
            .allCount(Math.toIntExact(stats.allCount()));
    if (stats.windowCount() < MIN_SAMPLES) {
      return summary.sufficient(false).build();
    }
    long decided = stats.accepted() + stats.rejected();
    return summary
        .sufficient(true)
        .firstRound(
            stats.firstRoundCount() == 0
                ? null
                : ExperienceSummary.FirstRound.of(
                    roundDays(stats.firstRoundMedian()),
                    roundDays(stats.firstRoundP25()),
                    roundDays(stats.firstRoundP75()),
                    Math.toIntExact(stats.firstRoundCount())))
        .deskReject(
            stats.deskRejectCount() == 0
                ? null
                : ExperienceSummary.DeskReject.of(
                    roundDays(stats.deskRejectMedian()), Math.toIntExact(stats.deskRejectCount())))
        .outcomes(
            ExperienceSummary.Outcomes.of(
                Math.toIntExact(stats.accepted()),
                Math.toIntExact(stats.rejected()),
                Math.toIntExact(stats.withdrawn()),
                Math.toIntExact(stats.inProgress())))
        .acceptance(
            decided == 0
                ? null
                : ExperienceSummary.Acceptance.of(
                    (double) stats.accepted() / decided, Math.toIntExact(decided)))
        .build();
  }

  /// 天数半进位到整数。
  ///
  /// @param days 天数（非负）
  /// @return 整天
  public static int roundDays(double days) {
    return BigDecimal.valueOf(days).setScale(0, RoundingMode.HALF_UP).intValueExact();
  }
}
