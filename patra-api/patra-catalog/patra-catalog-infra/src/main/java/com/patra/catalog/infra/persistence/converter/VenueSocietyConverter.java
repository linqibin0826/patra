package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.infra.persistence.entity.VenueSocietyDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueSocietyDO 转换器。
///
/// **职责**：
///
/// 在 `Society` 值对象和 `VenueSocietyDO` 数据库实体之间转换。
///
/// **映射关系**：
///
/// | Society 字段 | VenueSocietyDO 字段 |
/// |--------------|---------------------|
/// | url | url |
/// | organization | organization |
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueSocietyConverter {

  /// 将值对象转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param society 值对象
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  VenueSocietyDO toDO(Society society);

  /// 将数据库实体转换为值对象。
  ///
  /// @param doEntity 数据库实体
  /// @return 值对象
  default Society toEntity(VenueSocietyDO doEntity) {
    if (doEntity == null) {
      return null;
    }
    return Society.of(doEntity.getUrl(), doEntity.getOrganization());
  }
}
