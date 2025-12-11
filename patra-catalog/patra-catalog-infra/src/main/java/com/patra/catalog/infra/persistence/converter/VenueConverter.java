package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueDO 转换器（最小聚合根版本）。
///
/// **职责**：
///
/// 将 `VenueAggregate` 领域聚合根转换为 `VenueDO` 数据库实体。
///
/// **CQRS 设计说明**：
///
/// 聚合根已精简为最小化，只包含核心身份标识和来源追踪：
///
/// | 聚合根字段 | DO 字段 |
/// |-----------|---------|
/// | venueType | venue_type |
/// | displayName | display_name |
/// | provenance.code | provenance_code |
/// | provenance.sourceCreatedDate | source_created_date |
/// | provenance.sourceUpdatedDate | source_updated_date |
/// | - | last_synced_at (设置为当前时间) |
///
/// **注意**：
///
/// - `identifiers` 由 `VenueIdentifierConverter` 单独处理
/// - 补充数据（detail/stats/apc/societies）由各自的 Converter 处理
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueConverter {

  /// 将领域聚合根转换为数据库实体。
  ///
  /// @param aggregate 领域聚合根
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueType", expression = "java(aggregate.getVenueType().getCode())")
  @Mapping(target = "provenanceCode", source = "provenance.code")
  @Mapping(target = "sourceCreatedDate", source = "provenance.sourceCreatedDate")
  @Mapping(target = "sourceUpdatedDate", source = "provenance.sourceUpdatedDate")
  @Mapping(target = "lastSyncedAt", expression = "java(java.time.Instant.now())")
  VenueDO toDO(VenueAggregate aggregate);
}
