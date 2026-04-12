package com.patra.catalog.domain.model.read.venue;

import java.util.List;

/// 期刊年度发文统计读模型。
///
/// 用于前端趋势图展示，非分页接口——发文统计数据有界（~50 年 ≈ ~50 条记录）。
///
/// @param stats 年度统计列表（按年份降序）
public record VenueStatsReadModel(List<YearStats> stats) {

  /// 防御性拷贝，确保不可变。
  public VenueStatsReadModel {
    stats = stats != null ? List.copyOf(stats) : List.of();
  }

  /// 单年统计记录。
  ///
  /// @param year 统计年份
  /// @param worksCount 该年发表作品数量
  /// @param citedByCount 该年被引用次数
  /// @param oaWorksCount 该年 OA 作品数量（可为 null）
  public record YearStats(short year, int worksCount, int citedByCount, Integer oaWorksCount) {}
}
