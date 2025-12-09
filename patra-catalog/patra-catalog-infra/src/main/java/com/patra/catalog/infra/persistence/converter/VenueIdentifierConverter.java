package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueIdentifierDO 转换器。
///
/// **职责**：
///
/// 在 `VenueIdentifier` 领域实体和 `VenueIdentifierDO` 数据库实体之间转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueIdentifierConverter {

  /// 将标识符领域实体转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param identifier 领域实体
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  @Mapping(target = "identifierType", expression = "java(identifier.type().name())")
  @Mapping(target = "identifierValue", source = "value")
  @Mapping(target = "isPrimary", constant = "false")
  VenueIdentifierDO toDO(VenueIdentifier identifier);

  /// 将数据库实体转换为领域实体。
  ///
  /// @param doEntity 数据库实体
  /// @return 领域实体，如果 doEntity 为 null 则返回 null
  default VenueIdentifier toEntity(VenueIdentifierDO doEntity) {
    if (doEntity == null) {
      return null;
    }
    return new VenueIdentifier(
        VenueIdentifierType.valueOf(doEntity.getIdentifierType()), doEntity.getIdentifierValue());
  }
}
