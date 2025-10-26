package com.patra.storage.domain.port;

import com.patra.storage.domain.model.aggregate.FileMetadata;
import com.patra.storage.domain.model.vo.StorageKey;
import java.util.Optional;

/** Repository abstraction for persisting {@link FileMetadata} aggregates. */
public interface FileMetadataRepository {

  /**
   * Persists the supplied aggregate.
   *
   * @param metadata aggregate to store
   * @return persisted aggregate (with identifier/version filled)
   */
  FileMetadata save(FileMetadata metadata);

  /**
   * Loads metadata by its canonical storage key.
   *
   * @param storageKey bucket/key tuple
   * @return existing metadata when present
   */
  Optional<FileMetadata> findByStorageKey(StorageKey storageKey);
}
