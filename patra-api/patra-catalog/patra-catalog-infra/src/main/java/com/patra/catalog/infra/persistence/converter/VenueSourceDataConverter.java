package com.patra.catalog.infra.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.enums.DataSourceCode;
import com.patra.catalog.domain.model.vo.venue.VenueSourceData;
import com.patra.catalog.infra.persistence.entity.VenueSourceDataDO;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/// VenueSourceDataDO 转换器。
///
/// **职责**：
///
/// 在 `VenueSourceData` 值对象和 `VenueSourceDataDO` 数据库实体之间转换。
///
/// **注意**：
///
/// - `rawData` 和 `extractedData` 在 DO 中是 `JsonNode`，在值对象中是 `String`
/// - `sourceCode` 在 DO 中是 `String`，在值对象中是枚举
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class VenueSourceDataConverter {

  @Autowired protected ObjectMapper objectMapper;

  /// 将值对象转换为数据库实体。
  ///
  /// **注意**：`id` 需要在调用方设置。
  ///
  /// @param entity 值对象
  /// @return 数据库实体
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "sourceCode", expression = "java(entity.sourceCode().getCode())")
  @Mapping(target = "rawData", source = "rawData", qualifiedByName = "stringToJsonNode")
  @Mapping(target = "extractedData", source = "extractedData", qualifiedByName = "stringToJsonNode")
  public abstract VenueSourceDataDO toDO(VenueSourceData entity);

  /// 将数据库实体转换为值对象。
  ///
  /// @param doEntity 数据库实体
  /// @return 值对象
  public VenueSourceData toEntity(VenueSourceDataDO doEntity) {
    if (doEntity == null) {
      return null;
    }

    DataSourceCode sourceCode = DataSourceCode.fromCodeOrNull(doEntity.getSourceCode());
    if (sourceCode == null) {
      log.warn("无效的数据源代码 '{}'，使用默认值 OPENALEX", doEntity.getSourceCode());
      sourceCode = DataSourceCode.OPENALEX;
    }

    return VenueSourceData.of(
        doEntity.getVenueId(),
        sourceCode,
        doEntity.getSourceId(),
        jsonNodeToString(doEntity.getRawData()),
        jsonNodeToString(doEntity.getExtractedData()),
        doEntity.getSourceCreatedAt(),
        doEntity.getSourceUpdatedAt(),
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
