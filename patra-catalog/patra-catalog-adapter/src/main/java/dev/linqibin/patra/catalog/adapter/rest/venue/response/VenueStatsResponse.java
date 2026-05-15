package dev.linqibin.patra.catalog.adapter.rest.venue.response;

import java.util.List;

/// 期刊年度发文统计响应。
///
/// 包含历年发文量、引用量和 OA 发文量，供前端趋势图展示。
///
/// @param stats 年度统计列表（按年份降序）
public record VenueStatsResponse(List<YearStatsDto> stats) {

  /// 单年统计 DTO。
  ///
  /// @param year 统计年份
  /// @param worksCount 该年发表作品数量
  /// @param citedByCount 该年被引用次数
  /// @param oaWorksCount 该年 OA 作品数量（可为 null）
  public record YearStatsDto(short year, int worksCount, int citedByCount, Integer oaWorksCount) {}
}
