package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.entity.VenueMetrics;
import com.patra.catalog.infra.persistence.entity.VenueMetricsDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueMetricsDO 转换器。
///
/// **职责**：
///
/// 将 `VenueMetrics` 领域实体转换为 `VenueMetricsDO` 数据库实体（批量导入场景）。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueMetricsConverter {

  /// 将年度指标领域实体转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param metrics 领域实体
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  @Mapping(target = "year", expression = "java((short) metrics.getYear())")
  VenueMetricsDO toDO(VenueMetrics metrics);
}
