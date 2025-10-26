package com.patra.storage.app.recordupload;

import com.patra.storage.domain.model.aggregate.FileMetadata;
import com.patra.storage.domain.model.enums.StorageProvider;
import com.patra.storage.domain.model.vo.BusinessContext;
import com.patra.storage.domain.model.vo.FileChecksum;
import com.patra.storage.domain.model.vo.FileSize;
import com.patra.storage.domain.model.vo.StorageKey;
import com.patra.storage.domain.port.FileMetadataRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application orchestrator that records file metadata once uploads reach object storage. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordUploadOrchestrator {

  private final FileMetadataRepository repository;

  /**
   * Executes the record upload use case.
   *
   * @param command validated upload description from adapters
   * @return persistence result payload
   */
  @Transactional
  public RecordUploadResult execute(RecordUploadCommand command) {
    Objects.requireNonNull(command, "command cannot be null");

    FileMetadata metadata =
        FileMetadata.create(
                new StorageKey(command.bucketName(), command.objectKey()),
                new FileSize(command.fileSize()),
                new FileChecksum(command.md5Hash(), command.sha256Hash()),
                new BusinessContext(
                    command.serviceName(),
                    command.businessType(),
                    command.businessId(),
                    command.correlationData()),
                StorageProvider.fromName(command.providerType()))
            .withContentType(command.contentType())
            .withExpiresAt(command.expiresAt())
            .withRecordRemarks(command.recordRemarks())
            .withIpAddress(command.ipAddress());

    FileMetadata saved = repository.save(metadata);
    log.info(
        "File metadata recorded: id={}, storageKey={}",
        saved.getId(),
        saved.getStorageKey().fullKey());
    return new RecordUploadResult(saved.getId(), saved.getUploadedAt());
  }
}
