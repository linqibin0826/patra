package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.enums.VenueRelationType;
import com.patra.catalog.domain.model.vo.venue.VenueRelation;
import com.patra.catalog.infra.persistence.entity.VenueRelationDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// VenueRelationDO 转换器。
///
/// **职责**：
///
/// 在 `VenueRelation` 领域实体和 `VenueRelationDO` 数据库实体之间转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueRelationConverter {

  Logger log = LoggerFactory.getLogger(VenueRelationConverter.class);

  /// 将领域实体转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param entity 领域实体
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  @Mapping(target = "relationType", expression = "java(entity.relationType().getCode())")
  VenueRelationDO toDO(VenueRelation entity);

  /// 将数据库实体转换为领域实体。
  ///
  /// 处理枚举转换，当枚举值无效时使用默认值 PRECEDING 并记录警告日志。
  ///
  /// @param doEntity 数据库实体
  /// @return 领域实体，如果 doEntity 为 null 则返回 null
  default VenueRelation toEntity(VenueRelationDO doEntity) {
    if (doEntity == null) {
      return null;
    }

    VenueRelationType relationType = VenueRelationType.fromCodeOrNull(doEntity.getRelationType());
    if (relationType == null) {
      log.warn(
          "无效的关系类型代码 '{}' (relatedTitle='{}')，使用默认值 PRECEDING",
          doEntity.getRelationType(),
          doEntity.getRelatedTitle());
      relationType = VenueRelationType.PRECEDING;
    }
    return new VenueRelation(
        doEntity.getRelatedVenueId(),
        doEntity.getRelatedNlmId(),
        doEntity.getRelatedTitle(),
        relationType,
        doEntity.getEffectiveDate(),
        doEntity.getNotes());
  }
}
