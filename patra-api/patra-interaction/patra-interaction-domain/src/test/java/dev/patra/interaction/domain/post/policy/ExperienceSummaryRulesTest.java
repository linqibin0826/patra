package dev.patra.interaction.domain.post.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.post.model.read.ExperienceStats;
import dev.patra.interaction.domain.post.model.read.ExperienceSummary;
import org.junit.jupiter.api.Test;

/// 期刊汇总的口径（spec 第 7 节、决策 G）：不到 5 条只给条数；分位数半进位到整天；录用率只算录用与拒稿。
class ExperienceSummaryRulesTest {

  /// 窗口内 15 条：非直接拒稿 12 条（P25 17.5、中位 25、P75 32.5），直接拒稿 3 条（中位 4.5）。
  ///
  /// @return 构建器
  private static ExperienceStats.ExperienceStatsBuilder full() {
    return ExperienceStats.builder()
        .allCount(20)
        .windowCount(15)
        .firstRoundCount(12)
        .firstRoundP25(17.5)
        .firstRoundMedian(25.0)
        .firstRoundP75(32.5)
        .deskRejectCount(3)
        .deskRejectMedian(4.5)
        .accepted(6)
        .rejected(6)
        .withdrawn(1)
        .inProgress(2);
  }

  /// 窗口内 4 条：只有条数与全部条数。
  @Test
  void should_return_only_counts_below_five() {
    ExperienceSummary summary =
        ExperienceSummaryRules.summarize(full().windowCount(4).allCount(9).build());

    assertThat(summary.count()).isEqualTo(4);
    assertThat(summary.allCount()).isEqualTo(9);
    assertThat(summary.sufficient()).isFalse();
    assertThat(summary.firstRound()).isNull();
    assertThat(summary.deskReject()).isNull();
    assertThat(summary.outcomes()).isNull();
    assertThat(summary.acceptance()).isNull();
  }

  /// 满 5 条：各项统计齐全。
  @Test
  void should_return_full_summary_from_five() {
    ExperienceSummary summary = ExperienceSummaryRules.summarize(full().windowCount(5).build());

    assertThat(summary.sufficient()).isTrue();
    assertThat(summary.count()).isEqualTo(5);
    assertThat(summary.firstRound()).isEqualTo(ExperienceSummary.FirstRound.of(25, 18, 33, 12));
    assertThat(summary.deskReject()).isEqualTo(ExperienceSummary.DeskReject.of(5, 3));
    assertThat(summary.outcomes()).isEqualTo(ExperienceSummary.Outcomes.of(6, 6, 1, 2));
  }

  /// 四舍五入是半进位（PG 对 double 的 round 是银行家舍入，不能用）。
  @Test
  void should_round_half_up() {
    assertThat(ExperienceSummaryRules.roundDays(17.5)).isEqualTo(18);
    assertThat(ExperienceSummaryRules.roundDays(32.5)).isEqualTo(33);
    assertThat(ExperienceSummaryRules.roundDays(42.49)).isEqualTo(42);
    assertThat(ExperienceSummaryRules.roundDays(0.0)).isZero();
  }

  /// 样本为 0 的组整组为 null。
  @Test
  void should_omit_empty_groups() {
    ExperienceSummary summary =
        ExperienceSummaryRules.summarize(
            full()
                .firstRoundCount(0)
                .firstRoundP25(null)
                .firstRoundMedian(null)
                .firstRoundP75(null)
                .deskRejectCount(0)
                .deskRejectMedian(null)
                .build());

    assertThat(summary.firstRound()).isNull();
    assertThat(summary.deskReject()).isNull();
  }

  /// 录用率 = 录用 ÷（录用 + 拒稿），撤稿与进行中不进分母。
  @Test
  void should_count_only_accepted_and_rejected_in_acceptance() {
    assertThat(ExperienceSummaryRules.summarize(full().build()).acceptance())
        .isEqualTo(ExperienceSummary.Acceptance.of(0.5, 12));
    assertThat(
            ExperienceSummaryRules.summarize(full().accepted(1).rejected(3).build())
                .acceptance()
                .rate())
        .isEqualTo(0.25);
  }

  /// 没有已完结的稿件：没有录用率。
  @Test
  void should_omit_acceptance_without_decided() {
    assertThat(
            ExperienceSummaryRules.summarize(full().accepted(0).rejected(0).build()).acceptance())
        .isNull();
  }
}
