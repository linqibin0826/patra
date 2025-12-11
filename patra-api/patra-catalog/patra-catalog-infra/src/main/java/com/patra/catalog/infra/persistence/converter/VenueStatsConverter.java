package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.vo.venue.VenueStats;
import com.patra.catalog.infra.persistence.entity.VenueStatsDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueStatsDO 转换器。
///
/// **职责**：
///
/// 在 `VenueStats` 值对象和 `VenueStatsDO` 数据库实体之间转换。
///
/// **映射关系**：
///
/// | VenueStats 字段 | VenueStatsDO 字段 |
/// |-----------------|-------------------|
/// | worksCount | works_count |
/// | citedByCount | cited_by_count |
/// | hIndex | h_index |
/// | i10Index | i10_index |
/// | twoYearMeanCitedness | two_year_mean_citedness |
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueStatsConverter {

  /// 将值对象转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param stats 值对象
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  VenueStatsDO toDO(VenueStats stats);

  /// 将数据库实体转换为值对象。
  ///
  /// @param doEntity 数据库实体
  /// @return 值对象
  default VenueStats toEntity(VenueStatsDO doEntity) {
    if (doEntity == null) {
      return null;
    }
    return VenueStats.of(
        doEntity.getWorksCount(),
        doEntity.getCitedByCount(),
        doEntity.getHIndex(),
        doEntity.getI10Index(),
        doEntity.getTwoYearMeanCitedness());
  }
}
