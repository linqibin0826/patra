package dev.linqibin.patra.objectstorage.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.objectstorage.domain.model.aggregate.FileMetadata;
import dev.linqibin.patra.objectstorage.domain.model.enums.FileStatus;
import dev.linqibin.patra.objectstorage.domain.model.enums.StorageProvider;
import dev.linqibin.patra.objectstorage.domain.model.vo.BusinessContext;
import dev.linqibin.patra.objectstorage.domain.model.vo.FileChecksum;
import dev.linqibin.patra.objectstorage.domain.model.vo.FileSize;
import dev.linqibin.patra.objectstorage.domain.model.vo.StorageKey;
import dev.linqibin.patra.objectstorage.infra.adapter.persistence.entity.FileMetadataEntity;
import dev.linqibin.commons.json.JsonMapperHolder;
import java.util.Locale;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// 文件元数据 JPA 转换器。
///
/// MapStruct 映射器，负责在领域聚合根（{@link FileMetadata}）和 JPA 实体（{@link FileMetadataEntity}）之间进行转换。
/// 这个转换器是基础设施层的反腐败层，隔离领域模型与持久化模型的差异。
///
/// 转换规则：
///
/// - 领域值对象（如 StorageKey、FileSize）解包为 Entity 的基本类型字段
/// - 领域枚举转换为 Entity 的字符串字段
/// - Map 类型的关联数据序列化为 JsonNode 以适配 JPA 的 JSON 类型处理
@Mapper(componentModel = "spring")
public interface FileMetadataJpaMapper {

  ObjectMapper OBJECT_MAPPER = JsonMapperHolder.getObjectMapper();
  TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  /// 将领域聚合根映射到其持久化表示。
  ///
  /// 将领域模型转换为 JPA 实体，用于插入或更新数据库记录。
  /// MapStruct 自动生成实现，通过表达式解包值对象和枚举。
  ///
  /// @param aggregate 领域聚合根
  /// @return JPA 实体
  @Mapping(target = "storageKey", expression = "java(aggregate.getStorageKey().fullKey())")
  @Mapping(target = "bucketName", expression = "java(aggregate.getStorageKey().bucket())")
  @Mapping(target = "objectKey", expression = "java(aggregate.getStorageKey().objectKey())")
  @Mapping(target = "fileSize", expression = "java(aggregate.getFileSize().bytes())")
  @Mapping(target = "md5Hash", expression = "java(aggregate.getChecksum().md5Hash())")
  @Mapping(target = "sha256Hash", expression = "java(aggregate.getChecksum().sha256Hash())")
  @Mapping(target = "serviceName", expression = "java(aggregate.getContext().serviceName())")
  @Mapping(target = "businessType", expression = "java(aggregate.getContext().businessType())")
  @Mapping(target = "businessId", expression = "java(aggregate.getContext().businessId())")
  @Mapping(
      target = "correlationData",
      expression = "java(mapCorrelationData(aggregate.getContext().correlationData()))")
  @Mapping(target = "providerType", expression = "java(aggregate.getProvider().name())")
  @Mapping(target = "fileStatus", expression = "java(aggregate.getStatus().name())")
  // BaseJpaEntity 审计字段由 JPA Auditing 自动管理
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  FileMetadataEntity toEntity(FileMetadata aggregate);

  /// 将 JPA 实体映射回领域聚合根。
  ///
  /// 从数据库加载的 JPA 实体重建领域模型，恢复值对象和枚举类型。
  /// 手动实现以精确控制值对象的构造和验证逻辑。
  ///
  /// @param entity JPA 实体
  /// @return 领域聚合根
  default FileMetadata toAggregate(FileMetadataEntity entity) {
    if (entity == null) {
      return null;
    }
    StorageKey storageKey = new StorageKey(entity.getBucketName(), entity.getObjectKey());
    FileSize fileSize = new FileSize(entity.getFileSize());
    FileChecksum checksum = new FileChecksum(entity.getMd5Hash(), entity.getSha256Hash());
    BusinessContext context =
        new BusinessContext(
            entity.getServiceName(),
            entity.getBusinessType(),
            entity.getBusinessId(),
            toCorrelationMap(entity.getCorrelationData()));
    FileStatus status =
        entity.getFileStatus() == null
            ? FileStatus.ACTIVE
            : FileStatus.valueOf(entity.getFileStatus().toUpperCase(Locale.ENGLISH));
    return FileMetadata.restore(
        entity.getId(),
        storageKey,
        fileSize,
        entity.getContentType(),
        checksum,
        context,
        StorageProvider.fromName(entity.getProviderType()),
        status,
        entity.getUploadedAt(),
        entity.getExpiresAt(),
        entity.getRecordRemarks(),
        entity.getVersion(),
        entity.getIpAddress(),
        entity.getCreatedAt(),
        entity.getCreatedBy(),
        entity.getCreatedByName(),
        entity.getUpdatedAt(),
        entity.getUpdatedBy(),
        entity.getUpdatedByName());
  }

  /// 将关联元数据从 Map 形式序列化为 JSON。
  ///
  /// 用于将领域模型中的 Map 类型关联数据转换为 JsonNode，
  /// 适配 JPA 的 JSON 字段持久化。
  ///
  /// @param source 源 Map 对象
  /// @return JSON 节点表示
  default JsonNode mapCorrelationData(Map<String, Object> source) {
    return OBJECT_MAPPER.valueToTree(source == null ? Map.of() : source);
  }

  /// 将 JSON 节点转换回 Map 形式。
  ///
  /// 从数据库加载的 JsonNode 反序列化为 Map，用于重建领域对象的关联数据。
  ///
  /// @param node JSON 节点
  /// @return Map 表示
  private Map<String, Object> toCorrelationMap(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return Map.of();
    }
    return OBJECT_MAPPER.convertValue(node, MAP_TYPE);
  }
}
