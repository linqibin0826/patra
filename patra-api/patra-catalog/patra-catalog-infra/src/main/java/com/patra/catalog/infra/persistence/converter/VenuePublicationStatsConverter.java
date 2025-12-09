package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.infra.persistence.entity.VenuePublicationStatsDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenuePublicationStatsDO 转换器。
///
/// **职责**：
///
/// 在 `VenuePublicationStats` 领域实体和 `VenuePublicationStatsDO` 数据库实体之间转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenuePublicationStatsConverter {

  /// 将领域实体转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param entity 领域实体
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  @Mapping(target = "year", expression = "java((short) entity.year())")
  VenuePublicationStatsDO toDO(VenuePublicationStats entity);

  /// 将数据库实体转换为领域实体。
  ///
  /// 使用工厂方法创建 Record 实例，处理 null 值。
  ///
  /// @param doEntity 数据库实体
  /// @return 领域实体，如果 doEntity 为 null 则返回 null
  default VenuePublicationStats toEntity(VenuePublicationStatsDO doEntity) {
    if (doEntity == null) {
      return null;
    }
    return VenuePublicationStats.create(
        doEntity.getYear().intValue(),
        doEntity.getWorksCount() != null ? doEntity.getWorksCount() : 0,
        doEntity.getCitedByCount() != null ? doEntity.getCitedByCount() : 0,
        doEntity.getOaWorksCount());
  }
}
