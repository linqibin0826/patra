package com.patra.catalog.infra.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.aggregate.VenueRatingAggregate;
import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueRatingId;
import com.patra.catalog.infra.persistence.entity.VenueRatingDO;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/// VenueRating 聚合根与 DO 的转换器。
///
/// **职责**：
///
/// 在 `VenueRatingAggregate` 聚合根和 `VenueRatingDO` 数据库实体之间转换。
///
/// **转换说明**：
///
/// - **DO → Aggregate**：使用 `restore()` + `restoreState()` 两步法重建聚合根
/// - **Aggregate → DO**：从聚合根提取属性构建 DO
/// - `ratingData` 和 `categories` 在 DO 中是 `JsonNode`，在聚合根中是 `String`
/// - `ratingSystem` 在 DO 中是 `String`，在聚合根中是枚举
/// - `year` 在 DO 中是 `Short`，在聚合根中是 `int`
///
/// @author linqibin
/// @since 0.6.0
@Slf4j
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class VenueRatingConverter {

  @Autowired protected ObjectMapper objectMapper;

  /// 将聚合根转换为数据库实体。
  ///
  /// @param aggregate 聚合根
  /// @return 数据库实体
  @Mapping(target = "id", source = "id", qualifiedByName = "venueRatingIdToLong")
  @Mapping(target = "year", expression = "java((short) aggregate.getYear())")
  @Mapping(target = "ratingSystem", expression = "java(aggregate.getRatingSystem().getCode())")
  @Mapping(target = "ratingData", source = "ratingData", qualifiedByName = "stringToJsonNode")
  @Mapping(target = "categories", source = "categories", qualifiedByName = "stringToJsonNode")
  @Mapping(target = "version", source = "version")
  public abstract VenueRatingDO toDO(VenueRatingAggregate aggregate);

  /// 将数据库实体转换为聚合根。
  ///
  /// 使用 `restore()` + `restoreState()` 两步法重建聚合根：
  /// 1. `restore()` 恢复聚合根身份（ID、业务唯一键、版本）
  /// 2. `restoreState()` 恢复可变状态
  ///
  /// @param doEntity 数据库实体
  /// @return 聚合根
  public VenueRatingAggregate toAggregate(VenueRatingDO doEntity) {
    if (doEntity == null) {
      return null;
    }

    RatingSystem ratingSystem = RatingSystem.fromCodeOrNull(doEntity.getRatingSystem());
    if (ratingSystem == null) {
      log.warn("无效的评价体系代码 '{}'，使用默认值 JCR", doEntity.getRatingSystem());
      ratingSystem = RatingSystem.JCR;
    }

    // Step 1: 恢复聚合根身份
    VenueRatingAggregate aggregate =
        VenueRatingAggregate.restore(
            VenueRatingId.of(doEntity.getId()),
            doEntity.getVenueId(),
            doEntity.getYear().intValue(),
            ratingSystem,
            doEntity.getVersion());

    // Step 2: 恢复可变状态
    aggregate.restoreState(
        doEntity.getQuartile(),
        doEntity.getImpactScore(),
        jsonNodeToString(doEntity.getRatingData()),
        jsonNodeToString(doEntity.getCategories()),
        doEntity.getSourceUrl(),
        doEntity.getFetchedAt());

    return aggregate;
  }

  /// 将 VenueRatingId 转换为 Long。
  @Named("venueRatingIdToLong")
  protected Long venueRatingIdToLong(VenueRatingId id) {
    return id != null ? id.value() : null;
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
