package com.patra.catalog.infra.adapter.persistence.converter.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.aggregate.VenueRatingAggregate;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueRatingId;
import com.patra.catalog.infra.adapter.persistence.entity.VenueRatingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

/// 载体评级 JPA 实体转换器。
///
/// **职责**：
///
/// - `VenueRatingAggregate` ↔ `VenueRatingEntity` 双向转换
/// - 值对象（`VenueId`、`VenueRatingId`）与基本类型的映射
/// - JSON 字段（`ratingData`、`categories`）的 String ↔ JsonNode 转换
/// - `RatingSystem` 枚举直接映射（JPA 自动转换）
///
/// **JSON 转换说明**：
///
/// - Domain 层：ratingData/categories 存储为 String（JSON 字符串）
/// - Entity 层：使用 Jackson JsonNode 进行 JSON 处理
/// - 转换时通过 ObjectMapper 进行序列化/反序列化
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public abstract class VenueRatingJpaMapper {

  @Autowired protected ObjectMapper objectMapper;

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate 载体评级聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "venueRatingIdToLong")
  @Mapping(target = "venueId", source = "venueId", qualifiedByName = "venueIdToLong")
  @Mapping(target = "year", source = "year", qualifiedByName = "intToShort")
  @Mapping(target = "ratingData", source = "ratingData", qualifiedByName = "stringToJsonNode")
  @Mapping(target = "categories", source = "categories", qualifiedByName = "stringToJsonNode")
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  public abstract VenueRatingEntity toEntity(VenueRatingAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `VenueRatingAggregate.restore()` 工厂方法重建聚合根。
  ///
  /// @param entity JPA 实体
  /// @return 载体评级聚合根
  public VenueRatingAggregate toAggregate(VenueRatingEntity entity) {
    if (entity == null) {
      return null;
    }

    VenueRatingAggregate aggregate =
        VenueRatingAggregate.restore(
            entity.getId() != null ? VenueRatingId.of(entity.getId()) : null,
            VenueId.of(entity.getVenueId()),
            entity.getYear().intValue(),
            entity.getRatingSystem(),
            entity.getVersion());

    // 恢复可变状态
    aggregate.restoreState(
        entity.getQuartile(),
        entity.getImpactScore(),
        jsonNodeToString(entity.getRatingData()),
        jsonNodeToString(entity.getCategories()),
        entity.getSourceUrl(),
        entity.getFetchedAt());

    return aggregate;
  }

  /// 将 VenueRatingId 转换为 Long。
  @Named("venueRatingIdToLong")
  Long venueRatingIdToLong(VenueRatingId id) {
    return id != null ? id.value() : null;
  }

  /// 将 VenueId 转换为 Long。
  @Named("venueIdToLong")
  Long venueIdToLong(VenueId id) {
    return id != null ? id.value() : null;
  }

  /// 将 int 转换为 Short。
  @Named("intToShort")
  Short intToShort(int value) {
    return (short) value;
  }

  /// 将 String 转换为 JsonNode。
  @Named("stringToJsonNode")
  JsonNode stringToJsonNode(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON: " + json, e);
    }
  }

  /// 将 JsonNode 转换为 String。
  String jsonNodeToString(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize JsonNode", e);
    }
  }

  /// 更新托管实体的可变字段。
  ///
  /// 仅更新可变字段，保持不变量（venueId、year、ratingSystem）不变。
  /// 用于同一事务内的更新操作，避免创建新实体导致的会话冲突。
  ///
  /// @param entity 托管实体（已由 EntityManager.find() 加载）
  /// @param aggregate 包含更新数据的聚合根
  public void updateEntity(VenueRatingEntity entity, VenueRatingAggregate aggregate) {
    if (entity == null || aggregate == null) {
      return;
    }
    // 更新可变字段
    entity.setQuartile(aggregate.getQuartile());
    entity.setImpactScore(aggregate.getImpactScore());
    entity.setRatingData(stringToJsonNode(aggregate.getRatingData()));
    entity.setCategories(stringToJsonNode(aggregate.getCategories()));
    entity.setSourceUrl(aggregate.getSourceUrl());
    entity.setFetchedAt(aggregate.getFetchedAt());
  }
}
