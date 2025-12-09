package com.patra.catalog.infra.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueRating;
import com.patra.catalog.infra.persistence.entity.VenueRatingDO;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/// VenueRatingDO 转换器。
///
/// **职责**：
///
/// 在 `VenueRating` 值对象和 `VenueRatingDO` 数据库实体之间转换。
///
/// **注意**：
///
/// - `ratingData` 和 `categories` 在 DO 中是 `JsonNode`，在值对象中是 `String`
/// - `ratingSystem` 在 DO 中是 `String`，在值对象中是枚举
/// - `year` 在 DO 中是 `Short`，在值对象中是 `int`
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class VenueRatingConverter {

  @Autowired protected ObjectMapper objectMapper;

  /// 将值对象转换为数据库实体。
  ///
  /// **注意**：`id` 需要在调用方设置。
  ///
  /// @param entity 值对象
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "year", expression = "java((short) entity.year())")
  @Mapping(target = "ratingSystem", expression = "java(entity.ratingSystem().getCode())")
  @Mapping(target = "ratingData", source = "ratingData", qualifiedByName = "stringToJsonNode")
  @Mapping(target = "categories", source = "categories", qualifiedByName = "stringToJsonNode")
  public abstract VenueRatingDO toDO(VenueRating entity);

  /// 将数据库实体转换为值对象。
  ///
  /// @param doEntity 数据库实体
  /// @return 值对象
  public VenueRating toEntity(VenueRatingDO doEntity) {
    if (doEntity == null) {
      return null;
    }

    RatingSystem ratingSystem = RatingSystem.fromCodeOrNull(doEntity.getRatingSystem());
    if (ratingSystem == null) {
      log.warn("无效的评价体系代码 '{}'，使用默认值 JCR", doEntity.getRatingSystem());
      ratingSystem = RatingSystem.JCR;
    }

    return VenueRating.of(
        doEntity.getVenueId(),
        doEntity.getYear().intValue(),
        ratingSystem,
        doEntity.getQuartile(),
        doEntity.getImpactScore(),
        jsonNodeToString(doEntity.getRatingData()),
        jsonNodeToString(doEntity.getCategories()),
        doEntity.getSourceUrl(),
        doEntity.getFetchedAt());
  }

  /// 将 String 转换为 JsonNode。
  @Named("stringToJsonNode")
  protected JsonNode stringToJsonNode(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      log.warn("JSON 解析失败: {}", json, e);
      return null;
    }
  }

  /// 将 JsonNode 转换为 String。
  protected String jsonNodeToString(JsonNode jsonNode) {
    if (jsonNode == null) {
      return null;
    }
    return jsonNode.toString();
  }
}
