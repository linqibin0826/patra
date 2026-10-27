package dev.patra.interaction.adapter.post.rest.response;

import dev.patra.interaction.domain.post.model.read.ExperienceSummary;
import lombok.Builder;

/// 期刊页「同行经验」汇总。`sufficient` 为假（窗口内不到 5 条）时只有 `count` 与 `allCount`，其余为 `null`。天数已
/// 按线性插值算出并半进位到整天；`acceptance.rate` 是 0–1 的比例。
///
/// @param window `3y` / `all`
/// @param count 窗口内条数
/// @param allCount 全部条数
/// @param sufficient 是否给出统计
/// @param firstRound 首轮用时（不含直接拒稿）：`median`、`p25`、`p75`、`sampleSize`
/// @param deskReject 直接拒稿用时：`median`、`sampleSize`
/// @param outcomes 按最终结果的分布：`accepted`、`rejected`、`withdrawn`、`inProgress`
/// @param acceptance 录用率：`rate`、`decidedCount`
@Builder
public record ExperienceSummaryResponse(
    String window,
    int count,
    int allCount,
    boolean sufficient,
    ExperienceSummary.FirstRound firstRound,
    ExperienceSummary.DeskReject deskReject,
    ExperienceSummary.Outcomes outcomes,
    ExperienceSummary.Acceptance acceptance) {

  /// 转换。
  ///
  /// @param window 窗口参数
  /// @param summary 汇总
  /// @return 响应
  public static ExperienceSummaryResponse from(String window, ExperienceSummary summary) {
    return ExperienceSummaryResponse.builder()
        .window(window)
        .count(summary.count())
        .allCount(summary.allCount())
        .sufficient(summary.sufficient())
        .firstRound(summary.firstRound())
        .deskReject(summary.deskReject())
        .outcomes(summary.outcomes())
        .acceptance(summary.acceptance())
        .build();
  }
}
