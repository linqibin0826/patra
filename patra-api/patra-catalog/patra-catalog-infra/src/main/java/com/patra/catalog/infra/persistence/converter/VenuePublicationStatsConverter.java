package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.entity.VenuePublicationStats;
import com.patra.catalog.infra.persistence.entity.VenuePublicationStatsDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenuePublicationStatsDO 转换器。
///
/// **职责**：
///
/// 将 `VenuePublicationStats` 领域实体转换为 `VenuePublicationStatsDO` 数据库实体（批量导入场景）。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenuePublicationStatsConverter {

  /// 将年度统计领域实体转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param stats 领域实体
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  @Mapping(target = "year", expression = "java((short) stats.getYear())")
  VenuePublicationStatsDO toDO(VenuePublicationStats stats);
}
