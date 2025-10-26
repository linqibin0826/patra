package com.patra.storage.infra.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.storage.domain.model.aggregate.FileMetadata;
import com.patra.storage.domain.model.enums.FileStatus;
import com.patra.storage.domain.model.enums.StorageProvider;
import com.patra.storage.domain.model.vo.BusinessContext;
import com.patra.storage.domain.model.vo.FileChecksum;
import com.patra.storage.domain.model.vo.FileSize;
import com.patra.storage.domain.model.vo.StorageKey;
import com.patra.storage.infra.persistence.entity.FileMetadataDO;
import java.util.Locale;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper bridging domain aggregates and Data Objects. */
@Mapper(componentModel = "spring")
public interface FileMetadataConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  /**
   * Maps the domain aggregate to its persistence representation.
   *
   * @param aggregate domain aggregate
   * @return persistence Data Object
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
   * Maps Data Objects back into domain aggregates.
   *
   * @param dataObject persistence Data Object
   * @return domain aggregate
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

  /** Serializes correlation metadata from map form to JSON. */
  default JsonNode mapCorrelationData(Map<String, Object> source) {
    return OBJECT_MAPPER.valueToTree(source == null ? Map.of() : source);
  }

  private Map<String, Object> toCorrelationMap(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return Map.of();
    }
    return OBJECT_MAPPER.convertValue(node, MAP_TYPE);
  }
}
