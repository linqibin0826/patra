package com.patra.storage.infra.persistence.repository;

import com.patra.storage.domain.model.aggregate.FileMetadata;
import com.patra.storage.domain.model.vo.StorageKey;
import com.patra.storage.domain.port.FileMetadataRepository;
import com.patra.storage.infra.persistence.converter.FileMetadataConverter;
import com.patra.storage.infra.persistence.entity.FileMetadataDO;
import com.patra.storage.infra.persistence.mapper.FileMetadataMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Infrastructure repository implementation backed by MyBatis-Plus. */
@Repository
@RequiredArgsConstructor
public class FileMetadataRepositoryImpl implements FileMetadataRepository {

  private final FileMetadataMapper mapper;
  private final FileMetadataConverter converter;

  @Override
  public FileMetadata save(FileMetadata metadata) {
    FileMetadataDO dataObject = converter.toDO(metadata);
    if (metadata.getId() == null) {
      mapper.insert(dataObject);
    } else {
      mapper.updateById(dataObject);
    }
    FileMetadataDO persisted = mapper.selectById(dataObject.getId());
    return converter.toAggregate(persisted);
  }

  @Override
  public Optional<FileMetadata> findByStorageKey(StorageKey storageKey) {
    FileMetadataDO dataObject = mapper.findByStorageKey(storageKey.fullKey());
    return Optional.ofNullable(converter.toAggregate(dataObject));
  }
}
