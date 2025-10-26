package com.patra.ingest.infra.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.api.dto.AuthorDTO;
import com.patra.catalog.api.dto.JournalDTO;
import com.patra.catalog.api.dto.LiteratureDTO;
import com.patra.common.objectstorage.ObjectKeyTemplate;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.StandardLiterature;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardAuthor;
import com.patra.ingest.domain.model.vo.StandardLiterature.StandardJournal;
import com.patra.ingest.domain.port.LiteraturePublisherPort;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.starter.objectstorage.ObjectStorageProperties;
import com.patra.starter.objectstorage.ObjectStorageTemplate;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadResult;
import com.patra.storage.api.client.StorageClient;
import com.patra.storage.api.dto.RecordUploadResponse;
import com.patra.storage.api.dto.UploadRecordRequest;
import feign.FeignException;
import feign.RetryableException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Anti-corruption layer that converts ingest domain models into catalog payloads and coordinates
 * uploads/metadata recording against the storage service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiteraturePublisherAdapter implements LiteraturePublisherPort {

  private static final String STORAGE_CHANNEL = "storage.metadata.internal";
  private static final String AGGREGATE_TYPE = "TASK_RUN";
  private static final String OP_TYPE = "METADATA_RECORD";
  private static final String SERVICE_NAME = "patra-ingest";
  private static final String BUSINESS_TYPE = "literature_batch";
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  private final ObjectMapper objectMapper;
  private final ObjectStorageTemplate objectStorageTemplate;
  private final ObjectStorageProperties storageProperties;
  private final StorageClient storageClient;
  private final OutboxMessageRepository outboxMessageRepository;

  @Override
  public PublishResult publish(List<StandardLiterature> literature, PublishContext context) {
    List<StandardLiterature> safeLiterature =
        literature == null ? Collections.emptyList() : literature;
    List<LiteratureDTO> payload =
        safeLiterature.stream().map(this::toDto).collect(Collectors.toList());

    byte[] serialized = serializePayload(payload, context);
    Checksums checksums = calculateChecksums(serialized);
    String bucket = resolveBucket();
    String objectKey = generateObjectKey(context);

    UploadResult uploadResult =
        uploadPayload(bucket, objectKey, serialized, context, payload.size());
    recordMetadata(uploadResult, context, serialized.length, checksums);

    return PublishResult.builder()
        .storageKey(uploadResult.getStorageKey())
        .publishedCount(payload.size())
        .build();
  }

  private byte[] serializePayload(List<LiteratureDTO> payload, PublishContext context) {
    try {
      byte[] serialized = objectMapper.writeValueAsBytes(payload);
      log.info(
          "literature payload prepared runId={} batchNo={} provenance={} size={} bytes entries={}",
          context.runId(),
          context.batchNo(),
          context.provenanceCode(),
          serialized.length,
          payload.size());
      return serialized;
    } catch (JsonProcessingException ex) {
      throw new LiteraturePublishException("Failed to serialize literature payload", ex);
    }
  }

  private UploadResult uploadPayload(
      String bucket, String objectKey, byte[] payload, PublishContext context, int entryCount) {
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
      UploadResult result = objectStorageTemplate.upload(bucket, objectKey, inputStream, metadata);
      log.info(
          "literature payload uploaded bucket={} key={} size={} bytes",
          result.getBucketName(),
          result.getObjectKey(),
          result.getFileSize());
      return result;
    } catch (Exception ex) {
      throw new LiteraturePublishException("Failed to upload literature payload", ex);
    }
  }

  private void recordMetadata(
      UploadResult uploadResult, PublishContext context, long payloadSize, Checksums checksums) {
    UploadRecordRequest request =
        buildUploadRecordRequest(uploadResult, context, payloadSize, checksums);
    try {
      RecordUploadResponse response = storageClient.recordUpload(request);
      log.info(
          "Metadata recorded successfully storageKey={} metadataId={}",
          uploadResult.getStorageKey(),
          response.metadataId());
    } catch (FeignException e) {
      handleMetadataRecordFailure(e, request, context);
    } catch (Exception e) {
      if (e instanceof RetryableException) {
        log.warn("Metadata recording timeout, persisting to Outbox for retry", e);
        saveToOutbox(request, context);
        return;
      }
      log.error("Unexpected error recording metadata, persisting to Outbox", e);
      saveToOutbox(request, context);
    }
  }

  private UploadRecordRequest buildUploadRecordRequest(
      UploadResult result, PublishContext context, long payloadSize, Checksums checksums) {
    Map<String, Object> correlation = buildCorrelationData(result, context);
    String providerType = storageProperties.getActiveProvider();
    String normalizedProvider =
        StringUtils.hasText(providerType) ? providerType.toUpperCase(Locale.ROOT) : "MINIO";
    return new UploadRecordRequest(
        result.getBucketName(),
        result.getObjectKey(),
        payloadSize,
        "application/json",
        checksums.md5(),
        checksums.sha256(),
        SERVICE_NAME,
        BUSINESS_TYPE,
        buildBusinessId(context),
        correlation,
        normalizedProvider,
        null,
        buildRecordRemarks(context));
  }

  private void handleMetadataRecordFailure(
      FeignException exception, UploadRecordRequest request, PublishContext context) {
    int status = exception.status();
    if (status >= 500 || status == 503) {
      log.warn("patra-storage unavailable (HTTP {}), storing metadata request in Outbox", status);
      saveToOutbox(request, context);
      return;
    }
    if (status >= 400 && status < 500) {
      log.error(
          "Invalid metadata record request (HTTP {}), manual investigation required. Request bucket={}, key={}",
          status,
          request.bucketName(),
          request.objectKey(),
          exception);
      return;
    }
    log.error("Unexpected Feign error recording metadata", exception);
    saveToOutbox(request, context);
  }

  private void saveToOutbox(UploadRecordRequest request, PublishContext context) {
    try {
      String payloadJson = objectMapper.writeValueAsString(request);
      String headersJson = objectMapper.writeValueAsString(buildHeaders(context));
      Long aggregateId = context.runId() != null ? context.runId() : 0L;
      OutboxMessage message =
          OutboxMessage.builder()
              .aggregateType(AGGREGATE_TYPE)
              .aggregateId(aggregateId)
              .channel(STORAGE_CHANNEL)
              .opType(OP_TYPE)
              .partitionKey(safeProvenance(context.provenanceCode()))
              .dedupKey(generateDedupKey(request))
              .payloadJson(payloadJson)
              .headersJson(headersJson)
              .statusCode("PENDING")
              .retryCount(0)
              .build();
      outboxMessageRepository.saveOrUpdate(message);
      log.info(
          "Metadata record request saved to Outbox runId={} storageKey={} dedupKey={}",
          context.runId(),
          request.storageKey(),
          message.getDedupKey());
    } catch (Exception e) {
      log.error(
          "CRITICAL: Failed to persist metadata record request to Outbox storageKey={} runId={}",
          request.bucketName() + "/" + request.objectKey(),
          context.runId(),
          e);
    }
  }

  private Map<String, Object> buildHeaders(PublishContext context) {
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("provenanceCode", context.provenanceCode());
    headers.put("batchNo", context.batchNo());
    headers.put("traceId", MDC.get("traceId"));
    return headers;
  }

  private Map<String, Object> buildCorrelationData(UploadResult result, PublishContext context) {
    Map<String, Object> correlation = new LinkedHashMap<>();
    if (context.runId() != null) {
      correlation.put("runId", context.runId());
    }
    correlation.put("batchNo", context.batchNo());
    if (StringUtils.hasText(context.provenanceCode())) {
      correlation.put("provenanceCode", context.provenanceCode());
    }
    correlation.put("storageKey", result.getStorageKey());
    return correlation;
  }

  private String buildBusinessId(PublishContext context) {
    String provenance = safeProvenance(context.provenanceCode());
    String runIdSegment = context.runId() != null ? String.valueOf(context.runId()) : "na";
    return provenance + "-" + context.batchNo() + "-" + runIdSegment;
  }

  private String buildRecordRemarks(PublishContext context) {
    Map<String, Object> remarks = new LinkedHashMap<>();
    remarks.put("runId", context.runId());
    remarks.put("batchNo", context.batchNo());
    remarks.put("provenanceCode", context.provenanceCode());
    try {
      return objectMapper.writeValueAsString(remarks);
    } catch (JsonProcessingException ex) {
      return remarks.toString();
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

  private String generateDedupKey(UploadRecordRequest request) {
    String storageKey = request.storageKey();
    String input = storageKey + ':' + request.fileSize();
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      sha256.update(input.getBytes(StandardCharsets.UTF_8));
      return HEX_FORMAT.formatHex(sha256.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Missing SHA-256 algorithm", ex);
    }
  }

  private String resolveBucket() {
    String active = storageProperties.getActiveProvider();
    ObjectStorageProperties.ProviderConfig config = storageProperties.getProviders().get(active);
    if (config == null || !StringUtils.hasText(config.getBucket())) {
      throw new IllegalStateException("No bucket configured for provider " + active);
    }
    return config.getBucket();
  }

  private String generateObjectKey(PublishContext context) {
    String provenance = safeProvenance(context.provenanceCode());
    long runId = context.runId() != null ? context.runId() : 0L;

    // Build business ID: {provenanceCode}-{runId}-batch-{batchNo(3-digit)}
    String businessId = String.format("%s-%d-batch-%03d", provenance, runId, context.batchNo());

    // Generate standardized key: ingest/literature-batch/yyyy/MM/dd/{businessId}.json
    return ObjectKeyTemplate.generateDailyKey(
        "ingest", "literature-batch", businessId, LocalDate.now(), "json");
  }

  private String safeProvenance(String provenanceCode) {
    return StringUtils.hasText(provenanceCode)
        ? provenanceCode.toLowerCase(Locale.ROOT)
        : "unknown";
  }

  private LiteratureDTO toDto(StandardLiterature source) {
    return LiteratureDTO.builder()
        .title(source.getTitle())
        .abstractText(source.getAbstractText())
        .authors(mapAuthors(source.getAuthors()))
        .journal(mapJournal(source.getJournal()))
        .identifiers(source.getIdentifiers())
        .publicationDate(source.getPublicationDate())
        .keywords(source.getKeywords())
        .language(null)
        .publicationTypes(List.of())
        .build();
  }

  private List<AuthorDTO> mapAuthors(List<StandardAuthor> authors) {
    if (CollectionUtils.isEmpty(authors)) {
      return List.of();
    }
    return authors.stream()
        .map(
            author ->
                AuthorDTO.builder()
                    .lastName(author.getLastName())
                    .foreName(author.getForeName())
                    .initials(null)
                    .affiliations(resolveAffiliations(author.getAffiliation()))
                    .identifier(null)
                    .identifierSource(null)
                    .build())
        .collect(Collectors.toUnmodifiableList());
  }

  private JournalDTO mapJournal(StandardJournal journal) {
    if (journal == null) {
      return null;
    }
    return JournalDTO.builder()
        .title(journal.getTitle())
        .issn(journal.getIssn())
        .issnType(null)
        .publisher(journal.getPublisher())
        .country(null)
        .build();
  }

  private List<String> resolveAffiliations(String affiliation) {
    if (!StringUtils.hasText(affiliation)) {
      return List.of();
    }
    return List.of(affiliation);
  }

  /** Publishing exception indicating serialization, upload, or storage failure. */
  public static class LiteraturePublishException extends RuntimeException {
    public LiteraturePublishException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private record Checksums(String md5, String sha256) {}
}
