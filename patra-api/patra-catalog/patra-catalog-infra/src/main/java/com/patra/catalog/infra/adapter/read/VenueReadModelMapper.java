package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// Venue 读模型 MapStruct 转换器。
///
/// 将 JPA Entity 转换为 CQRS 读端的 ReadModel。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VenueReadModelMapper {

  /// 将 VenueEntity 转换为列表摘要读模型。
  ///
  /// @param entity Venue JPA 实体
  /// @return Venue 摘要读模型
  VenueSummaryReadModel toReadModel(VenueEntity entity);
}
