package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
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
/// **嵌入式值对象（JSON 字段）**：
///
/// | 聚合根字段 | DO 字段 | 说明 |
/// |-----------|---------|------|
/// | publicationProfile | publication_profile | 出版概况 |
/// | citationMetrics | citation_metrics | 引用指标 |
/// | openAccess | open_access | 开放获取信息 |
/// | affiliatedSocieties | affiliated_societies | 关联学会 |
///
/// **快速访问字段**：
///
/// | 字段 | 来源 |
/// |------|------|
/// | nlm_id | aggregate.getIdentifier(NLM) |
/// | issn_l | aggregate.getIdentifier(ISSN_L) |
/// | openalex_id | aggregate.getIdentifier(OPENALEX) |
/// | abbreviated_title | PublicationProfile.abbreviatedTitle |
/// | primary_language | PublicationProfile.languages.primaryLanguage |
/// | country_code | PublicationProfile.countryCode |
///
/// **注意**：
///
/// - `identifiers` 由 `VenueIdentifierConverter` 单独处理
/// - 嵌入式值对象直接映射（JSON 序列化由 MyBatis-Plus JacksonTypeHandler 处理）
///
/// @author linqibin
/// @since 0.1.0
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    imports = {VenueIdentifierType.class, PublicationProfile.class})
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
  // 快速访问字段：从 PublicationProfile 提取
  @Mapping(
      target = "abbreviatedTitle",
      expression = "java(extractAbbreviatedTitle(aggregate.getPublicationProfile()))")
  @Mapping(
      target = "primaryLanguage",
      expression = "java(extractPrimaryLanguage(aggregate.getPublicationProfile()))")
  @Mapping(
      target = "countryCode",
      expression = "java(extractCountryCode(aggregate.getPublicationProfile()))")
  // 嵌入式值对象（JSON 字段）
  @Mapping(target = "publicationProfile", source = "publicationProfile")
  @Mapping(target = "citationMetrics", source = "citationMetrics")
  @Mapping(target = "openAccess", source = "openAccess")
  @Mapping(target = "affiliatedSocieties", source = "affiliatedSocieties")
  VenueDO toDO(VenueAggregate aggregate);

  /// 从聚合根提取指定类型的标识符值。
  ///
  /// @param aggregate 聚合根
  /// @param type 标识符类型
  /// @return 标识符值，如果不存在则返回 null
  default String extractIdentifier(VenueAggregate aggregate, VenueIdentifierType type) {
    return aggregate.getIdentifier(type).orElse(null);
  }

  /// 从出版概况中提取缩写标题。
  ///
  /// @param profile 出版概况
  /// @return 缩写标题，如果不存在则返回 null
  default String extractAbbreviatedTitle(PublicationProfile profile) {
    return profile != null ? profile.abbreviatedTitle() : null;
  }

  /// 从出版概况中提取主要语言。
  ///
  /// @param profile 出版概况
  /// @return 主要语言代码，如果不存在则返回 null
  default String extractPrimaryLanguage(PublicationProfile profile) {
    if (profile == null || profile.languages() == null) {
      return null;
    }
    return profile.languages().getMainLanguage();
  }

  /// 从出版概况中提取国家代码。
  ///
  /// @param profile 出版概况
  /// @return 国家代码，如果不存在则返回 null
  default String extractCountryCode(PublicationProfile profile) {
    return profile != null ? profile.countryCode() : null;
  }
}
