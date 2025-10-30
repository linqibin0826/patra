package com.patra.ingest.infra.integration.storage;

import com.patra.ingest.domain.port.StorageMetadataPort;
import com.patra.starter.objectstorage.ObjectStorageProperties;
import com.patra.storage.api.client.StorageClient;
import com.patra.storage.api.dto.RecordUploadResponse;
import com.patra.storage.api.dto.UploadRecordRequest;
import feign.FeignException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Infrastructure adapter implementing metadata recording to patra-storage service.
 *
 * <p>This adapter provides a thin RPC integration layer to the patra-storage bounded context. It
 * delegates to the {@link StorageClient} Feign client for actual service communication.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Convert domain metadata requests to RPC DTOs
 *   <li>Invoke patra-storage service via Feign client
 *   <li>Translate service responses to domain results
 *   <li>Translate Feign exceptions to domain exceptions
 * </ul>
 *
 * <p>Error handling and retry logic are delegated to the application layer orchestrator.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageMetadataAdapter implements StorageMetadataPort {

  @Value("${spring.application.name}")
  private String serviceName;

  private final StorageClient storageClient;
  private final ObjectStorageProperties objectStorageProperties;

  @Override
  public MetadataResult recordUpload(MetadataRequest request) {
    UploadRecordRequest rpcRequest = toRpcRequest(request);

    try {
      RecordUploadResponse response = storageClient.recordUpload(rpcRequest);

      return MetadataResult.builder()
          .metadataId(response.metadataId())
          .recordedAt(response.recordedAt())
          .build();

    } catch (FeignException e) {
      // Translate Feign errors to domain exceptions for application layer handling
      throw new StorageMetadataException(
          "Failed to record metadata to patra-storage: " + e.getMessage(), e);
    }
  }

  private UploadRecordRequest toRpcRequest(MetadataRequest request) {
    // Populate infrastructure-specific fields
    String actualServiceName =
        StringUtils.hasText(request.serviceName()) ? request.serviceName() : serviceName;

    String providerType = objectStorageProperties.getActiveProvider();
    String normalizedProvider =
        StringUtils.hasText(providerType) ? providerType.toUpperCase(Locale.ROOT) : "MINIO";
    String actualProviderType =
        StringUtils.hasText(request.providerType()) ? request.providerType() : normalizedProvider;

    return new UploadRecordRequest(
        request.bucketName(),
        request.objectKey(),
        request.fileSize(),
        request.contentType(),
        request.md5(),
        request.sha256(),
        actualServiceName,
        request.businessType(),
        request.businessId(),
        request.correlation(),
        actualProviderType,
        null, // expiresAt - not used for literature storage
        request.remarks());
  }

  /** Metadata recording exception indicating patra-storage service communication failure. */
  public static class StorageMetadataException extends RuntimeException {
    public StorageMetadataException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
