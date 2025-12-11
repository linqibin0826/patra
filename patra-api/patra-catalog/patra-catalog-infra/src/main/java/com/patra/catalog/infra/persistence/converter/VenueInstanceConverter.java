package com.patra.catalog.infra.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.infra.persistence.entity.VenueInstanceDO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/// VenueInstanceDO 转换器。
///
/// **职责**：
///
/// 在 `VenueInstanceAggregate` 聚合根和 `VenueInstanceDO` 数据库实体之间转换。
///
/// **映射策略**：
///
/// - `toDO`: 聚合根 → DO（用于插入/更新）
/// - `toAggregate`: DO → 聚合根（使用 `VenueInstanceAggregate.restore()`）
///
/// **instanceMetadata 转换**：
///
/// - 聚合根使用 `String` 类型（`instanceMetadataJson`）保持领域层纯净
/// - DO 使用 `JsonNode` 类型（`instanceMetadata`）便于数据库 JSON 字段处理
/// - 转换时自动进行 `String` ↔ `JsonNode` 互转
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueInstanceConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /// 将聚合根转换为数据库实体。
  ///
  /// **注意**：`instanceMetadata` 字段通过 `@AfterMapping` 处理 JSON 转换。
  ///
  /// @param aggregate 聚合根
  /// @return 数据库实体
  @Mapping(target = "instanceMetadata", ignore = true)
  VenueInstanceDO toDO(VenueInstanceAggregate aggregate);

  /// 在 MapStruct 映射完成后，处理 instanceMetadataJson → instanceMetadata 转换。
  @AfterMapping
  default void mapInstanceMetadata(
      VenueInstanceAggregate aggregate, @MappingTarget VenueInstanceDO doEntity) {
    String jsonString = aggregate.getInstanceMetadataJson();
    if (jsonString != null && !jsonString.isBlank()) {
      try {
        JsonNode jsonNode = OBJECT_MAPPER.readTree(jsonString);
        doEntity.setInstanceMetadata(jsonNode);
      } catch (JsonProcessingException e) {
        // JSON 解析失败时记录警告，保持 instanceMetadata 为 null
        // 这种情况不应该发生，因为领域层应该确保 JSON 格式正确
      }
    }
  }

  /// 将数据库实体转换为聚合根。
  ///
  /// 使用 `VenueInstanceAggregate.restore()` 工厂方法重建聚合根，
  /// 然后设置 `instanceMetadataJson` 字段。
  ///
  /// @param doEntity 数据库实体
  /// @return 聚合根
  default VenueInstanceAggregate toAggregate(VenueInstanceDO doEntity) {
    if (doEntity == null) {
      return null;
    }
    VenueInstanceAggregate aggregate =
        VenueInstanceAggregate.restore(
            doEntity.getId(),
            doEntity.getVenueId(),
            doEntity.getVolume(),
            doEntity.getIssue(),
            doEntity.getEdition(),
            doEntity.getPublicationYear(),
            doEntity.getPublicationMonth(),
            doEntity.getPublicationDay(),
            doEntity.getConferenceName(),
            doEntity.getConferenceStartDate(),
            doEntity.getConferenceEndDate(),
            doEntity.getConferenceLocation(),
            doEntity.getVersion());

    // 处理 instanceMetadata → instanceMetadataJson 转换
    JsonNode metadata = doEntity.getInstanceMetadata();
    if (metadata != null && !metadata.isNull()) {
      aggregate.setInstanceMetadataJson(metadata.toString());
    }

    return aggregate;
  }
}
