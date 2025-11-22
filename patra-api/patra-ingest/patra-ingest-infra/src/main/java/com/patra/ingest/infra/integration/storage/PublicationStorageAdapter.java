package com.patra.ingest.infra.integration.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalPublication;
import com.patra.ingest.domain.port.PublicationStoragePort;
import com.patra.starter.objectstorage.ObjectStorageTemplate;
import com.patra.starter.objectstorage.StorageLocation;
import com.patra.starter.objectstorage.StorageLocationResolver;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/// 基础设施适配器,实现出版物存储到对象存储的功能。
/// 
/// 此适配器专注于技术性存储操作:
/// 
/// - 序列化: 将 {@link CanonicalPublication} 转换为 JSON 字节
///   - 校验和计算: MD5 和 SHA-256 用于完整性验证
///   - 存储上传: 通过 {@link ObjectStorageTemplate} 上传到 S3/MinIO
/// 
/// 存储格式: 直接使用共享内核模型 {@link CanonicalPublication}，保证数据完整性。
/// 
/// 跨服务集成(元数据记录)在应用层单独处理。
@Component
@RequiredArgsConstructor
@Slf4j
public class PublicationStorageAdapter implements PublicationStoragePort {

  private static final String BUSINESS_TYPE = "publication-batch";
  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("HHmmssSSS");

  private final ObjectMapper objectMapper;
  private final ObjectStorageTemplate objectStorageTemplate;
  private final StorageLocationResolver storageLocationResolver;

  @Override
  public StorageResult store(List<CanonicalPublication> publication, StorageContext context) {
    // 步骤 1: 序列化为 JSON (直接存储 CanonicalPublication,保证数据完整性)
    byte[] serialized = serializePayload(publication, context);

    // 步骤 2: 计算校验和
    Checksums checksums = calculateChecksums(serialized);

    // 步骤 3: 解析存储位置
    StorageLocation location = resolveStorageLocation(context);

    // 步骤 4: 上传到对象存储
    UploadResult uploadResult = uploadPayload(location, serialized, publication.size(), context);

    log.info(
        "出版物已存储 bucket={} key={} size={} bytes count={}",
        uploadResult.getBucketName(),
        uploadResult.getObjectKey(),
        uploadResult.getFileSize(),
        publication.size());

    return StorageResult.builder()
        .storageKey(uploadResult.getStorageKey())
        .bucketName(uploadResult.getBucketName())
        .objectKey(uploadResult.getObjectKey())
        .fileSize(uploadResult.getFileSize())
        .md5(checksums.md5())
        .sha256(checksums.sha256())
        .publicationCount(publication.size())
        .build();
  }

  private byte[] serializePayload(
      List<CanonicalPublication> payload, PublicationStoragePort.StorageContext context) {
    try {
      byte[] serialized = objectMapper.writeValueAsBytes(payload);
      log.info(
          "出版物负载已准备 runId={} batchNo={} provenance={} size={} bytes entries={}",
          context.runId(),
          context.batchNo(),
          context.provenanceCode(),
          serialized.length,
          payload.size());
      return serialized;
    } catch (JsonProcessingException ex) {
      throw new PublicationStorageException("序列化出版物负载失败", ex);
    }
  }

  private Checksums calculateChecksums(byte[] payload) {
    try {
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      md5.update(payload);
      sha256.update(payload);
      return new Checksums(
          HEX_FORMAT.formatHex(md5.digest()), HEX_FORMAT.formatHex(sha256.digest()));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("缺少摘要算法", ex);
    }
  }

  private StorageLocation resolveStorageLocation(PublicationStoragePort.StorageContext context) {
    com.patra.starter.objectstorage.StorageContext storageContext =
        com.patra.starter.objectstorage.StorageContext.builder()
            .businessType(BUSINESS_TYPE)
            .filename(generateFilename(context))
            .businessId(buildBusinessId(context))
            .correlationEntry("batchNo", context.batchNo())
            .correlationEntry("provenanceCode", safeProvenance(context.provenanceCode()))
            .correlationEntry("runId", context.runId())
            .build();

    return storageLocationResolver.resolve(storageContext);
  }

  private UploadResult uploadPayload(
      StorageLocation location,
      byte[] payload,
      int entryCount,
      PublicationStoragePort.StorageContext context) {
    ObjectMetadata metadata =
        ObjectMetadata.builder()
            .contentLength(payload.length)
            .contentType("application/json")
            .userMetadata(
                Map.of(
                    "provenanceCode", safeProvenance(context.provenanceCode()),
                    "runId", String.valueOf(context.runId()),
                    "batchNo", String.valueOf(context.batchNo()),
                    "entries", String.valueOf(entryCount)))
            .build();

    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(payload)) {
      UploadResult result =
          objectStorageTemplate.upload(
              location.bucket(), location.objectKey(), inputStream, metadata);
      log.info(
          "出版物负载已上传 bucket={} key={} size={} bytes",
          result.getBucketName(),
          result.getObjectKey(),
          result.getFileSize());
      return result;
    } catch (Exception ex) {
      throw new PublicationStorageException("上传出版物负载失败", ex);
    }
  }

  private String generateFilename(PublicationStoragePort.StorageContext context) {
    String provenance = safeProvenance(context.provenanceCode());
    String runSegment = context.runId() == null ? "na" : Long.toUnsignedString(context.runId(), 36);
    String timestamp = LocalDateTime.now().format(FILENAME_TIMESTAMP_FORMAT);
    return String.format(
        "%s-batch-%03d-%s-%s.json", provenance, context.batchNo(), runSegment, timestamp);
  }

  private String buildBusinessId(PublicationStoragePort.StorageContext context) {
    String provenance = safeProvenance(context.provenanceCode());
    String runIdSegment = context.runId() != null ? String.valueOf(context.runId()) : "na";
    return provenance + "-" + context.batchNo() + "-" + runIdSegment;
  }

  private String safeProvenance(ProvenanceCode provenanceCode) {
    if (provenanceCode == null) {
      return "unknown";
    }
    String code = provenanceCode.getCode();
    return StringUtils.hasText(code) ? code.toLowerCase(Locale.ROOT) : "unknown";
  }

  /// 存储异常,指示序列化或上传失败。
  public static class PublicationStorageException extends RuntimeException {
    public PublicationStorageException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private record Checksums(String md5, String sha256) {}
}
