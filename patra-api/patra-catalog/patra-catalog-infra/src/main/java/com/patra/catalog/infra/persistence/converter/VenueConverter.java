package com.patra.catalog.infra.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.ApcInfo;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenueStats;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/// VenueDO 转换器。
///
/// **职责**：
///
/// 将 `VenueAggregate` 领域聚合根转换为 `VenueDO` 数据库实体（批量导入场景）。
///
/// **设计说明**：
///
/// - 使用 abstract class 而非 interface，因为需要 `ObjectMapper` 进行 JSON 字段序列化
/// - 使用 `unmappedTargetPolicy = IGNORE` 忽略未映射的目标字段
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class VenueConverter {

  @Autowired protected ObjectMapper objectMapper;

  /// 将领域聚合根转换为数据库实体。
  ///
  /// **映射说明**：
  ///
  /// - `venueType`：通过 `getCode()` 获取枚举编码
  /// - `hostOrganization`：展开为 `hostOrganizationId`、`hostOrganizationName`、`hostOrganizationLineage`
  /// - `currentStats`：展开为统计字段，null 时使用默认值
  /// - `apcInfo`：展开为 `apcUsd` 和 `apcPrices`（JSON）
  /// - `societies`：转换为 JSON
  /// - `provenance`：展开为 `provenanceCode`、`sourceCreatedDate`、`sourceUpdatedDate`
  /// - `lastSyncedAt`：设置为当前时间
  ///
  /// **注意**：`id` 需要在调用方设置。
  ///
  /// @param aggregate 领域聚合根
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "venueType", expression = "java(aggregate.getVenueType().getCode())")
  @Mapping(target = "hostOrganizationId", source = "hostOrganization.id")
  @Mapping(target = "hostOrganizationName", source = "hostOrganization.name")
  @Mapping(target = "hostOrganizationLineage", source = "hostOrganization.lineage")
  @Mapping(target = "worksCount", source = "currentStats", qualifiedByName = "extractWorksCount")
  @Mapping(
      target = "citedByCount",
      source = "currentStats",
      qualifiedByName = "extractCitedByCount")
  @Mapping(target = "HIndex", source = "currentStats.hIndex")
  @Mapping(target = "i10Index", source = "currentStats.i10Index")
  @Mapping(target = "twoYearMeanCitedness", source = "currentStats.twoYearMeanCitedness")
  @Mapping(target = "apcUsd", source = "apcInfo.usd")
  @Mapping(target = "apcPrices", source = "apcInfo.prices", qualifiedByName = "apcPricesToJson")
  @Mapping(target = "societies", source = "societies", qualifiedByName = "societiesToJson")
  @Mapping(target = "provenanceCode", source = "provenance.code")
  @Mapping(target = "sourceCreatedDate", source = "provenance.sourceCreatedDate")
  @Mapping(target = "sourceUpdatedDate", source = "provenance.sourceUpdatedDate")
  @Mapping(target = "lastSyncedAt", expression = "java(java.time.Instant.now())")
  @Mapping(target = "isOa", source = "oa")
  @Mapping(target = "isInDoaj", source = "inDoaj")
  @Mapping(target = "isCore", source = "core")
  public abstract VenueDO toDO(VenueAggregate aggregate);

  // ========================================
  // 自定义映射方法
  // ========================================

  /// 从统计对象中提取 worksCount，null 时返回 0。
  @Named("extractWorksCount")
  protected Integer extractWorksCount(VenueStats stats) {
    if (stats == null || stats.worksCount() == null) {
      return 0;
    }
    return stats.worksCount();
  }

  /// 从统计对象中提取 citedByCount，null 时返回 0。
  @Named("extractCitedByCount")
  protected Integer extractCitedByCount(VenueStats stats) {
    if (stats == null || stats.citedByCount() == null) {
      return 0;
    }
    return stats.citedByCount();
  }

  /// 将 APC 价格列表转换为 JSON。
  @Named("apcPricesToJson")
  protected JsonNode apcPricesToJson(List<ApcInfo.ApcPrice> prices) {
    if (prices == null || prices.isEmpty()) {
      return null;
    }

    ArrayNode arrayNode = objectMapper.createArrayNode();
    for (ApcInfo.ApcPrice price : prices) {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("price", price.price());
      if (price.currency() != null) {
        node.put("currency", price.currency());
      }
      arrayNode.add(node);
    }
    return arrayNode;
  }

  /// 将学会列表转换为 JSON。
  @Named("societiesToJson")
  protected JsonNode societiesToJson(List<Society> societies) {
    if (societies == null || societies.isEmpty()) {
      return null;
    }

    ArrayNode arrayNode = objectMapper.createArrayNode();
    for (Society society : societies) {
      ObjectNode node = objectMapper.createObjectNode();
      if (society.url() != null) {
        node.put("url", society.url());
      }
      if (society.organization() != null) {
        node.put("organization", society.organization());
      }
      arrayNode.add(node);
    }
    return arrayNode;
  }
}
