package com.patra.catalog.infra.batch.venue;

import com.patra.starter.batch.core.JobParams;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/// 期刊富化 Job 共享参数（LetPub / Scopus 通用）。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VenueEnrichmentJobParams implements JobParams {

  /// 目标评级年份（如 2025），Reader 筛选缺少该年份评级数据的期刊。
  private Long targetYear;

  /// 最低被引次数阈值，0 表示不过滤。
  private Long minCitedByCount;
}
