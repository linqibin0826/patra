package com.patra.storage.domain.model.aggregate;

import com.patra.storage.domain.model.enums.FileStatus;
import com.patra.storage.domain.model.enums.StorageProvider;
import com.patra.storage.domain.model.vo.BusinessContext;
import com.patra.storage.domain.model.vo.FileChecksum;
import com.patra.storage.domain.model.vo.FileSize;
import com.patra.storage.domain.model.vo.StorageKey;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

/** Aggregate root representing metadata captured for each stored object. */
@Getter
@ToString
public class FileMetadata {

  private Long id;
  private StorageKey storageKey;
  private FileSize fileSize;
  private String contentType;
  private FileChecksum checksum;
  private BusinessContext context;
  private StorageProvider provider;
  private FileStatus status;
  private Instant uploadedAt;
  private Instant expiresAt;
  private Instant deletedAt;
  private String recordRemarks;
  private Long version;

  @Getter(AccessLevel.NONE)
  private byte[] ipAddress;

  private Instant createdAt;
  private Long createdBy;
  private String createdByName;
  private Instant updatedAt;
  private Long updatedBy;
  private String updatedByName;
  private Boolean deleted;

  private FileMetadata() {
    // Use static factory
  }

  /**
   * Creates a new metadata aggregate, initializing mandatory audit attributes.
   *
   * @param storageKey canonical storage locator
   * @param fileSize physical payload size
   * @param checksum integrity checksum data
   * @param context upstream business context
   * @param provider storage provider type
   * @return initialized aggregate ready for persistence
   */
  public static FileMetadata create(
      StorageKey storageKey,
      FileSize fileSize,
      FileChecksum checksum,
      BusinessContext context,
      StorageProvider provider) {
    Objects.requireNonNull(storageKey, "storageKey cannot be null");
    Objects.requireNonNull(fileSize, "fileSize cannot be null");
    Objects.requireNonNull(checksum, "checksum cannot be null");
    Objects.requireNonNull(context, "context cannot be null");
    Objects.requireNonNull(provider, "provider cannot be null");

    FileMetadata metadata = new FileMetadata();
    metadata.storageKey = storageKey;
    metadata.fileSize = fileSize;
    metadata.checksum = checksum;
    metadata.context = context;
    metadata.provider = provider;
    metadata.status = FileStatus.ACTIVE;
    metadata.uploadedAt = Instant.now();
    metadata.version = 0L;
    metadata.deleted = Boolean.FALSE;
    metadata.createdAt = metadata.uploadedAt;
    metadata.updatedAt = metadata.uploadedAt;
    return metadata;
  }

  /**
   * Restores an aggregate from an existing persistence snapshot.
   *
   * @param id primary key
   * @param storageKey persisted storage key
   * @param fileSize persisted size in bytes
   * @param contentType persisted MIME type
   * @param checksum checksum snapshot
   * @param context business context snapshot
   * @param provider provider type
   * @param status file lifecycle status
   * @param uploadedAt upload timestamp
   * @param expiresAt expiration timestamp
   * @param deletedAt deletion timestamp
   * @param recordRemarks audit remark payload
   * @param version optimistic lock version
   * @param ipAddress requester IP stored in binary
   * @param createdAt creation time
   * @param createdBy creator id
   * @param createdByName creator display name
   * @param updatedAt last update time
   * @param updatedBy updater id
   * @param updatedByName updater display name
   * @param deleted logical deletion marker
   * @return fully materialized aggregate
   */
  public static FileMetadata restore(
      Long id,
      StorageKey storageKey,
      FileSize fileSize,
      String contentType,
      FileChecksum checksum,
      BusinessContext context,
      StorageProvider provider,
      FileStatus status,
      Instant uploadedAt,
      Instant expiresAt,
      Instant deletedAt,
      String recordRemarks,
      Long version,
      byte[] ipAddress,
      Instant createdAt,
      Long createdBy,
      String createdByName,
      Instant updatedAt,
      Long updatedBy,
      String updatedByName,
      Boolean deleted) {
    FileMetadata metadata = new FileMetadata();
    metadata.id = id;
    metadata.storageKey = storageKey;
    metadata.fileSize = fileSize;
    metadata.contentType = contentType;
    metadata.checksum = checksum;
    metadata.context = context;
    metadata.provider = provider;
    metadata.status = status;
    metadata.uploadedAt = uploadedAt;
    metadata.expiresAt = expiresAt;
    metadata.deletedAt = deletedAt;
    metadata.recordRemarks = recordRemarks;
    metadata.version = version;
    metadata.ipAddress = ipAddress == null ? null : ipAddress.clone();
    metadata.createdAt = createdAt;
    metadata.createdBy = createdBy;
    metadata.createdByName = createdByName;
    metadata.updatedAt = updatedAt;
    metadata.updatedBy = updatedBy;
    metadata.updatedByName = updatedByName;
    metadata.deleted = deleted;
    return metadata;
  }

  /**
   * Assigns the generated identifier after persistence.
   *
   * @param id database primary key
   */
  public void assignId(Long id) {
    if (this.id != null) {
      throw new IllegalStateException("Aggregate already has an id");
    }
    this.id = Objects.requireNonNull(id, "id cannot be null");
  }

  /**
   * Updates the optimistic lock version, invoked by the repository upon save.
   *
   * @param version new version value
   */
  public void updateVersion(Long version) {
    this.version = Objects.requireNonNull(version, "version cannot be null");
  }

  /**
   * Configures the MIME type associated with the stored object.
   *
   * @param contentType optional MIME type string
   * @return this aggregate for fluent usage
   */
  public FileMetadata withContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  /**
   * Configures the expiry time for retention management.
   *
   * @param expiresAt optional expiry timestamp
   * @return this aggregate for fluent usage
   */
  public FileMetadata withExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /**
   * Captures caller supplied remarks for audit trails.
   *
   * @param remarks optional JSON/text remarks
   * @return this aggregate for fluent usage
   */
  public FileMetadata withRecordRemarks(String remarks) {
    this.recordRemarks = remarks;
    return this;
  }

  /**
   * Stores the caller IP (IPv4/IPv6) in binary form.
   *
   * @param ip optional binary IP representation
   * @return this aggregate for fluent usage
   */
  public FileMetadata withIpAddress(byte[] ip) {
    this.ipAddress = ip == null ? null : ip.clone();
    return this;
  }

  /**
   * Marks the file as deleted while keeping the audit trail.
   *
   * @param operatorId operator identifier executing the deletion
   * @param operatorName operator display name
   */
  public void markAsDeleted(Long operatorId, String operatorName) {
    if (this.status == FileStatus.DELETED) {
      throw new IllegalStateException("File already deleted");
    }
    this.status = FileStatus.DELETED;
    this.deleted = Boolean.TRUE;
    this.deletedAt = Instant.now();
    touchAudit(operatorId, operatorName);
  }

  /**
   * Checks whether the metadata has exceeded its retention period.
   *
   * @return {@code true} when the file should be considered expired
   */
  public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt);
  }

  /**
   * Returns a defensive copy of the stored IP address.
   *
   * @return binary IP or {@code null} when not provided
   */
  public byte[] getIpAddress() {
    return ipAddress == null ? null : ipAddress.clone();
  }

  /**
   * Refreshes audit metadata.
   *
   * @param operatorId optional operator id
   * @param operatorName optional operator display name
   */
  public void touchAudit(Long operatorId, String operatorName) {
    this.updatedAt = Instant.now();
    this.updatedBy = operatorId;
    this.updatedByName = operatorName;
  }
}
