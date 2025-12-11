package com.patra.catalog.infra.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.catalog.domain.model.vo.venue.HostOrganization;
import com.patra.catalog.domain.model.vo.venue.IndexingInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationHistory;
import com.patra.catalog.domain.model.vo.venue.VenueDetail;
import com.patra.catalog.domain.model.vo.venue.VenueLanguages;
import com.patra.catalog.infra.persistence.entity.VenueDetailDO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// VenueDetailDO 转换器。
///
/// **职责**：
///
/// 在 `VenueDetail` 值对象和 `VenueDetailDO` 数据库实体之间转换。
///
/// **映射关系**：
///
/// | VenueDetail 字段 | VenueDetailDO 字段 | 说明 |
/// |------------------|-------------------|------|
/// | abbreviatedTitle | abbreviated_title | 直接映射 |
/// | alternateTitles | alternate_titles | List → JSON |
/// | homepageUrl | homepage_url | 直接映射 |
/// | frequency | frequency | 直接映射 |
/// | publicationHistory.startYear | publication_start_year | 嵌套展平 |
/// | publicationHistory.endYear | publication_end_year | 嵌套展平 |
/// | publicationHistory.ceased | ceased | 嵌套展平 |
/// | languages.getMainLanguage() | primary_language | 提取主语言 |
/// | languages | languages | 完整 JSON |
/// | hostOrganization.id | host_organization_id | 嵌套展平 |
/// | hostOrganization.name | host_organization_name | 嵌套展平 |
/// | hostOrganization.lineage | host_organization_lineage | List → JSON |
/// | countryCode | country_code | 直接映射 |
/// | indexingInfo.status | indexing_status | 嵌套展平 |
/// | indexingInfo.medlineTa | medline_ta | 嵌套展平 |
/// | indexingInfo.isoAbbreviation | iso_abbreviation | 嵌套展平 |
/// | isOa | is_oa | 直接映射 |
/// | isInDoaj | is_in_doaj | 直接映射 |
/// | oaType | oa_type | 直接映射 |
/// | extData | ext_data | Map → JSON |
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueDetailConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /// 将值对象转换为数据库实体。
  ///
  /// **注意**：`id` 和 `venueId` 需要在调用方设置。
  ///
  /// @param detail 值对象
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueId", ignore = true)
  @Mapping(target = "publicationStartYear", expression = "java(getStartYear(detail))")
  @Mapping(target = "publicationEndYear", expression = "java(getEndYear(detail))")
  @Mapping(target = "ceased", expression = "java(getCeased(detail))")
  @Mapping(target = "primaryLanguage", expression = "java(getPrimaryLanguage(detail))")
  @Mapping(target = "languages", expression = "java(languagesToJson(detail.languages()))")
  @Mapping(target = "hostOrganizationId", expression = "java(getHostOrgId(detail))")
  @Mapping(target = "hostOrganizationName", expression = "java(getHostOrgName(detail))")
  @Mapping(target = "hostOrganizationLineage", expression = "java(getHostOrgLineage(detail))")
  @Mapping(target = "indexingStatus", expression = "java(getIndexingStatus(detail))")
  @Mapping(target = "medlineTa", expression = "java(getMedlineTa(detail))")
  @Mapping(target = "isoAbbreviation", expression = "java(getIsoAbbreviation(detail))")
  VenueDetailDO toDO(VenueDetail detail);

  /// 将数据库实体转换为值对象。
  ///
  /// @param doEntity 数据库实体
  /// @return 值对象
  default VenueDetail toEntity(VenueDetailDO doEntity) {
    if (doEntity == null) {
      return null;
    }

    return VenueDetail.builder()
        .abbreviatedTitle(doEntity.getAbbreviatedTitle())
        .alternateTitles(doEntity.getAlternateTitles())
        .homepageUrl(doEntity.getHomepageUrl())
        .frequency(doEntity.getFrequency())
        .publicationHistory(buildPublicationHistory(doEntity))
        .languages(buildLanguages(doEntity))
        .hostOrganization(buildHostOrganization(doEntity))
        .countryCode(doEntity.getCountryCode())
        .indexingInfo(buildIndexingInfo(doEntity))
        .isOa(Boolean.TRUE.equals(doEntity.getIsOa()))
        .isInDoaj(Boolean.TRUE.equals(doEntity.getIsInDoaj()))
        .oaType(doEntity.getOaType())
        .extData(doEntity.getExtData())
        .build();
  }

  // ========== toDO 辅助方法（嵌套展平） ==========

  /// 获取创刊年份。
  default Short getStartYear(VenueDetail detail) {
    if (detail.publicationHistory() == null || detail.publicationHistory().startYear() == null) {
      return null;
    }
    return detail.publicationHistory().startYear().shortValue();
  }

  /// 获取停刊年份。
  default Short getEndYear(VenueDetail detail) {
    if (detail.publicationHistory() == null || detail.publicationHistory().endYear() == null) {
      return null;
    }
    return detail.publicationHistory().endYear().shortValue();
  }

  /// 获取是否停刊。
  default Boolean getCeased(VenueDetail detail) {
    if (detail.publicationHistory() == null) {
      return null;
    }
    return detail.publicationHistory().ceased();
  }

  /// 获取主语言。
  default String getPrimaryLanguage(VenueDetail detail) {
    if (detail.languages() == null) {
      return null;
    }
    return detail.languages().getMainLanguage();
  }

  /// 将语言信息转换为 JSON。
  default JsonNode languagesToJson(VenueLanguages languages) {
    if (languages == null || languages.isEmpty()) {
      return null;
    }
    ObjectNode node = OBJECT_MAPPER.createObjectNode();
    if (languages.hasPrimaryLanguages()) {
      node.set("primary", OBJECT_MAPPER.valueToTree(languages.primary()));
    }
    if (languages.hasSummaryLanguages()) {
      node.set("summary", OBJECT_MAPPER.valueToTree(languages.summary()));
    }
    return node;
  }

  /// 获取宿主机构 ID。
  default String getHostOrgId(VenueDetail detail) {
    return detail.hostOrganization() != null ? detail.hostOrganization().id() : null;
  }

  /// 获取宿主机构名称。
  default String getHostOrgName(VenueDetail detail) {
    return detail.hostOrganization() != null ? detail.hostOrganization().name() : null;
  }

  /// 获取宿主机构所有权链。
  default List<String> getHostOrgLineage(VenueDetail detail) {
    if (detail.hostOrganization() == null || !detail.hostOrganization().hasLineage()) {
      return null;
    }
    return detail.hostOrganization().lineage();
  }

  /// 获取索引状态。
  default String getIndexingStatus(VenueDetail detail) {
    return detail.indexingInfo() != null ? detail.indexingInfo().status() : null;
  }

  /// 获取 MEDLINE 缩写。
  default String getMedlineTa(VenueDetail detail) {
    return detail.indexingInfo() != null ? detail.indexingInfo().medlineTa() : null;
  }

  /// 获取 ISO 缩写。
  default String getIsoAbbreviation(VenueDetail detail) {
    return detail.indexingInfo() != null ? detail.indexingInfo().isoAbbreviation() : null;
  }

  // ========== toEntity 辅助方法（重建嵌套对象） ==========

  /// 重建出版历史值对象。
  default PublicationHistory buildPublicationHistory(VenueDetailDO doEntity) {
    if (doEntity.getPublicationStartYear() == null
        && doEntity.getPublicationEndYear() == null
        && doEntity.getCeased() == null) {
      return null;
    }
    Integer startYear =
        doEntity.getPublicationStartYear() != null
            ? doEntity.getPublicationStartYear().intValue()
            : null;
    Integer endYear =
        doEntity.getPublicationEndYear() != null
            ? doEntity.getPublicationEndYear().intValue()
            : null;
    boolean ceased = Boolean.TRUE.equals(doEntity.getCeased());
    return PublicationHistory.of(startYear, endYear, ceased);
  }

  /// 重建语言信息值对象。
  default VenueLanguages buildLanguages(VenueDetailDO doEntity) {
    JsonNode json = doEntity.getLanguages();
    if (json == null || json.isNull()) {
      // 如果只有主语言字段但没有完整 JSON
      if (doEntity.getPrimaryLanguage() != null) {
        return VenueLanguages.ofSingleLanguage(doEntity.getPrimaryLanguage());
      }
      return null;
    }

    List<String> primary = jsonArrayToList(json.path("primary"));
    List<String> summary = jsonArrayToList(json.path("summary"));

    if (primary.isEmpty() && summary.isEmpty()) {
      return null;
    }
    return VenueLanguages.of(primary, summary);
  }

  /// 重建宿主机构值对象。
  default HostOrganization buildHostOrganization(VenueDetailDO doEntity) {
    if (doEntity.getHostOrganizationId() == null || doEntity.getHostOrganizationName() == null) {
      return null;
    }
    return HostOrganization.of(
        doEntity.getHostOrganizationId(),
        doEntity.getHostOrganizationName(),
        doEntity.getHostOrganizationLineage());
  }

  /// 重建索引信息值对象。
  default IndexingInfo buildIndexingInfo(VenueDetailDO doEntity) {
    if (doEntity.getIndexingStatus() == null
        && doEntity.getMedlineTa() == null
        && doEntity.getIsoAbbreviation() == null) {
      return null;
    }
    return IndexingInfo.of(
        doEntity.getIndexingStatus(), doEntity.getMedlineTa(), doEntity.getIsoAbbreviation());
  }

  /// 将 JSON 数组转换为字符串列表。
  ///
  /// **注意**：使用标准迭代方式解析数组元素，而非 `findValuesAsText()`。
  /// `findValuesAsText(fieldName)` 是递归查找指定字段名的值，不适用于解析简单字符串数组。
  private static List<String> jsonArrayToList(JsonNode arrayNode) {
    if (arrayNode == null || !arrayNode.isArray()) {
      return List.of();
    }
    if (arrayNode.isEmpty()) {
      return List.of();
    }
    java.util.ArrayList<String> result = new java.util.ArrayList<>();
    for (JsonNode node : arrayNode) {
      if (node.isTextual() && !node.asText().isBlank()) {
        result.add(node.asText());
      }
    }
    return result.isEmpty() ? List.of() : List.copyOf(result);
  }
}
