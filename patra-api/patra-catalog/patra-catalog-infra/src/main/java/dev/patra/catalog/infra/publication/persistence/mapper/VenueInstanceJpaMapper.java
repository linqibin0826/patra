package dev.patra.catalog.infra.publication.persistence.mapper;

import dev.patra.catalog.domain.kernel.model.vo.VenueId;
import dev.patra.catalog.domain.publication.model.aggregate.VenueInstanceAggregate;
import dev.patra.catalog.domain.publication.model.vo.VenueInstanceId;
import dev.patra.catalog.infra.publication.persistence.entity.VenueInstanceEntity;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/// 载体实例 JPA 实体转换器。
///
/// **职责**：
///
/// - `VenueInstanceAggregate` ↔ `VenueInstanceEntity` 双向转换
/// - 值对象（`VenueId`、`VenueInstanceId`）与基本类型的映射
/// - JSON 字段（`instanceMetadata`）的 String ↔ JsonNode 转换
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public abstract class VenueInstanceJpaMapper {

  @Autowired protected JsonColumnCodec codec;

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate 载体实例聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "instanceIdToLong")
  @Mapping(target = "venueId", source = "venueId", qualifiedByName = "venueIdToLong")
  @Mapping(
      target = "instanceMetadata",
      source = "instanceMetadataJson",
      qualifiedByName = "stringToJsonNode")
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  public abstract VenueInstanceEntity toEntity(VenueInstanceAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `VenueInstanceAggregate.restore()` 工厂方法重建聚合根。
  ///
  /// @param entity JPA 实体
  /// @return 载体实例聚合根
  public VenueInstanceAggregate toAggregate(VenueInstanceEntity entity) {
    if (entity == null) {
      return null;
    }

    VenueInstanceAggregate aggregate =
        VenueInstanceAggregate.restore(
            entity.getId() != null ? VenueInstanceId.of(entity.getId()) : null,
            VenueId.of(entity.getVenueId()),
            entity.getVolume(),
            entity.getIssue(),
            entity.getEdition(),
            entity.getPublicationYear(),
            entity.getPublicationMonth(),
            entity.getPublicationDay(),
            entity.getConferenceName(),
            entity.getConferenceStartDate(),
            entity.getConferenceEndDate(),
            entity.getConferenceLocation(),
            entity.getVersion());

    // 恢复可变状态
    if (entity.getInstanceMetadata() != null) {
      aggregate.setInstanceMetadataJson(jsonNodeToString(entity.getInstanceMetadata()));
    }

    return aggregate;
  }

  /// 更新托管实体的可变字段。
  ///
  /// VenueInstance 大部分字段是不变量，仅 instanceMetadata 可变。
  ///
  /// @param entity 托管实体
  /// @param aggregate 包含更新数据的聚合根
  public void updateEntity(VenueInstanceEntity entity, VenueInstanceAggregate aggregate) {
    if (entity == null || aggregate == null) {
      return;
    }
    // 仅更新可变字段
    entity.setInstanceMetadata(stringToJsonNode(aggregate.getInstanceMetadataJson()));
  }

  /// 将 VenueInstanceId 转换为 Long。
  @Named("instanceIdToLong")
  Long instanceIdToLong(VenueInstanceId id) {
    return id != null ? id.value() : null;
  }

  /// 将 VenueId 转换为 Long。
  @Named("venueIdToLong")
  Long venueIdToLong(VenueId id) {
    return id != null ? id.value() : null;
  }

  /// 将 String 转换为 JsonNode。
  @Named("stringToJsonNode")
  JsonNode stringToJsonNode(String json) {
    return codec.read(json);
  }

  /// 将 JsonNode 转换为 String。
  String jsonNodeToString(JsonNode node) {
    return node == null || node.isNull() ? null : codec.write(node);
  }
}
