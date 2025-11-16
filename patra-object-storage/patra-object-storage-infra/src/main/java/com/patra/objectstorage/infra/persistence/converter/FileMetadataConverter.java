package com.patra.objectstorage.infra.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.objectstorage.domain.model.aggregate.FileMetadata;
import com.patra.objectstorage.domain.model.enums.FileStatus;
import com.patra.objectstorage.domain.model.enums.StorageProvider;
import com.patra.objectstorage.domain.model.vo.BusinessContext;
import com.patra.objectstorage.domain.model.vo.FileChecksum;
import com.patra.objectstorage.domain.model.vo.FileSize;
import com.patra.objectstorage.domain.model.vo.StorageKey;
import com.patra.objectstorage.infra.persistence.entity.FileMetadataDO;
import java.util.Locale;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 文件元数据转换器。
 *
 * <p>MapStruct映射器,负责在领域聚合根({@link FileMetadata})和数据对象({@link FileMetadataDO})之间进行转换。
 * 这个转换器是基础设施层的反腐败层,隔离领域模型与持久化模型的差异。
 *
 * <p>转换规则:
 *
 * <ul>
 *   <li>领域值对象(如StorageKey、FileSize)解包为DO的基本类型字段
 *   <li>领域枚举转换为DO的字符串字段
 *   <li>Map类型的关联数据序列化为JsonNode以适配MyBatis的JSON类型处理器
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface FileMetadataConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  /**
   * 将领域聚合根映射到其持久化表示。
   *
   * <p>将领域模型转换为数据对象,用于插入或更新数据库记录。 MapStruct自动生成实现,通过表达式解包值对象和枚举。
   *
   * @param aggregate 领域聚合根
   * @return 持久化数据对象
   */
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
  FileMetadataDO toDO(FileMetadata aggregate);

  /**
   * 将数据对象映射回领域聚合根。
   *
   * <p>从数据库加载的数据对象重建领域模型,恢复值对象和枚举类型。 手动实现以精确控制值对象的构造和验证逻辑。
   *
   * @param dataObject 持久化数据对象
   * @return 领域聚合根
   */
  default FileMetadata toAggregate(FileMetadataDO dataObject) {
    if (dataObject == null) {
      return null;
    }
    StorageKey storageKey = new StorageKey(dataObject.getBucketName(), dataObject.getObjectKey());
    FileSize fileSize = new FileSize(dataObject.getFileSize());
    FileChecksum checksum = new FileChecksum(dataObject.getMd5Hash(), dataObject.getSha256Hash());
    BusinessContext context =
        new BusinessContext(
            dataObject.getServiceName(),
            dataObject.getBusinessType(),
            dataObject.getBusinessId(),
            toCorrelationMap(dataObject.getCorrelationData()));
    FileStatus status =
        dataObject.getFileStatus() == null
            ? FileStatus.ACTIVE
            : FileStatus.valueOf(dataObject.getFileStatus().toUpperCase(Locale.ENGLISH));
    FileMetadata metadata =
        FileMetadata.restore(
            dataObject.getId(),
            storageKey,
            fileSize,
            dataObject.getContentType(),
            checksum,
            context,
            StorageProvider.fromName(dataObject.getProviderType()),
            status,
            dataObject.getUploadedAt(),
            dataObject.getExpiresAt(),
            dataObject.getDeletedAt(),
            dataObject.getRecordRemarks(),
            dataObject.getVersion(),
            dataObject.getIpAddress(),
            dataObject.getCreatedAt(),
            dataObject.getCreatedBy(),
            dataObject.getCreatedByName(),
            dataObject.getUpdatedAt(),
            dataObject.getUpdatedBy(),
            dataObject.getUpdatedByName(),
            dataObject.getDeleted());
    return metadata;
  }

  /**
   * 将关联元数据从Map形式序列化为JSON。
   *
   * <p>用于将领域模型中的Map类型关联数据转换为JsonNode, 适配MyBatis-Plus的JacksonTypeHandler进行JSON字段的持久化。
   *
   * @param source 源Map对象
   * @return JSON节点表示
   */
  default JsonNode mapCorrelationData(Map<String, Object> source) {
    return OBJECT_MAPPER.valueToTree(source == null ? Map.of() : source);
  }

  /**
   * 将JSON节点转换回Map形式。
   *
   * <p>从数据库加载的JsonNode反序列化为Map,用于重建领域对象的关联数据。
   *
   * @param node JSON节点
   * @return Map表示
   */
  private Map<String, Object> toCorrelationMap(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return Map.of();
    }
    return OBJECT_MAPPER.convertValue(node, MAP_TYPE);
  }
}
