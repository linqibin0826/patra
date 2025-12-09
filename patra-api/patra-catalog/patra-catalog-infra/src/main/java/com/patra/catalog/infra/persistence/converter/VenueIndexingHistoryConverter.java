package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.enums.CitationSubset;
import com.patra.catalog.domain.model.enums.IndexingTreatment;
import com.patra.catalog.domain.model.vo.venue.VenueIndexingHistory;
import com.patra.catalog.infra.persistence.entity.VenueIndexingHistoryDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueIndexingHistoryDO 转换器。
///
/// **职责**：
///
/// 在 `VenueIndexingHistory` 领域实体和 `VenueIndexingHistoryDO` 数据库实体之间转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueIndexingHistoryConverter {

  /// 将领域实体转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param entity 领域实体
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  @Mapping(
      target = "indexingTreatment",
      expression =
          "java(entity.indexingTreatment() != null ? entity.indexingTreatment().getCode() : null)")
  @Mapping(
      target = "citationSubset",
      expression =
          "java(entity.citationSubset() != null ? entity.citationSubset().getCode() : null)")
  VenueIndexingHistoryDO toDO(VenueIndexingHistory entity);

  /// 将数据库实体转换为领域实体。
  ///
  /// 处理枚举转换，枚举值可能为 null。
  ///
  /// @param doEntity 数据库实体
  /// @return 领域实体
  default VenueIndexingHistory toEntity(VenueIndexingHistoryDO doEntity) {
    if (doEntity == null) {
      return null;
    }
    IndexingTreatment treatment =
        doEntity.getIndexingTreatment() != null
            ? IndexingTreatment.fromCodeOrNull(doEntity.getIndexingTreatment())
            : null;
    CitationSubset subset =
        doEntity.getCitationSubset() != null
            ? CitationSubset.fromCodeOrNull(doEntity.getCitationSubset())
            : null;

    return new VenueIndexingHistory(
        doEntity.getIndexingSource(),
        Boolean.TRUE.equals(doEntity.getCurrentlyIndexed()),
        treatment,
        subset,
        doEntity.getStartYear(),
        doEntity.getStartVolume(),
        doEntity.getStartIssue(),
        doEntity.getEndYear(),
        doEntity.getEndVolume(),
        doEntity.getEndIssue());
  }
}
