package com.patra.ingest.infra.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.api.dto.LiteratureDTO;
import com.patra.common.model.StandardLiterature;
import com.patra.common.objectstorage.StorageContext;
import com.patra.common.objectstorage.StorageLocation;
import com.patra.ingest.domain.port.LiteratureStoragePort;
import com.patra.ingest.infra.acl.LiteratureConverter;
import com.patra.starter.objectstorage.ObjectStorageTemplate;
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

/**
 * Infrastructure adapter implementing literature storage to object storage.
 *
 * <p>This adapter focuses solely on technical storage operations:
 *
 * <ul>
 *   <li>ACL mapping: StandardLiterature → LiteratureDTO (external API format)
 *   <li>Serialization: Convert payload to JSON bytes
 *   <li>Checksum calculation: MD5 and SHA-256 for integrity verification
 *   <li>Storage upload: Upload to S3/MinIO via {@link ObjectStorageTemplate}
 * </ul>
 *
 * <p>Cross-service integration (metadata recording) is handled separately in the application layer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiteratureStorageAdapter implements LiteratureStoragePort {

  private static final String BUSINESS_TYPE = "literature-batch";
  private static final HexFormat HEX_FORMAT = HexFormat.of();
  private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("HHmmssSSS");

  private final ObjectMapper objectMapper;
  private final ObjectStorageTemplate objectStorageTemplate;
  private final StorageLocationResolver storageLocationResolver;
  private final LiteratureConverter literatureConverter;

  @Override
  public StorageResult store(List<StandardLiterature> literature, StorageContext context) {
    // Step 1: ACL mapping (domain → external DTO)
    List<LiteratureDTO> payload = literatureConverter.toDto(literature);

    // Step 2: Serialize to JSON
    byte[] serialized = serializePayload(payload, context);

    // Step 3: Calculate checksums
    Checksums checksums = calculateChecksums(serialized);

    // Step 4: Resolve storage location
    StorageLocation location = resolveStorageLocation(context);

    // Step 5: Upload to object storage
    UploadResult uploadResult = uploadPayload(location, serialized, payload.size(), context);

    log.info(
        "Literature stored bucket={} key={} size={} bytes count={}",
        uploadResult.getBucketName(),
        uploadResult.getObjectKey(),
        uploadResult.getFileSize(),
        payload.size());

    return StorageResult.builder()
        .storageKey(uploadResult.getStorageKey())
        .bucketName(uploadResult.getBucketName())
        .objectKey(uploadResult.getObjectKey())
        .fileSize(uploadResult.getFileSize())
        .md5(checksums.md5())
        .sha256(checksums.sha256())
        .literatureCount(payload.size())
        .build();
  }

  private byte[] serializePayload(
      List<LiteratureDTO> payload, LiteratureStoragePort.StorageContext context) {
    try {
      byte[] serialized = objectMapper.writeValueAsBytes(payload);
      log.info(
          "Literature payload prepared runId={} batchNo={} provenance={} size={} bytes entries={}",
          context.runId(),
          context.batchNo(),
          context.provenanceCode(),
          serialized.length,
          payload.size());
      return serialized;
    } catch (JsonProcessingException ex) {
      throw new LiteratureStorageException("Failed to serialize literature payload", ex);
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
      throw new IllegalStateException("Missing digest algorithm", ex);
    }
  }

  private StorageLocation resolveStorageLocation(LiteratureStoragePort.StorageContext context) {
    com.patra.common.objectstorage.StorageContext storageContext =
        com.patra.common.objectstorage.StorageContext.builder()
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
      LiteratureStoragePort.StorageContext context) {
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
          "Literature payload uploaded bucket={} key={} size={} bytes",
          result.getBucketName(),
          result.getObjectKey(),
          result.getFileSize());
      return result;
    } catch (Exception ex) {
      throw new LiteratureStorageException("Failed to upload literature payload", ex);
    }
  }

  private String generateFilename(LiteratureStoragePort.StorageContext context) {
    String provenance = safeProvenance(context.provenanceCode());
    String runSegment = context.runId() == null ? "na" : Long.toUnsignedString(context.runId(), 36);
    String timestamp = LocalDateTime.now().format(FILENAME_TIMESTAMP_FORMAT);
    return String.format(
        "%s-batch-%03d-%s-%s.json", provenance, context.batchNo(), runSegment, timestamp);
  }

  private String buildBusinessId(LiteratureStoragePort.StorageContext context) {
    String provenance = safeProvenance(context.provenanceCode());
    String runIdSegment = context.runId() != null ? String.valueOf(context.runId()) : "na";
    return provenance + "-" + context.batchNo() + "-" + runIdSegment;
  }

  private String safeProvenance(String provenanceCode) {
    return StringUtils.hasText(provenanceCode)
        ? provenanceCode.toLowerCase(Locale.ROOT)
        : "unknown";
  }

  /** Storage exception indicating serialization or upload failure. */
  public static class LiteratureStorageException extends RuntimeException {
    public LiteratureStorageException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private record Checksums(String md5, String sha256) {}
}
