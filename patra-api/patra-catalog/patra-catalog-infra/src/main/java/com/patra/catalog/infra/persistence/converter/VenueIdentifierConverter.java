package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.entity.VenueIdentifier;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueIdentifierDO 转换器。
///
/// **职责**：
///
/// 将 `VenueIdentifier` 领域实体转换为 `VenueIdentifierDO` 数据库实体（批量导入场景）。
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
  @Mapping(target = "identifierType", expression = "java(identifier.getType().name())")
  @Mapping(target = "identifierValue", source = "value")
  @Mapping(target = "isPrimary", source = "primary")
  VenueIdentifierDO toDO(VenueIdentifier identifier);
}
