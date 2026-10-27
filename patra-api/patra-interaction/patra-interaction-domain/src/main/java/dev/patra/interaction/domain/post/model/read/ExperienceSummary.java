package dev.patra.interaction.domain.post.model.read;

import lombok.Builder;

/// 期刊页「同行经验」汇总：窗口内不到 5 条时只有 `count` 与 `allCount`（`sufficient` 为假），其余为 `null`。
///
/// @param count 窗口内条数
/// @param allCount 全部条数（「看全部 12 条」用）
/// @param sufficient 是否满 5 条、给出统计
/// @param firstRound 首轮用时（不含直接拒稿），样本为 0 时 `null`
/// @param deskReject 直接拒稿用时，样本为 0 时 `null`
/// @param outcomes 按最终结果的分布
/// @param acceptance 录用率，没有已完结稿件时 `null`
@Builder
public record ExperienceSummary(
    int count,
    int allCount,
    boolean sufficient,
    FirstRound firstRound,
    DeskReject deskReject,
    Outcomes outcomes,
    Acceptance acceptance) {

  /// 首轮用时（天，半进位）。
  ///
  /// @param median 中位数
  /// @param p25 P25
  /// @param p75 P75
  /// @param sampleSize 样本数
  public record FirstRound(int median, int p25, int p75, int sampleSize) {

    /// 创建。
    ///
    /// @param median 中位数
    /// @param p25 P25
    /// @param p75 P75
    /// @param sampleSize 样本数
    /// @return 首轮用时
    public static FirstRound of(int median, int p25, int p75, int sampleSize) {
      return new FirstRound(median, p25, p75, sampleSize);
    }
  }

  /// 直接拒稿用时（天，半进位）。
  ///
  /// @param median 中位数
  /// @param sampleSize 样本数
  public record DeskReject(int median, int sampleSize) {

    /// 创建。
    ///
    /// @param median 中位数
    /// @param sampleSize 样本数
    /// @return 直接拒稿用时
    public static DeskReject of(int median, int sampleSize) {
      return new DeskReject(median, sampleSize);
    }
  }

  /// 按最终结果的分布。
  ///
  /// @param accepted 录用
  /// @param rejected 拒稿
  /// @param withdrawn 撤稿
  /// @param inProgress 进行中
  public record Outcomes(int accepted, int rejected, int withdrawn, int inProgress) {

    /// 创建。
    ///
    /// @param accepted 录用
    /// @param rejected 拒稿
    /// @param withdrawn 撤稿
    /// @param inProgress 进行中
    /// @return 分布
    public static Outcomes of(int accepted, int rejected, int withdrawn, int inProgress) {
      return new Outcomes(accepted, rejected, withdrawn, inProgress);
    }
  }

  /// 录用率 = 录用 ÷（录用 + 拒稿）。
  ///
  /// @param rate 比例 0–1
  /// @param decidedCount 参与计算的已完结条数
  public record Acceptance(double rate, int decidedCount) {

    /// 创建。
    ///
    /// @param rate 比例
    /// @param decidedCount 已完结条数
    /// @return 录用率
    public static Acceptance of(double rate, int decidedCount) {
      return new Acceptance(rate, decidedCount);
    }
  }
}
