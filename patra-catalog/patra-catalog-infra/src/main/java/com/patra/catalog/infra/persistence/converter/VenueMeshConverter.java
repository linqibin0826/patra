package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.vo.venue.VenueMesh;
import com.patra.catalog.infra.persistence.entity.VenueMeshDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueMeshDO 转换器。
///
/// **职责**：
///
/// 在 `VenueMesh` 领域实体和 `VenueMeshDO` 数据库实体之间转换。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueMeshConverter {

  /// 将领域实体转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param entity 领域实体
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  VenueMeshDO toDO(VenueMesh entity);

  /// 将数据库实体转换为领域实体。
  ///
  /// @param doEntity 数据库实体
  /// @return 领域实体，如果 doEntity 为 null 则返回 null
  default VenueMesh toEntity(VenueMeshDO doEntity) {
    if (doEntity == null) {
      return null;
    }
    return new VenueMesh(
        doEntity.getDescriptorName(),
        doEntity.getDescriptorUi(),
        Boolean.TRUE.equals(doEntity.getIsMajorTopic()),
        doEntity.getQualifierName(),
        doEntity.getQualifierUi());
  }
}
