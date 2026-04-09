package com.patra.catalog.adapter.scheduler.job;

import com.xxl.job.core.context.XxlJobHelper;

/// 期刊富化 Job 的 XXL-Job 参数解析结果。
///
/// **参数格式**：`targetYear[,minCitedByCount]`
///
/// - `targetYear`（必填）：目标评级年份，如 `2025`
/// - `minCitedByCount`（可选）：最低被引次数阈值，默认 0（不过滤）
///
/// @param targetYear 目标评级年份
/// @param minCitedByCount 最低被引次数阈值，0 表示不过滤
/// @author linqibin
/// @since 0.1.0
public record VenueEnrichJobParam(short targetYear, int minCitedByCount) {

  /// 从 XXL-Job 任务参数解析。
  ///
  /// @return 解析后的参数
  /// @throws IllegalArgumentException 参数为空或格式错误
  public static VenueEnrichJobParam fromXxlJobParam() {
    String jobParam = XxlJobHelper.getJobParam();
    if (jobParam == null || jobParam.isBlank()) {
      throw new IllegalArgumentException(
          "缺少必填参数，格式：targetYear[,minCitedByCount]（如 2025 或 2025,1000）");
    }

    String[] parts = jobParam.trim().split(",");
    short targetYear = parseTargetYear(parts[0]);
    int minCitedByCount = parts.length > 1 ? parseMinCitedByCount(parts[1]) : 0;
    return new VenueEnrichJobParam(targetYear, minCitedByCount);
  }

  /// 解析 targetYear 参数（合法范围 2000-2100）。
  private static short parseTargetYear(String value) {
    try {
      short year = Short.parseShort(value.trim());
      if (year < 2000 || year > 2100) {
        throw new IllegalArgumentException("targetYear 超出合理范围：" + year + "，应在 2000-2100 之间");
      }
      return year;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("targetYear 格式错误：'" + value + "'，应为年份数字（如 2025）");
    }
  }

  /// 解析 minCitedByCount 参数（非负整数）。
  private static int parseMinCitedByCount(String value) {
    try {
      int count = Integer.parseInt(value.trim());
      if (count < 0) {
        throw new IllegalArgumentException("minCitedByCount 不能为负数：" + count);
      }
      return count;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("minCitedByCount 格式错误：'" + value + "'，应为非负整数（如 1000）");
    }
  }
}
