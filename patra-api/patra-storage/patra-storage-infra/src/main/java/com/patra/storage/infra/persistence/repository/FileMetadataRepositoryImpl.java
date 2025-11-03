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

/**
 * 文件元数据仓储实现。
 *
 * <p>基础设施层的仓储实现,使用MyBatis-Plus作为ORM框架,实现领域层定义的 {@link FileMetadataRepository}
 * 端口。负责聚合根的持久化、查询和领域对象与数据对象之间的转换。
 *
 * <p>实现要点:
 *
 * <ul>
 *   <li>使用转换器({@link FileMetadataConverter})在领域模型和数据模型之间进行双向转换
 *   <li>保存操作根据聚合根是否有ID判断执行insert还是update
 *   <li>保存后重新查询以获取数据库生成的字段(如ID、version、审计时间戳)
 *   <li>查询操作通过唯一的storage_key字段实现幂等性检查
 * </ul>
 */
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
