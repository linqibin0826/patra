package dev.patra.analytics.domain.insight.model.vo;

import java.time.Instant;
import java.util.List;

/// 热读页：快照的前几行与榜上总数。
///
/// @param computedAt 快照的计算时间；从没算过为 null
/// @param rankedTotal 榜上一共几篇（0 ~ 100）
/// @param items 前几行，按位置
public record HotReadingSummary(Instant computedAt, int rankedTotal, List<HotReadingEntry> items) {

  /// 防御性拷贝。
  public HotReadingSummary {
    items = items != null ? List.copyOf(items) : List.of();
  }

  /// 构造。
  ///
  /// @param computedAt 计算时间
  /// @param rankedTotal 榜上总数
  /// @param items 前几行
  /// @return 热读页
  public static HotReadingSummary of(
      Instant computedAt, int rankedTotal, List<HotReadingEntry> items) {
    return new HotReadingSummary(computedAt, rankedTotal, items);
  }

  /// 任务从没成功跑过。
  ///
  /// @return 空页
  public static HotReadingSummary empty() {
    return new HotReadingSummary(null, 0, List.of());
  }
}
