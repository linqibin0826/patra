package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueDO 转换器。
///
/// 将 `VenueAggregate` 领域聚合根转换为 `VenueDO` 数据库实体。
///
/// **字段映射**：
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
/// **快速访问字段**：
///
/// | 字段 | 来源 |
/// |------|------|
/// | nlm_id | aggregate.getIdentifier(NLM) |
/// | issn_l | aggregate.getIdentifier(ISSN_L) |
/// | openalex_id | aggregate.getIdentifier(OPENALEX) |
/// | abbreviated_title | VenueDetail (由 Repository 同步) |
/// | primary_language | VenueDetail (由 Repository 同步) |
/// | country_code | VenueDetail (由 Repository 同步) |
///
/// **注意**：
///
/// - `identifiers` 由 `VenueIdentifierConverter` 单独处理
/// - 补充数据（detail/stats/apc/societies）由各自的 Converter 处理
///
/// @author linqibin
/// @since 0.1.0
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    imports = {VenueIdentifierType.class})
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
  // 快速访问字段：标识符
  @Mapping(
      target = "nlmId",
      expression = "java(extractIdentifier(aggregate, VenueIdentifierType.NLM))")
  @Mapping(
      target = "issnL",
      expression = "java(extractIdentifier(aggregate, VenueIdentifierType.ISSN_L))")
  @Mapping(
      target = "openalexId",
      expression = "java(extractIdentifier(aggregate, VenueIdentifierType.OPENALEX))")
  // 快速访问字段：详情（由 replaceDetailsBatch 同步）
  @Mapping(target = "abbreviatedTitle", ignore = true)
  @Mapping(target = "primaryLanguage", ignore = true)
  @Mapping(target = "countryCode", ignore = true)
  VenueDO toDO(VenueAggregate aggregate);

  /// 从聚合根提取指定类型的标识符值。
  ///
  /// @param aggregate 聚合根
  /// @param type 标识符类型
  /// @return 标识符值，如果不存在则返回 null
  default String extractIdentifier(VenueAggregate aggregate, VenueIdentifierType type) {
    return aggregate.getIdentifier(type).orElse(null);
  }
}
